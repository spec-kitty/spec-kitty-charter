package ai.speckitty.charter.kernel.domain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PEP 440 version ranges: says whether a version satisfies a specifier set, and whether two sets share a version.
 * {@link Pep440} owns the grammar, and this class reads the clauses it parses: a set {@link Pep440} refuses is no
 * range here, and a set it accepts parses completely, every operand included, before any decision is made. A decision
 * therefore never runs on half a set, and a malformed set is always an {@link IllegalArgumentException}, never a
 * different failure part-way through.
 *
 * <p>The subset covers what a pack descriptor uses: {@code ==}, {@code !=}, {@code <=}, {@code >=}, {@code <},
 * {@code >} and {@code ~=}, joined by commas (all must hold), with an {@code ==X.Y.*} prefix wildcard, and the
 * {@code ===} identity clause, which compares the text and needs no version. Prereleases, post releases, development
 * releases and epochs order as PEP 440 orders them, with numbers of any size. A local version segment
 * ({@code +local}) is ignored for ordering, as PEP 440's public ordering ignores it, and it counts only where
 * {@code packaging} lets it count: {@code ==} and {@code !=} compare it when their operand carries one, and ignore the
 * held version's when the operand has none. A prefix match ({@code ==1.0.*}, and the prefix a {@code ~=} implies)
 * compares the epoch, zero-pads the held release, and matches pre, post and development releases of the prefix too.
 *
 * <p>Two PEP 440 refinements are not modelled: the set that excludes prereleases by default ({@code >=1.0} does not
 * admit {@code 2.0b1}), and the exclusive-comparison caveats of {@code <V} and {@code >V} about pre- and post-releases
 * of {@code V}. A pack version is a release, and an implicit rule would refuse a range its author wrote in good faith.
 * {@link #admits} and {@link #overlaps} share the one ordering, so they agree on every version.
 */
public final class Pep440Range {

    private static final Pattern VERSION = Pattern.compile(
            "^v?(?:(\\d++)!)?(\\d++(?:\\.\\d++)*+)(?:[-_.]?(a|b|c|rc|alpha|beta|pre|preview)[-_.]?(\\d*+))?"
                    + "(?:-(\\d++)|[-_.]?(post|rev|r)[-_.]?(\\d*+))?(?:[-_.]?dev[-_.]?(\\d*+))?"
                    + "(?:\\+([a-z0-9]++(?:[-_.][a-z0-9]++)*+))?$");

    private Pep440Range() {}

    /**
     * Whether a text is a well-formed range: a set {@link Pep440} accepts, with every operand a version this class
     * can order.
     *
     * @param range the specifier set
     * @return whether {@link #admits} and {@link #overlaps} accept it
     */
    public static boolean isWellFormed(String range) {
        try {
            compile(Objects.requireNonNull(range, "range"));
            return true;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    /**
     * Whether a text is a PEP 440 version.
     *
     * @param version the text
     * @return whether {@link #admits} can order it
     */
    public static boolean isVersion(String version) {
        return Parsed.parse(Objects.requireNonNull(version, "version")).isPresent();
    }

    /**
     * Whether a version satisfies every specifier in a set.
     *
     * @param range a PEP 440 specifier set, for example one admitting 1.x but not 2.0
     * @param version a PEP 440 version
     * @return true when the version satisfies the set
     * @throws IllegalArgumentException when the set is not a well-formed range or the version is not a PEP 440 version
     */
    public static boolean admits(String range, String version) {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(version, "version");
        List<Spec> specs = compile(range);
        Parsed candidate = Parsed.parse(version)
                .orElseThrow(() -> new IllegalArgumentException("not a PEP 440 version: " + version));
        return satisfies(specs, new Candidate(version.trim(), Optional.of(candidate)));
    }

    /**
     * Whether some version satisfies both specifier sets: whether two ranges asked of one pack leave a version that
     * could satisfy them together. Ranges that only touch ({@code <2} and {@code >=2}) share no version.
     *
     * <p>The answer is read from the boundaries the two sets name. Every set partitions the versions at its operands,
     * so a version at each operand and one just past it (the release padded with a deeper segment, the next
     * prerelease, post-release or development number, and the next value of the last release segment, which is the
     * first version past a {@code .*} wildcard or a {@code ~=} range), the lowest version of each release and of each
     * epoch cover every region either set can tell apart; the sets overlap when one of those candidates satisfies both
     * under {@link #admits}'s ordering. An {@code ===} clause names the one text it admits, so that text is the only
     * candidate when a set holds one.
     *
     * @param first a PEP 440 specifier set
     * @param second a PEP 440 specifier set
     * @return true when at least one version satisfies both sets
     * @throws IllegalArgumentException when either set is not a well-formed range
     */
    public static boolean overlaps(String first, String second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        List<Spec> left = compile(first);
        List<Spec> right = compile(second);
        List<Spec> all = new ArrayList<>(left);
        all.addAll(right);
        return candidates(all).stream().anyMatch(c -> satisfies(left, c) && satisfies(right, c));
    }

    // ---- the parsed set -----------------------------------------------------------------------------------------

    /** One clause with its operand parsed; {@code version} is empty for {@code ===}. */
    private record Spec(String operator, String operand, Optional<Parsed> version, boolean wildcard) {}

    /** A text to test and, when it is a version, its parsed form. */
    private record Candidate(String text, Optional<Parsed> version) {}

    private static List<Spec> compile(String range) {
        List<Pep440.Clause> clauses = Pep440.parse(range)
                .orElseThrow(() -> new IllegalArgumentException("not a PEP 440 specifier set: " + range));
        List<Spec> specs = new ArrayList<>(clauses.size());
        for (Pep440.Clause clause : clauses) {
            if (clause.operator().equals("===")) {
                specs.add(new Spec("===", clause.operand(), Optional.empty(), false));
                continue;
            }
            boolean wildcard = clause.operand().endsWith(".*");
            String text =
                    wildcard ? clause.operand().substring(0, clause.operand().length() - 2) : clause.operand();
            Parsed version = Parsed.parse(text)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "not a PEP 440 version: " + clause.operand() + " in " + clause.operator()));
            specs.add(new Spec(clause.operator(), clause.operand(), Optional.of(version), wildcard));
        }
        return specs;
    }

    private static boolean satisfies(List<Spec> specs, Candidate candidate) {
        for (Spec spec : specs) {
            if (!admitsOne(spec, candidate)) {
                return false;
            }
        }
        return true;
    }

    private static boolean admitsOne(Spec spec, Candidate candidate) {
        if (spec.operator().equals("===")) {
            return candidate.text().equalsIgnoreCase(spec.operand());
        }
        if (candidate.version().isEmpty()) {
            return false;
        }
        Parsed held = candidate.version().get();
        Parsed target = spec.version().orElseThrow();
        if (spec.wildcard()) {
            return spec.operator().equals("==") == hasPrefix(held, target.epoch(), target.release());
        }
        if (spec.operator().equals("~=")) {
            List<BigInteger> prefix =
                    target.release().subList(0, target.release().size() - 1);
            return compare(held, target) >= 0 && hasPrefix(held, target.epoch(), prefix);
        }
        int order = compare(held, target);
        boolean equal =
                order == 0 && (target.local().isEmpty() || target.local().equals(held.local()));
        return switch (spec.operator()) {
            case "==" -> equal;
            case "!=" -> !equal;
            case "<" -> order < 0;
            case "<=" -> order <= 0;
            case ">" -> order > 0;
            case ">=" -> order >= 0;
            default -> throw new IllegalStateException("unsupported PEP 440 operator: " + spec.operator());
        };
    }

    // ---- the candidates an overlap is decided on ----------------------------------------------------------------

    private static List<Candidate> candidates(List<Spec> specs) {
        Set<String> identities = new LinkedHashSet<>();
        for (Spec spec : specs) {
            if (spec.operator().equals("===")) {
                identities.add(spec.operand());
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        if (!identities.isEmpty()) {
            for (String identity : identities) {
                candidates.add(new Candidate(identity, Parsed.parse(identity)));
            }
            return candidates;
        }
        int depth = specs.stream()
                        .mapToInt(spec -> spec.version().orElseThrow().release().size())
                        .max()
                        .orElse(1)
                + 1;
        Set<String> texts = new LinkedHashSet<>();
        texts.add("0.dev0");
        for (Spec spec : specs) {
            addCandidateTexts(texts, spec, depth);
        }
        for (String text : texts) {
            candidates.add(new Candidate(text, Parsed.parse(text)));
        }
        return candidates;
    }

    /**
     * Adds to {@code texts} the versions around one operand that an overlap is decided on: the operand itself (and
     * with its local label), and its nearest neighbours in each segment. The epoch floor ({@code E!0.dev0}, the lowest
     * version of the operand's epoch) is added too, and for a {@code ~=} operand the bump of its parent release (the
     * first version past the prefix the operator implies).
     */
    private static void addCandidateTexts(Set<String> texts, Spec spec, int depth) {
        Parsed operand = spec.version().orElseThrow();
        List<BigInteger> release = operand.release();
        texts.add((operand.epoch().signum() == 0 ? "" : operand.epoch() + "!") + "0.dev0");
        String exact = canonical(operand.epoch(), release, operand.pre(), operand.post(), operand.dev());
        texts.add(exact);
        operand.local().ifPresent(local -> texts.add(exact + "+" + String.join(".", local)));
        List<BigInteger> padded = new ArrayList<>(release);
        while (padded.size() < depth) {
            padded.add(BigInteger.ZERO);
        }
        texts.add(canonical(operand.epoch(), padded, Optional.empty(), Optional.empty(), Optional.empty()) + ".1");
        texts.add(
                canonical(operand.epoch(), release, Optional.empty(), Optional.empty(), Optional.of(BigInteger.ZERO)));
        operand.pre()
                .ifPresent(pre -> texts.add(canonical(
                        operand.epoch(), release, Optional.of(pre.next()), Optional.empty(), Optional.empty())));
        texts.add(canonical(
                operand.epoch(),
                release,
                operand.pre(),
                Optional.of(operand.post().map(post -> post.add(BigInteger.ONE)).orElse(BigInteger.ZERO)),
                Optional.empty()));
        operand.dev()
                .ifPresent(dev -> texts.add(canonical(
                        operand.epoch(),
                        release,
                        operand.pre(),
                        operand.post(),
                        Optional.of(dev.add(BigInteger.ONE)))));
        texts.add(canonical(operand.epoch(), bumpLast(release), Optional.empty(), Optional.empty(), Optional.empty()));
        if (spec.operator().equals("~=")) {
            List<BigInteger> parent = release.subList(0, release.size() - 1);
            texts.add(
                    canonical(operand.epoch(), bumpLast(parent), Optional.empty(), Optional.empty(), Optional.empty()));
        }
    }

    private static List<BigInteger> bumpLast(List<BigInteger> release) {
        List<BigInteger> bumped = new ArrayList<>(release);
        int last = bumped.size() - 1;
        bumped.set(last, bumped.get(last).add(BigInteger.ONE));
        return bumped;
    }

    private static String canonical(
            BigInteger epoch,
            List<BigInteger> release,
            Optional<Pre> pre,
            Optional<BigInteger> post,
            Optional<BigInteger> dev) {
        StringBuilder text = new StringBuilder();
        if (epoch.signum() != 0) {
            text.append(epoch).append('!');
        }
        for (int i = 0; i < release.size(); i++) {
            text.append(i == 0 ? "" : ".").append(release.get(i));
        }
        pre.ifPresent(found -> text.append(found.label()).append(found.number()));
        post.ifPresent(found -> text.append(".post").append(found));
        dev.ifPresent(found -> text.append(".dev").append(found));
        return text.toString();
    }

    // ---- ordering -----------------------------------------------------------------------------------------------

    /** The prefix match of {@code ==X.*} and {@code ~=}: same epoch, and the held release, zero-padded, starts with it. */
    private static boolean hasPrefix(Parsed held, BigInteger epoch, List<BigInteger> prefix) {
        if (!held.epoch().equals(epoch)) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            BigInteger segment = i < held.release().size() ? held.release().get(i) : BigInteger.ZERO;
            if (!segment.equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int compare(Parsed left, Parsed right) {
        int byEpoch = left.epoch().compareTo(right.epoch());
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
        if (left.pre().isPresent()) {
            int byPreNumber = left.pre()
                    .get()
                    .number()
                    .compareTo(right.pre().orElseThrow().number());
            if (byPreNumber != 0) {
                return byPreNumber;
            }
        }
        int byPost = Boolean.compare(left.post().isPresent(), right.post().isPresent());
        if (byPost == 0 && left.post().isPresent()) {
            byPost = left.post().get().compareTo(right.post().orElseThrow());
        }
        if (byPost != 0) {
            return byPost;
        }
        // A development release sorts before the version it precedes: absent is the greatest.
        if (left.dev().isEmpty() || right.dev().isEmpty()) {
            return Boolean.compare(left.dev().isEmpty(), right.dev().isEmpty());
        }
        return left.dev().get().compareTo(right.dev().orElseThrow());
    }

    private static int compareRelease(List<BigInteger> left, List<BigInteger> right) {
        int size = Math.max(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            BigInteger a = i < left.size() ? left.get(i) : BigInteger.ZERO;
            BigInteger b = i < right.size() ? right.get(i) : BigInteger.ZERO;
            int order = a.compareTo(b);
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /** A prerelease segment: its normalized label ({@code a}, {@code b} or {@code rc}) and number. */
    private record Pre(String label, BigInteger number) {
        Pre next() {
            return new Pre(label, number.add(BigInteger.ONE));
        }
    }

    /** A parsed version, kept as the parts ordering needs. */
    private record Parsed(
            BigInteger epoch,
            List<BigInteger> release,
            Optional<Pre> pre,
            Optional<BigInteger> post,
            Optional<BigInteger> dev,
            Optional<List<String>> local) {

        /** Before every prerelease for a development-only release, after every prerelease for a plain one. */
        int preRank() {
            if (pre.isPresent()) {
                return switch (pre.get().label()) {
                    case "a" -> 0;
                    case "b" -> 1;
                    default -> 2;
                };
            }
            return post.isEmpty() && dev.isPresent() ? -1 : 3;
        }

        static Optional<Parsed> parse(String version) {
            // PEP 440 normalizes case, and packaging folds Unicode case in some positions, so fold before matching.
            String folded = version.trim().toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
            Matcher matcher = VERSION.matcher(folded);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            BigInteger epoch = matcher.group(1) == null ? BigInteger.ZERO : new BigInteger(matcher.group(1));
            List<BigInteger> release = new ArrayList<>();
            String dotted = matcher.group(2);
            int from = 0;
            while (from <= dotted.length()) {
                int dot = dotted.indexOf('.', from);
                int to = dot < 0 ? dotted.length() : dot;
                release.add(new BigInteger(dotted.substring(from, to)));
                from = to + 1;
            }
            Optional<Pre> pre = matcher.group(3) == null
                    ? Optional.empty()
                    : Optional.of(new Pre(label(matcher.group(3)), number(matcher.group(4))));
            Optional<BigInteger> post = matcher.group(5) != null
                    ? Optional.of(new BigInteger(matcher.group(5)))
                    : matcher.group(6) != null ? Optional.of(number(matcher.group(7))) : Optional.empty();
            Optional<BigInteger> dev =
                    matcher.group(8) == null ? Optional.empty() : Optional.of(number(matcher.group(8)));
            Optional<List<String>> local = matcher.group(9) == null
                    ? Optional.empty()
                    : Optional.of(Arrays.stream(matcher.group(9).split("[-_.]"))
                            .map(segment -> segment.chars().allMatch(Character::isDigit)
                                    ? new BigInteger(segment).toString()
                                    : segment)
                            .toList());
            return Optional.of(new Parsed(epoch, List.copyOf(release), pre, post, dev, local));
        }

        private static BigInteger number(String digits) {
            return digits == null || digits.isEmpty() ? BigInteger.ZERO : new BigInteger(digits);
        }

        private static String label(String pre) {
            return switch (pre) {
                case "a", "alpha" -> "a";
                case "b", "beta" -> "b";
                default -> "rc";
            };
        }
    }
}
