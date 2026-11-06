package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real packs of the pinned spec-kitty-open-packs checkout compose through the service's own read: the production
 * {@link DomainRepositoryService} over the checkout's directories, and the offering read over it (#363). Each test
 * asserts the served packs, not only that nothing was thrown, because a refusal must not be able to turn into a success
 * unnoticed (#360). The refusal tests change one thing in a throwaway copy of one pack; the checkout stays read-only, and
 * {@code built-in} is always read from it.
 *
 * <p>A pack's qualifier is its descriptor's {@code name}, which need not be its directory: {@code packs/qa} answers to
 * {@code quality-assurance}.
 */
@Tag("open-packs")
@DisplayName("The real open-packs packs compose through the offering read (#363)")
class OpenPacksComposeTest {

    private static final String BUILT_IN_PACK_ID = "01ARWG13C000000000000000FG";
    private static final String BUILT_IN_DEPENDENCY =
            "depends_on:\n  - pack_id: " + BUILT_IN_PACK_ID + "\n    range: \">=1.0,<2\"\n";
    private static final String QA_EDGE_TO_BUILT_IN = "target: built-in::tactic:bdd-scenario-formulation";
    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @TempDir
    private Path scratch;

    @BeforeEach
    void theCheckoutIsThePin() {
        OpenPacksCorpus.assertPinned();
    }

    private static PackLocation real(String directory) {
        return new PackLocation(
                directory, level(directory), OpenPacksCorpus.packsDirectory().resolve(directory));
    }

    private static PackLevel level(String directory) {
        return directory.equals("built-in") ? PackLevel.BUILT_IN : PackLevel.ORG;
    }

    private static List<OfferingEntry> offering(PackLocation... packs) {
        DomainRepositoryService store =
                new DomainRepositoryService(() -> List.of(packs), new YamlSerializationStrategy());
        OfferingReadService read = new OfferingReadService(store, store, store, new ConsistentRead(() -> DIGEST));
        return read.offering().value();
    }

    /** A copy of the real pack with {@code from} replaced by {@code to} in one file; fails if that changes nothing. */
    private PackLocation changed(String directory, String file, String from, String to) throws IOException {
        Path source = OpenPacksCorpus.packsDirectory().resolve(directory);
        Path copy = scratch.resolve(directory);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path target = copy.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target);
                }
            }
        }
        Path edited = copy.resolve(file);
        String before = Files.readString(edited, StandardCharsets.UTF_8);
        assertThat(before).as("the change this test makes").contains(from);
        Files.writeString(edited, before.replace(from, to), StandardCharsets.UTF_8);
        return new PackLocation(directory, level(directory), copy);
    }

    private static void assertRefused(RefusalCode code, String namedPack, PackLocation... packs) {
        assertThatThrownBy(() -> offering(packs)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(code);
            assertThat(refusal.packs()).extracting(RefusalPack::name).first().isEqualTo(namedPack);
        });
    }

    /** The edge a pack's descriptor declares on built-in, as the offering serves it. */
    private static void assertServesItsDependencyOnBuiltIn(OfferingEntry pack) {
        assertThat(pack.dependencies())
                .extracting(ResolvedEdge::to, ResolvedEdge::range)
                .containsExactly(tuple(BUILT_IN_PACK_ID, ">=1.0,<2"));
    }

    @Test
    @DisplayName("quality-assurance composes with built-in")
    void qualityAssuranceWithBuiltIn() {
        List<OfferingEntry> served = offering(real("built-in"), real("qa"));

        assertThat(served).extracting(OfferingEntry::name).containsExactly("built-in", "quality-assurance");
        assertServesItsDependencyOnBuiltIn(served.get(1));
    }

    @Test
    @DisplayName("cave-kitty composes with built-in and serves the dependency no reference of it would catch")
    void caveKittyWithBuiltIn() {
        List<OfferingEntry> served = offering(real("built-in"), real("cave-kitty"));

        assertThat(served).extracting(OfferingEntry::name).containsExactly("built-in", "cave-kitty");
        // cave-kitty writes no reference to built-in, so removing its depends_on is not refused (see below): this
        // assertion is what goes red if the dependency leaves the real pack.
        assertServesItsDependencyOnBuiltIn(served.get(1));
    }

    @Test
    @DisplayName("event-driven composes with built-in")
    void eventDrivenWithBuiltIn() {
        List<OfferingEntry> served = offering(real("built-in"), real("event-driven"));

        assertThat(served).extracting(OfferingEntry::name).containsExactly("built-in", "event-driven");
        assertServesItsDependencyOnBuiltIn(served.get(1));
    }

    @Test
    @DisplayName("spec-kitty-internal composes with built-in")
    void internalWithBuiltIn() {
        List<OfferingEntry> served = offering(real("built-in"), real("internal"));

        assertThat(served).extracting(OfferingEntry::name).containsExactly("built-in", "spec-kitty-internal");
        assertServesItsDependencyOnBuiltIn(served.get(1));
    }

    @Test
    @DisplayName("all the migrated packs compose together")
    void everyMigratedPackTogether() {
        List<OfferingEntry> served =
                offering(real("built-in"), real("cave-kitty"), real("event-driven"), real("internal"), real("qa"));

        assertThat(served)
                .extracting(OfferingEntry::name)
                .containsExactly("built-in", "cave-kitty", "event-driven", "spec-kitty-internal", "quality-assurance");
    }

    @Test
    @DisplayName("a reference put back to its bare form is refused, naming the pack")
    void aBareReferenceIsRefused() throws IOException {
        PackLocation bare =
                changed("qa", "drg/fragment.yaml", QA_EDGE_TO_BUILT_IN, "target: tactic:bdd-scenario-formulation");

        assertRefused(RefusalCode.PACK_REFERENCE_UNQUALIFIED, "quality-assurance", real("built-in"), bare);
    }

    @Test
    @DisplayName("a directive's cross-pack edge put back to bare is refused, naming the pack that wrote it (ADR-0063)")
    void aBareCrossPackEdgeOfADirectiveIsRefused() throws IOException {
        PackLocation bare = changed(
                "event-driven",
                "drg/fragment.yaml",
                "target: built-in::tactic:anti-corruption-layer",
                "target: tactic:anti-corruption-layer");

        assertRefused(RefusalCode.PACK_REFERENCE_UNQUALIFIED, "event-driven", real("built-in"), bare);
    }

    @Test
    @DisplayName("qualified references with the depends_on removed are refused")
    void aRemovedDependencyIsRefused() throws IOException {
        PackLocation independent = changed("qa", "pack.yaml", BUILT_IN_DEPENDENCY, "");

        assertRefused(RefusalCode.PACK_REFERENCE_PACK_MISSING, "quality-assurance", real("built-in"), independent);
    }

    @Test
    @DisplayName("a dependency on a pack id no configured pack declares is refused")
    void anUnknownDependencyIsRefused() throws IOException {
        PackLocation unknown = changed("qa", "pack.yaml", BUILT_IN_PACK_ID, "01ARWG13C000000000000000ZZ");

        assertRefused(RefusalCode.PACK_DEPENDENCY_UNKNOWN, "quality-assurance", real("built-in"), unknown);
    }

    @Test
    @DisplayName("a dependency range that is no PEP 440 specifier is refused")
    void aMalformedRangeIsRefused() throws IOException {
        PackLocation malformed = changed("qa", "pack.yaml", ">=1.0,<2", "banana");

        // A malformed range makes the descriptor unreadable before the resolver runs, so this refusal lists no pack;
        // its
        // detail names the configured directory.
        assertThatThrownBy(() -> offering(real("built-in"), malformed))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessageContaining("pack.yaml of qa: depends_on[0] range 'banana'");
    }

    @Test
    @DisplayName("cave-kitty without its depends_on composes and serves no edge: only the served edge can tell")
    void caveKittyWithoutItsDependencyIsNotRefused() throws IOException {
        PackLocation independent = changed("cave-kitty", "pack.yaml", BUILT_IN_DEPENDENCY, "");

        List<OfferingEntry> served = offering(real("built-in"), independent);

        assertThat(served).extracting(OfferingEntry::name).containsExactly("built-in", "cave-kitty");
        assertThat(served.get(1).dependencies()).isEmpty();
    }
}
