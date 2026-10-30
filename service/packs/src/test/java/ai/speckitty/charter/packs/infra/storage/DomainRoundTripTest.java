package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Outcome;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Refused;
import ai.speckitty.charter.packs.infra.storage.WholePackRoundTrip.Written;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The domain adapter of the whole-pack gate over a small pack: one file of each modelled kind (small real examples
 * from the built-in pack), a descriptor and a graph shard. The adapter reports what it read, wrote and read back; the
 * comparison of meaning is the gate's, so these tests assert on outcomes and on the written files themselves.
 */
class DomainRoundTripTest {

    private static final String PACK_YAML = """
            pack_id: 01ARWG13C000000000000000FG
            pack_version: 1.0.0
            parent_pack: null
            name: built-in
            pack_level: built-in
            """;
    private static final String TACTIC = """
            schema_version: "1.0"
            id: refactoring-inline-temp
            name: Refactoring Inline Temp
            purpose: Remove temporary variables that only alias simple expressions.
            steps:
              - title: Identify trivial temporary aliases
                description: Find temp variables that store pure expressions.
              - title: Inline the expression
                description: Replace the temp usage with the underlying expression.
            """;
    private static final String DIRECTIVE = """
            schema_version: "1.0"
            id: DIRECTIVE_018
            title: Doctrine Versioning Requirement
            intent: Doctrine artifacts must be versioned with traceable change rationale.
            enforcement: required
            """;
    private static final String STYLEGUIDE = """
            schema_version: "1.0"
            id: reasons-canvas-writing
            title: REASONS Canvas Writing Styleguide
            scope: docs
            principles:
              - Lead each section with a one-sentence summary, then bullet specifics.
            """;
    private static final String TOOLGUIDE = """
            schema_version: "1.0"
            id: powershell-syntax
            tool: powershell
            title: PowerShell Syntax Guide
            guide_path: powershell-syntax.guide.md
            summary: PowerShell-specific command, parameter, and path conventions for agents.
            commands:
              - spec-kitty
              - git
            """;
    private static final String PARADIGM = """
            schema_version: "1.0"
            id: atomic-design
            name: Atomic Design
            summary: Compose interfaces from a five-level hierarchy.
            """;
    private static final String SHARD = """
            schema_version: '1.0'
            generated_at: STATIC
            generated_by: drg-migration-v1
            nodes:
            - urn: tactic:refactoring-inline-temp
              kind: tactic
              label: Refactoring Inline Temp
            - urn: directive:DIRECTIVE_018
              kind: directive
              label: Doctrine Versioning Requirement
            edges:
            - source: directive:DIRECTIVE_018
              target: tactic:refactoring-inline-temp
              relation: requires
            """;

    private static final String PROCEDURE = """
            schema_version: "1.0"
            id: x
            title: X
            description: A procedure.
            entry_condition: E
            exit_condition: X
            steps:
              - title: S
            """;

    private static final String PROFILE = """
            profile-id: java-jenny
            name: Java Jenny
            roles: [implementer]
            purpose: Writes Java.
            specialization:
              primary-focus: Java.
            directive-references:
              - code: "003"
                name: Decision Documentation
                rationale: Record decisions.
            """;

    @TempDir
    Path temp;

    private Path source;
    private Map<String, String> kinds;

    @BeforeEach
    void writeThePack() throws IOException {
        source = temp.resolve("src/built-in");
        kinds = new TreeMap<>();
        put("pack.yaml", "pack-descriptor", PACK_YAML);
        put("tactics/refactoring/refactoring-inline-temp.tactic.yaml", "tactic", TACTIC);
        put("directives/018-doctrine-versioning-requirement.directive.yaml", "directive", DIRECTIVE);
        put("styleguides/reasons-canvas-writing.styleguide.yaml", "styleguide", STYLEGUIDE);
        put("toolguides/powershell-syntax.toolguide.yaml", "toolguide", TOOLGUIDE);
        put("toolguides/powershell-syntax.guide.md", "toolguide", "# PowerShell Syntax Guide\n\n## Overview\n\nO.\n");
        put("paradigms/atomic-design.paradigm.yaml", "paradigm", PARADIGM);
        put("tactic.graph.yaml", "graph-shard", SHARD);
        put("agent_profiles/a-file-named-otherwise.agent.yaml", "agent-profile", PROFILE);
        put("procedures/x.procedure.yaml", "procedure", PROCEDURE);
    }

    private void put(String path, String kind, String text) throws IOException {
        Path file = source.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        kinds.put(path, kind);
    }

    private Map<String, Outcome> roundTrip(SerializationStrategy strategy) {
        return new DomainRoundTrip(strategy).roundTrip(source, temp.resolve("out/built-in"), kinds);
    }

