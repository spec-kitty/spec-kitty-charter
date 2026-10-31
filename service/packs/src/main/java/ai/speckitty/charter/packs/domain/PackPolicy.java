package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.DoctrineGraph;
import ai.speckitty.charter.kernel.domain.DrgNode;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Pep440;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Tension;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.RawNode;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single owner of the pack rules: it chooses every code and writes every text. It is pure; an adapter reads
 * the files into {@link PackContents} and this class judges them. Only this class and the code enums name the
 * pack-level constants (ArchUnit enforces it).
 */
public final class PackPolicy {

    private static final String PACK_YAML = "pack.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";

    /** The rules that look at the pack as a whole; they are attached to {@code pack.yaml} and ordered last. */
    private static final Set<PackFindingCode> PACK_WIDE = EnumSet.of(
            PackFindingCode.PACK_LEVEL_MISSING,
            PackFindingCode.PACK_LEVEL_MISMATCH,
            PackFindingCode.ORG_FRAGMENT_MISSING);

    /** The schema keywords the detail vocabulary has a row for; every other keyword gets the fixed fallback text. */
    private static final Set<String> VOCABULARY_KEYWORDS = Set.of(
            "type",
            "additionalProperties",
            "required",
            "minLength",
            "pattern",
            "enum",
            "const",
            "minItems",
            "minimum",
            "maximum",
            "exclusiveMinimum");

    private static final String NODES = "nodes";
    private static final String EDGES = "edges";

    /**
     * Today's spelling of the tension relationship in a graph document (ADR-0036 Decision 4). The domain binds it to a
     * {@link Tension} held beside the relationships, never to a relation type (ADR-0028, ADR-0045); the writers spell
     * a tension back with it.
     */
    public static final String TENSION = "in_tension_with";

    /*
     * The key order of each document kind is the order of the bundled schema's `properties`
     * (contracts/packs/schemas/<kind>.schema.json): graph-shard, org-fragment and pack-descriptor.
     */
    private static final List<String> DESCRIPTOR_KEYS = List.of(
            "pack_id",
            "pack_version",
            "parent_pack",
            "name",
            "pack_level",
            "requires_core",
            "active_charter",
            "pack_drg");
    private static final List<String> SHARD_ROOT_KEYS =
            List.of("schema_version", "generated_at", "generated_by", NODES, EDGES);
    private static final List<String> FRAGMENT_ROOT_KEYS = List.of(NODES, EDGES);
    private static final List<String> SHARD_NODE_KEYS = List.of("urn", "kind", "label", "provenance", "tags");
    private static final List<String> FRAGMENT_NODE_KEYS = List.of("id", "kind", "title", "body_path");
    private static final List<String> SHARD_EDGE_KEYS =
            List.of("source", "target", "relation", "when", "reason", "impacts");
    private static final List<String> FRAGMENT_EDGE_KEYS = List.of("source", "target", "relation", "reason", "impacts");

    /** The plan's precedence: file group, then file name, then the place within the file. */
    static final Comparator<PackFinding> PRECEDENCE = Comparator.comparingInt(PackPolicy::group)
            .thenComparing(PackFinding::file, PackPolicy::comparePaths)
            .thenComparing((a, b) -> comparePlaces(a.file(), a.place(), b.place()));

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

    private static PackFinding levelFinding(PackFindingCode code, String detail) {
        return new PackFinding(code, PACK_YAML, List.of(), detail);
    }

    /**
     * Runs every rule: the level rules, the descriptor rules, the schema and parse findings of unreadable files (the
     * load path's and, for a full read, every other classified file's), the graph rules of the graph documents that
     * were read, and the step, artifact and asset rules of a full read.
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
            case FileRead.Read<PackDescriptor> read -> findings.addAll(descriptorRules(name, read.value()));
            case FileRead.Absent<PackDescriptor> absent -> {}
        }
        findings.addAll(orgFragmentRules(name, contents));
        for (FileRead<RawGraph> document : documents(contents)) {
            switch (document) {
                case FileRead.Unreadable<RawGraph> bad -> findings.addAll(unreadable(name, bad));
                case FileRead.Read<RawGraph> read ->
                    findings.addAll(graph(name, read.value()).findings());
                case FileRead.Absent<RawGraph> absent -> {}
            }
        }
        for (FileRead.Unreadable<?> file : contents.unreadableFiles()) {
            findings.addAll(unreadable(name, file));
        }
        findings.addAll(stepRules(contents.stepContracts()));
        findings.addAll(artifactRules(contents.artifacts()));
        findings.addAll(assetRules(contents.assets()));
        findings.sort(PRECEDENCE);
        return new PackVerdict(contents.files(), findings);
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
     * Whether the detail vocabulary has a row for a schema keyword.
     *
     * @param keyword the schema keyword
     * @return {@code true} when a violation of the keyword gets a text of its own, not the fixed fallback
     */
    public static boolean handles(String keyword) {
        return VOCABULARY_KEYWORDS.contains(keyword);
    }

