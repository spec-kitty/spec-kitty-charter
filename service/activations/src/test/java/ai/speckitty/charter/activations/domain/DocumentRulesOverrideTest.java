package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.ClosureSnapshots.snapshot;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * IC-13 (operator ruling Q9, research R12): a deny entry on a required artifact is accepted when an active override of
 * it satisfies the requirement in the document as it will be stored, and the rule reads the in-force resolution through
 * {@link InForceFacts}, never a copy of it. The scope is DIV-204's: a create judges every deny entry; a replace, a
 * deactivate, a preset applied with force and a select with includeDeactivated judge only the deny entries they add.
 * The facts are asked only when a deny entry is added on a required artifact that no pack locks.
 */
class DocumentRulesOverrideTest {

    private static final ArtifactRef T = new ArtifactRef(DIRECTIVE, "T");
    private static final ArtifactRef T2 = new ArtifactRef(DIRECTIVE, "T2");
    private static final ArtifactRef F = new ArtifactRef(DIRECTIVE, "F");
    private static final ArtifactRef LK = new ArtifactRef(DIRECTIVE, "LK");

    private static final ClosureSnapshot CLOSURE = snapshot()
            .pack("main", "1.0.0")
            .held(DIRECTIVE, "T", "T2", "F", "LK", "X")
            .held(TACTIC, "e")
            .requiredBy("directive:T", "PID-main")
            .requiredBy("directive:T2", "PID-main")
            .requiredBy("directive:LK", "PID-main")
            .lockedBy("directive:LK", "PID-main")
            .build();

    private static final ActivationDocument EMPTY = ActivationDocument.builder()
            .packs(List.of(PackRef.of("main")))
            .build()
            .document();

    /** The facts of a test: what the resolution answers, and every query it was asked. */
    private static final class Facts implements InForceFacts {

        private final Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
        private final List<InForceQuery> asked = new ArrayList<>();
        private CharterRefusal refusal;

        /** The requirement of {@code target} is satisfied by {@code overrider}: out of force, overridden. */
        Facts satisfied(ArtifactRef target, ArtifactRef overrider) {
            states.put(
                    target,
                    new ArtifactState(
                            target,
                            State.INACTIVE,
                            Reason.OVERRIDDEN,
                            true,
                            Optional.of(overrider),
                            Optional.of(overrider),
                            Optional.empty()));
            return this;
        }

        /** {@code target} is put back in force by the floor: nothing satisfies it. */
        Facts unsatisfied(ArtifactRef target) {
            states.put(
                    target,
                    new ArtifactState(
                            target,
                            State.ACTIVE,
                            Reason.REQUIRED,
                            true,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()));
            return this;
        }

        /** The merge of the closure is refused when the facts are asked. */
        Facts refusing(CharterRefusal merge) {
            this.refusal = merge;
            return this;
        }

        @Override
        public InForceAnswer inForce(InForceQuery query) {
            asked.add(query);
            if (refusal != null) {
                throw refusal;
            }
            return new InForceAnswer(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        }

        List<InForceQuery> asked() {
            return List.copyOf(asked);
        }
    }

    private static ActivationDocument.Builder document() {
        return ActivationDocument.builder().packs(List.of(PackRef.of("main")));
    }

    private static CharterRefusal refused(Optional<CharterRefusal> refusal, RefusalCode code) {
        assertThat(refusal).isPresent();
        assertThat(refusal.orElseThrow().code()).isEqualTo(code);
        return refusal.orElseThrow();
    }

    @Test
    void aCreateAcceptsADenyEntryOnARequiredArtifactWhenTheOverrideOfItSatisfiesTheRequirement() {
        Facts facts = new Facts().satisfied(T, F);
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();

        assertThat(DocumentRules.check(document, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).containsExactly(EffectiveSet.queryOf(document));
    }

    @Test
    void aCreateJudgesEveryDenyEntryOfTheBodyAndNamesOnlyTheOnesNothingSatisfies() {
        Facts facts = new Facts().satisfied(T, F).unsatisfied(T2);
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T", "T2"))
                .build()
                .document();

        CharterRefusal refusal = refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ARTIFACT_REQUIRED);

        assertThat(refusal.getMessage()).contains("directive:T2").doesNotContain("directive:T,");
        assertThat(refusal.requiredBy()).hasSize(1);
    }

