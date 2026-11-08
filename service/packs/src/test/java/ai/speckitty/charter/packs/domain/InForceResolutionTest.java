package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceResolution.Edges;
import ai.speckitty.charter.packs.domain.InForceStates.Bucket;
import ai.speckitty.charter.packs.domain.InForceStates.DenyNotApplied;
import ai.speckitty.charter.packs.domain.InForceStates.DenyReason;
import ai.speckitty.charter.packs.domain.InForceStates.OverrideNotApplied;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import ai.speckitty.charter.packs.domain.InForceStates.State;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The in-force resolution, one test per rule of the composition and activation rules page (E1 to E14, C27, C28, I9 to
 * I11) and per ruled answer (A2, A3, A8, the floor enhancer, the bound floor, the mutual override). Each test builds the
 * merged closure by hand: the strata are pure, so no pack is composed.
 */
class InForceResolutionTest {

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static Set<Urn> urns(String... urns) {
        Set<Urn> set = new LinkedHashSet<>();
        Arrays.stream(urns).forEach(urn -> set.add(u(urn)));
        return set;
    }

    /** A merged closure and its edges under construction. */
    private static final class Closure {

        private final Map<Urn, MergedClosure.Winner> winners = new LinkedHashMap<>();
        private final Map<Urn, Set<Urn>> overrides = new LinkedHashMap<>();
        private final Map<Urn, Set<Urn>> enhances = new LinkedHashMap<>();
        private final Map<Urn, Urn> bindings = new LinkedHashMap<>();
        private final Map<Urn, Set<String>> languages = new LinkedHashMap<>();
        private final Map<Urn, List<String>> required = new LinkedHashMap<>();
        private final Map<Urn, List<String>> unheld = new LinkedHashMap<>();

        Closure level(MutabilityLevel level, String... urns) {
            for (String text : urns) {
                Urn urn = u(text);
                PerPackPolicy.Level policy = level == MutabilityLevel.OPEN
                        ? PerPackPolicy.Level.OPEN
                        : new PerPackPolicy.Level(level, Optional.of("id-pack"));
                winners.put(
                        urn,
                        new MergedClosure.Winner(urn, new PackIdentifier("pack"), "id-pack", PackLevel.ORG, policy));
            }
            return this;
        }

        Closure open(String... urns) {
            return level(MutabilityLevel.OPEN, urns);
        }

        Closure locked(String... urns) {
            return level(MutabilityLevel.LOCKED, urns);
        }

        Closure extendable(String... urns) {
            return level(MutabilityLevel.EXTENDABLE, urns);
        }

        Closure overrides(String from, String to) {
            overrides.computeIfAbsent(u(from), k -> new LinkedHashSet<>()).add(u(to));
            return this;
        }

        Closure enhances(String from, String to) {
            enhances.computeIfAbsent(u(from), k -> new LinkedHashSet<>()).add(u(to));
            return this;
        }

        Closure bound(String urn, String missionType) {
            bindings.put(u(urn), u(missionType));
            return this;
        }

        Closure requires(String... urns) {
            Arrays.stream(urns).forEach(urn -> required.put(u(urn), List.of("id-pack")));
            return this;
        }

        Closure languages(String urn, String... declared) {
            languages.put(u(urn), Set.of(declared));
            return this;
        }

        Closure unheld(String... urns) {
            Arrays.stream(urns).forEach(urn -> unheld.put(u(urn), List.of("id-pack")));
            return this;
        }

        InForceStates resolve(InForceRequest request) {
            MergedClosure merged =
                    new MergedClosure(winners, List.of(), languages, Map.of(), new RequiredFloor(required, unheld));
            return InForceResolution.resolve(merged, new Edges(overrides, enhances, bindings), request);
        }

        InForceStates select(String... urns) {
            return resolve(InForceRequest.selecting(urns(urns)));
        }

        InForceStates resolve(Set<Urn> selected, Set<Urn> denied) {
            return resolve(new InForceRequest(selected, denied, Optional.empty(), Map.of()));
        }
    }

