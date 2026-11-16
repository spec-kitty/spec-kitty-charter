package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.rules.DescriptorChecks;
import ai.speckitty.charter.packs.domain.rules.GraphBinding;
import ai.speckitty.charter.packs.domain.rules.GraphBinding.GraphBuild;
import ai.speckitty.charter.packs.domain.rules.Paths;
import ai.speckitty.charter.packs.domain.rules.Precedence;
import ai.speckitty.charter.packs.domain.rules.Wording;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single owner of the pack rules: it chooses every code and writes every text. It is pure; an adapter reads
 * the files into {@link PackContents} and this class judges them. Only this class and the code enums name the
 * pack-level constants (ArchUnit enforces it).
 */
public final class PackPolicy {

    private static final String PACK_YAML = "pack.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";

    /** The rules that look at the pack as a whole; they are attached to {@code pack.yaml} and ordered last. */
    private static final Set<ReasonCode> PACK_WIDE = PackFindingCode.PLACEMENT;

    private static final String STEP_ENHANCEMENT = "step_enhancement";

    /** The descriptor {@code schema_version} that makes a slot that holds text a refusal (ADR-0066 version gate). */
    private static final String VERSIONED_SCHEMA = "1.0";

    private static final String UNVERSIONED_WARNING =
            "; the descriptor declares no schema_version, so this is a warning";

    private static final String EDGES = "edges";

    /** The finding text of an authored edge to or from a guard or a lifecycle; see {@link GraphBinding#NOT_AUTHORED}. */
    public static final String NOT_AUTHORED = GraphBinding.NOT_AUTHORED;

    /** The finding text of a term as an edge endpoint; see {@link GraphBinding#TERM_ENDPOINT}. */
    public static final String TERM_ENDPOINT = GraphBinding.TERM_ENDPOINT;

    /** The plan's precedence, with the pack-wide rules as their own group ({@link Precedence#comparator}). */
    private static final Comparator<PackFinding> PRECEDENCE = Precedence.comparator(PACK_WIDE);

    private PackPolicy() {}

    /**
     * The level rules (issue 48): a missing level is a warning, a different level is blocking.
     *
     * <p>They fire only on an absent or a read descriptor, never on an unreadable one, and never for a bare path,
     * which has no configured level to compare with.
     *
     * @param name the pack's configured name
     * @param configured the configured level; empty for a bare path
     * @param descriptor what {@code pack.yaml} gave
     * @return the level findings, attached to {@code pack.yaml} with an empty place
     */
    public static List<PackFinding> level(
            String name, Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor) {
        if (configured.isEmpty()) {
            return List.of();
        }
        String lvl = configured.get().token();
        return switch (descriptor) {
            case FileRead.Absent<PackDescriptor> absent ->
                List.of(levelFinding(
                        PackFindingCode.PACK_LEVEL_MISSING,
                        lvl + " pack " + name + " has no pack.yaml; the configured level " + lvl + " is used"));
            case FileRead.Unreadable<PackDescriptor> unreadable -> List.of();
            case FileRead.Read<PackDescriptor> read -> declared(name, configured.get(), read.value());
        };
    }

    private static List<PackFinding> declared(String name, PackLevel configured, PackDescriptor descriptor) {
        Optional<PackLevel> declared = descriptor.declaredLevel();
        if (declared.isEmpty()) {
            return List.of(levelFinding(
                    PackFindingCode.PACK_LEVEL_MISSING,
                    "pack.yaml of " + name + " has no pack_level; the configured level " + configured.token()
                            + " is used"));
        }
        if (declared.get() != configured) {
            return List.of(levelFinding(
                    PackFindingCode.PACK_LEVEL_MISMATCH,
                    "Pack '" + name + "' is configured as " + configured.token() + " but declares pack_level "
                            + declared.get().token()));
        }
        return List.of();
    }

    private static PackFinding levelFinding(ReasonCode code, String detail) {
        return new PackFinding(code, PACK_YAML, List.of(), detail);
    }

