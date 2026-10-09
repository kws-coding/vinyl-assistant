package dev.vinyl.poc.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Compares what was read from the photos with one candidate release and returns one piece of evidence per
 * identifier. Rules are deliberately forgiving about formatting and partial reads, and strict about content.
 */
public class EvidenceBuilder {

    private static final int MIN_MATRIX_CHARS = 5;
    private static final int MATRIX_FUZZY_FROM = 6;
    private static final int MIN_BARCODE_DIGITS = 8;
    private static final int MIN_DISTINGUISHING_CHARS = 4;
    private static final int MIN_CATALOG_RUN = 4;
    /** Discogs release countries that say nothing about where a copy was manufactured. */
    private static final Set<String> BROAD_COUNTRIES = Set.of("worldwide", "europe");

    public List<IdentifierEvidence> build(ExtractedFacts facts, ReleaseInfo release) {
        return List.of(
                barcode(facts, release),
                catalogNumber(facts, release),
                label(facts, release),
                country(facts, release),
                format(facts, release),
                matrix(facts, release));
    }

    private IdentifierEvidence barcode(ExtractedFacts facts, ReleaseInfo r) {
        List<String> candidates = r.barcodes().stream()
                .filter(b -> b.matches("[0-9 \\-]+"))
                .map(EvidenceBuilder::digitsWithoutLeadingZeros)
                .filter(d -> d.length() >= MIN_BARCODE_DIGITS - 1)
                .toList();
        List<Fact> observed = facts.barcodes().stream()
                .filter(f -> digitsWithoutLeadingZeros(f.value()).length() >= MIN_BARCODE_DIGITS - 1).toList();
        IdentifierEvidence result = compare(Identifier.BARCODE, observed, candidates,
                (o, c) -> {
                    String digits = digitsWithoutLeadingZeros(o);
                    return digits.equals(c) || differsOnlyByCheckDigit(digits, c);
                });
        if (result.outcome() == Outcome.MISMATCH && isFragmentOfListed(observed, candidates)) {
            return IdentifierEvidence.missing(Identifier.BARCODE);
        }
        return result;
    }

