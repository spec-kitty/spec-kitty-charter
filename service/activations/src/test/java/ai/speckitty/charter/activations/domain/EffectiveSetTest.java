package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.EffectiveSet.Bucket;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Suggestion;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyReason;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.EnhancerWithoutTarget;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.Overridden;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.OverrideNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.PinNotHeld;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.PruningReason;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.RequiredNotInForce;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.Edge;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.MergedSnapshot.Winner;
import ai.speckitty.charter.activations.domain.SkippedReport.SkippedDeactivated;
import ai.speckitty.charter.activations.infra.packs.PacksAvailability;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The effective set of a document over what the port answered (FR-013, FR-017): one state and one reason per artifact
 * of the merged closure, one bucket per artifact of the document that is not in force, and the summary of the whole
 * set. The answer is built by hand as values, so each test pins one row of the precedence table (data-model.md, E9) or
 * one bucket (E14) in what the set keeps of it; the resolution itself is the packs slice's.
 */
class EffectiveSetTest {

    private static NodeRef node(String urn) {
        return NodeRef.of(ref(urn));
    }

    private static ArtifactRef ref(String urn) {
        return ArtifactRef.of(Urn.parse(urn).orElseThrow()).orElseThrow();
    }

    /** A merged closure and the answer of the in-force resolution for it, under construction. */
    private static final class Scene {

        private final Map<ArtifactRef, Winner> winners = new LinkedHashMap<>();
        private final Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<ArtifactRef> stale = new ArrayList<>();
        private final List<ArtifactRef> notHeld = new ArrayList<>();
        private final List<DenyNotApplied> denies = new ArrayList<>();
        private final List<OverrideNotApplied> overrides = new ArrayList<>();
        private final List<RequiredNotInForce> notInForce = new ArrayList<>();
        private final Map<ArtifactRef, List<NodeRef>> targets = new LinkedHashMap<>();

        /** An artifact of the pack {@code alpha} at {@code org}, open, in the given state and for the given reason. */
        Scene artifact(String urn, State state, Reason reason) {
            ArtifactRef ref = ref(urn);
            winners.put(ref, new Winner("alpha", PackLevel.ORG, Policy.OPEN));
            states.put(ref, new ArtifactState(ref, state, reason, false, List.of(), List.of(), Optional.empty()));
            return this;
        }

        Scene bucket(String urn, Bucket bucket) {
            return with(
                    urn,
                    s -> new ArtifactState(
                            s.ref(),
                            s.state(),
                            s.reason(),
                            s.required(),
                            s.satisfiedBy(),
                            s.overriddenBy(),
                            Optional.of(bucket)));
        }

        Scene required(String urn) {
            return with(
                    urn,
                    s -> new ArtifactState(
                            s.ref(), s.state(), s.reason(), true, s.satisfiedBy(), s.overriddenBy(), s.bucket()));
        }

        Scene satisfiedBy(String urn, String... by) {
            return with(
                    urn,
                    s -> new ArtifactState(
                            s.ref(), s.state(), s.reason(), s.required(), refs(by), s.overriddenBy(), s.bucket()));
        }

        Scene overriddenBy(String urn, String... by) {
            return with(
                    urn,
                    s -> new ArtifactState(
                            s.ref(), s.state(), s.reason(), s.required(), s.satisfiedBy(), refs(by), s.bucket()));
        }

        private static List<ArtifactRef> refs(String... urns) {
            return Arrays.stream(urns).map(EffectiveSetTest::ref).toList();
        }

        Scene held(String urn, String packId, PackLevel level, Policy policy) {
            winners.put(ref(urn), new Winner(packId, level, policy));
            return this;
        }

        Scene suggests(String from, String to, String when, String rationale) {
            edges.add(new Edge(
                    ref(from),
                    ref(to),
                    RelationType.SUGGESTS,
                    Optional.ofNullable(when),
                    Optional.ofNullable(rationale)));
            return this;
        }

        Scene edge(String from, RelationType type, String to) {
            edges.add(new Edge(ref(from), ref(to), type, Optional.empty(), Optional.empty()));
            return this;
        }

        private Scene with(String urn, UnaryOperator<ArtifactState> change) {
            states.compute(ref(urn), (key, state) -> change.apply(state));
            return this;
        }

