package ai.speckitty.charter.packs.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.PackRegistrationService;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The composing read and a registration list the same violations for a pack whose graph documents cannot be read
 * (#617): pointer, code and detail, in registration's order. The golden subjects plant one broken document each, with
 * a schema fault; this pack breaks three documents, one that is not YAML and one with several malformed qualifiers, and
 * puts them where the read order and registration's path order differ (a root shard is read before the fragment;
 * {@code drg/} sorts first).
 */
@DisplayName("a composing read lists a registration's violations for unreadable graph documents (#617)")
class GraphFaultParityTest {

    private static final String PACK = ExamplePacks.BUILT_IN;

    @Test
    @DisplayName(
            "a shard that is not YAML, a shard with malformed qualifiers and a fragment with an unknown relation: the same"
                    + " violations, in path order")
    void brokenDocumentsAreListedAsARegistrationListsThem(@TempDir Path host) throws IOException {
        Path composed = broken(Files.createDirectories(host.resolve("compose")));
        Path registered = broken(Files.createDirectories(host.resolve("register")));

        CharterRefusal compose = catchThrowableOfType(
                CharterRefusal.class,
                () -> new DomainRepositoryService(
                                () -> List.of(new PackLocation(PACK, PackLevel.BUILT_IN, composed)),
                                new YamlSerializationStrategy())
                        .compositionFacts(new PackIdentifier(PACK)));
        CharterRefusal registration = catchThrowableOfType(
                CharterRefusal.class,
                () -> PackRegistrationService.readUpload(
                        new FileSystemPackInspector(),
                        new WholePackStore(new YamlSerializationStrategy()),
                        PACK,
                        new StagedPack(registered)));

        assertThat(compose.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        List<RefusalViolation> atGraphs = registration.violations().stream()
                .filter(violation -> violation.pointer().equals("drg/fragment.yaml")
                        || violation.pointer().startsWith("zz-"))
                .toList();
        assertThat(atGraphs)
                .extracting(RefusalViolation::pointer)
                .containsExactly(
                        "drg/fragment.yaml",
                        "zz-broken.graph.yaml",
                        "zz-qualifiers.graph.yaml",
                        "zz-qualifiers.graph.yaml");
        assertThat(compose.violations()).isEqualTo(atGraphs);
        assertThat(compose.violations())
                .extracting(RefusalViolation::code)
                .as("a graph document that is not YAML is a drg_schema_error, as a registration codes it")
                .containsOnly("drg_schema_error");
        assertThat(compose.getMessage())
                .isEqualTo("a graph document of " + PACK + " cannot be read: " + PACK + "/drg/fragment.yaml: "
                        + "unknown relation 'in_tension_with'");
    }

    private static Path broken(Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(PACK, host);
        Files.writeString(pack.resolve("zz-broken.graph.yaml"), "nodes: [valid\n", StandardCharsets.UTF_8);
        // Two edges, the first malformed at both ends: one fault per edge.
        String over = "p".repeat(PackIdentifier.MAX_LENGTH + 1);
        Files.writeString(pack.resolve("zz-qualifiers.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: '%1$s::directive:A', target: '%1$s::directive:B', relation: requires}
                - {source: 'directive:A', target: '%1$sq::directive:B', relation: requires}
                """.formatted(over), StandardCharsets.UTF_8);
        Files.createDirectories(pack.resolve("drg"));
        Files.writeString(pack.resolve("drg/fragment.yaml"), """
                nodes: []
                edges:
                - source: directive:A
                  target: directive:B
                  relation: in_tension_with
                """, StandardCharsets.UTF_8);
        return pack;
    }
}
