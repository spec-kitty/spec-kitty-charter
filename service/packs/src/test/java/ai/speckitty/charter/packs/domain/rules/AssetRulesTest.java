package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.domain.rules.PlantedFixtures.facts;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AssetRules (#554, T032)")
class AssetRulesTest {

    private static final String SIDECAR = "assets/a.asset.yaml";

    private static List<Reason> escapes(AssetFacts... assets) {
        return AssetRules.PATH_ESCAPES.apply(facts(List.of(), List.of(assets), List.of(), List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../escape.txt", "/etc/passwd", "C:/windows/x.txt", "docs/../../x.txt"})
    @DisplayName("a path that leaves assets/ as written is one asset violation at the sidecar, in the shared wording")
    void aPathThatLeavesAsWrittenEscapes(String path) {
        assertThat(escapes(new AssetFacts(SIDECAR, "a", path, "text/plain", true)))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackCodes.ASSET);
                    assertThat(finding.severity()).isEqualTo(Severity.VIOLATION);
                    assertThat(finding.isFailing()).isTrue();
                    assertThat(finding.rule()).isEqualTo("asset");
                    assertThat(finding.location()).isEqualTo(Location.in(SIDECAR));
                    assertThat(finding.detail())
                            .isEqualTo(SIDECAR + ": asset path '" + path + "' escapes the pack's assets/ root");
                });
    }

    @Test
    @DisplayName("a path the reader resolved outside assets/, through a symbolic link, escapes too")
    void aPathResolvedOutsideEscapes() {
        assertThat(escapes(new AssetFacts(SIDECAR, "a", "out/etc/passwd", "text/plain", false)))
                .extracting(Reason::detail)
                .containsExactly(SIDECAR + ": asset path 'out/etc/passwd' escapes the pack's assets/ root");
    }

    @Test
    @DisplayName("a path inside assets/, or an empty one the schema refuses, does not escape")
    void aContainedOrEmptyPathDoesNotEscape() {
        assertThat(escapes(
                        new AssetFacts(SIDECAR, "a", "logo.png", "image/png", true),
                        new AssetFacts("assets/b.asset.yaml", "b", "", "image/png", false)))
                .isEmpty();
    }

    @Test
    @DisplayName("each asset is judged, in read order")
    void eachAssetIsJudged() {
        assertThat(escapes(
                        new AssetFacts("assets/b.asset.yaml", "b", "../b", "text/plain", true),
                        new AssetFacts(SIDECAR, "a", "ok.txt", "text/plain", true),
                        new AssetFacts("assets/c.asset.yaml", "c", "../c", "text/plain", true)))
                .extracting(reason -> reason.location().file())
                .containsExactly("assets/b.asset.yaml", "assets/c.asset.yaml");
    }

    @Test
    @DisplayName("two sidecars that declare one id are one duplicate_artifact_id at the later sidecar (S2, row 15)")
    void aRepeatedAssetIdIsOneFindingAtTheLaterSidecar() {
        PackContents contents = facts(
                List.of(
                        new ArtifactFacts("assets/b.asset.yaml", "asset", "same"),
                        new ArtifactFacts(SIDECAR, "asset", "same"),
                        new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same")),
                List.of(),
                List.of(),
                List.of());

        assertThat(AssetRules.ID_REPEATED.apply(contents)).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.DUPLICATE_ARTIFACT_ID);
            assertThat(finding.location()).isEqualTo(Location.in("assets/b.asset.yaml"));
            assertThat(finding.detail())
                    .isEqualTo("assets/b.asset.yaml: asset id 'same' is already declared in " + SIDECAR);
        });
    }

    @Test
    @DisplayName("an asset id another kind also declares, or two asset ids, is no repeat")
    void anAssetIdIsUniqueAmongTheSidecarsOnly() {
        PackContents contents = facts(
                List.of(
                        new ArtifactFacts(SIDECAR, "asset", "same"),
                        new ArtifactFacts("assets/b.asset.yaml", "asset", "other"),
                        new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same"),
                        new ArtifactFacts("tactics/b.tactic.yaml", "tactic", "same")),
                List.of(),
                List.of(),
                List.of());

        assertThat(AssetRules.ID_REPEATED.apply(contents)).isEmpty();
    }

    @Test
    @DisplayName("the holder's ruleset is the escape, then the repeated id")
    void theRulesetIsItsRules() {
        PackContents contents = facts(
                List.of(
                        new ArtifactFacts(SIDECAR, "asset", "same"),
                        new ArtifactFacts("assets/b.asset.yaml", "asset", "same")),
                List.of(new AssetFacts(SIDECAR, "same", "../x", "text/plain", true)),
                List.of(),
                List.of());

        assertThat(AssetRules.RULES.apply(contents))
                .extracting(Reason::code, reason -> reason.location().file())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PackCodes.ASSET, SIDECAR),
                        org.assertj.core.groups.Tuple.tuple(PackCodes.DUPLICATE_ARTIFACT_ID, "assets/b.asset.yaml"));
    }

    static List<Planted> planted() {
        return PlantedFixtures.all();
    }

    @ParameterizedTest
    @MethodSource("planted")
    @DisplayName(
            "over every planted and example pack, the escape finds what validate mode finds at a sidecar the read kept")
    void theEscapeFindsWhatValidateModeFinds(Planted planted) {
        // The read refuses a sidecar of its own; the composition skips it (WP07, T035), as validate mode does today.
        Set<String> refused = PlantedFixtures.refusedByTheRead(planted.readout());

        assertThat(AssetRules.PATH_ESCAPES.apply(planted.contents()).stream()
                        .filter(finding -> !refused.contains(finding.location().file())))
                .containsExactlyInAnyOrderElementsOf(
                        PlantedFixtures.policyFindings(planted.readout(), Set.of(PackCodes.ASSET)));
    }

    static List<Planted> expecting() {
        return PlantedFixtures.expecting(Set.of(PackCodes.DUPLICATE_ARTIFACT_ID.value())).stream()
                .filter(planted ->
                        planted.contents().artifacts().stream().anyMatch(artifact -> "asset".equals(artifact.kind())))
                .toList();
    }

    @ParameterizedTest
    @MethodSource("expecting")
    @DisplayName("a plant's expected.json is the oracle of the repeated asset id")
    void thePlantsExpectedFindingsAreTheOracle(Planted planted) {
        assertThat(AssetRules.ID_REPEATED.apply(planted.contents()))
                .extracting(Expected::of)
                .containsExactlyElementsOf(planted.expected().orElseThrow().stream()
                        .filter(expected ->
                                PackCodes.DUPLICATE_ARTIFACT_ID.value().equals(expected.code()))
                        .toList());
    }

    @Test
    @DisplayName("every escaping sidecar of a plant is a sidecar the read refused, so the escape is reported once")
    void everyPlantedEscapeIsTheReadsToo() {
        int escapes = 0;
        for (Planted planted : PlantedFixtures.all()) {
            Set<String> refused = PlantedFixtures.refusedByTheRead(planted.readout());
            List<Reason> found = AssetRules.PATH_ESCAPES.apply(planted.contents());
            assertThat(found)
                    .as(planted.key())
                    .allSatisfy(finding ->
                            assertThat(refused).contains(finding.location().file()));
            escapes += found.size();
        }
        assertThat(escapes).as("the escape plants are judged").isPositive();
    }
}
