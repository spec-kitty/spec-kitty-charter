package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("A mission step contract round-trips through the YAML format and the store (#191)")
class MissionStepContractRoundTripTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5YHYDSTEPCONTRACTFIXT0A");

    private static final String FULL = """
            schema_version: "1.0"
            id: review
            action: review
            mission: software-dev
            gates:
              - on_transition: "in_progress->for_review"
                handler: "spec-kitty-pre-review"
                handler_kind: "mission_step_contract"
                schema_version: "1.0"
                fail_open: true
                provenance: "built-in"
            steps:
              - id: bootstrap
                description: Load charter context for this action
                command: "spec-kitty charter context --action review --json"
                inputs:
                  - flag: --wp
                    source: wp_id
                    optional: false
                  - flag: --mission
                    source: mission_slug
              - id: verify
                description: Verify the work
                delegates_to:
                  kind: directive
                  candidates:
                    - DIRECTIVE_030
                    - DIRECTIVE_029
                    - DIRECTIVE_030
                guidance: >
                  Folded guidance that spans
                  two source lines.
            """;

    private static final String MINIMAL = """
            schema_version: "1.0"
            id: implement
            action: implement
            mission: software-dev
            steps:
              - id: bootstrap
                description: Load charter context for this action
            """;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("every field of the schema survives read, domain, write and read again")
    void everyFieldSurvives() {
        MissionStepContractDso read = read(FULL);

        MissionStepContractDso again = read(throughTheDomain(read));

        assertThat(again).isEqualTo(read);
        assertThat(read.steps().get(0).command()).contains("spec-kitty charter context --action review --json");
        assertThat(read.steps().get(0).inputs())
                .extracting(MissionStepContractDso.Input::flag, MissionStepContractDso.Input::source)
                .containsExactly(tuple("--wp", "wp_id"), tuple("--mission", "mission_slug"));
        assertThat(read.steps().get(1).delegatesTo().orElseThrow().kind()).isEqualTo("directive");
        assertThat(read.gates()).singleElement().satisfies(gate -> {
            assertThat(gate.failOpen()).contains(true);
            assertThat(gate.handlerKind()).contains("mission_step_contract");
            assertThat(gate.provenance()).contains("built-in");
        });
        assertThat(read.steps().get(0).inputs())
                .extracting(MissionStepContractDso.Input::optional)
                .containsExactly(java.util.Optional.of(false), java.util.Optional.empty());
        assertThat(read.steps().get(1).delegatesTo().orElseThrow().candidates())
                .containsExactly("DIRECTIVE_030", "DIRECTIVE_029", "DIRECTIVE_030");
        assertThat(read.steps().get(1).guidance()).contains("Folded guidance that spans two source lines.\n");
    }

    @Test
    @DisplayName("booleans are written as booleans and a version that looks like a number stays a string")
    void scalarsKeepTheirTypes() {
        String written = throughTheDomain(read(FULL));

        Map<String, Object> document = map(Yaml.load(written));
        Map<String, Object> gate = first(document, "gates");
        Map<String, Object> step = first(document, "steps");
        Map<String, Object> input = first(step, "inputs");
        assertThat(gate.get("fail_open")).isEqualTo(true);
        assertThat(input.get("optional")).isEqualTo(false);
        assertThat(document.get("schema_version")).isEqualTo("1.0");
        assertThat(gate.get("schema_version")).isEqualTo("1.0");
    }

    @Test
    @DisplayName("an absent optional field stays absent: nothing is written for it, no null and no empty list")
    void absentStaysAbsent() {
        String written = throughTheDomain(read(MINIMAL));

        Map<String, Object> document = map(Yaml.load(written));
        Map<String, Object> step = first(document, "steps");
        assertThat(document).containsOnlyKeys("schema_version", "id", "action", "mission", "steps");
        assertThat(step).containsOnlyKeys("id", "description");
    }

    @Test
    @DisplayName("a gate that omits its defaults keeps them omitted; one that writes them keeps them written")
    void schemaDefaultsAreKeptAsWritten() {
        String omitted = MINIMAL + """
                gates:
                  - on_transition: "a->b"
                    handler: h
                    schema_version: "1.0"
                """;

        Map<String, Object> omittedGate = gate(throughTheDomain(read(omitted)));
        Map<String, Object> writtenGate = gate(throughTheDomain(read(FULL)));

        assertThat(omittedGate).containsOnlyKeys("on_transition", "handler", "schema_version");
        assertThat(writtenGate)
                .containsEntry("handler_kind", "mission_step_contract")
                .containsEntry("fail_open", true);
    }

    @ParameterizedTest(name = "an unknown key {0} is refused")
    @ValueSource(strings = {"top", "step", "delegation", "input", "gate"})
    @DisplayName("an unknown key is refused at every level of the file")
    void anUnknownKeyIsRefused(String level) {
        String text = switch (level) {
            case "top" -> FULL + "surprise: 1\n";
            case "step" ->
                FULL.replace(
                        "    description: Verify the work\n", "    description: Verify the work\n    surprise: 1\n");
            case "delegation" -> FULL.replace("      kind: directive\n", "      kind: directive\n      surprise: 1\n");
            case "input" -> FULL.replace("        source: wp_id\n", "        source: wp_id\n        surprise: 1\n");
            case "gate" ->
                FULL.replace("    provenance: \"built-in\"\n", "    provenance: \"built-in\"\n    surprise: 1\n");
            default -> throw new IllegalArgumentException(level);
        };
        assertThat(text).as("the planted key is in the file").contains("surprise: 1");

        FileRead<MissionStepContractDso> read =
                strategy.read(MissionStepContractBinding.INSTANCE, text, "x.step-contract.yaml");

        assertThat(read).isInstanceOf(FileRead.Unreadable.class);
        assertThat(((FileRead.Unreadable<MissionStepContractDso>) read).violations())
                .anySatisfy(violation -> {
                    assertThat(violation.keyword()).isEqualTo("additionalProperties");
                    assertThat(violation.property()).contains("surprise");
                });
    }

    @Test
    @DisplayName("a delegation to something that is not an artifact kind is refused by the schema")
    void anUnknownDelegationKindIsRefused() {
        String text = FULL.replace("      kind: directive\n", "      kind: profile\n");

        assertThat(strategy.read(MissionStepContractBinding.INSTANCE, text, "x.step-contract.yaml"))
                .isInstanceOf(FileRead.Unreadable.class);
    }

    @Test
    @DisplayName("a pack loads a contract from a nested directory, and a new one is created where a load finds it")
    void aPackLoadsAndCreatesContracts(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("missions/built_in_step_contracts/review.step-contract.yaml"), FULL);
        DomainRepositoryService service = service(pack);

        Pack loaded = service.load(PACK);

        assertThat(loaded.violations()).isEmpty();
        assertThat(loaded.artifacts(ArtifactKind.MISSION_STEP_CONTRACT, MissionStepContract.class))
                .singleElement()
                .satisfies(contract -> {
                    assertThat(contract.localId()).isEqualTo("review");
                    assertThat(contract.steps()).hasSize(2);
                    assertThat(contract.gates()).hasSize(1);
                });
        assertThat(PackFileSet.files(pack, ArtifactKind.MISSION_STEP_CONTRACT))
                .containsExactly("missions/built_in_step_contracts/review.step-contract.yaml");

        MissionStepContract implement = MissionStepContractBinding.INSTANCE.toEntity(PACK, read(MINIMAL));
        service.saveArtifact(implement);

        assertThat(pack.resolve("implement.step-contract.yaml")).isRegularFile();
        assertThat(service.load(PACK).artifacts(ArtifactKind.MISSION_STEP_CONTRACT))
                .extracting(artifact -> artifact.localId())
                .containsExactlyInAnyOrder("review", "implement");
    }

    @Test
    @DisplayName("a contract file with an unknown key is a violation of the pack, and the contract is not loaded")
    void anUnknownKeyIsAPackViolation(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("mission_step_contracts/review.step-contract.yaml"), FULL + "surprise: 1\n");

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.MISSION_STEP_CONTRACT)).isEmpty();
        assertThat(loaded.violations())
                .extracting(PackElementViolation::file)
                .containsExactly("mission_step_contracts/review.step-contract.yaml");
    }

    private MissionStepContractDso read(String text) {
        FileRead<MissionStepContractDso> read =
                strategy.read(MissionStepContractBinding.INSTANCE, text, "probe.step-contract.yaml");
        assertThat(read).as("the planted file reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<MissionStepContractDso>) read).value();
    }

    private String throughTheDomain(MissionStepContractDso dso) {
        MissionStepContractBinding binding = MissionStepContractBinding.INSTANCE;
        return strategy.write(binding, binding.toDso(binding.toEntity(PACK, dso)));
    }

    private static Map<String, Object> gate(String written) {
        Map<String, Object> document = map(Yaml.load(written));
        return first(document, "gates");
    }

    @SuppressWarnings("unchecked") // a YAML mapping read back from text this test wrote
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked") // a YAML list of mappings read back from text this test wrote
    private static Map<String, Object> first(Map<String, Object> owner, String key) {
        assertThat(owner.get(key)).isInstanceOf(List.class);
        return map(((List<Object>) owner.get(key)).get(0));
    }

    private static Path pack(Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("step-pack"));
        write(pack.resolve("pack.yaml"), """
                pack_id: 01M5YHYDSTEPCONTRACTFIXT0A
                pack_version: 1.0.0
                name: step-pack
                pack_level: org
                """);
        return pack;
    }

    private static DomainRepositoryService service(Path pack) {
        PackLocation location = new PackLocation("step-pack", PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
