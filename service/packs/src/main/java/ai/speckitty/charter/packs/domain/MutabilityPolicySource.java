package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the {@link MutabilityLevel} that applies to a target URN (ADR-0012, FR-011). It is a pure, immutable
 * resolver: given per-URN entries and per-kind defaults, it answers in the order ADR-0012 §Decision 2 fixes &mdash;
 * a per-URN entry wins; else the target kind's default; else {@link MutabilityLevel#OPEN} (an artifact no policy
 * names, in a pack with no policy file, is open).
 *
 * <p>This mission scopes the source to what FR-011 needs (a Medium requirement): it carries declared levels in memory
 * and does not read the pack-root {@code replaceable-builtins.yaml} / per-kind policy file (that migration is out of
 * scope, ADR-0012 §Decision 5). An {@link #empty()} source therefore resolves every target to {@code open}, which is
 * exactly ADR-0012's migration reality &mdash; no pack ships a policy file yet, so nothing is tightened. Populating a
 * source (future policy-file reader) makes the gate enforce with no further change: the seam is additive.
 */
public final class MutabilityPolicySource {

    private final Map<NodeKind, MutabilityLevel> kindDefaults;
    private final Map<Urn, MutabilityLevel> urnEntries;

    private MutabilityPolicySource(Map<NodeKind, MutabilityLevel> kindDefaults, Map<Urn, MutabilityLevel> urnEntries) {
        this.kindDefaults = Map.copyOf(kindDefaults);
        this.urnEntries = Map.copyOf(urnEntries);
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
     * The level that applies to a target: a per-URN entry, else the kind default, else {@link MutabilityLevel#OPEN}.
     *
     * @param pack the pack being mutated; reserved for the future per-pack policy file (ADR-0012 §Decision 2) and not
     *     consulted by this in-memory resolver
     * @param target the artifact or relationship endpoint the write concerns
     * @return the resolved level; never {@code null}
     */
    public MutabilityLevel levelOf(Pack pack, Urn target) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(target, "target");
        MutabilityLevel entry = urnEntries.get(target);
        if (entry != null) {
            return entry;
        }
        return kindDefaults.getOrDefault(target.kind(), MutabilityLevel.OPEN);
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
