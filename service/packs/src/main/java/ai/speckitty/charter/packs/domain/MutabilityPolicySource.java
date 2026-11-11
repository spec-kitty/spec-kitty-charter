package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the {@link MutabilityLevel} that one pack declares for a target URN (ADR-0012, FR-011). It is a pure,
 * immutable resolver: given per-URN entries and per-kind defaults, it answers in the order ADR-0012 §Decision 2 fixes
 * &mdash; a per-URN entry wins; else the target kind's default; else {@link MutabilityLevel#OPEN} (an artifact no policy
 * names, in a pack with no policy file, is open).
 *
 * <p>One source is one pack's policy: the adapter reads it from the pack-root file the descriptor's
 * {@code mutability_policy} names (ADR-0063), and a pack that names none has the {@link #empty()} source. Along a
 * composition closure the sources are combined by {@link PerPackPolicy}, where the highest level wins; the built-in
 * pack's own file and the {@code replaceable-builtins.yaml} migration remain out of scope (ADR-0012 §Decision 5).
 */
public final class MutabilityPolicySource {

    private final Map<NodeKind, MutabilityLevel> kindDefaults;
    private final Map<Urn, MutabilityLevel> urnEntries;

    private MutabilityPolicySource(Map<NodeKind, MutabilityLevel> kindDefaults, Map<Urn, MutabilityLevel> urnEntries) {
        this.kindDefaults = Map.copyOf(kindDefaults);
        this.urnEntries = Map.copyOf(urnEntries);
    }

    /**
     * The default level of each kind the file sets one for; a kind it leaves out is open.
     *
     * @return an unmodifiable map
     */
    public Map<NodeKind, MutabilityLevel> kindDefaults() {
        return kindDefaults;
    }

    /**
     * The targets the file names one by one, with their levels; a kind default names no target.
     *
     * @return an unmodifiable map
     */
    public Map<Urn, MutabilityLevel> urnEntries() {
        return urnEntries;
    }

    /**
     * A source that declares nothing, so every target resolves to {@link MutabilityLevel#OPEN}.
     *
     * @return the empty source
     */
    public static MutabilityPolicySource empty() {
        return builder().build();
    }

    /**
     * Starts a builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The level this pack declares for a target: a per-URN entry, else the kind default, else
     * {@link MutabilityLevel#OPEN}.
     *
     * @param target the artifact or relationship endpoint the write concerns
     * @return the resolved level; never {@code null}
     */
    public MutabilityLevel levelOf(Urn target) {
        Objects.requireNonNull(target, "target");
        MutabilityLevel entry = urnEntries.get(target);
        if (entry != null) {
            return entry;
        }
        return kindDefaults.getOrDefault(target.kind(), MutabilityLevel.OPEN);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MutabilityPolicySource that
                && kindDefaults.equals(that.kindDefaults)
                && urnEntries.equals(that.urnEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kindDefaults, urnEntries);
    }

    @Override
    public String toString() {
        return "MutabilityPolicySource[kindDefaults=" + kindDefaults + ", urnEntries=" + urnEntries + "]";
    }

    /** Fluent builder for a {@link MutabilityPolicySource}. */
    public static final class Builder {
        private final Map<NodeKind, MutabilityLevel> kindDefaults = new EnumMap<>(NodeKind.class);
        private final Map<Urn, MutabilityLevel> urnEntries = new HashMap<>();

        private Builder() {}

        /**
         * Declares the default level for a node kind.
         *
         * @param kind the node kind
         * @param level the level to apply to targets of that kind with no per-URN entry
         * @return this builder
         */
        public Builder kindDefault(NodeKind kind, MutabilityLevel level) {
            kindDefaults.put(Objects.requireNonNull(kind, "kind"), Objects.requireNonNull(level, "level"));
            return this;
        }

        /**
         * Declares the level for a single target URN.
         *
         * @param target the target URN
         * @param level the level to apply to that target
         * @return this builder
         */
        public Builder entry(Urn target, MutabilityLevel level) {
            urnEntries.put(Objects.requireNonNull(target, "target"), Objects.requireNonNull(level, "level"));
            return this;
        }

        /**
         * Builds the immutable source.
         *
         * @return the source
         */
        public MutabilityPolicySource build() {
            return new MutabilityPolicySource(kindDefaults, urnEntries);
        }
    }
}
