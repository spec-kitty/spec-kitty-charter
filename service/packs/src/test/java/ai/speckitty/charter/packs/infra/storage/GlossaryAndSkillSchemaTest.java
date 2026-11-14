package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The {@code glossary} (ADR-0049) and {@code skill} schemas (FR-006, FR-007): every example file of either kind validates,
 * every object in both is closed, and the keywords the skill schema brings ({@code not}, {@code maxLength}) yield a
 * stable violation detail.
 *
 * <p>The example-pack check reads the charter-owned example packs (ADR-0070).
 */
@DisplayName("Glossary and skill schemas (#192, ADR-0049)")
class GlossaryAndSkillSchemaTest {

    private static final String GLOSSARY = "glossary";
    private static final String SKILL = "skill";
    private static final String GLOSSARY_SUFFIX = ".glossary.yaml";
    private static final String SKILL_SUFFIX = ".skill.yaml";

    private static final String VALID_GLOSSARY = """
            schema_version: "1.0"
            id: demo-glossary
            name: Demo
            description: A demo.
            tags: [demo]
            applies_to:
              paths: ["src/**"]
              languages: [java]
              stacks: [spring]
            terms:
            - id: lane
              name: lane
              definition: A sequence of work packages.
              examples: [Lane a holds WP01.]
              aliases: [swimlane]
              avoid: [track, queue]
              status: active
              see_also: ["term:demo-glossary/mission"]
            """;

    /** The smallest valid context: only the required fields. */
    private static final String MINIMAL_GLOSSARY = """
            schema_version: "1.0"
            id: g
            name: G
            description: D
            terms:
            - {id: s, name: s, definition: d, status: active}
            """;

    private static final String VALID_PROMPT_SKILL = """
            schema_version: "1.0"
            id: demo-skill
            title: Demo
            description: A demo skill.
            triggers: [demo]
            form: prompt
            body_path: demo-skill.skill.md
            parameters:
            - name: issue
              required: true
              description: The issue.
              default: "1"
            invocation:
              user_invocable: true
              model_invocable: false
              side_effects: [git-push]
            tools: ["*"]
            version: 1.0.0
            maintainers: ["@spec-kitty/core"]
            overrides: other-skill
            """;

    private static final String VALID_WRAPPER_SKILL = """
            schema_version: "1.0"
            id: demo-wrapper
            title: Demo
            description: A demo wrapper.
            form: wrapper
            expands_to:
              target: cli:spec-kitty next
              args: --mission x
            """;

    private static List<SchemaViolation> validate(String kind, String yaml) {
        return PackSchemas.bundled().validate(kind, "x.yaml", Yaml.load(yaml));
    }

    private static String detail(SchemaViolation violation) {
        List<PackFinding> findings = PackPolicy.schemaFindings("demo", List.of(violation));
        assertThat(findings).hasSize(1);
        return findings.get(0).detail();
    }

    // ---- the example files ------------------------------------------------------------------------

    @Test
    @DisplayName("every glossary context and skill of the example packs validates")
    void everyFileOfTheExamplePacksValidates() throws IOException {
        List<Path> files = filesOfBothKinds(ExamplePacks.packsDirectory());

        assertThat(problems(files)).as("files that do not validate").isEmpty();
        assertThat(files)
                .as("glossary contexts and skills in the example packs")
                .isNotEmpty();
        PackFiles.assertEachPackHolds(
                "glossary context",
                files.stream()
                        .filter(f -> f.toString().endsWith(GLOSSARY_SUFFIX))
                        .toList(),
                ExamplePacks.BUILT_IN);
        PackFiles.assertEachPackHolds(
                "skill",
                files.stream().filter(f -> f.toString().endsWith(SKILL_SUFFIX)).toList(),
                ExamplePacks.BUILT_IN,
                ExamplePacks.ORG,
                ExamplePacks.PROJECT);
    }

