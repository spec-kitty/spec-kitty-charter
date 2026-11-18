package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CostTier;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.routing.domain.Grade;
import ai.speckitty.charter.routing.domain.ModelEntry;
import ai.speckitty.charter.routing.domain.RoutingCatalog;
import ai.speckitty.charter.routing.domain.RoutingObjective;
import ai.speckitty.charter.routing.domain.TaskFit;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The routing catalog's binding (ADR-0080 decision 5, #108): the fixture carries every optional member, reads into the
 * domain with its numbers as exact decimals, and writes back byte for byte through the production writer.
 */
@DisplayName("A routing catalog round-trips from its file (#108)")
class RoutingCatalogBindingTest {

    private static final String PACK = "routing-catalog";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final String FILE = "routing_catalogs/default.routing-catalog.yaml";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    private RoutingCatalogDso dso() {
        return new FilesystemPackStore(host, strategy)
                .read(PACK_ID, List.of(RoutingCatalogBinding.INSTANCE))
                .dsos(RoutingCatalogBinding.INSTANCE)
                .getFirst();
    }

    private RoutingCatalog loaded(Path pack) {
        Pack loaded = new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy)
                .load(PACK_ID);
        assertThat(loaded.violations()).isEmpty();
        return loaded.artifacts().stream()
                .filter(RoutingCatalog.class::isInstance)
                .map(RoutingCatalog.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("the fixture reads into a catalog with exact decimals, its policy and its sources")
    void theFixtureReadsIntoTheDomain() throws IOException {
        RoutingCatalog catalog = loaded(PackFixtures.copyOf(PACK, host));

        assertThat(catalog.kind()).isEqualTo(ArtifactKind.ROUTING_CATALOG);
        assertThat(catalog.localId()).isEqualTo("default");
        assertThat(catalog.taskTypes()).extracting(type -> type.id()).containsExactly("design", "review");
        ModelEntry first = catalog.models().getFirst();
        assertThat(first.taskFit()).extracting(TaskFit::score).containsExactly(new BigDecimal("0.66"), BigDecimal.ONE);
        assertThat(first.taskFit().getFirst().confidence()).contains(Grade.HIGH);
        assertThat(first.cost().tier()).isEqualTo(CostTier.PREMIUM);
        assertThat(first.cost().outputPer1mUsd()).contains(new BigDecimal("75.5"));
        assertThat(first.latencyTier()).contains(Grade.HIGH);
        assertThat(catalog.models().get(1).family()).isEmpty();
        assertThat(catalog.policy().objective()).isEqualTo(RoutingObjective.QUALITY_FIRST);
        assertThat(catalog.policy().weights().latency()).isEqualTo(new BigDecimal("0.05"));
        assertThat(catalog.policy().maxTierFor("review")).contains(CostTier.MEDIUM);
        assertThat(catalog.policy().maxTierFor("design")).isEmpty();
        assertThat(catalog.sources())
                .singleElement()
                .satisfies(source -> assertThat(source.name()).isEqualTo("example"));
    }

    @Test
    @DisplayName("the file writes back byte for byte through the production writer")
    void theFixtureWritesBackByteForByte() throws IOException {
        Path pack = PackFixtures.copyOf(PACK, host);
        String source = Files.readString(pack.resolve(FILE));
        RoutingCatalogDso dso = dso();
        RoutingCatalog catalog = RoutingCatalogBinding.INSTANCE.toEntity(PACK_ID, dso);
        assertThat(RoutingCatalogBinding.INSTANCE.toDso(catalog))
                .as("DSO fidelity")
                .isEqualTo(dso);

        Files.delete(pack.resolve(FILE));
        new FilesystemPackStore(host, strategy)
                .write(PACK_ID, RoutingCatalogBinding.INSTANCE, RoutingCatalogBinding.INSTANCE.toDso(catalog));

        assertThat(pack.resolve(FILE)).hasContent(source);
    }

    @Test
    @DisplayName("a catalog's file is routing_catalogs/<id>.routing-catalog.yaml, its id the file stem")
    void theFileNameIsTheId() {
        assertThat(RoutingCatalogBinding.INSTANCE.fileName("default")).isEqualTo(FILE);
        assertThat(RoutingCatalogBinding.INSTANCE.idIsFileStem()).isTrue();
        assertThat(RoutingCatalogBinding.INSTANCE.placedAtItsFileName()).isTrue();
    }
}
