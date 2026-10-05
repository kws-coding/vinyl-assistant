package dev.vinyl.poc.vision;

public enum PhotoRole {
    FRONT, BACK, LABELS, BARCODE, RUNOUT_A, RUNOUT_B, OTHER, UNCLEAR;

    /** Lenient: anything the model returns that we do not know is reported as UNCLEAR, never guessed. */
    public static PhotoRole parse(String s) {
        if (s == null) {
            return UNCLEAR;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNCLEAR;
        }
    }
}
