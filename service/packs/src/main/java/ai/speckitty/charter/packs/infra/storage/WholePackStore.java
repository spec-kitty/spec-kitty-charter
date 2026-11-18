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
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Loads a whole pack from a directory into the {@link Pack} aggregate, and writes an aggregate to a new directory
 * (ADR-0037 decision 5). This is the seam the whole-pack round-trip gate drives.
 *
 * <p>The load reads {@code pack.yaml}, every file of every kind bound in {@link KindBindings}, the asset files, the
 * templates, the presets and the pack document ({@code README.md}, #260) into the aggregate (one assembly with
 * {@link DomainRepositoryService#load(PackIdentifier)}), records where each artifact sat in a {@link PackLayout}, and
 * reads the DRG documents (the root {@code *.graph.yaml} shards and {@code drg/fragment.yaml}, the files the source
 * fingerprint covers) with every field. It lists every other file of the pack as
 * {@linkplain LoadedPack#notHeld() not held}, each with its reason. It is partial-tolerant like
 * {@link DomainRepositoryService#load(PackIdentifier)}: an unreadable artifact file is a violation on the aggregate. An
 * unreadable DRG document refuses the pack as {@code drg_schema_error} (ADR-0036 decision 3). The pack's identity is
 * the {@code pack_id} its descriptor declares, whatever the directory is called. The load reads the directory as it
 * finds it; it takes no lock, so a pack swapped by a concurrent commit may be read half before and half after.
 *
 * <p>The write puts {@code pack.yaml}, each artifact at its layout path with its companions beside it (ADR-0039), each
 * asset file at {@code assets/<path>}, each preset at {@code presets/<id>.yaml}, the pack document at {@code README.md}
 * in the Markdown form's canonical text, and each DRG document at its own path into a staging directory beside the
 * target, then renames the staging directory into place, as {@link PackCommitStore} does; a failure removes the staging
 * directory and leaves no target. The manifest, when the pack has one, is written last, its derived fields recomputed
 * from the staged bytes (ADR-0042). Templates and asset files are written byte for byte; everything else is UTF-8 text.
 * It writes what the aggregate and the DRG documents hold and nothing else: the files not held are not copied, and
 * the caller has their list. It refuses a target that already exists (replacing a live pack is the commit store's job)
 * and a pack with read violations: writing it would drop content without a word. A {@code pack.yaml} with an undeclared
 * key never loads (its schema is closed, ADR-0036 decision 3), so the descriptor written is the whole of the file read.
 */
@Service
public final class WholePackStore implements WholePackWriteStore.Loader {

    /**
     * A loaded pack and what is stored beside the aggregate.
     *
     * @param pack the aggregate
     * @param layout each artifact's pack-relative path
     * @param graphs the DRG documents as authored, the root shards by name then the fragment
     * @param notHeld every other file of the pack but a retired one, in one path-sorted list, each with its kind and
     *     reason: a file an {@code exclusions} rule names or only the {@code ignored} fallback takes, with that rule's
     *     reason (#451), and a file nothing models, with {@value NotHeldFile#UNMODELLED_REASON} (operator ruling Q1,
     *     #554). None is pack content: the write does not reproduce them, and a registration does not hold them and
     *     reports each one as a {@code file_not_held} warning
     * @param manifest the {@code pack-manifest.yaml} as read, its derived fields unchecked; empty when the pack has
     *     none, or it is unreadable or lists a preset entry that does not name {@code presets/<id>.yaml} (then a
     *     violation on the aggregate). This store is its only owner (ADR-0042).
     * @param policy the text of the mutability policy file the descriptor names, as read and checked against the
     *     {@code policy} schema (ADR-0063); empty when the descriptor names none, or the named file is missing or
     *     invalid (then a violation on the aggregate)
     * @param warnings the load findings that do not refuse the pack, kept apart from the aggregate's violations
     *     (A4): the {@code prompt_slot_not_empty} warning of a step prompt in a pack whose descriptor declares no
     *     {@code schema_version}
     */
    public record LoadedPack(
            Pack pack,
            PackLayout layout,
            List<GraphDocument> graphs,
            List<NotHeldFile> notHeld,
            Optional<PackManifestDso> manifest,
            Optional<String> policy,
            List<Reason> warnings) {
        /**
         * Checks the fields and copies the lists.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param notHeld the files the pack does not hold, path-sorted, with their reasons
         * @param manifest the manifest, if the pack holds a readable one
         * @param policy the named policy file's text, if the pack holds a valid one
         * @param warnings the load warnings
         */
        public LoadedPack {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(layout, "layout");
            graphs = List.copyOf(graphs);
            notHeld = List.copyOf(notHeld);
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(policy, "policy");
            warnings = List.copyOf(warnings);
        }

        /**
         * A loaded pack with no warning.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param notHeld the files the pack does not hold
         * @param manifest the manifest, if the pack holds a readable one
         * @param policy the named policy file's text, if the pack holds a valid one
         */
        public LoadedPack(
                Pack pack,
                PackLayout layout,
                List<GraphDocument> graphs,
                List<NotHeldFile> notHeld,
                Optional<PackManifestDso> manifest,
                Optional<String> policy) {
            this(pack, layout, graphs, notHeld, manifest, policy, List.of());
        }

        /**
         * A loaded pack without a policy file.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param notHeld the files the pack does not hold
         * @param manifest the manifest, if the pack holds a readable one
         */
        public LoadedPack(
                Pack pack,
                PackLayout layout,
                List<GraphDocument> graphs,
                List<NotHeldFile> notHeld,
                Optional<PackManifestDso> manifest) {
            this(pack, layout, graphs, notHeld, manifest, Optional.empty());
        }

        /**
         * A loaded pack without a manifest.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param notHeld the files the pack does not hold
         */
        public LoadedPack(Pack pack, PackLayout layout, List<GraphDocument> graphs, List<NotHeldFile> notHeld) {
            this(pack, layout, graphs, notHeld, Optional.empty());
        }
    }

    private final SerializationStrategy strategy;
    private final List<KindBinding<?, ?>> bindings;
    private final Companions.Rules companionRules;

    /** The walk of a directory that is not there: the store's read refuses it before it lists anything. */
    private static final PackFileSet.Walk NO_WALK = PackFileSet.Walk.NONE;

    /**
     * The one reader of a pack tree over this store's format, bindings and companion rules (#554, S1): the strict load
     * reads through it ({@link #load}), and a test that carries a binding of its own reads a staged tree with it before
     * it asks {@link #loadStaged}.
     */
    private final FileSystemPackInspector reader;

    /**
     * A store over a format.
     *
     * @param strategy the serialization format
     */
    @Autowired
    public WholePackStore(SerializationStrategy strategy) {
        this(strategy, KindBindings.all(), Companions.Rules.bundled());
    }

    /**
     * A store with its own bindings and companion rules: the seam a test uses to carry a binding of its own, such as
     * one whose companion is Markdown with a test structure schema (#194), through the whole-pack load and write.
     *
     * @param strategy the serialization format
     * @param bindings the bindings to read and write with, in place of the registry; at most one per kind
     * @param companionRules the table that classifies companion paths and the schemas of Markdown companions
     */
    WholePackStore(SerializationStrategy strategy, List<KindBinding<?, ?>> bindings, Companions.Rules companionRules) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.bindings = List.copyOf(bindings);
        this.companionRules = Objects.requireNonNull(companionRules, "companionRules");
        this.reader = new FileSystemPackInspector(companionRules.table(), strategy, this.bindings, companionRules);
    }

    /**
     * The one reader over this store's format, bindings and companion rules. Test-serving: production code reaches the
     * reader through the {@code PackReading} bean and this store's own methods; a test that carries a binding of its own
     * reads a staged tree with this store's reader, so its readout and the store's assembly agree.
     *
     * @return the reader
     */
    FileSystemPackInspector reader() {
        return reader;
    }

    /**
     * Loads every modelled part of the pack in {@code directory}.
     *
     * @param directory the pack directory
     * @return the aggregate, its layout, the DRG documents and the files not held
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory is not a pack directory or has no
     *     readable {@code pack.yaml}; with {@code DRG_SCHEMA_ERROR} when a DRG document cannot be read or breaks its
     *     schema; with {@code VALIDATION_FAILED} when the readable artifacts break an aggregate invariant, such as two
     *     files declaring one id
     */
    public LoadedPack load(Path directory) {
        return loadStrict(directory);
    }

    /**
     * The derived manifest of the pack in {@code directory}, read with this store's bindings and companion rules
     * ({@link PackManifestDerivation#derivedAt}): what the manifest stamp writes and the registration's membership check
     * compares with.
     *
     * @param directory the pack directory
     * @return the manifest with the derived lists
     */
    PackManifestDso derivedManifest(Path directory) {
        return PackManifestDerivation.derivedAt(directory, strategy, bindings, companionRules);
    }

    /**
     * The strict load over the one read ({@link FileSystemPackInspector#readWithStore}): the aggregate from its store
     * half ({@link StoreRead}), and the files not held from its census ({@code readTree(...).census().notHeld()}), so
     * "held" has one owner (#554, S3). A root that is not a directory, or cannot be listed, is the store half's refusal,
     * in its words.
     */
    private LoadedPack loadStrict(Path directory) {
        Path packDir = directory.toAbsolutePath().normalize();
        String name = packDir.getFileName() == null ? "pack" : String.valueOf(packDir.getFileName());
        FileSystemPackInspector.OneRead one =
                reader.readWithStore(new PackRoot(name, Optional.empty(), packDir.toString()));
        StoreRead read = one.store();
        read.graphRefusal().ifPresent(refusal -> {
            throw new CharterRefusal(RefusalCode.DRG_SCHEMA_ERROR, refusal);
        });
        if (!(read.assembly() instanceof Assembly.Assembled held)) {
            throw refusalOf(read);
        }
        return new LoadedPack(
                PackAssembly.build(held),
                read.layout(),
                read.graphs(),
                one.tree().census().notHeld(),
                read.manifest(),
                held.policyText(),
                held.warnings());
    }

    /** The refusal the store's read answered, as the strict load throws it. */
    private static CharterRefusal refusalOf(StoreRead read) {
        if (read.assembly() instanceof Assembly.Refused refused) {
            return new CharterRefusal(refused.code(), refused.message());
        }
        throw new IllegalStateException("the store's read did not refuse the pack");
    }

    /**
     * Assembles a staged tree from the one read the registration judged (#554, S1, FR-006, T049): the aggregate through
     * {@link PackAssembly#build} from the readout's assembly input alone, its composition facts and the digest of each
     * artifact. It reads nothing and judges nothing: the registration refused the upload on the readout's rationale
     * ({@code StorageRulesets.verdict}) before it asks, and a read whose rationale passed holds the whole assembly input.
     */
    @Override
    public WholePackWriteStore.Contents loadStaged(PackReadout read) {
        return contentsOf(new PackIdentifier(read.contents().root().name()), heldBy(read));
    }

    /** The assembly input of a read the registration judged: a read whose rationale passed always holds one. */
    private static Assembly.Assembled heldBy(PackReadout read) {
        if (!(read.contents().assembly() instanceof Assembly.Assembled held)) {
            throw new IllegalArgumentException(
                    "the read of " + read.contents().root().name() + " holds no assembly input; judge it first");
        }
        return held;
    }

    /**
     * Removes the files the one read lists as not held ({@link PackReadout#notHeld()}) from the staged tree, and the
     * directories that leave empty, so the revision and the commit that follow see the tree the service will hold
     * (FR-005, FR-021). It touches the staging directory only: it never reads a path from the pack, and a path that leaves the directory, or is not a
     * regular file, is left alone.
     */
    @Override
    public void removeNotHeld(StagedPack staged, List<NotHeldFile> notHeld) {
        Path root = staged.directory().toAbsolutePath().normalize();
        for (NotHeldFile each : notHeld) {
            Path file = root.resolve(each.path()).normalize();
            // A symlink or an entry outside the root is skipped: the census buckets such entries first, so none is
            // listed.
            if (!file.startsWith(root) || file.equals(root)) {
                continue;
            }
            try {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(file);
                    removeEmptyParents(root, file.getParent());
                }
            } catch (IOException e) {
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE,
                        "The staged file " + each.path() + " could not be removed before the commit",
                        e);
            }
        }
    }

    /**
     * Writes the staged tree's manifest with the membership derived from what the one read holds and fresh derived
     * fields, creating it when the upload held none (#110). It reads no pack file but the bytes the stamp digests, and
     * touches the staging directory only.
     */
    @Override
    public void stampManifest(StagedPack staged, PackReadout read) {
        Assembly.Assembled held = heldBy(read);
        Map<Urn, String> places = new HashMap<>();
        held.artifacts().forEach(placed -> places.put(placed.artifact().urn(), placed.file()));
        PackManifestDso derived = PackManifestDerivation.derived(PackAssembly.build(held), new PackLayout(places));
        try {
            ManifestMembership.stamp(staged.directory(), directory -> derived, strategy);
        } catch (IOException e) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE, "The staged " + PackFileSet.MANIFEST + " could not be written", e);
        }
    }

    /** Deletes {@code directory} and its parents while they are empty, stopping at {@code root}. */
    private static void removeEmptyParents(Path root, @Nullable Path directory) throws IOException {
        Path at = directory;
        while (at != null && !at.equals(root) && at.startsWith(root)) {
            try (Stream<Path> children = Files.list(at)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(at);
            at = at.getParent();
        }
    }

    @Override
    public WholePackWriteStore.Contents loadStored(Path directory) {
        Path packDir = directory.toAbsolutePath().normalize();
        String name = packDir.getFileName() == null ? "pack" : String.valueOf(packDir.getFileName());
        if (!Files.isDirectory(packDir)) {
            throw refusalOf(StoreRead.of(packDir, name, NO_WALK, strategy, bindings, companionRules));
        }
        StoreRead read =
                StoreRead.of(packDir, name, PackFileSet.walk(packDir, name), strategy, bindings, companionRules);
        read.graphRefusal().ifPresent(refusal -> {
            throw new CharterRefusal(RefusalCode.DRG_SCHEMA_ERROR, refusal);
        });
        if (!(read.assembly() instanceof Assembly.Assembled held)) {
            throw refusalOf(read);
        }
        return contentsOf(new PackIdentifier(name), held);
    }

    /** What a salvage has read so far. */
    private static final class Salvage {
        final Set<String> defined = new TreeSet<>();
        final Map<Urn, String> digests = new HashMap<>();
        final List<Relationship> edges = new ArrayList<>();
        final Map<String, Urn> loose = new HashMap<>();

        /** The ids a file that failed to read declares: no copy of such an id stands for it, whatever else reads. */
        final Set<String> unreadable = new HashSet<>();
    }

    /** The digest of an id one of whose files cannot be read: no upload's copy ever equals it. */
    private static final String UNREADABLE_DIGEST = "unreadable";

    /**
     * Reads what can still be read of a stored pack, every file on its own (ADR-0065, decision 01M6V6N74ER28H8S6TSVZ8ZEY2):
     * an artifact file by the {@code id} it declares, read as plain YAML, and by the id its path names when that differs
     * or the key cannot be read, and as an entity when it binds; a graph file as plain YAML, entry by entry. A file that
     * fails to read costs only itself.
     */
    @Override
    public WholePackWriteStore.Remains salvageStored(Path directory) {
        Path packDir = directory.toAbsolutePath().normalize();
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        String name = String.valueOf(packDir.getFileName());
        PackIdentifier key = new PackIdentifier(name);
        FilesystemPackStore store = new FilesystemPackStore(parent, strategy, companionRules);
        Salvage salvage = new Salvage();
        PackFileSet.Listing listing = PackFileSet.listing(packDir); // the pack is listed once
        for (KindBinding<?, ?> binding : bindings) {
            for (String file : listing.files(binding.kind())) {
                salvageFile(store, key, packDir, binding, file, salvage);
            }
        }
        listing.templates().forEach(file -> salvageTemplate(packDir, file, salvage));
        List<Path> graphs = new ArrayList<>(PackFileSet.rootShards(packDir, name));
        if (Files.isRegularFile(packDir.resolve(PackFileSet.FRAGMENT))) {
            graphs.add(packDir.resolve(PackFileSet.FRAGMENT));
        }
        graphs.forEach(graph -> salvageGraph(graph, salvage));
        salvage.unreadable.forEach(id -> Urn.parse(id).ifPresent(urn -> salvage.digests.put(urn, UNREADABLE_DIGEST)));
        return new WholePackWriteStore.Remains(salvage.defined, salvage.digests, salvage.edges, salvage.loose);
    }

    private <D, E extends CharterArtifact> void salvageFile(
            FilesystemPackStore store,
            PackIdentifier key,
            Path packDir,
            KindBinding<D, E> binding,
            String file,
            Salvage out) {
        String stem = stemOf(file, binding.fileToken());
        Optional<String> declared = plainText(packDir.resolve(file)).flatMap(text -> declaredId(text, binding.idKey()));
        Optional<String> byDeclared = urnOf(binding, declared.orElse(stem));
        Optional<String> byName = urnOf(binding, stem);
        byDeclared.ifPresent(out.defined::add);
        byName.ifPresent(out.defined::add);
        if (!readEntity(store, key, packDir, binding, file, out)) {
            // One copy of the id cannot be read: the readable twin, if any, is no proof that the id is unchanged.
            byDeclared.ifPresent(out.unreadable::add);
            byName.ifPresent(out.unreadable::add);
        }
    }

    /**
     * The listed file itself, never a lookup by id: two files declaring one id each contribute their own read.
     *
     * @return whether the file read
     */
    private <D, E extends CharterArtifact> boolean readEntity(
            FilesystemPackStore store,
            PackIdentifier key,
            Path packDir,
            KindBinding<D, E> binding,
            String file,
            Salvage out) {
        try {
            Optional<D> read = store.readListed(packDir, file, binding);
            read.ifPresent(dso -> addRead(binding.toEntity(key, dso), out));
            return read.isPresent();
        } catch (RuntimeException readFault) {
            // This file's read fault: it is counted by its ids above, and nothing else of it is known.
            return false;
        }
    }

    /**
     * An id declared by two files has no one digest: the two are combined, so that no upload's copy equals it.
     */
    private void addRead(CharterArtifact entity, Salvage out) {
        out.digests.merge(
                entity.urn(),
                digestOf(entity),
                (first, second) -> PackRevision.computeOverBytes(List.of(
                                first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8)))
                        .token());
        out.edges.addAll(entity.derivedRelationships());
    }

    private static Optional<String> urnOf(KindBinding<?, ?> binding, String id) {
        try {
            return Optional.of(new Urn(NodeKind.of(binding.kind()), id).toString());
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    private static Optional<String> plainText(Path file) {
        return FilesystemPackStore.readBytes(file).flatMap(FilesystemPackStore::decodeUtf8);
    }

    /** The {@code id} a file declares, read as plain YAML outside any schema. */
    private static Optional<String> declaredId(String text, String idKey) {
        if (ParsedYaml.parse(text, "salvage").document() instanceof Map<?, ?> map
                && map.get(idKey) instanceof String id
                && !id.isBlank()) {
            return Optional.of(id.strip());
        }
        return Optional.empty();
    }

    private static void salvageTemplate(Path packDir, String file, Salvage out) {
        Urn urn = new Urn(NodeKind.TEMPLATE, PackFileSet.templateId(file));
        out.defined.add(urn.toString());
        FilesystemPackStore.readBytes(packDir.resolve(file))
                .ifPresent(bytes -> out.digests.put(
                        urn, PackRevision.computeOverBytes(List.of(bytes)).token()));
    }

    /** A graph file as plain YAML: every entry of {@code edges} whose source can be read; a file that is no YAML adds none. */
    private static void salvageGraph(Path file, Salvage out) {
        Optional<String> text = plainText(file);
        if (text.isPresent()
                && ParsedYaml.parse(text.get(), "salvage").document() instanceof Map<?, ?> document
                && document.get("edges") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> fields) {
                    salvageEdge(fields, out);
                }
            }
        }
    }

    private static void salvageEdge(Map<?, ?> entry, Salvage out) {
        Optional<PackQualifiedReference> source = reference(entry.get("source"));
        if (source.isEmpty() || source.get().asUrn().isEmpty()) {
            return;
        }
        Optional<PackQualifiedReference> target = reference(entry.get("target"));
        Optional<RelationType> relation =
                entry.get("relation") instanceof String token ? RelationType.fromToken(token) : Optional.empty();
        if (target.isPresent() && target.get().asUrn().isPresent() && relation.isPresent()) {
            try {
                if (EndpointRule.bind(
                                relation.get(),
                                source.get().asUrn().get(),
                                source.get().packName(),
                                target.get().asUrn().get(),
                                target.get().packName(),
                                Optional.empty(),
                                Optional.empty())
                        instanceof EndpointRule.Bound bound) {
                    out.edges.add(bound.relationship());
                    return;
                }
                // refused by the endpoint table: not a relationship, so it is kept loosely below
            } catch (IllegalArgumentException selfLoop) {
                // not a relationship: it is kept loosely below
            }
        }
        out.loose.put(
                source.get() + "|" + entry.get("target"), source.get().asUrn().get());
    }

    private static Optional<PackQualifiedReference> reference(@Nullable Object value) {
        if (!(value instanceof String text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(PackQualifiedReference.parse(text));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static String stemOf(String file, String token) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        String suffix = "." + token + ".yaml";
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /**
     * What a tree holds: the aggregate, built from the read's assembly input alone ({@link PackAssembly#build}), the
     * facts the compose reads, and each artifact's digest. The facts were built from the same read
     * ({@link CompositionFactsBuilder#heldFacts}).
     */
    private WholePackWriteStore.Contents contentsOf(PackIdentifier key, Assembly.Assembled held) {
        PackCompositionFacts facts = CompositionFactsBuilder.heldFacts(key, held);
        Pack pack = PackAssembly.build(held);
        Map<Urn, String> digests = new HashMap<>();
        pack.artifacts().forEach(artifact -> digests.put(artifact.urn(), digestOf(artifact)));
        return new WholePackWriteStore.Contents(pack, facts, digests);
    }

    /** A digest of one file-backed artifact as {@link #write} would write it, its companions included. */
    private String digestOf(CharterArtifact artifact) {
        List<byte[]> parts = new ArrayList<>();
        if (artifact instanceof Template template) {
            parts.add(TemplateBinding.INSTANCE.payloadOf(template).bytes());
        } else {
            KindBinding<?, ?> kind = bindings.stream()
                    .filter(binding -> binding.kind() == artifact.kind())
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalArgumentException("cannot digest a " + artifact.kind() + " artefact"));
            digestParts(kind, artifact, parts);
        }
        return PackRevision.computeOverBytes(parts).token();
    }

    private <D> void digestParts(KindBinding<D, ?> kind, CharterArtifact artifact, List<byte[]> parts) {
        D dso = kind.dsoOf(artifact);
        parts.add(utf8(strategy.write(kind, dso)));
        for (KindBinding.Companion companion : kind.companions(dso)) {
            companion.content().ifPresent(content -> parts.add(utf8(Companions.text(content))));
        }
    }

    /**
     * Writes a loaded pack to a directory that does not exist yet.
     *
     * @param loaded the pack, its layout and its DRG documents
     * @param target the directory to create
     * @throws CharterRefusal with {@code VALIDATION_FAILED} when the pack has read violations, or a mission type names
     *     an output template the pack does not hold (#277), or a guard or a lifecycle does not close over its
     *     references (ADR-0054); with
     *     {@code PACK_UNREADABLE} when the target exists, a path leaves the target, two parts
     *     share a path, or the write fails. In every case no staging directory remains, and no target unless it was
     *     there before.
     */
    public void write(LoadedPack loaded, Path target) {
        Pack pack = loaded.pack();
        if (!pack.violations().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    "pack " + pack.id().value() + " has " + pack.violations().size()
                            + " unreadable files; writing it would drop them");
        }
        ReferenceSubjects.firstRefusal(pack).ifPresent(finding -> {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, finding.refusal());
        });
        Path destination = target.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the write target needs a parent directory");
        }
        @Nullable Path staging = null;
        @Nullable Path reserved = null;
        try {
            Files.createDirectories(parent);
            reserved = reserve(destination, pack);
            staging = Files.createTempDirectory(parent, ".pack-write-");
            putContents(staging, loaded);
            PackCommitStore.moveOver(staging, reserved);
            staging = null; // moved into place; nothing left to clean up
            reserved = null;
        } catch (CharterRefusal refusal) {
            PackCommitStore.deleteTree(staging);
            PackCommitStore.release(reserved);
            throw refusal;
        } catch (IOException | RuntimeException failure) {
            PackCommitStore.deleteTree(staging);
            PackCommitStore.release(reserved);
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "cannot write pack " + pack.id().value(),
                    failure);
        }
    }

    /** Puts every file of the loaded pack into the staging directory, each path once. */
    private void putContents(Path staging, LoadedPack loaded) throws IOException {
        Pack pack = loaded.pack();
        Set<String> written = new HashSet<>();
        putDescriptor(staging, loaded, written);
        for (CharterArtifact artifact : pack.artifacts()) {
            putArtifact(staging, loaded.layout().pathOf(artifact), artifact, written);
        }
        for (AssetFile file : pack.assetFiles()) {
            putPayload(
                    staging,
                    PackFileSet.ASSETS + file.path(),
                    file.payload().bytes(),
                    PackFileKind.ASSET_PAYLOAD,
                    written);
        }
        for (Preset preset : pack.presets()) {
            put(
                    staging,
                    PresetFiles.pathOf(preset.id()),
                    utf8(strategy.writePreset(PresetFiles.toDso(preset))),
                    written);
        }
        for (GraphDocument graph : loaded.graphs()) {
            put(staging, graph.file(), utf8(strategy.writeGraph(graph)), written);
        }
        if (pack.document().isPresent()) {
            put(
                    staging,
                    PackDocumentMarkdown.FILE,
                    utf8(PackDocumentMarkdown.toWritableText(
                            pack.id(), pack.document().get())),
                    written);
        }
        // #110: the manifest is always written, its membership derived from the pack written, never carried as read.
        putManifest(staging, pack, PackManifestDerivation.derived(pack, loaded.layout()), written);
    }

    /**
     * Writes {@code pack.yaml} and, right after it, the policy file it names (ADR-0063), as it was read. A descriptor that
     * names a policy file the loaded pack does not hold is refused: the stored pack would be unreadable.
     */
    private void putDescriptor(Path staging, LoadedPack loaded, Set<String> written) throws IOException {
        Pack pack = loaded.pack();
        put(
                staging,
                PackFileSet.PACK_YAML,
                utf8(strategy.writeDescriptor(PackDescriptorDso.of(pack.descriptor()))),
                written);
        Optional<String> named = pack.descriptor().paths().mutabilityPolicy();
        if (named.isEmpty()) {
            return;
        }
        String text = loaded.policy()
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.VALIDATION_FAILED,
                        "pack " + pack.id().value() + " names mutability_policy " + named.get()
                                + ", which the loaded pack does not hold; writing it would leave the pack unreadable"));
        put(staging, named.get(), utf8(text), written);
    }

    /**
     * Writes the manifest last, every derived field recomputed from the bytes staged (ADR-0042): what was read is never
     * copied. Its lists are the derived ones (#110), so a listed path the write does not hold is a fault of this store,
     * and refuses the write.
     */
    private void putManifest(Path staging, Pack pack, PackManifestDso manifest, Set<String> written)
            throws IOException {
        PackManifests.Recomputed recomputed = PackManifests.recompute(manifest, path -> staged(staging, path));
        if (!recomputed.missing().isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    PackFileSet.MANIFEST + " of " + pack.id().value() + " lists "
                            + recomputed.missing().getFirst() + ", which the write does not hold");
        }
        put(staging, PackFileSet.MANIFEST, utf8(strategy.writeManifest(recomputed.stamped())), written);
    }

    /** The bytes of a staged pack-relative file, or empty when the path leaves the pack or holds no regular file. */
    private static Optional<byte[]> staged(Path staging, String path) {
        if (Companions.inPack(staging, path) instanceof Companions.Inside at && Files.isRegularFile(at.file())) {
            return FilesystemPackStore.readBytes(at.file());
        }
        return Optional.empty();
    }

    /**
     * Creates the empty target directory, which fails if anything is there already: the check and the claim are one
     * step, so a target that appears after the check cannot be written over.
     */
    private static Path reserve(Path destination, Pack pack) throws IOException {
        try {
            return Files.createDirectory(destination);
        } catch (java.nio.file.FileAlreadyExistsException taken) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "refusing to write pack " + pack.id().value() + " over an existing " + destination.getFileName());
        }
    }

    /** A template's bytes as they are; any other artifact's text through its binding, as UTF-8, with its companions. */
    private void putArtifact(Path staging, String path, CharterArtifact artifact, Set<String> written)
            throws IOException {
        if (artifact instanceof Template template) {
            putPayload(
                    staging,
                    path,
                    TemplateBinding.INSTANCE.payloadOf(template).bytes(),
                    PackFileKind.TEMPLATE,
                    written);
            return;
        }
        KindBinding<?, ?> kind = bindings.stream()
                .filter(binding -> binding.kind() == artifact.kind())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact"));
        putArtifact(staging, path, kind, artifact, written);
    }

    /**
     * Writes an artifact's file and then each of its companions beside it (ADR-0039), each in its declared form: text
     * as it is, Markdown in its canonical form (#194). A companion without content, one the companion rule refuses, or
     * a Markdown companion that breaks its structure schema refuses the write: after a load none can happen, so it is
     * a caller's error.
     */
    private <D> void putArtifact(
            Path staging, String path, KindBinding<D, ?> kind, CharterArtifact artifact, Set<String> written)
            throws IOException {
        D dso = kind.dsoOf(artifact);
        put(staging, path, utf8(strategy.write(kind, dso)), written);
        for (KindBinding.Companion companion : kind.companions(dso)) {
            String named = path + ": companion " + companion.path();
            CompanionContent content = companion
                    .content()
                    .orElseThrow(() -> new CharterRefusal(RefusalCode.PACK_UNREADABLE, named + " has no content"));
            Companions.Inside at = switch (Companions.resolve(companionRules, staging, path, companion)) {
                case Companions.Inside inside -> inside;
                case Companions.Refused refused ->
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, named + " " + refused.reason());
            };
            Companions.breach(companionRules, companion, at.packRelative(), content)
                    .ifPresent(breach -> {
                        throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, named + " breaks its " + breach);
                    });
            put(staging, at.packRelative(), utf8(Companions.text(content)), written);
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Writes one {@code bytes} file of the staged pack, an asset file or a template, as {@link #put} does. Its path must
     * classify back as {@code kind} ({@link PackFileSet#notHeldAs}): one file, one owner, as the store's own write
     * refuses it.
     */
    private static void putPayload(Path staging, String relative, byte[] content, String kind, Set<String> written)
            throws IOException {
        if (Companions.inPack(staging, relative) instanceof Companions.Inside at) {
            PackFileSet.notHeldAs(staging, at.packRelative(), kind).ifPresent(reason -> {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "path " + relative + " " + reason);
            });
        }
        put(staging, relative, content, written);
    }

    /**
     * Writes one file of the staged pack. A path the containment rule refuses ({@link Companions#inPack}: one that
     * leaves the pack, among others) is refused, and so is one already written, case ignored, so a case-insensitive
     * file system cannot fold two parts into one file.
     */
    private static void put(Path staging, String relative, byte[] content, Set<String> written) throws IOException {
        Companions.Inside at = switch (Companions.inPack(staging, relative)) {
            case Companions.Inside inside -> inside;
            case Companions.Refused refused ->
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "path " + relative + " " + refused.reason());
        };
        Path file = at.file();
        if (!written.add(at.claim())) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "two parts of the pack are written to " + relative);
        }
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        Files.write(file, content);
    }
}
