package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The mutability policies of the packs of a composition, each keyed by the {@code pack_id} its descriptor declares
 * (ADR-0062). It answers one question, shared by the registration write gate and the activation lock query: the level
 * of a target along a closure.
 *
 * <p>The answer is the highest level any pack of the closure declares for the target (OPEN &lt; EXTENDABLE &lt; LOCKED),
 * whatever the closure's order: a later pack raises a level but never lowers it (ADR-0012 §Decision 3). The pack
 * reported as setting it is the first one, in closure order, that declared that highest level. A pack with no policy is
 * open; so is a target no pack of the closure names, and then no pack set it.
 *
 * @param byPack each pack's policy, by descriptor {@code pack_id}; a pack left out has the empty policy
 */
public record PerPackPolicy(Map<String, MutabilityPolicySource> byPack) {

    /**
     * Checks and copies the map.
     *
     * @param byPack each pack's policy, by descriptor {@code pack_id}
     */
    public PerPackPolicy {
        byPack = Map.copyOf(Objects.requireNonNull(byPack, "byPack"));
    }

    /**
     * A target's level along a closure, and the pack that set it.
     *
     * @param level the highest level any pack of the closure declares
     * @param setBy the descriptor {@code pack_id} of the first pack, in closure order, that declared it; empty when no
     *     pack raised the target above {@link MutabilityLevel#OPEN}
     */
    public record Level(MutabilityLevel level, Optional<String> setBy) {

        /** The answer for a target no pack raises. */
        public static final Level OPEN = new Level(MutabilityLevel.OPEN, Optional.empty());

        /**
         * Checks the fields.
         *
         * @param level the level
         * @param setBy the setting pack
         */
        public Level {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(setBy, "setBy");
        }
    }

    /**
     * The highest level the packs of {@code closurePackIds} declare for {@code urn}.
     *
     * @param closurePackIds the closure's descriptor {@code pack_id}s, in closure order
     * @param urn the target
     * @return the level and the pack that set it
     */
    public Level levelIn(List<String> closurePackIds, Urn urn) {
        Objects.requireNonNull(closurePackIds, "closurePackIds");
        Objects.requireNonNull(urn, "urn");
        Level highest = Level.OPEN;
        for (String packId : closurePackIds) {
            MutabilityLevel declared =
                    byPack.getOrDefault(packId, MutabilityPolicySource.empty()).levelOf(urn);
            // MutabilityLevel declares its levels rising, so the ordinal order is the strictness order.
            if (declared.compareTo(highest.level()) > 0) {
                highest = new Level(declared, Optional.of(packId));
            }
        }
        return highest;
    }
}
