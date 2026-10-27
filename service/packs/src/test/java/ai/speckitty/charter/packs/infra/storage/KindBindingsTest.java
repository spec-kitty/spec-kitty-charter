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
    @DisplayName("the asset sidecar is appended last, after the kinds bound before it, so packs keep their digest")
    void theAssetIsAppendedAfterTheSiblings() {
        List<ArtifactKind> kinds = KindBindings.kinds();
        // Digest stability needs append-only: a new kind goes at the end, never before one already bound.
        assertThat(kinds.get(kinds.size() - 1)).isEqualTo(ArtifactKind.ASSET);
        assertThat(kinds.indexOf(ArtifactKind.ASSET)).isGreaterThan(kinds.indexOf(ArtifactKind.SKILL));
        assertThat(KindBindings.of(ArtifactKind.ASSET)).containsSame(AssetBinding.INSTANCE);
        assertThat(AssetBinding.INSTANCE.fileName("logo")).isEqualTo("logo.asset.yaml");
        assertThat(YamlFormats.of(AssetBinding.INSTANCE)).isSameAs(AssetYaml.INSTANCE);
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
            java.nio.file.Files.writeString(pack.resolve(name), "id: x\n");
            assertThat(PackFileSet.files(pack, binding.kind()))
                    .as(binding.kind().token())
                    .contains(name);
        }
    }
}
