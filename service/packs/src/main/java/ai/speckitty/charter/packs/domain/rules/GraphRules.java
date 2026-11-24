package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.RelationshipEndpointRule;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The content rules of a pack's graph documents (#554, FR-001, FR-011, T036): each node and edge binds
 * ({@link GraphBinding}), each edge's relation allows its endpoints (slice F's bridge, {@link
 * RelationshipEndpointRule}), the pack's tensions are consistent, and each {@code anti_pattern} node has its file
 * (ADR-0052 decision 14). Each is a kernel rule over the facts of the one read ({@link PackContents}); it reads no
 * file and knows no surface.
 *
 * <p>Both judge only the documents the read read: a document the read refused is a read fault of its own
 * ({@code drg_schema_error}), and it holds no fact. The rules that read the pack's edges for another kind (the mission
 * type's and the step enhancement's) are switched off when a document could not be read ({@link #graphsRead}).
 */
public final class GraphRules {

    /**
     * A node or an edge of a document the read read that does not bind: one {@link PackCodes#DRG_SCHEMA_ERROR} per
     * node or edge, at its place ({@code /nodes/<n>} or {@code /edges/<n>}), in document order, the shards first and
     * then the fragment, in {@link GraphBinding}'s words.
     */
    public static final Rule<PackContents> UNBOUND = GraphRules::unbound;

    /**
     * An edge the bridge refuses (FR-011, #515): an {@code overrides} edge whose source is not of an activatable kind
     * ({@link PackCodes#OVERRIDES_SOURCE_NOT_ACTIVATABLE}), then an edge whose relation does not allow its endpoint
     * kinds ({@link PackCodes#RELATIONSHIP_ENDPOINT_NOT_ALLOWED}), each at {@code /edges/<n>} of its document, in edge
     * order, as the load lists them.
     */
    public static final Rule<PackContents> ENDPOINT_NOT_ALLOWED = GraphRules::endpoints;

    /**
     * The pack's tensions are consistent (ADR-0045 decisions 1 and 6), across every graph document it read, in merge
     * order: a second tension on an unordered pair of bare URNs ({@link PackCodes#TENSION_DUPLICATED}, at the second,
     * naming the first), and a tension in the wrong orientation ({@link PackCodes#TENSION_ORIENTATION}): the side the
     * pack owns is the source, and when it owns both sides, or neither, the smaller bare URN is. A side is owned when
     * its reference is bare or names the pack's own name and the pack holds the artifact. A tension of an artifact
     * with itself is {@link PackCodes#TENSION_SELF}, reported by the binding ({@link GraphBinding}).
     */
    public static final Rule<PackContents> TENSION_CONSISTENCY = GraphRules::tensions;

    /**
     * An {@code anti_pattern} node the pack declares with no file (ADR-0052 decision 14, ADR-0053 decision 31; #154): an
     * anti-pattern is held by its file, {@code anti_patterns/<id>.anti-pattern.yaml}, and a node of it in a graph
     * document of the same pack only declares it. One {@link PackCodes#DRG_SCHEMA_ERROR} per such node, at its place
     * ({@code /nodes/<n>}), in document order, the shards first and then the fragment, as a node of a kind the pack may
     * not declare is ({@link GraphBinding}). Only the pack's own nodes are judged: another pack's anti-pattern is a
     * qualified edge endpoint, which the reference check judges. A node of any other kind is not judged here.
     *
     * <p>Over facts alone, a file the read refused holds no fact, so its node reads as having none; the storage edge,
     * which knows the refused files, states the node's fault only when its file is not one of them
     * ({@link #antiPatternsWithoutFile(PackContents, Predicate)}), so the file's own fault is the one cause.
     */
    public static final Rule<PackContents> ANTI_PATTERN_WITHOUT_FILE =
            contents -> antiPatternsWithoutFile(contents, file -> false);

    /** Every graph rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES = ModularRuleset.<PackContents>emptyRules()
            .must(UNBOUND)
            .must(ENDPOINT_NOT_ALLOWED)
            .must(TENSION_CONSISTENCY)
            .must(ANTI_PATTERN_WITHOUT_FILE);

    private GraphRules() {}

    static List<Reason> unbound(PackContents contents) {
        List<Reason> findings = new ArrayList<>();
        for (RawGraph raw : read(contents)) {
            findings.addAll(GraphBinding.graph(contents.root().name(), raw).findings());
        }
        return findings;
    }

    static List<Reason> endpoints(PackContents contents) {
        RelationshipEndpointRule.Scope scope = new RelationshipEndpointRule.Scope(
                MissionTypeRules.ownName(contents),
                contents.stepEnhancements().enhancements().stream()
                        .map(StepEnhancementFacts.Enhancement::id)
                        .collect(Collectors.toSet()));
        List<Reason> sources = new ArrayList<>();
        List<Reason> table = new ArrayList<>();
        for (RawGraph raw : read(contents)) {
            for (int i = 0; i < raw.edges().size(); i++) {
                RawEdge edge = raw.edges().get(i);
                Optional<RelationshipEndpointRule.Refusal> refusal = RelationshipEndpointRule.refusal(
                        new RelationshipEndpointRule.Edge(raw.file(), edge.source(), edge.target(), edge.relation()),
                        scope);
                if (refusal.isPresent()) {
                    boolean byTable =
                            RelationshipEndpointRule.CODE.equals(refusal.get().code());
                    (byTable ? table : sources)
                            .add(Findings.at(
                                    byTable
                                            ? PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED
                                            : PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE,
                                    raw.file(),
                                    List.of(GraphBinding.EDGES, Integer.toString(i)),
                                    Paths.origin(contents.root().name(), raw.file()) + ": "
                                            + refusal.get().reason()));
                }
            }
        }
        List<Reason> findings = new ArrayList<>(sources);
        findings.addAll(table);
        return findings;
    }

    static List<Reason> tensions(PackContents contents) {
        String own = MissionTypeRules.ownName(contents);
        Set<String> held = contents.artifacts().stream()
                .map(artifact -> artifact.kind() + ":" + artifact.id())
                .collect(Collectors.toSet());
        Map<Set<Urn>, String> seen = new HashMap<>();
        List<Reason> findings = new ArrayList<>();
        for (RawGraph raw : read(contents)) {
            String origin = Paths.origin(contents.root().name(), raw.file());
            for (int i = 0; i < raw.edges().size(); i++) {
                RawEdge edge = raw.edges().get(i);
                if (!RelationType.TENSION.token().equals(edge.relation())) {
                    continue;
                }
                Optional<PackQualifiedReference> source = reference(edge.source());
                Optional<PackQualifiedReference> target = reference(edge.target());
                Optional<Urn> from = source.flatMap(PackQualifiedReference::asUrn);
                Optional<Urn> to = target.flatMap(PackQualifiedReference::asUrn);
                if (from.isEmpty() || to.isEmpty() || from.get().equals(to.get())) {
                    // Not a reference, or a tension of an artifact with itself: the binding reports it.
                    continue;
                }
                List<String> place = List.of(GraphBinding.EDGES, Integer.toString(i));
                String written = raw.file() + " /edges/" + i;
                Set<Urn> pair = Set.of(from.get(), to.get());
                String first = seen.putIfAbsent(pair, written);
                if (first != null) {
                    findings.add(Findings.at(
                            PackCodes.TENSION_DUPLICATED,
                            raw.file(),
                            place,
                            origin + ": a second tension on " + canonical(from.get(), to.get()) + "; " + first
                                    + " already joins the pair"));
                    continue;
                }
                boolean sourceOwned = owned(source.get(), from.get(), own, held);
                boolean targetOwned = owned(target.get(), to.get(), own, held);
                Optional<String> expected = sourceOwned == targetOwned
                        ? (from.get().toString().compareTo(to.get().toString()) > 0
                                ? Optional.of(to.get().toString())
                                : Optional.empty())
                        : (targetOwned ? Optional.of(to.get().toString()) : Optional.empty());
                if (expected.isPresent()) {
                    findings.add(Findings.at(
                            PackCodes.TENSION_ORIENTATION,
                            raw.file(),
                            place,
                            origin + ": the tension between " + canonical(from.get(), to.get()) + " is written with "
                                    + from.get() + " as its source; its source is " + expected.get()
                                    + (sourceOwned == targetOwned
                                            ? ", the smaller of the two"
                                            : ", the side the pack holds")));
                }
            }
        }
        return findings;
    }

    /**
     * {@link #ANTI_PATTERN_WITHOUT_FILE}, less each node whose file is there but was refused for a fault of its own
     * (A9): the node has its file, and the file's fault is the one cause, so the node is not stated as having none.
     *
     * @param contents the facts of the one read
     * @param refused whether the read refused the file at a pack-relative path, for a violation of its own
     * @return one {@link PackCodes#DRG_SCHEMA_ERROR} per {@code anti_pattern} node with no file, in document order
     */
    public static List<Reason> antiPatternsWithoutFile(PackContents contents, Predicate<String> refused) {
        Set<String> held = contents.artifacts().stream()
                .map(artifact -> artifact.kind() + ":" + artifact.id())
                .collect(Collectors.toSet());
        List<Reason> findings = new ArrayList<>();
        for (RawGraph raw : read(contents)) {
            for (int i = 0; i < raw.nodes().size(); i++) {
                Optional<Urn> urn = antiPattern(raw.shape(), raw.nodes().get(i));
                if (urn.isPresent() && !held.contains(urn.get().toString()) && !refused.test(fileOf(urn.get()))) {
                    findings.add(Findings.at(
                            PackCodes.DRG_SCHEMA_ERROR,
                            raw.file(),
                            List.of(GraphBinding.NODES, Integer.toString(i)),
                            Paths.origin(contents.root().name(), raw.file()) + ": " + urn.get()
                                    + " is declared with no file; an anti-pattern is held by "
                                    + fileOf(urn.get())
                                    + " in the same pack"));
                }
            }
        }
        return findings;
    }

    /** The one file that holds an anti-pattern: {@code anti_patterns/<id>.anti-pattern.yaml}. */
    private static String fileOf(Urn antiPattern) {
        return NodeKind.ANTI_PATTERN.plural() + "/" + antiPattern.id() + ".anti-pattern.yaml";
    }

    /** The URN of an {@code anti_pattern} node as written: a shard URN, or a fragment id of the plural kind. */
    private static Optional<Urn> antiPattern(RawGraph.Shape shape, RawNode node) {
        return switch (shape) {
            case SHARD -> Urn.parse(node.ref()).filter(urn -> urn.kind() == NodeKind.ANTI_PATTERN);
            case FRAGMENT ->
                NodeKind.ANTI_PATTERN.plural().equals(node.kind())
                                && !node.ref().isEmpty()
                        ? Optional.of(new Urn(NodeKind.ANTI_PATTERN, node.ref()))
                        : Optional.empty();
        };
    }

    /** Whether the pack holds the artifact a reference names: a bare reference, or one qualified with its own name. */
    private static boolean owned(PackQualifiedReference reference, Urn urn, String own, Set<String> held) {
        return reference.packName().map(own::equals).orElse(true) && held.contains(urn.toString());
    }

    private static String canonical(Urn one, Urn other) {
        return one.toString().compareTo(other.toString()) <= 0 ? one + " and " + other : other + " and " + one;
    }

    private static Optional<PackQualifiedReference> reference(String text) {
        try {
            return Optional.of(PackQualifiedReference.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** The documents the read read, in merge order: the shards, then the fragment. */
    static List<RawGraph> read(PackContents contents) {
        List<RawGraph> read = new ArrayList<>();
        for (FileRead<RawGraph> document : documents(contents)) {
            if (document instanceof FileRead.Read<RawGraph> good) {
                read.add(good.value());
            }
        }
        return read;
    }

    /**
     * Whether every graph document of the pack was read (the edges-read gate, T035): one the read refused is a read
     * fault of its own, and a rule that reads the pack's edges is not judged without it, as a step or an enhancement
     * would otherwise lack an edge the pack may well hold. The one read's fact is the gate on every surface: a document
     * the read marked unreadable, whether its text, its schema or a {@code body_path} that leaves the pack refused it.
     *
     * @param contents the pack's facts
     * @return true when no graph document is unreadable
     */
    static boolean graphsRead(PackContents contents) {
        return documents(contents).stream().noneMatch(FileRead.Unreadable.class::isInstance);
    }

    /**
     * The step enhancements whose edges have an endpoint written as a pack-qualified reference that does not parse, a
     * qualifier that is no pack id among them (#582), by id. The schemas cannot cap the qualifier, so the binding
     * refuses such an edge; the step-enhancement edge rules then leave that one enhancement unjudged rather than count
     * an edge the pack wrote, and every other element of the pack is judged as before.
     *
     * @param contents the pack's contents
     * @return the ids of the step enhancements an edge with a malformed endpoint starts at
     */
    static Set<String> enhancementsWithMalformedEdges(PackContents contents) {
        String prefix = NodeKind.STEP_ENHANCEMENT.token() + ":";
        String own = MissionTypeRules.ownName(contents);
        return documents(contents).stream()
                .filter(document -> document instanceof FileRead.Read<RawGraph>)
                .flatMap(document -> ((FileRead.Read<RawGraph>) document).value().edges().stream())
                .filter(edge -> RelationType.fromToken(edge.relation())
                        .filter(type -> type == RelationType.ENHANCES || type == RelationType.OVERRIDES)
                        .isPresent())
                .filter(edge -> malformed(edge.source()) || malformed(edge.target()))
                .flatMap(edge -> ownSource(edge.source(), own).stream())
                .filter(source -> source.startsWith(prefix))
                .map(source -> source.substring(prefix.length()))
                .collect(Collectors.toSet());
    }

    /**
     * The URN of an edge's source when the source is this pack's: bare, qualified with the pack's own name, or written
     * with a qualifier that does not parse (that edge is refused, so its enhancement is left unjudged); empty for a
     * source another pack holds, whose enhancement this pack's rules do not judge.
     */
    private static Optional<String> ownSource(String source, String own) {
        Optional<PackQualifiedReference> parsed = reference(source);
        if (parsed.isEmpty()) {
            return Optional.of(urnPart(source));
        }
        return parsed.get().packName().filter(name -> !name.equals(own)).isPresent()
                ? Optional.empty()
                : Optional.of(parsed.get().urn());
    }

    /** The text after the last {@code ::}, or the whole text when it holds none. */
    private static String urnPart(String endpoint) {
        int qualifier = endpoint.lastIndexOf("::");
        return qualifier < 0 ? endpoint : endpoint.substring(qualifier + 2);
    }

    private static boolean malformed(String endpoint) {
        return endpoint.contains("::") && reference(endpoint).isEmpty();
    }

    /** The graph documents of the pack, in merge order: the shards, then the fragment. */
    static List<FileRead<RawGraph>> documents(PackContents contents) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        return documents;
    }
}
