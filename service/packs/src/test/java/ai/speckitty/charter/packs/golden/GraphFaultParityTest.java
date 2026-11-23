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
 * (#617): pointer, code and detail, in registration's order. A fault at an edge points at it, or at its field when the
 * schema names one ({@code <file>#/edges/<index>}); a document that is not YAML is pointed at whole. The golden
 * subjects plant one broken document each, with a schema fault; this pack breaks seven documents: one that is not YAML,
 * one with several malformed qualifiers, three with an edge that joins an artifact to itself (bare, through the pack's
 * own name, and twice the same edge in one document), one whose reason names its own file and a fragment with an
 * unknown relation. It puts them where the read order and registration's path order differ (a root shard is read before
 * the fragment; {@code drg/} sorts first).
 */
@DisplayName("a composing read lists a registration's violations for unreadable graph documents (#617)")
class GraphFaultParityTest {

    private static final String PACK = ExamplePacks.BUILT_IN;

    @Test
    @DisplayName("a shard that is not YAML, a shard with malformed qualifiers, two that join an artifact"
            + " to itself, one whose fault names its own file and a fragment with an unknown relation: the same"
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
                .filter(violation -> violation.pointer().startsWith("drg/fragment.yaml")
                        || violation.pointer().startsWith("zz-"))
                .toList();
        assertThat(atGraphs)
                .extracting(RefusalViolation::pointer)
                .containsExactly(
                        "drg/fragment.yaml#/edges/0/relation",
                        "zz-bare-self.graph.yaml#/edges/0",
                        "zz-broken.graph.yaml",
                        "zz-named.graph.yaml#/edges/0/relation",
                        "zz-own-self.graph.yaml#/edges/0",
                        "zz-qualifiers.graph.yaml#/edges/0",
                        "zz-qualifiers.graph.yaml#/edges/1",
                        "zz-twice-self.graph.yaml#/edges/0",
                        "zz-twice-self.graph.yaml#/edges/1");
        assertThat(compose.violations()).isEqualTo(atGraphs);
        assertThat(compose.violations())
                .extracting(RefusalViolation::code)
                .as("a graph document that is not YAML is a drg_schema_error, as a registration codes it")
                .containsOnly("drg_schema_error");
        assertThat(compose.getMessage())
                .isEqualTo("a graph document of " + PACK + " cannot be read: " + PACK + "/drg/fragment.yaml: "
                        + "unknown relation 'in_tension_with'");

        CharterRefusal strict = catchThrowableOfType(
                CharterRefusal.class, () -> new WholePackStore(new YamlSerializationStrategy()).load(composed));
        assertThat(strict.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR);
        assertThat(strict.getMessage())
                .as("the strict load names the first by path too")
                .endsWith("/drg/fragment.yaml: unknown relation 'in_tension_with'");
    }

    @Test
    @DisplayName("a fragment node whose body_path leaves the pack is pointed at, /nodes/<index>, by the read and the"
            + " registration alike")
    void anEscapingNodeIsPointedAtByBoth(@TempDir Path host) throws IOException {
        Path composed = escaping(Files.createDirectories(host.resolve("compose")));
        Path registered = escaping(Files.createDirectories(host.resolve("register")));

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

        List<RefusalViolation> atFragment = registration.violations().stream()
                .filter(violation -> violation.pointer().startsWith("drg/fragment.yaml"))
                .toList();
        assertThat(atFragment).extracting(RefusalViolation::pointer).containsExactly("drg/fragment.yaml#/nodes/1");
        assertThat(compose.violations()).isEqualTo(atFragment);
    }

    private static Path escaping(Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(PACK, host);
        Files.createDirectories(pack.resolve("drg"));
        Files.writeString(pack.resolve("drg/fragment.yaml"), """
                nodes:
                - id: INSIDE
                  kind: directives
                - id: OUTSIDE
                  kind: directives
                  body_path: ../outside.directive.yaml
                edges: []
                """, StandardCharsets.UTF_8);
        return pack;
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
        Files.writeString(pack.resolve("zz-named.graph.yaml"), """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'directive:A', target: 'directive:B', relation: zz-named.graph.yaml}
                """, StandardCharsets.UTF_8);
        String self = """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: '%1$s', target: '%1$s', relation: requires}
                """;
        Files.writeString(
                pack.resolve("zz-bare-self.graph.yaml"),
                self.formatted("tactic:acceptance-test-first"),
                StandardCharsets.UTF_8);
        Files.writeString(
                pack.resolve("zz-own-self.graph.yaml"),
                self.formatted(PACK + "::tactic:tdd-red-green-refactor"),
                StandardCharsets.UTF_8);
        // Two equal edges that join an artifact to itself: two faults, one at each edge.
        Files.writeString(
                pack.resolve("zz-twice-self.graph.yaml"),
                (self + "- {source: '%1$s', target: '%1$s', relation: requires}\n")
                        .formatted(PACK + "::tactic:tdd-red-green-refactor"),
                StandardCharsets.UTF_8);
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
