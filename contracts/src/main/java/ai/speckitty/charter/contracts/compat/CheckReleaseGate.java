package ai.speckitty.charter.contracts.compat;

import ai.speckitty.charter.contracts.compat.ReleaseGate.Decision;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * The command line of {@link ReleaseGate}, run by the Gradle task {@code checkContractCompatibility}:
 * {@code CheckReleaseGate <openapi.yaml> <CHANGELOG.md> (--git <merged-tags> <head-tags> | --no-git)}, each tags
 * file holding one tag per line: the tags merged into HEAD, and those that point at HEAD. On success it prints the decision as its first line ({@code COMPARE <tag>}, {@code NONE} or
 * {@code SKIPPED}) and the message as its second, and exits 0; it exits 1 with the message when the gate refuses,
 * and 2 when an input cannot be read.
 *
 * <p>{@code CheckReleaseGate --baseline <openapi.yaml> <tag>} checks the contract archived at a baseline tag
 * ({@link ReleaseGate#baselineRefusal}): it prints {@code BASELINE <version>} and exits 0, or prints the refusal
 * and exits 1.
 *
 * <p>{@code CheckReleaseGate --release <openapi.yaml> <CHANGELOG.md> <tag> [<changes-dir>]} checks a tag pushed to
 * publish a release ({@link ReleaseGate#releaseRefusal}): it prints {@code RELEASE <version>} and exits 0, or prints
 * the refusal and exits 1. Given the changelog fragments directory, it also refuses while a fragment is left
 * unassembled ({@link ChangelogFragments}, ADR-0047). The Gradle task {@code checkReleaseTag} runs it.
 *
 * <p>{@code CheckReleaseGate --previous-breaking <openapi.yaml> <CHANGELOG.md> <merged-tags>} prints
 * {@code PREVIOUS_BREAKING <tag>} when the version is a breaking bump (a major bump, or a minor bump while the major
 * is 0) over a release whose tag is merged ({@link ReleaseGate#previousBreakingBaseline}), else {@code NONE}; it exits
 * 0, or 2 when an input cannot be read.
 */
public final class CheckReleaseGate {

    private static final String USAGE =
            "usage: CheckReleaseGate <openapi.yaml> <CHANGELOG.md> (--git <merged-tags> <head-tags> | --no-git)"
                    + " | CheckReleaseGate --baseline <openapi.yaml> <tag>"
                    + " | CheckReleaseGate --release <openapi.yaml> <CHANGELOG.md> <tag> [<changes-dir>]"
                    + " | CheckReleaseGate --previous-breaking <openapi.yaml> <CHANGELOG.md> <merged-tags>";

    private CheckReleaseGate() {}

    /**
     * Runs the gate over the files the arguments name and exits with the code the class describes.
     *
     * @param args the arguments the class describes
     */
    public static void main(String[] args) {
        int code = run(List.of(args), System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(List<String> args, PrintStream out) {
        if (args.size() == 4 && "--previous-breaking".equals(args.get(0))) {
            return previousBreaking(args, out);
        }
        if ((args.size() == 4 || args.size() == 5) && "--release".equals(args.get(0))) {
            @Nullable Path changes = args.size() == 5 ? Path.of(args.get(4)) : null;
            return release(Path.of(args.get(1)), Path.of(args.get(2)), args.get(3), changes, out);
        }
        if (args.size() == 3 && "--baseline".equals(args.get(0))) {
            return baseline(Path.of(args.get(1)), args.get(2), out);
        }
        boolean noGit = args.size() == 3 && "--no-git".equals(args.get(2));
        boolean tags = args.size() == 5 && "--git".equals(args.get(2));
        if (!noGit && !tags) {
            out.println(USAGE);
            return 2;
        }
        String infoVersion;
        String changelog;
        Set<String> merged = Set.of();
        Set<String> atHead = Set.of();
        try {
            infoVersion = infoVersion(Path.of(args.get(0)));
            changelog = Files.readString(Path.of(args.get(1)));
            if (tags) {
                merged = tags(Path.of(args.get(3)));
                atHead = tags(Path.of(args.get(4)));
            }
        } catch (IOException | YamlEngineException | IllegalArgumentException e) {
            out.println("Cannot read the release gate's inputs: " + e.getMessage());
            return 2;
        }
        Decision decision = noGit
                ? ReleaseGate.decideWithoutGit(infoVersion, changelog)
                : ReleaseGate.decide(infoVersion, changelog, merged, atHead);
        switch (decision) {
            case Decision.Compare compare -> out.println("COMPARE " + compare.tag());
            case Decision.NoBaseline none -> out.println("NONE");
            case Decision.Skipped skipped -> out.println("SKIPPED");
            case Decision.Refused refused -> {
                out.println(refused.message());
                return 1;
            }
        }
        out.println(decision.message());
        return 0;
    }

    private static int previousBreaking(List<String> args, PrintStream out) {
        try {
            Optional<String> tag = ReleaseGate.previousBreakingBaseline(
                    infoVersion(Path.of(args.get(1))),
                    Files.readString(Path.of(args.get(2))),
                    tags(Path.of(args.get(3))));
            out.println(tag.map(t -> "PREVIOUS_BREAKING " + t).orElse("NONE"));
            return 0;
        } catch (IOException | YamlEngineException | IllegalArgumentException e) {
            out.println("Cannot read the release gate's inputs: " + e.getMessage());
            return 2;
        }
    }

    private static int baseline(Path openapi, String tag, PrintStream out) {
        String version;
        try {
            version = infoVersion(openapi);
        } catch (IOException | YamlEngineException | IllegalArgumentException e) {
            out.println("Cannot read the release gate's inputs: " + e.getMessage());
            return 2;
        }
        Optional<Decision.Refused> refused = ReleaseGate.baselineRefusal(tag, version);
        if (refused.isPresent()) {
            out.println(refused.get().message());
            return 1;
        }
        out.println("BASELINE " + version);
        return 0;
    }

    private static int release(Path openapi, Path changelog, String tag, @Nullable Path changes, PrintStream out) {
        String version;
        String log;
        List<String> pending;
        try {
            version = infoVersion(openapi);
            log = Files.readString(changelog);
            pending = changes == null ? List.of() : ChangelogFragments.pending(changes);
        } catch (IOException | YamlEngineException | IllegalArgumentException e) {
            out.println("Cannot read the release gate's inputs: " + e.getMessage());
            return 2;
        }
        // The release refusal first: an unreleased tag is refused as unreleased, whatever fragments are left.
        Optional<Decision.Refused> refused = ReleaseGate.releaseRefusal(tag, version, log);
        if (refused.isPresent()) {
            out.println(refused.get().message());
            return 1;
        }
        if (!pending.isEmpty()) {
            out.println(pending.size() + (pending.size() == 1 ? " changelog fragment is" : " changelog fragments are")
                    + " not assembled into the CHANGELOG: " + String.join(", ", pending)
                    + ". Run ./gradlew :contracts:assembleContractChangelog in the release pull request.");
            return 1;
        }
        out.println("RELEASE " + version);
        out.println("The tag " + tag + ", info.version and the top CHANGELOG heading name release " + version + ".");
        return 0;
    }

    private static Set<String> tags(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The {@code info.version} of an OpenAPI document, which must be a string. */
    private static String infoVersion(Path openapi) throws IOException {
        try (InputStream in = Files.newInputStream(openapi)) {
            Object document =
                    new Load(LoadSettings.builder().setLabel(openapi.toString()).build()).loadFromInputStream(in);
            Object version = document instanceof Map<?, ?> root && root.get("info") instanceof Map<?, ?> info
                    ? info.get("version")
                    : null;
            return asString(version, openapi);
        }
    }

    private static String asString(@Nullable Object version, Path openapi) {
        if (version instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException(openapi + " has no string info.version"
                + (version == null ? "" : " (found the YAML value " + version + "; quote it)"));
    }
}
