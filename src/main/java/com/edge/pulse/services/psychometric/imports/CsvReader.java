package com.edge.pulse.services.psychometric.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal RFC-4180 CSV reader: first row is the header; returns one map per data row. No external deps. */
public final class CsvReader {
    private CsvReader() {}

    public static List<Map<String, String>> parse(String content) {
        if (content == null) return List.of();
        if (content.startsWith("﻿")) content = content.substring(1); // strip BOM
        List<String[]> rows = splitRows(content);
        List<Map<String, String>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        String[] header = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                map.put(header[c].trim(), c < cells.length ? cells[c] : "");
            }
            out.add(map);
        }
        return out;
    }

    private static List<String[]> splitRows(String content) {
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false, rowHasContent = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuotes = false;
                } else cur.append(ch);
            } else {
                switch (ch) {
                    case '"' -> { inQuotes = true; rowHasContent = true; }
                    case ',' -> { fields.add(cur.toString()); cur.setLength(0); rowHasContent = true; }
                    case '\r' -> { /* ignore; handled by \n */ }
                    case '\n' -> {
                        fields.add(cur.toString()); cur.setLength(0);
                        if (rowHasContent || fields.size() > 1 || !fields.get(0).isEmpty())
                            rows.add(fields.toArray(new String[0]));
                        fields.clear(); rowHasContent = false;
                    }
                    default -> { cur.append(ch); rowHasContent = true; }
                }
            }
        }
        if (cur.length() > 0 || !fields.isEmpty()) {
            fields.add(cur.toString());
            if (rowHasContent || fields.size() > 1 || !fields.get(0).isEmpty())
                rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }
}
