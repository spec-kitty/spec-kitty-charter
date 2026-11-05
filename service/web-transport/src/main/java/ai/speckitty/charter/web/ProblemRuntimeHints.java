package ai.speckitty.charter.web;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.Problem;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * What a native executable needs to write the shared problem bodies (ADR-0046 decision 5). Spring AOT registers
 * the bodies it sees in a controller's signature; a slice adapter's guard or error valve writes a {@link Problem}
 * outside any, and {@link ProblemHandler#unexpected} returns one as an {@code Object}. Without these hints the native
 * executable writes those bodies empty. The hypermedia {@link Link}, which {@link ProblemJson} mixes in for every slice,
 * binds through its accessors too. Imported by {@link ProblemJson}, which owns the mix-in.
 */
public class ProblemRuntimeHints implements RuntimeHintsRegistrar {

    /** For Spring, which instantiates the registrar named by {@code @ImportRuntimeHints}. */
    public ProblemRuntimeHints() {}

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar()
                .registerReflectionHints(hints.reflection(), Problem.class, CharterRefusal.class, Link.class);
        // The mix-in is read only for its class annotation, so the type alone is registered.
        hints.reflection().registerType(ProblemJson.omitAbsentMembers());
    }
}
