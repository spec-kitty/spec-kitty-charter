package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.Skill;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("A skill round-trips through the YAML format and the store, with its body (#192)")
class SkillYamlTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5YVECSKILLPACKFIXTURE0A");

    /** Every declared field of the prompt form, each optional one written with a non-default value. */
    private static final String PROMPT = """
            schema_version: "1.0"
            id: land-pr
            title: Land a PR
            description: "Use when a ready-for-review pull request needs a maintainer — rebases it."
            triggers: ["land this PR", "landing pass", "land this PR"]
            form: prompt
            body_path: land-pr.skill.md
            parameters:
              - name: pr
                required: true
                description: The PR number or URL
              - name: base
                required: false
                default: main
              - name: note
            invocation:
              user_invocable: false
              model_invocable: true
              side_effects: [git-push, gh-write]
            tools: [Bash, Read]
            version: 1.0.0
            maintainers: ["@spec-kitty/core"]
            overrides: land-pr-legacy
            """;

    private static final String WRAPPER = """
            schema_version: "1.0"
            id: status
            title: Status
            description: Shows the status.
            form: wrapper
            expands_to:
              target: "cli:spec-kitty agent tasks status"
              args: --json
            enhances: status-base
            """;

    /** Real skills declare side effects and no model_invocable; that absence must survive. */
    private static final String SIDE_EFFECTS_ONLY = """
            schema_version: "1.0"
            id: issue-triage
            title: Triage GitHub issues
            description: Use to triage a batch of issues.
            form: prompt
            body_path: issue-triage.skill.md
            invocation:
              user_invocable: true
              side_effects: [gh-write]
            """;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @Test
    @DisplayName("every field of a prompt skill survives read, domain, write and read again")
    void aPromptSkillRoundTrips() {
        SkillDso dso = read(PROMPT).withBody(Optional.of("Goal: land it.\r\nNo final newline"));
        SkillDso back = SkillBinding.INSTANCE.toDso(SkillBinding.INSTANCE.toEntity(PACK, dso));
        String written = strategy.write(SkillBinding.INSTANCE, back);

        assertThat(back).as("the body survives the domain, byte for byte").isEqualTo(dso);
        assertThat(MeaningComparator.adr0036().compareTexts("skill", PROMPT, written))
                .isEmpty();
        assertThat(written).as("the format never writes the body").doesNotContain("Goal: land it.");
    }

    @Test
    @DisplayName("a wrapper skill round-trips and names no companion")
    void aWrapperSkillRoundTrips() {
        SkillDso dso = read(WRAPPER);

        assertThat(MeaningComparator.adr0036().compareTexts("skill", WRAPPER, throughTheDomain(dso)))
                .isEmpty();
        assertThat(SkillBinding.INSTANCE.companions(dso)).isEmpty();
    }

    @Test
    @DisplayName("side effects do not force model_invocable: an absent flag is not written")
    void nothingIsForced() {
        Map<String, Object> invocation = mapping(throughTheDomain(read(SIDE_EFFECTS_ONLY)), "invocation");

        assertThat(invocation).containsOnlyKeys("user_invocable", "side_effects");
        assertThat(map(Yaml.load(throughTheDomain(read(SIDE_EFFECTS_ONLY)))))
                .as("no tools, version or other default is written")
                .doesNotContainKeys("tools", "version", "maintainers", "triggers", "parameters");
    }

    @Test
    @DisplayName("an invocation with no field, or only an empty list, reads as none and is not written back")
    void anEmptyInvocationIsNone() {
        for (String invocation : List.of("invocation: {}\n", "invocation:\n  side_effects: []\n")) {
            String source = WRAPPER + invocation;

            assertThat(read(source).invocation()).as(invocation).isEmpty();
            assertThat(map(Yaml.load(throughTheDomain(read(source))))).doesNotContainKey("invocation");
        }
        // ADR-0036 decision 2 makes both mean "no invocation". The comparator agrees for {} and, applying the rule one
        // level only, not yet for {side_effects: []}; no pinned file has either shape.
        String empty = WRAPPER + "invocation: {}\n";
        assertThat(MeaningComparator.adr0036().compareTexts("skill", empty, throughTheDomain(read(empty))))
                .isEmpty();
    }

    @Test
    @DisplayName("booleans stay booleans and a version that looks like a number stays a string")
    void typesAreKept() {
        Map<String, Object> written = map(Yaml.load(throughTheDomain(read(PROMPT))));

        assertThat(written.get("version")).isEqualTo("1.0.0");
        assertThat(written.get("schema_version")).isEqualTo("1.0");
        assertThat(mapping(throughTheDomain(read(PROMPT)), "invocation").get("user_invocable"))
                .isEqualTo(false);
    }

    @Test
    @DisplayName("the binding declares the body as its one companion and fills it in")
    void theBodyIsTheCompanion() {
        SkillDso dso = read(PROMPT);

        assertThat(SkillBinding.INSTANCE.hasCompanions()).isTrue();
        assertThat(SkillBinding.INSTANCE.companions(dso))
                .containsExactly(new KindBinding.Companion("land-pr.skill.md", Optional.empty()));
        SkillDso filled = SkillBinding.INSTANCE.withCompanions(
                dso, Map.of("land-pr.skill.md", new CompanionContent.Text("body\n")));
        assertThat(filled.body()).contains("body\n");
        assertThat(SkillBinding.INSTANCE.companions(filled))
                .containsExactly(new KindBinding.Companion(
                        "land-pr.skill.md", Optional.of(new CompanionContent.Text("body\n"))));
    }

    @Test
    @DisplayName("a pack loads a skill with its body; a missing body is a companion violation naming the path")
    void aPackLoadsSkillsWithTheirBodies(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack.resolve("skills/land-pr.skill.yaml"), PROMPT);
        write(pack.resolve("skills/land-pr.skill.md"), "Goal: land it.\r\nNo final newline");
        write(pack.resolve("skills/issue-triage.skill.yaml"), SIDE_EFFECTS_ONLY);

        Pack loaded = service(pack).load(PACK);

        assertThat(loaded.artifacts(ArtifactKind.SKILL, Skill.class))
                .singleElement()
                .satisfies(skill -> assertThat(skill.body()).contains("Goal: land it.\r\nNo final newline"));
        assertThat(loaded.violations())
                .extracting(v -> v.file() + " | " + v.kind() + " | " + v.reason())
                .containsExactly("skills/issue-triage.skill.yaml | companion | issue-triage.skill.md: missing");
    }

    @Test
    @DisplayName("a new skill is created with its body where a load finds both; a moved body leaves no orphan")
    void aNewSkillIsWrittenAsAPair(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        DomainRepositoryService service = service(pack);
        SkillDso dso = read(PROMPT).withBody(Optional.of("first\n"));

        service.saveArtifact(SkillBinding.INSTANCE.toEntity(PACK, dso));

        assertThat(Files.readString(pack.resolve("land-pr.skill.md"))).isEqualTo("first\n");
        assertThat(service.load(PACK).artifacts(ArtifactKind.SKILL, Skill.class))
                .singleElement()
                .satisfies(skill -> assertThat(skill.body()).contains("first\n"));

        SkillDso moved = read(PROMPT.replace("body_path: land-pr.skill.md", "body_path: prompts/land-pr.md"))
                .withBody(Optional.of("second\n"));
        service.saveArtifact(SkillBinding.INSTANCE.toEntity(PACK, moved));

        assertThat(pack.resolve("land-pr.skill.md")).doesNotExist();
        assertThat(Files.readString(pack.resolve("prompts/land-pr.md"))).isEqualTo("second\n");
    }

    private SkillDso read(String text) {
        FileRead<SkillDso> read = strategy.read(SkillBinding.INSTANCE, text, "probe.skill.yaml");
        assertThat(read).as("the planted file reads").isInstanceOf(FileRead.Read.class);
        return ((FileRead.Read<SkillDso>) read).value();
    }

    private String throughTheDomain(SkillDso dso) {
        SkillDso withBody = dso.bodyPath().isPresent() && dso.body().isEmpty() ? dso.withBody(Optional.of("b")) : dso;
        return strategy.write(
                SkillBinding.INSTANCE, SkillBinding.INSTANCE.toDso(SkillBinding.INSTANCE.toEntity(PACK, withBody)));
    }

    private static Map<String, Object> mapping(String written, String key) {
        return map(map(Yaml.load(written)).get(key));
    }

    @SuppressWarnings("unchecked") // a YAML mapping read back from text this test wrote
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static Path pack(Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("skill-pack"));
        write(pack.resolve("pack.yaml"), """
                pack_id: 01M5YVECSKILLPACKFIXTURE0A
                pack_version: 1.0.0
                name: skill-pack
                pack_level: org
                """);
        return pack;
    }

    private static DomainRepositoryService service(Path pack) {
        PackLocation location = new PackLocation("skill-pack", PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
