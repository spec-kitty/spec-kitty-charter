package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.UnresolvedTemplate;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionTypeSteps;
import ai.speckitty.charter.packs.domain.MutabilityPolicyRepository;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.PackEdge;
import ai.speckitty.charter.packs.domain.PackEdgeRepository;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureRepository;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.StepEnhancementFault;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The edge concretization of the pack persistence ports: one service that realizes {@link TacticRepository},
 * {@link PackDescriptorRepository}, {@link PackCatalogRepository}, {@link PackRepository},
 * {@link MutabilityPolicyRepository}, {@link MissionTypeSteps} and {@link PackCompanionFiles} over the filesystem.
 * The Spring stereotype lives only here; the ports in the domain stay plain interfaces, and a database-backed
 * sibling would carry its own stereotype.
 *
 * <p>It serves every configured pack through an explicit {@link PackIdentifier}. The identifier is resolved to a pack
 * directory through the configured {@link Locations}: a location whose configured name equals the identifier wins,
 * otherwise the location whose {@code pack.yaml} declares that {@code pack_id}. A directory name is therefore never
 * assumed to be the id. An identifier that no location resolves is refused {@code PACK_NOT_FOUND}, as the write side
 * refuses it.
 *
 * <p>The persistence shapes ({@link TacticDso}, {@link PackDescriptorDso}) stay inside this package. Each kind is
 * mapped to and from its domain entity by its {@link KindBinding}, and assembled and committed through the
 * {@link KindBindings} registry (ADR-0037); only domain types leave the service. Templates and asset files, the
 * {@code bytes} content form, are assembled beside them by the same helper ({@link #assembly}) and written byte for
 * byte; they have no repository port of their own.
 */
@Service
public final class DomainRepositoryService
        implements TacticRepository,
                DirectiveRepository,
                StyleguideRepository,
                ToolguideRepository,
                ProcedureRepository,
                ParadigmRepository,
                PackDescriptorRepository,
                PackCatalogRepository,
                PackArtifactRepository,
                PackEdgeRepository,
                PackRepository,
                MutabilityPolicyRepository,
                MissionTypeSteps,
                PackCompanionFiles {

    private final Locations locations;
    private final SerializationStrategy strategy;

    /**
     * A service over the injected format.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     */
    @Autowired
    public DomainRepositoryService(Locations locations, SerializationStrategy strategy) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    // ---- the per-kind repositories, all through the kind registry ----------------------------------------------

    @Override
    public void save(Tactic tactic) {
        saveArtifact(tactic);
    }

    @Override
    public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic) {
        return loadOne(pack, TacticBinding.INSTANCE, tactic.value());
    }

    @Override
    public void save(Directive directive) {
        saveArtifact(directive);
    }

    @Override
    public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive) {
        return loadOne(pack, DirectiveBinding.INSTANCE, directive.value());
    }

    @Override
    public void save(Styleguide styleguide) {
        saveArtifact(styleguide);
    }

    @Override
    public Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier styleguide) {
        return loadOne(pack, StyleguideBinding.INSTANCE, styleguide.value());
    }

    @Override
    public void save(Toolguide toolguide) {
        saveArtifact(toolguide);
    }

    @Override
    public Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier toolguide) {
        return loadOne(pack, ToolguideBinding.INSTANCE, toolguide.value());
    }

    @Override
    public Optional<Procedure> load(PackIdentifier pack, ProcedureIdentifier procedure) {
        return loadOne(pack, ProcedureBinding.INSTANCE, procedure.value());
    }

    @Override
    public void save(Paradigm paradigm) {
        saveArtifact(paradigm);
    }

    @Override
    public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm) {
        return loadOne(pack, ParadigmBinding.INSTANCE, paradigm.value());
    }

    /**
     * Saves an agent profile into its pack, in place when a file of the pack already declares its {@code profile-id}.
     * No port names it yet: profile selection (ADR-0032) adds one to the profiles slice.
     *
     * @param profile the profile to save
     * @throws CharterRefusal when the pack is not stored here or is read-only, or the file cannot be written
     */
    public void save(AgentProfile profile) {
        saveArtifact(profile);
    }

    /**
     * Saves one artifact of any bound kind, as the typed saves do; the whole-pack gate's adapter uses it.
     *
     * @param artifact the artifact to save
     */
    void saveArtifact(CharterArtifact artifact) {
        saveArtifact(artifact.packId(), artifact);
    }

    /**
     * Saves one artifact of any stored kind, a {@link Template} included, into a configured pack: a known id replaces
     * its file, an unknown id creates it at its kind's canonical place. The whole-pack gate's adapter writes through it.
     *
     * @param pack the pack to write into
     * @param artifact the artifact
     * @throws IllegalArgumentException when the artifact is of a kind this store does not persist
     * @throws CharterRefusal when the pack is not configured or is read-only, or the write fails
     */
    void saveArtifact(PackIdentifier pack, CharterArtifact artifact) {
        Resolved resolved = writable(pack);
        writeArtifact(resolved.store, resolved.key, artifact);
    }

    /**
     * Saves one artifact of a kind that is not placed by its id at a given place: a known id replaces its file where it
     * sits, an unknown id is created at {@code place}. The whole-pack gate's adapter writes a step enhancement through
     * it, at the place its file was read from (ADR-0066, A12).
     *
     * @param pack the pack to write into
     * @param artifact the artifact
     * @param place the pack-relative path of a new artifact's file
     * @throws IllegalArgumentException when the artifact is a {@link Template}, is of a kind placed by its id, or is of a
     *     kind this store does not persist
     * @throws CharterRefusal when the pack is not configured or is read-only, the place leaves the pack, is taken or
     *     does not name the artifact's file, or the write fails
     */
    void saveArtifact(PackIdentifier pack, CharterArtifact artifact, String place) {
        if (artifact instanceof Template) {
            throw new IllegalArgumentException("a template is placed by its id, not at a given place");
        }
        KindBinding<?, ?> kind = KindBindings.of(artifact.kind())
                .orElseThrow(() -> new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact"));
        if (placedById(kind)) {
            throw new IllegalArgumentException(
                    "a " + artifact.kind().token() + " is placed by its id, not at a given place");
        }
        Resolved resolved = writable(pack);
        writeAt(resolved.store, resolved.key, kind, artifact, place);
    }

    /** Whether the binding can name the file of an id: a kind whose {@code fileName} refuses is not placed by id. */
    private static boolean placedById(KindBinding<?, ?> kind) {
        try {
            kind.fileName("probe");
            return true;
        } catch (IllegalArgumentException refuses) {
            return false;
        }
    }

    private static <D> void writeAt(
            FilesystemPackStore store,
            PackIdentifier key,
            KindBinding<D, ?> kind,
            CharterArtifact artifact,
            String place) {
        store.writeAt(key, kind, kind.dsoOf(artifact), place);
    }

    /**
     * Writes one asset file of a configured pack, byte for byte, at {@code assets/<path>}.
     *
     * @param pack the pack to write into
     * @param file the asset file
     * @throws CharterRefusal when the pack is not configured or is read-only, or the write fails
     */
    void writeAssetFile(PackIdentifier pack, AssetFile file) {
        Resolved resolved = writable(pack);
        resolved.store.writePayload(
                resolved.key, PackFileSet.ASSETS + file.path(), file.payload(), PackFileKind.ASSET_PAYLOAD);
    }

    /**
     * Writes one activation preset of a configured pack in place, at {@code presets/<id>.yaml} (ADR-0042).
     *
     * @param pack the pack to write into
     * @param preset the preset
     * @throws CharterRefusal when the pack is not configured or is read-only, or the write fails
     */
    void writePreset(PackIdentifier pack, Preset preset) {
        Resolved resolved = writable(pack);
        resolved.store.writePreset(resolved.key, PresetFiles.toDso(preset));
    }

    /**
     * Writes the document of a configured pack in place, at {@code pack.md}, in the Markdown form's canonical text
     * (#260, ADR-0042 decision 8). Like {@link #writePreset}, it is the whole-pack gate's seam: it bypasses the commit
     * store's lock and does not restamp a manifest, so no production write path calls it.
     *
     * @param pack the pack to write into
     * @param document the document
     * @throws CharterRefusal with {@code VALIDATION_FAILED} when a part of the document cannot stand in a Markdown
     *     document; when the pack is not configured or is read-only, or the write fails
     */
    void writePackDocument(PackIdentifier pack, PackDocument document) {
        Resolved resolved = writable(pack);
        resolved.store.writePackDocument(resolved.key, PackDocumentMarkdown.toWritableText(pack, document));
    }

    private <D, E extends CharterArtifact> Optional<E> loadOne(
            PackIdentifier pack, KindBinding<D, E> kind, String elementId) {
        Resolved resolved = resolve(pack);
        return resolved.store.readOne(resolved.key, kind, elementId).flatMap(dso -> entity(pack, kind, dso));
    }

    // ---- PackDescriptorRepository --------------------------------------------------------------------------------

    @Override
    public PackDescriptor get(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return resolved.store.readDescriptor(resolved.key).toDescriptor();
    }

    @Override
    public Optional<PackDescriptor> find(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return resolved.store.findDescriptor(resolved.key).map(PackDescriptorDso::toDescriptor);
    }

    @Override
    public void save(PackIdentifier pack, PackDescriptor descriptor) {
        Resolved resolved = writable(pack);
        resolved.store.writeDescriptor(resolved.key, PackDescriptorDso.of(descriptor));
    }

    // ---- PackCatalogRepository -----------------------------------------------------------------------------------

    /**
     * Every configured location as a configured pack: its configured name as the identifier and its configured level,
     * in the order the locations give them.
     */
    @Override
    public List<ConfiguredPack> configured() {
        return locations.all().stream()
                .map(location -> new ConfiguredPack(new PackIdentifier(location.name()), location.level()))
                .toList();
    }

    // ---- MissionTypeSteps ------------------------------------------------------------------------------------

    /**
     * The step ids a mission type declares: the packs rings hold a mission type as a {@link CharterArtifact}, and this
     * store, which binds the missions domain, reads them off the {@link MissionType} it loaded.
     *
     * @param missionType an artifact of kind {@code mission_type}, as the pack aggregate holds it
     * @return the ids of its steps and its on-demand steps; empty for any other artifact
     */
    @Override
    public Set<String> stepIds(CharterArtifact missionType) {
        if (!(missionType instanceof MissionType type)) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        type.steps().stream().map(MissionStep::id).forEach(ids::add);
        type.onDemandSteps().stream().map(MissionStep::id).forEach(ids::add);
        return Set.copyOf(ids);
    }

    // ---- PackCompanionFiles ----------------------------------------------------------------------------------

    /**
     * The stored bytes of each step's prompt and guidelines files of a mission type this store loaded: each read at the
     * path the step names, beside the type's file ({@code missions/<id>/steps/<step>.prompt.md}, S8), through the same
     * containment rule as every payload the store reads, and never parsed. Call it inside the verified read that loaded
     * the type, so the bytes are the ones the type was parsed from.
     *
     * @param pack the pack that holds the mission type
     * @param missionType an artifact of kind {@code mission_type}, as the pack aggregate holds it
     * @return each step's files by its id, its steps then its on-demand steps; empty for any other artifact
     * @throws CharterRefusal {@code PACK_UNREADABLE} when a file the type names cannot be read now, or
     *     {@code PACK_NOT_FOUND} when no configured pack has this identifier
     */
    @Override
    public Map<String, PackCompanionFiles.StepFiles> stepFiles(PackIdentifier pack, CharterArtifact missionType) {
        if (!(missionType instanceof MissionType type)) {
            return Map.of();
        }
        Path directory = resolve(pack).directory;
        String typeDirectory = "missions/" + type.localId() + "/";
        Map<String, PackCompanionFiles.StepFiles> files = new LinkedHashMap<>();
        List<MissionStep> steps = new ArrayList<>(type.steps());
        steps.addAll(type.onDemandSteps());
        for (MissionStep step : steps) {
            files.put(
                    step.id(),
                    new PackCompanionFiles.StepFiles(
                            step.prompt()
                                    .map(document -> stored(
                                            pack,
                                            directory,
                                            typeDirectory + MissionTypeBinding.promptPathOf(step.id()))),
                            step.guidelines()
                                    .map(document -> stored(
                                            pack,
                                            directory,
                                            typeDirectory + MissionTypeBinding.guidelinesPathOf(step.id())))));
        }
        return Collections.unmodifiableMap(files);
    }

    /** The bytes of a file the loaded pack holds; one that cannot be read now is unreadable, never an absent file. */
    private static Payload stored(PackIdentifier pack, Path directory, String file) {
        StringBuilder why = new StringBuilder();
        return FilesystemPackStore.readPayload(directory, file, why::append)
                .map(Payload::of)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + ": " + file + ": " + why));
    }

    // ---- PackArtifactRepository -------------------------------------------------------------------------------

    /**
     * The edges of the pack's graph documents, in {@link FilesystemPackStore#readGraphs} order: the root shards by
     * file name, then the fragment, each in document order (ADR-0064). An {@code in_tension_with} edge is a tension,
     * not a relationship, and is left out.
     *
     * @param pack the pack
     * @return the edges
     * @throws CharterRefusal {@code PACK_UNREADABLE} when a graph document cannot be read or an edge names an unknown
     *     relation
     */
    @Override
    public List<PackEdge> edges(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        FilesystemPackStore.GraphRead graphs = resolved.store.readGraphs(resolved.key);
        if (!graphs.violations().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "a graph document of " + pack.value() + " cannot be read");
        }
        List<PackEdge> edges = new ArrayList<>();
        for (GraphDocument document : graphs.documents()) {
            for (GraphDocument.Edge edge : document.edges()) {
                if (PackPolicy.TENSION.equals(edge.relation())) {
                    // A tension is read as a tension, never answered as a relationship (Relation.yaml).
                    continue;
                }
                RelationType relation = RelationType.fromToken(edge.relation())
                        .orElseThrow(() -> new CharterRefusal(
                                RefusalCode.PACK_UNREADABLE,
                                document.file() + " of " + pack.value() + ": unknown relation '" + edge.relation()
                                        + "'"));
                edges.add(new PackEdge(edge.source(), edge.target(), relation));
            }
        }
        return List.copyOf(edges);
    }

    /**
     * What the compose needs to know about the pack, from one assembly of it and one read of its graph documents:
     *
     * <ul>
     *   <li>the artifacts its graph documents say it changes: the distinct targets of its {@code enhances} and
     *       {@code overrides} relations, in first-seen order;
     *   <li>the artifacts it holds, as bare URNs, of every kind a reference can name, in the order the pack lists them:
     *       the files it holds, and the nodes its graph documents declare for a kind that is never a file (an
     *       {@code anti_pattern}, a re-kinded node of another kind's graph). It is the one answer to "does this pack
     *       hold this artifact";
     *   <li>the references it writes, as written, {@code [<pack-name>::]<kind>:<id>}: both endpoints of every edge of its
     *       graph documents, then what each of its artifacts writes ({@link CharterArtifact#writtenReferences()}, which
     *       is the targets of the relationships the walk follows, so the check and the walk cannot disagree, and the
     *       references a kind keeps without minting an edge);
     *   <li>the mutability policy its descriptor names by {@code mutability_policy} (ADR-0063), or the empty policy;
     *   <li>the activation presets it holds, from the same assembly; a preset the domain refuses is a violation, which
     *       makes the pack unreadable here as any other file does;
     *   <li>the typed edges it writes, as written, from the same graph read and assembly ({@link #typedEdges});
     *   <li>the {@code applies_to_languages} of each artifact it holds that declares any, by bare URN, from the same
     *       assembly ({@link #languagesOf}).
     * </ul>
     *
     * <p>The answer fails closed: a graph document or a file of the pack that cannot be read refuses it
     * {@code pack_unreadable}, naming the first such file, and so does a named policy file that is missing or invalid
     * ({@link #policyOf(PackIdentifier)}), since what an unreadable file holds or writes cannot be
     * judged and a compose that skipped it would pass what it never saw. The whole-pack load and the pack-scoped reads
     * still answer for such a pack, so its author can inspect it.
     */
    @Override
    public PackCompositionFacts compositionFacts(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        FilesystemPackStore.GraphRead graphs = resolved.store.readGraphs(resolved.key);
        if (!graphs.violations().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "a graph document of " + pack.value() + " cannot be read");
        }
        Pack assembled = readable(pack);
        MutabilityPolicySource policy = policyOf(resolved, pack, assembled.descriptor());
        Set<String> changed = new LinkedHashSet<>();
        Set<String> references = new LinkedHashSet<>();
        Set<String> declared = new LinkedHashSet<>();
        for (GraphDocument document : graphs.documents()) {
            edgeFacts(pack, assembled.descriptor().name(), document, changed, references);
            declaredWithoutFile(document).forEach(declared::add);
        }
        Set<String> defined = new LinkedHashSet<>();
        Map<String, List<String>> languages = new LinkedHashMap<>();
        Map<String, String> bindings = new LinkedHashMap<>();
        for (CharterArtifact artifact : assembled.artifacts()) {
            artifact.writtenReferences().forEach(reference -> references.add(reference.toString()));
            if (artifact.urn().kind().authoredInGraphs()) {
                defined.add(artifact.urn().toString());
                List<String> declaredLanguages = languagesOf(artifact);
                if (!declaredLanguages.isEmpty()) {
                    languages.put(artifact.urn().toString(), declaredLanguages);
                }
                missionTypeOf(artifact)
                        .ifPresent(missionType -> bindings.put(artifact.urn().toString(), missionType.toString()));
            }
        }
        defined.addAll(declared);
        return new PackCompositionFacts(
                List.copyOf(changed),
                List.copyOf(defined),
                List.copyOf(references),
                policy,
                assembled.presets(),
                typedEdges(graphs.documents(), assembled),
                languages,
                bindings);
    }

    /**
     * The mission type an artifact is bound to (A7 Q1): a mission step contract is bound to the mission its
     * {@code mission} names; every other kind is bound to none.
     */
    private static Optional<Urn> missionTypeOf(CharterArtifact artifact) {
        return artifact instanceof MissionStepContract contract
                ? Optional.of(Urn.missionType(contract.mission()))
                : Optional.empty();
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
     * {@link Relationship#equals} ignores the relation and the qualifiers, so the key is explicit.
     */
    private static List<Relationship> typedEdges(List<GraphDocument> documents, Pack assembled) {
        Map<EdgeKey, Relationship> edges = new LinkedHashMap<>();
        documents.stream().filter(document -> !registered(document)).forEach(document -> graphEdges(document, edges));
        assembled
                .artifacts()
                .forEach(artifact -> artifact.derivedRelationships().forEach(edge -> keep(edge, edges)));
        documents.stream().filter(DomainRepositoryService::registered).forEach(document -> graphEdges(document, edges));
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
     * endpoint or a relation that is no reference, which the reads that judge the document report.
     */
    static Optional<Relationship> typedEdge(GraphDocument.Edge edge) {
        try {
            PackQualifiedReference source = PackQualifiedReference.parse(edge.source());
            PackQualifiedReference target = PackQualifiedReference.parse(edge.target());
            Optional<RelationType> relation = RelationType.fromToken(edge.relation());
            if (source.asUrn().isEmpty() || target.asUrn().isEmpty() || relation.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Relationship(
                    source.asUrn().get(),
                    source.packName(),
                    target.asUrn().get(),
                    target.packName(),
                    relation.get(),
                    edge.when(),
                    edge.reason()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * The policy the pack's descriptor names (ADR-0063), read through the same resolution as the compose's facts, so
     * the offering and every closure judge the file. A pack no pack is configured as is refused {@code pack_not_found};
     * a pack is refused {@code pack_unreadable} when its descriptor cannot be read, or names a policy file that is
     * missing or invalid.
     */
    @Override
    public MutabilityPolicySource policyOf(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return policyOf(
                resolved, pack, resolved.store.readDescriptor(resolved.key).toDescriptor());
    }

    /**
     * The policy file {@code descriptor} names, read from the resolved pack's directory; the empty policy when it names
     * none. A named file that is no regular file, is not UTF-8 text, is not YAML or breaks the {@code policy} schema
     * makes the pack unreadable: a lock that cannot be read is never taken as open.
     */
    private static MutabilityPolicySource policyOf(Resolved resolved, PackIdentifier pack, PackDescriptor descriptor) {
        Optional<String> named = descriptor.paths().mutabilityPolicy();
        if (named.isEmpty()) {
            return MutabilityPolicySource.empty();
        }
        try {
            return PolicyYaml.readNamed(resolved.directory, named.get()).source();
        } catch (PolicyYaml.Unreadable invalid) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    pack.value() + " names mutability_policy " + named.get() + ", which " + invalid.getMessage());
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
     * Refuses a pack whose graph edge joins an artifact to itself by a qualifier naming the pack itself, the case the
     * schema and the bare check cannot see (see {@link PackPolicy#joinsItself}); an endpoint that is no reference is
     * left to the reads that judge the document.
     */
    private static void refuseSelfLoop(PackIdentifier pack, String packName, String file, GraphDocument.Edge edge) {
        try {
            if (PackPolicy.joinsItself(
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

    /** The pack's aggregate, refused {@code pack_unreadable} when any file of it could not be read. */
    private Pack readable(PackIdentifier pack) {
        Pack assembled = assemble(pack);
        if (!assembled.violations().isEmpty()) {
            PackElementViolation first = assembled.violations().getFirst();
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "the file " + first.file() + " of " + pack.value() + " cannot be read: " + first.reason());
        }
        return assembled;
    }

    /**
     * Whether a relation changes its target: a full replacement of it ({@code overrides}). An {@code enhances} relation
     * is additive and changes nothing, so two sibling enhancers are no conflict (A14, rule C14).
     */
    private static boolean refines(String relation) {
        return RelationType.OVERRIDES.token().equals(relation);
    }

    // ---- PackRepository --------------------------------------------------------------------

    @Override
    public Pack load(PackIdentifier pack) {
        return assemble(pack);
    }

    @Override
    public List<Tactic> tacticsOf(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return resolved.store.readAll(resolved.key, TacticBinding.INSTANCE).stream()
                .flatMap(dso -> entity(pack, TacticBinding.INSTANCE, dso).stream())
                .toList();
    }

    /**
     * Writes one artefact into {@code store} under {@code key}, through its kind's binding: a known id replaces its
     * file, an unknown id creates {@code <id>.<kind>.yaml}. The {@link PackCommitStore} calls it to stage the one
     * artefact a registration added or replaced before the atomic directory swap, leaving the descriptor and every
     * sibling byte-identical; the mapping stays in the binding so the commit path and the per-child saves cannot drift.
     * A {@link Template} is written byte for byte at {@code templates/<id>}, refused when another rule classifies that
     * path (a {@code templates/x.tactic.yaml}): one file, one owner.
     *
     * @param store the store to write through (for a commit, one rooted at the staging directory's parent)
     * @param key the directory-name key the store addresses the pack by
     * @param artifact the artefact to write
     * @throws IllegalArgumentException when {@code artifact} is of a kind this store does not persist
     */
    static void writeArtifact(FilesystemPackStore store, PackIdentifier key, CharterArtifact artifact) {
        if (artifact instanceof Template template) {
            store.writePayload(
                    key,
                    TemplateBinding.INSTANCE.pathOf(template),
                    TemplateBinding.INSTANCE.payloadOf(template),
                    PackFileKind.TEMPLATE);
            return;
        }
        KindBinding<?, ?> kind = KindBindings.of(artifact.kind())
                .orElseThrow(() -> new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact"));
        write(store, key, kind, artifact);
    }

    private static <D> void write(
            FilesystemPackStore store, PackIdentifier key, KindBinding<D, ?> kind, CharterArtifact artifact) {
        store.write(key, kind, kind.dsoOf(artifact));
    }

    private Pack assemble(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        // No read serves the warnings in phase 1: the list is filled and dropped.
        return assembly(
                        pack,
                        resolved.store.read(resolved.key),
                        new HashMap<>(),
                        resolved.store.readGraphs(resolved.key),
                        new ArrayList<>())
                .build();
    }

    /**
     * The one assembly of a read into the aggregate, shared by {@link #load(PackIdentifier)} and
     * {@link WholePackStore#load}: the descriptor, the read violations, every bound kind (through {@link #collect}),
     * the asset files, the templates and the presets. Each added artifact's file is put in {@code places} under its
     * URN; a preset is no artifact and has none, its place being {@code presets/<id>.yaml} by the stem rule. The
     * templates are built first, so that a mission type whose output template names none of them is refused (#277).
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(PackIdentifier pack, FilesystemPackStore.PackRead read, Map<Urn, String> places) {
        Pack.Builder builder =
                Pack.builder().id(pack).descriptor(read.descriptor().toDescriptor());
        read.violations().forEach(builder::violation);
        Map<String, Template> templates = new LinkedHashMap<>();
        List<PackElementViolation> templateViolations = new ArrayList<>();
        for (FilesystemPackStore.TemplateFile file : read.templates()) {
            String place = file.place();
            try {
                templates.put(place, TemplateBinding.INSTANCE.toEntityAt(pack, place, file.content()));
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                templateViolations.add(new PackElementViolation(place, "domain", String.valueOf(breach.getMessage())));
            }
        }
        Set<String> namable = MissionTemplates.namable(templates.values());
        GuardClosure closure = GuardClosure.of(pack, read);
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            collect(pack, kind, read, builder, places, namable, closure);
        }
        read.assetFiles().forEach(builder::assetFile);
        templates.forEach((place, template) -> {
            builder.artifact(template);
            places.put(template.urn(), place);
        });
        templateViolations.forEach(builder::violation);
        for (FilesystemPackStore.Stored<PresetDso> stored : read.presets()) {
            try {
                builder.preset(PresetFiles.toPreset(stored.dso()));
            } catch (ConstraintBreachException breach) {
                builder.violation(
                        new PackElementViolation(stored.file(), "domain", String.valueOf(breach.getMessage())));
            }
        }
        // #260: the document's parts are the read model's; one the domain refuses is a violation, never a failed load.
        read.document().ifPresent(stored -> {
            try {
                builder.document(PackDocumentMarkdown.toDomain(stored.dso()));
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                builder.violation(
                        new PackElementViolation(stored.file(), "domain", String.valueOf(breach.getMessage())));
            }
        });
        return builder;
    }

    /**
     * The assembly of {@link #assembly(PackIdentifier, FilesystemPackStore.PackRead, Map)} given the pack's graph
     * documents too: the one seat of the step enhancement and fragment faults (FR-004 to FR-006), reached by the
     * whole-pack load, and through it the registration, and by every composing read ({@code assemble}, so {@code readable}).
     * Nothing is thrown for such a fault. Before the existing assembly it
     *
     * <ol>
     *   <li>judges the facts of the read's step enhancements, their companions and the graph edges (no orphan set: the
     *       read lists no unnamed file; the edge rules are not judged over a graph read that has a violation, which is
     *       refused on its own);
     *   <li>hands the existing assembly a judged copy of the read: each {@code companion} violation of a
     *       step enhancement file kept under the kind {@code prompt_fragment_invalid}, the store hard-coding the
     *       generic kind, and each file whose id repeats (the second in path order) left out of the held enhancements,
     *       so that {@code Pack.guardKind}'s same-id rule never breaches at {@code build()} (operator ruling A2);
     * </ol>
     *
     * and last appends each rule fault as a violation at its file, after the violations of the store, of every bound
     * kind, the templates, the presets and the pack document, and after the slot faults of the step prompts (FR-007,
     * IC-03): the order of {@code readable}'s first violation. A slot fault the descriptor's version only warns of is
     * put on {@code warnings}, not on the violations.
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param graphs what the store read of the pack's graph documents
     * @param warnings receives the load findings that do not refuse the pack (A4, A8), filled as {@code places} is
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            FilesystemPackStore.GraphRead graphs,
            List<PackFinding> warnings) {
        List<StepEnhancementFault.Finding> faults = PackPolicy.stepEnhancementFaults(
                stepEnhancementFacts(read, graphs), graphs.violations().isEmpty());
        Pack.Builder builder = assembly(pack, judged(read, faults), places);
        recordPromptSlotFaults(read, builder, warnings);
        faults.stream().map(PackPolicy::violationOf).forEach(builder::violation);
        return builder;
    }

    /**
     * Judges the slots of every step prompt of the stored mission types (FR-007) and records each fault at the prompt's
     * pack-relative file: a blocking fault as a violation, a warning on {@code warnings}, never on the violations
     * (A4). The text parsed is the canonical text of the prompt document the store read into the mission type
     * ({@code MissionTypeDso.Step#prompt}), frontmatter included (A5, A11): the same text validate mode parses. A step
     * with no prompt has nothing to judge.
     */
    private static void recordPromptSlotFaults(
            FilesystemPackStore.PackRead read, Pack.Builder builder, List<PackFinding> warnings) {
        Optional<String> version = read.descriptor().toDescriptor().schemaVersion();
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            List<MissionTypeDso.Step> steps = new ArrayList<>(stored.dso().steps());
            steps.addAll(stored.dso().onDemandSteps());
            for (MissionTypeDso.Step step : steps) {
                if (step.promptPath().isEmpty() || step.prompt().isEmpty()) {
                    continue;
                }
                String file = companionFile(stored.file(), step.promptPath().get());
                PromptSlots.Parse parse =
                        PromptSlots.parse(MarkdownCodec.write(step.prompt().get()));
                for (PackPolicy.PromptSlotFault fault : PackPolicy.promptSlotFaults(parse, version)) {
                    if (fault.code().severity() == PackFindingCode.Severity.BLOCKING) {
                        builder.violation(PackPolicy.violationOf(file, fault));
                    } else {
                        warnings.add(PackPolicy.findingOf(file, fault));
                    }
                }
            }
        }
    }

    /** The pack-relative file of a companion path, which is relative to the file of the artifact that names it. */
    private static String companionFile(String artifactFile, String companionPath) {
        Path file = Path.of(artifactFile).resolveSibling(companionPath).normalize();
        return file.toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static String enhancementSuffix() {
        return "." + StepEnhancementBinding.INSTANCE.fileToken() + ".yaml";
    }

    /** The facts of the held step enhancements, their companions' canonical texts and the pack's edges. */
    private static StepEnhancementFacts stepEnhancementFacts(
            FilesystemPackStore.PackRead read, FilesystemPackStore.GraphRead graphs) {
        List<StepEnhancementFacts.Enhancement> enhancements = new ArrayList<>();
        List<StepEnhancementFacts.Companion> companions = new ArrayList<>();
        for (FilesystemPackStore.Stored<StepEnhancementDso> stored : read.elements(StepEnhancementBinding.INSTANCE)) {
            List<StepEnhancementFacts.Entry> entries = stored.dso().promptFragments().stream()
                    .map(entry -> new StepEnhancementFacts.Entry(entry.slot(), entry.path()))
                    .toList();
            StepEnhancementFacts.Enhancement enhancement = new StepEnhancementFacts.Enhancement(
                    stored.file(),
                    FileSystemPackInspector.typeDirectory(stored.file()),
                    stored.dso().id(),
                    entries);
            enhancements.add(enhancement);
            for (int i = 0; i < entries.size(); i++) {
                String companion = enhancement.companionPath(entries.get(i));
                stored.dso()
                        .promptFragments()
                        .get(i)
                        .body()
                        .ifPresent(body -> companions.add(FileSystemPackInspector.companionFact(companion, body)));
            }
        }
        List<StepEnhancementFacts.Edge> edges = new ArrayList<>();
        for (GraphDocument document : graphs.documents()) {
            for (GraphDocument.Edge edge : document.edges()) {
                FileSystemPackInspector.stepEnhancementEdge(
                                edge.relation(),
                                edge.source(),
                                edge.target(),
                                read.descriptor().name())
                        .ifPresent(edges::add);
            }
        }
        // Orphans are judged at validate and registration only: no fragment file list is passed at load.
        return new StepEnhancementFacts(enhancements, companions, Set.of(), Set.of(), edges);
    }

    /** The read the existing assembly gets: the remapped companion violations, the repeated ids left out. */
    private static FilesystemPackStore.PackRead judged(
            FilesystemPackStore.PackRead read, List<StepEnhancementFault.Finding> faults) {
        Set<String> withheld = new LinkedHashSet<>();
        for (StepEnhancementFault.Finding fault : faults) {
            if (fault.rule() == StepEnhancementFault.STEM_REPEATED) {
                withheld.add(fault.path());
            }
        }
        String kind = PackPolicy.violationKind(PackFindingCode.PROMPT_FRAGMENT_INVALID);
        List<PackElementViolation> violations = read.violations().stream()
                .map(violation -> FilesystemPackStore.COMPANION_KIND.equals(violation.kind())
                                && violation.file().endsWith(enhancementSuffix())
                        ? new PackElementViolation(violation.file(), kind, violation.reason())
                        : violation)
                .toList();
        Map<ArtifactKind, List<FilesystemPackStore.Stored<?>>> elements = new EnumMap<>(ArtifactKind.class);
        elements.putAll(read.elements());
        elements.computeIfPresent(
                ArtifactKind.STEP_ENHANCEMENT,
                (key, held) -> held.stream()
                        .filter(stored -> !withheld.contains(stored.file()))
                        .toList());
        return new FilesystemPackStore.PackRead(
                read.descriptor(),
                elements,
                violations,
                read.assetFiles(),
                read.templates(),
                read.presets(),
                read.document());
    }

    /**
     * Adds each readable element of one kind to the aggregate, failing closed: a domain-invalid element is recorded as
     * a violation, named by its id, rather than aborting assembly. A mission type whose output template names no
     * template of the pack (#277) is left out with a {@code reference} violation of its file, as the read's own check of
     * a named file words it; so is a guard or a lifecycle that does not close over its references (ADR-0054,
     * {@link GuardClosure}). Each added element's file is put in {@code places} under its URN.
     */
    static <D, E extends CharterArtifact> void collect(
            PackIdentifier pack,
            KindBinding<D, E> kind,
            FilesystemPackStore.PackRead read,
            Pack.Builder builder,
            Map<Urn, String> places,
            Set<String> namableTemplates,
            GuardClosure closure) {
        for (FilesystemPackStore.Stored<D> stored : read.elements(kind)) {
            try {
                E entity = kind.toEntity(pack, stored.dso());
                Optional<UnresolvedTemplate> unresolved = MissionTemplates.firstUnresolved(entity, namableTemplates);
                if (unresolved.isPresent()) {
                    builder.violation(new PackElementViolation(
                            stored.file(), "reference", MissionTemplates.reason(unresolved.get())));
                    continue;
                }
                Optional<String> unclosed = closure.firstUnresolved(entity);
                if (unclosed.isPresent()) {
                    builder.violation(new PackElementViolation(stored.file(), "reference", unclosed.get()));
                    continue;
                }
                Urn urn = entity.urn();
                builder.artifact(entity);
                places.put(urn, stored.file());
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                builder.violation(new PackElementViolation(
                        kind.idOf(stored.dso()), "domain", String.valueOf(breach.getMessage())));
            }
        }
    }

    // ---- identifier to directory ---------------------------------------------------------------------------------

    /** A pack's store, the directory-name key the store addresses it by, and the pack's directory. */
    private record Resolved(FilesystemPackStore store, PackIdentifier key, Path directory) {}

    /**
     * The real path of a pack's live directory, refused with {@code PACK_UNREADABLE} when it is not a directory, cannot
     * be resolved, or its {@link Locations} keeps it read-only. This is the one write gate both the per-file writes
     * ({@link #writable}) and {@link PackCommitStore#commit} apply before anything is written, so a pack linked from
     * outside the configuration root is read-only through every write path (ADR-0038 as amended 2026-10-31, #287). The
     * real path is captured here and reused by the caller for the write itself, so a link retargeted after the check
     * cannot redirect the write.
     */
    static Path writableRealDirectory(Locations locations, Path directory, PackIdentifier pack) {
        if (!Files.isDirectory(directory)) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " is not a directory");
        }
        Path real;
        try {
            real = directory.toRealPath();
        } catch (IOException unresolved) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " cannot be resolved", unresolved);
        }
        locations.whyReadOnly(real).ifPresent(why -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " " + why);
        });
        return real;
    }

    /**
     * Resolves a pack to write into over the real path of its directory, refusing one its {@link Locations} keeps
     * read-only before anything is written (#287). The store is rooted at the real parent and keyed by the real name,
     * not by re-resolving the configured link at write time, so an in-place write lands in the link's target and a
     * link retargeted after the read-only check cannot redirect it (as {@link PackCommitStore} reuses its live
     * directory).
     */
    private Resolved writable(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        Path real = writableRealDirectory(locations, resolved.directory, pack);
        Path realParent = real.getParent();
        if (realParent == null || real.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " has no parent directory");
        }
        return new Resolved(
                new FilesystemPackStore(realParent, strategy),
                new PackIdentifier(real.getFileName().toString()),
                real);
    }

    /** The pack's store and directory, at the location {@link PackLocator} resolves. */
    private Resolved resolve(PackIdentifier pack) {
        return over(PackLocator.locate(locations, strategy, pack));
    }

    /** The store rooted at the pack directory's parent, addressed by the directory's own name. */
    private Resolved over(PackLocation location) {
        Path directory = PackLocator.directoryOf(location);
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack location has no parent directory");
        }
        return new Resolved(
                new FilesystemPackStore(parent, strategy),
                new PackIdentifier(directory.getFileName().toString()),
                directory);
    }

    // ---- DSO to entity -------------------------------------------------------------------------------------------

    /** The entity of a readable element, or empty when it breaks a domain invariant. */
    private static <D, E extends CharterArtifact> Optional<E> entity(
            PackIdentifier pack, KindBinding<D, E> kind, D dso) {
        try {
            return Optional.of(kind.toEntity(pack, dso));
        } catch (ConstraintBreachException | IllegalArgumentException breach) {
            return Optional.empty();
        }
    }
}
