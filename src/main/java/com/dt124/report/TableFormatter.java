package com.dt124.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal console table formatter. Produces aligned, readable tables for the live demo.
 */
public final class TableFormatter {

    private TableFormatter() {
    }

    public static String table(String[] headers, List<String[]> rows) {
        int columns = headers.length;
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < columns && i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i] == null ? 0 : row[i].length());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(separator(widths)).append('\n');
        sb.append(formatRow(headers, widths)).append('\n');
        sb.append(separator(widths)).append('\n');
        for (String[] row : rows) {
            sb.append(formatRow(row, widths)).append('\n');
        }
        sb.append(separator(widths)).append('\n');
        return sb.toString();
    }

    public static String section(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("=".repeat(Math.max(60, title.length() + 4))).append('\n');
        sb.append("== ").append(title).append('\n');
        sb.append("=".repeat(Math.max(60, title.length() + 4))).append('\n');
        return sb.toString();
    }

    private static String separator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int width : widths) {
            sb.append("-".repeat(width + 2)).append('+');
        }
        return sb.toString();
    }

    private static String formatRow(String[] row, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String value = i < row.length && row[i] != null ? row[i] : "";
            sb.append(' ').append(pad(value, widths[i])).append(' ').append('|');
        }
        return sb.toString();
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    public static List<String[]> rows(List<String[]> source) {
        return new ArrayList<>(source);
    }
}
