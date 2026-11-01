package ai.speckitty.charter.contracts.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangelogFragmentsTest {

    private static final String LOG = """
            # Changelog

            Preamble.

            ## 0.2.0-SNAPSHOT

            Intro.

            ### Breaking

            - an older break

            ### An older change (issue #1)

            Its paragraph.

            ### Added

            - an older addition

            ## 0.1.0

            ### Added

            - released
            """;

    @TempDir
    Path dir;

    private Path fragment(String name, String text) throws IOException {
        return Files.writeString(dir.resolve(name), text);
    }

    @Test
    void theReadmeIsNotAFragmentAndFragmentsAreReadNewestFirst() throws IOException {
        fragment("README.md", "# How to write a fragment\n");
        fragment("2026-11-01-a.md", "### Added\n\n- a\n");
        fragment("2026-11-02-b.md", "### Added\n\n- b\n");
        assertThat(ChangelogFragments.read(dir))
                .extracting(ChangelogFragments.Fragment::name)
                .containsExactly("2026-11-02-b.md", "2026-11-01-a.md");
    }

    @Test
    void aMissingDirectoryHoldsNoFragment() throws IOException {
        assertThat(ChangelogFragments.read(dir.resolve("absent"))).isEmpty();
    }

    @Test
    void aFragmentUnderAnExistingSubsectionGoesToTheTopOfIt() throws IOException {
        fragment("2026-11-01-a.md", "\n### Added\n\n- addition a\n- with two lines\n\n");
        fragment("2026-11-02-b.md", "### Breaking\n\n- break b\n");
        fragment("2026-11-03-c.md", "### Added\n\n- addition c\n");
        String assembled = ChangelogFragments.assemble(LOG, ChangelogFragments.read(dir));
        assertThat(assembled)
                .contains("### Breaking\n\n- break b\n\n- an older break\n")
                .contains("### Added\n\n- addition c\n\n- addition a\n- with two lines\n\n- an older addition\n")
                // The released section below is untouched.
                .endsWith("## 0.1.0\n\n### Added\n\n- released\n");
    }

    @Test
    void aFragmentWithItsOwnHeadingGoesRightAfterTheBreakingSubsection() throws IOException {
        fragment("2026-11-01-a.md", "### A change (issue #2)\n\nIts paragraph.\n\n- a bullet\n");
        fragment("2026-11-02-b.md", "### B change (issue #3)\n\nIts paragraph.\n");
        String assembled = ChangelogFragments.assemble(LOG, ChangelogFragments.read(dir));
        assertThat(assembled).contains("""
                        - an older break

                        ### B change (issue #3)

                        Its paragraph.

                        ### A change (issue #2)

                        Its paragraph.

                        - a bullet

                        ### An older change (issue #1)
                        """);
    }

    @Test
    void withoutABreakingSubsectionAnOwnHeadingGoesBeforeTheFirstSubsection() throws IOException {
        fragment("2026-11-01-a.md", "### A change\n\nText.\n");
        String log = "## 1.1.0-SNAPSHOT\n\nIntro.\n\n### Added\n\n- x\n";
        assertThat(ChangelogFragments.assemble(log, ChangelogFragments.read(dir)))
                .isEqualTo("## 1.1.0-SNAPSHOT\n\nIntro.\n\n### A change\n\nText.\n\n### Added\n\n- x\n");
    }

    @Test
    void inASectionWithNoSubsectionTheFragmentEndsTheSection() throws IOException {
        fragment("2026-11-01-a.md", "### Added\n\n- x\n");
        String log = "## 1.1.0-SNAPSHOT\n\nIntro.\n\n## 1.0.0\n\n- old\n";
        assertThat(ChangelogFragments.assemble(log, ChangelogFragments.read(dir)))
                .isEqualTo("## 1.1.0-SNAPSHOT\n\nIntro.\n\n### Added\n\n- x\n\n## 1.0.0\n\n- old\n");
    }

    @Test
    void aHeadingInsideAFencedBlockIsNotASubsection() throws IOException {
        fragment("2026-11-01-a.md", "### Added\n\n- new\n");
        String log = "## 1.1.0-SNAPSHOT\n\n```\n### Added\n```\n\n### Added\n\n- old\n";
        assertThat(ChangelogFragments.assemble(log, ChangelogFragments.read(dir)))
                .isEqualTo("## 1.1.0-SNAPSHOT\n\n```\n### Added\n```\n\n### Added\n\n- new\n\n- old\n");
    }

    @Test
    void assemblingNoFragmentChangesNothing() {
        assertThat(ChangelogFragments.assemble(LOG, List.of())).isEqualTo(LOG);
    }

    @Test
    void aFragmentMustBeOneDatedLevelThreeBlock() throws IOException {
        fragment("notes.md", "### Added\n\n- x\n");
        assertThatThrownBy(() -> ChangelogFragments.read(dir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notes.md")
                .hasMessageContaining("YYYY-MM-DD-slug.md");
        Files.delete(dir.resolve("notes.md"));

        fragment("2026-11-01-a.md", "- no heading\n");
        assertThatThrownBy(() -> ChangelogFragments.read(dir)).hasMessageContaining("must start with a `### ` heading");
        fragment("2026-11-01-a.md", "### Added\n");
        assertThatThrownBy(() -> ChangelogFragments.read(dir)).hasMessageContaining("has no text under its heading");
        fragment("2026-11-01-a.md", "### Added\n\n- x\n\n### Changed\n\n- y\n");
        assertThatThrownBy(() -> ChangelogFragments.read(dir)).hasMessageContaining("one `###` block");
        fragment("2026-11-01-a.md", "### Added\n\n- x\n\n## 9.9.9\n");
        assertThatThrownBy(() -> ChangelogFragments.read(dir)).hasMessageContaining("one `###` block");
    }

    @Test
    void aChangelogWithNoVersionSectionIsRefused() throws IOException {
        fragment("2026-11-01-a.md", "### Added\n\n- x\n");
        List<ChangelogFragments.Fragment> fragments = ChangelogFragments.read(dir);
        assertThatThrownBy(() -> ChangelogFragments.assemble("# Changelog\n", fragments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no `## ` version section");
    }

    @Test
    void theCommandLineAssemblesThenRemovesTheFragments() throws IOException {
        Path log = Files.writeString(dir.resolve("CHANGELOG.md"), LOG);
        Path changes = Files.createDirectory(dir.resolve("changes"));
        Files.writeString(changes.resolve("README.md"), "# Fragments\n");
        Files.writeString(changes.resolve("2026-11-01-a.md"), "### Added\n\n- addition a\n");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int code = ChangelogFragments.run(
                List.of(log.toString(), changes.toString()),
                new java.io.PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(code).isZero();
        assertThat(Files.readString(log)).contains("### Added\n\n- addition a\n\n- an older addition\n");
        assertThat(changes.resolve("2026-11-01-a.md")).doesNotExist();
        assertThat(changes.resolve("README.md")).exists();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("Assembled 1 fragment into " + log + ": 2026-11-01-a.md\n");
    }
}
