package ai.speckitty.charter.service;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The packs the service loads, for example {@code charter.packs[0].name=built-in},
 * {@code charter.packs[0].level=built-in}, {@code charter.packs[0].path=/path/to/built-in}.
 */
@ConfigurationProperties("charter")
public record CharterProperties(List<Pack> packs) {

    public CharterProperties {
        packs = packs == null ? List.of() : List.copyOf(packs);
    }

    /** One configured pack. */
    public record Pack(String name, String level, Path path) {}
}