        EffectiveSet of(ActivationDocument document, ClosureSnapshot closure) {
            return EffectiveSet.of(
                    document,
                    closure,
                    new MergedSnapshot(winners, Map.of(), edges, List.of()),
                    new InForceAnswer(states, stale, denies, overrides, notHeld, notInForce, targets));
        }

        EffectiveSet of() {
            return of(
                    ActivationDocument.builder().build().document(),
                    ClosureSnapshots.snapshot().build());
        }
    }

    private static EffectiveArtifact only(EffectiveSet set, String urn) {
        Optional<EffectiveArtifact> found = set.artifacts().stream()
                .filter(artifact -> artifact.ref().equals(ref(urn)))
                .findFirst();
        assertThat(found).as(urn + " is listed").isPresent();
        return found.orElseThrow();
    }

    // ---- the precedence table: one state and one reason per artifact (E9) ----

    @Test
    void aLockedArtifactIsActiveWithItsProvenanceAndItsPolicy() {
        EffectiveSet set = new Scene()
                .artifact("directive:L", State.ACTIVE, Reason.LOCKED)
                .required("directive:L")
                .held("directive:L", "beta", PackLevel.BUILT_IN, Policy.LOCKED)
                .of();

        assertThat(only(set, "directive:L"))
                .isEqualTo(new EffectiveArtifact(
                        ref("directive:L"),
                        "beta",
                        PackLevel.BUILT_IN,
                        State.ACTIVE,
                        true,
                        List.of(),
                        Policy.LOCKED,
                        Reason.LOCKED,
                        Optional.empty()));
        assertThat(only(set, "directive:L").kind()).isEqualTo(ArtifactKind.DIRECTIVE);
        assertThat(only(set, "directive:L").artifactId()).isEqualTo("L");
    }

    @Test
    void aSelectedArtifactIsActiveAndAnUnselectedOneIsInactiveWithNoBucket() {
        EffectiveSet set = new Scene()
                .artifact("tactic:S", State.ACTIVE, Reason.SELECTED)
                .artifact("tactic:U", State.INACTIVE, Reason.NOT_SELECTED)
                .of();

        assertThat(only(set, "tactic:S").state()).isEqualTo(State.ACTIVE);
        assertThat(only(set, "tactic:S").reason()).isEqualTo(Reason.SELECTED);
        assertThat(only(set, "tactic:U").state()).isEqualTo(State.INACTIVE);
        assertThat(only(set, "tactic:U").reason()).isEqualTo(Reason.NOT_SELECTED);
        assertThat(set.skipped()).isEqualTo(EffectiveSkippedReport.empty());
    }

    @Test
    void aDeniedArtifactIsExcludedAndListedAsSkippedDeactivatedWithNothingRequiringIt() {
        EffectiveSet set = new Scene()
                .artifact("tactic:D", State.EXCLUDED, Reason.DEACTIVATED)
                .bucket("tactic:D", Bucket.DEACTIVATED)
                .of();

        assertThat(only(set, "tactic:D").state()).isEqualTo(State.EXCLUDED);
        assertThat(only(set, "tactic:D").reason()).isEqualTo(Reason.DEACTIVATED);
        assertThat(set.skipped().skippedDeactivated())
                .containsExactly(new SkippedDeactivated(ref("tactic:D"), false, List.of()));
    }

    @Test
    void anArtifactTheLanguageFilterExcludesIsInactiveAndListedAsLanguageFiltered() {
        EffectiveSet set = new Scene()
                .artifact("tactic:J", State.INACTIVE, Reason.LANGUAGE_FILTERED)
                .bucket("tactic:J", Bucket.LANGUAGE_FILTERED)
                .of();

        assertThat(only(set, "tactic:J").reason()).isEqualTo(Reason.LANGUAGE_FILTERED);
        assertThat(set.skipped().languageFiltered()).containsExactly(ref("tactic:J"));
    }

    @Test
    void anArtifactBoundToAMissionTypeNotInForceIsInactiveAndListedUnderThatBucket() {
        EffectiveSet set = new Scene()
                .artifact("tactic:B", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE)
                .bucket("tactic:B", Bucket.MISSION_TYPE_NOT_IN_FORCE)
                .of();

        assertThat(only(set, "tactic:B").reason()).isEqualTo(Reason.MISSION_TYPE_NOT_IN_FORCE);
        assertThat(set.skipped().missionTypeNotInForce()).containsExactly(ref("tactic:B"));
    }

