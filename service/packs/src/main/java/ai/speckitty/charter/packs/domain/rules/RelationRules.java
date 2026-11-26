package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ComposedRelations;
import ai.speckitty.charter.packs.domain.ComposedRelations.Edge;
import ai.speckitty.charter.packs.domain.ComposedRelations.Place;
import ai.speckitty.charter.packs.domain.EnhancementCompose;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The relation rules of a composed closure (IC-15, C28, C36, A6, A8, A11, A12, the lock a step inherits from its
 * mission type, OD-2 (A), and the specialization rule of ruling D2), moved from the application ring onto the walker
 * (#639, ADR-0082 slice 4). They are pure checks over the typed edges the packs write, never over {@code GraphWalk},
 * which sees derived relations only (#397). Each reads the subject's placed edges ({@link ComposedRelations}); the
 * cycles and the grounding are {@link PackGraph}'s walks, iterative, so a long chain cannot exhaust the stack.
 * {@link PackRulesets#relations} declares them, each with every fault it finds, in the order of the edges. A fault an
 * earlier rule implies is not found again: a self-override is no override cycle, and an enhancer on or leading into a
 * reported enhances cycle is not judged ungrounded.
 */
final class RelationRules {

    private RelationRules() {}

    // ---- A12, A11, A6 -------------------------------------------------------------------------------------------

    /** Every {@code overrides} edge whose target resolves to its own source (A12). */
    static List<ClosureFault> selfOverride(ComposedRelations relations) {
        return relations.edges().stream()
                .filter(Edge::isSelfOverride)
                .map(edge -> violation(
                        relations,
                        RefusalCode.SELF_OVERRIDE,
                        "the overrides edge of " + edge.source() + " in pack " + relations.name(edge.source())
                                + " resolves to its own source",
                        edge.writer(),
                        edge.source()))
                .toList();
    }

    /** Every artifact that both enhances and overrides one target (A11). */
    static List<ClosureFault> conflicting(ComposedRelations relations) {
        Set<List<Place>> enhanced = new LinkedHashSet<>();
        relations.edges().stream()
                .filter(edge -> edge.is(RelationType.ENHANCES))
                .forEach(edge -> edge.target().ifPresent(target -> enhanced.add(List.of(edge.source(), target))));
        return relations.edges().stream()
                .filter(edge -> edge.is(RelationType.OVERRIDES))
                .filter(edge -> edge.target()
                        .filter(target -> enhanced.contains(List.of(edge.source(), target)))
                        .isPresent())
                .map(edge -> violation(
                        relations,
                        RefusalCode.CONFLICTING_RELATIONS,
                        edge.source() + " in pack " + relations.name(edge.source()) + " both enhances and overrides "
                                + edge.target().orElseThrow(),
                        edge.writer(),
                        edge.source(),
                        edge.target().orElseThrow()))
                .toList();
    }

    /**
     * Every {@code overrides} edge onto a target that is not open (A6): the level is the strictest any pack of the closure
     * declares for it, each policy scoped to what its pack and the packs it builds on hold (A15), the source's own pack
     * left out as it is by the mutability check (B8).
     */
    static List<ClosureFault> notPermitted(ComposedRelations relations) {
        Map<PackUlid, MutabilityPolicySource> scoped = relations.scopedPolicies();
        Map<PackIdentifier, PerPackPolicy> judging = new LinkedHashMap<>();
        List<ClosureFault> faults = new ArrayList<>();
        for (Edge edge : relations.edges()) {
            if (!edge.is(RelationType.OVERRIDES) || edge.target().isEmpty()) {
                continue;
            }
            Place target = edge.target().get();
            PerPackPolicy policy =
                    judging.computeIfAbsent(edge.source().pack(), pack -> judgedWithout(relations, pack, scoped));
            PerPackPolicy.Level level =
                    policy.levelIn(List.copyOf(policy.byPack().keySet()), target.urn());
            if (level.level() != MutabilityLevel.OPEN) {
                faults.add(notOpen(relations, edge, target, level));
            }
        }
        return List.copyOf(faults);
    }

    /**
     * Every enhancement of a step whose mission type is locked (C22 and C25 as amended, OD-2 (A)): a step takes its mission
     * type's level, judged along the closure with the enhancing pack's own policy left out, as {@link #notPermitted}
     * leaves out the source's pack. The read refuses the same closure {@code pack_unreadable} from the merge.
     */
    static List<ClosureFault> lockedStepEnhanced(ComposedRelations relations) {
        return EnhancementCompose.lockedTargets(
                        relations.packs(), relations::edgesOf, new PerPackPolicy(relations.scopedPolicies()))
                .stream()
                .map(locked -> {
                    List<PackUnderResolution> involved = new ArrayList<>(List.of(locked.enhancingPack()));
                    locked.lockedBy()
                            .filter(pack -> !pack.equals(locked.enhancingPack()))
                            .ifPresent(involved::add);
                    return fault(RefusalCode.OVERRIDE_NOT_PERMITTED, locked.detail(), involved);
                })
                .toList();
    }

    private static PerPackPolicy judgedWithout(
            ComposedRelations relations, PackIdentifier pack, Map<PackUlid, MutabilityPolicySource> scoped) {
        Map<PackUlid, MutabilityPolicySource> others = new LinkedHashMap<>(scoped);
        others.remove(relations.packOf(pack).descriptorId());
        return new PerPackPolicy(others);
    }

    private static ClosureFault notOpen(
            ComposedRelations relations, Edge edge, Place target, PerPackPolicy.Level level) {
        List<PackUnderResolution> involved =
                new ArrayList<>(List.of(relations.packOf(edge.source().pack())));
        level.setBy().flatMap(relations::byDescriptorPackId).ifPresent(involved::add);
        return fault(
                RefusalCode.OVERRIDE_NOT_PERMITTED,
                "the overrides edge of " + edge.source() + " in pack " + relations.name(edge.source()) + " is onto "
                        + target + ", which is " + level.level().token() + along(level)
                        + "; only an open artifact can be overridden",
                involved);
    }

    private static String along(PerPackPolicy.Level level) {
        return level.setBy().map(setBy -> " by the pack " + setBy.value()).orElse("");
    }

    // ---- C28 and A8 ---------------------------------------------------------------------------------------------

    /** Every cycle of {@code overrides} edges, each once; a self-override is {@link #selfOverride}'s, not a cycle. */
    static List<ClosureFault> overrideCycle(ComposedRelations relations) {
        return PackGraph.cyclesIn(relations.graphOf(RelationType.OVERRIDES, edge -> !edge.isSelfOverride())).stream()
                .map(cycle -> violation(
                        relations,
                        RefusalCode.OVERRIDE_CYCLE,
                        "overrides edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)))
                .toList();
    }

    /** Every cycle of {@code enhances} edges, each once. */
    static List<ClosureFault> enhancesCycle(ComposedRelations relations) {
        return relations.enhancesCycles().stream()
                .map(cycle -> violation(
                        relations,
                        RefusalCode.ENHANCES_CYCLE,
                        "enhances edges form a cycle: " + describe(cycle),
                        cycle.toArray(Place[]::new)))
                .toList();
    }

    /**
     * Every enhancer none of whose {@code enhances} chains reaches an artifact that enhances nothing (A8): an
     * enhancement never stands alone, and a chain that ends at an artifact nothing holds ends nowhere. Grounding spreads
     * from each edge onto a held non-enhancer to its enhancers, one pass over the reverse edges; an enhancer that is on,
     * or reaches, an enhances cycle is that cycle's fault and is not found again here.
     */
    static List<ClosureFault> ungroundedEnhancer(ComposedRelations relations) {
        Set<Place> enhancers = new LinkedHashSet<>();
        relations.edges().stream()
                .filter(edge -> edge.is(RelationType.ENHANCES))
                .forEach(edge -> enhancers.add(edge.source()));
        Set<Place> onCycles = new LinkedHashSet<>();
        relations.enhancesCycles().forEach(onCycles::addAll);
        return PackGraph.ungrounded(enhancers, relations.enhancesGraph(), onCycles).stream()
                .map(enhancer -> violation(
                        relations,
                        RefusalCode.ENHANCES_CYCLE,
                        enhancer + " in pack " + relations.name(enhancer)
                                + " enhances, and no chain of enhances edges from it reaches an artifact that enhances"
                                + " nothing",
                        enhancer))
                .toList();
    }

    // ---- ruling D2 ----------------------------------------------------------------------------------------------

    /**
     * Every cycle the closure's {@code specializes_from} edges between agent profiles form, each once (#556, FR-003): a
     * profile's lineage ends, so its depth is defined. One fault per cycle, naming its profile ids in order and the
     * packs that write its edges.
     */
    static List<ClosureFault> specializationCycle(ComposedRelations relations) {
        ComposedRelations.Specializations specializations = relations.specializations();
        return PackGraph.cyclesIn(specializations.graph()).stream()
                .map(cycle -> specialization(cycle, specializations.writers()))
                .toList();
    }

    private static ClosureFault specialization(List<Urn> cycle, Map<List<Urn>, PackUnderResolution> writers) {
        List<String> ids = new ArrayList<>(cycle.stream().map(Urn::id).toList());
        ids.add(ids.getFirst());
        Set<PackUnderResolution> involved = new LinkedHashSet<>();
        for (int i = 0; i < cycle.size(); i++) {
            List<Urn> edge = List.of(cycle.get(i), cycle.get((i + 1) % cycle.size()));
            Optional.ofNullable(writers.get(edge)).ifPresent(involved::add);
        }
        return fault(
                RefusalCode.SPECIALIZATION_CYCLE,
                "specializes_from edges among agent profiles form a cycle: " + String.join(" -> ", ids)
                        + "; a profile's lineage ends at a root",
                List.copyOf(involved));
    }

    // ---- naming -------------------------------------------------------------------------------------------------

    private static String describe(Collection<Place> cycle) {
        List<String> names = new ArrayList<>(cycle.stream().map(Place::toString).toList());
        names.add(names.getFirst());
        return String.join(" -> ", names);
    }

    /** A fault naming the packs of the places, each once. */
    private static ClosureFault violation(
            ComposedRelations relations, RefusalCode code, String detail, Place... places) {
        return build(relations, code, detail, Optional.empty(), places);
    }

    /** A fault naming first the writing pack, then the packs of the places, each once. */
    private static ClosureFault violation(
            ComposedRelations relations, RefusalCode code, String detail, PackUnderResolution writer, Place... places) {
        return build(relations, code, detail, Optional.of(writer), places);
    }

    private static ClosureFault build(
            ComposedRelations relations,
            RefusalCode code,
            String detail,
            Optional<PackUnderResolution> writer,
            Place... places) {
        Set<PackUnderResolution> involved = new LinkedHashSet<>();
        writer.ifPresent(involved::add);
        for (Place place : places) {
            involved.add(relations.packOf(place.pack()));
        }
        return fault(code, detail, List.copyOf(involved));
    }

    /** A fault naming packs of the closure, each as the refusal names a pack, about the first it names. */
    private static ClosureFault fault(RefusalCode code, String detail, List<PackUnderResolution> packs) {
        List<RefusalPack> named = packs.stream()
                .distinct()
                .map(pack -> new RefusalPack(
                        pack.descriptorId(),
                        pack.configured().id(),
                        pack.descriptor().name()))
                .toList();
        return ClosureFault.of(PackOrigins.firstOf(named), code, detail, named);
    }
}
