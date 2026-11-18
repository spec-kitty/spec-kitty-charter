package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packvalidation.PackCheck;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An asset id outside the kebab-case pattern is invalid content (FR-006 of slice A2): the planted pack
 * {@code planted/asset-id-not-kebab} holds one asset sidecar whose id is {@code Run}, and validate mode, the store's
 * load and the registration's staged load each refuse it by the {@code asset} schema's {@code id} pattern. The same pack
 * with the id written {@code run} is the control.
 */
@DisplayName("an asset id that is not one kebab-case segment is refused (FR-006)")
class AssetIdNotKebabTest {

    private static final String PLANT = "asset-id-not-kebab";
    private static final String SIDECAR = "assets/run.py.asset.yaml";

    @Test
    @DisplayName("validate mode: one blocking artifact_schema_error on the sidecar, at its id")
    void validateModeRefusesIt(@TempDir Path host) {
        Path pack = planted(host);

        List<Reason> findings = new PackCheck(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()))
                .findings();

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
            assertThat(finding.location().file()).isEqualTo(SIDECAR);
            assertThat(finding.detail()).contains("id");
        });
    }

    @Test
    @DisplayName("the store's load: the sidecar is a violation of the pack, so the pack does not load clean")
    void theLoadRefusesIt(@TempDir Path host) {
        Path pack = planted(host);

        WholePackStore.LoadedPack loaded = new WholePackStore(new YamlSerializationStrategy()).load(pack);

        assertThat(loaded.pack().violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.file()).isEqualTo(SIDECAR));
        assertThat(loaded.pack().assets()).isEmpty();
    }

    @Test
    @DisplayName("registration: 422 validation_failed naming the sidecar")
    void registrationRefusesIt(@TempDir Path host) {
        Path pack = planted(host);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThatThrownBy(() -> StagedReads.contents(store, pack))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .singleElement()
                            .satisfies(
                                    violation -> assertThat(violation.detail()).contains(SIDECAR));
                });
    }

    @Test
    @DisplayName("control: the same pack with the id run validates, loads and registers")
    void theKebabIdIsFine(@TempDir Path host) throws IOException {
        Path pack = planted(host);
        Path sidecar = pack.resolve(SIDECAR);
        Files.writeString(
                sidecar,
                Files.readString(sidecar, StandardCharsets.UTF_8).replace("id: Run", "id: run"),
                StandardCharsets.UTF_8);
        WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

        assertThat(new PackCheck(new FileSystemPackInspector())
                        .validate(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()))
                        .findings())
                .isEmpty();
        assertThat(store.load(pack).pack().violations()).isEmpty();
        assertThat(StagedReads.contents(store, pack).pack().assets())
                .extracting(asset -> asset.localId())
                .containsExactly("run");
    }

    /** A copy of the planted pack under {@code host}, so a test may change it. */
    private static Path planted(Path host) {
        return PlantedPacks.copyOf(PLANT, host);
    }
}
