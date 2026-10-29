package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pack presets (#195)")
class PackPresetsTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackDescriptor DESCRIPTOR =
            new PackDescriptor("01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty());

    private static Preset preset(String id) {
        return new Preset(
                id, "d", List.of(), Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("GUARD"))), List.of());
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(ID)
                .schemaVersion("1.0")
                .title("t")
                .intent("i")
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Pack.Builder bare() {
        return Pack.builder().id(ID).descriptor(DESCRIPTOR);
    }

    @Test
    @DisplayName("a pack holds no preset unless one is added")
    void noPresetByDefault() {
        assertThat(bare().build().presets()).isEmpty();
    }

    @Test
    @DisplayName("the presets are sorted by id, whatever order they were added in")
    void presetsAreSortedById() {
        Pack pack = bare().preset(preset("minimal")).preset(preset("default")).build();

        assertThat(pack.presets()).extracting(Preset::id).containsExactly("default", "minimal");
    }

    @Test
    @DisplayName("two presets with one id breach, naming the id")
    void twoPresetsWithOneIdBreach() {
        assertThatThrownBy(() -> bare().preset(preset("minimal"))
                        .preset(preset("minimal"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("A pack must not hold two presets with the id 'minimal'.");
    }

    @Test
    @DisplayName("toBuilder keeps the presets")
    void toBuilderKeepsThePresets() {
        Pack pack = bare().preset(preset("default")).preset(preset("minimal")).build();

        assertThat(pack.toBuilder().build().presets()).isEqualTo(pack.presets());
    }

    @Test
    @DisplayName("adding or replacing an artifact keeps the presets")
    void withKeepsThePresets() {
        Pack pack = bare().preset(preset("default")).artifact(directive("D1")).build();

        Pack added = pack.with(directive("D2"));
        Pack replaced = added.with(directive("D1"));

        assertThat(added.presets()).isEqualTo(pack.presets());
        assertThat(replaced.presets()).isEqualTo(pack.presets());
        assertThat(replaced.directives()).extracting(d -> d.localId()).containsExactly("D2", "D1");
    }
}
