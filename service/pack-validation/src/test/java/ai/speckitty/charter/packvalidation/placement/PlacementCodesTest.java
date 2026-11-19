package ai.speckitty.charter.packvalidation.placement;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The placement tokens are built once, here (#554, Q7, S2). */
class PlacementCodesTest {

    @Test
    void thePlacementCodesAndTheirSeverities() {
        assertThat(PlacementCodes.ALL)
                .containsExactlyInAnyOrder(
                        new ReasonCode("pack_level_missing", Severity.WARNING),
                        new ReasonCode("pack_level_missing", Severity.VIOLATION),
                        new ReasonCode("pack_level_mismatch", Severity.VIOLATION),
                        new ReasonCode("org_fragment_missing", Severity.VIOLATION));
        assertThat(PlacementCodes.PACK_LEVEL_MISMATCH.value())
                .as("the mismatch speaks the wire refusal's token")
                .isEqualTo(RefusalCode.PACK_LEVEL_MISMATCH.token());
        assertThat(PlacementCodes.PACK_LEVEL_MISSING_VIOLATION.value())
                .as("the set check's twin speaks the warning's token, so the contract is unchanged")
                .isEqualTo(PlacementCodes.PACK_LEVEL_MISSING.value());
    }

    @Test
    void everyTokenIsSnakeCaseAndBuiltNowhereElse() {
        assertThat(PlacementCodes.ALL)
                .extracting(ReasonCode::value)
                .allMatch(token -> token.matches("[a-z][a-z0-9_]*"));
        assertThat(Stream.concat(PackCodes.ALL.stream(), StorageCodes.ALL.stream())
                        .map(ReasonCode::value))
                .as("a placement token is built in neither PackCodes nor StorageCodes")
                .doesNotContainAnyElementsOf(
                        PlacementCodes.ALL.stream().map(ReasonCode::value).toList());
    }
}
