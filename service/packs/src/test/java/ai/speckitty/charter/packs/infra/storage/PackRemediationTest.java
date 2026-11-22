package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.FileOutcome;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PackRemediation} (FR-009, ruling R-j, plan D7, T028/T029/T031): a pack whose only faults are its manifest's and
 * its files' form is rewritten in the round trip's canonical form and restamped, its meaning and its stray files kept; a
 * pack with any other storage fault is refused with nothing written; a remediated pack is unchanged by a second run; and
 * a failed swap leaves the pack as it was.
 */
class PackRemediationTest {

    /** A constituent of the example org pack, rewritten out of canonical form by the fixture. */
    private static final String EDITED = "agent_profiles/human-operator.agent.yaml";

    /** A constituent the fixture's manifest leaves out. */
    private static final String LEFT_OUT = "agent_profiles/reviewer-rita.agent.yaml";

    /** A file the census does not hold. */
    private static final String STRAY = "notes.txt";

    @TempDir
    Path temp;

    // --- the fixtures ----------------------------------------------------------------------------------------------

    /** A copy of the example org pack, alone under {@code temp/<dir>/}. */
    private Path orgPack(String dir) {
        return ExamplePacks.copyOf(ExamplePacks.ORG, temp.resolve(dir));
    }

    private static PackRoot root(Path pack) {
        return new PackRoot(pack.getFileName().toString(), Optional.empty(), pack.toString());
    }

    /**
     * The org pack red for its manifest and its form: one constituent's keys out of canonical order under an author's
     * comment (its content hash goes stale), one constituent left out of the manifest, and a stray file.
     */
    private Path redForManifestAndForm() throws IOException {
        Path pack = orgPack("red");
        Path edited = pack.resolve(EDITED);
        List<String> lines = Files.readAllLines(edited, StandardCharsets.UTF_8);
        StringBuilder reordered = new StringBuilder("# An author's note the canonical form does not keep.\n");
        reordered.append(lines.get(1)).append('\n').append(lines.get(0)).append('\n');
        lines.subList(2, lines.size()).forEach(line -> reordered.append(line).append('\n'));
        Files.writeString(edited, reordered.toString(), StandardCharsets.UTF_8);
        Path manifest = pack.resolve(PackFileSet.MANIFEST);
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        int entry = text.indexOf("- kind: agent_profile\n  id: reviewer-rita\n");
        int next = text.indexOf("- kind:", entry + 1);
        Files.writeString(manifest, text.substring(0, entry) + text.substring(next), StandardCharsets.UTF_8);
        Files.writeString(pack.resolve(STRAY), "an operator's notes, kept byte for byte\r\n", StandardCharsets.UTF_8);
        return pack;
    }

    /** The org pack with a graph fault remediation does not repair, beside a stale manifest it would. */
    private Path unrepairable() throws IOException {
        Path pack = orgPack("unrepairable");
        Path fragment = pack.resolve("drg/fragment.yaml");
        Files.writeString(
                fragment,
                Files.readString(fragment, StandardCharsets.UTF_8)
                        .replace("nodes:\n", "nodes:\n- id: ghost\n  kind: not_a_kind\n  title: Ghost\n"),
                StandardCharsets.UTF_8);
        Path edited = pack.resolve(EDITED);
        Files.writeString(
                edited, Files.readString(edited, StandardCharsets.UTF_8) + "# edited\n", StandardCharsets.UTF_8);
        return pack;
    }

    private static List<String> violationCodes(Path pack) {
        return codes(StorageRulesets.verdict(new FileSystemPackInspector().readTree(root(pack)))
                .violations());
    }

    private static List<String> codes(List<Reason> reasons) {
        return reasons.stream().map(reason -> reason.code().value()).toList();
    }

