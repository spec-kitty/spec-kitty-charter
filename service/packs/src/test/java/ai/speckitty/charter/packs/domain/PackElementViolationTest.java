package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A pack file's violation is at its file, and at the place inside it when the place is known. */
class PackElementViolationTest {

    @Test
    @DisplayName("a violation of the whole file has the empty pointer")
    void aWholeFileViolationHasTheEmptyPointer() {
        PackElementViolation violation = new PackElementViolation("a.graph.yaml", PackCodes.DRG_SCHEMA_ERROR, "d");

        assertThat(violation.file()).isEqualTo("a.graph.yaml");
        assertThat(violation.pointer()).isEmpty();
    }

    @Test
    @DisplayName("a violation at a place inside the file carries its JSON pointer")
    void aPlacedViolationCarriesItsPointer() {
        assertThat(new PackElementViolation("a.graph.yaml", "/edges/0", PackCodes.DRG_SCHEMA_ERROR, "d").pointer())
                .isEqualTo("/edges/0");
    }

    @Test
    @DisplayName("a pointer that does not start with '/' is no JSON pointer")
    void aPointerStartsWithASlash() {
        assertThatThrownBy(() -> new PackElementViolation("a.graph.yaml", "edges/0", PackCodes.DRG_SCHEMA_ERROR, "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("edges/0");
    }
}
