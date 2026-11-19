package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.TensionReadService.Tensions;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.TensionFinding;
import ai.speckitty.charter.api.model.ActivationTensions;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.TensionFinding.CodeEnum;
import ai.speckitty.charter.api.model.TensionFinding.ResolutionsEnum;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The wire form of {@code getActivationTensions} (ADR-0045 decisions 11 and 12): the tensions as the action context
 * answers them, and each finding with its resolutions and links. {@code effective-source} and {@code effective-target}
 * are always there; {@code exclude-source} and {@code exclude-target} only for a side the exclusion write would accept,
 * each the concrete path of that write with its method, so the read never advertises a write that would refuse.
 */
@Component
public class TensionReadMapper {

    private static final String PUT = "PUT";

    private final ActivationMapper activations;

    /**
     * A mapper over the activation's links.
     *
     * @param activations the mapper whose paths the links expand
     */
    @Autowired
    public TensionReadMapper(ActivationMapper activations) {
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * The tensions read.
     *
     * @param read what the service read
     * @return its wire form
     */
    public ActivationTensions toTensions(Tensions read) {
        String id = read.stored().id().value();
        return new ActivationTensions(
                id,
                read.stored().revision().token(),
                read.governanceFingerprint(),
                read.tensions().stream()
                        .map(EffectiveReadMapper::contextTension)
                        .toList(),
                read.findings().stream().map(finding -> finding(id, finding)).toList(),
                Map.of("self", new Link(activations.tensions(id))));
    }

    private ai.speckitty.charter.api.model.TensionFinding finding(String id, TensionFinding finding) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("effective-source", new Link(effective(id, finding.source())));
        links.put("effective-target", new Link(effective(id, finding.target())));
        if (finding.sourceExcludable()) {
            links.put("exclude-source", new Link(exclusion(id, finding.source())).method(PUT));
        }
        if (finding.targetExcludable()) {
            links.put("exclude-target", new Link(exclusion(id, finding.target())).method(PUT));
        }
        return new ai.speckitty.charter.api.model.TensionFinding(
                CodeEnum.fromValue(TensionFinding.CODE),
                finding.source().urn(),
                finding.target().urn(),
                finding.resolutions().stream()
                        .map(resolution -> ResolutionsEnum.fromValue(resolution.token()))
                        .toList(),
                links);
    }

    private String effective(String id, NodeRef side) {
        return activations.effectiveArtifact(id, side.kind().token(), side.id());
    }

    private String exclusion(String id, NodeRef side) {
        return activations.exclusion(id, side.kind().token(), side.id());
    }
}
