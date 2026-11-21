package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.ImpactCheck;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The dependents of a pack a removal would remove (ADR-0081 D1, D6, D7), the first judgement of the impact check's
 * removal (#556, D3: the impact check absorbs #517's {@code PackDependents} seam). A pack depends on the removed one
 * when its descriptor's {@code depends_on} or {@code parent_pack} names the removed pack's {@code pack_id} (none can
 * when its own {@code pack.yaml} cannot be read), an activation depends on it when its {@code packs} list names its
 * configured name ({@link ActivationImpact.ActivationRoots#names}), and an artifact depends on it when
 * {@link InboundReferences} finds that it refers to one of the removed pack's artifacts. Only direct dependents are
 * named: a pack that builds on a dependent is that dependent's concern.
 */
final class RemovalDependents {

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

    private final InboundReferences inbound;
    private final ActivationImpact activations;

    /**
     * Dependents over the inbound scan and the activations port.
     *
     * @param inbound the inbound scan
     * @param activations the activations slice's port
     */
    RemovalDependents(InboundReferences inbound, ActivationImpact activations) {
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * Every direct dependent of the pack, in a stable order: the packs ({@code depends_on}, then {@code parent_pack},
     * each by configured name), then the activations (by id), then the artifacts of other packs that refer to its
     * artifacts. It fails closed: a configured pack it cannot read cannot be judged.
     *
     * @param stored the stored packs, read through the removal's session
     * @param removed the pack the removal would remove
     * @return the dependents; empty when nothing depends on the pack
     * @throws CharterRefusal {@code pack_unreadable} naming a configured pack whose descriptor cannot be read
     */
    List<Dependent> of(StagedPackViews.View stored, ImpactCheck.Removal removed) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(removed, "removed");
        List<Dependent> found = new ArrayList<>(packsBuildingOn(stored, removed));
        found.addAll(activationsNaming(removed));
        found.addAll(referrers(stored, removed));
        return List.copyOf(found);
    }

    /** The packs whose {@code depends_on}, then whose {@code parent_pack}, names the removed pack. */
    private static List<Dependent> packsBuildingOn(StagedPackViews.View stored, ImpactCheck.Removal removed) {
        // A pack whose pack.yaml cannot be read declares no pack_id an edge could name; every other pack is still read,
        // so one that cannot be judged fails the removal closed.
        Optional<String> target = packIdOf(removed).map(PackUlid::value);
        List<Dependent> dependsOn = new ArrayList<>();
        List<Dependent> parentOf = new ArrayList<>();
        List<ConfiguredPack> others = stored.catalog().configured().stream()
                .filter(configured ->
                        !configured.id().equals(removed.configured().id()))
                .sorted(Comparator.comparing(configured -> configured.id().value()))
                .toList();
        for (ConfiguredPack other : others) {
            PackDescriptor descriptor = descriptorOf(stored, other);
            RefusalPack pack = new RefusalPack(new PackUlid(descriptor.packId()), other.id(), descriptor.name());
            if (descriptor.dependencies().stream()
                    .map(PackDependency::packId)
                    .anyMatch(packId -> target.filter(packId::equals).isPresent())) {
                dependsOn.add(new Dependent(
                        Kind.DEPENDS_ON,
                        Optional.of(pack),
                        "The pack " + other.id().value() + " declares depends_on "
                                + removed.configured().id().value() + "."));
            }
            if (descriptor
                    .parentPack()
                    .filter(parent -> target.filter(parent::equals).isPresent())
                    .isPresent()) {
                parentOf.add(new Dependent(
                        Kind.PARENT_PACK,
                        Optional.of(pack),
                        "The pack " + other.id().value() + " declares parent_pack "
                                + removed.configured().id().value() + "."));
            }
        }
        List<Dependent> packs = new ArrayList<>(dependsOn);
        packs.addAll(parentOf);
        return packs;
    }

    /** A configured pack that cannot be judged fails the removal closed: it might depend on the removed pack. */
    private static PackDescriptor descriptorOf(StagedPackViews.View stored, ConfiguredPack other) {
        try {
            return stored.descriptors().find(other.id()).orElseThrow(() -> unjudgeable(other, null));
        } catch (CharterRefusal unreadable) {
            if (unreadable.code() == RefusalCode.PACK_UNREADABLE) {
                throw unjudgeable(other, unreadable);
            }
            throw unreadable;
        }
    }

    private static CharterRefusal unjudgeable(ConfiguredPack other, @Nullable CharterRefusal cause) {
        String detail = "The pack " + other.id().value()
                + " cannot be read, so whether it depends on the pack being removed cannot be judged; repair it first.";
        return cause == null
                ? new CharterRefusal(RefusalCode.PACK_UNREADABLE, detail)
                : new CharterRefusal(RefusalCode.PACK_UNREADABLE, detail, cause);
    }

    /**
     * The stored activations whose {@code packs} list names the removed pack, by id. An activation whose document cannot
     * be read might name it, so the removal fails closed (review R3-1): no refusal code names a fault of the activation
     * store, so it ends as the service's own failure, as a registration's impact check does (DIV-614).
     */
    private List<Dependent> activationsNaming(ImpactCheck.Removal removed) {
        List<String> unreadable = activations.unreadableActivations();
        if (!unreadable.isEmpty()) {
            throw new IllegalStateException("The stored activations " + unreadable
                    + " cannot be read, so whether they name the pack being removed cannot be judged; repair them"
                    + " first.");
        }
        String name = removed.configured().id().value();
        return activations.activationRoots().stream()
                .filter(roots -> roots.names(name))
                .map(ActivationImpact.ActivationRoots::id)
                .distinct()
                .sorted()
                .map(id -> new Dependent(
                        Kind.ACTIVATION,
                        Optional.empty(),
                        "The activation " + id + " names " + name + " in its packs."))
                .toList();
    }

    /** The artifacts of other packs that refer to one of the removed pack's artifacts. */
    private List<Dependent> referrers(StagedPackViews.View stored, ImpactCheck.Removal removed) {
        InboundReferences.StagedTree empty = new InboundReferences.StagedTree(
                removed.configured().id(),
                Optional.of(nameOf(removed)),
                removed.descriptor(),
                PackCompositionFacts.NONE);
        return inbound.scan(stored, empty, removed.held()).stream()
                .map(referrer -> new Dependent(
                        Kind.ARTIFACT_REFERENCE,
                        Optional.of(new RefusalPack(referrer.descriptorPackId(), referrer.pack(), referrer.name())),
                        referrer.referrer() + " of the pack " + referrer.pack().value() + " refers to "
                                + referrer.removed() + "."))
                .toList();
    }

    /** The {@code pack_id} another pack's {@code depends_on} or {@code parent_pack} names the removed pack by. */
    private static Optional<PackUlid> packIdOf(ImpactCheck.Removal removed) {
        return removed.descriptor().map(declared -> new PackUlid(declared.packId()));
    }

    /** The name a qualified reference names the removed pack by: the declared one, or its configured name. */
    private static String nameOf(ImpactCheck.Removal removed) {
        return removed.descriptor()
                .map(PackDescriptor::name)
                .orElseGet(() -> removed.configured().id().value());
    }

    /**
     * The {@code 409 pack_has_dependents} fault that names them (ADR-0081 D7): its packs member names each dependent pack
     * once, in the order of the first dependent that names it, and its violations name every dependent; the answer lists
     * at most 100 of each and counts the rest in its detail, as every refusal's does.
     *
     * @param packId the configured name of the pack the removal would remove
     * @param dependents the dependents; not empty
     * @return the fault
     */
    static ClosureFault fault(String packId, List<Dependent> dependents) {
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
        String detail =
                "The pack " + packId + " has " + dependents.size() + " dependent(s); remove or change them first.";
        return ClosureFault.of(
                ClosureFault.ORIGIN, RefusalCode.PACK_HAS_DEPENDENTS, detail, packs, violations, List.of());
    }
}