    /**
     * The printed number often leaves out the check digit that Discogs lists (or the other way round). One side
     * matches the other when it is the same digits plus the correct final EAN/UPC check digit.
     */
    static boolean differsOnlyByCheckDigit(String a, String b) {
        String shorter = a.length() <= b.length() ? a : b;
        String longer = shorter == a ? b : a;
        if (longer.length() != shorter.length() + 1 || shorter.length() > 12 || !longer.startsWith(shorter)) {
            return false;
        }
        String padded = "0".repeat(12 - shorter.length()) + shorter;
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (padded.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10 == longer.charAt(longer.length() - 1) - '0';
    }

    /** A truncated read is a piece of the real barcode, so it neither confirms nor contradicts it. */
    private static boolean isFragmentOfListed(List<Fact> observed, List<String> candidates) {
        return observed.stream().map(f -> digitsWithoutLeadingZeros(f.value()))
                .anyMatch(o -> candidates.stream().anyMatch(c -> c.length() > o.length() && c.contains(o)));
    }

    private IdentifierEvidence catalogNumber(ExtractedFacts facts, ReleaseInfo r) {
        List<String> candidates = r.catalogNumbers().stream().map(FuzzyText::alnum).filter(s -> !s.isEmpty()).toList();
        return compare(Identifier.CATALOG_NUMBER, readable(facts.catalogNumbers()), candidates,
                (o, c) -> FuzzyText.alnum(o).equals(c));
    }

    private IdentifierEvidence label(ExtractedFacts facts, ReleaseInfo r) {
        List<String> candidates = r.labels().stream().map(FuzzyText::alnum).filter(s -> !s.isEmpty()).toList();
        return compare(Identifier.LABEL, readable(facts.labels()), candidates, (o, c) -> {
            String a = FuzzyText.alnum(o);
            return !a.isEmpty() && (a.equals(c) || c.contains(a) || a.contains(c));
        });
    }

    private IdentifierEvidence country(ExtractedFacts facts, ReleaseInfo r) {
        String country = r.country() == null ? "" : r.country().trim().toLowerCase(Locale.ROOT);
        if (country.isEmpty() || BROAD_COUNTRIES.contains(country)) {
            return IdentifierEvidence.missing(Identifier.COUNTRY);
        }
        List<String> candidates = List.of(country.split("\\s*[&,/]\\s*")).stream()
                .map(EvidenceBuilder::countryAlias).toList();
        return compare(Identifier.COUNTRY, readable(facts.countries()), candidates,
                (o, c) -> countryAlias(o.toLowerCase(Locale.ROOT)).equals(c));
    }

    private IdentifierEvidence format(ExtractedFacts facts, ReleaseInfo r) {
        String text = String.join(" ", r.formatDescriptions()).toLowerCase(Locale.ROOT);
        boolean stereo = text.contains("stereo");
        boolean mono = text.contains("mono");
        for (Fact f : facts.formats()) {
            String v = f.value().toLowerCase(Locale.ROOT);
            if (v.contains("stereo")) {
                if (stereo) {
                    return IdentifierEvidence.match(Identifier.FORMAT, f.rawText());
                }
                if (mono) {
                    return IdentifierEvidence.mismatch(Identifier.FORMAT, f.rawText());
                }
            } else if (v.contains("mono")) {
                if (mono) {
                    return IdentifierEvidence.match(Identifier.FORMAT, f.rawText());
                }
                if (stereo) {
                    return IdentifierEvidence.mismatch(Identifier.FORMAT, f.rawText());
                }
            }
        }
        return IdentifierEvidence.missing(Identifier.FORMAT);
    }

    /**
     * Partial and slightly misread etchings are expected. A fragment of at least five characters matches when
     * it appears in a listed runout, allowing one misread character from six characters up. It is a mismatch
     * only when the candidate lists runouts and none fit, which sends the record to a person.
     */
    private IdentifierEvidence matrix(ExtractedFacts facts, ReleaseInfo r) {
        List<String> candidates = r.matrices().stream().map(FuzzyText::alnum).filter(s -> !s.isEmpty()).toList();
        List<String> catalogNumbers = r.catalogNumbers().stream().map(FuzzyText::alnum).toList();
        List<Fact> observed = facts.matrices().stream()
                .filter(f -> FuzzyText.alnum(f.value()).length() >= MIN_MATRIX_CHARS)
                .filter(f -> distinguishingChars(FuzzyText.alnum(f.value()), catalogNumbers) >= MIN_DISTINGUISHING_CHARS)
                .toList();
        return compare(Identifier.MATRIX, observed, candidates, (o, c) -> {
            String fragment = FuzzyText.alnum(o);
            int errors = fragment.length() >= MATRIX_FUZZY_FROM ? 1 : 0;
            return FuzzyText.approxContains(c, fragment, errors);
        });
    }

    /**
     * A read that is mostly the catalog number (a label rim, or the start of a stamper code) is on every pressing
     * of the release, so it cannot tell them apart. Counts the characters left after removing runs of four or
     * more that appear in a catalog number.
     */
    static int distinguishingChars(String fragment, List<String> catalogNumbers) {
        String rest = fragment;
        for (String cat : catalogNumbers) {
            for (int len = Math.min(rest.length(), cat.length()); len >= MIN_CATALOG_RUN; len--) {
                boolean removed = false;
                for (int i = 0; i + len <= rest.length() && !removed; i++) {
                    if (cat.contains(rest.substring(i, i + len))) {
                        rest = rest.substring(0, i) + rest.substring(i + len);
                        removed = true;
                    }
                }
                if (removed) {
                    break;
                }
            }
        }
        return rest.length();
    }

    private static IdentifierEvidence compare(Identifier id, List<Fact> observed, List<String> candidates,
                                              BiPredicate<String, String> same) {
        if (observed.isEmpty() || candidates.isEmpty()) {
            return IdentifierEvidence.missing(id);
        }
        for (Fact f : observed) {
            for (String c : candidates) {
                if (same.test(f.value(), c)) {
                    return IdentifierEvidence.match(id, f.rawText());
                }
            }
        }
        return IdentifierEvidence.mismatch(id, observed.get(0).rawText());
    }

    /** A read with no letters or digits says nothing, so it is dropped instead of compared. */
    private static List<Fact> readable(List<Fact> facts) {
        return facts.stream().filter(f -> !FuzzyText.alnum(f.value()).isEmpty()).toList();
    }

    private static String digitsWithoutLeadingZeros(String s) {
        return s.replaceAll("[^0-9]", "").replaceFirst("^0+", "");
    }

    private static String countryAlias(String c) {
        String s = c.trim().toLowerCase(Locale.ROOT).replace(".", "");
        return switch (s) {
            case "usa", "united states", "united states of america", "us" -> "us";
            case "uk", "united kingdom", "england", "great britain" -> "uk";
            case "west germany", "deutschland", "germany" -> "germany";
            default -> s;
        };
    }
}
