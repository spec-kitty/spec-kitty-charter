package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The pinned spec-kitty-open-packs checkout the round-trip and the acceptance test run against. The pin
 * ({@code openPacksRef} in gradle.properties) is the single source of truth; Gradle passes it as the
 * {@code charter.openPacksRef} system property. Nothing here skips: a missing, moved or dirty checkout fails.
 */
public final class OpenPacksCorpus {

    /** Every {@code *.yaml} file under {@code packs/} at the pinned head (re-counted at 5df92a5). */
    static final int YAML_FILE_COUNT = 516;

    /**
     * The load diagnostics of every pack of the pinned corpus, under the ratified strict rule: a pack without
     * pack.yaml is diagnosed at every level. A pack added to or fixed in the corpus changes this table on purpose.
     */
    static final Map<String, List<Diagnostic>> DIAGNOSTICS = Map.of(
            "built-in", List.of(),
            "cave-kitty", List.of(noPackYaml("cave-kitty")),
            "event-driven", List.of(noPackYaml("event-driven")),
            "internal", List.of(),
            "qa", List.of(noPackYaml("qa")));

    private static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final long GIT_TIMEOUT_SECONDS = 30;

    private OpenPacksCorpus() {}

    private static Diagnostic noPackYaml(String orgPack) {
        return new Diagnostic(
                Diagnostic.Code.PACK_LEVEL_MISSING,
                "org pack " + orgPack + " has no pack.yaml; the configured level org is used");
    }

    /** The checkout root; requires the current layout, {@code <root>/packs/built-in}. */
    public static Path root() {
        String dir = System.getProperty("charter.openPacksDir");
        if (dir == null || !Files.isDirectory(Path.of(dir, "packs", "built-in"))) {
            throw new IllegalStateException(
                    "No spec-kitty-open-packs checkout at '" + dir
                            + "' (expected <checkout>/packs/built-in); pass -PopenPacksDir=<checkout>. The round-trip never skips.");
        }
        return Path.of(dir);
    }

    /** Fails unless the checkout is at the pinned commit with a clean tree. */
    public static void assertPinned() {
        Path root = root();
        String pin = System.getProperty("charter.openPacksRef");
        if (pin == null || !SHA.matcher(pin).matches()) {
            throw new IllegalStateException("charter.openPacksRef is '" + pin
                    + "', not 40 lowercase hex characters; set openPacksRef in gradle.properties");
        }
        String fix = "git -C " + root + " fetch origin " + pin + " && git -C " + root + " checkout " + pin;
        String head = git(root, "rev-parse", "HEAD").strip();
        if (!head.equals(pin)) {
            throw new IllegalStateException(
                    "The open-packs checkout is at " + head + " but the pin is " + pin + ". Fix: " + fix);
        }
        String dirty = git(root, "status", "--porcelain", "--untracked-files=all");
        if (!dirty.isBlank()) {
            throw new IllegalStateException("The open-packs checkout has uncommitted changes:\n" + dirty
                    + "Fix: git -C " + root + " stash --include-untracked, or clean it, then " + fix);
        }
    }

    /** The directory that holds the pack directories: {@code <checkout>/packs}. */
    static Path packsDirectory() {
        return root().resolve("packs");
    }

    /** Every {@code *.yaml} file under {@code packs/}, sorted. */
    static List<Path> yamlFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(root().resolve("packs"))) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
        }
    }

    /** The built-in pack, then every other directory under {@code packs/} as an org pack, sorted by name. */
    static List<PackLocation> packs() throws IOException {
        Path packs = root().resolve("packs");
        List<PackLocation> result = new ArrayList<>();
        result.add(new PackLocation("built-in", PackLevel.BUILT_IN, packs.resolve("built-in")));
        try (Stream<Path> dirs = Files.list(packs)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().equals("built-in"))
                    .sorted()
                    .forEach(
                            dir -> result.add(new PackLocation(dir.getFileName().toString(), PackLevel.ORG, dir)));
        }
        return result;
    }

    private static String git(Path root, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Timed out after " + GIT_TIMEOUT_SECONDS + "s: " + command);
            }
            String text = new String(out, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Failed (exit " + process.exitValue() + "): " + command + "\n" + text);
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot run " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: " + command, e);
        }
    }
}
