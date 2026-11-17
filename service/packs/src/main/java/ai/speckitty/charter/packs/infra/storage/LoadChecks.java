package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.StepEnhancementFault;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The load's own rules over the store's read, judged beside the Spring-free {@link PackAssembly} so that the whole-pack
 * load, the composing read and the manifest's derived lists hold the same artifacts and report the same faults (#554,
 * T023). Staging inside the single pull request (R4 b): the content rules judge the one read from WP07, and WP12 deletes
 * this class with the load's second judgement. It is a plain class, not a bean, so the reader that derives the manifest
 * reaches no framework ({@code THE_PACKS_LOAD_REACHES_NO_FRAMEWORK}).
 */
final class LoadChecks {

    private LoadChecks() {}

    /**
     * The assembly of {@link PackAssembly#assembly(PackIdentifier, FilesystemPackStore.PackRead, Map)} given the pack's
     * graph documents too, judged by the load's rules until the content rules judge the one read (#554, WP07, WP12): the
     * one seat of the step enhancement and fragment faults (FR-004 to FR-006), reached by the
     * whole-pack load, and through it the registration, and by every composing read ({@code assemble}, so {@code readable}).
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
            List<PackFinding> warnings) {
        List<StepEnhancementFault.Finding> faults = PackPolicy.stepEnhancementFaults(
                PackAssembly.stepEnhancementFacts(read, graphs),
                graphs.violations().isEmpty());
        List<PackElementViolation> repeats = new ArrayList<>();
        Set<String> repeating = new LinkedHashSet<>();
        for (FilesystemPackStore.Stored<MissionTypeDso> stored : read.elements(MissionTypeBinding.INSTANCE)) {
            List<PackElementViolation> found =
                    PackPolicy.substepIdViolations(PackAssembly.missionTypeFacts(stored.file(), stored.dso()));
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
        Pack.Builder builder = PackAssembly.assembly(pack, read, places, withheld, repeating);
        repeats.forEach(builder::violation);
        recordPromptSlotFaults(read, builder, warnings);
        faults.stream().map(PackPolicy::violationOf).forEach(builder::violation);
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
            FilesystemPackStore.PackRead read, Pack.Builder builder, List<PackFinding> warnings) {
        Optional<String> version = read.descriptor().toDescriptor().schemaVersion();
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
                for (PackPolicy.PromptSlotFault fault : PackPolicy.promptSlotFaults(parse, version)) {
                    if (fault.code().severity() == Severity.VIOLATION) {
                        builder.violation(PackPolicy.violationOf(file, fault));
                    } else {
                        warnings.add(PackPolicy.findingOf(file, fault));
                    }
                }
            }
        }
    }
}
