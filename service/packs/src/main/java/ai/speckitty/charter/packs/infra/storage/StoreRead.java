package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackContents.Assembly;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.ReferenceFacts;
import ai.speckitty.charter.packs.domain.StepEnhancementFault;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import ai.speckitty.charter.packs.domain.rules.FragmentRules;
import ai.speckitty.charter.packs.domain.rules.PromptSlotRules;
import ai.speckitty.charter.packs.domain.rules.StepRules;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.rules.LinkRules;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The one read's DSO half (#554, S1, T025, T028): what the store's own reader reads of a pack tree, assembled once
 * through {@link PackAssembly} with the faults the store half meets on the way ({@link #assembly}), and mapped into the
 * domain values the
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
 * @param graphsUnreadable whether a graph document could not be read, known even when the load refused the pack after
 *     its graph read: the composing read refuses that first
 */
record StoreRead(
        Assembly assembly,
        PackLayout layout,
        List<GraphDocument> graphs,
        Optional<PackManifestDso> manifest,
        Optional<String> graphRefusal,
        boolean graphsUnreadable) {

    private static final java.util.concurrent.atomic.LongAdder RUNS = new java.util.concurrent.atomic.LongAdder();

    /**
     * Copies the lists.
     *
     * @param assembly the domain values
     * @param layout the layout
     * @param graphs the graph documents
     * @param manifest the manifest
     * @param graphRefusal the strict load's graph refusal
     * @param graphsUnreadable whether a graph document could not be read
     */
    StoreRead {
        graphs = List.copyOf(graphs);
    }

    /**
     * Reads a pack directory's DSO half once.
     *
     * @param directory the pack directory
     * @param name the name the pack is known by: the refusals and the composition facts name it
     * @param walk the walk of the tree the one read makes; the store's listing comes from it
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
        RUNS.increment();
        Path packDir = directory.toAbsolutePath().normalize();
        List<StoreRead> refusal = new ArrayList<>();
        DsoRead dso = dsoRead(packDir, strategy, companionRules, bindings, walk, refusal);
        if (dso == null) {
            return refusal.getFirst();
        }
        FilesystemPackStore.GraphRead graphs = dso.graphs();
        FilesystemPackStore.PackRead read = dso.read();
        PackIdentifier id = new PackIdentifier(read.descriptor().packId());
        Optional<String> graphRefusal = graphRefusal(id, graphs);
        Map<Urn, String> places = new HashMap<>();
        List<Reason> warnings = new ArrayList<>();
        List<PackElementViolation> storeOnly = new ArrayList<>();
        List<ReferenceFacts> references = new ArrayList<>();
        Pack.Builder builder = assembly(id, read, places, graphs, warnings, storeOnly::add, references::add);
        linked(walk).forEach(builder::violation);
        List<PackElementViolation> trailing = new ArrayList<>();
        Optional<PackManifestDso> manifest = manifest(packDir, strategy, trailing);
        List<String> policyFault = new ArrayList<>();
        Optional<PolicyYaml.Named> policy =
                read.descriptor().mutabilityPolicy().flatMap(file -> policy(packDir, file, trailing, policyFault));
        trailing.forEach(builder::violation);
        storeOnly.addAll(namedPolicyFaults(read, trailing));
        Pack pack;
        try {
            pack = builder.build();
        } catch (ConstraintBreachException breach) {
            return breached(graphs.violations(), String.valueOf(breach.getMessage()), graphRefusal);
        }
        List<String> factsRefusal = new ArrayList<>();
        Optional<PackCompositionFacts> facts = facts(name, pack, read, graphs, policy, factsRefusal);
        Assembly.Assembled assembled = new Assembly.Assembled(
                id,
                pack.descriptor(),
                placed(pack, places),
                pack.assetFiles(),
                pack.presets(),
                pack.document(),
                pack.violations(),
                pack.violations().size() - trailing.size(),
                graphs.violations(),
                warnings,
                storeOnly,
                policy.map(PolicyYaml.Named::text),
                policyFault.stream().findFirst(),
                facts,
                factsRefusal.stream().findFirst(),
                references.stream().findFirst().orElse(ReferenceFacts.NONE));
        return new StoreRead(
                assembled,
                new PackLayout(places),
                graphs.documents(),
                manifest,
                graphRefusal,
                !graphs.violations().isEmpty());
    }

    /**
     * How many times this JVM has run the DSO half: the counter that tells every surface reads through this one
     * implementation (#554, T028; the composing read included, operator decision 2026-11-17).
     *
     * @return the count
     */
    static long runs() {
        return RUNS.sum();
    }

    /**
     * One {@code pack_entry_linked} per link of the walk, at the link, in {@code LinkRules}' words, the path stated once
     * by the violation's file (D6): the store half
     * states it itself, as it states {@code duplicate_step_id}, so the composing read of a configured pack, which judges
     * nothing else, refuses a pack holding a link. It is recorded on the aggregate, not among the store-only faults, so
     * a registration and validate mode state it once, through {@code LinkRules}.
     */
    static List<PackElementViolation> linked(PackFileSet.Walk walk) {
        return walk.links().stream()
                .map(link -> new PackElementViolation(link, StorageCodes.PACK_ENTRY_LINKED, LinkRules.LINKED))
                .toList();
    }

    /** Each held artifact with the file it is stored in, in the aggregate's order. */
    private static List<Assembly.Placed> placed(Pack pack, Map<Urn, String> places) {
        List<Assembly.Placed> artifacts = new ArrayList<>();
        for (CharterArtifact artifact : pack.artifacts()) {
            String place = Objects.requireNonNull(places.get(artifact.urn()), "every held artifact has a place");
            artifacts.add(new Assembly.Placed(place, artifact));
        }
        return artifacts;
    }

    /**
     * The store's read of the pack in {@code packDir}, its graph documents first; a directory with no parent, or a
     * refusal of either read, goes to {@code refusal}.
     */
    private static @Nullable DsoRead dsoRead(
            Path packDir,
            SerializationStrategy strategy,
            Companions.Rules companionRules,
            List<KindBinding<?, ?>> bindings,
            PackFileSet.Walk walk,
            List<StoreRead> refusal) {
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            refusal.add(refused(
                    List.of(), RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory", false, false));
            return null;
        }
        FilesystemPackStore store = new FilesystemPackStore(parent, strategy, companionRules);
        return dsoRead(store, new PackIdentifier(String.valueOf(packDir.getFileName())), bindings, walk, refusal);
    }

    /** What the store's reader read of a pack: its graph documents, then everything else. */
    private record DsoRead(FilesystemPackStore.GraphRead graphs, FilesystemPackStore.PackRead read) {}

    /**
     * The store's read of a pack, its graph documents first; a refusal of either goes to {@code refusal}, the graph
     * read's fault kept when the rest is refused.
     */
    private static @Nullable DsoRead dsoRead(
            FilesystemPackStore store,
            PackIdentifier key,
            List<KindBinding<?, ?>> bindings,
            PackFileSet.Walk walk,
            List<StoreRead> refusal) {
        FilesystemPackStore.GraphRead graphs;
        try {
            graphs = store.readGraphs(key);
        } catch (CharterRefusal refused) {
            refusal.add(refused(List.of(), refused.code(), String.valueOf(refused.getMessage()), false, false));
            return null;
        }
        try {
            return new DsoRead(graphs, store.read(key, bindings, graphs, walk));
        } catch (CharterRefusal refused) {
            // The graph read found the directory, so the rest of the read refuses only the descriptor: its fault is
            // placed at pack.yaml with no second read of the file (#554, WP04 review MINOR-4).
            refusal.add(refused(
                    List.of(),
                    refused.code(),
                    String.valueOf(refused.getMessage()),
                    true,
                    !graphs.violations().isEmpty()));
            return null;
        }
    }

    /** The strict load's refusal of the first graph document that cannot be read, when one cannot. */
    private static Optional<String> graphRefusal(PackIdentifier id, FilesystemPackStore.GraphRead graphs) {
        return graphs.violations().stream()
                .findFirst()
                .map(first -> id.value() + "/" + first.file() + ": " + first.reason());
    }

    /**
     * The composition facts of the assembled pack, when every graph document was read; a refusal of the builder (an edge
     * that joins an artifact to itself) goes to {@code refusal}.
     */
    private static Optional<PackCompositionFacts> facts(
            String name,
            Pack pack,
            FilesystemPackStore.PackRead read,
            FilesystemPackStore.GraphRead graphs,
            Optional<PolicyYaml.Named> policy,
            List<String> refusal) {
        if (!graphs.violations().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(CompositionFactsBuilder.build(
                    new PackIdentifier(name),
                    pack,
                    read,
                    graphs.documents(),
                    policy.map(PolicyYaml.Named::source).orElse(MutabilityPolicySource.empty())));
        } catch (CharterRefusal refused) {
            refusal.add(String.valueOf(refused.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * The aggregate's own invariant breached at {@code build()} (A-M1 row 3): refused as a whole, {@code
     * validation_failed}, after the graph faults read before it. No planted pack reaches it today: the withholding and
     * the repeated-id rule leave the aggregate nothing to breach, so it stays as the guard's fact.
     *
     * @param graphFaults the graph faults read before the build
     * @param breach the breach's message
     * @param graphRefusal the strict load's graph refusal
     * @return the refused read
     */
    static StoreRead breached(List<PackElementViolation> graphFaults, String breach, Optional<String> graphRefusal) {
        return new StoreRead(
                new Assembly.Refused(graphFaults, RefusalCode.VALIDATION_FAILED, breach, false),
                new PackLayout(Map.of()),
                List.of(),
                Optional.empty(),
                graphRefusal,
                !graphFaults.isEmpty());
    }

    private static StoreRead refused(
            List<PackElementViolation> graphFaults,
            RefusalCode code,
            String message,
            boolean atDescriptor,
            boolean graphsUnreadable) {
        return new StoreRead(
                new Assembly.Refused(graphFaults, code, message, atDescriptor),
                new PackLayout(Map.of()),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                graphsUnreadable);
    }

    /** Row 32: the fault of the policy file the descriptor names, among the trailing ones, is the store's alone. */
    private static List<PackElementViolation> namedPolicyFaults(
            FilesystemPackStore.PackRead read, List<PackElementViolation> trailing) {
        return trailing.stream()
                .filter(violation -> read.descriptor()
                        .mutabilityPolicy()
                        .filter(violation.file()::equals)
                        .isPresent())
                .toList();
    }

    /** What a step enhancement's file ends with, whose companion faults the assembly keeps as the fragment's. */
    private static final String ENHANCEMENT_FILE = ".step-enhancement.yaml";

    /**
     * The manifest, when the pack holds a readable one whose preset entries name their own files; otherwise empty, and
     * an unreadable or misplaced manifest is a violation.
     */
    private static Optional<PackManifestDso> manifest(
            Path packDir, SerializationStrategy strategy, List<PackElementViolation> violations) {
        if (!PackFileSet.unlinkedFile(packDir, PackFileSet.MANIFEST)) {
            return Optional.empty();
        }
        Path manifestFile = packDir.resolve(PackFileSet.MANIFEST);
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
                        new PackElementViolation(PackFileSet.MANIFEST, PackCodes.ARTIFACT_SCHEMA_ERROR, reason)));
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
     * The store half's assembly of {@link PackAssembly#assembly(PackIdentifier, FilesystemPackStore.PackRead, Map)} given
     * the pack's graph documents too, with the faults the store half meets on the way (#554, S7, WP12): what the
     * composing read answers from, the first of them as {@code pack_unreadable}, and what the per-artifact reads hold.
     * A registration and validate mode judge the one read's rationale instead, in which the content rules state these
     * faults ({@code StorageRulesets.verdict}); the store half's statement of the same fault is not repeated there.
     * Nothing is thrown for such a fault. Before the existing assembly it
     *
     * <ol>
     *   <li>judges the facts of the read's step enhancements, their companions and the graph edges (no orphan set: the
     *       read lists no unnamed file; the edge rules are not judged over a graph read that has a violation, which is
     *       refused on its own);
     *   <li>hands {@link PackAssembly} the files it leaves out, which then shapes the read (each {@code companion}
     *       violation of a step enhancement file kept under the kind {@code prompt_fragment_invalid}): each file whose
     *       id repeats (the second in path order), so that {@code Pack.guardKind}'s same-id rule never breaches at
     *       {@code build()} (operator ruling A2), and each mission type whose step repeats a substep id: it is recorded
     *       under the kind
     *       {@code duplicate_step_id} at its file, after the violations of the existing assembly, the code and file
     *       validate mode reports (FR-004), never as the domain's generic refusal;
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
            List<Reason> warnings) {
        return assembly(pack, read, places, graphs, warnings, violation -> {});
    }

    /**
     * The store half's assembly ({@link #assembly(PackIdentifier, FilesystemPackStore.PackRead, Map,
     * FilesystemPackStore.GraphRead, List)}), handing {@code storeOnly} each fault only the store half meets, which no
     * reader or rule states (#554, A-M1, WP12 review MAJOR-2): a template read twice (row 16), a value-object breach
     * (row 17), and a companion the store cannot read (row 19; a reference or an output template that does not close,
     * row 18, is the PACK ruleset's since #355), a step enhancement's under {@code prompt_fragment_invalid} as the assembly keeps it. The rule families
     * it judges for the composing read (the step enhancements, {@code duplicate_step_id}, the prompt slots, the step
     * links and the substep delegations) are not among them: the content rules state those.
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param graphs what the store read of the pack's graph documents
     * @param warnings receives the load findings that do not refuse the pack
     * @param storeOnly receives each fault only the store half meets
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            FilesystemPackStore.GraphRead graphs,
            List<Reason> warnings,
            Consumer<PackElementViolation> storeOnly) {
        return assembly(pack, read, places, graphs, warnings, storeOnly, facts -> {});
    }

    /**
     * The store half's assembly, handing {@code storeOnly} each fault only the store half meets and {@code references}
     * what the reference closure judges of the shaped read the aggregate is built from (#355).
     *
     * @param pack the pack the artifacts belong to
     * @param read what the store read
     * @param places receives each added artifact's pack-relative file
     * @param graphs what the store read of the pack's graph documents
     * @param warnings receives the load findings that do not refuse the pack
     * @param storeOnly receives each fault only the store half meets
     * @param references receives the reference closure's facts
     * @return the builder, ready to build
     */
    static Pack.Builder assembly(
            PackIdentifier pack,
            FilesystemPackStore.PackRead read,
            Map<Urn, String> places,
            FilesystemPackStore.GraphRead graphs,
            List<Reason> warnings,
            Consumer<PackElementViolation> storeOnly,
            Consumer<ReferenceFacts> references) {
        read.storeOnly()
                .forEach(violation -> storeOnly.accept(
                        FilesystemPackStore.COMPANION_KIND.equals(violation.kind())
                                        && violation.file().endsWith(ENHANCEMENT_FILE)
                                ? new PackElementViolation(
                                        violation.file(), PackCodes.PROMPT_FRAGMENT_INVALID, violation.reason())
                                : violation));
        List<StepEnhancementFault.Finding> faults = FragmentRules.judge(
                PackAssembly.stepEnhancementFacts(read, graphs.documents()),
                graphs.violations().isEmpty());
        List<PackElementViolation> repeats = new ArrayList<>();
        Set<String> repeating = new LinkedHashSet<>();
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            List<PackElementViolation> found =
                    StepRules.repeatedSubstepIds(PackAssembly.missionTypeFacts(stored.file(), stored.dso())).stream()
                            .map(StoreRead::heldBack)
                            .toList();
            if (!found.isEmpty()) {
                repeating.add(stored.file());
                repeats.addAll(found);
            }
        }
        Set<String> withheld = new LinkedHashSet<>();
        for (StepEnhancementFault.Finding fault : faults) {
            if (fault.rule() == StepEnhancementFault.STEM_REPEATED) {
                withheld.add(fault.path());
            }
        }
        Pack.Builder builder = PackAssembly.assembly(pack, read, places, withheld, repeating, storeOnly, references);
        repeats.forEach(builder::violation);
        recordPromptSlotFaults(read, builder, warnings);
        faults.stream()
                .map(fault -> new PackElementViolation(fault.path(), PackCodes.PROMPT_FRAGMENT_INVALID, fault.reason()))
                .forEach(builder::violation);
        missionStepViolations(read, places, graphs).forEach(builder::violation);
        substepDelegationViolations(read, places, graphs).forEach(builder::violation);
        return builder;
    }

    /**
     * Each fault of {@link MissionStepLinkRule} over the mission types the assembly holds (one the domain refuses is
     * refused on its own and not judged) and the edges of the readable graph documents, at its file. When a graph
     * document could not be read the rule is not judged: the read reports that document, and a step would otherwise
     * lack an edge the pack may well hold.
     */
    private static List<PackElementViolation> missionStepViolations(
            FilesystemPackStore.PackRead read, Map<Urn, String> places, FilesystemPackStore.GraphRead graphs) {
        if (!graphs.violations().isEmpty()) {
            return List.of();
        }
        return MissionStepLinkRule.judge(read.descriptor().name(), heldTypes(read, places), edgesOf(graphs)).stream()
                .map(fault -> new PackElementViolation(fault.file(), PackCodes.MISSION_STEP_UNLINKED, fault.reason()))
                .toList();
    }

    /**
     * Each fault of {@link SubstepDelegationRule} over the mission types the assembly holds, the artifacts it holds and
     * the edges of the readable graph documents, at the mission type file (FR-005). When a graph document could not be
     * read the rule is not judged: the read reports that document, and a candidate would otherwise lack an edge the pack
     * may well hold.
     */
    private static List<PackElementViolation> substepDelegationViolations(
            FilesystemPackStore.PackRead read, Map<Urn, String> places, FilesystemPackStore.GraphRead graphs) {
        if (!graphs.violations().isEmpty()) {
            return List.of();
        }
        Set<String> held = new HashSet<>();
        places.keySet().forEach(urn -> held.add(urn.toString()));
        return SubstepDelegationRule.judge(read.descriptor().name(), heldTypes(read, places), held, edgesOf(graphs))
                .stream()
                .map(fault -> new PackElementViolation(
                        fault.file(),
                        SubstepDelegationRule.UNRESOLVED.equals(fault.code())
                                ? PackCodes.SUBSTEP_DELEGATION_UNRESOLVED
                                : PackCodes.SUBSTEP_DELEGATION_UNSCOPED,
                        fault.reason()))
                .toList();
    }

    /**
     * A mission type the store half withholds for a substep id it repeats ({@link StepRules#repeatedSubstepIds}): the
     * rule's finding recorded on the aggregate at the type's file, under the rule's code and in its words, so the
     * composing read refuses the pack with the content rule's answer (FR-004, S7).
     */
    private static PackElementViolation heldBack(Reason finding) {
        return new PackElementViolation(finding.location().file(), finding.code(), finding.detail());
    }

    /**
     * The facts of the mission types the assembly holds: a type the domain refuses is refused on its own and is no fact.
     */
    private static List<MissionTypeFacts> heldTypes(FilesystemPackStore.PackRead read, Map<Urn, String> places) {
        List<MissionTypeFacts> types = new ArrayList<>();
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            MissionTypeDso type = stored.dso();
            if (stored.file().equals(places.get(new Urn(NodeKind.MISSION_TYPE, type.id())))) {
                types.add(PackAssembly.missionTypeFacts(stored.file(), type));
            }
        }
        return types;
    }

    /** The edges of the readable graph documents, as written, in document order. */
    private static List<MissionStepLinkRule.Edge> edgesOf(FilesystemPackStore.GraphRead graphs) {
        List<MissionStepLinkRule.Edge> edges = new ArrayList<>();
        for (GraphDocument document : graphs.documents()) {
            for (GraphDocument.Edge edge : document.edges()) {
                edges.add(new MissionStepLinkRule.Edge(document.file(), edge.source(), edge.target(), edge.relation()));
            }
        }
        return edges;
    }

    /**
     * Judges the slots of every step prompt of the stored mission types (FR-007) and records each fault at the prompt's
     * pack-relative file: a blocking fault as a violation, a warning on {@code warnings}, never on the violations
     * (A4). The text parsed is the canonical text of the prompt document the store read into the mission type
     * ({@code MissionTypeDso.Step#prompt}), frontmatter included (A5, A11): the same text validate mode parses. A step
     * with no prompt has nothing to judge.
     */
    private static void recordPromptSlotFaults(
            FilesystemPackStore.PackRead read, Pack.Builder builder, List<Reason> warnings) {
        boolean versioned =
                PromptSlotRules.versioned(read.descriptor().toDescriptor().schemaVersion());
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            List<MissionTypeDso.Step> steps = new ArrayList<>(stored.dso().steps());
            steps.addAll(stored.dso().onDemandSteps());
            for (MissionTypeDso.Step step : steps) {
                if (step.promptPath().isEmpty() || step.prompt().isEmpty()) {
                    continue;
                }
                String file = PackAssembly.companionFile(
                        stored.file(), step.promptPath().get());
                PromptSlots.Parse parse =
                        PromptSlots.parse(MarkdownCodec.write(step.prompt().get()));
                for (PromptSlotRules.Fault fault : PromptSlotRules.faults(parse, versioned)) {
                    if (fault.code().severity() == Severity.VIOLATION) {
                        builder.violation(new PackElementViolation(file, fault.code(), fault.reason()));
                    } else {
                        warnings.add(PromptSlotRules.finding(file, fault));
                    }
                }
            }
        }
    }
}
