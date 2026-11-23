package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The manifest gate's stale check (#630, operator ruling R-b, plan D4): a {@code content_hash} or a
 * {@code manifest_hash} of {@code pack-manifest.yaml} that does not verify against the incoming bytes is one
 * {@code manifest_stale} per field, at the manifest, after {@code manifest_membership}; a path membership already names
 * gets none. The rule reads the census's stale fields and derives nothing; the reader fills them from the bytes.
 */
@DisplayName("ManifestRules: manifest_stale (#630)")
class ManifestRulesTest {

    private static final FileSystemPackInspector READER = new FileSystemPackInspector();

    private static final String SKILL = "skills/project-triage.skill.yaml";

    private static final String PRESET = "presets/project.yaml";

    private static final TreeCensus.Entries HELD = new TreeCensus.Entries(
            List.of("tactic:a at tactics/a.tactic.yaml", "tactic:b at tactics/b.tactic.yaml"),
            List.of("p at presets/p.yaml"));

    private static final TreeCensus.StaleField STALE_A = new TreeCensus.StaleField(
            "constituents{tactics/a.tactic.yaml}.content_hash",
            List.of("constituents", "0", "content_hash"),
            Optional.of("tactics/a.tactic.yaml"));

    private static final TreeCensus.StaleField STALE_P = new TreeCensus.StaleField(
            "presets{presets/p.yaml}.content_hash",
            List.of("presets", "0", "content_hash"),
            Optional.of("presets/p.yaml"));

    private static final TreeCensus.StaleField STALE_HASH =
            new TreeCensus.StaleField("manifest_hash", List.of("manifest_hash"), Optional.empty());

