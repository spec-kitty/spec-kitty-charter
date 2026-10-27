package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Template entity")
class TemplateEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01TEMPLATEPACK");
    private static final Payload CONTENT =
            Payload.of("---\ntitle: x\n---\n# Minutes\n".getBytes(StandardCharsets.UTF_8));

    private static Template.Builder valid() {
        return Template.builder()
                .id(new TemplateIdentifier("writing/meeting-minutes.md"))
                .packId(PACK)
                .content(CONTENT);
    }

    @Test
    @DisplayName("the spine is kind=TEMPLATE, localId=path, label=file name, urn=template:<path>")
    void theSpineFollowsTheContract() {
        Template template = valid().build();

        assertThat(template.kind()).isEqualTo(ArtifactKind.TEMPLATE);
        assertThat(template.packId()).isEqualTo(PACK);
        assertThat(template.localId()).isEqualTo("writing/meeting-minutes.md");
        assertThat(template.label()).isEqualTo("meeting-minutes.md");
        assertThat(template.description()).isEmpty();
        assertThat(template.schemaVersion()).isEmpty();
        assertThat(template.relationships()).isEmpty();
        assertThat(template.urn().toString()).isEqualTo("template:writing/meeting-minutes.md");
    }

    @Test
    @DisplayName("a top-level template's label is its whole path")
    void topLevelLabel() {
        assertThat(valid().id(new TemplateIdentifier("plan.md")).build().label())
                .isEqualTo("plan.md");
    }

    @Test
    @DisplayName("the content is kept as bytes and compared by value")
    void contentEquality() {
        Template template = valid().build();

        assertThat(template.content()).isEqualTo(Payload.of(CONTENT.bytes()));
        assertThat(template.content()).isNotEqualTo(Payload.of(new byte[] {0}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/writing/x.md", "../x.md", "writing/../x.md", "writing//x.md", "writing/"})
    @DisplayName("a template identifier that is not a relative path breaches")
    void invalidPathsBreach(String path) {
        assertThatThrownBy(() -> new TemplateIdentifier(path)).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a nested relative path is a valid template identifier")
    void nestedPathIsValid() {
        assertThatCode(() -> new TemplateIdentifier("a/b/c.md")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("two templates are equal by identity")
    void equalByIdentity() {
        assertThat(valid().build())
                .isEqualTo(valid().content(Payload.of(new byte[0])).build());
    }
}
