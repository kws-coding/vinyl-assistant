package dev.vinyl.poc.runner;

import java.math.BigDecimal;

/** One row of records.csv. Optional numbers are null when blank. ebayMatch is same, unsure, no or blank.
 * listingNotes is the user's own words for the Discogs comment (for example how it plays); blank if none. */
public record RecordInput(String id, String bucket, String mediaGrade, String sleeveGrade, String knownDefects,
                          BigDecimal costBasis, String truthReleaseId, BigDecimal ebaySoldAvg,
                          BigDecimal ebaySoldHigh, String ebayMatch, String notes, String listingNotes) {
}
