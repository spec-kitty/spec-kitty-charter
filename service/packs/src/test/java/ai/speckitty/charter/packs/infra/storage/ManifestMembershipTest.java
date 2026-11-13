package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Refused;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #110 (ADR-0042 amendment of 2026-11-12, ADR-0074 decision 7): the manifest's membership is derived from the pack the
 * write holds, one constituent per artifact at its own file and one entry per preset, each list in path order; every
 * write restamps it, and generates it when the pack held none; a whole-pack registration whose uploaded lists differ
 * from the derived ones is refused, and so is a source manifest in the round-trip gate.
 */
@DisplayName("Manifest membership and restamping on every write (#110)")
class ManifestMembershipTest {

    private static final String PROJECT = ExamplePacks.PROJECT;
    private static final String STYLEGUIDE = "styleguides/reasons-canvas-writing.styleguide.yaml";
    private static final String STYLEGUIDE_ENTRY =
            "- kind: styleguide\n  id: reasons-canvas-writing\n  path: " + STYLEGUIDE + "\n";

    private static final String DIRECTIVE_ENTRY =
            "- kind: directive\n  id: PROJECT_001\n  path: directives/PROJECT_001.directive.yaml\n";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();
    private final WholePackStore store = new WholePackStore(strategy);

    // ---- the derivation ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the project pack's derived lists are its four artifact files and its preset, in path order")
    void theProjectPacksDerivedListsAreItsFiles() {
        Path pack = ExamplePacks.pack(PROJECT);
        WholePackStore.LoadedPack loaded = store.load(pack);
        PackManifestDso derived = ManifestMembership.derived(loaded.pack(), loaded.layout());

        assertThat(derived.constituents())
                .extracting(c -> c.kind() + ":" + c.id() + "@" + c.path())
                .containsExactly(
                        "agent_profile:project-reviewer@agent_profiles/project-reviewer.agent.yaml",
                        "directive:PROJECT_001@directives/PROJECT_001.directive.yaml",
                        "skill:project-triage@skills/project-triage.skill.yaml",
                        "styleguide:reasons-canvas-writing@" + STYLEGUIDE);
        assertThat(derived.presets())
                .extracting(p -> p.id() + "@" + p.path())
                .containsExactly("project@presets/project.yaml");
    }

    @Test
    @DisplayName("every example pack holds a manifest whose lists are the derived ones and whose hashes verify")
    void everyExamplePackHoldsAFreshDerivedManifest() {
        for (String name : ExamplePacks.names()) {
            Path pack = ExamplePacks.pack(name);
            WholePackStore.LoadedPack loaded = store.load(pack);

            assertThat(loaded.manifest()).as(name + " holds a manifest").isPresent();
            assertThat(ManifestMembership.mismatch(
                            loaded.manifest().get(), ManifestMembership.derived(loaded.pack(), loaded.layout())))
                    .as(name)
                    .isEmpty();
            assertThat(PackManifests.stale(loaded.manifest().get(), path -> bytesOf(pack.resolve(path))))
                    .as(name)
                    .isEmpty();
        }
    }

    // ---- the store's whole-pack write ----------------------------------------------------------------------------

    @Test
    @DisplayName(
            "the whole-pack write generates a manifest for a pack that held none, listing every artifact and preset")
    void theWholePackWriteGeneratesAManifest(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(PROJECT, host.resolve("source"));
        deleteManifest(pack);
        WholePackStore.LoadedPack loaded = store.load(pack);
        Path target = host.resolve("written").resolve(PROJECT);

        assertThat(loaded.manifest()).isEmpty();
        store.write(loaded, target);

        assertFreshAndDerived(target);
    }

    @Test
    @DisplayName("the whole-pack write derives the lists: an entry left out is listed again, a stray one is dropped")
    void theWholePackWriteDerivesTheLists(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(PROJECT, host.resolve("source"));
        WholePackStore.LoadedPack loaded = store.load(pack);
        PackManifestDso listed = loaded.manifest().orElseThrow();
        PackManifestDso skewed = new PackManifestDso(
                listed.schemaVersion(),
                listed.manifestHash(),
                List.of(
                        listed.constituents().getFirst(),
                        new PackManifestDso.Constituent("tactic", "ghost", "ghost.tactic.yaml", "0".repeat(64))),
                listed.presets());
        Path target = host.resolve("written").resolve(PROJECT);

        store.write(
                new WholePackStore.LoadedPack(
                        loaded.pack(), loaded.layout(), loaded.graphs(), loaded.unmodelled(), Optional.of(skewed)),
                target);

        assertFreshAndDerived(target);
        assertThat(manifestOf(target).constituents())
                .extracting(PackManifestDso.Constituent::id)
                .doesNotContain("ghost")
                .contains("reasons-canvas-writing");
    }

    // ---- the whole-pack registration -----------------------------------------------------------------------------

