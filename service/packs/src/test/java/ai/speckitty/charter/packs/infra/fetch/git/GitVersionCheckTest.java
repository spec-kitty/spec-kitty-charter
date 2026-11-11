package ai.speckitty.charter.packs.infra.fetch.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Whether the host has a git the source may use (T088, T152, NFR-011, C-008): the version is read once from
 * {@code git --version} and the source is off below 2.50.1 and without a binary. The binary is the fake git of
 * {@link GitShim}, so no host git is needed.
 */
class GitVersionCheckTest {

    @TempDir
    Path temp;

    private GitVersionCheck check(String versionText) {
        GitShim shim = GitShim.in(temp).version(versionText);
        return GitVersionCheck.resolve(shim.executable(), null);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "git version 2.43.0|TOO_OLD",
                "git version 2.50.0|TOO_OLD",
                "git version 2.50.1|ENABLED",
                "git version 2.51.0|ENABLED",
                "git version 3.0.0|ENABLED",
                "git version 2.50.1.windows.1|ENABLED",
                "git version 2.39.5 (Apple Git-154)|TOO_OLD",
                "git version 2.51.0-rc1|ENABLED",
                "git version 1.99.99|TOO_OLD",
            })
    void theVersionDecidesWhetherTheSourceIsOn(String output, GitVersionCheck.Status expected) {
        GitVersionCheck found = check(output + "\n");

        assertThat(found.status()).isEqualTo(expected);
        assertThat(found.enabled()).isEqualTo(expected == GitVersionCheck.Status.ENABLED);
        assertThat(found.version()).isPresent();
    }

    @Test
    void anEnabledCheckNamesTheBinaryAndTheVersion() {
        GitShim shim = GitShim.in(temp).version("git version 2.50.1\n");

        GitVersionCheck found = GitVersionCheck.resolve(shim.executable(), null);

        assertThat(found.executable()).isEqualTo(shim.executable());
        assertThat(found.version()).contains(new GitVersion(2, 50, 1));
        assertThat(found.describe())
                .contains("2.50.1")
                .contains(shim.executable().toString());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "git version 2.51.0|2.51.0",
                "git version 2.50.1.windows.1|2.50.1",
                "git version 2.39.5 (Apple Git-154)|2.39.5",
                "git version 2.51|2.51.0",
                "git version 10.2.3|10.2.3",
            })
    void aVersionIsReadFromTheFirstThreeNumbers(String output, String expected) {
        assertThat(GitVersion.parse(output + "\n").map(GitVersion::toString)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "''",
                "garbage",
                "git version",
                "git version x.y.z",
                "not git version 2.51.0",
                "git version 2.x.0",
                "git version -2.51.0",
            })
    void anythingElseIsNotAVersion(String output) {
        assertThat(GitVersion.parse(output.equals("''") ? "" : output)).isEmpty();
    }

    @Test
    void versionsCompareByNumberNotByText() {
        assertThat(new GitVersion(2, 9, 0)).isLessThan(new GitVersion(2, 10, 0));
        assertThat(new GitVersion(2, 50, 1)).isEqualByComparingTo(new GitVersion(2, 50, 1));
        assertThat(new GitVersion(2, 50, 2)).isGreaterThan(GitVersionCheck.MINIMUM_VERSION);
        assertThat(new GitVersion(3, 0, 0)).isGreaterThan(new GitVersion(2, 99, 99));
    }

    @Test
    void garbageOutputIsUnreadableAndDisablesTheSource() {
        GitVersionCheck found = check("hello from nowhere\n");

        assertThat(found.status()).isEqualTo(GitVersionCheck.Status.UNREADABLE);
        assertThat(found.enabled()).isFalse();
        assertThat(found.version()).isEmpty();
    }

    @Test
    void aBinaryThatFailsIsUnreadable() throws IOException {
        Path failing = script("exit-3", "#!/bin/sh\necho 'git version 2.51.0'\nexit 3\n");

        assertThat(GitVersionCheck.resolve(failing, null).status()).isEqualTo(GitVersionCheck.Status.UNREADABLE);
    }

    @Test
    void aConfiguredPathThatIsNotThereIsMissing() {
        GitVersionCheck found = GitVersionCheck.resolve(temp.resolve("no-such-git"), "");

        assertThat(found.status()).isEqualTo(GitVersionCheck.Status.MISSING);
        assertThat(found.enabled()).isFalse();
        assertThat(found.describe()).contains("charter.pack-sources.git.executable");
    }

    @Test
    void aConfiguredPathThatIsNotExecutableIsMissing() throws IOException {
        Path notExecutable = temp.resolve("plain-file");
        Files.writeString(notExecutable, "#!/bin/sh\necho 'git version 2.51.0'\n");

        assertThat(GitVersionCheck.resolve(notExecutable, null).status()).isEqualTo(GitVersionCheck.Status.MISSING);
    }

    @Test
    void aConfiguredPathMustBeAbsolute() {
        assertThat(GitVersionCheck.resolve(Path.of("git"), "/usr/bin").status())
                .isEqualTo(GitVersionCheck.Status.MISSING);
    }

    @Test
    void withNothingConfiguredGitIsLookedForOnThePath() {
        GitShim shim = GitShim.in(temp).version("git version 2.50.1\n");

        GitVersionCheck found = GitVersionCheck.resolve(null, shim.directory().toString());

        assertThat(found.status()).isEqualTo(GitVersionCheck.Status.ENABLED);
        assertThat(found.executable()).isEqualTo(shim.executable());
    }

    @Test
    void aPathWithNoGitOrOnlyRelativeEntriesFindsNone() {
        assertThat(GitVersionCheck.resolve(null, temp.toString()).status()).isEqualTo(GitVersionCheck.Status.MISSING);
        assertThat(GitVersionCheck.resolve(null, "bin:.:").status()).isEqualTo(GitVersionCheck.Status.MISSING);
        assertThat(GitVersionCheck.resolve(null, null).status()).isEqualTo(GitVersionCheck.Status.MISSING);
    }

    @Test
    void theConfiguredPathWinsOverThePath() throws IOException {
        GitShim configured = GitShim.in(temp.resolve("a")).version("git version 2.50.1\n");
        GitShim onPath = GitShim.in(temp.resolve("b")).version("git version 2.43.0\n");

        GitVersionCheck found = GitVersionCheck.resolve(
                configured.executable(), onPath.directory().toString());

        assertThat(found.status()).isEqualTo(GitVersionCheck.Status.ENABLED);
        assertThat(found.executable()).isEqualTo(configured.executable());
        assertThat(onPath.calls()).isEmpty();
    }

    @Test
    void theVersionIsAskedOnceInAnOwnerOnlyHomeWithAnEnvironmentBuiltFromEmpty() {
        GitShim shim = GitShim.in(temp).version("git version 2.51.0\n");

        GitVersionCheck.resolve(shim.executable(), null);

        assertThat(shim.calls()).singleElement().satisfies(call -> {
            assertThat(call.arguments()).containsExactly("--version");
            Map<String, String> environment = new HashMap<>(call.environment());
            // The shell the shim runs in adds its own bookkeeping variables; nothing else is there.
            environment.keySet().removeAll(Set.of("PWD", "_", "SHLVL", "OLDPWD"));
            assertThat(environment.keySet())
                    .containsExactlyInAnyOrder(
                            "PATH",
                            "HOME",
                            "XDG_CONFIG_HOME",
                            "GIT_CONFIG_NOSYSTEM",
                            "GIT_CONFIG_GLOBAL",
                            "GIT_TERMINAL_PROMPT",
                            "LC_ALL",
                            "GIT_ALLOW_PROTOCOL",
                            "GIT_LFS_SKIP_SMUDGE");
            assertThat(call.environment())
                    .containsEntry("PATH", shim.directory().toString())
                    .containsEntry("HOME", call.cwd());
            assertThat(call.cwdMode())
                    .as("git runs in, and reads its config from, a home no other user can open or plant a file in")
                    .isEqualTo("drwx------");
        });
    }

    @Test
    void anUnenabledCheckHasNoExecutableToRun() {
        GitVersionCheck found = check("git version 2.43.0\n");

        assertThatThrownBy(found::executable).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theMinimumIsTwoFiftyOne() {
        assertThat(GitVersionCheck.MINIMUM_VERSION).isEqualTo(new GitVersion(2, 50, 1));
        assertThat(Optional.of(GitVersionCheck.MINIMUM_VERSION).map(GitVersion::toString))
                .contains("2.50.1");
    }

    private Path script(String name, String text) throws IOException {
        Path file = temp.resolve(name);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        return file;
    }
}
