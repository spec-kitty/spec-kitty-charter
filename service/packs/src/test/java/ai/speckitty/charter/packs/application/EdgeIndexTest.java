package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.LayerMerge;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The edge-backed resolver: the typed edges of a closure's facts, indexed by (holding pack, source URN), so
 * a {@link GraphWalk} reaches every graph-authored kind from the facts the compose already read. Packs are named by
 * their descriptor {@code pack_id} ({@code id-<name>}), the identity the {@link ClosureView} uses.
 */
class EdgeIndexTest {

    /** A hand-built closure: each pack with what it holds and the edges its facts carry. */
    static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new HashMap<>();

        /**
         * Adds a pack, depending on the named packs.
         *
         * @param name the configured identifier and descriptor name; the descriptor {@code pack_id} is {@code id-name}
         * @param dependsOn the names of the packs it depends on
         * @param held the bare URNs it holds
         * @param edges the edges its facts carry
         * @return this closure
         */
        Closure pack(String name, List<String> dependsOn, List<String> held, Relationship... edges) {
            return pack(name, PackLevel.ORG, dependsOn, held, edges);
        }

        /** A pack at a level; packs are added in merge order (built-in, org, project). */
        Closure pack(String name, PackLevel level, List<String> held, Relationship... edges) {
            return pack(name, level, List.of(), held, edges);
        }

