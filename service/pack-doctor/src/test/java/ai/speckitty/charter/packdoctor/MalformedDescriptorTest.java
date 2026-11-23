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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A malformed descriptor field fails its pack (#624): exit 1, one {@code validation_failed} problem for the pack that
 * names the descriptor rule's code at {@code pack.yaml}, never an internal error (exit 2); {@code CLOSURE} is skipped
 * and names the pack. Each case plants one malformed field into the example org pack's {@code pack.yaml}. The three
 * format fields ({@code pack_version}, a {@code depends_on} range, {@code requires_core}) go through the descriptor
 * prechecks and crashed before #624, so they run in every mode: {@code --pack}, {@code --config-root} and
 * {@code --config-root --round-trip} (which carries the "no {@code round_trip_red}" claim). The schema fields were
 * already refused by the pack loader, and the mode only changes how the set is given, so they run once, with
 * {@code --pack}, pinning that every malformed field stays a problem.
 */
class MalformedDescriptorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String PACK_YAML = "pack.yaml";

    @TempDir
    Path temp;

    /** One malformed field: the text replaced in org-base's {@code pack.yaml}, its replacement, and the code. */
    private record Plant(String field, String from, String to, String code) {
        @Override
        public String toString() {
            return field;
        }
    }

    /** The format fields: they crashed before #624, and are checked in every mode. */
    static Stream<Arguments> precheckPlants() {
        return Stream.of(
                        new Plant(
                                "pack_version",
                                "pack_version: 1.0.0",
                                "pack_version: not-a-version",
                                "pack_version_invalid"),
                        new Plant(
                                "depends_on range",
                                "range: \">=1.0,<2\"",
                                "range: \"not-a-range\"",
                                "dependency_range_invalid"),
                        new Plant(
                                "requires_core",
                                "pack_level: org",
                                "pack_level: org\nrequires_core: \"not-a-range\"",
                                "requires_core_invalid"))
                .flatMap(plant -> Stream.of(Mode.values()).map(mode -> Arguments.of(plant, mode)));
    }

    /** The schema fields: the loader already refused them, and the mode does not change that, so one run each. */
    static Stream<Arguments> schemaPlants() {
        return Stream.of(
                        new Plant("pack_level", "pack_level: org", "pack_level: galaxy", "pack_unreadable"),
                        new Plant(
                                "pack_id",
                                "pack_id: 01M6Y9ACNAAD96XPNQS0XWP2FF",
                                "pack_id: not-a-ulid",
                                "pack_unreadable"),
                        new Plant(
                                "depends_on pack_id",
                                "- pack_id: 01ARWG13C000000000000000FG",
                                "- pack_id: nope",
                                "pack_unreadable"),
                        new Plant(
                                "schema_version",
                                "schema_version: \"1.0\"",
                                "schema_version: \"9.9\"",
                                "pack_unreadable"),
                        new Plant("name", "name: org-base", "name: Org Base!", "pack_unreadable"),
                        new Plant(
                                "undeclared key",
                                "pack_level: org",
                                "pack_level: org\nflavour: mint",
                                "pack_unreadable"))
                .map(plant -> Arguments.of(plant, Mode.PACKS));
    }

    /** How the set is given: as {@code --pack} entries, as a config root, or as a config root with the round trip. */
    enum Mode {
        PACKS,
        CONFIG_ROOT,
        CONFIG_ROOT_ROUND_TRIP
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource({"precheckPlants", "schemaPlants"})
    void aMalformedDescriptorFieldFailsThePackWithItsCode(Plant plant, Mode mode) throws IOException {
        boolean configRoot = mode != Mode.PACKS;
        Path packs = temp.resolve("root").resolve(ConfigRoot.PACKS);
        List<String> names = configRoot ? ExamplePacks.names() : List.of(ExamplePacks.BUILT_IN, ExamplePacks.ORG);
        names.forEach(name -> ExamplePacks.copyOf(name, packs));
        Path descriptor = packs.resolve(ExamplePacks.ORG).resolve(PACK_YAML);
        String text = Files.readString(descriptor);
        assertThat(text).as("the plant's anchor is in org-base's pack.yaml").contains(plant.from());
        Files.writeString(descriptor, text.replace(plant.from(), plant.to()));

        List<String> args = new ArrayList<>();
        if (configRoot) {
            args.addAll(List.of("--config-root", temp.resolve("root").toString()));
            if (mode == Mode.CONFIG_ROOT_ROUND_TRIP) {
                args.add("--round-trip");
            }
        } else {
            for (String name : names) {
                args.addAll(List.of("--pack", packs.resolve(name) + ":" + level(name)));
            }
        }
        Run run = run(args.toArray(String[]::new));

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.err()).doesNotContain("internal error");
        JsonNode problems = run.report().get("problems");
        assertThat(problems).as(run.out()).hasSize(1);
        JsonNode problem = problems.get(0);
        assertThat(problem.get("code").asString()).isEqualTo("validation_failed");
        assertThat(problem.get("instance").asString())
                .isEqualTo(packs.resolve(ExamplePacks.ORG).toUri().toString());
        List<String> atDescriptor = new ArrayList<>();
        problem.get("violations").forEach(violation -> {
            if (violation.get("pointer").asString().startsWith(PACK_YAML)) {
                atDescriptor.add(violation.get("code").asString());
            }
        });
        assertThat(atDescriptor).as(run.out()).contains(plant.code());
        List<String> codes = new ArrayList<>();
        problem.get("violations")
                .forEach(violation -> codes.add(violation.get("code").asString()));
        assertThat(codes)
                .as("one cause, no round-trip red beside it")
                .contains(plant.code())
                .doesNotContain("round_trip_red");
        assertThat(run.report().get("checks").toString()).doesNotContain("CLOSURE");
        List<String> closureSkips = new ArrayList<>();
        run.report().get("skipped").forEach(skip -> {
            if (skip.get("check").asString().equals("CLOSURE")) {
                closureSkips.add(skip.get("reason").asString());
            }
        });
        assertThat(closureSkips).as(run.out()).anyMatch(reason -> reason.contains(ExamplePacks.ORG + " ("));
    }

    /**
     * A pack given its id ({@code <dir>=<id>}) whose directory is named otherwise is named by its id everywhere: in the
     * violations and in the reason {@code CLOSURE} is skipped, never by its directory's name (squad B12).
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableDescriptors")
    void aPackGivenItsIdIsNamedByItsIdInTheSkipReasonToo(String text) throws IOException {
        Path packs = temp.resolve("packs");
        ExamplePacks.copyOf(ExamplePacks.BUILT_IN, packs);
        Path renamed = Files.move(ExamplePacks.copyOf(ExamplePacks.ORG, temp.resolve("copied")), packs.resolve("x"));
        if (text.isEmpty()) {
            Files.delete(renamed.resolve(PACK_YAML));
        } else {
            Files.writeString(renamed.resolve(PACK_YAML), text);
        }

        Run run = run(
                "--pack", packs.resolve(ExamplePacks.BUILT_IN) + ":" + level(ExamplePacks.BUILT_IN),
                "--pack", renamed + ":" + level(ExamplePacks.ORG) + "=" + ExamplePacks.ORG);

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.out())
                .as("the directory's name is never the pack's")
                .doesNotContain(" of x ", " of x:", "pack x ");
        List<String> reasons = new ArrayList<>();
        run.report()
                .get("skipped")
                .forEach(skip -> reasons.add(skip.get("reason").asString()));
        assertThat(reasons).isNotEmpty().allMatch(reason -> reason.contains(ExamplePacks.ORG));
    }

    static Stream<String> unreadableDescriptors() {
        // Unparsable, a key given twice, and no pack.yaml at all (the empty text).
        return Stream.of("pack_level: [unclosed\n", "pack_id: Other\npack_id: Again\n", "");
    }

    private static String level(String name) {
        return ExamplePacks.level(name).token();
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
