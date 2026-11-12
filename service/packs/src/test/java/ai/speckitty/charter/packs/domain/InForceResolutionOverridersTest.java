package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceResolution.Edges;
import ai.speckitty.charter.packs.domain.InForceStates.ArtifactState;
import ai.speckitty.charter.packs.domain.InForceStates.Bucket;
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
 * Every overrider in force is named (operator ruling of 2026-11-12 on #439, option (b); spec FR-013, SC-006; rules E5, E9,
 * E14): {@code satisfiedBy}, {@code overriddenBy} and the overriders of an override not applied are lists of every artifact
 * in force that overrides the artifact, directly or through a chain, sorted by the level of each overrider's winning pack
 * (built-in, org, project) and then by the full URN in code point order. The state, the reason and the bucket do not
 * depend on the list. Each fixture gives the overriders ids whose order by URN alone differs from the ruled order, so a
 * sort by URN alone fails here.
 */
class InForceResolutionOverridersTest {

    private static Urn u(String urn) {
        return Urn.parse(urn).orElseThrow();
    }

    private static List<Urn> list(String... urns) {
        return Arrays.stream(urns).map(InForceResolutionOverridersTest::u).toList();
    }

    /** A merged closure whose winners each sit at a level of their own, and its overrides edges. */
    private static final class Closure {

        private final Map<Urn, MergedClosure.Winner> winners = new LinkedHashMap<>();
        private final Map<Urn, Set<Urn>> overrides = new LinkedHashMap<>();
        private final Map<Urn, List<String>> required = new LinkedHashMap<>();

        Closure at(PackLevel level, String... urns) {
            return held(level, PerPackPolicy.Level.OPEN, urns);
        }

        Closure lockedAt(PackLevel level, String... urns) {
            return held(
                    level, new PerPackPolicy.Level(MutabilityLevel.LOCKED, Optional.of(level.token() + "-pack")), urns);
        }

        private Closure held(PackLevel level, PerPackPolicy.Level policy, String... urns) {
            for (String text : urns) {
                Urn urn = u(text);
                winners.put(
                        urn,
                        new MergedClosure.Winner(
                                urn, new PackIdentifier(level.token()), level.token() + "-pack", level, policy));
            }
            return this;
        }

        Closure overrides(String from, String to) {
            overrides.computeIfAbsent(u(from), k -> new LinkedHashSet<>()).add(u(to));
            return this;
        }

        Closure requires(String... urns) {
            Arrays.stream(urns).forEach(urn -> required.put(u(urn), List.of("org-pack")));
            return this;
        }

        InForceStates select(String... urns) {
            Set<Urn> selected = new LinkedHashSet<>(list(urns));
            MergedClosure merged =
                    new MergedClosure(winners, List.of(), Map.of(), Map.of(), new RequiredFloor(required, Map.of()));
            return InForceResolution.resolve(
                    merged, new Edges(overrides, Map.of(), Map.of()), InForceRequest.selecting(selected));
        }
    }

    /** T at org, overridden by C at built-in, B at org and A at project: by URN alone the order would be A, B, C. */
    private static Closure threeLevels() {
        return new Closure()
                .at(PackLevel.BUILT_IN, "directive:C")
                .at(PackLevel.ORG, "directive:T", "directive:B")
                .at(PackLevel.PROJECT, "directive:A")
                .overrides("directive:A", "directive:T")
                .overrides("directive:B", "directive:T")
                .overrides("directive:C", "directive:T")
                .requires("directive:T");
    }

    private static ArtifactState stateOf(InForceStates states, String urn) {
        return states.stateOf(u(urn)).orElseThrow();
    }

    @Test
    void aSelectedArtifactHiddenByOverridersAtThreeLevelsNamesEveryOneBuiltInThenOrgThenProject() {
        InForceStates states = threeLevels().select("directive:A", "directive:B", "directive:C", "directive:T");

        ArtifactState target = stateOf(states, "directive:T");
        assertThat(target.overriddenBy()).containsExactlyElementsOf(list("directive:C", "directive:B", "directive:A"));
        assertThat(target.satisfiedBy()).containsExactlyElementsOf(list("directive:C", "directive:B", "directive:A"));
    }

    @Test
    void aRequiredArtifactTheDocumentDoesNotSelectIsSatisfiedByEveryOverriderInTheSameOrder() {
        InForceStates states = threeLevels().select("directive:A", "directive:B", "directive:C");

        ArtifactState target = stateOf(states, "directive:T");
        assertThat(target.reason()).isEqualTo(Reason.NOT_SELECTED);
        assertThat(target.satisfiedBy()).containsExactlyElementsOf(list("directive:C", "directive:B", "directive:A"));
        assertThat(target.overriddenBy()).as("overriddenBy is for the reason overridden alone").isEmpty();
    }