    @Test
    void aMissionTypeIsListedLikeAnyOtherKindWithItsOwnState() {
        EffectiveSet set = new Scene()
                .artifact("mission_type:M", State.INACTIVE, Reason.NOT_SELECTED)
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("tactic:T", State.EXCLUDED, Reason.DEACTIVATED)
                .bucket("tactic:T", Bucket.DEACTIVATED)
                .of();

        assertThat(only(set, "mission_type:M").kind()).isEqualTo(ArtifactKind.MISSION_TYPE);
        assertThat(only(set, "mission_type:M").state()).isEqualTo(State.INACTIVE);
        assertThat(set.artifacts()).extracting(EffectiveArtifact::kind).contains(ArtifactKind.MISSION_TYPE);
    }

    @Test
    void anArtifactAnArtifactInForceOverridesIsInactiveAndListedAsOverriddenWithItsOverrider() {
        EffectiveSet set = new Scene()
                .artifact("tactic:T", State.INACTIVE, Reason.OVERRIDDEN)
                .bucket("tactic:T", Bucket.OVERRIDDEN)
                .overriddenBy("tactic:T", "tactic:E")
                .artifact("tactic:E", State.ACTIVE, Reason.SELECTED)
                .of();

        assertThat(only(set, "tactic:T").reason()).isEqualTo(Reason.OVERRIDDEN);
        assertThat(set.skipped().overridden())
                .containsExactly(new Overridden(ref("tactic:T"), List.of(ref("tactic:E"))));
    }

    @Test
    void everyOverriderTheResolutionNamesIsCarriedInItsOrderAndNeverSortedAgain() {
        // The resolution orders by the level of the winning pack, then the URN (#439): Z before A is that order, and
        // the domain keeps it rather than sorting by URN.
        EffectiveSet set = new Scene()
                .artifact("tactic:T", State.INACTIVE, Reason.OVERRIDDEN)
                .required("tactic:T")
                .satisfiedBy("tactic:T", "tactic:Z", "tactic:A")
                .overriddenBy("tactic:T", "tactic:Z", "tactic:A")
                .bucket("tactic:T", Bucket.OVERRIDDEN)
                .artifact("tactic:Z", State.ACTIVE, Reason.SELECTED)
                .artifact("tactic:A", State.ACTIVE, Reason.SELECTED)
                .of();

        assertThat(only(set, "tactic:T").satisfiedBy()).containsExactly(ref("tactic:Z"), ref("tactic:A"));
        assertThat(set.skipped().overridden())
                .containsExactly(new Overridden(ref("tactic:T"), List.of(ref("tactic:Z"), ref("tactic:A"))));
        assertThat(set.summary().required()).as("satisfied, so counted").isEqualTo(1);
    }

