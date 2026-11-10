package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The profile governance walk over the real pinned corpus (NFR-004): every profile of the closure of the {@code qa} pack,
 * which builds on {@code built-in}, is walked over the pack's composed closure and finishes within a generous budget.
 * The cycle fixture of {@code GovernanceWalkTest} owns the termination guarantee; this test owns the real shapes.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never skips.
 */
@Tag("open-packs")
@DisplayName("The governance walk finishes for every pinned profile (NFR-004)")
class ProfileGovernanceCorpusTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final Duration BUDGET = Duration.ofSeconds(10);

    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    private static ClosureQueries queries() {
        List<PackLocation> packs = List.of(
                new PackLocation(
                        "built-in",
                        PackLevel.BUILT_IN,
                        OpenPacksCorpus.packsDirectory().resolve("built-in")),
                new PackLocation(
                        "qa", PackLevel.ORG, OpenPacksCorpus.packsDirectory().resolve("qa")));
        DomainRepositoryService store = new DomainRepositoryService(() -> packs, new YamlSerializationStrategy());
        ConsistentRead read = new ConsistentRead(() -> DIGEST);
        OfferingReadService offering = new OfferingReadService(store, store, store, read);
        return new ClosureQueries(offering, store, read, store);
    }

    @Test
    @DisplayName(
            "each of the built-in and qa profiles is walked, pack-scoped, and lists neither itself nor a dangling parent")
    void everyProfileOfTheQaClosureIsWalked() throws IOException {
        ClosureQueries queries = queries();
        ClosureView view = queries.snapshotWithin(Optional.of(List.of(new PackIdentifier("qa"))));
        List<String> profiles = new ArrayList<>(new TreeSet<>(view.heldIds().get(ArtifactKind.AGENT_PROFILE)));

        List<String> onDisk = new ArrayList<>();
        for (String pack : List.of("built-in", "qa")) {
            OpenPacksCorpus.filesEndingWith(
                            OpenPacksCorpus.packsDirectory().resolve(pack).resolve("agent_profiles"), ".agent.yaml")
                    .forEach(name -> onDisk.add(name.substring(0, name.length() - ".agent.yaml".length())));
        }
        assertThat(profiles)
                .as("the built-in and qa profiles: the closure holds exactly the profile files on disk")
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(onDisk);
        int listed = 0;
        for (String id : profiles) {
            Urn profile = new Urn(NodeKind.AGENT_PROFILE, id);
            ProfileGovernance.Governance governance =
                    assertTimeoutPreemptively(BUDGET, () -> view.packGovernance(profile, 1));
            assertThat(governance.profile()).isEqualTo(profile);
            assertThat(governance.entries())
                    .as("%s lists every node once and not itself", id)
                    .extracting(ProfileGovernance.Entry::urn)
                    .doesNotContain(profile)
                    .doesNotHaveDuplicates();
            assertThat(governance.entries())
                    .as("%s: a held entry has a holder, an unheld one has none", id)
                    .allSatisfy(entry -> assertThat(entry.holder().isPresent()).isEqualTo(entry.held()));
            assertThat(governance.entries())
                    .as("%s lists no dangling parent: every entry is held", id)
                    .allSatisfy(entry -> assertThat(entry.held()).isTrue());
            listed += governance.entries().size();
        }
        assertThat(listed).as("the corpus profiles carry governance").isPositive();
    }

    @Test
    @DisplayName("the service composes the pack's closure and walks one profile")
    void theServiceWalksOneProfileOfTheQaPack() {
        ClosureQueries queries = queries();
        List<String> held =
                new ArrayList<>(new TreeSet<>(queries.snapshotWithin(Optional.of(List.of(new PackIdentifier("qa"))))
                        .heldIds()
                        .get(ArtifactKind.AGENT_PROFILE)));

        Optional<ProfileGovernance.Governance> governance = assertTimeoutPreemptively(
                BUDGET, () -> new ProfileGovernance(queries).ofPack(new PackIdentifier("qa"), held.get(0), 1));

        assertThat(governance).isPresent();
    }
}