        Closure pack(String name, PackLevel level, List<String> dependsOn, List<String> held, Relationship... edges) {
            List<PackDependency> dependencies = dependsOn.stream()
                    .map(other -> new PackDependency(packId(other), ">=0"))
                    .toList();
            PackDescriptor descriptor = new PackDescriptor(
                    packId(name),
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependencies);
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), held));
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(),
                            held,
                            List.of(),
                            MutabilityPolicySource.empty(),
                            List.of(),
                            Arrays.asList(edges)));
            return this;
        }

        Closure pack(String name, List<String> held, Relationship... edges) {
            return pack(name, List.of(), held, edges);
        }

        EdgeIndex index() {
            return EdgeIndex.of(packs, facts);
        }

        /** The index under the layer merge of the closure, whose packs were added in merge order. */
        EdgeIndex merged() {
            return index().merged(mergedClosure());
        }

        /** The layer merge of the closure, whose packs were added in merge order. */
        MergedClosure mergedClosure() {
            return LayerMerge.merge(packs, facts, new PerPackPolicy(Map.of()));
        }
    }

    static String packId(String name) {
        return "id-" + name;
    }

    static PackIdentifier pack(String name) {
        return new PackIdentifier(packId(name));
    }

    static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    /** An edge as written: either endpoint may carry a {@code <pack>::} qualifier. */
    static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return new Relationship(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    private static List<String> written(CharterArtifact node) {
        return node.derivedRelationships().stream()
                .map(relationship ->
                        relationship.from() + " " + relationship.type().token() + " " + relationship.toReference())
                .toList();
    }

    @Test
    void aHeldSourceResolvesToANodeCarryingItsIndexedEdgesInThatPack() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:T", "agent_profile:P", "agent_profile:Q"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"),
                        edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Q"))
                .index();

        CharterArtifact directive =
                index.inPack(pack("alpha")).resolve(urn("directive:D")).orElseThrow();
        assertThat(directive.packId()).isEqualTo(pack("alpha"));
        assertThat(directive.urn()).isEqualTo(urn("directive:D"));
        assertThat(directive.label()).isEqualTo("directive:D");
        assertThat(written(directive)).containsExactly("directive:D requires tactic:T");
        assertThat(written(index.inPack(pack("alpha"))
                        .resolve(urn("agent_profile:P"))
                        .orElseThrow()))
                .as("every graph-authored kind resolves, not only the five the component resolver dispatches")
                .containsExactly("agent_profile:P specializes_from agent_profile:Q");
        assertThat(index.inPack(pack("alpha"))
                        .resolve(urn("tactic:T"))
                        .orElseThrow()
                        .derivedRelationships())
                .as("a held artifact with no edges is still a node")
                .isEmpty();
    }

    @Test
    void aPackThatDoesNotHoldTheUrnResolvesNothing() {
        EdgeIndex index = new Closure()
                .pack("alpha", List.of("directive:D"), edge("directive:D", RelationType.REQUIRES, "tactic:T"))
                .pack("beta", List.of("tactic:T"))
                .index();

        assertThat(index.inPack(pack("alpha")).resolve(urn("tactic:T"))).isEmpty();
        assertThat(index.inPack(pack("beta")).resolve(urn("directive:D"))).isEmpty();
        assertThat(index.inPack(new PackIdentifier("id-nowhere")).resolve(urn("directive:D")))
                .isEmpty();
    }

    @Test
    void twoPacksHoldingOneUrnEachKeepTheirOwnEdges() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("directive:D", "tactic:T1"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T1"))
                .pack(
                        "beta",
                        List.of("directive:D", "tactic:T2"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T2"))
                .index();

        assertThat(written(
                        index.inPack(pack("alpha")).resolve(urn("directive:D")).orElseThrow()))
                .containsExactly("directive:D requires tactic:T1");
        assertThat(written(
                        index.inPack(pack("beta")).resolve(urn("directive:D")).orElseThrow()))
                .containsExactly("directive:D requires tactic:T2");
    }

    @Test
    void aQualifiedReferenceIsHeldByThePackOfThatNameByTheComposesRule() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("tactic:T"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("directive:D"),
                        edge("directive:D", RelationType.REQUIRES, "beta::tactic:T"))
                .index();

        assertThat(index.holdingPack(PackQualifiedReference.parse("beta::tactic:T"), pack("alpha")))
                .contains(pack("beta"));
        assertThat(index.holdingPack(PackQualifiedReference.parse("gamma::tactic:T"), pack("alpha")))
                .isEmpty();
        assertThat(index.holdingPack(PackQualifiedReference.parse("directive:D"), pack("alpha")))
                .as("a bare reference is held by the referring pack when it holds it")
                .contains(pack("alpha"));
    }

    @Test
    void aQualifiedTargetOutsideTheWritersDependsOnClosureIsRefusedAsTheComposeRefusesIt() {
        Closure closure = new Closure()
                .pack("beta", List.of("tactic:T"))
                .pack("alpha", List.of("directive:D"), edge("directive:D", RelationType.REQUIRES, "beta::tactic:T"));

        assertThatThrownBy(closure::index)
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING));
    }

    @Test
    void anEdgeWrittenFromAnotherPacksArtifactIsHeldThereAndKeepsItsBareTargetInTheWriter() {
        EdgeIndex index = new Closure()
                .pack("beta", List.of("directive:B"))
                .pack(
                        "alpha",
                        List.of("beta"),
                        List.of("tactic:T"),
                        edge("beta::directive:B", RelationType.REQUIRES, "tactic:T"))
                .index();

        assertThat(written(
                        index.inPack(pack("beta")).resolve(urn("directive:B")).orElseThrow()))
                .containsExactly("directive:B requires alpha::tactic:T");
    }

    // ---- the merged view (FR-010; C19, C20, C36) ------------------------------------------------------------------

    @Test
    void c19AnEdgeStartingAtALosingPlaceIsDroppedWithTheArtifact() {
        EdgeIndex merged = new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("directive:D", "tactic:T1"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T1"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("directive:D", "tactic:T2"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T2"))
                .merged();

        assertThat(merged.inPack(pack("org")).resolve(urn("directive:D")))
                .as("the losing place no longer holds the artifact, so no edge starts there")
                .isEmpty();
        assertThat(written(merged.inPack(pack("project"))
                        .resolve(urn("directive:D"))
                        .orElseThrow()))
                .containsExactly("directive:D requires tactic:T2");
        assertThat(merged.placesOf(urn("directive:D")))
                .containsExactly(new EdgeIndex.Place(pack("project"), urn("directive:D")));
    }

    @Test
    void c20AnEdgeEndingAtALosingPlaceEndsAtTheWinnerBareOrQualified() {
        EdgeIndex merged = new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("directive:D", "tactic:T"),
                        edge("tactic:T", RelationType.REQUIRES, "directive:D"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("directive:D", "tactic:U"),
                        edge("tactic:U", RelationType.REQUIRES, "org::directive:D"))
                .merged();

        Relationship bare = merged.inPack(pack("org"))
                .resolve(urn("tactic:T"))
                .orElseThrow()
                .derivedRelationships()
                .getFirst();
        Relationship qualified = merged.inPack(pack("project"))
                .resolve(urn("tactic:U"))
                .orElseThrow()
                .derivedRelationships()
                .getFirst();
        assertThat(merged.neighbour(bare)).contains(new EdgeIndex.Place(pack("project"), urn("directive:D")));
        assertThat(merged.neighbour(qualified)).contains(new EdgeIndex.Place(pack("project"), urn("directive:D")));
    }

    @Test
    void anEdgeEndingAtAWinnerIsLeftAsWritten() {
        EdgeIndex merged = new Closure()
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("directive:D", "tactic:T"),
                        edge("tactic:T", RelationType.REQUIRES, "directive:D"))
                .merged();

        Relationship kept = merged.inPack(pack("project"))
                .resolve(urn("tactic:T"))
                .orElseThrow()
                .derivedRelationships()
                .getFirst();
        assertThat(kept.toPack()).isEmpty();
        assertThat(merged.neighbour(kept)).contains(new EdgeIndex.Place(pack("project"), urn("directive:D")));
    }

    @Test
    void c36AnOverridesEdgeThatResolvesToItsOwnSourceRefusesTheMerge() {
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("directive:D"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("directive:D"),
                        edge("directive:D", RelationType.OVERRIDES, "org::directive:D"));

        assertThatThrownBy(closure::merged).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains("directive:D", "project", "self_override");
        });
    }

    @Test
    void anOverridesEdgeToAnotherArtifactMergesAsAnyEdgeDoes() {
        EdgeIndex merged = new Closure()
                .pack("org", PackLevel.ORG, List.of("directive:D"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("directive:X"),
                        edge("directive:X", RelationType.OVERRIDES, "org::directive:D"))
                .merged();

        Relationship edge = merged.inPack(pack("project"))
                .resolve(urn("directive:X"))
                .orElseThrow()
                .derivedRelationships()
                .getFirst();
        assertThat(merged.neighbour(edge)).contains(new EdgeIndex.Place(pack("org"), urn("directive:D")));
    }

    @Test
    void theStepsOfAMissionTypeAreThoseOfTheWinnersOnTheMergedView() {
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("mission_type:M", "action:M/one", "action:M/two"))
                .pack("project", PackLevel.PROJECT, List.of("action:M/one"));

        assertThat(closure.index().stepsOf(urn("mission_type:M")))
                .extracting(EdgeIndex.Place::urn)
                .containsExactly(urn("action:M/one"), urn("action:M/two"), urn("action:M/one"));
        assertThat(closure.merged().stepsOf(urn("mission_type:M")))
                .containsExactly(
                        new EdgeIndex.Place(pack("org"), urn("action:M/two")),
                        new EdgeIndex.Place(pack("project"), urn("action:M/one")));
    }

    // ---- OD-9, the closure form of StepHolding (decision 01M7037V93Y077H8M4W9V8Y6B3)
    // ---------------------------------

    @Test
    void anActionDeclaredWithoutItsMissionTypeInAnyPackIsNotAStepOfThatMissionType() {
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("mission_type:M", "action:M/one"))
                .pack("project", PackLevel.PROJECT, List.of("action:M/two"));

        assertThat(closure.merged().stepsOf(urn("mission_type:M")))
                .as("no closure pack defines M and declares M/two, so the closure holds no such step")
                .containsExactly(new EdgeIndex.Place(pack("org"), urn("action:M/one")));
    }

    @Test
    void theStepsOfAMissionTypeComeFromAPackThatDefinesItNotFromAPackThatOnlyDeclaresTheAction() {
        Closure closure = new Closure()
                .pack("alpha", PackLevel.ORG, List.of("mission_type:M"))
                .pack("beta", PackLevel.PROJECT, List.of("action:M/s"));

        assertThat(closure.merged().stepsOf(urn("mission_type:M"))).isEmpty();
        assertThat(closure.index().stepsOf(urn("mission_type:M"))).isEmpty();
    }

    @Test
    void aWinningMissionTypeKeepsTheUniqueStepsOfTheVersionItReplaced() {
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("mission_type:M", "action:M/one", "action:M/two"))
                .pack("project", PackLevel.PROJECT, List.of("mission_type:M", "action:M/one"));

        assertThat(closure.merged().stepsOf(urn("mission_type:M")))
                .containsExactly(
                        new EdgeIndex.Place(pack("org"), urn("action:M/two")),
                        new EdgeIndex.Place(pack("project"), urn("action:M/one")));
    }

    @Test
    void writtenLinksKeepAnEdgeToATargetNoPackHoldsWhereLinksDropsIt() {
        EdgeIndex index = new Closure()
                .pack(
                        "alpha",
                        List.of("agent_profile:P", "directive:D"),
                        edge("agent_profile:P", RelationType.REQUIRES, "directive:D"),
                        edge("agent_profile:P", RelationType.SPECIALIZES_FROM, "agent_profile:Gone"))
                .index();

        assertThat(index.links()).extracting(EdgeIndex.Link::target).containsExactly(urn("directive:D"));
        assertThat(index.writtenLinks())
                .extracting(EdgeIndex.Link::source, EdgeIndex.Link::relation, EdgeIndex.Link::target)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(
                                urn("agent_profile:P"), RelationType.REQUIRES, urn("directive:D")),
                        org.assertj.core.api.Assertions.tuple(
                                urn("agent_profile:P"), RelationType.SPECIALIZES_FROM, urn("agent_profile:Gone")));
        assertThat(index.holdsUrn(urn("directive:D"))).isTrue();
        assertThat(index.holdsUrn(urn("agent_profile:Gone"))).isFalse();
    }

    @Test
    void writtenLinksOfAMergedIndexLeaveOutTheEdgesOfAVersionALaterLevelReplaced() {
        Closure closure = new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("agent_profile:P", "directive:Old"),
                        edge("agent_profile:P", RelationType.REQUIRES, "directive:Old"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("agent_profile:P", "directive:New"),
                        edge("agent_profile:P", RelationType.REQUIRES, "directive:New"));

        assertThat(closure.merged().writtenLinks())
                .extracting(EdgeIndex.Link::target)
                .containsExactly(urn("directive:New"));
    }
}
