package com.shoppulse.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Masks personal data before result rows are sent to the model.
 *
 * <p>The merchant is entitled to see their own customers' email addresses, so the API response
 * is unchanged. What leaves the building is a different question: a model-written query may
 * legitimately select {@code v_customer_summary.email}, and there is no reason for those
 * addresses to reach a third-party inference provider just so it can phrase a sentence.
 * The model only ever needs the shape of the data, so emails are masked on the way out.
 */
final class PiiMasker {

    private PiiMasker() {
    }

    /** True for column names that hold personal data we refuse to send to the model. */
    static boolean isSensitive(String column) {
        if (column == null) {
            return false;
        }
        String c = column.toLowerCase(Locale.ROOT);
        return c.contains("email") || c.contains("password");
    }

    /** Returns a copy of {@code rows} with every sensitive column masked. */
    static List<List<Object>> maskRows(List<String> columns, List<List<Object>> rows) {
        if (columns == null || rows == null) {
            return rows;
        }
        boolean[] sensitive = new boolean[columns.size()];
        boolean any = false;
        for (int i = 0; i < columns.size(); i++) {
            sensitive[i] = isSensitive(columns.get(i));
            any |= sensitive[i];
        }
        if (!any) {
            return rows;
        }
        List<List<Object>> masked = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            List<Object> copy = new ArrayList<>(row);
            for (int i = 0; i < copy.size() && i < sensitive.length; i++) {
                if (sensitive[i]) {
                    copy.set(i, mask(copy.get(i)));
                }
            }
            masked.add(copy);
        }
        return masked;
    }

    /** {@code jane.doe@example.com} becomes {@code j***@example.com}; anything else becomes {@code ***}. */
    static Object mask(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        int at = s.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return s.charAt(0) + "***" + s.substring(at);
    }
}
