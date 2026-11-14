package ai.speckitty.charter.packs.infra.fetch.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.packs.infra.fetch.git.GitFailure.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How git is run (T089, T152, plan.md "The system git binary"): the exact argument list, the environment built from
 * empty, the deadline with a forced end of the whole process tree, the size watch, and the capped output. The binary is
 * the fake git of {@link GitShim}, whose record is what these tests read.
 */
class GitCommandTest {

    /** The pins of the plan, in the order they are given. */
    private static final List<String> PINS = List.of(
            "protocol.allow=never",
            "protocol.https.allow=always",
            "http.followRedirects=false",
            "submodule.recurse=false",
            "core.hooksPath=/dev/null",
            "core.symlinks=false",
            "core.fsmonitor=false",
            "transfer.fsckObjects=true",
            "core.bigFileThreshold=1m",
            "pack.windowMemory=16m",
            "pack.threads=1",
            "core.packedGitLimit=64m",
            "core.packedGitWindowSize=16m",
            "core.deltaBaseCacheLimit=16m",
            "filter.lfs.clean=",
            "filter.lfs.smudge=",
            "filter.lfs.process=",
            "filter.lfs.required=false");

    @TempDir
    Path temp;

    private GitShim shim;
    private Path home;

    private GitCommand command(Duration timeout, GitTransport transport) throws IOException {
        shim = GitShim.in(temp);
        home = Files.createDirectories(temp.resolve("home"));
        return new GitCommand(shim.executable(), home, timeout, transport);
    }

    private GitCommand command() throws IOException {
        return command(Duration.ofSeconds(30), GitTransport.NONE);
    }

    private static List<String> pinned(String... arguments) {
        List<String> argv = new ArrayList<>();
        for (String pin : PINS) {
            argv.add("-c");
            argv.add(pin);
        }
        argv.addAll(List.of(arguments));
        return argv;
    }

    @Test
    void theArgumentListIsTheFixedPinsThenTheCallersArgumentsAndNothingElse() throws IOException {
        command().run(List.of("init", "--bare", "--template=", "/work/repo.git"), 1024);

        assertThat(shim.calls())
                .singleElement()
                .satisfies(call -> assertThat(call.arguments())
                        .containsExactlyElementsOf(pinned("init", "--bare", "--template=", "/work/repo.git")));
        assertThat(GitCommand.PINS).containsExactlyElementsOf(PINS);
    }

    @Test
    void aTransportAddsItsPinsAfterTheFixedOnesAndItsVariablesOverTheFixedOnes() throws IOException {
        GitTransport transport = new GitTransport(
                List.of("http.curloptResolve=git.example.org:443:93.184.216.34"), Map.of("GIT_ALLOW_PROTOCOL", "file"));

        command(Duration.ofSeconds(30), transport).run(List.of("fetch", "--", "https://git.example.org/a.git"), 1024);

        assertThat(shim.calls()).singleElement().satisfies(call -> {
            List<String> expected = pinned();
            expected.addAll(List.of("-c", "http.curloptResolve=git.example.org:443:93.184.216.34"));
            expected.addAll(List.of("fetch", "--", "https://git.example.org/a.git"));
            assertThat(call.arguments()).containsExactlyElementsOf(expected);
            assertThat(call.environment()).containsEntry("GIT_ALLOW_PROTOCOL", "file");
        });
    }

