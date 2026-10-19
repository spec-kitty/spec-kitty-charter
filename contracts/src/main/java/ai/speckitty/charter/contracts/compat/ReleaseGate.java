package ai.speckitty.charter.contracts.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which released contract the breaking-change check compares against, from {@code info.version} of
 * {@code contracts/charter/openapi.yaml}, {@code contracts/charter/CHANGELOG.md} and the {@code contract-charter-v*}
 * tags merged into HEAD (DM 01M5986KJ7B18K7GQ25E47J989). This Javadoc is the authority on the gate.
 *
 * <ol>
 *   <li><b>Headings.</b> A version heading is a second-level heading ({@code ## }) whose text is a version, written
 *       {@code 1.2.0}, {@code v1.2.0} or {@code [1.2.0]}, optionally followed by more text ({@code - 2026-10-19}).
 *       A version is {@code X.Y.Z} (released) or {@code X.Y.Z-SNAPSHOT} (unreleased). The CHANGELOG lists versions
 *       newest first; its first second-level heading is the <i>top heading</i>, and it must be a version.</li>
 *   <li><b>Version gate.</b> {@code info.version} must be a version and equal the top heading, released or not.</li>
 *   <li><b>Merged tags only.</b> Only tags merged into HEAD ({@code git tag --merged HEAD}) count, so an older
 *       commit or a stale branch never sees a later release. Every such tag must name a released version the
 *       CHANGELOG records: a tag nobody wrote down would otherwise hide behind an older baseline.</li>
 *   <li><b>Pending release.</b> When the top heading is released and its tag is not merged into HEAD (the release
 *       pull request, before {@code main} is tagged at its merge commit), the release is <i>pending</i>: it is
 *       compared against the previous release, and the message says "pending release". Once its tag is merged it
 *       is compared against itself, so a breaking change after the release without a version bump still fails.</li>
 *   <li><b>Same major only.</b> The baseline is the newest recorded release, other than a pending one, with the
 *       major version of {@code info.version}. Its tag must be merged into HEAD; when it is not, the gate refuses
 *       rather than fall back to an older release. With no such release there is no baseline, and the gate says
 *       so and passes: {@code 2.0.0-SNAPSHOT} with only 1.x releases is not compared.</li>
 *   <li><b>Breaking subsection.</b> When the major version of {@code info.version} is above that of the previous
 *       release (the newest released heading below the top heading), the top section must hold a
 *       {@code ### Breaking} subsection.</li>
 * </ol>
 */
public final class ReleaseGate {

    /** The prefix of a released contract's tag; the version follows it. */
    public static final String TAG_PREFIX = "contract-charter-v";

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-SNAPSHOT)?");
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
    private record Version(String text, int major, boolean released) {

        static Optional<Version> parse(String text) {
            Matcher m = VERSION.matcher(text);
            return m.matches()
                    ? Optional.of(new Version(text, Integer.parseInt(m.group(1)), m.group(4) == null))
                    : Optional.empty();
        }

        String tag() {
            return TAG_PREFIX + text;
        }
    }

    /**
     * A second-level heading of the CHANGELOG and whether its section holds a Breaking subsection.
     *
     * @param text     the heading text
     * @param version  the version it names, if it names one
     * @param breaking whether a {@code ### Breaking} subsection follows it before the next second-level heading
     */
    private record Section(String text, Optional<Version> version, boolean breaking) {}

    /**
     * Runs the gate.
     *
     * @param infoVersion the {@code info.version} of the contract
     * @param changelog   the text of the contract's CHANGELOG
     * @param mergedTags  the {@code contract-charter-v*} tags merged into HEAD
     * @return what to compare against, or why nothing is, or why the gate fails
     */
    public static Decision decide(String infoVersion, String changelog, Set<String> mergedTags) {
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
        boolean pending = top.released() && !mergedTags.contains(top.tag());
        List<Version> candidates = pending ? released.subList(1, released.size()) : released;
        Optional<Version> baseline =
                candidates.stream().filter(v -> v.major() == top.major()).findFirst();
        if (baseline.isEmpty()) {
            return new Decision.NoBaseline("No released baseline: "
                    + (pending ? "pending release " + top.text() + "; " : "")
                    + (candidates.isEmpty()
                            ? "contracts/charter/CHANGELOG.md records no released version before this one."
                            : "contracts/charter/CHANGELOG.md records no released " + top.major() + ".x version,"
                                    + " and a new major version is not compared with an older one."));
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

    /** The version gate (rules 1 and 2) and the Breaking subsection (rule 6). */
    private static Optional<Decision.Refused> changelogRefusal(String infoVersion, List<Section> sections) {
        Optional<Version> info = Version.parse(infoVersion);
        if (info.isEmpty()) {
            return refuse("info.version '" + infoVersion + "' is not X.Y.Z or X.Y.Z-SNAPSHOT.");
        }
        if (sections.isEmpty()) {
            return refuse("contracts/charter/CHANGELOG.md has no version heading (## X.Y.Z).");
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
        Optional<Version> previous = sections.stream()
                .skip(1)
                .flatMap(s -> s.version().stream())
                .filter(Version::released)
                .findFirst();
        if (previous.isPresent() && info.get().major() > previous.get().major() && !top.breaking()) {
            return refuse("The top section " + infoVersion + " of contracts/charter/CHANGELOG.md raises the major"
                    + " version from " + previous.get().text() + " to " + infoVersion + " but has no ### Breaking"
                    + " subsection; list what breaks there.");
        }
        return Optional.empty();
    }

    private static Optional<Decision.Refused> refuse(String message) {
        return Optional.of(new Decision.Refused(message));
    }

    /** The second-level headings of the CHANGELOG, in order. */
    private static List<Section> sections(String changelog) {
        List<Section> sections = new ArrayList<>();
        for (String line : changelog.lines().toList()) {
            Matcher heading = LEVEL_TWO.matcher(line);
            if (heading.matches()) {
                sections.add(new Section(heading.group(1), versionOf(heading.group(1)), false));
            } else if (BREAKING.matcher(line).matches() && !sections.isEmpty()) {
                Section last = sections.removeLast();
                sections.add(new Section(last.text(), last.version(), true));
            }
        }
        return sections;
    }

    private static Optional<Version> versionOf(String heading) {
        Matcher m = HEADING_VERSION.matcher(heading);
        return m.matches() ? Version.parse(m.group(1)) : Optional.empty();
    }
}
