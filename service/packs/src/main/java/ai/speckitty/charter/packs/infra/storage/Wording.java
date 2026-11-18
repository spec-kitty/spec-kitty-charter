package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.domain.rules.GraphBinding.EDGES;
import static ai.speckitty.charter.packs.domain.rules.GraphBinding.NODES;
import static ai.speckitty.charter.packs.domain.rules.GraphBinding.emptyId;
import static ai.speckitty.charter.packs.domain.rules.Paths.PACK_YAML;
import static ai.speckitty.charter.packs.domain.rules.Paths.isFragment;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.rules.Findings;
import ai.speckitty.charter.packs.domain.rules.Paths;
import ai.speckitty.charter.packs.domain.rules.Precedence;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one reader's detail vocabulary of a file that could not be read (#554, FR-016: the reader owns the schema
 * wording): its parse failure or its schema violations, each as a read fault whose code follows the file kind and whose
 * text is chosen by the violation's keyword and place, never by a validator's message. It knows the JSON-Schema
 * keywords, so it lives in the storage edge, not in the core.
 */
public final class Wording {

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

    /**
     * The named fact of a {@code const} violation that a store rule states in its own words (a file stem, a preset
     * entry's path): the detail is {@code <file>: <reason>}, the store's wording, on every surface (#554, R2 row 3).
     */
    public static final String REASON = "reason";

    private Wording() {}

    /**
     * Whether the detail vocabulary has a row for a schema keyword.
     *
     * @param keyword the schema keyword
     * @return {@code true} when a violation of the keyword gets a text of its own, not the fixed fallback
     */
    public static boolean handles(String keyword) {
        return VOCABULARY_KEYWORDS.contains(keyword);
    }

    /**
     * The findings of a file that could not be read: its parse failure, or its schema violations.
     *
     * <p>A file a store rule refuses though it parses reads {@code <origin> <refusal>}, a retired file (#194)
     * {@code <origin> is retired: <reason>}. The code of a parse failure follows the file
     * kind, like every other finding (R17): {@code pack_unreadable} for {@code pack.yaml}, {@code drg_schema_error} for
     * a shard or the fragment and {@code artifact_schema_error} for any other file. An {@link FileRead.Unreadable} holds
     * exactly one of a parse failure and schema violations, so every unreadable file has a finding.
     *
     * @param packName the pack's configured name
     * @param unreadable the file that could not be read
     * @return the findings, in precedence order
     */
    public static List<Reason> unreadable(String packName, FileRead.Unreadable<?> unreadable) {
        String file = unreadable.file();
        PackContents.ParseFailure parse = unreadable.parse();
        if (parse != null) {
            if (parse.refusal().isPresent()) {
                String refusal = parse.refusal().get();
                return List.of(Findings.at(
                        parse.code().orElseGet(() -> codeFor(file)), file, List.of(), PackCodes.detail(file, refusal)));
            }
            if (PACK_YAML.equals(file)) {
                return List.of(Findings.at(
                        StorageCodes.PACK_UNREADABLE,
                        file,
                        List.of(),
                        Paths.origin(packName, file) + " is not valid YAML: " + parse.firstLine()));
            }
            return List.of(Findings.at(parseCodeFor(file), file, List.of(), PackCodes.detail(file, parse.firstLine())));
        }
        return schemaFindings(packName, unreadable.violations());
    }

    /**
     * Turns schema violation facts into findings with the plan's detail vocabulary.
     *
     * <p>The text is chosen by the violation's keyword and the class of its place (the root, a node or edge item, a
     * field of one, or elsewhere), never by a validator's message. The code is chosen by the file kind:
     * {@code pack_unreadable} for {@code pack.yaml}, {@code drg_schema_error} for a shard or the fragment and
     * {@code artifact_schema_error} for any other file.
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
    public static List<Reason> schemaFindings(String packName, List<SchemaViolation> violations) {
        List<SchemaViolation> ordered = new ArrayList<>(collapseNullable(violations));
        ordered.sort(Comparator.comparing(SchemaViolation::file, Precedence::comparePaths)
                .thenComparing((a, b) -> Precedence.comparePlaces(a.file(), a.pointer(), b.pointer()))
                .thenComparingInt(v -> keywordRank(v.keyword())));
        Set<Reason> findings = new LinkedHashSet<>();
        for (SchemaViolation violation : ordered) {
            findings.add(Findings.at(
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
            if (branches.size() < 2 || branches.stream().allMatch(Wording::expectsNull)) {
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

    private static ReasonCode codeFor(String file) {
        if (PACK_YAML.equals(file)) {
            return StorageCodes.PACK_UNREADABLE;
        }
        if (isPromptFragment(file)) {
            return PackCodes.PROMPT_FRAGMENT_INVALID;
        }
        if (file.endsWith(".graph.yaml") || isFragment(file)) {
            return PackCodes.DRG_SCHEMA_ERROR;
        }
        return PackCodes.ARTIFACT_SCHEMA_ERROR;
    }

    /**
     * The code of a file that does not parse (#554, R2 row 1): a graph document's and a prompt fragment's own code, as
     * for its schema faults, and {@code unreadable} for any other file, as the registration answers.
     */
    private static ReasonCode parseCodeFor(String file) {
        ReasonCode code = codeFor(file);
        return code.equals(PackCodes.ARTIFACT_SCHEMA_ERROR) ? StorageCodes.UNREADABLE : code;
    }