    @Test
    void anEnhancerWithoutATargetInForceIsInactiveAndListedWithItsSortedTargets() {
        Scene scene = new Scene()
                .artifact("skill:E", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE)
                .bucket("skill:E", Bucket.ENHANCED_TARGET_NOT_IN_FORCE);
        scene.targets.put(ref("skill:E"), List.of(node("skill:Z"), node("skill:A")));

        EffectiveSet set = scene.of();

        assertThat(only(set, "skill:E").reason()).isEqualTo(Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(set.skipped().enhancedTargetNotInForce())
                .containsExactly(new EnhancerWithoutTarget(ref("skill:E"), List.of(node("skill:A"), node("skill:Z"))));
    }

    // ---- the requirement, judged after the table ----

    @Test
    void aRequiredArtifactNothingSatisfiesIsActiveWithTheReasonRequired() {
        EffectiveSet set = new Scene()
                .artifact("tactic:R", State.ACTIVE, Reason.REQUIRED)
                .required("tactic:R")
                .of();

        assertThat(only(set, "tactic:R").required()).isTrue();
        assertThat(only(set, "tactic:R").reason()).isEqualTo(Reason.REQUIRED);
        assertThat(only(set, "tactic:R").satisfiedBy()).isEmpty();
    }

    @Test
    void aRequiredArtifactAnOverrideSatisfiesKeepsItsStateAndNamesTheSatisfier() {
        EffectiveSet set = new Scene()
                .artifact("tactic:T", State.INACTIVE, Reason.OVERRIDDEN)
                .required("tactic:T")
                .satisfiedBy("tactic:T", "tactic:E")
                .overriddenBy("tactic:T", "tactic:E")
                .bucket("tactic:T", Bucket.OVERRIDDEN)
                .artifact("tactic:E", State.ACTIVE, Reason.SELECTED)
                .of();

        assertThat(only(set, "tactic:T").state()).isEqualTo(State.INACTIVE);
        assertThat(only(set, "tactic:T").required()).isTrue();
        assertThat(only(set, "tactic:T").satisfiedBy()).containsExactly(ref("tactic:E"));
    }

    @Test
    void aRequiredArtifactThatCannotStandInForceKeepsItsReasonAndIsListed() {
        Scene scene = new Scene()
                .artifact("skill:E", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE)
                .required("skill:E");
        scene.notInForce.add(new RequiredNotInForce(ref("skill:E"), PruningReason.ENHANCED_TARGET_NOT_IN_FORCE));

        EffectiveSet set = scene.of();

        assertThat(only(set, "skill:E").state()).isEqualTo(State.INACTIVE);
        assertThat(only(set, "skill:E").required()).isTrue();
        assertThat(set.skipped().requiredNotInForce())
                .containsExactly(new RequiredNotInForce(ref("skill:E"), PruningReason.ENHANCED_TARGET_NOT_IN_FORCE));
    }

    // ---- the buckets of the rules not applied, and of the ids and pins the closure no longer has ----

    @Test
    void theDenyEntriesAndTheOverridesNotAppliedAreListedAsTheyWereAnswered() {
        Scene scene = new Scene();
        scene.denies.add(new DenyNotApplied(ref("tactic:L"), DenyReason.LOCKED));
        scene.denies.add(new DenyNotApplied(ref("tactic:R"), DenyReason.REQUIRED));
        scene.overrides.add(new OverrideNotApplied(ref("tactic:L"), List.of(ref("tactic:E"))));

        EffectiveSkippedReport skipped = scene.of().skipped();

        assertThat(skipped.denyNotApplied())
                .containsExactly(
                        new DenyNotApplied(ref("tactic:L"), DenyReason.LOCKED),
                        new DenyNotApplied(ref("tactic:R"), DenyReason.REQUIRED));
        assertThat(skipped.overrideNotApplied())
                .containsExactly(new OverrideNotApplied(ref("tactic:L"), List.of(ref("tactic:E"))));
    }

    @Test
    void theStaleIdsAndTheMustHaveItemsNobodyHoldsAreListedSortedByKindThenId() {
        Scene scene = new Scene();
        scene.stale.addAll(List.of(ref("tactic:b"), ref("directive:z"), ref("tactic:a")));
        scene.notHeld.addAll(List.of(ref("skill:S"), ref("directive:D")));

        EffectiveSkippedReport skipped = scene.of().skipped();

        assertThat(skipped.staleIds()).containsExactly(ref("directive:z"), ref("tactic:a"), ref("tactic:b"));
        assertThat(skipped.requiredNotHeld()).containsExactly(ref("directive:D"), ref("skill:S"));
    }

    @Test
    void aPinToAVersionThePackIsNotHeldAtIsListedAndAHeldPinIsNot() {
        ActivationDocument document = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("alpha", "1.0.0"), PackRef.pinned("beta", "2.0.0"), PackRef.of("gamma")))
                .build()
                .document();
        ClosureSnapshot closure = ClosureSnapshots.snapshot()
                .pack("alpha", "1.5.0")
                .pack("beta", "2.0.0")
                .pack("gamma", "3.0.0")
                .build();

        EffectiveSkippedReport skipped = new Scene().of(document, closure).skipped();

