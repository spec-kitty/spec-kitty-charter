package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stale manifest hash is one problem for one cause (#630, operator ruling R-b, plan D4): an edited constituent of the
 * example org pack leaves its {@code content_hash} stale, and {@code pack-doctor validate} exits 1 with exactly one
 * violation, {@code manifest_stale} at {@code pack-manifest.yaml}, naming the field. With {@code --round-trip} the answer
 * is the same: the round trip no longer reports the source-stale manifest as {@code round_trip_red}. The set is given as
 * {@code --pack} entries and as a config root, each with and without the round trip.
 */
class StaleManifestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String EDITED = "agent_profiles/human-operator.agent.yaml";

    @TempDir
    Path temp;

    /** How the set is given, and whether the round trip runs. */
    enum Mode {
        PACKS(false, false),
        PACKS_ROUND_TRIP(false, true),
        CONFIG_ROOT(true, false),
        CONFIG_ROOT_ROUND_TRIP(true, true);

        private final boolean configRoot;
        private final boolean roundTrip;

        Mode(boolean configRoot, boolean roundTrip) {
            this.configRoot = configRoot;
            this.roundTrip = roundTrip;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mode.class)
    void aStaleContentHashIsExactlyOneManifestStale(Mode mode) throws IOException {
        Path packs = temp.resolve("root").resolve(ConfigRoot.PACKS);
        List<String> names = mode.configRoot ? ExamplePacks.names() : List.of(ExamplePacks.BUILT_IN, ExamplePacks.ORG);
        names.forEach(name -> ExamplePacks.copyOf(name, packs));
        Path edited = packs.resolve(ExamplePacks.ORG).resolve(EDITED);
        Files.writeString(
                edited, Files.readString(edited, StandardCharsets.UTF_8) + "# edited\n", StandardCharsets.UTF_8);

        List<String> args = new ArrayList<>();
        if (mode.configRoot) {
            args.addAll(List.of("--config-root", temp.resolve("root").toString()));
        } else {
            for (String name : names) {
                args.addAll(List.of(
                        "--pack",
                        packs.resolve(name) + ":" + ExamplePacks.level(name).token()));
            }
        }
        if (mode.roundTrip) {
            args.add("--round-trip");
        }
        Run run = run(args.toArray(String[]::new));

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        List<String> codes = new ArrayList<>();
        List<String> pointers = new ArrayList<>();
        List<String> details = new ArrayList<>();
        run.report()
                .get("problems")
                .forEach(problem -> problem.get("violations").forEach(violation -> {
                    codes.add(violation.get("code").asString());
                    pointers.add(violation.get("pointer").asString());
                    details.add(violation.get("detail").asString());
                }));
        assertThat(codes).as(run.out()).isEqualTo(List.of("manifest_stale"));
        assertThat(pointers).isEqualTo(List.of("pack-manifest.yaml#/constituents/0/content_hash"));
        assertThat(details)
                .isEqualTo(List.of("pack-manifest.yaml: constituents{" + EDITED + "}.content_hash does not match the"
                        + " bytes of " + EDITED));
    }

    /** What one run answered. */
    private record Run(int exit, String out, String err) {
        JsonNode report() {
            return JSON.readTree(out);
        }
    }

    private Run run(String... args) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path temporary = Files.createDirectories(temp.resolve("tmp"));
        int exit = PackDoctorMain.run(
                Stream.concat(Stream.of("validate"), Stream.of(args)).toArray(String[]::new),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temporary);
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
}
