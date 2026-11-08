package ai.speckitty.charter.packs.infra.fetch.git;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fake git of {@code src/test/resources/git-shim/git} (T152), copied into a directory of the test's own with the
 * answers it replays and the record it keeps of every call: the arguments and the whole environment git was started
 * with. It stands for the host binary in every test that must run in {@code check} on a host with any git, or none.
 */
final class GitShim {

    /** What one call of the shim was given. */
    record Call(List<String> arguments, Map<String, String> environment, String cwd) {}

    private final Path directory;

    private GitShim(Path directory) {
        this.directory = directory;
    }

    /** The shim in a fresh subdirectory of {@code parent}, answering {@code git version 2.51.0}. */
    static GitShim in(Path parent) {
        try {
            Path directory = Files.createDirectories(parent.resolve("shim-bin"));
            try (InputStream script = GitShim.class.getResourceAsStream("/git-shim/git")) {
                Files.copy(script, directory.resolve("git"));
            }
            Files.setPosixFilePermissions(directory.resolve("git"), PosixFilePermissions.fromString("rwxr-xr-x"));
            return new GitShim(directory);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    Path executable() {
        return directory.resolve("git");
    }

    Path directory() {
        return directory;
    }

    GitShim version(String text) {
        return file("version", text.getBytes(StandardCharsets.UTF_8));
    }

    GitShim out(String subcommand, String text) {
        return out(subcommand, text.getBytes(StandardCharsets.UTF_8));
    }

    GitShim out(String subcommand, byte[] bytes) {
        return file(subcommand + ".out", bytes);
    }

    GitShim err(String subcommand, String text) {
        return file(subcommand + ".err", text.getBytes(StandardCharsets.UTF_8));
    }

    GitShim exit(String subcommand, int status) {
        return file(subcommand + ".exit", Integer.toString(status).getBytes(StandardCharsets.UTF_8));
    }

    GitShim sleep(String subcommand, int seconds) {
        return file(subcommand + ".sleep", Integer.toString(seconds).getBytes(StandardCharsets.UTF_8));
    }

    GitShim grow(String subcommand, long bytes) {
        return file(subcommand + ".grow", Long.toString(bytes).getBytes(StandardCharsets.UTF_8));
    }

    private GitShim file(String name, byte[] content) {
        try {
            Files.write(directory.resolve(name), content);
            return this;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Every call the shim has answered, oldest first. */
    List<Call> calls() {
        Path record = directory.resolve("record");
        if (!Files.exists(record)) {
            return List.of();
        }
        List<Call> calls = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        Map<String, String> environment = new LinkedHashMap<>();
        String cwd = "";
        try {
            for (String line : Files.readAllLines(record, StandardCharsets.UTF_8)) {
                if (line.equals("begin")) {
                    arguments = new ArrayList<>();
                    environment = new LinkedHashMap<>();
                } else if (line.equals("end")) {
                    calls.add(new Call(arguments, environment, cwd));
                } else if (line.startsWith("cwd:")) {
                    cwd = line.substring("cwd:".length());
                } else if (line.startsWith("argv:")) {
                    arguments.add(line.substring("argv:".length()));
                } else if (line.startsWith("env:")) {
                    int equals = line.indexOf('=');
                    environment.put(line.substring("env:".length(), equals), line.substring(equals + 1));
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return calls;
    }
}
