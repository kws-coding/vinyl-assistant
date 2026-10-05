package dev.vinyl.poc.discogs;

import dev.vinyl.poc.domain.ReleaseInfo;
import java.util.List;
import java.util.Objects;

/** Maps Discogs DTOs to plain domain values. */
public final class ReleaseMapper {

    private ReleaseMapper() {
    }

    public static ReleaseInfo toInfo(ReleaseDto dto) {
        return new ReleaseInfo(
                String.valueOf(dto.id()),
                dto.title(),
                dto.country(),
                dto.year(),
                distinct(dto.labels(), ReleaseDto.Label::catno),
                identifiers(dto, "Barcode"),
                identifiers(dto, "Matrix / Runout"),
                distinct(dto.labels(), ReleaseDto.Label::name),
                distinct(dto.formats(), f -> f.descriptions() == null ? null : String.join(", ", f.descriptions())));
    }

    private static List<String> identifiers(ReleaseDto dto, String type) {
        return distinct(dto.identifiers(), i -> type.equals(i.type()) ? i.value() : null);
    }

    private static <T> List<String> distinct(List<T> items, java.util.function.Function<T, String> f) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(f).filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
    }
}