    private static void expect(InForceStates states, String urn, State state, Reason reason) {
        assertThat(states.stateOf(u(urn))).as(urn).hasValueSatisfying(actual -> {
            assertThat(actual.state()).as(urn + " state").isEqualTo(state);
            assertThat(actual.reason()).as(urn + " reason").isEqualTo(reason);
        });
    }

    private static void expectSatisfiedBy(InForceStates states, String urn, String by) {
        assertThat(states.stateOf(u(urn)).orElseThrow().satisfiedBy())
                .as(urn + " satisfiedBy")
                .contains(u(by));
    }

    // ---- stratum 1: the candidate set (E1, E2, E3, E9) ----

    @Test
    void aSelectedArtifactWithinScopeIsInForceBySelection() {
        InForceStates states = new Closure().open("tactic:T", "tactic:U").select("tactic:T");

        expect(states, "tactic:T", State.ACTIVE, Reason.SELECTED);
        expect(states, "tactic:U", State.INACTIVE, Reason.NOT_SELECTED);
        assertThat(states.stateOf(u("tactic:U")).orElseThrow().bucket()).isEmpty();
    }

    @Test
    void everyKindIsACandidateWithNoKindGate() {
        InForceStates states = new Closure()
                .open("mission_type:M", "skill:S", "glossary_pack:G", "directive:D")
                .select("mission_type:M", "skill:S", "glossary_pack:G", "directive:D");

        states.states()
                .values()
                .forEach(state ->
                        assertThat(state.active()).as(state.urn().toString()).isTrue());
    }

    @Test
    void aDeniedArtifactIsExcludedAndDeactivated() {
        InForceStates states = new Closure().open("tactic:T").resolve(urns("tactic:T"), urns("tactic:T"));

        expect(states, "tactic:T", State.EXCLUDED, Reason.DEACTIVATED);
        assertThat(states.stateOf(u("tactic:T")).orElseThrow().bucket()).contains(Bucket.DEACTIVATED);
    }

    @Test
    void aDenyEntryOnAnUnselectedArtifactStillExcludesIt() {
        InForceStates states = new Closure().open("tactic:T").resolve(urns(), urns("tactic:T"));

        expect(states, "tactic:T", State.EXCLUDED, Reason.DEACTIVATED);
    }

    @Test
    void theLanguageFilterExcludesAnArtifactThatDeclaresOtherLanguages() {
        Closure closure = new Closure()
                .open("tactic:Go", "tactic:Java", "tactic:Any")
                .languages("tactic:Go", "go")
                .languages("tactic:Java", "java");
        InForceRequest request = new InForceRequest(
                urns("tactic:Go", "tactic:Java", "tactic:Any"), Set.of(), Optional.of(Set.of("go")), Map.of());

        InForceStates states = closure.resolve(request);

        expect(states, "tactic:Go", State.ACTIVE, Reason.SELECTED);
        expect(states, "tactic:Java", State.INACTIVE, Reason.LANGUAGE_FILTERED);
        expect(states, "tactic:Any", State.ACTIVE, Reason.SELECTED);
        assertThat(states.stateOf(u("tactic:Java")).orElseThrow().bucket()).contains(Bucket.LANGUAGE_FILTERED);
    }

