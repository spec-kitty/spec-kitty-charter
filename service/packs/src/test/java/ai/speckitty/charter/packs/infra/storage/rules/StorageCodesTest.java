package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageCodes: the storage edge's codes, each token built once (#554, R2, S3)")
class StorageCodesTest {

    @Test
    @DisplayName("every token is snake case, and ALL holds every constant once")
    void everyTokenIsSnakeCase() {
        assertThat(StorageCodes.ORDERED)
                .allSatisfy(code -> assertThat(code.value()).matches("[a-z][a-z0-9_]*"));
        assertThat(StorageCodes.ALL).hasSameSizeAs(StorageCodes.ORDERED);
    }

    @Test
    @DisplayName("no storage token is a token the domain builds")
    void noStorageTokenIsADomainToken() {
        assertThat(StorageCodes.ORDERED)
                .extracting(ReasonCode::value)
                .doesNotContainAnyElementsOf(
                        PackCodes.ALL.stream().map(ReasonCode::value).toList());
    }

    @Test
    @DisplayName("only file_not_held is a warning")
    void onlyFileNotHeldIsAWarning() {
        assertThat(StorageCodes.ORDERED)
                .filteredOn(code -> code.severity() == Severity.WARNING)
                .containsExactly(StorageCodes.FILE_NOT_HELD);
    }

    @Test
    @DisplayName("each staging twin in PackFindingCode is its StorageCodes constant, token and severity (until WP13)")
    void eachStagingTwinIsItsStorageCode() {
        assertThat(PackFindingCode.PACK_UNREADABLE).isEqualTo(StorageCodes.PACK_UNREADABLE);
        assertThat(PackFindingCode.UNREADABLE).isEqualTo(StorageCodes.UNREADABLE);
        assertThat(PackFindingCode.ARTIFACT_SCHEMA_ERROR).isEqualTo(StorageCodes.ARTIFACT_SCHEMA_ERROR);
        assertThat(PackFindingCode.REQUIRES_CORE_INVALID).isEqualTo(StorageCodes.REQUIRES_CORE_INVALID);
        assertThat(PackFindingCode.PACK_VERSION_INVALID).isEqualTo(StorageCodes.PACK_VERSION_INVALID);
        assertThat(PackFindingCode.DEPENDENCY_RANGE_INVALID).isEqualTo(StorageCodes.DEPENDENCY_RANGE_INVALID);
        assertThat(PackFindingCode.ASSET_MIME_INVALID).isEqualTo(StorageCodes.ASSET_MIME_INVALID);
        assertThat(PackFindingCode.FILE_NOT_HELD).isEqualTo(StorageCodes.FILE_NOT_HELD);
    }

    @Test
    @DisplayName("a token the contract enum spells is the contract's token (A-m1)")
    void aTokenTheContractSpellsIsTheContractsToken() {
        assertThat(StorageCodes.PACK_UNREADABLE.value()).isEqualTo(RefusalCode.PACK_UNREADABLE.token());
        assertThat(StorageCodes.ASSEMBLY_REFUSED.value()).isEqualTo(RefusalCode.VALIDATION_FAILED.token());
        assertThat(StorageCodes.FILE_NOT_HELD.value()).isEqualTo(Diagnostic.Code.FILE_NOT_HELD.token());
    }
}
