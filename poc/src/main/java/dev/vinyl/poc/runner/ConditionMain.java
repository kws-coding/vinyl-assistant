package dev.vinyl.poc.runner;

import dev.vinyl.poc.vision.ConditionCache;
import dev.vinyl.poc.vision.ConditionClient;
import dev.vinyl.poc.vision.ConditionResult;
import dev.vinyl.poc.vision.ImageResizer;
import dev.vinyl.poc.vision.VisionConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Throwaway. The condition experiment. Usage: ConditionMain [--root DIR] [--only ID] [--refresh]
 *   ConditionMain --summary [--root DIR] [--report FILE] [--review FILE]
 * Reads photos from records/NNN/condition/ (vinyl surface under a lamp, sleeve). The model sees only those photos,
 * never your grade, notes or known defects, and suggests a visual-only grade that you can override. Lock your grades
 * in records.csv before running so neither side influences the other. POC_MAX_CONDITION_PHOTOS (default 8) caps
 * photos per record; a record over the cap is reported as failed, not sent. Needs VINYL_ANTHROPIC_KEY (paid calls).
 * --summary makes no calls and costs nothing.
 */
public class ConditionMain {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".");
        String only = null;
        boolean refresh = false;
        boolean summary = false;
        Path reportFile = null;
        Path reviewFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> root = Path.of(args[++i]);
                case "--only" -> only = args[++i];
                case "--refresh" -> refresh = true;
                case "--summary" -> summary = true;
                case "--report" -> reportFile = Path.of(args[++i]);
                case "--review" -> reviewFile = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (summary) {
            summarise(root, reportFile, reviewFile);
            return;
        }

        String capEnv = System.getenv("POC_MAX_CONDITION_PHOTOS");
        int maxPhotos = capEnv == null || capEnv.isBlank() ? 8 : Integer.parseInt(capEnv.trim());
        VisionConfig vcfg = VisionConfig.fromEnvironment();
        CallLog log = new CallLog();
        ConditionClient client = ConditionClient.fromEnvironment(vcfg, log::ai);
        ConditionCache cache = new ConditionCache(root.resolve("out/condition-cache"));

        List<List<String>> report = new ArrayList<>();
        List<List<String>> review = new ArrayList<>();
        for (RecordInput rec : RecordsCsv.read(root.resolve("records.csv"))) {
            if (only != null && !only.equals(rec.id())) {
                continue;
            }
            Path dir = root.resolve("records").resolve(rec.id()).resolve("condition");
            if (!Files.isDirectory(dir)) {
                System.out.println(rec.id() + "  no condition folder, skipped");
                continue;
            }
            log.setRecord(rec.id());
            try {
                ConditionResult result = refresh ? null : cache.get(rec.id()).orElse(null);
                boolean cached = result != null;
                if (!cached) {
                    List<Path> photos = ImageResizer.listPhotos(dir);
                    if (photos.isEmpty()) {
                        throw new IOException("No photos found in " + dir);
                    }
                    checkCap(photos.size(), maxPhotos);
                    List<Path> jpegs = ImageResizer.resize(photos, root.resolve("out/resized-condition").resolve(rec.id()),
                            vcfg.maxImageEdge());
                    result = client.assess(jpegs);
                    cache.put(rec.id(), result);
                }
                report.add(ConditionReport.row(rec, result, cached));
                review.addAll(ConditionReport.reviewRows(rec, result));
                System.out.printf("%s  media: suggested %s (yours %s)  sleeve: suggested %s (yours %s)  cost=$%s%n", rec.id(),
                        result.media().grade(), blank(rec.mediaGrade()), result.sleeve().grade(), blank(rec.sleeveGrade()),
                        cached ? "0" : result.call().costUsd().toPlainString());
            } catch (Exception e) {
                System.out.println(rec.id() + "  FAILED: " + e.getMessage());
            }
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        write(root.resolve("reports/condition-" + stamp + ".csv"), ConditionReport.HEADER, report);
        write(root.resolve("reports/condition-review-" + stamp + ".csv"), ConditionReport.REVIEW_HEADER, review);
        log.write(root.resolve("logs/condition-calls-" + stamp + ".csv"));
        System.out.println("Report: reports/condition-" + stamp + ".csv");
        System.out.println("Review sheet to mark: reports/condition-review-" + stamp + ".csv");
        System.out.println(ConditionReport.LIMIT_NOTE);
    }

    static void checkCap(int photos, int max) throws IOException {
        if (photos > max) {
            throw new IOException("Not sent to the vision model: " + photos + " condition photos is over the limit of "
                    + max + " (POC_MAX_CONDITION_PHOTOS). Remove photos or raise the limit.");
        }
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() ? "(blank)" : s;
    }

    private static void write(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(Csv.join(header));
        rows.forEach(r -> lines.add(Csv.join(r)));
        Files.write(file, lines);
    }

    private static void summarise(Path root, Path reportFile, Path reviewFile) throws IOException {
        Path report = reportFile != null ? reportFile : newest(root.resolve("reports"), "condition-\\d{8}-\\d{6}\\.csv");
        Path review = reviewFile != null ? reviewFile : newest(root.resolve("reports"), "condition-review-.*\\.csv");
        System.out.println("Report: " + report + "\nReview: " + review);
        System.out.print(ConditionSummary.render(read(report), read(review)));
    }

    private static Path newest(Path dir, String regex) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().matches(regex)).sorted().reduce((a, b) -> b)
                    .orElseThrow(() -> new IOException("No file matching " + regex + " in " + dir));
        }
    }

    private static List<Map<String, String>> read(Path file) throws IOException {
        List<List<String>> table = Csv.parse(Files.readString(file));
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
}
