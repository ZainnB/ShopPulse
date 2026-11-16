package com.shoppulse.ai;

import com.shoppulse.common.BusinessException;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The security centrepiece of the AI module: it turns text written by a language model
 * (or anyone else) into a statement that is safe to run, or refuses with a clear reason.
 *
 * <p>It exists because model output is untrusted input. The guard is deliberately strict
 * and conservative: when in doubt it rejects. It is pure (no Spring, no I/O) so it can be
 * tested exhaustively. It is one of several locks; the others are the read-only database
 * role, a read-only transaction, a statement timeout and a row cap (see
 * {@link ReadOnlySqlExecutor}).
 *
 * <h2>Rules, in the order they are applied</h2>
 * <ol>
 *   <li><b>Normalise.</b> Markdown fences are removed, whitespace is collapsed and trailing
 *       semicolons are dropped. The statement is limited to {@value #MAX_LENGTH} characters.</li>
 *   <li><b>One statement only.</b> Any {@code ;} that is still present (inside or outside a
 *       string literal) is rejected, so {@code SELECT 1; DELETE ...} cannot get through.</li>
 *   <li><b>No comments.</b> {@code --}, {@code /*} and {@code *}{@code /} are rejected anywhere, because
 *       comments are the classic way to hide a payload from a keyword scan.</li>
 *   <li><b>Plain characters only.</b> Backslashes, double quotes (quoted identifiers),
 *       dollar signs (dollar-quoting, parameters), backticks, {@code ?} and any non
 *       printable-ASCII character are rejected outside string literals. Backslashes are
 *       rejected everywhere so {@code E'\x..'} escapes cannot be used to smuggle text.</li>
 *   <li><b>String literals are masked.</b> Before any keyword scan, the contents of
 *       {@code '...'} literals are replaced by filler, so {@code WHERE category_name = 'drop'}
 *       is fine while the same word outside a literal is not. An unterminated literal is rejected.</li>
 *   <li><b>SELECT only.</b> The first word must be {@code SELECT} or {@code WITH}, and a
 *       deny-list of statement and dangerous keywords (INSERT, UPDATE, DELETE, DROP, ALTER,
 *       CREATE, TRUNCATE, GRANT, REVOKE, COPY, CALL, DO, MERGE, EXECUTE, SET, VACUUM,
 *       TABLE, INTO, locking clauses ...) is rejected anywhere in the statement, which also
 *       blocks {@code SELECT ... INTO} and {@code SELECT ... FOR UPDATE}.</li>
 *   <li><b>No dangerous functions or catalogs.</b> Any identifier starting with {@code pg_}
 *       or {@code lo_}, {@code dblink*}, {@code information_schema}, {@code current_setting},
 *       {@code set_config}, XML export helpers and similar are rejected.</li>
 *   <li><b>View allow-list.</b> Every table-like reference after {@code FROM} or {@code JOIN},
 *       including comma-separated lists, must be one of exactly the five analytics views
 *       ({@link #ALLOWED_VIEWS}) or a CTE defined earlier in the same statement. Schema-qualified
 *       names, functions in the FROM clause (for example {@code generate_series}) and unknown
 *       tables are rejected. A CTE name is only visible after its own definition has closed
 *       (or inside itself for {@code WITH RECURSIVE}); otherwise a CTE named {@code orders}
 *       could be used to make an earlier {@code FROM orders} look legitimate. Subqueries
 *       are covered because every {@code FROM}/{@code JOIN} token in the statement is checked,
 *       whatever its nesting depth. {@code EXTRACT(x FROM y)}, {@code SUBSTRING(x FROM n)},
 *       {@code TRIM(... FROM x)} and {@code IS DISTINCT FROM} are recognised and skipped.</li>
 *   <li><b>Row cap.</b> A top-level {@code LIMIT} larger than {@code maxRows} is lowered to
 *       {@code maxRows}, {@code LIMIT ALL} is replaced, and a statement without a LIMIT gets
 *       one appended.</li>
 * </ol>
 */
public final class SqlGuard {

    /** The only relations the model may ever query. */
    public static final Set<String> ALLOWED_VIEWS = Set.of(
            "v_sales_by_day",
            "v_sales_by_category",
            "v_product_sales_daily",
            "v_top_products",
            "v_customer_summary");

    static final int MAX_LENGTH = 4000;

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "create", "truncate", "grant", "revoke",
            "copy", "call", "do", "merge", "execute", "set", "vacuum", "into", "table", "analyze",
            "explain", "comment", "lock", "listen", "notify", "prepare", "deallocate", "refresh",
            "reindex", "cluster", "discard", "reset", "show", "begin", "commit", "rollback",
            "savepoint", "declare", "fetch", "load", "import", "returning", "security", "owner",
            "pg_sleep", "pg_read_file", "dblink", "information_schema", "pg_catalog",
            "current_setting", "set_config", "query_to_xml", "query_to_xmlschema", "table_to_xml",
            "schema_to_xml", "database_to_xml", "cursor_to_xml", "ts_stat", "nextval", "setval",
            "currval", "lastval", "to_regclass", "current_database", "current_user", "session_user",
            "current_schema", "user", "version", "inet_server_addr", "inet_client_addr");

    private static final Pattern FORBIDDEN_PREFIX = Pattern.compile("^(pg_.*|lo_.*|dblink.*|has_.*_privilege|txid_.*)$");
    private static final Pattern FOR_LOCK = Pattern.compile("\\bfor (share|key|no)\\b");
    private static final Pattern TOKEN = Pattern.compile(
            "'[^']*'|[a-z_][a-z0-9_$]*|[0-9]+(?:\\.[0-9]+)?[a-z0-9_]*|::|<>|<=|>=|!=|\\|\\||\\S");
    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*");
    private static final Pattern TRAILING_SEMICOLONS = Pattern.compile("[;\\s]+$");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    /** Functions whose parentheses legitimately contain a bare FROM keyword. */
    private static final Set<String> FROM_FUNCTIONS = Set.of("extract", "substring", "trim", "overlay", "position");

    /** Words that end a FROM clause at the same nesting depth. */
    private static final Set<String> CLAUSE_TERMINATORS = Set.of(
            "where", "group", "having", "order", "limit", "offset", "union", "intersect", "except",
            "window", "fetch", "for", "returning");

    private final int maxRows;

    public SqlGuard(int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
    }

    public int maxRows() {
        return maxRows;
    }

    /**
     * Validates and normalises a candidate statement.
     *
     * @return a single-line SELECT statement that references only allow-listed views and
     *         carries a LIMIT no larger than {@code maxRows}
     * @throws BusinessException with a short reason if any rule is violated
     */
    public String validate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw reject("the query is empty");
        }
        String sql = FENCE.matcher(candidate).replaceAll(" ");
        sql = sql.replaceAll("\\s+", " ").trim();
        sql = TRAILING_SEMICOLONS.matcher(sql).replaceAll("").trim();
        if (sql.isEmpty()) {
            throw reject("the query is empty");
        }
        if (sql.length() > MAX_LENGTH) {
            throw reject("the query is too long");
        }
        if (sql.indexOf(';') >= 0) {
            throw reject("only a single statement is allowed");
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw reject("SQL comments are not allowed");
        }
        if (sql.indexOf('\\') >= 0) {
            throw reject("backslashes are not allowed");
        }

        String masked = mask(sql);
        rejectUnsafeCharacters(masked);

        List<Tok> toks = tokenize(masked);
        if (toks.isEmpty()) {
            throw reject("the query is empty");
        }
        int[] match = matchParentheses(toks);
        int[] depth = depths(toks);
        String first = toks.get(0).text();
        if (!first.equals("select") && !first.equals("with")) {
            throw reject("only SELECT statements are allowed");
        }
        rejectForbiddenWords(toks, masked);

        boolean recursive = first.equals("with") && toks.size() > 1 && toks.get(1).is("recursive");
        List<Cte> ctes = first.equals("with") ? parseCtes(toks, match, recursive) : List.of();
        int refs = checkTableReferences(toks, match, depth, ctes, recursive);
        if (refs == 0) {
            throw reject("the query must read from one of the analytics views");
        }
        return applyLimit(sql, toks, depth);
    }

    // ------------------------------------------------------------------ lexical rules

    /** Replaces the contents of every string literal by filler of the same length, lower-casing the rest. */
    private static String mask(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inLiteral = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!inLiteral) {
                out.append(Character.toLowerCase(c));
                if (c == '\'') {
                    inLiteral = true;
                }
            } else if (c == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    out.append("xx"); // escaped quote inside the literal
                    i++;
                } else {
                    out.append('\'');
                    inLiteral = false;
                }
            } else {
                out.append('x');
            }
        }
        if (inLiteral) {
            throw reject("unterminated string literal");
        }
        return out.toString();
    }

    private static void rejectUnsafeCharacters(String masked) {
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                throw reject("unsupported characters in the query");
            }
            if (c == '"' || c == '$' || c == '`' || c == '?') {
                throw reject("quoted identifiers, dollar quoting and parameters are not allowed");
            }
        }
    }

    private static List<Tok> tokenize(String masked) {
        List<Tok> toks = new ArrayList<>();
        Matcher m = TOKEN.matcher(masked);
        while (m.find()) {
            toks.add(new Tok(m.group(), m.start(), m.end()));
        }
        return toks;
    }

    private static int[] matchParentheses(List<Tok> toks) {
        int[] match = new int[toks.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < toks.size(); i++) {
            if (toks.get(i).is("(")) {
                stack.push(i);
            } else if (toks.get(i).is(")")) {
                if (stack.isEmpty()) {
                    throw reject("unbalanced parentheses");
                }
                int open = stack.pop();
                match[open] = i;
                match[i] = open;
            }
        }
        if (!stack.isEmpty()) {
            throw reject("unbalanced parentheses");
        }
        return match;
    }

    /** Nesting level of each token; both parentheses of a pair carry the outer level. */
    private static int[] depths(List<Tok> toks) {
        int[] depth = new int[toks.size()];
        int d = 0;
        for (int i = 0; i < toks.size(); i++) {
            if (toks.get(i).is(")")) {
                d--;
            }
            depth[i] = d;
            if (toks.get(i).is("(")) {
                d++;
            }
        }
        return depth;
    }

    // ------------------------------------------------------------------ keyword rules

    private static void rejectForbiddenWords(List<Tok> toks, String masked) {
        for (Tok t : toks) {
            if (!t.isWord()) {
                continue;
            }
            if (FORBIDDEN_WORDS.contains(t.text())) {
                throw reject("'" + t.text().toUpperCase(Locale.ROOT) + "' is not allowed");
            }
            if (FORBIDDEN_PREFIX.matcher(t.text()).matches()) {
                throw reject("'" + t.text() + "' is not allowed");
            }
        }
        if (FOR_LOCK.matcher(masked).find()) {
            throw reject("locking clauses are not allowed");
        }
    }

    // ------------------------------------------------------------------ table allow-list

    private record Cte(String name, int bodyStart, int bodyEnd) {
    }

    /**
     * Parses {@code WITH [RECURSIVE] name [(cols)] AS [NOT] [MATERIALIZED] ( body ) [, ...] SELECT}.
     * Anything that does not fit this shape is rejected rather than guessed at.
     */
    private static List<Cte> parseCtes(List<Tok> toks, int[] match, boolean recursive) {
        List<Cte> ctes = new ArrayList<>();
        int j = recursive ? 2 : 1;
        while (true) {
            if (j >= toks.size() || !toks.get(j).isWord()) {
                throw reject("could not understand the WITH clause");
            }
            String name = toks.get(j).text();
            j++;
            if (j < toks.size() && toks.get(j).is("(")) {
                j = match[j] + 1;
            }
            if (j >= toks.size() || !toks.get(j).is("as")) {
                throw reject("could not understand the WITH clause");
            }
            j++;
            while (j < toks.size() && (toks.get(j).is("not") || toks.get(j).is("materialized"))) {
                j++;
            }
            if (j >= toks.size() || !toks.get(j).is("(")) {
                throw reject("could not understand the WITH clause");
            }
            ctes.add(new Cte(name, j, match[j]));
            j = match[j] + 1;
            if (j < toks.size() && toks.get(j).is(",")) {
                j++;
                continue;
            }
            break;
        }
        if (j >= toks.size() || !toks.get(j).is("select")) {
            throw reject("a WITH clause must be followed by SELECT");
        }
        return ctes;
    }

    /** Checks every FROM/JOIN reference in the statement and returns how many were seen. */
    private static int checkTableReferences(List<Tok> toks, int[] match, int[] depth, List<Cte> ctes, boolean recursive) {
        String[] enclosing = enclosingFunctions(toks);
        int[] refs = {0};
        for (int i = 0; i < toks.size(); i++) {
            Tok t = toks.get(i);
            if (t.is("join")) {
                checkRef(toks, match, i + 1, ctes, recursive, refs);
            } else if (t.is("from")) {
                if (i > 1 && toks.get(i - 1).is("distinct")
                        && (toks.get(i - 2).is("is") || toks.get(i - 2).is("not"))) {
                    continue; // x IS [NOT] DISTINCT FROM y
                }
                boolean nextIsQuery = i + 1 < toks.size()
                        && (toks.get(i + 1).is("select") || toks.get(i + 1).is("with"));
                if (!nextIsQuery && enclosing[i] != null && FROM_FUNCTIONS.contains(enclosing[i])) {
                    continue; // EXTRACT(year FROM d), SUBSTRING(x FROM 2), ...
                }
                checkRef(toks, match, i + 1, ctes, recursive, refs);
                // Comma-separated table list: "FROM a, b" and "FROM a JOIN b ON ..., c".
                for (int k = i + 1; k < toks.size(); k++) {
                    if (depth[k] < depth[i]) {
                        break;
                    }
                    if (depth[k] == depth[i]) {
                        Tok x = toks.get(k);
                        if (x.isWord() && CLAUSE_TERMINATORS.contains(x.text())) {
                            break;
                        }
                        if (x.is(",")) {
                            checkRef(toks, match, k + 1, ctes, recursive, refs);
                        }
                    }
                }
            }
        }
        return refs[0];
    }

    private static void checkRef(List<Tok> toks, int[] match, int j, List<Cte> ctes, boolean recursive, int[] refs) {
        if (j < toks.size() && toks.get(j).is("lateral")) {
            j++;
        }
        if (j >= toks.size()) {
            throw reject("a table name is missing after FROM/JOIN");
        }
        Tok t = toks.get(j);
        if (t.is("(")) {
            int k = j + 1;
            while (k < toks.size() && toks.get(k).is("(")) {
                k++;
            }
            if (k >= toks.size()) {
                throw reject("a table name is missing after FROM/JOIN");
            }
            Tok inner = toks.get(k);
            if (inner.is("select") || inner.is("with") || inner.is("values")) {
                return; // a subquery: its own FROM/JOIN tokens are checked by the main loop
            }
            checkRef(toks, match, k, ctes, recursive, refs); // a parenthesised join group
            return;
        }
        if (!t.isWord()) {
            throw reject("unsupported table expression");
        }
        if (j + 1 < toks.size() && (toks.get(j + 1).is(".") || toks.get(j + 1).is("("))) {
            throw reject("schema-qualified names and functions are not allowed in FROM: " + t.text());
        }
        boolean allowed = ALLOWED_VIEWS.contains(t.text());
        if (!allowed) {
            for (Cte c : ctes) {
                if (c.name().equals(t.text()) && (j > c.bodyEnd() || (recursive && j > c.bodyStart()))) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            throw reject("'" + t.text() + "' is not an allowed view; use only " + String.join(", ", sortedViews()));
        }
        refs[0]++;
    }

    /** For each token, the name of the function whose parentheses directly enclose it (or null). */
    private static String[] enclosingFunctions(List<Tok> toks) {
        String[] out = new String[toks.size()];
        Deque<String> stack = new ArrayDeque<>();
        for (int i = 0; i < toks.size(); i++) {
            Tok t = toks.get(i);
            out[i] = stack.isEmpty() ? null : stack.peek();
            if (t.is("(")) {
                stack.push(i > 0 && toks.get(i - 1).isWord() ? toks.get(i - 1).text() : "");
            } else if (t.is(")") && !stack.isEmpty()) {
                stack.pop();
            }
        }
        return out;
    }

    private static List<String> sortedViews() {
        return ALLOWED_VIEWS.stream().sorted().toList();
    }

    // ------------------------------------------------------------------ row cap

    private String applyLimit(String sql, List<Tok> toks, int[] depth) {
        int limitIdx = -1;
        for (int i = 0; i < toks.size(); i++) {
            if (depth[i] == 0 && toks.get(i).is("limit")) {
                limitIdx = i;
            }
        }
        if (limitIdx < 0) {
            return sql + " LIMIT " + maxRows;
        }
        if (limitIdx + 1 >= toks.size()) {
            throw reject("LIMIT needs a number");
        }
        Tok arg = toks.get(limitIdx + 1);
        String replacement;
        if (arg.is("all")) {
            replacement = String.valueOf(maxRows);
        } else if (DIGITS.matcher(arg.text()).matches()) {
            BigInteger requested = new BigInteger(arg.text());
            if (requested.compareTo(BigInteger.valueOf(maxRows)) <= 0) {
                return sql;
            }
            replacement = String.valueOf(maxRows);
        } else {
            throw reject("LIMIT must be a plain number");
        }
        return sql.substring(0, arg.start()) + replacement + sql.substring(arg.end());
    }

    private static BusinessException reject(String reason) {
        return new BusinessException("Query rejected: " + reason + ".");
    }

    private record Tok(String text, int start, int end) {
        boolean is(String word) {
            return text.equals(word);
        }

        boolean isWord() {
            char c = text.charAt(0);
            return (c >= 'a' && c <= 'z') || c == '_';
        }
    }
}
