package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BROKEN_FILE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Whole-pack load and write (ADR-0037 decision 5)")
class WholePackStoreTest {

    private static final String DIRECTIVE_FILE = "directives/043-close-the-class.directive.yaml";
    private static final String NESTED_TACTIC = "tactics/analysis/gamma.tactic.yaml";
    private static final String SHARD = "tactic.graph.yaml";
    private static final String SKILL_FILE = "skills/land-pr.skill.yaml";
    private static final String SKILL_BODY = "skills/land-pr.skill.md";
    private static final String SKILL = """
            schema_version: "1.0"
            id: land-pr
            title: Land a PR
            description: Use when a pull request needs landing.
            form: prompt
            body_path: land-pr.skill.md
            invocation:
              user_invocable: true
              side_effects: [git-push]
            """;

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
        out.put("fragments", PackFixtures.fragments(loaded));
        out.put("assetFiles", loaded.pack().assetFiles());
        out.put(
                "templates",
                loaded.pack().templates().stream()
                        .map(template ->
                                template.id().value() + "=" + template.content().sha256())
                        .toList());
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
        assertThat(loaded.pack().id().value()).isEqualTo("01M5DEH8R0VNDTR1PF1XTVRE0A");
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
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
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
    @DisplayName(
            "a pack whose root is a link lists the unmodelled files of its target; links below it are not followed")
    void aLinkedRootListsItsUnmodelledFiles(@TempDir Path host) throws IOException {
        Path real = richPack(host.resolve("checkout"));
        write(real, "README.md", "# hello\n");
        write(real, "sub/r.yaml", "a: 1\n");
        Path outside = Files.createDirectories(host.resolve("outside"));
        write(outside, "beyond.md", "# not the pack's\n");
        SymbolicLinks.create(real.resolve("below"), outside);
        Path link = SymbolicLinks.create(
                Files.createDirectories(host.resolve("packs")).resolve("pack-a"), real);

        WholePackStore.LoadedPack loaded = store.load(link);

        assertThat(loaded.unmodelled())
                .as("the link's target walked from its real path (#284), the link below the root not entered")
                .containsExactly("README.md", "sub/r.yaml")
                .isEqualTo(store.load(real).unmodelled());
    }

