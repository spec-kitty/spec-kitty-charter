package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a guard: a flat mirror of the fields of a {@code *.guard.yaml} (ADR-0054 decision 1), not
 * the domain entity. Its record equality is the round-trip fidelity assertion: an absent optional differs from a
 * present one, and a stated default ({@code network: false}, {@code on_indeterminate: block}) is kept as stated.
 *
 * @param id the guard's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param title the {@code title}
 * @param description the {@code description}, when stated
 * @param interfaceName the {@code interface}
 * @param implementedBy the {@code implemented_by}, as authored
 * @param configSchema the {@code config_schema}, as the tree it was authored as, when stated
 * @param limits the {@code limits}, when stated
 * @param onIndeterminate the {@code on_indeterminate}, when stated
 */
record GuardDso(
        String id,
        String schemaVersion,
        String title,
        Optional<String> description,
        String interfaceName,
        String implementedBy,
        Optional<ConfigValue> configSchema,
        Optional<Limits> limits,
        Optional<String> onIndeterminate) {

    /**
     * Checks that every component is present.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param title the title
     * @param description the description
     * @param interfaceName the interface
     * @param implementedBy the implementation
     * @param configSchema the config schema
     * @param limits the limits
     * @param onIndeterminate the on-indeterminate token
     */
    GuardDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(interfaceName, "interfaceName");
        Objects.requireNonNull(implementedBy, "implementedBy");
        Objects.requireNonNull(configSchema, "configSchema");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(onIndeterminate, "onIndeterminate");
    }

    /**
     * The {@code limits} block.
     *
     * @param timeoutSeconds the {@code timeout_seconds}, when stated
     * @param network the {@code network}, when stated
     */
    record Limits(Optional<Integer> timeoutSeconds, Optional<Boolean> network) {
        /**
         * Checks that every component is present.
         *
         * @param timeoutSeconds the timeout
         * @param network the network flag
         */
        Limits {
            Objects.requireNonNull(timeoutSeconds, "timeoutSeconds");
            Objects.requireNonNull(network, "network");
        }
    }
}
