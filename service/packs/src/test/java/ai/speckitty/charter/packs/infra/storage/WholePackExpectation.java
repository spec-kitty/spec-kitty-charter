package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What the whole-pack gate expects to see, and the two-way ratchet that holds the observed result to it.
 *
 * <p>Three checked-in lists drive it. {@code whole-pack/expected-red.yaml} names what is known not to round-trip yet: a
 * {@code file-kinds.yaml} schema with no adapter ({@code kinds}), or a single file of a modelled kind ({@code files}),
 * each with a reason and the issue that owns it. {@code whole-pack/exclusions.yaml} is the gate's own append-only list
 * of files it does not compare, each with a reason. {@code whole-pack/known-failures.yaml} names files of a modelled
 * kind that fail because the pack's own data is inconsistent, each pinned to where it differs and to the
 * spec-kitty-open-packs issue that owns the fix (ADR-0036 decision 5).
 *
 * <p>The gate runs over every charter example pack (ADR-0070, #547), so a file entry, a known failure and an exclusion
 * name their pack as their first path segment ({@code org-base/drg/fragment.yaml}); {@link #forPack} scopes the lists
 * to one pack, whose run is judged by {@link #evaluate}. What only all the packs together can prove is checked apart:
 * every modelled kind has a file in some example pack ({@link #emptyKindProblems}), every entry names an example pack
 * ({@link #unscopedEntries}), and every {@code file-kinds.yaml} schema is modelled or listed in {@code kinds} with a
 * reason ({@link #schemaProblems}).
 *
 * <p>A file the table excludes (census kind {@code excluded:<glob>}) is judged apart, and is never red for being
 * unmodelled: the run checks that the store did not write it ({@link Status#EXCLUDED}), and the file needs no entry in
 * any of the lists above (#451).
 *
 * <p>The gate passes only when the observed reds are exactly the expected ones: an unlisted red fails, and so does a
 * listed item that now round-trips, so a fix is recorded the moment it lands. Stale entries, an exclusion over a
 * modelled file and an empty pack fail too, so the gate cannot pass vacuously. With no lists at all
 * ({@link #none}) every red is a problem: the open-packs drift test judges each pack that way, so a file of a kind the
 * round trip does not model fails it by kind and path.
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
     * A {@code file-kinds.yaml} schema no adapter models: a file classed under it is expected red. It goes stale when the
     * round trip models the schema or the table drops it ({@link #schemaProblems}).
     *
     * @param kind the schema name, as the census names a file of it
     * @param reason why the round trip does not model it
     * @param issue the issue that owns it
     */
    record KindEntry(String kind, String reason, String issue) {}

    /**
     * An expected-red file of a modelled kind.
     *
     * @param path the path from the example packs' directory, the pack's name first ({@code org-base/drg/fragment.yaml});
     *     pack-relative once {@link #forPack} has scoped the list
     * @param at where its first difference is expected (as {@link #atOf}), or the whole red detail for a refused file;
     *     a red file whose first difference moves elsewhere, or that is refused for another reason, is a new loss
     * @param reason why it is red
     * @param issue the issue that owns the fix
     */
    record FileEntry(String path, String at, String reason, String issue) {}

    /**
     * A file of a modelled kind that fails because the pack's own data is inconsistent, while the fix is pending in
     * spec-kitty-open-packs (ADR-0036 decision 5). It expires itself: once the file round-trips, the gate fails.
     *
     * @param path the path from the example packs' directory, the pack's name first; pack-relative once
     *     {@link #forPack} has scoped the list
     * @param at where the file differs (as {@link #atOf}), or the whole red detail for a refused file
     * @param reason why it fails
     * @param issue the open-packs issue, {@code spec-kitty/spec-kitty-open-packs#<number>}
     * @param roundTripOnly whether only the gate's round trip reports the failure while the store load accepts the
     *     file, so load-refusal ratchets skip the entry and the gate still pins it exactly
     */
    record KnownFailure(String path, String at, String reason, String issue, boolean roundTripOnly) {

        /**
         * The convenience form of an entry the load itself refuses, so {@code roundTripOnly} is false.
         *
         * @param path the pack-relative path
         * @param at where the file differs
         * @param reason why it fails
         * @param issue the open-packs issue
         */
        KnownFailure(String path, String at, String reason, String issue) {
            this(path, at, reason, issue, false);
        }
    }

    /**
     * A class of files the gate does not compare.
     *
     * @param match a glob from the example packs' directory, the pack's name first ({@code *} within a path segment,
     *     {@code **} across segments); pack-relative once {@link #forPack} has scoped the list
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
     */
    record Evaluation(List<FileResult> files, List<String> problems) {

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
    private final List<Exclusion> exclusions;
    private final List<KnownFailure> knownFailures;

    private WholePackExpectation(
            List<KindEntry> kinds,
            List<FileEntry> files,
            List<Exclusion> exclusions,
            List<KnownFailure> knownFailures) {
        this.kinds = List.copyOf(kinds);
        this.files = List.copyOf(files);
        this.exclusions = List.copyOf(exclusions);
        this.knownFailures = List.copyOf(knownFailures);
    }

    /**
     * No expectation at all: every red is a problem. The open-packs drift test judges each pack with it, so a file of a
     * kind the round trip does not model, or a file the round trip gave no outcome for, fails by kind and path (#547).
     *
     * @return the empty expectation
     */
    static WholePackExpectation none() {
        return new WholePackExpectation(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * The lists scoped to one example pack: the file entries, known failures and exclusions under {@code pack/},
     * pack-relative, and every kind entry, which names a schema, not a file.
     *
     * @param pack the example pack's name
     * @return the scoped expectation, judged by {@link #evaluate} against that pack's census
     */
    WholePackExpectation forPack(String pack) {
        String prefix = pack + "/";
        return new WholePackExpectation(
                kinds,
                files.stream()
                        .filter(entry -> entry.path().startsWith(prefix))
                        .map(entry -> new FileEntry(
                                entry.path().substring(prefix.length()), entry.at(), entry.reason(), entry.issue()))
                        .toList(),
                exclusions.stream()
                        .filter(entry -> entry.match().startsWith(prefix))
                        .map(entry -> new Exclusion(entry.match().substring(prefix.length()), entry.reason()))
                        .toList(),
                knownFailures.stream()
                        .filter(entry -> entry.path().startsWith(prefix))
                        .map(entry -> new KnownFailure(
                                entry.path().substring(prefix.length()),
                                entry.at(),
                                entry.reason(),
                                entry.issue(),
                                entry.roundTripOnly()))
                        .toList());
    }

    /**
     * The entries no {@link #forPack} scope reaches: a file entry, known failure or exclusion whose first path segment
     * is not one of {@code packs}, so no pack's run would ever judge it.
     *
     * @param packs the example packs' names
     * @return one problem per such entry, in list order
     */
    List<String> unscopedEntries(Collection<String> packs) {
        List<String> problems = new ArrayList<>();
        files.stream()
                .map(FileEntry::path)
                .filter(path -> !underOne(path, packs))
                .forEach(path -> problems.add("STALE_BASELINE " + path + ": names no example pack " + packs
                        + "; fix it in " + EXPECTED_RED_RESOURCE));
        knownFailures.stream()
                .map(KnownFailure::path)
                .filter(path -> !underOne(path, packs))
                .forEach(path -> problems.add("STALE_KNOWN_FAILURE " + path + ": names no example pack " + packs
                        + "; fix it in " + KNOWN_FAILURES_RESOURCE));
        exclusions.stream()
                .map(Exclusion::match)
                .filter(match -> !underOne(match, packs))
                .forEach(match -> problems.add("STALE_EXCLUSION " + match + ": names no example pack " + packs
                        + "; fix it in " + EXCLUSIONS_RESOURCE));
        return problems;
    }

    private static boolean underOne(String path, Collection<String> packs) {
        return packs.stream().anyMatch(pack -> path.startsWith(pack + "/"));
    }

    /**
     * Ties the gate's kinds to the file-kind table (#547): every {@code file-kinds.yaml} schema is modelled by the round
     * trip or listed in {@code kinds} with a reason, and every {@code kinds} entry is a schema of the table that the round
     * trip does not model. A schema the round trip silently skips, as {@code org-fragment} was before #509, fails here
     * whether or not an example pack holds a file of it.
     *
     * @param schemas the schema names of the table's {@code files} rules
     * @param modelledKinds the gate kinds the round trip models
     * @return one problem per schema or entry out of line, schemas first, each list sorted
     */
    List<String> schemaProblems(Collection<String> schemas, Set<String> modelledKinds) {
        Set<String> listed = new TreeSet<>();
        kinds.forEach(entry -> listed.add(entry.kind()));
        List<String> problems = new ArrayList<>();
        new TreeSet<>(schemas)
                .stream()
                        .filter(schema -> !modelledKinds.contains(schema) && !listed.contains(schema))
                        .forEach(schema -> problems.add("UNMODELLED_SCHEMA " + schema
                                + ": the round trip does not model this file-kinds.yaml schema, and "
                                + EXPECTED_RED_RESOURCE
                                + " does not list it in kinds with a reason"));
        listed.stream()
                .filter(kind -> !schemas.contains(kind))
                .forEach(kind -> problems.add("STALE_BASELINE kind " + kind
                        + ": not a file-kinds.yaml schema; remove it from " + EXPECTED_RED_RESOURCE));
        return problems;
    }

    /**
     * Every modelled kind has a file in at least one example pack, so the gate proves it somewhere (ADR-0070: the example
     * packs carry every kind between them, each at the level it belongs to).
     *
     * @param censusByPack each example pack's census
     * @param modelledKinds the gate kinds the round trip models
     * @return one problem per modelled kind no pack has a file of, sorted
     */
    static List<String> emptyKindProblems(
            Map<String, SortedMap<String, String>> censusByPack, Set<String> modelledKinds) {
        Set<String> held = new HashSet<>();
        censusByPack.values().forEach(census -> held.addAll(census.values()));
        return new TreeSet<>(modelledKinds)
                .stream()
                        .filter(kind -> !held.contains(kind))
                        .map(kind ->
                                "EMPTY_MODELLED_KIND " + kind + ": no example pack has a file of this modelled kind")
                        .toList();
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
        Map<String, Object> red = document(expectedRedYaml, EXPECTED_RED_RESOURCE, Set.of("kinds", "files"));
        Map<String, Object> excluded = document(exclusionsYaml, EXCLUSIONS_RESOURCE, Set.of("exclusions"));
        List<KindEntry> kinds = new ArrayList<>();
        for (Map<String, String> entry : entries(red.get("kinds"), "kinds", List.of("kind", "reason", "issue"))) {
            kinds.add(new KindEntry(entry.get("kind"), entry.get("reason"), entry.get("issue")));
        }
        List<FileEntry> files = new ArrayList<>();
        for (Map<String, String> entry : entries(red.get("files"), "files", List.of("path", "at", "reason", "issue"))) {
            files.add(new FileEntry(entry.get("path"), entry.get("at"), entry.get("reason"), entry.get("issue")));
        }
        List<Exclusion> exclusions = new ArrayList<>();
        for (Map<String, String> entry :
                entries(excluded.get("exclusions"), "exclusions", List.of("match", "reason"))) {
            exclusions.add(new Exclusion(entry.get("match"), entry.get("reason")));
        }
        Map<String, Object> known = document(knownFailuresYaml, KNOWN_FAILURES_RESOURCE, Set.of("known_failures"));
        List<KnownFailure> knownFailures = new ArrayList<>();
        for (Map<String, String> entry : entries(
                known.get("known_failures"),
                "known_failures",
                List.of("path", "at", "reason", "issue"),
                Set.of("round_trip_only"))) {
            String path = entry.get("path");
            String issue = entry.get("issue");
            if (!OPEN_PACKS_ISSUE.matcher(issue).matches()) {
                throw new IllegalArgumentException("known_failures entry " + path + " has issue '" + issue
                        + "'; expected spec-kitty/spec-kitty-open-packs#<number> (ADR-0036 decision 5)");
            }
            String flag = entry.get("round_trip_only");
            if (flag != null && !"true".equals(flag) && !"false".equals(flag)) {
                throw new IllegalArgumentException(
                        "known_failures entry " + path + " has round_trip_only '" + flag + "'; expected true or false");
            }
            knownFailures.add(new KnownFailure(path, entry.get("at"), entry.get("reason"), issue, "true".equals(flag)));
        }
        return new WholePackExpectation(kinds, files, exclusions, knownFailures);
    }

    /**
     * The known failures, in list order: the single list the gate and every other ratchet over the example packs read,
     * never a copy; scope it with {@link #forPack} first. A refused file's entry pins the whole detail,
     * {@code refused: <reason>}.
     *
     * @return the known failures
     */
    List<KnownFailure> knownFailures() {
        return knownFailures;
    }

    /**
     * Judges one pack's gate run against lists scoped to it ({@link #forPack}, or {@link #none}).
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
        Judgement judgement = new Judgement(census, modelledKinds, observed);
        census.forEach(judgement::judge);
        judgement.checkFileEntries();
        judgement.checkKnownFailures();
        judgement.checkKindEntries();
        judgement.checkExclusions();
        judgement.checkNotEmpty();
        return judgement.evaluation();
    }

    /**
     * One gate run being judged: the expectation's lists indexed, and the verdicts and problems so far. Each file is
     * judged in census order, then each list is checked against the whole census, so the problems keep that order.
     */
    private final class Judgement {
        private final SortedMap<String, String> census;
        private final Set<String> modelledKinds;
        private final Map<String, Optional<String>> observed;
        private final Map<String, FileEntry> fileEntries = new LinkedHashMap<>();
        private final Map<String, KindEntry> kindEntries = new LinkedHashMap<>();
        private final Map<String, KnownFailure> knownEntries = new LinkedHashMap<>();
        private final Set<String> ambiguous;
        private final List<Pattern> patterns;
        private final List<FileResult> results = new ArrayList<>();
        private final List<String> problems = new ArrayList<>();
        private final Set<Integer> usedExclusions = new HashSet<>();
        private final Set<String> excludedPaths = new HashSet<>();

        Judgement(SortedMap<String, String> census, Set<String> modelledKinds, Map<String, Optional<String>> observed) {
            this.census = census;
            this.modelledKinds = modelledKinds;
            this.observed = observed;
            files.forEach(entry -> fileEntries.put(entry.path(), entry));
            kinds.forEach(entry -> kindEntries.put(entry.kind(), entry));
            knownFailures.forEach(entry -> knownEntries.put(entry.path(), entry));
            ambiguous = new HashSet<>(fileEntries.keySet());
            ambiguous.retainAll(knownEntries.keySet());
            patterns = exclusions.stream().map(e -> glob(e.match())).toList();
        }

        /** Judges one file of the census. */
        void judge(String path, String kind) {
            if (kind.startsWith(WholePackCensus.EXCLUDED)) {
                judgeExcludedFile(path, kind);
                return;
            }
            boolean modelled = modelledKinds.contains(kind);
            Optional<String> red = modelled
                    ? observed.getOrDefault(path, Optional.of("the round trip gave no outcome for this file"))
                    : Optional.of("no adapter models kind " + kind);
            int exclusion = firstMatch(patterns, path);
            if (exclusion >= 0) {
                judgeExclusion(path, kind, modelled, exclusion);
                return;
            }
            if (red.isEmpty()) {
                judgeGreen(path, kind);
                return;
            }
            judgeRed(path, kind, modelled, red.get());
        }

        /** An excluded file is not pack content: checked, not compared. The store must not have written it. */
        private void judgeExcludedFile(String path, String kind) {
            excludedPaths.add(path);
            Optional<String> written = observed.getOrDefault(
                    path, Optional.of("the gate did not check whether the store wrote this excluded file"));
            if (written.isEmpty()) {
                results.add(new FileResult(
                        path,
                        kind,
                        Status.EXCLUDED,
                        "excluded by " + kind.substring(WholePackCensus.EXCLUDED.length()) + ": not written"));
            } else {
                results.add(new FileResult(path, kind, Status.RED, written.get()));
                problems.add("EXCLUDED_WRITTEN " + path + " (" + kind + "): " + written.get());
            }
        }

        private void judgeExclusion(String path, String kind, boolean modelled, int exclusion) {
            usedExclusions.add(exclusion);
            excludedPaths.add(path);
            if (modelled) {
                problems.add("EXCLUDED_MODELLED " + path + " (" + kind + "): a modelled file must not be excluded");
            }
            results.add(new FileResult(
                    path, kind, Status.EXCLUDED, exclusions.get(exclusion).reason()));
        }

        private void judgeGreen(String path, String kind) {
            results.add(new FileResult(path, kind, Status.GREEN, ""));
            if (fileEntries.containsKey(path)) {
                problems.add(
                        "UNEXPECTEDLY_GREEN " + path + ": it round-trips now; remove it from " + EXPECTED_RED_RESOURCE);
            }
            if (knownEntries.containsKey(path) && !ambiguous.contains(path)) {
                problems.add("UNEXPECTEDLY_GREEN " + path + ": it round-trips now; remove it from "
                        + KNOWN_FAILURES_RESOURCE);
            }
        }

        private void judgeRed(String path, String kind, boolean modelled, String red) {
            if (ambiguous.contains(path)) {
                // Judged by neither list, so it cannot pass by accident; the whole-list check names the conflict.
                results.add(new FileResult(path, kind, Status.RED, red));
                return;
            }
            @Nullable KnownFailure knownEntry = modelled ? knownEntries.get(path) : null;
            if (knownEntry != null) {
                results.add(knownFailure(path, kind, red, knownEntry, problems));
                return;
            }
            @Nullable FileEntry fileEntry = fileEntries.get(path);
            // A kind entry only ever covers a kind no adapter models; for a modelled kind each file is listed alone.
            @Nullable KindEntry kindEntry = modelled ? null : kindEntries.get(kind);
            results.add(expectedRed(path, kind, red, fileEntry, kindEntry));
        }

        /** Judges a red file against the expected-red list: by its file entry, else its kind entry. */
        private FileResult expectedRed(
                String path, String kind, String red, @Nullable FileEntry fileEntry, @Nullable KindEntry kindEntry) {
            if (fileEntry != null && !matches(red, fileEntry.at())) {
                problems.add("CHANGED_RED " + path + " (" + kind + "): expected the first difference at '"
                        + fileEntry.at() + "', now: " + red);
                return new FileResult(path, kind, Status.RED, red);
            } else if (fileEntry != null) {
                return new FileResult(
                        path, kind, Status.EXPECTED_RED, expected(red, fileEntry.reason(), fileEntry.issue()));
            } else if (kindEntry != null) {
                return new FileResult(
                        path, kind, Status.EXPECTED_RED, expected(red, kindEntry.reason(), kindEntry.issue()));
            }
            problems.add("UNEXPECTED_RED " + path + " (" + kind + "): " + red);
            return new FileResult(path, kind, Status.RED, red);
        }

        void checkFileEntries() {
            for (FileEntry entry : files) {
                if (!census.containsKey(entry.path())) {
                    problems.add("STALE_BASELINE " + entry.path() + ": no such file; remove it from "
                            + EXPECTED_RED_RESOURCE);
                } else if (excludedPaths.contains(entry.path())) {
                    problems.add("STALE_BASELINE " + entry.path() + ": the file is excluded; remove it from "
                            + EXPECTED_RED_RESOURCE);
                }
            }
        }

        void checkKnownFailures() {
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
                    problems.add("AMBIGUOUS_ENTRY " + entry.path() + ": listed in both " + EXPECTED_RED_RESOURCE
                            + " and " + KNOWN_FAILURES_RESOURCE);
                }
            }
        }

        /**
         * A kind entry names a schema, not this pack's files, so its staleness against the table is
         * {@link #schemaProblems}'s; here only a modelled kind listed as a whole is a problem.
         */
        void checkKindEntries() {
            for (KindEntry entry : kinds) {
                if (modelledKinds.contains(entry.kind())) {
                    problems.add("BASELINED_MODELLED_KIND " + entry.kind()
                            + ": a modelled kind is never expected red as a whole; remove it from "
                            + EXPECTED_RED_RESOURCE + " and list any red file of it one by one");
                }
            }
        }

        void checkExclusions() {
            for (int i = 0; i < exclusions.size(); i++) {
                if (!usedExclusions.contains(i)) {
                    problems.add("STALE_EXCLUSION " + exclusions.get(i).match() + ": matches no file");
                }
            }
        }

        /** A run over no file proves nothing, so it cannot pass. */
        void checkNotEmpty() {
            if (census.isEmpty()) {
                problems.add("EMPTY_PACK: the census has no file, so the run proves nothing");
            }
        }

        Evaluation evaluation() {
            return new Evaluation(List.copyOf(results), List.copyOf(problems));
        }
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

    /** Whether a red detail is the one an entry pins: the same {@link #atOf}, or the whole refusal. */
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
        return entries(list, where, fields, Set.of());
    }

    /**
     * As {@link #entries(Object, String, List)}, with keys an entry may carry but need not: a key that is neither
     * required nor optional is refused as unknown.
     */
    private static List<Map<String, String>> entries(
            @Nullable Object list, String where, List<String> fields, Set<String> optional) {
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
                if (!fields.contains(String.valueOf(key)) && !optional.contains(String.valueOf(key))) {
                    throw new IllegalArgumentException(where + " entry " + map + " has an unknown key '" + key
                            + "'; allowed: " + fields + (optional.isEmpty() ? "" : " or " + optional));
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
