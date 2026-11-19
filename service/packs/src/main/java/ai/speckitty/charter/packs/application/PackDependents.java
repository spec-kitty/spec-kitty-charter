package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Port: what still depends on a pack a {@code DELETE} would remove (ADR-0081 D1, D6, D7). The removal asks it before it
 * composes anything; ADR-0075's external-check slice (#556) implements or absorbs it as part of its impact check.
 */
@FunctionalInterface
public interface PackDependents {

    /** How a dependent depends on the pack, with the violation code the refusal names it by. */
    enum Kind {
        /** A configured pack whose {@code depends_on} names the pack. */
        DEPENDS_ON("depends_on"),
        /** A configured pack whose {@code parent_pack} is the pack. */
        PARENT_PACK("parent_pack"),
        /** A stored activation whose {@code packs} list names the pack. */
        ACTIVATION("activation"),
        /** An artifact of another pack that refers to one of the pack's artifacts. */
        ARTIFACT_REFERENCE("artifact_reference");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        /**
         * The violation code of this kind.
         *
         * @return the snake_case code
         */
        public String code() {
            return code;
        }
    }

    /**
     * One dependent.
     *
     * @param kind how it depends on the pack
     * @param pack the dependent pack, for a pack or an artifact of one; empty for an activation
     * @param detail what depends on what, as the refusal says it
     */
    record Dependent(Kind kind, Optional<RefusalPack> pack, String detail) {

        /**
         * Checks the fields.
         *
         * @param kind how it depends on the pack
         * @param pack the dependent pack, if any
         * @param detail what depends on what
         */
        public Dependent {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * The pack a removal would remove.
     *
     * @param configured how it is configured: its name and level
     * @param descriptor its descriptor, salvaged from its {@code pack.yaml} when the pack cannot be loaded whole
     * @param held every artifact and term it holds, as URNs; an artifact of another pack that refers to one of them is a
     *     dependent
     */
    record Removed(ConfiguredPack configured, PackDescriptor descriptor, Set<Urn> held) {

        /**
         * Checks the fields and copies the set.
         *
         * @param configured how it is configured
         * @param descriptor its descriptor
         * @param held what it holds
         */
        public Removed {
            Objects.requireNonNull(configured, "configured");
            Objects.requireNonNull(descriptor, "descriptor");
            held = Set.copyOf(held);
        }
    }

    /**
     * Every direct dependent of the pack, in a stable order: the packs ({@code depends_on}, then {@code parent_pack}, each
     * by configured name), then the activations (by id), then the artifacts of other packs that refer to its artifacts.
     * It fails closed: a configured pack it cannot read cannot be judged.
     *
     * @param stored the stored packs, read through the removal's session
     * @param removed the pack the removal would remove
     * @return the dependents; empty when nothing depends on the pack
     * @throws CharterRefusal {@code pack_unreadable} naming a configured pack whose descriptor cannot be read
     */
    List<Dependent> of(StagedPackViews.View stored, Removed removed);

    /**
     * The {@code 409 pack_has_dependents} that names them (ADR-0081 D7): its packs member names each dependent pack
     * once, in the order of the first dependent that names it, and its violations name every dependent; the answer lists
     * at most 100 of each and counts the rest in its detail, as every refusal's does.
     *
     * @param packId the configured name of the pack the removal would remove
     * @param dependents the dependents; not empty
     * @return the refusal
     */
    static CharterRefusal refusal(String packId, List<Dependent> dependents) {
        if (dependents.isEmpty()) {
            throw new IllegalArgumentException("a refusal names at least one dependent");
        }
        List<RefusalViolation> violations = dependents.stream()
                .map(dependent -> new RefusalViolation("", dependent.kind().code(), dependent.detail()))
                .toList();
        List<RefusalPack> packs = dependents.stream()
                .flatMap(dependent -> dependent.pack().stream())
                .distinct()
                .toList();
        String detail = "The pack " + packId + " has " + dependents.size()
                + " dependent(s); remove or change them first.";
        return CharterRefusal.withPacksAndViolations(RefusalCode.PACK_HAS_DEPENDENTS, detail, packs, violations);
    }
}
