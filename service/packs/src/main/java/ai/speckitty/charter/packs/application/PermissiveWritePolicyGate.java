package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import org.springframework.stereotype.Component;

/**
 * The permissive default {@link WritePolicyGate}: it authorizes every write, which is today's behaviour (every
 * artifact is effectively {@code open}). It is the non-{@code @Primary} default so that WP06 can supply the real
 * ADR-0012 mutability-policy gate as the {@code @Primary} bean without touching any registration service. This WP
 * models no mutability levels.
 */
@Component
public final class PermissiveWritePolicyGate implements WritePolicyGate {

    /** For Spring, which finds the component by scan. */
    public PermissiveWritePolicyGate() {}

    /**
     * Authorizes every write unconditionally.
     *
     * @param pack the pack being mutated
     * @param operation the write about to be performed
     * @param target the artifact or relationship endpoint
     */
    @Override
    public void authorize(Pack pack, Operation operation, Urn target) {
        // Permissive default: every write is allowed. WP06 replaces this with the ADR-0012 policy as @Primary.
    }
}
