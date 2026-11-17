package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.MissionStepLinkRule;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.SubstepDelegationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The content rules of a pack's mission types over its graph (#554, FR-001, T033; slice F): each step has its
 * {@code requires} edge, and each substep's delegation names an artifact the pack holds and the step's action scopes.
 * Each is a kernel rule over the facts of the one read ({@link PackContents}), through slice F's pure functions
 * ({@link MissionStepLinkRule}, {@link SubstepDelegationRule}); it reads no file and knows no surface, and states each
 * fault as one {@link Reason}.
 *
 * <p>Both read the edges of the pack's graph documents, so neither is judged when a graph document could not be read: a
 * step or a candidate would otherwise lack an edge the pack may well hold. A mission type that repeats a substep id is
 * not held ({@link StepRules#SUBSTEP_ID_REPEATED} reports it), so neither judges it.
 */
public final class MissionTypeRules {

    /**
     * A step with no authored {@code requires} edge to its action, or a stray step edge: one
     * {@link PackCodes#MISSION_STEP_UNLINKED} per fault ({@link MissionStepLinkRule}), at the mission type's file or
     * the stray edge's graph document, per type in file order.
     */
    public static final Rule<PackContents> STEP_UNLINKED =
            ((Rule<PackContents>) MissionTypeRules::unlinkedSteps).when(MissionTypeRules::graphsRead);

    /**
     * A substep's delegation candidate that is no artifact of its kind in the pack
     * ({@link PackCodes#SUBSTEP_DELEGATION_UNRESOLVED}), or that the step's action has no {@code scope} edge to
     * ({@link PackCodes#SUBSTEP_DELEGATION_UNSCOPED}): one finding per candidate ({@link SubstepDelegationRule}), at the
     * mission type's file and the candidate's place.
     */
    public static final Rule<PackContents> SUBSTEP_DELEGATION =
            ((Rule<PackContents>) MissionTypeRules::delegations).when(MissionTypeRules::graphsRead);

    /** Every mission type rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(STEP_UNLINKED).must(SUBSTEP_DELEGATION);

    private MissionTypeRules() {}

    static List<Reason> unlinkedSteps(PackContents contents) {
        return MissionStepLinkRule.judge(ownName(contents), heldTypes(contents), edgesOf(contents)).stream()
                .map(fault -> Findings.at(
                        PackCodes.MISSION_STEP_UNLINKED,
                        fault.file(),
                        List.of(),
                        Wording.origin(contents.root().name(), fault.file()) + ": " + fault.reason()))
                .toList();
    }

    static List<Reason> delegations(PackContents contents) {
        Set<String> held = contents.artifacts().stream()
                .map(artifact -> artifact.kind() + ":" + artifact.id())
                .collect(Collectors.toSet());
        return SubstepDelegationRule.judge(ownName(contents), heldTypes(contents), held, edgesOf(contents)).stream()
                .map(fault -> Findings.at(
                        SubstepDelegationRule.UNRESOLVED.equals(fault.code())
                                ? PackCodes.SUBSTEP_DELEGATION_UNRESOLVED
                                : PackCodes.SUBSTEP_DELEGATION_UNSCOPED,
                        fault.file(),
                        fault.place(),
                        Wording.origin(contents.root().name(), fault.file()) + ": " + fault.reason()))
                .toList();
    }

    /** The mission types the pack holds: one whose step repeats a substep id is refused on its own. */
    static List<MissionTypeFacts> heldTypes(PackContents contents) {
        return contents.missionTypes().stream()
                .filter(type -> !type.repeatsASubstepId())
                .toList();
    }

    /** The name the pack's descriptor declares, else its configured name. */
    static String ownName(PackContents contents) {
        return contents.descriptor() instanceof FileRead.Read<PackDescriptor> descriptor
                ? descriptor.value().name()
                : contents.root().name();
    }

    /** The edges of every graph document that was read, as written, in merge order: the shards, then the fragment. */
    static List<MissionStepLinkRule.Edge> edgesOf(PackContents contents) {
        List<MissionStepLinkRule.Edge> edges = new ArrayList<>();
        for (FileRead<RawGraph> document : documents(contents)) {
            if (document instanceof FileRead.Read<RawGraph> read) {
                for (RawEdge edge : read.value().edges()) {
                    edges.add(new MissionStepLinkRule.Edge(
                            read.value().file(), edge.source(), edge.target(), edge.relation()));
                }
            }
        }
        return edges;
    }

    /** Whether every graph document of the pack was read: one that could not be read is refused on its own. */
    static boolean graphsRead(PackContents contents) {
        return documents(contents).stream().noneMatch(FileRead.Unreadable.class::isInstance);
    }

    /** The graph documents of the pack, in merge order: the shards, then the fragment. */
    static List<FileRead<RawGraph>> documents(PackContents contents) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        return documents;
    }
}