    @Test
    void noLanguageFilterAdmitsEveryLanguage() {
        InForceStates states = new Closure()
                .open("tactic:Java")
                .languages("tactic:Java", "java")
                .select("tactic:Java");

        expect(states, "tactic:Java", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void aLockedArtifactIsInForceWhateverTheDocumentSays() {
        Closure closure = new Closure().locked("directive:L").languages("directive:L", "java");
        InForceRequest request = new InForceRequest(Set.of(), urns("directive:L"), Optional.of(Set.of("go")), Map.of());

        InForceStates states = closure.resolve(request);

        expect(states, "directive:L", State.ACTIVE, Reason.LOCKED);
        assertThat(states.denyNotApplied()).containsExactly(new DenyNotApplied(u("directive:L"), DenyReason.LOCKED));
    }

    @Test
    void anIdTheClosureDoesNotHoldIsStaleAndHasNoState() {
        InForceStates states =
                new Closure().open("tactic:T").resolve(urns("tactic:T", "tactic:Gone"), urns("directive:AlsoGone"));

        assertThat(states.staleIds()).containsExactly(u("directive:AlsoGone"), u("tactic:Gone"));
        assertThat(states.stateOf(u("tactic:Gone"))).isEmpty();
    }

    @Test
    void aMustHaveItemNoPackHoldsIsReportedNotHeld() {
        InForceStates states =
                new Closure().open("tactic:T").unheld("tactic:Ghost").select();

        assertThat(states.requiredNotHeld()).containsExactly(u("tactic:Ghost"));
    }

    // ---- stratum 2: overrides (E4, A3, C27, C28) ----

    @Test
    void anOverriderInForceHidesItsTarget() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:E", "tactic:T");

        expect(states, "tactic:E", State.ACTIVE, Reason.SELECTED);
        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
        assertThat(states.stateOf(u("tactic:T")).orElseThrow().bucket()).contains(Bucket.OVERRIDDEN);
    }

    @Test
    void anOverriddenArtifactNamesTheOverriderInForce() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:E", "tactic:T");

        assertThat(states.stateOf(u("tactic:T")).orElseThrow().overriddenBy()).contains(u("tactic:E"));
        assertThat(states.stateOf(u("tactic:E")).orElseThrow().overriddenBy()).isEmpty();
    }

    @Test
    void anOverriddenArtifactNamesTheTopOfTheChainAndTheSmallestOfSeveral() {
        InForceStates chain = new Closure()
                .open("tactic:F", "tactic:E", "tactic:T")
                .overrides("tactic:F", "tactic:E")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:F", "tactic:E", "tactic:T");
        InForceStates two = new Closure()
                .open("tactic:E2", "tactic:E1", "tactic:T")
                .overrides("tactic:E2", "tactic:T")
                .overrides("tactic:E1", "tactic:T")
                .select("tactic:E1", "tactic:E2", "tactic:T");

        assertThat(chain.stateOf(u("tactic:E")).orElseThrow().overriddenBy()).contains(u("tactic:F"));
        assertThat(chain.stateOf(u("tactic:T")).orElseThrow().overriddenBy()).contains(u("tactic:F"));
        assertThat(two.stateOf(u("tactic:T")).orElseThrow().overriddenBy()).contains(u("tactic:E1"));
    }

    @Test
    void anArtifactOutOfForceForAnotherReasonNamesNoOverrider() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T", "tactic:U")
                .overrides("tactic:E", "tactic:T")
                .resolve(urns("tactic:E"), urns("tactic:U"));

