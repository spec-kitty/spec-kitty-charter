package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.Performer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The agent-profile YAML form")
class AgentProfileYamlTest {

    private static final SerializationStrategy YAML = new YamlSerializationStrategy();
    private static final MeaningOfTexts MEANING = MeaningOfTexts.adr0036();

    private static final String MINIMAL = """
            profile-id: p
            name: P
            role: implementer
            purpose: Builds things.
            specialization:
              primary-focus: Building.
            """;

    private static AgentProfileDso read(String text) {
        FileRead<AgentProfileDso> read = YAML.read(AgentProfileBinding.INSTANCE, text, "p.agent.yaml");
        assertThat(read).as(text).isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<AgentProfileDso>) read).value();
    }

    /** Reads, maps to the entity and back, writes; the written text must mean what the source meant. */
    private static String roundTrip(String source) {
        AgentProfileDso dso = read(source);
        AgentProfile entity = AgentProfileBinding.INSTANCE.toEntity(new PackIdentifier("p"), dso);
        String written = YAML.write(AgentProfileBinding.INSTANCE, AgentProfileBinding.INSTANCE.toDso(entity));
        assertThat(MEANING.compareTexts("agent-profile", source, written))
                .as("written:%n%s", written)
                .isEmpty();
        assertThat(read(written)).isEqualTo(dso);
        return written;
    }

    @Test
    @DisplayName("an undeclared defaulted field is not written: no default is invented")
    void undeclaredDefaultsStayUndeclared() {
        String written = roundTrip(MINIMAL);

        assertThat(written)
                .doesNotContain(
                        "routing-priority", "max-concurrent-tasks", "schema-version", "performer", "description");
    }

    @Test
    @DisplayName("number-looking strings stay strings; integers stay typed; the performer stays its token")
    void scalarTypesSurvive() {
        String source = MINIMAL + """
                schema-version: "1.0"
                routing-priority: 50
                max-concurrent-tasks: 3
                performer: agent
                directive-references:
                  - code: "003"
                    name: Decision Documentation
                    rationale: Record decisions.
                """;

        String written = roundTrip(source);

        Map<?, ?> document = (Map<?, ?>) Yaml.load(written);
        assertThat(document.get("schema-version")).isEqualTo("1.0");
        assertThat(document.get("routing-priority")).isInstanceOf(Number.class);
        assertThat(document.get("performer")).isEqualTo("agent");
        assertThat(((Map<?, ?>) ((List<?>) document.get("directive-references")).get(0)).get("code"))
                .isEqualTo("003");
    }

    @Test
    @DisplayName("a folded block that ends in a newline keeps it")
    void trailingNewlinesSurvive() {
        String source = """
                profile-id: p
                name: P
                role: implementer
                purpose: >
                  Builds
                  things.
                specialization:
                  primary-focus: Building.
                """;

        roundTrip(source);

        assertThat(read(source).purpose()).isEqualTo("Builds things.\n");
    }

    /** Every property the schema declares, at every depth, populated. */
    private static final String FULL = """
            profile-id: p
            name: P
            description: D
            schema-version: "2"
            role: implementer
            alt-roles: [reviewer]
            avatar-image: a.png
            capabilities: [c1, c2, c1]
            routing-priority: 100
            max-concurrent-tasks: 7
            model: m
            effort: high
            purpose: Builds things.
            specialization:
              primary-focus: Building.
              secondary-awareness: s
              avoidance-boundary: a
              success-definition: d
            collaboration:
              handoff-to: [reviewer]
              handoff-from: [planner]
              works-with: [architect]
              output-artifacts: [code]
              operating-procedures: [tdd]
              canonical-verbs: [build]
            mode-defaults:
              - mode: m1
                description: d1
                use-case: u1
            initialization-declaration: I
            performer: human
            specialization-context:
              languages: [java]
              frameworks: [spring]
              file-patterns: ["*.java"]
              domain-keywords: [kw]
              writing-style: [terse]
              complexity-preference: [low]
            applies_to_languages: [java]
            directive-references:
              - code: "003"
                name: Decision Documentation
                rationale: r
            tactic-references:
              - id: tdd
                rationale: r
            toolguide-references:
              - id: git
                rationale: r
            styleguide-references:
              - id: java
                rationale: r
            self-review-protocol:
              steps:
                - name: s1
                  command: ./gradlew check
                  gate: green
                - name: s2
                  gate: reviewed
            excluding: [capabilities]
            """;

    @Test
    @DisplayName("every declared field of the schema is carried, nested ones included")
    void everyFieldIsCarried() {
        assertThat(roundTrip(FULL)).isNotBlank();
    }

    @Test
    @DisplayName("the fully populated profile names every top-level property the schema declares")
    void theFullProfileCoversTheSchema() {
        Map<?, ?> full = (Map<?, ?>) Yaml.load(FULL);

        assertThat(full.keySet().stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrderElementsOf(PackSchemas.bundled().declaredProperties("agent-profile"));
    }

    @Test
    @DisplayName("an integer beyond the domain's range is refused, never wrapped")
    void outOfRangeIntegersAreRefused() {
        AgentProfileDso dso = read(MINIMAL + "max-concurrent-tasks: 99999999999999999999\n");

        assertThat(dso.maxConcurrentTasks()).contains(Long.MAX_VALUE);
        assertThatThrownBy(() -> AgentProfileBinding.INSTANCE.toEntity(new PackIdentifier("p"), dso))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-concurrent-tasks");
        assertThat(read(MINIMAL + "max-concurrent-tasks: 3000000000\n").maxConcurrentTasks())
                .contains(3_000_000_000L);
    }

    @Test
    @DisplayName("an empty declared list or object equals an absent one, and is not written")
    void emptyDeclaredFieldsEqualAbsent() {
        String source = MINIMAL + """
                directive-references: []
                collaboration: {}
                specialization-context:
                  frameworks: []
                self-review-protocol: null
                model: null
                """;

        String written = roundTrip(source);

        assertThat(written).doesNotContain("collaboration", "specialization-context", "self-review-protocol", "model");
    }

    @Test
    @DisplayName("excluding is written as authored in both forms, an empty value list included")
    void excludingKeepsItsForm() {
        roundTrip(MINIMAL + "excluding: [capabilities, alt-roles]\n");
        String map = MINIMAL + """
                excluding:
                  capabilities: [deploy]
                  alt-roles: []
                """;

        roundTrip(map);

        AgentProfile entity = AgentProfileBinding.INSTANCE.toEntity(new PackIdentifier("p"), read(map));
        assertThat(entity.excluding())
                .contains(new Exclusion.FieldValues(Map.of("capabilities", List.of("deploy"), "alt-roles", List.of())));
        assertThat(MEANING.compareTexts("agent-profile", map, MINIMAL + "excluding: {capabilities: [deploy]}\n"))
                .as("inside the open map an empty list is data")
                .isPresent();
    }

    @Test
    @DisplayName("performer round-trips as declared, maps to the entity, and an absent performer stays absent")
    void performerRoundTrips() {
        for (String token : List.of("agent", "human")) {
            String written = roundTrip(MINIMAL + "performer: " + token + "\n");

            assertThat(((Map<?, ?>) Yaml.load(written)).get("performer")).isEqualTo(token);
        }
        AgentProfile human =
                AgentProfileBinding.INSTANCE.toEntity(new PackIdentifier("p"), read(MINIMAL + "performer: human\n"));
        assertThat(human.performer()).contains(Performer.HUMAN);
        assertThat(human.humanPerformed()).isTrue();
        AgentProfileDso silent = read(MINIMAL);
        assertThat(silent.performer()).isEmpty();
        assertThat(AgentProfileBinding.INSTANCE
                        .toEntity(new PackIdentifier("p"), silent)
                        .performer())
                .isEmpty();
        assertThat(roundTrip(MINIMAL)).doesNotContain("performer");
    }

    @Test
    @DisplayName("a performer outside agent and human is unreadable")
    void anUnknownPerformerIsUnreadable() {
        assertThat(YAML.read(AgentProfileBinding.INSTANCE, MINIMAL + "performer: robot\n", "p.agent.yaml"))
                .isInstanceOf(FileRead.Unreadable.class);
    }

    @Test
    @DisplayName("the operator role without performer human is a schema violation at the item, as role or as alt-role")
    void operatorWithoutAHumanIsUnreadableWithAPointer() {
        String head = "profile-id: p\nname: P\npurpose: Do.\nspecialization:\n  primary-focus: F\n";
        for (String human : List.of(
                head + "role: operator\nperformer: human\n",
                head + "role: reviewer\nalt-roles: [operator]\nperformer: human\n")) {
            assertThat(YAML.read(AgentProfileBinding.INSTANCE, human, "p.agent.yaml"))
                    .as(human)
                    .isInstanceOf(FileRead.Read.class);
        }
        record Case(String text, List<String> pointer) {}
        for (Case c : List.of(
                new Case(head + "role: operator\n", List.of("role")),
                new Case(head + "role: operator\nperformer: agent\n", List.of("role")),
                new Case(head + "role: reviewer\nalt-roles: [implementer, operator]\n", List.of("alt-roles", "1")))) {
            FileRead<AgentProfileDso> read = YAML.read(AgentProfileBinding.INSTANCE, c.text(), "p.agent.yaml");

            assertThat(read).as(c.text()).isInstanceOf(FileRead.Unreadable.class);
            var violations = ((FileRead.Unreadable<AgentProfileDso>) read).violations();
            assertThat(violations).as(c.text()).hasSize(1);
            assertThat(violations.getFirst().pointer()).as(c.text()).isEqualTo(c.pointer());
            assertThat(violations.getFirst().keyword()).isEqualTo("not");
            assertThat(violations.getFirst().value()).contains("operator");
        }
    }

    @Test
    @DisplayName("a file that carries the removed sentinel key is unreadable: the schema is closed")
    void theRemovedSentinelKeyIsUnreadable() {
        assertThat(YAML.read(AgentProfileBinding.INSTANCE, MINIMAL, "p.agent.yaml"))
                .isInstanceOf(FileRead.Read.class);
        assertThat(YAML.read(AgentProfileBinding.INSTANCE, MINIMAL + "sentinel: false\n", "p.agent.yaml"))
                .isInstanceOf(FileRead.Unreadable.class);
    }

    @Test
    @DisplayName("a lineage field is refused as unknown: lineage is a graph edge")
    void lineageFieldsAreRefused() {
        for (String field : List.of("specializes_from: implementer-ivan", "enhances: x", "overrides: x")) {
            assertThat(YAML.read(AgentProfileBinding.INSTANCE, MINIMAL + field + "\n", "p.agent.yaml"))
                    .as(field)
                    .isInstanceOf(FileRead.Unreadable.class);
        }
    }

    @Test
    @DisplayName("a nested unknown field is refused")
    void nestedUnknownFieldsAreRefused() {
        String source = MINIMAL.replace("  primary-focus: Building.\n", "  primary-focus: Building.\n  bogus: 1\n");

        assertThat(YAML.read(AgentProfileBinding.INSTANCE, source, "p.agent.yaml"))
                .isInstanceOf(FileRead.Unreadable.class);
    }
}
