package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code pack-doctor remediate} (FR-009, ruling R-j, plan D7, T030/T031): over a set red for its manifest and its form,
 * it rewrites and restamps the packs, prints each pack's outcome and then the set's {@code validate --round-trip}
 * report, and exits 0; the meaning of every modelled file and the bytes of a stray file are kept. A pack with a fault it
 * does not repair is refused, named and left byte for byte as it was, and the run exits 1. A second run is unchanged.
 * A command line it cannot run exits 2.
 */
class RemediateTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String EDITED = "agent_profiles/human-operator.agent.yaml";

    private static final String STRAY = "notes.txt";

    @TempDir
    Path temp;

    /** What one run answered. */
    private record Run(int exit, String out, String err) {

        /** The report, which follows the outcome lines on standard output. */
        JsonNode report() {
            return JSON.readTree(out.substring(out.indexOf("{\n")));
        }

        /** Every violation code of the report, in order. */
        List<String> codes() {
            List<String> codes = new ArrayList<>();
            report().get("problems")
                    .forEach(problem -> problem.get("violations")
                            .forEach(
                                    violation -> codes.add(violation.get("code").asString())));
            return codes;
        }
    }

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path temporary = temp.resolve("tmp");
        try {
            Files.createDirectories(temporary);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        int exit = PackDoctorMain.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temporary);
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    // --- the fixtures ----------------------------------------------------------------------------------------------

    /** The built-in and org example packs under {@code temp/packs/}. */
    private Path packs() {
        Path packs = temp.resolve("packs");
        ExamplePacks.copyOf(ExamplePacks.BUILT_IN, packs);
        ExamplePacks.copyOf(ExamplePacks.ORG, packs);
        return packs;
    }

    /** The two packs as {@code <dir>:<level>} values, the built-in pack first. */
    private static String[] values(Path packs) {
        return new String[] {
            packs.resolve(ExamplePacks.BUILT_IN) + ":"
                    + ExamplePacks.level(ExamplePacks.BUILT_IN).token(),
            packs.resolve(ExamplePacks.ORG) + ":"
                    + ExamplePacks.level(ExamplePacks.ORG).token()
        };
    }

    private Run validate(Path packs) {
        String[] given = values(packs);
        return run("validate", "--pack", given[0], "--pack", given[1], "--round-trip");
    }

    private Run remediate(Path packs) {
        String[] given = values(packs);
        return run("remediate", given[0], given[1]);
    }

    /**
     * The org pack red for its manifest and its form: one constituent's keys out of canonical order under an author's
     * comment (its content hash goes stale), one constituent left out of the manifest, and a stray file.
     */
    private static void redForManifestAndForm(Path org) throws IOException {
        Path edited = org.resolve(EDITED);
        List<String> lines = Files.readAllLines(edited, StandardCharsets.UTF_8);
        StringBuilder reordered = new StringBuilder("# An author's note the canonical form does not keep.\n");
        reordered.append(lines.get(1)).append('\n').append(lines.get(0)).append('\n');
        lines.subList(2, lines.size()).forEach(line -> reordered.append(line).append('\n'));
        Files.writeString(edited, reordered.toString(), StandardCharsets.UTF_8);
        Path manifest = org.resolve("pack-manifest.yaml");
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        int entry = text.indexOf("- kind: agent_profile\n  id: reviewer-rita\n");
        int next = text.indexOf("- kind:", entry + 1);
        Files.writeString(manifest, text.substring(0, entry) + text.substring(next), StandardCharsets.UTF_8);
        Files.writeString(org.resolve(STRAY), "an operator's notes, kept byte for byte\r\n", StandardCharsets.UTF_8);
    }

    // --- the repair ------------------------------------------------------------------------------------------------

    @Test
    void aSetRedForItsManifestAndFormValidatesAfterRemediate() throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        redForManifestAndForm(org);
        Path before = temp.resolve("before");
        copy(org, before);
        Run red = validate(packs);
        assertThat(red.exit()).as(red.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(red.codes()).contains("manifest_membership", "manifest_stale");

        Run remediated = remediate(packs);

        assertThat(remediated.exit()).as(remediated.out() + remediated.err()).isEqualTo(PackDoctorMain.VALID);
        assertThat(remediated.out()).contains(org + ": rewritten\n");
        assertThat(remediated.codes()).isEmpty();
        assertThat(remediated.report().get("checks").toString()).contains("ROUND_TRIP");
        Run green = validate(packs);
        assertThat(green.exit()).as(green.out()).isEqualTo(PackDoctorMain.VALID);
        assertThat(green.codes()).isEmpty();
        assertThat(Files.readString(org.resolve(EDITED), StandardCharsets.UTF_8))
                .doesNotContain("An author's note");
        assertThat(Files.readAllBytes(org.resolve(STRAY))).isEqualTo(Files.readAllBytes(before.resolve(STRAY)));
        assertMeaningKept(before, org);
    }

    @Test
    void aSecondRunIsUnchanged() throws IOException {
        Path packs = packs();
        redForManifestAndForm(packs.resolve(ExamplePacks.ORG));
        assertThat(remediate(packs).exit()).isEqualTo(PackDoctorMain.VALID);
        SortedMap<String, String> remediated = treeHash(packs);

        Run again = remediate(packs);

        assertThat(again.exit()).as(again.out() + again.err()).isEqualTo(PackDoctorMain.VALID);
        assertThat(again.out())
                .startsWith(packs.resolve(ExamplePacks.BUILT_IN) + ": unchanged\n" + packs.resolve(ExamplePacks.ORG)
                        + ": unchanged\n");
        assertThat(treeHash(packs)).isEqualTo(remediated);
    }

    // --- the refusal -----------------------------------------------------------------------------------------------

    @Test
    void aPackWithAFaultItDoesNotRepairIsRefusedNamedAndUntouched() throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        Path fragment = org.resolve("drg/fragment.yaml");
        Files.writeString(
                fragment,
                Files.readString(fragment, StandardCharsets.UTF_8)
                        .replace("nodes:\n", "nodes:\n- id: ghost\n  kind: not_a_kind\n  title: Ghost\n"),
                StandardCharsets.UTF_8);
        redForManifestAndForm(org);
        SortedMap<String, String> before = treeHash(org);

        Run run = remediate(packs);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.out())
                .contains(org + ": refused\n")
                .contains("  drg_schema_error at drg/fragment.yaml")
                .contains("ghost");
        assertThat(run.codes()).contains("drg_schema_error");
        assertThat(treeHash(org)).isEqualTo(before);
    }

    // --- the command line ------------------------------------------------------------------------------------------

    @Test
    void remediateWithNoPackIsAUsageFault() {
        Run run = run("remediate");

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).startsWith("pack-doctor: nothing to remediate").contains("pack-doctor remediate <dir>");
    }

    @Test
    void aPathThatIsNoDirectoryIsAUsageFault() {
        Run run = run("remediate", temp.resolve("missing").toString());

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains("is not a directory");
    }

    @Test
    void anOptionIsAUsageFault() {
        Run run = run(
                "remediate", "--round-trip", packs().resolve(ExamplePacks.ORG).toString());

        assertThat(run.exit()).isEqualTo(PackDoctorMain.FAILED);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).startsWith("pack-doctor: unknown option --round-trip");
    }

    @Test
    void remediateHelpPrintsTheUsage() {
        Run run = run("remediate", "--help");

        assertThat(run.exit()).isEqualTo(PackDoctorMain.VALID);
        assertThat(run.out()).isEqualTo(Arguments.USAGE);
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    /** Every YAML and Markdown file the census holds means the same before and after (ADR-0036), the manifest aside. */
    private static void assertMeaningKept(Path before, Path after) throws IOException {
        MeaningComparator comparator = MeaningComparator.adr0036();
        Load yaml = new Load(LoadSettings.builder().build());
        for (Map.Entry<String, String> entry : PackRoundTrip.census(before).entrySet()) {
            String path = entry.getKey();
            if (!PackRoundTrip.modelledKinds().contains(entry.getValue()) || "pack-manifest.yaml".equals(path)) {
                continue;
            }
            String source = Files.readString(before.resolve(path), StandardCharsets.UTF_8);
            String written = Files.readString(after.resolve(path), StandardCharsets.UTF_8);
            if (path.endsWith(".md")) {
                assertThat(comparator.compareMarkdown(markdown(source), markdown(written)))
                        .as(path)
                        .isEmpty();
            } else {
                assertThat(comparator.differences(
                                entry.getValue(), yaml.loadFromString(source), yaml.loadFromString(written)))
                        .as(path)
                        .isEmpty();
            }
        }
    }

    private static Reading markdown(String text) {
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownCodec.Parsed parsed -> new Reading.Markdown(parsed.document());
            case MarkdownRefusal refusal -> new Reading.MarkdownRefused(refusal.message());
        };
    }

    /** Every file and directory under {@code root}, root-relative, with a digest of a file's bytes. */
    private static SortedMap<String, String> treeHash(Path root) throws IOException {
        SortedMap<String, String> tree = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                tree.put(
                        root.relativize(path).toString().replace('\\', '/'),
                        Files.isDirectory(path) ? "<directory>" : digest(Files.readAllBytes(path)));
            }
        }
        return tree;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path to = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
    }
}