    /**
     * Runs every rule: the level rules, the descriptor rules, the schema and parse findings of unreadable files (the
     * load path's and, for a full read, every other classified file's), the graph rules of the graph documents that
     * were read, and the step, artifact and asset rules of a full read; and warns {@code file_not_held} once for each
     * file the store does not hold, whose contents no rule reads (#554, open case 6).
     *
     * @param contents what the adapter read
     * @return the verdict, with the findings in precedence order
     */
    public static PackVerdict judge(PackContents contents) {
        String name = contents.root().name();
        List<PackFinding> findings =
                new ArrayList<>(level(name, contents.root().configuredLevel(), contents.descriptor()));
        switch (contents.descriptor()) {
            case FileRead.Unreadable<PackDescriptor> bad -> findings.addAll(unreadable(name, bad));
            case FileRead.Read<PackDescriptor> read -> findings.addAll(DescriptorChecks.findings(name, read.value()));
            case FileRead.Absent<PackDescriptor> absent -> {}
        }
        findings.addAll(orgFragmentRules(name, contents));
        for (FileRead<RawGraph> document : documents(contents)) {
            switch (document) {
                case FileRead.Unreadable<RawGraph> bad -> findings.addAll(unreadable(name, bad));
                case FileRead.Read<RawGraph> read ->
                    findings.addAll(GraphBinding.graph(name, read.value()).findings());
                case FileRead.Absent<RawGraph> absent -> {}
            }
        }
        for (FileRead.Unreadable<?> file : contents.unreadableFiles()) {
            findings.addAll(unreadable(name, file));
        }
        findings.addAll(artifactRules(contents.artifacts()));
        findings.addAll(assetRules(contents.assets(), refusedFiles(contents)));
        findings.addAll(stepEnhancementFindings(name, contents.stepEnhancements(), graphsRead(contents)));
        findings.addAll(promptSlotFindings(contents));
        findings.addAll(endpointFindings(name, contents));
        findings.addAll(missionStepFindings(name, contents));
        findings.addAll(substepIdRules(contents.missionTypes()));
        findings.addAll(substepDelegationFindings(name, contents));
        findings.addAll(notHeldFindings(contents.notHeld()));
        findings.sort(PRECEDENCE);
        return new PackVerdict(contents.files(), findings);
    }

    /**
     * One {@code file_not_held} warning per file the store does not hold, excluded or unmodelled, in the words a
     * registration uses (#554, Q1, open case 6): what counts is that the file is not held, not why. The read judged
     * nothing in it.
     */
    private static List<PackFinding> notHeldFindings(List<NotHeldFile> notHeld) {
        return notHeld.stream().map(NotHeldFile::finding).toList();
    }

    // ---- step enhancements and prompt fragments -------------------------------------------------

    /** The rules that read the pack's graph documents; they are not judged when a graph document could not be read. */
    private static final Set<StepEnhancementFault> EDGE_RULES = EnumSet.of(
            StepEnhancementFault.EDGE_COUNT,
            StepEnhancementFault.EDGE_TARGET_NOT_STEP,
            StepEnhancementFault.OVERRIDES_EDGE,
            StepEnhancementFault.DIRECTORY_MISMATCH);

    /**
     * Judges one pack's step enhancements, their fragments and the edges of their graph documents (FR-004 to FR-006).
     * A graph document that could not be read is refused on its own, so when {@code edgesRead} is {@code false} the
     * rules that need its edges are not judged: an enhancement would otherwise lack an edge the pack may well hold.
     *
     * @param facts the pack's step enhancement facts
     * @param edgesRead whether every graph document of the pack was read
     * @return every fault, in the rules' order: pack-relative path, then rule order
     */
    public static List<StepEnhancementFault.Finding> stepEnhancementFaults(
            StepEnhancementFacts facts, boolean edgesRead) {
        return StepEnhancementRules.judge(facts).stream()
                .filter(fault -> edgesRead || !EDGE_RULES.contains(fault.rule()))
                .toList();
    }

