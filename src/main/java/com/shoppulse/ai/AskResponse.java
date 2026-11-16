package com.shoppulse.ai;

import java.util.List;

/** Everything the dashboard needs to show an answer, the query behind it and a chart. */
public record AskResponse(
        String question,
        String answer,
        String sql,
        List<String> columns,
        List<List<Object>> rows,
        String chartType,
        String chartLabelColumn,
        String chartValueColumn,
        boolean aiGenerated,
        String note) {
}