    @Test
    @DisplayName("a skill and its body are loaded, modelled and written as a pair, the body byte for byte")
    void aSkillIsWrittenWithItsBody(@TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        write(source, SKILL_FILE, SKILL);
        write(source, SKILL_BODY, "Goal: land it.\r\nNo final newline — kept");
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        store.write(loaded, target);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.unmodelled()).doesNotContain(SKILL_BODY);
        assertThat(Files.readString(target.resolve(SKILL_BODY))).isEqualTo("Goal: land it.\r\nNo final newline — kept");
        assertThat(meaning(store.load(target))).isEqualTo(meaning(loaded));
    }

    @Test
    @DisplayName("a skill whose body is missing is a violation, and the write refuses the pack")
    void aMissingBodyRefusesTheWrite(@TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        write(source, SKILL_FILE, SKILL);
        WholePackStore.LoadedPack loaded = store.load(source);

        assertThat(loaded.pack().violations())
                .extracting(v -> v.file() + ": " + v.reason())
                .containsExactly(SKILL_FILE + ": land-pr.skill.md: missing");
        assertThatThrownBy(() -> store.write(loaded, host.resolve("out")))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("unreadable files");
    }

    @Test
    @DisplayName("a companion the rule refuses refuses the write, and nothing is left behind")
    void aRefusedCompanionRefusesTheWrite(@TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        write(source, SKILL_FILE, SKILL);
        write(source, SKILL_BODY, "body\n");
        WholePackStore.LoadedPack loaded = store.load(source);
        SkillDso escaping = SkillBinding.INSTANCE
                .toDso(loaded.pack().artifacts(ArtifactKind.SKILL, Skill.class).getFirst())
                .withBody(Optional.of("body\n"));
        SkillDso moved = new SkillDso(
                escaping.schemaVersion(),
                escaping.id(),
                escaping.title(),
                escaping.description(),
                escaping.triggers(),
                escaping.form(),
                Optional.of("../../escape.md"),
                escaping.expandsTo(),
                escaping.parameters(),
                escaping.invocation(),
                escaping.tools(),
                escaping.version(),
                escaping.maintainers(),
                escaping.overrides(),
                escaping.enhances(),
                escaping.body());
        WholePackStore.LoadedPack tampered = new WholePackStore.LoadedPack(
                loaded.pack().with(SkillBinding.INSTANCE.toEntity(loaded.pack().id(), moved)),
                loaded.layout(),
                loaded.graphs(),
                loaded.unmodelled());
        Path target = host.resolve("out");

        assertThatThrownBy(() -> store.write(tampered, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("companion ../../escape.md leaves the pack");
        assertThat(target).doesNotExist();
        assertThat(host.resolve("escape.md")).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
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
    @DisplayName("a descriptor with an undeclared key does not load, so its value can never be dropped on write")
    void undeclaredDescriptorKeysAreRefusedOnLoad(@TempDir Path host) throws IOException {
        Path clean = richPack(host.resolve("clean"));
        Path source = richPack(host.resolve("source"));
        Files.writeString(
                source.resolve(PackFileSet.PACK_YAML),
                "surprise: kept-nowhere\n",
                java.nio.file.StandardOpenOption.APPEND);

        assertThat(store.load(clean).pack().violations()).isEmpty();
        assertThatThrownBy(() -> store.load(source))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("pack.yaml of pack-a cannot be read");
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

    // ---- asset files and templates, byte for byte (T022) -------------------------------------------------------

    private static final byte[] OTF = {'O', 'T', 'T', 'O', 0, 0, '\r', '\n', (byte) 0xff, '\r', '\n', 'z'};

    private static void write(Path pack, String relative, byte[] bytes) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /** The rich pack plus nested binary payloads with their sidecars, a sidecar-less payload, READMEs and templates. */
    private static Path payloadPack(Path host) throws IOException {
        Path pack = richPack(host);
        write(pack, "assets/debrief/fonts/Bold.otf.asset.yaml", """
                id: font-bold
                mime: font/otf
                path: debrief/fonts/Bold.otf
                """);
        write(pack, "assets/debrief/fonts/Bold.otf", OTF);
        write(pack, "assets/debrief/logo.png.asset.yaml", """
                id: logo
                mime: image/png
                path: debrief/logo.png
                title: Logo
                """);
        write(pack, "assets/debrief/logo.png", AssetStoreTest.BINARY);
        write(pack, "assets/lint.config.yaml", "rules:\n  - a\n");
        write(pack, "assets/README.md", "# assets\n");
        write(pack, "assets/debrief/README.md", "# debrief\n");
        write(pack, "assets/debrief/.DS_Store", OTF);
        write(pack, "templates/.gitkeep", new byte[0]);
        write(pack, "templates/writing/meeting-minutes.md", "---\r\ntitle: M\r\n---\r\nbody".getBytes(UTF_8));
        write(pack, "templates/empty.md", new byte[0]);
        write(pack, "missions/plan/templates/spec.md", "# mission template\n");
        return pack;
    }

    @Test
    @DisplayName("asset files, a sidecar-less payload and templates write and reload with identical bytes and meaning")
    void payloadsRoundTripByteForByte(@TempDir Path host) throws IOException {
        Path source = payloadPack(host.resolve("source"));
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        store.write(loaded, target);
        WholePackStore.LoadedPack reloaded = store.load(target);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().assets()).hasSize(2);
        assertThat(loaded.pack().assetFiles()).hasSize(3);
        assertThat(loaded.pack().templates())
                .as("two top-level templates and a mission type's (#194)")
                .hasSize(3);
        assertThat(meaning(reloaded)).isEqualTo(meaning(loaded));
        for (String file : List.of(
                "assets/debrief/fonts/Bold.otf",
                "assets/debrief/logo.png",
                "assets/lint.config.yaml",
                "templates/writing/meeting-minutes.md",
                "templates/empty.md")) {
            assertThat(AssetStoreTest.sha256(target.resolve(file)))
                    .as(file)
                    .isEqualTo(AssetStoreTest.sha256(source.resolve(file)));
        }
        assertThat(Files.size(target.resolve("templates/empty.md"))).isZero();
        assertThat(loaded.layout().paths())
                .containsEntry(
                        new Urn(NodeKind.TEMPLATE, "writing/meeting-minutes.md"),
                        "templates/writing/meeting-minutes.md")
                .containsEntry(new Urn(NodeKind.ASSET, "logo"), "assets/debrief/logo.png.asset.yaml");
    }

    @Test
    @DisplayName("of the files under assets/ and templates/, only the READMEs and the hidden files are unmodelled")
    void onlyTheReadmesAndHiddenFilesUnderAssetsAndTemplatesAreUnmodelled(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(payloadPack(host));

        assertThat(loaded.unmodelled())
                .containsExactly(
                        "assets/README.md",
                        "assets/debrief/.DS_Store",
                        "assets/debrief/README.md",
                        "templates/.gitkeep");
    }

    @Test
    @DisplayName("two asset files whose paths differ only by case refuse the write and leave no target")
    void assetFilesThatFoldToOnePathAreRefused(@TempDir Path host) throws IOException {
        Path source = payloadPack(host.resolve("source"));
        write(source, "assets/A.png", OTF);
        write(source, "assets/a.png", OTF);
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("two parts");
        assertThat(target).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    @Test
    @DisplayName("two templates whose paths differ only by case refuse the write and leave no target")
    void templatesThatFoldToOnePathAreRefused(@TempDir Path host) throws IOException {
        Path source = payloadPack(host.resolve("source"));
        write(source, "templates/x/A.md", OTF);
        write(source, "templates/x/a.md", OTF);
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("two parts");
        assertThat(target).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    @Test
    @DisplayName("a sidecar whose payload is missing is a violation, so the pack is not written as if whole")
    void aMissingPayloadRefusesTheWrite(@TempDir Path host) throws IOException {
        Path source = payloadPack(host.resolve("source"));
        Files.delete(source.resolve("assets/debrief/logo.png"));
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        assertThat(loaded.pack().violations())
                .containsExactly(new PackElementViolation(
                        "assets/debrief/logo.png.asset.yaml",
                        "asset",
                        "payload assets/debrief/logo.png does not exist"));
        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
        assertThat(target).doesNotExist();
    }

    /**
     * One file has one owner (ADR-0039 decision 3, #260): a skill whose body names the pack's {@code pack.md} is refused
     * as a {@code companion} violation, and the file stays the pack document; the same skill naming its own body loads.
     */
    @Test
    @DisplayName("a companion that is the pack document is refused; pack.md keeps its one owner")
    void aCompanionThatIsThePackDocumentIsRefused(@TempDir Path host) throws IOException {
        Path clean = richPack(host.resolve("clean"));
        write(clean, PackDocumentMarkdown.FILE, "# P\n\nThe pack.\n");
        Path claimed = richPack(host.resolve("claimed"));
        write(claimed, SKILL_FILE, SKILL.replace("body_path: land-pr.skill.md", "body_path: ../pack.md"));
        write(claimed, PackDocumentMarkdown.FILE, "# P\n\nThe pack.\n");

        assertThat(store.load(clean).pack().violations()).isEmpty();
        WholePackStore.LoadedPack loaded = store.load(claimed);
        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(SKILL_FILE);
            assertThat(violation.kind()).isEqualTo("companion");
            assertThat(violation.reason()).startsWith("../pack.md: is a pack file of kind pack-document");
        });
        assertThat(loaded.pack().artifacts(ArtifactKind.SKILL)).isEmpty();
        assertThat(loaded.pack().document()).isPresent();
        assertThat(PackFileSet.digested(claimed)).containsOnlyOnce(PackDocumentMarkdown.FILE);
    }

    /**
     * #272: the load classifies a companion path case-folded, as the write's claim check does, so a name that differs
     * from a ruled file only in case is refused at load like the exact name. On a case-insensitive file system
     * {@code PACK.md} reads the bytes of {@code pack.md}; the load must not admit what the write then refuses as two
     * parts written to one file. The refusal is by name, so it holds on any file system.
     */
    @ParameterizedTest(name = "body_path {0}")
    @ValueSource(strings = {"../PACK.md", "../Pack.md", "../pack.MD"})
    @DisplayName("a companion that names the pack document in another letter case is refused at load")
    void aCompanionThatIsThePackDocumentInAnotherCaseIsRefused(String bodyPath, @TempDir Path host) throws IOException {
        Path claimed = richPack(host.resolve("claimed"));
        write(claimed, SKILL_FILE, SKILL.replace("body_path: land-pr.skill.md", "body_path: " + bodyPath));
        write(claimed, PackDocumentMarkdown.FILE, "# P\n\nThe pack.\n");

        WholePackStore.LoadedPack loaded = store.load(claimed);

        assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(SKILL_FILE);
            assertThat(violation.kind()).isEqualTo("companion");
            assertThat(violation.reason())
                    .isEqualTo(bodyPath + ": is a pack file of kind pack-document (file-kinds rule '*/pack.md'), not a"
                            + " companion");
        });
        assertThat(loaded.pack().artifacts(ArtifactKind.SKILL)).isEmpty();
        assertThat(loaded.pack().document()).isPresent();
    }

    /**
     * One file has one owner (ADR-0039 decision 3): a skill whose body names a file the {@code payloads} rules classify
     * (a template, an asset file) is a pack inconsistency. The file stays with the rule that classifies it; the skill
     * is refused as a {@code companion} violation, so the pack is never written as if whole, and the fingerprint lists
     * the file once.
     */
    @ParameterizedTest
    @ValueSource(strings = {"templates/body.md", "assets/body.md"})
    @DisplayName("a companion that is a template or an asset file is refused; the file keeps its one owner")
    void aCompanionThatIsATemplateOrAnAssetFileIsRefused(String claimed, @TempDir Path host) throws IOException {
        Path source = richPack(host.resolve("source"));
        write(source, SKILL_FILE, SKILL.replace("body_path: land-pr.skill.md", "body_path: ../" + claimed));
        write(source, claimed, "body\n");
        WholePackStore.LoadedPack loaded = store.load(source);
        Path target = host.resolve("out");

        assertThat(loaded.pack().violations())
                .containsExactly(new PackElementViolation(
                        SKILL_FILE,
                        "companion",
                        "../" + claimed + ": is a pack file of kind "
                                + (claimed.startsWith("templates/") ? "template" : "asset-payload")
                                + ", not a companion"));
        assertThat(loaded.pack().artifacts(ArtifactKind.SKILL)).isEmpty();
        assertThat(Stream.concat(
                        loaded.pack().templates().stream().map(t -> "templates/" + t.id().value()),
                        loaded.pack().assetFiles().stream().map(f -> "assets/" + f.path())))
                .containsExactly(claimed);
        assertThat(PackFileSet.digested(source)).containsOnlyOnce(claimed).endsWith(claimed);
        assertThatThrownBy(() -> store.write(loaded, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("unreadable files");
        assertThat(target).doesNotExist();
    }

    /**
     * F7: one file, one owner. A pack holding a template whose path another rule classifies ({@code templates/
     * x.tactic.yaml}) is refused by the whole-pack write, naming the path, and no target remains; the same pack with a
     * template whose path only the template rule classifies is written.
     */
    @Test
    @DisplayName("a template whose path another rule classifies refuses the whole-pack write")
    void aTemplateWhosePathAnotherRuleClassifiesRefusesTheWrite(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(payloadPack(host.resolve("source")));
        Payload content = Payload.of("x".getBytes(UTF_8));

        store.write(
                with(loaded, TemplateBinding.INSTANCE.toEntity(loaded.pack().id(), "x.md", content)),
                host.resolve("ok"));
        WholePackStore.LoadedPack claimed =
                with(loaded, TemplateBinding.INSTANCE.toEntity(loaded.pack().id(), "x.tactic.yaml", content));

        assertThat(host.resolve("ok/templates/x.md")).hasContent("x");
        assertThatThrownBy(() -> store.write(claimed, host.resolve("out")))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("path templates/x.tactic.yaml is a pack file of kind tactic, not a template");
        assertThat(host.resolve("out")).doesNotExist();
        assertThat(stagingLeftovers(host)).isEmpty();
    }

    // ---- the staged load of a registration (IC-04 step 2) -----------------------------------------------------------

    @Test
    @DisplayName(
            "loadStaged digests each artifact as it would be written, so a changed artifact is told from a kept one")
    void loadStagedDigestsEachArtifactAsItWouldBeWritten(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        Urn alpha = new Urn(NodeKind.TACTIC, "alpha-tactic");
        Urn gamma = new Urn(NodeKind.TACTIC, "gamma-tactic");
        WholePackWriteStore.Contents before = store.loadStaged(new StagedPack(pack));
        write(
                pack,
                PackFixtures.ALPHA_FILE,
                Files.readString(pack.resolve(PackFixtures.ALPHA_FILE), UTF_8)
                        .replace("Alpha tactic", "Alpha, changed"));

        WholePackWriteStore.Contents after = store.loadStaged(new StagedPack(pack));

        assertThat(before.digests()).containsKeys(alpha, gamma);
        assertThat(after.digests().get(alpha)).isNotEqualTo(before.digests().get(alpha));
        assertThat(after.digests().get(gamma)).isEqualTo(before.digests().get(gamma));
    }

    @Test
    @DisplayName("loadStaged hands the compose what the pack holds and the typed edges it writes")
    void loadStagedHandsOverTheFactsOfThePack(@TempDir Path host) throws IOException {
        Path pack = richPack(host);

        WholePackWriteStore.Contents contents = store.loadStaged(new StagedPack(pack));

        assertThat(contents.facts().defined())
                .contains("tactic:alpha-tactic", "tactic:gamma-tactic", "directive:DIRECTIVE_043");
        assertThat(contents.pack().descriptor().packId()).isEqualTo("01M5DEH8R0VNDTR1PF1XTVRE0A");
    }

    @Test
    @DisplayName("loadStaged refuses with validation_failed and one violation per fault, never a pack_unreadable")
    void loadStagedRefusesWithOneViolationPerFault(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(pack, "notes/readme.txt", "stray");
        write(pack, BROKEN_FILE, "id: [unclosed");

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .extracting(violation -> violation.pointer())
                            .contains("notes/readme.txt", BROKEN_FILE);
                });
        assertThatThrownBy(() -> store.loadStored(pack))
                .as("a stored pack that cannot be read is refused as the reads refuse it, not as an upload")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    @DisplayName("loadStaged lists every broken graph document at its file, beside the pack's other faults")
    void loadStagedListsEveryGraphFaultAtItsFile(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(
                pack,
                "zeta.graph.yaml",
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\nsurprise: 1\n");
        write(
                pack,
                "alpha.graph.yaml",
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\nother: 2\n");
        write(pack, "notes/readme.txt", "stray");

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .extracting(violation -> violation.pointer() + " " + violation.code())
                            .containsExactly(
                                    "alpha.graph.yaml drg_schema_error",
                                    "zeta.graph.yaml drg_schema_error",
                                    "notes/readme.txt unmodelled");
                    assertThat(refusal.violations().getFirst().detail()).contains("alpha.graph.yaml");
                });
    }

    @Test
    @DisplayName("loadStaged lists a single broken graph document at its file, and no unmodelled entry for it")
    void loadStagedListsOneGraphFaultAtItsFile(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(
                pack,
                "broken.graph.yaml",
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\nsurprise: 1\n");

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.violations())
                                .extracting(violation -> violation.pointer() + " " + violation.code())
                                .containsExactly("broken.graph.yaml drg_schema_error"));
    }

    @Test
    @DisplayName("loadStaged lists each fault of one broken graph document, every one at that file")
    void loadStagedListsEachFaultOfOneGraphDocument(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(pack, "broken.graph.yaml", "schema_version: '1.0'\nsurprise: 1\n");

        assertThatThrownBy(() -> store.loadStaged(new StagedPack(pack)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.violations())
                                .hasSizeGreaterThan(1)
                                .allSatisfy(violation -> {
                                    assertThat(violation.pointer()).isEqualTo("broken.graph.yaml");
                                    assertThat(violation.code()).isEqualTo("drg_schema_error");
                                }));
    }

    @Test
    @DisplayName(
            "salvageStored keeps what can be read of a pack that does not read whole: ids by path, edges that parse")
    void salvageStoredCountsAnUnparsableFileByItsPathAndKeepsTheEdgesThatParse(@TempDir Path host) throws IOException {
        Path pack = richPack(host);
        write(pack, BROKEN_FILE, "id: [unclosed");
        write(pack, "good.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: tactic:alpha-tactic
                  target: tactic:gamma-tactic
                  relation: requires
                """);
        write(pack, "bad.graph.yaml", "edges: [unclosed");

        WholePackWriteStore.Remains remains = store.salvageStored(pack);

        assertThat(remains.defined())
                .contains("tactic:alpha-tactic", "tactic:broken-tactic", "directive:DIRECTIVE_043");
        assertThat(remains.digests()).containsKey(new Urn(NodeKind.TACTIC, "alpha-tactic"));
        assertThat(remains.edges()).extracting(edge -> edge.from().toString()).contains("tactic:alpha-tactic");
        assertThatThrownBy(() -> store.salvageStored(host.resolve("nowhere")))
                .as("a pack whose artifacts cannot be told is refused, never taken as empty")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    /** A strategy that counts how many artifact files it is asked to read. */
    private static SerializationStrategy counting(SerializationStrategy delegate, AtomicInteger reads) {
        return (SerializationStrategy) Proxy.newProxyInstance(
                SerializationStrategy.class.getClassLoader(),
                new Class<?>[] {SerializationStrategy.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("read") && args != null && args.length == 3) {
                        reads.incrementAndGet();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private static int readsToSalvage(Path host, int files) throws IOException {
        Path pack = host.resolve("big-" + files);
        write(
                pack,
                "pack.yaml",
                "pack_id: 01M6R0WP14QQQQQQQQQQQQQQQQ\npack_version: 1.0.0\nname: big\npack_level: org\n");
        for (int i = 0; i < files; i++) {
            write(
                    pack,
                    "directives/D" + i + ".directive.yaml",
                    "schema_version: \"1.0\"\nid: D" + i + "\ntitle: T\nintent: I\nenforcement: required\n");
        }
        AtomicInteger reads = new AtomicInteger();
        long lookupsBefore = FilesystemPackStore.lookupParses();
        WholePackWriteStore.Remains remains =
                new WholePackStore(counting(new YamlSerializationStrategy(), reads)).salvageStored(pack);
        assertThat(remains.defined()).hasSize(files);
        return reads.get() + (int) (FilesystemPackStore.lookupParses() - lookupsBefore);
    }

    @Test
    @DisplayName(
            "salvageStored parses each file a bounded number of times, by strategy read or by lookup: its cost grows linearly")
    void salvageStoredReadsEachFileABoundedNumberOfTimes(@TempDir Path host) throws IOException {
        int small = readsToSalvage(host, 200);
        int large = readsToSalvage(host, 400);

        assertThat(small).as("reads for 200 files").isLessThanOrEqualTo(2 * 200);
        assertThat(large).as("reads for 400 files").isLessThanOrEqualTo(2 * small + 10);
    }

    @Test
    @DisplayName("salvageStored reads the file it listed, so two files declaring one id cannot stand in for each other")
    void salvageStoredReadsEachListedFileEvenWhenTwoDeclareOneId(@TempDir Path host) throws IOException {
        Urn urn = new Urn(NodeKind.DIRECTIVE, "TWIN");
        String descriptor = "pack_id: 01M6R0WP14QQQQQQQQQQQQQQQQ\npack_version: 1.0.0\nname: twins\npack_level: org\n";
        String one = "schema_version: \"1.0\"\nid: TWIN\ntitle: One\nintent: I\nenforcement: required\n";
        String two = one.replace("One", "Two");
        Path onlyOne = host.resolve("one");
        write(onlyOne, "pack.yaml", descriptor);
        write(onlyOne, "directives/A.directive.yaml", one);
        Path onlyTwo = host.resolve("two");
        write(onlyTwo, "pack.yaml", descriptor);
        write(onlyTwo, "directives/B.directive.yaml", two);
        Path both = host.resolve("both");
        write(both, "pack.yaml", descriptor);
        write(both, "directives/A.directive.yaml", one);
        write(both, "directives/B.directive.yaml", two);

        String digestOne = store.salvageStored(onlyOne).digests().get(urn);
        String digestTwo = store.salvageStored(onlyTwo).digests().get(urn);
        String digestBoth = store.salvageStored(both).digests().get(urn);

        assertThat(digestOne).isNotEqualTo(digestTwo);
        assertThat(digestBoth)
                .as("the id is declared twice, with two contents: neither copy's digest stands for it")
                .isNotIn(digestOne, digestTwo);
    }

    // ---- step enhancements and their fragments (ADR-0066, #428) --------------------------------------------------

    private static final String ENHANCEMENT_FILE = "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml";
    private static final String FRAGMENT_REASONS = "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md";
    private static final String FRAGMENT_CANVAS = "missions/software-dev/enhancements/spdd-canvas.prompt-fragment.md";

    /** pack-a without its broken file, plus one enhancement that fills two slots with two companions, and its edge. */
    private static Path enhancingPack(Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        Files.delete(pack.resolve(BROKEN_FILE));
        write(pack, ENHANCEMENT_FILE, """
                schema_version: '1.0'
                id: spdd-plan
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: spdd-plan.prompt-fragment.md
                  - slot: spdd:canvas
                    path: spdd-canvas.prompt-fragment.md
                """);
        write(pack, FRAGMENT_REASONS, "Fill or update the REASONS canvas while composing the plan.\n");
        write(
                pack,
                FRAGMENT_CANVAS,
                "# Canvas\n\nKeep the canvas beside the plan.\n\n## Approach\n\nName the strategy.\n");
        write(pack, "enhancements.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: step_enhancement:spdd-plan
                  target: action:software-dev/plan
                  relation: enhances
                """);
        return pack;
    }

    @Test
    @DisplayName(
            "an enhancement with two companions loads at its place and writes back at the same paths, same meaning")
    void aStepEnhancementAndItsCompanionsLoadAndWriteBackAtTheSamePaths(@TempDir Path host) throws IOException {
        Path source = enhancingPack(host.resolve("source"));

        WholePackStore.LoadedPack loaded = store.load(source);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.pack().artifacts(ArtifactKind.STEP_ENHANCEMENT)).hasSize(1);
        assertThat(loaded.layout().paths())
                .containsEntry(new Urn(NodeKind.STEP_ENHANCEMENT, "spdd-plan"), ENHANCEMENT_FILE);
        assertThat(loaded.unmodelled())
                .as("each fragment is named by exactly one entry, so the enhancement holds both")
                .doesNotContain(FRAGMENT_REASONS, FRAGMENT_CANVAS);
        assertThat(PackFixtures.fragments(loaded)).hasSize(2);
        Path target = host.resolve("out").resolve(source.getFileName().toString());

        store.write(loaded, target);
        WholePackStore.LoadedPack reloaded = store.load(target);

        assertThat(filesUnder(target)).contains(ENHANCEMENT_FILE, FRAGMENT_REASONS, FRAGMENT_CANVAS);
        assertThat(meaning(reloaded)).isEqualTo(meaning(loaded));
        assertThat(reloaded.layout().paths()).isEqualTo(loaded.layout().paths());
        assertThat(reloaded.unmodelled()).isEqualTo(loaded.unmodelled());
    }

    @Test
    @DisplayName("mutant: a written fragment that is altered, or dropped, changes the meaning the round trip compares")
    void anAlteredOrDroppedFragmentChangesTheMeaning(@TempDir Path host) throws IOException {
        Path source = enhancingPack(host.resolve("source"));
        WholePackStore.LoadedPack loaded = store.load(source);
        Path altered = host.resolve("altered").resolve(source.getFileName().toString());
        Path dropped = host.resolve("dropped").resolve(source.getFileName().toString());
        store.write(loaded, altered);
        store.write(loaded, dropped);
        Files.writeString(altered.resolve(FRAGMENT_REASONS), "Fill nothing while composing the plan.\n");
        Files.delete(dropped.resolve(FRAGMENT_CANVAS));

        WholePackStore.LoadedPack reloadedAltered = store.load(altered);
        WholePackStore.LoadedPack reloadedDropped = store.load(dropped);

        assertThat(meaning(reloadedAltered))
                .as("an altered fragment is a different meaning")
                .isNotEqualTo(meaning(loaded));
        assertThat(meaning(reloadedDropped))
                .as("a dropped fragment is a different meaning")
                .isNotEqualTo(meaning(loaded));
        assertThat(meaning(store.load(source)))
                .as("the control: nothing mutated")
                .isEqualTo(meaning(loaded));
    }

    @Test
    @DisplayName("a fragment no entry names is not held: it stays listed as an unmodelled file (IC-02 reclassifies it)")
    void aFragmentNoEntryNamesStaysUnmodelled(@TempDir Path host) throws IOException {
        Path source = enhancingPack(host.resolve("source"));
        write(source, "missions/software-dev/enhancements/stray.prompt-fragment.md", "Nobody names this.\n");

        WholePackStore.LoadedPack loaded = store.load(source);

        assertThat(loaded.unmodelled()).contains("missions/software-dev/enhancements/stray.prompt-fragment.md");
        assertThat(loaded.unmodelled()).doesNotContain(FRAGMENT_REASONS, FRAGMENT_CANVAS, ENHANCEMENT_FILE);
    }

    private static WholePackStore.LoadedPack with(WholePackStore.LoadedPack loaded, Template template) {
        return new WholePackStore.LoadedPack(
                loaded.pack().with(template), loaded.layout(), loaded.graphs(), loaded.unmodelled());
    }
}
