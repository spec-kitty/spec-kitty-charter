package ai.speckitty.charter.packs.domain.rules;

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
import ai.speckitty.charter.packs.domain.RelationshipEndpointRule;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The content rules of a pack's graph documents (#554, FR-001, FR-011, T036): each node and edge binds
 * ({@link GraphBinding}), and each edge's relation allows its endpoints (slice F's bridge, {@link
 * RelationshipEndpointRule}). Each is a kernel rule over the facts of the one read ({@link PackContents}); it reads no
 * file and knows no surface.
 *
 * <p>Both judge only the documents the read read: a document the read refused is a read fault of its own
 * ({@code drg_schema_error}), and it holds no fact. The rules that read the pack's edges for another kind (the mission
 * type's and the step enhancement's) are switched off when a document could not be read ({@link #graphsRead}).
 */
public final class GraphRules {

    private static final String EDGES = "edges";

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

    /** Every graph rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES = ModularRuleset.<PackContents>emptyRules()
            .must(UNBOUND)
            .must(ENDPOINT_NOT_ALLOWED)
            .must(TENSION_CONSISTENCY);

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
                                    List.of(EDGES, Integer.toString(i)),
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
                List<String> place = List.of(EDGES, Integer.toString(i));
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

    /** The graph documents of the pack, in merge order: the shards, then the fragment. */
    static List<FileRead<RawGraph>> documents(PackContents contents) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        return documents;
    }
}