        assertThat(skipped.pinNotHeld()).containsExactly(new PinNotHeld("alpha", "1.0.0"));
    }

    // ---- the listing and the summary ----

    @Test
    void theArtifactsAreSortedByKindThenByIdInCodePointOrder() {
        EffectiveSet set = new Scene()
                .artifact("tactic:b", State.ACTIVE, Reason.SELECTED)
                .artifact("mission_type:M", State.ACTIVE, Reason.SELECTED)
                .artifact("tactic:B", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:Z", State.ACTIVE, Reason.SELECTED)
                .of();

        assertThat(set.artifacts())
                .extracting(artifact -> artifact.kind().token() + ":" + artifact.artifactId())
                .containsExactly("directive:Z", "mission_type:M", "tactic:B", "tactic:b");
    }

    @Test
    void theSummaryCountsTheWholeSetAndEveryEntryOfTheSkippedReport() {
        Scene scene = new Scene()
                .artifact("tactic:A1", State.ACTIVE, Reason.SELECTED)
                .artifact("tactic:A2", State.ACTIVE, Reason.REQUIRED)
                .required("tactic:A2")
                .artifact("directive:L", State.ACTIVE, Reason.LOCKED)
                .required("directive:L")
                .held("directive:L", "alpha", PackLevel.ORG, Policy.LOCKED)
                .artifact("tactic:I", State.INACTIVE, Reason.NOT_SELECTED)
                .artifact("tactic:X", State.EXCLUDED, Reason.DEACTIVATED)
                .bucket("tactic:X", Bucket.DEACTIVATED)
                .artifact("tactic:T", State.INACTIVE, Reason.OVERRIDDEN)
                .required("tactic:T")
                .satisfiedBy("tactic:T", "tactic:A1")
                .overriddenBy("tactic:T", "tactic:A1")
                .bucket("tactic:T", Bucket.OVERRIDDEN)
                .artifact("skill:E", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE)
                .required("skill:E");
        scene.notInForce.add(new RequiredNotInForce(ref("skill:E"), PruningReason.ENHANCED_TARGET_NOT_IN_FORCE));
        scene.stale.add(ref("tactic:gone"));
        scene.denies.add(new DenyNotApplied(ref("directive:L"), DenyReason.LOCKED));

        EffectiveSet set = scene.of();

        assertThat(set.summary())
                .isEqualTo(new EffectiveSet.Summary(3, 3, 1, 3, 1, set.skipped().count()));
        assertThat(set.skipped().count())
                .as("deactivated, overridden, requiredNotInForce, stale and denyNotApplied")
                .isEqualTo(5);
    }

    // ---- a suggestion: reached only through suggests ----

    @Test
    void anArtifactOnlyAnArtifactInForceSuggestsIsListedAsASuggestionWithTheEdgesWhenAndReason() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:R", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .edge("directive:D", RelationType.REQUIRES, "directive:R")
                .suggests("directive:D", "directive:S", "the change touches the schema", "S covers the schema checks")
                .of();

        assertThat(only(set, "directive:S").suggestion())
                .contains(new Suggestion(
                        Optional.of("the change touches the schema"), Optional.of("S covers the schema checks")));
        assertThat(only(set, "directive:R").suggestion()).isEmpty();
        assertThat(only(set, "directive:D").suggestion()).isEmpty();
    }

    @Test
    void aSuggestionWhoseEdgeStatesNeitherWhenNorReasonIsStillASuggestion() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "directive:S", null, null)
                .of();

        assertThat(only(set, "directive:S").suggestion()).contains(new Suggestion(Optional.empty(), Optional.empty()));
    }

    @Test
    void anArtifactAlsoReachedThroughAHardRelationIsNoSuggestion() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:Q", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "directive:S", "when", "why")
                .edge("directive:Q", RelationType.REQUIRES, "directive:S")
                .of();

        assertThat(only(set, "directive:S").suggestion()).isEmpty();
    }

    @Test
    void aSuggestionOfAnArtifactNotInForceIsNoSuggestionAndNeitherIsAnArtifactInForceItself() {
        EffectiveSet set = new Scene()
                .artifact("directive:Off", State.INACTIVE, Reason.NOT_SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:On", State.ACTIVE, Reason.SELECTED)
                .suggests("directive:Off", "directive:S", "when", "why")
                .suggests("directive:D", "directive:On", "when", "why")
                .of();

        assertThat(only(set, "directive:S").suggestion())
                .as("its suggester is out of force")
                .isEmpty();
        assertThat(only(set, "directive:On").suggestion())
                .as("it is in force by its own route")
                .isEmpty();
    }

    @Test
    void onlyAnArtifactTheDocumentDoesNotSelectCarriesASuggestionWhateverOtherReasonKeepsAnArtifactOut() {
        Scene scene = new Scene().artifact("directive:D", State.ACTIVE, Reason.SELECTED);
        Map<String, Reason> outOfForce = Map.of(
                "tactic:Denied", Reason.DEACTIVATED,
                "tactic:Hidden", Reason.OVERRIDDEN,
                "tactic:Filtered", Reason.LANGUAGE_FILTERED,
                "tactic:Unbound", Reason.MISSION_TYPE_NOT_IN_FORCE,
                "tactic:Ungrounded", Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        outOfForce.forEach((urn, reason) -> scene.artifact(
                        urn, reason == Reason.DEACTIVATED ? State.EXCLUDED : State.INACTIVE, reason)
                .suggests("directive:D", urn, "when", "why"));
        scene.artifact("tactic:Unselected", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "tactic:Unselected", "when", "why");

        EffectiveSet set = scene.of();

        outOfForce.forEach((urn, reason) ->
                assertThat(only(set, urn).suggestion()).as(reason.token()).isEmpty());
        assertThat(only(set, "tactic:Unselected").suggestion())
                .as("not_selected")
                .isPresent();
    }

    @Test
    void anEnhancementPullsNothingInSoAnArtifactAnArtifactInForceEnhancesIsStillASuggestion() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:E", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "directive:S", "when", "why")
                .edge("directive:E", RelationType.ENHANCES, "directive:S")
                .of();

        assertThat(only(set, "directive:S").suggestion())
                .contains(new Suggestion(Optional.of("when"), Optional.of("why")));
    }

    @Test
    void severalSuggestersGiveTheEdgeOfTheSmallestOne() {
        EffectiveSet set = new Scene()
                .artifact("directive:B", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:A", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:B", "directive:S", "from b", "b")
                .suggests("directive:A", "directive:S", "from a", "a")
                .of();

        assertThat(only(set, "directive:S").suggestion())
                .contains(new Suggestion(Optional.of("from a"), Optional.of("a")));
    }

    // ---- a suggestion the action context lists, through the one carrier ----

    @Test
    void anArtifactTheActionReachesOnlyThroughSuggestsIsListedWithTheWalksEdgeAsItsSuggestion() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "directive:S", "the list's edge", "the list's reason")
                .of();

        Optional<EffectiveArtifact> suggested =
                set.suggested(ref("directive:S"), Optional.of("the walk's edge"), Optional.of("the walk's reason"));

        assertThat(suggested).hasValueSatisfying(artifact -> {
            assertThat(artifact.ref()).isEqualTo(ref("directive:S"));
            assertThat(artifact.state()).isEqualTo(State.INACTIVE);
            assertThat(artifact.reason()).isEqualTo(Reason.NOT_SELECTED);
            assertThat(artifact.packId()).isEqualTo("alpha");
            assertThat(artifact.suggestion())
                    .as("the edge the walk reached it by, which is the nearest, not the one the list picks")
                    .contains(new Suggestion(Optional.of("the walk's edge"), Optional.of("the walk's reason")));
        });
    }

    @Test
    void anEdgeThatStatesNeitherWhenNorReasonIsStillASuggestionOfTheContext() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "directive:S", null, null)
                .of();

        assertThat(set.suggested(ref("directive:S"), Optional.empty(), Optional.empty()))
                .hasValueSatisfying(artifact ->
                        assertThat(artifact.suggestion()).contains(new Suggestion(Optional.empty(), Optional.empty())));
    }

    @Test
    void onlyAnArtifactTheDocumentDoesNotSelectIsListedAsASuggestionWhateverOtherReasonKeepsAnArtifactOut() {
        Scene scene = new Scene().artifact("directive:D", State.ACTIVE, Reason.SELECTED);
        Map<String, Reason> outOfForce = Map.of(
                "tactic:Denied", Reason.DEACTIVATED,
                "tactic:Hidden", Reason.OVERRIDDEN,
                "tactic:Filtered", Reason.LANGUAGE_FILTERED,
                "tactic:Unbound", Reason.MISSION_TYPE_NOT_IN_FORCE,
                "tactic:Ungrounded", Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        outOfForce.forEach((urn, reason) -> scene.artifact(
                        urn, reason == Reason.DEACTIVATED ? State.EXCLUDED : State.INACTIVE, reason)
                .suggests("directive:D", urn, "w", "r"));
        scene.artifact("tactic:Unselected", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "tactic:Unselected", "w", "r");
        EffectiveSet set = scene.of();

        outOfForce.forEach((urn, reason) -> assertThat(set.suggested(ref(urn), Optional.of("w"), Optional.of("r")))
                .as(reason.token())
                .isEmpty());
        assertThat(set.suggested(ref("tactic:Unselected"), Optional.of("w"), Optional.of("r")))
                .as("not_selected, in the same set")
                .isPresent();
    }

    @Test
    void anArtifactInForceAndOneTheSetDoesNotHoldAreNoSuggestion() {
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .of();

        assertThat(set.suggested(ref("directive:D"), Optional.of("w"), Optional.of("r")))
                .isEmpty();
        assertThat(set.suggested(ref("directive:Unknown"), Optional.of("w"), Optional.of("r")))
                .isEmpty();
    }

    @Test
    void anArtifactAnotherArtifactInForceRequiresOutsideTheContextIsNoSuggestionOfTheContextAndOneNobodyRequiresIs() {
        // Decision 01M70CTZP7P2EHSCK2G0RET74B: a context suggestion is an artifact the listing gives a suggestion and
        // that a
        // listed entry reaches through suggests. X is in force, outside the context, and requires S: the listing gives
        // S none.
        EffectiveSet set = new Scene()
                .artifact("directive:D", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:X", State.ACTIVE, Reason.SELECTED)
                .artifact("directive:S", State.INACTIVE, Reason.NOT_SELECTED)
                .artifact("directive:S2", State.INACTIVE, Reason.NOT_SELECTED)
                .suggests("directive:D", "directive:S", "w", "r")
                .suggests("directive:D", "directive:S2", "w2", "r2")
                .edge("directive:X", RelationType.REQUIRES, "directive:S")
                .of();

        assertThat(only(set, "directive:S").suggestion())
                .as("the listing's own answer")
                .isEmpty();
        assertThat(set.suggested(ref("directive:S"), Optional.of("w"), Optional.of("r")))
                .as("hard-reached by an artifact in force outside the context")
                .isEmpty();
        assertThat(set.suggested(ref("directive:S2"), Optional.of("w2"), Optional.of("r2")))
                .as("the control, in the same set: suggested only")
                .hasValueSatisfying(artifact -> assertThat(artifact.suggestion())
                        .contains(new Suggestion(Optional.of("w2"), Optional.of("r2"))));
    }

    // ---- the report of a write's shape, and the request ----

    @Test
    void theBucketsOnlyAWriteFillsAreEmptyAtRead() {
        EffectiveSet set = new Scene()
                .artifact("tactic:D", State.EXCLUDED, Reason.DEACTIVATED)
                .bucket("tactic:D", Bucket.DEACTIVATED)
                .of();

        SkippedReport base = set.skipped().base();

        assertThat(base.skippedByScope()).isEmpty();
        assertThat(base.unreachedBehindDeactivated()).isEmpty();
        assertThat(base.skippedShared()).isEmpty();
        assertThat(base.skippedByDepth()).isEmpty();
        assertThat(base.skippedDeactivated())
                .extracting(SkippedDeactivated::ref)
                .containsExactly(ref("tactic:D"));
    }

    @Test
    void theQueryCarriesTheSelectionTheDenyEntriesTheLanguagesAndThePinsOfEveryKind() {
        ActivationDocument document = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("alpha", "1.0.0"), PackRef.of("beta")))
                .languages(List.of("java", "python"))
                .selected(ArtifactKind.DIRECTIVE, List.of("D1"))
                .selected(ArtifactKind.MISSION_TYPE, List.of("software-dev"))
                .deactivated(ArtifactKind.TACTIC, List.of("T9"))
                .build()
                .document();

        InForceQuery query = EffectiveSet.queryOf(document);

        assertThat(query.selected()).containsExactlyInAnyOrder(ref("directive:D1"), ref("mission_type:software-dev"));
        assertThat(query.deactivated()).containsExactly(ref("tactic:T9"));
        assertThat(query.languages()).contains(Set.of("java", "python"));
        assertThat(query.pins()).containsExactly(Map.entry("alpha", "1.0.0"));
    }

    @Test
    void aDocumentWithNoLanguagesAsksForNoFilterWhichIsNotAFilterNamingNoLanguage() {
        InForceQuery query =
                EffectiveSet.queryOf(ActivationDocument.builder().build().document());

        assertThat(query.languages()).isEmpty();
        assertThat(query.selected()).isEmpty();
        assertThat(query.pins()).isEmpty();
    }

    // ---- OD-6: the effective set of a step enhancer, through the session over a real closure -----------------------

    /**
     * The effective set of a document over a real closure: one pack holding mission type {@code M}, its step
     * {@code action:M/s} and the step enhancement {@code x}, which enhances the step. The pack declares no binding for
     * the step enhancement, so only the amended rule E6 can bind it to {@code M}.
     */
    private static EffectiveSet effectiveSetOverAStepEnhancer(List<String> selectedMissionTypes) {
        ConfiguredPack configured = new ConfiguredPack(new PackIdentifier("alpha"), PackLevel.ORG);
        PackDescriptor descriptor = new PackDescriptor(
                "id-alpha",
                "1.0.0",
                Optional.empty(),
                "alpha",
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.<PackDependency>of());
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return descriptor;
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.of(descriptor);
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor changed) {
                throw new UnsupportedOperationException("the closure never writes");
            }
        };
        Relationship enhances = new Relationship(
                Urn.parse("step_enhancement:x").orElseThrow(),
                Urn.parse("action:M/s").orElseThrow(),
                RelationType.ENHANCES,
                Optional.empty(),
                Optional.empty());
        PackCompositionFacts facts = new PackCompositionFacts(
                List.of(),
                List.of("mission_type:M", "action:M/s", "step_enhancement:x"),
                List.of(),
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(enhances),
                Map.of());
        PackCatalogRepository catalog = () -> List.of(configured);
        ConsistentRead read = new ConsistentRead(() -> "sha256:" + "1".repeat(64));
        OfferingReadService offering = new OfferingReadService(catalog, repository, pack -> facts, read);
        var session = new PacksAvailability(new ClosureQueries(offering, catalog, read)).session(Optional.empty());
        ActivationDocument document = ActivationDocument.builder()
                .selected(ArtifactKind.STEP_ENHANCEMENT, List.of("x"))
                .selected(ArtifactKind.MISSION_TYPE, selectedMissionTypes)
                .build()
                .document();
        return EffectiveSet.of(
                document, session.snapshot(), session.merged(), session.inForce(EffectiveSet.queryOf(document)));
    }

    @Test
    void aSelectedStepEnhancerIsInForceWhileTheMissionTypeOfItsStepIsAndNeitherListsNorWarnsAboutIt() {
        EffectiveSet set = effectiveSetOverAStepEnhancer(List.of("M"));

        assertThat(only(set, "step_enhancement:x").state()).isEqualTo(State.ACTIVE);
        assertThat(only(set, "step_enhancement:x").reason()).isEqualTo(Reason.SELECTED);
        assertThat(set.skipped().enhancedTargetNotInForce())
                .as("the bucket no longer carries a step enhancer whose mission type is in force")
                .isEmpty();
        assertThat(set.skipped().missionTypeNotInForce()).isEmpty();
    }

    @Test
    void aSelectedStepEnhancerWhoseMissionTypeIsNotSelectedIsListedUnderTheMissionTypeBucket() {
        EffectiveSet set = effectiveSetOverAStepEnhancer(List.of());

        assertThat(only(set, "step_enhancement:x").state()).isEqualTo(State.INACTIVE);
        assertThat(only(set, "step_enhancement:x").reason()).isEqualTo(Reason.MISSION_TYPE_NOT_IN_FORCE);
        assertThat(set.skipped().missionTypeNotInForce()).containsExactly(ref("step_enhancement:x"));
        assertThat(set.skipped().enhancedTargetNotInForce()).isEmpty();
    }
}
