package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How a pack fault is stated: the one helper that encodes a place as a JSON pointer (RFC 6901). */
class FindingsTest {

    @Test
    void aFindingIsAFailingReasonOfItsCodeAtItsFile() {
        Reason finding = Findings.at(PackCodes.DUPLICATE_STEP_ID, "a.yaml", List.of(), "a.yaml: d");

        assertThat(finding.isFailing()).isTrue();
        assertThat(finding.code()).isEqualTo(PackCodes.DUPLICATE_STEP_ID);
        assertThat(finding.rule()).isEqualTo(PackCodes.DUPLICATE_STEP_ID.value());
        assertThat(finding.location()).isEqualTo(Location.in("a.yaml"));
        assertThat(finding.detail()).isEqualTo("a.yaml: d");
    }

    @Test
    void aSegmentHoldingASlashOrATildeIsEscapedInThePointer() {
        assertThat(Findings.at(PackCodes.DRG_SCHEMA_ERROR, "a.yaml", List.of("x/y", "~z", "0"), "d")
                        .location()
                        .pointer())
                .isEqualTo("/x~1y/~0z/0");
    }
}
