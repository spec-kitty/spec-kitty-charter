package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The storage kind registry (ADR-0037)")
class KindBindingsTest {

    @Test
    @DisplayName("every bound kind has one YAML format that reads its persistence shape, and nothing else has one")
    void everyBindingHasItsFormat() {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            assertThat(YamlFormats.of(binding).dsoType())
                    .as(binding.kind().token())
                    .isEqualTo(binding.dsoType());
        }
        assertThat(YamlFormats.all().stream().map(YamlFormat::kind).toList())
                .containsExactlyInAnyOrderElementsOf(KindBindings.kinds());
    }

    @Test
    @DisplayName("each kind is bound once")
    void eachKindIsBoundOnce() {
        assertThat(new HashSet<>(KindBindings.kinds())).hasSameSizeAs(KindBindings.kinds());
    }

    @Test
    @DisplayName("the five kinds keep their load and digest order, so an unchanged pack keeps its fingerprint")
    void theFirstFiveKeepTheirOrder() {
        assertThat(KindBindings.kinds().subList(0, 5))
                .isEqualTo(List.of(
                        ArtifactKind.TACTIC,
                        ArtifactKind.DIRECTIVE,
                        ArtifactKind.STYLEGUIDE,
                        ArtifactKind.TOOLGUIDE,
                        ArtifactKind.PARADIGM));
    }

    @Test
    @DisplayName("the asset sidecar is appended after the kinds bound before it, so packs keep their digest")
    void theAssetIsAppendedAfterTheSiblings() {
        List<ArtifactKind> kinds = KindBindings.kinds();
        // Digest stability needs append-only: a new kind goes at the end, never before one already bound.
        assertThat(kinds.indexOf(ArtifactKind.ASSET)).isGreaterThan(kinds.indexOf(ArtifactKind.SKILL));
        assertThat(KindBindings.of(ArtifactKind.ASSET)).containsSame(AssetBinding.INSTANCE);
        assertThat(AssetBinding.INSTANCE.fileName("logo")).isEqualTo("logo.asset.yaml");
        assertThat(YamlFormats.of(AssetBinding.INSTANCE)).isSameAs(AssetYaml.INSTANCE);
    }

    @Test
    @DisplayName("the mission type is appended last, after the procedure, placed at its own path (#194, ADR-0044)")
    void theMissionTypeIsAppendedLast() {
        List<ArtifactKind> kinds = KindBindings.kinds();
        // Append-only: the eleven kinds bound before it keep their positions, so a pack without mission types keeps
        // its digest.
        assertThat(kinds).hasSize(12);
        assertThat(kinds.get(kinds.size() - 1)).isEqualTo(ArtifactKind.MISSION_TYPE);
        assertThat(kinds.get(kinds.size() - 2)).isEqualTo(ArtifactKind.PROCEDURE);
        assertThat(kinds.get(kinds.size() - 3)).isEqualTo(ArtifactKind.ASSET);
        assertThat(YamlFormats.all()).hasSize(12).last().isSameAs(MissionTypeYaml.INSTANCE);
        assertThat(KindBindings.of(ArtifactKind.MISSION_TYPE)).containsSame(MissionTypeBinding.INSTANCE);
        assertThat(YamlFormats.of(MissionTypeBinding.INSTANCE)).isSameAs(MissionTypeYaml.INSTANCE);
        assertThat(MissionTypeBinding.INSTANCE.fileName("research"))
                .isEqualTo("missions/research/research.mission-type.yaml");
        assertThat(MissionTypeBinding.INSTANCE.schemaName()).isEqualTo("mission-type");
        assertThat(MissionTypeBinding.INSTANCE.fileToken()).isEqualTo("mission-type");
        assertThat(MissionTypeBinding.INSTANCE.placedAtItsFileName()).isTrue();
        // The procedure keeps its place before the mission type, its format and its default spellings (#194).
        assertThat(YamlFormats.of(ProcedureBinding.INSTANCE)).isSameAs(ProcedureYaml.INSTANCE);
        assertThat(ProcedureBinding.INSTANCE.fileName("land-a-change")).isEqualTo("land-a-change.procedure.yaml");
        assertThat(ProcedureBinding.INSTANCE.schemaName()).isEqualTo("procedure");
        assertThat(ProcedureBinding.INSTANCE.idKey()).isEqualTo("id");
        assertThat(ProcedureBinding.INSTANCE.placedAtItsFileName()).isFalse();
    }

    @Test
    @DisplayName("the test fixtures' copies of the step companion structure schemas are the bundled ones")
    void theFixtureStepSchemaCopiesAreTheBundledOnes() throws java.io.IOException {
        for (String schema : List.of("mission-step-prompt", "mission-step-guidelines")) {
            try (var fixture = KindBindingsTest.class.getResourceAsStream(
                            "/markdown-rule-fixtures/" + schema + ".schema.json");
                    var bundled =
                            KindBindingsTest.class.getResourceAsStream("/packs/schemas/" + schema + ".schema.json")) {
                assertThat(fixture).as(schema).isNotNull();
                assertThat(bundled).as(schema).isNotNull();
                assertThat(fixture.readAllBytes()).as(schema).isEqualTo(bundled.readAllBytes());
            }
        }
    }

    @Test
    @DisplayName("every bound kind has a bundled schema, and its schema name leads back to its binding")
    void everyBoundKindHasASchema() {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            assertThat(PackSchemas.bundled().validate(binding.schemaName(), "probe.yaml", Map.of()))
                    .as(binding.kind().token())
                    .isNotEmpty();
            assertThat(KindBindings.bySchema(binding.schemaName()))
                    .as(binding.kind().token())
                    .containsSame(binding);
        }
    }

    @Test
    @DisplayName("a kind whose schema, file token and id key are not its token names all three")
    void theAgentProfileNamesItsSpellings() {
        assertThat(AgentProfileBinding.INSTANCE.schemaName()).isEqualTo("agent-profile");
        assertThat(AgentProfileBinding.INSTANCE.fileName("p")).isEqualTo("p.agent.yaml");
        assertThat(AgentProfileBinding.INSTANCE.idKey()).isEqualTo("profile-id");
        assertThat(ParadigmBinding.INSTANCE.schemaName()).isEqualTo("paradigm");
        assertThat(ParadigmBinding.INSTANCE.idKey()).isEqualTo("id");
    }

    @Test
    @DisplayName("file-kinds.yaml classifies each binding's file name to the schema the binding names (ADR-0037)")
    void eachFileNameIsClassifiedToItsBindingsSchema() {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            String file = "probe-pack/" + binding.fileName("x");
            assertThat(PackFileKind.bundled().classify(file))
                    .as(file)
                    .isInstanceOfSatisfying(
                            PackFileKind.Mapped.class,
                            mapped -> assertThat(mapped.kind()).isEqualTo(binding.schemaName()));
        }
    }

    @Test
    @DisplayName("a schema name finds the binding that names it, and only that one")
    void aSchemaFindsItsBinding() {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            assertThat(KindBindings.bySchema(binding.schemaName())).containsSame(binding);
        }
        assertThat(KindBindings.bySchema("no-such-schema")).isEmpty();
    }

    @Test
    @DisplayName("each binding's entity type is the kind's entity")
    void entityTypesMatchTheKinds() {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            assertThat(binding.entityType().getSimpleName().toLowerCase(Locale.ROOT))
                    .isEqualTo(binding.kind().token().replace("_", ""));
        }
    }

    @Test
    @DisplayName("the file a new artifact is created under is read back as that kind")
    void theDefaultFileNameClassifiesBackToTheKind(@org.junit.jupiter.api.io.TempDir java.nio.file.Path host)
            throws java.io.IOException {
        java.nio.file.Path pack = java.nio.file.Files.createDirectories(host.resolve("probe-pack"));
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            String name = binding.fileName("probe-" + binding.kind().token());
            // A mission type's file name has directories (ADR-0044); the store's write creates them.
            java.nio.file.Files.createDirectories(pack.resolve(name).getParent());
            java.nio.file.Files.writeString(pack.resolve(name), "id: x\n");
            assertThat(PackFileSet.files(pack, binding.kind()))
                    .as(binding.kind().token())
                    .contains(name);
        }
    }

    @Test
    @DisplayName("the companion structures are derived from the bindings: each Markdown files rule but the pack"
            + " document's names one, and each declared one is the structure the binding's companion carries")
    void theCompanionStructuresAreTheBindingsOwn() {
        assertThat(KindBindings.isCompanionStructure(ToolguideBinding.GUIDE_SCHEMA))
                .isTrue();
        assertThat(KindBindings.isCompanionStructure("toolguide")).isFalse();
        assertThat(KindBindings.byCompanionStructure(ToolguideBinding.GUIDE_SCHEMA))
                .containsSame(ToolguideBinding.INSTANCE);
        // #260: pack.md is the one Markdown files rule that is no companion; the pack holds it in its own slot.
        assertThat(KindBindings.isCompanionStructure(PackDocumentMarkdown.KIND)).isFalse();
        assertThat(PackFileKind.bundled().fileRules())
                .filteredOn(rule -> rule.contentForm() == ContentForm.MARKDOWN)
                .filteredOn(rule -> !rule.schema().equals(PackDocumentMarkdown.KIND))
                .isNotEmpty()
                .allSatisfy(rule -> assertThat(KindBindings.isCompanionStructure(rule.schema()))
                        .as(rule.match())
                        .isTrue());
        ToolguideDso dso = new ToolguideDso(
                "x",
                "1.0",
                "x",
                "X",
                "x.guide.md",
                "s",
                List.of(),
                List.of(),
                java.util.Optional.empty(),
                List.of(),
                java.util.Optional.empty());
        assertThat(ToolguideBinding.INSTANCE.companions(dso))
                .allSatisfy(companion -> assertThat(ToolguideBinding.INSTANCE.companionStructures())
                        .containsAll(companion.structureSchema().stream().toList()));
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            if (!binding.companionStructures().isEmpty()) {
                assertThat(binding.hasCompanions()).as(binding.kind().token()).isTrue();
            }
        }
    }
}
