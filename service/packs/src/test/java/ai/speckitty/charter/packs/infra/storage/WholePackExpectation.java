package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What the whole-pack gate expects to see, and the two-way ratchet that holds the observed result to it.
 *
 * <p>Three checked-in lists drive it. {@code whole-pack/expected-red.yaml} names what is known not to round-trip yet: a
 * whole gate kind with no adapter ({@code kinds}), or a single file of a modelled kind ({@code files}), each with a
 * reason and the issue that owns it; it also names a modelled kind the pack has no file of ({@code empty_kinds}), which
 * the gate cannot prove and must not pass in silence. {@code whole-pack/exclusions.yaml} is the gate's own append-only list of files it
 * does not compare, each with a reason. {@code whole-pack/known-failures.yaml} names files of a modelled kind that fail
 * because the pack's own data is inconsistent, each pinned to where it differs and to the spec-kitty-open-packs issue
 * that owns the fix (ADR-0036 decision 5).
 *
 * <p>The gate passes only when the observed reds are exactly the expected ones: an unlisted red fails, and so does a
 * listed item that now round-trips, so a fix is recorded the moment it lands. Stale entries, an exclusion over a
 * modelled file and a modelled kind with no files fail too, so the gate cannot pass vacuously.
 */
final class WholePackExpectation {

    static final String EXPECTED_RED_RESOURCE = "whole-pack/expected-red.yaml";
    static final String EXCLUSIONS_RESOURCE = "whole-pack/exclusions.yaml";
    static final String KNOWN_FAILURES_RESOURCE = "whole-pack/known-failures.yaml";

    /** The only form of issue a known failure may name: an issue of spec-kitty-open-packs. */
    private static final Pattern OPEN_PACKS_ISSUE = Pattern.compile("spec-kitty/spec-kitty-open-packs#[1-9]\\d*");

    /** The status of one file in a gate run. */
    enum Status {
        /** Written, read back, and its meaning is unchanged. */
        GREEN,
        /** Expected to differ, and does. */
        EXPECTED_RED,
        /** Not compared, for a listed reason. */
        EXCLUDED,
        /** Differs, and nothing expects it to: a problem. */
        RED,
        /** Differs because of a pack inconsistency tracked upstream, and is listed as a known failure. */
        KNOWN_FAILURE
    }

    /**
     * An expected-red gate kind: no adapter models it yet.
     *
     * @param kind the gate kind
     * @param reason why it is red
     * @param issue the issue that turns it green
     */
    record KindEntry(String kind, String reason, String issue) {}

    /**
     * A modelled gate kind the pinned pack has no file of, so the gate cannot prove it; listed so that an empty modelled
     * kind is never passed in silence. The entry goes stale the moment the pack gains a file of the kind.
     *
     * @param kind the modelled gate kind
     * @param reason why the pack has none, and where the kind is proved instead
     * @param issue the issue that owns it
     */
    record EmptyKindEntry(String kind, String reason, String issue) {}

    /**
     * An expected-red file of a modelled kind.
     *
     * @param path the pack-relative path
     * @param at where its first difference is expected (the path before the first {@code ": "} of the red detail, or
     *     {@code refused}); a red file whose first difference moves elsewhere is a new loss, not this one
     * @param reason why it is red
     * @param issue the issue that owns the fix
     */
    record FileEntry(String path, String at, String reason, String issue) {}

    /**
     * A file of a modelled kind that fails because the pack's own data is inconsistent, while the fix is pending in
     * spec-kitty-open-packs (ADR-0036 decision 5). It expires itself: once the file round-trips, the gate fails.
     *
     * @param path the pack-relative path
     * @param at where the file differs (as {@link #atOf}), or the whole red detail for a refused file
     * @param reason why it fails
     * @param issue the open-packs issue, {@code spec-kitty/spec-kitty-open-packs#<number>}
     */
    record KnownFailure(String path, String at, String reason, String issue) {}

    /**
     * A class of files the gate does not compare.
     *
     * @param match a pack-relative glob ({@code *} within a path segment, {@code **} across segments)
     * @param reason why
     */
    record Exclusion(String match, String reason) {}

    /**
     * One file's verdict.
     *
     * @param path the pack-relative path
     * @param kind the gate kind
     * @param status the status
     * @param detail the first difference, refusal or reason; empty for a green file
     */
    record FileResult(String path, String kind, Status status, String detail) {}

