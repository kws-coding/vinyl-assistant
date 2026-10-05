package dev.vinyl.poc.vision;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageResizerTest {

    @TempDir
    Path tmp;

    @Test
    void listsOnlyPhotosAndIgnoresOtherFiles() throws IOException {
        Files.write(tmp.resolve("IMG_1.HEIC"), new byte[1]);
        Files.write(tmp.resolve("b.jpg"), new byte[1]);
        Files.write(tmp.resolve("notes.txt"), new byte[1]);
        assertEquals(2, ImageResizer.listPhotos(tmp).size());
    }

    @Test
    void resizesToTheCapUnderNeutralNamesAndLeavesTheOriginal() throws IOException {
        assumeTrue(Files.exists(Path.of("/usr/bin/sips")), "sips (macOS) not available");
        Path original = tmp.resolve("IMG_4021.png");
        ImageIO.write(new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB), "png", original.toFile());
        long before = Files.size(original);

        List<Path> out = ImageResizer.resize(List.of(original), tmp.resolve("out"), 1000);

        assertEquals("p01.jpg", out.get(0).getFileName().toString());
        BufferedImage img = ImageIO.read(out.get(0).toFile());
        assertEquals(1000, Math.max(img.getWidth(), img.getHeight()));
        assertEquals(before, Files.size(original));
    }
}
