package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.MutabilityCheck;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.Locations;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void aWholePackUploadWithManySchemaViolationsIsRefusedWithEveryOneInPlaceOrder(@TempDir Path host) {
        // #648: the refusal lists a file's violations in place order, steps first to last. The worst case at scale
        // (20 000 violations) is held to a time by PackViolationCollectionBudgetTest.
        Rig rig = new Rig(host);
        Packs upload = brokenSteps(300);

        CharterRefusal refused = refused(rig, upload);

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.violations())
                .containsExactlyElementsOf(IntStream.range(0, 300)
                        .mapToObj(i -> new RefusalViolation(
                                "tactics/many.tactic.yaml#/steps/" + i + "/description",
                                "artifact_schema_error",
                                "tactics/many.tactic.yaml: 'description' must be a string"))
                        .toList());
        assertThat(rig.packs.resolve("fresh")).as("nothing was stored").doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fresh::"})
    void anUploadOfSelfJoinsBesideSchemaViolationsIsRefusedWithEveryFaultInOrder(String qualifier, @TempDir Path host) {
        // #648: a self-join is stated at its graph document, in edge order, ahead of the schema violations of the
        // tactics, bare or written through the pack's own name; the words name the bare form. The worst case at scale
        // is held to a time by PackViolationCollectionBudgetTest.
        Rig rig = new Rig(host);

        CharterRefusal refused = refused(rig, selfJoinsBesideBrokenTactics(qualifier, 300, 2, 200));

        List<RefusalViolation> expected = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            expected.add(new RefusalViolation(
                    "tactic.graph.yaml#/edges/" + i,
                    "drg_schema_error",
                    "tactic.graph.yaml: a relationship cannot join 'tactic:t" + i + "' to itself"));
        }
        for (String id : List.of("many-a", "many-b")) {
            String file = "tactics/" + id + ".tactic.yaml";
            for (int i = 0; i < 200; i++) {
                expected.add(new RefusalViolation(
                        file + "#/steps/" + i + "/description",
                        "artifact_schema_error",
                        file + ": 'description' must be a string"));
            }
        }
        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.violations()).containsExactlyElementsOf(expected);
    }

    /** An upload of one tactic of {@code steps} steps that each break their description: one violation each. */
    static Packs brokenSteps(int steps) {
        return Packs.of(PackRegistrationServiceTest.ulid('Z'), "fresh", "1.0.0", "org")
                .file("tactics/many.tactic.yaml", tactic("many", steps));
    }

    /**
     * An upload of a graph document of {@code joins} edges that each join a tactic to itself, beside {@code tactics}
     * tactics of {@code steps} broken steps each ({@code many-a}, {@code many-b} ...).
     */
    static Packs selfJoinsBesideBrokenTactics(String qualifier, int joins, int tactics, int steps) {
        StringBuilder shard = new StringBuilder("schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n");
        for (int i = 0; i < joins; i++) {
            String urn = qualifier + "tactic:t" + i;
            shard.append("- {source: '")
                    .append(urn)
                    .append("', target: '")
                    .append(urn)
                    .append("', relation: requires}\n");
        }
        Packs upload = Packs.of(PackRegistrationServiceTest.ulid('Z'), "fresh", "1.0.0", "org")
                .file("tactic.graph.yaml", shard.toString());
        for (int t = 0; t < tactics; t++) {
            String id = "many-" + (char) ('a' + t);
            upload.file("tactics/" + id + ".tactic.yaml", tactic(id, steps));
        }
        return upload;
    }

    /** The refusal of registering {@code upload}, which is staged first, outside what a caller measures. */
    static CharterRefusal refused(Rig rig, Packs upload) {
        StagedPack staged = rig.stage(upload);
        return PackRegistrationServiceTest.refusal(() -> rig.register("fresh", staged, Preconditions.none()));
    }

    private static String tactic(String id, int steps) {
        StringBuilder tactic = new StringBuilder("schema_version: \"1.0\"\nid: " + id + "\nname: Many\nsteps:\n");
        for (int i = 0; i < steps; i++) {
            tactic.append("  - {title: t, description: 1}\n");
        }
        return tactic.toString();
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
        MutabilityCheck gate = new PackMutabilityPolicy(
                new OfferingReadService(repository, repository, repository, read), repository, repository, read);
        ClosureImpactCheck impact =
                new ClosureImpactCheck(new StagedPackViewFactory(counting, strategy), ActivationImpact.NONE);
        return new PackRegistrationService(
                new FileSystemPackInspector(),
                new WholePackStore(strategy),
                rig.commits,
                rig.writeStore,
                new StagedPackViewFactory(counting, strategy),
                gate,
                new InboundReferences(repository, repository, repository),
                impact);
    }
}
