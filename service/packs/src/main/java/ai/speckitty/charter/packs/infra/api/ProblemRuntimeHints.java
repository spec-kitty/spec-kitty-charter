package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Problem;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * What a native executable needs to write this adapter's problem bodies (ADR-0046 decision 5). Spring AOT registers
 * the bodies it sees in a controller's signature; the token guard and the connector's error valve write a
 * {@link Problem} outside any, and {@link ProblemHandler#unexpected} returns one as an {@code Object}. Without these
 * hints the native executable writes those bodies empty. Imported by {@link ProblemJson}, which owns the mix-in. The
 * error valve needs none: it is added to Tomcat's pipeline as an instance, so Tomcat never creates it by name.
 */
public class ProblemRuntimeHints implements RuntimeHintsRegistrar {

    /** For Spring, which instantiates the registrar named by {@code @ImportRuntimeHints}. */
    public ProblemRuntimeHints() {}

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar()
                .registerReflectionHints(hints.reflection(), Problem.class, CharterRefusal.class);
        // The mix-in is read only for its class annotation, so the type alone is registered.
        hints.reflection().registerType(ProblemJson.omitAbsentMembers());
    }
}
