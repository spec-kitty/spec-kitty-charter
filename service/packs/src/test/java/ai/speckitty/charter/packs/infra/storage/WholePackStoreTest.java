package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BROKEN_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Whole-pack load and write (ADR-0037 decision 5)")
class WholePackStoreTest {

    private static final String DIRECTIVE_FILE = "directives/043-close-the-class.directive.yaml";
    private static final String NESTED_TACTIC = "tactics/analysis/gamma.tactic.yaml";
    private static final String SHARD = "tactic.graph.yaml";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    /** pack-a without its broken file, plus a directive whose file name is not its id, a nested tactic and DRG docs. */
    private static Path richPack(Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        Files.delete(pack.resolve(BROKEN_FILE));
        write(pack, DIRECTIVE_FILE, """
                id: DIRECTIVE_043
                schema_version: '1.0'
                title: Close the class
                intent: Fix the class of defect, not the instance.
                enforcement: required
                """);
        write(pack, NESTED_TACTIC, """
                id: gamma-tactic
                schema_version: '1.0'
                name: Gamma
                steps:
                  - title: Only step
                """);
        write(pack, SHARD, """
                schema_version: '1.0'
                generated_at: STATIC
                generated_by: drg-migration-v1
                nodes:
                - urn: tactic:gamma-tactic
                  kind: tactic
                  label: Gamma
                  provenance: hand-written
                  tags:
                  - analysis
                  - analysis
                - urn: directive:DIRECTIVE_043
                  kind: directive
                edges:
                - source: tactic:gamma-tactic
                  target: directive:DIRECTIVE_043
                  relation: requires
                  impacts: 0.5
                  when: always
                  reason: because
                """);
        write(pack, PackFileSet.FRAGMENT, """
                nodes:
                  - id: DIRECTIVE_043
                    kind: directives
                    title: Close the class
                    body_path: directives/043-close-the-class.directive.yaml
                edges:
                  - source: directive:DIRECTIVE_043
                    target: tactic:gamma-tactic
                    relation: suggests
                """);
        return pack;
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    /** What a loaded pack means: the descriptor, every artifact as its persistence shape by kind, the DRG documents. */
    private static Map<String, Object> meaning(WholePackStore.LoadedPack loaded) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("descriptor", loaded.pack().descriptor());
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            List<Object> dsos = new ArrayList<>();
            for (CharterArtifact artifact : loaded.pack().artifacts(kind.kind())) {
                dsos.add(kind.dsoOf(artifact));
            }
            out.put(kind.kind().token(), dsos);
        }
        out.put("graphs", loaded.graphs());
        return out;
    }

    private static List<String> filesUnder(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path)
                            .toString()
                            .replace(path.getFileSystem().getSeparator(), "/"))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> stagingLeftovers(Path parent) throws IOException {
        try (Stream<Path> list = Files.list(parent)) {
            return list.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(".pack-write-"))
                    .toList();
        }
    }

    @Test
    @DisplayName("the load holds every bound kind, both DRG shapes with every field, and where each artifact sat")
    void theLoadHoldsEverything(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(richPack(host));

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().id().value()).isEqualTo("01M5DEH8ROUNDTRIPFIXTURE0A");
        assertThat(loaded.pack().tactics()).hasSize(3);
        assertThat(loaded.pack().directives()).hasSize(1);
        assertThat(loaded.layout().paths()).containsEntry(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_043"), DIRECTIVE_FILE);
        assertThat(loaded.graphs()).extracting(GraphDocument::file).containsExactly(SHARD, PackFileSet.FRAGMENT);
        GraphDocument.Shard shard = (GraphDocument.Shard) loaded.graphs().getFirst();
        assertThat(shard.generatedBy()).contains("drg-migration-v1");
        assertThat(shard.nodes().getFirst().provenance()).contains("hand-written");
        assertThat(shard.nodes().getFirst().tags()).containsExactly("analysis", "analysis");
        assertThat(shard.edges().getFirst().impacts()).contains(0.5);
        GraphDocument.Fragment fragment =
                (GraphDocument.Fragment) loaded.graphs().get(1);
        assertThat(fragment.nodes().getFirst().bodyPath()).contains(DIRECTIVE_FILE);
    }

    @Test
    @DisplayName("a pack written to a fresh directory reloads with the same meaning, each artifact at its own path")
    void aWrittenPackReloadsWithTheSameMeaning(@TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out").resolve(source.getFileName().toString());

        store.write(loaded, target);
        WholePackStore.LoadedPack reloaded = store.load(target);

        assertThat(meaning(reloaded)).isEqualTo(meaning(loaded));
        assertThat(reloaded.layout().paths()).isEqualTo(loaded.layout().paths());
        assertThat(filesUnder(target))
                .containsExactly(
                        PackFixtures.ALPHA_FILE,
                        PackFixtures.BETA_FILE,
                        DIRECTIVE_FILE,
                        PackFileSet.FRAGMENT,
                        PackFileSet.PACK_YAML,
                        SHARD,
                        NESTED_TACTIC);
        assertThat(stagingLeftovers(target.getParent())).isEmpty();
    }

    @Test
    @DisplayName("an artifact with no recorded place is written where a registration would create it")
    void anUnplacedArtifactGoesToItsCanonicalPath(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(richPack(host.resolve("source")));
        Path target = host.resolve("out");

        store.write(
                new WholePackStore.LoadedPack(
                        loaded.pack(), PackLayout.canonical(), loaded.graphs(), loaded.unmodelled()),
                target);

        assertThat(filesUnder(target))
                .contains("DIRECTIVE_043.directive.yaml", "gamma-tactic.tactic.yaml", "alpha-tactic.tactic.yaml");
        assertThat(meaning(store.load(target))).isEqualTo(meaning(loaded));
    }

    @Test
    @DisplayName("a target that exists is refused and left as it was")
    void anExistingTargetIsRefused(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(richPack(host.resolve("source")));
        Path target = Files.createDirectories(host.resolve("out"));
        Files.writeString(target.resolve("keep.txt"), "mine");

        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOf(CharterRefusal.class)
                .extracting(refusal -> ((CharterRefusal) refusal).code())
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(filesUnder(target)).containsExactly("keep.txt");
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    @Test
    @DisplayName("a pack with read violations is refused and nothing is created")
    void aPackWithViolationsIsRefused(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(PackFixtures.copyOf("pack-a", host.resolve("source")));
        Path target = host.resolve("out");

        assertThat(loaded.pack().violations()).isNotEmpty();
        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOf(CharterRefusal.class)
                .extracting(refusal -> ((CharterRefusal) refusal).code())
                .isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(target).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    @Test
    @DisplayName("a path that leaves the pack aborts the write and leaves no target and no staging directory")
    void anEscapingPathLeavesNothingBehind(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(richPack(host.resolve("source")));
        CharterArtifact directive =
                loaded.pack().artifacts(ArtifactKind.DIRECTIVE).getFirst();
        Map<Urn, String> places = new LinkedHashMap<>(loaded.layout().paths());
        places.put(directive.urn(), "../escaped.directive.yaml");
        Path parent = Files.createDirectories(host.resolve("out"));
        Path target = parent.resolve("pack");

        assertThatThrownBy(() -> store.write(
                        new WholePackStore.LoadedPack(
                                loaded.pack(), new PackLayout(places), loaded.graphs(), List.of()),
                        target))
                .isInstanceOf(CharterRefusal.class);
        assertThat(target).doesNotExist();
        assertThat(parent.resolve("escaped.directive.yaml")).doesNotExist();
        assertThat(stagingLeftovers(parent)).isEmpty();
    }

    @Test
    @DisplayName("two parts written to one path abort the write")
    void twoPartsAtOnePathAreRefused(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(richPack(host.resolve("source")));
        Map<Urn, String> places = new LinkedHashMap<>(loaded.layout().paths());
        places.put(loaded.pack().directives().getFirst().urn(), SHARD);
        Path target = host.resolve("out");

        assertThatThrownBy(() -> store.write(
                        new WholePackStore.LoadedPack(
                                loaded.pack(), new PackLayout(places), loaded.graphs(), List.of()),
                        target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("two parts");
        assertThat(target).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    @Test
    @DisplayName("a pack without pack.yaml is refused on load")
    void aPackWithoutADescriptorIsRefused(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        Files.delete(pack.resolve(PackFileSet.PACK_YAML));

        assertThatThrownBy(() -> store.load(pack))
                .isInstanceOf(CharterRefusal.class)
                .extracting(refusal -> ((CharterRefusal) refusal).code())
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
    }

    @Test
    @DisplayName("an unreadable DRG document refuses the pack as drg_schema_error (ADR-0036 decision 3)")
    void anUnreadableGraphRefusesThePack(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        Files.writeString(pack.resolve("broken.graph.yaml"), "schema_version: '1.0'\nsurprise: 1\n");

        assertThatThrownBy(() -> store.load(pack))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("broken.graph.yaml")
                .extracting(refusal -> ((CharterRefusal) refusal).code())
                .isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
    }

    @Test
    @DisplayName("every file nothing models is listed on load and is not written")
    void unmodelledFilesAreListedNotCopied(@TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        write(source, "README.md", "# hello\n");
        write(source, "sub/r.yaml", "a: 1\n");
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        store.write(loaded, target);

        assertThat(loaded.unmodelled()).containsExactly("README.md", "sub/r.yaml");
        assertThat(filesUnder(target)).doesNotContain("README.md", "sub/r.yaml");
    }

    @Test
    @DisplayName("the target may have another name than the source; the identity is the declared pack_id")
    void theTargetNameIsFree(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(richPack(host.resolve("source")));
        Path target = host.resolve("renamed");

        store.write(loaded, target);

        assertThat(store.load(target).pack().id()).isEqualTo(loaded.pack().id());
        assertThat(meaning(store.load(target))).isEqualTo(meaning(loaded));
    }

    @Test
    @DisplayName("a descriptor with undeclared keys is refused, since their values are not kept")
    void undeclaredDescriptorKeysAreRefused(@TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        Files.writeString(
                source.resolve(PackFileSet.PACK_YAML),
                "surprise: kept-nowhere\n",
                java.nio.file.StandardOpenOption.APPEND);
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        assertThat(loaded.pack().descriptor().undeclaredKeys()).contains("surprise");
        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("undeclared keys");
        assertThat(target).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    @Test
    @DisplayName("two files declaring one id are a refusal on load, not a raw invariant breach")
    void aDuplicateIdIsARefusal(@TempDir Path host) throws IOException {
        Path source = richPack(host);
        Files.copy(source.resolve(NESTED_TACTIC), source.resolve("tactics/again.tactic.yaml"));

        assertThatThrownBy(() -> store.load(source))
                .isInstanceOf(CharterRefusal.class)
                .extracting(refusal -> ((CharterRefusal) refusal).code())
                .isEqualTo(RefusalCode.VALIDATION_FAILED);
    }
}
