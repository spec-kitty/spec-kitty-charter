package ai.speckitty.charter.deployable;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * The aggregate of the layer configurations: one scan over the three per-layer {@code config} packages (application,
 * storage adapter, inbound adapter), so a test can wire a layer slice from its own {@code config} package without
 * listing beans. An empty package scans to nothing; each layer's configuration lands in its own package.
 *
 * <p>This aggregates only the layers' own beans, including the storage layer's {@code DomainRepositoryService.Locations}
 * bound from {@code charter.config-root}. The remaining property-bound composition-root beans (for example
 * {@code PackLocations}, the guard, {@code FileSystemPackSources}) are supplied by the {@code deployable} wiring classes
 * ({@code CharterWiring}, {@code GuardWiring}) through {@code CharterServiceApplication}'s own scan — so this class
 * alone does not boot the full application context; a full-context test goes through
 * {@code CharterServiceApplication}.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan({
    "ai.speckitty.charter.packs.application.config",
    "ai.speckitty.charter.packs.infra.storage.config",
    "ai.speckitty.charter.packs.infra.api.config"
})
public class DeployableConfiguration {

    /** For Spring, which instantiates the configuration class; nothing else constructs it. */
    public DeployableConfiguration() {}
}
