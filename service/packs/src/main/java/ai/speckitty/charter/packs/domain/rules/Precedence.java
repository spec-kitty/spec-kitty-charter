package ai.speckitty.charter.packs.domain.rules;

import static ai.speckitty.charter.packs.domain.rules.GraphBinding.EDGES;
import static ai.speckitty.charter.packs.domain.rules.GraphBinding.NODES;
import static ai.speckitty.charter.packs.domain.rules.Paths.FRAGMENT;
import static ai.speckitty.charter.packs.domain.rules.Paths.PACK_YAML;
import static ai.speckitty.charter.packs.domain.rules.Paths.isFragment;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The plan's precedence of the findings: file group, then file name, then the place within the file. The file order
 * is Python's {@code PurePath} order and the place order follows each document kind's declared keys.
 */
public final class Precedence {

    /*
     * The key order of each document kind is the order of the bundled schema's `properties`
     * (contracts/packs/schemas/<kind>.schema.json): graph-shard, org-fragment and pack-descriptor.
     */
    private static final List<String> DESCRIPTOR_KEYS =
            List.of("pack_id", "pack_version", "parent_pack", "name", "pack_level", "requires_core", "pack_drg");
    private static final List<String> SHARD_ROOT_KEYS =
            List.of("schema_version", "generated_at", "generated_by", NODES, EDGES);
    private static final List<String> FRAGMENT_ROOT_KEYS = List.of(NODES, EDGES);
    private static final List<String> SHARD_NODE_KEYS = List.of("urn", "kind", "label", "provenance", "tags");
    private static final List<String> FRAGMENT_NODE_KEYS = List.of("id", "kind", "title", "body_path");
    private static final List<String> SHARD_EDGE_KEYS = List.of("source", "target", "relation", "when", "reason");
    // #509: a fragment's edge holds a when, as the org-fragment schema's _OrgDRGEdge declares, in a shard edge's order.
    private static final List<String> FRAGMENT_EDGE_KEYS = SHARD_EDGE_KEYS;

    private Precedence() {}

    /**
     * The plan's precedence over kernel reasons (#554, N2): file group ({@link #group}), then file name
     * ({@link #comparePaths}), then the place within the file ({@link #comparePlaces}), read as the segments of the
     * reason's pointer (RFC 6901, decoded). The reasons whose code is in {@code firstGroup} look at the pack as a whole;
     * they form their own group, after the graph documents and before every other file. Its caller names that group,
     * so this class names no edge's codes.
     *
     * @param firstGroup the codes of the rules that look at the pack as a whole
     * @return the order
     */
    public static Comparator<Reason> comparator(Set<ReasonCode> firstGroup) {
        Set<ReasonCode> wide = Set.copyOf(firstGroup);
        return Comparator.<Reason>comparingInt(
                        reason -> group(reason.code(), reason.location().file(), wide))
                .thenComparing(reason -> reason.location().file(), Precedence::comparePaths)
                .thenComparing((a, b) -> comparePlaces(
                        a.location().file(),
                        segments(a.location().pointer()),
                        segments(b.location().pointer())));
    }

    /**
     * The group a finding sorts in: a code of {@code firstGroup} is 4; otherwise {@code pack.yaml} is 1, a root
     * {@code *.graph.yaml} shard 2, {@code drg/fragment.yaml} 3 and any other file 5.
     *
     * @param code the finding's code
     * @param file the pack-relative file it belongs to
     * @param firstGroup the codes of the rules that look at the pack as a whole
     * @return the group, lowest first
     */
    public static int group(ReasonCode code, String file, Set<ReasonCode> firstGroup) {
        if (firstGroup.contains(code)) {
            return 4;
        }
        if (PACK_YAML.equals(file)) {
            return 1;
        }
        if (FRAGMENT.equals(file)) {
            return 3;
        }
        return file.endsWith(".graph.yaml") && file.indexOf('/') < 0 ? 2 : 5;
    }

    /** The segments of a JSON pointer (RFC 6901), decoded; none for the whole file. */
    static List<String> segments(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : pointer.substring(1).split("/", -1)) {
            segments.add(segment.replace("~1", "/").replace("~0", "~"));
        }
        return segments;
    }

    /**
     * Python's {@code PurePath} order for {@code /}-separated paths: segment by segment, a shorter prefix first, and
     * each segment by code point. Plain {@code String} order differs: {@code '-'} and {@code '.'} sort below
     * {@code '/'}, so {@code python-legacy/y} would sort before {@code python/x}.
     *
     * @param a one pack-relative path
     * @param b the other
     * @return negative, zero or positive as {@code a} sorts before, with or after {@code b}
     */
    public static int comparePaths(String a, String b) {
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

    /**
     * The order of two places within one file: segment by segment, a shorter place first, each segment by the declared
     * key order of the file's kind.
     *
     * @param file the pack-relative file both places are in
     * @param a one place, as pointer segments
     * @param b the other
     * @return negative, zero or positive as {@code a} sorts before, with or after {@code b}
     */
    public static int comparePlaces(String file, List<String> a, List<String> b) {
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
