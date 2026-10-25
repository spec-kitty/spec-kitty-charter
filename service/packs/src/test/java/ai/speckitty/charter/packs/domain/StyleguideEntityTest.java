package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Styleguide entity")
class StyleguideEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01PACK");

    private static Styleguide.Builder valid() {
        return Styleguide.builder()
                .id(new StyleguideIdentifier("java-style"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Java style")
                .scope(StyleguideScope.CODE)
                .principle("Prefer immutability");
    }

    @Test
    @DisplayName("a scope token outside the enum is rejected while a member token resolves")
    void scopeTokenMembership() {
        assertThat(StyleguideScope.fromToken("architecture")).contains(StyleguideScope.ARCHITECTURE);
        assertThat(StyleguideScope.fromToken("gibberish")).isEmpty();
        assertThat(StyleguideScope.fromToken("CODE")).isEmpty();
    }

    @Test
    @DisplayName("principles must be non-empty")
    void principlesNonEmpty() {
        assertThat(valid().build().principles()).containsExactly("Prefer immutability");
        assertThatThrownBy(() -> Styleguide.builder()
                        .id(new StyleguideIdentifier("x"))
                        .packId(PACK)
                        .schemaVersion("1.0")
                        .title("T")
                        .scope(StyleguideScope.DOCS)
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("schema version must be 1.0 and title non-blank")
    void scalarsEnforced() {
        assertThatThrownBy(() -> valid().schemaVersion("2.0").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid().title(" ").build()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("patterns and anti-patterns must be non-empty when present, and may be absent")
    void cardinalityWhenPresent() {
        Styleguide absent = valid().build();
        assertThat(absent.patterns()).isEmpty();
        assertThat(absent.antiPatterns()).isEmpty();

        var pattern = new StyleguidePattern("p", "d", Optional.empty(), Optional.of("good"));
        var anti = new StyleguideAntiPattern("a", "d", "bad", "good");
        Styleguide full = valid().pattern(pattern).antiPattern(anti).build();
        assertThat(full.patterns()).containsExactly(pattern);
        assertThat(full.antiPatterns()).containsExactly(anti);

        assertThatThrownBy(() -> valid().patterns(List.of()).build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid().antiPatterns(List.of()).build()).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("as a charter artifact it is labelled by title and mints no description and no relationships")
    void spineContract() {
        Styleguide sg = valid().reference("Clean Code ch.3").build();

        assertThat(sg.kind()).isEqualTo(ArtifactKind.STYLEGUIDE);
        assertThat(sg.localId()).isEqualTo("java-style");
        assertThat(sg.label()).isEqualTo("Java style");
        assertThat(sg.description()).isEmpty();
        assertThat(sg.relationships()).isEmpty();
        assertThat(sg.references()).containsExactly("Clean Code ch.3");
    }
}
