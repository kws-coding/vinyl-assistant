package dev.vinyl.poc.runner;

import java.math.BigDecimal;

/** One row of records.csv. Optional numbers are null when blank. */
public record RecordInput(String id, String bucket, String mediaGrade, String sleeveGrade, String knownDefects,
                          BigDecimal costBasis, String truthReleaseId, BigDecimal ebaySoldAvg, String notes) {
}
