package dev.vinyl.poc.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes the report CSV. Ends with the attribution line Discogs' terms require. Throwaway. */
public final class ReportWriter {

    public static final String ATTRIBUTION = "# Data provided by Discogs (https://www.discogs.com). "
            + "Pricing is an estimate; actual label cost is recorded after the sale.";

    private ReportWriter() {
    }

    public static void write(Path file, List<ReportRow> rows) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(Csv.join(ReportRow.HEADER));
        rows.forEach(r -> lines.add(Csv.join(r.fields())));
        lines.add(ATTRIBUTION);
        Files.write(file, lines);
    }
}
