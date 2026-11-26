package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.LayerMerge;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MergedRelations;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import ai.speckitty.charter.packs.fixtures.CompositionFacts;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
         * @param name the configured identifier and descriptor name; the descriptor {@code pack_id} is {@link FixtureUlids#of the ULID of the name}
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
            return pack(name, level, dependsOn, held, List.of(), edges);
        }

        /** A pack at a level whose glossary contexts hold the terms, as bare URNs. */
        Closure glossaryHolder(
                String name, PackLevel level, List<String> held, List<String> terms, Relationship... edges) {
            return pack(name, level, List.of(), held, terms, edges);
        }

        private Closure pack(
                String name,
                PackLevel level,
                List<String> dependsOn,
                List<String> held,
                List<String> terms,
                Relationship... edges) {
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
                    new ConfiguredPack(new PackIdentifier(name), level),
                    descriptor,
                    List.of(),
                    held,
                    Set.copyOf(terms)));
            facts.put(
                    name,
                    CompositionFacts.of(
                            List.of(),
                            held,
                            List.of(),
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

        /**
         * The edge-index faults of the closure under its layer merge ({@link PackRulesets#edgeIndexFaults}): the rules
         * judge the edges of this index and of the merged one.
         */
        List<ClosureFault> edgeIndexFaults() {
            MergedClosure merge = mergedClosure();
            return PackRulesets.edgeIndexFaults(MergedRelations.of(
                    packs,
                    pack -> facts.get(pack.configured().id().value()).edges(),
                    new PerPackPolicy(Map.of()),
                    merge,
                    index().placedEdges(),
                    index().merged(merge).placedEdges()));
        }

        /** The index under the layer merge of the closure, whose packs were added in merge order. */
        EdgeIndex merged() {
            return index().merged(mergedClosure());
        }

        /** The merged index, counting each visit of an edge or a node to {@code visits}. */
        EdgeIndex merged(EdgeIndex.Visits visits) {
            return index().merged(mergedClosure(), visits);
        }

        /** The tensions in force of the closure under its layer merge, with the artifacts the predicate holds in force. */
        TensionsInForce tensions(java.util.function.Predicate<Urn> effective) {
            MergedClosure merge = mergedClosure();
            return TensionsInForce.of(
                    packs, facts, index(), new ClosureView.MergedView(merge, index().merged(merge)), effective);
        }

        /** The layer merge of the closure, whose packs were added in merge order. */
        MergedClosure mergedClosure() {
            return LayerMerge.merge(packs, facts, new PerPackPolicy(Map.of()));
        }
    }

    static String packId(String name) {
        return FixtureUlids.of(name);
    }

    static PackUlid pack(String name) {
        return FixtureUlids.ulid(name);
    }

    static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    /** An edge as written: either endpoint may carry a {@code <pack>::} qualifier. */
    static Relationship edge(String from, RelationType type, String to) {
        PackQualifiedReference source = PackQualifiedReference.parse(from);
        PackQualifiedReference target = PackQualifiedReference.parse(to);
        return Relationship.of(
                source.asUrn().orElseThrow(),
                source.packName(),
                target.asUrn().orElseThrow(),
                target.packName(),
                type,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * The {@code requires} edges a pack writes from mission type {@code type} to each of its steps (FR-011), then
     * {@code edges}: the links a held mission type in force needs for the merge to accept it.
     */
    static Relationship[] linked(String type, List<String> steps, Relationship... edges) {
        List<Relationship> out = new ArrayList<>();
        steps.forEach(
                step -> out.add(edge("mission_type:" + type, RelationType.REQUIRES, "action:" + type + "/" + step)));
        out.addAll(Arrays.asList(edges));
        return out.toArray(Relationship[]::new);
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
        assertThat(directive.packId())
                .as("a node carries the id its pack is registered under")
                .isEqualTo(new PackIdentifier("alpha"));
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
        assertThat(index.inPack(pack("nowhere")).resolve(urn("directive:D"))).isEmpty();
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
    void aQualifiedEndpointOutsideTheWritersClosureIsTheReferencesChecksFaultAndTheIndexJudgesNone() {
        // T020: every endpoint an edge writes is a reference of its pack's facts (the facts builder's edge facts), so
        // the compose's references check refuses a qualified endpoint outside the writer's depends_on closure, every
        // one of them, before an index is built; the index itself judges nothing.
        List<String> written = List.of("directive:D", "beta::tactic:T", "beta::tactic:U");
        Closure closure = new Closure()
                .pack("beta", List.of("tactic:T", "tactic:U"))
                .pack(
                        "alpha",
                        List.of("directive:D"),
                        edge("directive:D", RelationType.REQUIRES, "beta::tactic:T"),
                        edge("directive:D", RelationType.REQUIRES, "beta::tactic:U"));
        Map<String, PackCompositionFacts> facts = new HashMap<>(closure.facts);
        PackCompositionFacts alpha = facts.get("alpha");
        facts.put(
                "alpha",
                CompositionFacts.of(
                        List.of(),
                        alpha.held(),
                        List.of(),
                        written,
                        MutabilityPolicySource.empty(),
                        List.of(),
                        alpha.edges()));

        assertThat(PackRulesets.references(
                        closure.packs,
                        closure.packs,
                        pack -> Objects.requireNonNull(
                                facts.get(pack.configured().id().value()), "facts")))
                .extracting(ClosureFault::code, fault -> fault.detail().contains("beta::tactic:"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(RefusalCode.PACK_REFERENCE_PACK_MISSING, true),
                        org.assertj.core.groups.Tuple.tuple(RefusalCode.PACK_REFERENCE_PACK_MISSING, true));
        assertThat(closure.index().placedEdges())
                .as("the index judges nothing: it holds the edges as written")
                .hasSize(2);
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

    /** The base layer: mission type rdev with its steps a and b, each linked from the type. */
    private static Closure baseRdev() {
        return new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("mission_type:rdev", "action:rdev/a", "action:rdev/b"),
                        edge("mission_type:rdev", RelationType.REQUIRES, "action:rdev/a"),
                        edge("mission_type:rdev", RelationType.REQUIRES, "action:rdev/b"));
    }

    @Test
    void aWinningTypesOwnStepLinksAreLeftToTheLoadAndMerge() {
        Closure closure = baseRdev()
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("mission_type:rdev", "action:rdev/a"),
                        edge("mission_type:rdev", RelationType.REQUIRES, "action:rdev/a"));

        assertThat(closure.merged().holds(new EdgeIndex.Place(pack("project"), urn("mission_type:rdev"))))
                .isTrue();
    }

    @Test
    void anotherPacksEdgeToAStepOfTheTypeInForceMerges() {
        Closure closure = baseRdev()
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("directive:D"),
                        edge("org::mission_type:rdev", RelationType.REQUIRES, "org::action:rdev/b"));

        assertThat(closure.merged().holds(new EdgeIndex.Place(pack("org"), urn("mission_type:rdev"))))
                .isTrue();
    }

    @Test
    void anotherPacksEdgeToAnActionThatIsNoStepOfTheTypeInForceIsTheReferencesFaultWithTheStepLinkViolation() {
        // Ruling Q-C: pack_reference_artifact_missing, with the violation mission_step_unlinked.
        Closure closure = baseRdev()
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("directive:D"),
                        edge("org::mission_type:rdev", RelationType.REQUIRES, "org::action:rdev/c"),
                        edge("org::mission_type:rdev", RelationType.REQUIRES, "org::action:rdev/d"));

        assertThat(closure.edgeIndexFaults())
                .as("every stray link, not only the first")
                .satisfiesExactly(
                        first -> {
                            assertThat(first.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
                            assertThat(first.detail())
                                    .isEqualTo("the mission type in force `mission_type:rdev` of pack `org` is required"
                                            + " to `action:rdev/c` by another pack's edge, which names no step of the"
                                            + " type");
                            assertThat(first.violations()).singleElement().satisfies(violation -> {
                                assertThat(violation.code()).isEqualTo("mission_step_unlinked");
                                assertThat(violation.detail()).isEqualTo(first.detail());
                            });
                            assertThat(first.packs())
                                    .extracting(pack -> pack.packId().value())
                                    .containsExactly("org");
                        },
                        second -> assertThat(second.detail()).contains("action:rdev/d"));
        assertThat(closure.merged().holds(new EdgeIndex.Place(pack("org"), urn("mission_type:rdev"))))
                .as("the merge itself judges nothing")
                .isTrue();
    }

    @Test
    void aWinningTypeKeepsTheStepsOfTheVersionItLayersOverForAnotherPacksEdge() {
        Closure closure = baseRdev()
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("mission_type:rdev", "action:rdev/a"),
                        edge("mission_type:rdev", RelationType.REQUIRES, "action:rdev/a"))
                .pack(
                        "team",
                        PackLevel.PROJECT,
                        List.of("project"),
                        List.of("directive:T"),
                        edge("project::mission_type:rdev", RelationType.REQUIRES, "org::action:rdev/b"));

        assertThat(closure.merged().holds(new EdgeIndex.Place(pack("project"), urn("mission_type:rdev"))))
                .isTrue();
    }

    @Test
    void c36AnOverridesEdgeThatResolvesToItsOwnSourceOnceRePointedIsASelfOverride() {
        // Ruling D2: self_override, judged on the re-pointed index, on the read as on the registration.
        Closure closure = new Closure()
                .pack("org", PackLevel.ORG, List.of("directive:D"))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("org"),
                        List.of("directive:D"),
                        edge("directive:D", RelationType.OVERRIDES, "org::directive:D"));

        assertThat(closure.edgeIndexFaults()).singleElement().satisfies(fault -> {
            assertThat(fault.code()).isEqualTo(RefusalCode.SELF_OVERRIDE);
            assertThat(fault.detail())
                    .isEqualTo("pack `project` writes the overrides edge `directive:D` -> `project::directive:D`,"
                            + " which resolves to its own source in the merged closure");
            assertThat(fault.packs()).extracting(pack -> pack.packId().value()).containsExactly("project");
            assertThat(fault.violations()).isEmpty();
        });
        assertThat(new Closure()
                        .pack("org", PackLevel.ORG, List.of("directive:D"))
                        .pack(
                                "project",
                                PackLevel.PROJECT,
                                List.of("org"),
                                List.of("directive:X"),
                                edge("directive:X", RelationType.OVERRIDES, "org::directive:D"))
                        .edgeIndexFaults())
                .as("the twin: the edge starts at an artifact that replaces nothing")
                .isEmpty();
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
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("mission_type:M", "action:M/one", "action:M/two"),
                        linked("M", List.of("one", "two")))
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
                .pack("org", PackLevel.ORG, List.of("mission_type:M", "action:M/one"), linked("M", List.of("one")))
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
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("mission_type:M", "action:M/one", "action:M/two"),
                        linked("M", List.of("one", "two")))
                .pack(
                        "project",
                        PackLevel.PROJECT,
                        List.of("mission_type:M", "action:M/one"),
                        linked("M", List.of("one")));

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
        assertThat(index.writtenLinks())
                .extracting(EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(urn("directive:D"), true),
                        org.assertj.core.api.Assertions.tuple(urn("agent_profile:Gone"), false));
    }

    @Test
    void writtenLinksResolveABareTargetInTheSourcesPackSoATargetOnlyAnotherPackHoldsIsNotHeld() {
        EdgeIndex index = new Closure()
                .pack("builtin", List.of(), List.of("directive:D"))
                .pack(
                        "qa",
                        List.of("builtin"),
                        List.of("agent_profile:P"),
                        edge("agent_profile:P", RelationType.REQUIRES, "directive:D"))
                .index();

        assertThat(index.links()).isEmpty();
        assertThat(index.writtenLinks())
                .extracting(EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactly(org.assertj.core.api.Assertions.tuple(urn("directive:D"), false));
    }

    @Test
    void writtenLinksResolveAQualifiedTargetInTheNamedPackAndHoldItThere() {
        EdgeIndex index = new Closure()
                .pack("builtin", List.of(), List.of("directive:D"))
                .pack("extra", List.of(), List.of("directive:D"))
                .pack(
                        "qa",
                        List.of("builtin", "extra"),
                        List.of("agent_profile:P"),
                        edge("agent_profile:P", RelationType.REQUIRES, "builtin::directive:D"))
                .index();

        assertThat(index.links()).extracting(EdgeIndex.Link::target).containsExactly(urn("directive:D"));
        assertThat(index.writtenLinks())
                .extracting(EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactly(org.assertj.core.api.Assertions.tuple(urn("directive:D"), true));
    }

    /**
     * A glossary context overrides whole (ADR-0049; spec story 4, scenario 3): a later level's copy of {@code g} that
     * lacks {@code lane} takes {@code term:g/lane} away with the copy it replaces, so the edge onto it is not held. The
     * closure without the overriding pack, the control, holds it.
     */
    @Test
    void aMergedIndexHoldsATermOnlyWhenTheWinningCopyOfItsContextHoldsIt() {
        Relationship onto = edge("directive:D", RelationType.VOCABULARY, "term:g/lane");
        Closure overridden = new Closure()
                .glossaryHolder(
                        "org",
                        PackLevel.ORG,
                        List.of("directive:D", "glossary_context:g"),
                        List.of("term:g/lane"),
                        onto)
                .glossaryHolder("project", PackLevel.PROJECT, List.of("glossary_context:g"), List.of("term:g/other"));
        Closure alone = new Closure()
                .glossaryHolder(
                        "org",
                        PackLevel.ORG,
                        List.of("directive:D", "glossary_context:g"),
                        List.of("term:g/lane"),
                        onto);

        assertThat(overridden.merged().writtenLinks())
                .extracting(EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactly(org.assertj.core.api.Assertions.tuple(urn("term:g/lane"), false));
        assertThat(overridden.merged().placesOf(urn("term:g/other")))
                .containsExactly(new EdgeIndex.Place(pack("project"), urn("term:g/other")));
        assertThat(overridden.index().writtenLinks())
                .as("unmerged, every place holds what its pack holds")
                .extracting(EdgeIndex.Link::held)
                .containsExactly(true);
        assertThat(alone.merged().writtenLinks())
                .as("the control: no later level replaces g")
                .extracting(EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactly(org.assertj.core.api.Assertions.tuple(urn("term:g/lane"), true));
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
    // ---- scope owners (research R2, squad A6; C-003) ---------------------------------------------------------------

    @Test
    void theScopeOwnersOfAnArtifactAreTheActionsAndMissionTypesThatScopeItHeldOrNot() {
        EdgeIndex merged = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", "action:M/a", "action:qa/x", "directive:D", "directive:Q"),
                        edge("mission_type:M", RelationType.REQUIRES, "action:M/a"),
                        edge("action:M/a", RelationType.SCOPE, "directive:D"),
                        edge("mission_type:M", RelationType.SCOPE, "directive:D"),
                        edge("action:qa/x", RelationType.SCOPE, "directive:Q"),
                        edge("action:gone/y", RelationType.SCOPE, "directive:Q"))
                .merged();

        assertThat(merged.scopeOwners().get(urn("directive:D")))
                .containsExactly(urn("action:M/a"), urn("mission_type:M"));
        assertThat(merged.scopeOwners().get(urn("directive:Q")))
                .as("action:qa/x is no held step (no pack defines mission_type:qa) and no pack holds action:gone/y:"
                        + " both still own their target")
                .containsExactly(urn("action:gone/y"), urn("action:qa/x"));
        assertThat(merged.isScopedBy(urn("directive:D"), urn("mission_type:M"))).isTrue();
        assertThat(merged.isScopedBy(urn("directive:Q"), urn("mission_type:M"))).isFalse();
        assertThat(merged.isProjectWide(urn("directive:D"))).isFalse();
        assertThat(merged.isProjectWide(urn("directive:Q"))).isFalse();
    }

    @Test
    void aScopeEdgeToATargetItsPackDoesNotHoldStillOwnsTheTargetItNames() {
        EdgeIndex merged = new Closure()
                .pack("builtin", List.of(), List.of("directive:D"))
                .pack(
                        "qa",
                        List.of("builtin"),
                        List.of("mission_type:qa", "action:qa/x"),
                        edge("mission_type:qa", RelationType.REQUIRES, "action:qa/x"),
                        edge("action:qa/x", RelationType.SCOPE, "directive:D"))
                .merged();

        assertThat(merged.isProjectWide(urn("directive:D")))
                .as("the bare target resolves in qa, which holds no directive:D, yet the author placed D on qa/x")
                .isFalse();
    }

    @Test
    void onlyAScopeEdgeFromAnActionOrAMissionTypeOwnsItsTarget() {
        EdgeIndex merged = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", "action:M/a", "tactic:T", "directive:R", "directive:S"),
                        edge("action:M/a", RelationType.APPLIES, "directive:R"),
                        edge("mission_type:M", RelationType.REQUIRES, "action:M/a"),
                        edge("tactic:T", RelationType.REQUIRES, "directive:S"))
                .merged();

        assertThat(merged.scopeOwners()).isEmpty();
        assertThat(merged.isProjectWide(urn("directive:R"))).isTrue();
        assertThat(merged.isProjectWide(urn("directive:S")))
                .as("an edge from a tactic is no owner")
                .isTrue();
    }

    @Test
    void theScopeEdgesOfAVersionALaterLevelReplacedOwnNothing() {
        EdgeIndex merged = new Closure()
                .pack(
                        "org",
                        PackLevel.ORG,
                        List.of("mission_type:M", "action:M/a", "directive:Old"),
                        edge("mission_type:M", RelationType.REQUIRES, "action:M/a"),
                        edge("action:M/a", RelationType.SCOPE, "directive:Old"))
                .pack("project", PackLevel.PROJECT, List.of("action:M/a"))
                .merged();

        assertThat(merged.isProjectWide(urn("directive:Old"))).isTrue();
    }

    @Test
    void theScopeOwnerBuildVisitsEachEdgeOnceAndOnlyOnce() {
        java.util.concurrent.atomic.AtomicLong visits = new java.util.concurrent.atomic.AtomicLong();
        EdgeIndex merged = new Closure()
                .pack(
                        "alpha",
                        List.of("mission_type:M", "action:M/a", "directive:D", "tactic:T"),
                        edge("mission_type:M", RelationType.REQUIRES, "action:M/a"),
                        edge("action:M/a", RelationType.SCOPE, "directive:D"),
                        edge("mission_type:M", RelationType.SCOPE, "tactic:T"),
                        edge("directive:D", RelationType.REQUIRES, "tactic:T"))
                .merged(visits::incrementAndGet);

        merged.isProjectWide(urn("directive:D"));
        merged.isScopedBy(urn("tactic:T"), urn("mission_type:M"));
        merged.isProjectWide(urn("tactic:T"));

        assertThat(visits.get())
                .as("one pass over the four edges, then the memo")
                .isEqualTo(4);
    }

    @Test
    void theScopeOwnersAreWorkedOutOnceAndTheHeldUrnsListEveryArtifactInUrnOrder() {
        EdgeIndex merged = new Closure()
                .pack(
                        "alpha",
                        List.of("tactic:T", "mission_type:M", "directive:D"),
                        edge("mission_type:M", RelationType.SCOPE, "directive:D"))
                .merged();

        assertThat(merged.scopeOwners()).isSameAs(merged.scopeOwners());
        assertThat(merged.heldUrns()).isSameAs(merged.heldUrns());
        assertThat(merged.heldUrns()).containsExactly(urn("directive:D"), urn("mission_type:M"), urn("tactic:T"));
    }
}
