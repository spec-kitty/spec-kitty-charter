package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.Preset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The inbound scan of a whole-pack replace (FR-036, ADR-0065 D4): which other artifacts, presets and descriptors still
 * refer to an artifact the replace removes.
 */
@Component
public final class InboundReferences {

    /**
     * One thing that still refers to a removed artifact.
     *
     * @param pack the configured identifier of the pack that holds the referrer
     * @param descriptorPackId the {@code pack_id} that pack's descriptor declares
     * @param name that pack's name
     * @param referrer what refers: an artifact URN, {@code preset:<id>} or {@code overrides:<artifact>}
     * @param removed the removed artifact it refers to
     */
    public record Referrer(PackIdentifier pack, String descriptorPackId, String name, String referrer, Urn removed) {}

    /**
     * The staged tree standing in for the stored pack.
     *
     * @param replacing the configured identifier of the stored pack the tree replaces; empty for a new pack
     * @param replacedName the name the stored pack had, which other packs may have qualified a reference with, since a
     *     replace can rename the pack; empty for a new pack
     * @param descriptor the staged pack's descriptor
     * @param facts the staged pack's composition facts
     */
    public record StagedTree(
            Optional<PackIdentifier> replacing,
            Optional<String> replacedName,
            PackDescriptor descriptor,
            PackCompositionFacts facts) {}

    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final PackArtifactRepository artifacts;