    /** Every file and directory under {@code pack}, pack-relative, with a digest of a file's bytes. */
    private static SortedMap<String, String> treeHash(Path pack) throws IOException {
        SortedMap<String, String> tree = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(pack)) {
            for (Path path : paths.toList()) {
                String relative = pack.relativize(path).toString().replace('\\', '/');
                tree.put(relative, Files.isDirectory(path) ? "<directory>" : digest(Files.readAllBytes(path)));
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

    /** The names beside the pack in its parent directory: no staged or aside copy is left behind. */
    private static List<String> besides(Path pack) throws IOException {
        try (Stream<Path> entries = Files.list(pack.getParent())) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    // --- the precondition (T029) -----------------------------------------------------------------------------------

    @Test
    void thePreconditionIsTheVerdictWithoutTheManifestGate() throws IOException {
        Path red = redForManifestAndForm();
        TreeRead read = new FileSystemPackInspector().readTree(root(red));

        assertThat(codes(StorageRulesets.verdict(read).violations())).contains("manifest_membership", "manifest_stale");
        assertThat(StorageRulesets.verdictWithoutTheManifestGate(read).violations())
                .isEmpty();
    }

    @Test
    void thePreconditionKeepsEveryOtherViolation() throws IOException {
        TreeRead read = new FileSystemPackInspector().readTree(root(unrepairable()));

        assertThat(codes(StorageRulesets.verdictWithoutTheManifestGate(read).violations()))
                .containsExactly("drg_schema_error");
        assertThat(StorageRulesets.verdictWithoutTheManifestGate(read).violations())
                .isEqualTo(StorageRulesets.verdict(read).violations());
    }

    // --- the rewrite (T028, T031) ----------------------------------------------------------------------------------

    @Test
    void aPackRedForItsManifestAndFormIsRewrittenCanonicalAndStamped() throws IOException {
        Path red = redForManifestAndForm();
        Path before = temp.resolve("before");
        TreeCopy.copy(red, before);
        assertThat(violationCodes(red)).contains("manifest_membership", "manifest_stale");

        PackRemediation.Outcome outcome = new PackRemediation().remediate(root(red));

        assertThat(outcome).isEqualTo(new PackRemediation.Rewritten());
        assertThat(violationCodes(red)).isEmpty();
        assertThat(Files.readString(red.resolve(EDITED), StandardCharsets.UTF_8))
                .isEqualTo(canonicalOf(ExamplePacks.pack(ExamplePacks.ORG), EDITED))
                .doesNotContain("An author's note");
        assertThat(Files.readString(red.resolve(PackFileSet.MANIFEST), StandardCharsets.UTF_8))
                .contains("path: " + LEFT_OUT);
        assertThat(Files.readAllBytes(red.resolve(STRAY))).isEqualTo(Files.readAllBytes(before.resolve(STRAY)));
        assertThat(Files.readAllBytes(red.resolve("docs/authoring-notes.md")))
                .isEqualTo(Files.readAllBytes(before.resolve("docs/authoring-notes.md")));
        assertMeaningKept(before, red);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
    }

    @Test
    void aSecondRunIsUnchanged() throws IOException {
        Path red = redForManifestAndForm();
        assertThat(new PackRemediation().remediate(root(red))).isEqualTo(new PackRemediation.Rewritten());
        SortedMap<String, String> remediated = treeHash(red);

        assertThat(new PackRemediation().remediate(root(red))).isEqualTo(new PackRemediation.Unchanged());
        assertThat(treeHash(red)).isEqualTo(remediated);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
    }

    @Test
    void aPackWithNoManifestGetsTheDerivedOne() throws IOException {
        Path pack = orgPack("bare");
        Files.delete(pack.resolve(PackFileSet.MANIFEST));

        assertThat(new PackRemediation().remediate(root(pack))).isEqualTo(new PackRemediation.Rewritten());
        assertThat(pack.resolve(PackFileSet.MANIFEST)).isRegularFile();
        assertThat(violationCodes(pack)).isEmpty();
    }

    // --- the refusal and the staged swap (T028, T029, T031) --------------------------------------------------------

    @Test
    void aPackWithAFaultItDoesNotRepairIsRefusedAndNothingIsWritten() throws IOException {
        Path pack = unrepairable();
        SortedMap<String, String> before = treeHash(pack);

        PackRemediation.Outcome outcome = new PackRemediation().remediate(root(pack));

        assertThat(outcome).isInstanceOf(PackRemediation.Refused.class);
        List<Reason> violations = ((PackRemediation.Refused) outcome).violations();
        assertThat(codes(violations)).containsExactly("drg_schema_error");
        assertThat(violations.getFirst().location().file()).isEqualTo("drg/fragment.yaml");
        assertThat(violations.getFirst().detail()).contains("ghost");
        assertThat(treeHash(pack)).isEqualTo(before);
        assertThat(besides(pack)).containsExactly(ExamplePacks.ORG);
    }

    @Test
    void aSwapThatFailsLeavesThePackAsItWas() throws IOException {
        Path red = redForManifestAndForm();
        SortedMap<String, String> before = treeHash(red);
        AtomicInteger moves = new AtomicInteger();
        // The first move puts the live pack aside; the second, the staged pack into its place, fails.
        PackRemediation remediation = new PackRemediation((from, to) -> {
            if (moves.incrementAndGet() == 2) {
                throw new IOException("planted: the staged pack cannot take the live pack's place");
            }
            Files.move(from, to);
        });

        assertThatThrownBy(() -> remediation.remediate(root(red)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("is unchanged")
                .hasRootCauseMessage("planted: the staged pack cannot take the live pack's place");
        assertThat(moves).hasValue(3);
        assertThat(treeHash(red)).isEqualTo(before);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    /** The text the round trip writes for {@code path} of the pack in {@code source}: its canonical form. */
    private String canonicalOf(Path source, String path) throws IOException {
        Path target = temp.resolve("canonical");
        PackRoundTripResult result = PackRoundTrip.run(new PackIdentifier(ExamplePacks.ORG), source, target);
        FileOutcome.Written written = (FileOutcome.Written) result.outcomes().get(path);
        return Files.readString(target.resolve(written.writtenPath()), StandardCharsets.UTF_8);
    }

    /** Every file the census holds means the same before and after (ADR-0036), its derived manifest fields aside. */
    private static void assertMeaningKept(Path before, Path after) throws IOException {
        TreeCensus census = WholePackCensus.census(before);
        SortedMap<String, String> kinds = WholePackCensus.kinds(census);
        Function<String, ContentForm> forms = WholePackCensus.formsOf(census);
        MeaningOfTexts meaning = MeaningOfTexts.adr0036();
        for (var entry : kinds.entrySet()) {
            String path = entry.getKey();
            // The manifest's lists and hashes are derived, not meaning: the stamp writes them (ADR-0042).
            if (WholePackCensus.notHeld(census).containsKey(path) || PackFileSet.MANIFEST.equals(path)) {
                continue;
            }
            Path source = before.resolve(path);
            Path written = after.resolve(path);
            switch (forms.apply(path)) {
                case YAML ->
                    assertThat(meaning.differencesOfTexts(entry.getValue(), text(source), text(written)))
                            .as(path)
                            .isEmpty();
                case MARKDOWN ->
                    assertThat(meaning.compareMarkdown(text(source), text(written)))
                            .as(path)
                            .isEmpty();
                case TEXT, BYTES ->
                    assertThat(Files.readAllBytes(written)).as(path).isEqualTo(Files.readAllBytes(source));
                default -> throw new AssertionError("no meaning comparison for the form of " + path);
            }
        }
    }

    private static String text(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** A plain recursive copy, for the snapshot taken before a remediation. */
    private static final class TreeCopy {
        private TreeCopy() {}

        static void copy(Path source, Path target) throws IOException {
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
}
