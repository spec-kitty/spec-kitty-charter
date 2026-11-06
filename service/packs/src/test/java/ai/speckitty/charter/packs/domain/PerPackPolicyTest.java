package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The level along a closure (ADR-0012 decision 3, ADR-0063 decision 3): the highest level any pack of the closure sets
 * wins, and the pack that first reached it is reported. Each case is run in both closure orders, so "highest wins"
 * cannot pass as "last wins".
 */
class PerPackPolicyTest {

    private static final String BUILTIN = "BUILTIN";
    private static final String ORG = "ORG";
    private static final Urn D1 = new Urn(NodeKind.DIRECTIVE, "D1");

    private static MutabilityPolicySource entry(Urn urn, MutabilityLevel level) {
        return MutabilityPolicySource.builder().entry(urn, level).build();
    }

    @Test
    void aLockByALaterPackIsTheAnswerAndReversingTheOrderDoesNotLowerIt() {
        PerPackPolicy policy = new PerPackPolicy(Map.of(
                BUILTIN, entry(D1, MutabilityLevel.OPEN),
                ORG, entry(D1, MutabilityLevel.LOCKED)));

        assertThat(policy.levelIn(List.of(BUILTIN, ORG), D1))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.LOCKED, Optional.of(ORG)));
        assertThat(policy.levelIn(List.of(ORG, BUILTIN), D1))
                .as("the open entry after the lock does not lower it")
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.LOCKED, Optional.of(ORG)));
    }

    @Test
    void aKindDefaultIsNeverLoweredByALaterUrnEntry() {
        PerPackPolicy policy = new PerPackPolicy(Map.of(
                BUILTIN,
                MutabilityPolicySource.builder()
                        .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.EXTENDABLE)
                        .build(),
                ORG,
                entry(D1, MutabilityLevel.OPEN)));

        assertThat(policy.levelIn(List.of(BUILTIN, ORG), D1))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.EXTENDABLE, Optional.of(BUILTIN)));
        assertThat(policy.levelIn(List.of(ORG, BUILTIN), D1))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.EXTENDABLE, Optional.of(BUILTIN)));
    }

    @Test
    void aTargetNoPackNamesIsOpenWithNoSettingPack() {
        PerPackPolicy policy = new PerPackPolicy(Map.of(
                BUILTIN, entry(new Urn(NodeKind.DIRECTIVE, "OTHER"), MutabilityLevel.LOCKED),
                ORG, MutabilityPolicySource.empty()));

        assertThat(policy.levelIn(List.of(BUILTIN, ORG), new Urn(NodeKind.TACTIC, "t")))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.OPEN, Optional.empty()));
        assertThat(policy.levelIn(List.of(), D1))
                .as("an empty closure")
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.OPEN, Optional.empty()));
    }

    @Test
    void aPackOfTheClosureWithoutAPolicyIsOpenNotAnError() {
        PerPackPolicy policy = new PerPackPolicy(Map.of(ORG, entry(D1, MutabilityLevel.EXTENDABLE)));

        assertThat(policy.levelIn(List.of("UNLISTED", ORG), D1))
                .isEqualTo(new PerPackPolicy.Level(MutabilityLevel.EXTENDABLE, Optional.of(ORG)));
    }

    @Test
    void onATieTheFirstPackInClosureOrderThatReachedTheMaximumIsReported() {
        PerPackPolicy policy = new PerPackPolicy(Map.of(
                BUILTIN, entry(D1, MutabilityLevel.LOCKED),
                ORG, entry(D1, MutabilityLevel.LOCKED)));

        assertThat(policy.levelIn(List.of(BUILTIN, ORG), D1).setBy()).contains(BUILTIN);
        assertThat(policy.levelIn(List.of(ORG, BUILTIN), D1).setBy()).contains(ORG);
    }
}
