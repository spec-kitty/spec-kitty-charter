package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.StepHolding;
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

    /**
     * The {@code 409 artifact_referenced} that names every referrer: the packs member lists each referring pack once, in the
     * order of the first referrer, and each violation holds the pack in its pointer and says what refers to what. A
     * registration and a {@code DELETE} refuse with this one answer.
     *
     * @param detail the sentence that says what was refused
     * @param removedBy what removes the artifact, as the violations say it: {@code registration} or {@code removal}
     * @param referrers the referrers; not empty
     * @return the refusal
     */
    public static CharterRefusal refusal(String detail, String removedBy, List<Referrer> referrers) {
        List<RefusalViolation> violations = referrers.stream()
                .map(referrer -> new RefusalViolation(
                        referrer.pack().value() + "/" + referrer.referrer(),
                        "artifact_referenced",
                        referrer.referrer() + " of the pack " + referrer.name() + " refers to " + referrer.removed()
                                + ", which the " + removedBy + " removes."))
                .toList();
        List<RefusalPack> packs = referrers.stream()
                .map(referrer -> new RefusalPack(
                        referrer.descriptorPackId(), referrer.pack().value(), referrer.name()))
                .distinct()
                .toList();
        return CharterRefusal.withPacksAndViolations(RefusalCode.ARTIFACT_REFERENCED, detail, packs, violations);
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
        return scan(new StagedPackViews.View(catalog, descriptors, artifacts), staged, removed);
    }

    /**
     * The same scan over the stored packs of one session. A registration passes the session it judges its identity
     * with, so that the scan reads each pack's files once and lists the configured locations once, instead of once per
     * pack it reads (every listing parses every {@code pack.yaml}, NFR-009).
     *
     * @param stored the stored packs, read through one session
     * @param staged the staged tree
     * @param removed the artifacts the replace removes
     * @return the referrers, by pack and referrer; empty when nothing refers
     */
    public List<Referrer> scan(StagedPackViews.View stored, StagedTree staged, Set<Urn> removed) {
        return scan(stored, staged, removed, false);
    }

    /**
     * The scan of a {@code DELETE} of artifacts from one pack (FR-010 of #99, D10): the registration's scan, and a
     * removed mission type is also referred to by every artifact bound to it (a step contract whose {@code mission}
     * names it, {@link PackCompositionFacts#bindings()}) and by every edge that names one of its action nodes,
     * {@code action:<type>/<step>}, at either end. An edge that starts at a removed artifact is not a referrer, as in
     * the registration's scan; an edge that starts at an action node of a removed type is.
     *
     * @param stored the stored packs, read through one session
     * @param staged the pack after the removal
     * @param removed the artifacts the removal removes
     * @return the referrers, by pack and referrer; empty when nothing refers
     */
    public List<Referrer> scanRemoval(StagedPackViews.View stored, StagedTree staged, Set<Urn> removed) {
        return scan(stored, staged, removed, true);
    }

    private List<Referrer> scan(StagedPackViews.View stored, StagedTree staged, Set<Urn> removed, boolean removal) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(removed, "removed");
        if (removed.isEmpty()) {
            return List.of();
        }
        List<Member> members = membersOf(stored, staged);
        Set<Referrer> found = new LinkedHashSet<>();
        for (Member member : members) {
            found.addAll(referrersOf(member, members, staged, removed, removal));
        }
        return found.stream()
                .sorted(Comparator.comparing(
                                (Referrer referrer) -> referrer.pack().value())
                        .thenComparing(Referrer::referrer)
                        .thenComparing(referrer -> referrer.removed().toString()))
                .toList();
    }

    /** The configured packs but the one the tree replaces, which the tree stands for, and the tree. */
    private static List<Member> membersOf(StagedPackViews.View stored, StagedTree staged) {
        List<Member> members = new ArrayList<>();
        for (ConfiguredPack configured : stored.catalog().configured()) {
            if (!staged.replacing().filter(configured.id()::equals).isPresent()) {
                readMember(stored, configured).ifPresent(members::add);
            }
        }
        PackIdentifier id = staged.replacing()
                .orElseGet(() -> new PackIdentifier(staged.descriptor().name()));
        members.add(new Member(id, staged.descriptor(), staged.facts(), true));
        return members;
    }

    private static Optional<Member> readMember(StagedPackViews.View stored, ConfiguredPack configured) {
        try {
            Optional<PackDescriptor> descriptor = stored.descriptors().find(configured.id());
            if (descriptor.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Member(
                    configured.id(), descriptor.get(), stored.artifacts().compositionFacts(configured.id()), false));
        } catch (CharterRefusal unreadable) {
            return Optional.empty();
        }
    }

    private static List<Referrer> referrersOf(
            Member member, List<Member> members, StagedTree staged, Set<Urn> removed, boolean removal) {
        List<Member> closure = closureOf(member, members);
        List<Referrer> out = new ArrayList<>();
        List<Reference> references = new ArrayList<>(referencesOf(member));
        if (removal) {
            references.addAll(removalReferencesOf(member));
        }
        for (Reference reference : references) {
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

    /**
     * The removed artifact a reference resolves to, when it resolves to one of the staged pack's. An action node is
     * removed with its mission type, and resolves where the type does.
     */
    private static Optional<Urn> removedTarget(
            PackQualifiedReference target, List<Member> closure, StagedTree staged, Set<Urn> removed) {
        Optional<Urn> urn = target.asUrn();
        Optional<Urn> owner = urn.filter(removed::contains)
                .or(() -> urn.flatMap(StepHolding::missionTypeOf).filter(removed::contains));
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        boolean resolvesToStaged = target.qualified()
                ? target.packName()
                        .filter(name -> namesStagedPack(name, staged))
                        .isPresent()
                : bareResolvesToStaged(owner.get(), closure);
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

    /**
     * What else refers to a removed mission type: each artifact bound to a mission type refers to it, and each edge
     * that starts at an action node refers to that node.
     */
    private static List<Reference> removalReferencesOf(Member member) {
        List<Reference> out = new ArrayList<>();
        member.facts()
                .bindings()
                .forEach((bound, missionType) -> Urn.parse(bound)
                        .ifPresent(source -> out.add(new Reference(
                                bound,
                                Optional.of(source),
                                new PackQualifiedReference(Optional.empty(), missionType)))));
        for (Relationship edge : member.facts().edges()) {
            if (edge.from().kind() == NodeKind.ACTION) {
                out.add(new Reference(edge.fromReference().toString(), Optional.empty(), edge.fromReference()));
            }
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
