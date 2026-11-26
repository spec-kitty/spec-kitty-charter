package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.infra.storage.PackFileKind.Classification;
import ai.speckitty.charter.packs.infra.storage.PackFileKind.Excluded;
import ai.speckitty.charter.packs.infra.storage.PackFileKind.Mapped;
import ai.speckitty.charter.packs.infra.storage.PackFileKind.RefusedByName;
import ai.speckitty.charter.packs.infra.storage.PackFileKind.Unclassified;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the packs contract's classifier conformance cases ({@code contracts/packs/conformance/classifier-cases.yaml},
 * #559) against the service's own classifier, and checks that every rule of {@code file-kinds.yaml} has a case, so the
 * published cases cannot fall behind the table they describe.
 */
class ClassifierConformanceTest {

    private static final Path CASES = Path.of("contracts/packs/conformance/classifier-cases.yaml");
    private static final Set<String> KEYS = Set.of("path", "expect", "rule", "kind", "schema", "content_form");
    private static final String SCHEMA_PREFIX = "packs/";
    private static final PackFileKind KINDS = PackFileKind.bundled();

    /** One case, as the cases file writes it. */
    record Case(
            String path,
            String expect,
            @Nullable String rule,
            @Nullable String kind,
            @Nullable String schema,
            @Nullable String contentForm) {
        @Override
        public String toString() {
            return path + " -> " + expect;
        }
    }

    static List<Case> cases() throws IOException {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("system property charter.repositoryRoot").isNotNull();
        Object tree = Yaml.load(Files.readString(Path.of(root).resolve(CASES)));
        assertThat(tree).as("the cases file").isInstanceOf(Map.class);
        Object list = ((Map<?, ?>) tree).get("cases");
        assertThat(list).as("its cases").isInstanceOf(List.class);
        List<Case> cases = new ArrayList<>();
        for (Object item : (List<?>) list) {
            assertThat(item).as("a case").isInstanceOf(Map.class);
            Map<?, ?> entry = (Map<?, ?>) item;
            assertThat(KEYS)
                    .as("the keys of %s", entry)
                    .containsAll(entry.keySet().stream().map(String::valueOf).toList());
            cases.add(new Case(
                    text(entry, "path"),
                    text(entry, "expect"),
                    optional(entry, "rule"),
                    optional(entry, "kind"),
                    optional(entry, "schema"),
                    optional(entry, "content_form")));
        }
        return cases;
    }

    @ParameterizedTest
    @MethodSource("cases")
    void theClassifierMeetsTheCase(Case c) {
        Classification actual = KINDS.classify(c.path());
        switch (c.expect()) {
            case "mapped" -> {
                assertThat(actual).as(c.path()).isInstanceOf(Mapped.class);
                Mapped mapped = (Mapped) actual;
                assertThat(mapped.kind()).as("kind of %s", c.path()).isEqualTo(c.kind());
                assertThat(mapped.schema())
                        .as("schema of %s", c.path())
                        .isEqualTo(Optional.ofNullable(c.schema()).map(s -> SCHEMA_PREFIX + s));
                assertThat(mapped.contentForm().token())
                        .as("content form of %s", c.path())
                        .isEqualTo(c.contentForm());
                assertThat(decidingRule(c.path())).as("rule of %s", c.path()).isEqualTo(c.rule());
            }
            case "excluded" -> {
                assertThat(actual).as(c.path()).isInstanceOf(Excluded.class);
                assertThat(((Excluded) actual).exclusionClass())
                        .as("rule of %s", c.path())
                        .isEqualTo(c.rule());
                assertNoMappedFields(c);
            }
            case "retired" -> {
                // A file of a retired family or a root case variant: refused by name under the case's rule.
                assertThat(actual).as(c.path()).isInstanceOf(RefusedByName.class);
                assertThat(((RefusedByName) actual).match())
                        .as("rule of %s", c.path())
                        .isEqualTo(c.rule());
                assertNoMappedFields(c);
            }
            case "unmodelled" -> {
                assertThat(actual).as(c.path()).isEqualTo(Unclassified.INSTANCE);
                assertThat(c.rule())
                        .as("rule of an unmodelled case %s", c.path())
                        .isNull();
                assertNoMappedFields(c);
            }
            default -> throw new AssertionError("unknown expect '" + c.expect() + "' for " + c.path());
        }
    }

