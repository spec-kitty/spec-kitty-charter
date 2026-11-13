package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void aLocationIsAFileAndAJsonPointerIntoIt() {
        Location title = Location.in("pack.yaml").at("/title");

        assertThat(title).isEqualTo(new Location("pack.yaml", "/title"));
        assertThat(title.isPlaced()).isTrue();
        // the empty pointer is the whole file
        assertThat(Location.in("pack.yaml").pointer()).isEmpty();
        assertThat(Location.in("pack.yaml").isPlaced()).isTrue();
        // control: nowhere names no file
        assertThat(Location.NOWHERE.isPlaced()).isFalse();
    }

    @Test
    void aPointerNeedsAFileAndStartsWithASlash() {
        assertThatThrownBy(() -> new Location("", "/title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a file");
        assertThatThrownBy(() -> Location.in("pack.yaml").at("title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("starts with '/'");
        assertThatThrownBy(() -> Location.in("")).isInstanceOf(IllegalArgumentException.class);
        // control: a file with no pointer, and no file with no pointer, are both well-formed
        assertThat(new Location("pack.yaml", "")).isEqualTo(Location.in("pack.yaml"));
        assertThat(new Location("", "")).isEqualTo(Location.NOWHERE);
    }

    @Test
    void aReasonCodeIsNotBlankAndEqualByItsValue() {
        assertThat(ReasonCode.of("file_not_held")).isEqualTo(ReasonCode.of("file_not_held"));
        assertThat(ReasonCode.of("file_not_held").value()).isEqualTo("file_not_held");
        assertThatThrownBy(() -> ReasonCode.of(" ")).isInstanceOf(IllegalArgumentException.class);
        // control: a different value is a different code
        assertThat(ReasonCode.of("file_not_held")).isNotEqualTo(ReasonCode.INVARIANT);
    }
}
