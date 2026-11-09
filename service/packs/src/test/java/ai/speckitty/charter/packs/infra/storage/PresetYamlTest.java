package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.Preset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The preset's activation-list keys are declared in three places: the YAML form's {@link PresetYaml#LIST_KEYS}, the
 * bundled {@code preset} schema and the domain's {@link Preset#ACTIVATION_KINDS}. A list the schema accepts but the
 * form does not read would be dropped on write, so the three are held equal here (#195, ADR-0042).
 */
@DisplayName("Preset list keys: the YAML form, the schema and the domain agree (#195)")
class PresetYamlTest {

    private static final List<String> SCALAR_KEYS = List.of("schema_version", "id", "description", "activated_kinds");

    @Test
    @DisplayName("the form reads exactly the schema's activation lists, in the schema's order")
    void theFormReadsExactlyTheSchemasLists() {
        List<String> schemaLists = new ArrayList<>(PackSchemas.bundled().declaredProperties(PresetYaml.SCHEMA));
        schemaLists.removeAll(SCALAR_KEYS);

        assertThat(PresetYaml.LIST_KEYS).containsExactlyElementsOf(schemaLists);
    }

    @Test
    @DisplayName("each artifact list is a kind the domain governs, and the mission types are the one other list")
    void eachArtifactListIsAGovernedKind() {
        List<String> fromDomain = new ArrayList<>(Preset.ACTIVATION_KINDS.stream()
                .map(kind -> "activated_" + kind.plural())
                .toList());
        fromDomain.add("activated_mission_types");

        assertThat(PresetYaml.LIST_KEYS).containsExactlyInAnyOrderElementsOf(fromDomain);
    }
}
