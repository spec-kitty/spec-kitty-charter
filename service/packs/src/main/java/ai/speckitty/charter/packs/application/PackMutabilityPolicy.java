package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The real ADR-0012 mutability write gate (FR-011): the {@code @Primary} {@link WritePolicyGate} that supersedes the
 * {@link PermissiveWritePolicyGate} default, so every registration service enforces the owning pack's level through
 * the WP04 port without any service, controller or commit-store edit.
 *
 * <p>It resolves the target's {@link MutabilityLevel} through the {@link MutabilityPolicySource} and refuses a write
 * the level does not permit with {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (mapped to 409 by the WP04 refusal
 * handler). The level&rarr;operation mapping and the refusal matrix are documented on {@link MutabilityLevel}.
 *
 * <p>It is a Spring-managed edge (it carries the framework annotations), so it lives in the application ring, not in
 * the pure {@code packs.domain} ring where the policy model ({@link MutabilityLevel}, {@link MutabilityPolicySource})
 * resides; {@code HexagonalArchitectureTest} gates that split. The no-arg constructor Spring uses wires an
 * {@link MutabilityPolicySource#empty() empty} source: ADR-0012's migration reality is that no pack ships a policy
 * file yet, so every artifact is {@code open} and today's writes keep working (tighten never loosen). The source
 * constructor lets a test, or a future policy-file reader, supply declared levels; enforcement is then live with no
 * other change.
 */
@Component
@Primary
public final class PackMutabilityPolicy implements WritePolicyGate {

    private final MutabilityPolicySource source;

    /** For Spring: the permissive-by-data default until a policy file is read (every target resolves {@code open}). */
    public PackMutabilityPolicy() {
        this(MutabilityPolicySource.empty());
    }

    /**
     * A gate over a given source of declared levels.
     *
     * @param source the levels this gate enforces
     */
    public PackMutabilityPolicy(MutabilityPolicySource source) {
        this.source = source;
    }

    /**
     * Authorizes a write, or refuses it when it exceeds the target's mutability level.
     *
     * @param pack the pack being mutated
     * @param operation the write about to be performed
     * @param target the artifact or relationship endpoint the write concerns
     * @throws CharterRefusal {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409) when the target's level does not
     *     permit the operation
     */
    @Override
    public void authorize(Pack pack, Operation operation, Urn target) {
        MutabilityLevel level = source.levelOf(pack, target);
        if (!level.permits(operation)) {
            throw new CharterRefusal(
                    RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                    "Operation " + operation + " on " + target + " is refused: the owning pack declares it "
                            + level.token() + ", which does not permit this write (ADR-0012).");
        }
    }
}
