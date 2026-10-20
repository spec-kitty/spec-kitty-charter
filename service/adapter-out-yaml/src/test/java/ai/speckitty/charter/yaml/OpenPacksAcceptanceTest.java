package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.PackContents.ClassifiedFile;
import ai.speckitty.charter.domain.PackFinding;
import ai.speckitty.charter.domain.PackFindingCode;
import ai.speckitty.charter.domain.PackRoot;
import ai.speckitty.charter.domain.PackValidation;
import ai.speckitty.charter.domain.PackVerdict;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The acceptance of #114 against the pinned, clean open-packs checkout, without a vacuous pass (FR-011, SC-001,
 * NFR-002): the checkout is the pinned commit, every YAML file is mapped to exactly one schema or excluded for a
 * reviewed reason, the counts are exact, and every mapped file validates. Two probes show that the classification
 * check bites. Never skipped: a missing, moved or dirty checkout fails.
 */
@Tag("open-packs")
class OpenPacksAcceptanceTest {

    /** The mapped files per schema at the pin (re-counted at 01edb4e); the sum is 409. */
    static final Map<String, Integer> MAPPED = orderedCounts(
            "pack-descriptor", 2,
            "graph-shard", 14,
            "org-fragment", 4,
            "org-charter", 4,
            "directive", 55,
            "tactic", 137,
            "styleguide", 34,
            "toolguide", 24,
            "paradigm", 15,
            "procedure", 41,
            "agent-profile", 31,
            "mission-step-contract", 28,
            "asset", 20);

    /** The excluded files per reviewed exclusion class (its glob) at the pin; the sum is 107. */
    static final Map<String, Integer> EXCLUDED = orderedCounts(
            "**/*.config.yaml", 1,
            "**/*.glossary-pack.yaml", 4,
            "**/*.skill.yaml", 5,
            "**/actions/*/index.yaml", 24,
            "**/governance-profile.yaml", 6,
            "**/mission-steps/**/step.yaml", 37,
            "**/mission_types/*.yaml", 6,
            "**/missions/*/expected-artifacts.yaml", 6,
            "**/missions/*/mission-runtime.yaml", 6,
            "**/missions/*/mission.yaml", 6,
            "**/presets/*.yaml", 3,
            "**/templates/plan-field-declaration.yaml", 1,
            "*/pack-manifest.yaml", 1,
            "built-in.source.yaml", 1);

    private static final int MAPPED_TOTAL = 409;
    private static final int EXCLUDED_TOTAL = 107;

    private static Map<String, Integer> orderedCounts(Object... pairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            counts.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return counts;
    }

    // ---- NFR-002: the budget and its record ------------------------------------------------------------------

    /** Validate mode over all mapped files, warm, must stay within this (NFR-002). */
    static final long VALIDATE_BUDGET_MS = 15_000;

    /** Loading the packs may take at most this much longer than before the mission (NFR-002). */
    static final long LOAD_HEADROOM_MS = 2_000;

    /**
     * The warm median of five loads of the five open-packs packs at the mission base {@code 61d0980}, taken against the
     * old-layout checkout at {@code 7f063ae} with a scratch harness (never committed): the median of the medians of
     * seven JVM runs, which were 57, 61, 66, 67, 71, 76 and 82 ms (the first load of each JVM took 204 to 265 ms).
     * Linux 6.18, Intel Xeon 2.10 GHz, 4 cores, OpenJDK 25.0.4.1. The guard is this plus {@link #LOAD_HEADROOM_MS}.
     *
     * <p>At this branch's head, against the pinned layout, the same harness measured a warm median of 88 ms (the medians
     * of seven JVM runs: 73, 81, 87, 88, 90, 93 and 114) and a first load of 1.0 to 1.2 s, which pays for compiling the
     * schemas once: 21 ms and about 0.85 s more than the baseline, both within the 2 s the NFR allows.
     */
    static final long LOAD_BASELINE_MEDIAN_MS = 67;

    // ---- classification --------------------------------------------------------------------------------------

