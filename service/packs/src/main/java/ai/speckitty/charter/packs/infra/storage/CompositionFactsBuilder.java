package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.EndpointRule;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The one builder of a pack's composition facts ({@link PackCompositionFacts}, #554 T027, F5, P7): a plain class, not a
 * bean, so {@code CLOSURE} can judge real facts outside the service. The composing read
 * ({@link DomainRepositoryService#compositionFacts(PackIdentifier)}) and the registration's load
 * ({@link WholePackStore#loadStaged}) both build through it from the read they already made, so there is one
 * derivation and no second read of the tree.
 */
final class CompositionFactsBuilder {

    private CompositionFactsBuilder() {}

    /**
     * The pack under resolution of a pack directory, read without the service (#554, FR-015, T027): what {@code CLOSURE}
     * judges of one pack of a set (its descriptor, the artifacts its {@code overrides} edges change, the artifacts it
     * holds and the terms it holds), read and judged as the composing read reads a stored pack, so a pack read
     * without the service is judged on the facts the service judges.
     *
     * <p>It makes a read of its own, so it is for tests and for the composing read's parity checks. pack-check (#478)
     * takes a pack's facts from the readout of its one read ({@code PackReadout.facts}, {@link #readoutFacts}), never
     * from here: both build through {@link #built}, so the facts are the same.
     *
     * @param configured how the set configures the pack: its id and level
     * @param directory the pack's directory
     * @param strategy the serialization format the pack is read in
     * @return the pack under resolution
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be read, as the composing read refuses it
     */
    static PackUnderResolution underResolution(
            ConfiguredPack configured, Path directory, SerializationStrategy strategy) {
        Path packDir = directory.toAbsolutePath().normalize();
        PackIdentifier pack = configured.id();
        StoreRead read = StoreRead.of(
                packDir,
                pack,
                PackFileSet.walk(packDir, pack.value()),
                strategy,
                KindBindings.all(),
                Companions.Rules.bundled());
        return built(pack, read).underResolution(configured);
    }

    /**
     * The readout's composition facts (#478, S1, S10): what {@link #built} answers for the one read's store half, or the
     * refusal that stopped it. It never throws for a pack that cannot be read: every refusal {@link #composeFacts}
     * raises becomes {@link PackReadout.Unreadable} with its code and message, and a read with no assembly input
     * ({@link Assembly.None}) is {@code pack_unreadable}. A pack known by a name that is not a pack id (#583, D5) has no
     * facts either: its store half refused it ({@link StoreRead#ofNamed}), the reader states that as its one
     * {@code pack_id_invalid} fault, and the facts carry the same refusal, so the pack gets no second problem.
     *
     * @param name the pack's name, as the refusals name it; the id it is composed under when it is a pack id
     * @param read the store half of the one read
     * @return the facts, or why there are none
     */
    static PackReadout.Facts readoutFacts(String name, StoreRead read) {
        if (read.assembly() instanceof Assembly.None) {
            return PackReadout.Unreadable.noAssembly(name);
        }
        if (!PackIdentifier.isPackId(name)) {
            return new PackReadout.Unreadable(RefusalCode.PACK_UNREADABLE, StoreRead.packIdInvalid(name));
        }
        try {
            return built(new PackIdentifier(name), read);
        } catch (CharterRefusal refusal) {
            return new PackReadout.Unreadable(refusal.code(), String.valueOf(refusal.getMessage()));
        }
    }

    /**
     * The one derivation of a pack's composition facts from a read (#478, S1): the descriptor the load assembled and
     * {@link #composeFacts}. {@link #underResolution} and {@link #readoutFacts} both build through it.
     *
     * @param pack the pack, as the refusals name it
     * @param read the read
     * @return the descriptor and the facts
     * @throws CharterRefusal as {@link #composeFacts} refuses the pack
     */
    private static PackReadout.Built built(PackIdentifier pack, StoreRead read) {
        PackCompositionFacts facts = composeFacts(pack, read);
        return new PackReadout.Built(((Assembly.Assembled) read.assembly()).descriptor(), facts);
    }

    /**
     * The composing read's mapping of one read (#554, S4, WP05 review M2): what the compose answers for a pack, from the
     * {@link StoreRead} half of the one reader. A graph document that cannot be read refuses the pack first, then the
     * load's own refusal of the pack, then {@link #heldFacts}. The composing read and {@link #underResolution} share
     * it, so a stored pack and a pack read without the service are answered alike.
     *
     * @param pack the pack, as the refusals name it
     * @param read the read
     * @return the facts
     * @throws CharterRefusal with {@code PACK_UNREADABLE}, or the load's own refusal code, when the pack cannot be read
     */
    static PackCompositionFacts composeFacts(PackIdentifier pack, StoreRead read) {
        if (read.graphsUnreadable()) {
            throw graphUnreadable(pack);
        }
        return switch (read.assembly()) {
            case Assembly.Assembled held -> heldFacts(pack, held);
            case Assembly.Refused refused -> throw new CharterRefusal(refused.code(), refused.message());
            case Assembly.None none -> throw new IllegalStateException("the store's read gave no assembly input");
        };
    }

    /**
     * The facts of a pack the load held, judged as the composing read judges a stored pack: the assembly's first fault
     * ({@link #requireReadable}), then a named policy file that cannot be read ({@link #unreadablePolicy}), then a
     * refusal of the builder (an edge that joins an artifact to itself).
     *
     * @param pack the pack, as the refusals name it
     * @param held what the load held
     * @return the facts
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be read
     */
    static PackCompositionFacts heldFacts(PackIdentifier pack, Assembly.Assembled held) {
        requireReadable(pack, held.violations().subList(0, held.assembled()));
        Optional<String> named = held.descriptor().paths().mutabilityPolicy();
        if (named.isPresent() && held.policyText().isEmpty()) {
            throw unreadablePolicy(pack, named.get(), held.policyFault().orElse("is missing"));
        }
        held.factsRefusal().ifPresent(refusal -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, refusal);
        });
        return held.facts().orElseThrow(() -> graphUnreadable(pack));
    }

    private static CharterRefusal graphUnreadable(PackIdentifier pack) {
        return FilesystemPackStore.graphUnreadable(pack, Optional.empty());
    }

    /**
     * The composition facts of one pack (#554, T027, P7): from the pack's assembled aggregate, the store's read of it,
     * its graph documents and the policy its descriptor names, what the compose needs to know about it. See
     * {@link DomainRepositoryService#compositionFacts(PackIdentifier)} for each fact.
     *
     * @param pack the pack, as the refusals name it
     * @param assembled the pack's aggregate, built by {@link PackAssembly} from {@code read}
     * @param read what the store read of the pack
     * @param graphs the pack's graph documents, the root shards by name then the fragment
     * @param policy the mutability policy the descriptor names, or the empty policy
     * @return the facts
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when a graph edge joins an artifact to itself through a
     *     qualifier that names the pack itself
     */
    static PackCompositionFacts build(
            PackIdentifier pack,
            Pack assembled,
            FilesystemPackStore.PackRead read,
            List<GraphDocument> graphs,
            MutabilityPolicySource policy) {
        Set<String> changed = new LinkedHashSet<>();
        Set<String> references = new LinkedHashSet<>();
        Set<String> declared = new LinkedHashSet<>();
        for (GraphDocument document : graphs) {
            edgeFacts(pack, assembled.descriptor().name(), document, changed, references);
            declaredWithoutFile(document).forEach(declared::add);
        }
        Set<String> defined = new LinkedHashSet<>();
        Map<String, List<String>> languages = new LinkedHashMap<>();
        Map<Urn, List<String>> steps = new LinkedHashMap<>();
        List<String> terms = new ArrayList<>();
        for (CharterArtifact artifact : assembled.artifacts()) {
            artifact.writtenReferences().forEach(reference -> references.add(reference.toString()));
            termsOf(artifact).forEach(terms::add);
            // Held: a kind a graph declares, or an activatable artifact no graph declares (the routing catalog,
            // ADR-0080), which layers, takes the policy and comes into force as any activatable kind does.
            if (artifact.urn().kind().declarableInGraphs() || artifact.kind().activatable()) {
                defined.add(artifact.urn().toString());
                List<String> declaredLanguages = languagesOf(artifact);
                if (!declaredLanguages.isEmpty()) {
                    languages.put(artifact.urn().toString(), declaredLanguages);
                }
                if (artifact instanceof MissionType type) {
                    List<String> declaredSteps = new ArrayList<>();
                    type.steps().forEach(step -> declaredSteps.add(step.id()));
                    type.onDemandSteps().forEach(step -> declaredSteps.add(step.id()));
                    steps.put(artifact.urn(), declaredSteps);
                }
            }
        }
        defined.addAll(declared);
        List<MissionTypeFacts> types = new ArrayList<>();
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            types.add(PackAssembly.missionTypeFacts(stored.file(), stored.dso()));
        }
        List<SubstepDelegationRule.QualifiedCandidate> delegations =
                SubstepDelegationRule.qualifiedCandidates(assembled.descriptor().name(), types);
        references.removeAll(ownStepLinkTargets(graphs, assembled.descriptor().name(), steps));
        return new PackCompositionFacts(
                List.copyOf(changed),
                List.copyOf(defined),
                List.copyOf(references),
                policy,
                assembled.presets(),
                typedEdges(graphs, assembled),
                languages,
                PromptFacts.of(promptSlotFacts(read), PackAssembly.stepEnhancementFacts(read, graphs), steps),
                terms,
                delegations);
    }

    /** The terms a glossary context holds, as bare URNs, in authored order; every other kind holds none. */
    private static Stream<String> termsOf(CharterArtifact artifact) {
        return artifact instanceof GlossaryContext context
                ? context.terms().stream().map(term -> term.urn(context.id()).toString())
                : Stream.empty();
    }

    /**
     * The slot parse of each step prompt of the stored mission types, at the prompt's pack-relative file, over the
     * canonical text the load rule parses ({@code StoreRead.recordPromptSlotFaults}): the input of the prompt facts (research R9).
     */
    private static List<PackContents.PromptSlotFacts> promptSlotFacts(FilesystemPackStore.PackRead read) {
        List<PackContents.PromptSlotFacts> prompts = new ArrayList<>();
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            List<MissionTypeDso.Step> steps = new ArrayList<>(stored.dso().steps());
            steps.addAll(stored.dso().onDemandSteps());
            for (MissionTypeDso.Step step : steps) {
                if (step.promptPath().isPresent() && step.prompt().isPresent()) {
                    prompts.add(new PackContents.PromptSlotFacts(
                            PackAssembly.companionFile(
                                    stored.file(), step.promptPath().get()),
                            PromptSlots.parse(MarkdownCodec.write(step.prompt().get()))));
                }
            }
        }
        return prompts;
    }

    /**
     * The {@code applies_to_languages} an artifact declares: the kinds that carry the field are a tactic, a styleguide,
     * a toolguide, a procedure and an agent profile; every other kind declares none.
     */
    private static List<String> languagesOf(CharterArtifact artifact) {
        return switch (artifact) {
            case Tactic tactic -> tactic.appliesToLanguages();
            case Styleguide styleguide -> styleguide.appliesToLanguages();
            case Toolguide toolguide -> toolguide.appliesToLanguages();
            case Procedure procedure -> procedure.appliesToLanguages();
            case AgentProfile profile -> profile.appliesToLanguages();
            default -> List.of();
        };
    }

    /**
     * Every typed edge the pack writes, as written: the edges of its graph documents in file order, then each of
     * its components' derived relationships in assembly order (a guard's {@code implemented_by}, ADR-0064), then the
     * edges of its registered relationship shard ({@link RelationshipShard#SHARD_PATH}, a root shard the one graph read
     * already holds). Each (from, relation, to, qualifiers) is kept once, where it was first seen: the kernel
     * {@link Relationship#equals} is slot identity (the pair and its class, ADR-0045 decision 1) and ignores the relation
     * within a class and the qualifiers, so the key is explicit. A {@code tension} is a typed edge like any other.
     */
    private static List<Relationship> typedEdges(List<GraphDocument> documents, Pack assembled) {
        Map<EdgeKey, Relationship> edges = new LinkedHashMap<>();
        documents.stream().filter(document -> !registered(document)).forEach(document -> graphEdges(document, edges));
        assembled
                .artifacts()
                .forEach(artifact -> artifact.derivedRelationships().forEach(edge -> keep(edge, edges)));
        documents.stream().filter(CompositionFactsBuilder::registered).forEach(document -> graphEdges(document, edges));
        return List.copyOf(edges.values());
    }

    /** What makes two typed edges the same edge. */
    private record EdgeKey(Urn from, Optional<String> fromPack, RelationType type, Urn to, Optional<String> toPack) {}

    private static void keep(Relationship edge, Map<EdgeKey, Relationship> edges) {
        edges.putIfAbsent(new EdgeKey(edge.from(), edge.fromPack(), edge.type(), edge.to(), edge.toPack()), edge);
    }

    private static boolean registered(GraphDocument document) {
        return RelationshipShard.SHARD_PATH.equals(document.file());
    }

    private static void graphEdges(GraphDocument document, Map<EdgeKey, Relationship> edges) {
        for (GraphDocument.Edge edge : document.edges()) {
            typedEdge(edge).ifPresent(typed -> keep(typed, edges));
        }
    }

    /**
     * One graph-document edge as the kernel's typed edge, with each endpoint's qualifier as written; empty for an
     * endpoint or a relation that is no reference, or a relation that does not allow the endpoint kinds, which the
     * reads that judge the document report.
     */
    static Optional<Relationship> typedEdge(GraphDocument.Edge edge) {
        try {
            PackQualifiedReference source = PackQualifiedReference.parse(edge.source());
            PackQualifiedReference target = PackQualifiedReference.parse(edge.target());
            Optional<RelationType> relation = RelationType.fromToken(edge.relation());
            if (source.asUrn().isEmpty() || target.asUrn().isEmpty() || relation.isEmpty()) {
                return Optional.empty();
            }
            return EndpointRule.bind(
                                    relation.get(),
                                    source.asUrn().get(),
                                    source.packName(),
                                    target.asUrn().get(),
                                    target.packName(),
                                    edge.when(),
                                    edge.reason())
                            instanceof EndpointRule.Bound bound
                    ? Optional.of(bound.relationship())
                    : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * What one graph document's edges tell the compose: the targets of its {@code overrides} relations into
     * {@code changed}, both endpoints of every edge into {@code references}; a self-loop is refused.
     */
    private static void edgeFacts(
            PackIdentifier pack, String packName, GraphDocument document, Set<String> changed, Set<String> references) {
        for (GraphDocument.Edge edge : document.edges()) {
            if (refines(edge.relation())) {
                changed.add(edge.target());
            }
            refuseSelfLoop(pack, packName, document.file(), edge);
            references.add(edge.source());
            references.add(edge.target());
        }
    }

    /**
     * The targets of the pack's own step links, {@code mission_type:T requires action:T/<step>} from a type the pack
     * holds to a step its type file declares (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN): such a target is the
     * pack's own step, whichever pack's graph declares the action node, so the compose does not judge it as a reference
     * that must resolve in the pack.
     */
    private static Set<String> ownStepLinkTargets(
            List<GraphDocument> documents, String packName, Map<Urn, List<String>> steps) {
        Set<String> own = new LinkedHashSet<>();
        for (GraphDocument document : documents) {
            for (GraphDocument.Edge edge : document.edges()) {
                if (!RelationType.REQUIRES.token().equals(edge.relation())) {
                    continue;
                }
                try {
                    PackQualifiedReference source = PackQualifiedReference.parse(edge.source());
                    PackQualifiedReference target = PackQualifiedReference.parse(edge.target());
                    Optional<Urn> type = source.asUrn()
                            .filter(urn ->
                                    source.packName().map(packName::equals).orElse(true))
                            .filter(steps::containsKey);
                    Optional<Urn> step =
                            target.asUrn().filter(urn -> target.packName().isEmpty() && urn.kind() == NodeKind.ACTION);
                    if (type.isPresent()
                            && step.isPresent()
                            && steps.getOrDefault(type.get(), List.of()).stream()
                                    .anyMatch(id ->
                                            step.get().id().equals(type.get().id() + "/" + id))) {
                        own.add(edge.target());
                    }
                } catch (IllegalArgumentException malformed) {
                    // not a reference: the reads that judge the document report it
                }
            }
        }
        return own;
    }

    /**
     * Refuses a pack whose graph edge joins an artifact to itself by a qualifier naming the pack itself, the case the
     * schema and the bare check cannot see (see {@link GraphBinding#joinsItself}); an endpoint that is no reference is
     * left to the reads that judge the document.
     */
    private static void refuseSelfLoop(PackIdentifier pack, String packName, String file, GraphDocument.Edge edge) {
        try {
            if (GraphBinding.joinsItself(
                    packName,
                    PackQualifiedReference.parse(edge.source()),
                    PackQualifiedReference.parse(edge.target()))) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE,
                        "the file " + file + " of " + pack.value() + " cannot be read: an edge joins '" + edge.source()
                                + "' to itself");
            }
        } catch (IllegalArgumentException malformed) {
            // Not a reference: the document is judged where it is read, not here.
        }
    }

    /**
     * The nodes a graph document declares for a kind that is never a file of its own: an {@code anti_pattern} lives
     * only as a node of a graph document, and so does a mission type's step {@code action:<M>/<step>}, a value of its
     * mission type's file that a graph names as a node (A7 Q1, rule W20's {@code steps(M)}).
     */
    private static List<String> declaredWithoutFile(GraphDocument document) {
        Set<NodeKind> withoutFile = EnumSet.of(NodeKind.ANTI_PATTERN, NodeKind.ACTION);
        return switch (document) {
            case GraphDocument.Shard shard ->
                shard.nodes().stream()
                        .filter(node -> withoutFile.stream()
                                .anyMatch(kind -> kind.token().equals(node.kind())))
                        .map(GraphDocument.ShardNode::urn)
                        .toList();
            case GraphDocument.Fragment fragment ->
                fragment.nodes().stream()
                        .flatMap(node -> NodeKind.fromPlural(node.kind())
                                .filter(withoutFile::contains)
                                .map(kind -> kind.token() + ":" + node.id())
                                .stream())
                        .toList();
        };
    }

    /**
     * Whether a relation changes its target: a full replacement of it ({@code overrides}). An {@code enhances} relation
     * is additive and changes nothing, so two sibling enhancers are no conflict (A14, rule C14).
     */
    private static boolean refines(String relation) {
        return RelationType.OVERRIDES.token().equals(relation);
    }

    /**
     * The composing read's mapping of the load's faults (#554, S4): the first, when there is one, refuses the pack
     * {@code pack_unreadable}, naming its file; an edge its relation's endpoint table does not allow names the code
     * {@code relationship_endpoint_not_allowed} before its reason.
     *
     * @param pack the pack, as the refusal names it
     * @param violations the assembly's violations, in the load's order
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when there is a violation
     */
    static void requireReadable(PackIdentifier pack, List<PackElementViolation> violations) {
        if (!violations.isEmpty()) {
            PackElementViolation first = violations.getFirst();
            String reason = PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED.equals(first.kind())
                    ? first.kind().value() + ": " + first.reason()
                    : first.reason();
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "the file " + first.file() + " of " + pack.value() + " cannot be read: " + reason);
        }
    }

    /**
     * The refusal of a pack whose descriptor names a policy file that cannot be read (ADR-0063).
     *
     * @param pack the pack, as the refusal names it
     * @param named the file the descriptor names
     * @param why why the file cannot be read
     * @return the refusal
     */
    static CharterRefusal unreadablePolicy(PackIdentifier pack, String named, String why) {
        return new CharterRefusal(
                RefusalCode.PACK_UNREADABLE, pack.value() + " names mutability_policy " + named + ", which " + why);
    }
}
