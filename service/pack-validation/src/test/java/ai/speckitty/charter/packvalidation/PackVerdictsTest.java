package ai.speckitty.charter.packvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Validate mode's mapper (#554, FR-008, N2): the failing reasons in precedence order, beside the classified files. */
class PackVerdictsTest {

    private static Reason at(ReasonCode code, String file) {
        return new Reason(code.value(), Origin.NONE, Evaluation.FAIL, code, Location.in(file), code.value());
    }

    static List<ReasonCode> placementCodes() {
        return List.copyOf(PlacementCodes.ALL);
    }

    @ParameterizedTest
    @MethodSource("placementCodes")
    void eachPlacementCodeSortsInThePackWideGroup(ReasonCode code) {
        Reason descriptor = at(PackCodes.DRG_SCHEMA_ERROR, "pack.yaml");
        Reason shard = at(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml");
        Reason fragment = at(PackCodes.DRG_SCHEMA_ERROR, "drg/fragment.yaml");
        Reason placement = at(code, "pack.yaml");
        Reason other = at(PackCodes.DUPLICATE_ARTIFACT_ID, "a/a.tactic.yaml");

        PackVerdict verdict =
                PackVerdicts.of(List.of(), new Rationale(List.of(other, placement, fragment, shard, descriptor)));

        assertThat(verdict.findings())
                .as("%s: after the graph documents, before every other file", code.value())
                .containsExactly(descriptor, shard, fragment, placement, other);
    }

    @Test
    void theVerdictCarriesTheFilesAndOnlyTheFailingReasons() {
        List<ClassifiedFile> files =
                List.of(new ClassifiedFile("pack.yaml", Optional.of("pack-descriptor"), Optional.empty()));
        Reason failing = at(PackCodes.DUPLICATE_STEP_ID, "a.mission-type.yaml");

        PackVerdict verdict = PackVerdicts.of(files, new Rationale(List.of(Reason.passed("held"), failing)));

        assertThat(verdict.files()).isEqualTo(files);
        assertThat(verdict.findings()).containsExactly(failing);
    }

    @Test
    void theFindingsOfOneFileAreInPlaceOrder() {
        Reason deep = at(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml")
                .at(Location.in("a.graph.yaml").at("/nodes/1"));
        Reason shallow = at(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml")
                .at(Location.in("a.graph.yaml").at("/nodes"));

        assertThat(PackVerdicts.of(List.of(), new Rationale(List.of(deep, shallow)))
                        .findings())
                .containsExactly(shallow, deep);
    }
}
