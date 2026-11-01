package ai.speckitty.charter.contracts.compat;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The contract's changelog fragments (ADR-0047): a contract change adds one file under {@code contracts/charter/changes/}
 * instead of editing {@code CHANGELOG.md}, so parallel pull requests never conflict on the changelog. The release pull
 * request assembles the fragments into the top version section of {@code CHANGELOG.md}, and the publishing gate
 * ({@link CheckReleaseGate}, {@code --release}) refuses a tag while any fragment is left.
 *
 * <p>A fragment is named {@code YYYY-MM-DD-slug.md} and holds exactly one {@code ###} block: its heading, then its
 * text. The {@code README.md} of the directory is not a fragment. Assembly, newest fragment first:
 *
 * <ul>
 *   <li>a fragment whose heading names an existing subsection of the top version section ({@code ### Breaking},
 *       {@code ### Added}, ...) puts its text at the top of that subsection;
 *   <li>any other fragment becomes its own subsection, right after {@code ### Breaking}, or before the section's first
 *       subsection when it has no {@code ### Breaking}, or at the end of a section with no subsection.
 * </ul>
 *
 * <p>The version headings and the {@code ### Breaking} marker stay in {@code CHANGELOG.md}, so {@link ReleaseGate} reads
 * the same text as before. Headings inside fenced code blocks are not structure.
 *
 * <p>The command line, run by the Gradle task {@code assembleContractChangelog}: {@code ChangelogFragments
 * <CHANGELOG.md> <changes-dir>} assembles the fragments into the changelog and deletes them; it exits 0, or 2 when an
 * input cannot be read or a fragment is malformed.
 */
public final class ChangelogFragments {

    private static final Pattern NAME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-[a-z0-9]+(?:-[a-z0-9]+)*\\.md$");
    private static final Pattern LEVEL_TWO = Pattern.compile("^##(?!#)[ \\t]+\\S.*$");
    private static final Pattern LEVEL_THREE = Pattern.compile("^###(?!#)[ \\t]+(\\S.*?)[ \\t]*$");
    private static final Pattern ANY_HEADING_UP_TO_THREE = Pattern.compile("^#{1,3}(?!#)[ \\t]+\\S.*$");
    private static final Pattern FENCE = Pattern.compile("^[ \\t]{0,3}(```|~~~)");
    private static final String README = "README.md";
    private static final String BREAKING = "Breaking";

    private ChangelogFragments() {}

    /**
     * One fragment: its file name, its {@code ###} heading text and the lines under it, trimmed of blank lines.
     *
     * @param name the file name, {@code YYYY-MM-DD-slug.md}
     * @param heading the heading text after {@code ### }
     * @param body the lines under the heading, with no leading or trailing blank line
     */
    public record Fragment(String name, String heading, List<String> body) {

        /** Copies the body, so the record stays immutable. */
        public Fragment {
            body = List.copyOf(body);
        }
    }

    /**
     * Runs the assembly the class describes.
     *
     * @param args {@code <CHANGELOG.md> <changes-dir>}
     */
    public static void main(String[] args) {
        int code = run(List.of(args), System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(List<String> args, PrintStream out) {
        if (args.size() != 2) {
            out.println("usage: ChangelogFragments <CHANGELOG.md> <changes-dir>");
            return 2;
        }
        Path changelog = Path.of(args.get(0));
        Path changes = Path.of(args.get(1));
        try {
            List<Fragment> fragments = read(changes);
            Files.writeString(changelog, assemble(Files.readString(changelog), fragments), StandardCharsets.UTF_8);
            for (Fragment fragment : fragments) {
                Files.delete(changes.resolve(fragment.name()));
            }
            out.println("Assembled " + fragments.size() + (fragments.size() == 1 ? " fragment" : " fragments")
                    + " into " + changelog
                    + (fragments.isEmpty()
                            ? "."
                            : ": " + fragments.stream().map(Fragment::name).collect(Collectors.joining(", "))));
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            out.println("Cannot assemble the changelog fragments: " + e.getMessage());
            return 2;
        }
    }

    /**
     * The names of the fragments left in a directory, sorted; none when the directory does not exist.
     *
     * @param changes the fragments directory
     * @return the file names of every {@code *.md} but the {@code README.md}
     * @throws IOException when the directory cannot be listed
     */
    public static List<String> pending(Path changes) throws IOException {
        if (!Files.isDirectory(changes)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(changes)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md") && !README.equals(n))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Reads and checks every fragment in a directory, newest first (by file name, descending).
     *
     * @param changes the fragments directory; a missing one holds no fragment
     * @return the fragments, newest first
     * @throws IOException when a file cannot be read
     * @throws IllegalArgumentException when a fragment is misnamed or not exactly one {@code ###} block
     */
    public static List<Fragment> read(Path changes) throws IOException {
        List<Fragment> fragments = new ArrayList<>();
        for (String name : pending(changes)) {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException(name + " must be named YYYY-MM-DD-slug.md");
            }
            fragments.add(parse(name, Files.readString(changes.resolve(name))));
        }
        fragments.sort(Comparator.comparing(Fragment::name).reversed());
        return List.copyOf(fragments);
    }

    private static Fragment parse(String name, String text) {
        List<String> lines = trim(text.lines().toList());
        var heading = lines.isEmpty() ? null : LEVEL_THREE.matcher(lines.get(0));
        if (heading == null || !heading.matches()) {
            throw new IllegalArgumentException(name + " must start with a `### ` heading");
        }
        List<String> body = trim(lines.subList(1, lines.size()));
        if (body.isEmpty()) {
            throw new IllegalArgumentException(name + " has no text under its heading");
        }
        boolean fenced = false;
        for (String line : body) {
            if (FENCE.matcher(line).find()) {
                fenced = !fenced;
            } else if (!fenced && ANY_HEADING_UP_TO_THREE.matcher(line).matches()) {
                throw new IllegalArgumentException(
                        name + " must hold one `###` block: a `#`, `##` or `###` heading under it is not allowed");
            }
        }
        return new Fragment(name, heading.group(1), body);
    }

    /**
     * The changelog with the fragments assembled into its top version section, as the class describes.
     *
     * @param changelog the text of {@code CHANGELOG.md}
     * @param fragments the fragments, newest first, as {@link #read} returns them
     * @return the assembled text; the changelog itself when there is no fragment
     * @throws IllegalArgumentException when the changelog has no {@code ## } version section
     */
    public static String assemble(String changelog, List<Fragment> fragments) {
        if (fragments.isEmpty()) {
            return changelog;
        }
        List<String> lines = new ArrayList<>(changelog.lines().toList());
        // Oldest first, each inserted at the top of its place: the newest ends up first.
        for (Fragment fragment : fragments.reversed()) {
            insert(lines, fragment);
        }
        return String.join("\n", lines) + (changelog.endsWith("\n") ? "\n" : "");
    }

    private static void insert(List<String> lines, Fragment fragment) {
        Section section = section(lines);
        for (int i = 0; i < section.subsections().size(); i++) {
            int at = section.subsections().get(i);
            if (headingText(lines.get(at)).equals(fragment.heading())) {
                List<String> block = new ArrayList<>();
                block.add("");
                block.addAll(fragment.body());
                lines.addAll(at + 1, block);
                return;
            }
        }
        int at = section.end();
        for (int i = 0; i < section.subsections().size(); i++) {
            if (headingText(lines.get(section.subsections().get(i))).equals(BREAKING)) {
                at = i + 1 < section.subsections().size()
                        ? section.subsections().get(i + 1)
                        : section.end();
                break;
            }
            if (i == 0) {
                at = section.subsections().get(0);
            }
        }
        List<String> block = new ArrayList<>();
        if (at > 0 && !lines.get(at - 1).isBlank()) {
            block.add("");
        }
        block.add("### " + fragment.heading());
        block.add("");
        block.addAll(fragment.body());
        if (at < lines.size() && !lines.get(at).isBlank()) {
            block.add("");
        }
        lines.addAll(at, block);
    }

    /** The top version section: the line indexes of its {@code ###} headings, and where it ends. */
    private record Section(List<Integer> subsections, int end) {}

    private static Section section(List<String> lines) {
        int start = -1;
        int end = lines.size();
        List<Integer> subsections = new ArrayList<>();
        boolean fenced = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (FENCE.matcher(line).find()) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                continue;
            }
            if (LEVEL_TWO.matcher(line).matches()) {
                if (start >= 0) {
                    end = i;
                    break;
                }
                start = i;
            } else if (start >= 0 && LEVEL_THREE.matcher(line).matches()) {
                subsections.add(i);
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("the changelog has no `## ` version section to assemble into");
        }
        // The section ends before the blank lines that separate it from the next one.
        while (end - 1 > start && lines.get(end - 1).isBlank()) {
            end--;
        }
        return new Section(List.copyOf(subsections), end);
    }

    private static String headingText(String line) {
        var m = LEVEL_THREE.matcher(line);
        return m.matches() ? m.group(1) : "";
    }

    private static List<String> trim(List<String> lines) {
        int from = 0;
        int to = lines.size();
        while (from < to && lines.get(from).isBlank()) {
            from++;
        }
        while (to > from && lines.get(to - 1).isBlank()) {
            to--;
        }
        return lines.subList(from, to);
    }
}
