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
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
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
                .isEqualTo("{\"packs\":[{\"packId\":\"zeta\"},{\"packId\":\"alpha\"}],\"schemaVersion\":2,"
                        + "\"selected\":{\"directive\":[\"D1\",\"D2\"]}}");
    }

    @Test
    void listsSortByCodePoint() {
        ActivationDocument document = build(b -> b.languages(List.of("😀", "�", "a")));

        assertThat(document.languages()).hasValueSatisfying(l -> assertThat(l).containsExactly("a", "�", "😀"));
    }

    @Test
    void anEmptyAllowListIsStoredAsAbsentSoBothGiveOneRevision() {
        ActivationDocument absent = build(b -> b.selected(DIRECTIVE, List.of("D1")));
        ActivationDocument empty = build(b ->
                b.selected(DIRECTIVE, List.of("D1")).selected(TACTIC, List.of()).selected(MISSION_TYPE, List.of()));

        assertThat(empty.selected(TACTIC)).isEmpty();
        assertThat(empty.selected(MISSION_TYPE)).isEmpty();
        assertThat(empty).isEqualTo(absent);
        assertThat(empty.revision()).isEqualTo(absent.revision());
        assertThat(canonical(empty)).isEqualTo("{\"schemaVersion\":2,\"selected\":{\"directive\":[\"D1\"]}}");
    }

    @Test
    void languagesKeepAbsentAndEmptyDistinct() {
        ActivationDocument absent = build(b -> b);
        ActivationDocument empty = build(b -> b.languages(List.of()));

        assertThat(absent.languages()).isEmpty();
        assertThat(empty.languages()).hasValueSatisfying(l -> assertThat(l).isEmpty());
        assertThat(empty.revision()).isNotEqualTo(absent.revision());
        assertThat(canonical(empty)).isEqualTo("{\"languages\":[],\"schemaVersion\":2}");
    }

    @Test
    void absentMeansNoneForEveryKind() {
        ActivationDocument minimal = build(b -> b);

        for (ArtifactKind kind : ArtifactKind.values()) {
            assertThat(minimal.selected(kind)).as("selected %s", kind).isEmpty();
            assertThat(minimal.deactivated(kind)).as("deactivated %s", kind).isEmpty();
        }
    }

    @Test
    void aMissionTypeIsSelectedAndDeniedLikeAnyKind() {
        ActivationDocument document = build(b -> b.selected(MISSION_TYPE, List.of("software-dev", "research"))
                .deactivated(MISSION_TYPE, List.of("documentation")));

        assertThat(document.selected(MISSION_TYPE))
                .hasValueSatisfying(ids -> assertThat(ids).containsExactly("research", "software-dev"));
        assertThat(document.deactivated(MISSION_TYPE)).containsExactly("documentation");
        assertThat(document.withoutSelected(MISSION_TYPE, "research").selected(MISSION_TYPE))
                .hasValueSatisfying(ids -> assertThat(ids).containsExactly("software-dev"));
        assertThat(document.withoutDeny(MISSION_TYPE, "documentation").deactivated())
                .isEmpty();
        assertThat(canonical(document))
                .isEqualTo("{\"deactivated\":{\"mission_type\":[\"documentation\"]},\"schemaVersion\":2,"
                        + "\"selected\":{\"mission_type\":[\"research\",\"software-dev\"]}}");
    }

    @Test
    void anUnselectThatEmptiesAListLeavesItAbsent() {
        ActivationDocument document = build(b -> b.selected(SKILL, List.of("land-pr")));

        ActivationDocument unselected = document.withoutSelected(SKILL, "land-pr");

        assertThat(unselected.selected(SKILL)).isEmpty();
        assertThat(unselected).isEqualTo(build(b -> b));
    }

    @Test
    void theDocumentHasNoKindGateAndNoMissionTypeSet() {
        assertThat(Arrays.stream(ActivationDocument.class.getMethods()).map(Method::getName))
                .doesNotContain("kinds", "activatedKinds", "missionTypes", "inForce");
        assertThat(Arrays.stream(ActivationDocument.Builder.class.getMethods()).map(Method::getName))
                .doesNotContain("activatedKinds", "missionTypes");
        assertThat(Arrays.stream(ActivationDocument.Written.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly("packs", "selected", "deactivated");
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
    void aKindOutsideTheActivatableKindsIsAViolation() {
        ActivationDocument.Built built = ActivationDocument.builder()
                .selected(ArtifactKind.ASSET, List.of("x"))
                .deactivated(ArtifactKind.TEMPLATE, List.of("y"))
                .selected(DIRECTIVE, List.of("D1"))
                .build();

        assertThat(built.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(
                        tuple("/selected/asset", "unknown_kind"), tuple("/deactivated/template", "unknown_kind"));
        assertThat(built.document().selected()).containsOnlyKeys(DIRECTIVE);
        assertThat(built.document().deactivated()).isEmpty();
    }

    @Test
    void everyActivatableKindIsAcceptedInBothLists() {
        ActivationDocument.Builder builder = ActivationDocument.builder();
        List<ArtifactKind> activatable = Arrays.stream(ArtifactKind.values())
                .filter(ArtifactKind::activatable)
                .toList();
        activatable.forEach(kind -> builder.selected(kind, List.of("s")).deactivated(kind, List.of("d")));

        ActivationDocument.Built built = builder.build();

        assertThat(built.violations()).isEmpty();
        assertThat(built.document().selected().keySet()).containsExactlyInAnyOrderElementsOf(activatable);
        assertThat(built.document().deactivated().keySet()).containsExactlyInAnyOrderElementsOf(activatable);
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
        assertThatThrownBy(() -> ActivationDocument.builder().schemaVersion(1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActivationDocument.builder().schemaVersion(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActivationDocument.builder().packs(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDocumentIsAtSchemaVersionTwo() {
        assertThat(build(b -> b).schemaVersion()).isEqualTo(2);
        assertThat(build(b -> b.schemaVersion(2)).toCanonicalTree()).containsEntry("schemaVersion", 2);
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
        ActivationDocument first = build(b -> b.selected(MISSION_TYPE, List.of("software-dev", "research"))
                .deactivated(TACTIC, List.of("T2", "T1"))
                .selected(DIRECTIVE, List.of("D1"))
                .packs(List.of(PackRef.pinned("org", "1.0"))));
        ActivationDocument second = build(b -> b.packs(List.of(PackRef.pinned("org", "1.0")))
                .selected(DIRECTIVE, List.of("D1", "D1"))
                .deactivated(TACTIC, List.of("T1", "T2"))
                .selected(MISSION_TYPE, List.of("research", "software-dev")));

        assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(second.revision()).isEqualTo(first.revision());
        assertThat(canonical(first))
                .isEqualTo("{\"deactivated\":{\"tactic\":[\"T1\",\"T2\"]},"
                        + "\"packs\":[{\"packId\":\"org\",\"version\":\"1.0\"}],\"schemaVersion\":2,"
                        + "\"selected\":{\"directive\":[\"D1\"],\"mission_type\":[\"research\",\"software-dev\"]}}");
        assertThat(first.toString()).isEqualTo(canonical(first));
    }

    @Test
    void theCanonicalTreeKeepsTheSchemaMemberOrder() {
        ActivationDocument document = build(b -> b.languages(List.of("java"))
                .deactivated(TACTIC, List.of("T1"))
                .selected(DIRECTIVE, List.of("D1"))
                .packs(List.of(PackRef.of("p"))));

        Map<String, Object> tree = document.toCanonicalTree();

        assertThat(tree.keySet()).containsExactly("schemaVersion", "packs", "selected", "deactivated", "languages");
    }
}
