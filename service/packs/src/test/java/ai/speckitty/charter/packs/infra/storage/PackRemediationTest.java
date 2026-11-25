package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
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

        assertThat(outcome).isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
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
        assertThat(new PackRemediation().remediate(root(red)))
                .isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
        SortedMap<String, String> remediated = treeHash(red);

        assertThat(new PackRemediation().remediate(root(red))).isEqualTo(new PackRemediation.Unchanged());
        assertThat(treeHash(red)).isEqualTo(remediated);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
    }

    @Test
    void aPackWithNoManifestGetsTheDerivedOne() throws IOException {
        Path pack = orgPack("bare");
        Files.delete(pack.resolve(PackFileSet.MANIFEST));

        assertThat(new PackRemediation().remediate(root(pack)))
                .isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
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

    /**
     * Tier R B4: a tactic inside the code-point cap whose canonical form is past it (each tab of its plain purpose is
     * written {@code \t}). The round trip refuses it at read-back, by the same cap, so the pack is not round-tripped:
     * an outcome naming the file and the cause, with nothing written, never a failure of the remediation.
     */
    @Test
    void aPackWhoseFileTheRoundTripRefusesIsNotRoundTrippedNamingItAndNothingIsWritten() throws IOException {
        Path pack = orgPack("grows");
        String tactic = "tactics/reasons-canvas-fill.tactic.yaml";
        Files.writeString(
                pack.resolve(tactic),
                "schema_version: \"1.0\"\nid: reasons-canvas-fill\nname: Fill the REASONS Canvas\npurpose: "
                        + "x\t".repeat(360_000) + "x\nsteps:\n- {title: Draft, description: Draft it.}\n",
                StandardCharsets.UTF_8);
        SortedMap<String, String> before = treeHash(pack);

        PackRemediation.Outcome outcome = new PackRemediation().remediate(root(pack));

        assertThat(outcome).isInstanceOf(PackRemediation.NotRoundTripped.class);
        assertThat(((PackRemediation.NotRoundTripped) outcome).refused())
                .containsOnlyKeys(tactic)
                .hasEntrySatisfying(tactic, reason -> assertThat(reason).contains("the document is too large"));
        assertThat(treeHash(pack)).isEqualTo(before);
        assertThat(besides(pack)).containsExactly(ExamplePacks.ORG);
    }

    @Test
    void aPackEditedWhileItIsRemediatedIsRefusedAndTheEditIsKept() throws IOException {
        Path red = redForManifestAndForm();
        String edit = "the author's edit, made after the copy was taken\n";
        PackRemediation remediation = new PackRemediation(
                PackRemediation::rename,
                new Hooks(false),
                PackRemediation::removeTree,
                () -> Files.writeString(red.resolve(STRAY), edit, StandardCharsets.UTF_8));

        PackRemediation.Outcome outcome = remediation.remediate(root(red));

        assertThat(outcome).isInstanceOf(PackRemediation.Refused.class);
        assertThat(((PackRemediation.Refused) outcome).violations())
                .singleElement()
                .satisfies(reason -> assertThat(reason.detail()).contains("the pack changed during remediation"));
        assertThat(Files.readString(red.resolve(STRAY), StandardCharsets.UTF_8))
                .as("the swap never took the edit away")
                .isEqualTo(edit);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
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

    @Test
    void aSwapWhosePutBackFailsNamesWhereThePackIsAndNeverSaysItIsUnchanged() throws IOException {
        Path red = redForManifestAndForm();
        SortedMap<String, String> before = treeHash(red);
        AtomicInteger moves = new AtomicInteger();
        List<Path> asides = new ArrayList<>();
        // The live pack goes aside; the staged pack cannot take its place, and the pack cannot be put back.
        PackRemediation remediation = new PackRemediation((from, to) -> {
            int move = moves.incrementAndGet();
            if (move == 1) {
                asides.add(to);
                Files.move(from, to);
                return;
            }
            throw new IOException("planted: move " + move + " fails");
        });

        assertThatThrownBy(() -> remediation.remediate(root(red)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageNotContaining("unchanged")
                .hasMessageContaining(asides.getFirst().toString())
                .satisfies(thrown -> {
                    assertThat(thrown.getCause()).hasMessage("planted: move 2 fails");
                    assertThat(thrown.getCause().getSuppressed())
                            .singleElement()
                            .satisfies(restore -> assertThat(restore).hasMessage("planted: move 3 fails"));
                });
        assertThat(red).doesNotExist();
        assertThat(treeHash(asides.getFirst())).isEqualTo(before);
        assertThat(besides(asides.getFirst()))
                .as("only the aside copy, which holds the pack; the staged copy is gone")
                .containsExactly(asides.getFirst().getFileName().toString());
    }

    @Test
    void aMoveThatFailsUncheckedIsWordedAsAnyFailedMoveAndThePackIsAsItWas() throws IOException {
        Path red = redForManifestAndForm();
        SortedMap<String, String> before = treeHash(red);
        AtomicInteger moves = new AtomicInteger();
        PackRemediation remediation = new PackRemediation((from, to) -> {
            if (moves.incrementAndGet() == 2) {
                throw new IllegalStateException("planted: an unchecked failure of the move into place");
            }
            Files.move(from, to);
        });

        assertThatThrownBy(() -> remediation.remediate(root(red)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("it is unchanged")
                .hasRootCauseMessage("planted: an unchecked failure of the move into place");
        assertThat(treeHash(red)).isEqualTo(before);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
    }

    @Test
    void anUncheckedFailureWhosePutBackFailsTooCarriesThePutBackWherePackDoctorPrintsIt() throws IOException {
        Path red = redForManifestAndForm();
        AtomicInteger moves = new AtomicInteger();
        List<Path> asides = new ArrayList<>();
        PackRemediation remediation = new PackRemediation((from, to) -> {
            int move = moves.incrementAndGet();
            if (move == 1) {
                asides.add(to);
                Files.move(from, to);
                return;
            }
            throw new IllegalStateException("planted: move " + move + " fails");
        });

        assertThatThrownBy(() -> remediation.remediate(root(red)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining(asides.getFirst().toString())
                .satisfies(thrown -> {
                    // pack-doctor prints the cause and each failure suppressed on it.
                    assertThat(thrown.getCause()).isInstanceOf(IOException.class);
                    assertThat(thrown.getCause()).hasRootCauseMessage("planted: move 2 fails");
                    assertThat(thrown.getCause().getSuppressed())
                            .singleElement()
                            .satisfies(restore -> assertThat(restore).hasMessage("planted: move 3 fails"));
                });
    }

    // --- the old copy (fold review MEDIUM 1)
    // --------------------------------------------------------------------------

    /** Whether this process can write into a directory its mode keeps read-only, as root with a DAC override can. */
    private boolean bypassesPermissions() throws IOException {
        Path probe = Files.createDirectory(temp.resolve("probe"));
        Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            Files.createFile(probe.resolve("written"));
            return true;
        } catch (AccessDeniedException denied) {
            return false;
        } finally {
            Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void aReadOnlyFileIsRepairedAndRewrittenInTheCopyAndKeepsItsMode() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "no POSIX modes here");
        assumeTrue(!bypassesPermissions(), "this process writes through any mode (root with a DAC override)");
        Path red = orgPack("red");
        Path profile = red.resolve(EDITED);
        String text = Files.readString(profile, StandardCharsets.UTF_8);
        Files.writeString(
                profile, text.replace("role: operator\n", "role: operator\nrouting-priority: \"40\"\n"),
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("r--r--r--"));
        try {
            PackRemediation.Outcome outcome = new PackRemediation().remediate(root(red));

            assertThat(outcome)
                    .isEqualTo(new PackRemediation.Rewritten(
                            Optional.empty(),
                            List.of(new PackRemediation.Repaired(EDITED, "/routing-priority", "40", true))));
            assertThat(Files.readString(profile, StandardCharsets.UTF_8)).contains("routing-priority: 40\n");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(profile)))
                    .as("the rewritten file keeps its own mode")
                    .isEqualTo("r--r--r--");
        } finally {
            Files.setPosixFilePermissions(profile, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @Test
    void aPackWithReadOnlyDirectoriesIsRewrittenAndNoOldCopyIsLeftBesideIt() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "no POSIX modes here");
        assumeTrue(!bypassesPermissions(), "this process writes through any mode (root with a DAC override)");
        Path red = redForManifestAndForm();
        Files.delete(red.resolve(PackFileSet.MANIFEST));
        Path profiles = red.resolve("agent_profiles");
        Files.setPosixFilePermissions(profiles, PosixFilePermissions.fromString("r-xr-xr-x"));
        Files.setPosixFilePermissions(red, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThat(new PackRemediation().remediate(root(red)))
                    .isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
            assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(profiles)))
                    .as("the rewritten pack keeps its own modes")
                    .isEqualTo("r-xr-xr-x");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(red.resolve(PackFileSet.MANIFEST))))
                    .as("the manifest the pack never had takes its directory's mode, without search bits")
                    .isEqualTo("r--r--r--");
        } finally {
            Files.setPosixFilePermissions(red, PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.setPosixFilePermissions(profiles, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void anOldCopyThatCannotBeDeletedIsNamedAndTheSwapStands() throws IOException {
        Path red = redForManifestAndForm();
        List<Path> asides = new ArrayList<>();
        PackRemediation remediation = new PackRemediation(
                (from, to) -> {
                    if (to.getFileName().toString().contains(".aside-")) {
                        asides.add(to);
                    }
                    PackRemediation.rename(from, to);
                },
                new Hooks(false),
                copy -> {
                    if (copy.getFileName().toString().contains(".aside-")) {
                        throw new AccessDeniedException(copy.toString(), null, "planted: the old copy stays");
                    }
                    PackRemediation.removeTree(copy);
                });

        PackRemediation.Outcome outcome = remediation.remediate(root(red));

        assertThat(outcome).isEqualTo(new PackRemediation.Rewritten(Optional.of(asides.getFirst()), List.of()));
        assertThat(violationCodes(red)).as("the swap stands").isEmpty();
        assertThat(asides.getFirst()).isDirectory();
    }

    // --- a pack root that is a link (B3) -----------------------------------------------------------------------------

    @Test
    void aPackRootThatIsALinkIsRemediatedInTheDirectoryItNamesAndTheLinkIsKept() throws IOException {
        Path red = redForManifestAndForm();
        Path link = temp.resolve("current");
        try {
            Files.createSymbolicLink(link, red);
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(false, "the file system cannot make a symbolic link");
            return;
        }

        PackRemediation.Outcome outcome =
                new PackRemediation().remediate(new PackRoot(ExamplePacks.ORG, Optional.empty(), link.toString()));

        assertThat(outcome).isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readSymbolicLink(link)).isEqualTo(red);
        assertThat(violationCodes(red)).isEmpty();
        assertThat(besides(red)).containsExactlyInAnyOrder(ExamplePacks.ORG);
        assertThat(besides(link)).contains("current").noneMatch(name -> name.startsWith("."));
    }

    // --- nothing beside an unchanged pack (B4) -----------------------------------------------------------------------

    @Test
    void anUnchangedPackHasNothingWrittenBesideItOrInIt() throws IOException {
        Path red = redForManifestAndForm();
        assertThat(new PackRemediation().remediate(root(red)))
                .isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
        FileTime longAgo = FileTime.from(Instant.parse("2001-01-01T00:00:00Z"));
        Files.setLastModifiedTime(red.getParent(), longAgo);
        Files.setLastModifiedTime(red, longAgo);

        assertThat(new PackRemediation().remediate(root(red))).isEqualTo(new PackRemediation.Unchanged());

        assertThat(Files.getLastModifiedTime(red.getParent()))
                .as("no staged copy was made beside the pack, so a read-only parent is no obstacle")
                .isEqualTo(longAgo);
        assertThat(Files.getLastModifiedTime(red)).isEqualTo(longAgo);
    }

    // --- a stop of the JVM (B5) --------------------------------------------------------------------------------------

    /** The hook a remediation registers, run by the test as the JVM would run it. */
    private static final class Hooks implements PackRemediation.Interruption {
        private final List<Thread> registered = new ArrayList<>();
        private final List<Thread> unregistered = new ArrayList<>();
        private final boolean runAtOnce;

        Hooks(boolean runAtOnce) {
            this.runAtOnce = runAtOnce;
        }

        @Override
        public void register(Thread hook) {
            registered.add(hook);
            if (runAtOnce) {
                // The stop arrives at once: the hook runs to its end before the remediation goes on.
                hook.start();
                try {
                    hook.join(10_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }

        @Override
        public void unregister(Thread hook) {
            unregistered.add(hook);
        }
    }

    @Test
    void aStopBeforeTheSwapLeavesThePackAsItWasAndNothingBesideIt() throws IOException {
        Path red = redForManifestAndForm();
        SortedMap<String, String> before = treeHash(red);
        Hooks hooks = new Hooks(true);

        assertThatThrownBy(() -> new PackRemediation(PackRemediation::rename, hooks).remediate(root(red)))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("it is unchanged");
        assertThat(treeHash(red)).isEqualTo(before);
        assertThat(besides(red)).containsExactly(ExamplePacks.ORG);
        assertThat(hooks.unregistered).isEqualTo(hooks.registered).hasSize(1);
    }

    @Test
    void aStopDuringTheSwapWaitsForItThenLeavesNothingBesideThePack() throws Exception {
        Path red = redForManifestAndForm();
        Hooks hooks = new Hooks(false);
        AtomicInteger moves = new AtomicInteger();
        PackRemediation remediation = new PackRemediation(
                (from, to) -> {
                    if (moves.incrementAndGet() == 1) {
                        // The JVM is told to stop as the swap begins: its hook must wait for the swap to end.
                        Thread hook = hooks.registered.getFirst();
                        hook.start();
                        long deadline = System.nanoTime() + 10_000_000_000L;
                        while (hook.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                            Thread.onSpinWait();
                        }
                        assertThat(hook.getState()).isEqualTo(Thread.State.BLOCKED);
                    }
                    PackRemediation.rename(from, to);
                },
                hooks);

        assertThat(remediation.remediate(root(red)))
                .isEqualTo(new PackRemediation.Rewritten(Optional.empty(), List.of()));
        hooks.registered.getFirst().join(10_000);

        assertThat(violationCodes(red)).isEmpty();
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
