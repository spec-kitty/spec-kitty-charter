package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicyRepository;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The real ADR-0012 mutability write gate (FR-011, FR-022): the {@code @Primary} {@link WritePolicyGate} that
 * supersedes the {@link PermissiveWritePolicyGate} default, so every registration service enforces the level through
 * the WP04 port without any service, controller or commit-store edit.
 *
 * <p>A write into pack P is judged at the highest level along P's composition closure (ADR-0062 decision 3): each pack
 * of the closure contributes the policy its descriptor names, and
 * {@link PerPackPolicy#levelIn} answers the highest level and the pack that set it, the one function the activation
 * lock query shares. A level the write exceeds is refused with {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409),
 * naming the pack that set it. The level&rarr;operation mapping is documented on {@link MutabilityLevel}.
 *
 * <p>Cost: the gate composes P's closure once per registration, in one verified read: {@link
 * OfferingReadService#composedWithFacts(java.util.Collection)} inside a {@link ConsistentRead}, and each member's
 * policy is the {@link ai.speckitty.charter.packs.domain.PackCompositionFacts#policy()} that compose read, so the
 * closure and its policies are one answer from one source, the source the closure snapshot reads too. A pack change
 * during the read refuses the write as {@code stale_input}, never a mixed answer. Registrations are rare, so the read
 * is acceptable; a closure that cannot be composed, or a member whose policy file is missing or invalid, refuses the
 * write as the compose refuses it, since a lock that cannot be read is never taken as open. Each pack's
 * {@code policy.yaml} is read (ADR-0062); no open pack ships one yet, so every target of the open packs is open and
 * their writes keep working (tighten never loosen).
 *
 * <p>It is a Spring-managed edge (it carries the framework annotations), so it lives in the application ring, not in
 * the pure {@code packs.domain} ring where the policy model resides; {@code HexagonalArchitectureTest} gates that split.
 */
@Component
@Primary
public final class PackMutabilityPolicy implements WritePolicyGate {

    /**
     * One pack of a closure: the identifier it is configured as, and the {@code pack_id} its descriptor declares.
     *
     * @param configured the configured identifier, the one its policy is read by
     * @param descriptorPackId the descriptor's {@code pack_id}, the key of {@link PerPackPolicy}
     */
    record Member(PackIdentifier configured, String descriptorPackId) {

        /**
         * Checks the fields.
         *
         * @param configured the configured identifier
         * @param descriptorPackId the descriptor's {@code pack_id}
         */
        Member {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
        }
    }

    /** The composition closure of the pack being written, dependencies first. */
    @FunctionalInterface
    interface Closure {
        /**
         * The closure of a pack.
         *
         * @param pack the pack being written
         * @return its closure's members, in closure order, the pack itself included
         */
        List<Member> of(Pack pack);
    }

    /** The policies along the closure of a pack being written, by descriptor {@code pack_id}, in closure order. */
    @FunctionalInterface
    private interface ClosurePolicies {
        Map<String, MutabilityPolicySource> of(Pack pack);
    }

    private static final String STALE = "The packs changed while the write's policy was read; send the write again.";

    private final ClosurePolicies closurePolicies;

    /**
     * The gate Spring wires: the closure is the compose rooted at the pack being written, and each member's policy is
     * the one that compose read, all in one verified read.
     *
     * @param offerings the compose
     * @param catalog the configured packs, to root the compose at the configured pack being written
     * @param descriptors the descriptors, to find the configured pack a write addresses by its {@code pack_id}
     * @param read the verifier that digests the inputs before and after the read
     */
    @Autowired
    public PackMutabilityPolicy(
            OfferingReadService offerings,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            ConsistentRead read) {
        this(pack -> read.read(
                        STALE,
                        () -> policiesOf(offerings.composedWithFacts(Set.of(rootOf(catalog, descriptors, pack)))))
                .value());
    }

    /**
     * A gate over a given closure and policy port: the seam a test uses to plant a dependency's lock. Production reads
     * no policy through the port; it stays for this seam, and {@code DomainRepositoryService} still implements it.
     *
     * @param policies the port that reads each member's policy
     * @param closure the closure of the pack being written
     */
    PackMutabilityPolicy(MutabilityPolicyRepository policies, Closure closure) {
        this(policiesOver(Objects.requireNonNull(policies, "policies"), Objects.requireNonNull(closure, "closure")));
    }

    private PackMutabilityPolicy(ClosurePolicies closurePolicies) {
        this.closurePolicies = closurePolicies;
    }

    /**
     * A gate over one pack's declared levels, its closure the pack alone.
     *
     * @param source the levels this gate enforces
     */
    public PackMutabilityPolicy(MutabilityPolicySource source) {
        this(any -> source, pack -> List.of(new Member(pack.id(), pack.id().value())));
    }

    /** A gate with no policy anywhere: every target is open, as before any pack shipped a policy file. */
    public PackMutabilityPolicy() {
        this(MutabilityPolicySource.empty());
    }

    /**
     * Authorizes a write, or refuses it when it exceeds the target's level along the pack's closure.
     *
     * @param pack the pack being mutated
     * @param operation the write about to be performed
     * @param target the artifact or relationship endpoint the write concerns
     * @throws CharterRefusal {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409) when the target's level does not
     *     permit the operation; {@code PACK_UNREADABLE} or a composition refusal when the closure cannot be judged
     */
    @Override
    public void authorize(Pack pack, Operation operation, Urn target) {
        Map<String, MutabilityPolicySource> byPack = closurePolicies.of(pack);
        PerPackPolicy.Level level = new PerPackPolicy(byPack).levelIn(List.copyOf(byPack.keySet()), target);
        if (!level.level().permits(operation)) {
            throw new CharterRefusal(
                    RefusalCode.GOVERNANCE_POLICY_VIOLATION,
                    "Operation " + operation + " on " + target + " is refused: pack "
                            + level.setBy().orElse(pack.id().value()) + " of the composition of "
                            + pack.id().value()
                            + " declares it " + level.level().token()
                            + ", which does not permit this write (ADR-0012, ADR-0062).");
        }
    }

    /**
     * The configured pack a write addresses: the one configured as the pack's identifier, else the one whose descriptor
     * declares the pack's {@code pack_id} (a pack may be addressed by either).
     */
    private static PackIdentifier rootOf(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, Pack pack) {
        List<PackIdentifier> configured =
                catalog.configured().stream().map(ConfiguredPack::id).toList();
        if (configured.contains(pack.id())) {
            return pack.id();
        }
        String packId = pack.descriptor().packId();
        return configured.stream()
                .filter(candidate -> declares(descriptors, candidate, packId))
                .findFirst()
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + pack.id().value() + " is not a configured pack"));
    }

    /** Whether the configured pack's descriptor declares {@code packId}; an unreadable one declares nothing. */
    private static boolean declares(PackDescriptorRepository descriptors, PackIdentifier candidate, String packId) {
        try {
            return descriptors
                    .find(candidate)
                    .filter(found -> found.packId().equals(packId))
                    .isPresent();
        } catch (CharterRefusal unreadable) {
            return false;
        }
    }

    /** The policy of each closure pack, as the compose read it, by descriptor {@code pack_id} in closure order. */
    private static Map<String, MutabilityPolicySource> policiesOf(OfferingReadService.ComposedClosure composed) {
        Map<String, MutabilityPolicySource> byPack = new LinkedHashMap<>();
        for (PackUnderResolution member : composed.packs()) {
            byPack.putIfAbsent(
                    member.descriptor().packId(), composed.factsOf(member).policy());
        }
        return byPack;
    }

    /** The test seam's policies: each member's policy read through the port, by descriptor {@code pack_id}. */
    private static ClosurePolicies policiesOver(MutabilityPolicyRepository policies, Closure closure) {
        return pack -> {
            Map<String, MutabilityPolicySource> byPack = new LinkedHashMap<>();
            for (Member member : closure.of(pack)) {
                byPack.putIfAbsent(member.descriptorPackId(), policies.policyOf(member.configured()));
            }
            return byPack;
        };
    }
}
