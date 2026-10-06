package dev.vinyl.poc.runner;

import dev.vinyl.poc.vision.ImageResizer;
import dev.vinyl.poc.vision.PhotoRole;
import dev.vinyl.poc.vision.VisionCache;
import dev.vinyl.poc.vision.VisionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Throwaway. Usage: SummaryMain [--root DIR] [--report FILE] [--exclude IDS] [--high-value USD]
 * Reads a report CSV (default: the newest in DIR/reports) and prints the measures. Record 001 is excluded by
 * default, as in THRESHOLDS.md; use --exclude none to include everything. Makes no API calls and costs nothing:
 * photo roles are read from the vision cache and the file names on disk.
 */
public class SummaryMain {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".");
        Path report = null;
        String exclude = "001";
        double highValue = 25;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> root = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--exclude" -> exclude = args[++i];
                case "--high-value" -> highValue = Double.parseDouble(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (report == null) {
            report = newestReport(root.resolve("reports"));
        }
        Set<String> excluded = exclude.equalsIgnoreCase("none") ? Set.of()
                : Arrays.stream(exclude.split(",")).map(String::trim).collect(Collectors.toSet());

        List<Map<String, String>> rows = readRows(report).stream()
                .filter(r -> !excluded.contains(r.getOrDefault("record_id", "").trim())).toList();

        PhotoRoleScorer.Totals roles = null;
        Path rolesFile = root.resolve("photo_roles.csv");
        if (Files.exists(rolesFile)) {
            roles = scoreRoles(root, rolesFile, rows);
        }

        System.out.println("Report: " + report + (excluded.isEmpty() ? "" : "  (excluding " + excluded + ")"));
        System.out.print(Summary.render(rows, highValue, roles));
        System.out.println(ReportWriter.ATTRIBUTION);
    }

    private static Path newestReport(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().matches("report-.*\\.csv")).sorted().reduce((a, b) -> b)
                    .orElseThrow(() -> new IOException("No report-*.csv in " + dir));
        }
    }

    /** Skips the trailing attribution line and any other line starting with #. */
    static List<Map<String, String>> readRows(Path report) throws IOException {
        String text = Files.readString(report).replaceAll("(?m)^#.*$", "");
        List<List<String>> table = Csv.parse(text);
        List<Map<String, String>> out = new ArrayList<>();
        if (table.isEmpty()) {
            return out;
        }
        List<String> header = table.get(0).stream().map(String::trim).toList();
        for (List<String> r : table.subList(1, table.size())) {
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < header.size() && i < r.size(); i++) {
                m.put(header.get(i), r.get(i));
            }
            out.add(m);
        }
        return out;
    }

    private static PhotoRoleScorer.Totals scoreRoles(Path root, Path rolesFile, List<Map<String, String>> rows)
            throws IOException {
        Map<String, Map<String, PhotoRole>> truth = PhotoRolesCsv.read(rolesFile);
        VisionCache cache = new VisionCache(root.resolve("out/vision-cache"));
        PhotoRoleScorer.Totals total = PhotoRoleScorer.Totals.empty();
        for (Map<String, String> row : rows) {
            String id = row.getOrDefault("record_id", "").trim();
            Map<String, PhotoRole> roles = truth.get(id);
            VisionResult read = cache.get(id).orElse(null);
            if (roles == null || read == null) {
                continue;
            }
            List<String> names = ImageResizer.listPhotos(root.resolve("records").resolve(id)).stream()
                    .map(p -> p.getFileName().toString()).toList();
            total = total.plus(PhotoRoleScorer.score(id, names, read.photos(), roles));
        }
        return total;
    }
}
