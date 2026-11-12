package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.Locations;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cost of one registration does not grow with the number of stored packs (NFR-009). Every listing of the configured
 * locations parses every {@code pack.yaml}, so a step that lists once per pack it looks at is a square: creating one
 * unrelated pack took 0.63 s with 100 stored packs, 2.7 s with 200 and 13.7 s with 400, past the 5 s of the registry
 * lock at about 300. The test counts listings, which is deterministic where a clock is not, and compares two store
 * sizes, so a constant that happens to be large cannot hide growth.
 */
class PackRegistrationScaleTest {

    private static final int SMALL = 8;
    private static final int LARGE = 40;

    @Test
    void creatingAPackListsTheConfiguredLocationsAsOftenWithManyStoredPacksAsWithFew(@TempDir Path host) {
        int few = listingsOfOneCreate(host.resolve("few"), SMALL);
        int many = listingsOfOneCreate(host.resolve("many"), LARGE);

        assertThat(many)
                .as(
                        "listings for a create with %d stored packs against %d stored packs (each listing parses every"
                                + " pack.yaml, so a count that grows with the store is a square)",
                        LARGE, SMALL)
                .isEqualTo(few);
        assertThat(few)
                .as("listings of one create, however few packs are stored")
                .isLessThanOrEqualTo(6);
    }

    @Test
    void replacingAPackListsTheConfiguredLocationsAsOftenWithManyStoredPacksAsWithFew(@TempDir Path host) {
        int few = listingsOfOneReplace(host.resolve("few"), SMALL, false);
        int many = listingsOfOneReplace(host.resolve("many"), LARGE, false);

        assertThat(many)
                .as("listings for a replace with %d stored packs against %d stored packs", LARGE, SMALL)
                .isEqualTo(few);
        assertThat(few)
                .as("listings of one replace, however few packs are stored")
                .isLessThanOrEqualTo(8);
    }

    @Test
    void aReplaceThatRemovesAnArtifactScansTheOtherPacksWithoutListingOncePerPack(@TempDir Path host) {
        int few = listingsOfOneReplace(host.resolve("few"), SMALL, true);
        int many = listingsOfOneReplace(host.resolve("many"), LARGE, true);

        assertThat(many)
                .as(
                        "listings for a replace that drops an artifact (the inbound scan reads every other pack) with %d"
                                + " stored packs against %d",
                        LARGE, SMALL)
                .isEqualTo(few);
        assertThat(few).as("listings of one replace with an inbound scan").isLessThanOrEqualTo(10);
    }

    private static int listingsOfOneCreate(Path host, int stored) {
        Rig rig = new Rig(host);
        install(rig, stored);
        AtomicInteger listings = new AtomicInteger();
        PackRegistrationService service = serviceCountingListings(rig, listings);

        PackRegistrationService.RegistrationOutcome outcome = service.register(
                "fresh",
                rig.stage(Packs.of(PackRegistrationServiceTest.ulid('Z'), "fresh", "1.0.0", "org")
                        .directive("FRESH_ONE", "Fresh")),
                Preconditions.none());

        assertThat(outcome.outcome())
                .as("the create happened, so the count is of a real registration")
                .isEqualTo(Outcome.CREATED);
        assertThat(rig.packs.resolve("fresh").resolve("pack.yaml")).isRegularFile();
        return listings.get();
    }

    private static int listingsOfOneReplace(Path host, int stored, boolean dropsAnArtifact) {
        Rig rig = new Rig(host);
        install(rig, stored);
        String id = PackRegistrationServiceTest.ulid('Z');
        rig.register(
                "fresh",
                rig.stage(Packs.of(id, "fresh", "1.0.0", "org")
                        .directive("FRESH_ONE", "Fresh")
                        .directive("FRESH_TWO", "Fresh, the one a replace may drop")),
                Preconditions.none());
        AtomicInteger listings = new AtomicInteger();
        PackRegistrationService service = serviceCountingListings(rig, listings);

        PackRegistrationService.RegistrationOutcome outcome =
                service.register("fresh", rig.stage(replacement(id, dropsAnArtifact)), Preconditions.none());

        assertThat(outcome.outcome()).isEqualTo(Outcome.REPLACED);
        return listings.get();
    }

    private static Packs replacement(String id, boolean dropsAnArtifact) {
        Packs next = Packs.of(id, "fresh", "2.0.0", "org").directive("FRESH_ONE", "Fresh again");
        return dropsAnArtifact ? next : next.directive("FRESH_TWO", "Fresh, kept");
    }

    private static void install(Rig rig, int stored) {
        for (int i = 1; i <= stored; i++) {
            rig.install(
                    "other-" + i,
                    Packs.of("01M6R0WP14" + String.format("%016d", i), "other-" + i, "1.0.0", "org")
                            .directive("OTHER_" + i, "Other " + i));
        }
    }

    /** The service over repositories whose every listing is counted; the writes go to the rig's own store. */
    private static PackRegistrationService serviceCountingListings(Rig rig, AtomicInteger listings) {
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        Locations counting = () -> {
            listings.incrementAndGet();
            return rig.locations.all();
        };
        DomainRepositoryService repository = new DomainRepositoryService(counting, strategy);
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations), rig.generation);
        WritePolicyGate gate = new PackMutabilityPolicy(
                new OfferingReadService(repository, repository, repository, read), repository, repository, read);
        RegistrationImpact impact =
                new RegistrationImpact(new StagedPackViewFactory(counting, strategy), ActivationImpact.NONE);
        return new PackRegistrationService(
                new WholePackStore(strategy),
                rig.commits,
                rig.writeStore,
                new StagedPackViewFactory(counting, strategy),
                gate,
                new InboundReferences(repository, repository, repository),
                impact);
    }
}
