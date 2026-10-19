package ai.speckitty.charter.contracts.compat;

import ai.speckitty.charter.contracts.compat.ReleaseGate.Decision;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * The command line of {@link ReleaseGate}, run by the Gradle task {@code checkContractCompatibility}:
 * {@code CheckReleaseGate <openapi.yaml> <CHANGELOG.md> (--merged-tags <file> | --no-git)}, the tags file holding
 * one tag per line. On success it prints the decision as its first line ({@code COMPARE <tag>}, {@code NONE} or
 * {@code SKIPPED}) and the message as its second, and exits 0; it exits 1 with the message when the gate refuses,
 * and 2 when an input cannot be read.
 */
public final class CheckReleaseGate {

    private static final String USAGE =
            "usage: CheckReleaseGate <openapi.yaml> <CHANGELOG.md> (--merged-tags <file> | --no-git)";

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
        boolean noGit = args.size() == 3 && "--no-git".equals(args.get(2));
        boolean tags = args.size() == 4 && "--merged-tags".equals(args.get(2));
        if (!noGit && !tags) {
            out.println(USAGE);
            return 2;
        }
        String infoVersion;
        String changelog;
        Set<String> merged = Set.of();
        try {
            infoVersion = infoVersion(Path.of(args.get(0)));
            changelog = Files.readString(Path.of(args.get(1)));
            if (tags) {
                merged = Files.readAllLines(Path.of(args.get(3))).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException | YamlEngineException | IllegalArgumentException e) {
            out.println("Cannot read the release gate's inputs: " + e.getMessage());
            return 2;
        }
        Decision decision = noGit
                ? ReleaseGate.decideWithoutGit(infoVersion, changelog)
                : ReleaseGate.decide(infoVersion, changelog, merged);
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