    /**
     * A judged gate run.
     *
     * @param files every census file, once, sorted by path
     * @param problems every reason the run fails; empty when it passes
     * @param emptyKinds the listed modelled kinds the pack has no file of, which the run cannot prove
     */
    record Evaluation(List<FileResult> files, List<String> problems, List<EmptyKindEntry> emptyKinds) {

        /**
         * The count of each status per kind.
         *
         * @return kind to status to count, both sorted
         */
        SortedMap<String, SortedMap<Status, Integer>> byKind() {
            SortedMap<String, SortedMap<Status, Integer>> byKind = new TreeMap<>();
            for (FileResult file : files) {
                byKind.computeIfAbsent(file.kind(), kind -> new TreeMap<>()).merge(file.status(), 1, Integer::sum);
            }
            return byKind;
        }
    }

    private final List<KindEntry> kinds;
    private final List<FileEntry> files;
    private final List<EmptyKindEntry> emptyKinds;
    private final List<Exclusion> exclusions;
    private final List<KnownFailure> knownFailures;

    private WholePackExpectation(
            List<KindEntry> kinds,
            List<FileEntry> files,
            List<EmptyKindEntry> emptyKinds,
            List<Exclusion> exclusions,
            List<KnownFailure> knownFailures) {
        this.kinds = List.copyOf(kinds);
        this.files = List.copyOf(files);
        this.emptyKinds = List.copyOf(emptyKinds);
        this.exclusions = List.copyOf(exclusions);
        this.knownFailures = List.copyOf(knownFailures);
    }

    /**
     * The checked-in expectation, from the test classpath.
     *
     * @return the expectation
     */
    static WholePackExpectation fromClasspath() {
        return load(resource(EXPECTED_RED_RESOURCE), resource(EXCLUSIONS_RESOURCE), resource(KNOWN_FAILURES_RESOURCE));
    }

    /**
     * Reads an expectation from the texts of the two lists.
     *
     * @param expectedRedYaml the text of {@code expected-red.yaml}
     * @param exclusionsYaml the text of {@code exclusions.yaml}
     * @return the expectation
     * @throws IllegalArgumentException when an entry lacks a reason or issue, carries an unknown key, or repeats
     */
    static WholePackExpectation load(String expectedRedYaml, String exclusionsYaml) {
        return load(expectedRedYaml, exclusionsYaml, "known_failures: []\n");
    }

    /**
     * Reads an expectation from the texts of the three lists.
     *
     * @param expectedRedYaml the text of {@code expected-red.yaml}
     * @param exclusionsYaml the text of {@code exclusions.yaml}
     * @param knownFailuresYaml the text of {@code known-failures.yaml}
     * @return the expectation
     * @throws IllegalArgumentException when an entry lacks a field, carries an unknown key, or repeats, or when a known
     *     failure does not name a {@code spec-kitty/spec-kitty-open-packs#<number>} issue
     */
    static WholePackExpectation load(String expectedRedYaml, String exclusionsYaml, String knownFailuresYaml) {
        Map<String, Object> red =
                document(expectedRedYaml, EXPECTED_RED_RESOURCE, Set.of("kinds", "files", "empty_kinds"));
        Map<String, Object> excluded = document(exclusionsYaml, EXCLUSIONS_RESOURCE, Set.of("exclusions"));
        List<KindEntry> kinds = new ArrayList<>();
        for (Map<String, String> entry : entries(red.get("kinds"), "kinds", List.of("kind", "reason", "issue"))) {
            kinds.add(new KindEntry(entry.get("kind"), entry.get("reason"), entry.get("issue")));
        }
        List<FileEntry> files = new ArrayList<>();
        for (Map<String, String> entry : entries(red.get("files"), "files", List.of("path", "at", "reason", "issue"))) {
            files.add(new FileEntry(entry.get("path"), entry.get("at"), entry.get("reason"), entry.get("issue")));
        }
        List<EmptyKindEntry> emptyKinds = new ArrayList<>();
        for (Map<String, String> entry :
                entries(red.get("empty_kinds"), "empty_kinds", List.of("kind", "reason", "issue"))) {
            emptyKinds.add(new EmptyKindEntry(entry.get("kind"), entry.get("reason"), entry.get("issue")));
        }
        List<Exclusion> exclusions = new ArrayList<>();
        for (Map<String, String> entry :
                entries(excluded.get("exclusions"), "exclusions", List.of("match", "reason"))) {
            exclusions.add(new Exclusion(entry.get("match"), entry.get("reason")));
        }
        Map<String, Object> known = document(knownFailuresYaml, KNOWN_FAILURES_RESOURCE, Set.of("known_failures"));
        List<KnownFailure> knownFailures = new ArrayList<>();
        for (Map<String, String> entry :
                entries(known.get("known_failures"), "known_failures", List.of("path", "at", "reason", "issue"))) {
            String path = entry.get("path");
            String issue = entry.get("issue");
            if (!OPEN_PACKS_ISSUE.matcher(issue).matches()) {
                throw new IllegalArgumentException("known_failures entry " + path + " has issue '" + issue
                        + "'; expected spec-kitty/spec-kitty-open-packs#<number> (ADR-0036 decision 5)");
            }
            knownFailures.add(new KnownFailure(path, entry.get("at"), entry.get("reason"), issue));
        }
        return new WholePackExpectation(kinds, files, emptyKinds, exclusions, knownFailures);
    }

