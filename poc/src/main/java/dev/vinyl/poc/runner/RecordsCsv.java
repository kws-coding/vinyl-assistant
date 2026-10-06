package dev.vinyl.poc.runner;

import dev.vinyl.poc.domain.EbayMatch;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads records.csv: id,bucket,media_grade,sleeve_grade,known_defects,cost_basis,truth_release_id,ebay_sold_avg,notes; optional: ebay_sold_high, ebay_match (same, unsure, no) */
public final class RecordsCsv {

    private RecordsCsv() {
    }

    public static List<RecordInput> read(Path file) throws IOException {
        List<List<String>> rows = Csv.parse(Files.readString(file));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            col.put(rows.get(0).get(i).trim().toLowerCase(), i);
        }
        List<RecordInput> out = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            String id = get(r, col, "id");
            if (id.isEmpty()) {
                continue;
            }
            out.add(new RecordInput(id, get(r, col, "bucket"), get(r, col, "media_grade"),
                    get(r, col, "sleeve_grade"), get(r, col, "known_defects"),
                    number(id, "cost_basis", get(r, col, "cost_basis")), get(r, col, "truth_release_id"),
                    number(id, "ebay_sold_avg", get(r, col, "ebay_sold_avg")),
                    number(id, "ebay_sold_high", get(r, col, "ebay_sold_high")),
                    match(id, get(r, col, "ebay_match")), get(r, col, "notes")));
        }
        return out;
    }

    private static String get(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i >= row.size() ? "" : row.get(i).trim();
    }

    private static String match(String id, String v) {
        try {
            EbayMatch.parse(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Record " + id + ": " + e.getMessage());
        }
        return v;
    }

    private static BigDecimal number(String id, String name, String v) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(v.replace("$", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Record " + id + ": " + name + " is not a number: " + v);
        }
    }
}
