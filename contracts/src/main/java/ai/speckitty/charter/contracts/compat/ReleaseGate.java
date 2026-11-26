package ai.speckitty.charter.contracts.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Which released contract the breaking-change check compares against, from {@code info.version} of
 * {@code contracts/charter/openapi.yaml}, {@code contracts/charter/CHANGELOG.md} and the {@code contract-charter-v*}
 * tags merged into HEAD (DM 01M5986KJ7B18K7GQ25E47J989). This Javadoc is the authority on the gate.
 *
 * <ol>
 *   <li><b>Headings.</b> A version heading is a second-level heading ({@code ## }) whose text is a version, written
 *       {@code 1.2.0}, {@code v1.2.0} or {@code [1.2.0]}, optionally followed by more text ({@code - 2026-10-19}).
 *       A version is {@code X.Y.Z} (a final release), {@code X.Y.Z-<pre>} (a release candidate, operator ruling R1
 *       of 2026-11-16) or {@code X.Y.Z-SNAPSHOT} (unreleased). A final release and a release candidate are both
 *       <i>published</i> under a tag; "released" below means published unless it says final. The pre-release
 *       {@code <pre>} has the service line's grammar (ADR-0046 decision 1): letters, digits and dots, so
 *       {@code 0.2.0-rc.1} and not {@code 0.2.0-rc-1}, and no build metadata ({@code +...}). Each dot-separated
 *       identifier is non-empty, a numeric one has no leading zero (SemVer), and none is {@code SNAPSHOT} in any
 *       case. Each number is {@code 0} or starts with a non-zero digit ({@code 01.2.0} is not SemVer and is
 *       refused) and is at most 2147483647 (a larger one is refused). A heading that looks like a version but
 *       breaks these rules is refused, wherever it stands. Lines inside a fenced code block ({@code ```} or {@code ~~~}) are not headings. The
 *       CHANGELOG's first second-level heading is the <i>top heading</i>, and it must be a version.</li>
 *   <li><b>Order.</b> The version headings are strictly descending by SemVer precedence, newest first: a version
 *       listed twice is refused, and so is one listed below an older one. For one {@code X.Y.Z}, its release
 *       candidates rank lowest, in SemVer order ({@code rc.2} above {@code rc.1}, {@code rc.10} above
 *       {@code rc.9}), then its {@code -SNAPSHOT}, then the final {@code X.Y.Z}: after a candidate, the
 *       {@code -SNAPSHOT} is the work toward the next candidate or the final release. The top heading is above
 *       every released heading, so a {@code 1.1.5-SNAPSHOT} above a released {@code 1.2.0}, or a
 *       {@code 1.0.0-SNAPSHOT} above a released {@code 2.0.0}, is refused. When the newest released heading below
 *       the top is a release candidate, the top has its {@code X.Y.Z}: after {@code 0.2.0-rc.1} comes
 *       {@code 0.2.0-SNAPSHOT}, {@code 0.2.0-rc.2} or {@code 0.2.0}, and a {@code 0.2.1-SNAPSHOT} or
 *       {@code 0.3.0-SNAPSHOT} is refused until the final {@code 0.2.0} is recorded.</li>
 *   <li><b>Version gate.</b> {@code info.version} must be a version and equal the top heading, released or not.</li>
 *   <li><b>Merged tags only.</b> Only tags merged into HEAD ({@code git tag --merged HEAD}) count, so an older
 *       commit or a stale branch never sees a later release. Every such tag must name a released version the
 *       CHANGELOG records: a tag nobody wrote down would otherwise hide behind an older baseline.</li>
 *   <li><b>Pending release.</b> When the top heading is released and no tag for it is merged into HEAD (the release
 *       pull request, before {@code main} is tagged at its merge commit), the release is <i>pending</i>: it is
 *       compared against the previous final release (rule 7), and the message says "pending release".</li>
 *   <li><b>Released top, already tagged.</b> When the top heading is released and its tag is merged into HEAD, HEAD
 *       must be the commit of that tag ({@code git tag --points-at HEAD}): a final release is then compared against
 *       itself, and a release candidate against the previous final release of its line (rule 7). Any
 *       later commit is refused until the next {@code -SNAPSHOT} is opened (heading and {@code info.version}
 *       together), so a change after a release never hides under the released version.</li>
 *   <li><b>Same major only.</b> The baseline is the newest recorded <i>final</i> release, other than a pending one,
 *       in the
 *       <i>release line</i> of {@code info.version}: from 1.0.0 the line is the major version ({@code 1.x}); while
 *       the major is 0 it is the major and minor ({@code 0.2.x}), since in 0.x a minor version may break and a patch
 *       may not (ADR-0022). A release candidate is never a baseline: a candidate, and the {@code -SNAPSHOT} after
 *       it, are compared against the previous final release of their line, so what changed between two candidates
 *       is not compared. The baseline's tag must be merged into HEAD; when it is not, the gate refuses rather
 *       than fall back to an older release. With no such release there is no baseline, and the gate says so and
 *       passes: {@code 2.0.0-SNAPSHOT} with only 1.x releases is not compared, nor is {@code 0.2.0-SNAPSHOT} with
 *       only 0.1.x releases.</li>
 *   <li><b>Breaking subsection.</b> When the release line of {@code info.version} is above that of the previous
 *       release (the newest <i>final</i> release below the top heading; a release candidate never counts), the top
 *       section must hold a {@code ### Breaking} subsection, unless a release candidate of the same line below it,
 *       and above that previous release, already holds one: the first section of a new line lists what breaks. That is a major bump ({@code 1.4.0} to {@code 2.0.0-SNAPSHOT}, and
 *       {@code 0.3.0} to {@code 1.0.0-SNAPSHOT}) or, while the major is 0, a minor bump ({@code 0.1.2} to
 *       {@code 0.2.0-SNAPSHOT}).</li>
 *   <li><b>Breaking bump, for information.</b> On a bump of the release line that passes the Breaking rule, the
 *       previous release, the newest of the earlier line, is named when its tag is merged into HEAD
 *       ({@link #previousBreakingBaseline}), so the build can show what the new major, or the new 0.x minor,
 *       breaks. It never fails the build.</li>
 *   <li><b>Baseline is the release.</b> The contract archived at the baseline tag has an {@code info.version} equal
 *       to the version the tag names ({@link #baselineRefusal}), so a tag moved onto a later commit is not
 *       compared against as if it were the release.</li>
 *   <li><b>Publishing tag.</b> A tag pushed to publish a release must be {@code contract-charter-v<version>} for a
 *       final release or a release candidate equal to {@code info.version} (a malformed pre-release is refused), and the heading, order and version gates (rules 1 to 3)
 *       must pass, so the top heading is that version too ({@link #releaseRefusal}). The workflow
 *       {@code contract-release.yml} publishes nothing when this refuses.</li>
 *   <li><b>Packs contract tag.</b> The packs contract ({@code contracts/packs/}, #559) is its own versioned line: its
 *       version is the top heading of {@code contracts/packs/CHANGELOG.md}, and rules 1, 2 and 8 hold over that
 *       file. A tag pushed to publish it must be {@code contract-packs-v<version>} for a final release or a release
 *       candidate equal to that heading ({@link #packsReleaseRefusal}); {@code packs-contract-release.yml} publishes nothing when this
 *       refuses. Once that tag is merged into HEAD, a change to the packs contract's files under the released heading
 *       is refused on every build until the next {@code -SNAPSHOT} is opened, and a released heading below the top
 *       whose tag is not merged fails the build closed ({@link #packsChangelogRefusal(String, Set, Set)}, #663).
 *       The other rules read the charter contract only.</li>
 * </ol>
 */
public final class ReleaseGate {

    /** The prefix of a released contract's tag; the version follows it. */
    public static final String TAG_PREFIX = "contract-charter-v";

    /** The tag prefix of a packs contract release ({@code contracts/packs/}, #559). */
    public static final String PACKS_TAG_PREFIX = "contract-packs-v";

    /** The packs contract's CHANGELOG, whose top heading is the packs contract's version (#559). */
    public static final String PACKS_CHANGELOG = "contracts/packs/CHANGELOG.md";

    private static final String NUMBER = "(0|[1-9]\\d*)";
    private static final String SNAPSHOT = "SNAPSHOT";
    // The service line's pre-release grammar (ADR-0046 decision 1): letters, digits and dots, no build metadata.
    private static final String PRE = "[0-9A-Za-z.]+";
    private static final Pattern PRE_RELEASE = Pattern.compile(PRE);
    private static final Pattern VERSION =
            Pattern.compile(NUMBER + "\\." + NUMBER + "\\." + NUMBER + "(?:-(" + PRE + "))?");
    private static final Pattern LOOSE_VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:([-+])(.*))?");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern LEVEL_TWO = Pattern.compile("^##(?!#)[ \\t]+(.*?)[ \\t]*$");
    private static final Pattern HEADING_VERSION = Pattern.compile("^\\[?v?([^\\s\\]]+)\\]?(?:\\s.*)?$");
    private static final Pattern BREAKING = Pattern.compile("^###(?!#)[ \\t]+Breaking\\b.*$", Pattern.CASE_INSENSITIVE);

    private ReleaseGate() {}

    /** The outcome of the gate. */
    public sealed interface Decision {

        /**
         * What the gate tells the person running the check.
         *
         * @return one sentence
         */
        String message();

        /**
         * Compare against the released contract at a tag.
         *
         * @param tag     the baseline tag
         * @param message which release it is, and whether a release is pending
         */
        record Compare(String tag, String message) implements Decision {}

        /**
         * There is no released baseline; the check passes.
         *
         * @param message why
         */
        record NoBaseline(String message) implements Decision {}

        /**
         * The git history was not consulted on request; the CHANGELOG gates passed.
         *
         * @param message what was and was not checked
         */
        record Skipped(String message) implements Decision {}

        /**
         * The gate fails.
         *
         * @param message why, and how to fix it
         */
        record Refused(String message) implements Decision {}
    }

    /** What a version's suffix makes it. */
    private enum Kind {
        /** {@code X.Y.Z-<pre>}: a release candidate, published under its tag but never a baseline. */
        PRE_RELEASE,
        /** {@code X.Y.Z-SNAPSHOT}: unreleased. */
        SNAPSHOT,
        /** {@code X.Y.Z}: a final release. */
        FINAL
    }

    /** A parsed version. */
    private record Version(String text, int major, int minor, int patch, Kind kind, List<String> pre)
            implements Comparable<Version> {

        Version {
            pre = List.copyOf(pre);
        }

        static Optional<Version> parse(String text) {
            Matcher m = VERSION.matcher(text);
            if (!m.matches() || preReleaseFault(m.group(4)).isPresent()) {
                return Optional.empty();
            }
            Kind kind =
                    m.group(4) == null ? Kind.FINAL : SNAPSHOT.equals(m.group(4)) ? Kind.SNAPSHOT : Kind.PRE_RELEASE;
            try {
                return Optional.of(new Version(
                        text,
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        kind,
                        kind == Kind.PRE_RELEASE ? List.of(m.group(4).split("\\.")) : List.of()));
            } catch (NumberFormatException overflow) {
                return Optional.empty();
            }
        }

        /** Why a pre-release suffix that has the grammar's characters is not one, or empty when it is (or is none). */
        private static Optional<String> preReleaseFault(String suffix) {
            if (suffix == null || SNAPSHOT.equals(suffix)) {
                return Optional.empty();
            }
            for (String identifier : suffix.split("\\.", -1)) {
                if (identifier.isEmpty()) {
                    return Optional.of("has an empty pre-release identifier in -" + suffix);
                }
                if (identifier.chars().allMatch(Character::isDigit)
                        && identifier.length() > 1
                        && identifier.charAt(0) == '0') {
                    return Optional.of("has a numeric pre-release identifier with a leading zero in -" + suffix
                            + ", which SemVer forbids");
                }
                if (SNAPSHOT.equalsIgnoreCase(identifier)) {
                    return Optional.of("has -" + suffix + ": an unreleased version ends in -SNAPSHOT, upper case and"
                            + " alone, and a release candidate never says SNAPSHOT");
                }
            }
            return Optional.empty();
        }

        /** Why text that looks like a version is not one, or empty when it is no such text or is a version. */
        static Optional<String> malformation(String text) {
            Matcher m = LOOSE_VERSION.matcher(text);
            if (!m.matches() || parse(text).isPresent()) {
                return Optional.empty();
            }
            if (m.group(4) != null) {
                if ("+".equals(m.group(4))) {
                    return Optional.of("has build metadata (+...), which a contract version may not carry");
                }
                if (!PRE_RELEASE.matcher(m.group(5)).matches()) {
                    return Optional.of("has a pre-release -" + m.group(5) + " that is not letters, digits and dots"
                            + " (write rc.1, not rc-1)");
                }
                Optional<String> fault = preReleaseFault(m.group(5));
                if (fault.isPresent()) {
                    return fault;
                }
            }
            boolean leadingZero = IntStream.rangeClosed(1, 3)
                    .anyMatch(i -> m.group(i).length() > 1 && m.group(i).charAt(0) == '0');
            return Optional.of(
                    leadingZero
                            ? "has a number with a leading zero, which SemVer forbids"
                            : "has a number above " + Integer.MAX_VALUE);
        }

        /**
         * The release-gate precedence: the numbers, then, for one X.Y.Z, its release candidates in SemVer order, then
         * its {@code -SNAPSHOT}, then the final release. A {@code -SNAPSHOT} ranks above the candidates because after
         * one it is the development toward the next candidate or the final release.
         */
        @Override
        public int compareTo(Version other) {
            int byNumbers = Comparator.comparingInt(Version::major)
                    .thenComparingInt(Version::minor)
                    .thenComparingInt(Version::patch)
                    .compare(this, other);
            if (byNumbers != 0) {
                return byNumbers;
            }
            int byKind = kind.compareTo(other.kind);
            return byKind != 0 || kind != Kind.PRE_RELEASE ? byKind : comparePre(pre, other.pre);
        }

        /** SemVer 2.0.0 item 11: identifier by identifier, numbers numerically and below text, a prefix lower. */
        private static int comparePre(List<String> left, List<String> right) {
            for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
                String a = left.get(i);
                String b = right.get(i);
                boolean numericA = a.chars().allMatch(Character::isDigit);
                boolean numericB = b.chars().allMatch(Character::isDigit);
                int by;
                if (numericA && numericB) {
                    by = a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
                } else if (numericA != numericB) {
                    by = numericA ? -1 : 1;
                } else {
                    by = a.compareTo(b);
                }
                if (by != 0) {
                    return by;
                }
            }
            return Integer.compare(left.size(), right.size());
        }

        /** Whether it is published under a tag: a final release or a release candidate, not a -SNAPSHOT. */
        boolean published() {
            return kind != Kind.SNAPSHOT;
        }

        /** Whether it is a final release, the only kind a compatibility baseline may be. */
        boolean isFinal() {
            return kind == Kind.FINAL;
        }

        boolean preRelease() {
            return kind == Kind.PRE_RELEASE;
        }

        /** Whether it has the same X.Y.Z as another, whatever its suffix. */
        boolean sameNumbers(Version other) {
            return major == other.major && minor == other.minor && patch == other.patch;
        }

        /** {@code X.Y.Z} without a suffix. */
        String numbers() {
            return major + "." + minor + "." + patch;
        }

        String tag() {
            return TAG_PREFIX + text;
        }

        /** Whether this version is in the same release line as another: the major, and while it is 0 the minor. */
        boolean sameLine(Version other) {
            return major == other.major && (major > 0 || minor == other.minor);
        }

        /** Whether this version's release line is above another's: a higher major, or a higher 0.x minor. */
        boolean lineAbove(Version other) {
            return major > other.major || (major == 0 && other.major == 0 && minor > other.minor);
        }

        /** The release line, {@code 2.x} or {@code 0.3.x}. */
        String line() {
            return major > 0 ? major + ".x" : "0." + minor + ".x";
        }
    }

    /**
     * A second-level heading of the CHANGELOG and whether its section holds a Breaking subsection.
     *
     * @param text     the heading text
     * @param token    the part of the heading that is meant as the version
     * @param version  the version it names, if it names one
     * @param breaking whether a {@code ### Breaking} subsection follows it before the next second-level heading
     */
    private record Section(String text, String token, Optional<Version> version, boolean breaking) {}

    /**
     * A versioned line the CHANGELOG gates check: the charter contract, or the packs contract (#559).
     *
     * @param changelog the CHANGELOG's repository path, named in every refusal
     * @param mismatch  the refusal when the version source and the top heading differ, given both versions
     */
    private record Line(String changelog, BinaryOperator<String> mismatch) {
        static final Line CHARTER = new Line(
                "contracts/charter/CHANGELOG.md",
                (version, top) -> "info.version " + version + " of contracts/charter/openapi.yaml does not equal the"
                        + " top heading " + top + " of contracts/charter/CHANGELOG.md; bump both together.");

        static Line packs(String tag) {
            return new Line(
                    PACKS_CHANGELOG,
                    (version, top) -> "The tag " + tag + " names " + version + ", but the top heading of "
                            + PACKS_CHANGELOG + " at that commit is " + top + ". A release tag names the released"
                            + " version the top heading carries: tag the release's merge commit on main.");
        }

        String mismatch(String version, String top) {
            return mismatch.apply(version, top);
        }
    }

    /**
     * Runs the gate.
     *
     * @param infoVersion the {@code info.version} of the contract
     * @param changelog   the text of the contract's CHANGELOG
     * @param mergedTags  the {@code contract-charter-v*} tags merged into HEAD
     * @param tagsAtHead  the {@code contract-charter-v*} tags that point at HEAD itself
     * @return what to compare against, or why nothing is, or why the gate fails
     */
    public static Decision decide(
            String infoVersion, String changelog, Set<String> mergedTags, Set<String> tagsAtHead) {
        List<Section> sections = sections(changelog);
        Optional<Decision.Refused> refused = changelogRefusal(infoVersion, sections);
        if (refused.isPresent()) {
            return refused.get();
        }
        List<Version> published = sections.stream()
                .flatMap(s -> s.version().stream())
                .filter(Version::published)
                .toList();
        Optional<Decision.Refused> unrecorded = unrecordedRefusal(published, mergedTags);
        if (unrecorded.isPresent()) {
            return unrecorded.get();
        }
        Version top = sections.getFirst().version().orElseThrow();
        if (top.published() && mergedTags.contains(top.tag()) && !tagsAtHead.contains(top.tag())) {
            return new Decision.Refused("The top heading " + top.text() + " of contracts/charter/CHANGELOG.md is"
                    + " released: " + top.tag() + " is merged into HEAD, and HEAD is not that release. A change after a"
                    + " release belongs under the next version: open the next -SNAPSHOT (heading and info.version"
                    + " together"
                    + (top.preRelease() ? "; after a release candidate it is " + top.numbers() + "-SNAPSHOT" : "")
                    + ").");
        }
        boolean pending = top.published() && !mergedTags.contains(top.tag());
        // Rule 7: only a final release is a baseline; a release candidate never is.
        List<Version> candidates = published.stream()
                .filter(Version::isFinal)
                .filter(v -> !(pending && v.equals(top)))
                .toList();
        Optional<Version> baseline =
                candidates.stream().filter(v -> v.sameLine(top)).findFirst();
        if (baseline.isEmpty()) {
            return noBaseline(top, pending, candidates.isEmpty());
        }
        String tag = baseline.get().tag();
        if (!mergedTags.contains(tag)) {
            return new Decision.Refused("contracts/charter/CHANGELOG.md records "
                    + baseline.get().text()
                    + " as released, but its tag " + tag + " is not merged into HEAD. Fetch the tags, or tag the"
                    + " release's merge commit on main.");
        }
        return new Decision.Compare(
                tag,
                (pending ? "pending release " + top.text() + ": " : "") + "compared against "
                        + baseline.get().text());
    }

    /** Refuses tags merged into HEAD that the CHANGELOG does not record as published versions. */
    private static Optional<Decision.Refused> unrecordedRefusal(List<Version> published, Set<String> mergedTags) {
        Set<String> recorded = new TreeSet<>();
        published.forEach(v -> recorded.add(v.tag()));
        Set<String> unrecorded = new TreeSet<>(mergedTags);
        unrecorded.removeAll(recorded);
        if (unrecorded.isEmpty()) {
            return Optional.empty();
        }
        return refuse("Tags " + unrecorded + " are merged into HEAD, but"
                + " contracts/charter/CHANGELOG.md does not record them as released versions (recorded: "
                + (recorded.isEmpty() ? "none" : recorded) + ").");
    }

    /** Why the top version has no released baseline on its line. */
    private static Decision.NoBaseline noBaseline(Version top, boolean pending, boolean noCandidates) {
        return new Decision.NoBaseline("No released baseline: "
                + (pending ? "pending release " + top.text() + "; " : "")
                + (noCandidates
                        ? "contracts/charter/CHANGELOG.md records no final release before this one."
                        : "contracts/charter/CHANGELOG.md records no final " + top.line() + " release,"
                                + (top.major() > 0
                                        ? " and a new major version is not compared with an older one."
                                        : " and a new minor version of 0.x is not compared with an older one.")));
    }

    /**
     * The baseline rule: the contract archived at a release tag must carry the version the tag names.
     *
     * @param tag                  the baseline tag, {@code contract-charter-v<version>}
     * @param archivedInfoVersion the {@code info.version} of the contract as archived at that tag
     * @return the refusal, or empty when the tag's contract is the release it names
     */
    public static Optional<Decision.Refused> baselineRefusal(String tag, String archivedInfoVersion) {
        String named = tag.startsWith(TAG_PREFIX) ? tag.substring(TAG_PREFIX.length()) : tag;
        if (Version.parse(named).filter(Version::isFinal).isPresent() && named.equals(archivedInfoVersion)) {
            return Optional.empty();
        }
        return refuse("The baseline tag " + tag + " names " + named + ", but the contract archived at that tag says"
                + " its info.version is " + archivedInfoVersion + ". A baseline is the contract as released: the tag"
                + " was moved or made on the wrong commit. Tag the release's merge commit on main.");
    }

    /**
     * The publishing rule: a tag pushed to publish a release must name a released version, and that version must be
     * both the contract's {@code info.version} and the top CHANGELOG heading (with the heading, order and version
     * gates passing). The workflow {@code contract-release.yml} publishes nothing when this refuses.
     *
     * @param tag         the pushed tag, {@code contract-charter-v<version>}
     * @param infoVersion the {@code info.version} of the contract at that tag
     * @param changelog   the text of {@code contracts/charter/CHANGELOG.md} at that tag
     * @return the refusal, or empty when the tag, {@code info.version} and the top heading name one released version
     */
    public static Optional<Decision.Refused> releaseRefusal(String tag, String infoVersion, String changelog) {
        if (!tag.startsWith(TAG_PREFIX)) {
            return refuse("The tag " + tag + " is not a contract release tag: it must be " + TAG_PREFIX + "<version>.");
        }
        String named = tag.substring(TAG_PREFIX.length());
        Optional<String> unpublishable = unpublishable(tag, named);
        if (unpublishable.isPresent()) {
            return refuse(unpublishable.get());
        }
        if (!named.equals(infoVersion)) {
            return refuse("The tag " + tag + " names " + named + ", but the contract at that tag says its"
                    + " info.version is " + infoVersion + ". A release tag names the released version the contract"
                    + " and the top CHANGELOG heading carry: tag the release's merge commit on main.");
        }
        return changelogRefusal(infoVersion, sections(changelog));
    }

    /**
     * The publishing rule of the packs contract (rule 12, #559): a tag pushed to publish a packs contract release must
     * be {@code contract-packs-v<version>} for a released version, and the heading, order and Breaking rules (1, 2 and
     * 8) must pass over {@code contracts/packs/CHANGELOG.md} with that version as its top heading. The packs contract
     * has no other version carrier: its CHANGELOG's top heading is its version.
     *
     * @param tag       the pushed tag, {@code contract-packs-v<version>}
     * @param changelog the text of {@code contracts/packs/CHANGELOG.md} at that tag
     * @return the refusal, or empty when the tag and the top heading name one released version
     */
    public static Optional<Decision.Refused> packsReleaseRefusal(String tag, String changelog) {
        if (!tag.startsWith(PACKS_TAG_PREFIX)) {
            return refuse("The tag " + tag + " is not a packs contract release tag: it must be " + PACKS_TAG_PREFIX
                    + "<version>.");
        }
        String named = tag.substring(PACKS_TAG_PREFIX.length());
        Optional<String> unpublishable = unpublishable(tag, named);
        if (unpublishable.isPresent()) {
            return refuse(unpublishable.get());
        }
        return changelogRefusal(named, sections(changelog), Line.packs(tag));
    }

    /** Why a tag's version may not be published: a -SNAPSHOT, or no version (a malformed pre-release among them). */
    private static Optional<String> unpublishable(String tag, String named) {
        if (Version.parse(named).filter(Version::published).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("The tag " + tag + " names " + named + ", which is not a release (X.Y.Z) or a release"
                + " candidate (X.Y.Z-<pre>, letters, digits and dots): "
                + (named.endsWith("-" + SNAPSHOT)
                        ? "a -SNAPSHOT is never published."
                        : "it " + Version.malformation(named).orElse("is not a version") + "."));
    }

    /**
     * The packs contract's CHANGELOG heading rules (#559): the heading, order and Breaking rules (1, 2 and 8) over
     * {@code contracts/packs/CHANGELOG.md}, whose top heading, released or not, is the packs contract's version. No
     * tag is read: {@code checkPacksChangelog} runs only these when {@code -PcontractBaseline=none} skips the tag rules
     * on purpose outside CI. Every other build runs {@link #packsChangelogRefusal(String, Set, Set)}.
     *
     * @param changelog the text of {@code contracts/packs/CHANGELOG.md}
     * @return the refusal, or empty when the rules pass
     */
    public static Optional<Decision.Refused> packsChangelogRefusal(String changelog) {
        List<Section> sections = sections(changelog);
        if (sections.isEmpty()) {
            return refuse(PACKS_CHANGELOG + " has no version heading (## X.Y.Z).");
        }
        Section top = sections.getFirst();
        if (top.version().isEmpty()) {
            return refuse("The top heading '" + top.text() + "' of " + PACKS_CHANGELOG + " is not a version"
                    + " (X.Y.Z, X.Y.Z-<pre> or X.Y.Z-SNAPSHOT).");
        }
        String version = top.version().get().text();
        return changelogRefusal(version, sections, Line.packs(PACKS_TAG_PREFIX + version));
    }

    /**
     * The packs contract's CHANGELOG rules on every build: the heading rules of {@link #packsChangelogRefusal(String)},
     * then two tag rules (#663), with each heading's tag named by this gate's heading grammar.
     *
     * <ul>
     *   <li>Every released heading below the top has its {@code contract-packs-v<version>} tag merged into HEAD. A
     *       missing one means the clone lacks the tags, or the release was never tagged; either way the freeze below
     *       could not be judged, so the build fails closed rather than passing unchecked.</li>
     *   <li>When the top heading is released and its tag is merged into HEAD, the packs contract's files at HEAD are
     *       those of the tag: a change after a release belongs under the next {@code -SNAPSHOT}. Unlike the charter
     *       contract's rule 6, which refuses any later commit, other files may change. Before its tag is merged (the
     *       release pull request) the top heading is not judged.</li>
     * </ul>
     *
     * @param changelog    the text of {@code contracts/packs/CHANGELOG.md}
     * @param mergedTags   the {@code contract-packs-v*} tags merged into HEAD
     * @param changedSince those of them whose packs contract files differ at HEAD
     * @return the refusal, or empty when the rules pass
     */
    public static Optional<Decision.Refused> packsChangelogRefusal(
            String changelog, Set<String> mergedTags, Set<String> changedSince) {
        Optional<Decision.Refused> refused = packsChangelogRefusal(changelog);
        if (refused.isPresent()) {
            return refused;
        }
        List<Section> sections = sections(changelog);
        for (Section below : sections.subList(1, sections.size())) {
            Optional<Version> released = below.version().filter(Version::published);
            if (released.isPresent()
                    && !mergedTags.contains(PACKS_TAG_PREFIX + released.get().text())) {
                return refuse("The released heading ## " + released.get().text() + " of " + PACKS_CHANGELOG
                        + " has no " + PACKS_TAG_PREFIX + released.get().text() + " tag merged into HEAD, so the"
                        + " packs contract's files cannot be checked against the releases. Fetch the tags (git fetch"
                        + " --tags; actions/checkout fetch-depth: 0), or tag that release.");
            }
        }
        Version version = sections.getFirst().version().orElseThrow();
        String tag = PACKS_TAG_PREFIX + version.text();
        if (version.published() && mergedTags.contains(tag) && changedSince.contains(tag)) {
            return refuse("The top heading " + version.text() + " of " + PACKS_CHANGELOG + " is released: " + tag
                    + " is merged into HEAD, and the packs contract's files (file-kinds.yaml, schemas/, conformance/)"
                    + " differ from that release. A change after a release belongs under the next version: "
                    + (version.preRelease()
                            ? "reopen ## " + version.numbers() + "-SNAPSHOT above ## " + version.text() + "."
                            : "open the next -SNAPSHOT above ## " + version.text() + "."));
        }
        return Optional.empty();
    }

    /**
     * The release a breaking bump is shown against, for information only: when the top section raises the release
     * line above that of the previous release (a major bump, or a minor bump while the major is 0), that release's
     * tag, provided it is merged into HEAD.
     *
     * @param infoVersion the {@code info.version} of the contract
     * @param changelog   the text of the contract's CHANGELOG
     * @param mergedTags  the {@code contract-charter-v*} tags merged into HEAD
     * @return the tag of the previous release, or empty when this is no breaking bump, the CHANGELOG gates refuse,
     *         or that tag is not merged into HEAD
     */
    public static Optional<String> previousBreakingBaseline(
            String infoVersion, String changelog, Set<String> mergedTags) {
        List<Section> sections = sections(changelog);
        if (changelogRefusal(infoVersion, sections).isPresent()) {
            return Optional.empty();
        }
        Version top = sections.getFirst().version().orElseThrow();
        return previousFinal(sections).filter(top::lineAbove).map(Version::tag).filter(mergedTags::contains);
    }

    /**
     * Runs only the gates that read no git history: the version gate and the Breaking subsection.
     *
     * @param infoVersion the {@code info.version} of the contract
     * @param changelog   the text of the contract's CHANGELOG
     * @return {@link Decision.Skipped} when they pass, else {@link Decision.Refused}
     */
    public static Decision decideWithoutGit(String infoVersion, String changelog) {
        Optional<Decision.Refused> refused = changelogRefusal(infoVersion, sections(changelog));
        return refused.isPresent()
                ? refused.get()
                : new Decision.Skipped(
                        "The CHANGELOG gates pass: info.version " + infoVersion + " equals the top CHANGELOG heading.");
    }

    /** The heading, order and version gates (rules 1 to 3) and the Breaking subsection (rule 8). */
    private static Optional<Decision.Refused> changelogRefusal(String infoVersion, List<Section> sections) {
        return changelogRefusal(infoVersion, sections, Line.CHARTER);
    }

    /**
     * The heading, order and version gates and the Breaking subsection of one versioned line.
     *
     * @param infoVersion the version the line's version source names, which the top heading must equal
     * @param sections    the line's CHANGELOG headings
     * @param line        the line, for its CHANGELOG's name and its version mismatch
     */
    private static Optional<Decision.Refused> changelogRefusal(String infoVersion, List<Section> sections, Line line) {
        String file = line.changelog();
        Optional<Version> info = Version.parse(infoVersion);
        if (info.isEmpty()) {
            return refuse("info.version '" + infoVersion + "' "
                    + Version.malformation(infoVersion).orElse("is not X.Y.Z, X.Y.Z-<pre> or X.Y.Z-SNAPSHOT") + ".");
        }
        if (sections.isEmpty()) {
            return refuse(file + " has no version heading (## X.Y.Z).");
        }
        for (Section section : sections) {
            Optional<String> malformed = Version.malformation(section.token());
            if (malformed.isPresent()) {
                return refuse("The heading '" + section.text() + "' of " + file + " names a version" + " that "
                        + malformed.get() + ".");
            }
        }
        Section top = sections.getFirst();
        if (top.version().isEmpty()) {
            return refuse("The top heading '" + top.text() + "' of " + file + " is not a version"
                    + " (X.Y.Z, X.Y.Z-<pre> or X.Y.Z-SNAPSHOT).");
        }
        if (!top.version().get().text().equals(infoVersion)) {
            return refuse(line.mismatch(infoVersion, top.version().get().text()));
        }
        Optional<Decision.Refused> unordered = orderRefusal(sections, file);
        if (unordered.isPresent()) {
            return unordered;
        }
        Optional<Version> previous = previousFinal(sections);
        if (previous.isPresent()
                && info.get().lineAbove(previous.get())
                && !top.breaking()
                && !breakingInCandidate(sections, info.get())) {
            return refuse("The top section " + infoVersion + " of " + file + " raises the "
                    + (info.get().major() > previous.get().major() ? "major version" : "minor version of 0.x")
                    + " from " + previous.get().text() + " to " + infoVersion + " but has no ### Breaking"
                    + " subsection; list what breaks there.");
        }
        return Optional.empty();
    }

    /** The newest final release below the top heading; a release candidate is never the previous release. */
    private static Optional<Version> previousFinal(List<Section> sections) {
        return sections.stream()
                .skip(1)
                .flatMap(s -> s.version().stream())
                .filter(Version::isFinal)
                .findFirst();
    }

    /**
     * Whether a release candidate of the top's line, below the top and above the previous final release, already
     * holds the {@code ### Breaking} subsection: the first section of a new line lists what breaks, once.
     */
    private static boolean breakingInCandidate(List<Section> sections, Version top) {
        for (Section section : sections.subList(1, sections.size())) {
            Optional<Version> version = section.version();
            if (version.isPresent() && version.get().isFinal()) {
                return false;
            }
            if (version.isPresent()
                    && version.get().preRelease()
                    && version.get().sameLine(top)
                    && section.breaking()) {
                return true;
            }
        }
        return false;
    }

    /** The order rules (rule 2): no version twice, the top above every release, strictly descending. */
    private static Optional<Decision.Refused> orderRefusal(List<Section> sections, String file) {
        List<Version> versions =
                sections.stream().flatMap(s -> s.version().stream()).toList();
        Set<String> seen = new TreeSet<>();
        for (Version version : versions) {
            if (!seen.add(version.text())) {
                return refuse(file + " lists " + version.text() + " twice; each version has one" + " section.");
            }
        }
        Version top = versions.getFirst();
        Optional<Version> above = versions.stream()
                .skip(1)
                .filter(Version::published)
                .filter(v -> top.compareTo(v) <= 0)
                .findFirst();
        if (above.isPresent()) {
            return refuse("The top heading " + top.text() + " is not above the released "
                    + above.get().text()
                    + " in " + file + ". A new version is higher than every release: after "
                    + above.get().text() + " open the next -SNAPSHOT (heading and info.version together).");
        }
        for (int i = 1; i < versions.size(); i++) {
            if (versions.get(i).compareTo(versions.get(i - 1)) > 0) {
                return refuse("The version headings of " + file + " are not in descending order,"
                        + " newest first: " + versions.get(i).text() + " is listed after "
                        + versions.get(i - 1).text() + ".");
            }
        }
        Optional<Version> candidate =
                versions.stream().skip(1).filter(Version::published).findFirst().filter(Version::preRelease);
        if (candidate.isPresent() && !top.sameNumbers(candidate.get())) {
            Version rc = candidate.get();
            return refuse("The top heading " + top.text() + " of " + file + " follows the release candidate "
                    + rc.text() + ", whose final release is not recorded. After a release candidate the next version"
                    + " is " + rc.numbers() + "-SNAPSHOT, a later candidate of " + rc.numbers() + " or the final "
                    + rc.numbers() + ".");
        }
        return Optional.empty();
    }

    private static Optional<Decision.Refused> refuse(String message) {
        return Optional.of(new Decision.Refused(message));
    }

    /** The second-level headings of the CHANGELOG, in order, outside fenced code blocks. */
    private static List<Section> sections(String changelog) {
        List<Section> sections = new ArrayList<>();
        String fence = "";
        for (String line : changelog.lines().toList()) {
            Matcher fenceLine = FENCE.matcher(line);
            if (fenceLine.matches()) {
                String marks = fenceLine.group(1);
                if (fence.isEmpty()) {
                    fence = marks;
                } else if (marks.charAt(0) == fence.charAt(0)
                        && marks.length() >= fence.length()
                        && fenceLine.group(2).isBlank()) {
                    fence = "";
                }
                continue;
            }
            if (!fence.isEmpty()) {
                continue;
            }
            Matcher heading = LEVEL_TWO.matcher(line);
            if (heading.matches()) {
                String token = tokenOf(heading.group(1));
                sections.add(new Section(heading.group(1), token, Version.parse(token), false));
            } else if (BREAKING.matcher(line).matches() && !sections.isEmpty()) {
                Section last = sections.removeLast();
                sections.add(new Section(last.text(), last.token(), last.version(), true));
            }
        }
        return sections;
    }

    private static String tokenOf(String heading) {
        Matcher m = HEADING_VERSION.matcher(heading);
        return m.matches() ? m.group(1) : heading;
    }
}
