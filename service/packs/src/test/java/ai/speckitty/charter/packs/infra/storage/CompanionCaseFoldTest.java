package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * #272: a companion path is classified case-folded, the fold the write's claim check applies, so every kind of rule
 * (a file rule, an exclusion, a retired rule, a payload) owns every spelling of its files, and the refusal names the
 * rule as the table writes it. A name no rule owns in any case is the companion's.
 */
@DisplayName("A companion path is classified case-folded (#272)")
class CompanionCaseFoldTest {

    private static final String ARTIFACT = "skills/s.skill.yaml";

    @TempDir
    Path host;

    private Companions.Resolution resolve(String path) throws IOException {
        Path packDir =
                Files.createDirectories(host.resolve("pack")).toAbsolutePath().normalize();
        return Companions.resolve(
                Companions.Rules.bundled(), packDir, ARTIFACT, new KindBinding.Companion(path, Optional.empty()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "../pack.md | is a pack file of kind pack-document",
                "../missions/research/README.md | is excluded by file-kinds rule",
                "../missions/research/command-templates/spec.md | is retired by file-kinds rule",
                "../assets/logo.md | is a pack file of kind asset",
                "../templates/spec.md | is a pack file of kind template"
            })
    @DisplayName("a name that differs from a ruled file only in case is refused as that file is, by the same rule")
    void everyKindOfRuleOwnsEverySpelling(String path, String reason) throws IOException {
        Companions.Resolution exact = resolve(path);
        assertThat(exact)
                .isInstanceOfSatisfying(
                        Companions.Refused.class,
                        refused -> assertThat(refused.reason()).startsWith(reason));

        for (String spelling : new String[] {path.toUpperCase(Locale.ROOT), capitalized(path)}) {
            assertThat(resolve(spelling)).as(spelling).isEqualTo(exact);
        }
    }

    @Test
    @DisplayName("positive control: a name no rule owns in any case is the companion's, in any case")
    void aFreeNameIsTheCompanionsInAnyCase() throws IOException {
        assertThat(resolve("Notes.MD")).isInstanceOf(Companions.Inside.class);
        assertThat(resolve("../PACKS.md")).as("a near neighbour of pack.md").isInstanceOf(Companions.Inside.class);
    }

    /** {@code ../missions/Research/Readme.md}: each segment's first letter upper-cased. */
    private static String capitalized(String path) {
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(
                    segment.isEmpty() || segment.startsWith(".")
                            ? segment
                            : Character.toUpperCase(segment.charAt(0)) + segment.substring(1));
        }
        return out.toString();
    }
}
