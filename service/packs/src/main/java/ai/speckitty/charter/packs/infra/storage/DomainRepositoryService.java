package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.MutabilityPolicyRepository;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.PackDocumentRepository;
import ai.speckitty.charter.packs.domain.PackDocumentText;
import ai.speckitty.charter.packs.domain.PackEdge;
import ai.speckitty.charter.packs.domain.PackEdgeRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureRepository;
import ai.speckitty.charter.packs.domain.PromptFacts;
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
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The edge concretization of the pack persistence ports: one service that realizes {@link TacticRepository},
 * {@link PackDescriptorRepository}, {@link PackCatalogRepository}, {@link PackRepository},
 * {@link MutabilityPolicyRepository}, {@link PackCompanionFiles} and {@link PackDocumentRepository} over the filesystem.
 * It is a plain class, framework-free: {@code StorageConfiguration} registers it as the one bean realizing the pack
 * ports, and the production round trip ({@link PackRoundTrip}) builds it directly, so pack-check, which runs the round
 * trip without Spring, reaches no Spring type through it (ADR-0075 as amended 2026-11-13, #478). The ports in the
 * domain stay plain interfaces, and a database-backed sibling would be registered in its place.
 *
 * <p>It serves every configured pack through an explicit {@link PackIdentifier}. The identifier is resolved to a pack
 * directory through the configured {@link Locations}: the location configured as that registered id ({@link
 * PackLocator}, #583). The {@code pack_id} a {@code pack.yaml} declares is never an address. An identifier that no
 * location is configured as is refused {@code PACK_NOT_FOUND}, as the write side refuses it.
 *
 * <p>The persistence shapes ({@link TacticDso}, {@link PackDescriptorDso}) stay inside this package. Each kind is
 * mapped to and from its domain entity by its {@link KindBinding}, and assembled and committed through the
 * {@link KindBindings} registry (ADR-0037); only domain types leave the service. Templates and asset files, the
 * {@code bytes} content form, are assembled beside them by the same Spring-free {@link PackAssembly} and written byte
 * for byte; they have no repository port of their own.
 */
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
                PackCompanionFiles,
                PackDocumentRepository {

    private final Locations locations;
    private final SerializationStrategy strategy;

    /**
     * A service over the injected format.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     */
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
            PackDirectory key,
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
     * Writes the document of a configured pack in place, at {@code README.md}, in the Markdown form's canonical text
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

    /**
     * Writes the mutability policy of a configured pack in place, at {@code policy.yaml} (ADR-0063, #476), as
     * {@link PolicyYaml#write} words it. Like {@link #writePreset}, it is the whole-pack gate's seam: it bypasses the
     * commit store's lock and does not restamp a manifest, so no production write path calls it. The gate therefore
     * attests {@link PolicyYaml#write}, the model writer; the store's own copy of {@code policy.yaml}
     * ({@code WholePackStore}) copies the file as it was read, and {@code PolicyRoundTripTest} pins that copy.
     *
     * @param pack the pack to write into
     * @param policy the policy
     * @throws CharterRefusal when the pack is not configured or is read-only, or the write fails
     */
    void writePolicy(PackIdentifier pack, MutabilityPolicySource policy) {
        Resolved resolved = writable(pack);
        resolved.store.writePolicy(resolved.key, PolicyYaml.write(policy));
    }

    private <D, E extends CharterArtifact> Optional<E> loadOne(
            PackIdentifier pack, KindBinding<D, E> kind, String elementId) {
        return reading(
                pack,
                resolved ->
                        resolved.store.readOne(resolved.key, kind, elementId).flatMap(dso -> entity(pack, kind, dso)));
    }

    // ---- PackDescriptorRepository --------------------------------------------------------------------------------

    @Override
    public PackDescriptor get(PackIdentifier pack) {
        return reading(
                pack, resolved -> resolved.store.readDescriptor(resolved.key).toDescriptor());
    }

    @Override
    public Optional<PackDescriptor> find(PackIdentifier pack) {
        return reading(
                pack, resolved -> resolved.store.findDescriptor(resolved.key).map(PackDescriptorDso::toDescriptor));
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
                .map(location -> new ConfiguredPack(location.id(), location.level()))
                .toList();
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
        return reading(pack, resolved -> stepFiles(pack, type, resolved.directory));
    }

    private static Map<String, PackCompanionFiles.StepFiles> stepFiles(
            PackIdentifier pack, MissionType type, Path directory) {
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
     * file name, then the fragment, each in document order (ADR-0064). A {@code tension} edge is a relationship
     * like any other (ADR-0045 decision 1).
     *
     * @param pack the pack
     * @return the edges
     * @throws CharterRefusal {@code PACK_UNREADABLE} when a graph document cannot be read or an edge names an unknown
     *     relation
     */
    @Override
    public List<PackEdge> edges(PackIdentifier pack) {
        return reading(pack, resolved -> edges(pack, resolved));
    }

    private static List<PackEdge> edges(PackIdentifier pack, Resolved resolved) {
        FilesystemPackStore.GraphRead graphs = resolved.store.readGraphs(resolved.key);
        if (!graphs.violations().isEmpty()) {
            throw graphs.unreadable(pack);
        }
        List<PackEdge> edges = new ArrayList<>();
        for (GraphDocument document : graphs.documents()) {
            for (GraphDocument.Edge edge : document.edges()) {
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
     *   <li>the typed edges it writes, as written, from the same graph read and assembly ({@code CompositionFactsBuilder.typedEdges});
     *   <li>the {@code applies_to_languages} of each artifact it holds that declares any, by bare URN, from the same
     *       assembly ({@code CompositionFactsBuilder.languagesOf});
     *   <li>the slots each step prompt declares and the fills each step enhancement writes, from the same read, over
     *       the canonical texts the load rule judges ({@link PromptFacts#of}, research R9), and the steps each mission
     *       type it holds declares, from the same assembly, which place a step's prompt in force (operator ruling Q3);
     *   <li>the terms its glossary contexts hold, as bare URNs, from the same assembly: items of their contexts, so not
     *       among the artifacts it holds (ADR-0049 decision 3).
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
        return reading(pack, resolved -> compositionFacts(pack, resolved));
    }

    private PackCompositionFacts compositionFacts(PackIdentifier pack, Resolved resolved) {
        // The StoreRead half of the one reader, over one walk (#554, T028; operator decision 2026-11-17): the composing
        // read runs the same DSO read registration's readTree runs, and none of the reader's prechecks (S4).
        StoreRead read = StoreRead.of(
                resolved.directory,
                pack,
                PackFileSet.walk(resolved.directory, pack.value()),
                strategy,
                KindBindings.all(),
                Companions.Rules.bundled());
        return CompositionFactsBuilder.composeFacts(pack, read);
    }

    /**
     * The policy the pack's descriptor names (ADR-0063), read through the same resolution as the compose's facts, so
     * the offering and every closure judge the file. A pack no pack is configured as is refused {@code pack_not_found};
     * a pack is refused {@code pack_unreadable} when its descriptor cannot be read, or names a policy file that is
     * missing or invalid.
     */
    @Override
    public MutabilityPolicySource policyOf(PackIdentifier pack) {
        return reading(
                pack,
                resolved -> policyOf(
                        resolved,
                        pack,
                        resolved.store.readDescriptor(resolved.key).toDescriptor()));
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
            throw CompositionFactsBuilder.unreadablePolicy(pack, named.get(), String.valueOf(invalid.getMessage()));
        }
    }

    // ---- PackRepository --------------------------------------------------------------------

    @Override
    public Pack load(PackIdentifier pack) {
        return reading(pack, resolved -> assemble(pack, resolved));
    }

    @Override
    public List<Tactic> tacticsOf(PackIdentifier pack) {
        return reading(
                pack,
                resolved -> resolved.store.readAll(resolved.key, TacticBinding.INSTANCE).stream()
                        .flatMap(dso -> entity(pack, TacticBinding.INSTANCE, dso).stream())
                        .toList());
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
    static void writeArtifact(FilesystemPackStore store, PackDirectory key, CharterArtifact artifact) {
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
            FilesystemPackStore store, PackDirectory key, KindBinding<D, ?> kind, CharterArtifact artifact) {
        store.write(key, kind, kind.dsoOf(artifact));
    }

    private static Pack assemble(PackIdentifier pack, Resolved resolved) {
        // The lenient load: a graph document the store refuses (a schema fault, a body_path escape, or an edge
        // endpoint whose qualifier is no pack id, #582) is left out, and its violation is not carried. A removal still
        // fails closed: it reads the pack's authored edges too, and that read refuses such a pack (authored()).
        FilesystemPackStore.GraphRead graphs = resolved.store.readGraphs(resolved.key);
        // One walk, shared with the store's read, so the load records each link as StoreRead.of does (D6).
        PackFileSet.Walk walk = PackFileSet.walk(resolved.directory, pack.value());
        // No read serves the warnings in phase 1: the list is filled and dropped.
        Pack.Builder builder = StoreRead.assembly(
                pack,
                resolved.store.read(resolved.key, KindBindings.all(), graphs, walk),
                new HashMap<>(),
                graphs,
                new ArrayList<>());
        StoreRead.linked(walk).forEach(builder::violation);
        return builder.build();
    }

    // ---- the pack document (#451) --------------------------------------------------------------------------------

    @Override
    public Optional<PackDocumentText> document(PackIdentifier pack) {
        return reading(pack, resolved -> PackDocumentFiles.read(pack, resolved.directory));
    }

    @Override
    public boolean holdsDocument(PackIdentifier pack) {
        return reading(pack, resolved -> PackDocumentFiles.holds(resolved.directory));
    }

    // ---- identifier to directory ---------------------------------------------------------------------------------

    /** A pack's store, the directory-name key the store addresses it by, and the pack's directory. */
    private record Resolved(FilesystemPackStore store, PackDirectory key, Path directory) {}

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
        return new Resolved(new FilesystemPackStore(realParent, strategy), PackDirectory.of(real), real);
    }

    /** The pack's store and configured directory, at the location {@link PackLocator} resolves: what a write addresses. */
    private Resolved resolve(PackIdentifier pack) {
        return over(PackLocator.directoryOf(PackLocator.locate(locations, pack)));
    }

    /**
     * A lock-free read of the pack at the location {@link PackLocator} resolves: {@code body} is given the store over the
     * directory a reader reads, the aside copy during a replace's swap window too, and its answer is kept only when no
     * swap moved that directory under it ({@link PackLocator#read}, #490). A whole load runs inside one such read.
     */
    private <T> T reading(PackIdentifier pack, Function<Resolved, T> body) {
        PackLocation location = PackLocator.locate(locations, pack);
        return PackLocator.read(locations, location, directory -> body.apply(over(directory)));
    }

    /** The store rooted at the pack directory's parent, addressed by the directory's own name. */
    private Resolved over(Path directory) {
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack location has no parent directory");
        }
        return new Resolved(new FilesystemPackStore(parent, strategy), PackDirectory.of(directory), directory);
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
