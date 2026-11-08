package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActivationImpact.ClosureChange;
import ai.speckitty.charter.packs.application.ActivationImpact.ClosureChanges;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The activations a registration affects (IC-15, FR-027, B4): for each stored activation whose closure holds the written
 * pack, the set of artifacts in force before and after the write, and the ids of the activations whose set differs, sorted.
 * An activation whose closure does not hold the pack is not asked. A closure that cannot be merged has no set: it differs
 * from one that can, and equals another that cannot.
 */
class ActivationImpactAffectedTest {

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private ActivationImpactAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ActivationImpactAdapter(repository);
    }

    // ---- a closure of hand-built packs, merged for real

    private static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new LinkedHashMap<>();

        Closure pack(String name, PackLevel level, MutabilityPolicySource policy, String... held) {
            PackDescriptor descriptor = new PackDescriptor(
                    "id-" + name,
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    List.of());
            List<String> urns = List.of(held);
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), urns));
            facts.put(
                    name, new PackCompositionFacts(List.of(), urns, List.of(), policy, List.of(), List.of(), Map.of()));
            policies.put("id-" + name, policy);
            return this;
        }

        ClosureView view() {
            return new ClosureView(
                    List.of(),
                    Map.of(),
                    Map.of(),
                    new RequiredFloor(Map.of()),
                    new PerPackPolicy(policies),
                    packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                    List.of(),
                    Map.of(),
                    "sha256:merged",
                    EdgeIndex.of(packs, facts),
                    new ClosureView.MergeInputs(packs, facts),
                    "sha256:closure");
        }
    }

    private static ClosureView holding(String... urns) {
        return new Closure()
                .pack("acme", PackLevel.ORG, MutabilityPolicySource.empty(), urns)
                .view();
    }

    /** A closure whose merge refuses: a project pack replaces a built-in locked directive. */
    private static ClosureView unmergeable() {
        return new Closure()
                .pack(
                        "base",
                        PackLevel.BUILT_IN,
                        MutabilityPolicySource.builder()
                                .entry(Urn.parse("directive:A").orElseThrow(), MutabilityLevel.LOCKED)
                                .build(),
                        "directive:A")
                .pack("acme", PackLevel.ORG, MutabilityPolicySource.empty(), "directive:A")
                .view();
    }

    private void activation(String id, Optional<String> pack, String... selected) {
        ActivationDocument.Builder builder =
                ActivationDocument.builder().selected(ArtifactKind.DIRECTIVE, List.of(selected));
        pack.ifPresent(name -> builder.packs(List.of(PackRef.of(name))));
        repository.create(new ActivationId(id), builder.build().document());
    }

    private static ClosureChanges closures(Map<Optional<List<String>>, ClosureChange> byRoots) {
        return roots -> Optional.ofNullable(byRoots.get(roots));
    }

    @Test
    void anActivationWhoseSelectedArtifactLeftTheClosureIsAffectedAndAnUnrelatedOneIsNot() {
        activation("a-1", Optional.of("acme"), "A");
        activation("a-2", Optional.of("other"), "O");
        ClosureChanges closures = closures(Map.of(
                Optional.of(List.of("acme")),
                new ClosureChange(Optional.of(holding("directive:A")), holding("directive:B"))));

        assertThat(adapter.activationsAffected(closures)).containsExactly("a-1");
    }

    @Test
    void anActivationWhoseInForceSetIsTheSameBeforeAndAfterIsNotAffectedWhateverTheWriteChanged() {
        activation("a-1", Optional.of("acme"), "A");
        ClosureChanges closures = closures(Map.of(
                Optional.of(List.of("acme")),
                new ClosureChange(Optional.of(holding("directive:A")), holding("directive:A", "directive:B"))));

        assertThat(adapter.activationsAffected(closures)).isEmpty();
    }

    @Test
    void anActivationThatSelectsWhatTheWriteAddedIsAffected() {
        activation("a-1", Optional.of("acme"), "B");
        ClosureChanges closures = closures(Map.of(
                Optional.of(List.of("acme")),
                new ClosureChange(Optional.of(holding("directive:A")), holding("directive:A", "directive:B"))));

        assertThat(adapter.activationsAffected(closures)).containsExactly("a-1");
    }

    @Test
    void anActivationWhoseSelectedArtifactStaysInForceButWhoseWinnerMovedToAnotherPackAndLevelIsAffected() {
        activation("a-1", Optional.of("acme"), "A");
        ClosureView before = new Closure()
                .pack("base", PackLevel.BUILT_IN, MutabilityPolicySource.empty(), "directive:A")
                .view();
        ClosureView after = new Closure()
                .pack("base", PackLevel.BUILT_IN, MutabilityPolicySource.empty(), "directive:A")
                .pack("acme", PackLevel.ORG, MutabilityPolicySource.empty(), "directive:A")
                .view();
        ClosureChanges closures =
                closures(Map.of(Optional.of(List.of("acme")), new ClosureChange(Optional.of(before), after)));

        assertThat(adapter.activationsAffected(closures))
                .as("A is in force before and after, but the version in force is another pack's, at another level")
                .containsExactly("a-1");
    }

    @Test
    void anActivationWhoseSelectedArtifactStaysInForceFromTheSamePackButUnderAnotherPolicyIsAffected() {
        activation("a-1", Optional.of("acme"), "A");
        ClosureView before = holding("directive:A");
        ClosureView after = new Closure()
                .pack(
                        "acme",
                        PackLevel.ORG,
                        MutabilityPolicySource.builder()
                                .entry(Urn.parse("directive:A").orElseThrow(), MutabilityLevel.EXTENDABLE)
                                .build(),
                        "directive:A")
                .view();
        ClosureChanges closures =
                closures(Map.of(Optional.of(List.of("acme")), new ClosureChange(Optional.of(before), after)));

        assertThat(adapter.activationsAffected(closures)).containsExactly("a-1");
    }

    @Test
    void anActivationWhoseWinnersAreTheSameBeforeAndAfterIsNotAffectedByAnotherPacksArtifact() {
        activation("a-1", Optional.of("acme"), "A");
        ClosureView before = holding("directive:A");
        ClosureView after = new Closure()
                .pack("acme", PackLevel.ORG, MutabilityPolicySource.empty(), "directive:A")
                .pack("other", PackLevel.ORG, MutabilityPolicySource.empty(), "directive:Z")
                .view();
        ClosureChanges closures =
                closures(Map.of(Optional.of(List.of("acme")), new ClosureChange(Optional.of(before), after)));

        assertThat(adapter.activationsAffected(closures))
                .as("the control: A has the same winner, and Z is held but not in force")
                .isEmpty();
    }

    @Test
    void aClosureThatDidNotExistBeforeHasNothingInForceBefore() {
        activation("a-1", Optional.empty(), "A");
        activation("a-2", Optional.empty(), "Z");
        ClosureChanges closures =
                closures(Map.of(Optional.empty(), new ClosureChange(Optional.empty(), holding("directive:A"))));

        assertThat(adapter.activationsAffected(closures))
                .as("a-1 selects A, which the new closure puts in force; a-2 selects Z, which nothing holds")
                .containsExactly("a-1");
    }

    @Test
    void theIdsAreSortedAndEachIsListedOnce() {
        activation("c-3", Optional.of("acme"), "A");
        activation("a-1", Optional.of("acme"), "A");
        activation("b-2", Optional.of("acme"), "A");
        ClosureChanges closures = closures(Map.of(
                Optional.of(List.of("acme")), new ClosureChange(Optional.of(holding("directive:A")), holding())));

        assertThat(adapter.activationsAffected(closures)).containsExactly("a-1", "b-2", "c-3");
    }

    @Test
    void aClosureThatCannotBeMergedAfterTheWriteDiffersFromOneThatCouldBeBefore() {
        activation("a-1", Optional.of("acme"), "A");
        ClosureChanges closures = closures(Map.of(
                Optional.of(List.of("acme")), new ClosureChange(Optional.of(holding("directive:A")), unmergeable())));

        assertThat(adapter.activationsAffected(closures)).containsExactly("a-1");
    }

    @Test
    void aClosureThatCannotBeMergedBeforeOrAfterIsNoChange() {
        activation("a-1", Optional.of("acme"), "A");
        ClosureChanges closures = closures(
                Map.of(Optional.of(List.of("acme")), new ClosureChange(Optional.of(unmergeable()), unmergeable())));

        assertThat(adapter.activationsAffected(closures)).isEmpty();
    }

    @Test
    void noStoredActivationIsAskedAboutAnythingAndNoneIsAffected() {
        assertThat(adapter.activationsAffected(roots -> {
                    throw new AssertionError("no activation to ask about: " + roots);
                }))
                .isEmpty();
    }
}
