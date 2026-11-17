package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.missions.domain.UnresolvedTemplate;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                templateViolations.add(new PackElementViolation(
                        place, StorageCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
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
                builder.violation(new PackElementViolation(
                        stored.file(), StorageCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
            }
        }
        // #260: the document's parts are the read model's; one the domain refuses is a violation, never a failed load.
        read.document().ifPresent(stored -> {
            try {
                builder.document(PackDocumentMarkdown.toDomain(stored.dso()));
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                builder.violation(new PackElementViolation(
                        stored.file(), StorageCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
            }
        });
        return builder;
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
                            stored.file(),
                            StorageCodes.ARTIFACT_SCHEMA_ERROR,
                            MissionTemplates.reason(unresolved.get())));
                    continue;
                }
                Optional<String> unclosed = closure.firstUnresolved(entity);
                if (unclosed.isPresent()) {
                    builder.violation(new PackElementViolation(
                            stored.file(), StorageCodes.ARTIFACT_SCHEMA_ERROR, unclosed.get()));
                    continue;
                }
                Urn urn = entity.urn();
                String first = places.get(urn);
                if (first != null) {
                    // #554, R2 row 10: the first file in the read's order owns the id; a repeat is its own fault.
                    builder.violation(new PackElementViolation(
                            stored.file(),
                            PackCodes.DUPLICATE_ARTIFACT_ID,
                            PackCodes.duplicateArtifactId(kind.kind().token(), kind.idOf(stored.dso()), first)));
                    continue;
                }
                builder.artifact(entity);
                places.put(urn, stored.file());
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                builder.violation(new PackElementViolation(
                        stored.file(), StorageCodes.ARTIFACT_SCHEMA_ERROR, String.valueOf(breach.getMessage())));
            }
        }
    }
}
