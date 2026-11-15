package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The names slice F retires are gone (FR-006, FR-007, C-005): the mission step contract kind, its link relation and the
 * relations {@code impacts}, {@code replaces} and {@code delegates_to}. Each scan lists every line that still names one,
 * so a reintroduced name fails with its file and line.
 *
 * <ul>
 *   <li>the main sources of every service module ({@code service/*}{@code /src/main}): none of the tokens;
 *   <li>the contracts ({@code contracts/}): none of the tokens, but in the change fragments ({@code changes/}) and the
 *       changelogs, which record the retirement, in the {@code retired:} block of {@code packs/file-kinds.yaml}, which
 *       refuses the retired files by name, in {@code removed-routes.feature}, which pins the retired routes, in
 *       {@code registration/retired-files.feature}, which pins the refusal of the retired files at registration, and in
 *       {@code activation-retired-keys.feature}, which pins the refusal of the retired charter keys at the activation
 *       seam, and in {@code packs/conformance/classifier-cases.yaml}, whose cases pin each retired rule of the
 *       classifier (#559).
 * </ul>
 *
 * <p>A positive control plants a token in a scratch tree and checks the same scan finds it, so a scan that reads
 * nothing cannot pass.
 */
class RetiredNamesScanTest {

    /** The repository root, read from the module directory the tests run in. */
    private static final Path ROOT = Path.of("../..");

    /** The tokens of the retired kind, in its wire, type and constant spellings, and the retired relations' constants. */
    private static final Pattern RETIRED = Pattern.compile(
            "mission_step_contract|MissionStepContract|MISSION_STEP_CONTRACT|\\b(?:IMPACTS|REPLACES|DELEGATES_TO)\\b");

    /** The retired link relation as Java names it: a string literal. */
    private static final Pattern MAIN_RELATION = Pattern.compile("\"step-contract\"");

    /** The retired link relation as the contract names it: a link key, a list entry or a literal. */
    private static final Pattern CONTRACT_RELATION = Pattern.compile("step-contract");

    @Test
    void noMainSourceOfAServiceModuleNamesARetiredName() throws IOException {
        List<Path> mains = new ArrayList<>();
        try (Stream<Path> modules = Files.list(ROOT.resolve("service"))) {
            modules.map(module -> module.resolve("src/main"))
                    .filter(Files::isDirectory)
                    .forEach(mains::add);
        }
        assertThat(mains).as("the main source trees of the service modules").hasSizeGreaterThan(5);

        List<String> hits = new ArrayList<>();
        for (Path main : mains) {
            hits.addAll(scan(main, RETIRED, MAIN_RELATION));
        }

        assertThat(hits)
                .as("lines of service/*/src/main that name a retired name")
                .isEmpty();
    }

    @Test
    void noContractFileNamesARetiredNameButTheRecordsOfItsRetirement() {
        Path contracts = ROOT.resolve("contracts");
        assertThat(contracts.resolve("charter/openapi.yaml"))
                .as("the contracts are read")
                .isRegularFile();

        List<String> hits = scan(contracts, RETIRED, CONTRACT_RELATION);

        assertThat(hits).as("lines of contracts/ that name a retired name").isEmpty();
    }

    @Test
    void theScanFindsAPlantedNameAndSkipsTheRecordsOfTheRetirement(@TempDir Path scratch) throws IOException {
        Files.createDirectories(scratch.resolve("changes"));
        Files.createDirectories(scratch.resolve("packs"));
        Files.createDirectories(scratch.resolve("charter/scenarios"));
        Files.writeString(scratch.resolve("a.yaml"), "kind: mission_step_contract\n");
        Files.writeString(scratch.resolve("b.java"), "MissionRelation.of(\"step-contract\");\n");
        Files.writeString(scratch.resolve("c.java"), "RelationType.REPLACES\n");
        Files.writeString(scratch.resolve("d.yaml"), "replaces: lower case is no constant\n");
        Files.writeString(scratch.resolve("changes/x.md"), "mission_step_contract is retired\n");
        Files.writeString(scratch.resolve("CHANGELOG.md"), "MissionStepContract is retired\n");
        Files.writeString(scratch.resolve("charter/scenarios/removed-routes.feature"), "mission_step_contract\n");
        Files.writeString(scratch.resolve("packs/file-kinds.yaml"), """
                files:
                  - match: "*/mission_step_contract.yaml"

                retired:
                  # The step contracts.
                  - match: "**/*.step-contract.yaml"

                  - match: "**/mission_step_contract.graph.yaml"
                payloads: []
                """);

        List<String> hits = scan(scratch, RETIRED, MAIN_RELATION);

        assertThat(hits)
                .as("the planted names, and outside the retired block the one the files rule names")
                .containsExactlyInAnyOrder(
                        "a.yaml:1: kind: mission_step_contract",
                        "b.java:1: MissionRelation.of(\"step-contract\");",
                        "c.java:1: RelationType.REPLACES",
                        "packs/file-kinds.yaml:2:   - match: \"*/mission_step_contract.yaml\"");
    }

    /** Every line under {@code root} that matches either pattern, as {@code <path>:<line>: <text>}, records aside. */
    private static List<String> scan(Path root, Pattern names, Pattern relation) {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (isRecordOfTheRetirement(relative) || isBuildOutput(relative)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                boolean retiredBlock = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (relative.endsWith("packs/file-kinds.yaml")
                            && !line.isEmpty()
                            && !line.startsWith(" ")
                            && !line.startsWith("#")) {
                        retiredBlock = line.startsWith("retired:");
                    }
                    if (retiredBlock) {
                        continue;
                    }
                    if (names.matcher(line).find() || relation.matcher(line).find()) {
                        hits.add(relative + ":" + (i + 1) + ": " + line);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    /**
     * The change fragments, the changelogs and the pins of the retired routes and files, the classifier cases among
     * them, record the retirement by name.
     */
    private static boolean isRecordOfTheRetirement(String relative) {
        return relative.startsWith("changes/")
                || relative.contains("/changes/")
                || relative.endsWith("CHANGELOG.md")
                || relative.endsWith("scenarios/removed-routes.feature")
                || relative.endsWith("scenarios/registration/retired-files.feature")
                || relative.endsWith("scenarios/activation-retired-keys.feature")
                || relative.endsWith("packs/conformance/classifier-cases.yaml");
    }

    /** What a build writes beside the sources is no source: it is generated, possibly from an older checkout. */
    private static boolean isBuildOutput(String relative) {
        return relative.startsWith("build/") || relative.contains("/build/") || relative.contains("/.gradle/");
    }
}
