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
            .missionTypes("software-dev")
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
                .missionTypes(List.of("software-dev"))
                .activatedKinds(List.of(DIRECTIVE, TACTIC))
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
    void anUnknownDeniedIdIsNamed() {
        assertThat(violations(ActivationDocument.builder().deactivated(TACTIC, List.of("X"))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/deactivated/tactic/0", "unknown_artifact"));
    }

    @Test
    void anUnknownMissionTypeIsNamed() {
        assertThat(violations(ActivationDocument.builder().missionTypes(List.of("ghost"))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/missionTypes/0", "unknown_mission_type"));
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
    void anEmptyDenyListIsInvalid() {
        assertThat(violations(ActivationDocument.builder().deactivated(DIRECTIVE, List.of())))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/deactivated/directive", "empty_deny_list"));
    }

    @Test
    void aKindOutsideTheActivatableKindsIsInvalid() {
        assertThat(violations(ActivationDocument.builder().activatedKinds(List.of(ArtifactKind.ASSET))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/activatedKinds/0", "unknown_kind"));
    }

    /** Every index points into the body as written. */
    @Test
    void everyViolationIsCollectedInJsonPointerOrder() {
        ActivationDocument.Builder builder = ActivationDocument.builder()
                .selected(DIRECTIVE, List.of("Z", "A", "D1"))
                .missionTypes(List.of("ghost"))
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
                .activatedKinds(List.of(ArtifactKind.TEMPLATE));

        assertThat(violations(builder))
                .extracting(RefusalViolation::pointer)
                .containsExactly(
                        "/activatedKinds/0",
                        "/deactivated/tactic",
                        "/missionTypes/0",
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
                        "/selected/directive/1");
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
                        .missionTypes(List.of("software-dev", "ghost"))))
                .extracting(RefusalViolation::pointer)
                .containsExactly("/deactivated/tactic/0", "/deactivated/tactic/2", "/missionTypes/1");
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
                        .activatedKinds(List.of(DIRECTIVE, ArtifactKind.ASSET))))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(
                        tuple("/activatedKinds/1", "unknown_kind"),
                        tuple("/deactivated/directive/1", "unknown_artifact"),
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
