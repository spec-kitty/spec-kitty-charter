package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
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
}