    /**
     * The known failures, in list order: the single list the gate and every other ratchet over the gate's pack
     * (built-in) read, never a copy. A refused file's entry pins the whole detail, {@code refused: <reason>}.
     *
     * @return the known failures
     */
    List<KnownFailure> knownFailures() {
        return knownFailures;
    }

    /**
     * Judges one gate run.
     *
     * @param census every file of the pack with its gate kind
     * @param modelledKinds the gate kinds the round trip models
     * @param observed for each file of a modelled kind, empty when it round-tripped, else the red detail, which starts
     *     with where the file first differs followed by {@code ": "} (for example {@code steps[2].title: …} or
     *     {@code refused: …})
     * @return every file's verdict, and every problem
     */
    Evaluation evaluate(
            SortedMap<String, String> census, Set<String> modelledKinds, Map<String, Optional<String>> observed) {
        Map<String, FileEntry> fileEntries = new LinkedHashMap<>();
        files.forEach(entry -> fileEntries.put(entry.path(), entry));
        Map<String, KindEntry> kindEntries = new LinkedHashMap<>();
        kinds.forEach(entry -> kindEntries.put(entry.kind(), entry));
        Map<String, KnownFailure> knownEntries = new LinkedHashMap<>();
        knownFailures.forEach(entry -> knownEntries.put(entry.path(), entry));
        Set<String> ambiguous = new HashSet<>(fileEntries.keySet());
        ambiguous.retainAll(knownEntries.keySet());
        List<Pattern> patterns = exclusions.stream().map(e -> glob(e.match())).toList();

        List<FileResult> results = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<Integer> usedExclusions = new HashSet<>();
        Set<String> redKinds = new HashSet<>();
        Set<String> excludedPaths = new HashSet<>();
        census.forEach((path, kind) -> {
            boolean modelled = modelledKinds.contains(kind);
            Optional<String> red = modelled
                    ? observed.getOrDefault(path, Optional.of("the round trip gave no outcome for this file"))
                    : Optional.of("no adapter models kind " + kind);
            int exclusion = firstMatch(patterns, path);
            if (exclusion >= 0) {
                usedExclusions.add(exclusion);
                excludedPaths.add(path);
                if (modelled) {
                    problems.add("EXCLUDED_MODELLED " + path + " (" + kind + "): a modelled file must not be excluded");
                }
                results.add(new FileResult(
                        path, kind, Status.EXCLUDED, exclusions.get(exclusion).reason()));
                return;
            }
            if (red.isEmpty()) {
                results.add(new FileResult(path, kind, Status.GREEN, ""));
                if (fileEntries.containsKey(path)) {
                    problems.add("UNEXPECTEDLY_GREEN " + path + ": it round-trips now; remove it from "
                            + EXPECTED_RED_RESOURCE);
                }
                if (knownEntries.containsKey(path) && !ambiguous.contains(path)) {
                    problems.add("UNEXPECTEDLY_GREEN " + path + ": it round-trips now; remove it from "
                            + KNOWN_FAILURES_RESOURCE);
                }
                return;
            }
            redKinds.add(kind);
            if (ambiguous.contains(path)) {
                // Judged by neither list, so it cannot pass by accident; the whole-list check names the conflict.
                results.add(new FileResult(path, kind, Status.RED, red.get()));
                return;
            }
            @Nullable KnownFailure knownEntry = modelled ? knownEntries.get(path) : null;
            if (knownEntry != null) {
                results.add(knownFailure(path, kind, red.get(), knownEntry, problems));
                return;
            }
            @Nullable FileEntry fileEntry = fileEntries.get(path);
            // A kind entry only ever covers a kind no adapter models; for a modelled kind each file is listed alone.
            @Nullable KindEntry kindEntry = modelled ? null : kindEntries.get(kind);
            if (fileEntry != null && !atOf(red.get()).equals(fileEntry.at())) {
                results.add(new FileResult(path, kind, Status.RED, red.get()));
                problems.add("CHANGED_RED " + path + " (" + kind + "): expected the first difference at '"
                        + fileEntry.at() + "', now: " + red.get());
            } else if (fileEntry != null) {
                results.add(new FileResult(
                        path, kind, Status.EXPECTED_RED, expected(red.get(), fileEntry.reason(), fileEntry.issue())));
            } else if (kindEntry != null) {
                results.add(new FileResult(
                        path, kind, Status.EXPECTED_RED, expected(red.get(), kindEntry.reason(), kindEntry.issue())));
            } else {
                results.add(new FileResult(path, kind, Status.RED, red.get()));
                problems.add("UNEXPECTED_RED " + path + " (" + kind + "): " + red.get());
            }
        });

        for (FileEntry entry : files) {
            if (!census.containsKey(entry.path())) {
                problems.add(
                        "STALE_BASELINE " + entry.path() + ": no such file; remove it from " + EXPECTED_RED_RESOURCE);
            } else if (excludedPaths.contains(entry.path())) {
                problems.add("STALE_BASELINE " + entry.path() + ": the file is excluded; remove it from "
                        + EXPECTED_RED_RESOURCE);
            }
        }
        for (KnownFailure entry : knownFailures) {
            @Nullable String kind = census.get(entry.path());
            if (kind == null) {
                problems.add("STALE_KNOWN_FAILURE " + entry.path() + ": no such file; remove it from "
                        + KNOWN_FAILURES_RESOURCE);
            } else if (excludedPaths.contains(entry.path())) {
                problems.add("STALE_KNOWN_FAILURE " + entry.path() + ": the file is excluded; remove it from "
                        + KNOWN_FAILURES_RESOURCE);
            } else if (!modelledKinds.contains(kind)) {
                problems.add("KNOWN_FAILURE_UNMODELLED " + entry.path() + " (" + kind
                        + "): no adapter models this kind, so the entry can never expire");
            }
            if (ambiguous.contains(entry.path())) {
                problems.add("AMBIGUOUS_ENTRY " + entry.path() + ": listed in both " + EXPECTED_RED_RESOURCE + " and "
                        + KNOWN_FAILURES_RESOURCE);
            }
        }
        for (KindEntry entry : kinds) {
            if (modelledKinds.contains(entry.kind())) {
                problems.add("BASELINED_MODELLED_KIND " + entry.kind()
                        + ": a modelled kind is never expected red as a whole; remove it from "
                        + EXPECTED_RED_RESOURCE + " and list any red file of it one by one");
            } else if (!census.containsValue(entry.kind())) {
                problems.add("STALE_BASELINE kind " + entry.kind() + ": no file has this kind; remove it from "
                        + EXPECTED_RED_RESOURCE);
            } else if (!redKinds.contains(entry.kind())) {
                problems.add("UNEXPECTEDLY_GREEN kind " + entry.kind() + ": no file of it is red now; remove it from "
                        + EXPECTED_RED_RESOURCE);
            }
        }
        for (int i = 0; i < exclusions.size(); i++) {
            if (!usedExclusions.contains(i)) {
                problems.add("STALE_EXCLUSION " + exclusions.get(i).match() + ": matches no file");
            }
        }
        Set<String> listedEmpty = new HashSet<>();
        for (EmptyKindEntry entry : emptyKinds) {
            listedEmpty.add(entry.kind());
            if (!modelledKinds.contains(entry.kind())) {
                problems.add("STALE_BASELINE empty kind " + entry.kind() + ": not a modelled kind; remove it from "
                        + EXPECTED_RED_RESOURCE);
            } else if (census.containsValue(entry.kind())) {
                problems.add("STALE_BASELINE empty kind " + entry.kind() + ": the pack has a file of it now; remove it"
                        + " from " + EXPECTED_RED_RESOURCE);
            }
        }
        for (String kind : new java.util.TreeSet<>(modelledKinds)) {
            if (!census.containsValue(kind) && !listedEmpty.contains(kind)) {
                problems.add("EMPTY_MODELLED_KIND " + kind + ": the pack has no file of this modelled kind");
            }
        }
        List<EmptyKindEntry> unproved = emptyKinds.stream()
                .filter(entry -> modelledKinds.contains(entry.kind()) && !census.containsValue(entry.kind()))
                .toList();
        return new Evaluation(List.copyOf(results), List.copyOf(problems), unproved);
    }

