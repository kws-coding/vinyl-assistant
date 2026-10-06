package dev.vinyl.poc.runner;

import dev.vinyl.poc.vision.PhotoRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads photo_roles.csv: record_id,filename,role. Roles must be exact; a typo is an error, not unclear. Throwaway. */
public final class PhotoRolesCsv {

    private PhotoRolesCsv() {
    }

    /** record id to (lower-case file name to role) */
    public static Map<String, Map<String, PhotoRole>> read(Path file) throws IOException {
        List<List<String>> rows = Csv.parse(Files.readString(file));
        Map<String, Map<String, PhotoRole>> out = new HashMap<>();
        if (rows.isEmpty()) {
            return out;
        }
        List<String> header = rows.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int id = header.indexOf("record_id");
        int name = header.indexOf("filename");
        int role = header.indexOf("role");
        if (id < 0 || name < 0 || role < 0) {
            throw new IllegalArgumentException("photo_roles.csv needs the columns record_id, filename, role");
        }
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() <= Math.max(id, Math.max(name, role)) || r.get(id).isBlank()) {
                continue;
            }
            PhotoRole parsed;
            try {
                parsed = PhotoRole.valueOf(r.get(role).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("photo_roles.csv: unknown role '" + r.get(role) + "' for "
                        + r.get(id) + "/" + r.get(name));
            }
            out.computeIfAbsent(r.get(id).trim(), k -> new HashMap<>())
                    .put(r.get(name).trim().toLowerCase(Locale.ROOT), parsed);
        }
        return out;
    }
}