    private static final String REQUIRES_CORE = "requires_core";

    /**
     * {@code requires_core} must be a PEP 440 specifier set (R3). An undeclared key never reaches here: the closed schema
     * makes its file unreadable (ADR-0036 decision 3).
     */
    private static List<PackFinding> descriptorRules(String name, PackDescriptor descriptor) {
        List<PackFinding> findings = new ArrayList<>();
        Optional<String> requiresCore = descriptor.requiresCore();
        if (requiresCore.isPresent() && !Pep440.isValidSpecifierSet(requiresCore.get())) {
            findings.add(new PackFinding(
                    PackFindingCode.REQUIRES_CORE_INVALID,
                    PACK_YAML,
                    List.of(REQUIRES_CORE),
                    "pack.yaml of " + name + ": requires_core '" + requiresCore.get()
                            + "' is not a PEP 440 version specifier"));
        }
        return findings;
    }

    /**
     * A step id declared twice in one contract: one finding per repeated id, at its second occurrence. Core's
     * "duplicate step IDs" check refuses the whole contract once; the place says where the first repeat is.
     */
    private static List<PackFinding> stepRules(List<PackContents.StepContractFacts> contracts) {
        List<PackFinding> findings = new ArrayList<>();
        for (PackContents.StepContractFacts contract : contracts) {
            Set<String> seen = new LinkedHashSet<>();
            Set<String> reported = new LinkedHashSet<>();
            for (int i = 0; i < contract.stepIds().size(); i++) {
                String id = contract.stepIds().get(i);
                if (!seen.add(id) && reported.add(id)) {
                    findings.add(new PackFinding(
                            PackFindingCode.DUPLICATE_STEP_ID,
                            contract.file(),
                            List.of("steps", Integer.toString(i), "id"),
                            contract.file() + ": step id '" + id + "' is declared more than once"));
                }
            }
        }
        return findings;
    }

