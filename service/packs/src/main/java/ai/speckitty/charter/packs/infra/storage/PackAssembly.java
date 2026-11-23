package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.ReferenceFacts;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.rules.ReferenceClosure;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Builds the {@link Pack} aggregate from what the store read, and judges nothing (#554, S1, T023): a plain class, not a
 * bean, so the load reaches no framework ({@code THE_PACKS_LOAD_REACHES_NO_FRAMEWORK}, F2). It maps each schema-valid
 * element to its entity through its kind's binding, adds the asset files, the templates, the presets and the pack
 * document, and records each file it meets that the domain refuses on the way (a value-object breach, a mission type
 * whose output template the pack does not hold, a guard or a lifecycle that does not close, a second file of one id) at
 * that file, without throwing, so the caller lists every fault at once. Which step enhancements and mission types the
 * aggregate leaves out is decided before it, by the rules that judge them ({@link #assembly(PackIdentifier,
 * FilesystemPackStore.PackRead, Map, Set, Set)}); the assembly only shapes the read so that {@code Pack.guardKind}
 * never breaches.
 *
 * <p>{@link DomainRepositoryService} and {@link WholePackStore} build through it, as {@link PackManifestDerivation} does,
 * so the composing read, the whole-pack load and the manifest's derived lists hold the same artifacts.
 */
final class PackAssembly {

    private PackAssembly() {}

    /**
     * The one assembly of a read into the aggregate, shared by {@link DomainRepositoryService#load(PackIdentifier)},
     * {@link WholePackStore#load} and {@link PackManifestDerivation}: the descriptor, the read violations, every bound kind (through {@link #collect}),
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
        return assembly(pack, read, places, violation -> {});
    }

    /**
     * The assembly of {@link #assembly(PackIdentifier, FilesystemPackStore.PackRead, Map)}, handing {@code storeOnly}
     * each fault only the assembly meets, which no reader or rule states (#554, A-M1 rows 17 and 18): a value-object
     * breach of a template, a preset, the pack document or an artifact, a mission type's output template the pack does
     * not hold, a guard or lifecycle reference that does not close. A repeated id is not one: the content rules state
     * it.
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param storeOnly receives each fault only the assembly meets, as it is recorded on the builder
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            Consumer<PackElementViolation> storeOnly) {
        return assembly(pack, read, places, storeOnly, facts -> {});
    }

    /**
     * The assembly of {@link #assembly(PackIdentifier, FilesystemPackStore.PackRead, Map, Consumer)}, handing
     * {@code references} what the reference closure judges of the read (#355): every asset, guard, lifecycle and
     * mission type that builds, with its file, before the load leaves any out. The PACK ruleset states the closure from
     * those facts ({@code ReferenceRules}), so its faults are not store-only.
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param storeOnly receives each fault only the assembly meets, as it is recorded on the builder
     * @param references receives the reference closure's facts of the read
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            Consumer<PackElementViolation> storeOnly,
            Consumer<ReferenceFacts> references) {
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
                PackElementViolation fault = PackElementViolation.wholeFile(
                        place, PackCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage()));
                templateViolations.add(fault);
                storeOnly.accept(fault);
            }
        }
        ReferenceFacts facts = referenceFacts(pack, read, templates.values());
        references.accept(facts);
        ReferenceClosure closure = ReferenceClosure.of(facts);
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            collect(pack, kind, read, builder, places, closure, storeOnly);
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
                record(
                        builder,
                        storeOnly,
                        PackElementViolation.wholeFile(
                                stored.file(), PackCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
            }
        }
        // #260: the document's parts are the read model's; one the domain refuses is a violation, never a failed load.
        read.document().ifPresent(stored -> {
            try {
                builder.document(PackDocumentMarkdown.toDomain(stored.dso()));
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                record(
                        builder,
                        storeOnly,
                        PackElementViolation.wholeFile(
                                stored.file(), PackCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
            }
        });
        return builder;
    }

    /** Records a fault only the assembly meets on the builder, and hands it to {@code storeOnly}. */
    private static void record(
            Pack.Builder builder, Consumer<PackElementViolation> storeOnly, PackElementViolation fault) {
        builder.violation(fault);
        storeOnly.accept(fault);
    }

    /**
     * The aggregate built from the one read's assembly input alone (#554, S1, S6, T023): the descriptor, each held
     * artifact, the asset files, the presets, the document and every fault the load met, in the read's order. It judges
     * nothing: the read found the faults, and the aggregate carries them.
     *
     * @param held the assembly input of the one read
     * @return the aggregate
     */
    static Pack build(PackContents.Assembly.Assembled held) {
        Pack.Builder builder = Pack.builder().id(held.id()).descriptor(held.descriptor());
        held.violations().forEach(builder::violation);
        held.artifacts().forEach(placed -> builder.artifact(placed.artifact()));
        held.assetFiles().forEach(builder::assetFile);
        held.presets().forEach(builder::preset);
        held.document().ifPresent(builder::document);
        return builder.build();
    }

