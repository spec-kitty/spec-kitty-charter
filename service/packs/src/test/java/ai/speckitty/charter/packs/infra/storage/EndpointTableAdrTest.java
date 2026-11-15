package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.EndpointPair;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Tension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-012's independent oracle: the endpoint table as ADR-0077 decision 5 writes it, parsed from the ADR, is the table
 * the kernel answers ({@link RelationType#endpoints()}, {@link Tension#allowedEndpoints()}). The record and the code
 * cannot drift: a row changed in one and not the other fails here. The words of the table read as follows: {@code G}
 * is the six guidance kinds; "any kind authored in graphs" is every kind a graph fragment names by a plural
 * ({@code file-kinds.yaml}'s {@code fragment_kinds}); "the glossary kind" is {@code glossary_pack}; "activatable" is
 * {@link ArtifactKind#activatable()}; a parenthesis is a remark, and so is what follows the first comma after
 * {@code ↔}.
 */
@DisplayName("ADR-0077 decision 5's endpoint table is the kernel's (FR-012)")
class EndpointTableAdrTest {

    private static final Path ADR = Path.of(System.getProperty("charter.repositoryRoot"))
            .resolve("docs/adr/0077-kind-taxonomy-and-step-authority.md");
    private static final String TENSION_ROW = "tension";
    private static final Set<NodeKind> GUIDANCE = EnumSet.of(
            NodeKind.DIRECTIVE,
            NodeKind.TACTIC,
            NodeKind.STYLEGUIDE,
            NodeKind.TOOLGUIDE,
            NodeKind.PARADIGM,
            NodeKind.PROCEDURE);
    private static final Pattern ROW = Pattern.compile("^\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|\\s*$");
    private static final Pattern SEMICOLON = Pattern.compile(";");
    private static final Pattern COMMA = Pattern.compile(",");
    private static final Pattern ONE_WAY = Pattern.compile("→");
    private static final Pattern BOTH_WAYS = Pattern.compile("↔");
    private static final Pattern RELATION = Pattern.compile("`([a-z_]+)`");
    private static final Pattern DIAGONAL_EXCEPT = Pattern.compile("^X → X for every activatable X except (.+)$");
    private static final Pattern DIAGONAL_WHERE = Pattern.compile("^X → X, where X is activatable and not (.+)$");

    @Test
    @DisplayName("every row of the ADR's table is the kernel's row, and every relation has one")
    void theAdrsTableIsTheKernels() throws IOException {
        assertTableIsTheKernels(Files.readString(ADR, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("positive control: a row the ADR widens fails the check")
    void aWidenedRowFails() throws IOException {
        String adr = Files.readString(ADR, StandardCharsets.UTF_8);
        String row = "| `rejects` | G → anti_pattern |";
        assertThat(adr).contains(row);
        String skewed = adr.replace(row, "| `rejects` | G → anti_pattern or asset |");

        assertThatThrownBy(() -> assertTableIsTheKernels(skewed))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the endpoints of rejects");
    }

    @Test
    @DisplayName("positive control: a row the ADR loses fails the check")
    void aLostRowFails() throws IOException {
        String adr = Files.readString(ADR, StandardCharsets.UTF_8);
        String row = "| `instantiates` | action → template |\n";
        assertThat(adr).contains(row);

        assertThatThrownBy(() -> assertTableIsTheKernels(adr.replace(row, "")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the relations of the ADR's table");
    }

    private static void assertTableIsTheKernels(String adr) {
        Map<String, Set<EndpointPair>> table = parse(adr);
        Set<String> expected = new LinkedHashSet<>();
        Arrays.stream(RelationType.values()).map(RelationType::token).forEach(expected::add);
        expected.add(TENSION_ROW);
        assertThat(table.keySet()).as("the relations of the ADR's table").isEqualTo(expected);
        for (RelationType type : RelationType.values()) {
            assertThat(table.get(type.token()))
                    .as("the endpoints of " + type.token())
                    .isEqualTo(type.endpoints());
        }
        assertThat(table.get(TENSION_ROW)).as("the endpoints of the tension").isEqualTo(Tension.allowedEndpoints());
    }

    /** The rows of decision 5's table, by relation token ({@code tension} for the tension row). */
    private static Map<String, Set<EndpointPair>> parse(String adr) {
        int start = adr.indexOf("| Relation | Source → target |");
        assertThat(start).as("decision 5's table").isNotNegative();
        Map<String, Set<EndpointPair>> table = new LinkedHashMap<>();
        for (String line : adr.substring(start).lines().skip(2).toList()) {
            Matcher row = ROW.matcher(line.strip());
            if (!row.matches()) {
                break;
            }
            String head = row.group(1);
            String relation = head.startsWith(TENSION_ROW) ? TENSION_ROW : first(RELATION, head);
            table.put(relation, pairs(row.group(2)));
        }
        return table;
    }

    private static Set<EndpointPair> pairs(String cell) {
        Set<EndpointPair> pairs = new LinkedHashSet<>();
        String text = cell.replaceAll("\\s*\\([^)]*\\)", "").replace("`", "").strip();
        for (String rectangle : SEMICOLON.split(text, -1)) {
            String part = rectangle.strip();
            Matcher except = DIAGONAL_EXCEPT.matcher(part);
            Matcher where = DIAGONAL_WHERE.matcher(part);
            if (except.matches() || where.matches()) {
                Set<NodeKind> kinds = activatable();
                kinds.removeAll(kinds((except.matches() ? except : where).group(1)));
                kinds.forEach(kind -> pairs.add(new EndpointPair(kind, kind)));
                continue;
            }
            boolean symmetric = part.contains("↔");
            String[] ends = (symmetric ? BOTH_WAYS : ONE_WAY).split(part, 2);
            assertThat(ends).as("a rectangle of two ends: %s", part).hasSize(2);
            String target = symmetric ? COMMA.split(ends[1], 2)[0] : ends[1];
            for (NodeKind source : kinds(ends[0])) {
                for (NodeKind to : kinds(target)) {
                    pairs.add(new EndpointPair(source, to));
                }
            }
        }
        return pairs;
    }

    /** A list of kinds as the table writes it: comma-separated, its last joined by "or" or "and". */
    private static Set<NodeKind> kinds(String list) {
        Set<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        for (String term : COMMA.split(list.replace(" or ", ",").replace(" and ", ","), -1)) {
            String word = term.strip();
            switch (word) {
                case "G" -> kinds.addAll(GUIDANCE);
                case "any kind authored in graphs" ->
                    PackFileKind.bundled().fragmentKinds().values().stream()
                            .map(token -> NodeKind.fromToken(token).orElseThrow())
                            .forEach(kinds::add);
                case "the glossary kind" -> kinds.add(NodeKind.GLOSSARY_CONTEXT);
                default ->
                    kinds.add(NodeKind.fromToken(word)
                            .orElseThrow(() -> new AssertionError("no kind is written \"" + word + "\"")));
            }
        }
        return kinds;
    }

    private static Set<NodeKind> activatable() {
        Set<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        Arrays.stream(ArtifactKind.values())
                .filter(ArtifactKind::activatable)
                .map(NodeKind::of)
                .forEach(kinds::add);
        return kinds;
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertThat(matcher.find()).as("a relation token in %s", text).isTrue();
        return matcher.group(1);
    }
}