    /**
     * The validate-mode finding of one fault: the one code {@link PackCodes#PROMPT_FRAGMENT_INVALID}, at the file
     * the rule names.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param fault the fault
     * @return the blocking finding, with an empty place
     */
    public static PackFinding findingOf(String packName, StepEnhancementFault.Finding fault) {
        return new PackFinding(
                PackCodes.PROMPT_FRAGMENT_INVALID,
                fault.path(),
                List.of(),
                origin(packName, fault.path()) + ": " + fault.reason());
    }

    /**
     * The load violation of one fault: a violation of the pack at the fault's file, of the wire token of its code.
     * Nothing is thrown for it, so a registration lists every fault at its file and a composing read names the first.
     *
     * @param fault the fault
     * @return the violation
     */
    public static PackElementViolation violationOf(StepEnhancementFault.Finding fault) {
        return new PackElementViolation(fault.path(), PackCodes.PROMPT_FRAGMENT_INVALID, fault.reason());
    }

    /**
     * Judges the step enhancement facts of a validate-mode read and answers every fault as a finding.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param facts the pack's step enhancement facts
     * @param edgesRead whether every graph document of the pack was read
     * @return one finding per fault, blocking, in the rules' order
     */
    public static List<PackFinding> stepEnhancementFindings(
            String packName, StepEnhancementFacts facts, boolean edgesRead) {
        return stepEnhancementFaults(facts, edgesRead).stream()
                .map(fault -> findingOf(packName, fault))
                .toList();
    }

    /**
     * Judges the step enhancement facts of a load and answers every fault as a violation.
     *
     * @param facts the pack's step enhancement facts
     * @return one violation per fault, in the rules' order
     */
    public static List<PackElementViolation> stepEnhancementViolations(StepEnhancementFacts facts) {
        return stepEnhancementFaults(facts, true).stream()
                .map(PackPolicy::violationOf)
                .toList();
    }

    // ---- the endpoint rule: the source of an overrides edge, then the table --------------------------------------

    /**
     * Judges the edges of every graph document that was read by {@link RelationshipEndpointRule}, as the load does: one
     * blocking finding per refused edge, at its document and its place {@code /edges/<index>}. An {@code overrides}
     * edge whose source kind is not activatable (FR-014) is {@link PackCodes#OVERRIDES_SOURCE_NOT_ACTIVATABLE};
     * an edge whose relation does not allow its endpoint kinds is {@link PackCodes#RELATIONSHIP_ENDPOINT_NOT_ALLOWED}.
     * The source findings come first, then the table's, each in edge order, as the load lists them. A document that
     * could not be read is refused on its own and not judged here.
     */
    private static List<PackFinding> endpointFindings(String packName, PackContents contents) {
        RelationshipEndpointRule.Scope scope = new RelationshipEndpointRule.Scope(
                contents.descriptor() instanceof FileRead.Read<PackDescriptor> descriptor
                        ? descriptor.value().name()
                        : packName,
                contents.stepEnhancements().enhancements().stream()
                        .map(StepEnhancementFacts.Enhancement::id)
                        .collect(Collectors.toSet()));
        List<PackFinding> sources = new ArrayList<>();
        List<PackFinding> table = new ArrayList<>();
        for (FileRead<RawGraph> document : documents(contents)) {
            if (!(document instanceof FileRead.Read<RawGraph> read)) {
                continue;
            }
            RawGraph raw = read.value();
            for (int i = 0; i < raw.edges().size(); i++) {
                RawEdge edge = raw.edges().get(i);
                Optional<RelationshipEndpointRule.Refusal> refusal = RelationshipEndpointRule.refusal(
                        new RelationshipEndpointRule.Edge(raw.file(), edge.source(), edge.target(), edge.relation()),
                        scope);
                if (refusal.isPresent()) {
                    boolean byTable =
                            RelationshipEndpointRule.CODE.equals(refusal.get().code());
                    (byTable ? table : sources)
                            .add(new PackFinding(
                                    byTable
                                            ? PackCodes.RELATIONSHIP_ENDPOINT_NOT_ALLOWED
                                            : PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE,
                                    raw.file(),
                                    List.of(EDGES, Integer.toString(i)),
                                    origin(packName, raw.file()) + ": "
                                            + refusal.get().reason()));
                }
            }
        }
        List<PackFinding> findings = new ArrayList<>(sources);
        findings.addAll(table);
        return findings;
    }

