package com.shoppulse.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic plain-English summary of query rows: the answer when no model is available and the
 * safety net when the second (summarising) model call fails.
 */
final class RowSummarizer {

    private RowSummarizer() {
    }

    static String summarize(List<String> columns, List<List<Object>> rows, String labelColumn, String valueColumn) {
        if (rows == null || rows.isEmpty()) {
            return "No matching data was found for that question.";
        }
        if (rows.size() == 1) {
            StringBuilder sb = new StringBuilder("Result: ");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(columns.get(i).replace('_', ' ')).append(" = ").append(format(columns.get(i), rows.get(0).get(i)));
            }
            return sb.append('.').toString();
        }
        int valueIdx = indexOf(columns, valueColumn);
        if (valueIdx < 0) {
            valueIdx = firstNumericColumn(rows);
        }
        int labelIdx = indexOf(columns, labelColumn);
        if (labelIdx < 0 || labelIdx == valueIdx) {
            labelIdx = 0;
        }
        if (valueIdx < 0 || labelIdx == valueIdx) {
            return "Found " + rows.size() + " rows.";
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Object> max = null;
        List<Object> min = null;
        BigDecimal maxV = null;
        BigDecimal minV = null;
        for (List<Object> row : rows) {
            BigDecimal v = toDecimal(row.get(valueIdx));
            if (v == null) {
                continue;
            }
            total = total.add(v);
            if (maxV == null || v.compareTo(maxV) > 0) {
                maxV = v;
                max = row;
            }
            if (minV == null || v.compareTo(minV) < 0) {
                minV = v;
                min = row;
            }
        }
        if (max == null) {
            return "Found " + rows.size() + " rows.";
        }
        String valueName = columns.get(valueIdx);
        return "Found %d rows. Highest %s: %s (%s). Lowest: %s (%s). Combined %s: %s."
                .formatted(rows.size(), valueName.replace('_', ' '), max.get(labelIdx), format(valueName, max.get(valueIdx)),
                        min.get(labelIdx), format(valueName, min.get(valueIdx)),
                        valueName.replace('_', ' '), format(valueName, total));
    }

    static String format(String column, Object value) {
        if (value == null) {
            return "none";
        }
        BigDecimal d = value instanceof Number ? toDecimal(value) : null;
        if (d == null) {
            return String.valueOf(value);
        }
        String name = column.toLowerCase(Locale.ROOT);
        boolean money = name.contains("revenue") || name.contains("price") || name.contains("avg_order") || name.contains("value");
        if (money) {
            return String.format(Locale.US, "$%,.2f", d);
        }
        return d.stripTrailingZeros().scale() <= 0
                ? String.format(Locale.US, "%,d", d.setScale(0, RoundingMode.HALF_UP).toBigInteger())
                : d.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static int indexOf(List<String> columns, String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    static int firstNumericColumn(List<List<Object>> rows) {
        List<Object> first = rows.get(0);
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i) instanceof Number) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal toDecimal(Object o) {
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return null;
    }
}
