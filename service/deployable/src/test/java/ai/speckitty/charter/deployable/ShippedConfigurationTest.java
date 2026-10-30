package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The configuration files the repository ships (#229, ADR-0041 decisions 1 and 3): the local {@code application.yaml}
 * is unchanged, the container profile's file holds only its defaults, the compose example and its env template show the
 * setup without a secret, and git ignores the files a secret goes in. A scan of the shipped configuration and of the
 * tracked files fails on a committed secret; its positive controls show it would.
 */
class ShippedConfigurationTest {

    private static final Path ROOT = Path.of(System.getProperty("charter.test.repo-root", ""));
    private static final Path RESOURCES = ROOT.resolve("service/deployable/src/main/resources");
    private static final Path COMPOSE = ROOT.resolve("deploy/compose/compose.yaml");
    private static final Path ENV_TEMPLATE = ROOT.resolve("deploy/compose/.env.example");

    /** A value that looks like a credential: 32 or more characters of a token alphabet. */
    private static final Pattern SECRET_SHAPED = Pattern.compile("[A-Za-z0-9+/=_-]{32,}");

    private static final Pattern ENV_ENTRY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*");

    // --- reading ---------------------------------------------------------------------------------------------------

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every scalar of a YAML document under its dotted key; a list of {@code KEY=value} entries reads as a map. */
    private static Map<String, String> flattenedYaml(String text) {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", new Load(LoadSettings.builder().build()).loadFromString(text), flat);
        return flat;
    }

    private static void flatten(String key, Object node, Map<String, String> flat) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((name, value) -> flatten(child(key, String.valueOf(name)), value, flat));
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof String entry && ENV_ENTRY.matcher(entry).matches()) {
                    int at = entry.indexOf('=');
                    flat.put(child(key, entry.substring(0, at)), entry.substring(at + 1));
                } else {
                    flatten(key + "[" + i + "]", item, flat);
                }
            }
        } else {
            flat.put(key, node == null ? "" : String.valueOf(node));
        }
    }

    private static String child(String key, String name) {
        return key.isEmpty() ? name : key + "." + name;
    }

    /** The live entries of an env file: no comments, an {@code export} prefix dropped, split on the first {@code =}. */
    private static Map<String, String> envEntries(String text) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String raw : text.split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int at = line.indexOf('=');
            entries.put(at < 0 ? line : line.substring(0, at), at < 0 ? "" : line.substring(at + 1));
        }
        return entries;
    }

    private static List<Path> applicationFiles() {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.filter(file -> file.getFileName().toString().matches("application.*\\.ya?ml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** What a git command answered: its exit code and its standard output, one entry per line. */
    private record GitResult(int exit, List<String> lines) {}

    private static GitResult git(String... arguments) {
        if (!Files.exists(ROOT.resolve(".git"))) {
            throw new IllegalStateException("no git checkout at " + ROOT + ": this test needs git to read the rules");
        }
        List<String> command = new ArrayList<>(List.of("git", "-C", ROOT.toString()));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new GitResult(process.waitFor(), output.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static boolean ignored(String path) {
        // 0: ignored, 1: not ignored; anything else is git failing, which must not pass for either.
        int exit = git("check-ignore", "-q", "--no-index", path).exit();
        if (exit != 0 && exit != 1) {
            throw new IllegalStateException("git check-ignore failed for " + path + " with exit " + exit);
        }
        return exit == 0;
    }

    private static List<String> trackedFiles() {
        GitResult result = git("ls-files");
        if (result.exit() != 0) {
            throw new IllegalStateException("git ls-files failed with exit " + result.exit());
        }
        return result.lines();
    }

    // --- the secret scan -------------------------------------------------------------------------------------------

    private static String normalized(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    /** A bare reference, {@code ${NAME}}, carries no value of its own; one with a default ({@code ${NAME:x}}) does. */
    private static final Pattern BARE_REFERENCE = Pattern.compile("\\$\\{[A-Za-z0-9_.-]+}");

    /** The keys whose value is a committed secret; never the values, so a finding does not leak one. */
    private static List<String> secretFindings(Map<String, String> entries) {
        List<String> findings = new ArrayList<>();
        entries.forEach((key, value) -> {
            String own = BARE_REFERENCE.matcher(value).replaceAll("").strip();
            boolean token = normalized(key).endsWith("charterworktreetoken");
            if (!own.isEmpty() && (token || SECRET_SHAPED.matcher(own).find())) {
                findings.add(key);
            }
        });
        return findings;
    }

    /** Tracked paths a secret goes in: an env file other than the template, or anything in a secrets directory. */
    private static List<String> secretPaths(List<String> tracked) {
        return tracked.stream()
                .filter(path -> {
                    String name = path.substring(path.lastIndexOf('/') + 1);
                    boolean envFile = name.startsWith(".env") && !".env.example".equals(name);
                    return envFile || ("/" + path).contains("/secrets/");
                })
                .toList();
    }

    // --- the local daemon ------------------------------------------------------------------------------------------

    @Test
    void theLocalApplicationYamlStillBindsLoopbackOnPortZeroAndImportsNothing() {
        Map<String, String> local = flattenedYaml(read(RESOURCES.resolve("application.yaml")));
        assertThat(local).containsEntry("server.address", "127.0.0.1").containsEntry("server.port", "0");
        assertThat(local.keySet()).noneMatch(key -> key.startsWith("spring.config.import"));
        assertThat(local.keySet()).noneMatch(key -> key.startsWith("spring.profiles"));
    }

    // --- the container profile's file ------------------------------------------------------------------------------

    @Test
    void theContainerProfileFileHoldsOnlyItsDefaultsAndTheRequiredImport() {
        Map<String, String> container = flattenedYaml(read(RESOURCES.resolve("application-container.yaml")));
        assertThat(container.keySet())
                .containsExactlyInAnyOrder("spring.config.import", "server.address", "server.port");
        assertThat(container)
                .containsEntry("spring.config.import", "configtree:${charter.container.secrets-dir:/run/secrets}/")
                .containsEntry("server.address", "0.0.0.0")
                .containsEntry("server.port", "8080");
        assertThat(container.get("spring.config.import")).doesNotStartWith("optional:");
    }

    // --- the compose example and its env template ------------------------------------------------------------------

    @Test
    void theComposeExampleRunsTheContainerProfileWithARequiredReadOnlySecretsMount() {
        Map<String, String> compose = flattenedYaml(read(COMPOSE));
        assertThat(compose.get("services.charter.image")).startsWith("${CHARTER_IMAGE:?");
        assertThat(compose).containsEntry("services.charter.env_file", ".env");
        assertThat(compose).containsEntry("services.charter.environment.SPRING_PROFILES_ACTIVE", "container");
        String mount = compose.entrySet().stream()
                .filter(entry -> entry.getKey().matches("services\\.charter\\.volumes\\[\\d+]\\.target"))
                .filter(entry -> "/run/secrets".equals(entry.getValue()))
                .map(entry -> entry.getKey().substring(0, entry.getKey().length() - ".target".length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no volume is mounted at /run/secrets"));
        assertThat(compose)
                .containsEntry(mount + ".type", "bind")
                .containsEntry(mount + ".read_only", "true")
                .containsEntry(mount + ".bind.create_host_path", "false");
    }

    @Test
    void theEnvTemplateSetsNoValueAndLeavesTheSecretsAsComments() {
        Map<String, String> entries = envEntries(read(ENV_TEMPLATE));
        assertThat(entries).containsOnlyKeys("CHARTER_IMAGE");
        assertThat(entries.values()).allMatch(String::isEmpty);
        // An empty live CHARTER_WORKTREE_TOKEN would hide a mounted token file, and an empty secrets directory would
        // import the whole filesystem: both stay out of the live keys.
        assertThat(read(ENV_TEMPLATE)).contains("# CHARTER_WORKTREE_TOKEN=").doesNotContain("SECRETS_DIR");
    }

    // --- what git keeps out ----------------------------------------------------------------------------------------

    @Test
    void gitIgnoresEnvFilesAndTheSecretsDirectoryButNotTheTemplate() {
        assertThat(ignored("deploy/compose/.env")).isTrue();
        assertThat(ignored("deploy/compose/.env.local")).isTrue();
        assertThat(ignored(".env")).isTrue();
        assertThat(ignored("deploy/compose/secrets/charter.worktree-token")).isTrue();
        assertThat(ignored("deploy/compose/.env.example")).isFalse();
    }

    // --- no secret is committed ------------------------------------------------------------------------------------

    @Test
    void noShippedConfigurationFileHoldsASecret() {
        Map<String, List<String>> findings = new LinkedHashMap<>();
        for (Path file : applicationFiles()) {
            findings.put(file.getFileName().toString(), secretFindings(flattenedYaml(read(file))));
        }
        findings.put("compose.yaml", secretFindings(flattenedYaml(read(COMPOSE))));
        findings.put(".env.example", secretFindings(envEntries(read(ENV_TEMPLATE))));
        assertThat(findings).containsKeys("application.yaml", "application-container.yaml");
        assertThat(findings.values()).allMatch(List::isEmpty);
    }

    @Test
    void noTrackedFileIsAnEnvFileOrInASecretsDirectory() {
        List<String> tracked = trackedFiles();
        assertThat(tracked).contains("deploy/compose/.env.example");
        assertThat(secretPaths(tracked)).isEmpty();
    }

    @Test
    void theSecretScanCatchesEachFormOfACommittedToken() {
        // Positive controls: each planted form is found, and by its key alone.
        String hex = "0123456789abcdef0123456789abcdef01234567";
        assertThat(secretFindings(flattenedYaml("charter:\n  worktree-token: " + hex + "\n")))
                .containsExactly("charter.worktree-token");
        assertThat(secretFindings(
                        flattenedYaml("services:\n  s:\n    environment:\n      - CHARTER_WORKTREE_TOKEN=abc\n")))
                .containsExactly("services.s.environment.CHARTER_WORKTREE_TOKEN");
        assertThat(secretFindings(envEntries("export CHARTER_WORKTREE_TOKEN=abc\n")))
                .containsExactly("CHARTER_WORKTREE_TOKEN");
        assertThat(secretFindings(envEntries("CHARTER_WORKTREETOKEN=abc\n"))).containsExactly("CHARTER_WORKTREETOKEN");
        assertThat(secretFindings(flattenedYaml("innocent: " + hex + "\n"))).containsExactly("innocent");
        assertThat(secretFindings(flattenedYaml("url: https://user:" + hex + "@example.org\n")))
                .containsExactly("url");
        // A reference with a default, or text around a reference, carries a value of its own.
        assertThat(secretFindings(envEntries("CHARTER_WORKTREE_TOKEN=${CHARTER_WORKTREE_TOKEN:abc}\n")))
                .containsExactly("CHARTER_WORKTREE_TOKEN");
        assertThat(secretFindings(envEntries("X=${A}" + hex + "${B}\n"))).containsExactly("X");
        // What is not a secret passes: an empty value, a reference, a short ordinary value.
        assertThat(secretFindings(envEntries("CHARTER_WORKTREE_TOKEN=\n"))).isEmpty();
        assertThat(secretFindings(flattenedYaml("token:\n  charter-worktree-token: ${CHARTER_WORKTREE_TOKEN}\n")))
                .isEmpty();
        assertThat(secretFindings(flattenedYaml("server:\n  port: 8080\n"))).isEmpty();
    }

    @Test
    void theTrackedPathCheckCatchesEnvFilesAndSecretsDirectories() {
        assertThat(secretPaths(
                        List.of("deploy/compose/.env", "x/secrets/y", ".env.prod", "deploy/compose/.env.example")))
                .containsExactly("deploy/compose/.env", "x/secrets/y", ".env.prod");
    }
}
