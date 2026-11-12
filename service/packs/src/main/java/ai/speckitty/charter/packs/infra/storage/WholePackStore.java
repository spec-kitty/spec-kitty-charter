package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.application.WholePackWriteStore.ExcludedFile;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
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
 * {@linkplain LoadedPack#unmodelled() unmodelled}: nothing models those yet. It is partial-tolerant like
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
 * It writes what the aggregate and the DRG documents hold and nothing else: the unmodelled files are not copied, and
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
     * @param unmodelled the pack-relative paths of every other file of the pack, sorted: what the write will not
     *     reproduce
     * @param manifest the {@code pack-manifest.yaml} as read, its derived fields unchecked; empty when the pack has
     *     none, or it is unreadable or lists a preset entry that does not name {@code presets/<id>.yaml} (then a
     *     violation on the aggregate). This store is its only owner (ADR-0042).
     * @param policy the text of the mutability policy file the descriptor names, as read and checked against the
     *     {@code policy} schema (ADR-0063); empty when the descriptor names none, or the named file is missing or
     *     invalid (then a violation on the aggregate)
     * @param warnings the load findings that do not refuse the pack, kept apart from the aggregate's violations
     *     (A4): the {@code prompt_slot_not_empty} warning of a step prompt in a pack whose descriptor declares no
     *     {@code schema_version}
     * @param excluded every file the table excludes, sorted (an {@code exclusions} rule names it, or only the {@code ignored}
     *     fallback takes it): not pack content, so neither modelled nor
     *     {@linkplain #unmodelled unmodelled}, each with the rule's reason (#451). The load lists them and the write
     *     does not reproduce them; a registration does not hold them and reports each one.
     */
    public record LoadedPack(
            Pack pack,
            PackLayout layout,
            List<GraphDocument> graphs,
            List<String> unmodelled,
            Optional<PackManifestDso> manifest,
            Optional<String> policy,
            List<PackFinding> warnings,
            List<ExcludedFile> excluded) {
        /**
         * Checks the fields and copies the lists.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param unmodelled the files nothing models
         * @param manifest the manifest, if the pack holds a readable one
         * @param policy the named policy file's text, if the pack holds a valid one
         * @param warnings the load warnings
         * @param excluded the excluded files with their reasons
         */
        public LoadedPack {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(layout, "layout");
            graphs = List.copyOf(graphs);
            unmodelled = List.copyOf(unmodelled);
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(policy, "policy");
            warnings = List.copyOf(warnings);
            excluded = List.copyOf(excluded);
        }

        /**
         * A loaded pack that excludes no file.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param unmodelled the files nothing models
         * @param manifest the manifest, if the pack holds a readable one
         * @param policy the named policy file's text, if the pack holds a valid one
         * @param warnings the load warnings
         */
        public LoadedPack(
                Pack pack,
                PackLayout layout,
                List<GraphDocument> graphs,
                List<String> unmodelled,
                Optional<PackManifestDso> manifest,
                Optional<String> policy,
                List<PackFinding> warnings) {
            this(pack, layout, graphs, unmodelled, manifest, policy, warnings, List.of());
        }

        /**
         * A loaded pack with no warning.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param unmodelled the files nothing models
         * @param manifest the manifest, if the pack holds a readable one
         * @param policy the named policy file's text, if the pack holds a valid one
         */
        public LoadedPack(
                Pack pack,
                PackLayout layout,
                List<GraphDocument> graphs,
                List<String> unmodelled,
                Optional<PackManifestDso> manifest,
                Optional<String> policy) {
            this(pack, layout, graphs, unmodelled, manifest, policy, List.of());
        }

        /**
         * A loaded pack without a policy file.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param unmodelled the files nothing models
         * @param manifest the manifest, if the pack holds a readable one
         */
        public LoadedPack(
                Pack pack,
                PackLayout layout,
                List<GraphDocument> graphs,
                List<String> unmodelled,
                Optional<PackManifestDso> manifest) {
            this(pack, layout, graphs, unmodelled, manifest, Optional.empty());
        }

        /**
         * A loaded pack without a manifest.
         *
         * @param pack the aggregate
         * @param layout the layout
         * @param graphs the DRG documents
         * @param unmodelled the files nothing models
         */
        public LoadedPack(Pack pack, PackLayout layout, List<GraphDocument> graphs, List<String> unmodelled) {
            this(pack, layout, graphs, unmodelled, Optional.empty());
        }
    }

    private static final Pattern FRAGMENT_FILE =
            Pattern.compile("^missions/[^/]+/enhancements/[^/]+\\.prompt-fragment\\.md$");
    private static final String ENHANCEMENT_SUFFIX = ".step-enhancement.yaml";

    private final SerializationStrategy strategy;
    private final List<KindBinding<?, ?>> bindings;
    private final Companions.Rules companionRules;

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
    }

    /**
     * Loads every modelled part of the pack in {@code directory}.
     *
     * @param directory the pack directory
     * @return the aggregate, its layout, the DRG documents and the unmodelled files
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the directory is not a pack directory or has no
     *     readable {@code pack.yaml}; with {@code DRG_SCHEMA_ERROR} when a DRG document cannot be read or breaks its
     *     schema; with {@code VALIDATION_FAILED} when the readable artifacts break an aggregate invariant, such as two
     *     files declaring one id
     */
    public LoadedPack load(Path directory) {
        return load(directory, true, new ArrayList<>()).pack();
    }

    /** What a load read: the pack, and the graph documents that could not be read, when the load did not refuse them. */
    private record Loaded(LoadedPack pack, List<PackElementViolation> graphFaults) {}

    /**
     * The one load. With {@code refuseGraphFault} it refuses on the first unreadable graph document as {@link #load}
     * does, right after the graph read; without it, it goes on and answers the faults in {@link Loaded#graphFaults()}
     * (the registration, which lists every fault at once). The faults are also copied to {@code graphFaults} the moment
     * they are read, so a caller that meets a later refusal still has them.
     */
    private Loaded load(Path directory, boolean refuseGraphFault, List<PackElementViolation> graphFaults) {
        Path packDir = directory.toAbsolutePath().normalize();
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        FilesystemPackStore store = new FilesystemPackStore(parent, strategy, companionRules);
        PackIdentifier key = new PackIdentifier(String.valueOf(packDir.getFileName()));
        FilesystemPackStore.PackRead read = store.read(key, bindings);
        PackIdentifier id = new PackIdentifier(read.descriptor().packId());
        Map<Urn, String> places = new HashMap<>();
        FilesystemPackStore.GraphRead graphs = store.readGraphs(key);
        List<PackFinding> warnings = new ArrayList<>();
        Pack.Builder builder = DomainRepositoryService.assembly(id, read, places, graphs, warnings);
        graphFaults.addAll(graphs.violations());
        if (refuseGraphFault && !graphFaults.isEmpty()) {
            PackElementViolation first = graphFaults.getFirst();
            throw new CharterRefusal(
                    RefusalCode.DRG_SCHEMA_ERROR, id.value() + "/" + first.file() + ": " + first.reason());
        }
        Path manifestFile = packDir.resolve(PackFileSet.MANIFEST);
        Optional<PackManifestDso> manifest = manifest(manifestFile, builder);
        Optional<String> namedPolicy = read.descriptor().mutabilityPolicy();
        Optional<String> policy = namedPolicy.flatMap(named -> policy(packDir, named, builder));
        Pack pack;
        try {
            pack = builder.build();
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
        Set<String> modelled = new HashSet<>(places.values());
        modelled.addAll(companionFiles(packDir, read));
        modelled.add(PackFileSet.PACK_YAML);
        graphs.documents().forEach(graph -> modelled.add(graph.file()));
        // An unreadable graph document is already a fault at its file; it is not also an unmodelled file.
        graphs.violations().forEach(fault -> modelled.add(fault.file()));
        pack.assetFiles().forEach(file -> modelled.add(PackFileSet.ASSETS + file.path()));
        pack.presets().forEach(preset -> modelled.add(PresetFiles.pathOf(preset.id())));
        // An unreadable pack document is likewise a fault at its file (#451), not also an unmodelled file.
        if (pack.document().isPresent()
                || pack.violations().stream().anyMatch(fault -> fault.file().equals(PackDocumentMarkdown.FILE))) {
            modelled.add(PackDocumentMarkdown.FILE);
        }
        if (Files.isRegularFile(manifestFile)) {
            modelled.add(PackFileSet.MANIFEST);
        }
        namedPolicy.ifPresent(modelled::add);
        List<ExcludedFile> excluded = new ArrayList<>();
        List<String> unmodelled = unmodelled(packDir, modelled, key, excluded);
        return new Loaded(
                new LoadedPack(
                        pack,
                        new PackLayout(places),
                        graphs.documents(),
                        unmodelled,
                        manifest,
                        policy,
                        warnings,
                        excluded),
                List.copyOf(graphFaults));
    }

    /**
     * Reads a staged tree for a registration, refusing everything that makes the upload wrong with
     * {@code validation_failed} (IC-04 step 2): what {@link #load} refuses ({@code pack_unreadable}, a graph that breaks
     * its schema, an aggregate invariant) and what it tolerates, which a registration may not: a read violation, a file
     * nothing models, and a {@value RelationshipShard#SHARD_PATH} without the registration stamp, which is a reserved
     * shard. Each fault is a violation of the refusal, so an author sees every one at once, a graph document that breaks
     * its schema included: each of its faults is a {@code drg_schema_error} violation at its pack-relative file, not a refusal on
     * the first (operator ruling D-1). The order is deterministic: the graph faults first, in path order (the load's own
     * first refusal, now listed with the rest), then the pack's read and rule violations, the unmodelled files and
     * orphan fragments in path order, and the reserved shard last. When the aggregate itself cannot be built, the
     * refusal at {@code pack} follows the graph faults already read.
     */
    @Override
    public WholePackWriteStore.Contents loadStaged(StagedPack staged) {
        Path directory = staged.directory();
        Loaded read = loadRefusingAsInvalid(directory);
        LoadedPack loaded = read.pack();
        List<RefusalViolation> violations = new ArrayList<>(graphViolations(read.graphFaults()));
        loaded.pack()
                .violations()
                .forEach(violation ->
                        violations.add(violationOf(violation.file(), violation.kind(), violation.reason())));
        List<String> unmodelled =
                unreportedFiles(loaded.unmodelled(), loaded.pack().violations());
        Map<String, PackElementViolation> orphans = orphanFragments(unmodelled);
        unmodelled.forEach(file -> {
            PackElementViolation orphan = orphans.get(file);
            violations.add(
                    orphan != null
                            ? violationOf(file, orphan.kind(), orphan.reason())
                            : violationOf(
                                    file,
                                    "unmodelled",
                                    "no pack file kind models this file; a registration holds only modelled files"));
        });
        reservedShard(directory).ifPresent(violations::add);
        if (!violations.isEmpty()) {
            throw CharterRefusal.withViolations(
                    RefusalCode.VALIDATION_FAILED,
                    "The pack cannot be registered: " + violations.size() + " fault(s), the first at "
                            + violations.getFirst().pointer() + ".",
                    violations);
        }
        return stagedContentsOf(directory, loaded);
    }

    /**
     * The unmodelled files that no violation of the pack already accounts for: one fault, one violation. A file the
     * store refused with a violation of its own (an asset sidecar whose payload it does not hold, #382; a retired file
     * refused by name, FR-022, SC-002; a pack document it cannot read, #451) is not listed again as unmodelled. An
     * enhancement file the store refuses or withholds (a companion, schema or stem fault, a repeated id) is outside the
     * model, so it is unmodelled, and so are the fragments beside it; but the fault is already reported at the
     * enhancement, so neither the file nor the fragments of its directory are listed again. A genuinely unmodelled file
     * elsewhere stays listed, and so does an orphan fragment beside an enhancement that is held.
     */
    private static List<String> unreportedFiles(List<String> unmodelled, List<PackElementViolation> reported) {
        Set<String> reportedFiles = new HashSet<>();
        reported.forEach(violation -> reportedFiles.add(violation.file()));
        Set<String> refusedDirectories = new HashSet<>();
        for (String file : unmodelled) {
            if (file.endsWith(ENHANCEMENT_SUFFIX) && reportedFiles.contains(file)) {
                refusedDirectories.add(file.substring(0, file.lastIndexOf('/') + 1));
            }
        }
        return unmodelled.stream()
                .filter(file -> !reportedFiles.contains(file))
                .filter(file -> !(FRAGMENT_FILE.matcher(file).matches()
                        && refusedDirectories.contains(file.substring(0, file.lastIndexOf('/') + 1))))
                .toList();
    }

    /**
     * The unmodelled prompt fragments that are orphans (D11): named by no held enhancement, so refused here under
     * {@code prompt_fragment_invalid} at the fragment, and in validate mode. A fragment in a directory holding an
     * enhancement that is not held (refused by the store, or withheld as a repeated id) is no orphan, and with the
     * enhancement's own fault already reported it is not listed at all ({@link #unreportedFiles}) (A4). An
     * enhancement that only has a rule fault is held and excuses nothing (fifth analysis A5). The rule and its text are
     * the step enhancement rules'.
     */
    private static Map<String, PackElementViolation> orphanFragments(List<String> unmodelled) {
        Set<String> fragments = new HashSet<>();
        Set<String> notHeld = new HashSet<>();
        for (String file : unmodelled) {
            if (FRAGMENT_FILE.matcher(file).matches()) {
                fragments.add(file);
            } else if (file.endsWith(ENHANCEMENT_SUFFIX)) {
                notHeld.add(file);
            }
        }
        Map<String, PackElementViolation> orphans = new HashMap<>();
        PackPolicy.stepEnhancementViolations(
                        new StepEnhancementFacts(List.of(), List.of(), fragments, notHeld, List.of()))
                .forEach(violation -> orphans.put(violation.file(), violation));
        return orphans;
    }

    /** The facts of a staged tree; a file the reading of them cannot take is a fault of the upload, a 422, not a 500. */
    private WholePackWriteStore.Contents stagedContentsOf(Path directory, LoadedPack loaded) {
        try {
            return contentsOf(directory, loaded, loaded.excluded());
        } catch (CharterRefusal refusal) {
            if (refusal.code() != RefusalCode.PACK_UNREADABLE) {
                throw refusal;
            }
            String detail = String.valueOf(refusal.getMessage());
            throw CharterRefusal.withViolations(
                    RefusalCode.VALIDATION_FAILED,
                    "The pack cannot be registered: " + detail,
                    List.of(violationOf("pack", "unreadable", detail)));
        }
    }

    /**
     * Removes the files {@code contents} reports as not held ({@link WholePackWriteStore.Contents#notHeld}) from the
     * staged tree, and the directories that leave
     * empty, so the revision and the commit that follow see the tree the service will hold (FR-005, FR-021). It touches
     * the staging directory only: it never reads a path from the pack, and a path that leaves the directory, or is not a
     * regular file, is left alone.
     */
    @Override
    public void removeNotHeld(StagedPack staged, WholePackWriteStore.Contents contents) {
        Path root = staged.directory().toAbsolutePath().normalize();
        for (ExcludedFile notHeld : contents.notHeld()) {
            Path file = root.resolve(notHeld.file()).normalize();
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
                        "The staged file " + notHeld.file() + " could not be removed before the commit",
                        e);
            }
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
        return contentsOf(directory, load(directory), List.of());
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
                out.edges.add(new Relationship(
                        source.get().asUrn().get(),
                        source.get().packName(),
                        target.get().asUrn().get(),
                        target.get().packName(),
                        relation.get(),
                        Optional.empty(),
                        Optional.empty()));
                return;
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
     * {@link #load} with each fault of the tree, which in an upload is the author's, answered {@code validation_failed}.
     * The graph faults are collected, not refused on; a refusal of the load that follows them (the pack as a whole) is
     * listed after them.
     */
    private Loaded loadRefusingAsInvalid(Path directory) {
        List<PackElementViolation> graphFaults = new ArrayList<>();
        try {
            return load(directory, false, graphFaults);
        } catch (CharterRefusal refusal) {
            List<RefusalViolation> violations = new ArrayList<>(graphViolations(graphFaults));
            violations.add(violationOf("pack", refusal.code().token(), String.valueOf(refusal.getMessage())));
            throw CharterRefusal.withViolations(
                    RefusalCode.VALIDATION_FAILED,
                    "The pack cannot be registered: " + refusal.getMessage(),
                    violations);
        }
    }

    /** A {@code drg_schema_error} violation per unreadable graph document, at its file, in path order. */
    private static List<RefusalViolation> graphViolations(List<PackElementViolation> faults) {
        return faults.stream()
                .sorted(Comparator.comparing(PackElementViolation::file))
                .map(fault -> violationOf(fault.file(), RefusalCode.DRG_SCHEMA_ERROR.token(), fault.reason()))
                .toList();
    }

    /** A violation of the refusal; a kind that is no snake-case token is told as {@code invalid}. */
    private static RefusalViolation violationOf(String pointer, String code, String detail) {
        // The detail names the file as the pointer does, so a client that shows only the detail still says where.
        String named = pointer.isEmpty() || detail.contains(pointer) ? detail : pointer + ": " + detail;
        return new RefusalViolation(pointer, code.matches("[a-z][a-z0-9_]*") ? code : "invalid", named);
    }

    /** The reserved shard, when the tree holds one that no registration wrote. */
    private static Optional<RefusalViolation> reservedShard(Path directory) {
        Path shard = directory.resolve(RelationshipShard.SHARD_PATH);
        if (!Files.isRegularFile(shard, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        boolean stamped;
        try {
            stamped = RelationshipShard.isWrittenByRegistration(Files.readString(shard, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            stamped = false;
        }
        return stamped
                ? Optional.empty()
                : Optional.of(violationOf(
                        RelationshipShard.SHARD_PATH,
                        "reserved_shard",
                        RelationshipShard.SHARD_PATH
                                + " is reserved for the registered relationships; a file of this name"
                                + " without generated_by: charter-registration is refused. Register the edges in a shard of"
                                + " your own."));
    }

    /**
     * What the registration judges of a loaded tree: the aggregate, the facts the compose reads, each artifact's digest,
     * and the files it does not hold, each also a {@code file_not_held} warning after the load's own.
     */
    private WholePackWriteStore.Contents contentsOf(Path directory, LoadedPack loaded, List<ExcludedFile> notHeld) {
        Path packDir = directory.toAbsolutePath().normalize();
        PackIdentifier key = new PackIdentifier(String.valueOf(packDir.getFileName()));
        PackLevel level = loaded.pack().descriptor().declaredLevel().orElse(PackLevel.ORG);
        // The facts are read the way the compose reads a stored pack, through a repository that lists only this tree.
        DomainRepositoryService single =
                new DomainRepositoryService(() -> List.of(new PackLocation(key.value(), level, packDir)), strategy);
        List<PackFinding> warnings = new ArrayList<>(loaded.warnings());
        notHeld.forEach(file -> warnings.add(file.notHeld()));
        return new WholePackWriteStore.Contents(
                loaded.pack(), single.compositionFacts(key), digestsOf(loaded.pack()), warnings, notHeld);
    }

    /** A digest of each file-backed artifact as {@link #write} would write it, its companions included. */
    private Map<Urn, String> digestsOf(Pack pack) {
        Map<Urn, String> digests = new HashMap<>();
        for (CharterArtifact artifact : pack.artifacts()) {
            digests.put(artifact.urn(), digestOf(artifact));
        }
        return digests;
    }

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
     * The manifest, when the pack holds a readable one whose preset entries name their own files; otherwise empty, and
     * an unreadable or misplaced manifest is a violation on the aggregate.
     */
    private Optional<PackManifestDso> manifest(Path manifestFile, Pack.Builder builder) {
        if (!Files.isRegularFile(manifestFile)) {
            return Optional.empty();
        }
        switch (readManifest(manifestFile)) {
            case FileRead.Read<PackManifestDso> good -> {
                List<String> misplaced = misplacedPresetEntries(good.value());
                misplaced.forEach(
                        reason -> builder.violation(new PackElementViolation(PackFileSet.MANIFEST, "schema", reason)));
                return misplaced.isEmpty() ? Optional.of(good.value()) : Optional.empty();
            }
            case FileRead.Unreadable<PackManifestDso> bad ->
                FilesystemPackStore.violationsOf(bad).forEach(builder::violation);
            case FileRead.Absent<PackManifestDso> absent -> {}
        }
        return Optional.empty();
    }

    /**
     * The text of the policy file the descriptor names (ADR-0063), read and checked as the compose reads it; a named
     * file that is missing or invalid is a violation on the aggregate, so the write refuses the pack instead of storing
     * a descriptor that names a file it does not hold.
     */
    private static Optional<String> policy(Path packDir, String named, Pack.Builder builder) {
        try {
            return Optional.of(PolicyYaml.readNamed(packDir, named).text());
        } catch (PolicyYaml.Unreadable invalid) {
            builder.violation(new PackElementViolation(
                    named, "unreadable", "named by mutability_policy, the file " + invalid.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Every preset entry of a manifest that does not name its own preset's file, as its reason
     * ({@link PresetFiles#entryPathMismatch}), in listed order.
     *
     * @param manifest a schema-valid manifest
     * @return the reasons; empty when every entry names {@code presets/<id>.yaml}
     */
    static List<String> misplacedPresetEntries(PackManifestDso manifest) {
        List<String> out = new ArrayList<>();
        for (PackManifestDso.PresetEntry entry : manifest.presets()) {
            PresetFiles.entryPathMismatch(entry.id(), entry.path()).ifPresent(out::add);
        }
        return out;
    }

    /** The manifest's text read through the format, or why it cannot be read; the store never parses it. */
    private FileRead<PackManifestDso> readManifest(Path file) {
        Optional<byte[]> bytes = FilesystemPackStore.readBytes(file);
        return bytes.flatMap(FilesystemPackStore::decodeUtf8)
                .map(text -> strategy.readManifest(text, PackFileSet.MANIFEST))
                .orElseGet(() -> new FileRead.Unreadable<>(
                        PackFileSet.MANIFEST,
                        new ParseFailure(PackFileSet.MANIFEST, FilesystemPackStore.notText(bytes)),
                        List.of()));
    }

    /**
     * The pack-relative paths of the companions every readable artifact names (ADR-0039), as the companion rule resolves
     * them: they are modelled with their artifact, so none is listed as unmodelled.
     */
    private Set<String> companionFiles(Path packDir, FilesystemPackStore.PackRead read) {
        Set<String> out = new HashSet<>();
        for (KindBinding<?, ?> kind : bindings) {
            if (kind.hasCompanions()) {
                out.addAll(companionFiles(packDir, kind, read));
            }
        }
        return out;
    }

    private <D> List<String> companionFiles(Path packDir, KindBinding<D, ?> kind, FilesystemPackStore.PackRead read) {
        List<String> out = new ArrayList<>();
        for (FilesystemPackStore.Stored<D> stored : read.elements(kind)) {
            for (KindBinding.Companion companion : kind.companions(stored.dso())) {
                if (Companions.resolve(companionRules, packDir, stored.file(), companion)
                        instanceof Companions.Inside at) {
                    out.add(at.packRelative());
                }
            }
        }
        return out;
    }

    /**
     * Every regular file of the pack that is not in {@code modelled}, pack-relative and sorted. The walk starts at the
     * directory's real path, as {@code PackFileSet}'s does, so a pack root that is a symbolic link is entered (#284);
     * links below the root are not followed. Unlike {@code PackFileSet}, a file that is itself a link is not listed
     * here ({@code NOFOLLOW_LINKS}): the whole-pack census classifies in-pack symlinks separately, so they are not
     * counted among the unmodelled regular files. A file that classifies as excluded is not unmodelled: it goes to
     * {@code excluded} with its rule's reason.
     */
    private List<String> unmodelled(
            Path packDir, Set<String> modelled, PackIdentifier key, List<ExcludedFile> excluded) {
        Path root;
        try {
            root = packDir.toRealPath();
        } catch (IOException e) {
            throw PackFileSet.unlistable(key.value(), e);
        }
        List<String> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> root.relativize(path)
                            .toString()
                            .replace(path.getFileSystem().getSeparator(), "/"))
                    .filter(file -> !modelled.contains(file))
                    .sorted()
                    .toList();
        } catch (IOException | RuntimeException e) {
            throw PackFileSet.unlistable(key.value(), e);
        }
        // A file an exclusion names (or the ignored fallback) is not pack content: listed apart, never unmodelled
        // (#451).
        List<String> unmodelled = new ArrayList<>();
        for (String file : files) {
            if (companionRules.table().classify(key.value() + "/" + file) instanceof PackFileKind.Excluded rule) {
                excluded.add(new ExcludedFile(file, rule.reason()));
            } else {
                unmodelled.add(file);
            }
        }
        return unmodelled;
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
        MissionTemplates.requireResolved(pack);
        GuardClosure.requireResolved(pack);
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
        if (loaded.manifest().isPresent()) {
            putManifest(staging, pack, loaded.manifest().get(), written);
        }
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
     * copied. A listed path the write does not hold refuses the write.
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
