package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.RelationshipRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import org.junit.jupiter.api.Test;

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
        Relationship mapped = RelationshipWire.fromWire(wire(URN_AT_THE_CAP, "directive:D1"));
        assertThat(mapped.from().toString()).hasSize(255);

        String qualifiedAtTheCap = "p".repeat(64) + "::directive:" + "D".repeat(255 - 64 - 2 - 10);
        assertThat(qualifiedAtTheCap).hasSize(255);
        assertThat(RelationshipWire.fromWire(wire("directive:D1", qualifiedAtTheCap))
                        .toPack())
                .contains("p".repeat(64));
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
}
