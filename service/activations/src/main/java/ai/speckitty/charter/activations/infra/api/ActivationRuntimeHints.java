package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationChange;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.DeactivatedArtifacts;
import ai.speckitty.charter.api.model.EffectiveArtifact;
import ai.speckitty.charter.api.model.EffectiveArtifactRead;
import ai.speckitty.charter.api.model.EffectiveArtifactSuggestion;
import ai.speckitty.charter.api.model.EffectiveContextArtifact;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.api.model.SharedSkip;
import ai.speckitty.charter.api.model.SkippedDeactivated;
import ai.speckitty.charter.api.model.SkippedReport;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * What a native executable needs to write and read the activation documents as {@link ActivationJson} rules (ADR-0046
 * decision 5): the mix-in is read only by reflection, and the DTOs it reaches bind through their accessors. Without
 * these hints the native executable writes the {@code null} members of an activation answer, or an empty body. The
 * answer of the per-artifact and preset writes ({@code ActivationChange} and the skipped report, references and links
 * it reaches) is registered too, so the native executable writes it as the JVM does.
 * Imported by {@link ActivationJson}, which owns the mix-in.
 */
public class ActivationRuntimeHints implements RuntimeHintsRegistrar {

    /** For Spring, which instantiates the registrar named by {@code @ImportRuntimeHints}. */
    public ActivationRuntimeHints() {}

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar()
                .registerReflectionHints(
                        hints.reflection(),
                        Activation.class,
                        ActivationConfig.class,
                        PackPin.class,
                        SelectedArtifacts.class,
                        DeactivatedArtifacts.class,
                        SelectionRequest.class,
                        ExclusionRequest.class,
                        PresetApplication.class,
                        ActivationChange.class,
                        EffectiveArtifact.class,
                        EffectiveArtifactRead.class,
                        EffectiveArtifactSuggestion.class,
                        EffectiveContextArtifact.class,
                        EffectiveGraphNode.class,
                        SkippedReport.class,
                        SkippedDeactivated.class,
                        SharedSkip.class,
                        ArtifactReference.class,
                        Link.class);
        // The mix-in is read only for its class annotation, so the type alone is registered.
        hints.reflection().registerType(ActivationJson.omitAbsentMembers());
    }
}
