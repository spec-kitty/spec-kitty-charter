package ai.speckitty.charter.kernel.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PEP 440 version ranges: says whether a version satisfies a specifier set. {@link Pep440} answers whether a specifier
 * set is well formed; this class answers what it admits, which a resolver needs when it checks a pack's declared range
 * against the version the offering holds.
 *
 * <p>The subset covers what a pack descriptor uses: {@code ==}, {@code !=}, {@code <=}, {@code >=}, {@code <},
 * {@code >} and {@code ~=}, joined by commas (all must hold), with an {@code ==X.Y.*} prefix wildcard. Prereleases,
 * post releases, development releases and epochs order as PEP 440 orders them. A local version segment
 * ({@code +local}) is accepted and ignored for ordering, as PEP 440's public ordering ignores it.
 *
 * <p>Specifier sets that exclude prereleases by default ({@code >=1.0} does not admit {@code 2.0b1}) are not modelled:
 * a pack version is a release, and an implicit prerelease rule would refuse a range its author wrote in good faith.
 */
public final class Pep440Range {

    private static final Pattern SPECIFIER = Pattern.compile("^(===|==|!=|<=|>=|~=|<|>)\\s*(\\S+)$");
    private static final Pattern VERSION = Pattern.compile(
            "^(?:v)?(?:(\\d+)!)?(\\d+(?:\\.\\d+)*)(?:[-_.]?(a|b|c|rc|alpha|beta|pre|preview)[-_.]?(\\d*))?"
                    + "(?:-(\\d+)|[-_.]?(post|rev|r)[-_.]?(\\d*))?(?:[-_.]?dev[-_.]?(\\d*))?(?:\\+[a-z0-9]+(?:[-_.][a-z0-9]+)*)?$");

    private Pep440Range() {}

    /**
     * Whether a version satisfies every specifier in a set.
     *
     * @param range a PEP 440 specifier set, for example one admitting 1.x but not 2.0
     * @param version a PEP 440 version
     * @return true when the version satisfies the set
     */
    public static boolean admits(String range, String version) {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(version, "version");
        Parsed candidate = Parsed.of(version);
        for (String raw : split(',', range)) {
            String specifier = raw.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            Matcher matcher = SPECIFIER.matcher(specifier);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("not a PEP 440 specifier: " + specifier);
            }
            if (!admitsOne(matcher.group(1), matcher.group(2), candidate)) {
                return false;
            }
        }
        return true;
    }

    private static boolean admitsOne(String operator, String operand, Parsed candidate) {
        if (operator.equals("===")) {
            return candidate.text().equals(operand);
        }
        if (operand.endsWith(".*") && (operator.equals("==") || operator.equals("!="))) {
            List<Integer> prefix =
                    Parsed.of(operand.substring(0, operand.length() - 2)).release();
            boolean matches = startsWith(candidate.release(), prefix) && candidate.plainRelease();
            return operator.equals("==") == matches;
        }
        if (operator.equals("~=")) {
            Parsed floor = Parsed.of(operand);
            if (floor.release().size() < 2) {
                throw new IllegalArgumentException("a ~= specifier needs at least two release segments: " + operand);
            }
            List<Integer> prefix = floor.release().subList(0, floor.release().size() - 1);
            return compare(candidate, floor) >= 0 && startsWith(candidate.release(), prefix);
        }
        Parsed target = Parsed.of(operand);
        int order = compare(candidate, target);
        return switch (operator) {
            case "==" -> order == 0;
            case "!=" -> order != 0;
            case "<" -> order < 0;
            case "<=" -> order <= 0;
            case ">" -> order > 0;
            case ">=" -> order >= 0;
            default -> throw new IllegalArgumentException("unsupported PEP 440 operator: " + operator);
        };
    }

    private static List<String> split(char separator, String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == separator) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static boolean startsWith(List<Integer> release, List<Integer> prefix) {
        if (release.size() < prefix.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!release.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int compare(Parsed left, Parsed right) {
        int byEpoch = Integer.compare(left.epoch(), right.epoch());
        if (byEpoch != 0) {
            return byEpoch;
        }
        int byRelease = compareRelease(left.release(), right.release());
        if (byRelease != 0) {
            return byRelease;
        }
        int byPre = Integer.compare(left.preRank(), right.preRank());
        if (byPre != 0) {
            return byPre;
        }
        if (left.preRank() == right.preRank() && left.preRank() >= 0 && left.preRank() < 3) {
            int byPreNumber = Integer.compare(left.preNumber(), right.preNumber());
            if (byPreNumber != 0) {
                return byPreNumber;
            }
        }
        int byPost = Integer.compare(left.post(), right.post());
        if (byPost != 0) {
            return byPost;
        }
        return Integer.compare(left.dev(), right.dev());
    }

    private static int compareRelease(List<Integer> left, List<Integer> right) {
        int size = Math.max(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            int a = i < left.size() ? left.get(i) : 0;
            int b = i < right.size() ? right.get(i) : 0;
            int order = Integer.compare(a, b);
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /** A parsed version, kept as the parts ordering needs. */
    private record Parsed(
            String text,
            int epoch,
            List<Integer> release,
            int preRank,
            int preNumber,
            int post,
            int dev,
            boolean plainRelease) {

        static Parsed of(String version) {
            Matcher matcher = VERSION.matcher(version.trim().toLowerCase(Locale.ROOT));
            if (!matcher.matches()) {
                throw new IllegalArgumentException("not a PEP 440 version: " + version);
            }
            int epoch = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
            List<Integer> release = new ArrayList<>();
            for (String part : split('.', matcher.group(2))) {
                release.add(Integer.parseInt(part));
            }
            String pre = matcher.group(3);
            int preNumber = number(matcher.group(4), 0);
            Integer postNumber = matcher.group(5) != null
                    ? Integer.valueOf(matcher.group(5))
                    : matcher.group(6) != null ? Integer.valueOf(number(matcher.group(7), 0)) : null;
            Integer devNumber = matcher.group(8) == null ? null : Integer.valueOf(number(matcher.group(8), 0));
            int preRank;
            if (pre != null) {
                preRank = rank(pre);
            } else if (postNumber == null && devNumber != null) {
                preRank = -1;
            } else {
                preRank = 3;
            }
            boolean plain = pre == null && postNumber == null && devNumber == null;
            return new Parsed(
                    version.trim(),
                    epoch,
                    List.copyOf(release),
                    preRank,
                    preNumber,
                    postNumber == null ? -1 : postNumber,
                    devNumber == null ? Integer.MAX_VALUE : devNumber,
                    plain);
        }

        private static int number(String digits, int whenEmpty) {
            return digits == null || digits.isEmpty() ? whenEmpty : Integer.parseInt(digits);
        }

        private static int rank(String pre) {
            return switch (pre) {
                case "a", "alpha" -> 0;
                case "b", "beta" -> 1;
                default -> 2;
            };
        }
    }
}