    private String written(Outcome outcome) throws IOException {
        assertThat(outcome).isInstanceOf(Written.class);
        return Files.readString(temp.resolve("out/built-in").resolve(((Written) outcome).writtenPath()));
    }

    @Test
    void everyModelledFileIsWrittenAndReadBack() {
        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes).containsOnlyKeys(kinds.keySet());
        assertThat(outcomes.values()).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(Written.class));
    }

    @Test
    void anArtefactIsWrittenAtThePackRootNotAtItsSourcePath() {
        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("tactics/refactoring/refactoring-inline-temp.tactic.yaml"))
                .isEqualTo(new Written("refactoring-inline-temp.tactic.yaml"));
        assertThat(outcomes.get("pack.yaml")).isEqualTo(new Written("pack.yaml"));
        assertThat(outcomes.get("agent_profiles/a-file-named-otherwise.agent.yaml"))
                .as("paired by profile-id, written under the agent file token")
                .isEqualTo(new Written("java-jenny.agent.yaml"));
        assertThat(outcomes.get("tactic.graph.yaml")).isEqualTo(new Written("tactic.graph.yaml"));
    }

    @Test
    void aStepContractIsModelledThroughItsBindingAndWrittenUnderItsFileToken() throws IOException {
        put("missions/built_in_step_contracts/review.step-contract.yaml", "mission-step-contract", """
                schema_version: "1.0"
                id: review
                action: review
                mission: software-dev
                steps:
                  - id: bootstrap
                    description: Load charter context for this action
                """);

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("missions/built_in_step_contracts/review.step-contract.yaml"))
                .isEqualTo(new Written("review.step-contract.yaml"));
    }

    @Test
    void theWrittenShardKeepsItsWriterAndItsEdge() throws IOException {
        String shard = written(roundTrip(new YamlSerializationStrategy()).get("tactic.graph.yaml"));

        assertThat(shard).contains("generated_by: drg-migration-v1").contains("relation: requires");
        assertThat(GraphDocumentReader.read(shard, GraphDocumentReader.Shape.SHARD, "tactic.graph.yaml"))
                .isInstanceOf(FileRead.Read.class);
    }

    @Test
    void aFieldOutsideTheSchemaIsRefusedAndNamedWhileSiblingsAreWritten() throws IOException {
        put("tactics/refactoring/refactoring-inline-temp.tactic.yaml", "tactic", TACTIC + "x_unknown: 1\n");

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("tactics/refactoring/refactoring-inline-temp.tactic.yaml"))
                .isInstanceOfSatisfying(
                        Refused.class, refused -> assertThat(refused.reason()).contains("x_unknown"));
        assertThat(outcomes.get("directives/018-doctrine-versioning-requirement.directive.yaml"))
                .isInstanceOf(Written.class);
    }

    @Test
    void twoFilesDeclaringOneIdAreRefused() throws IOException {
        put("tactics/copy.tactic.yaml", "tactic", TACTIC);

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("tactics/copy.tactic.yaml"))
                .isInstanceOfSatisfying(
                        Refused.class, refused -> assertThat(refused.reason()).contains("same id"));
    }

    @Test
    void aBrokenDescriptorRefusesThePackInsteadOfAbortingTheGate() throws IOException {
        put("pack.yaml", "pack-descriptor", "pack_id: [unclosed\n");

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("pack.yaml")).isInstanceOf(Refused.class);
        assertThat(outcomes.get("tactics/refactoring/refactoring-inline-temp.tactic.yaml"))
                .isInstanceOfSatisfying(
                        Refused.class, refused -> assertThat(refused.reason()).contains("does not assemble"));
        assertThat(outcomes.get("tactic.graph.yaml")).isInstanceOf(Written.class);
    }

    /** Positive control: a writer that loses a field still yields a written file, and the loss is in that file. */
    @Test
    void aLossyWriterIsVisibleInTheWrittenFile() throws IOException {
        SerializationStrategy lossy = new LossyTacticWriter(new YamlSerializationStrategy());

        String tactic = written(roundTrip(lossy).get("tactics/refactoring/refactoring-inline-temp.tactic.yaml"));

        assertThat(TACTIC).contains("purpose:");
        assertThat(tactic).doesNotContain("purpose:").contains("id: refactoring-inline-temp");
    }

    @Test
    void theSourcePackIsLeftUnchanged() throws IOException {
        Map<String, String> before = digests(source);

        roundTrip(new YamlSerializationStrategy());

        assertThat(digests(source)).isEqualTo(before);
    }

    @Test
    void theAdapterModelsEveryBoundKindAndThePackLevelFiles() {
        Set<String> expected = new HashSet<>(Set.of(
                "pack-descriptor",
                "graph-shard",
                "asset-payload",
                "template",
                "preset",
                "pack-manifest",
                "pack-document"));
        KindBindings.all().forEach(binding -> expected.add(binding.schemaName()));
        assertThat(new DomainRoundTrip(new YamlSerializationStrategy()).modelledKinds())
                .isEqualTo(expected);
    }

    // ---- #193: asset sidecars, asset files and templates -------------------------------------------------------

    private static final byte[] LOGO = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, (byte) 0xFF};
    private static final String LOGO_SIDECAR = """
            id: pack-logo
            mime: image/png
            path: logo.png
            title: The pack's logo
            """;

    private void putBytes(String path, String kind, byte[] bytes) throws IOException {
        Path file = source.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        kinds.put(path, kind);
    }

    @Test
    void anAssetFileWithAndWithoutASidecarAndATemplateAreWrittenInPlaceByteForByte() throws IOException {
        byte[] lint = "print('lint')\r\nno final newline".getBytes(StandardCharsets.UTF_8);
        byte[] minutes = "---\ntitle: Minutes\n---\n# Minutes\n".getBytes(StandardCharsets.UTF_8);
        putBytes("assets/logo.png", "asset-payload", LOGO);
        put("assets/logo.png.asset.yaml", "asset", LOGO_SIDECAR);
        putBytes("assets/tools/lint.py", "asset-payload", lint);
        putBytes("templates/writing/minutes.md", "template", minutes);

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("assets/logo.png")).isEqualTo(new Written("assets/logo.png"));
        assertThat(outcomes.get("assets/tools/lint.py")).isEqualTo(new Written("assets/tools/lint.py"));
        assertThat(outcomes.get("templates/writing/minutes.md")).isEqualTo(new Written("templates/writing/minutes.md"));
        assertThat(outcomes.get("assets/logo.png.asset.yaml")).isEqualTo(new Written("pack-logo.asset.yaml"));
        Path out = temp.resolve("out/built-in");
        assertThat(Files.readAllBytes(out.resolve("assets/logo.png"))).isEqualTo(LOGO);
        assertThat(Files.readAllBytes(out.resolve("assets/tools/lint.py"))).isEqualTo(lint);
        assertThat(Files.readAllBytes(out.resolve("templates/writing/minutes.md")))
                .isEqualTo(minutes);
    }

    @Test
    void aSidecarWhosePayloadIsMissingIsRefusedWithTheStoresExactReason() throws IOException {
        put("assets/logo.png.asset.yaml", "asset", LOGO_SIDECAR.replace("path: logo.png", "path: assets/logo.png"));
        putBytes("assets/logo.png", "asset-payload", LOGO);

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("assets/logo.png.asset.yaml"))
                .isEqualTo(new Refused("payload assets/assets/logo.png does not exist"));
        assertThat(outcomes.get("assets/logo.png")).isEqualTo(new Written("assets/logo.png"));
        assertThat(outcomes.get("tactics/refactoring/refactoring-inline-temp.tactic.yaml"))
                .isInstanceOf(Written.class);
    }

    // ---- presets (#195) ------------------------------------------------------------------------

    private static final String PRESET = """
            schema_version: '1.0'
            id: p
            description: d
            activated_tactics:
              - id: t
                must_have: true
            """;

    @Test
    void aPresetIsWrittenInPlaceAndABrokenOneIsRefusedWithTheStoresExactReasons() throws IOException {
        put("presets/p.yaml", "preset", PRESET);
        put(
                "presets/q.yaml",
                "preset",
                "name: q\nschema_version: '1.0'\nid: q\ndescription: d\nmission_type_activations:\n  - plan\n");

        Map<String, Outcome> outcomes = roundTrip(new YamlSerializationStrategy());

        assertThat(outcomes.get("presets/p.yaml")).isEqualTo(new Written("presets/p.yaml"));
        assertThat(MeaningComparator.adr0036().compareTexts("preset", PRESET, written(outcomes.get("presets/p.yaml"))))
                .isEmpty();
        assertThat(outcomes.get("presets/q.yaml"))
                .isEqualTo(new Refused("additionalProperties violated at the document: undeclared key 'name'; "
                        + "additionalProperties violated at the document: undeclared key 'mission_type_activations'"));
        assertThat(outcomes.get("tactics/refactoring/refactoring-inline-temp.tactic.yaml"))
                .isInstanceOf(Written.class);
    }

    @Test
    void aPresetTheWriterBreaksIsRefusedOnReadBack() throws IOException {
        put("presets/p.yaml", "preset", PRESET);

        Map<String, Outcome> faithful = roundTrip(new YamlSerializationStrategy());
        Map<String, Outcome> broken = new DomainRoundTrip(new PresetBreakingWriter(new YamlSerializationStrategy()))
                .roundTrip(source, temp.resolve("broken/built-in"), kinds);

        assertThat(faithful.get("presets/p.yaml")).isEqualTo(new Written("presets/p.yaml"));
        assertThat(broken.get("presets/p.yaml"))
                .isEqualTo(new Refused(
                        "read-back: additionalProperties violated at the document: undeclared key 'surplus'"));
    }

    /** The save hook of the gate's byte controls: what is written is the hook's pack, not the loaded one. */
    @Test
    void theSaveWritesThePackTheHookReturns() throws IOException {
        putBytes("assets/logo.png", "asset-payload", LOGO);
        DomainRoundTrip emptied = new DomainRoundTrip(
                new YamlSerializationStrategy(),
                pack -> Pack.builder()
                        .id(pack.id())
                        .descriptor(pack.descriptor())
                        .assetFile(new AssetFile("logo.png", Payload.of(new byte[0])))
                        .build());

        Map<String, Outcome> outcomes = emptied.roundTrip(source, temp.resolve("out/built-in"), kinds);

        assertThat(outcomes.get("assets/logo.png")).isEqualTo(new Written("assets/logo.png"));
        assertThat(Files.readAllBytes(temp.resolve("out/built-in/assets/logo.png")))
                .isEmpty();
    }

    private static Map<String, String> digests(Path root) throws IOException {
        Map<String, String> digests = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                digests.put(root.relativize(file).toString(), sha256(Files.readAllBytes(file)));
            }
        }
        return digests;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writes tactics without their {@code purpose} line; everything else is delegated unchanged. */
    private record LossyTacticWriter(SerializationStrategy delegate) implements SerializationStrategy {

        @Override
        public <D> String write(KindBinding<D, ?> kind, D dso) {
            String text = delegate.write(kind, dso);
            if (kind.kind() != ArtifactKind.TACTIC) {
                return text;
            }
            return text.lines()
                    .filter(line -> !line.startsWith("purpose:"))
                    .reduce("", (joined, line) -> joined + line + "\n");
        }

        @Override
        public <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file) {
            return delegate.read(kind, text, file);
        }

        @Override
        public String writeDescriptor(PackDescriptorDso descriptor) {
            return delegate.writeDescriptor(descriptor);
        }

        @Override
        public FileRead<PackDescriptorDso> readDescriptor(String text) {
            return delegate.readDescriptor(text);
        }

        @Override
        public String writeGraph(GraphDocument document) {
            return delegate.writeGraph(document);
        }

        @Override
        public FileRead<GraphDocument> readGraph(String text, GraphDocumentReader.Shape shape, String file) {
            return delegate.readGraph(text, shape, file);
        }

        @Override
        public String writePreset(PresetDso preset) {
            return delegate.writePreset(preset);
        }

        @Override
        public FileRead<PresetDso> readPreset(String text, String file) {
            return delegate.readPreset(text, file);
        }

        @Override
        public String writeManifest(PackManifestDso manifest) {
            return delegate.writeManifest(manifest);
        }

        @Override
        public FileRead<PackManifestDso> readManifest(String text, String file) {
            return delegate.readManifest(text, file);
        }
    }

    /** A writer that adds a key the preset schema does not declare: the read-back control of the preset branch. */
    private record PresetBreakingWriter(SerializationStrategy delegate) implements SerializationStrategy {

        @Override
        public <D> String write(KindBinding<D, ?> kind, D dso) {
            return delegate.write(kind, dso);
        }

        @Override
        public <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file) {
            return delegate.read(kind, text, file);
        }

        @Override
        public String writeDescriptor(PackDescriptorDso descriptor) {
            return delegate.writeDescriptor(descriptor);
        }

        @Override
        public FileRead<PackDescriptorDso> readDescriptor(String text) {
            return delegate.readDescriptor(text);
        }

        @Override
        public String writeGraph(GraphDocument document) {
            return delegate.writeGraph(document);
        }

        @Override
        public FileRead<GraphDocument> readGraph(String text, GraphDocumentReader.Shape shape, String file) {
            return delegate.readGraph(text, shape, file);
        }

        @Override
        public String writePreset(PresetDso preset) {
            return delegate.writePreset(preset) + "surplus: 1\n";
        }

        @Override
        public FileRead<PresetDso> readPreset(String text, String file) {
            return delegate.readPreset(text, file);
        }

        @Override
        public String writeManifest(PackManifestDso manifest) {
            return delegate.writeManifest(manifest);
        }

        @Override
        public FileRead<PackManifestDso> readManifest(String text, String file) {
            return delegate.readManifest(text, file);
        }
    }
}
