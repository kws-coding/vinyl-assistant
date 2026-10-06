package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordsCsvTest {

    @TempDir
    Path dir;

    private List<RecordInput> read(String csv) throws Exception {
        Path f = dir.resolve("records.csv");
        Files.writeString(f, csv);
        return RecordsCsv.read(f);
    }

    @Test
    void theOptionalEbayColumnsMayBeAbsent() throws Exception {
        RecordInput r = read("id,bucket,media_grade,truth_release_id,ebay_sold_avg,notes\n001,easy,VG+,123,,\n").get(0);
        assertNull(r.ebaySoldHigh());
        assertEquals("", r.ebayMatch());
        assertNull(r.ebaySoldAvg());
    }

    @Test
    void theEbayColumnsAreReadWhenPresent() throws Exception {
        // made-up values
        RecordInput r = read("id,ebay_sold_avg,ebay_sold_high,ebay_match\n001,25,$90.50,same\n").get(0);
        assertEquals(new BigDecimal("25"), r.ebaySoldAvg());
        assertEquals(new BigDecimal("90.50"), r.ebaySoldHigh());
        assertEquals("same", r.ebayMatch());
    }

    @Test
    void anInvalidMatchOrNumberNamesTheRecord() {
        IllegalArgumentException m = assertThrows(IllegalArgumentException.class,
                () -> read("id,ebay_match\n007,maybe\n"));
        assertTrue(m.getMessage().contains("007"));
        IllegalArgumentException n = assertThrows(IllegalArgumentException.class,
                () -> read("id,ebay_sold_high\n008,lots\n"));
        assertTrue(n.getMessage().contains("008"));
    }
}
