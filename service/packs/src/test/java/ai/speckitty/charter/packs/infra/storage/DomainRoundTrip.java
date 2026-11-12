package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Today's {@link WholePackRoundTrip}: read → domain → write → read back, through the public storage service only.
 *
 * <p>A {@link DomainRepositoryService} over the source directory loads the {@link Pack} aggregate (descriptor and every
 * kind bound in {@link KindBindings}, with every read violation); a second one over the target directory saves the
 * descriptor and each artefact; loading the target is the read-back. Graph shards go through
 * {@link GraphDocumentReader} → {@link PackPolicy#graph} → {@link GraphShardWriter}, keeping the source's
 * {@code generated_by}. Nothing here touches {@code Pack} or {@code FilesystemPackStore} directly (#189 owns them).
 *
 * <p>A new artefact is written as {@code <id>.<fileToken>.yaml} at the pack root, wherever its source lived, so source
 * and written files are paired by kind and {@code id}. The exception is a kind whose place cannot be derived from its
 * id ({@link #NOT_PLACED_BY_ID}, the step enhancement, ADR-0066): it is written at the place its source file sat, through
 * the place-aware save, and paired by kind and {@code id} in subdirectories too. The modelled artefact kinds are the
 * registry's: a kind joins the gate by binding itself, with no edit here.
 *
 * <p>#193: an asset sidecar is saved like an artefact; asset files and templates are written in place, byte for byte,
 * and paired by path. A sidecar the store refused for a missing payload is refused with the store's exact reason.
 *
 * <p>#195: a preset is written in place at {@code presets/<id>.yaml}, which by the stem rule is its source path, so
 * source and written presets are paired by path. A preset the store refused is refused with the store's exact reasons,
 * joined by {@code "; "}: no kind prefix and no unknown-field suffix, so the known-failure list and the seam test pin
 * one text (research R5).
 *
 * <p>#476: the mutability policy file the descriptor names (ADR-0063) is written in place at {@code policy.yaml}, from
 * the policy the source pack's read declares, and paired by path. The gate compares the two files by meaning.
 *
 * <p>#260: the pack document is written in place at {@code README.md}, paired by path like a preset, and refused with
 * the store's exact reasons (a Markdown refusal names its line). The gate compares the two files by the Markdown model.
 *
 * <p>#195: the source {@code pack-manifest.yaml} is read through the format; an unreadable one is refused with the
 * store's exact reasons. A readable one is restamped (ADR-0042) over the written files it pairs with: a constituent
 * with the written artefact of its kind and id, a preset entry with the preset written at its path. A listed file the
 * round trip did not write refuses it; otherwise the stamped manifest is written at its own path.
 *
 * <p>ADR-0039, #194: a companion file is modelled with the artefact that names it ({@link WholePackCensus#companionsOf}).
 * It is paired with the companion the owner's written file names at the same relative path, and the gate compares the
 * two in the companion's declared form ({@link WholePackCensus#formsOf}). A source companion the written owner does
 * not name is refused as unpaired; a written companion no source companion pairs with is an extra written file. A
 * companion's text is never read here, so Markdown never reaches the YAML reader.
 */
final class DomainRoundTrip implements WholePackRoundTrip {

    static final String DESCRIPTOR = "pack-descriptor";
    static final String GRAPH_SHARD = "graph-shard";
    /** The read-back key that stands for every written file when the written pack does not load at all. */
    private static final String READ_BACK_FAILED = "";

    // #193: an asset sidecar is an artefact; asset files and templates are payloads, written in place, paired by path.
    static final String ASSET = "asset";
    static final String ASSET_PAYLOAD = PackFileSet.ASSET_PAYLOAD;
    static final String TEMPLATE = "template";

    /** #195: a preset is a pack-level file, not an artefact: written in place, paired by path. */
    static final String PRESET = PresetFiles.KIND;

    /** #195: the pack manifest, owned by the whole-pack seam: restamped over the written files, paired by path. */
    static final String MANIFEST = PackManifestYaml.SCHEMA;

    /** #260: the pack document, {@code README.md}, a pack-level file: written in place, paired by path. */
    static final String PACK_DOCUMENT = PackDocumentMarkdown.KIND;

    /** #476: the mutability policy file the descriptor names (ADR-0063), a pack-level file: written in place. */
    static final String POLICY = PolicyYaml.SCHEMA;

    /**
     * The kinds whose files are not placed by id alone, so a write cannot derive the place from the id: the step
     * enhancement's type directory is the target step's (A12). They are written at the place their file was read from
     * in the source pack and paired by that place, in subdirectories too. {@code KindBindingsTest} skips the same set
     * in its two {@code fileName} loops, and a test holds the two equal.
     */
    static final Set<ArtifactKind> NOT_PLACED_BY_ID = Set.of(ArtifactKind.STEP_ENHANCEMENT);

    /** The gate kinds of the bound artefact kinds: each binding's schema name, as the census names its files. */
    static final Set<String> ARTEFACT_KINDS =
            KindBindings.all().stream().map(KindBinding::schemaName).collect(Collectors.toUnmodifiableSet());

    private static final Set<String> MODELLED = Stream.concat(
                    ARTEFACT_KINDS.stream(),
                    Stream.of(
                            DESCRIPTOR, GRAPH_SHARD, ASSET_PAYLOAD, TEMPLATE, PRESET, MANIFEST, PACK_DOCUMENT, POLICY))
            .collect(Collectors.toUnmodifiableSet());

    private final SerializationStrategy strategy;
    private final UnaryOperator<Pack> beforeSave;
    private final UnaryOperator<MutabilityPolicySource> beforePolicySave;

    /**
     * An adapter over a format.
     *
     * @param strategy the format to read and write with; the gate uses {@link YamlSerializationStrategy}
     */
    DomainRoundTrip(SerializationStrategy strategy) {
        this(strategy, UnaryOperator.identity());
    }

    /**
     * An adapter that changes the loaded pack before saving it: the gate's positive controls (#193) flip or drop
     * payload bytes here, so a loss travels the production write path and is never edited into a written file.
     *
     * @param strategy the format to read and write with
     * @param beforeSave maps the loaded pack to the pack that is saved; outcomes still answer for the loaded pack
     */
    DomainRoundTrip(SerializationStrategy strategy, UnaryOperator<Pack> beforeSave) {
        this(strategy, beforeSave, UnaryOperator.identity());
    }

    /**
     * An adapter that also changes the source's policy before writing it: the policy is no part of the {@link Pack},
     * so the policy control (#476) changes it here. The loss travels {@code DomainRepositoryService.writePolicy} and
     * {@code PolicyYaml.write}, the gate's seam: the gate attests that model writer, not {@code WholePackStore}, which
     * still copies {@code policy.yaml} as it was read.
     *
     * @param strategy the format to read and write with
     * @param beforeSave maps the loaded pack to the pack that is saved
     * @param beforePolicySave maps the source's policy to the policy that is written
     */
    DomainRoundTrip(
            SerializationStrategy strategy,
            UnaryOperator<Pack> beforeSave,
            UnaryOperator<MutabilityPolicySource> beforePolicySave) {
        this.strategy = strategy;
        this.beforeSave = beforeSave;
        this.beforePolicySave = beforePolicySave;
    }

    @Override
    public Set<String> modelledKinds() {
        return MODELLED;
    }

    @Override
    public Map<String, Outcome> roundTrip(Path source, Path target, Map<String, String> kindOf) {
        String name = String.valueOf(source.getFileName());
        PackIdentifier id = new PackIdentifier(name);
        createDirectories(target);
        DomainRepositoryService from = service(name, source);
        DomainRepositoryService to = service(name, target);

        SortedMap<String, Outcome> outcomes = new TreeMap<>();
        SortedMap<String, WholePackCensus.CompanionFile> companions = WholePackCensus.companionsOf(source);
        Map<String, @Nullable String> sourceIds = sourceIds(source, kindOf, companions.keySet());
        Loaded loaded = load(from, id);
        @Nullable Pack pack = loaded.pack();
        @Nullable String descriptorFailure =
                writeAll(from, to, id, pack == null ? null : beforeSave.apply(pack), sourcePlaces(sourceIds, kindOf));
        // #476: the policy on its own, so a source policy that cannot be read refuses only the policy file.
        @Nullable String policyFailure = descriptorFailure == null ? writePolicy(from, to, id) : null;
        Map<String, List<String>> refusals = pack == null ? Map.of() : byFile(pack.violations(), "");
        @Nullable String assemblyFailure = loaded.failure();
        // The shards are written before the read-back: the step enhancement rules read the pack's graph documents.
        Map<String, Outcome> shards = shards(name, source, target, kindOf, companions.keySet());
        Loaded reloaded = descriptorFailure == null ? load(to, id) : new Loaded(null, "nothing was written");
        Map<String, List<String>> readBack = readBack(reloaded);
        Map<String, String> written = writtenByKindAndId(target);
        Rewrite rewrite = new Rewrite(
                source,
                target,
                kindOf,
                sourceIds,
                pack,
                assemblyFailure,
                descriptorFailure,
                policyFailure,
                refusals,
                readBack,
                written,
                pack == null ? Map.of() : missingPayloads(pack),
                shards,
                reloaded);
        kindOf.forEach((path, kind) -> {
            if (companions.containsKey(path)) {
                return; // paired below, once its owner's outcome is known
            }
            @Nullable Outcome outcome = outcomeOf(path, kind, rewrite);
            if (outcome != null) {
                outcomes.put(path, outcome);
            }
        });
        SortedMap<String, WholePackCensus.CompanionFile> writtenCompanions = WholePackCensus.companionsOf(target);
        companions.forEach((path, companion) -> {
            if (kindOf.containsKey(path)) {
                outcomes.put(path, companion(target, companion, outcomes, writtenCompanions));
            }
        });
        return outcomes;
    }

    /** Writes every graph shard that is no companion, by its path. */
    private static Map<String, Outcome> shards(
            String name, Path source, Path target, Map<String, String> kindOf, Set<String> companionPaths) {
        Map<String, Outcome> shards = new HashMap<>();
        kindOf.forEach((path, kind) -> {
            if (GRAPH_SHARD.equals(kind) && !companionPaths.contains(path)) {
                shards.put(path, shard(name, source, target, path));
            }
        });
        return shards;
    }

    /** The read-back's refusals by file, or the one refusal of a written pack that does not load. */
    private static Map<String, List<String>> readBack(Loaded reloaded) {
        return reloaded.pack() == null
                ? Map.of(READ_BACK_FAILED, List.of("read-back: the written pack does not load: " + reloaded.failure()))
                : byFile(reloaded.pack().violations(), "read-back: ");
    }

    /**
     * What one round trip read and wrote, for judging its files: the source pack as loaded, every failure on the way,
     * and the written pack as read back.
     */
    private record Rewrite(
            Path source,
            Path target,
            Map<String, String> kindOf,
            Map<String, @Nullable String> sourceIds,
            @Nullable Pack pack,
            @Nullable String assemblyFailure,
            @Nullable String descriptorFailure,
            @Nullable String policyFailure,
            Map<String, List<String>> refusals,
            Map<String, List<String>> readBack,
            Map<String, String> written,
            Map<String, List<String>> missingPayloads,
            Map<String, Outcome> shards,
            Loaded reloaded) {}

    /** The outcome of a file that is no companion, by its gate kind; none for a kind the round trip does not model. */
    private @Nullable Outcome outcomeOf(String path, String kind, Rewrite rewrite) {
        if (DESCRIPTOR.equals(kind)) {
            return rewrite.descriptorFailure() != null
                    ? new Refused(rewrite.descriptorFailure())
                    : readBackOr(PackFileSet.PACK_YAML, rewrite.readBack());
        } else if (rewrite.missingPayloads().containsKey(path)) {
            return new Refused(String.join("; ", rewrite.missingPayloads().get(path)));
        } else if (ARTEFACT_KINDS.contains(kind)) {
            return artefact(
                    rewrite.source(),
                    path,
                    kind,
                    rewrite.refusals(),
                    rewrite.readBack(),
                    rewrite.written(),
                    rewrite.kindOf(),
                    rewrite.sourceIds(),
                    rewrite.assemblyFailure());
        } else if (GRAPH_SHARD.equals(kind)) {
            return rewrite.shards().get(path);
        } else if (ASSET_PAYLOAD.equals(kind) || TEMPLATE.equals(kind)) {
            return payload(path, kind, rewrite.pack(), rewrite.assemblyFailure(), rewrite.reloaded());
        } else if (MANIFEST.equals(kind)) {
            return manifest(rewrite.source(), rewrite.target(), path, rewrite.written());
        } else if (PRESET.equals(kind)) {
            return inPlace(
                    path,
                    "preset",
                    DomainRoundTrip::holdsPreset,
                    rewrite.pack(),
                    rewrite.assemblyFailure(),
                    rewrite.reloaded());
        } else if (POLICY.equals(kind)) {
            return policy(
                    rewrite.target(), path, rewrite.descriptorFailure(), rewrite.policyFailure(), rewrite.readBack());
        } else if (PACK_DOCUMENT.equals(kind)) {
            return inPlace(
                    path,
                    "document",
                    DomainRoundTrip::holdsDocument,
                    rewrite.pack(),
                    rewrite.assemblyFailure(),
                    rewrite.reloaded());
        }
        return null;
    }

    // ---- companions (ADR-0039, #194) ------------------------------------------------------------

    /**
     * A source companion's outcome: refused with its owner, else {@link Written} at the companion the written owner
     * names at the same relative path, else refused as unpaired. Only paths are compared here; the gate reads both files
     * in the companion's declared form.
     */
    private static Outcome companion(
            Path target,
            WholePackCensus.CompanionFile companion,
            Map<String, Outcome> outcomes,
            SortedMap<String, WholePackCensus.CompanionFile> writtenCompanions) {
        String owner = companion.ownerKind() + " " + companion.owner();
        @Nullable Outcome ownerOutcome = outcomes.get(companion.owner());
        if (ownerOutcome instanceof Refused refused) {
            return new Refused("its " + owner + " is refused: " + refused.reason());
        }
        if (!(ownerOutcome instanceof Written writtenOwner)) {
            return new Refused("its " + owner + " has no outcome");
        }
        for (Map.Entry<String, WholePackCensus.CompanionFile> entry : writtenCompanions.entrySet()) {
            WholePackCensus.CompanionFile candidate = entry.getValue();
            if (candidate.owner().equals(writtenOwner.writtenPath())
                    && candidate.declared().equals(companion.declared())) {
                return Files.isRegularFile(target.resolve(entry.getKey()))
                        ? new Written(entry.getKey())
                        : new Refused("not written: the written " + companion.ownerKind() + " "
                                + writtenOwner.writtenPath() + " names " + companion.declared() + ", which is missing");
            }
        }
        return new Refused("unpaired: the written " + companion.ownerKind() + " " + writtenOwner.writtenPath()
                + " names no companion " + companion.declared());
    }

    /**
     * Writes the descriptor and every artefact of {@code pack} through {@code to}; when the pack did not load, only the
     * descriptor is copied.
     *
     * @param places the source place of each artefact of a kind in {@link #NOT_PLACED_BY_ID}, keyed
     *     {@code <gate kind>/<id>}
     * @return why the descriptor could not be read or written, or null when it was written
     */
    private static @Nullable String writeAll(
            DomainRepositoryService from,
            DomainRepositoryService to,
            PackIdentifier id,
            @Nullable Pack pack,
            Map<String, String> places) {
        try {
            if (pack == null) {
                to.save(id, from.get(id));
                return null;
            }
            to.save(id, pack.descriptor());
            // #193: asset files byte for byte, in place; sidecars and templates are artefacts like any other.
            pack.assetFiles().forEach(file -> to.writeAssetFile(id, file));
            // A kind not placed by id is written where its source file sat; every other kind at its canonical place.
            for (CharterArtifact artifact : pack.artifacts()) {
                if (NOT_PLACED_BY_ID.contains(artifact.kind())) {
                    to.saveArtifact(id, artifact, placeOf(artifact, places));
                } else {
                    to.saveArtifact(artifact);
                }
            }
            // #195: presets in place, at presets/<id>.yaml.
            pack.presets().forEach(preset -> to.writePreset(id, preset));
            // #260: the pack document in place, at README.md.
            pack.document().ifPresent(document -> to.writePackDocument(id, document));
            return null;
        } catch (CharterRefusal | ConstraintBreachException | IllegalArgumentException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /**
     * The source place (pack-relative path) of each artefact of a kind in {@link #NOT_PLACED_BY_ID}, keyed
     * {@code <gate kind>/<id>} as {@link #writtenByKindAndId} keys the written files. Computed from the census before
     * anything is written.
     */
    private static Map<String, String> sourcePlaces(
            Map<String, @Nullable String> sourceIds, Map<String, String> kindOf) {
        Map<String, String> places = new HashMap<>();
        sourceIds.forEach((path, elementId) -> {
            String kind = kindOf.get(path);
            if (elementId != null && kind != null && notPlacedById(kind)) {
                places.putIfAbsent(kind + "/" + elementId, path);
            }
        });
        return places;
    }

    private static boolean notPlacedById(String gateKind) {
        return KindBindings.bySchema(gateKind)
                .map(binding -> NOT_PLACED_BY_ID.contains(binding.kind()))
                .orElse(false);
    }

    private static String placeOf(CharterArtifact artifact, Map<String, String> places) {
        String schema = KindBindings.of(artifact.kind()).orElseThrow().schemaName();
        @Nullable String place = places.get(schema + "/" + artifact.localId());
        if (place == null) {
            throw new IllegalArgumentException(
                    "no source place for " + artifact.urn() + ": its file is not in the source census");
        }
        return place;
    }

    private DomainRepositoryService service(String name, Path directory) {
        return new DomainRepositoryService(
                () -> List.of(new PackLocation(name, PackLevel.BUILT_IN, directory)), strategy);
    }

    // ---- artefacts -----------------------------------------------------------------------------

    private static Outcome artefact(
            Path source,
            String path,
            String kind,
            Map<String, List<String>> refusals,
            Map<String, List<String>> readBack,
            Map<String, String> written,
            Map<String, String> kindOf,
            Map<String, @Nullable String> sourceIds,
            @Nullable String assemblyFailure) {
        List<String> refused = refusals.getOrDefault(path, List.of());
        if (!refused.isEmpty()) {
            return new Refused(String.join("; ", refused) + unknownFields(kind, read(source.resolve(path))));
        }
        @Nullable String elementId = sourceIds.get(path);
        if (elementId == null) {
            return new Refused("the source declares no id");
        }
        long sameId = kindOf.entrySet().stream()
                .filter(entry ->
                        entry.getValue().equals(kind) && !entry.getKey().equals(path))
                .filter(entry -> elementId.equals(sourceIds.get(entry.getKey())))
                .count();
        if (sameId > 0) {
            return new Refused("another " + kind + " file declares the same id '" + elementId + "'");
        }
        if (assemblyFailure != null) {
            return new Refused("the pack does not assemble: " + assemblyFailure);
        }
        @Nullable String writtenPath = written.get(kind + "/" + elementId);
        if (writtenPath == null) {
            List<String> domain = refusals.getOrDefault(elementId, List.of());
            return new Refused(
                    domain.isEmpty() ? "not written: no domain element was built" : String.join("; ", domain));
        }
        return readBackOr(writtenPath, readBack);
    }

    /** The loaded aggregate, or why it refused to assemble. */
    private record Loaded(@Nullable Pack pack, @Nullable String failure) {}

    private static Loaded load(DomainRepositoryService service, PackIdentifier id) {
        try {
            return new Loaded(service.load(id), null);
        } catch (CharterRefusal | ConstraintBreachException | IllegalArgumentException refused) {
            return new Loaded(null, String.valueOf(refused.getMessage()));
        }
    }

    /**
     * The top-level keys of a source file that its kind's schema does not declare, named so a refusal says which field
     * was unknown (the validator only reports that one was). Only top-level keys are named; a nested unknown field is
     * still refused by the reader, just not named here.
     */
    private static String unknownFields(String kind, String text) {
        if (!(Yaml.load(text) instanceof Map<?, ?> document)) {
            return "";
        }
        List<String> declared = PackSchemas.bundled().declaredProperties(kind);
        List<String> unknown = document.keySet().stream()
                .map(String::valueOf)
                .filter(key -> !declared.contains(key))
                .sorted()
                .toList();
        return unknown.isEmpty() ? "" : " (unknown field: " + String.join(", ", unknown) + ")";
    }

    /** The declared id of every source artefact file, read once; a companion is no artefact file and is not read. */
    private static Map<String, @Nullable String> sourceIds(
            Path source, Map<String, String> kindOf, Set<String> companions) {
        Map<String, @Nullable String> ids = new HashMap<>();
        kindOf.forEach((path, kind) -> {
            if (ARTEFACT_KINDS.contains(kind) && !companions.contains(path)) {
                ids.put(path, idOf(read(source.resolve(path)), bindingOf(kind).idKey()));
            }
        });
        return ids;
    }

    /**
     * The written artefact files, keyed {@code <gate kind>/<id>}: a file {@code <stem>.<fileToken>.yaml} belongs to the
     * binding whose file token is exactly its last name segment, and is keyed by that binding's schema name, the gate
     * kind the census gives its source.
     */
    private static Map<String, String> writtenByKindAndId(Path target) {
        Map<String, String> written = new HashMap<>();
        // A kind placed at its own path (a mission type, ADR-0044) is written below the pack directory, and so is a
        // kind written at its source place (NOT_PLACED_BY_ID); every other kind directly in it, so only those two are
        // looked for in subdirectories.
        try (Stream<Path> files = Files.walk(target)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                boolean nested = !target.equals(file.getParent());
                String fileName = nested
                        ? target.relativize(file).toString().replace('\\', '/')
                        : String.valueOf(file.getFileName());
                if (!fileName.endsWith(".yaml")) {
                    continue;
                }
                String stem = fileName.substring(0, fileName.length() - ".yaml".length());
                if (stem.indexOf('.') < 0) {
                    continue; // no <id>.<fileToken> stem, so no artefact file
                }
                String token = stem.substring(stem.lastIndexOf('.') + 1);
                for (KindBinding<?, ?> binding : KindBindings.all()) {
                    boolean candidate = binding.fileToken().equals(token)
                            && (!nested || binding.placedAtItsFileName() || NOT_PLACED_BY_ID.contains(binding.kind()));
                    @Nullable String elementId = candidate ? idOf(read(file), binding.idKey()) : null;
                    if (elementId != null) {
                        written.put(binding.schemaName() + "/" + elementId, fileName);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return written;
    }

    /**
     * #476: writes the policy the source's descriptor names, in place, from the policy the source read declares; it
     * does not depend on the pack assembling.
     *
     * @return why the source policy could not be read or the policy written, or null when it was written or not named
     */
    private @Nullable String writePolicy(DomainRepositoryService from, DomainRepositoryService to, PackIdentifier id) {
        try {
            if (from.get(id).paths().mutabilityPolicy().isPresent()) {
                to.writePolicy(id, beforePolicySave.apply(from.policyOf(id)));
            }
            return null;
        } catch (CharterRefusal | ConstraintBreachException | IllegalArgumentException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /**
     * #476: the policy file's outcome: refused with the descriptor or with the policy's own write, or when the written
     * pack holds no policy file at its path (the descriptor names none); refused when the written file does not read
     * through the {@code policy} schema, as the compose reads it; else what the read-back of the written pack says.
     */
    private static Outcome policy(
            Path target,
            String path,
            @Nullable String descriptorFailure,
            @Nullable String policyFailure,
            Map<String, List<String>> readBack) {
        if (descriptorFailure != null) {
            return new Refused(descriptorFailure);
        }
        if (policyFailure != null) {
            return new Refused(policyFailure);
        }
        if (!Files.isRegularFile(target.resolve(path))) {
            return new Refused("not written: the descriptor names no mutability_policy " + path);
        }
        try {
            PolicyYaml.readNamed(target, path);
        } catch (PolicyYaml.Unreadable unreadable) {
            return new Refused("read-back: the written file " + unreadable.getMessage());
        }
        return readBackOr(path, readBack);
    }

    private static Outcome readBackOr(String writtenPath, Map<String, List<String>> readBack) {
        List<String> problems = readBack.getOrDefault(writtenPath, readBack.getOrDefault(READ_BACK_FAILED, List.of()));
        return problems.isEmpty() ? new Written(writtenPath) : new Refused(String.join("; ", problems));
    }

    /** Violations grouped by the file they name (for a domain violation, the element id). */
    private static Map<String, List<String>> byFile(List<PackElementViolation> violations, String prefix) {
        Map<String, List<String>> byFile = new TreeMap<>();
        for (PackElementViolation violation : violations) {
            byFile.computeIfAbsent(violation.file(), file -> new ArrayList<>())
                    .add(prefix + violation.kind() + ": " + violation.reason());
        }
        return byFile;
    }

    // ---- asset files and templates (#193) -------------------------------------------------------

    /**
     * The store's refusal of a sidecar whose payload it does not hold, by sidecar file, with the store's exact reason
     * ({@code payload assets/<path> does not exist}); the gate pins that reason, so it carries no kind prefix.
     */
    private static Map<String, List<String>> missingPayloads(Pack pack) {
        Map<String, List<String>> byFile = new TreeMap<>();
        for (PackElementViolation violation : pack.violations()) {
            if (ASSET.equals(violation.kind()) && violation.reason().startsWith("payload ")) {
                byFile.computeIfAbsent(violation.file(), file -> new ArrayList<>())
                        .add(violation.reason());
            }
        }
        return byFile;
    }

    /**
     * An asset file or a template is written in place: {@link Written} at its own path when the loaded pack held it
     * and the read-back pack holds it again, else {@link Refused}. The bytes are the gate's to compare.
     */
    private static Outcome payload(
            String path, String kind, @Nullable Pack pack, @Nullable String assemblyFailure, Loaded reloaded) {
        if (pack == null) {
            return new Refused("the pack does not assemble: " + assemblyFailure);
        }
        if (!holds(pack, path)) {
            return new Refused("not read: the loaded pack holds no " + kind + " at " + path);
        }
        @Nullable Pack readBack = reloaded.pack();
        if (readBack == null) {
            return new Refused("read-back: the written pack does not load: " + reloaded.failure());
        }
        return holds(readBack, path)
                ? new Written(path)
                : new Refused("read-back: the written pack holds no " + kind + " at " + path);
    }

    /** Whether {@code pack} holds the asset file or template at the pack-relative {@code path}. */
    private static boolean holds(Pack pack, String path) {
        return pack.assetFiles().stream().anyMatch(file -> path.equals(PackFileSet.ASSETS + file.path()))
                || pack.templates().stream()
                        .anyMatch(template -> path.equals(TemplateBinding.INSTANCE.pathOf(template)));
    }

    // ---- the manifest (#195) -------------------------------------------------------------------

    private Outcome manifest(Path source, Path target, String path, Map<String, String> written) {
        Optional<String> text = FilesystemPackStore.readText(source.resolve(path));
        if (text.isEmpty()) {
            return new Refused(FilesystemPackStore.NOT_UTF8);
        }
        FileRead<PackManifestDso> read = strategy.readManifest(text.get(), path);
        if (read instanceof FileRead.Unreadable<PackManifestDso> bad) {
            return new Refused(FilesystemPackStore.violationsOf(bad).stream()
                    .map(PackElementViolation::reason)
                    .collect(Collectors.joining("; ")));
        }
        if (!(read instanceof FileRead.Read<PackManifestDso> good)) {
            return new Refused("the manifest does not read");
        }
        List<String> misplaced = WholePackStore.misplacedPresetEntries(good.value());
        if (!misplaced.isEmpty()) {
            return new Refused(String.join("; ", misplaced));
        }
        // #110: the lists are derived from the source pack, as a registration derives them; a difference refuses.
        List<String> membership =
                ManifestMembership.mismatch(good.value(), ManifestMembership.derivedAt(source, strategy));
        if (!membership.isEmpty()) {
            return new Refused(String.join("; ", membership));
        }
        Map<String, String> pairing = pairing(good.value(), target, written);
        PackManifests.Recomputed recomputed = PackManifests.recompute(
                good.value(),
                listed -> pairing.containsKey(listed)
                        ? FilesystemPackStore.readBytes(target.resolve(pairing.get(listed)))
                        : Optional.empty());
        if (!recomputed.missing().isEmpty()) {
            return new Refused(
                    path + " lists " + recomputed.missing().getFirst() + ", which the round trip did not write");
        }
        write(target.resolve(path), strategy.writeManifest(recomputed.stamped()));
        return new Written(path);
    }

    /**
     * Each listed path of the manifest with the written file it pairs with: a constituent with the written artefact of
     * its kind token and id, a template constituent and a preset entry with the file written at its own path. A path
     * with no written file is left out. The store pairs every entry by path; the gate's flat layout cannot, so a
     * constituent whose path is stale but whose id is written pairs here while the store's write refuses it.
     */
    static Map<String, String> pairing(PackManifestDso manifest, Path target, Map<String, String> written) {
        Map<String, String> out = new HashMap<>();
        for (PackManifestDso.Constituent constituent : manifest.constituents()) {
            // A template has no binding: the gate writes it in place, at its own path, so it pairs by path.
            if (ArtifactKind.TEMPLATE.token().equals(constituent.kind())) {
                if (Files.isRegularFile(target.resolve(constituent.path()))) {
                    out.put(constituent.path(), constituent.path());
                }
                continue;
            }
            ArtifactKind.fromToken(constituent.kind())
                    .flatMap(KindBindings::of)
                    .map(binding -> written.get(binding.schemaName() + "/" + constituent.id()))
                    .ifPresent(file -> out.put(constituent.path(), file));
        }
        for (PackManifestDso.PresetEntry preset : manifest.presets()) {
            if (Files.isRegularFile(target.resolve(preset.path()))) {
                out.put(preset.path(), preset.path());
            }
        }
        return out;
    }

    // ---- pack-level files written in place: presets (#195) and the pack document (#260) -----------

    /**
     * A pack-level file written in place: {@link Written} at its own path when the loaded pack held it and the
     * read-back pack holds it again with no violation on that path, else {@link Refused} with the store's exact
     * reasons. The gate then compares the two files in their form.
     *
     * @param path the file, pack-relative
     * @param what what the pack holds there, named in a refusal
     * @param holds whether a pack holds the file at {@code path}
     */
    private static Outcome inPlace(
            String path,
            String what,
            BiPredicate<Pack, String> holds,
            @Nullable Pack pack,
            @Nullable String assemblyFailure,
            Loaded reloaded) {
        if (pack == null) {
            return new Refused("the pack does not assemble: " + assemblyFailure);
        }
        List<String> refused = reasonsAt(pack, path);
        if (!refused.isEmpty()) {
            return new Refused(String.join("; ", refused));
        }
        if (!holds.test(pack, path)) {
            return new Refused("not read: the loaded pack holds no " + what + " at " + path);
        }
        @Nullable Pack readBack = reloaded.pack();
        if (readBack == null) {
            return new Refused("read-back: the written pack does not load: " + reloaded.failure());
        }
        List<String> readBackRefused = reasonsAt(readBack, path);
        if (!readBackRefused.isEmpty()) {
            return new Refused("read-back: " + String.join("; ", readBackRefused));
        }
        return holds.test(readBack, path)
                ? new Written(path)
                : new Refused("read-back: the written pack holds no " + what + " at " + path);
    }

    /** The reasons of every violation on {@code path}, as the store gave them. */
    private static List<String> reasonsAt(Pack pack, String path) {
        return pack.violations().stream()
                .filter(violation -> path.equals(violation.file()))
                .map(PackElementViolation::reason)
                .toList();
    }

    private static boolean holdsPreset(Pack pack, String path) {
        return pack.presets().stream().anyMatch(preset -> path.equals(PresetFiles.pathOf(preset.id())));
    }

    private static boolean holdsDocument(Pack pack, String path) {
        return PackDocumentMarkdown.FILE.equals(path) && pack.document().isPresent();
    }

    // ---- graph shards --------------------------------------------------------------------------

    private static Outcome shard(String name, Path source, Path target, String path) {
        String text = read(source.resolve(path));
        if (!(GraphDocumentReader.read(text, Shape.SHARD, path) instanceof FileRead.Read<RawGraph> raw)) {
            return new Refused("the shard does not read");
        }
        PackPolicy.GraphBuild build = PackPolicy.graph(name, raw.value());
        if (!build.findings().isEmpty()) {
            return new Refused("the graph does not build: " + build.findings());
        }
        @Nullable String generatedBy = Yaml.load(text) instanceof Map<?, ?> document && document.get("generated_by") != null
                ? String.valueOf(document.get("generated_by"))
                : null;
        String writtenText = GraphShardWriter.write(build.graph(), generatedBy);
        write(target.resolve(path), writtenText);
        if (!(GraphDocumentReader.read(writtenText, Shape.SHARD, path) instanceof FileRead.Read<RawGraph> reread)) {
            return new Refused("read-back: the written shard does not read");
        }
        PackPolicy.GraphBuild rebuilt = PackPolicy.graph(name, reread.value());
        if (!rebuilt.findings().isEmpty()) {
            return new Refused("read-back: the written shard does not build: " + rebuilt.findings());
        }
        if (!sameGraph(build.graph(), rebuilt.graph())) {
            return new Refused("read-back: the written shard builds into another graph");
        }
        return new Written(path);
    }

    /**
     * Whether two graphs hold the same nodes, tensions and relationships. Relationships are compared field by field:
     * {@code Relationship.equals} is pair identity and ignores the relation, the condition and the rationale.
     */
    static boolean sameGraph(DoctrineGraph source, DoctrineGraph written) {
        return List.copyOf(source.nodes()).equals(List.copyOf(written.nodes()))
                && source.tensions().equals(written.tensions())
                && relationshipFields(source).equals(relationshipFields(written));
    }

    private static List<List<Object>> relationshipFields(DoctrineGraph graph) {
        return graph.relationships().stream()
                .map(r -> List.<Object>of(r.from(), r.to(), r.type(), r.when(), r.rationale()))
                .toList();
    }

    // ---- files ---------------------------------------------------------------------------------

    /** The binding of an artefact gate kind: the gate kind is the schema name. */
    private static KindBinding<?, ?> bindingOf(String kind) {
        return KindBindings.bySchema(kind)
                .orElseThrow(() -> new IllegalStateException("no stored kind is validated by the schema " + kind));
    }

    private static @Nullable String idOf(String text, String idKey) {
        try {
            return Yaml.load(text) instanceof Map<?, ?> map && map.get(idKey) != null
                    ? String.valueOf(map.get(idKey))
                    : null;
        } catch (RuntimeException unparsable) {
            return null;
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
