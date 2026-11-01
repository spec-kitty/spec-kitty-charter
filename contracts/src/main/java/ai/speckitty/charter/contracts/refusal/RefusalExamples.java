package ai.speckitty.charter.contracts.refusal;

import ai.speckitty.charter.contracts.closure.JsonPointer;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * The raised-code check of the bundled charter contract (#176): a client reads a refusal's examples and its
 * description to learn which codes it may meet, so neither may name a code the service does not raise.
 *
 * <p>The raised codes are read from the contract itself, never listed here: they are the keys of the {@code
 * x-refusal} map of the {@code CharterRefusalCode} schema whose {@code raised} is {@code true}. The check then
 * judges every response of every operation under {@code paths}:
 *
 * <ul>
 *   <li>for each media type whose schema is titled {@code CharterRefusal}, its {@code example.code} and every
 *       {@code examples.*.value.code} must be a raised code;
 *   <li>its {@code description} must not name a code of the map that is not raised, as a whole word, in
 *       backticks or not ({@code pack_level_mismatch} is named by {@code `pack_level_mismatch`}, not by {@code
 *       no_pack_level_mismatch}).
 * </ul>
 *
 * <p>An operation that carries {@code x-reserved} is not judged (ADR-0020 D8): it is declared in full ahead of being
 * built and answers 501, a {@code Problem} with no code, whatever the request holds, so a client meets none of the
 * refusals it declares. Those refusals describe the operation once built, and the codes reserved for it stay
 * {@code raised: false} until then. Building the operation removes its marker together with its 501
 * ({@code built-operation-declares-no-501}), and from then on its refusals are judged like any other: the codes they
 * name must be raised by that time.
 *
 * <p>A document without such a map is reported: the check would otherwise pass vacuously. The command line, run by
 * the Gradle task {@code checkRefusalExamples}, takes the bundle as its one argument and exits 0 when it passes, 1
 * with one line per violation when it does not, and 2 when it cannot be read.
 */
public final class RefusalExamples {

    private static final String CODE_SCHEMA = "CharterRefusalCode";
    private static final String NOT_RAISED = ", which " + CODE_SCHEMA + "'s x-refusal does not declare raised";

    // The bundle inlines every $ref, so each operation repeats its shared responses; with the activation set (#125) it
    // passed snakeyaml's default limit of 3 MiB of code points, as ClosureChecker notes. The file is the build's own
    // output, not input from outside, so a larger limit admits nothing untrusted.
    private static final int BUNDLE_CODE_POINT_LIMIT = 64 * 1024 * 1024;

    private RefusalExamples() {}

    /**
     * What the check judged and what it found.
     *
     * @param examples the refusal examples judged
     * @param descriptions the response descriptions judged
     * @param violations one line per violation, its JSON Pointer first; empty when the document passes
     */
    public record Report(int examples, int descriptions, List<String> violations) {

        /** Copies the violations, so a report never changes. */
        public Report {
            violations = List.copyOf(violations);
        }
    }

    /**
     * Checks the bundle at {@code bundle}, a YAML or JSON OpenAPI document.
     *
     * @param bundle the path of the bundled contract
     * @return what was judged and the violations found
     * @throws IOException when the file cannot be read
     */
    public static Report check(Path bundle) throws IOException {
        try (InputStream in = Files.newInputStream(bundle)) {
            return check(new Load(LoadSettings.builder()
                            .setLabel(bundle.toString())
                            .setCodePointLimit(BUNDLE_CODE_POINT_LIMIT)
                            .build())
                    .loadFromInputStream(in));
        }
    }

    /**
     * Checks a loaded OpenAPI document: mappings, lists and scalars as a YAML loader produces them.
     *
     * @param document the loaded document
     * @return what was judged and the violations found
     */
    public static Report check(@Nullable Object document) {
        Map<?, ?> refusals = refusalMap(document);
        if (refusals == null) {
            return new Report(0, 0, List.of("/: the document has no " + CODE_SCHEMA + " schema with an x-refusal map"));
        }
        Set<String> raised = raised(refusals);
        Judge judge = new Judge(raised, notRaised(refusals, raised));
        if (document instanceof Map<?, ?> root && root.get("paths") instanceof Map<?, ?> paths) {
            JsonPointer pathsPointer = JsonPointer.ROOT.child("paths");
            paths.forEach((path, item) -> judge.pathItem(pathsPointer.child(String.valueOf(path)), item));
        }
        return new Report(judge.examples, judge.descriptions, judge.violations);
    }

    /**
     * Checks the bundle the one argument names and exits with the code the class describes.
     *
     * @param args the path of the bundle, and nothing else
     */
    public static void main(String[] args) {
        int code = run(List.of(args), System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(List<String> args, PrintStream out) {
        if (args.size() != 1) {
            out.println("usage: RefusalExamples <bundled-openapi.yaml>");
            return 2;
        }
        Report report;
        try {
            report = check(Path.of(args.getFirst()));
        } catch (IOException | YamlEngineException e) {
            out.println("Cannot read " + args.getFirst() + ": " + e.getMessage());
            return 2;
        }
        if (report.violations().isEmpty()) {
            out.println("All " + report.examples() + " refusal examples and " + report.descriptions()
                    + " response descriptions name raised refusal codes only.");
            return 0;
        }
        out.println(report.violations().size() + " refusal examples or descriptions name a code that is not raised:");
        report.violations().forEach(out::println);
        return 1;
    }

    /** The first x-refusal map of a schema titled CharterRefusalCode, depth first, or null. */
    private static @Nullable Map<?, ?> refusalMap(@Nullable Object node) {
        if (node instanceof Map<?, ?> map) {
            if (CODE_SCHEMA.equals(map.get("title")) && map.get("x-refusal") instanceof Map<?, ?> refusals) {
                return refusals;
            }
            return firstRefusalMap(map.values());
        }
        if (node instanceof List<?> list) {
            return firstRefusalMap(list);
        }
        return null;
    }

    private static @Nullable Map<?, ?> firstRefusalMap(Iterable<?> nodes) {
        for (Object child : nodes) {
            Map<?, ?> found = refusalMap(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Set<String> raised(Map<?, ?> refusals) {
        Set<String> codes = new TreeSet<>();
        refusals.forEach((code, declaration) -> {
            if (declaration instanceof Map<?, ?> fields && Boolean.TRUE.equals(fields.get("raised"))) {
                codes.add(String.valueOf(code));
            }
        });
        return codes;
    }

    private static List<String> notRaised(Map<?, ?> refusals, Set<String> raised) {
        return refusals.keySet().stream()
                .map(String::valueOf)
                .filter(code -> !raised.contains(code))
                .sorted()
                .toList();
    }

    /** Walks the responses and collects what it judged and found. */
    private static final class Judge {

        private final Set<String> raised;
        private final List<Pattern> unraised = new ArrayList<>();
        private final List<String> unraisedCodes;
        private final List<String> violations = new ArrayList<>();
        private int examples;
        private int descriptions;

        Judge(Set<String> raised, List<String> unraisedCodes) {
            this.raised = raised;
            this.unraisedCodes = unraisedCodes;
            unraisedCodes.forEach(code ->
                    unraised.add(Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(code) + "(?![A-Za-z0-9_])")));
        }

        void pathItem(JsonPointer at, @Nullable Object item) {
            if (!(item instanceof Map<?, ?> operations)) {
                return;
            }
            operations.forEach((method, operation) -> {
                if (operation instanceof Map<?, ?> op
                        && op.get("x-reserved") == null
                        && op.get("responses") instanceof Map<?, ?> responses) {
                    JsonPointer responsesAt = at.child(String.valueOf(method)).child("responses");
                    responses.forEach(
                            (status, response) -> response(responsesAt.child(String.valueOf(status)), response));
                }
            });
        }

        private void response(JsonPointer at, @Nullable Object node) {
            if (!(node instanceof Map<?, ?> response)) {
                return;
            }
            if (response.get("description") instanceof String description) {
                description(at.child("description"), description);
            }
            if (response.get("content") instanceof Map<?, ?> content) {
                content.forEach(
                        (mediaType, body) -> mediaType(at.child("content").child(String.valueOf(mediaType)), body));
            }
        }

        private void description(JsonPointer at, String description) {
            descriptions++;
            for (int i = 0; i < unraised.size(); i++) {
                if (unraised.get(i).matcher(description).find()) {
                    violations.add(at + ": the description names " + unraisedCodes.get(i) + NOT_RAISED);
                }
            }
        }

        private void mediaType(JsonPointer at, @Nullable Object node) {
            if (!(node instanceof Map<?, ?> body)
                    || !(body.get("schema") instanceof Map<?, ?> schema)
                    || !"CharterRefusal".equals(schema.get("title"))) {
                return;
            }
            if (body.containsKey("example")) {
                example(at.child("example"), body.get("example"));
            }
            if (body.get("examples") instanceof Map<?, ?> named) {
                named.forEach((name, example) -> {
                    JsonPointer exampleAt = at.child("examples").child(String.valueOf(name));
                    Object value = example instanceof Map<?, ?> map ? map.get("value") : null;
                    example(exampleAt.child("value"), value);
                });
            }
        }

        private void example(JsonPointer at, @Nullable Object value) {
            examples++;
            Object code = value instanceof Map<?, ?> map ? map.get("code") : null;
            if (!(code instanceof String name) || !raised.contains(name)) {
                violations.add(at.child("code") + ": the example names " + code + NOT_RAISED);
            }
        }
    }
}
