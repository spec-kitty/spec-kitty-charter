package ai.speckitty.charter.packs.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A transition-gate binding declared on a {@link MissionStepContract}. It is inert data: what a binding does is #155's.
 * Every field is kept as written, and no rule goes beyond what the schema types.
 *
 * @param onTransition the binding's {@code on_transition}
 * @param handler the binding's {@code handler}
 * @param handlerKind the binding's {@code handler_kind} as written, kept as text because its closed set is #155's;
 *     empty when absent
 * @param schemaVersion the binding's {@code schema_version}
 * @param failOpen the binding's {@code fail_open} as written; empty when absent
 * @param provenance the binding's {@code provenance}; empty when absent or null
 */
public record GateBinding(
        String onTransition,
        String handler,
        Optional<String> handlerKind,
        String schemaVersion,
        Optional<Boolean> failOpen,
        Optional<String> provenance) {

    /**
     * Checks that every field is present.
     *
     * @param onTransition the lane transition
     * @param handler the handler name
     * @param handlerKind the handler kind, possibly empty
     * @param schemaVersion the binding's schema version
     * @param failOpen the fail-open marker, possibly empty
     * @param provenance the provenance, possibly empty
     */
    public GateBinding {
        Objects.requireNonNull(onTransition, "onTransition");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(handlerKind, "handlerKind");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(failOpen, "failOpen");
        Objects.requireNonNull(provenance, "provenance");
    }
}
