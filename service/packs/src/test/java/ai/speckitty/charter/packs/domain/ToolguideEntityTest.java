package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Toolguide entity")
class ToolguideEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01TOOLPACK");

    /** A fully valid builder; each invariant test mutates exactly one field, so the valid case is the positive control. */
    private static Toolguide.Builder valid() {
        return Toolguide.builder()
                .id(new ToolguideIdentifier("ripgrep"))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep for code search")
                .guidePath("guides/ripgrep.md")
                .summary("How to search the codebase with ripgrep.")
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
        assertThat(toolguide.guidePath()).isEqualTo("guides/ripgrep.md");
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
                .guidePath("a.md")
                .summary("s")
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
    @DisplayName("a non-relative or non-markdown guide path is rejected; a relative *.md path is accepted")
    void guidePathPatternIsGuarded() {
        assertThatCode(() -> valid().guidePath("guides/ripgrep.md").build()).doesNotThrowAnyException();
        assertThatThrownBy(() -> valid().guidePath("/abs/ripgrep.md").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("relative *.md path");
        assertThatThrownBy(() -> valid().guidePath("ripgrep.txt").build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("relative *.md path");
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