    /**
     * Judges the pack's mission types over the edges of every graph document that was read by
     * {@link MissionStepLinkRule}, as the load does: one blocking finding per fault, at the mission type file of an
     * unlinked step or at the graph document of a stray edge. When a graph document could not be read the rule is not
     * judged, as a step would otherwise lack an edge the pack may well hold.
     */
    private static List<PackFinding> missionStepFindings(String packName, PackContents contents) {
        if (!graphsRead(contents)) {
            return List.of();
        }
        return MissionStepLinkRule.judge(ownName(packName, contents), heldTypes(contents), edgesOf(contents)).stream()
                .map(fault -> new PackFinding(
                        PackCodes.MISSION_STEP_UNLINKED,
                        fault.file(),
                        List.of(),
                        origin(packName, fault.file()) + ": " + fault.reason()))
                .toList();
    }

    /**
     * Judges the pack's mission types by {@link SubstepDelegationRule}, as the load does: one blocking finding per
     * candidate that is no artifact of its kind in the pack, or that the step's action has no {@code scope} edge to, at
     * the mission type file and the candidate's place. When a graph document could not be read the rule is not judged,
     * as a candidate would otherwise lack an edge the pack may well hold.
     */
    private static List<PackFinding> substepDelegationFindings(String packName, PackContents contents) {
        if (!graphsRead(contents)) {
            return List.of();
        }
        Set<String> held = contents.artifacts().stream()
                .map(artifact -> artifact.kind() + ":" + artifact.id())
                .collect(Collectors.toSet());
        return SubstepDelegationRule.judge(ownName(packName, contents), heldTypes(contents), held, edgesOf(contents))
                .stream()
                .map(fault -> new PackFinding(
                        SubstepDelegationRule.UNRESOLVED.equals(fault.code())
                                ? PackCodes.SUBSTEP_DELEGATION_UNRESOLVED
                                : PackCodes.SUBSTEP_DELEGATION_UNSCOPED,
                        fault.file(),
                        fault.place(),
                        origin(packName, fault.file()) + ": " + fault.reason()))
                .toList();
    }

    /**
     * A substep id declared twice within one step of a mission type (FR-004): one finding per repeated id, at its
     * second occurrence. The load refuses the type in its domain.
     */
    private static List<PackFinding> substepIdRules(List<MissionTypeFacts> types) {
        List<PackFinding> findings = new ArrayList<>();
        for (MissionTypeFacts type : types) {
            Set<String> seen = new LinkedHashSet<>();
            Set<String> reported = new LinkedHashSet<>();
            for (MissionTypeFacts.SubstepFacts substep : type.substeps()) {
                String key = substep.list() + "/" + substep.stepIndex() + "/" + substep.id();
                if (!seen.add(key) && reported.add(key)) {
                    List<String> place = new ArrayList<>(substep.place());
                    place.add("id");
                    findings.add(new PackFinding(
                            PackCodes.DUPLICATE_STEP_ID,
                            type.file(),
                            place,
                            type.file() + ": substep id '" + substep.id() + "' is declared more than once in the step '"
                                    + substep.stepId() + "'"));
                }
            }
        }
        return findings;
    }

    /**
     * The load's record of a mission type whose step repeats a substep id (FR-004): one violation of the kind
     * {@code duplicate_step_id} per repeated id, at the type's file, with the text validate mode gives the finding. The
     * load holds no such type.
     *
     * @param type the facts of one stored mission type
     * @return its violations; empty when no step repeats a substep id
     */
    public static List<PackElementViolation> substepIdViolations(MissionTypeFacts type) {
        return substepIdRules(List.of(type)).stream()
                .map(finding -> new PackElementViolation(finding.file(), PackCodes.DUPLICATE_STEP_ID, finding.detail()))
                .toList();
    }

