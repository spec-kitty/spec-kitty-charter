package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackCodes;
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
    @DisplayName("a token the contract enum spells is the contract's token (A-m1)")
    void aTokenTheContractSpellsIsTheContractsToken() {
        assertThat(StorageCodes.PACK_UNREADABLE.value()).isEqualTo(RefusalCode.PACK_UNREADABLE.token());
        assertThat(StorageCodes.FILE_NOT_HELD.value()).isEqualTo(Diagnostic.Code.FILE_NOT_HELD.token());
    }
}