    /** An artifact id is unique within its kind; the first file in path order owns it (core's {@code duplicate_id}).
     * Path order is pathlib's: segment by segment, so a directory sorts before a sibling that extends its name. */
    private static List<PackFinding> artifactRules(List<PackContents.ArtifactFacts> artifacts) {
        List<PackContents.ArtifactFacts> ordered = new ArrayList<>(artifacts);
        ordered.sort(Comparator.comparing(PackContents.ArtifactFacts::file, PackPolicy::comparePaths));
        Map<List<String>, String> owners = new LinkedHashMap<>();
        List<PackFinding> findings = new ArrayList<>();
        for (PackContents.ArtifactFacts artifact : ordered) {
            String first = owners.putIfAbsent(List.of(artifact.kind(), artifact.id()), artifact.file());
            if (first != null) {
                findings.add(new PackFinding(
                        PackFindingCode.DUPLICATE_ARTIFACT_ID,
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
     * judged; an empty path or MIME type is the schema's business and is skipped.
     */
    private static List<PackFinding> assetRules(List<PackContents.AssetFacts> assets) {
        List<PackFinding> findings = new ArrayList<>();
        for (PackContents.AssetFacts asset : assets) {
            if (asset.path().isEmpty()) {
                continue;
            }
            if (escapes(asset)) {
                findings.add(assetFinding(
                        PackFindingCode.ASSET_PATH_ESCAPE,
                        asset,
                        asset.file() + ": asset path '" + asset.path() + "' escapes the pack's assets/ root"));
            } else if (!asset.mime().isEmpty()) {
                mimeProblem(asset)
                        .ifPresent(detail ->
                                findings.add(assetFinding(PackFindingCode.ASSET_MIME_INVALID, asset, detail)));
            }
        }
        return findings;
    }

    private static PackFinding assetFinding(PackFindingCode code, PackContents.AssetFacts asset, String detail) {
        return new PackFinding(code, asset.file(), List.of(), detail);
    }

    /**
     * Core's {@code resolve_relative_path_within_root} judges the path as both a {@code PurePosixPath} and a
     * {@code PureWindowsPath}: absolute in either flavour, or a {@code ..} part in either, escapes. The adapter's
     * containment check cannot see the Windows forms on Linux, where {@code \} and {@code C:} are ordinary name
     * characters, so the domain reads them itself.
     */
    private static boolean escapes(PackContents.AssetFacts asset) {
        String path = asset.path();
        return !asset.containedInAssetsRoot() || isAbsolute(path) || hasDotDotPart(path);
    }

    /**
     * POSIX absolute (a leading {@code /}), or Windows absolute: a UNC prefix of two separators of either kind, or a
     * drive (any one character but a separator, and a colon) followed by a separator. {@code C:x} and a single leading {@code \} are not absolute.
     */
    private static boolean isAbsolute(String path) {
        return path.startsWith("/") || hasUncPrefix(path) || hasDriveRoot(path) || hasBackslashColonRoot(path);
    }

    /**
     * A quirk of Python 3.13's {@code ntpath.isabs}, which reads the normalized text: {@code \:\x} counts as a
     * drive-and-root form ({@code \} as the "drive" character) once a real segment follows; {@code \:\} alone,
     * or {@code \:\.}, does not.
     */
    private static boolean hasBackslashColonRoot(String path) {
        return path.startsWith("\\:")
                && path.length() > 2
                && isSeparator(path.charAt(2))
                && Arrays.stream(path.substring(3).split("[/\\\\]", -1)).anyMatch(s -> !s.isEmpty() && !".".equals(s));
    }

    private static boolean hasUncPrefix(String path) {
        return path.length() >= 2 && isSeparator(path.charAt(0)) && isSeparator(path.charAt(1));
    }

    private static boolean hasDriveRoot(String path) {
        int afterDrive = driveLength(path);
        return afterDrive > 0 && afterDrive < path.length() && isSeparator(path.charAt(afterDrive));
    }

    /** The length of a leading {@code <char>:} drive, or 0. */
    private static int driveLength(String path) {
        if (path.isEmpty()) {
            return 0;
        }
        int first = path.codePointAt(0);
        int width = Character.charCount(first);
        return !isSeparator(first) && width < path.length() && path.charAt(width) == ':' ? width + 1 : 0;
    }

    /** A {@code ..} between separators of either flavour, with a Windows drive taken off first. */
    private static boolean hasDotDotPart(String path) {
        String rest = path.substring(driveLength(path));
        return List.of(rest.split("[/\\\\]", -1)).contains("..");
    }

    private static boolean isSeparator(int c) {
        return c == '/' || c == '\\';
    }

    private static Optional<String> mimeProblem(PackContents.AssetFacts asset) {
        String mime = asset.mime();
        int slash = mime.indexOf('/');
        if (slash <= 0 || slash == mime.length() - 1) {
            return Optional.of(asset.file() + ": asset mime '" + mime + "' is not a well-formed 'type/subtype' value");
        }
        return AssetMimeTable.expected(asset.path())
                .filter(expected -> !expected.type().equals(mime))
                .map(expected -> asset.file() + ": asset mime '" + mime + "' does not match the '"
                        + expected.extension() + "' extension (" + expected.type() + ")");
    }

    private static int group(PackFinding finding) {
        if (PACK_WIDE.contains(finding.code())) {
            return 4;
        }
        String file = finding.file();
        if (PACK_YAML.equals(file)) {
            return 1;
        }
        if (FRAGMENT.equals(file)) {
            return 3;
        }
        return file.endsWith(".graph.yaml") && file.indexOf('/') < 0 ? 2 : 5;
    }

    /** The graph documents of a load, in merge order: the shards, then the fragment. */
    private static List<FileRead<RawGraph>> documents(PackContents contents) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        return documents;
    }

    // ---- graph building ------------------------------------------------------------------------

    /**
     * A graph built from facts, with what could not be bound.
     *
     * @param graph the nodes and edges that bound
     * @param findings one finding for each node or edge that did not bind, in document order
     */
    public record GraphBuild(DoctrineGraph graph, List<PackFinding> findings) {
        /**
         * Copies the findings.
         *
         * @param graph the graph
         * @param findings the findings
         */
        public GraphBuild {
            findings = List.copyOf(findings);
        }
    }

    /**
     * Builds the graph of one document and collects what does not bind: the URN binding, the tension edges, which
     * bind to {@link Tension}s beside the relationships (ADR-0028, ADR-0045), and the {@code impacts} rules, NaN and
     * the infinities included. Every declared node field is carried onto its {@link DrgNode} (ADR-0036).
     *
     * <p>A node or edge with a finding is left out of the graph, so the constructor checks of {@link Relationship}
     * and {@link Tension} stay programming-error guards and never fire. An edge reports its first fault only, checked
     * in the order the adapter's reader used: relation, then {@code impacts}, then the endpoints, then a self-loop.
     * The schema normally refuses these first; the domain check keeps the same text for a document that reaches here
     * anyway. The {@code impacts} strength is validated here but is not carried on the bound {@link Relationship}
     * (ADR-0025: the signed strength lives on the storage DSO, not on the domain type).
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the document as written
     * @return the graph and the findings
     */
    public static GraphBuild graph(String packName, RawGraph raw) {
        return build(packName, raw, false);
    }

    /**
     * Builds the graph of the registered-relationship shard, which holds structural relationships only: a tension is
     * authored in a pack's graph shards and never registered, so one found here is a finding and is not bound. The
     * store then refuses to rewrite the shard rather than drop the tension in silence.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the registered shard as written
     * @return the graph and the findings
     */
    public static GraphBuild registeredGraph(String packName, RawGraph raw) {
        return build(packName, raw, true);
    }

    private static GraphBuild build(String packName, RawGraph raw, boolean registered) {
        String origin = packName + "/" + raw.file();
        List<DrgNode> nodes = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        List<Tension> tensions = new ArrayList<>();
        List<PackFinding> findings = new ArrayList<>();
        for (int i = 0; i < raw.nodes().size(); i++) {
            RawNode node = raw.nodes().get(i);
            Optional<DrgNode> bound = switch (raw.shape()) {
                case SHARD -> shardNode(node);
                case FRAGMENT -> fragmentNode(node);
            };
            if (bound.isPresent()) {
                nodes.add(bound.get());
            } else {
                findings.add(graphFinding(raw, NODES, i, nodeProblem(raw.shape(), node, origin)));
            }
        }
        for (int i = 0; i < raw.edges().size(); i++) {
            RawEdge edge = raw.edges().get(i);
            Object outcome = registered && TENSION.equals(edge.relation())
                    ? origin + ": a registered relationship is never a tension; " + TENSION
                            + " is authored in a pack's graph shards"
                    : edge(edge, origin);
            switch (outcome) {
                case Relationship relationship -> relationships.add(relationship);
                case Tension tension -> tensions.add(tension);
                default -> findings.add(graphFinding(raw, EDGES, i, String.valueOf(outcome)));
            }
        }
        return new GraphBuild(new DoctrineGraph(nodes, relationships, tensions), findings);
    }

    private static PackFinding graphFinding(RawGraph raw, String list, int index, String detail) {
        return new PackFinding(
                PackFindingCode.DRG_SCHEMA_ERROR, raw.file(), List.of(list, Integer.toString(index)), detail);
    }

    private static Optional<DrgNode> shardNode(RawNode node) {
        return Urn.parse(node.ref()).map(urn -> boundNode(urn, node));
    }

    /** The node with every field written for it; a fragment node has a body path, a shard node provenance and tags. */
    private static DrgNode boundNode(Urn urn, RawNode node) {
        return new DrgNode(urn, node.label(), node.provenance(), node.tags(), node.bodyPath());
    }

    private static Optional<DrgNode> fragmentNode(RawNode node) {
        if (node.ref().isEmpty()) {
            return Optional.empty();
        }
        return NodeKind.fromPlural(node.kind()).map(kind -> boundNode(new Urn(kind, node.ref()), node));
    }

    private static String nodeProblem(RawGraph.Shape shape, RawNode node, String origin) {
        return switch (shape) {
            case SHARD -> origin + ": node urn '" + node.ref() + "' is not <kind>:<id>";
            case FRAGMENT ->
                node.ref().isEmpty()
                        ? origin + ": " + emptyId(node.kind())
                        : origin + ": node " + node.ref() + " has unknown kind '" + node.kind() + "'";
        };
    }

    private static String emptyId(String kind) {
        return "a node of kind '" + kind + "' has an empty id";
    }

    /**
     * Binds one edge; the result is the {@link Relationship}, the {@link Tension} of an {@code in_tension_with} edge, or
     * the finding text for the first fault. The {@code impacts} strength is validated here but not carried on the
     * {@link Relationship} (ADR-0025: the signed strength is a storage-DSO concern).
     */
    private static Object edge(RawEdge raw, String origin) {
        if (TENSION.equals(raw.relation())) {
            return tension(raw, origin);
        }
        Optional<RelationType> relation = RelationType.fromToken(raw.relation());
        if (relation.isEmpty()) {
            return origin + ": unknown relation '" + raw.relation() + "'";
        }
        Optional<String> strengthProblem = strengthProblem(relation.get(), raw.impacts());
        if (strengthProblem.isPresent()) {
            return origin + ": " + strengthProblem.get();
        }
        Object endpoints = endpoints(raw, origin);
        return endpoints instanceof Endpoints bound
                ? new Relationship(bound.from(), bound.to(), relation.get(), raw.when(), raw.reason())
                : endpoints;
    }

    /**
     * Binds a tension edge, kept as written (ADR-0036 Decision 4: the reader never turns it around). A tension has no
     * strength (ADR-0028 Decision 1), so an {@code impacts} on it is a fault, never dropped.
     */
    private static Object tension(RawEdge raw, String origin) {
        if (raw.impacts().isPresent()) {
            return origin + ": an " + TENSION + " edge has no 'impacts'";
        }
        Object endpoints = endpoints(raw, origin);
        return endpoints instanceof Endpoints bound
                ? new Tension(bound.from(), bound.to(), raw.when(), raw.reason())
                : endpoints;
    }

    /** The two endpoints of an edge, both URNs and not the same one. */
    private record Endpoints(Urn from, Urn to) {}

    /** The endpoints of an edge, or the finding text for the first that does not bind. */
    private static Object endpoints(RawEdge raw, String origin) {
        Optional<Urn> source = Urn.parse(raw.source());
        if (source.isEmpty()) {
            return origin + ": edge endpoint '" + raw.source() + "' is not a URN";
        }
        Optional<Urn> target = Urn.parse(raw.target());
        if (target.isEmpty()) {
            return origin + ": edge endpoint '" + raw.target() + "' is not a URN";
        }
        if (source.get().equals(target.get())) {
            return origin + ": a relationship cannot join '" + source.get() + "' to itself";
        }
        return new Endpoints(source.get(), target.get());
    }

    /** An {@code impacts} edge carries a strength in [-1, 1]; no other edge carries one. */
    private static Optional<String> strengthProblem(RelationType relation, Optional<Double> impacts) {
        if (relation != RelationType.IMPACTS) {
            return impacts.isPresent()
                    ? Optional.of("a " + relation.token() + " edge has no 'impacts'")
                    : Optional.empty();
        }
        if (impacts.isEmpty()) {
            return Optional.of("an impacts edge is missing 'impacts'");
        }
        double strength = impacts.get();
        return isStrength(strength) ? Optional.empty() : Optional.of("'impacts' " + strength + " is outside [-1, 1]");
    }

    /**
     * Whether {@code value} is a valid {@code impacts} strength: in [-1, 1], NaN and the infinities excluded.
     *
     * @param value the strength
     * @return {@code true} when it lies in [-1, 1]
     */
    private static boolean isStrength(double value) {
        return value >= -1.0 && value <= 1.0;
    }

    // ---- schema and parse findings -------------------------------------------------------------

    /**
     * The findings of a file that could not be read: its parse failure, or its schema violations.
     *
     * <p>A file a store rule refuses though it parses reads {@code <origin> <refusal>}, a retired file (#194)
     * {@code <origin> is retired: <reason>}. The code of a parse failure follows the file
     * kind, like every other finding (R17): {@code pack_unreadable} for {@code pack.yaml}, {@code drg_schema_error} for
     * a shard or the fragment, {@code org_charter_schema_error} for the org charter and {@code artifact_schema_error}
     * for any other file. An {@link FileRead.Unreadable} holds exactly one of a parse failure and schema violations, so
     * every unreadable file has a finding.
     *
     * @param packName the pack's configured name
     * @param unreadable the file that could not be read
     * @return the findings, in precedence order
     */
    public static List<PackFinding> unreadable(String packName, FileRead.Unreadable<?> unreadable) {
        String file = unreadable.file();
        PackContents.ParseFailure parse = unreadable.parse();
        if (parse != null) {
            String text = parse.refusal()
                    .map(refusal -> origin(packName, file) + " " + refusal)
                    .orElseGet(() -> PACK_YAML.equals(file)
                            ? origin(packName, file) + " is not valid YAML"
                            : origin(packName, file) + " is not valid " + language(file) + ": " + parse.firstLine());
            return List.of(new PackFinding(codeFor(file), file, List.of(), text));
        }
        return schemaFindings(packName, unreadable.violations());
    }

    /**
     * Turns schema violation facts into findings with the plan's detail vocabulary.
     *
     * <p>The text is chosen by the violation's keyword and the class of its place (the root, a node or edge item, a
     * field of one, or elsewhere), never by a validator's message. The code is chosen by the file kind:
     * {@code pack_unreadable} for {@code pack.yaml}, {@code drg_schema_error} for a shard or the fragment,
     * {@code org_charter_schema_error} for the org charter and {@code artifact_schema_error} for any other file.
     *
     * <p>Contract of the facts, beside the pointer, {@code property} and {@code value}. Facts networknt produces are
     * read from their positional argument only: {@code "1"} is the expected JSON type of a {@code type} violation
     * ({@code "0"} is the type found), {@code "0"} is the regex of a {@code pattern} violation and {@code "0"} is
     * the minimum of a {@code minItems} violation ({@code "1"} is the count found). Facts networknt does not emit are
     * named keys the adapter must add: {@code kind}, the sibling plural kind of a fragment node, for
     * {@code minLength} and {@code pattern} on its {@code id}; and {@code id} (or {@code urn} for a shard node), the
     * sibling identity of a node, for {@code enum} on its {@code kind}. A fact that is absent is never guessed: the
     * violation gets the fixed fallback text.
     *
     * <p>A nullable {@code anyOf[T, null]} is collapsed on the facts networknt emits, which are only the branch
     * {@code type} violations at one pointer. At one file and pointer, when there are two or more {@code type}
     * violations and at least one expects something other than {@code null}, the ones expecting {@code null} are
     * dropped, and so is any {@code anyOf} violation at that pointer. A lone {@code type} violation that expects
     * {@code null} is kept. Any other {@code anyOf} or {@code oneOf}, and any other keyword with no row, gets the
     * fixed locale-free fallback {@code <o>: '<pointer>' breaks schema keyword '<keyword>'}.
     *
     * <p>Within one place the keyword order is {@code type}, {@code additionalProperties}, {@code required}, then the
     * rest, so an unknown key is reported before a missing one, as the adapter's reader did.
     *
     * @param packName the pack's configured name
     * @param violations the violations
     * @return the findings, in precedence order, with identical findings reported once
     */
    public static List<PackFinding> schemaFindings(String packName, List<SchemaViolation> violations) {
        List<SchemaViolation> ordered = new ArrayList<>(collapseNullable(violations));
        ordered.sort(Comparator.comparing(SchemaViolation::file, PackPolicy::comparePaths)
                .thenComparing((a, b) -> comparePlaces(a.file(), a.pointer(), b.pointer()))
                .thenComparingInt(v -> keywordRank(v.keyword())));
        Set<PackFinding> findings = new LinkedHashSet<>();
        for (SchemaViolation violation : ordered) {
            findings.add(new PackFinding(
                    codeFor(violation.file()), violation.file(), violation.pointer(), detail(packName, violation)));
        }
        return List.copyOf(findings);
    }

    private static List<SchemaViolation> collapseNullable(List<SchemaViolation> violations) {
        Map<Spot, List<SchemaViolation>> types = new LinkedHashMap<>();
        for (SchemaViolation v : violations) {
            if ("type".equals(v.keyword())) {
                types.computeIfAbsent(new Spot(v.file(), v.pointer()), k -> new ArrayList<>())
                        .add(v);
            }
        }
        List<SchemaViolation> kept = new ArrayList<>(violations);
        for (List<SchemaViolation> branches : types.values()) {
            if (branches.size() < 2 || branches.stream().allMatch(PackPolicy::expectsNull)) {
                continue;
            }
            SchemaViolation first = branches.get(0);
            kept.removeIf(v -> v.file().equals(first.file())
                    && v.pointer().equals(first.pointer())
                    && ("anyOf".equals(v.keyword()) || ("type".equals(v.keyword()) && expectsNull(v))));
        }
        return kept;
    }

    /** A place in a file, by its pointer segments: a key that holds a {@code /} is one segment, not two. */
    private record Spot(String file, List<String> pointer) {}

    private static boolean expectsNull(SchemaViolation violation) {
        return "null".equals(violation.args().get("1"));
    }

    private static int keywordRank(String keyword) {
        return switch (keyword) {
            case "type" -> 0;
            case "additionalProperties" -> 1;
            case "required" -> 2;
            default -> 3;
        };
    }

    private static PackFindingCode codeFor(String file) {
        if (PACK_YAML.equals(file)) {
            return PackFindingCode.PACK_UNREADABLE;
        }
        if (file.endsWith(".graph.yaml") || isFragment(file)) {
            return PackFindingCode.DRG_SCHEMA_ERROR;
        }
        return file.endsWith("org-charter.yaml")
                ? PackFindingCode.ORG_CHARTER_SCHEMA_ERROR
                : PackFindingCode.ARTIFACT_SCHEMA_ERROR;
    }

    private static boolean isFragment(String file) {
        return file.equals(FRAGMENT) || file.endsWith("/" + FRAGMENT);
    }

    /** The language a file is written in, as a parse failure names it: Markdown for a {@code .md} companion. */
    private static String language(String file) {
        return file.endsWith(".md") ? "Markdown" : "YAML";
    }

    private static String origin(String packName, String file) {
        return PACK_YAML.equals(file) ? "pack.yaml of " + packName : packName + "/" + file;
    }

    /** Where in a document a pointer points; the vocabulary keys on it. */
    private enum Place {
        ROOT,
        ROOT_FIELD,
        ITEM,
        ITEM_FIELD,
        OTHER;

        static Place of(List<String> pointer) {
            boolean listed = !pointer.isEmpty() && (NODES.equals(pointer.get(0)) || EDGES.equals(pointer.get(0)));
            return switch (pointer.size()) {
                case 0 -> ROOT;
                case 1 -> ROOT_FIELD;
                case 2 -> listed ? ITEM : OTHER;
                case 3 -> listed ? ITEM_FIELD : OTHER;
                default -> OTHER;
            };
        }
    }

    private static String detail(String packName, SchemaViolation v) {
        String file = v.file();
        boolean descriptor = PACK_YAML.equals(file);
        String o = origin(packName, file);
        List<String> pointer = v.pointer();
        Place place = Place.of(pointer);
        String key = pointer.isEmpty() ? "" : pointer.get(pointer.size() - 1);
        String item = !pointer.isEmpty() && NODES.equals(pointer.get(0)) ? "node" : "edge";
        String value = v.value().orElse("");
        Optional<String> property = v.property();
        boolean fragmentNodeId =
                isFragment(file) && place == Place.ITEM_FIELD && NODES.equals(pointer.get(0)) && "id".equals(key);
        return switch (v.keyword()) {
            case "type" -> typeDetail(v, o, descriptor, place, key, item);
            case "additionalProperties" ->
                property.map(p -> o + (place == Place.ITEM ? " " + item : "") + ": unknown key '" + p + "'")
                        .orElseGet(() -> fallback(o, v));
            case "required" ->
                property.map(p -> descriptor ? o + " has no " + p : o + ": missing '" + p + "'")
                        .orElseGet(() -> fallback(o, v));
            case "minLength" ->
                fragmentNodeId && v.args().containsKey("kind")
                        ? o + ": " + emptyId(v.args().get("kind"))
                        : descriptor
                                        && place == Place.ROOT_FIELD
                                        && v.value().filter(String::isEmpty).isPresent()
                                ? o + ": " + key + " must not be empty"
                                : fallback(o, v);
            case "pattern" -> patternDetail(v, o, place, key, fragmentNodeId, value);
            case "enum" -> enumDetail(v, o, descriptor, place, key, value);
            case "const" -> o + ": '" + key + "' has an unsupported value '" + value + "'";
            case "minItems" -> {
                String least = v.args().get("0");
                yield least == null ? fallback(o, v) : o + ": '" + key + "' needs at least " + least + " item(s)";
            }
            case "minimum", "maximum", "exclusiveMinimum" ->
                v.value().isPresent() ? o + ": '" + key + "' " + value + " is out of range" : fallback(o, v);
            default -> fallback(o, v);
        };
    }

    private static String typeDetail(
            SchemaViolation v, String o, boolean descriptor, Place place, String key, String item) {
        String expected = v.args().get("1");
        if (expected == null) {
            return fallback(o, v);
        }
        String quoted = descriptor ? key : "'" + key + "'";
        return switch (expected) {
            case "object" ->
                switch (place) {
                    case ROOT -> descriptor ? o + " is not a mapping" : o + ": expected a mapping";
                    case ITEM -> o + " " + item + ": expected a mapping";
                    default -> fallback(o, v);
                };
            case "array" -> o + " " + key + ": expected a list";
            case "string" -> o + ": " + quoted + " must be a string";
            case "number" -> o + ": " + quoted + " must be a number";
            case "integer" -> o + ": " + quoted + " must be an integer";
            case "boolean" -> o + ": " + quoted + " must be a boolean";
            default -> fallback(o, v);
        };
    }

    private static String patternDetail(
            SchemaViolation v, String o, Place place, String key, boolean fragmentNodeId, String value) {
        if (fragmentNodeId) {
            String kind = v.args().get("kind");
            if (kind == null) {
                return fallback(o, v);
            }
            return value.isEmpty() ? o + ": " + emptyId(kind) : o + ": node id '" + value + "' is not <kind>:<id>-safe";
        }
        if (place == Place.ITEM_FIELD && NODES.equals(v.pointer().get(0)) && "urn".equals(key)) {
            String regex = v.args().get("0");
            if (regex != null && regex.matches("\\^[a-z_]+:")) {
                String declared = regex.substring(1, regex.length() - 1);
                return o + ": node " + value + " declares kind '" + declared + "'";
            }
            return o + ": node urn '" + value + "' is not <kind>:<id>";
        }
        if (place == Place.ITEM_FIELD
                && EDGES.equals(v.pointer().get(0))
                && ("source".equals(key) || "target".equals(key))) {
            return o + ": edge endpoint '" + value + "' is not a URN";
        }
        return o + ": '" + key + "' does not match its pattern";
    }

    private static String enumDetail(
            SchemaViolation v, String o, boolean descriptor, Place place, String key, String value) {
        if (place == Place.ITEM_FIELD && EDGES.equals(v.pointer().get(0)) && "relation".equals(key)) {
            return o + ": unknown relation '" + value + "'";
        }
        if (place == Place.ITEM_FIELD && NODES.equals(v.pointer().get(0)) && "kind".equals(key)) {
            String id =
                    v.args().containsKey("id") ? v.args().get("id") : v.args().get("urn");
            if (id == null) {
                return fallback(o, v);
            }
            return o + ": node " + id + " has unknown kind '" + value + "'";
        }
        if (descriptor && place == Place.ROOT_FIELD && "pack_level".equals(key)) {
            return o + " declares an unknown pack_level '" + value + "'";
        }
        return o + ": '" + key + "' has an unsupported value '" + value + "'";
    }

    private static String fallback(String o, SchemaViolation v) {
        String pointer = "/" + String.join("/", v.pointer());
        return o + ": '" + pointer + "' breaks schema keyword '" + v.keyword() + "'";
    }

    /**
     * Python's {@code PurePath} order for {@code /}-separated paths: segment by segment, a shorter prefix first, and
     * each segment by code point. Plain {@code String} order differs: {@code '-'} and {@code '.'} sort below
     * {@code '/'}, so {@code python-legacy/y} would sort before {@code python/x}.
     */
    static int comparePaths(String a, String b) {
        String[] left = a.split("/", -1);
        String[] right = b.split("/", -1);
        int shared = Math.min(left.length, right.length);
        for (int i = 0; i < shared; i++) {
            int byCodePoint = compareCodePoints(left[i], right[i]);
            if (byCodePoint != 0) {
                return byCodePoint;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static int compareCodePoints(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i);
            int y = b.codePointAt(j);
            if (x != y) {
                return Integer.compare(x, y);
            }
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    // ---- ordering within a file ----------------------------------------------------------------

    private static int comparePlaces(String file, List<String> a, List<String> b) {
        int shared = Math.min(a.size(), b.size());
        for (int i = 0; i < shared; i++) {
            if (!a.get(i).equals(b.get(i))) {
                return compareSegments(keyOrder(file, a, i), a.get(i), b.get(i));
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    /**
     * A total order over the segments of a place: an index (digits only, any length) sorts before any other segment;
     * two indexes by value, compared without parsing, by length after the leading zeros and then by digit, and
     * {@code 07} before {@code 7} when they are the same value; any other two by the rank of the declared key and then
     * by code unit. Deciding index-or-not first is what keeps it transitive. Comparing an index with a key by code
     * unit while two indexes compare by value is a cycle ({@code 9 < 10} by value, {@code 10 < 1a} and {@code 1a < 9}
     * by code unit), which {@code List.sort} refuses with an {@link IllegalArgumentException}.
     */
    private static int compareSegments(List<String> keys, String a, String b) {
        boolean indexA = isIndex(a);
        boolean indexB = isIndex(b);
        if (indexA != indexB) {
            return indexA ? -1 : 1;
        }
        if (indexA) {
            String digitsA = withoutLeadingZeros(a);
            String digitsB = withoutLeadingZeros(b);
            int byLength = Integer.compare(digitsA.length(), digitsB.length());
            if (byLength != 0) {
                return byLength;
            }
            int byDigits = digitsA.compareTo(digitsB);
            return byDigits != 0 ? byDigits : a.compareTo(b);
        }
        int rankA = rank(keys, a);
        int rankB = rank(keys, b);
        return rankA != rankB ? Integer.compare(rankA, rankB) : a.compareTo(b);
    }

    private static boolean isIndex(String segment) {
        return !segment.isEmpty() && segment.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static String withoutLeadingZeros(String digits) {
        int first = 0;
        while (first < digits.length() - 1 && digits.charAt(first) == '0') {
            first++;
        }
        return digits.substring(first);
    }

    /** The position of a declared key; unknown keys rank after every known one. */
    private static int rank(List<String> keys, String segment) {
        int index = keys.indexOf(segment);
        return index < 0 ? keys.size() : index;
    }

    /** The declared key order for the object whose key sits at {@code depth} of the pointer. */
    private static List<String> keyOrder(String file, List<String> pointer, int depth) {
        boolean descriptor = PACK_YAML.equals(file);
        boolean fragment = isFragment(file);
        boolean shard = !descriptor && !fragment && file.endsWith(".graph.yaml");
        if (depth == 0) {
            return descriptor ? DESCRIPTOR_KEYS : fragment ? FRAGMENT_ROOT_KEYS : shard ? SHARD_ROOT_KEYS : List.of();
        }
        if (depth == 2 && (shard || fragment)) {
            boolean node = NODES.equals(pointer.get(0));
            if (node) {
                return fragment ? FRAGMENT_NODE_KEYS : SHARD_NODE_KEYS;
            }
            return fragment ? FRAGMENT_EDGE_KEYS : SHARD_EDGE_KEYS;
        }
        return List.of();
    }
}
