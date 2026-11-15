package ai.speckitty.charter.profiles.domain.resolution;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Inheritance along {@code specializes_from} (ruling D6 of ADR-0078): a chain folded root to leaf by per-field
 * replacement. A top-level field a descendant declares replaces the ancestor's, and a field it leaves absent is
 * inherited from the nearest ancestor that declares it. Identity fields ({@code profile-id}, {@code name},
 * {@code purpose}, {@code role}, {@code alt-roles}, {@code performer}, {@code routing-priority}) are never inherited:
 * they are the leaf's. The required {@code specialization} is always the leaf's own, since every profile declares it.
 *
 * <p>Each profile of the chain is expected to be merged with its own enhancers first ({@link ProfileEnhancementMerge}),
 * and a profile with several parents is expected to have them listed before it in closure order: the caller orders
 * the chain, this fold only reads it. Selection judges a candidate by its own declared fields, never by this fold.
 */
public final class ProfileInheritance {

    private ProfileInheritance() {}

    /**
     * Folds a chain.
     *
     * @param rootToLeaf the chain, the root first and the profile itself last
     * @return the leaf with the fields it leaves absent inherited
     * @throws IllegalArgumentException when the chain is empty
     */
    public static AgentProfile fold(List<AgentProfile> rootToLeaf) {
        Objects.requireNonNull(rootToLeaf, "rootToLeaf");
        if (rootToLeaf.isEmpty()) {
            throw new IllegalArgumentException("a lineage chain holds at least the profile itself");
        }
        AgentProfile leaf = rootToLeaf.getLast();
        Map<ProfileField, AgentProfile> sources = new LinkedHashMap<>();
        for (AgentProfile profile : rootToLeaf) {
            for (ProfileField field : ProfileField.values()) {
                if (!field.identity() && field.declaredBy(profile)) {
                    sources.put(field, profile);
                }
            }
        }
        return ProfileField.assemble(leaf, sources);
    }
}
