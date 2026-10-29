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
 * trip. The guide is not a YAML field: the YAML form reads no guide, and the store fills it from the companion
 * ({@link ToolguideGuideTest}).
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
    @DisplayName("a fully populated toolguide survives the round trip, order and duplicates kept, and a date-like"
            + " last_updated is written quoted so a YAML 1.1 reader keeps it a string")
    void aFullToolguideSurvivesTheRoundTrip() {
        ToolguideDso dso = new ToolguideDso(
                "ripgrep",
                "1.0",
                "ripgrep",
                "Use ripgrep",
                "ripgrep.guide.md",
                "How to search with ripgrep.",
                List.of("rg a", "rg b", "rg a"),
                List.of("java", "python"),
                Optional.of("2026-07-29"),
                List.of("docs/a.md", "docs/a.md"),
                Optional.empty());

        String written = yaml.write(ToolguideBinding.INSTANCE, dso);

        assertThat(read(yaml.read(ToolguideBinding.INSTANCE, written, FILE))).isEqualTo(dso);
        // Core loads packs with a YAML 1.1 resolver, which reads a plain 2026-07-29 as a date, not the str it declares.
        assertThat(written).containsPattern("(?m)^last_updated: (['\"])2026-07-29\\1$");
        assertThat(written).as("a plain string stays plain").containsPattern("(?m)^tool: ripgrep$");
    }

    @Test
    @DisplayName("a minimal toolguide normalizes absent lists to empty and keeps last_updated absent")
    void aMinimalToolguideNormalizesAbsentListsToEmpty() {
        ToolguideDso dso = new ToolguideDso(
                "a",
                "1.0",
                "a",
                "T",
                "a.guide.md",
                "S",
                List.of(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty());

        assertThat(read(yaml.read(ToolguideBinding.INSTANCE, yaml.write(ToolguideBinding.INSTANCE, dso), FILE)))
                .isEqualTo(dso);
    }

    @Test
    @DisplayName("a present last_updated and an absent one stay distinct through the round trip")
    void presentAndAbsentLastUpdatedStayDistinct() {
        ToolguideDso present = new ToolguideDso(
                "a",
                "1.0",
                "a",
                "T",
                "a.guide.md",
                "S",
                List.of(),
                List.of(),
                Optional.of("2026-01-01"),
                List.of(),
                Optional.empty());
        ToolguideDso absent = new ToolguideDso(
                "a",
                "1.0",
                "a",
                "T",
                "a.guide.md",
                "S",
                List.of(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty());

        assertThat(read(yaml.read(ToolguideBinding.INSTANCE, yaml.write(ToolguideBinding.INSTANCE, present), FILE)))
                .isEqualTo(present);
        assertThat(read(yaml.read(ToolguideBinding.INSTANCE, yaml.write(ToolguideBinding.INSTANCE, absent), FILE)))
                .isEqualTo(absent);
        assertThat(present).isNotEqualTo(absent);
    }

    @Test
    @DisplayName("an unknown top-level key is unreadable while the same fixture without it reads clean (FR-016)")
    void anUnknownTopLevelKeyIsUnreadableWhileTheKnownKeyControlReadsClean() {
        String clean = "id: a\nschema_version: '1.0'\ntool: T\ntitle: T\nguide_path: a.guide.md\nsummary: S\n";

        assertThat(yaml.read(ToolguideBinding.INSTANCE, clean, FILE)).isInstanceOf(FileRead.Read.class);
        assertThat(yaml.read(ToolguideBinding.INSTANCE, clean + "surprise: 1\n", FILE))
                .isInstanceOf(FileRead.Unreadable.class);
    }

    @Test
    @DisplayName("guide_path is <id>.guide.md beside the toolguide: a repo-rooted, nested, absolute or UPPER_CASE.md"
            + " path is unreadable while the sanitized name reads clean")
    void aGuidePathPatternViolationIsUnreadable() {
        String prefix = "id: a\nschema_version: '1.0'\ntool: T\ntitle: T\nsummary: S\nguide_path: ";

        assertThat(yaml.read(ToolguideBinding.INSTANCE, prefix + "a-b.guide.md\n", FILE))
                .isInstanceOf(FileRead.Read.class);
        for (String refused : List.of(
                "/a.guide.md",
                "toolguides/a.guide.md",
                "packs/internal/toolguides/TERMINOLOGY_GUARD.md",
                "TERMINOLOGY_GUARD.md",
                "a.md",
                "A.guide.md",
                "a-.guide.md")) {
            assertThat(yaml.read(ToolguideBinding.INSTANCE, prefix + refused + "\n", FILE))
                    .as(refused)
                    .isInstanceOf(FileRead.Unreadable.class);
        }
    }
}