    private static List<String> problems(List<Path> files) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            String kind = file.getFileName().toString().endsWith(SKILL_SUFFIX) ? SKILL : GLOSSARY;
            List<SchemaViolation> violations = PackSchemas.bundled()
                    .validate(kind, file.toString(), Yaml.load(Files.readString(file, StandardCharsets.UTF_8)));
            if (!violations.isEmpty()) {
                problems.add(file + ": " + violations);
            }
        }
        return problems;
    }

    private static List<Path> filesOfBothKinds(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(GLOSSARY_SUFFIX) || name.endsWith(SKILL_SUFFIX);
                    })
                    .sorted()
                    .toList();
        }
    }

    // ---- positive controls ---------------------------------------------------------------------

    @Test
    @DisplayName(
            "a glossary context carrying every field, and prompt and wrapper skills carrying every field, validate")
    void fixturesCarryingEveryFieldValidate() {
        assertThat(validate(GLOSSARY, VALID_GLOSSARY)).isEmpty();
        assertThat(validate(GLOSSARY, MINIMAL_GLOSSARY)).isEmpty();
        assertThat(validate(SKILL, VALID_PROMPT_SKILL)).isEmpty();
        assertThat(validate(SKILL, VALID_WRAPPER_SKILL)).isEmpty();
    }

    @ParameterizedTest(name = "an explicit null at {0} is refused")
    @CsvSource({"tags", "applies_to"})
    @DisplayName("an optional context field refuses an explicit null: the shape is closed and typed")
    void anExplicitNullIsRefused(String field) {
        String nulls = MINIMAL_GLOSSARY.replace("terms:\n", field + ": null\nterms:\n");

        assertThat(validate(GLOSSARY, nulls)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("type");
            assertThat(v.pointer()).containsExactly(field);
        });
    }

    // ---- unknown keys, at every level -------------------------------------------------------------

    @ParameterizedTest(name = "{0}: an unknown key at /{1} is refused")
    @CsvSource({
        "glossary, '', 'schema_version: \"1.0\"\nid: g\nname: G\ndescription: D\nterms:\n- {id: s, name: s, definition: d, status: active}\nextra: 1\n'",
        "glossary, terms/0, 'schema_version: \"1.0\"\nid: g\nname: G\ndescription: D\nterms:\n- {id: s, name: s, definition: d, status: active, extra: 1}\n'",
        "glossary, applies_to, 'schema_version: \"1.0\"\nid: g\nname: G\ndescription: D\napplies_to: {paths: [x], extra: [y]}\nterms:\n- {id: s, name: s, definition: d, status: active}\n'",
        "skill, '', 'schema_version: \"1.0\"\nid: s\ntitle: t\ndescription: d\nform: prompt\nbody_path: s.md\nextra: 1\n'",
        "skill, expands_to, 'schema_version: \"1.0\"\nid: s\ntitle: t\ndescription: d\nform: wrapper\nexpands_to: {target: \"builtin:x\", extra: 1}\n'",
        "skill, parameters/0, 'schema_version: \"1.0\"\nid: s\ntitle: t\ndescription: d\nform: prompt\nbody_path: s.md\nparameters:\n- {name: p, extra: 1}\n'",
        "skill, invocation, 'schema_version: \"1.0\"\nid: s\ntitle: t\ndescription: d\nform: prompt\nbody_path: s.md\ninvocation: {user_invocable: true, extra: 1}\n'"
    })
    void anUnknownKeyIsRefusedAtEachLevel(String kind, String pointer, String yaml) {
        List<String> expected = pointer.isEmpty() ? List.of() : List.of(pointer.split("/"));

        assertThat(validate(kind, yaml)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("additionalProperties");
            assertThat(v.pointer()).isEqualTo(expected);
            assertThat(v.property()).isEqualTo(Optional.of("extra"));
        });
    }

    // ---- glossary refusals ---------------------------------------------------------------------

    @ParameterizedTest(name = "{1} at /{2} breaks ''{3}''")
    @CsvSource(
            delimiter = '|',
            value = {
                "status: active|status: retired|terms/0/status|enum",
                "id: lane|id: Lane|terms/0/id|pattern",
                "id: lane|id: a--b|terms/0/id|pattern",
                "id: lane|id: 4-eyes|terms/0/id|pattern",
                "id: demo-glossary|id: Demo|id|pattern",
                "see_also: [\"term:demo-glossary/mission\"]|see_also: [mission]|terms/0/see_also/0|pattern",
                "see_also: [\"term:demo-glossary/mission\"]|see_also: [\"glossary_context:demo-glossary\"]"
                        + "|terms/0/see_also/0|pattern",
                "see_also: [\"term:demo-glossary/mission\"]|see_also: []|terms/0/see_also|minItems",
                "tags: [demo]|tags: []|tags|minItems",
                "tags: [demo]|tags: [demo, demo]|tags|uniqueItems",
                "name: Demo|name: \" \"|name|pattern",
                "description: A demo.|description: \" \"|description|pattern",
                "definition: A sequence of work packages.|definition: \" \"|terms/0/definition|pattern",
                "avoid: [track, queue]|avoid: []|terms/0/avoid|minItems",
                "paths: [\"src/**\"]|paths: []|applies_to/paths|minItems",
                "schema_version: \"1.0\"|schema_version: \"2.0\"|schema_version|const"
            })
    void aGlossaryOffItsShapeIsRefused(String valid, String broken, String pointer, String keyword) {
        assertThat(VALID_GLOSSARY).contains(valid);

        assertThat(validate(GLOSSARY, VALID_GLOSSARY.replace(valid, broken)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.keyword()).isEqualTo(keyword);
                    assertThat(v.pointer()).containsExactly(pointer.split("/"));
                });
    }

    @Test
    @DisplayName("a context or term id of 65 characters breaks 'maxLength'; 64 is the limit")
    void anIdOfSixtyFiveCharactersIsRefused() {
        assertThat(validate(GLOSSARY, MINIMAL_GLOSSARY.replace("id: g", "id: " + "g".repeat(65))))
                .extracting(SchemaViolation::keyword)
                .containsExactly("maxLength");
        assertThat(validate(GLOSSARY, MINIMAL_GLOSSARY.replace("{id: s,", "{id: " + "s".repeat(65) + ",")))
                .extracting(SchemaViolation::keyword)
                .containsExactly("maxLength");
        assertThat(validate(GLOSSARY, MINIMAL_GLOSSARY.replace("id: g", "id: " + "g".repeat(64))))
                .isEmpty();
    }

    @ParameterizedTest(name = "a context without {0} is refused")
    @CsvSource({"schema_version", "id", "name", "description", "terms"})
    void aContextWithoutARequiredFieldIsRefused(String field) {
        String yaml = MINIMAL_GLOSSARY
                .lines()
                .filter(line -> !line.startsWith(field + ":") && !(field.equals("terms") && line.startsWith("-")))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));

        assertThat(validate(GLOSSARY, yaml)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("required");
            assertThat(v.property()).contains(field);
        });
    }

    @Test
    @DisplayName("a glossary context with no terms is refused")
    void noTermsIsRefused() {
        assertThat(validate(
                        GLOSSARY,
                        MINIMAL_GLOSSARY.replace(
                                "terms:\n- {id: s, name: s, definition: d, status: active}\n", "terms: []\n")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.keyword()).isEqualTo("minItems");
                    assertThat(v.pointer()).containsExactly("terms");
                });
    }

    // ---- skill refusals, and the two keywords new to the bundle ------------------------------------

    @Test
    @DisplayName("a prompt skill without body_path is refused")
    void aPromptSkillWithoutBodyPathIsRefused() {
        String yaml = VALID_PROMPT_SKILL.replace("body_path: demo-skill.skill.md\n", "");

        assertThat(validate(SKILL, yaml)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("required");
            assertThat(v.property()).isEqualTo(Optional.of("body_path"));
            assertThat(v.pointer()).isEmpty();
        });
    }

    @Test
    @DisplayName("a wrapper skill with body_path breaks 'not', with a stable detail")
    void aWrapperSkillWithBodyPathBreaksNot() {
        String yaml = VALID_WRAPPER_SKILL + "body_path: demo-wrapper.skill.md\n";

        assertThat(validate(SKILL, yaml)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("not");
            assertThat(v.pointer()).isEmpty();
            assertThat(detail(v)).isEqualTo("demo/x.yaml: '/' breaks schema keyword 'not'");
        });
    }

    @Test
    @DisplayName("a prompt skill with expands_to breaks 'not' too")
    void aPromptSkillWithExpandsToBreaksNot() {
        String yaml = VALID_PROMPT_SKILL + "expands_to: {target: \"builtin:x\"}\n";

        assertThat(validate(SKILL, yaml)).extracting(SchemaViolation::keyword).containsExactly("not");
    }

    @Test
    @DisplayName("an id of 65 characters breaks 'maxLength', with a stable detail")
    void anIdOfSixtyFiveCharactersBreaksMaxLength() {
        String id = "a".repeat(65);
        String yaml = VALID_WRAPPER_SKILL.replace("id: demo-wrapper", "id: " + id);

        assertThat(validate(SKILL, yaml)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("maxLength");
            assertThat(v.pointer()).containsExactly("id");
            assertThat(v.value()).contains(id);
            assertThat(detail(v)).isEqualTo("demo/x.yaml: '/id' breaks schema keyword 'maxLength'");
        });
        assertThat(validate(SKILL, VALID_WRAPPER_SKILL.replace("id: demo-wrapper", "id: " + "a".repeat(64))))
                .as("64 characters is the limit, not past it")
                .isEmpty();
    }
}