    @Test
    void theEnvironmentIsBuiltFromEmptyAndNothingOfTheServicesIsInherited() throws IOException {
        // Variables a service really has: none may reach git, whatever they are.
        assertThat(System.getenv()).isNotEmpty();
        command().run(List.of("rev-parse", "--end-of-options", "refs/charter/fetched"), 1024);

        assertThat(shim.calls()).singleElement().satisfies(call -> {
            Map<String, String> environment = new HashMap<>(call.environment());
            environment.keySet().removeAll(Set.of("PWD", "_", "SHLVL", "OLDPWD"));
            assertThat(environment)
                    .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                            Map.entry("PATH", shim.directory().toString()),
                            Map.entry("HOME", home.toString()),
                            Map.entry("XDG_CONFIG_HOME", home.toString()),
                            Map.entry("GIT_CONFIG_NOSYSTEM", "1"),
                            Map.entry("GIT_CONFIG_GLOBAL", "/dev/null"),
                            Map.entry("GIT_TERMINAL_PROMPT", "0"),
                            Map.entry("LC_ALL", "C"),
                            Map.entry("GIT_ALLOW_PROTOCOL", "https"),
                            Map.entry("GIT_LFS_SKIP_SMUDGE", "1")));
            assertThat(environment.keySet())
                    .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("proxy")
                            || name.contains("ASKPASS")
                            || name.equals("SSH_AUTH_SOCK"));
        });
    }

    @Test
    void anArgumentIsNeverGivenToAShell() throws IOException {
        String hostile = "refs/heads/x; touch " + temp.resolve("pwned") + " $(id) `id` 'q\"";

        command().run(List.of("fetch", "--", hostile), 1024);

        assertThat(shim.calls().getFirst().arguments()).endsWith("fetch", "--", hostile);
        assertThat(temp.resolve("pwned")).doesNotExist();
    }

    @Test
    void gitRunsInTheRunsOwnDirectory() throws IOException {
        command().run(List.of("rev-parse"), 1024);

        assertThat(shim.calls())
                .singleElement()
                .satisfies(call -> assertThat(Path.of(call.cwd())).isEqualTo(home));
    }

    @Test
    void whatGitPrintsIsReturned() throws IOException {
        command();
        shim.out("rev-parse", "0123456789abcdef0123456789abcdef01234567\n");

        byte[] output = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE)
                .run(List.of("rev-parse", "x"), 1024);

        assertThat(new String(output, StandardCharsets.UTF_8)).isEqualTo("0123456789abcdef0123456789abcdef01234567\n");
    }

    @Test
    void anExitStatusThatIsNotZeroFailsWithWhatGitSaidOnStderrKeptApart() throws IOException {
        command();
        shim.exit("fetch", 128).err("fetch", "fatal: couldn't find remote ref refs/heads/nope\n");
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);

        assertThatThrownBy(() -> git.run(List.of("fetch"), 1024)).isInstanceOfSatisfying(GitFailure.class, failure -> {
            assertThat(failure.kind()).isEqualTo(Kind.EXIT);
            assertThat(failure.exitStatus()).isEqualTo(128);
            assertThat(failure.stderr()).contains("couldn't find remote ref");
            assertThat(failure.getMessage()).doesNotContain("remote ref").doesNotContain("fatal");
        });
    }

    @Test
    void outputOverTheCallersCapEndsTheRun() throws IOException {
        command();
        shim.out("ls-tree", "x".repeat(5000));
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);

        assertThatThrownBy(() -> git.run(List.of("ls-tree"), 1000))
                .isInstanceOfSatisfying(
                        GitFailure.class, failure -> assertThat(failure.kind()).isEqualTo(Kind.OUTPUT_TOO_LARGE));
    }

    @Test
    void outputAtTheCapIsTaken() throws IOException {
        command();
        shim.out("ls-tree", "x".repeat(1000));
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);

        assertThat(git.run(List.of("ls-tree"), 1000)).hasSize(1000);
    }

    @Test
    void stderrIsDrainedAndCappedSoAChattyGitNeitherBlocksNorGrowsTheHeap() throws IOException {
        command();
        shim.err("fetch", "e".repeat(500_000)).exit("fetch", 1);
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);

        assertThatThrownBy(() -> git.run(List.of("fetch"), 1024)).isInstanceOfSatisfying(GitFailure.class, failure -> {
            assertThat(failure.kind()).isEqualTo(Kind.EXIT);
            assertThat(failure.stderr().length()).isBetween(1, 16 * 1024);
        });
    }

    @Test
    void theDeadlineEndsTheWholeProcessTreeNotOnlyTheProcessThatWasStarted() throws IOException {
        command();
        // The shim sleeps in a child of its own; the run is over only when that child is gone too.
        shim.sleep("fetch", 30);
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofMillis(700), GitTransport.NONE);
        long started = System.nanoTime();

        assertThatThrownBy(() -> git.run(List.of("fetch"), 1024))
                .isInstanceOfSatisfying(
                        GitFailure.class, failure -> assertThat(failure.kind()).isEqualTo(Kind.DEADLINE));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void theDeadlineIsOverTheWholeFetchNotEachRun() throws IOException {
        command();
        shim.sleep("fetch", 1);
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofMillis(1500), GitTransport.NONE);

        git.run(List.of("fetch"), 1024);
        assertThatThrownBy(() -> git.run(List.of("fetch"), 1024))
                .isInstanceOfSatisfying(
                        GitFailure.class, failure -> assertThat(failure.kind()).isEqualTo(Kind.DEADLINE));
    }

    @Test
    void aRunStartedAfterTheDeadlineIsNeverStarted() throws IOException, InterruptedException {
        command();
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofMillis(50), GitTransport.NONE);
        Thread.sleep(200);

        assertThatThrownBy(() -> git.run(List.of("rev-parse"), 1024))
                .isInstanceOfSatisfying(
                        GitFailure.class, failure -> assertThat(failure.kind()).isEqualTo(Kind.DEADLINE));
        assertThat(shim.calls()).isEmpty();
    }

    @Test
    void theSizeWatchEndsAFetchThatFillsTheDisk() throws IOException {
        command();
        Path repository = Files.createDirectories(temp.resolve("repo.git"));
        shim.grow("fetch", 2_000_000);
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(60), GitTransport.NONE)
                .watching(() -> directorySize(repository) > 1_000_000);
        long started = System.nanoTime();

        assertThatThrownBy(() -> git.run(List.of("--git-dir=" + repository, "fetch"), 1024))
                .isInstanceOfSatisfying(
                        GitFailure.class, failure -> assertThat(failure.kind()).isEqualTo(Kind.TOO_LARGE));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        assertThat(directorySize(repository)).isGreaterThan(1_000_000);
    }

    @Test
    void aStreamedRunHandsOverItsOutputAsItIsWrittenAndEndsWithTheStatus() throws IOException {
        command();
        shim.out("cat-file", "blob-bytes");
        Path stdin = Files.writeString(temp.resolve("oids"), "0123\n");
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);

        try (GitCommand.Running running = git.stream(List.of("cat-file", "--batch"), stdin)) {
            InputStream out = running.stdout();
            assertThat(new String(out.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("blob-bytes");
            running.finish();
        }
        assertThat(shim.calls().getFirst().arguments()).endsWith("cat-file", "--batch");
    }

    @Test
    void aStreamedRunThatFailsSaysSoWhenItIsFinished() throws IOException {
        command();
        shim.exit("cat-file", 128).err("cat-file", "fatal: bad object\n");
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(30), GitTransport.NONE);

        try (GitCommand.Running running = git.stream(List.of("cat-file", "--batch"), null)) {
            running.stdout().readAllBytes();
            assertThatThrownBy(running::finish)
                    .isInstanceOfSatisfying(
                            GitFailure.class,
                            failure -> assertThat(failure.kind()).isEqualTo(Kind.EXIT));
        }
    }

    @Test
    void closingAStreamedRunEndsItsProcessTree() throws IOException {
        command();
        shim.sleep("cat-file", 30);
        GitCommand git = new GitCommand(shim.executable(), home, Duration.ofSeconds(60), GitTransport.NONE);
        long started = System.nanoTime();

        try (GitCommand.Running running = git.stream(List.of("cat-file", "--batch"), null)) {
            assertThat(running.stdout()).isNotNull();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void aBinaryThatCannotBeStartedIsAStartFailure() throws IOException {
        GitCommand git = new GitCommand(
                temp.resolve("no-such-git"),
                Files.createDirectories(temp.resolve("h")),
                Duration.ofSeconds(5),
                GitTransport.NONE);

        assertThatThrownBy(() -> git.run(List.of("init"), 1024))
                .isInstanceOfSatisfying(
                        GitFailure.class, failure -> assertThat(failure.kind()).isEqualTo(Kind.START));
    }

    private static long directorySize(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException gone) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException unreadable) {
            return 0;
        }
    }
}
