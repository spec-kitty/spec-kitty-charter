package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.AssetMimeTable;
import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.MediaTypes;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRelativePaths;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.RoutingCatalogFacts;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.domain.rules.DescriptorChecks;
import ai.speckitty.charter.packs.domain.rules.Findings;
import ai.speckitty.charter.packs.domain.rules.Paths;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import ai.speckitty.charter.packs.infra.storage.rules.LinkRules;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import ai.speckitty.charter.packs.infra.storage.rules.TreeCensus;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The one reader of a pack tree (#554, S1, FR-016): one walk of a pack directory gives one {@link TreeRead}, the pack's
 * storage-agnostic {@link PackContents}, the {@link TreeCensus} that owns "held" (every file classified, the files not
 * held with their kind and reason, the manifest and the reserved shard) and the read faults, each a kernel reason with
 * the code its owner builds and the reader's wording. It decides nothing beyond the read: the storage edge's
 * compositions judge it ({@link StorageRulesets#verdict}), and the readout carries their verdict. It implements the domain port {@link PackReading}, so
 * registration and validate mode reach the read without naming a storage type (S6): the one domain port of the read.
 *
 * <p>A root that is not a directory, or that cannot be listed or read, is one read fault, {@code pack_unreadable} at
 * {@value #ROOT}, as a registration answers it (F4); the read never throws on it. The census reads the manifest, the reserved shard and the files not held without following a link.
 *
 * <p>The descriptor's format (its {@code requires_core}, {@code pack_version} and {@code depends_on} ranges, through
 * {@link DescriptorChecks}) and each contained asset's MIME type (through {@link AssetMimeTable}) are prechecks of the
 * read (S2): read faults with the {@link StorageCodes} tokens and the R2 wording (rows 14 and 38).
 *
 * <p>The read takes {@code pack.yaml}, the root {@code *.graph.yaml} shards (sorted by file name) and
 * {@code drg/fragment.yaml}, the places core's runtime reads, each as {@link FileRead.Absent},
 * {@link FileRead.Unreadable} or {@link FileRead.Read}.
 *
 * <p>It then reads every other {@code *.yaml} file of the pack, in path order:
 * each is classified by {@link PackFileKind} (mapped to a schema, or excluded for a reviewed reason; a file no rule
 * covers is left out of the classification and listed as not held, #554), and each mapped file that has a schema is
 * parsed and validated through {@link PackSchemas}; a schema-less payload (a {@code payloads} rule's kind, such as an
 * asset payload that happens to be YAML) is listed with its kind and not read. The step ids, artifact ids and asset
 * declarations of the files that passed their schema are the facts the rules judge; a file that did not is a read
 * fault.
 *
 * <p>It lists every payload and template the store's listing holds, whatever its extension, with its
 * kind, and reads none (#251). It applies the store's payload rule to the schema-valid asset sidecars
 * ({@link SidecarPayloads}, ADR-0040 decision 6): a sidecar whose payload the pack does not hold, or that names a
 * payload an earlier sidecar named, is a read fault with the load's refusal, so validate mode
 * and the load refuse the same sidecars in the same words.
 *
 * <p>It also reads every {@code *.md} file that a {@code files} rule maps to a bound kind's Markdown
 * companion structure (a toolguide's guide, #194), as the store's load does: parsed with {@link MarkdownCodec},
 * validated against the structure schema, then held to the rules the schema cannot express
 * ({@link KindBinding#checkCompanion}). A guide that breaks one is a read fault, so validate
 * mode and the load refuse the same guide. It reads the pack document ({@code README.md}, #260) the same way, through
 * the store's own read ({@link PackDocumentMarkdown#read}). Other Markdown files are not read, nor listed in the
 * classification.
 *
 * <p>It reports every retired file (ADR-0044 decision 6), YAML and Markdown alike, as
 * a read fault naming its rule's reason, from the store's own listing
 * ({@link PackFileSet.Listing#retired()}), so validate mode and the load refuse the same files by name.
 *
 * <p>Every call reads the files again, every file through {@link ParsedYaml} and never through {@link Yaml} directly,
 * so that a document nested too deeply or holding a scalar no validator can judge is a parse failure. A file whose
 * bytes are not UTF-8 is a parse failure too, and the code of its finding follows its kind like every other. The
 * content of a file never throws.
 */
public final class FileSystemPackInspector implements PackReading {

    /** Where a fault of the root itself is placed: the pack as a whole, as a registration places it (F4). */
    static final String ROOT = "pack";

    private static final String MARKDOWN = ".md";

    /** The fixed length of a chain of symlinks that is followed; as long as the kernel's, which refuses past 40. */
    private static final int MAX_LINKS = 40;

    /**
     * The schema kinds a {@code files} rule of {@code file-kinds.yaml} maps whose schema declares an {@code id} (an
     * agent profile declares {@code profile-id}, so it is not one), less the pack-level {@code preset}: a preset
     * declares an id but is no artifact (#195, ADR-0042). Derived from the table and the schemas, so a kind added to
     * either is one here too, and built on first use: it needs the schemas, which are built lazily.
     */
    private static final class ArtifactKinds {
        /** File kinds with an {@code id} that are pack-level files, not artifacts; declared first, as derive reads it. */
        private static final Set<String> PACK_LEVEL = Set.of(PresetYaml.SCHEMA);

        static final Set<String> KINDS = derive();

        private static Set<String> derive() {
            PackSchemas schemas = PackSchemas.bundled();
            return PackFileKind.bundled().fileRules().stream()
                    .map(PackFileKind.FileRule::schema)
                    .distinct()
                    .filter(kind -> !PACK_LEVEL.contains(kind))
                    .filter(kind -> schemas.declaredProperties(kind).contains("id"))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /** The artifact schema kinds, for the tests. */
    static Set<String> artifactKinds() {
        return ArtifactKinds.KINDS;
    }

    private final PackFileKind table;

    /** The store's format: the read's DSO half and the manifest's lists are read in it. */
    private final SerializationStrategy strategy;

    /** The bindings the read's DSO half reads with. */
    private final List<KindBinding<?, ?>> bindings;

    /** The companion rules the read's DSO half reads with. */
    private final Companions.Rules companionRules;

    /** The one walk of a pack tree: {@link PackFileSet#walk}, or a test's failing walk. */
    private final BiFunction<Path, String, PackFileSet.Walk> walker;

    /** An inspector over the bundled file-kinds table; it keeps no state. */
    public FileSystemPackInspector() {
        this(PackFileKind.bundled());
    }

    /**
     * The reader over the bundled file-kinds table and companion rules and every binding, in {@code strategy}'s format:
     * the reader a composition root supplies, over the format its store reads.
     *
     * @param strategy the serialization format
     */
    public FileSystemPackInspector(SerializationStrategy strategy) {
        this(PackFileKind.bundled(), strategy, KindBindings.all(), Companions.Rules.bundled());
    }

    /**
     * An inspector that classifies with {@code table} in place of the bundled one: the seam a test uses to classify
     * with rules of its own, retired ones included (#194).
     *
     * @param table the file-kinds table
     */
    FileSystemPackInspector(PackFileKind table) {
        this(
                table,
                new YamlSerializationStrategy(),
                KindBindings.all(),
                new Companions.Rules(table, PackSchemas::bundled));
    }

    /**
     * The reader a store composes, over its own format, bindings and companion rules, so the one read is the store's
     * read (#554, S1).
     *
     * @param table the file-kinds table
     * @param strategy the serialization format
     * @param bindings the bindings to read with
     * @param companionRules the companion rules
     */
    FileSystemPackInspector(
            PackFileKind table,
            SerializationStrategy strategy,
            List<KindBinding<?, ?>> bindings,
            Companions.Rules companionRules) {
        this(table, strategy, bindings, companionRules, PackFileSet::walk);
    }

    /**
     * The reader over a walk of the test's own: the seam a test uses for a directory that exists but cannot be listed,
     * which a process running as root cannot plant with permissions (#554, T026).
     *
     * @param table the file-kinds table
     * @param strategy the serialization format
     * @param bindings the bindings to read with
     * @param companionRules the companion rules
     * @param walker the walk of a pack directory and its name
     */
    FileSystemPackInspector(
            PackFileKind table,
            SerializationStrategy strategy,
            List<KindBinding<?, ?>> bindings,
            Companions.Rules companionRules,
            BiFunction<Path, String, PackFileSet.Walk> walker) {
        this.table = Objects.requireNonNull(table, "table");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.bindings = List.copyOf(bindings);
        this.companionRules = Objects.requireNonNull(companionRules, "companionRules");
        this.walker = Objects.requireNonNull(walker, "walker");
    }

    /**
     * Reads one pack root once (S6): its contents, the files it classifies, the files the store does not hold and the
     * storage edge's verdict of the read ({@link StorageRulesets#verdict}: the read faults, the registration's
     * composition and the manifest gate), in registration's order, and the pack's composition facts from the same read
     * ({@link CompositionFactsBuilder#readoutFacts}, #478 S1): the store half the walk already ran, never a second read.
     *
     * @param root the pack to read; its {@code locator} is the directory
     * @return the readout
     */
    @Override
    public PackReadout read(PackRoot root) {
        return readout(root, readWithStore(root));
    }

    /**
     * The readout of one read already made: its contents, census, verdict and composition facts, all from {@code one}.
     *
     * @param root the pack read; its name is the name the composition facts' refusals name, and a name that is not a
     *     pack id gives no facts (#583, D5)
     * @param one the one read of {@code root}
     * @return the readout
     */
    static PackReadout readout(PackRoot root, OneRead one) {
        TreeRead tree = one.tree();
        return new PackReadout(
                tree.contents(),
                tree.census().files(),
                tree.census().notHeld(),
                StorageRulesets.verdict(tree),
                CompositionFactsBuilder.readoutFacts(root.name(), one.store()));
    }

    /**
     * The one read of a pack tree (S1): every file, once. Package-private: a public method of a port's implementation
     * may not return a storage type ({@code STORAGE_RETURNS_DOMAIN}), and its callers, the store's load and the composing
     * read, share this package.
     *
     * @param root the pack to read; its {@code locator} is the directory
     * @return the contents, the census and the read faults; a root that cannot be read is one read fault
     */
    TreeRead readTree(PackRoot root) {
        return readWithStore(root).tree();
    }

    /**
     * The one read of a pack tree and the store half it ran over the same walk (#554, S3): what the store's strict load
     * answers from, so that its files not held are the census's ({@link TreeCensus#notHeld()}) and "held" has one
     * owner.
     *
     * @param tree the one read
     * @param store the store half of it ({@link StoreRead}), the DSO read the census shares a walk with
     */
    record OneRead(TreeRead tree, StoreRead store) {}

    /**
     * The one read of a pack tree with its store half ({@link #readTree}).
     *
     * @param root the pack to read; its {@code locator} is the directory
     * @return the read and its store half; a root that cannot be read is one read fault and a refused store half
     */
    OneRead readWithStore(PackRoot root) {
        // #319: normalize, not toRealPath: a linked root keeps its configured name (#284).
        Path directory = Path.of(root.locator()).toAbsolutePath().normalize();
        String name = root.name();
        if (!Files.isDirectory(directory)) {
            return unreadableRoot(
                    root, "pack " + name + " is not a directory", storeRead(directory, name, PackFileSet.Walk.NONE));
        }
        try {
            return readDirectory(root, directory, name);
        } catch (CharterRefusal refusal) {
            if (refusal.code() != RefusalCode.PACK_UNREADABLE) {
                throw refusal;
            }
            String detail = String.valueOf(refusal.getMessage());
            return unreadableRoot(
                    root,
                    detail,
                    new StoreRead(
                            new Assembly.Refused(List.of(), RefusalCode.PACK_UNREADABLE, detail, false),
                            new PackLayout(Map.of()),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            false));
        }
    }

    /**
     * The read's DSO half ({@link StoreRead}) over this reader's format, bindings and companion rules, assembled under
     * the root's name; a name that is not a pack id is refused, and {@link #readFaults} states it ({@link
     * StoreRead#ofNamed}).
     */
    private StoreRead storeRead(Path directory, String name, PackFileSet.Walk walk) {
        return StoreRead.ofNamed(directory, name, walk, strategy, bindings, companionRules);
    }

    /** The read of a root that cannot be read: no fact, an empty census, and the one fault at {@value #ROOT}. */
    private static OneRead unreadableRoot(PackRoot root, String detail, StoreRead store) {
        PackContents nothing = new PackContents(
                root,
                new FileRead.Absent<>(PackFileSet.PACK_YAML),
                List.of(),
                new FileRead.Absent<>(PackFileSet.FRAGMENT),
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of(),
                List.of(),
                store.assembly());
        return new OneRead(
                new TreeRead(
                        nothing,
                        TreeCensus.empty(),
                        List.of(Findings.at(StorageCodes.PACK_UNREADABLE, ROOT, List.of(), detail))),
                store);
    }

    private OneRead readDirectory(PackRoot root, Path directory, String name) {
        FileRead<PackDescriptor> descriptor = new FileRead.Absent<>(PackFileSet.PACK_YAML);
        Path packYaml = directory.resolve(PackFileSet.PACK_YAML);
        if (PackFileSet.unlinkedFile(directory, PackFileSet.PACK_YAML)) {
            descriptor = read(packYaml, PackFileSet.PACK_YAML, name, PackDescriptorReader::read);
        }
        List<FileRead<RawGraph>> shards = new ArrayList<>();
        for (Path shard : shards(directory, name)) {
            String file = directory.relativize(shard).toString();
            shards.add(read(shard, file, name, text -> shard(text, file)));
        }
        FileRead<RawGraph> fragment = new FileRead.Absent<>(PackFileSet.FRAGMENT);
        Path fragmentYaml = directory.resolve("drg").resolve("fragment.yaml");
        if (PackFileSet.unlinkedFile(directory, PackFileSet.FRAGMENT)) {
            fragment = read(fragmentYaml, PackFileSet.FRAGMENT, name, FileSystemPackInspector::fragment);
        }
        // One walk of the tree: the census and the DSO half read the files it lists (#554, T028).
        PackFileSet.Walk walk = walker.apply(directory, name);
        StoreRead store = storeRead(directory, name, walk);
        FullRead full = new FullRead(
                directory, name, shards.stream().map(FileRead::file).toList(), table, unnamedPolicy(descriptor), walk);
        full.readAll();
        PackContents contents = new PackContents(
                root,
                descriptor,
                shards,
                fragment,
                full.artifacts,
                full.assets,
                full.stepEnhancementFacts(edgesOf(shards, fragment, ownName(descriptor, name))),
                full.promptSlots,
                full.missionTypes,
                full.routingCatalogs,
                store.assembly());
        SortedMap<String, TreeCensus.Companion> companions =
                CensusEntries.companions(directory, full.listing(), bindings, companionRules, strategy);
        TreeCensus census = new TreeCensus(
                full.files,
                full.notHeld,
                manifest(directory, full.manifestRead, store),
                reservedShard(directory),
                CensusEntries.of(packDirectoryName(directory), walk, table, companions),
                List.copyOf(companions.values()));
        return new OneRead(new TreeRead(contents, census, readFaults(name, descriptor, shards, fragment, full)), store);
    }

    /**
     * Whether {@code policy.yaml} is a file the descriptor does not name (#554, open case 9): the load reads the policy
     * file only as the one {@code mutability_policy} names (ADR-0063), so an unnamed one is not held. When the
     * descriptor cannot be read, which file it names is unknown, and the file is held and judged.
     */
    private static boolean unnamedPolicy(FileRead<PackDescriptor> descriptor) {
        return descriptor instanceof FileRead.Read<PackDescriptor> read && unnamedPolicy(read.value());
    }

    /**
     * Whether {@code policy.yaml} is a file this descriptor does not name (#554, open case 9).
     *
     * @param descriptor the pack's descriptor
     * @return true when its {@code mutability_policy} names no {@code policy.yaml}
     */
    private static boolean unnamedPolicy(PackDescriptor descriptor) {
        return !descriptor
                .paths()
                .mutabilityPolicy()
                .filter(PolicyYaml.FILE::equals)
                .isPresent();
    }

    /**
     * The read faults, in the reader's order: the name's, when the pack is not named by a pack id (#583, D5), the
     * descriptor's (its read, or its format prechecks, S2), each root
     * shard's, the fragment's, every other file's, then each contained asset's MIME precheck (S2). Each is worded once,
     * here ({@link Wording}), with the code its owner builds.
     */
    private static List<Reason> readFaults(
            String name,
            FileRead<PackDescriptor> descriptor,
            List<FileRead<RawGraph>> shards,
            FileRead<RawGraph> fragment,
            FullRead full) {
        List<Reason> faults = new ArrayList<>();
        if (!PackIdentifier.isPackId(name)) {
            faults.add(Findings.at(StorageCodes.PACK_ID_INVALID, ROOT, List.of(), StoreRead.notAPackId(name)));
        }
        switch (descriptor) {
            case FileRead.Unreadable<PackDescriptor> bad -> faults.addAll(Wording.unreadable(name, bad));
            case FileRead.Read<PackDescriptor> good ->
                DescriptorChecks.faults(name, good.value()).forEach(fault -> faults.add(precheck(fault)));
            case FileRead.Absent<PackDescriptor> absent -> {}
        }
        List<FileRead<RawGraph>> documents = new ArrayList<>(shards);
        documents.add(fragment);
        for (FileRead<RawGraph> document : documents) {
            if (document instanceof FileRead.Unreadable<RawGraph> bad) {
                faults.addAll(Wording.unreadable(name, bad));
            }
        }
        full.unreadable.forEach(bad -> faults.addAll(Wording.unreadable(name, bad)));
        for (AssetFacts asset : full.assets) {
            mimeProblem(asset)
                    .ifPresent(detail ->
                            faults.add(Findings.at(StorageCodes.ASSET_MIME_INVALID, asset.file(), List.of(), detail)));
        }
        return faults;
    }

    /**
     * A descriptor format fault as the reader states it (S2, FR-017): a precheck at {@code pack.yaml}, under the code
     * the storage edge builds for the value it is about.
     */
    private static Reason precheck(DescriptorChecks.Fault fault) {
        ReasonCode code = switch (fault.check()) {
            case REQUIRES_CORE -> StorageCodes.REQUIRES_CORE_INVALID;
            case PACK_VERSION -> StorageCodes.PACK_VERSION_INVALID;
            case DEPENDENCY_RANGE -> StorageCodes.DEPENDENCY_RANGE_INVALID;
        };
        return Findings.at(code, PackFileSet.PACK_YAML, fault.place(), fault.detail());
    }

    /**
     * The MIME precheck of one asset (S2, R2 row 14): a type that is not {@code type/subtype}, or that differs from the
     * one its extension implies. An empty path or type is the schema's business, and a path that escapes the assets
     * root is the escape rule's, so neither is prechecked.
     */
    static Optional<String> mimeProblem(AssetFacts asset) {
        String mime = asset.mime();
        if (asset.path().isEmpty()
                || mime.isEmpty()
                || !asset.containedInAssetsRoot()
                || PackRelativePaths.leavesRoot(asset.path())) {
            return Optional.empty();
        }
        if (!MediaTypes.wellFormed(mime)) {
            return Optional.of(asset.file() + ": asset mime '" + mime + "' is not a well-formed 'type/subtype' value");
        }
        return AssetMimeTable.expected(asset.path())
                .filter(expected -> !expected.type().equals(mime))
                .map(expected -> asset.file() + ": asset mime '" + mime + "' does not match the '"
                        + expected.extension() + "' extension (" + expected.type() + ")");
    }

    /**
     * The manifest's facts, read without following a link: whether anything stands at its path and whether it is a
     * regular file; the lists it declares, read by the store's own manifest reader when the read found it valid; and
     * the lists derived from what the pack holds ({@link PackManifestDerivation}, T024), so the membership rule compares
     * two facts. A pack the store cannot load has no derived lists.
     */
    private TreeCensus.Manifest manifest(Path directory, boolean readable, StoreRead store) {
        Path file = directory.resolve(PackFileSet.MANIFEST);
        boolean present = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        if (!present) {
            return TreeCensus.Manifest.ABSENT;
        }
        boolean regular = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
        Optional<TreeCensus.Entries> listed = Optional.empty();
        if (readable
                && FilesystemPackStore.readText(file)
                                .map(text -> strategy.readManifest(text, PackFileSet.MANIFEST))
                                .orElse(null)
                        instanceof FileRead.Read<PackManifestDso> good) {
            listed = Optional.of(ManifestMembership.entries(good.value()));
        }
        Optional<TreeCensus.Entries> derived = Optional.empty();
        if (listed.isPresent() && store.assembly() instanceof Assembly.Assembled held) {
            // From the same read's assembly (T024): nothing is read again.
            derived = Optional.of(ManifestMembership.entries(
                    PackManifestDerivation.derived(PackAssembly.build(held), store.layout())));
        }
        return new TreeCensus.Manifest(present, regular, listed, derived);
    }

    /** What stands at the reserved shard's path, read without following a link (C-4). */
    private static TreeCensus.ReservedShard reservedShard(Path directory) {
        Path shard = directory.resolve(RelationshipShard.SHARD_PATH);
        if (!Files.isRegularFile(shard, LinkOption.NOFOLLOW_LINKS)) {
            return TreeCensus.ReservedShard.ABSENT;
        }
        return FilesystemPackStore.readText(shard)
                        .filter(RelationshipShard::isWrittenByRegistration)
                        .isPresent()
                ? TreeCensus.ReservedShard.STAMPED
                : TreeCensus.ReservedShard.UNSTAMPED;
    }

    /**
     * The files of a pack directory the store does not hold, as the census lists them: each regular file that is not
     * itself a link, in {@link String} order, that the table excludes or no rule models, unless a held artifact claims
     * it as a companion (a skill body); a {@code policy.yaml} the descriptor does not name; and a file a rule maps that
     * nothing names, a companion no artifact names, but not an orphan prompt fragment, which is refused (open case 9). A retired
     * file is refused by name, never listed (WP02 review L2). The census lists what the store does not hold through
     * here, and the store's load takes the census's list ({@code WholePackStore#load}), so "held" has one owner (#554,
     * S3).
     *
     * @param directory the pack directory
     * @param walk the pack's walk: its files that are not themselves links are listed, and its listing is digested
     * @param table the file-kinds table
     * @param unnamedPolicy whether {@code policy.yaml} is a file the descriptor does not name
     * @return the files not held, each with its kind and reason
     */
    private static List<NotHeldFile> notHeld(
            Path directory, PackFileSet.Walk walk, PackFileKind table, boolean unnamedPolicy) {
        Path last = directory.toAbsolutePath().normalize().getFileName();
        String prefix = (last == null ? "pack" : last.toString()) + "/";
        Set<String> held = Set.copyOf(PackFileSet.digested(directory, table, walk));
        List<NotHeldFile> out = new ArrayList<>();
        for (String file : walk.regular()) {
            if (unnamedPolicy && PolicyYaml.FILE.equals(file)) {
                out.add(NotHeldFile.unnamedPolicy(file));
                continue;
            }
            if (held.contains(file)) {
                continue;
            }
            switch (table.classify(prefix + file)) {
                case PackFileKind.Excluded excluded -> out.add(NotHeldFile.excluded(file, excluded.reason()));
                case PackFileKind.Unclassified unclassified -> out.add(NotHeldFile.unmodelled(file));
                case PackFileKind.Mapped mapped -> {
                    // A mapped file nothing names is not held (open case 9); an orphan prompt fragment is refused.
                    if (!StepEnhancementBinding.FRAGMENT_SCHEMA.equals(mapped.kind())) {
                        out.add(NotHeldFile.unnamedCompanion(file));
                    }
                }
                case PackFileKind.Retired retired -> {}
            }
        }
        return out;
    }

    /** The step enhancement edges of the pack's graph documents that were read: the root shards, then the fragment. */
    private static List<StepEnhancementFacts.Edge> edgesOf(
            List<FileRead<RawGraph>> shards, FileRead<RawGraph> fragment, String packName) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(shards);
        documents.add(fragment);
        List<StepEnhancementFacts.Edge> edges = new ArrayList<>();
        for (FileRead<RawGraph> document : documents) {
            if (document instanceof FileRead.Read<RawGraph> read) {
                read.value()
                        .edges()
                        .forEach(edge -> stepEnhancementEdge(edge.relation(), edge.source(), edge.target(), packName)
                                .ifPresent(edges::add));
            }
        }
        return edges;
    }

    /** The name composition knows the pack by: the descriptor's when it was read, else the configured name. */
    private static String ownName(FileRead<PackDescriptor> descriptor, String configured) {
        return descriptor instanceof FileRead.Read<PackDescriptor> read
                ? read.value().name()
                : configured;
    }

    /**
     * The edge fact of a graph edge whose source is a step enhancement of the pack: an {@code enhances} or an
     * {@code overrides} edge from {@code step_enhancement:<id>}, written bare or qualified with the pack's own name
     * ({@code <pack>::step_enhancement:<id>}; composition accepts the pack itself in the closure and places the edge
     * at the pack's own enhancement). A source qualified by another pack names that pack's enhancement, as
     * {@code PackCompositionResolver.holderOf} resolves it, so it is no edge of this pack's enhancements. The target
     * is split from its pack qualifier here, once, for the validate-mode facts and the load alike. Any other edge is
     * no fact.
     *
     * @param relation the relation as written
     * @param source the source endpoint as written
     * @param target the target endpoint as written
     * @param packName the name composition knows this pack by
     * @return the edge fact; empty for an edge the step enhancement rules do not judge
     */
    static Optional<StepEnhancementFacts.Edge> stepEnhancementEdge(
            String relation, String source, String target, String packName) {
        Optional<RelationType> type = RelationType.fromToken(relation)
                .filter(found -> found == RelationType.ENHANCES || found == RelationType.OVERRIDES);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        PackQualifiedReference origin;
        try {
            origin = PackQualifiedReference.parse(source);
        } catch (IllegalArgumentException malformed) {
            // Not a reference: the graph document is judged where it is read.
            return Optional.empty();
        }
        if (origin.packName().filter(qualifier -> !qualifier.equals(packName)).isPresent()
                || !origin.urn().startsWith(NodeKind.STEP_ENHANCEMENT.token() + ":")) {
            return Optional.empty();
        }
        String bare = origin.urn();
        try {
            PackQualifiedReference reference = PackQualifiedReference.parse(target);
            return Optional.of(new StepEnhancementFacts.Edge(type.get(), bare, reference.urn(), reference.packName()));
        } catch (IllegalArgumentException malformed) {
            // Not a reference: the graph document is judged where it is read; the rule sees a target that is no step.
            // A qualifier that is no pack id (#582) keeps the edge rules from judging the pack (GraphRules.graphsRead).
            return Optional.of(
                    new StepEnhancementFacts.Edge(type.get(), bare, target.replace("::", ":"), Optional.empty()));
        }
    }

    /**
     * The type directory of a step enhancement file, the {@code <type>} of {@code missions/<type>/enhancements/}.
     *
     * @param file the pack-relative path of the enhancement
     * @return the second segment of the path; empty when the path has fewer segments
     */
    static String typeDirectory(String file) {
        String[] segments = file.split("/", -1);
        return segments.length > 1 ? segments[1] : "";
    }

    /**
     * The facts of one companion the store or the inspector read: its canonical text is the written text of the parsed
     * document (A5), so load, validate mode and the read judge one text.
     *
     * @param file the companion's pack-relative path
     * @param document the parsed companion
     * @return the fact
     */
    static StepEnhancementFacts.Companion companionFact(String file, MarkdownDocument document) {
        boolean blank = document.title().isEmpty()
                && document.preamble().isBlank()
                && document.sections().isEmpty();
        return StepEnhancementFacts.Companion.read(
                file, document.frontmatter().isPresent(), blank, MarkdownCodec.write(document));
    }

    // ---- the full scope ------------------------------------------------------------------------------------------

    /**
     * Whether validate mode reads a mapped file as YAML: a {@code yaml} file with a schema. A payload (a
     * {@code bytes} file, no schema) has nothing to validate: it is listed, not read. A {@code markdown} or
     * {@code text} file is refused loudly, never read as YAML. (This walk lists {@code *.yaml} files only; a Markdown
     * companion is read by its own walk, as Markdown.)
     *
     * @param mapped the file's classification
     * @param file the file, pack-relative, named in the failure
     * @return whether the file is read and validated as YAML
     * @throws IllegalStateException for a {@code markdown} file
     */
    static boolean readsAsYaml(PackFileKind.Mapped mapped, String file) {
        return switch (mapped.contentForm()) {
            case YAML -> mapped.schema().isPresent();
            case BYTES -> false;
            case MARKDOWN, TEXT -> throw PackFileSet.notStored(file, mapped.contentForm());
        };
    }

    /**
     * The name of a pack directory as the file-kinds globs see it: the directory that holds it is their root.
     *
     * @param directory the pack directory
     * @return its last segment, or {@code pack} for a root with none
     */
    private static String packDirectoryName(Path directory) {
        Path last = directory.toAbsolutePath().normalize().getFileName();
        return last == null ? "pack" : last.toString();
    }

    /** The state of one full read: every YAML file of the pack, in path order, and what they yielded. */
    private static final class FullRead {
        private final Path directory;
        private final String name;
        private final Set<String> loadPath = new HashSet<>();
        final List<ClassifiedFile> files = new ArrayList<>();
        final List<FileRead.Unreadable<?>> unreadable = new ArrayList<>();
        final List<MissionTypeFacts> missionTypes = new ArrayList<>();
        final List<RoutingCatalogFacts> routingCatalogs = new ArrayList<>();
        final List<ArtifactFacts> artifacts = new ArrayList<>();
        final List<AssetFacts> assets = new ArrayList<>();
        private final List<StepEnhancementFacts.Enhancement> enhancements = new ArrayList<>();
        private final List<StepEnhancementFacts.Companion> companions = new ArrayList<>();
        final List<PackContents.PromptSlotFacts> promptSlots = new ArrayList<>();
        final List<NotHeldFile> notHeld = new ArrayList<>();
        private final Set<String> fragmentFiles = new HashSet<>();
        private final Set<String> unparsedEnhancements = new HashSet<>();
        private final Set<String> assetIds = new HashSet<>();
        private final List<SidecarPayloads.Sidecar> sidecars = new ArrayList<>();
        private final PackFileKind table;
        private final boolean unnamedPolicy;
        private final PackFileSet.Walk walk;

        /** Whether the read found the manifest valid, so the census reads its lists. */
        boolean manifestRead;

        FullRead(
                Path directory,
                String name,
                List<String> shards,
                PackFileKind table,
                boolean unnamedPolicy,
                PackFileSet.Walk walk) {
            this.directory = directory;
            this.name = name;
            this.table = table;
            this.unnamedPolicy = unnamedPolicy;
            this.walk = walk;
            loadPath.add(PackFileSet.PACK_YAML);
            loadPath.add(PackFileSet.FRAGMENT);
            loadPath.addAll(shards);
        }

        /** The store's listing of the walk, once {@link #readAll} made it. */
        private PackFileSet.@Nullable Listing listing;

        /** The store's listing of the walk, which {@link #readAll} made. */
        PackFileSet.Listing listing() {
            return Objects.requireNonNull(listing, "the listing is made by readAll");
        }

        void readAll() {
            // The classification globs are relative to the directory that holds the pack directories.
            String prefix = packDirectoryName(directory) + "/";
            readYamlFiles(prefix);
            readMarkdownFiles(prefix);
            PackFileSet.Listing listing = PackFileSet.listing(directory, table, walk);
            this.listing = listing;
            listPayloads(listing, prefix);
            refuseSidecarsWithoutPayload(listing);
            listing.retired()
                    .forEach((file, reason) -> unreadable.add(new FileRead.Unreadable<>(
                            file, ParseFailure.refused(file, reason, StorageCodes.RETIRED), List.of())));
            notHeld.addAll(FileSystemPackInspector.notHeld(directory, walk, table, unnamedPolicy));
            // An unnamed file, a policy file or a companion nothing names though a rule maps it, is listed once, as not
            // held, never also as classified (open case 9, WP05 review); an excluded file is listed with its rule both
            // ways, as validate mode shows it.
            Set<String> unnamed = new HashSet<>();
            notHeld.stream()
                    .filter(file -> file.kind() == NotHeldFile.Kind.UNNAMED)
                    .forEach(file -> unnamed.add(file.path()));
            files.removeIf(classified -> unnamed.contains(classified.file()));
        }

        /** Classifies every YAML file of the pack, and reads each mapped one the load path has not read. */
        private void readYamlFiles(String prefix) {
            for (Path path : packFiles(directory, walk, ".yaml")) {
                String file = slashed(directory.relativize(path));
                switch (table.classify(prefix + file)) {
                    case PackFileKind.Excluded excluded ->
                        files.add(new ClassifiedFile(file, Optional.empty(), Optional.of(excluded.exclusionClass())));
                    case PackFileKind.Mapped mapped -> {
                        files.add(new ClassifiedFile(file, Optional.of(mapped.kind()), Optional.empty()));
                        // A policy file the descriptor does not name is not held, so nothing in it is judged (open case
                        // 9).
                        if (readsAsYaml(mapped, file)
                                && !loadPath.contains(file)
                                && !(unnamedPolicy && PolicyYaml.FILE.equals(file))) {
                            readMapped(path, file, mapped.kind());
                        }
                    }
                    // Reported by readAll from the store's listing, with the retired Markdown files.
                    case PackFileKind.Retired retired -> {}
                    case PackFileKind.Unclassified unclassified -> {}
                }
            }
        }

        /** Lists the excluded Markdown files, and reads the companions and documents. */
        private void readMarkdownFiles(String prefix) {
            Set<String> held = new HashSet<>(PackFileSet.digested(directory, table, walk));
            for (Path path : packFiles(directory, walk, MARKDOWN)) {
                String file = slashed(directory.relativize(path));
                PackFileKind.Classification classification = table.classify(prefix + file);
                if (classification instanceof PackFileKind.Excluded excluded) {
                    // Excluded Markdown (an exclusions rule, or the ignored fallback, #451) is listed as excluded
                    // YAML is: never read, never a finding. A file a bound kind claims as its companion (a skill
                    // body) is held with its owner, so it is not listed; the digest knows which files are held.
                    if (!held.contains(file)) {
                        files.add(new ClassifiedFile(file, Optional.empty(), Optional.of(excluded.exclusionClass())));
                    }
                    continue;
                }
                if (!(classification instanceof PackFileKind.Mapped mapped)
                        || mapped.contentForm() != ContentForm.MARKDOWN) {
                    continue;
                }
                if (KindBindings.isCompanionStructure(mapped.kind())) {
                    // A companion no artifact names is not held, so nothing in it is judged (open case 9); a prompt
                    // fragment is still read, as the orphan rule judges it.
                    if (held.contains(file) || StepEnhancementBinding.FRAGMENT_SCHEMA.equals(mapped.kind())) {
                        readCompanion(path, file, mapped.kind());
                    }
                } else if (PackDocumentMarkdown.KIND.equals(mapped.kind())) {
                    readDocument(path, file);
                } else {
                    // A Markdown kind with no store path must fail loud in validate mode too, as the store's
                    // listing walk does (PackFileSet#loaded) — never silently skipped. No rule reaches
                    // this today; it keeps load and validate symmetric by construction.
                    throw PackFileSet.notStored(file, mapped.contentForm());
                }
            }
        }

        /**
         * Lists every payload and template the store's listing holds with its kind, whatever its extension (#251): the
         * YAML walk has listed the YAML-named ones. None is read.
         */
        private void listPayloads(PackFileSet.Listing listing, String prefix) {
            Set<String> listed = new HashSet<>();
            files.forEach(classified -> listed.add(classified.file()));
            for (String file : concat(listing.assetFiles(), listing.templates())) {
                if (!listed.contains(file) && table.classify(prefix + file) instanceof PackFileKind.Mapped mapped) {
                    files.add(new ClassifiedFile(file, Optional.of(mapped.kind()), Optional.empty()));
                }
            }
            files.sort(Comparator.comparing(ClassifiedFile::file, FileSystemPackInspector::comparePaths));
        }

        /**
         * The store's payload rule ({@link SidecarPayloads}, ADR-0040 decision 6), so validate mode and the load refuse
         * the same sidecars in the same words (#251): over the schema-valid sidecars in the store's order, against the
         * asset files the store holds. An asset file is held when the store's read holds it
         * ({@link FilesystemPackStore#readPayload(Path, String, java.util.function.Consumer)}); one that leaves the pack
         * or cannot be read is unreadable with the load's reason, and names no payload. A refused sidecar is unreadable
         * with the load's refusal; its facts stand, so the asset rules still judge it.
         */
        private void refuseSidecarsWithoutPayload(PackFileSet.Listing listing) {
            List<String> held = new ArrayList<>();
            for (String file : listing.assetFiles()) {
                FilesystemPackStore.readPayload(
                                directory,
                                file,
                                reason -> unreadable.add(new FileRead.Unreadable<>(
                                        file, ParseFailure.refused(file, reason, StorageCodes.UNREADABLE), List.of())))
                        .ifPresent(bytes -> held.add(file.substring(PackFileSet.ASSETS.length())));
            }
            sidecars.sort(Comparator.comparing(SidecarPayloads.Sidecar::file));
            for (SidecarPayloads.Refused refused : SidecarPayloads.refused(sidecars, held)) {
                unreadable.add(new FileRead.Unreadable<>(
                        refused.file(),
                        ParseFailure.refused(refused.file(), refused.reason(), PackCodes.ASSET),
                        List.of()));
            }
        }

        private static List<String> concat(List<String> first, List<String> second) {
            List<String> both = new ArrayList<>(first);
            both.addAll(second);
            return both;
        }

        /** The pack document (#260), read as the store's load reads it, so the two refuse the same {@code README.md}. */
        private void readDocument(Path path, String file) {
            if (read(path, file, name, PackDocumentMarkdown::read) instanceof FileRead.Unreadable<?> bad) {
                unreadable.add(bad);
            }
        }

        private void readCompanion(Path path, String file, String structure) {
            FileRead<Object> outcome = read(path, file, name, text -> companion(text, file, structure));
            if (outcome instanceof FileRead.Unreadable<?> bad) {
                unreadable.add(coded(bad, companionCode(structure)));
            }
            if (StepEnhancementBinding.FRAGMENT_SCHEMA.equals(structure)) {
                fragmentFact(file, outcome);
            }
            if (MissionTypeBinding.PROMPT_SCHEMA.equals(structure)
                    && outcome instanceof FileRead.Read<Object> good
                    && good.value() instanceof MarkdownDocument document) {
                promptSlots.add(
                        new PackContents.PromptSlotFacts(file, PromptSlots.parse(MarkdownCodec.write(document))));
            }
        }

        /**
         * A prompt fragment as a fact. One the read refused is marked unreadable, so no rule judges it again, and is
         * no orphan candidate: its own fault already refuses it, at the fragment.
         */
        private void fragmentFact(String file, FileRead<Object> outcome) {
            if (outcome instanceof FileRead.Read<Object> good && good.value() instanceof MarkdownDocument document) {
                companions.add(companionFact(file, document));
                fragmentFiles.add(file);
            } else {
                companions.add(StepEnhancementFacts.Companion.unreadable(file));
            }
        }

        private void readMapped(Path path, String file, String kind) {
            FileRead<Object> read = read(path, file, name, text -> parseAndValidate(text, file, kind));
            switch (read) {
                case FileRead.Unreadable<Object> bad -> {
                    unreadable.add(bad);
                    if (StepEnhancementBinding.INSTANCE.schemaName().equals(kind)) {
                        unparsedEnhancements.add(file);
                    }
                }
                case FileRead.Read<Object> good -> {
                    if (PackFileSet.MANIFEST.equals(file)) {
                        manifestRead = true;
                    }
                    factsOf(file, kind, good.value());
                    Optional<String> refused = MissionTypeChecks.refusal(directory, file, kind, good.value(), table);
                    refused.ifPresent(refusal -> unreadable.add(new FileRead.Unreadable<>(
                            file, ParseFailure.refused(file, refusal, PackCodes.ARTIFACT_SCHEMA_ERROR), List.of())));
                    // The load holds no mission type its checks refuse, so neither do the step-link facts.
                    if (refused.isEmpty()) {
                        missionTypeFactsOf(file, kind, good.value());
                    }
                    routingCatalogFactsOf(file, kind, good.value());
                }
                case FileRead.Absent<Object> absent -> {}
            }
        }

        private void factsOf(String file, String kind, Object document) {
            if (!ArtifactKinds.KINDS.contains(kind) || !(document instanceof Map<?, ?> map)) {
                return;
            }
            if (StepEnhancementBinding.INSTANCE.schemaName().equals(kind) && map.get("id") instanceof String id) {
                enhancements.add(new StepEnhancementFacts.Enhancement(file, typeDirectory(file), id, entriesOf(map)));
            }
            if (map.get("id") instanceof String id && !id.isEmpty()) {
                artifacts.add(new ArtifactFacts(file, kind.replace('-', '_'), id));
                if ("asset".equals(kind) && map.get("path") instanceof String path) {
                    sidecars.add(new SidecarPayloads.Sidecar(file, id, path));
                }
                if ("asset".equals(kind) && assetIds.add(id)) {
                    assets.add(assetFacts(file, id, map));
                }
            }
        }

        /**
         * The facts of a mission type the load holds: its id, the ids of its steps and on-demand steps, and the substeps
         * of each.
         */
        private void missionTypeFactsOf(String file, String kind, Object document) {
            if ("mission-type".equals(kind)
                    && document instanceof Map<?, ?> map
                    && map.get("id") instanceof String id) {
                List<String> steps = new ArrayList<>(idsOf(map.get("steps")));
                steps.addAll(idsOf(map.get("on_demand_steps")));
                List<MissionTypeFacts.SubstepFacts> substeps = new ArrayList<>(substepsOf("steps", map.get("steps")));
                substeps.addAll(substepsOf("on_demand_steps", map.get("on_demand_steps")));
                missionTypes.add(new MissionTypeFacts(file, id, steps, substeps));
            }
        }

        /** The substeps of each step of a schema-valid list, in written order. */
        private static List<MissionTypeFacts.SubstepFacts> substepsOf(String list, @Nullable Object steps) {
            List<MissionTypeFacts.SubstepFacts> out = new ArrayList<>();
            if (!(steps instanceof List<?> items)) {
                return out;
            }
            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof Map<?, ?> step && step.get("id") instanceof String stepId)) {
                    continue;
                }
                if (!(step.get("substeps") instanceof List<?> substeps)) {
                    continue;
                }
                for (int j = 0; j < substeps.size(); j++) {
                    if (substeps.get(j) instanceof Map<?, ?> substep && substep.get("id") instanceof String substepId) {
                        out.add(new MissionTypeFacts.SubstepFacts(
                                list, i, stepId, j, substepId, delegationOf(substep.get("delegates_to"))));
                    }
                }
            }
            return out;
        }

        private static Optional<MissionTypeFacts.Delegation> delegationOf(@Nullable Object written) {
            if (!(written instanceof Map<?, ?> delegation && delegation.get("kind") instanceof String kind)) {
                return Optional.empty();
            }
            List<String> candidates = new ArrayList<>();
            if (delegation.get("candidates") instanceof List<?> listed) {
                listed.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(candidates::add);
            }
            return Optional.of(new MissionTypeFacts.Delegation(kind, candidates));
        }

        /** The entries of a step enhancement that passed its schema; none when the key is absent. */
        private static List<StepEnhancementFacts.Entry> entriesOf(Map<?, ?> enhancement) {
            List<StepEnhancementFacts.Entry> entries = new ArrayList<>();
            if (enhancement.get("prompt_fragments") instanceof List<?> listed) {
                for (Object item : listed) {
                    if (item instanceof Map<?, ?> entry
                            && entry.get("slot") instanceof String slot
                            && entry.get("path") instanceof String path) {
                        entries.add(new StepEnhancementFacts.Entry(slot, path));
                    }
                }
            }
            return entries;
        }

        /**
         * The facts the step enhancement rules judge. An enhancement the store would not hold joins
         * {@code unparsedEnhancementFiles}, so no fragment beside it is judged an orphan (A4): one that did not parse,
         * one with a companion that is missing, refused or named by an earlier enhancement, and the second file of a
         * repeated id. An enhancement that only has a rule fault is held and is never listed.
         */
        StepEnhancementFacts stepEnhancementFacts(List<StepEnhancementFacts.Edge> edges) {
            Set<String> unparsed = new HashSet<>(unparsedEnhancements);
            Map<String, StepEnhancementFacts.Companion> existing = new HashMap<>();
            companions.forEach(companion -> existing.put(companion.path(), companion));
            Set<String> ids = new HashSet<>();
            Set<String> claimed = new HashSet<>();
            List<StepEnhancementFacts.Enhancement> ordered = new ArrayList<>(enhancements);
            ordered.sort(Comparator.comparing(
                    StepEnhancementFacts.Enhancement::path, FileSystemPackInspector::comparePaths));
            for (StepEnhancementFacts.Enhancement enhancement : ordered) {
                boolean refused = !ids.add(enhancement.id());
                for (StepEnhancementFacts.Entry entry : enhancement.entries()) {
                    String companion = enhancement.companionPath(entry);
                    StepEnhancementFacts.Companion found = existing.get(companion);
                    if (found == null || found.unreadable() || !claimed.add(companion)) {
                        refused = true;
                    }
                }
                if (refused) {
                    unparsed.add(enhancement.path());
                }
            }
            return new StepEnhancementFacts(enhancements, companions, fragmentFiles, unparsed, edges);
        }

        private AssetFacts assetFacts(String file, String id, Map<?, ?> manifest) {
            // The schema has required both as non-empty strings; the first owner of an id is the only one core checks.
            String path = String.valueOf(manifest.get("path"));
            String mime = String.valueOf(manifest.get("mime"));
            return new AssetFacts(file, id, path, mime, containedInAssetsRoot(directory.resolve("assets"), path));
        }

        /**
         * The facts of a routing catalog that passed its schema (ADR-0080): its task type ids, the task type of each
         * model's fits and of each tier constraint, as written. {@code RoutingCatalogRules} judges them.
         */
        private void routingCatalogFactsOf(String file, String kind, Object document) {
            if ("routing-catalog".equals(kind)
                    && document instanceof Map<?, ?> map
                    && map.get("id") instanceof String id) {
                List<RoutingCatalogFacts.ModelFacts> models = new ArrayList<>();
                for (Object model : map.get("models") instanceof List<?> written ? written : List.of()) {
                    if (model instanceof Map<?, ?> m && m.get("id") instanceof String modelId) {
                        models.add(
                                new RoutingCatalogFacts.ModelFacts(modelId, valuesOf(m.get("task_fit"), "task_type")));
                    }
                }
                List<String> constraints = map.get("routing_policy") instanceof Map<?, ?> policy
                        ? valuesOf(policy.get("tier_constraints"), "task_type")
                        : List.of();
                routingCatalogs.add(
                        new RoutingCatalogFacts(file, id, idsOf(map.get("task_types")), models, constraints));
            }
        }

        /** The string a key holds in each mapping of a list, as written; an item without it is skipped. */
        private static List<String> valuesOf(@Nullable Object list, String key) {
            List<String> values = new ArrayList<>();
            if (list instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> m && m.get(key) instanceof String value) {
                        values.add(value);
                    }
                }
            }
            return values;
        }

        /** The {@code id} of each map of a list, in order; none for anything that is not a list. */
        private static List<String> idsOf(@Nullable Object list) {
            List<String> ids = new ArrayList<>();
            if (list instanceof List<?> steps) {
                for (Object step : steps) {
                    if (step instanceof Map<?, ?> s && s.get("id") instanceof String id) {
                        ids.add(id);
                    }
                }
            }
            return ids;
        }
    }

    /**
     * Reads a Markdown companion as the store's load does: parsed, validated against its structure schema, then held
     * to its binding's own rules. A Markdown refusal and a broken rule are refusals naming the line or the rule, under
     * the code the registration answers (#554, R2 row 19): {@code prompt_fragment_invalid} for a step enhancement's
     * prompt fragment, {@code companion} for any other companion. A structure breach is a schema violation.
     */
    private static FileRead<Object> companion(String text, String file, String structure) {
        ReasonCode code = companionCode(structure);
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownRefusal refusal ->
                new FileRead.Unreadable<>(file, ParseFailure.refused(file, refusal.message(), code), List.of());
            case MarkdownCodec.Parsed parsed -> {
                List<SchemaViolation> violations =
                        PackSchemas.bundled().validateMarkdown(structure, file, parsed.document());
                if (!violations.isEmpty()) {
                    yield new FileRead.Unreadable<>(file, null, violations);
                }
                try {
                    KindBindings.byCompanionStructure(structure)
                            .ifPresent(binding -> binding.checkCompanion(structure, parsed.document()));
                } catch (IllegalArgumentException broken) {
                    yield new FileRead.Unreadable<>(
                            file, ParseFailure.refused(file, String.valueOf(broken.getMessage()), code), List.of());
                }
                yield new FileRead.Read<>(file, parsed.document());
            }
        };
    }

    /** The code of a companion's own refusal, as the registration answers it (#554, R2 row 19). */
    private static ReasonCode companionCode(String structure) {
        return StepEnhancementBinding.FRAGMENT_SCHEMA.equals(structure)
                ? PackCodes.PROMPT_FRAGMENT_INVALID
                : StorageCodes.COMPANION;
    }

    /** A companion whose bytes are not text, refused under the companion's code like its other read faults. */
    private static FileRead.Unreadable<?> coded(FileRead.Unreadable<?> bad, ReasonCode code) {
        ParseFailure parse = bad.parse();
        if (parse == null || parse.code().isPresent()) {
            return bad;
        }
        return new FileRead.Unreadable<>(
                bad.file(), ParseFailure.refused(bad.file(), parse.firstLine(), code), bad.violations());
    }

    /** Parses and validates a mapped file that is not read for the load path. */
    private static FileRead<Object> parseAndValidate(String text, String file, String kind) {
        if ("graph-shard".equals(kind)) {
            // The graph reader adds the sibling facts the detail vocabulary needs; the graph itself is not kept.
            return switch (GraphDocumentReader.read(text, GraphDocumentReader.Shape.SHARD, file)) {
                case FileRead.Unreadable<RawGraph> bad ->
                    new FileRead.Unreadable<>(file, bad.parse(), bad.violations());
                case FileRead.Read<RawGraph> good -> new FileRead.Read<>(file, good.value());
                case FileRead.Absent<RawGraph> absent -> new FileRead.Absent<>(file);
            };
        }
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(kind, file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (PresetYaml.SCHEMA.equals(kind)
                && document instanceof Map<?, ?> preset
                && preset.get("id") instanceof String id
                && PresetFiles.stemMismatch(file, id).isPresent()) {
            // The store's stem rule, checked here too so validate mode and the load refuse the same files (ADR-0042):
            // the id must equal one value, the file stem, so it reads as a const violation at /id.
            return new FileRead.Unreadable<>(
                    file,
                    null,
                    List.of(new SchemaViolation(
                            file,
                            List.of("id"),
                            "const",
                            Optional.empty(),
                            Optional.of(id),
                            Map.of(
                                    Wording.REASON,
                                    PresetFiles.stemMismatch(file, id).orElseThrow()))));
        }
        List<SchemaViolation> broken = artifactRules(file, kind, document);
        if (!broken.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, broken);
        }
        if (PackManifestYaml.SCHEMA.equals(kind) && document instanceof Map<?, ?> manifest) {
            List<SchemaViolation> misplaced = misplacedPresetEntries(file, manifest);
            if (!misplaced.isEmpty()) {
                return new FileRead.Unreadable<>(file, null, misplaced);
            }
        }
        // An empty document passes no schema that asks for an object, so a document that passed is a value.
        return new FileRead.Read<>(file, Objects.requireNonNullElse(document, Map.of()));
    }

    /**
     * The store's rules of an artifact that its schema cannot state, in the order the store applies them: the stem
     * rule ({@link #misnamedArtifact}) first, and only when it holds, the rules of the kind ({@link #kindRules}).
     */
    private static List<SchemaViolation> artifactRules(String file, String kind, @Nullable Object document) {
        return misnamedArtifact(file, kind, document).map(List::of).orElseGet(() -> kindRules(file, kind, document));
    }

    /**
     * The store's stem rule for an artifact whose binding declares it ({@link FileStems}, a procedure), checked here
     * too so validate mode and the load refuse the same files: the id must equal one value, the file stem, so it reads
     * as a const violation at the id.
     */
    private static Optional<SchemaViolation> misnamedArtifact(String file, String kind, @Nullable Object document) {
        Optional<KindBinding<?, ?>> binding = KindBindings.bySchema(kind);
        if (binding.isEmpty()
                || !(document instanceof Map<?, ?> artifact)
                || !(artifact.get(binding.get().idKey()) instanceof String id)
                || FileStems.mismatch(binding.get(), file, id).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SchemaViolation(
                file,
                List.of(binding.get().idKey()),
                "const",
                Optional.empty(),
                Optional.of(id),
                Map.of(
                        Wording.REASON,
                        FileStems.mismatch(binding.get(), file, id).orElseThrow())));
    }

    /**
     * The rules of an artifact's kind that its schema cannot state ({@link YamlFormat#rules}: a repeated principle name,
     * a repeated term id), which the store's read applies, checked here too so validate mode and the load refuse the
     * same files at the same pointer.
     */
    private static List<SchemaViolation> kindRules(String file, String kind, @Nullable Object document) {
        Optional<KindBinding<?, ?>> binding = KindBindings.bySchema(kind);
        if (binding.isEmpty() || !(document instanceof Map<?, ?> artifact)) {
            return List.of();
        }
        return YamlFormats.of(binding.get()).rules(artifact, file);
    }

    /**
     * The store's rule that a manifest's preset entry names {@code presets/<id>.yaml} ({@link
     * PresetFiles#entryPathMismatch}), checked here too so validate mode and the load refuse the same files: the path
     * must equal one value, so each miss reads as a const violation at {@code /presets/<i>/path}.
     */
    private static List<SchemaViolation> misplacedPresetEntries(String file, Map<?, ?> manifest) {
        List<SchemaViolation> out = new ArrayList<>();
        if (manifest.get("presets") instanceof List<?> entries) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i) instanceof Map<?, ?> entry
                        && entry.get("id") instanceof String id
                        && entry.get("path") instanceof String path
                        && PresetFiles.entryPathMismatch(id, path).isPresent()) {
                    out.add(new SchemaViolation(
                            file,
                            List.of("presets", String.valueOf(i), "path"),
                            "const",
                            Optional.empty(),
                            Optional.of(path),
                            Map.of(
                                    Wording.REASON,
                                    PresetFiles.entryPathMismatch(id, path).orElseThrow())));
                }
            }
        }
        return out;
    }

    /**
     * Every regular file of the pack's walk whose name ends in {@code suffix}, in any letter case (the ignored fallback
     * takes NOTES.MD as it takes notes.md, #451), named under {@code directory}, in the order of Python's
     * {@code PurePath}: segment by segment, a directory before a sibling that extends its name. {@code PackFileSet}'s
     * listing, which the store and the fingerprint share, orders by {@link String} instead; this validate-mode order
     * mirrors core's pathlib (#115). The walk starts at the directory's real path, so a pack root that is a symbolic
     * link is entered (#284); a link below the root is not a regular file of the walk, and is never read through (D6).
     */
    private static List<Path> packFiles(Path directory, PackFileSet.Walk walk, String suffix) {
        return walk.regular().stream()
                .filter(file -> file.toLowerCase(Locale.ROOT).endsWith(suffix))
                .sorted(FileSystemPackInspector::comparePaths)
                .map(directory::resolve)
                .toList();
    }

    private static String slashed(Path relative) {
        return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
    }

    /** Segment by segment, each by code point: how pathlib orders paths, so a path is not sorted by its separators. */
    private static int comparePaths(String a, String b) {
        String[] left = a.split("/", -1);
        String[] right = b.split("/", -1);
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int order = Arrays.compare(
                    left[i].codePoints().toArray(), right[i].codePoints().toArray());
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    // ---- asset containment ---------------------------------------------------------------------------------------

    /**
     * Whether a declared asset path stays inside the pack's assets root, following symlinks, as core's
     * {@code resolve_relative_path_within_root} judges it from the resolved paths. The path is resolved the way
     * {@code Path.resolve(strict=False)} does: each existing component is resolved, a symlink is followed to where it
     * points (a dangling one too), and a component that does not exist is kept as written. A path that cannot be
     * resolved is an escape: a symlink loop, or more links than the kernel follows. A path with a NUL cannot be a name
     * in any directory: core raises {@code ValueError} for it, uncaught, so there is no verdict to match; here it is
     * not contained, which makes it an {@code asset_path_escape} finding and never an exception.
     *
     * <p>The absolute and {@code ..} forms, including the Windows ones, are judged by the domain from the raw path.
     *
     * @param assetsRoot the pack's {@code assets/} directory, which need not exist
     * @param declared the path as the manifest wrote it
     * @return {@code true} when the path resolves inside the resolved assets root
     */
    static boolean containedInAssetsRoot(Path assetsRoot, String declared) {
        try {
            Path resolvedRoot = resolve(assetsRoot);
            return resolve(assetsRoot.resolve(declared)).startsWith(resolvedRoot);
        } catch (IOException | InvalidPathException e) {
            return false;
        }
    }

    private static Path resolve(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Deque<String> pending = new ArrayDeque<>();
        absolute.forEach(part -> pending.addLast(part.toString()));
        Path current = Objects.requireNonNull(absolute.getRoot(), "an absolute path has a root");
        int links = 0;
        while (!pending.isEmpty()) {
            String part = pending.removeFirst();
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                Path parent = current.getParent();
                current = parent == null ? current : parent;
                continue;
            }
            Path next = current.resolve(part);
            if (!Files.isSymbolicLink(next)) {
                current = next;
                continue;
            }
            if (++links > MAX_LINKS) {
                throw new IOException("too many levels of symbolic links");
            }
            Path target = Files.readSymbolicLink(next);
            List<String> names = new ArrayList<>();
            target.forEach(name -> names.add(name.toString()));
            for (int i = names.size() - 1; i >= 0; i--) {
                pending.addFirst(names.get(i));
            }
            Path targetRoot = target.getRoot();
            if (targetRoot != null) {
                current = targetRoot;
            }
        }
        return current;
    }

    // ---- reading one file ----------------------------------------------------------------------------------------

    /**
     * Reads a file's bytes as UTF-8 and hands the text to {@code reader}. Bytes that are not UTF-8 are not YAML: the
     * file is {@link FileRead.Unreadable} with a parse failure, so its finding takes the code of its kind. The file is
     * opened without following a link (D6, {@link PackFileSet#noFollowBytes}): one the walk listed and that became a
     * link before the read refuses the pack, {@code pack_unreadable} in the link rule's words, and is never read.
     */
    private static <T> FileRead<T> read(Path path, String file, String packName, Function<String, FileRead<T>> reader) {
        byte[] bytes;
        try {
            bytes = PackFileSet.noFollowBytes(path);
        } catch (PackFileSet.LinkedFile linked) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, LinkRules.detail(packName + "/" + file), linked);
        } catch (IOException e) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, packName + "/" + file + " cannot be read", e);
        }
        return FilesystemPackStore.decodeUtf8(bytes)
                .map(reader)
                .orElseGet(() -> new FileRead.Unreadable<>(
                        file, new ParseFailure(file, FilesystemPackStore.NOT_UTF8), List.of()));
    }

    /**
     * A root shard, the reserved one included, read as the store reads it: a source shard under the reserved name is the
     * census's fact ({@link TreeCensus.ReservedShard#UNSTAMPED}) and the storage rule's fault, not an unreadable graph
     * (#554, C-4).
     */
    private static FileRead<RawGraph> shard(String text, String file) {
        return GraphDocumentReader.read(text, GraphDocumentReader.Shape.SHARD, file);
    }

    /**
     * The fragment, refused whole when a node's {@code body_path} leaves the pack root (#254), as the load refuses it
     * ({@link FilesystemPackStore#readGraphs}): its finding is the fragment's {@code drg_schema_error}, naming the first
     * such node, and its edges are not judged, as for any graph document that cannot be read.
     */
    private static FileRead<RawGraph> fragment(String text) {
        FileRead<RawGraph> read =
                GraphDocumentReader.read(text, GraphDocumentReader.Shape.FRAGMENT, PackFileSet.FRAGMENT);
        if (read instanceof FileRead.Read<RawGraph> good) {
            for (PackContents.RawNode node : good.value().nodes()) {
                Optional<String> escape = Paths.bodyPathEscape(node.ref(), node.bodyPath());
                if (escape.isPresent()) {
                    return new FileRead.Unreadable<>(
                            PackFileSet.FRAGMENT,
                            ParseFailure.refused(PackFileSet.FRAGMENT, escape.get(), PackCodes.DRG_SCHEMA_ERROR),
                            List.of());
                }
            }
        }
        return read;
    }

    private static List<Path> shards(Path directory, String name) {
        return PackFileSet.rootShards(directory, name);
    }
}
