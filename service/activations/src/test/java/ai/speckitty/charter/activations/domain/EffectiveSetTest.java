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
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.RequiredNotInForce;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.Edge;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.MergedSnapshot.Winner;
import ai.speckitty.charter.activations.domain.SkippedReport.SkippedDeactivated;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
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
        private final Map<ArtifactRef, List<ArtifactRef>> targets = new LinkedHashMap<>();

        /** An artifact of the pack {@code alpha} at {@code org}, open, in the given state and for the given reason. */
        Scene artifact(String urn, State state, Reason reason) {
            ArtifactRef ref = ref(urn);
            winners.put(ref, new Winner("alpha", PackLevel.ORG, Policy.OPEN));
            states.put(
                    ref,
                    new ArtifactState(ref, state, reason, false, Optional.empty(), Optional.empty(), Optional.empty()));
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

        Scene satisfiedBy(String urn, String by) {
            return with(
                    urn,
                    s -> new ArtifactState(
                            s.ref(),
                            s.state(),
                            s.reason(),
                            s.required(),
                            Optional.of(ref(by)),
                            s.overriddenBy(),
                            s.bucket()));
        }

        Scene overriddenBy(String urn, String by) {
            return with(
                    urn,
                    s -> new ArtifactState(
                            s.ref(),
                            s.state(),
                            s.reason(),
                            s.required(),
                            s.satisfiedBy(),
                            Optional.of(ref(by)),
                            s.bucket()));
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
                        Optional.empty(),
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
        assertThat(set.skipped().overridden()).containsExactly(new Overridden(ref("tactic:T"), ref("tactic:E")));
    }

    @Test
    void anEnhancerWithoutATargetInForceIsInactiveAndListedWithItsSortedTargets() {
        Scene scene = new Scene()
                .artifact("skill:E", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE)
                .bucket("skill:E", Bucket.ENHANCED_TARGET_NOT_IN_FORCE);
        scene.targets.put(ref("skill:E"), List.of(ref("skill:Z"), ref("skill:A")));

        EffectiveSet set = scene.of();

        assertThat(only(set, "skill:E").reason()).isEqualTo(Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(set.skipped().enhancedTargetNotInForce())
                .containsExactly(new EnhancerWithoutTarget(ref("skill:E"), List.of(ref("skill:A"), ref("skill:Z"))));
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
        assertThat(only(set, "tactic:T").satisfiedBy()).contains(ref("tactic:E"));
    }

    @Test
    void aRequiredArtifactThatCannotStandInForceKeepsItsReasonAndIsListed() {
        Scene scene = new Scene()
                .artifact("skill:E", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE)
                .required("skill:E");
        scene.notInForce.add(new RequiredNotInForce(ref("skill:E"), Reason.ENHANCED_TARGET_NOT_IN_FORCE));

        EffectiveSet set = scene.of();

        assertThat(only(set, "skill:E").state()).isEqualTo(State.INACTIVE);
        assertThat(only(set, "skill:E").required()).isTrue();
        assertThat(set.skipped().requiredNotInForce())
                .containsExactly(new RequiredNotInForce(ref("skill:E"), Reason.ENHANCED_TARGET_NOT_IN_FORCE));
    }

    // ---- the buckets of the rules not applied, and of the ids and pins the closure no longer has ----

    @Test
    void theDenyEntriesAndTheOverridesNotAppliedAreListedAsTheyWereAnswered() {
        Scene scene = new Scene();
        scene.denies.add(new DenyNotApplied(ref("tactic:L"), DenyReason.LOCKED));
        scene.denies.add(new DenyNotApplied(ref("tactic:R"), DenyReason.REQUIRED));
        scene.overrides.add(new OverrideNotApplied(ref("tactic:L"), ref("tactic:E")));

        EffectiveSkippedReport skipped = scene.of().skipped();

        assertThat(skipped.denyNotApplied())
                .containsExactly(
                        new DenyNotApplied(ref("tactic:L"), DenyReason.LOCKED),
                        new DenyNotApplied(ref("tactic:R"), DenyReason.REQUIRED));
        assertThat(skipped.overrideNotApplied())
                .containsExactly(new OverrideNotApplied(ref("tactic:L"), ref("tactic:E")));
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
        scene.notInForce.add(new RequiredNotInForce(ref("skill:E"), Reason.ENHANCED_TARGET_NOT_IN_FORCE));
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
}
