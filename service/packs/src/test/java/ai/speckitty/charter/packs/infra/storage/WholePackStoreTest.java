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
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        assertThat(loaded.pack().templates()).hasSize(2);
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
                        "missions/plan/templates/spec.md",
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
        assertThatThrownBy(() -> store.write(loaded, target)).isInstanceOf(CharterRefusal.class);
        assertThat(target).doesNotExist();
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

    private static WholePackStore.LoadedPack with(WholePackStore.LoadedPack loaded, Template template) {
        return new WholePackStore.LoadedPack(
                loaded.pack().with(template), loaded.layout(), loaded.graphs(), loaded.unmodelled());
    }
}
