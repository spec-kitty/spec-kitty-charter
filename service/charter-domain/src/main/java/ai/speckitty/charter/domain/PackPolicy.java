package ai.speckitty.charter.domain;

import ai.speckitty.charter.domain.PackContents.FileRead;
import ai.speckitty.charter.domain.PackContents.RawEdge;
import ai.speckitty.charter.domain.PackContents.RawGraph;
import ai.speckitty.charter.domain.PackContents.RawNode;
import ai.speckitty.charter.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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

    private static final String NODES = "nodes";
    private static final String EDGES = "edges";
    private static final String LEGACY_TENSION = "in_tension_with";
    private static final double LEGACY_TENSION_STRENGTH = -1.0;

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
    private static final Comparator<PackFinding> PRECEDENCE = Comparator.comparingInt(PackPolicy::group)
            .thenComparing(PackFinding::file)
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
     * Runs every rule: the level rules, the schema and parse findings of unreadable files, and the graph rules of the
     * graph documents that were read. Later rules join here.
     *
     * @param contents what the adapter read
     * @return the verdict, with the findings in precedence order
     */
    public static PackVerdict judge(PackContents contents) {
        String name = contents.root().name();
        List<PackFinding> findings =
                new ArrayList<>(level(name, contents.root().configuredLevel(), contents.descriptor()));
        if (contents.descriptor() instanceof FileRead.Unreadable<PackDescriptor> bad) {
            findings.addAll(unreadable(name, bad));
        }
        for (FileRead<RawGraph> document : documents(contents)) {
            switch (document) {
                case FileRead.Unreadable<RawGraph> bad -> findings.addAll(unreadable(name, bad));
                case FileRead.Read<RawGraph> read ->
                    findings.addAll(graph(name, read.value()).findings());
                case FileRead.Absent<RawGraph> absent -> {}
            }
        }
        findings.sort(PRECEDENCE);
        return new PackVerdict(contents.files(), findings);
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

    /**
     * The one restriction shared by a load and a validation: the files a load reads.
     *
     * @param contents what the adapter read
     * @return {@code pack.yaml}, the root shards and {@code drg/fragment.yaml}, as pack-relative paths
     */
    public static List<String> loadPath(PackContents contents) {
        List<String> files = new ArrayList<>();
        files.add(PACK_YAML);
        contents.shards().forEach(shard -> files.add(shard.file()));
        files.add(FRAGMENT);
        return List.copyOf(files);
    }

    /**
     * Loads a pack, building the graph from the facts: refuses with the first blocking finding on the load path, or
     * builds the pack with the warnings on the load path as its diagnostics.
     *
     * <p>The graph is built from the shards in name order and then the fragment, the order the adapter merged them
     * in before. This is the same projection of {@link #judge} as {@link #load(PackContents, DoctrineGraph)}, so a
     * graph finding, a schema finding and a parse failure all refuse here.
     *
     * @param contents what the adapter read
     * @return the loaded pack
     * @throws CharterRefusal when a finding on the load path blocks, including the parse or schema finding of an
     *     unreadable file
     * @throws IllegalArgumentException when the pack has no configured level: a bare path is validate-only
     */
    public static CharterPack load(PackContents contents) {
        return loadWith(contents, () -> mergedGraph(contents));
    }

    /**
     * Loads a pack with a graph the caller built: refuses with the first blocking finding on the load path, or
     * builds the pack with the warnings on the load path as its diagnostics.
     *
     * <p>Transitional: the adapter calls this until it passes raw graph facts; then this overload is deleted.
     *
     * @param contents what the adapter read
     * @param graph the pack's graph, which the adapter built from the same files
     * @return the loaded pack
     * @throws CharterRefusal when a finding on the load path blocks, including the parse or schema finding of an
     *     unreadable file
     * @throws IllegalArgumentException when the pack has no configured level: a bare path is validate-only
     */
    public static CharterPack load(PackContents contents, DoctrineGraph graph) {
        return loadWith(contents, () -> graph);
    }

    private static CharterPack loadWith(PackContents contents, Supplier<DoctrineGraph> graph) {
        PackLevel configured = contents.root()
                .configuredLevel()
                .orElseThrow(() -> new IllegalArgumentException("load needs a configured level"));
        List<String> loadPath = loadPath(contents);
        List<PackFinding> findings = judge(contents).findings().stream()
                .filter(f -> loadPath.contains(f.file()))
                .toList();
        for (PackFinding finding : findings) {
            if (finding.severity() == PackFindingCode.Severity.BLOCKING) {
                throw finding.toRefusal();
            }
        }
        Optional<PackDescriptor> descriptor = switch (contents.descriptor()) {
            case FileRead.Read<PackDescriptor> read -> Optional.of(read.value());
            case FileRead.Absent<PackDescriptor> absent -> Optional.empty();
            case FileRead.Unreadable<PackDescriptor> unreadable ->
                throw new IllegalStateException("an unreadable pack.yaml always yields a blocking finding");
        };
        return new CharterPack(
                contents.root().name(),
                configured,
                descriptor,
                graph.get(),
                findings.stream().map(PackFinding::toDiagnostic).toList());
    }

    /** The graph documents of a load, in merge order: the shards, then the fragment. */
    private static List<FileRead<RawGraph>> documents(PackContents contents) {
        List<FileRead<RawGraph>> documents = new ArrayList<>(contents.shards());
        documents.add(contents.fragment());
        return documents;
    }

    private static DoctrineGraph mergedGraph(PackContents contents) {
        DoctrineGraph merged = DoctrineGraph.empty();
        for (FileRead<RawGraph> document : documents(contents)) {
            if (document instanceof FileRead.Read<RawGraph> read) {
                merged =
                        merged.merge(graph(contents.root().name(), read.value()).graph());
            }
        }
        return merged;
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
     * Builds the graph of one document and collects what does not bind: the URN binding, the legacy
     * {@code in_tension_with} mapping (read as {@code impacts} with strength -1, as core ADR 2026-07-26-3 means it)
     * and the {@code impacts} rules, NaN and the infinities included.
     *
     * <p>A node or edge with a finding is left out of the graph, so {@link DrgEdge}'s constructor check stays a
     * programming-error guard and never fires. An edge reports its first fault only, checked in the order the
     * adapter's reader used: relation, then {@code impacts}, then the endpoints. The schema normally refuses these
     * first; the domain check keeps the same text for a document that reaches here anyway.
     *
     * @param packName the pack's configured name, which prefixes the origin in a text
     * @param raw the document as written
     * @return the graph and the findings
     */
    public static GraphBuild graph(String packName, RawGraph raw) {
        String origin = packName + "/" + raw.file();
        List<DrgNode> nodes = new ArrayList<>();
        List<DrgEdge> edges = new ArrayList<>();
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
            Object outcome = edge(edge, origin);
            if (outcome instanceof DrgEdge bound) {
                edges.add(bound);
            } else {
                findings.add(graphFinding(raw, EDGES, i, String.valueOf(outcome)));
            }
        }
        return new GraphBuild(new DoctrineGraph(nodes, edges), findings);
    }

    private static PackFinding graphFinding(RawGraph raw, String list, int index, String detail) {
        return new PackFinding(
                PackFindingCode.DRG_SCHEMA_ERROR, raw.file(), List.of(list, Integer.toString(index)), detail);
    }

    private static Optional<DrgNode> shardNode(RawNode node) {
        return Urn.parse(node.ref()).map(urn -> new DrgNode(urn, node.label()));
    }

    private static Optional<DrgNode> fragmentNode(RawNode node) {
        if (node.ref().isEmpty()) {
            return Optional.empty();
        }
        return NodeKind.fromPlural(node.kind()).map(kind -> new DrgNode(new Urn(kind, node.ref()), node.label()));
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

    /** Binds one edge; the result is the {@link DrgEdge}, or the finding text for the first fault. */
    private static Object edge(RawEdge raw, String origin) {
        boolean legacy = LEGACY_TENSION.equals(raw.relation());
        Optional<Relation> relation = legacy ? Optional.of(Relation.IMPACTS) : Relation.fromToken(raw.relation());
        if (relation.isEmpty()) {
            return origin + ": unknown relation '" + raw.relation() + "'";
        }
        Optional<Double> impacts = raw.impacts();
        if (legacy) {
            if (impacts.isPresent()) {
                return origin + ": an in_tension_with edge has no 'impacts'";
            }
            impacts = Optional.of(LEGACY_TENSION_STRENGTH);
        }
        Optional<String> strengthProblem = strengthProblem(relation.get(), impacts);
        if (strengthProblem.isPresent()) {
            return origin + ": " + strengthProblem.get();
        }
        Optional<Urn> source = Urn.parse(raw.source());
        if (source.isEmpty()) {
            return origin + ": edge endpoint '" + raw.source() + "' is not a URN";
        }
        Optional<Urn> target = Urn.parse(raw.target());
        if (target.isEmpty()) {
            return origin + ": edge endpoint '" + raw.target() + "' is not a URN";
        }
        return new DrgEdge(source.get(), target.get(), relation.get(), impacts, raw.when(), raw.reason());
    }

    /** An {@code impacts} edge carries a strength in [-1, 1]; no other edge carries one. */
    private static Optional<String> strengthProblem(Relation relation, Optional<Double> impacts) {
        if (relation != Relation.IMPACTS) {
            return impacts.isPresent()
                    ? Optional.of("a " + relation.token() + " edge has no 'impacts'")
                    : Optional.empty();
        }
        if (impacts.isEmpty()) {
            return Optional.of("an impacts edge is missing 'impacts'");
        }
        double strength = impacts.get();
        return DrgEdge.isStrength(strength)
                ? Optional.empty()
                : Optional.of("'impacts' " + strength + " is outside [-1, 1]");
    }

    // ---- schema and parse findings -------------------------------------------------------------

    /**
     * The findings of a file that could not be read: its parse failure, or its schema violations.
     *
     * <p>The code of a parse failure follows the file kind, like every other finding (R17): {@code pack_unreadable} for
     * {@code pack.yaml}, {@code drg_schema_error} for a shard or the fragment, {@code org_charter_schema_error} for the
     * org charter and {@code artifact_schema_error} for any other file. A file that is unreadable without a parse
     * failure or any violation (which a producer should not send) gets a plain "is unreadable" text under the same
     * code, so it still refuses.
     *
     * @param packName the pack's configured name
     * @param unreadable the file that could not be read
     * @return the findings, in precedence order
     */
    public static List<PackFinding> unreadable(String packName, FileRead.Unreadable<?> unreadable) {
        String file = unreadable.file();
        PackContents.ParseFailure parse = unreadable.parse();
        if (parse != null) {
            String text = PACK_YAML.equals(file)
                    ? origin(packName, file) + " is not valid YAML"
                    : origin(packName, file) + " is not valid YAML: " + parse.firstLine();
            return List.of(new PackFinding(codeFor(file), file, List.of(), text));
        }
        if (unreadable.violations().isEmpty()) {
            return List.of(new PackFinding(codeFor(file), file, List.of(), origin(packName, file) + " is unreadable"));
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
        ordered.sort(Comparator.comparing(SchemaViolation::file)
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
        Map<List<String>, List<SchemaViolation>> types = new LinkedHashMap<>();
        for (SchemaViolation v : violations) {
            if ("type".equals(v.keyword())) {
                types.computeIfAbsent(List.of(v.file(), String.join("/", v.pointer())), k -> new ArrayList<>())
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

    private static int compareSegments(List<String> keys, String a, String b) {
        if (isIndex(a) && isIndex(b)) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        int rankA = rank(keys, a);
        int rankB = rank(keys, b);
        return rankA != rankB ? Integer.compare(rankA, rankB) : a.compareTo(b);
    }

    private static boolean isIndex(String segment) {
        return !segment.isEmpty() && segment.length() < 18 && segment.chars().allMatch(c -> c >= '0' && c <= '9');
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
