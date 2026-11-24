package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packs.domain.roundtrip.Reading;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator;
import ai.speckitty.charter.testfixtures.Fifos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
        // A pack directory whose name is no pack id, given with no =<id>, comes first: it is refused by name and
        // the packs after it are still remediated, never a crash that stops the run.
        Path misnamed = packs.resolve("Not_A_Pack_Id");
        copy(packs.resolve(ExamplePacks.BUILT_IN), misnamed);
        SortedMap<String, String> misnamedBefore = treeHash(misnamed);
        String[] given = values(packs);

        Run run = run("remediate", misnamed + ":built-in", given[0], given[1]);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.out())
                .contains(misnamed + ": refused\n")
                .contains("  pack_id_invalid at pack: pack directory 'Not_A_Pack_Id' is not a pack id.")
                .contains("write `" + misnamed + "=<id>`")
                .contains(packs.resolve(ExamplePacks.BUILT_IN) + ": rewritten\n")
                .contains(org + ": refused\n")
                .contains("  drg_schema_error at drg/fragment.yaml")
                .contains("ghost");
        assertThat(run.codes()).contains("drg_schema_error");
        assertThat(treeHash(org)).isEqualTo(before);
        assertThat(treeHash(misnamed)).isEqualTo(misnamedBefore);
    }

    /**
     * Operator ruling R-ad: a special file in a pack (a FIFO, made at test time; skipped where the operating system
     * cannot make one) is a fault the precondition refuses, as validate refuses it, naming the file: the run exits 1 and
     * the pack is left as it was. Before R-ad the precondition passed over it and the copy failed, exit 2.
     */
    @Test
    @Timeout(120)
    void aSpecialFileIsRefusedByValidateAndByRemediateNamingIt() throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        assumeTrue(Fifos.make(org.resolve("notes.fifo")), "the operating system cannot make a FIFO here");
        SortedMap<String, String> before = treeHash(org);

        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.codes()).containsExactly("unreadable");
            assertThat(run.out())
                    .contains("notes.fifo: is not a regular file or a directory; a pack holds only regular files and"
                            + " directories");
        }
        assertThat(remediated.out()).contains(org + ": refused\n").contains("  unreadable at notes.fifo");
        assertThat(treeHash(org)).isEqualTo(before);
    }

    /**
     * A file no reader can walk, which the precondition refuses: an alias bomb past the parser's alias budget, and a
     * document nested far past the depth bound. Neither {@code validate --round-trip} nor {@code remediate} reaches an
     * unguarded parse of it: each is a problem ({@code 1}), never an internal error or a crash.
     */
    static Stream<Named<String>> unwalkable() {
        StringBuilder bomb = new StringBuilder("a0: &a0 [x, x, x, x, x, x, x, x, x]\n");
        for (int level = 1; level <= 6; level++) {
            bomb.append("a%d: &a%d [%s]\n"
                    .formatted(level, level, String.join(", ", Collections.nCopies(9, "*a" + (level - 1)))));
        }
        return Stream.of(
                Named.of("an alias bomb", bomb.toString()),
                Named.of("nesting 100 000 deep", "a: " + "[".repeat(100_000) + "]".repeat(100_000) + "\n"));
    }

    @ParameterizedTest
    @MethodSource("unwalkable")
    void aFileNoReaderCanWalkIsAProblemForValidateWithTheRoundTripAndForRemediate(String text) throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        Files.writeString(org.resolve(EDITED), text, StandardCharsets.UTF_8);
        SortedMap<String, String> before = treeHash(org);

        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.err()).doesNotContain("internal error");
            assertThat(run.codes()).contains("unreadable");
        }
        assertThat(remediated.out()).contains(org + ": refused\n").contains("  unreadable at " + EDITED);
        assertThat(treeHash(org)).isEqualTo(before);
    }

    /** A tactic of the org example pack, which the size tests below replace. */
    private static final String TACTIC = "tactics/reasons-canvas-fill.tactic.yaml";

    /** A valid tactic whose purpose is {@code purpose} and whose steps are {@code steps}, as YAML flow items. */
    private static String tactic(String purpose, String steps) {
        return "schema_version: \"1.0\"\nid: reasons-canvas-fill\nname: Fill the REASONS Canvas\npurpose: " + purpose
                + "\nsteps:\n" + steps;
    }

    /**
     * Tier R B3: a scalar shared through aliases, within the code-point cap as text, whose expansion is past it. The
     * expanded size is bounded at the cap, so the file is {@code unreadable} on every path that parses it, and
     * neither {@code validate --round-trip} nor {@code remediate} serializes the expansion. Before the bound, the
     * reviewer's 500 000-code-point scalar aliased 30 000 times ran out of memory after about 90 s, exit 1, stdout empty.
     */
    @Test
    @Timeout(120)
    void aScalarAliasedPastTheCapIsUnreadableForValidateWithTheRoundTripAndForRemediate() throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        Files.writeString(
                org.resolve(TACTIC),
                tactic("&a \"" + "p".repeat(20_000) + "\"", "- {title: *a, description: *a}\n".repeat(200)),
                StandardCharsets.UTF_8);
        SortedMap<String, String> before = treeHash(org);

        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.err()).doesNotContain("internal error");
            assertThat(run.codes()).contains("unreadable");
            assertThat(run.out()).contains("the document is too large");
        }
        assertThat(remediated.out()).contains(org + ": refused\n").contains("  unreadable at " + TACTIC);
        assertThat(treeHash(org)).isEqualTo(before);
    }

    /**
     * Tier R B3': a long number scalar aliased many times. Its text is past the number bound, so the file is
     * {@code unreadable} before the loader converts it, on {@code validate}, with the round trip and for
     * {@code remediate}. Before, each reach cost a conversion downstream, and the confirm pass's 500 000-digit probe
     * with 30 000 reaches ran past 600 s.
     */
    @Test
    @Timeout(120)
    void aLongNumberAliasedManyTimesIsUnreadableOnEveryCommand() throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        Files.writeString(
                org.resolve(TACTIC),
                tactic(
                        "Big.",
                        "- {title: T, description: &a " + "9".repeat(20_000) + "}\n"
                                + "- {title: *a, description: *a}\n".repeat(40)),
                StandardCharsets.UTF_8);
        SortedMap<String, String> before = treeHash(org);

        String[] given = values(packs);
        Run plain = run("validate", "--pack", given[0], "--pack", given[1]);
        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(plain, validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.err()).doesNotContain("internal error");
            assertThat(run.codes()).contains("unreadable").doesNotContain("artifact_schema_error");
            assertThat(run.out()).contains("the document is too large");
        }
        assertThat(remediated.out()).contains(org + ": refused\n").contains("  unreadable at " + TACTIC);
        assertThat(treeHash(org)).isEqualTo(before);
    }

    /**
     * Tier R B3'': a routing catalog's score whose plain form is long ({@code 1e-99999}: 12 characters, 100 000 digits
     * written plain). It is refused when it is read, before anything renders it, so {@code validate}, the round trip
     * and {@code remediate} each answer {@code unreadable ... the document is too large}, exit 1. Before, the round trip
     * refused it as "no domain element was built", and {@code 1e-999999999} ran out of memory.
     */
    @Test
    @Timeout(120)
    void aScoreWhosePlainFormIsPastTheBoundIsUnreadableOnEveryCommand() throws IOException {
        Path packs = packs();
        Path builtIn = packs.resolve(ExamplePacks.BUILT_IN);
        Path catalog = builtIn.resolve("routing_catalogs/default.routing-catalog.yaml");
        Files.writeString(
                catalog,
                Files.readString(catalog, StandardCharsets.UTF_8).replaceFirst("score: 0\\.3", "score: 1e-99999"),
                StandardCharsets.UTF_8);
        SortedMap<String, String> before = treeHash(builtIn);

        String[] given = values(packs);
        Run plain = run("validate", "--pack", given[0], "--pack", given[1]);
        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(plain, validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.err()).doesNotContain("internal error");
            assertThat(run.codes()).contains("unreadable");
            assertThat(run.out()).contains("the document is too large").doesNotContain("no domain element was built");
        }
        assertThat(remediated.out())
                .contains(builtIn + ": refused\n")
                .contains("  unreadable at routing_catalogs/default.routing-catalog.yaml");
        assertThat(treeHash(builtIn)).isEqualTo(before);
    }

    /**
     * Tier R B4: a file inside the cap whose canonical form is past it (each tab of a plain scalar is written {@code \t}
     * in a double-quoted one, so the text grows by half). The canonical form is judged by the same cap when the round
     * trip reads it back: the file is a pack fault, named with its cause, on {@code validate --round-trip} and on
     * {@code remediate} alike (exit 1, a full report), never exit 2. Before, {@code remediate} exited 2 with stdout empty.
     */
    @Test
    @Timeout(120)
    void aFileWhoseCanonicalFormIsPastTheCapIsAProblemForValidateWithTheRoundTripAndForRemediate() throws IOException {
        Path packs = packs();
        Path org = packs.resolve(ExamplePacks.ORG);
        String purpose = "x\t".repeat(360_000) + "x";
        Files.writeString(
                org.resolve(TACTIC),
                tactic(purpose, "- {title: Draft, description: Draft it.}\n"),
                StandardCharsets.UTF_8);
        String text = Files.readString(org.resolve(TACTIC), StandardCharsets.UTF_8);
        assertThat(text.codePointCount(0, text.length())).isLessThan(1024 * 1024);
        SortedMap<String, String> before = treeHash(org);

        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.err()).doesNotContain("internal error").doesNotContain("is unchanged");
            // One fault for one cause: the written copy is paired with its source, never also "written but unpaired".
            assertThat(run.codes()).containsOnlyOnce("round_trip_red").doesNotContain("round_trip_written_unpaired");
            assertThat(run.out()).contains(TACTIC).contains("the document is too large");
        }
        assertThat(remediated.out())
                .contains(org + ": refused\n")
                .contains("  the round trip refused " + TACTIC + ": ")
                .contains("the document is too large");
        assertThat(treeHash(org)).isEqualTo(before);
    }

    /**
     * Tier R N5: a graph shard inside the cap whose canonical form is past it (a label of tabs, each written {@code \t}).
     * The round trip judges every text it writes by the cap, not only an artefact's, so the shard is one
     * {@code round_trip_red} naming the size, with no unreadable copy left unpaired, and {@code remediate} refuses the
     * pack naming it, exit 1.
     */
    @Test
    @Timeout(120)
    void aGraphShardWhoseCanonicalFormIsPastTheCapIsOneProblemNamingTheSize() throws IOException {
        Path packs = packs();
        Path builtIn = packs.resolve(ExamplePacks.BUILT_IN);
        Path shard = builtIn.resolve("directive.graph.yaml");
        Files.writeString(
                shard,
                Files.readString(shard, StandardCharsets.UTF_8)
                        .replace("label: Architectural Integrity Standard", "label: " + "x\t".repeat(360_000) + "x"),
                StandardCharsets.UTF_8);
        SortedMap<String, String> before = treeHash(builtIn);

        Run validated = validate(packs);
        Run remediated = remediate(packs);

        for (Run run : List.of(validated, remediated)) {
            assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
            assertThat(run.err()).doesNotContain("internal error").doesNotContain("is unchanged");
            assertThat(run.codes()).containsOnlyOnce("round_trip_red").doesNotContain("round_trip_written_unpaired");
            assertThat(run.out()).contains("directive.graph.yaml").contains("the document is too large");
        }
        assertThat(remediated.out())
                .contains(builtIn + ": refused\n")
                .contains("  the round trip refused directive.graph.yaml: not written: its canonical form holds");
        assertThat(treeHash(builtIn)).isEqualTo(before);
    }

    /**
     * Squad B5: a pack that could not be put back is not "unchanged"; the remediation's message names where it is, and
     * standard error carries the failure and every failure suppressed on it (the failed put-back).
     */
    @Test
    void aFailureWhosePutBackFailedIsPrintedWithWhereThePackIsAndEverySuppressedFailure() throws IOException {
        Path packs = packs();
        IOException swap = new IOException("planted: the staged pack cannot take the pack's place");
        swap.addSuppressed(new IOException("planted: the pack cannot be put back"));
        Remediate.Repair failing = root -> {
            throw new UncheckedIOException(
                    "cannot remediate " + root.locator() + ", and it could not be put back: the pack as it was is at"
                            + " /planted/.org-base.aside-1",
                    swap);
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] given = values(packs);

        int exit = PackDoctorMain.run(
                new String[] {"remediate", given[0], given[1]},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                Files.createDirectories(temp.resolve("tmp")),
                new PackDoctorMain.Composition(new FileSystemPackInspector(), PackRoundTrip::run, failing));

        assertThat(exit).isEqualTo(PackDoctorMain.FAILED);
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("the pack as it was is at /planted/.org-base.aside-1")
                .contains("planted: the staged pack cannot take the pack's place")
                .contains("planted: the pack cannot be put back")
                .doesNotContain("unchanged");
    }

    /**
     * Fold review MEDIUM 1: a pack rewritten whose old copy could not be deleted is still rewritten and the run's exit
     * stands; a warning on standard error names the copy to delete.
     */
    @Test
    void anOldCopyLeftBehindIsAWarningNamingItAndTheExitStands() throws IOException {
        Path packs = packs();
        Remediate.Repair leftBehind =
                root -> new Remediate.Rewritten(Optional.of(Path.of("/planted/.org-base.aside-1")));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] given = values(packs);

        int exit = PackDoctorMain.run(
                new String[] {"remediate", given[0], given[1]},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                Files.createDirectories(temp.resolve("tmp")),
                new PackDoctorMain.Composition(new FileSystemPackInspector(), PackRoundTrip::run, leftBehind));

        assertThat(exit).isEqualTo(PackDoctorMain.VALID);
        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains(given[0].substring(0, given[0].lastIndexOf(':')) + ": rewritten");
        assertThat(err.toString(StandardCharsets.UTF_8))
                .contains("pack-doctor: warning: ")
                .contains(
                        "was rewritten, but its old copy could not be deleted: /planted/.org-base.aside-1; delete it");
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
                        Files.isDirectory(path)
                                ? "<directory>"
                                : Files.isRegularFile(path) ? digest(Files.readAllBytes(path)) : "<special>");
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
