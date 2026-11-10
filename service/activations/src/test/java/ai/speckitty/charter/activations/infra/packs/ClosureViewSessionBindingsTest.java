package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The merged closure's bindings as a {@link ClosureViewSession} answers them (#99, D12): the mission type each winner is
 * bound to, as the pack that holds the version in force declares it, which {@code EffectiveReadService.missionTypeWinner}
 * reads the step contracts of a type from. The winner of a type across the levels is the project's copy. The closure is
 * merged for real from hand-built packs.
 */
class ClosureViewSessionBindingsTest {

    private static final ArtifactRef SD = new ArtifactRef(ArtifactKind.MISSION_TYPE, "sd");
    private static final ArtifactRef OTHER = new ArtifactRef(ArtifactKind.MISSION_TYPE, "other");

    /**
     * org holds the type sd and the contract a bound to it; proj, at the project level, holds its own sd, the contract b
     * bound to sd, and c bound to another type.
     */
    private static PacksSession session() {
        return new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of(),
                        List.of("mission_type:sd", "mission_step_contract:a"),
                        Map.of("mission_step_contract:a", "mission_type:sd"))
                .pack(
                        "proj",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("mission_type:sd", "mission_step_contract:b", "mission_step_contract:c"),
                        Map.of(
                                "mission_step_contract:b", "mission_type:sd",
                                "mission_step_contract:c", "mission_type:other"))
                .session();
    }

    @Test
    void eachBoundWinnerIsBoundToItsTypeFromWhicheverPackHoldsIt() {
        assertThat(session().merged().bindings())
                .containsOnly(
                        Map.entry(contract("a"), SD), Map.entry(contract("b"), SD), Map.entry(contract("c"), OTHER));
    }

    @Test
    void theWinnerOfTheTypeIsTheProjectsCopy() {
        EffectiveArtifactRead winner = session()
                .effectiveArtifact(
                        ArtifactKind.MISSION_TYPE,
                        "sd",
                        0,
                        new InForceQuery(Set.of(), Set.of(), Optional.empty(), Map.of()))
                .orElseThrow();

        assertThat(winner.packId()).isEqualTo("proj");
        assertThat(winner.level()).isEqualTo(PackLevel.PROJECT);
        assertThat(winner.artifact().packId()).isEqualTo(new PackIdentifier("proj"));
        assertThat(session().merged().winners().get(contract("a")).packId()).isEqualTo("org");
    }

    private static ArtifactRef contract(String id) {
        return new ArtifactRef(ArtifactKind.MISSION_STEP_CONTRACT, id);
    }

    /** A closure of hand-built packs, merged for real; a winner is read as a stub. */
    private static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new LinkedHashMap<>();

        Closure pack(
                String name, PackLevel level, List<String> dependsOn, List<String> held, Map<String, String> bindings) {
            String id = "id-" + name;
            PackDescriptor descriptor = new PackDescriptor(
                    id,
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependsOn.stream()
                            .map(other -> new PackDependency("id-" + other, ">=0"))
                            .toList());
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), held));
            MutabilityPolicySource policy = MutabilityPolicySource.empty();
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(), held, List.of(), policy, List.of(), List.of(), Map.of(), bindings));
            policies.put(id, policy);
            return this;
        }

        PacksSession session() {
            ClosureView view = new ClosureView(
                    List.of(),
                    Map.<ArtifactKind, Set<String>>of(),
                    Map.of(),
                    new RequiredFloor(Map.of()),
                    new PerPackPolicy(policies),
                    packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                    List.of(),
                    Map.of(),
                    "sha256:source",
                    EdgeIndex.of(packs, facts),
                    new ClosureView.MergeInputs(packs, facts, (pack, kind, id) -> Optional.of(entity(pack, kind, id))),
                    "sha256:closure");
            return new ClosureViewSession(view, PacksAvailability.snapshotOf(view));
        }
    }

    private static CharterArtifact entity(PackIdentifier pack, ArtifactKind kind, String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return kind;
            }

            @Override
            public PackIdentifier packId() {
                return pack;
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }
}
