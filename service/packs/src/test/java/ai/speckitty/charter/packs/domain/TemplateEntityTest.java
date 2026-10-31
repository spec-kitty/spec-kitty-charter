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
    @DisplayName("a mission type's template keeps its type when its id starts with '<type>/'")
    void aMissionTypesTemplateKeepsItsType() {
        Template template = valid().id(new TemplateIdentifier("plan/spec.md"))
                .missionType("plan")
                .build();

        assertThat(template.missionType()).contains("plan");
        assertThat(template.localId()).isEqualTo("plan/spec.md");
    }

    @Test
    @DisplayName("a mission type's template whose id does not start with '<type>/' is refused")
    void aMissionTypesTemplateOutsideItsTypeIsRefused() {
        assertThatThrownBy(() -> valid().id(new TemplateIdentifier("research/spec.md"))
                        .missionType("plan")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a mission type's template id starts with '<type>/': 'research/spec.md' of 'plan'");
        assertThatThrownBy(() -> valid().id(new TemplateIdentifier("plan.md"))
                        .missionType("plan")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid().id(new TemplateIdentifier("planning/spec.md"))
                        .missionType("plan")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a mission type holding '/' is refused, even when the id starts with it")
    void aMissionTypeWithASlashIsRefused() {
        assertThatThrownBy(() -> valid().id(new TemplateIdentifier("plan/sub/spec.md"))
                        .missionType("plan/sub")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a mission type's template id starts with '<type>/': 'plan/sub/spec.md' of 'plan/sub'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("a blank mission type is refused")
    void aBlankMissionTypeIsRefused(String type) {
        assertThatThrownBy(() -> valid().id(new TemplateIdentifier("plan/spec.md"))
                        .missionType(type)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plan.md", "plan/spec.md", "research/deep/notes.md"})
    @DisplayName("a top-level template, with no mission type, accepts any template identifier")
    void aTopLevelTemplateAcceptsAnyId(String id) {
        Template template = valid().id(new TemplateIdentifier(id)).build();

        assertThat(template.missionType()).isEmpty();
        assertThat(template.localId()).isEqualTo(id);
    }

    @Test
    @DisplayName("two templates are equal by identity")
    void equalByIdentity() {
        assertThat(valid().build())
                .isEqualTo(valid().content(Payload.of(new byte[0])).build());
    }
}
