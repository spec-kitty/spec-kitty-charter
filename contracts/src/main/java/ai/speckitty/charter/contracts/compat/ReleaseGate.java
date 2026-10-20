package ai.speckitty.charter.contracts.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
 *       A version is {@code X.Y.Z} (released) or {@code X.Y.Z-SNAPSHOT} (unreleased). Each number is {@code 0} or
 *       starts with a non-zero digit ({@code 01.2.0} is not SemVer and is refused) and is at most 2147483647 (a
 *       larger one is refused). Lines inside a fenced code block ({@code ```} or {@code ~~~}) are not headings. The
 *       CHANGELOG's first second-level heading is the <i>top heading</i>, and it must be a version.</li>
 *   <li><b>Order.</b> The version headings are strictly descending by SemVer precedence, newest first: a version
 *       listed twice is refused, and so is one listed below an older one. A {@code -SNAPSHOT} ranks below its
 *       release. The top heading is above every released heading, so a {@code 1.1.5-SNAPSHOT} above a released
 *       {@code 1.2.0}, or a {@code 1.0.0-SNAPSHOT} above a released {@code 2.0.0}, is refused.</li>
 *   <li><b>Version gate.</b> {@code info.version} must be a version and equal the top heading, released or not.</li>
 *   <li><b>Merged tags only.</b> Only tags merged into HEAD ({@code git tag --merged HEAD}) count, so an older
 *       commit or a stale branch never sees a later release. Every such tag must name a released version the
 *       CHANGELOG records: a tag nobody wrote down would otherwise hide behind an older baseline.</li>
 *   <li><b>Pending release.</b> When the top heading is released and no tag for it is merged into HEAD (the release
 *       pull request, before {@code main} is tagged at its merge commit), the release is <i>pending</i>: it is
 *       compared against the previous release, and the message says "pending release".</li>
 *   <li><b>Released top, already tagged.</b> When the top heading is released and its tag is merged into HEAD, HEAD
 *       must be the commit of that tag ({@code git tag --points-at HEAD}): it is then compared against itself. Any
 *       later commit is refused until the next {@code -SNAPSHOT} is opened (heading and {@code info.version}
 *       together), so a change after a release never hides under the released version.</li>
 *   <li><b>Same major only.</b> The baseline is the newest recorded release, other than a pending one, in the
 *       <i>release line</i> of {@code info.version}: from 1.0.0 the line is the major version ({@code 1.x}); while
 *       the major is 0 it is the major and minor ({@code 0.2.x}), since in 0.x a minor version may break and a patch
 *       may not (ADR-0022). The baseline's tag must be merged into HEAD; when it is not, the gate refuses rather
 *       than fall back to an older release. With no such release there is no baseline, and the gate says so and
 *       passes: {@code 2.0.0-SNAPSHOT} with only 1.x releases is not compared, nor is {@code 0.2.0-SNAPSHOT} with
 *       only 0.1.x releases.</li>
 *   <li><b>Breaking subsection.</b> When the release line of {@code info.version} is above that of the previous
 *       release (the newest released heading below the top heading), the top section must hold a
 *       {@code ### Breaking} subsection. That is a major bump ({@code 1.4.0} to {@code 2.0.0-SNAPSHOT}, and
 *       {@code 0.3.0} to {@code 1.0.0-SNAPSHOT}) or, while the major is 0, a minor bump ({@code 0.1.2} to
 *       {@code 0.2.0-SNAPSHOT}).</li>
 *   <li><b>Breaking bump, for information.</b> On a bump of the release line that passes the Breaking rule, the
 *       previous release, the newest of the earlier line, is named when its tag is merged into HEAD
 *       ({@link #previousBreakingBaseline}), so the build can show what the new major, or the new 0.x minor,
 *       breaks. It never fails the build.</li>
 *   <li><b>Baseline is the release.</b> The contract archived at the baseline tag has an {@code info.version} equal
 *       to the version the tag names ({@link #baselineRefusal}), so a tag moved onto a later commit is not
 *       compared against as if it were the release.</li>
 * </ol>
 */
public final class ReleaseGate {

    /** The prefix of a released contract's tag; the version follows it. */
    public static final String TAG_PREFIX = "contract-charter-v";

    private static final String NUMBER = "(0|[1-9]\\d*)";
    private static final Pattern VERSION = Pattern.compile(NUMBER + "\\." + NUMBER + "\\." + NUMBER + "(-SNAPSHOT)?");
    private static final Pattern LOOSE_VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-SNAPSHOT)?");
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

    /** A parsed version. */
    private record Version(String text, int major, int minor, int patch, boolean released)
            implements Comparable<Version> {

        static Optional<Version> parse(String text) {
            Matcher m = VERSION.matcher(text);
            if (!m.matches()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Version(
                        text,
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        m.group(4) == null));
            } catch (NumberFormatException overflow) {
                return Optional.empty();
            }
        }

        /** Why text that looks like a version is not one, or empty when it is no such text or is a version. */
        static Optional<String> malformation(String text) {
            Matcher m = LOOSE_VERSION.matcher(text);
            if (!m.matches() || parse(text).isPresent()) {
                return Optional.empty();
            }
            boolean leadingZero = IntStream.rangeClosed(1, 3)
                    .anyMatch(i -> m.group(i).length() > 1 && m.group(i).charAt(0) == '0');
            return Optional.of(
                    leadingZero
                            ? "has a number with a leading zero, which SemVer forbids"
                            : "has a number above " + Integer.MAX_VALUE);
        }

        /** SemVer precedence: the numbers, then a -SNAPSHOT below its release. */
        @Override
        public int compareTo(Version other) {
            int byNumbers = Comparator.comparingInt(Version::major)
                    .thenComparingInt(Version::minor)
                    .thenComparingInt(Version::patch)
                    .compare(this, other);
            return byNumbers != 0 ? byNumbers : Boolean.compare(released, other.released);
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
        List<Version> released = sections.stream()
                .flatMap(s -> s.version().stream())
                .filter(Version::released)
                .toList();
        Set<String> recorded = new TreeSet<>();
        released.forEach(v -> recorded.add(v.tag()));
        Set<String> unrecorded = new TreeSet<>(mergedTags);
        unrecorded.removeAll(recorded);
        if (!unrecorded.isEmpty()) {
            return new Decision.Refused("Tags " + unrecorded + " are merged into HEAD, but"
                    + " contracts/charter/CHANGELOG.md does not record them as released versions (recorded: "
                    + (recorded.isEmpty() ? "none" : recorded) + ").");
        }
        Version top = sections.getFirst().version().orElseThrow();
        if (top.released() && mergedTags.contains(top.tag()) && !tagsAtHead.contains(top.tag())) {
            return new Decision.Refused("The top heading " + top.text() + " of contracts/charter/CHANGELOG.md is"
                    + " released: " + top.tag() + " is merged into HEAD, and HEAD is not that release. A change after a"
                    + " release belongs under the next version: open the next -SNAPSHOT (heading and info.version"
                    + " together).");
        }
        boolean pending = top.released() && !mergedTags.contains(top.tag());
        List<Version> candidates = pending ? released.subList(1, released.size()) : released;
        Optional<Version> baseline =
                candidates.stream().filter(v -> v.sameLine(top)).findFirst();
        if (baseline.isEmpty()) {
            return new Decision.NoBaseline("No released baseline: "
                    + (pending ? "pending release " + top.text() + "; " : "")
                    + (candidates.isEmpty()
                            ? "contracts/charter/CHANGELOG.md records no released version before this one."
                            : "contracts/charter/CHANGELOG.md records no released " + top.line() + " version,"
                                    + (top.major() > 0
                                            ? " and a new major version is not compared with an older one."
                                            : " and a new minor version of 0.x is not compared with an older"
                                                    + " one.")));
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

    /**
     * The baseline rule: the contract archived at a release tag must carry the version the tag names.
     *
     * @param tag                  the baseline tag, {@code contract-charter-v<version>}
     * @param archivedInfoVersion the {@code info.version} of the contract as archived at that tag
     * @return the refusal, or empty when the tag's contract is the release it names
     */
    public static Optional<Decision.Refused> baselineRefusal(String tag, String archivedInfoVersion) {
        String named = tag.startsWith(TAG_PREFIX) ? tag.substring(TAG_PREFIX.length()) : tag;
        if (Version.parse(named).filter(Version::released).isPresent() && named.equals(archivedInfoVersion)) {
            return Optional.empty();
        }
        return refuse("The baseline tag " + tag + " names " + named + ", but the contract archived at that tag says"
                + " its info.version is " + archivedInfoVersion + ". A baseline is the contract as released: the tag"
                + " was moved or made on the wrong commit. Tag the release's merge commit on main.");
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
    public static Optional<String> previousBreakingBaseline(String infoVersion, String changelog, Set<String> mergedTags) {
        List<Section> sections = sections(changelog);
        if (changelogRefusal(infoVersion, sections).isPresent()) {
            return Optional.empty();
        }
        Version top = sections.getFirst().version().orElseThrow();
        return previousRelease(sections)
                .filter(top::lineAbove)
                .map(Version::tag)
                .filter(mergedTags::contains);
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
        Optional<Version> info = Version.parse(infoVersion);
        if (info.isEmpty()) {
            return refuse("info.version '" + infoVersion + "' "
                    + Version.malformation(infoVersion).orElse("is not X.Y.Z or X.Y.Z-SNAPSHOT") + ".");
        }
        if (sections.isEmpty()) {
            return refuse("contracts/charter/CHANGELOG.md has no version heading (## X.Y.Z).");
        }
        for (Section section : sections) {
            Optional<String> malformed = Version.malformation(section.token());
            if (malformed.isPresent()) {
                return refuse("The heading '" + section.text() + "' of contracts/charter/CHANGELOG.md names a version"
                        + " that " + malformed.get() + ".");
            }
        }
        Section top = sections.getFirst();
        if (top.version().isEmpty()) {
            return refuse("The top heading '" + top.text() + "' of contracts/charter/CHANGELOG.md is not a version"
                    + " (X.Y.Z or X.Y.Z-SNAPSHOT).");
        }
        if (!top.version().get().text().equals(infoVersion)) {
            return refuse("info.version " + infoVersion + " of contracts/charter/openapi.yaml does not equal the top"
                    + " heading " + top.version().get().text() + " of contracts/charter/CHANGELOG.md; bump both"
                    + " together.");
        }
        Optional<Decision.Refused> unordered = orderRefusal(sections);
        if (unordered.isPresent()) {
            return unordered;
        }
        Optional<Version> previous = previousRelease(sections);
        if (previous.isPresent() && info.get().lineAbove(previous.get()) && !top.breaking()) {
            return refuse("The top section " + infoVersion + " of contracts/charter/CHANGELOG.md raises the "
                    + (info.get().major() > previous.get().major() ? "major version" : "minor version of 0.x")
                    + " from " + previous.get().text() + " to " + infoVersion + " but has no ### Breaking"
                    + " subsection; list what breaks there.");
        }
        return Optional.empty();
    }

    /** The newest released heading below the top heading. */
    private static Optional<Version> previousRelease(List<Section> sections) {
        return sections.stream()
                .skip(1)
                .flatMap(s -> s.version().stream())
                .filter(Version::released)
                .findFirst();
    }

    /** The order rules (rule 2): no version twice, the top above every release, strictly descending. */
    private static Optional<Decision.Refused> orderRefusal(List<Section> sections) {
        List<Version> versions =
                sections.stream().flatMap(s -> s.version().stream()).toList();
        Set<String> seen = new TreeSet<>();
        for (Version version : versions) {
            if (!seen.add(version.text())) {
                return refuse("contracts/charter/CHANGELOG.md lists " + version.text() + " twice; each version has one"
                        + " section.");
            }
        }
        Version top = versions.getFirst();
        Optional<Version> above = versions.stream()
                .skip(1)
                .filter(Version::released)
                .filter(v -> top.compareTo(v) <= 0)
                .findFirst();
        if (above.isPresent()) {
            return refuse("The top heading " + top.text() + " is not above the released "
                    + above.get().text()
                    + " in contracts/charter/CHANGELOG.md. A new version is higher than every release: after "
                    + above.get().text() + " open the next -SNAPSHOT (heading and info.version together).");
        }
        for (int i = 1; i < versions.size(); i++) {
            if (versions.get(i).compareTo(versions.get(i - 1)) > 0) {
                return refuse("The version headings of contracts/charter/CHANGELOG.md are not in descending order,"
                        + " newest first: " + versions.get(i).text() + " is listed after "
                        + versions.get(i - 1).text() + ".");
            }
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