    @Test
    @DisplayName(
            "a registration refuses an uploaded manifest whose lists differ from the pack: unheld, repeated or left out, one violation per entry")
    void aRegistrationRefusesAManifestWhoseListsDifferFromThePack(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(PROJECT, host);
        editManifest(
                pack,
                text -> text.replace(STYLEGUIDE_ENTRY + contentHashLine(text, STYLEGUIDE), "")
                                .replace(
                                        "presets:\n",
                                        "- kind: tactic\n  id: ghost\n  path: tactics/ghost.tactic.yaml\n  content_hash: "
                                                + "0".repeat(64) + "\n" + DIRECTIVE_ENTRY
                                                + "  content_hash: " + "0".repeat(64) + "\npresets:\n")
                        + "- id: ghost\n  path: presets/ghost.yaml\n  content_hash: " + "0".repeat(64) + "\n");

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .extracting(RefusalViolation::pointer, RefusalViolation::code, RefusalViolation::detail)
                            .containsExactly(
                                    tuple(
                                            PackFileSet.MANIFEST,
                                            "manifest_membership",
                                            PackFileSet.MANIFEST
                                                    + ": lists constituent tactic:ghost at tactics/ghost.tactic.yaml,"
                                                    + " which the pack does not hold"),
                                    tuple(
                                            PackFileSet.MANIFEST,
                                            "manifest_membership",
                                            PackFileSet.MANIFEST
                                                    + ": lists preset ghost at presets/ghost.yaml, which the pack does"
                                                    + " not hold"),
                                    tuple(
                                            PackFileSet.MANIFEST,
                                            "manifest_membership",
                                            PackFileSet.MANIFEST
                                                    + ": lists constituent directive:PROJECT_001 at"
                                                    + " directives/PROJECT_001.directive.yaml twice"),
                                    tuple(
                                            PackFileSet.MANIFEST,
                                            "manifest_membership",
                                            PackFileSet.MANIFEST
                                                    + ": leaves out constituent styleguide:reasons-canvas-writing at "
                                                    + STYLEGUIDE + ", which the pack holds"));
                });
    }

    @Test
    @DisplayName(
            "a registration refuses a pack-manifest.yaml that is a directory, before the stamp could write over it")
    void aRegistrationRefusesAManifestThatIsNotAFile(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(PROJECT, host);
        deleteManifest(pack);
        Files.createDirectory(pack.resolve(PackFileSet.MANIFEST));

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .extracting(RefusalViolation::pointer, RefusalViolation::code)
                            .containsExactly(tuple(PackFileSet.MANIFEST, "unreadable"));
                });
    }

    @Test
    @DisplayName("the stamp refuses a pack-manifest.yaml that is a link, and leaves the link's target unchanged")
    void theStampRefusesAManifestThatIsALink(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(PROJECT, host.resolve("pack"));
        deleteManifest(pack);
        Path outside = host.resolve("outside.yaml");
        byte[] untouched = "outside: the pack\n".getBytes(StandardCharsets.UTF_8);
        Files.write(outside, untouched);
        try {
            Files.createSymbolicLink(pack.resolve(PackFileSet.MANIFEST), outside);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("symbolic links are not supported here: " + e);
        }

        assertThatThrownBy(() -> ManifestMembership.stamp(pack, store, strategy))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage()).contains("pack-manifest.yaml is not a regular file");
                });
        assertThat(Files.readAllBytes(outside)).isEqualTo(untouched);
    }

    @Test
    @DisplayName("the staged tree is restamped before its revision: generated when absent, derived and fresh")
    void theStagedTreeIsRestamped(@TempDir Path host) throws IOException {
        Path generated = ExamplePacks.copyOf(PROJECT, host.resolve("generated"));
        deleteManifest(generated);
        Path restamped = ExamplePacks.copyOf(PROJECT, host.resolve("restamped"));
        Files.writeString(
                restamped.resolve(STYLEGUIDE), Files.readString(restamped.resolve(STYLEGUIDE)) + "# edited\n");

        store.stampManifest(new StagedPack(generated));
        store.stampManifest(new StagedPack(restamped));

        assertFreshAndDerived(generated);
        assertFreshAndDerived(restamped);
    }

    // ---- the per-artifact PUT and DELETE -------------------------------------------------------------------------

    @Test
    @DisplayName("a PUT on a pack that held no manifest generates one listing the artifact it adds")
    void aPutGeneratesTheManifest(@TempDir Path host) {
        Path bare = ExamplePacks.copyOf(PROJECT, host);
        deleteManifest(bare);

        commitStore(bare).commit(new PackIdentifier(PROJECT), null, toolguide(), null);

        assertFreshAndDerived(bare);
        assertThat(manifestOf(bare).constituents())
                .extracting(c -> c.kind() + ":" + c.id())
                .contains("toolguide:ripgrep");
    }

    @Test
    @DisplayName("a PUT to a stored pack with an unreadable artifact file goes ahead, and the manifest lists what the"
            + " pack reads")
    void aPutToAPackWithAnUnreadableArtifactGoesAhead(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(PROJECT, host);
        Files.writeString(pack.resolve("directives/BROKEN.directive.yaml"), "id: BROKEN\nnot: [a directive\n");

        commitStore(pack).commit(new PackIdentifier(PROJECT), null, toolguide(), null);

        assertFreshAndDerived(pack);
        assertThat(manifestOf(pack).constituents())
                .extracting(PackManifestDso.Constituent::path)
                .contains("ripgrep.toolguide.yaml")
                .doesNotContain("directives/BROKEN.directive.yaml");
    }

    @Test
    @DisplayName("a commit that only rewrites the relationship shard still restamps and generates the manifest")
    void aShardOnlyCommitRestampsTheManifest(@TempDir Path host) {
        Path bare = ExamplePacks.copyOf(PROJECT, host);
        deleteManifest(bare);

        commitStore(bare)
                .commit(
                        new PackIdentifier(PROJECT),
                        null,
                        null,
                        "schema_version: '1.0'\ngenerated_at: '2026-01-01T00:00:00Z'\nnodes: []\nedges: []\n");

        assertFreshAndDerived(bare);
    }

    @Test
    @DisplayName("a DELETE drops the removed artifact's entry and restamps the manifest")
    void aDeleteDropsTheEntry(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(PROJECT, host);

        commitStore(pack).remove(new PackIdentifier(PROJECT), List.of(STYLEGUIDE), null, Map.of());

        assertFreshAndDerived(pack);
        assertThat(manifestOf(pack).constituents())
                .extracting(PackManifestDso.Constituent::path)
                .doesNotContain(STYLEGUIDE);
    }

    // ---- the gate ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the gate refuses a source manifest whose lists differ from the derived ones, with the same reason")
    void theGateRefusesAMembershipMismatch(@TempDir Path host) throws IOException {
        Path clean = ExamplePacks.copyOf(PROJECT, host.resolve("clean"));
        Path skewed = ExamplePacks.copyOf(PROJECT, host.resolve("skewed"));
        editManifest(skewed, text -> text.replace(STYLEGUIDE_ENTRY + contentHashLine(text, STYLEGUIDE), ""));

        assertThat(gate(clean, host.resolve("out/clean").resolve(PROJECT)))
                .isEqualTo(new Written(PackFileSet.MANIFEST));
        assertThat(gate(skewed, host.resolve("out/skewed").resolve(PROJECT)))
                .isEqualTo(new Refused("leaves out constituent styleguide:reasons-canvas-writing at " + STYLEGUIDE
                        + ", which the pack holds"));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private Outcome gate(Path pack, Path target) throws IOException {
        return new DomainRoundTrip(strategy)
                .roundTrip(pack, target, WholePackCensus.of(pack))
                .get(PackFileSet.MANIFEST);
    }

    /** The pack's manifest lists exactly the derived membership, and every derived field verifies. */
    private void assertFreshAndDerived(Path pack) {
        WholePackStore.LoadedPack loaded = store.load(pack);
        PackManifestDso manifest = loaded.manifest().orElseThrow(() -> new AssertionError(pack + " has no manifest"));
        assertThat(ManifestMembership.mismatch(manifest, ManifestMembership.derived(loaded.pack(), loaded.layout())))
                .as(pack + ": membership")
                .isEmpty();
        assertThat(PackManifests.stale(manifest, path -> bytesOf(pack.resolve(path))))
                .as(pack + ": derived fields")
                .isEmpty();
    }

    private PackManifestDso manifestOf(Path pack) {
        return store.load(pack).manifest().orElseThrow();
    }

    private PackCommitStore commitStore(Path pack) {
        Locations locations = () -> List.of(new PackLocation(PROJECT, PackLevel.PROJECT, pack));
        return new PackCommitStore(locations, strategy);
    }

    private static CharterArtifact toolguide() {
        return Toolguide.builder()
                .id(new ToolguideIdentifier("ripgrep"))
                .packId(new PackIdentifier(PROJECT))
                .schemaVersion("1.0")
                .tool("ripgrep")
                .title("Use ripgrep")
                .guide(Guide.of("How to use the tool."))
                .summary("How to search.")
                .build();
    }

    /** The {@code content_hash} line that follows the constituent entry at {@code path}. */
    private static String contentHashLine(String manifest, String path) {
        int at = manifest.indexOf("path: " + path + "\n");
        int start = at + ("path: " + path + "\n").length();
        return manifest.substring(start, manifest.indexOf('\n', start) + 1);
    }

    private static void editManifest(Path pack, java.util.function.UnaryOperator<String> edit) throws IOException {
        Path manifest = pack.resolve(PackFileSet.MANIFEST);
        String before = Files.readString(manifest, StandardCharsets.UTF_8);
        String after = edit.apply(before);
        assertThat(after).as("the edit changed the manifest").isNotEqualTo(before);
        Files.writeString(manifest, after, StandardCharsets.UTF_8);
    }

    private static void deleteManifest(Path pack) {
        try {
            Files.delete(pack.resolve(PackFileSet.MANIFEST));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<byte[]> bytesOf(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
