package ai.speckitty.charter.service;

import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.read.ActionContextQuery;
import ai.speckitty.charter.read.PackListingQuery;
import ai.speckitty.charter.yaml.FileSystemCharterPackRepository;
import ai.speckitty.charter.yaml.PackLocation;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Connects the use cases to the file-based adapter. */
@Configuration(proxyBeanMethods = false)
public class CharterWiring {

    /** For Spring, which instantiates the configuration class. */
    public CharterWiring() {}

    /** Where the packs are; a test may supply a {@code @Primary} one. */
    public interface PackLocations extends Supplier<List<PackLocation>> {}

    @Bean
    PackLocations packLocations(CharterProperties properties) {
        List<PackLocation> locations = properties.packs().stream()
                .map(p -> new PackLocation(
                        p.name(),
                        PackLevel.fromToken(p.level())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "charter.packs: unknown level '" + p.level() + "' for " + p.name())),
                        p.path()))
                .toList();
        return () -> locations;
    }

    @Bean
    CharterPackRepository charterPackRepository(PackLocations locations) {
        return new FileSystemCharterPackRepository(locations);
    }

    @Bean
    ActionContextQuery actionContextQuery(CharterPackRepository packs) {
        return new ActionContextQuery(packs);
    }

    @Bean
    PackListingQuery packListingQuery(CharterPackRepository packs) {
        return new PackListingQuery(packs);
    }
}
