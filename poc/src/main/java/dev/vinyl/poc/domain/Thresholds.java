package dev.vinyl.poc.domain;

/**
 * Editable decision settings. These are starting guesses, not tuned values.
 *
 * @param confidentScore minimum score for the top candidate to be called confident
 * @param plausibleScore minimum score for another candidate to count as a rival
 * @param riskLimit      value at risk above which a person must look
 */
public record Thresholds(double confidentScore, double plausibleScore, double riskLimit) {

    public static Thresholds defaults() {
        return new Thresholds(0.7, 0.4, 5.0);
    }
}
