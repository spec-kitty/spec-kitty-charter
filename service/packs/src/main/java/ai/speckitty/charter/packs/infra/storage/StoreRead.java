package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one read's DSO half (#554, S1, T025, T028): what the store's own reader reads of a pack tree, assembled once
 * through {@link PackAssembly} with the load's rules ({@link LoadChecks}), and mapped into the domain values the
 * aggregate needs ({@link Assembly}). The one reader runs it inside its walk, so the aggregate is built from the read
 * that was judged; the whole-pack load and write run it too, keeping the storage half ({@link #layout}, {@link #graphs},
 * {@link #manifest}) they write back. A plain class, not a bean ({@code THE_PACKS_LOAD_REACHES_NO_FRAMEWORK}).
 *
 * @param assembly the domain values: what the load holds and every fault it met, or why it refused the pack
 * @param layout each held artifact's pack-relative path; empty when the load refused the pack
 * @param graphs the graph documents read, the root shards by name then the fragment
 * @param manifest the manifest as read, when it is readable and names its presets' own files
 * @param graphRefusal the strict load's refusal of the first graph document that cannot be read
 *     ({@code <pack_id>/<file>: <reason>}), when one cannot
 */
record StoreRead(
        Assembly assembly,
        PackLayout layout,
        List<GraphDocument> graphs,
        Optional<PackManifestDso> manifest,
        Optional<String> graphRefusal) {

    private static final Pattern FRAGMENT_FILE =
            Pattern.compile("^missions/[^/]+/enhancements/[^/]+\\.prompt-fragment\\.md$");
    private static final String ENHANCEMENT_SUFFIX = ".step-enhancement.yaml";

    /**
     * Copies the lists.
     *
     * @param assembly the domain values
     * @param layout the layout
     * @param graphs the graph documents
     * @param manifest the manifest
     * @param graphRefusal the strict load's graph refusal
     */
    StoreRead {
        graphs = List.copyOf(graphs);
    }

    /**
     * Reads a pack directory's DSO half once.
     *
     * @param directory the pack directory
     * @param name the name the pack is known by: the refusals and the composition facts name it
     * @param walk the walk of the tree the one read makes; the store's listing and the untaken files come from it
     * @param strategy the serialization format
     * @param bindings the bindings to read with
     * @param companionRules the companion rules
     * @return the read; a refusal of the load is an {@link Assembly.Refused}, never thrown
     */
    static StoreRead of(
            Path directory,
            String name,
            PackFileSet.Walk walk,
            SerializationStrategy strategy,
            List<KindBinding<?, ?>> bindings,
            Companions.Rules companionRules) {
        Path packDir = directory.toAbsolutePath().normalize();
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            return refused(List.of(), RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory", false);
        }
        FilesystemPackStore store = new FilesystemPackStore(parent, strategy, companionRules);
        PackIdentifier key = new PackIdentifier(String.valueOf(packDir.getFileName()));
        FilesystemPackStore.GraphRead graphs;
        FilesystemPackStore.PackRead read;
        try {
            graphs = store.readGraphs(key);
            read = store.read(key, bindings, graphs, walk);
        } catch (CharterRefusal refusal) {
            return refused(
                    List.of(), refusal.code(), String.valueOf(refusal.getMessage()), descriptorFault(store, key));
        }
        PackIdentifier id = new PackIdentifier(read.descriptor().packId());
        Optional<String> graphRefusal = graphs.violations().stream()
                .findFirst()
                .map(first -> id.value() + "/" + first.file() + ": " + first.reason());
        Map<Urn, String> places = new HashMap<>();
        List<PackFinding> warnings = new ArrayList<>();
        Pack.Builder builder = LoadChecks.assembly(id, read, places, graphs, warnings);
        // The manifest's and the policy file's faults follow the assembly's; the composing read does not judge them.
        List<PackElementViolation> trailing = new ArrayList<>();
        Optional<PackManifestDso> manifest = manifest(packDir.resolve(PackFileSet.MANIFEST), strategy, trailing);
        Optional<String> named = read.descriptor().mutabilityPolicy();
        List<String> policyFault = new ArrayList<>();
        Optional<PolicyYaml.Named> policy = named.flatMap(file -> policy(packDir, file, trailing, policyFault));
        trailing.forEach(builder::violation);
        Pack pack;
        try {
            pack = builder.build();
        } catch (ConstraintBreachException breach) {
            return new StoreRead(
                    new Assembly.Refused(
                            graphs.violations(),
                            RefusalCode.VALIDATION_FAILED,
                            String.valueOf(breach.getMessage()),
                            false),
                    new PackLayout(Map.of()),
                    List.of(),
                    Optional.empty(),
                    graphRefusal);
        }
        List<Assembly.Placed> artifacts = new ArrayList<>();
        Map<Urn, String> digests = new HashMap<>();
        for (CharterArtifact artifact : pack.artifacts()) {
            String place = Objects.requireNonNull(places.get(artifact.urn()), "every held artifact has a place");
            artifacts.add(new Assembly.Placed(place, artifact));
            digests.put(artifact.urn(), digestOf(artifact, bindings, strategy));
        }
        Optional<PackCompositionFacts> facts = Optional.empty();
        Optional<String> factsRefusal = Optional.empty();
        if (graphs.violations().isEmpty()) {
            try {
                facts = Optional.of(CompositionFactsBuilder.build(
                        new PackIdentifier(name),
                        pack,
                        read,
                        graphs.documents(),
                        policy.map(PolicyYaml.Named::source).orElse(MutabilityPolicySource.empty())));
            } catch (CharterRefusal refusal) {
                factsRefusal = Optional.of(String.valueOf(refusal.getMessage()));
            }
        }
        Assembly.Assembled assembled = new Assembly.Assembled(
                id,
                pack.descriptor(),
                artifacts,
                pack.assetFiles(),
                pack.presets(),
                pack.document(),
                pack.violations(),
                pack.violations().size() - trailing.size(),
                graphs.violations(),
                warnings,
                untaken(packDir, walk.unlinked(), read, places, bindings, companionRules),
                digests,
                policy.map(PolicyYaml.Named::text),
                policyFault.stream().findFirst(),
                facts,
                factsRefusal);
        return new StoreRead(assembled, new PackLayout(places), graphs.documents(), manifest, graphRefusal);
    }

    private static StoreRead refused(
            List<PackElementViolation> graphFaults, RefusalCode code, String message, boolean atDescriptor) {
        return new StoreRead(
                new Assembly.Refused(graphFaults, code, message, atDescriptor),
                new PackLayout(Map.of()),
                List.of(),
                Optional.empty(),
                Optional.empty());
    }

    /** Whether the pack's descriptor is missing or unreadable, read as the store reads it. */
    private static boolean descriptorFault(FilesystemPackStore store, PackIdentifier key) {
        try {
            return store.descriptorFault(key).isPresent();
        } catch (CharterRefusal missing) {
            return false;
        }
    }

    /**
     * The manifest, when the pack holds a readable one whose preset entries name their own files; otherwise empty, and
     * an unreadable or misplaced manifest is a violation.
     */
    private static Optional<PackManifestDso> manifest(
            Path manifestFile, SerializationStrategy strategy, List<PackElementViolation> violations) {
        if (!Files.isRegularFile(manifestFile)) {
            return Optional.empty();
        }
        Optional<byte[]> bytes = FilesystemPackStore.readBytes(manifestFile);
        FileRead<PackManifestDso> read = bytes.flatMap(FilesystemPackStore::decodeUtf8)
                .map(text -> strategy.readManifest(text, PackFileSet.MANIFEST))
                .orElseGet(() -> new FileRead.Unreadable<>(
                        PackFileSet.MANIFEST,
                        new ParseFailure(PackFileSet.MANIFEST, FilesystemPackStore.notText(bytes)),
                        List.of()));
        switch (read) {
            case FileRead.Read<PackManifestDso> good -> {
                List<String> misplaced = misplacedPresetEntries(good.value());
                misplaced.forEach(reason -> violations.add(
                        new PackElementViolation(PackFileSet.MANIFEST, StorageCodes.ARTIFACT_SCHEMA_ERROR, reason)));
                return misplaced.isEmpty() ? Optional.of(good.value()) : Optional.empty();
            }
            case FileRead.Unreadable<PackManifestDso> bad -> violations.addAll(FilesystemPackStore.violationsOf(bad));
            case FileRead.Absent<PackManifestDso> absent -> {}
        }
        return Optional.empty();
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

    /**
     * The policy file the descriptor names (ADR-0063), read and checked as the compose reads it; a named file that is
     * missing or invalid is a violation, so the write refuses the pack instead of storing a descriptor that names a file
     * it does not hold.
     */
    private static Optional<PolicyYaml.Named> policy(
            Path packDir, String named, List<PackElementViolation> violations, List<String> fault) {
        try {
            return Optional.of(PolicyYaml.readNamed(packDir, named));
        } catch (PolicyYaml.Unreadable invalid) {
            fault.add(String.valueOf(invalid.getMessage()));
            violations.add(new PackElementViolation(
                    named, StorageCodes.UNREADABLE, "named by mutability_policy, the file " + invalid.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * The step enhancement files and prompt fragments the load did not take, in {@link String} order, from the walk's
     * file list: an enhancement the store refused or withheld, and a fragment no held enhancement names. A registration
     * looks for orphan fragments among them. Staging inside the single pull request (R4 b): the content rules judge the
     * orphans from the read's facts (WP07), and WP12 deletes this.
     */
    private static List<String> untaken(
            Path packDir,
            List<String> files,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            List<KindBinding<?, ?>> bindings,
            Companions.Rules companionRules) {
        Set<String> taken = new HashSet<>(places.values());
        for (KindBinding<?, ?> kind : bindings) {
            if (kind.hasCompanions()) {
                taken.addAll(companionFiles(packDir, kind, read, companionRules));
            }
        }
        String prefix = packDir.getFileName() + "/";
        return files.stream()
                .filter(file -> FRAGMENT_FILE.matcher(file).matches() || file.endsWith(ENHANCEMENT_SUFFIX))
                .filter(file -> !taken.contains(file))
                .filter(file -> companionRules.table().classify(prefix + file) instanceof PackFileKind.Mapped)
                .sorted()
                .toList();
    }

    private static <D> List<String> companionFiles(
            Path packDir, KindBinding<D, ?> kind, FilesystemPackStore.PackRead read, Companions.Rules companionRules) {
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
     * A digest of one file-backed artifact as the whole-pack write would write it, its companions included.
     *
     * @param artifact the artifact
     * @param bindings the bindings that write it
     * @param strategy the serialization format
     * @return the digest token
     */
    static String digestOf(CharterArtifact artifact, List<KindBinding<?, ?>> bindings, SerializationStrategy strategy) {
        List<byte[]> parts = new ArrayList<>();
        if (artifact instanceof Template template) {
            parts.add(TemplateBinding.INSTANCE.payloadOf(template).bytes());
        } else {
            KindBinding<?, ?> kind = bindings.stream()
                    .filter(binding -> binding.kind() == artifact.kind())
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalArgumentException("cannot digest a " + artifact.kind() + " artefact"));
            digestParts(kind, artifact, strategy, parts);
        }
        return PackRevision.computeOverBytes(parts).token();
    }

    private static <D> void digestParts(
            KindBinding<D, ?> kind, CharterArtifact artifact, SerializationStrategy strategy, List<byte[]> parts) {
        D dso = kind.dsoOf(artifact);
        parts.add(strategy.write(kind, dso).getBytes(StandardCharsets.UTF_8));
        for (KindBinding.Companion companion : kind.companions(dso)) {
            companion
                    .content()
                    .ifPresent(content -> parts.add(Companions.text(content).getBytes(StandardCharsets.UTF_8)));
        }
    }
}
