package dev.vinyl.poc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detects a folder that holds photos of more than one album: two artist reads, or two title reads, where
 * neither name contains the other once spacing and punctuation are ignored. One folder is one record, so a
 * conflict is sent to a person instead of being scored as if the photos agreed.
 */
public final class MixedPhotosCheck {

    private static final int MIN_CHARS = 3;

    private MixedPhotosCheck() {
    }

    /** Returns a short description of the conflict, or empty when every artist and title read agrees. */
    public static Optional<String> conflict(List<String> artists, List<String> titles) {
        return firstConflict("artist", artists).or(() -> firstConflict("title", titles));
    }

    private static Optional<String> firstConflict(String what, List<String> values) {
        List<String> raw = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (String v : values) {
            String key = FuzzyText.alnum(v);
            if (key.length() >= MIN_CHARS) {
                raw.add(v.trim());
                keys.add(key);
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                if (!keys.get(i).contains(keys.get(j)) && !keys.get(j).contains(keys.get(i))) {
                    return Optional.of("different " + what + "s read: \"" + raw.get(i) + "\" and \"" + raw.get(j) + "\"");
                }
            }
        }
        return Optional.empty();
    }
}
