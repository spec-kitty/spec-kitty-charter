package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.domain.Preset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The preset's activation-list keys come from the kernel kind registry (one kind model, IC-20, FR-041): the YAML form's
 * {@link PresetYaml#LIST_KEYS} and the domain's {@link Preset#ACTIVATION_KINDS} are derived from it, and the bundled
 * {@code preset} schema declares the same lists. A list the schema accepts but the form does not read would be dropped
 * on write, so the three are held equal here (#195, ADR-0042). The removed kind gate {@code activated_kinds} is refused.
 */
@DisplayName("Preset list keys: the registry, the YAML form, the schema and the domain agree (#195, FR-041)")
class PresetYamlTest {

    private static final List<String> SCALAR_KEYS = List.of("schema_version", "id", "description");

    private static final String PACK_YAML =
            "pack_id: 01M60H1APRESETSPACK0000000\npack_version: 0.1.0\nname: one-preset\npack_level: project\n";

    @Test
    @DisplayName("the form reads exactly the schema's activation lists")
    void theFormReadsExactlyTheSchemasLists() {
        List<String> schemaLists = new ArrayList<>(PackSchemas.bundled().declaredProperties(PresetYaml.SCHEMA));
        schemaLists.removeAll(SCALAR_KEYS);

        assertThat(PresetYaml.LIST_KEYS).containsExactlyInAnyOrderElementsOf(schemaLists);
    }

    @Test
    @DisplayName("the form's lists are activated_<plural> of every activatable kind, in the registry's order")
    void theListsComeFromTheKindRegistry() {
        List<String> fromRegistry = Arrays.stream(ArtifactKind.values())
                .filter(ArtifactKind::activatable)
                .map(kind -> "activated_" + kind.plural())
                .toList();

        assertThat(PresetYaml.LIST_KEYS).containsExactlyElementsOf(fromRegistry);
        assertThat(PresetYaml.LIST_KEYS)
                .contains("activated_skills", "activated_glossary_packs", "activated_mission_types");
    }

    @Test
    @DisplayName("each list is a kind the domain lets a preset name, and the domain names every activatable kind")
    void eachListIsAKindTheDomainAccepts() {
        List<String> fromDomain = Preset.ACTIVATION_KINDS.stream()
                .map(kind -> "activated_" + kind.plural())
                .toList();

        assertThat(PresetYaml.LIST_KEYS).containsExactlyInAnyOrderElementsOf(fromDomain);
    }

    @Test
    @DisplayName("a preset carrying activated_kinds is refused on its file at registration's load; without it, it loads")
    void theKindGateKeyIsRefused(@TempDir Path host) throws IOException {
        String preset = "schema_version: '1.0'\nid: p\ndescription: d\nactivated_skills:\n  - id: s\n";
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        WholePackStore.LoadedPack clean = store.load(onePresetPack(host.resolve("clean"), preset));
        WholePackStore.LoadedPack refused =
                store.load(onePresetPack(host.resolve("refused"), preset + "activated_kinds:\n  - skills\n"));

        assertThat(clean.pack().violations()).isEmpty();
        assertThat(clean.pack().presets()).singleElement().satisfies(p -> assertThat(p.artifacts())
                .containsOnlyKeys(ArtifactKind.SKILL));
        assertThat(refused.pack().presets()).isEmpty();
        assertThat(refused.pack().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo("presets/p.yaml");
            assertThat(violation.reason())
                    .isEqualTo("additionalProperties violated at the document: undeclared key 'activated_kinds'");
        });
    }

    private static Path onePresetPack(Path directory, String preset) throws IOException {
        Files.createDirectories(directory.resolve("presets"));
        Files.writeString(directory.resolve(PackFileSet.PACK_YAML), PACK_YAML);
        Files.writeString(directory.resolve("presets/p.yaml"), preset);
        return directory;
    }
}