    @Test
    void theOverridersOfOnePackAreSortedByUrnWithinTheirLevel() {
        InForceStates states = new Closure()
                .at(PackLevel.ORG, "directive:T", "directive:E3")
                .at(PackLevel.PROJECT, "directive:E2", "directive:E1")
                .overrides("directive:E2", "directive:T")
                .overrides("directive:E1", "directive:T")
                .overrides("directive:E3", "directive:T")
                .select("directive:E2", "directive:E1", "directive:E3", "directive:T");

        assertThat(stateOf(states, "directive:T").overriddenBy())
                .containsExactlyElementsOf(list("directive:E3", "directive:E1", "directive:E2"));
    }

    @Test
    void theUrnsOfOneLevelAreComparedByCodePointNotByUtf16Unit() {
        // U+FFFF and U+1F600: as UTF-16 units the surrogate pair of U+1F600 (0xD83D) sorts first; as code points it sorts last.
        String bmp = "directive:X￿";
        String astral = "directive:X😀";
        InForceStates states = new Closure()
                .at(PackLevel.ORG, "directive:T", astral, bmp)
                .overrides(astral, "directive:T")
                .overrides(bmp, "directive:T")
                .select(astral, bmp, "directive:T");

        assertThat(stateOf(states, "directive:T").overriddenBy()).containsExactlyElementsOf(list(bmp, astral));
    }

    @Test
    void onlyTheOverridersInForceAreNamedAndOneIsAListOfOne() {
        InForceStates states = threeLevels().select("directive:B", "directive:T");

        ArtifactState target = stateOf(states, "directive:T");
        assertThat(target.overriddenBy()).containsExactly(u("directive:B"));
        assertThat(target.satisfiedBy()).containsExactly(u("directive:B"));
    }

    @Test
    void anArtifactNoOverriderHidesNamesNoneAndTheTopOfAChainIsTheOneInForce() {
        InForceStates states = new Closure()
                .at(PackLevel.ORG, "directive:F", "directive:E", "directive:T", "directive:U")
                .overrides("directive:F", "directive:E")
                .overrides("directive:E", "directive:T")
                .select("directive:F", "directive:E", "directive:T", "directive:U");

        assertThat(stateOf(states, "directive:T").overriddenBy()).containsExactly(u("directive:F"));
        assertThat(stateOf(states, "directive:E").overriddenBy()).containsExactly(u("directive:F"));
        assertThat(stateOf(states, "directive:U").overriddenBy()).isEmpty();
        assertThat(stateOf(states, "directive:F").satisfiedBy()).isEmpty();
    }

    @Test
    void theStateTheReasonAndTheBucketDoNotDependOnHowManyOverridersAreNamed() {
        InForceStates three = threeLevels().select("directive:A", "directive:B", "directive:C", "directive:T");
        InForceStates one = threeLevels().select("directive:B", "directive:T");

        for (InForceStates states : List.of(three, one)) {
            ArtifactState target = stateOf(states, "directive:T");
            assertThat(target.state()).isEqualTo(State.INACTIVE);
            assertThat(target.reason()).isEqualTo(Reason.OVERRIDDEN);
            assertThat(target.required()).isTrue();
            assertThat(target.bucket()).contains(Bucket.OVERRIDDEN);
            assertThat(states.requiredNotInForce()).isEmpty();
        }
        for (String overrider : List.of("directive:A", "directive:B", "directive:C")) {
            assertThat(stateOf(three, overrider).state()).isEqualTo(State.ACTIVE);
            assertThat(stateOf(three, overrider).reason()).isEqualTo(Reason.SELECTED);
        }
    }

    @Test
    void anOverrideOfALockedArtifactNotAppliedNamesEveryOverriderInForceOnceForTheTarget() {
        InForceStates states = new Closure()
                .lockedAt(PackLevel.ORG, "directive:L")
                .at(PackLevel.ORG, "directive:B")
                .at(PackLevel.BUILT_IN, "directive:C")
                .at(PackLevel.PROJECT, "directive:A")
                .overrides("directive:A", "directive:L")
                .overrides("directive:B", "directive:L")
                .overrides("directive:C", "directive:L")
                .select("directive:A", "directive:B", "directive:C");

        assertThat(states.overrideNotApplied())
                .containsExactly(new OverrideNotApplied(
                        u("directive:L"), list("directive:C", "directive:B", "directive:A")));
        assertThat(stateOf(states, "directive:L").state()).isEqualTo(State.ACTIVE);
        assertThat(stateOf(states, "directive:L").overriddenBy()).isEmpty();
    }
}
