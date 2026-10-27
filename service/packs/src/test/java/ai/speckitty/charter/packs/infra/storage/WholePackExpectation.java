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
 * <p>Two checked-in lists drive it. {@code whole-pack/expected-red.yaml} names what is known not to round-trip yet: a
 * whole gate kind with no adapter ({@code kinds}), or a single file of a modelled kind ({@code files}), each with a
 * reason and the issue that owns it; it also names a modelled kind the pack has no file of ({@code empty_kinds}), which
 * the gate cannot prove and must not pass in silence. {@code whole-pack/exclusions.yaml} is the gate's own append-only list of files it
 * does not compare, each with a reason.
 *
 * <p>The gate passes only when the observed reds are exactly the expected ones: an unlisted red fails, and so does a
 * listed item that now round-trips, so a fix is recorded the moment it lands. Stale entries, an exclusion over a
 * modelled file and a modelled kind with no files fail too, so the gate cannot pass vacuously.
 */
final class WholePackExpectation {

    static final String EXPECTED_RED_RESOURCE = "whole-pack/expected-red.yaml";
    static final String EXCLUSIONS_RESOURCE = "whole-pack/exclusions.yaml";

    /** The status of one file in a gate run. */
    enum Status {
        /** Written, read back, and its meaning is unchanged. */
        GREEN,
        /** Expected to differ, and does. */
        EXPECTED_RED,
        /** Not compared, for a listed reason. */
        EXCLUDED,
        /** Differs, and nothing expects it to: a problem. */
        RED
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

    private WholePackExpectation(
            List<KindEntry> kinds, List<FileEntry> files, List<EmptyKindEntry> emptyKinds, List<Exclusion> exclusions) {
        this.kinds = List.copyOf(kinds);
        this.files = List.copyOf(files);
        this.emptyKinds = List.copyOf(emptyKinds);
        this.exclusions = List.copyOf(exclusions);
    }

    /**
     * The checked-in expectation, from the test classpath.
     *
     * @return the expectation
     */
    static WholePackExpectation fromClasspath() {
        return load(resource(EXPECTED_RED_RESOURCE), resource(EXCLUSIONS_RESOURCE));
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
        return new WholePackExpectation(kinds, files, emptyKinds, exclusions);
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
                return;
            }
            redKinds.add(kind);
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