    /** Where a red detail says the file first differs: the text before its first {@code ": "}. */
    static String atOf(String red) {
        int colon = red.indexOf(": ");
        return colon < 0 ? red : red.substring(0, colon);
    }

    /**
     * Judges a red file of a modelled kind that the known-failure list names: a known failure when it still differs
     * where the entry pins it, else a changed red.
     */
    private static FileResult knownFailure(
            String path, String kind, String red, KnownFailure entry, List<String> problems) {
        if (matches(red, entry.at())) {
            return new FileResult(
                    path,
                    kind,
                    Status.KNOWN_FAILURE,
                    red + " — known failure: " + entry.reason() + " (" + entry.issue() + ")");
        }
        problems.add("CHANGED_RED " + path + " (" + kind + "): expected the first difference at '" + entry.at()
                + "', now: " + red);
        return new FileResult(path, kind, Status.RED, red);
    }

    /** Whether a red detail is the one a known failure pins: the same {@link #atOf}, or the whole refusal. */
    private static boolean matches(String red, String at) {
        return "refused".equals(atOf(red)) ? red.equals(at) : atOf(red).equals(at);
    }

    private static String expected(String red, String reason, String issue) {
        return red + " — expected: " + reason + " (" + issue + ")";
    }

    private static int firstMatch(List<Pattern> patterns, String path) {
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).matcher(path).matches()) {
                return i;
            }
        }
        return -1;
    }

    /** {@code **}/ matches zero or more whole segments, {@code **} anything, {@code *} within a segment. */
    static Pattern glob(String match) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < match.length()) {
            char c = match.charAt(i);
            if (match.startsWith("**/", i)) {
                regex.append("(?:.*/)?");
                i += 3;
            } else if (match.startsWith("**", i)) {
                regex.append(".*");
                i += 2;
            } else {
                regex.append(
                        switch (c) {
                            case '*' -> "[^/]*";
                            case '?' -> "[^/]";
                            default -> Pattern.quote(String.valueOf(c));
                        });
                i += 1;
            }
        }
        return Pattern.compile(regex.toString());
    }

    // ---- loading -------------------------------------------------------------------------------

    private static Map<String, Object> document(String text, String name, Set<String> keys) {
        Object loaded = Yaml.load(text);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(name + " is not a map");
        }
        Map<String, Object> document = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (!keys.contains(String.valueOf(key))) {
                throw new IllegalArgumentException(name + " has an unknown key '" + key + "'; allowed: " + keys);
            }
            if (value != null) {
                document.put(String.valueOf(key), value);
            }
        });
        return document;
    }

    private static List<Map<String, String>> entries(@Nullable Object list, String where, List<String> fields) {
        if (list == null) {
            return List.of();
        }
        if (!(list instanceof List<?> items)) {
            throw new IllegalArgumentException(where + " is not a list");
        }
        List<Map<String, String>> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(where + " entry is not a map: " + item);
            }
            Map<String, String> entry = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (!fields.contains(String.valueOf(key))) {
                    throw new IllegalArgumentException(
                            where + " entry " + map + " has an unknown key '" + key + "'; allowed: " + fields);
                }
                if (value instanceof Map<?, ?> || value instanceof List<?>) {
                    throw new IllegalArgumentException(
                            where + " entry " + map + " has a non-text value for '" + key + "'");
                }
                entry.put(String.valueOf(key), String.valueOf(value));
            });
            for (String field : fields) {
                String value = entry.get(field);
                if (value == null || value.isBlank() || "null".equals(value)) {
                    throw new IllegalArgumentException(where + " entry " + map + " has no " + field);
                }
            }
            if (!seen.add(entry.get(fields.get(0)))) {
                throw new IllegalArgumentException(where + " lists '" + entry.get(fields.get(0)) + "' twice");
            }
            entries.add(entry);
        }
        return entries;
    }

    private static String resource(String name) {
        try (InputStream in = WholePackExpectation.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("The test resource " + name + " is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
