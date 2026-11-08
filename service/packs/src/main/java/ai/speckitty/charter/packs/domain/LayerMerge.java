package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The layer merge: the one winner function over a composed closure, its facts and its policies (FR-009 to FR-012,
 * rules C15, C17 and C18). It is pure: it reads what it is given and answers a {@link MergedClosure}; it reads no store,
 * holds no state and judges nothing the compose has not judged.
 *
 * <p>The version of an artifact id in force is chosen in two steps, in the order the rules give:
 *
 * <ol>
 *   <li><strong>Per level</strong> (C15, #357). The packs of one level that hold the id contest it. The compose has
 *       refused the contest unless a descriptor {@code overrides} entry settles it, and the member the entry names holds
 *       the version in force at that level. The step of a mission type ({@code action:<M>/<step>}) is part of {@code M},
 *       so the entry that settles {@code M} settles its steps. The compose refuses an unsettled contest of every
 *       artifact but a step, so a step contest no entry settles can reach this function: it is refused here with
 *       {@code pack_sibling_conflict}, never decided by load order. A preset has no such entry: two copies of one level
 *       are parent and child (C34), and the child, last in closure order, holds the level (ADR-0053 d6, C29).
 *   <li><strong>Across levels</strong> (C17, C18, ADR-0059 D3). In merge order (built-in, then org, then project) the
 *       level that applies wins, and its artifact replaces the others whole: nothing of a losing version is read.
 * </ol>
 */
public final class LayerMerge {

    private LayerMerge() {}

    /**
     * Merges a composed closure.
     *
     * @param closure the composed closure, in closure order
     * @param factsByConfiguredId the facts each pack was read with, by configured identifier
     * @param policy the mutability policies of the closure's packs
     * @return the winners and the layer overrides
     */
    public static MergedClosure merge(
            List<PackUnderResolution> closure,
            Map<String, PackCompositionFacts> factsByConfiguredId,
            PerPackPolicy policy) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(factsByConfiguredId, "factsByConfiguredId");
        Objects.requireNonNull(policy, "policy");
        List<String> closurePackIds =
                closure.stream().map(pack -> pack.descriptor().packId()).toList();
        Map<String, Set<String>> settlements = settlements(closure);
        Map<Urn, MergedClosure.Winner> winners = new LinkedHashMap<>();
        List<LayerOverride> overrides = new ArrayList<>();
        holdersByUrn(closure).forEach((urn, holders) -> {
            List<PackUnderResolution> byLevel =
                    levelWinners(holders, settledBy(urn, settlements.getOrDefault(settlementKey(urn), Set.of())));
            PackUnderResolution winner = byLevel.getLast();
            winners.put(urn, winnerOf(winner, urn, policy.levelIn(closurePackIds, urn)));
            for (PackUnderResolution loser : byLevel.subList(0, byLevel.size() - 1)) {
                allowOverride(urn, winner, loser, closurePackIds, policy);
                overrides.add(overrideOf(urn, winner, loser));
            }
        });
        Map<String, MergedClosure.MergedPreset> presets = mergedPresets(closure, factsByConfiguredId);
        return new MergedClosure(
                winners,
                overrides,
                languagesOf(winners, closure, factsByConfiguredId),
                presets,
                floorOf(closure, factsByConfiguredId, winners.keySet()));
    }

    /**
     * Refuses a layer override the owner's level forbids (C21, FR-011, ADR-0063 D3): only an {@code open} artifact may be
     * replaced by a later level. The level is judged along the closure with the winning pack left out, so the losers'
     * own files and any pack that raised the level count, and a pack never blocks its own override (research R3).
     */
    private static void allowOverride(
            Urn urn,
            PackUnderResolution winner,
            PackUnderResolution loser,
            List<String> closurePackIds,
            PerPackPolicy policy) {
        List<String> judging = closurePackIds.stream()
                .filter(packId -> !packId.equals(winner.descriptor().packId()))
                .toList();
        PerPackPolicy.Level level = policy.levelIn(judging, urn);
        if (level.level() != MutabilityLevel.OPEN) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "pack `" + winner.descriptor().name() + "` at "
                            + winner.configured().level().token()
                            + " replaces `" + urn + "` of pack `"
                            + loser.descriptor().name() + "` at "
                            + loser.configured().level().token() + ", but the artifact is "
                            + level.level().token()
                            + " along the closure"
                            + level.setBy()
                                    .map(by -> " (set by pack " + by + ")")
                                    .orElse("")
                            + "; only an open artifact may be replaced by a later level",
                    List.of(refusalPack(winner), refusalPack(loser)));
        }
    }

    private static RefusalPack refusalPack(PackUnderResolution pack) {
        return new RefusalPack(
                pack.descriptor().packId(),
                pack.configured().id().value(),
                pack.descriptor().name());
    }

    /**
     * The languages each winner declares, read from the winner's own pack alone: a version a later level replaced is
     * never consulted, so nothing is inherited (C18). A winner that declares none is absent.
     */
    private static Map<Urn, Set<String>> languagesOf(
            Map<Urn, MergedClosure.Winner> winners,
            List<PackUnderResolution> closure,
            Map<String, PackCompositionFacts> facts) {
        Map<String, PackCompositionFacts> byDescriptorPackId = new LinkedHashMap<>();
        closure.forEach(pack -> byDescriptorPackId.put(pack.descriptor().packId(), factsOf(pack, facts)));
        Map<Urn, Set<String>> languages = new LinkedHashMap<>();
        winners.forEach((urn, winner) -> {
            List<String> declared = byDescriptorPackId
                    .getOrDefault(winner.descriptorPackId(), PackCompositionFacts.NONE)
                    .languages()
                    .getOrDefault(urn.toString(), List.of());
            if (!declared.isEmpty()) {
                languages.put(urn, new LinkedHashSet<>(declared));
            }
        });
        return languages;
    }

    /** One preset id's copies: the pack that holds each, in closure order. */
    private record Copy(PackUnderResolution pack, Preset preset) {}

    /** Every copy of every preset id, in closure order; the ids in the order the closure first holds them. */
    private static Map<String, List<Copy>> copiesById(
            List<PackUnderResolution> closure, Map<String, PackCompositionFacts> facts) {
        Map<String, List<Copy>> copies = new LinkedHashMap<>();
        for (PackUnderResolution pack : closure) {
            for (Preset preset : factsOf(pack, facts).presets()) {
                copies.computeIfAbsent(preset.id(), ignored -> new ArrayList<>())
                        .add(new Copy(pack, preset));
            }
        }
        return copies;
    }

    private static PackCompositionFacts factsOf(PackUnderResolution pack, Map<String, PackCompositionFacts> facts) {
        return facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE);
    }

    /**
     * Each preset id with the copy in force and the union of the must-have items of the copy in force at each level (A1,
     * C29, C31). The same
     * winner function as an artifact's picks the copy: the pack last in closure order holds its level (a child after the
     * parent it extends), and the nearest project level applies. The requirement is computed beside it and only
     * tightens across levels: a copy that wins never drops an item the copy in force at another level requires, and a
     * copy a same-level copy replaced requires nothing.
     */
    private static Map<String, MergedClosure.MergedPreset> mergedPresets(
            List<PackUnderResolution> closure, Map<String, PackCompositionFacts> facts) {
        Map<String, MergedClosure.MergedPreset> merged = new LinkedHashMap<>();
        copiesById(closure, facts).forEach((id, copies) -> {
            List<PackUnderResolution> holders = copies.stream().map(Copy::pack).toList();
            PackUnderResolution winner =
                    levelWinners(holders, LayerMerge::lastHolder).getLast();
            Preset applied = copies.stream()
                    .filter(copy -> copy.pack()
                            .descriptor()
                            .packId()
                            .equals(winner.descriptor().packId()))
                    .findFirst()
                    .orElseThrow()
                    .preset();
            merged.put(
                    id,
                    new MergedClosure.MergedPreset(
                            applied, winner.descriptor().packId(), mustHaveOf(copiesInForceAtEachLevel(copies))));
        });
        return merged;
    }

    /**
     * The copy of a preset id in force at each level: within a level the copy of the pack last in closure order (a
     * child after the parent it extends) replaces the others, so a replaced copy requires nothing. The same winner
     * function as {@link #mergedPresets} and the artifacts use; the requirement is the union across these copies (A1).
     */
    private static List<Copy> copiesInForceAtEachLevel(List<Copy> copies) {
        Set<String> inForce = levelWinners(copies.stream().map(Copy::pack).toList(), LayerMerge::lastHolder).stream()
                .map(pack -> pack.descriptor().packId())
                .collect(Collectors.toSet());
        return copies.stream()
                .filter(copy -> inForce.contains(copy.pack().descriptor().packId()))
                .toList();
    }

    /** The must-have items of the copies given, each once, in closure order. */
    private static List<Urn> mustHaveOf(List<Copy> copies) {
        Set<Urn> items = new LinkedHashSet<>();
        copies.forEach(copy -> copy.preset()
                .required()
                .forEach((kind, ids) -> ids.forEach(id -> items.add(new Urn(NodeKind.of(kind), id)))));
        return List.copyOf(items);
    }

    /**
     * The floor over the copy of every preset id in force at each level of the closure, restricted to the artifacts a pack of it holds (A1,
     * C31, A10), which {@link #merge} answers with the winners but which is judged on no override: a closure whose
     * layer override the owner's level forbids still has this floor.
     *
     * @param closure the composed closure, in closure order
     * @param factsByConfiguredId the facts each pack was read with, by configured identifier
     * @return the union floor
     */
    public static RequiredFloor floorOf(
            List<PackUnderResolution> closure, Map<String, PackCompositionFacts> factsByConfiguredId) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(factsByConfiguredId, "factsByConfiguredId");
        return floorOf(closure, factsByConfiguredId, holdersByUrn(closure).keySet());
    }

    /**
     * The floor over the copy of every preset id in force at each level, restricted to what the merged closure holds
     * (A1, C31, A10).
     */
    private static RequiredFloor floorOf(
            List<PackUnderResolution> closure, Map<String, PackCompositionFacts> facts, Set<Urn> held) {
        Map<String, List<Copy>> inForce = new LinkedHashMap<>();
        copiesById(closure, facts).forEach((id, copies) -> inForce.put(id, copiesInForceAtEachLevel(copies)));
        return RequiredFloor.of(
                closure.stream()
                        .map(pack -> Map.entry(
                                pack.descriptor().packId(), presetsInForce(pack, factsOf(pack, facts), inForce)))
                        .toList(),
                held);
    }

    /** The presets of a pack that are the copy in force at its level. */
    private static List<Preset> presetsInForce(
            PackUnderResolution pack, PackCompositionFacts facts, Map<String, List<Copy>> inForce) {
        return facts.presets().stream()
                .filter(preset -> inForce.getOrDefault(preset.id(), List.of()).stream()
                        .anyMatch(copy -> copy.pack()
                                        .descriptor()
                                        .packId()
                                        .equals(pack.descriptor().packId())
                                && copy.preset().equals(preset)))
                .toList();
    }

    private static MergedClosure.Winner winnerOf(PackUnderResolution pack, Urn urn, PerPackPolicy.Level policy) {
        return new MergedClosure.Winner(
                urn,
                pack.configured().id(),
                pack.descriptor().packId(),
                pack.configured().level(),
                policy);
    }

    private static LayerOverride overrideOf(Urn urn, PackUnderResolution winner, PackUnderResolution loser) {
        return new LayerOverride(urn, sideOf(winner), sideOf(loser));
    }

    private static LayerOverride.Side sideOf(PackUnderResolution pack) {
        PackIdentifier id = pack.configured().id();
        return new LayerOverride.Side(id, pack.configured().level());
    }

    /** Every held URN with the packs that hold it, in closure order; the URNs in the order the closure first holds them. */
    private static Map<Urn, List<PackUnderResolution>> holdersByUrn(List<PackUnderResolution> closure) {
        Map<Urn, List<PackUnderResolution>> holders = new LinkedHashMap<>();
        for (PackUnderResolution pack : closure) {
            for (String defined : pack.definedArtifacts()) {
                Urn.parse(defined).ifPresent(urn -> {
                    List<PackUnderResolution> held = holders.computeIfAbsent(urn, ignored -> new ArrayList<>());
                    if (!held.contains(pack)) {
                        held.add(pack);
                    }
                });
            }
        }
        return holders;
    }

    /**
     * The pack holding the version in force at each level that holds the id, from the nearest built-in level to the
     * farthest.
     */
    private static List<PackUnderResolution> levelWinners(
            List<PackUnderResolution> holders, UnaryOperator<List<PackUnderResolution>> pickAmongSiblings) {
        Map<PackLevel, List<PackUnderResolution>> byLevel = new EnumMap<>(PackLevel.class);
        holders.forEach(pack -> byLevel.computeIfAbsent(pack.configured().level(), level -> new ArrayList<>())
                .add(pack));
        return byLevel.values().stream()
                .map(siblings -> siblings.size() == 1 ? siblings : pickAmongSiblings.apply(siblings))
                .map(List::getLast)
                .toList();
    }

    /**
     * The sibling a descriptor {@code overrides} entry names, as a one-element list (C15). A contest no entry settles is
     * refused, never decided by load order: the compose leaves a step ({@code action:<M>/<step>}) out of the sibling
     * contest, so two packs that both declare one can reach this point.
     */
    private static UnaryOperator<List<PackUnderResolution>> settledBy(Urn urn, Set<String> settled) {
        return siblings -> List.of(siblings.stream()
                .filter(pack -> settled.contains(pack.descriptor().packId()))
                .findFirst()
                .orElseThrow(() -> unsettled(urn, siblings)));
    }

    /** The preset copy of the sibling last in closure order: a child merges after the parent it extends (C29, C34). */
    private static List<PackUnderResolution> lastHolder(List<PackUnderResolution> siblings) {
        return List.of(siblings.getLast());
    }

    private static CharterRefusal unsettled(Urn urn, List<PackUnderResolution> siblings) {
        return new CharterRefusal(
                RefusalCode.PACK_SIBLING_CONFLICT,
                "packs "
                        + siblings.stream()
                                .map(pack -> "`" + pack.descriptor().name() + "` ("
                                        + pack.descriptor().packId() + ")")
                                .collect(Collectors.joining(" and "))
                        + " at "
                        + siblings.getFirst().configured().level().token() + " both hold `" + urn
                        + "`, and no overrides entry names the one whose version is in force",
                siblings.stream().map(LayerMerge::refusalPack).toList());
    }

    /** The members the closure's descriptors name as the winner of each artifact, by bare artifact URN text. */
    private static Map<String, Set<String>> settlements(List<PackUnderResolution> closure) {
        Map<String, Set<String>> members = new LinkedHashMap<>();
        for (PackUnderResolution pack : closure) {
            for (PackOverride override : pack.descriptor().overrides()) {
                members.computeIfAbsent(bare(override.artifact()), key -> new HashSet<>())
                        .add(override.member());
            }
        }
        return members;
    }

    /**
     * What settles a contest for the URN: the URN itself, or for a step {@code action:<M>/<step>} the mission type
     * {@code M}, whose contest the step shares.
     */
    private static String settlementKey(Urn urn) {
        int slash = urn.id().indexOf('/');
        if (urn.kind() == NodeKind.ACTION && slash > 0) {
            return Urn.missionType(urn.id().substring(0, slash)).toString();
        }
        return urn.toString();
    }

    /** The bare URN an {@code overrides.artifact} names: a {@code <pack>::} qualifier says where it is read, not which. */
    private static String bare(String reference) {
        try {
            PackQualifiedReference parsed = PackQualifiedReference.parse(reference);
            return parsed.asUrn().map(Urn::toString).orElse(parsed.urn());
        } catch (IllegalArgumentException malformed) {
            return reference;
        }
    }
}
