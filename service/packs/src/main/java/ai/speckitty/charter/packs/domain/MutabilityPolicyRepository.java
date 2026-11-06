package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;

/**
 * The port through which one pack's mutability policy is read, whatever the storage (ADR-0063). The policy is the file
 * the pack's descriptor names by {@code mutability_policy}; a pack that names none has the
 * {@link MutabilityPolicySource#empty() empty} policy, so every artifact of it is open.
 */
@FunctionalInterface
public interface MutabilityPolicyRepository {

    /**
     * The policy the pack declares.
     *
     * @param pack the pack's identity
     * @return its policy; {@link MutabilityPolicySource#empty()} when its descriptor names no policy file
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_NOT_FOUND} when no pack is configured
     *     as {@code pack}; with {@code PACK_UNREADABLE} when its descriptor cannot be read, or names a policy file that
     *     is missing or cannot be read
     */
    MutabilityPolicySource policyOf(PackIdentifier pack);
}
