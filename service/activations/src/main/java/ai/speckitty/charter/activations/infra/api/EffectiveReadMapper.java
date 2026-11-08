package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.ActivationSummary;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.PageInfo;
import ai.speckitty.charter.api.model.SkippedReport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The wire form of the effective reads. */
@Component
public class EffectiveReadMapper {

    /**
     * A mapper that names the activation through the document mapper's paths.
     *
     * @param activations the document mapper
     */
    @Autowired
    public EffectiveReadMapper(ActivationMapper activations) {
        Objects.requireNonNull(activations, "activations");
    }

    /**
     * A page of the effective artifacts.
     *
     * @param page the page
     * @return its wire form
     */
    public EffectiveArtifactList toList(EffectiveReadService.Page page) {
        return new EffectiveArtifactList(
                "", "", "", new ActivationSummary(0, 0, 0, 0, 0, 0), List.of(), new PageInfo(false, null, 0), Map.of());
    }

    /**
     * The skipped report.
     *
     * @param report the report
     * @return its wire form
     */
    public ActivationSkippedReport toSkipped(EffectiveReadService.Report report) {
        return new ActivationSkippedReport("", "", "", new SkippedReport(), Map.of());
    }
}