    /** A step enhancement's companion: a fault of its own read (a codec refusal, a structure breach) is its code. */
    private static boolean isPromptFragment(String file) {
        return file.startsWith("missions/") && file.endsWith(".prompt-fragment.md");
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
        String o = Paths.origin(packName, file);
        List<String> pointer = v.pointer();
        Place place = Place.of(pointer);
        String key = pointer.isEmpty() ? "" : pointer.get(pointer.size() - 1);
        String item = !pointer.isEmpty() && NODES.equals(pointer.get(0)) ? "node" : "edge";
        String value = v.value().orElse("");
        Optional<String> property = v.property();
        boolean fragmentNodeId = isFragmentNodeId(file, place, pointer, key);
        return switch (v.keyword()) {
            case "type" -> typeDetail(v, o, descriptor, place, key, item);
            case "additionalProperties" -> additionalPropertiesDetail(v, o, place, item, property);
            case "required" -> requiredDetail(v, o, descriptor, property);
            case "minLength" -> minLengthDetail(v, o, descriptor, place, key, fragmentNodeId);
            case "pattern" -> patternDetail(v, o, place, key, fragmentNodeId, value);
            case "enum" -> enumDetail(v, o, descriptor, place, key, value);
            case "const" ->
                v.args().containsKey(REASON)
                        ? o + ": " + v.args().get(REASON)
                        : o + ": '" + key + "' has an unsupported value '" + value + "'";
            case "minItems" -> minItemsDetail(v, o, key);
            case "minimum", "maximum", "exclusiveMinimum" -> rangeDetail(v, o, key, value);
            default -> fallback(o, v);
        };
    }

    /** Whether the violation sits on a node's {@code id} in a fragment, where the id's own rules name the fault. */
    private static boolean isFragmentNodeId(String file, Place place, List<String> pointer, String key) {
        return isFragment(file) && place == Place.ITEM_FIELD && NODES.equals(pointer.get(0)) && "id".equals(key);
    }

    private static String additionalPropertiesDetail(
            SchemaViolation v, String o, Place place, String item, Optional<String> property) {
        return property.map(p -> o + (place == Place.ITEM ? " " + item : "") + ": unknown key '" + p + "'")
                .orElseGet(() -> fallback(o, v));
    }

    private static String requiredDetail(SchemaViolation v, String o, boolean descriptor, Optional<String> property) {
        return property.map(p -> descriptor ? o + " has no " + p : o + ": missing '" + p + "'")
                .orElseGet(() -> fallback(o, v));
    }

    private static String minLengthDetail(
            SchemaViolation v, String o, boolean descriptor, Place place, String key, boolean fragmentNodeId) {
        return fragmentNodeId && v.args().containsKey("kind")
                ? o + ": " + emptyId(v.args().get("kind"))
                : descriptor
                                && place == Place.ROOT_FIELD
                                && v.value().filter(String::isEmpty).isPresent()
                        ? o + ": " + key + " must not be empty"
                        : fallback(o, v);
    }

    private static String minItemsDetail(SchemaViolation v, String o, String key) {
        String least = v.args().get("0");
        return least == null ? fallback(o, v) : o + ": '" + key + "' needs at least " + least + " item(s)";
    }

    private static String rangeDetail(SchemaViolation v, String o, String key, String value) {
        return v.value().isPresent() ? o + ": '" + key + "' " + value + " is out of range" : fallback(o, v);
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
}
