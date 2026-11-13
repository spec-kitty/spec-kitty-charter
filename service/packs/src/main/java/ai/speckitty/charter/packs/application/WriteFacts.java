package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the compose will read of a pack once one write is stored: the facts the stored pack gives, with the write laid
 * over them. The per-artifact writes and the two {@code DELETE}s hand the result to
 * {@link RegistrationImpact#check(ai.speckitty.charter.kernel.domain.PackIdentifier, java.util.function.UnaryOperator)}
 * so that the closures the pack joins are judged as the pack will be, with no second read of the pack and no copy of a
 * compose rule (IC-15).
 *
 * <p>Each function is pure. Edges are told apart by their relation, endpoints and qualifiers, as the compose's facts do
 * ({@link Relationship#equals} ignores the relation and the qualifiers, so it is not the key).
 */
public final class WriteFacts {

    private WriteFacts() {}

    /**
     * The facts with an artifact held: a write of an artifact the pack holds already changes none of them. The prompt
     * facts are kept: no per-artifact write writes a mission type's step prompts or a step enhancement's entries.
     *
     * @param facts the facts of the stored pack
     * @param artifact the artifact the write adds or replaces
     * @return the facts after the write
     */
    public static PackCompositionFacts withArtifact(PackCompositionFacts facts, Urn artifact) {
        String held = artifact.toString();
        if (facts.defined().contains(held)) {
            return facts;
        }
        List<String> defined = new ArrayList<>(facts.defined());
        defined.add(held);
        return new PackCompositionFacts(
                facts.changed(),
                defined,
                facts.references(),
                facts.policy(),
                facts.presets(),
                facts.edges(),
                facts.languages(),
                facts.bindings(),
                facts.prompts());
    }

    /**
     * The facts with the registered relationships replaced: the edges the new set lacks go, the edges it adds come, and
     * the {@code overrides} targets and the references that only those edges gave go and come with them. An edge
     * changes no prompt fact.
     *
     * @param facts the facts of the stored pack
     * @param before the registered relationships the pack stores now
     * @param after the registered relationships the write leaves
     * @return the facts after the write
     */
    public static PackCompositionFacts withRegisteredEdges(
            PackCompositionFacts facts, List<Relationship> before, List<Relationship> after) {
        Set<String> kept = keysOf(after);
        Set<String> was = keysOf(before);
        List<Relationship> dropped =
                before.stream().filter(edge -> !kept.contains(keyOf(edge))).toList();
        List<Relationship> added =
                after.stream().filter(edge -> !was.contains(keyOf(edge))).toList();
        return withEdges(facts, dropped, added);
    }

    /**
     * The facts without an artifact: it is no longer held, the registered edges that start at it are gone, and so are
     * the preset items that name it. A mission type takes its steps' prompts with it and a step enhancement its fills
     * ({@link ai.speckitty.charter.packs.domain.PromptFacts#without}).
     *
     * @param facts the facts of the stored pack
     * @param artifact the artifact the write removes
     * @param outgoing the registered edges that start at it
     * @return the facts after the write
     */
    public static PackCompositionFacts withoutArtifact(
            PackCompositionFacts facts, Urn artifact, List<Relationship> outgoing) {
        PackCompositionFacts edgeless = withEdges(facts, outgoing, List.of());
        List<String> defined = edgeless.defined().stream()
                .filter(held -> !held.equals(artifact.toString()))
                .toList();
        return new PackCompositionFacts(
                edgeless.changed(),
                defined,
                edgeless.references(),
                edgeless.policy(),
                edgeless.presets().stream()
                        .map(preset -> withoutItems(preset, artifact))
                        .toList(),
                edgeless.edges(),
                languagesWithout(edgeless.languages(), artifact),
                bindingsWithout(edgeless.bindings(), artifact),
                edgeless.prompts().without(artifact));
    }

    /**
     * The preset with the items that name an artifact removed; a kind left with no item is left out, as a preset holds
     * no empty list.
     *
     * @param preset the preset
     * @param artifact the artifact the items name
     * @return the preset after the removal; the same one when no item names the artifact
     */
    public static Preset withoutItems(Preset preset, Urn artifact) {
        Map<ArtifactKind, List<PresetItem>> kept = new LinkedHashMap<>();
        preset.artifacts().forEach((kind, items) -> {
            List<PresetItem> left =
                    items.stream().filter(item -> !names(kind, item, artifact)).toList();
            if (!left.isEmpty()) {
                kept.put(kind, left);
            }
        });
        return kept.equals(preset.artifacts()) ? preset : new Preset(preset.id(), preset.description(), kept);
    }

    /**
     * Whether a preset item names an artifact: the same kind and the same id.
     *
     * @param kind the kind of the list the item is in
     * @param item the item
     * @param artifact the artifact
     * @return whether the item names it
     */
    public static boolean names(ArtifactKind kind, PresetItem item, Urn artifact) {
        return artifact.kind().artifactKind().filter(kind::equals).isPresent()
                && item.id().equals(artifact.id());
    }

    /** The bindings of the artifacts that remain: a removed artifact is bound to nothing. */
    private static Map<String, String> bindingsWithout(Map<String, String> bindings, Urn artifact) {
        Map<String, String> remaining = new LinkedHashMap<>(bindings);
        remaining.remove(artifact.toString());
        return remaining;
    }

    /** The languages of the artifacts that remain: a removed artifact declares none. */
    private static Map<String, List<String>> languagesWithout(Map<String, List<String>> languages, Urn artifact) {
        Map<String, List<String>> remaining = new LinkedHashMap<>(languages);
        remaining.remove(artifact.toString());
        return remaining;
    }

    private static PackCompositionFacts withEdges(
            PackCompositionFacts facts, List<Relationship> dropped, List<Relationship> added) {
        Set<String> droppedKeys = keysOf(dropped);
        Map<String, Relationship> edges = new LinkedHashMap<>();
        facts.edges().stream()
                .filter(edge -> !droppedKeys.contains(keyOf(edge)))
                .forEach(edge -> edges.putIfAbsent(keyOf(edge), edge));
        added.forEach(edge -> edges.putIfAbsent(keyOf(edge), edge));
        List<Relationship> remaining = List.copyOf(edges.values());
        return new PackCompositionFacts(
                changedAfter(facts.changed(), dropped, added, remaining),
                facts.defined(),
                referencesAfter(facts.references(), dropped, added, remaining),
                facts.policy(),
                facts.presets(),
                remaining,
                facts.languages(),
                facts.bindings(),
                facts.prompts());
    }

    /** The {@code overrides} targets: those of the dropped edges go unless another edge still overrides them. */
    private static List<String> changedAfter(
            List<String> changed, List<Relationship> dropped, List<Relationship> added, List<Relationship> remaining) {
        Set<String> targets = new LinkedHashSet<>(changed);
        Set<String> stillOverridden = new HashSet<>();
        remaining.stream()
                .filter(edge -> edge.type() == RelationType.OVERRIDES)
                .forEach(edge -> stillOverridden.add(edge.toReference().toString()));
        dropped.stream()
                .filter(edge -> edge.type() == RelationType.OVERRIDES)
                .map(edge -> edge.toReference().toString())
                .filter(target -> !stillOverridden.contains(target))
                .forEach(targets::remove);
        added.stream()
                .filter(edge -> edge.type() == RelationType.OVERRIDES)
                .forEach(edge -> targets.add(edge.toReference().toString()));
        return List.copyOf(targets);
    }

    /** The references: both ends of the dropped edges go unless another edge still names them. */
    private static List<String> referencesAfter(
            List<String> references,
            List<Relationship> dropped,
            List<Relationship> added,
            List<Relationship> remaining) {
        Set<String> named = new HashSet<>();
        remaining.forEach(edge -> ends(edge).forEach(named::add));
        Set<String> out = new LinkedHashSet<>(references);
        dropped.stream()
                .flatMap(WriteFacts::ends)
                .filter(end -> !named.contains(end))
                .forEach(out::remove);
        added.stream().flatMap(WriteFacts::ends).forEach(out::add);
        return List.copyOf(out);
    }

    private static Stream<String> ends(Relationship edge) {
        return Stream.of(edge.fromReference().toString(), edge.toReference().toString());
    }

    private static Set<String> keysOf(List<Relationship> edges) {
        Set<String> keys = new HashSet<>();
        edges.forEach(edge -> keys.add(keyOf(edge)));
        return keys;
    }

    private static String keyOf(Relationship edge) {
        return String.join(
                "|",
                edge.fromReference().toString(),
                edge.type().token(),
                edge.toReference().toString());
    }
}