    @Test
    void anEnhancerAloneDoesNotSatisfyTheRequirement() {
        Facts facts = new Facts().unsatisfied(T);
        ActivationDocument document = document()
                .selected(TACTIC, List.of("e"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();

        refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ARTIFACT_REQUIRED);

        assertThat(facts.asked()).containsExactly(EffectiveSet.queryOf(document));
    }

    @Test
    void aRequiredArtifactTheResolutionDoesNotKnowIsNotSatisfied() {
        Facts facts = new Facts();
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();

        refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ARTIFACT_REQUIRED);
    }

    @Test
    void aLockedArtifactIsRefusedWhateverOverridesItAndTheFactsAreNotAsked() {
        Facts facts = new Facts().satisfied(LK, F);
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("LK"))
                .build()
                .document();

        refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ARTIFACT_LOCKED);

        assertThat(facts.asked()).isEmpty();
    }

    @Test
    void aLockedDenyEntryBesideASatisfiedRequiredOneIsStillRefusedAsLocked() {
        Facts facts = new Facts().satisfied(T, F);
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T", "LK"))
                .build()
                .document();

        CharterRefusal refusal = refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ARTIFACT_LOCKED);

        assertThat(refusal.getMessage()).contains("directive:LK").doesNotContain("directive:T,");
    }

    @Test
    void aReplaceJudgesTheBodyAsItWillBeStoredSoTheOverrideAndTheDenyEntryMayComeTogether() {
        Facts facts = new Facts().satisfied(T, F);
        ActivationDocument.Built body = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build();

        assertThat(DocumentRules.checkReplace(body, EMPTY, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).containsExactly(EffectiveSet.queryOf(body.document()));
    }

    @Test
    void aReplaceWithTheDenyEntryAndNoOverrideIsRefused() {
        Facts facts = new Facts().unsatisfied(T);
        ActivationDocument.Built body =
                document().deactivated(DIRECTIVE, List.of("T")).build();

        refused(DocumentRules.checkReplace(body, EMPTY, CLOSURE, facts), RefusalCode.ARTIFACT_REQUIRED);
    }

    @Test
    void aReplaceJudgesOnlyTheDenyEntriesItAddsSoAStoredOneNothingSatisfiesIsKept() {
        Facts facts = new Facts().satisfied(T, F).unsatisfied(T2);
        ActivationDocument stored =
                document().deactivated(DIRECTIVE, List.of("T2")).build().document();
        ActivationDocument.Built body = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T", "T2"))
                .build();

        assertThat(DocumentRules.checkReplace(body, stored, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).containsExactly(EffectiveSet.queryOf(body.document()));
    }

    @Test
    void aDeactivateJudgesTheDocumentItComputedAndTheDenyEntryItAdds() {
        Facts facts = new Facts().satisfied(T, F);
        ActivationDocument before =
                document().selected(DIRECTIVE, List.of("F")).build().document();
        ActivationDocument after = before.withDeny(DIRECTIVE, "T");

        assertThat(DocumentRules.checkWrite(before, after, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).containsExactly(EffectiveSet.queryOf(after));
    }

    @Test
    void aDeactivateWithOnlyAnEnhancerOfTheTargetIsRefused() {
        Facts facts = new Facts().unsatisfied(T);
        ActivationDocument before =
                document().selected(TACTIC, List.of("e")).build().document();

        refused(
                DocumentRules.checkWrite(before, before.withDeny(DIRECTIVE, "T"), CLOSURE, facts),
                RefusalCode.ARTIFACT_REQUIRED);
    }

    @Test
    void aPresetAppliedWithForceJudgesOnlyTheDenyEntriesItAddsAndNamesNothingItKept() {
        Facts facts = new Facts().satisfied(T, F).unsatisfied(T2);
        ActivationDocument before = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T2"))
                .build()
                .document();
        ActivationDocument after = before.withDeny(DIRECTIVE, "T");

        assertThat(DocumentRules.checkWrite(before, after, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).containsExactly(EffectiveSet.queryOf(after));
    }

    @Test
    void aSelectWithIncludeDeactivatedAddsNoDenyEntryAndNeverAsksTheFacts() {
        Facts facts = new Facts().unsatisfied(T2);
        ActivationDocument before =
                document().deactivated(DIRECTIVE, List.of("T2", "X")).build().document();
        ActivationDocument after = before.withoutDeny(DIRECTIVE, "X");

        assertThat(DocumentRules.checkWrite(before, after, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).isEmpty();
    }

    @Test
    void removingTheOverrideLaterIsNotRefusedWhileItsDenyEntryStaysStored() {
        Facts facts = new Facts().unsatisfied(T);
        ActivationDocument before = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();
        ActivationDocument after = before.withoutSelected(DIRECTIVE, "F");

        assertThat(DocumentRules.checkWrite(before, after, CLOSURE, facts)).isEmpty();
        assertThat(facts.asked()).isEmpty();
    }

    @Test
    void theFactsAreAskedOnlyForADenyEntryOnARequiredArtifact() {
        Facts facts = new Facts().unsatisfied(T);
        ActivationDocument free =
                document().deactivated(DIRECTIVE, List.of("X")).build().document();

        assertThat(DocumentRules.check(free, CLOSURE, facts)).isEmpty();
        assertThat(DocumentRules.check(EMPTY, CLOSURE, facts)).isEmpty();
        assertThat(DocumentRules.checkReplace(new ActivationDocument.Built(free, List.of()), EMPTY, CLOSURE, facts))
                .isEmpty();
        assertThat(facts.asked()).isEmpty();
    }

    @Test
    void aRefusalOfTheFactsReachesTheWriteThatAddsADenyEntryOnARequiredArtifactAndNoOther() {
        CharterRefusal merge = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "A layer override is forbidden.");
        Facts facts = new Facts().refusing(merge);
        ActivationDocument required =
                document().deactivated(DIRECTIVE, List.of("T")).build().document();
        ActivationDocument free =
                document().deactivated(DIRECTIVE, List.of("X")).build().document();

        assertThatThrownBy(() -> DocumentRules.check(required, CLOSURE, facts)).isSameAs(merge);
        assertThatThrownBy(() -> DocumentRules.checkWrite(EMPTY, required, CLOSURE, facts))
                .isSameAs(merge);
        assertThat(DocumentRules.check(free, CLOSURE, facts)).isEmpty();
    }

    @Test
    void aDocumentWithAnInvalidIdIsRefusedAsInvalidBeforeTheFactsAreAsked() {
        Facts facts = new Facts().satisfied(T, F);
        ActivationDocument document = document()
                .selected(ArtifactKind.DIRECTIVE, List.of("NOPE"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();

        refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ACTIVATION_INVALID);

        assertThat(facts.asked()).isEmpty();
    }

    @Test
    void theQueryTheFactsAreAskedCarriesTheLanguageFilterAndThePinsOfTheStoredDocument() {
        Facts facts = new Facts().satisfied(T, F);
        ActivationDocument document = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("main", "1.0.0")))
                .languages(List.of("java"))
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();

        assertThat(DocumentRules.check(document, CLOSURE, facts)).isEmpty();

        assertThat(facts.asked()).singleElement().satisfies(query -> {
            assertThat(query.selected()).containsExactly(F);
            assertThat(query.deactivated()).containsExactly(T);
            assertThat(query.languages()).contains(java.util.Set.of("java"));
            assertThat(query.pins()).containsEntry("main", "1.0.0");
        });
    }
}