    /**
     * Everything wrong with how {@code kinds} classifies the YAML files under {@code packs}: a file no rule covers, a
     * count that is not the pinned one, and a total that is not the corpus constant.
     */
    static List<String> classificationProblems(Path packs, PackFileKind kinds) throws IOException {
        Map<String, Integer> mapped = new TreeMap<>();
        Map<String, Integer> excluded = new TreeMap<>();
        List<String> unclassified = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(packs)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
        }
        for (Path file : files) {
            String relative = packs.relativize(file).toString().replace('\\', '/');
            switch (kinds.classify(relative)) {
                case PackFileKind.Mapped m -> mapped.merge(m.kind(), 1, Integer::sum);
                case PackFileKind.Excluded x -> excluded.merge(x.exclusionClass(), 1, Integer::sum);
                case PackFileKind.Unclassified u -> unclassified.add(relative);
            }
        }
        List<String> problems = new ArrayList<>();
        if (!unclassified.isEmpty()) {
            problems.add("unclassified files: " + unclassified);
        }
        if (!mapped.equals(new TreeMap<>(MAPPED))) {
            problems.add("mapped counts " + mapped + " differ from " + new TreeMap<>(MAPPED));
        }
        if (!excluded.equals(new TreeMap<>(EXCLUDED))) {
            problems.add("excluded counts " + excluded + " differ from " + new TreeMap<>(EXCLUDED));
        }
        int total = mapped.values().stream().mapToInt(Integer::intValue).sum()
                + excluded.values().stream().mapToInt(Integer::intValue).sum();
        if (total != OpenPacksCorpus.YAML_FILE_COUNT) {
            problems.add("mapped plus excluded is " + total + ", not the corpus constant "
                    + OpenPacksCorpus.YAML_FILE_COUNT);
        }
        if (files.size() != OpenPacksCorpus.YAML_FILE_COUNT) {
            problems.add(files.size() + " YAML files, not the corpus constant " + OpenPacksCorpus.YAML_FILE_COUNT);
        }
        return problems;
    }

    @Test
    void theCountsAreExactAndNoFileIsUnclassified() throws IOException {
        OpenPacksCorpus.assertPinned();

        assertThat(MAPPED.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(MAPPED_TOTAL);
        assertThat(EXCLUDED.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(EXCLUDED_TOTAL);
        assertThat(MAPPED_TOTAL + EXCLUDED_TOTAL).isEqualTo(OpenPacksCorpus.YAML_FILE_COUNT);
        assertThat(classificationProblems(OpenPacksCorpus.packsDirectory(), PackFileKind.bundled()))
                .isEmpty();
    }

    @Test
    void everyYamlFileIsClassifiedByExactlyOneRule() throws IOException {
        OpenPacksCorpus.assertPinned();
        Path packs = OpenPacksCorpus.packsDirectory();

        for (Path file : OpenPacksCorpus.yamlFiles()) {
            String relative = packs.relativize(file).toString().replace('\\', '/');
            assertThat(PackFileKind.bundled().matchingRules(relative))
                    .as("the rules that match %s", relative)
                    .hasSize(1);
        }
    }

    // ---- validation ------------------------------------------------------------------------------------------

    private static PackRoot rootOf(PackLocation pack) {
        return new PackRoot(pack.name(), Optional.of(pack.level()), pack.root().toString());
    }

    @Test
    void everyMappedFileOfEveryPackValidates() throws IOException {
        OpenPacksCorpus.assertPinned();
        PackValidation validation = new PackValidation(new FileSystemPackInspector());
        Map<String, Integer> mapped = new TreeMap<>();
        int excluded = 0;

        for (PackLocation pack : OpenPacksCorpus.packs()) {
            PackVerdict verdict = validation.validate(rootOf(pack));

            List<PackFinding> blocking = verdict.findings().stream()
                    .filter(f -> f.severity() == PackFindingCode.Severity.BLOCKING)
                    .toList();
            assertThat(blocking)
                    .as("blocking findings of %s:%n%s", pack.name(), verdict.findings())
                    .isEmpty();
            List<Diagnostic> warnings =
                    verdict.findings().stream().map(PackFinding::toDiagnostic).toList();
            assertThat(warnings)
                    .as("warnings of %s, every other finding is unexpected:%n%s", pack.name(), verdict.findings())
                    .containsExactlyElementsOf(OpenPacksCorpus.DIAGNOSTICS.get(pack.name()));
            for (ClassifiedFile file : verdict.files()) {
                file.kind().ifPresent(kind -> mapped.merge(kind, 1, Integer::sum));
                excluded += file.exclusionClass().isPresent() ? 1 : 0;
            }
        }

        // built-in.source.yaml sits beside the pack directories, not in one, so no pack's verdict lists it.
        assertThat(mapped).as("mapped files validated, by schema").isEqualTo(new TreeMap<>(MAPPED));
        assertThat(excluded).as("excluded files listed by the verdicts").isEqualTo(EXCLUDED_TOTAL - 1);
    }

    // ---- non-vacuity -----------------------------------------------------------------------------------------

    private static String fileKindsText() throws IOException {
        try (InputStream in =
                OpenPacksAcceptanceTest.class.getClassLoader().getResourceAsStream("packs/file-kinds.yaml")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void aCopyOfTheTableWithOneExclusionRemovedFailsTheClassification() throws IOException {
        OpenPacksCorpus.assertPinned();
        String table = fileKindsText();
        String removed = """
                  - match: "**/templates/plan-field-declaration.yaml"
                    reason: >-
                      A mission template's plan-field declaration. No model in core at 9554e7b2.
                """;
        assertThat(table).as("the exclusion to remove is in the table").contains(removed);

        PackFileKind without = new PackFileKind(table.replace(removed, ""));

        assertThat(without.exclusionRules())
                .hasSize(PackFileKind.bundled().exclusionRules().size() - 1);
        assertThat(classificationProblems(OpenPacksCorpus.packsDirectory(), without))
                .anyMatch(p -> p.startsWith("unclassified files:") && p.contains("plan-field-declaration.yaml"));
    }

    @Test
    void anUnknownClassOfFileAddedToACopyOfAPackFailsTheClassification(@TempDir Path tmp) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path packs = tmp.resolve("packs");
        Path source = OpenPacksCorpus.packsDirectory();
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path target = packs.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
        assertThat(classificationProblems(packs, PackFileKind.bundled()))
                .as("an untouched copy passes")
                .isEmpty();

        Files.writeString(packs.resolve("qa/surprise.unknown.yaml"), "a: b\n");

        assertThat(classificationProblems(packs, PackFileKind.bundled()))
                .anyMatch(p -> p.startsWith("unclassified files:") && p.contains("qa/surprise.unknown.yaml"));
    }

    @Test
    void aCopyOfTheTableWithAFilesRuleRemovedChangesTheMappedCounts() throws IOException {
        OpenPacksCorpus.assertPinned();
        String table = fileKindsText();
        String rule = """
                  - match: "**/*.toolguide.yaml"
                    schema: toolguide
                """;
        assertThat(table).contains(rule);

        List<String> problems =
                classificationProblems(OpenPacksCorpus.packsDirectory(), new PackFileKind(table.replace(rule, "")));

        assertThat(problems).anyMatch(p -> p.startsWith("unclassified files:"));
        assertThat(problems).anyMatch(p -> p.startsWith("mapped counts"));
    }

    // ---- NFR-002 ---------------------------------------------------------------------------------------------

    /** How long {@code work} takes, with a limit that is only a backstop against a hang. */
    private static long millis(Runnable work, long backstopMs) {
        long[] took = new long[1];
        assertTimeout(Duration.ofMillis(backstopMs), () -> {
            long start = System.nanoTime();
            work.run();
            took[0] = (System.nanoTime() - start) / 1_000_000;
        });
        return took[0];
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    @Test
    void validateModeAndTheLoadStayWithinTheirBudgets() throws IOException {
        OpenPacksCorpus.assertPinned();
        List<PackLocation> packs = OpenPacksCorpus.packs();
        PackValidation validation = new PackValidation(new FileSystemPackInspector());
        Runnable validateAll = () -> packs.forEach(pack -> {
            PackVerdict verdict = validation.validate(rootOf(pack));
            assertThat(verdict.files()).isNotEmpty();
        });
        FileSystemCharterPackRepository repository = new FileSystemCharterPackRepository(() -> packs);
        Runnable loadAll = () -> {
            List<CharterPack> loaded = repository.load().packs();
            assertThat(loaded).hasSize(packs.size());
        };

        long cold = millis(validateAll, 2 * VALIDATE_BUDGET_MS);
        long[] warm = new long[5];
        long[] load = new long[5];
        for (int i = 0; i < 5; i++) {
            warm[i] = millis(validateAll, 2 * VALIDATE_BUDGET_MS);
            load[i] = millis(loadAll, 2 * (LOAD_BASELINE_MEDIAN_MS + LOAD_HEADROOM_MS));
        }
        long warmMedian = median(warm);
        long loadMedian = median(load);
        System.out.printf(
                "NFR-002: validate mode over %d mapped files: first run %d ms, warm median of 5 %d ms (budget %d ms); "
                        + "load of the %d packs: median of 5 %d ms (baseline %d ms + %d ms)%n",
                MAPPED_TOTAL,
                cold,
                warmMedian,
                VALIDATE_BUDGET_MS,
                packs.size(),
                loadMedian,
                LOAD_BASELINE_MEDIAN_MS,
                LOAD_HEADROOM_MS);

        assertThat(warmMedian).as("validate mode, warm, over all mapped files").isLessThanOrEqualTo(VALIDATE_BUDGET_MS);
        assertThat(loadMedian)
                .as("repository load of the open-packs packs")
                .isLessThanOrEqualTo(LOAD_BASELINE_MEDIAN_MS + LOAD_HEADROOM_MS);
    }
}
