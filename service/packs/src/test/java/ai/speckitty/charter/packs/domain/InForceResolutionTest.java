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
    void aLockedTargetStopsTheChain() {
        InForceStates states = new Closure()
                .open("tactic:F", "tactic:T")
                .locked("tactic:L")
                .overrides("tactic:F", "tactic:L")
                .overrides("tactic:L", "tactic:T")
                .select("tactic:F", "tactic:T");

        expect(states, "tactic:T", State.ACTIVE, Reason.SELECTED);
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
}
