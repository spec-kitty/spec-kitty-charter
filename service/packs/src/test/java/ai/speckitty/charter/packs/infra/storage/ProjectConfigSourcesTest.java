package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.Fingerprint;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackSources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What the source fingerprint digests besides the packs: the two project configuration files, with presence. */
class ProjectConfigSourcesTest {

    private static final String PACKS = "sha256:" + "ab".repeat(32);

    @TempDir
    Path directory;

    private static ProjectConfigSources over(PackSources packs, Supplier<Optional<Path>> dir) {
        return new ProjectConfigSources(packs, dir);
    }

    private ProjectConfigSources sources() {
        return over(() -> PACKS, () -> Optional.of(directory));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(directory.resolve(name), content, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yaml", "charter.yaml"})
    void anEditToEitherFileChangesTheDigestAndRestoringItRestoresTheDigest(String name) throws IOException {
        write("config.yaml", "a");
        write("charter.yaml", "b");
        String before = sources().fingerprint();

        write(name, "edited");
        assertThat(sources().fingerprint()).isNotEqualTo(before);

        write(name, name.equals("config.yaml") ? "a" : "b");
        assertThat(sources().fingerprint()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yaml", "charter.yaml"})
    void anAbsentFileAndAnEmptyOneDigestDifferently(String name) throws IOException {
        String absent = sources().fingerprint();

        write(name, "");

        assertThat(sources().fingerprint()).isNotEqualTo(absent);
    }

    @Test
    void theTwoFilesDoNotSwapContentsWithoutChangingTheDigest() throws IOException {
        write("config.yaml", "x");
        write("charter.yaml", "y");
        String before = sources().fingerprint();

        write("config.yaml", "y");
        write("charter.yaml", "x");

        assertThat(sources().fingerprint()).isNotEqualTo(before);
    }

    @Test
    void theDigestChangesWithThePacksDigest() {
        assertThat(sources().fingerprint())
                .isNotEqualTo(over(() -> "sha256:" + "cd".repeat(32), () -> Optional.of(directory))
                        .fingerprint());
    }

    @Test
    void withoutADirectoryTheDigestIsStableAndDiffersFromTheOneOverAnEmptyDirectory() {
        ProjectConfigSources none = over(() -> PACKS, Optional::empty);

        assertThat(none.fingerprint()).matches("sha256:[0-9a-f]{64}").isEqualTo(none.fingerprint());
        assertThat(none.fingerprint()).isNotEqualTo(sources().fingerprint());
    }

    @Test
    void theDigestHasTheDocumentedLayout() throws IOException {
        write("config.yaml", "c");

        String expected = Fingerprint.sha256()
                .add("charter-sources/v1")
                .add(PACKS)
                .add("project-config/config.yaml")
                .add(new byte[] {1})
                .add("c")
                .add("project-config/charter.yaml")
                .add(new byte[] {0})
                .value();

        assertThat(sources().fingerprint()).isEqualTo(expected);
        assertThat(over(() -> PACKS, Optional::empty).fingerprint())
                .isEqualTo(Fingerprint.sha256()
                        .add("charter-sources/v1")
                        .add(PACKS)
                        .value());
    }

    @Test
    void aPackNamedProjectConfigCannotImpersonateTheLabel() throws IOException {
        // A flat scheme, in which pack files and configuration labels share one sequence of fields, would digest a
        // pack "project-config" with a file config.yaml like the configuration file itself.
        String flatPackDigest =
                Fingerprint.sha256().add("project-config/config.yaml").add("c").value();
        write("config.yaml", "c");
        ProjectConfigSources configured = over(() -> PACKS, () -> Optional.of(directory));
        ProjectConfigSources impersonating = over(() -> flatPackDigest, Optional::empty);

        assertThat(impersonating.fingerprint()).isNotEqualTo(configured.fingerprint());
        // And the packs digest is one field, so a pack digest that equals the tag cannot stand in for it either.
        assertThat(over(() -> "charter-sources/v1", Optional::empty).fingerprint())
                .isNotEqualTo(over(() -> PACKS, Optional::empty).fingerprint());
    }

    @Test
    void aDirectoryThatIsNotThereIsRefusedWithoutNamingItsPath() {
        Path missing = directory.resolve("gone");

        assertThatThrownBy(() -> over(() -> PACKS, () -> Optional.of(missing)).fingerprint())
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("the project configuration directory is not a directory")
                .message()
                .doesNotContain(missing.toString());
    }

    @Test
    void aFileThatExistsButCannotBeReadIsRefusedWithoutNamingItsPath() throws IOException {
        Files.createDirectory(directory.resolve("charter.yaml"));

        assertThatThrownBy(() -> sources().fingerprint())
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("project-config/charter.yaml cannot be read")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void theDirectoryIsAskedAgainOnEveryDigest() throws IOException {
        Path other = Files.createDirectory(directory.resolve("other"));
        Files.writeString(other.resolve("config.yaml"), "z");
        Path[] current = {directory};
        ProjectConfigSources sources = over(() -> PACKS, () -> Optional.of(current[0]));
        String first = sources.fingerprint();

        current[0] = other;

        assertThat(sources.fingerprint()).isNotEqualTo(first);
    }
}