    /** The mission types the load holds: one whose step repeats a substep id is refused, as its own violation. */
    private static List<MissionTypeFacts> heldTypes(PackContents contents) {
        return contents.missionTypes().stream()
                .filter(type -> !type.repeatsASubstepId())
                .toList();
    }

    /** The edges of every graph document that was read, as written, in document order. */
    private static List<MissionStepLinkRule.Edge> edgesOf(PackContents contents) {
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

    /** The name the pack's descriptor declares, else its configured name. */
    private static String ownName(String packName, PackContents contents) {
        return contents.descriptor() instanceof FileRead.Read<PackDescriptor> descriptor
                ? descriptor.value().name()
                : packName;
    }

    // ---- step prompt slots ------------------------------------------------------------------------

    /**
     * What {@link PackPolicy} concludes about one fault of a step prompt's slots: the finding code the descriptor's
     * version picks, and the reason without the file.
     *
     * @param code the finding code
     * @param reason what is wrong, naming the line of the marker; it does not name the file
     */
    public record PromptSlotFault(ReasonCode code, String reason) {
        /**
         * Checks the fields.
         *
         * @param code the finding code
         * @param reason the reason
         */
        public PromptSlotFault {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Judges the slots of one step prompt (FR-007): the faults of its {@link PromptSlots.Parse}, each as the finding
     * code the descriptor's version picks. A malformed marker is blocking whatever the version; a slot that holds text
     * is blocking when the descriptor declares {@code schema_version} {@code "1.0"} and a warning when it declares
     * none.
     *
     * @param parse the parse of the prompt's canonical text
     * @param schemaVersion the descriptor's {@code schema_version}; empty for an unversioned descriptor
     * @return one fault per fault of the parse, in the order found
     */
    public static List<PromptSlotFault> promptSlotFaults(PromptSlots.Parse parse, Optional<String> schemaVersion) {
        boolean versioned = schemaVersion.filter(VERSIONED_SCHEMA::equals).isPresent();
        List<PromptSlotFault> faults = new ArrayList<>();
        for (PromptSlots.Fault fault : parse.faults()) {
            String where = "line " + fault.line() + " of the canonical text: ";
            faults.add(
                    switch (fault.kind()) {
                        case MALFORMED -> new PromptSlotFault(PackCodes.PROMPT_SLOT_MALFORMED, where + fault.reason());
                        case NOT_EMPTY -> {
                            String slot = fault.slot()
                                    .map(id -> "slot " + id + " holds text")
                                    .orElse(fault.reason());
                            yield versioned
                                    ? new PromptSlotFault(PackCodes.PROMPT_SLOT_NOT_EMPTY, where + slot)
                                    : new PromptSlotFault(
                                            PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING,
                                            where + slot + UNVERSIONED_WARNING);
                        }
                    });
        }
        return faults;
    }

    /** The slot findings of a full read: the warnings and the blocking faults, by the version the descriptor declares. */
    private static List<PackFinding> promptSlotFindings(PackContents contents) {
        Optional<String> version = contents.descriptor() instanceof FileRead.Read<PackDescriptor> read
                ? read.value().schemaVersion()
                : Optional.empty();
        List<PackFinding> findings = new ArrayList<>();
        for (PackContents.PromptSlotFacts prompt : contents.promptSlots()) {
            promptSlotFaults(prompt.parse(), version).forEach(fault -> findings.add(findingOf(prompt.file(), fault)));
        }
        return findings;
    }

    /**
     * The finding of one slot fault, at the prompt file. A warning is listed apart from the violations: it never
     * refuses a load.
     *
     * @param file the prompt's pack-relative path
     * @param fault the fault
     * @return the finding, with an empty place
     */
    public static PackFinding findingOf(String file, PromptSlotFault fault) {
        return new PackFinding(fault.code(), file, List.of(), file + ": " + fault.reason());
    }

    /**
     * The load violation of one blocking slot fault: a violation of the pack at the prompt file, of the wire token of
     * its code. Nothing is thrown for it.
     *
     * @param file the prompt's pack-relative path
     * @param fault the fault
     * @return the violation
     * @throws IllegalArgumentException for the warning of an unversioned descriptor
     */
    public static PackElementViolation violationOf(String file, PromptSlotFault fault) {
        if (fault.code().severity() != Severity.VIOLATION) {
            throw new IllegalArgumentException(fault.code().value() + " is a warning, not a violation of a load fault");
        }
        return new PackElementViolation(file, fault.code(), fault.reason());
    }

    // ---- single-pack rules a schema cannot express ---------------------------------------------

    /**
     * An org pack is its fragment: when the pack is at the org level and {@code drg/fragment.yaml} is absent, the pack
     * has no graph to contribute (R16). The level is the configured one, or for a bare path the declared one. An
     * unreadable fragment is not a missing one; its own finding speaks.
     */
    private static List<PackFinding> orgFragmentRules(String name, PackContents contents) {
        if (!(contents.fragment() instanceof FileRead.Absent<RawGraph>) || !isOrgPack(contents)) {
            return List.of();
        }
        return List.of(new PackFinding(
                PackFindingCode.ORG_FRAGMENT_MISSING,
                PACK_YAML,
                List.of(),
                "org pack " + name + " has no " + FRAGMENT));
    }

    private static boolean isOrgPack(PackContents contents) {
        Optional<PackLevel> configured = contents.root().configuredLevel();
        if (configured.isPresent()) {
            return configured.get() == PackLevel.ORG;
        }
        return contents.descriptor() instanceof FileRead.Read<PackDescriptor> read
                && read.value()
                        .declaredLevel()
                        .filter(level -> level == PackLevel.ORG)
                        .isPresent();
    }

    /**
     * Whether the detail vocabulary has a row for a schema keyword; see {@link Wording#handles}.
     *
     * @param keyword the schema keyword
     * @return {@code true} when a violation of the keyword gets a text of its own, not the fixed fallback
     */
    public static boolean handles(String keyword) {
        return Wording.handles(keyword);
    }

    /** An artifact id is unique within its kind; the first file in path order owns it (core's {@code duplicate_id}).
     * Path order is pathlib's: segment by segment, so a directory sorts before a sibling that extends its name. */
    private static List<PackFinding> artifactRules(List<PackContents.ArtifactFacts> artifacts) {
        List<PackContents.ArtifactFacts> ordered = new ArrayList<>(artifacts);
        ordered.sort(Comparator.comparing(PackContents.ArtifactFacts::file, Precedence::comparePaths));
        Map<List<String>, String> owners = new LinkedHashMap<>();
        List<PackFinding> findings = new ArrayList<>();
        for (PackContents.ArtifactFacts artifact : ordered) {
            if (STEP_ENHANCEMENT.equals(artifact.kind())) {
                // A repeated step enhancement id is the step enhancement rules' STEM_REPEATED, at the same second file:
                // one finding per same-id file, as the load answers.
                continue;
            }
            String first = owners.putIfAbsent(List.of(artifact.kind(), artifact.id()), artifact.file());
            if (first != null) {
                findings.add(new PackFinding(
                        PackCodes.DUPLICATE_ARTIFACT_ID,
                        artifact.file(),
                        List.of(),
                        artifact.file() + ": " + artifact.kind() + " id '" + artifact.id() + "' is already declared in "
                                + first));
            }
        }
        return findings;
    }

    /**
     * The asset rules, in core's order: a path that escapes the assets root is reported and its MIME type is not
     * judged; an empty path or MIME type is the schema's business and is skipped. A sidecar is one fault (#554, R2 row
     * 13): an escape the read already refused at the sidecar is not reported a second time.
     */
    private static List<PackFinding> assetRules(List<PackContents.AssetFacts> assets, Set<String> refused) {
        List<PackFinding> findings = new ArrayList<>();
        for (PackContents.AssetFacts asset : assets) {
            if (asset.path().isEmpty()) {
                continue;
            }
            if (escapes(asset)) {
                if (!refused.contains(asset.file())) {
                    findings.add(assetFinding(
                            PackCodes.ASSET, asset, asset.file() + ": " + Paths.assetPathEscape(asset.path())));
                }
            } else if (!asset.mime().isEmpty()) {
                mimeProblem(asset)
                        .ifPresent(detail ->
                                findings.add(assetFinding(PackFindingCode.ASSET_MIME_INVALID, asset, detail)));
            }
        }
        return findings;
    }

    private static PackFinding assetFinding(ReasonCode code, PackContents.AssetFacts asset, String detail) {
        return new PackFinding(code, asset.file(), List.of(), detail);
    }

    /** The files the read refused, each already one finding of its own. */
    private static Set<String> refusedFiles(PackContents contents) {
        return contents.unreadableFiles().stream()
                .map(unreadable -> unreadable.file())
                .collect(Collectors.toSet());
    }

    /** A path that leaves the assets root as written ({@link PackRelativePaths#leavesRoot}), or once the adapter resolves it. */
    private static boolean escapes(PackContents.AssetFacts asset) {
        return !asset.containedInAssetsRoot() || PackRelativePaths.leavesRoot(asset.path());
    }

    private static Optional<String> mimeProblem(PackContents.AssetFacts asset) {
        String mime = asset.mime();
        if (!MediaTypes.wellFormed(mime)) {
            return Optional.of(asset.file() + ": asset mime '" + mime + "' is not a well-formed 'type/subtype' value");
        }
        return AssetMimeTable.expected(asset.path())
                .filter(expected -> !expected.type().equals(mime))
                .map(expected -> asset.file() + ": asset mime '" + mime + "' does not match the '"
                        + expected.extension() + "' extension (" + expected.type() + ")");
    }

    /** Whether every graph document of the load was read: an unreadable one is refused on its own. */
    private static boolean graphsRead(PackContents contents) {
        return documents(contents).stream().noneMatch(FileRead.Unreadable.class::isInstance);
    }

    /** The graph documents of a load, in merge order: the shards, then the fragment. */
    private static List<FileRead<RawGraph>> documents(PackContents contents) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        return documents;
    }

    // ---- graph building and the path faults -----------------------------------------------------

    /**
     * Builds the graph of one document; see {@link GraphBinding#graph}.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the document as written
     * @return the graph and the findings
     */
    public static GraphBuild graph(String packName, RawGraph raw) {
        return GraphBinding.graph(packName, raw);
    }

    /**
     * Builds the graph of the registered-relationship shard; see {@link GraphBinding#registeredGraph}.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the registered shard as written
     * @return the graph and the findings
     */
    public static GraphBuild registeredGraph(String packName, RawGraph raw) {
        return GraphBinding.registeredGraph(packName, raw);
    }

    /**
     * The fault of a fragment node whose {@code body_path} leaves the pack root; see {@link Paths#bodyPathEscape}.
     *
     * @param id the node's id
     * @param bodyPath its {@code body_path}, when written
     * @return the fault, or empty for a path that stays inside
     */
    public static Optional<String> bodyPathEscape(String id, Optional<String> bodyPath) {
        return Paths.bodyPathEscape(id, bodyPath);
    }

    // ---- schema and parse findings -------------------------------------------------------------

    /**
     * The findings of a file that could not be read; see {@link Wording#unreadable}.
     *
     * @param packName the pack's configured name
     * @param unreadable the file that could not be read
     * @return the findings, in precedence order
     */
    public static List<PackFinding> unreadable(String packName, FileRead.Unreadable<?> unreadable) {
        return Wording.unreadable(packName, unreadable);
    }

    /**
     * Turns schema violation facts into findings with the plan's detail vocabulary; see {@link Wording#schemaFindings}.
     *
     * @param packName the pack's configured name
     * @param violations the violations
     * @return the findings, in precedence order, with identical findings reported once
     */
    public static List<PackFinding> schemaFindings(String packName, List<SchemaViolation> violations) {
        return Wording.schemaFindings(packName, violations);
    }

    private static String origin(String packName, String file) {
        return Wording.origin(packName, file);
    }

    /** Python's {@code PurePath} order for {@code /}-separated paths; see {@link Precedence#comparePaths}. */
    static int comparePaths(String a, String b) {
        return Precedence.comparePaths(a, b);
    }
}
