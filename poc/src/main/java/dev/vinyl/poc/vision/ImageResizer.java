package dev.vinyl.poc.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Throwaway helper: converts photos (including HEIC) to capped JPEGs with macOS sips, under neutral names
 * so file names never reach the model. Originals are only read.
 */
public class ImageResizer {

    private static final Set<String> EXTENSIONS = Set.of("heic", "jpg", "jpeg", "png");

    public static List<Path> listPhotos(Path recordDir) throws IOException {
        try (Stream<Path> s = Files.list(recordDir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> EXTENSIONS.contains(extension(p)))
                    .sorted().toList();
        }
    }

    /** Writes p01.jpg, p02.jpg, ... into outDir and returns them in the same order as the inputs. */
    public static List<Path> resize(List<Path> photos, Path outDir, int maxEdge) throws IOException {
        Files.createDirectories(outDir);
        java.util.ArrayList<Path> out = new java.util.ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            Path target = outDir.resolve(String.format("p%02d.jpg", i + 1));
            Process p = new ProcessBuilder("sips", "-s", "format", "jpeg", "-s", "formatOptions", "80",
                    "-Z", String.valueOf(maxEdge), photos.get(i).toString(), "--out", target.toString())
                    .redirectErrorStream(true).start();
            try {
                p.getInputStream().readAllBytes();
                if (p.waitFor() != 0 || !Files.exists(target)) {
                    throw new IOException("sips failed for photo " + (i + 1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while resizing", e);
            }
            out.add(target);
        }
        return out;
    }

    private static String extension(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