    /**
     * A scan over the configured packs.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what each of them holds and writes
     */
    @Autowired
    public InboundReferences(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    /** One pack as the scan reads it: how it is configured, its descriptor, and what it holds and writes. */
    private record Member(PackIdentifier id, PackDescriptor descriptor, PackCompositionFacts facts, boolean staged) {

        boolean defines(Urn urn) {
            return facts.defined().contains(urn.toString());
        }

        /** The descriptor {@code pack_id}s the pack builds on: its {@code parent_pack} and its {@code depends_on}. */
        List<String> buildsOn() {
            return Stream.concat(
                            descriptor.parentPack().stream(),
                            descriptor.dependencies().stream().map(PackDependency::packId))
                    .toList();
        }
    }

    /** One thing a pack says about an artifact: where it says it, and what it points at. */
    private record Reference(String label, Optional<Urn> source, PackQualifiedReference target) {}

    /**
     * Every referrer of an artifact the replace removes, judged against the staged tree in place of the stored pack.
     *
     * <p>What refers is read from the edges each pack writes ({@link PackCompositionFacts#edges()}: its graph documents,
     * its artifacts' derived relations and its registered shard), the items of its presets (A10) and the entries of its
     * descriptor {@code overrides}. It is not read from {@link PackCompositionFacts#references()}, which holds both
     * ends of every edge with no source, nor from the data-only fields of ADR-0064 decision 11. A reference is
     * attributed the way the compose resolves it: a qualified one to the pack it names, a bare one to the staged pack
     * when the staged pack is in the closure of the pack that writes it and nothing else of that closure holds the id.
     * A pack that cannot be read is not judged: it composes in no closure either.
     *
     * @param staged the staged tree
     * @param removed the artifacts the replace removes
     * @return the referrers, by pack and referrer; empty when nothing refers
     */
    public List<Referrer> scan(StagedTree staged, Set<Urn> removed) {
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(removed, "removed");
        if (removed.isEmpty()) {
            return List.of();
        }
        List<Member> members = membersOf(staged);
        Set<Referrer> found = new LinkedHashSet<>();
        for (Member member : members) {
            found.addAll(referrersOf(member, members, staged, removed));
        }
        return found.stream()
                .sorted(Comparator.comparing(
                                (Referrer referrer) -> referrer.pack().value())
                        .thenComparing(Referrer::referrer)
                        .thenComparing(referrer -> referrer.removed().toString()))
                .toList();
    }

    /** The configured packs but the one the tree replaces, which the tree stands for, and the tree. */
    private List<Member> membersOf(StagedTree staged) {
        List<Member> members = new ArrayList<>();
        for (ConfiguredPack configured : catalog.configured()) {
            if (!staged.replacing().filter(configured.id()::equals).isPresent()) {
                readMember(configured).ifPresent(members::add);
            }
        }
        PackIdentifier id = staged.replacing()
                .orElseGet(() -> new PackIdentifier(staged.descriptor().name()));
        members.add(new Member(id, staged.descriptor(), staged.facts(), true));
        return members;
    }

    private Optional<Member> readMember(ConfiguredPack configured) {
        try {
            Optional<PackDescriptor> descriptor = descriptors.find(configured.id());
            if (descriptor.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(
                    new Member(configured.id(), descriptor.get(), artifacts.compositionFacts(configured.id()), false));
        } catch (CharterRefusal unreadable) {
            return Optional.empty();
        }
    }

    private static List<Referrer> referrersOf(
            Member member, List<Member> members, StagedTree staged, Set<Urn> removed) {
        List<Member> closure = closureOf(member, members);
        List<Referrer> out = new ArrayList<>();
        for (Reference reference : referencesOf(member)) {
            Optional<Urn> hit = removedTarget(reference.target(), closure, staged, removed);
            boolean sourceRemoved = member.staged()
                    && reference.source().filter(removed::contains).isPresent();
            if (hit.isPresent() && !sourceRemoved) {
                out.add(new Referrer(
                        member.id(),
                        member.descriptor().packId(),
                        member.descriptor().name(),
                        reference.label(),
                        hit.get()));
            }
        }
        return out;
    }

    /** The removed artifact a reference resolves to, when it resolves to one of the staged pack's. */
    private static Optional<Urn> removedTarget(
            PackQualifiedReference target, List<Member> closure, StagedTree staged, Set<Urn> removed) {
        Optional<Urn> urn = target.asUrn().filter(removed::contains);
        if (urn.isEmpty()) {
            return urn;
        }
        boolean resolvesToStaged = target.qualified()
                ? target.packName()
                        .filter(name -> namesStagedPack(name, staged))
                        .isPresent()
                : bareResolvesToStaged(urn.get(), closure);
        return resolvesToStaged ? urn : Optional.empty();
    }

    /** A qualifier names the staged pack by the name it is uploaded with, or the one the stored pack had. */
    private static boolean namesStagedPack(String name, StagedTree staged) {
        return name.equals(staged.descriptor().name())
                || staged.replacedName().filter(name::equals).isPresent();
    }

    /** A bare id is read in the closure of the pack that writes it: the staged pack's when nothing else holds it. */
    private static boolean bareResolvesToStaged(Urn urn, List<Member> closure) {
        return closure.stream().anyMatch(Member::staged)
                && closure.stream().filter(member -> !member.staged()).noneMatch(member -> member.defines(urn));
    }

    /** The pack and every pack it builds on, transitively, the pack first, each once. */
    private static List<Member> closureOf(Member member, List<Member> members) {
        Map<String, Member> byPackId = new LinkedHashMap<>();
        members.forEach(candidate -> byPackId.putIfAbsent(candidate.descriptor().packId(), candidate));
        List<Member> closure = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Member> pending = new ArrayDeque<>(List.of(member));
        while (!pending.isEmpty()) {
            Member next = pending.poll();
            if (seen.add(next.descriptor().packId())) {
                closure.add(next);
                next.buildsOn().stream()
                        .map(byPackId::get)
                        .filter(Objects::nonNull)
                        .forEach(pending::add);
            }
        }
        return closure;
    }

    private static List<Reference> referencesOf(Member member) {
        List<Reference> out = new ArrayList<>();
        for (Relationship edge : member.facts().edges()) {
            out.add(new Reference(edge.fromReference().toString(), Optional.of(edge.from()), edge.toReference()));
        }
        for (Preset preset : member.facts().presets()) {
            preset.artifacts()
                    .forEach((kind, items) -> items.forEach(item -> out.add(new Reference(
                            "preset:" + preset.id(),
                            Optional.empty(),
                            new PackQualifiedReference(
                                    Optional.empty(), new Urn(NodeKind.of(kind), item.id()).toString())))));
        }
        for (PackOverride override : member.descriptor().overrides()) {
            overrideReference(override).ifPresent(out::add);
        }
        return out;
    }

    private static Optional<Reference> overrideReference(PackOverride override) {
        try {
            return Optional.of(new Reference(
                    "overrides:" + override.artifact(),
                    Optional.empty(),
                    PackQualifiedReference.parse(override.artifact())));
        } catch (IllegalArgumentException malformed) {
            return Optional
                    .empty(); // an entry that is no reference refers to nothing; the descriptor's own read judges it
        }
    }
}
