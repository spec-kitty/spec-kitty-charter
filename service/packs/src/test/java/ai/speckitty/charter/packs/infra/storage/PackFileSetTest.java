package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One small, fixed tree that holds a file of every verdict class, read through {@link PackFileSet}. */
class PackFileSetTest {

    private static final List<String> TREE = List.of(
            "pack.yaml",
            "b.tactic.yaml",
            "a.tactic.yaml",
            ".hidden.tactic.yaml",
            ".h/in-hidden.tactic.yaml",
            "presets/p.tactic.yaml",
            "x.config.tactic.yaml",
            "deep/er/c.directive.yaml",
            "d.styleguide.yaml",
            "e.paradigm.yaml",
            "x.tactic.yml",
            "x.tactic.YAML",
            "x.TACTIC.yaml",
            "x.tactic.yaml.bak",
            "x.tactic.config.yaml",
            "notes.yaml",
            "f.toolguide.yaml",
            "org-charter.yaml",
            "sub/pack.yaml",
            "sub/y.graph.yaml",
            "sub/drg/fragment.yaml",
            "b.graph.yaml",
            ".a.graph.yaml",
            "drg/fragment.yaml");

    private Path pack;

    @BeforeEach
    void aPackWithAFileOfEveryVerdictClass(@TempDir Path host) throws IOException {
        pack = Files.createDirectories(host.resolve("qa"));
        for (String file : TREE) {
            Files.createDirectories(pack.resolve(file).getParent());
            Files.writeString(pack.resolve(file), "a: 1\n");
        }
        Files.createDirectories(pack.resolve("d.tactic.yaml"));
    }

    @Test
    void theFilesOfAKindAreTheOnesItsNameShapeSelectsInStringOrder() {
        assertThat(PackFileSet.files(pack, ArtifactKind.TACTIC))
                .containsExactly(
                        ".h/in-hidden.tactic.yaml",
                        ".hidden.tactic.yaml",
                        "a.tactic.yaml",
                        "b.tactic.yaml",
                        "presets/p.tactic.yaml",
                        "x.config.tactic.yaml");
        assertThat(PackFileSet.files(pack, ArtifactKind.DIRECTIVE)).containsExactly("deep/er/c.directive.yaml");
        assertThat(PackFileSet.files(pack, ArtifactKind.STYLEGUIDE)).containsExactly("d.styleguide.yaml");
        assertThat(PackFileSet.files(pack, ArtifactKind.TOOLGUIDE)).containsExactly("f.toolguide.yaml");
        assertThat(PackFileSet.files(pack, ArtifactKind.PARADIGM)).containsExactly("e.paradigm.yaml");
    }

    @Test
    void theDigestedFilesAreThePackYamlTheKindsInDeclarationOrderAndTheGraphInputs() {
        assertThat(PackFileSet.digested(pack))
                .containsExactly(
                        "pack.yaml",
                        ".h/in-hidden.tactic.yaml",
                        ".hidden.tactic.yaml",
                        "a.tactic.yaml",
                        "b.tactic.yaml",
                        "presets/p.tactic.yaml",
                        "x.config.tactic.yaml",
                        "deep/er/c.directive.yaml",
                        "d.styleguide.yaml",
                        "f.toolguide.yaml",
                        "e.paradigm.yaml",
                        ".a.graph.yaml",
                        "b.graph.yaml",
                        "drg/fragment.yaml");
    }

    /**
     * NFR-002: a pack without asset files or templates keeps its exact fingerprint. The value was captured over this
     * tree with the store as it was before asset files and templates were read (lane commit 57b49e2).
     */
    @Test
    void aPackWithoutPayloadsKeepsTheFingerprintItHadBeforePayloadsWereDigested() {
        assertThat(PackFileSet.assetFiles(pack)).isEmpty();
        assertThat(PackFileSet.templates(pack)).isEmpty();
        assertThat(new FileSystemPackSources(() -> List.of(new PackLocation("qa", PackLevel.ORG, pack))).fingerprint())
                .isEqualTo("sha256:02c8a8cdbc2e6458ec35c1b9fb34a2110d814b2503cf1f030b2851592c395125");
    }

    /**
     * A pack holding every payload shape: the sidecar is listed with the YAML kinds, the asset files and then the
     * template come last, after the fragment; the README under {@code assets/} and the mission template are neither.
     */
    @Test
    void assetFilesAndTemplatesAreDigestedLastAfterTheFragment(@TempDir Path host) throws IOException {
        Path withPayloads = Files.createDirectories(host.resolve("payloads/qa"));
        for (String file : List.of(
                "pack.yaml",
                "a.tactic.yaml",
                "drg/fragment.yaml",
                "assets/logo.png.asset.yaml",
                "assets/logo.png",
                "assets/a/b/nested.bin",
                "assets/lint.config.yaml",
                "assets/README.md",
                "assets/a/README.md",
                "templates/writing/minutes.md",
                "missions/m/templates/t.md",
                "docs/logo.png")) {
            Files.createDirectories(withPayloads.resolve(file).getParent());
            Files.write(withPayloads.resolve(file), new byte[] {0, '\r', '\n', (byte) 0xff});
        }

        assertThat(PackFileSet.files(withPayloads, ArtifactKind.ASSET)).containsExactly("assets/logo.png.asset.yaml");
        assertThat(PackFileSet.assetFiles(withPayloads))
                .containsExactly("assets/a/b/nested.bin", "assets/lint.config.yaml", "assets/logo.png");
        assertThat(PackFileSet.templates(withPayloads)).containsExactly("templates/writing/minutes.md");
        assertThat(PackFileSet.digested(withPayloads))
                .containsExactly(
                        "pack.yaml",
                        "a.tactic.yaml",
                        "assets/logo.png.asset.yaml",
                        "drg/fragment.yaml",
                        "assets/a/b/nested.bin",
                        "assets/lint.config.yaml",
                        "assets/logo.png",
                        "templates/writing/minutes.md");
    }

