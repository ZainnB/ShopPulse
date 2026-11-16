package com.shoppulse.ai;

import java.util.List;

/** Column names plus JSON-friendly row values returned by the read-only executor. */
public record QueryResult(List<String> columns, List<List<Object>> rows) {
}
