package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackElementViolation;
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

    @Test
    void aFaultAtAnEdgePointsAtItsIndexInTheEdgesList() {
        PackElementViolation violation = Findings.atEdge("drg/directive.graph.yaml", 3, PackCodes.TENSION_SELF, "d");

        assertThat(violation.file()).isEqualTo("drg/directive.graph.yaml");
        assertThat(violation.pointer()).isEqualTo("/edges/3");
    }

    @Test
    void aViolationIsStatedAtItsFileAndThePlaceInIt() {
        Reason finding = Findings.of(new PackElementViolation("a.yaml", "/edges/1", PackCodes.DRG_SCHEMA_ERROR, "d"));

        assertThat(finding.location()).isEqualTo(Location.in("a.yaml").at("/edges/1"));
        assertThat(finding.code()).isEqualTo(PackCodes.DRG_SCHEMA_ERROR);
        assertThat(finding.detail()).isEqualTo("d");
        assertThat(Findings.of(PackElementViolation.wholeFile("a.yaml", PackCodes.DRG_SCHEMA_ERROR, "d"))
                        .location())
                .as("a violation of the whole file points at the file")
                .isEqualTo(Location.in("a.yaml"));
    }

    @Test
    void aPointerIsTheEscapedSegmentsAndNoneIsTheWholeFile() {
        assertThat(Findings.pointer(List.of("a/b", "~c"))).isEqualTo("/a~1b/~0c");
        assertThat(Findings.pointer(List.of())).isEmpty();
        assertThat(Findings.segments(Findings.pointer(List.of("a/b", "~c")))).containsExactly("a/b", "~c");
    }
}