    @Test
    void aSubsetOfTheKindsLeavesTheOthersOutButKeepsThePackYamlAndTheGraphInputs() {
        assertThat(PackFileSet.digested(pack, EnumSet.of(ArtifactKind.PARADIGM, ArtifactKind.DIRECTIVE)))
                .containsExactly(
                        "pack.yaml",
                        "deep/er/c.directive.yaml",
                        "e.paradigm.yaml",
                        ".a.graph.yaml",
                        "b.graph.yaml",
                        "drg/fragment.yaml");
        assertThat(PackFileSet.digested(pack, EnumSet.noneOf(ArtifactKind.class)))
                .containsExactly("pack.yaml", ".a.graph.yaml", "b.graph.yaml", "drg/fragment.yaml");
    }

    @Test
    void aPackWithoutLoadedKindFilesDigestsPackYamlTheShardsByNameAndTheFragmentAsBefore(@TempDir Path host)
            throws IOException {
        Path shardOnly = Files.createDirectories(host.resolve("shards/qa"));
        for (String file : List.of("pack.yaml", "z.graph.yaml", "a.graph.yaml", "drg/fragment.yaml", "notes.yaml")) {
            Files.createDirectories(shardOnly.resolve(file).getParent());
            Files.writeString(shardOnly.resolve(file), "a: 1\n");
        }

        assertThat(PackFileSet.digested(shardOnly))
                .containsExactly("pack.yaml", "a.graph.yaml", "z.graph.yaml", "drg/fragment.yaml");
    }

    @Test
    void aDirectoryRootWrittenWithADotOrAClimbClassifiesLikeTheNormalizedOne() {
        List<String> expected = PackFileSet.digested(pack);

        assertThat(expected).contains("presets/p.tactic.yaml");
        assertThat(PackFileSet.digested(pack.resolve("."))).isEqualTo(expected);
        assertThat(PackFileSet.digested(pack.resolve("deep").resolve(".."))).isEqualTo(expected);
        assertThat(PackFileSet.files(pack.resolve("."), ArtifactKind.TACTIC))
                .isEqualTo(PackFileSet.files(pack, ArtifactKind.TACTIC));
    }

    @Test
    void aPackYamlThatIsNotAFileIsNotDigested() throws IOException {
        Files.delete(pack.resolve("pack.yaml"));
        assertThat(PackFileSet.digested(pack)).doesNotContain("pack.yaml");

        Files.createDirectory(pack.resolve("pack.yaml"));
        assertThat(PackFileSet.digested(pack)).doesNotContain("pack.yaml");
    }

    @Test
    void aDirectoryThatCannotBeListedIsRefusedAsUnreadable(@TempDir Path host) {
        Path missing = host.resolve("missing");

        assertThatThrownBy(() -> PackFileSet.files(missing, ArtifactKind.TACTIC))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage()).isEqualTo("pack missing cannot be listed");
                });
    }

    @Test
    void aDirectoryThatCannotBeListedIsRefusedUnderTheConfiguredNameWhenOneIsGiven(@TempDir Path host) {
        Path missing = host.resolve("directory-name");

        assertThatThrownBy(() -> PackFileSet.digested(missing, "configured-name"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage()).isEqualTo("pack configured-name cannot be listed");
                });
    }

    @Test
    void everyLoadedKindHasItsOwnTokenAndAFileNameCarriesIt() {
        for (ArtifactKind kind : KindBindings.kinds()) {
            assertThat(kind.token()).isEqualTo(kind.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /**
     * F1: the rule's content form decides the store. On one pack, a table whose payload rule under {@code assets/}
     * declares {@code bytes} holds the file as an asset file (the positive control); the same rule declaring
     * {@code yaml}, with the same kind token and path, would hold it nowhere, so the table refuses to load (#193)
     * rather than drop the file silently.
     */
    @Test
    void theContentFormNotTheKindTokenDecidesWhetherAFileIsHeldAsBytes() throws IOException {
        Files.createDirectories(pack.resolve("assets"));
        Files.writeString(pack.resolve("assets/x.bin"), "x");
        String table = """
                node_kinds: []
                fragment_kinds: {}
                files: []
                exclusions: []
                payloads: [{match: '*/assets/**/*', kind: asset-payload, content_form: %s}]
                """;

        PackFileSet.Listing bytes = PackFileSet.listing(pack, new PackFileKind(table.formatted("bytes")));

        assertThat(bytes.assetFiles()).containsExactly("assets/x.bin");
        assertThatThrownBy(() -> new PackFileKind(table.formatted("yaml")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the payloads rule for '*/assets/**/*' declares content_form 'yaml'");
    }
}
