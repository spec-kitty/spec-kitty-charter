package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.SKILL;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ActivationDocumentTest {

    private static String canonical(ActivationDocument document) {
        return new String(Jcs.canonical(document.toCanonicalTree()), StandardCharsets.UTF_8);
    }

    private static ActivationDocument build(Function<ActivationDocument.Builder, ActivationDocument.Builder> steps) {
        ActivationDocument.Built built =
                steps.apply(ActivationDocument.builder()).build();
        assertThat(built.violations()).isEmpty();
        return built.document();
    }

    @Test
    void allowListsAreSortedAndDeduplicatedWhilePacksKeepTheirOrder() {
        ActivationDocument document = build(b -> b.selected(DIRECTIVE, List.of("D2", "D1", "D2"))
                .packs(List.of(PackRef.of("zeta"), PackRef.of("alpha"))));

        assertThat(document.selected(DIRECTIVE))
                .hasValueSatisfying(ids -> assertThat(ids).containsExactly("D1", "D2"));
        assertThat(canonical(document))
                .isEqualTo("{\"packs\":[{\"packId\":\"zeta\"},{\"packId\":\"alpha\"}],\"schemaVersion\":1,"
                        + "\"selected\":{\"directive\":[\"D1\",\"D2\"]}}");
    }

    @Test
    void listsSortByCodePoint() {
        ActivationDocument document = build(b -> b.languages(List.of("😀", "�", "a")));

        assertThat(document.languages()).hasValueSatisfying(l -> assertThat(l).containsExactly("a", "�", "😀"));
    }

    @Test
    void absentAndEmptyAreBothNotInForceButStayDistinct() {
        ActivationDocument absent = build(b -> b);
        ActivationDocument empty = build(b -> b.activatedKinds(List.of())
                .missionTypes(List.of())
                .languages(List.of())
                .selected(DIRECTIVE, List.of()));

        assertThat(absent.inForce(DIRECTIVE)).isFalse();
        assertThat(empty.inForce(DIRECTIVE)).isFalse();
        assertThat(absent.activatedKinds()).isEmpty();
        assertThat(empty.activatedKinds()).hasValueSatisfying(k -> assertThat(k).isEmpty());
        assertThat(absent.selected(DIRECTIVE)).isEmpty();
        assertThat(empty.selected(DIRECTIVE))
                .hasValueSatisfying(s -> assertThat(s).isEmpty());
        assertThat(absent.toCanonicalTree()).isNotEqualTo(empty.toCanonicalTree());
        assertThat(absent.revision()).isNotEqualTo(empty.revision());
        assertThat(canonical(empty))
                .isEqualTo("{\"activatedKinds\":[],\"languages\":[],\"missionTypes\":[],\"schemaVersion\":1,"
                        + "\"selected\":{\"directive\":[]}}");
    }

    @Test
    void aKindIsInForceOnlyWhenActivatedKindsNamesIt() {
        ActivationDocument document = build(b -> b.activatedKinds(List.of(TACTIC, DIRECTIVE)));

        assertThat(document.inForce(DIRECTIVE)).isTrue();
        assertThat(document.inForce(SKILL)).isFalse();
        assertThat(canonical(document))
                .isEqualTo("{\"activatedKinds\":[\"directive\",\"tactic\"],\"schemaVersion\":1}");
    }

    @Test
    void anEmptySelectedOrDeactivatedObjectIsLeftOut() {
        ActivationDocument document = build(b -> b);

        assertThat(document.toCanonicalTree()).containsOnlyKeys("schemaVersion");
        assertThat(document.selected()).isEmpty();
        assertThat(document.deactivated()).isEmpty();
    }

    @Test
    void anEmptyDenyListIsAViolationNotAnException() {
        ActivationDocument.Built built =
                ActivationDocument.builder().deactivated(DIRECTIVE, List.of()).build();

        assertThat(built.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/deactivated/directive", "empty_deny_list"));
        assertThat(built.document().deactivated()).isEmpty();
    }

    @Test
    void aKindOutsideTheDocumentVocabularyIsAViolation() {
        ActivationDocument.Built built = ActivationDocument.builder()
                .activatedKinds(List.of(DIRECTIVE, ArtifactKind.ASSET))
                .selected(MISSION_TYPE, List.of("x"))
                .deactivated(ArtifactKind.TEMPLATE, List.of("y"))
                .build();

        assertThat(built.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(
                        tuple("/activatedKinds/1", "unknown_kind"),
                        tuple("/selected/mission_type", "unknown_kind"),
                        tuple("/deactivated/template", "unknown_kind"));
        assertThat(built.document().activatedKinds())
                .hasValueSatisfying(k -> assertThat(k).containsExactly(DIRECTIVE));
    }

    @Test
    void theDocumentKindsAreTheActivatableKindsLessTheMissionType() {
        assertThat(ActivationDocument.kinds())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(ArtifactKind.values())
                        .filter(ArtifactKind::activatable)
                        .filter(k -> k != MISSION_TYPE)
                        .toList());
    }

    @Test
    void aDuplicatePackIsAViolationAndKeptOnce() {
        ActivationDocument.Built built = ActivationDocument.builder()
                .packs(List.of(PackRef.of("a"), PackRef.pinned("a", "1"), PackRef.of("b")))
                .build();

        assertThat(built.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/packs/1/packId", "duplicate_pack"));
        assertThat(built.document().packs()).contains(List.of(PackRef.of("a"), PackRef.of("b")));
    }

    @Test
    void schemaGuaranteedShapesAreRefusedLoudly() {
        assertThatThrownBy(() -> ActivationDocument.builder().schemaVersion(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActivationDocument.builder().packs(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absentPacksMeansEveryConfiguredPack() {
        assertThat(build(b -> b).packs()).isEmpty();
    }

    @Test
    void denyAndSelectReturnNewDocumentsAndAnEmptiedDenyListIsDropped() {
        ActivationDocument original = build(b -> b);

        ActivationDocument denied = original.withDeny(TACTIC, "T1").withDeny(TACTIC, "T0");
        ActivationDocument selected = original.withSelected(DIRECTIVE, "D1");

        assertThat(original.deactivated()).isEmpty();
        assertThat(original.selected()).isEmpty();
        assertThat(denied.deactivated(TACTIC)).containsExactly("T0", "T1");
        assertThat(selected.selected(DIRECTIVE))
                .hasValueSatisfying(s -> assertThat(s).containsExactly("D1"));
        assertThat(denied.withoutDeny(TACTIC, "T0").deactivated(TACTIC)).containsExactly("T1");
        assertThat(denied.withoutDeny(TACTIC, "T0").withoutDeny(TACTIC, "T1").deactivated())
                .isEmpty();
        assertThat(denied.withoutDeny(TACTIC, "T0").withoutDeny(TACTIC, "T1")).isEqualTo(original);
    }

    @Test
    void theAccessorsAreUnmodifiable() {
        ActivationDocument document = build(b -> b.selected(DIRECTIVE, List.of("D1")));

        assertThatThrownBy(() -> document.selected().put(TACTIC, new TreeSet<>()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> document.selected(DIRECTIVE).orElseThrow().add("D2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalContentBuiltInAnotherOrderHasAnEqualRevision() {
        ActivationDocument first = build(b -> b.missionTypes(List.of("software-dev", "research"))
                .deactivated(TACTIC, List.of("T2", "T1"))
                .selected(DIRECTIVE, List.of("D1"))
                .packs(List.of(PackRef.pinned("org", "1.0"))));
        ActivationDocument second = build(b -> b.packs(List.of(PackRef.pinned("org", "1.0")))
                .selected(DIRECTIVE, List.of("D1", "D1"))
                .deactivated(TACTIC, List.of("T1", "T2"))
                .missionTypes(List.of("research", "software-dev")));

        assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(second.revision()).isEqualTo(first.revision());
        assertThat(canonical(first))
                .isEqualTo("{\"deactivated\":{\"tactic\":[\"T1\",\"T2\"]},\"missionTypes\":[\"research\","
                        + "\"software-dev\"],\"packs\":[{\"packId\":\"org\",\"version\":\"1.0\"}],"
                        + "\"schemaVersion\":1,\"selected\":{\"directive\":[\"D1\"]}}");
        assertThat(first.toString()).isEqualTo(canonical(first));
    }

    @Test
    void theCanonicalTreeKeepsTheSchemaMemberOrder() {
        ActivationDocument document = build(b -> b.languages(List.of("java"))
                .deactivated(TACTIC, List.of("T1"))
                .selected(DIRECTIVE, List.of("D1"))
                .missionTypes(List.of("m"))
                .activatedKinds(List.of(DIRECTIVE))
                .packs(List.of(PackRef.of("p"))));

        Map<String, Object> tree = document.toCanonicalTree();

        assertThat(tree.keySet())
                .containsExactly(
                        "schemaVersion",
                        "packs",
                        "activatedKinds",
                        "missionTypes",
                        "selected",
                        "deactivated",
                        "languages");
    }
}
