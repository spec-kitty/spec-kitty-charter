package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.infra.api.PackSourceIntake;
import ai.speckitty.charter.packs.infra.api.PackSourceSwitch;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes the sources of a pack registration (IC-05, IC-18): which kinds are switched on, from
 * {@code charter.pack-sources.<kind>.enabled}. The fetchers and the intake that join them to the inbound adapter follow.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PackSourceProperties.class)
public class PackSourceWiring {

    /** For Spring, which instantiates the configuration class. */
    public PackSourceWiring() {}

    /**
     * The switch of each source kind, at the configured positions; read at every request.
     *
     * @param properties the bound source configuration
     * @return the switch
     */
    @Bean
    PackSourceSwitch packSourceSwitch(PackSourceProperties properties) {
        Set<PackSourceKind> on = EnumSet.noneOf(PackSourceKind.class);
        if (properties.https().enabled()) {
            on.add(PackSourceKind.HTTPS_ARCHIVE);
        }
        if (properties.git().enabled()) {
            on.add(PackSourceKind.GIT);
        }
        if (properties.filesystem().enabled()) {
            on.add(PackSourceKind.FILESYSTEM);
        }
        return new PackSourceSwitch(on);
    }

    /**
     * What takes a registration from a source (skeleton: no source is built yet).
     *
     * @return the intake
     */
    @Bean
    PackSourceIntake packSourceIntake() {
        return PackSourceIntake.none();
    }
}
