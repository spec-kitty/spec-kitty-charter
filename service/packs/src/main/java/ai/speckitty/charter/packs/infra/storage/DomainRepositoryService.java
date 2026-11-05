package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.missions.domain.UnresolvedTemplate;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Preset;
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
 * {@link PackDescriptorRepository}, {@link PackCatalogRepository} and {@link PackRepository} over the filesystem.
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
                ParadigmRepository,
                PackDescriptorRepository,
                PackCatalogRepository,
                PackArtifactRepository,
                PackRepository {

    /** The seam that supplies the configured pack locations, read on every call so a reconfiguration is seen. */
    @FunctionalInterface
    public interface Locations {
        /**
         * The configured packs.
         *
         * @return every configured pack location
         */
        List<PackLocation> all();

        /**
         * Why a commit may not write the pack whose live directory is {@code realDirectory}, or empty when it may. The
         * default writes every configured pack; the pack domain keeps a pack linked from outside the configuration root
         * read-only (ADR-0038 as amended 2026-10-31).
         *
         * @param realDirectory the pack's live directory, its real path
         * @return the reason, to follow the pack's name in a refusal; empty when it may be written
         */
        default Optional<String> whyReadOnly(Path realDirectory) {
            return Optional.empty();
        }
    }

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

    // ---- PackArtifactRepository -------------------------------------------------------------------------------

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
     *       references a kind keeps without minting an edge).
     * </ul>
     *
     * <p>The answer fails closed: a graph document or a file of the pack that cannot be read refuses it
     * {@code pack_unreadable}, naming the first such file, since what an unreadable file holds or writes cannot be
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
        Set<String> changed = new LinkedHashSet<>();
        Set<String> references = new LinkedHashSet<>();
        Set<String> declared = new LinkedHashSet<>();
        for (GraphDocument document : graphs.documents()) {
            for (GraphDocument.Edge edge : document.edges()) {
                if (refines(edge.relation())) {
                    changed.add(edge.target());
                }
                refuseSelfLoop(pack, assembled.descriptor().name(), document.file(), edge);
                references.add(edge.source());
                references.add(edge.target());
            }
            declaredWithoutFile(document).forEach(declared::add);
        }
        Set<String> defined = new LinkedHashSet<>();
        for (CharterArtifact artifact : assembled.artifacts()) {
            artifact.writtenReferences().forEach(reference -> references.add(reference.toString()));
            if (artifact.urn().kind().authoredInGraphs()) {
                defined.add(artifact.urn().toString());
            }
        }
        defined.addAll(declared);
        return new PackCompositionFacts(List.copyOf(changed), List.copyOf(defined), List.copyOf(references));
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
     * only as a node of a graph document, so the document is where the pack holds it.
     */
    private static List<String> declaredWithoutFile(GraphDocument document) {
        String token = NodeKind.ANTI_PATTERN.token();
        return switch (document) {
            case GraphDocument.Shard shard ->
                shard.nodes().stream()
                        .filter(node -> token.equals(node.kind()))
                        .map(GraphDocument.ShardNode::urn)
                        .toList();
            case GraphDocument.Fragment fragment ->
                fragment.nodes().stream()
                        .filter(node -> NodeKind.ANTI_PATTERN.plural().equals(node.kind()))
                        .map(node -> token + ":" + node.id())
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

    /** Whether a relation changes its target: a field-merge into it ({@code enhances}) or a full replacement of it. */
    private static boolean refines(String relation) {
        return RelationType.ENHANCES.token().equals(relation)
                || RelationType.OVERRIDES.token().equals(relation);
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
        return assembly(pack, resolved.store.read(resolved.key), new HashMap<>())
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
                Optional<String> unsound =
                        PackPolicy.referenceProblem(read.descriptor().name(), entity);
                if (unsound.isPresent()) {
                    builder.violation(new PackElementViolation(stored.file(), "reference", unsound.get()));
                    continue;
                }
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

    private Resolved resolve(PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return over(location);
            }
        }
        for (PackLocation location : configured) {
            Resolved candidate = over(location);
            if (declaresId(candidate, pack)) {
                return candidate;
            }
        }
        // The identifier matches no configured pack: the caller asked wrongly (CharterRefusalCode pack_not_found).
        throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
    }

    /** The store rooted at the pack directory's parent, addressed by the directory's own name. */
    private Resolved over(PackLocation location) {
        Path directory = location.root().toAbsolutePath().normalize();
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack location has no parent directory");
        }
        return new Resolved(
                new FilesystemPackStore(parent, strategy),
                new PackIdentifier(directory.getFileName().toString()),
                directory);
    }

    private static boolean declaresId(Resolved candidate, PackIdentifier pack) {
        try {
            return candidate.store.readDescriptor(candidate.key).packId().equals(pack.value());
        } catch (CharterRefusal unreadable) {
            return false;
        }
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
