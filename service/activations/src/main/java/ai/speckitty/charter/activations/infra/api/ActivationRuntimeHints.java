package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.DeactivatedArtifacts;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.api.model.SelectionRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * What a native executable needs to write and read the activation documents as {@link ActivationJson} rules (ADR-0046
 * decision 5): the mix-in is read only by reflection, and the DTOs it reaches bind through their accessors. Without
 * these hints the native executable writes the {@code null} members of an activation answer, or an empty body.
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
                        ActivationConfig.class,
                        PackPin.class,
                        SelectedArtifacts.class,
                        DeactivatedArtifacts.class,
                        SelectionRequest.class,
                        ExclusionRequest.class,
                        PresetApplication.class,
                        Link.class);
        // The mix-in is read only for its class annotation, so the type alone is registered.
        hints.reflection().registerType(ActivationJson.omitAbsentMembers());
    }
}
