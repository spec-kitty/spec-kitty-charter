package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Spellings} reads a path's spelling from the directory listings (#252, #300), so these cases hold on a
 * case-sensitive and a case-insensitive file system alike.
 */
@DisplayName("A path's spelling, read from the listings")
class SpellingsTest {

    @Test
    void aPathListedAsWrittenIsExactAndAnUnlistedOneIsAbsentWhateverTheCase(@TempDir Path pack) throws IOException {
        Files.createDirectories(pack.resolve("templates/x"));
        Files.writeString(pack.resolve("templates/x/a.md"), "a");

        assertThat(Spellings.of(pack, "templates/x/a.md")).isEqualTo(new Spellings.Exact());
        assertThat(Spellings.of(pack, "templates/x/b.md")).isEqualTo(new Spellings.Absent());
        assertThat(Spellings.of(pack, "Templates/x/B.md"))
                .as("no file stands for it in any case, so it is absent, not respelled")
                .isEqualTo(new Spellings.Absent());
    }

    @Test
    void aPathListedOnlyInAnotherCaseIsGivenWithItsWholeOnDiskSpelling(@TempDir Path pack) throws IOException {
        Files.createDirectories(pack.resolve("Templates/x"));
        Files.writeString(pack.resolve("Templates/x/Spec.md"), "s");

        assertThat(Spellings.of(pack, "templates/x/spec.md")).isEqualTo(new Spellings.OtherCase("Templates/x/Spec.md"));
        assertThat(Spellings.of(pack, "Templates/x/spec.md")).isEqualTo(new Spellings.OtherCase("Templates/x/Spec.md"));
    }

    @Test
    void anExactEntryWinsOverOneInAnotherCaseAsACaseInsensitiveFileSystemOpensIt(@TempDir Path pack)
            throws IOException {
        Files.writeString(pack.resolve("a.md"), "a");
        Files.writeString(pack.resolve("A.md"), "A");

        assumeTrue(
                Files.readString(pack.resolve("a.md")).equals("a"),
                "two spellings stand only on a case-sensitive file system; elsewhere both names are one file");

        assertThat(Spellings.of(pack, "a.md")).isEqualTo(new Spellings.Exact());
        assertThat(Spellings.collision(pack, "a.md"))
                .as("a write lands on whichever file a case-insensitive file system opens, so it collides")
                .contains("A.md");
    }

    @Test
    void aWriteCollidesWithTheFirstEntryOnItsPathInAnotherCaseAndWithNothingElse(@TempDir Path pack)
            throws IOException {
        Files.createDirectories(pack.resolve("templates/x"));
        Files.writeString(pack.resolve("templates/x/A.md"), "A");

        assertThat(Spellings.collision(pack, "templates/x/a.md")).contains("templates/x/A.md");
        assertThat(Spellings.collision(pack, "templates/X/new.md")).contains("templates/x");
        assertThat(Spellings.collision(pack, "templates/x/A.md")).isEmpty();
        assertThat(Spellings.collision(pack, "templates/y/new.md")).isEmpty();
    }
}
