package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The profile governance walk over the example packs (NFR-004, ADR-0070): every profile of the closure of the
 * {@code project} example, which builds on {@code org-base} and through it on {@code built-in}, is walked over the
 * pack's composed closure and finishes within a generous budget. The cycle fixture of {@code GovernanceWalkTest} owns
 * the termination guarantee; this test owns the canonical shapes.
 */
@DisplayName("The governance walk finishes for every example profile (NFR-004)")
class ProfileGovernanceCorpusTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final Duration BUDGET = Duration.ofSeconds(10);

    private static ClosureQueries queries() {
        List<PackLocation> packs = ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
        DomainRepositoryService store = new DomainRepositoryService(() -> packs, new YamlSerializationStrategy());
        ConsistentRead read = new ConsistentRead(() -> DIGEST);
        OfferingReadService offering = new OfferingReadService(store, store, store, read);
        return new ClosureQueries(offering, store, read, store, store);
    }

    @Test
    @DisplayName("each example profile is walked, pack-scoped, and lists neither itself nor a dangling parent")
    void everyProfileOfTheProjectClosureIsWalked() throws IOException {
        ClosureQueries queries = queries();
        ClosureView view = queries.snapshotWithin(Optional.of(List.of(new PackIdentifier(ExamplePacks.PROJECT))));
        List<String> profiles = new ArrayList<>(new TreeSet<>(view.heldIds().get(ArtifactKind.AGENT_PROFILE)));

        List<String> onDisk = new ArrayList<>();
        for (String pack : ExamplePacks.names()) {
            PackFiles.filesEndingWith(ExamplePacks.pack(pack).resolve("agent_profiles"), ".agent.yaml")
                    .forEach(name -> onDisk.add(name.substring(0, name.length() - ".agent.yaml".length())));
        }
        assertThat(profiles)
                .as("the project closure holds exactly the profile files of the three example packs on disk")
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
        assertThat(listed).as("the example profiles carry governance").isPositive();
    }

    @Test
    @DisplayName("the service composes the pack's closure and walks one profile")
    void theServiceWalksOneProfileOfTheProjectPack() {
        ClosureQueries queries = queries();
        List<String> held = new ArrayList<>(
                new TreeSet<>(queries.snapshotWithin(Optional.of(List.of(new PackIdentifier(ExamplePacks.PROJECT))))
                        .heldIds()
                        .get(ArtifactKind.AGENT_PROFILE)));

        Optional<ProfileGovernance.Governance> governance = assertTimeoutPreemptively(
                BUDGET,
                () -> new ProfileGovernance(queries).ofPack(new PackIdentifier(ExamplePacks.PROJECT), held.get(0), 1));

        assertThat(governance).isPresent();
    }
}
