package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A guard of a {@link Pack} (ADR-0054): an enforced check a transition names, {@code implemented_by} an executable
 * asset or a closed built-in, run to the {@code guard/1} interface and failing closed. The charter declares it and
 * checks it at load; the client runs it. It carries no severity: whether a failure blocks is the transition entry's
 * {@code blocking}. Two guards are equal when they have the same identity.
 */
public final class Guard implements CharterArtifact {

    /** The one interface a guard is run to today. */
    public static final String INTERFACE = "guard/1";

    private static final ModularRuleset<Guard> RULES = ModularRuleset.<Guard>emptyRules()
            .must(Constraints.of("A guard needs a title that is not blank.", guard -> !guard.title.isBlank()))
            .must(Constraints.of(
                    "A guard needs a schema version that is not blank.", guard -> !guard.schemaVersion.isBlank()))
            .must(Constraints.of(
                    "A guard is run to the guard/1 interface.", guard -> guard.interfaceName.equals(INTERFACE)))
            .must(Constraints.of(
                    "A guard's config_schema is a mapping.",
                    guard -> guard.configSchema
                            .map(ConfigValue.Mapping.class::isInstance)
                            .orElse(true)));

    private final GuardIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final Optional<String> description;
    private final String interfaceName;
    private final GuardImplementation implementation;
    private final Optional<ConfigValue> configSchema;
    private final Optional<GuardLimits> limits;
    private final Optional<OnIndeterminate> onIndeterminate;

    /**
     * Reconstructs a guard from its parts.
     *
     * @param id the guard's identity
     * @param packId the pack that owns the guard
     * @param schemaVersion the schema version text
     * @param title the display label
     * @param description the description, possibly empty
     * @param interfaceName the interface it is run to, {@value #INTERFACE}
     * @param implementation what implements it
     * @param configSchema the JSON Schema an entry's config is checked against, possibly empty; it is held as a tree
     *     and checked as a schema by the store, which knows JSON Schema
     * @param limits the run limits, possibly empty
     * @param onIndeterminate what could-not-evaluate counts as, possibly empty (block)
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public Guard(
            GuardIdentifier id,
            PackIdentifier packId,
            String schemaVersion,
            String title,
            Optional<String> description,
            String interfaceName,
            GuardImplementation implementation,
            Optional<ConfigValue> configSchema,
            Optional<GuardLimits> limits,
            Optional<OnIndeterminate> onIndeterminate) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.interfaceName = Objects.requireNonNull(interfaceName, "interfaceName");
        this.implementation = Objects.requireNonNull(implementation, "implementation");
        this.configSchema = Objects.requireNonNull(configSchema, "configSchema");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.onIndeterminate = Objects.requireNonNull(onIndeterminate, "onIndeterminate");
        RULES.guard(this);
    }

    /**
     * The guard's identity.
     *
     * @return the identifier
     */
    public GuardIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GUARD;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    @Override
    public String label() {
        return title;
    }

    @Override
    public Optional<String> description() {
        return description;
    }

    /**
     * The one relationship a guard originates: {@code implemented_by} its asset, derived from the field (ADR-0054
     * decision 3). It is derived runtime data, never authored: the write refuses a derived relation and the round trip
     * never compares it (ADR-0064). A built-in has no node, so it derives none; neither does an asset of another pack, which the load
     * refuses until the pack dependency closure (ADR-0048) resolves it, and which a URN cannot yet qualify.
     *
     * @return the derived relationship, or an empty list
     */
    @Override
    public List<Relationship> derivedRelationships() {
        if (implementation instanceof GuardImplementation.AssetBacked asset
                && asset.pack().isEmpty()) {
            return List.of(
                    Relationship.of(urn(), new Urn(NodeKind.ASSET, asset.assetId()), RelationType.IMPLEMENTED_BY));
        }
        return List.of();
    }

    /**
     * The guard's {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * The interface the guard is run to.
     *
     * @return {@value #INTERFACE}
     */
    public String interfaceName() {
        return interfaceName;
    }

    /**
     * What implements the guard.
     *
     * @return the implementation
     */
    public GuardImplementation implementation() {
        return implementation;
    }

    /**
     * The JSON Schema a transition entry's {@code config} is checked against, held as the tree it was authored as.
     *
     * @return the schema, empty when the guard takes no config
     */
    public Optional<ConfigValue> configSchema() {
        return configSchema;
    }

    /**
     * The run limits.
     *
     * @return the limits, empty when none are stated
     */
    public Optional<GuardLimits> limits() {
        return limits;
    }

    /**
     * What could-not-evaluate counts as, as authored.
     *
     * @return the stated value, empty when absent
     */
    public Optional<OnIndeterminate> onIndeterminate() {
        return onIndeterminate;
    }

    /**
     * What could-not-evaluate counts as, with the default applied.
     *
     * @return {@link OnIndeterminate#BLOCK} unless the guard states {@code pass}
     */
    public OnIndeterminate indeterminate() {
        return onIndeterminate.orElse(OnIndeterminate.BLOCK);
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Guard that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Guard[" + id.value() + " of pack " + packId.value() + "]";
    }
}
