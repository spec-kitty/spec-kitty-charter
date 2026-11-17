package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.domain.rules.PlantedFixtures.facts;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.PackRelativePaths;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Expected;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("AssetRules (#554, T032)")
class AssetRulesTest {

    private static final String SIDECAR = "assets/a.asset.yaml";

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
    @DisplayName("the holder's ruleset is the repeated id")
    void theRulesetIsItsRules() {
        PackContents contents = facts(
                List.of(
                        new ArtifactFacts(SIDECAR, "asset", "same"),
                        new ArtifactFacts("assets/b.asset.yaml", "asset", "same")),
                List.of(new AssetFacts(SIDECAR, "same", "../x", "text/plain", true)),
                List.of(),
                List.of());

        assertThat(AssetRules.RULES.apply(contents)).hasSize(1).isEqualTo(AssetRules.ID_REPEATED.apply(contents));
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

    /**
     * The read owns the escape (S2, one implementation): every planted sidecar whose path leaves {@code assets/} is a
     * read fault {@code asset} at the sidecar. Written so, in R2 row 13's wording; through a symbolic link, as the
     * missing payload it then names (open case 1, as WP04 settled it).
     */
    @Test
    @DisplayName("every planted escape is one asset read fault at its sidecar, in R2 row 13's wording")
    void everyPlantedEscapeIsAReadFault() {
        int escapes = 0;
        for (Planted planted : PlantedFixtures.all()) {
            List<Reason> readFaults = planted.tree().readFaults();
            for (AssetFacts asset : planted.contents().assets()) {
                if (asset.path().isEmpty()
                        || (asset.containedInAssetsRoot() && !PackRelativePaths.leavesRoot(asset.path()))) {
                    continue;
                }
                escapes++;
                String detail = PackRelativePaths.leavesRoot(asset.path())
                        ? asset.file() + ": asset path '" + asset.path() + "' escapes the pack's assets/ root"
                        : asset.file() + ": payload assets/" + asset.path() + " does not exist";
                assertThat(readFaults)
                        .as(planted.key())
                        .filteredOn(fault -> fault.location().file().equals(asset.file()))
                        .extracting(Reason::code, Reason::location, Reason::detail)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple(
                                PackCodes.ASSET, Location.in(asset.file()), detail));
            }
        }
        assertThat(escapes).as("the escape plants are read").isPositive();
    }
}
