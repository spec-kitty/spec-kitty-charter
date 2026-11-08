package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.ClosureSnapshots.snapshot;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RequiringPack;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentRulesTest {

    private static final ClosureSnapshot CLOSURE = snapshot()
            .pack("built-in", "1.0.0")
            .pack("org-a", "2.1.0")
            .held(DIRECTIVE, "D1", "D2", "D3")
            .held(TACTIC, "T1", "T2")
            .held(ArtifactKind.MISSION_TYPE, "software-dev")
            .requiredBy("directive:D1", "PID-org-a", "PID-built-in")
            .requiredBy("directive:D2", "PID-org-a")
            .lockedBy("directive:D2", "PID-built-in")
            .build();

    private static CharterRefusal refusal(ActivationDocument.Builder builder) {
        Optional<CharterRefusal> refusal = DocumentRules.check(builder.build(), CLOSURE);
        assertThat(refusal).isPresent();
        return refusal.orElseThrow();
    }

    private static List<RefusalViolation> violations(ActivationDocument.Builder builder) {
        CharterRefusal refusal = refusal(builder);
        assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
        return refusal.violations();
    }

    @Test
    void aDocumentThatNamesOnlyWhatTheClosureHoldsPasses() {
        ActivationDocument document = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("org-a", "2.1.0"), PackRef.of("built-in")))
                .selected(DIRECTIVE, List.of("D1", "D3"))
                .deactivated(TACTIC, List.of("T1"))
                .selected(ArtifactKind.MISSION_TYPE, List.of("software-dev"))
                .build()
                .document();

        assertThat(DocumentRules.check(document, CLOSURE)).isEmpty();
    }

    @Test
    void anUnknownSelectedIdIsNamed() {
        List<RefusalViolation> violations =
                violations(ActivationDocument.builder().selected(DIRECTIVE, List.of("NOPE")));

        assertThat(violations)
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/selected/directive/0", "unknown_artifact"));
        assertThat(violations.getFirst().detail()).contains("NOPE");
    }

    @Test
    void aUrnAndACaseVariantNormalizeToTheDeclaredIdAndGiveOneDocument() {
        ClosureSnapshot closure =
                snapshot().held(DIRECTIVE, "D1", "D3").held(TACTIC, "T1").build();
        ActivationDocument.Built spelled = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("directive:D1", "d3"))
                .deactivated(TACTIC, List.of("t1", "tactic:T1"))
                .build();
        ActivationDocument declared = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("D1", "D3"))
                .deactivated(TACTIC, List.of("T1"))
                .build()
                .document();

        ActivationDocument.Built normalized = DocumentRules.normalize(spelled, closure);

        assertThat(DocumentRules.check(normalized, closure)).isEmpty();
        assertThat(normalized.document()).isEqualTo(declared);
        assertThat(normalized.document().revision()).isEqualTo(declared.revision());
        assertThat(normalized.written().selected().get(DIRECTIVE)).containsExactly("D1", "D3");
    }

    @Test
    void anAmbiguousSpellingIsRefusedAtItsPositionAndAnUnknownOneStaysUnknown() {
        ClosureSnapshot closure =
                snapshot().held(DIRECTIVE, "Twin", "TWIN", "D1").build();
        ActivationDocument.Built spelled = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("D1", "twin", "tactic:D1", "NOPE"))
                .build();

        Optional<CharterRefusal> refusal = DocumentRules.check(DocumentRules.normalize(spelled, closure), closure);

        assertThat(refusal).hasValueSatisfying(found -> {
            assertThat(found.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
            assertThat(found.violations())
                    .extracting(RefusalViolation::pointer, RefusalViolation::code)
                    .containsExactly(
                            tuple("/selected/directive/1", "ambiguous_artifact"),
                            tuple("/selected/directive/2", "unknown_artifact"),
                            tuple("/selected/directive/3", "unknown_artifact"));
            assertThat(found.violations().getFirst().detail()).contains("Twin", "TWIN");
        });
    }

    @Test
    void aDenyEntryWrittenAsAUrnIsStillRefusedWhenThePackRequiresTheArtifact() {
        ActivationDocument.Built spelled = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("directive:d1"))
                .build();

        CharterRefusal refusal = DocumentRules.check(DocumentRules.normalize(spelled, CLOSURE), CLOSURE)
                .orElseThrow();

        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
        assertThat(refusal.getMessage()).contains("directive:D1");
    }

    @Test
    void anUnknownDeniedIdIsNamed() {
        assertThat(violations(ActivationDocument.builder().deactivated(TACTIC, List.of("X"))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/deactivated/tactic/0", "unknown_artifact"));
    }

    @Test
    void anUnknownMissionTypeIsNamedAsAnyUnknownArtifactIs() {
        assertThat(violations(ActivationDocument.builder().selected(ArtifactKind.MISSION_TYPE, List.of("ghost"))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/selected/mission_type/0", "unknown_artifact"));
    }

    @Test
    void packsMustBeConfiguredOnceAndPinnedToTheHeldVersion() {
        assertThat(violations(ActivationDocument.builder().packs(List.of(PackRef.of("zeta")))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/0/packId", "unknown_pack"));
        assertThat(violations(ActivationDocument.builder().packs(List.of(PackRef.of("org-a"), PackRef.of("org-a")))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/1/packId", "duplicate_pack"));
        assertThat(violations(ActivationDocument.builder().packs(List.of(PackRef.pinned("org-a", "2.0.0")))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/0/version", "version_not_held"));
    }

    @Test
    void aVersionPinOtherThanTheHeldOneIsAnInvalidDocumentTellingTheHeldAndThePinnedVersion() {
        CharterRefusal refusal = refusal(ActivationDocument.builder().packs(List.of(PackRef.pinned("org-a", "2.0.0"))));

        assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
        assertThat(refusal.violations())
                .singleElement()
                .satisfies(violation ->
                        assertThat(violation.detail()).isEqualTo("The pack 'org-a' is held at 2.1.0, not 2.0.0."));
    }

    @Test
    void anEmptyDenyListIsInvalid() {
        assertThat(violations(ActivationDocument.builder().deactivated(DIRECTIVE, List.of())))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/deactivated/directive", "empty_deny_list"));
    }

    @Test
    void aKindOutsideTheActivatableKindsIsInvalid() {
        assertThat(violations(ActivationDocument.builder().selected(ArtifactKind.ASSET, List.of("A1"))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/selected/asset", "unknown_kind"));
    }

    /** Every index points into the body as written. */
    @Test
    void everyViolationIsCollectedInJsonPointerOrder() {
        ActivationDocument.Builder builder = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("Z", "A", "D1"))
                .selected(ArtifactKind.MISSION_TYPE, List.of("ghost"))
                .deactivated(DIRECTIVE, List.of("D1"))
                .deactivated(TACTIC, List.of())
                .packs(List.of(
                        PackRef.of("p2"),
                        PackRef.of("p3"),
                        PackRef.of("p4"),
                        PackRef.of("p5"),
                        PackRef.of("p6"),
                        PackRef.of("p7"),
                        PackRef.of("p8"),
                        PackRef.of("p9"),
                        PackRef.of("p10"),
                        PackRef.of("p11"),
                        PackRef.of("p2")))
                .selected(ArtifactKind.TEMPLATE, List.of("T"));

        assertThat(violations(builder))
                .extracting(RefusalViolation::pointer)
                .containsExactly(
                        "/deactivated/tactic",
                        "/packs/0/packId",
                        "/packs/1/packId",
                        "/packs/2/packId",
                        "/packs/3/packId",
                        "/packs/4/packId",
                        "/packs/5/packId",
                        "/packs/6/packId",
                        "/packs/7/packId",
                        "/packs/8/packId",
                        "/packs/9/packId",
                        "/packs/10/packId",
                        "/selected/directive/0",
                        "/selected/directive/1",
                        "/selected/mission_type/0",
                        "/selected/template");
    }

    @Test
    void anUnknownIdWrittenBeforeASortedKnownOneIsNamedWhereWritten() {
        List<RefusalViolation> violations =
                violations(ActivationDocument.builder().selected(DIRECTIVE, List.of("Z", "D1")));

        assertThat(violations)
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/selected/directive/0", "unknown_artifact"));
        assertThat(violations.getFirst().detail()).contains("'Z'");
    }

    @Test
    void aRepeatedUnknownIdIsNamedAtEveryWrittenPosition() {
        assertThat(violations(ActivationDocument.builder()
                        .deactivated(TACTIC, List.of("X", "T1", "X"))
                        .selected(ArtifactKind.MISSION_TYPE, List.of("software-dev", "ghost"))))
                .extracting(RefusalViolation::pointer)
                .containsExactly("/deactivated/tactic/0", "/deactivated/tactic/2", "/selected/mission_type/1");
    }

    @Test
    void aDuplicatePackThenAnUnknownPackHaveDistinctPointers() {
        assertThat(violations(ActivationDocument.builder()
                        .packs(List.of(PackRef.of("org-a"), PackRef.of("org-a"), PackRef.of("zeta")))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/1/packId", "duplicate_pack"), tuple("/packs/2/packId", "unknown_pack"));
    }

    @Test
    void aDuplicatePackThenAMismatchedPinPointAtTheirOwnPositions() {
        assertThat(violations(ActivationDocument.builder()
                        .packs(List.of(
                                PackRef.of("org-a"),
                                PackRef.pinned("org-a", "9.9.9"),
                                PackRef.pinned("built-in", "0.1")))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(
                        tuple("/packs/1/packId", "duplicate_pack"), tuple("/packs/2/version", "version_not_held"));
    }

    @Test
    void mixedKindsAndMembersEachPointWhereWritten() {
        assertThat(violations(ActivationDocument.builder()
                        .selected(TACTIC, List.of("T2", "NOPE", "T1"))
                        .selected(DIRECTIVE, List.of("D3", "D2", "Q"))
                        .deactivated(DIRECTIVE, List.of("D3", "GONE"))
                        .selected(ArtifactKind.ASSET, List.of("A1"))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(
                        tuple("/deactivated/directive/1", "unknown_artifact"),
                        tuple("/selected/asset", "unknown_kind"),
                        tuple("/selected/directive/2", "unknown_artifact"),
                        tuple("/selected/tactic/1", "unknown_artifact"));
    }

    @Test
    void aDocumentWithoutAWrittenFormIsIndexedByItsCanonicalLists() {
        ActivationDocument document = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("Z", "A", "D1"))
                .build()
                .document();

        assertThat(DocumentRules.check(document, CLOSURE).orElseThrow().violations())
                .extracting(RefusalViolation::pointer)
                .containsExactly("/selected/directive/0", "/selected/directive/2");
    }

    @Test
    void aDenyOnARequiredArtifactNamesEveryRequiringPack() {
        CharterRefusal refusal = refusal(ActivationDocument.builder().deactivated(DIRECTIVE, List.of("D1")));

        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
        assertThat(refusal.violations()).isEmpty();
        assertThat(refusal.requiredBy())
                .containsExactly(
                        new RequiringPack("PID-org-a", List.of()), new RequiringPack("PID-built-in", List.of()));
        assertThat(refusal.getMessage()).contains("directive:D1");
    }

    @Test
    void lockedWinsOverRequired() {
        CharterRefusal refusal = refusal(ActivationDocument.builder().deactivated(DIRECTIVE, List.of("D1", "D2")));

        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED);
        assertThat(refusal.requiredBy()).containsExactly(new RequiringPack("PID-built-in", List.of()));
        assertThat(refusal.getMessage()).contains("directive:D2");
    }

    @Test
    void theFloorIsJudgedOnlyWhenTheDocumentIsValid() {
        CharterRefusal refusal = refusal(ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("D2"))
                .selected(TACTIC, List.of("NOPE")));

        assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
        assertThat(refusal.requiredBy()).isEmpty();
    }

    @Test
    void aWriteIsJudgedOnTheFloorOnlyForTheDenyEntriesItAdds() {
        ActivationDocument stale = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("D1"))
                .build()
                .document();
        ActivationDocument selecting = stale.withSelected(TACTIC, "T1");
        ActivationDocument denying = stale.withDeny(DIRECTIVE, "D2");

        assertThat(DocumentRules.checkWrite(stale, selecting, CLOSURE))
                .as("a stored deny entry on the floor is tolerated, not applied")
                .isEmpty();
        assertThat(DocumentRules.checkWrite(stale, denying, CLOSURE)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED);
            assertThat(refusal.getMessage()).contains("directive:D2").doesNotContain("directive:D1");
        });
        assertThat(DocumentRules.checkWrite(stale, stale.withSelected(TACTIC, "NOPE"), CLOSURE))
                .hasValueSatisfying(refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID));
    }

    // ---- replace (A4, B1): the floor on the deny entries it adds, stale ids and unheld pins tolerated

    private static final ActivationDocument NOTHING_STORED =
            ActivationDocument.builder().build().document();

    @Test
    void a4AGetThenPutOfADenyEntryOnANowRequiredArtifactIsAccepted() {
        // The review's counter-example: the stored document denies T (D1 here), which a pack now requires; the client
        // sends back the body it read.
        ActivationDocument.Built body = ActivationDocument.builder()
                .selected(TACTIC, List.of("T1"))
                .deactivated(DIRECTIVE, List.of("D1"))
                .build();
        ActivationDocument stored = body.document();

        assertThat(DocumentRules.check(body, CLOSURE))
                .as("a create still judges every deny entry")
                .hasValueSatisfying(refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(DocumentRules.checkReplace(body, stored, CLOSURE)).isEmpty();
    }

    @Test
    void a4AStoredDenyEntryInAnotherSpellingOfARequiredArtifactIsNotAddedByAGetThenPut() {
        ClosureSnapshot closure = snapshot()
                .held(DIRECTIVE, "dir-001")
                .held(TACTIC, "T1")
                .requiredBy("directive:dir-001", "PID-org-a")
                .build();
        ActivationDocument stored = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("DIR-001"))
                .build()
                .document();
        ActivationDocument.Built body = DocumentRules.normalize(
                ActivationDocument.builder()
                        .deactivated(DIRECTIVE, List.of("DIR-001"))
                        .build(),
                closure);

        assertThat(DocumentRules.checkReplace(body, stored, closure))
                .as("the stored deny entry, spelled otherwise, is the same entry")
                .isEmpty();
        assertThat(DocumentRules.checkWrite(stored, body.document().withSelected(TACTIC, "T1"), closure))
                .as("a per-artifact write of another id keeps tolerating it")
                .isEmpty();
        ActivationDocument urnStored = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("directive:DIR-001"))
                .build()
                .document();
        assertThat(DocumentRules.checkReplace(body, urnStored, closure))
                .as("a stored URN spelling is the same entry too")
                .isEmpty();
    }

    @Test
    void aReplaceJudgesTheFloorOnEveryDenyEntryItAdds() {
        ActivationDocument stored = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("D1"))
                .build()
                .document();
        ActivationDocument.Built adding = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("D1", "D2"))
                .build();

        assertThat(DocumentRules.checkReplace(adding, stored, CLOSURE)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED);
            assertThat(refusal.getMessage()).contains("directive:D2").doesNotContain("directive:D1");
        });
        ActivationDocument.Built denyingD1 = ActivationDocument.builder()
                .deactivated(DIRECTIVE, List.of("D1"))
                .build();
        assertThat(DocumentRules.checkReplace(denyingD1, NOTHING_STORED, CLOSURE))
                .as("with nothing stored every deny entry is added")
                .hasValueSatisfying(refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
    }

    @Test
    void a4AReplaceToleratesAnIdThePacksNoLongerHoldOnlyWhenTheStoredDocumentNamesIt() {
        ActivationDocument stored = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("D3", "GONE"))
                .deactivated(TACTIC, List.of("OLD"))
                .build()
                .document();
        ActivationDocument.Built roundTrip = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("D3", "GONE"))
                .deactivated(TACTIC, List.of("OLD"))
                .build();
        ActivationDocument.Built typo = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("D3", "GONE", "NOPE"))
                .build();

        assertThat(DocumentRules.checkReplace(roundTrip, stored, CLOSURE)).isEmpty();
        assertThat(DocumentRules.checkReplace(typo, stored, CLOSURE)
                        .orElseThrow()
                        .violations())
                .as("an id the stored document never named is still unknown")
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/selected/directive/2", "unknown_artifact"));
        assertThat(DocumentRules.checkReplace(roundTrip, NOTHING_STORED, CLOSURE)
                        .orElseThrow()
                        .violations())
                .extracting(RefusalViolation::pointer)
                .containsExactly("/deactivated/tactic/0", "/selected/directive/1");
    }

    @Test
    void b1ACreateRefusesAPinNoPackHoldsAndAReplaceOfTheSameBodyDoesNot() {
        ActivationDocument.Built body = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("org-a", "2.0.0")))
                .build();

        assertThat(DocumentRules.check(body, CLOSURE).orElseThrow().violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/0/version", "version_not_held"));
        assertThat(DocumentRules.checkReplace(body, NOTHING_STORED, CLOSURE)).isEmpty();
        assertThat(DocumentRules.checkReplace(body, body.document(), CLOSURE)).isEmpty();
    }

    @Test
    void aReplaceStillRefusesAnUnknownPack() {
        ActivationDocument.Built body = ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("zeta", "1.0.0")))
                .build();

        assertThat(DocumentRules.checkReplace(body, body.document(), CLOSURE)
                        .orElseThrow()
                        .violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/0/packId", "unknown_pack"));
    }

    @Test
    void aDenyEntryOnARequiredOrLockedMissionTypeIsJudgedAsOnAnyKind() {
        ClosureSnapshot closure = snapshot()
                .held(ArtifactKind.MISSION_TYPE, "software-dev", "research", "free")
                .requiredBy("mission_type:software-dev", "built-in")
                .lockedBy("mission_type:research", "built-in")
                .build();

        assertThat(DocumentRules.check(
                        ActivationDocument.builder()
                                .deactivated(ArtifactKind.MISSION_TYPE, List.of("software-dev"))
                                .build(),
                        closure))
                .hasValueSatisfying(refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
        assertThat(DocumentRules.check(
                        ActivationDocument.builder()
                                .deactivated(ArtifactKind.MISSION_TYPE, List.of("research"))
                                .build(),
                        closure))
                .hasValueSatisfying(refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_LOCKED));
        assertThat(DocumentRules.check(
                        ActivationDocument.builder()
                                .deactivated(ArtifactKind.MISSION_TYPE, List.of("free"))
                                .build(),
                        closure))
                .isEmpty();
    }

    // ---- RequiringPack.via: the extends chain from the document's packs

    /** team extends org, which extends internal; helper is only depended on. */
    private static final ClosureSnapshot EXTENDED = snapshot()
            .pack("internal", "1.0.0")
            .extending("org", "1.0.0", "internal")
            .extending("team", "1.0.0", "org")
            .pack("helper", "1.0.0")
            .held(DIRECTIVE, "D1", "D2", "D3", "D4")
            .lockedBy("directive:D1", "internal")
            .requiredBy("directive:D2", "team")
            .requiredBy("directive:D3", "helper")
            .requiredBy("directive:D4", "org")
            .build();

    private static CharterRefusal denying(String id, String... packs) {
        ActivationDocument.Built built = ActivationDocument.builder()
                .packs(Arrays.stream(packs).map(PackRef::of).toList())
                .deactivated(DIRECTIVE, List.of(id))
                .build();
        return DocumentRules.check(built, EXTENDED).orElseThrow();
    }

    @Test
    void aLockInheritedOverParentPackNamesTheChainFromTheNamedPackUpToTheLockingOne() {
        assertThat(denying("D1", "team").requiredBy())
                .containsExactly(new RequiringPack("internal", List.of("team", "org")));
        assertThat(denying("D4", "team").requiredBy()).containsExactly(new RequiringPack("org", List.of("team")));
    }

    @Test
    void aPackTheDocumentNamesThatDeclaresTheItemItselfHasAnEmptyChain() {
        assertThat(denying("D2", "team").requiredBy()).containsExactly(new RequiringPack("team", List.of()));
        assertThat(denying("D4", "team", "org").requiredBy()).containsExactly(new RequiringPack("org", List.of()));
    }

    @Test
    void aPackReachedOverDependsOnOnlyHasAnEmptyChain() {
        assertThat(denying("D3", "team").requiredBy()).containsExactly(new RequiringPack("helper", List.of()));
    }
}
