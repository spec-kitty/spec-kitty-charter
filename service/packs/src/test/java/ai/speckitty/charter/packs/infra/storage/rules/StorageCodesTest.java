package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackCodes;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
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
    @DisplayName("no storage token is a token the domain builds, unless storage names the domain's constant (#556)")
    void noStorageTokenIsADomainToken() {
        assertThat(StorageCodes.ORDERED)
                .filteredOn(code -> !domainConstants().contains(code))
                .extracting(ReasonCode::value)
                .doesNotContainAnyElementsOf(
                        PackCodes.ALL.stream().map(ReasonCode::value).toList());
        assertThat(StorageCodes.ORDERED)
                .filteredOn(code -> domainConstants().contains(code))
                .containsExactlyInAnyOrder(StorageCodes.PACK_UNREADABLE, StorageCodes.ASSEMBLY_REFUSED);
    }

    @Test
    @DisplayName("only file_not_held is a warning")
    void onlyFileNotHeldIsAWarning() {
        assertThat(StorageCodes.ORDERED)
                .filteredOn(code -> code.severity() == Severity.WARNING)
                .containsExactly(StorageCodes.FILE_NOT_HELD);
    }

    @Test
    @DisplayName("pack_entry_linked is a violation, ordered before file_not_held (D1)")
    void packEntryLinkedIsAViolation() {
        assertThat(StorageCodes.PACK_ENTRY_LINKED.value()).isEqualTo("pack_entry_linked");
        assertThat(StorageCodes.PACK_ENTRY_LINKED.severity()).isEqualTo(Severity.VIOLATION);
        assertThat(StorageCodes.ALL).contains(StorageCodes.PACK_ENTRY_LINKED);
        assertThat(StorageCodes.ORDERED.indexOf(StorageCodes.PACK_ENTRY_LINKED))
                .isLessThan(StorageCodes.ORDERED.indexOf(StorageCodes.FILE_NOT_HELD));
    }

    @Test
    @DisplayName("a token the contract enum spells is the contract's token (A-m1)")
    void aTokenTheContractSpellsIsTheContractsToken() {
        assertThat(StorageCodes.PACK_UNREADABLE.value()).isEqualTo(RefusalCode.PACK_UNREADABLE.token());
        assertThat(StorageCodes.ASSEMBLY_REFUSED.value()).isEqualTo(RefusalCode.VALIDATION_FAILED.token());
        assertThat(StorageCodes.FILE_NOT_HELD.value()).isEqualTo(Diagnostic.Code.FILE_NOT_HELD.token());
    }

    @Test
    @DisplayName(
            "pack_id_invalid is a storage violation, in the vocabulary once, and no contract or domain token (#583)")
    void packIdInvalidIsAStorageViolation() {
        assertThat(StorageCodes.PACK_ID_INVALID.value()).isEqualTo("pack_id_invalid");
        assertThat(StorageCodes.PACK_ID_INVALID.severity()).isEqualTo(Severity.VIOLATION);
        assertThat(StorageCodes.ORDERED).containsOnlyOnce(StorageCodes.PACK_ID_INVALID);
        assertThat(StorageCodes.ALL).contains(StorageCodes.PACK_ID_INVALID);
        assertThat(RefusalCode.values())
                .extracting(RefusalCode::token)
                .doesNotContain(StorageCodes.PACK_ID_INVALID.value());
    }

    /** The domain's constants, by identity: a storage constant that is one of them names it, and builds nothing. */
    private static Set<ReasonCode> domainConstants() {
        Set<ReasonCode> constants = Collections.newSetFromMap(new IdentityHashMap<>());
        constants.addAll(PackCodes.ALL);
        return constants;
    }
}
