package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Toolguide entity")
class ToolguideEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01TOOLPACK");
    private static final Guide GUIDE = Guide.of("ripgrep searches files by regular expression.");

    /** A fully valid builder; each invariant test mutates exactly one field, so the valid case is the positive control. */
    private static Toolguide.Builder valid() {
        return Toolguide.builder()
                .id(new ToolguideIdentifier("ripgrep"))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep for code search")
                .summary("How to search the codebase with ripgrep.")
                .guide(GUIDE)
                .command("rg pattern")
                .appliesToLanguage("java")
                .lastUpdated("2026-10-25")
                .reference("docs/context/search.md");
    }

    @Test
    @DisplayName("a fully populated toolguide builds and exposes its fields")
    void aValidToolguideBuilds() {
        Toolguide toolguide = valid().build();

        assertThat(toolguide.id()).isEqualTo(new ToolguideIdentifier("ripgrep"));
        assertThat(toolguide.packId()).isEqualTo(PACK);
        assertThat(toolguide.tool()).isEqualTo("ripgrep");
        assertThat(toolguide.title()).isEqualTo("Use ripgrep for code search");
        assertThat(toolguide.guidePath())
                .as("beside the toolguide, named by its id")
                .isEqualTo("ripgrep.guide.md");
        assertThat(toolguide.guide()).isEqualTo(GUIDE);
        assertThat(toolguide.summary()).isEqualTo("How to search the codebase with ripgrep.");
        assertThat(toolguide.commands()).containsExactly("rg pattern");
        assertThat(toolguide.appliesToLanguages()).containsExactly("java");
        assertThat(toolguide.lastUpdated()).contains("2026-10-25");
        assertThat(toolguide.references()).containsExactly("docs/context/search.md");
    }

    @Test
    @DisplayName("the CharterArtifact spine is kind=TOOLGUIDE, label=title, description=summary, urn=toolguide:<id>")
    void theSpineFollowsTheContract() {
        Toolguide toolguide = valid().build();

        assertThat(toolguide.kind()).isEqualTo(ArtifactKind.TOOLGUIDE);
        assertThat(toolguide.localId()).isEqualTo("ripgrep");
        assertThat(toolguide.schemaVersion().orElseThrow()).isEqualTo("1.0");
        assertThat(toolguide.label()).isEqualTo("Use ripgrep for code search");
        assertThat(toolguide.description()).contains("How to search the codebase with ripgrep.");
        assertThat(toolguide.urn().toString()).isEqualTo("toolguide:ripgrep");
    }

    @Test
    @DisplayName("a toolguide mints no relationships: its references are bare citations")
    void relationshipsAreEmpty() {
        assertThat(valid().build().relationships()).isEmpty();
    }

    @Test
    @DisplayName("a minimal toolguide with absent optionals and empty lists builds")
    void aMinimalToolguideBuilds() {
        Toolguide minimal = Toolguide.builder()
                .id(new ToolguideIdentifier("a"))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("a")
                .title("t")
                .summary("s")
                .guide(Guide.of("o"))
                .build();

        assertThat(minimal.lastUpdated()).isEmpty();
        assertThat(minimal.commands()).isEmpty();
        assertThat(minimal.appliesToLanguages()).isEmpty();
        assertThat(minimal.references()).isEmpty();
    }

    @Test
    @DisplayName("a wrong schema version is rejected; 1.0 is accepted")
    void schemaVersionIsGuarded() {
        assertThatCode(() -> valid().schemaVersion("1.0").build()).doesNotThrowAnyException();
        assertThatThrownBy(() -> valid().schemaVersion("2.0").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("schema version of 1.0");
    }

    @Test
    @DisplayName("a blank tool, title or summary is rejected; the non-blank control is accepted")
    void nonBlankTextIsGuarded() {
        assertThatCode(() -> valid().build()).doesNotThrowAnyException();
        assertThatThrownBy(() -> valid().tool(" ").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("tool that is not blank");
        assertThatThrownBy(() -> valid().title(" ").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("title that is not blank");
        assertThatThrownBy(() -> valid().summary(" ").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("summary that is not blank");
    }

    @Test
    @DisplayName("the guide path is <id>.guide.md: that name is accepted, a repo-rooted, nested or other name is not")
    void guidePathIsTheIdsGuideFile() {
        assertThatCode(() -> valid().guidePath("ripgrep.guide.md").build()).doesNotThrowAnyException();
        for (String other : List.of(
                "guides/ripgrep.guide.md",
                "packs/built-in/toolguides/RIPGREP.md",
                "RIPGREP.md",
                "ripgrep.md",
                "other.guide.md",
                "/ripgrep.guide.md")) {
            assertThatThrownBy(() -> valid().guidePath(other).build())
                    .as(other)
                    .isInstanceOf(ConstraintBreachException.class)
                    .hasMessageContaining("<id>.guide.md");
        }
    }

    @Test
    @DisplayName("a toolguide needs its guide; the guided control builds")
    void aToolguideNeedsItsGuide() {
        Toolguide.Builder unguided = Toolguide.builder()
                .id(new ToolguideIdentifier("a"))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("a")
                .title("t")
                .summary("s");

        assertThatThrownBy(unguided::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("guide");
        assertThatCode(() -> unguided.guide(Guide.of("o")).build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an identifier that breaks the schema pattern is rejected; a valid one is accepted")
    void identifierPatternIsGuarded() {
        assertThatCode(() -> new ToolguideIdentifier("ripgrep-2")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ToolguideIdentifier("Ripgrep"))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("^[a-z][a-z0-9-]*$");
    }

    @Test
    @DisplayName("two toolguides are equal when their identity is equal, whatever else they hold")
    void equalityIsByIdentity() {
        Toolguide one = valid().build();
        Toolguide sameId = valid().title("A different title").build();
        Toolguide otherId = valid().id(new ToolguideIdentifier("other")).build();

        assertThat(one).isEqualTo(sameId).hasSameHashCodeAs(sameId);
        assertThat(one).isNotEqualTo(otherId);
    }
}
