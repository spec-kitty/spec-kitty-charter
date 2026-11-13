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
import ai.speckitty.charter.kernel.domain.RequiringPack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * IC-13 (operator ruling Q9, research R12): a deny entry on a required artifact is accepted when an active override of
 * it satisfies the requirement in the document as it will be stored, and the rule reads the in-force resolution through
 * {@link InForceFacts}, never a copy of it. The scope is DIV-204's: a create judges every deny entry; a deactivate
 * judges the named target whether or not its deny entry is already stored; a replace and the other writes judge only
 * the deny entries they add (a preset and a select add none).
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

        /**
         * The requirement of {@code target} is satisfied by {@code overriders}: the target is required and unselected, so
         * out of force as not selected, and the resolution names the satisfier in {@code satisfiedBy} only (DIV-225).
         */
        Facts satisfied(ArtifactRef target, ArtifactRef... overriders) {
            states.put(
                    target,
                    new ArtifactState(
                            target,
                            State.INACTIVE,
                            Reason.NOT_SELECTED,
                            true,
                            List.of(overriders),
                            List.of(),
                            Optional.empty()));
            return this;
        }

        /** {@code target} is put back in force by the floor: nothing satisfies it. */
        Facts unsatisfied(ArtifactRef target) {
            states.put(
                    target,
                    new ArtifactState(
                            target, State.ACTIVE, Reason.REQUIRED, true, List.of(), List.of(), Optional.empty()));
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
    void aRequirementSatisfiedBySeveralOverridersIsSatisfiedAsByOne() {
        // IC-13 reads the satisfiers as a list (#439): a non-empty list satisfies, whatever its length. The facts are
        // the resolution's answer; the document needs only to deny T.
        Facts facts = new Facts().satisfied(T, LK, F);
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();

        assertThat(DocumentRules.check(document, CLOSURE, facts)).isEmpty();
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

        assertThat(refusal.getMessage()).isEqualTo("A pack requires directive:T2, so the activation cannot deny it.");
        assertThat(refusal.requiredBy()).containsExactly(new RequiringPack("PID-main", List.of()));
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
    void aLockedDenyEntryIsRefusedWhateverOverridesItAndBesideASatisfiedRequiredOneWithoutAskingTheFacts() {
        Facts facts = new Facts().satisfied(T, F).satisfied(LK, F);
        ActivationDocument document = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T", "LK"))
                .build()
                .document();

        CharterRefusal refusal = refused(DocumentRules.check(document, CLOSURE, facts), RefusalCode.ARTIFACT_LOCKED);

        assertThat(refusal.getMessage())
                .as("only the locked entry is named, whatever overrides it")
                .isEqualTo("A pack locks directive:LK, so the activation cannot deny it.");
        assertThat(facts.asked())
                .as("a locked entry is refused without asking the resolution")
                .isEmpty();
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

    /** A write that adds no deny entry on a required artifact, run against the facts it must not ask. */
    private record Write(String name, Function<Facts, Optional<CharterRefusal>> run) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Write> writesThatAddNoDenyEntryOnARequiredArtifact() {
        ActivationDocument denied =
                document().deactivated(DIRECTIVE, List.of("T2", "X")).build().document();
        ActivationDocument overridden = document()
                .selected(DIRECTIVE, List.of("F"))
                .deactivated(DIRECTIVE, List.of("T"))
                .build()
                .document();
        ActivationDocument free =
                document().deactivated(DIRECTIVE, List.of("X")).build().document();
        return Stream.of(
                new Write(
                        "a select with includeDeactivated removes a deny entry",
                        facts -> DocumentRules.checkWrite(denied, denied.withoutDeny(DIRECTIVE, "X"), CLOSURE, facts)),
                new Write(
                        "the override is removed later and its deny entry stays stored",
                        facts -> DocumentRules.checkWrite(
                                overridden, overridden.withoutSelected(DIRECTIVE, "F"), CLOSURE, facts)),
                new Write(
                        "a create denies an artifact nothing requires",
                        facts -> DocumentRules.check(free, CLOSURE, facts)),
                new Write("a create denies nothing", facts -> DocumentRules.check(EMPTY, CLOSURE, facts)),
                new Write(
                        "a replace denies an artifact nothing requires",
                        facts -> DocumentRules.checkReplace(
                                new ActivationDocument.Built(free, List.of()), EMPTY, CLOSURE, facts)));
    }

    @ParameterizedTest
    @MethodSource("writesThatAddNoDenyEntryOnARequiredArtifact")
    void theFactsAreAskedOnlyForADenyEntryTheWriteAddsOnARequiredArtifact(Write write) {
        Facts facts = new Facts().unsatisfied(T).unsatisfied(T2);

        assertThat(write.run().apply(facts)).isEmpty();
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
