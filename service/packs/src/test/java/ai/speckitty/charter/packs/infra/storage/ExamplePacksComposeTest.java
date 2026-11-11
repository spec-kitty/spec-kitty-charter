package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The charter-owned example packs (ADR-0070) compose through the service's own read: the production
 * {@link DomainRepositoryService} over their directories, and the offering read over it (#363). Each test asserts the
 * served packs, not only that nothing was thrown, because a refusal must not be able to turn into a success unnoticed
 * (#360). The refusal tests change one thing in a throwaway copy of one pack; the examples stay read-only.
 */
@DisplayName("The example packs compose through the offering read (#363)")
class ExamplePacksComposeTest {

    private static final String BUILT_IN_PACK_ID = "01ARWG13C000000000000000FG";
    private static final String ORG_PACK_ID = "01M6Y9ACNAAD96XPNQS0XWP2FF";
    private static final String RANGE = ">=1.0,<2";
    private static final String BUILT_IN_DEPENDENCY =
            "depends_on:\n  - pack_id: " + BUILT_IN_PACK_ID + "\n    range: \"" + RANGE + "\"\n";
    private static final String ORG_DEPENDENCY =
            "depends_on:\n  - pack_id: " + ORG_PACK_ID + "\n    range: \"" + RANGE + "\"\n";
    private static final String ORG_EDGE_TO_BUILT_IN = "target: built-in::action:software-dev/specify";
    private static final String IMPACTS_EDGE = """
            - source: directive:PROJECT_001
              target: org-base::directive:DIRECTIVE_038
              relation: impacts
              impacts: 0.5
            """;
    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @TempDir
    private Path scratch;

    private static PackLocation real(String name) {
        return new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name));
    }

    private static List<OfferingEntry> offering(PackLocation... packs) {
        DomainRepositoryService store =
                new DomainRepositoryService(() -> List.of(packs), new YamlSerializationStrategy());
        OfferingReadService read = new OfferingReadService(store, store, store, new ConsistentRead(() -> DIGEST));
        return read.offering().value();
    }

    /** A copy of the example pack with each {@code from} replaced by its {@code to}; fails if an edit changes nothing. */
    private PackLocation changed(String name, String... fileFromTo) throws IOException {
        Path copy = ExamplePacks.copyOf(name, scratch);
        for (int i = 0; i < fileFromTo.length; i += 3) {
            Path edited = copy.resolve(fileFromTo[i]);
            String before = Files.readString(edited, StandardCharsets.UTF_8);
            assertThat(before).as("the change this test makes").contains(fileFromTo[i + 1]);
            Files.writeString(edited, before.replace(fileFromTo[i + 1], fileFromTo[i + 2]), StandardCharsets.UTF_8);
        }
        return new PackLocation(name, ExamplePacks.level(name), copy);
    }

    private static void assertRefused(RefusalCode code, String namedPack, PackLocation... packs) {
        assertThatThrownBy(() -> offering(packs)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(code);
            assertThat(refusal.packs()).extracting(RefusalPack::name).first().isEqualTo(namedPack);
        });
    }

    private static void assertServesItsDependency(OfferingEntry pack, String on) {
        assertThat(pack.dependencies())
                .extracting(ResolvedEdge::to, ResolvedEdge::range)
                .containsExactly(tuple(on, RANGE));
    }

    @Test
    @DisplayName("org-base composes with built-in and serves its dependency on it")
    void orgWithBuiltIn() {
        List<OfferingEntry> served = offering(real(ExamplePacks.BUILT_IN), real(ExamplePacks.ORG));

        assertThat(served).extracting(OfferingEntry::name).containsExactly("built-in", "org-base");
        assertServesItsDependency(served.get(1), BUILT_IN_PACK_ID);
    }

    @Test
    @DisplayName("the served packs are exactly the three examples, each serving its dependency")
    void everyExamplePackTogether() {
        List<OfferingEntry> served = offering(
                ExamplePacks.names().stream().map(ExamplePacksComposeTest::real).toArray(PackLocation[]::new));

        assertThat(served).extracting(OfferingEntry::name).containsExactlyElementsOf(ExamplePacks.names());
        assertThat(served.get(0).dependencies()).isEmpty();
        assertServesItsDependency(served.get(1), BUILT_IN_PACK_ID);
        assertServesItsDependency(served.get(2), ORG_PACK_ID);
    }

    @Test
    @DisplayName("a reference put back to its bare form is refused, naming the pack")
    void aBareReferenceIsRefused() throws IOException {
        PackLocation bare = changed(
                ExamplePacks.ORG, "drg/fragment.yaml", ORG_EDGE_TO_BUILT_IN, "target: action:software-dev/specify");

        assertRefused(RefusalCode.PACK_REFERENCE_UNQUALIFIED, "org-base", real(ExamplePacks.BUILT_IN), bare);
    }

    @Test
    @DisplayName("a directive's cross-pack edge put back to bare is refused, naming the pack that wrote it (ADR-0064)")
    void aBareCrossPackEdgeOfADirectiveIsRefused() throws IOException {
        PackLocation bare = changed(
                ExamplePacks.PROJECT,
                "project.graph.yaml",
                "target: org-base::directive:DIRECTIVE_038",
                "target: directive:DIRECTIVE_038");

        assertRefused(
                RefusalCode.PACK_REFERENCE_UNQUALIFIED,
                "project",
                real(ExamplePacks.BUILT_IN),
                real(ExamplePacks.ORG),
                bare);
    }

    @Test
    @DisplayName("qualified references with the depends_on removed are refused")
    void aRemovedDependencyIsRefused() throws IOException {
        PackLocation independent = changed(ExamplePacks.ORG, "pack.yaml", BUILT_IN_DEPENDENCY, "");

        assertRefused(RefusalCode.PACK_REFERENCE_PACK_MISSING, "org-base", real(ExamplePacks.BUILT_IN), independent);
    }

    @Test
    @DisplayName("a dependency on a pack id no configured pack declares is refused")
    void anUnknownDependencyIsRefused() throws IOException {
        PackLocation unknown = changed(ExamplePacks.ORG, "pack.yaml", BUILT_IN_PACK_ID, "01ARWG13C000000000000000ZZ");

        assertRefused(RefusalCode.PACK_DEPENDENCY_UNKNOWN, "org-base", real(ExamplePacks.BUILT_IN), unknown);
    }

    @Test
    @DisplayName("a dependency range that is no PEP 440 specifier is refused")
    void aMalformedRangeIsRefused() throws IOException {
        PackLocation malformed = changed(ExamplePacks.ORG, "pack.yaml", RANGE, "banana");

        // A malformed range makes the descriptor unreadable before the resolver runs, so this refusal lists no pack;
        // its detail names the configured directory.
        assertThatThrownBy(() -> offering(real(ExamplePacks.BUILT_IN), malformed))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessageContaining("pack.yaml of org-base: depends_on[0] range 'banana'");
    }

    @Test
    @DisplayName("a pack that writes no reference, without its depends_on, composes and serves no edge")
    void aPackWithoutReferencesOrDependencyIsNotRefused() throws IOException {
        // The project example writes its reference to org-base in project.graph.yaml and its pack.yaml overrides: with
        // them and
        // its depends_on gone, nothing refuses, so only the served edge can tell the dependency left.
        PackLocation independent = changed(
                ExamplePacks.PROJECT,
                "pack.yaml",
                ORG_DEPENDENCY,
                "",
                "pack.yaml",
                "overrides:\n  - artifact: styleguide:reasons-canvas-writing\n    member: " + ORG_PACK_ID + "\n",
                "",
                "project.graph.yaml",
                IMPACTS_EDGE,
                "");

        List<OfferingEntry> served = offering(real(ExamplePacks.BUILT_IN), real(ExamplePacks.ORG), independent);

        assertThat(served).extracting(OfferingEntry::name).containsExactlyElementsOf(ExamplePacks.names());
        assertThat(served.get(2).dependencies()).isEmpty();
    }
}
