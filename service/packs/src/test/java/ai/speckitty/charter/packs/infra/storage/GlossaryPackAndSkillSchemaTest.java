package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The {@code glossary-pack} and {@code skill} schemas (FR-006, FR-007): every pinned file of either kind validates,
 * every object in both is closed, and the keywords the skill schema brings ({@code not}, {@code maxLength}) yield a
 * stable violation detail.
 *
 * <p>The open-packs corpus test runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies
 * the checkout; the rest, this repository's vendored copies included, runs under {@code :packs:test}.
 */
@DisplayName("Glossary-pack and skill schemas (#192)")
class GlossaryPackAndSkillSchemaTest {

    private static final String GLOSSARY = "glossary-pack";
    private static final String SKILL = "skill";
    private static final String GLOSSARY_SUFFIX = ".glossary-pack.yaml";
    private static final String SKILL_SUFFIX = ".skill.yaml";

    private static final String VALID_GLOSSARY = """
            id: demo-glossary
            provenance: org
            description: A demo.
            terms:
            - surface: lane
              definition: A sequence of work packages.
              confidence: 0.9
              status: active
              see_also: [mission]
              synonyms_to_avoid: [track]
              aliases: [swimlane]
              banned_synonyms: [queue]
              introduced_in_mission: 01M5YVEC
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

    // ---- the pinned files ------------------------------------------------------------------------

    @Test
    @Tag("open-packs")
    @DisplayName("every pinned glossary pack and skill of the open-packs checkout validates")
    void everyPinnedFileOfTheOpenPacksCheckoutValidates() throws IOException {
        List<Path> files = filesOfBothKinds(OpenPacksCorpus.packsDirectory());

        assertThat(problems(files)).as("files that do not validate").isEmpty();
        assertThat(files).as("4 glossary packs and 5 skills").hasSize(9);
    }

    @Test
    @DisplayName("every glossary pack and skill vendored in this repository's packs/ validates")
    void everyVendoredFileValidates() throws IOException {
        String root = System.getProperty("charter.repositoryRoot");
        assertThat(root).as("system property charter.repositoryRoot").isNotNull();
        List<Path> files = new ArrayList<>();
        files.addAll(filesOfBothKinds(Path.of(root, "packs", "internal")));
        files.addAll(filesOfBothKinds(Path.of(root, "packs", "qa")));

        assertThat(problems(files)).as("files that do not validate").isEmpty();
        assertThat(files)
                .as("internal: 1 glossary pack and 5 skills; qa: 1 glossary pack")
                .hasSize(7);
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
    @DisplayName("a glossary pack carrying every field, and prompt and wrapper skills carrying every field, validate")
    void fixturesCarryingEveryFieldValidate() {
        assertThat(validate(GLOSSARY, VALID_GLOSSARY)).isEmpty();
        assertThat(validate(SKILL, VALID_PROMPT_SKILL)).isEmpty();
        assertThat(validate(SKILL, VALID_WRAPPER_SKILL)).isEmpty();
    }

    @Test
    @DisplayName("the nullable glossary fields accept an explicit null, as core does")
    void theNullableGlossaryFieldsAcceptNull() {
        String nulls = """
                id: demo-glossary
                provenance: org
                description: null
                terms:
                - surface: lane
                  definition: A sequence.
                  confidence: 1
                  status: draft
                  see_also: null
                  synonyms_to_avoid: null
                  aliases: null
                  banned_synonyms: null
                  introduced_in_mission: null
                """;
        assertThat(validate(GLOSSARY, nulls)).isEmpty();
    }

    // ---- unknown keys, at every level -------------------------------------------------------------

    @ParameterizedTest(name = "{0}: an unknown key at /{1} is refused")
    @CsvSource({
        "glossary-pack, '', 'id: g\nprovenance: org\nterms:\n- {surface: s, definition: d, confidence: 0.9, status: active}\nextra: 1\n'",
        "glossary-pack, terms/0, 'id: g\nprovenance: org\nterms:\n- {surface: s, definition: d, confidence: 0.9, status: active, extra: 1}\n'",
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

    // ---- glossary-pack refusals ---------------------------------------------------------------------

    @Test
    @DisplayName("a confidence written as a string is refused: core's model reads a float")
    void aStringConfidenceIsRefused() {
        String yaml = VALID_GLOSSARY.replace("confidence: 0.9", "confidence: \"0.9\"");

        assertThat(validate(GLOSSARY, yaml)).singleElement().satisfies(v -> {
            assertThat(v.keyword()).isEqualTo("type");
            assertThat(v.pointer()).containsExactly("terms", "0", "confidence");
        });
    }

    @Test
    @DisplayName("a glossary pack with no terms is refused")
    void noTermsIsRefused() {
        assertThat(validate(GLOSSARY, "id: g\nprovenance: org\nterms: []\n"))
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
