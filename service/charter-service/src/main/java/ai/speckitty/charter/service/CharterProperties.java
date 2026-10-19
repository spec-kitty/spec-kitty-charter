package ai.speckitty.charter.service;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The packs the service loads, for example {@code charter.packs[0].name=built-in},
 * {@code charter.packs[0].level=built-in}, {@code charter.packs[0].path=/path/to/built-in}.
 *
 * @param packs the configured packs, in configuration order; empty, never {@code null}, when none is configured
 */
@ConfigurationProperties("charter")
public record CharterProperties(@DefaultValue List<Pack> packs) {

    /**
     * Copies {@code packs}, so the configuration cannot change after binding.
     *
     * @param packs the configured packs; the binder supplies an empty list when none is configured
     */
    public CharterProperties {
        packs = List.copyOf(packs);
    }

    /**
     * One configured pack.
     *
     * @param name the pack's name, as the listing reports it
     * @param level the pack's level token, for example {@code built-in}; an unknown token fails the wiring
     * @param path the directory that holds the pack
     */
    public record Pack(String name, String level, Path path) {}
}