    /**
     * The assembly of a read with the files its rules withhold left out ({@link #shaped}): the step enhancement files in
     * {@code withheld} (the second file of a repeated id) and the mission types in {@code repeating} (a step that
     * repeats a substep id). The caller judges which they are, and records their faults itself.
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param withheld the step enhancement files the aggregate leaves out
     * @param repeating the mission type files the aggregate leaves out
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            Set<String> withheld,
            Set<String> repeating) {
        return assembly(pack, shaped(read, withheld, repeating), places);
    }

    /**
     * The shaped assembly, handing {@code storeOnly} each fault only the assembly meets
     * ({@link #assembly(PackIdentifier, FilesystemPackStore.PackRead, Map, Consumer)}).
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param withheld the step enhancement files the aggregate leaves out
     * @param repeating the mission type files the aggregate leaves out
     * @param storeOnly receives each fault only the assembly meets
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            Set<String> withheld,
            Set<String> repeating,
            Consumer<PackElementViolation> storeOnly) {
        return assembly(pack, read, places, withheld, repeating, storeOnly, facts -> {});
    }

    /**
     * The shaped assembly, handing {@code storeOnly} each fault only the assembly meets and {@code references} the
     * reference closure's facts of the shaped read, the one the aggregate is built from.
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param withheld the step enhancement files the aggregate leaves out
     * @param repeating the mission type files the aggregate leaves out
     * @param storeOnly receives each fault only the assembly meets
     * @param references receives the reference closure's facts
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            Set<String> withheld,
            Set<String> repeating,
            Consumer<PackElementViolation> storeOnly,
            Consumer<ReferenceFacts> references) {
        return assembly(pack, shaped(read, withheld, repeating), places, storeOnly, references);
    }

    /**
     * What the reference closure judges of a read: each asset, guard, lifecycle and mission type that builds, with its
     * file, in the order the load meets them (the kinds in {@link KindBindings#all()} order, each in the read's order);
     * the asset payload paths; the ids of the mission types' own templates.
     */
    private static ReferenceFacts referenceFacts(
            PackIdentifier pack, FilesystemPackStore.PackRead read, Collection<Template> templates) {
        List<ReferenceFacts.Placed> candidates = new ArrayList<>();
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            candidates(pack, kind, read, candidates);
        }
        return new ReferenceFacts(
                candidates,
                read.assetFiles().stream().map(AssetFile::path).collect(Collectors.toSet()),
                ReferenceClosure.namable(templates),
                EmbeddedSchemas.PORT);
    }

    private static <D, E extends CharterArtifact> void candidates(
            PackIdentifier pack,
            KindBinding<D, E> kind,
            FilesystemPackStore.PackRead read,
            List<ReferenceFacts.Placed> into) {
        for (FilesystemPackStore.Stored<D> stored : read.elements(kind)) {
            Optional<E> entity;
            try {
                entity = Optional.of(kind.toEntity(pack, stored.dso()));
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                // The value-object breach is the assembly's own fault; the closure judges what builds.
                entity = Optional.empty();
            }
            entity.flatMap(ReferenceSubjects::of)
                    .ifPresent(subject -> into.add(new ReferenceFacts.Placed(stored.file(), subject)));
        }
    }

    /**
     * A stored mission type as the plain facts the pack rules judge: its steps, then its on-demand steps, and the
     * substeps of each.
     *
     * @param file the type's pack-relative file
     * @param type the stored type
     * @return the facts
     */
    static MissionTypeFacts missionTypeFacts(String file, MissionTypeDso type) {
        List<String> steps = new ArrayList<>();
        List<MissionTypeFacts.SubstepFacts> substeps = new ArrayList<>();
        Map<String, List<MissionTypeDso.Step>> lists = new LinkedHashMap<>();
        lists.put("steps", type.steps());
        lists.put("on_demand_steps", type.onDemandSteps());
        lists.forEach((list, written) -> {
            for (int i = 0; i < written.size(); i++) {
                MissionTypeDso.Step step = written.get(i);
                steps.add(step.id());
                for (int j = 0; j < step.substeps().size(); j++) {
                    MissionTypeDso.Substep substep = step.substeps().get(j);
                    substeps.add(new MissionTypeFacts.SubstepFacts(
                            list,
                            i,
                            step.id(),
                            j,
                            substep.id(),
                            substep.delegatesTo()
                                    .map(delegation -> new MissionTypeFacts.Delegation(
                                            delegation.kind(), delegation.candidates()))));
                }
            }
        });
        return new MissionTypeFacts(file, type.id(), steps, substeps);
    }

    /** The pack-relative file of a companion path, which is relative to the file of the artifact that names it. */
    static String companionFile(String artifactFile, String companionPath) {
        Path file = Path.of(artifactFile).resolveSibling(companionPath).normalize();
        return file.toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static String enhancementSuffix() {
        return "." + StepEnhancementBinding.INSTANCE.fileToken() + ".yaml";
    }

    /** The facts of the held step enhancements, their companions' canonical texts and the pack's edges. */
    static StepEnhancementFacts stepEnhancementFacts(FilesystemPackStore.PackRead read, List<GraphDocument> graphs) {
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
        for (GraphDocument document : graphs) {
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

    /**
     * The read the assembly builds from, shaped so that the aggregate's own invariants hold (operator ruling A2): each
     * {@code companion} violation of a step enhancement file kept under the kind {@code prompt_fragment_invalid}, the
     * store hard-coding the generic kind (#554, R2 row 19), the step enhancement files in {@code withheld} left out of
     * the held enhancements, so that {@code Pack.guardKind}'s same-id rule never breaches at {@code build()}, and the
     * mission types in {@code repeating} left out of the held types. Which files those are is decided before the
     * assembly, by the rules that judge them; this only shapes.
     */
    private static FilesystemPackStore.PackRead shaped(
            FilesystemPackStore.PackRead read, Set<String> withheld, Set<String> repeating) {
        ReasonCode kind = PackCodes.PROMPT_FRAGMENT_INVALID;
        List<PackElementViolation> violations = read.violations().stream()
                .map(violation -> FilesystemPackStore.COMPANION_KIND.equals(violation.kind())
                                && violation.file().endsWith(enhancementSuffix())
                        ? PackElementViolation.wholeFile(violation.file(), kind, violation.reason())
                        : violation)
                .toList();
        Map<ArtifactKind, List<FilesystemPackStore.Stored<?>>> elements = new EnumMap<>(ArtifactKind.class);
        elements.putAll(read.elements());
        elements.computeIfPresent(
                ArtifactKind.STEP_ENHANCEMENT,
                (key, held) -> held.stream()
                        .filter(stored -> !withheld.contains(stored.file()))
                        .toList());
        elements.computeIfPresent(
                ArtifactKind.MISSION_TYPE,
                (key, held) -> held.stream()
                        .filter(stored -> !repeating.contains(stored.file()))
                        .toList());
        return new FilesystemPackStore.PackRead(
                read.descriptor(),
                elements,
                violations,
                read.assetFiles(),
                read.templates(),
                read.presets(),
                read.document(),
                read.storeOnly());
    }

    /**
     * Adds each readable element of one kind to the aggregate, failing closed: a domain-invalid element is recorded as
     * a violation, named by its id, rather than aborting assembly. An artifact that does not close over its references
     * ({@link ReferenceClosure}: a mission type whose output template is not one of its own, #277; an asset, a guard or
     * a lifecycle, ADR-0054) is left out with an {@code artifact_schema_error} at its file, in the rule's words. That
     * fault is the PACK ruleset's to state ({@code ReferenceRules}), so it is not handed to {@code storeOnly}. Each added
     * element's file is put in {@code places} under its URN.
     */
    private static <D, E extends CharterArtifact> void collect(
            PackIdentifier pack,
            KindBinding<D, E> kind,
            FilesystemPackStore.PackRead read,
            Pack.Builder builder,
            Map<Urn, String> places,
            ReferenceClosure closure,
            Consumer<PackElementViolation> storeOnly) {
        for (FilesystemPackStore.Stored<D> stored : read.elements(kind)) {
            try {
                E entity = kind.toEntity(pack, stored.dso());
                Optional<String> unclosed = ReferenceSubjects.of(entity).flatMap(closure::firstUnresolved);
                if (unclosed.isPresent()) {
                    builder.violation(PackElementViolation.wholeFile(
                            stored.file(), PackCodes.ARTIFACT_SCHEMA_ERROR, unclosed.get()));
                    continue;
                }
                Urn urn = entity.urn();
                String first = places.get(urn);
                if (first != null) {
                    // #554, R2 row 10: the first file in the read's order owns the id; a repeat is its own fault.
                    builder.violation(PackElementViolation.wholeFile(
                            stored.file(),
                            PackCodes.DUPLICATE_ARTIFACT_ID,
                            PackCodes.duplicateArtifactId(kind.kind().token(), kind.idOf(stored.dso()), first)));
                    continue;
                }
                builder.artifact(entity);
                places.put(urn, stored.file());
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                record(
                        builder,
                        storeOnly,
                        PackElementViolation.wholeFile(
                                stored.file(), PackCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
            }
        }
    }
}