        assertThat(states.stateOf(u("tactic:T")).orElseThrow().overriddenBy()).isEmpty();
        assertThat(states.stateOf(u("tactic:U")).orElseThrow().overriddenBy()).isEmpty();
    }

    @Test
    void overridesComposeTransitivelyFromTheTop() {
        InForceStates states = new Closure()
                .open("tactic:F", "tactic:E", "tactic:T")
                .overrides("tactic:F", "tactic:E")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:F", "tactic:E", "tactic:T");

        expect(states, "tactic:F", State.ACTIVE, Reason.SELECTED);
        expect(states, "tactic:E", State.INACTIVE, Reason.OVERRIDDEN);
        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
    }

    @Test
    void aHiddenOverriderStillHidesWhatItOverrides() {
        InForceStates states = new Closure()
                .open("tactic:F", "tactic:E", "tactic:T")
                .overrides("tactic:F", "tactic:E")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:F", "tactic:T");

        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
    }

    @Test
    void anOverriderThatIsNotInForceHidesNothing() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:T");

        expect(states, "tactic:T", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void aLockedTargetStaysAndTheOverrideIsReported() {
        InForceStates states = new Closure()
                .open("tactic:E")
                .locked("tactic:L")
                .overrides("tactic:E", "tactic:L")
                .select("tactic:E");

        expect(states, "tactic:L", State.ACTIVE, Reason.LOCKED);
        assertThat(states.overrideNotApplied()).containsExactly(new OverrideNotApplied(u("tactic:L"), u("tactic:E")));
    }

    @Test
    void anExtendableTargetIsNotHiddenEither() {
        InForceStates states = new Closure()
                .open("tactic:E")
                .extendable("tactic:X")
                .overrides("tactic:E", "tactic:X")
                .select("tactic:E", "tactic:X");

        expect(states, "tactic:X", State.ACTIVE, Reason.SELECTED);
        assertThat(states.overrideNotApplied()).containsExactly(new OverrideNotApplied(u("tactic:X"), u("tactic:E")));
    }

    @Test
    void aLockedArtifactInForceStillHidesWhatItOverrides() {
        InForceStates states = new Closure()
                .open("tactic:T")
                .locked("tactic:L")
                .overrides("tactic:L", "tactic:T")
                .select("tactic:T");

        expect(states, "tactic:L", State.ACTIVE, Reason.LOCKED);
        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
    }

    @Test
    void anOverrideCycleAmongTheCandidatesIsRefusedAsUnreadable() {
        Closure closure = new Closure()
                .open("tactic:A", "tactic:B")
                .overrides("tactic:A", "tactic:B")
                .overrides("tactic:B", "tactic:A");

        assertThatThrownBy(() -> closure.select("tactic:A", "tactic:B"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void anOverrideCycleNothingReachesIsNotJudged() {
        Closure closure = new Closure()
                .open("tactic:A", "tactic:B", "tactic:T")
                .overrides("tactic:A", "tactic:B")
                .overrides("tactic:B", "tactic:A");

        assertThat(closure.select("tactic:T")
                        .stateOf(u("tactic:T"))
                        .orElseThrow()
                        .active())
                .isTrue();
    }

    // ---- stratum 3: the floor, to a fixed point (E5, E13, I10, A2, A3, C28) ----

    @Test
    void aRequiredArtifactNothingSatisfiesIsPutInForce() {
        InForceStates states =
                new Closure().open("tactic:T").requires("tactic:T").select();

        expect(states, "tactic:T", State.ACTIVE, Reason.REQUIRED);
        assertThat(states.stateOf(u("tactic:T")).orElseThrow().required()).isTrue();
    }

    @Test
    void aRequiredArtifactTheDocumentSelectsIsInForceAsRequired() {
        InForceStates states =
                new Closure().open("tactic:T").requires("tactic:T").select("tactic:T");

        expect(states, "tactic:T", State.ACTIVE, Reason.REQUIRED);
    }

    @Test
    void aDenyEntryOnARequiredArtifactNothingSatisfiesIsNotApplied() {
        InForceStates states =
                new Closure().open("tactic:T").requires("tactic:T").resolve(urns(), urns("tactic:T"));

        expect(states, "tactic:T", State.ACTIVE, Reason.REQUIRED);
        assertThat(states.denyNotApplied()).containsExactly(new DenyNotApplied(u("tactic:T"), DenyReason.REQUIRED));
    }

    @Test
    void aRequiredArtifactTheLanguageFilterExcludesIsStillPutInForce() {
        Closure closure = new Closure().open("tactic:T").requires("tactic:T").languages("tactic:T", "java");
        InForceRequest request = new InForceRequest(urns("tactic:T"), Set.of(), Optional.of(Set.of("go")), Map.of());

        expect(closure.resolve(request), "tactic:T", State.ACTIVE, Reason.REQUIRED);
    }

    @Test
    void aRequiredArtifactAnActiveOverrideSatisfiesIsNotPutInForce() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T")
                .requires("tactic:T")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:E", "tactic:T");

        expect(states, "tactic:E", State.ACTIVE, Reason.SELECTED);
        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
        expectSatisfiedBy(states, "tactic:T", "tactic:E");
        assertThat(states.denyNotApplied()).isEmpty();
    }

    @Test
    void aDenyEntryOnARequiredArtifactAnOverrideSatisfiesStands() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T")
                .requires("tactic:T")
                .overrides("tactic:E", "tactic:T")
                .resolve(urns("tactic:E"), urns("tactic:T"));

        expect(states, "tactic:T", State.EXCLUDED, Reason.DEACTIVATED);
        expectSatisfiedBy(states, "tactic:T", "tactic:E");
        assertThat(states.denyNotApplied()).isEmpty();
    }

    @Test
    void anEnhancerNeverSatisfiesARequirement() {
        InForceStates states = new Closure()
                .open("tactic:T", "tactic:X")
                .requires("tactic:T")
                .enhances("tactic:X", "tactic:T")
                .select("tactic:X");

        expect(states, "tactic:T", State.ACTIVE, Reason.REQUIRED);
        expect(states, "tactic:X", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void aChainOfOverridesSatisfiesARequirementOnItsEnd() {
        InForceStates states = new Closure()
                .open("tactic:F", "tactic:E", "tactic:T")
                .requires("tactic:T")
                .overrides("tactic:F", "tactic:E")
                .overrides("tactic:E", "tactic:T")
                .select("tactic:F", "tactic:T");

        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
        expectSatisfiedBy(states, "tactic:T", "tactic:F");
    }

    @Test
    void anOverriderPutInForceByTheRequirementHidesItsTargets() {
        InForceStates states = new Closure()
                .open("tactic:U", "tactic:X")
                .requires("tactic:U")
                .overrides("tactic:U", "tactic:X")
                .select("tactic:X");

        expect(states, "tactic:U", State.ACTIVE, Reason.REQUIRED);
        expect(states, "tactic:X", State.INACTIVE, Reason.OVERRIDDEN);
    }

    @Test
    void aRequiredOverriderOfAnotherRequiredArtifactSatisfiesIt() {
        InForceStates states = new Closure()
                .open("tactic:R1", "tactic:R2")
                .requires("tactic:R1", "tactic:R2")
                .overrides("tactic:R1", "tactic:R2")
                .select();

        expect(states, "tactic:R1", State.ACTIVE, Reason.REQUIRED);
        expect(states, "tactic:R2", State.INACTIVE, Reason.NOT_SELECTED);
        expectSatisfiedBy(states, "tactic:R2", "tactic:R1");
    }

    @Test
    void anOverriderTheDocumentDeniesDoesNotSatisfyARequirement() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T")
                .requires("tactic:T")
                .overrides("tactic:E", "tactic:T")
                .resolve(urns("tactic:E"), urns("tactic:E"));

        expect(states, "tactic:T", State.ACTIVE, Reason.REQUIRED);
    }

    @Test
    void aSatisfiedRequirementNamesTheSmallestOverriderInForce() {
        InForceStates states = new Closure()
                .open("tactic:E2", "tactic:E1", "tactic:T")
                .requires("tactic:T")
                .overrides("tactic:E2", "tactic:T")
                .overrides("tactic:E1", "tactic:T")
                .select("tactic:E1", "tactic:E2", "tactic:T");

        expectSatisfiedBy(states, "tactic:T", "tactic:E1");
    }

    @Test
    void aLockedRequiredArtifactIsLockedNotRequired() {
        InForceStates states =
                new Closure().locked("tactic:L").requires("tactic:L").select();

        expect(states, "tactic:L", State.ACTIVE, Reason.LOCKED);
    }

    /** A2: the requirement is judged on the final set, to a fixed point, with overrides composed transitively (A3). */
    @Test
    void theRequirementIsJudgedOnTheFinalSetAndTheAnswerIsExactlyTheRuledOne() {
        InForceStates states = new Closure()
                .open("tactic:E", "tactic:T", "tactic:U")
                .overrides("tactic:E", "tactic:T")
                .overrides("tactic:U", "tactic:E")
                .requires("tactic:T", "tactic:U")
                .select("tactic:E", "tactic:T");

        expect(states, "tactic:U", State.ACTIVE, Reason.REQUIRED);
        expect(states, "tactic:E", State.INACTIVE, Reason.OVERRIDDEN);
        expect(states, "tactic:T", State.INACTIVE, Reason.OVERRIDDEN);
        expectSatisfiedBy(states, "tactic:T", "tactic:U");
        assertThat(states.stateOf(u("tactic:E")).orElseThrow().satisfiedBy()).isEmpty();
    }

    @Test
    void twoRequiredArtifactsOverridingEachOtherAreRefusedAsUnreadable() {
        Closure closure = new Closure()
                .open("tactic:A", "tactic:B")
                .requires("tactic:A", "tactic:B")
                .overrides("tactic:A", "tactic:B")
                .overrides("tactic:B", "tactic:A");

        assertThatThrownBy(() -> closure.select())
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    // ---- stratum 4: enhancer and binding pruning (E6, A7, A8, I11) ----

    @Test
    void anEnhancerWithATargetInForceIsInForce() {
        InForceStates states = new Closure()
                .open("tactic:T", "tactic:X")
                .enhances("tactic:X", "tactic:T")
                .select("tactic:T", "tactic:X");

        expect(states, "tactic:X", State.ACTIVE, Reason.SELECTED);
        expect(states, "tactic:T", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void anEnhancerWhoseTargetIsNotInForceIsPrunedAndReported() {
        InForceStates states = new Closure()
                .open("tactic:T", "tactic:X")
                .enhances("tactic:X", "tactic:T")
                .select("tactic:X");

        expect(states, "tactic:X", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(states.stateOf(u("tactic:X")).orElseThrow().bucket()).contains(Bucket.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(states.enhancedTargets()).containsEntry(u("tactic:X"), List.of(u("tactic:T")));
    }

    @Test
    void anEnhancerIsInForceOnlyByItsOwnRoute() {
        InForceStates states = new Closure()
                .open("tactic:T", "tactic:X")
                .enhances("tactic:X", "tactic:T")
                .select("tactic:T");

        expect(states, "tactic:X", State.INACTIVE, Reason.NOT_SELECTED);
    }

    @Test
    void thePruningRepeatsDownAChainOfEnhancers() {
        Closure closure = new Closure()
                .open("tactic:T", "tactic:E1", "tactic:E2")
                .enhances("tactic:E1", "tactic:T")
                .enhances("tactic:E2", "tactic:E1");

        InForceStates grounded = closure.select("tactic:T", "tactic:E1", "tactic:E2");
        InForceStates ungrounded = closure.select("tactic:E1", "tactic:E2");

        expect(grounded, "tactic:E2", State.ACTIVE, Reason.SELECTED);
        expect(ungrounded, "tactic:E1", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        expect(ungrounded, "tactic:E2", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
    }

    @Test
    void aRingOfEnhancersHoldsNothingUp() {
        InForceStates states = new Closure()
                .open("tactic:E1", "tactic:E2", "tactic:E3")
                .enhances("tactic:E1", "tactic:E2")
                .enhances("tactic:E2", "tactic:E1")
                .enhances("tactic:E3", "tactic:E3")
                .select("tactic:E1", "tactic:E2", "tactic:E3");

        states.states()
                .values()
                .forEach(state ->
                        expect(states, state.urn().toString(), State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE));
    }

    @Test
    void anEnhancerWithOneTargetInForceOfTwoStays() {
        InForceStates states = new Closure()
                .open("tactic:T1", "tactic:T2", "tactic:X")
                .enhances("tactic:X", "tactic:T1")
                .enhances("tactic:X", "tactic:T2")
                .select("tactic:T2", "tactic:X");

        expect(states, "tactic:X", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void aRequiredEnhancerWithoutATargetIsDroppedAndReported() {
        InForceStates states = new Closure()
                .open("tactic:T", "tactic:X")
                .enhances("tactic:X", "tactic:T")
                .requires("tactic:X")
                .select();

        expect(states, "tactic:X", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(states.stateOf(u("tactic:X")).orElseThrow().required()).isTrue();
        assertThat(states.requiredNotInForce())
                .containsExactly(
                        new InForceStates.RequiredNotInForce(u("tactic:X"), Reason.ENHANCED_TARGET_NOT_IN_FORCE));
    }

    @Test
    void aLockedEnhancerWithoutATargetIsDroppedAndReportedToo() {
        InForceStates states = new Closure()
                .open("tactic:T")
                .locked("tactic:X")
                .enhances("tactic:X", "tactic:T")
                .select();

        expect(states, "tactic:X", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        assertThat(states.requiredNotInForce())
                .containsExactly(
                        new InForceStates.RequiredNotInForce(u("tactic:X"), Reason.ENHANCED_TARGET_NOT_IN_FORCE));
    }

    @Test
    void aStepOfAMissionTypeNotInForceIsOut() {
        InForceStates states =
                new Closure().open("mission_type:m", "action:m/s").select("action:m/s");

        expect(states, "action:m/s", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
        assertThat(states.stateOf(u("action:m/s")).orElseThrow().bucket()).contains(Bucket.MISSION_TYPE_NOT_IN_FORCE);
    }

    @Test
    void aStepStaysWhileItsMissionTypeIsInForce() {
        InForceStates states =
                new Closure().open("mission_type:m", "action:m/s", "action:m/t").select("mission_type:m", "action:m/s");

        expect(states, "action:m/s", State.ACTIVE, Reason.SELECTED);
        expect(states, "action:m/t", State.INACTIVE, Reason.NOT_SELECTED);
    }

    @Test
    void anArtifactBoundToAMissionTypeIsInForceOnlyWhileItIs() {
        Closure closure = new Closure().open("mission_type:m", "tactic:B").bound("tactic:B", "mission_type:m");

        expect(closure.select("tactic:B"), "tactic:B", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
        expect(closure.select("tactic:B", "mission_type:m"), "tactic:B", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void aRequiredStepOfAMissionTypeNotInForceIsDroppedAndReported() {
        InForceStates states = new Closure()
                .open("mission_type:m", "action:m/s")
                .requires("action:m/s")
                .select();

        expect(states, "action:m/s", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
        assertThat(states.requiredNotInForce())
                .containsExactly(
                        new InForceStates.RequiredNotInForce(u("action:m/s"), Reason.MISSION_TYPE_NOT_IN_FORCE));
    }

    @Test
    void aRequiredMissionTypeKeepsItsSelectedStepsInForce() {
        InForceStates states = new Closure()
                .open("mission_type:m", "action:m/s")
                .requires("mission_type:m")
                .select("action:m/s");

        expect(states, "mission_type:m", State.ACTIVE, Reason.REQUIRED);
        expect(states, "action:m/s", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void anEnhancerOfAStepFollowsTheStepsMissionType() {
        InForceStates states = new Closure()
                .open("mission_type:m", "action:m/s", "tactic:X")
                .enhances("tactic:X", "action:m/s")
                .select("action:m/s", "tactic:X");

        expect(states, "action:m/s", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
        expect(states, "tactic:X", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
    }

    /** The floor is no exception to the binding: F is pruned, and the fixed point judges T again. */
    @Test
    void anOverriderPrunedByItsBindingNoLongerSatisfiesTheRequirement() {
        InForceStates states = new Closure()
                .open("mission_type:m", "tactic:F", "tactic:T")
                .bound("tactic:F", "mission_type:m")
                .overrides("tactic:F", "tactic:T")
                .requires("tactic:T")
                .select("tactic:F");

        expect(states, "tactic:F", State.INACTIVE, Reason.MISSION_TYPE_NOT_IN_FORCE);
        expect(states, "tactic:T", State.ACTIVE, Reason.REQUIRED);
        assertThat(states.stateOf(u("tactic:T")).orElseThrow().satisfiedBy()).isEmpty();
    }

    @Test
    void anOverriderPrunedAsAnEnhancerWithdrawsItsOverride() {
        InForceStates states = new Closure()
                .open("tactic:A", "tactic:X", "tactic:T")
                .enhances("tactic:X", "tactic:A")
                .overrides("tactic:X", "tactic:T")
                .select("tactic:X", "tactic:T");

        expect(states, "tactic:X", State.INACTIVE, Reason.ENHANCED_TARGET_NOT_IN_FORCE);
        expect(states, "tactic:T", State.ACTIVE, Reason.SELECTED);
    }

    @Test
    void anOverriderThatOverridesItsOwnMissionTypeHasNoStableAnswerAndIsRefused() {
        Closure closure = new Closure()
                .open("mission_type:m", "tactic:F")
                .bound("tactic:F", "mission_type:m")
                .overrides("tactic:F", "mission_type:m");

        assertThatThrownBy(() -> closure.select("tactic:F", "mission_type:m"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }
}
