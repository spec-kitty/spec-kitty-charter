package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fidelity bar of the toolguide kind is DSO-record equality: {@code readToolguide(writeToolguide(dso))} equals
 * {@code dso} (NFR-003). The present/empty/absent cases of the optionals and lists stay distinct through the round
 * trip.
 */
@DisplayName("ToolguideDso round trip")
class ToolguideDsoRoundTripTest {

    private static final String FILE = "x.toolguide.yaml";
    private final SerializationStrategy yaml = new YamlSerializationStrategy();

    private static <T> T read(FileRead<T> result) {
        return switch (result) {
            case FileRead.Read<T> ok -> ok.value();
            default -> throw new AssertionError("expected a clean read but got " + result);
        };
    }

    @Test
    @DisplayName("a fully populated toolguide survives the round trip, order and duplicates kept")
    void aFullToolguideSurvivesTheRoundTrip() {
        ToolguideDso dso = new ToolguideDso(
                "ripgrep",
                "1.0",
                "ripgrep",
                "Use ripgrep",
                "guides/ripgrep.md",
                "How to search with ripgrep.",
                List.of("rg a", "rg b", "rg a"),
                List.of("java", "python"),
                Optional.of("2026-10-25"),
                List.of("docs/a.md", "docs/a.md"));

        assertThat(read(yaml.readToolguide(yaml.writeToolguide(dso), FILE))).isEqualTo(dso);
    }

    @Test
    @DisplayName("a minimal toolguide normalizes absent lists to empty and keeps last_updated absent")
    void aMinimalToolguideNormalizesAbsentListsToEmpty() {
        ToolguideDso dso =
                new ToolguideDso("a", "1.0", "a", "T", "a.md", "S", List.of(), List.of(), Optional.empty(), List.of());

        assertThat(read(yaml.readToolguide(yaml.writeToolguide(dso), FILE))).isEqualTo(dso);
    }

    @Test
    @DisplayName("a present last_updated and an absent one stay distinct through the round trip")
    void presentAndAbsentLastUpdatedStayDistinct() {
        ToolguideDso present = new ToolguideDso(
                "a", "1.0", "a", "T", "a.md", "S", List.of(), List.of(), Optional.of("2026-01-01"), List.of());
        ToolguideDso absent =
                new ToolguideDso("a", "1.0", "a", "T", "a.md", "S", List.of(), List.of(), Optional.empty(), List.of());

        assertThat(read(yaml.readToolguide(yaml.writeToolguide(present), FILE))).isEqualTo(present);
        assertThat(read(yaml.readToolguide(yaml.writeToolguide(absent), FILE))).isEqualTo(absent);
        assertThat(present).isNotEqualTo(absent);
    }

    @Test
    @DisplayName("an unknown top-level key is unreadable while the same fixture without it reads clean (FR-016)")
    void anUnknownTopLevelKeyIsUnreadableWhileTheKnownKeyControlReadsClean() {
        String clean = "id: a\nschema_version: '1.0'\ntool: T\ntitle: T\nguide_path: g.md\nsummary: S\n";

        assertThat(yaml.readToolguide(clean, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.readToolguide(clean + "surprise: 1\n", FILE)).isInstanceOf(FileRead.Unreadable.class);
    }

    @Test
    @DisplayName("a guide_path that breaks the schema pattern is unreadable while a relative *.md reads clean")
    void aGuidePathPatternViolationIsUnreadable() {
        String relative = "id: a\nschema_version: '1.0'\ntool: T\ntitle: T\nguide_path: g.md\nsummary: S\n";
        String absolute = "id: a\nschema_version: '1.0'\ntool: T\ntitle: T\nguide_path: /g.md\nsummary: S\n";

        assertThat(yaml.readToolguide(relative, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.readToolguide(absolute, FILE)).isInstanceOf(FileRead.Unreadable.class);
    }
}