    /**
     * Every rule of the table, by section and glob, decides at least one case of its own section. A case-variant case
     * (a {@code retired} outcome under a root {@code files} rule) covers no rule: the {@code files} rule still needs a
     * {@code mapped} case.
     */
    @Test
    void everyRuleOfTheTableHasACase() throws IOException {
        Set<String> rules = new LinkedHashSet<>();
        KINDS.fileRules().forEach(r -> rules.add("files " + r.match()));
        KINDS.exclusionRules().forEach(r -> rules.add("exclusions " + r.match()));
        KINDS.retiredRules().forEach(r -> rules.add("retired " + r.match()));
        KINDS.payloadRules().forEach(r -> rules.add("payloads " + r.match()));
        KINDS.ignoredRules().forEach(r -> rules.add("ignored " + r.match()));
        Set<String> covered = new LinkedHashSet<>();
        cases().forEach(c -> coveredRule(c).ifPresent(covered::add));
        assertThat(covered).as("the rules the cases decide, by section").containsAll(rules);
        assertThat(rules).as("the rules of file-kinds.yaml, by section").containsAll(covered);
    }

    /**
     * The section and glob of the rule that decides {@code c}, or empty for an unmodelled case or a root case variant
     * (refused as {@code case_variant}, never as {@code Retired}, so it covers no rule, as the class Javadoc says). The section is the one that holds the glob, so a letter-case variant decided in the
     * lower-cased run (#661) covers its rule as an exact match does.
     */
    private static Optional<String> coveredRule(Case c) {
        String rule = c.rule();
        if (rule == null) {
            return Optional.empty();
        }
        return switch (c.expect()) {
            case "mapped" -> Optional.of((c.schema() != null ? "files " : "payloads ") + rule);
            case "excluded" ->
                Optional.of((holds(KINDS.exclusionRules().stream().map(PackFileKind.ExclusionRule::match), rule)
                                ? "exclusions "
                                : "ignored ")
                        + rule);
            case "retired" ->
                KINDS.classify(c.path()) instanceof PackFileKind.Retired
                                && holds(KINDS.retiredRules().stream().map(PackFileKind.RetiredRule::match), rule)
                        ? Optional.of("retired " + rule)
                        : Optional.empty();
            default -> Optional.empty();
        };
    }

    private static boolean holds(Stream<String> globs, String glob) {
        return globs.anyMatch(glob::equals);
    }

    @Test
    void everyOutcomeHasACaseAndNoPathRepeats() throws IOException {
        List<Case> cases = cases();
        assertThat(cases.stream().map(Case::expect).distinct())
                .containsExactlyInAnyOrder("mapped", "excluded", "retired", "unmodelled");
        assertThat(cases.stream().map(Case::path)).doesNotHaveDuplicates();
    }

    /**
     * The first {@code files} rule that maps {@code path} in lower case, which decides its kind in any spelling (#661),
     * or else the {@code payloads} rule.
     */
    private static @Nullable String decidingRule(String path) {
        Set<String> files = new LinkedHashSet<>();
        KINDS.fileRules().forEach(r -> files.add(r.match()));
        return Stream.of(
                        KINDS.matchingRulesFolded(path).stream()
                                .filter(files::contains)
                                .toList(),
                        KINDS.matchingPayloadRules(path))
                .filter(rules -> !rules.isEmpty())
                .map(rules -> rules.get(0))
                .findFirst()
                .orElse(null);
    }

    private static void assertNoMappedFields(Case c) {
        assertThat(Stream.of(c.kind(), c.schema(), c.contentForm()))
                .as("kind, schema and content_form of a %s case %s", c.expect(), c.path())
                .containsOnlyNulls();
    }

    private static String text(Map<?, ?> entry, String key) {
        String value = optional(entry, key);
        assertThat(value).as("%s of %s", key, entry).isNotNull();
        return value;
    }

    private static @Nullable String optional(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value == null ? null : value.toString();
    }
}
