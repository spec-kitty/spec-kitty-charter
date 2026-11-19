package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link PackDependents} of this service (ADR-0081 D1): the finders the registration's impact check already has,
 * asked about a removal. A pack depends on the removed one when its descriptor's {@code depends_on} or
 * {@code parent_pack} names the removed pack's {@code pack_id}, an activation depends on it when its {@code packs} list
 * names its configured name ({@link ActivationImpact.ActivationRoots#names}), and an artifact depends on it when
 * {@link InboundReferences} finds that it refers to one of the removed pack's artifacts. Only direct dependents are
 * named: a pack that builds on a dependent is that dependent's concern.
 */
@Component
public final class ClosurePackDependents implements PackDependents {

    private final InboundReferences inbound;
    private final ActivationImpact activations;

    /**
     * Dependents over the inbound scan and the activations port.
     *
     * @param inbound the inbound scan
     * @param activations the activations slice's port
     */
    public ClosurePackDependents(InboundReferences inbound, ActivationImpact activations) {
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.activations = Objects.requireNonNull(activations, "activations");
    }

    /**
     * Dependents as Spring wires them: a deployment without the activations slice stores no activation.
     *
     * @param inbound the inbound scan
     * @param activations the activations slice's port, when it is deployed
     */
    @Autowired
    public ClosurePackDependents(InboundReferences inbound, ObjectProvider<ActivationImpact> activations) {
        this(inbound, activations.getIfAvailable(() -> ActivationImpact.NONE));
    }

    @Override
    public List<Dependent> of(StagedPackViews.View stored, Removed removed) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(removed, "removed");
        List<Dependent> found = new ArrayList<>(packsBuildingOn(stored, removed));
        found.addAll(activationsNaming(removed));
        found.addAll(referrers(stored, removed));
        return List.copyOf(found);
    }

    /** The packs whose {@code depends_on}, then whose {@code parent_pack}, names the removed pack. */
    private static List<Dependent> packsBuildingOn(StagedPackViews.View stored, Removed removed) {
        String target = removed.descriptor().packId();
        List<Dependent> dependsOn = new ArrayList<>();
        List<Dependent> parentOf = new ArrayList<>();
        List<ConfiguredPack> others = stored.catalog().configured().stream()
                .filter(configured ->
                        !configured.id().equals(removed.configured().id()))
                .sorted(Comparator.comparing(configured -> configured.id().value()))
                .toList();
        for (ConfiguredPack other : others) {
            PackDescriptor descriptor = descriptorOf(stored, other);
            RefusalPack pack = new RefusalPack(descriptor.packId(), other.id().value(), descriptor.name());
            if (descriptor.dependencies().stream().map(PackDependency::packId).anyMatch(target::equals)) {
                dependsOn.add(new Dependent(
                        Kind.DEPENDS_ON,
                        Optional.of(pack),
                        "The pack " + other.id().value() + " declares depends_on "
                                + removed.configured().id().value() + "."));
            }
            if (descriptor.parentPack().filter(target::equals).isPresent()) {
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

    /** The stored activations whose {@code packs} list names the removed pack, by id. */
    private List<Dependent> activationsNaming(Removed removed) {
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
    private List<Dependent> referrers(StagedPackViews.View stored, Removed removed) {
        InboundReferences.StagedTree empty = new InboundReferences.StagedTree(
                Optional.of(removed.configured().id()),
                Optional.of(removed.descriptor().name()),
                removed.descriptor(),
                PackCompositionFacts.NONE);
        return inbound.scan(stored, empty, removed.held()).stream()
                .map(referrer -> new Dependent(
                        Kind.ARTIFACT_REFERENCE,
                        Optional.of(new RefusalPack(
                                referrer.descriptorPackId(), referrer.pack().value(), referrer.name())),
                        referrer.referrer() + " of the pack " + referrer.pack().value() + " refers to "
                                + referrer.removed() + "."))
                .toList();
    }
}
