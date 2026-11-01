package ai.speckitty.charter.contracts.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The relation table of {@code docs/development/hypermedia-hateoas.md} is the one source of the relation keys
 * (R-03 revised): the {@code enum} of the lint rule {@code link-relations-are-known} lists exactly its keys, the
 * backticked names of the table's first column.
 */
class RelationTableParityTest {

    private static final String RULE = "link-relations-are-known";
    private static final Pattern NAME = Pattern.compile("`([^`]+)`");

    @Test
    void theRuleListsExactlyTheRelationsOfTheGuidesTable() throws IOException {
        List<String> table = relationTable(Files.readString(path("charter.hypermediaGuide")));
        assertThat(table).as("the guide's relation table").isNotEmpty().doesNotHaveDuplicates();
        assertThat(ruleEnum(Files.readString(path("charter.lintRuleset"))))
                .as("the enum of %s", RULE)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(table);
    }

    @Test
    void theTableParserReadsEveryNameOfTheFirstColumnAndNothingElse() {
        String guide = """
                ## Relation names

                | Relation | Points to | Method | On | Status |
                |---|---|---|---|---|
                | `self` | The resource itself | `GET` | Every success response | Built |
                | `select`, `unselect` | `…/selections/{kind}` | `PUT`, `DELETE` | An artifact | Proposed |

                ## Anti-patterns

                | `not-a-relation` | outside the section |
                """;
        assertThat(relationTable(guide)).containsExactly("self", "select", "unselect");
    }

    /** The backticked names of the first column of the table under {@code ## Relation names}, in order. */
    static List<String> relationTable(String guide) {
        List<String> names = new ArrayList<>();
        boolean inSection = false;
        boolean header = true;
        for (String line : guide.lines().toList()) {
            if (line.startsWith("## ")) {
                inSection = line.equals("## Relation names");
                continue;
            }
            if (!inSection || !line.startsWith("|") || line.startsWith("|---")) {
                continue;
            }
            if (header) {
                header = false;
                continue;
            }
            Matcher name = NAME.matcher(line.split("\\|", -1)[1]);
            while (name.find()) {
                names.add(name.group(1));
            }
        }
        return names;
    }

    private static List<String> ruleEnum(String ruleset) {
        Map<?, ?> document = (Map<?, ?>) new Load(LoadSettings.builder().build()).loadFromString(ruleset);
        Map<?, ?> rule = (Map<?, ?>) ((Map<?, ?>) document.get("rules")).get(RULE);
        Map<?, ?> then = (Map<?, ?>) rule.get("then");
        Map<?, ?> schema = (Map<?, ?>) ((Map<?, ?>) then.get("functionOptions")).get("schema");
        List<?> names = (List<?>) ((Map<?, ?>) schema.get("propertyNames")).get("enum");
        return names.stream().map(String::valueOf).toList();
    }

    private static Path path(String property) {
        String value = System.getProperty(property);
        assertThat(value).as("the system property %s", property).isNotNull();
        return Path.of(value);
    }
}
