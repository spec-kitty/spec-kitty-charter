package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.infra.fetch.git.GitVersionCheck;
import ai.speckitty.charter.packs.application.PackSourceStagers;
import ai.speckitty.charter.packs.application.PackTreeReader;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.infra.api.PackSourceIntake;
import ai.speckitty.charter.packs.infra.api.PackSourceSwitch;
import ai.speckitty.charter.packs.infra.archive.BoundedTreeStager;
import ai.speckitty.charter.packs.infra.fetch.HttpsArchiveFetcher;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange;
import ai.speckitty.charter.packs.infra.fetch.LocalSourceReader;
import ai.speckitty.charter.packs.infra.fetch.PackSourceProperties;
import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes the sources of a pack registration (FR-004, IC-05, IC-18): which kinds are switched on, from
 * {@code charter.pack-sources.<kind>.enabled}; the fetcher of each kind that is built (the https archive and the local
 * path; the git source is WP18's); and the intake that joins them to the inbound adapter. The ports and adapters are the
 * packs slice's, and only the composition root names both sides.
 *
 * <p>Nothing is fetched that is not switched on and not allowlisted: every flag is off by default, the https and git
 * allowlists and the filesystem root are empty by default, and a service that was never configured for a source refuses
 * it. A filesystem source that is switched on with no root, and a root that is under {@code /proc}, {@code /sys} or
 * {@code /dev} or overlaps the configuration root, fail the start.
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
     * The writer of a walked tree: the extractor's path rules and limits, for a directory.
     *
     * @return the tree stager
     */
    @Bean
    PackTreeReader packTreeReader() {
        return new BoundedTreeStager();
    }

    /**
     * The https archive source, over the guarded exchange.
     *
     * @param exchange the guarded exchange, on the archive allowlist
     * @param reader the archive extractor
     * @param properties the bound source configuration
     * @return the fetcher
     */
    @Bean
    PackSourceFetcher httpsArchiveFetcher(
            HttpsExchange exchange, PackArchiveReader reader, PackSourceProperties properties) {
        return new HttpsArchiveFetcher(
                exchange, reader, properties.https().maxSize().toBytes());
    }

    /**
     * The local path source, confined to the configured root.
     *
     * @param properties the bound source configuration
     * @param tree the writer of a walked tree
     * @param configRoot the configuration root the source root must not overlap, when one is set
     * @return the fetcher
     * @throws IllegalStateException when the source is switched on and no root is configured
     * @throws IllegalArgumentException when the root is under {@code /proc}, {@code /sys} or {@code /dev}, or overlaps
     *     the configuration root
     */
    @Bean
    PackSourceFetcher filesystemSourceFetcher(
            PackSourceProperties properties, PackTreeReader tree, ObjectProvider<ConfigRoot> configRoot) {
        if (properties.filesystem().enabled() && properties.filesystem().root() == null) {
            throw new IllegalStateException("charter.pack-sources.filesystem.enabled is true but "
                    + "charter.pack-sources.filesystem.root is not set: name the directory a path is read under, "
                    + "or switch the filesystem source off.");
        }
        ConfigRoot configured = configRoot.getIfAvailable();
        Path config = configured == null ? null : configured.root();
        return new LocalSourceReader(properties.filesystem().root(), config, tree);
    }

    /**
     * The git the git source may use, resolved once (NFR-011).
     *
     * @param properties the bound source configuration
     * @return the check; the git source is available only when it is enabled
     */
    @Bean
    GitVersionCheck gitVersionCheck(PackSourceProperties properties) {
        return GitVersionCheck.resolve(properties.git().executable(), System.getenv("PATH"));
    }

    /**
     * The git source (scaffold).
     *
     * @param properties the bound source configuration
     * @param check the resolved git
     * @return the fetcher
     */
    @Bean
    PackSourceFetcher gitSourceFetcher(PackSourceProperties properties, GitVersionCheck check) {
        return new PackSourceFetcher() {
            @Override
            public PackSourceKind kind() {
                return PackSourceKind.GIT;
            }

            @Override
            public Staged fetch(PackSourceRequest request, Path directory) {
                throw new IllegalStateException("not built");
            }
        };
    }

    /**
     * The stagers of the kinds that are built.
     *
     * @param fetchers the fetchers, one per kind
     * @param loader reads a staged tree, for the name the check of the target compares
     * @return the stagers
     */
    @Bean
    PackSourceStagers packSourceStagers(List<PackSourceFetcher> fetchers, WholePackWriteStore.Loader loader) {
        return new PackSourceStagers(fetchers, loader);
    }

    /**
     * What takes a registration from a source, for the inbound adapter.
     *
     * @param switches whether each kind is on
     * @param stagers the stagers of the kinds that are built
     * @param info the use case that knows the worktree id
     * @return the intake
     */
    @Bean
    PackSourceIntake packSourceIntake(PackSourceSwitch switches, PackSourceStagers stagers, ServiceInfoQuery info) {
        return new PackSourceIntake(switches, stagers, info::worktree);
    }
}
