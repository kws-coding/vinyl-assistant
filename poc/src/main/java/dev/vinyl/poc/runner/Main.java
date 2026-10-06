package dev.vinyl.poc.runner;

import dev.vinyl.poc.discogs.DiscogsClient;
import dev.vinyl.poc.domain.ProfitSettings;
import dev.vinyl.poc.domain.ScoringWeights;
import dev.vinyl.poc.domain.Thresholds;
import dev.vinyl.poc.vision.VisionCache;
import dev.vinyl.poc.vision.VisionClient;
import dev.vinyl.poc.vision.VisionConfig;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Throwaway runner. Usage: Main [--root DIR] [--only ID] [--refresh]
 * DIR holds records.csv and records/NNN/ folders (default: current directory). Needs VINYL_ANTHROPIC_KEY and
 * DISCOGS_TOKEN in the environment; POC_POSTAGE (real label cost) is optional; without it profit uses a placeholder of 5.50 and says so.
 * POC_MAX_PHOTOS (default 11) caps photos per record; a record over the cap is reported as failed, not sent.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".");
        String only = null;
        boolean refresh = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> root = Path.of(args[++i]);
                case "--only" -> only = args[++i];
                case "--refresh" -> refresh = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        VisionConfig vcfg = VisionConfig.fromEnvironment();
        String postageEnv = System.getenv("POC_POSTAGE");
        boolean postagePlaceholder = postageEnv == null || postageEnv.isBlank();
        // 5.50 is the user's example for the POC; their real label cost is usually a bit less.
        BigDecimal postage = new BigDecimal(postagePlaceholder ? "5.50" : postageEnv.trim());
        String maxPhotosEnv = System.getenv("POC_MAX_PHOTOS");
        int maxPhotos = maxPhotosEnv == null || maxPhotosEnv.isBlank() ? 11 : Integer.parseInt(maxPhotosEnv.trim());
        PipelineSettings settings = new PipelineSettings(15, vcfg.maxImageEdge(), maxPhotos, ScoringWeights.defaults(),
                Thresholds.defaults(), ProfitSettings.discogs(postage), ProfitSettings.defaults(postage),
                postagePlaceholder);

        CallLog log = new CallLog();
        VisionClient vision = VisionClient.fromEnvironment(vcfg, log::ai);
        DiscogsClient discogs = DiscogsClient.fromEnvironment(log::discogs);
        RecordPipeline pipeline = new RecordPipeline(vision, new VisionCache(root.resolve("out/vision-cache")),
                discogs, settings, root.resolve("out/resized"), refresh);

        List<ReportRow> rows = new ArrayList<>();
        for (RecordInput rec : RecordsCsv.read(root.resolve("records.csv"))) {
            if (only != null && !only.equals(rec.id())) {
                continue;
            }
            log.setRecord(rec.id());
            try {
                ReportRow row = pipeline.run(rec, root.resolve("records").resolve(rec.id()));
                rows.add(row);
                System.out.printf("%s  %s  top=%s score=%s outcome=%s cost=$%s%n", row.recordId(), row.decision(),
                        row.topReleaseId(), row.topScore(), row.outcome(), row.aiCostUsd());
            } catch (Exception e) {
                // A failed record is reported, not hidden, and does not stop the run. Messages hold no secrets.
                System.out.println(rec.id() + "  FAILED: " + e.getMessage());
            }
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path report = root.resolve("reports/report-" + stamp + ".csv");
        Path calls = root.resolve("logs/calls-" + stamp + ".csv");
        ReportWriter.write(report, rows);
        log.write(calls);
        System.out.println("Report: " + report);
        System.out.println("Call log (" + log.size() + " calls): " + calls);
        System.out.println(ReportWriter.ATTRIBUTION);
    }
}