    private static TreeRead read(TreeCensus.Manifest manifest) {
        PackContents contents = new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Absent<>("pack.yaml"),
                List.of(),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of(),
                List.of());
        return new TreeRead(
                contents,
                new TreeCensus(List.of(), List.of(), manifest, TreeCensus.ReservedShard.ABSENT, List.of(), List.of()),
                List.of());
    }

    private static TreeCensus.Manifest held(List<TreeCensus.StaleField> stale) {
        return new TreeCensus.Manifest(true, true, Optional.of(HELD), Optional.of(HELD), stale);
    }

    @Nested
    @DisplayName("over the census")
    class OverTheCensus {

        @Test
        @DisplayName("a stale constituent hash is one manifest_stale at the manifest, naming the field")
        void aStaleConstituentHashIsOneManifestStale() {
            assertThat(StorageRulesets.manifestGate(read(held(List.of(STALE_A)))))
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(tuple(
                            StorageCodes.MANIFEST_STALE,
                            Location.in("pack-manifest.yaml").at("/constituents/0/content_hash"),
                            "pack-manifest.yaml: constituents{tactics/a.tactic.yaml}.content_hash does not match the"
                                    + " bytes of tactics/a.tactic.yaml"));
        }

        @Test
        @DisplayName("a stale preset hash and a stale manifest_hash are each one manifest_stale, in the census's order")
        void eachStaleFieldIsOneReason() {
            assertThat(StorageRulesets.manifestGate(read(held(List.of(STALE_A, STALE_P, STALE_HASH)))))
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    Location.in("pack-manifest.yaml").at("/constituents/0/content_hash"),
                                    "pack-manifest.yaml: constituents{tactics/a.tactic.yaml}.content_hash does not"
                                            + " match the bytes of tactics/a.tactic.yaml"),
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    Location.in("pack-manifest.yaml").at("/presets/0/content_hash"),
                                    "pack-manifest.yaml: presets{presets/p.yaml}.content_hash does not match the"
                                            + " bytes of presets/p.yaml"),
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    Location.in("pack-manifest.yaml").at("/manifest_hash"),
                                    "pack-manifest.yaml: manifest_hash does not match the manifest's own fields"));
        }

        @Test
        @DisplayName("a path membership names gets no manifest_stale; membership is stated first")
        void aPathMembershipNamesGetsNoManifestStale() {
            TreeCensus.Entries listed = new TreeCensus.Entries(
                    List.of("tactic:x at tactics/a.tactic.yaml", "tactic:b at tactics/b.tactic.yaml"),
                    List.of("p at presets/p.yaml"));
            TreeCensus.Manifest manifest = new TreeCensus.Manifest(
                    true, true, Optional.of(listed), Optional.of(HELD), List.of(STALE_A, STALE_P, STALE_HASH));

            assertThat(StorageRulesets.manifestGate(read(manifest)))
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(
                            tuple(
                                    StorageCodes.MANIFEST_MEMBERSHIP,
                                    "pack-manifest.yaml: lists constituent tactic:x at tactics/a.tactic.yaml, which"
                                            + " the pack does not hold"),
                            tuple(
                                    StorageCodes.MANIFEST_MEMBERSHIP,
                                    "pack-manifest.yaml: leaves out constituent tactic:a at tactics/a.tactic.yaml,"
                                            + " which the pack holds"),
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    "pack-manifest.yaml: presets{presets/p.yaml}.content_hash does not match the"
                                            + " bytes of presets/p.yaml"),
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    "pack-manifest.yaml: manifest_hash does not match the manifest's own fields"));
        }

        @Test
        @DisplayName("mismatch returns the paths it names beside its reasons")
        void mismatchReturnsThePathsItNames() {
            TreeCensus.Entries listed = new TreeCensus.Entries(
                    List.of("tactic:x at tactics/a.tactic.yaml", "tactic:b at tactics/b.tactic.yaml"),
                    List.of("p at presets/p.yaml", "p at presets/p.yaml"));

            ManifestRules.Mismatch mismatch = ManifestRules.mismatch(listed, HELD);

            assertThat(mismatch.reasons())
                    .containsExactly(
                            "lists constituent tactic:x at tactics/a.tactic.yaml, which the pack does not hold",
                            "lists preset p at presets/p.yaml twice",
                            "leaves out constituent tactic:a at tactics/a.tactic.yaml, which the pack holds");
            assertThat(mismatch.paths()).containsExactlyInAnyOrder("tactics/a.tactic.yaml", "presets/p.yaml");
            assertThat(ManifestRules.mismatch(HELD, HELD).reasons()).isEmpty();
            assertThat(ManifestRules.mismatch(HELD, HELD).paths()).isEmpty();
        }

        @Test
        @DisplayName("no manifest, or one that is not a regular file or was not read, states no manifest_stale")
        void noReadableManifestIsNoManifestStale() {
            for (TreeCensus.Manifest none : List.of(
                    TreeCensus.Manifest.ABSENT,
                    new TreeCensus.Manifest(true, false, Optional.of(HELD), Optional.of(HELD), List.of(STALE_A)),
                    new TreeCensus.Manifest(true, true, Optional.empty(), Optional.empty(), List.of()),
                    held(List.of()))) {
                assertThat(StorageRulesets.manifestGate(read(none)))
                        .as("%s", none)
                        .isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("over a real read: the reader fills the stale fields from the incoming bytes")
    class OverARead {

        private static List<Reason> violations(Path pack) {
            return READER
                    .read(new PackRoot(
                            ExamplePacks.PROJECT,
                            Optional.of(ExamplePacks.level(ExamplePacks.PROJECT)),
                            pack.toString()))
                    .rationale()
                    .details()
                    .stream()
                    .filter(reason -> reason.isFailing(Severity.VIOLATION))
                    .toList();
        }

        private static void append(Path file, String text) throws IOException {
            Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8) + text, StandardCharsets.UTF_8);
        }

        private static void replace(Path file, String from, String to) throws IOException {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(text).contains(from);
            Files.writeString(file, text.replace(from, to), StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("the fresh example pack has no violation (the control)")
        void theFreshPackIsClean(@TempDir Path temp) {
            assertThat(violations(ExamplePacks.copyOf(ExamplePacks.PROJECT, temp)))
                    .isEmpty();
        }

        @Test
        @DisplayName("an edited constituent is exactly one manifest_stale naming its content_hash")
        void anEditedConstituentIsOneManifestStale(@TempDir Path temp) throws IOException {
            Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp);
            append(pack.resolve(SKILL), "# edited\n");

            assertThat(violations(pack))
                    .extracting(Reason::code, reason -> reason.location().file(), Reason::detail)
                    .containsExactly(tuple(
                            StorageCodes.MANIFEST_STALE,
                            "pack-manifest.yaml",
                            "pack-manifest.yaml: constituents{" + SKILL + "}.content_hash does not match the bytes of "
                                    + SKILL));
        }

        @Test
        @DisplayName("an edited preset and an edited constituent are two manifest_stale, the constituent first")
        void twoStaleFieldsAreTwoReasons(@TempDir Path temp) throws IOException {
            Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp);
            append(pack.resolve(PRESET), "# edited\n");
            append(pack.resolve(SKILL), "# edited\n");

            assertThat(violations(pack))
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    "pack-manifest.yaml: constituents{" + SKILL
                                            + "}.content_hash does not match the bytes of " + SKILL),
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    "pack-manifest.yaml: presets{" + PRESET
                                            + "}.content_hash does not match the bytes of " + PRESET));
        }

        @Test
        @DisplayName("a stale manifest_hash is one manifest_stale naming manifest_hash")
        void aStaleManifestHashIsOneManifestStale(@TempDir Path temp) throws IOException {
            Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp);
            Path manifest = pack.resolve("pack-manifest.yaml");
            String hash = Files.readString(manifest, StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> line.startsWith("manifest_hash: "))
                    .findFirst()
                    .orElseThrow();
            replace(manifest, hash, "manifest_hash: " + "0".repeat(64));

            assertThat(violations(pack))
                    .extracting(Reason::code, reason -> reason.location().pointer(), Reason::detail)
                    .containsExactly(tuple(
                            StorageCodes.MANIFEST_STALE,
                            "/manifest_hash",
                            "pack-manifest.yaml: manifest_hash does not match the manifest's own fields"));
        }

        @Test
        @DisplayName("a file membership names is not also stale; membership comes first")
        void aFileMembershipNamesIsNotAlsoStale(@TempDir Path temp) throws IOException {
            Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp);
            append(pack.resolve(SKILL), "# edited\n");
            replace(pack.resolve("pack-manifest.yaml"), "  id: project-triage\n", "  id: project-triaged\n");

            assertThat(violations(pack))
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(
                            tuple(
                                    StorageCodes.MANIFEST_MEMBERSHIP,
                                    "pack-manifest.yaml: lists constituent skill:project-triaged at " + SKILL
                                            + ", which the pack does not hold"),
                            tuple(
                                    StorageCodes.MANIFEST_MEMBERSHIP,
                                    "pack-manifest.yaml: leaves out constituent skill:project-triage at " + SKILL
                                            + ", which the pack holds"),
                            tuple(
                                    StorageCodes.MANIFEST_STALE,
                                    "pack-manifest.yaml: manifest_hash does not match the manifest's own fields"));
        }

        @Test
        @DisplayName("a stale hash is judged only when nothing else failed, as membership is")
        void aStaleHashWaitsForTheOtherFaults(@TempDir Path temp) throws IOException {
            Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp);
            append(pack.resolve(SKILL), "not_a_skill_key: true\n");

            assertThat(violations(pack))
                    .extracting(Reason::code)
                    .isNotEmpty()
                    .doesNotContain(StorageCodes.MANIFEST_STALE, StorageCodes.MANIFEST_MEMBERSHIP);
        }
    }
}
