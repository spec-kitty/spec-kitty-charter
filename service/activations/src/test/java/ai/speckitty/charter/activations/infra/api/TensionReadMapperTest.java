package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.TensionReadService.Tensions;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.domain.TensionFinding;
import ai.speckitty.charter.activations.domain.TensionSnapshot;
import ai.speckitty.charter.api.model.ActivationTensions;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.kernel.domain.NodeKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The wire form of the tensions read (ADR-0045 decisions 11 and 12): the effective reads of both sides always, and the
 * exclusion of a side only when the read found it excludable, as a concrete PUT.
 */
class TensionReadMapperTest {

    private static final NodeRef A = new NodeRef(NodeKind.DIRECTIVE, "A");
    private static final NodeRef B = new NodeRef(NodeKind.DIRECTIVE, "B");
    private static final NodeRef C = new NodeRef(NodeKind.DIRECTIVE, "C");
    private static final String SELF = "/api/v1/activations/a-1";

    private final TensionReadMapper mapper = new TensionReadMapper(new ActivationMapper("/api/v1"));

    private static StoredActivation stored() {
        return new StoredActivation(
                new ActivationId("a-1"),
                ActivationDocument.builder().build().document(),
                new Revision("sha256:" + "a".repeat(64)));
    }

    private static TensionSnapshot unreconciled(NodeRef source, NodeRef target) {
        return new TensionSnapshot(source, target, Optional.empty(), Optional.of("why"), List.of());
    }

    @Test
    void aFindingLinksBothSidesReadsAndTheExclusionOfEachExcludableSideOnly() {
        Tensions read = new Tensions(
                stored(),
                "sha256:source",
                "sha256:closure",
                List.of(unreconciled(A, B), unreconciled(B, C)),
                List.of(
                        TensionFinding.of(unreconciled(A, B), false, true),
                        TensionFinding.of(unreconciled(B, C), false, false)));

        ActivationTensions wire = mapper.toTensions(read);

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getLinks()).containsOnlyKeys("self");
        assertThat(wire.getLinks().get("self").getHref()).isEqualTo(SELF + "/tensions");
        assertThat(wire.getTensions())
                .extracting(t -> t.getSource() + " " + t.getTarget())
                .containsExactly("directive:A directive:B", "directive:B directive:C");
        assertThat(wire.getTensions().getFirst().getReason()).isEqualTo("why");
        assertThat(wire.getFindings().get(0).getCode().getValue()).isEqualTo("unreconciled_tension");
        assertThat(wire.getFindings().get(0).getResolutions())
                .extracting(resolution -> resolution.getValue())
                .containsExactly("reconcile", "deactivate_one_side");
        assertThat(wire.getFindings().get(0).getLinks())
                .containsOnlyKeys("effective-source", "effective-target", "exclude-target");
        Link exclude = wire.getFindings().get(0).getLinks().get("exclude-target");
        assertThat(exclude.getHref()).isEqualTo(SELF + "/exclusions/directive/B");
        assertThat(exclude.getMethod()).isEqualTo("PUT");
        assertThat(exclude.getTemplated()).isNull();
        assertThat(wire.getFindings().get(0).getLinks().get("effective-source").getHref())
                .isEqualTo(SELF + "/artifacts/directive/A");
        assertThat(wire.getFindings().get(1).getLinks()).containsOnlyKeys("effective-source", "effective-target");
        assertThat(wire.getFindings().get(1).getResolutions())
                .extracting(resolution -> resolution.getValue())
                .containsExactly("reconcile");
    }
}
