package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.application.RelationshipDraft;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The request-side caps of a relationship's endpoints (#443): a {@code from} or a {@code to} longer than 255 characters
 * as written is a refused registration, whatever reaches the mapping. The kernel URN types stay uncapped, since they
 * also parse pack content whose schemas do not cap a URN.
 */
class RelationshipWireTest {

    /** "directive:" is 10 characters. */
    private static final String URN_AT_THE_CAP = "directive:" + "D".repeat(245);

    private static RelationshipRegistration wire(String from, String to) {
        return new RelationshipRegistration(from, to, "requires");
    }

    @Test
    void endpointsAtTheCapAreMapped() {
        RelationshipDraft mapped = RelationshipWire.fromWire(wire(URN_AT_THE_CAP, "directive:D1"));
        assertThat(mapped.from().toString()).hasSize(255);

        String pack = "p".repeat(PackIdentifier.MAX_LENGTH);
        String qualifiedAtTheCap = pack + "::directive:" + "D".repeat(255 - PackIdentifier.MAX_LENGTH - 2 - 10);
        assertThat(qualifiedAtTheCap).hasSize(255);
        assertThat(RelationshipWire.fromWire(wire("directive:D1", qualifiedAtTheCap))
                        .toPack())
                .contains(pack);
    }

    /** A pack name over the longest pack id is no pack id (#582): the contract's pattern cannot cap it, the wire does. */
    @Test
    void aPackNameOverTheLongestPackIdIsAValidationFailedRefusal() {
        String overCap = "p".repeat(PackIdentifier.MAX_LENGTH + 1) + "::directive:D1";
        assertThatThrownBy(() -> RelationshipWire.fromWire(wire("directive:D1", overCap)))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refused.getMessage()).contains(PackIdentifier.GRAMMAR_DETAIL);
                });
        assertThatThrownBy(() -> RelationshipWire.fromWire(wire("a_b::directive:D1", "directive:D2")))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refused.getMessage()).contains(PackIdentifier.GRAMMAR_DETAIL);
                });
    }

    @Test
    void aFromOrAToLongerThan255IsAValidationFailedRefusal() {
        assertThatThrownBy(() -> RelationshipWire.fromWire(wire(URN_AT_THE_CAP + "D", "directive:D1")))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refused.getMessage()).isEqualTo("from is longer than 255 characters.");
                });
        assertThatThrownBy(() -> RelationshipWire.fromWire(wire("directive:D1", "p::" + URN_AT_THE_CAP)))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refused.getMessage()).isEqualTo("to is longer than 255 characters.");
                });
    }

    @Test
    void aTermIsRegisteredOnlyAsTheTargetOfAVocabularyEdge() {
        // Positive control: a term as the target of a vocabulary relationship is mapped, bare or pack-qualified.
        RelationshipDraft valid = RelationshipWire.fromWire(
                new RelationshipRegistration("agent_profile:p", "core::term:spec-kitty-core/mission", "vocabulary"));
        assertThat(valid.to().toString()).isEqualTo("term:spec-kitty-core/mission");
        assertThat(valid.toPack()).contains("core");

        for (String[] refused : new String[][] {
            {"term:g/t", "directive:D1", "requires"},
            {"term:g/t", "glossary_context:g", "vocabulary"},
            {"directive:D1", "term:g/t", "overrides"},
            {"directive:D1", "core::term:g/t", "enhances"}
        }) {
            assertThatThrownBy(() ->
                            RelationshipWire.fromWire(new RelationshipRegistration(refused[0], refused[1], refused[2])))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                        assertThat(refusal.getMessage())
                                .isEqualTo(
                                        GraphBinding.TERM_ENDPOINT + refused[0] + " " + refused[2] + " " + refused[1]);
                    });
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"impacts", "replaces", "delegates_to", "in_tension_with", "no_such_relation"})
    void anUnknownTypeIsOneViolationAtType(String type) {
        assertThatThrownBy(() -> RelationshipWire.fromWire(
                        new RelationshipRegistration("mission_type:research", "directive:D1", type)))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refused.getMessage()).isEqualTo("unknown relation type: " + type);
                    assertThat(refused.violations())
                            .containsExactly(new RefusalViolation(
                                    "/type", "relationship_type_unknown", "unknown relation type: " + type));
                });
    }

    // ---- ADR-0045 decision 1: the wire of a tension ----------------------------------------------------------------

    @Test
    void aTensionMayNameAQualifiedSourceAndAnyOtherRelationMayNot() {
        RelationshipDraft tension = RelationshipWire.fromWire(
                new RelationshipRegistration("core::directive:A", "core::directive:B", "tension"));

        assertThat(tension.type()).isEqualTo(RelationType.TENSION);
        assertThat(tension.fromReference()).hasToString("core::directive:A");
        assertThat(tension.toReference()).hasToString("core::directive:B");
        assertThatThrownBy(() -> RelationshipWire.fromWire(
                        new RelationshipRegistration("core::directive:A", "core::directive:B", "requires")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
    }

    @Test
    void aTensionOfAnArtifactWithAVersionOfItselfIsASelfLoopWhileAStructuralOneIsNot() {
        assertThatThrownBy(() -> RelationshipWire.fromWire(
                        new RelationshipRegistration("directive:A", "other::directive:A", "tension")))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refused.getMessage()).contains("self-loop");
                });
        assertThat(RelationshipWire.fromWire(
                                new RelationshipRegistration("directive:A", "other::directive:A", "refines"))
                        .type())
                .isEqualTo(RelationType.REFINES);
    }
}
