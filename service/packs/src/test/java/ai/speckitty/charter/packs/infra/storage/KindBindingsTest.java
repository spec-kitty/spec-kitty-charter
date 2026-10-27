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
    @DisplayName("every bound kind has a bundled schema named by its token")
    void everyBoundKindHasASchema() {
        for (ArtifactKind kind : KindBindings.kinds()) {
            assertThat(PackSchemas.bundled().validate(kind.token(), "probe.yaml", Map.of()))
                    .as(kind.token())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("each binding's entity type is the kind's entity")
    void entityTypesMatchTheKinds() {
        for (KindBinding<?, ?> binding : KindBindings.all()) {
            assertThat(binding.entityType().getSimpleName().toLowerCase(Locale.ROOT))
                    .isEqualTo(binding.kind().token());
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
