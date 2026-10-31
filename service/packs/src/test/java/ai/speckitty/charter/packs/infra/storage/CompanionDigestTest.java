package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The source fingerprint covers companion files (ADR-0039): a skill's body is digested right after the skill, under
 * the same rule the load applies, and a pack without companions digests exactly what it did before.
 */
@DisplayName("The source fingerprint digests companion files with their artifact (#192)")
class CompanionDigestTest {

    private static final String PACK_YAML = """
            pack_id: 01M5YVECCOMPANIONDIGEST00A
            pack_version: 1.0.0
            name: p
            pack_level: org
            """;

    private static String skill(String id, String bodyPath) {
        return """
                schema_version: "1.0"
                id: %s
                title: T
                description: D
                form: prompt
                body_path: %s
                """.formatted(id, bodyPath);
    }

    @TempDir
    Path host;

    private Path pack() throws IOException {
        Path pack = Files.createDirectories(host.resolve("p"));
        write(pack, "pack.yaml", PACK_YAML);
        write(pack, "tactics/t.tactic.yaml", """
                id: t
                schema_version: '1.0'
                name: N
                steps:
                  - title: S
                """);
        return pack;
    }

    @Test
    @DisplayName("a skill's body is listed right after the skill")
    void theBodyFollowsItsSkill() throws IOException {
        Path pack = pack();
        write(pack, "skills/a.skill.yaml", skill("a", "a.skill.md"));
        write(pack, "skills/a.skill.md", "body\n");

        List<String> digested = PackFileSet.digested(pack);
        assertThat(digested).containsOnlyOnce("skills/a.skill.md");
        assertThat(digested.get(digested.indexOf("skills/a.skill.yaml") + 1)).isEqualTo("skills/a.skill.md");
    }

    @Test
    @DisplayName("changing only a body changes the fingerprint")
    void aBodyChangeMovesTheFingerprint() throws IOException {
        Path pack = pack();
        write(pack, "skills/a.skill.yaml", skill("a", "a.skill.md"));
        write(pack, "skills/a.skill.md", "before\n");
        FileSystemPackSources sources =
                new FileSystemPackSources(() -> List.of(new PackLocation("p", PackLevel.ORG, pack)));
        String before = sources.fingerprint();

        write(pack, "skills/a.skill.md", "after\n");

        assertThat(sources.fingerprint()).isNotEqualTo(before);
    }

    @Test
    @DisplayName("a companion the rule refuses, or that is not text, is not digested, and a shared one only once")
    void refusedCompanionsAreNotDigested() throws IOException {
        Path pack = pack();
        write(pack, "skills/link.skill.yaml", skill("link", "link.md"));
        write(pack, "skills/target.md", "x");
        Files.createSymbolicLink(pack.resolve("skills/link.md"), pack.resolve("skills/target.md"));
        write(pack, "skills/binary.skill.yaml", skill("binary", "binary.md"));
        Files.write(pack.resolve("skills/binary.md"), new byte[] {(byte) 0xC3, (byte) 0x28});
        write(pack, "skills/yaml.skill.yaml", skill("yaml", "../tactics/t.tactic.yaml/."));
        write(pack, "skills/one.skill.yaml", skill("one", "shared.md"));
        write(pack, "skills/two.skill.yaml", skill("two", "shared.md"));
        write(pack, "skills/shared.md", "x");

        List<String> digested = PackFileSet.digested(pack);

        assertThat(digested).doesNotContain("skills/link.md", "skills/target.md", "skills/binary.md");
        assertThat(digested).containsOnlyOnce("skills/shared.md", "tactics/t.tactic.yaml");
    }

    @Test
    @DisplayName("a body that is a template or an asset file is no companion: digested once, as a payload, last")
    void aBodyThatIsAPayloadIsDigestedOnceAsThePayload() throws IOException {
        Path pack = pack();
        write(pack, "skills/t.skill.yaml", skill("t", "../templates/t.md"));
        write(pack, "templates/t.md", "body\n");
        write(pack, "skills/a.skill.yaml", skill("a", "../assets/a.md"));
        write(pack, "assets/a.md", "body\n");

        List<String> digested = PackFileSet.digested(pack);

        assertThat(digested).containsOnlyOnce("templates/t.md", "assets/a.md");
        assertThat(digested).endsWith("skills/a.skill.yaml", "skills/t.skill.yaml", "assets/a.md", "templates/t.md");
    }

    @Test
    @DisplayName("binding skills adds nothing to the digest of a pack with no skill; only companions are Markdown")
    void packsWithoutCompanionsAreUnchanged() throws IOException {
        // The vendored qa pack holds many kinds (agent profiles, directives, a glossary pack, step contracts, tactics,
        // styleguides, ...) and Markdown beside them, and no skill.
        Path qa = Path.of(System.getProperty("charter.repositoryRoot", "../..")).resolve("packs/qa");
        Set<ArtifactKind> withoutSkills = EnumSet.copyOf(KindBindings.kinds());
        withoutSkills.remove(ArtifactKind.SKILL);

        List<String> digested = PackFileSet.digested(qa);
        // #193 digests the pack's asset files (three .py payloads) and then its templates, last; #194 makes the mission
        // type's templates templates.
        List<String> assetFiles = PackFileSet.assetFiles(qa);
        List<String> payloads = new java.util.ArrayList<>(assetFiles);
        payloads.addAll(PackFileSet.templates(qa));
        // #194: every file before them is YAML, except the companions: the three toolguide guides, each right after its
        // toolguide, and the qa mission type's seven step prompts, right after the mission-type file in step order.
        List<String> beforeAssets = digested.subList(0, digested.size() - payloads.size());
        List<String> guides =
                beforeAssets.stream().filter(file -> file.endsWith(".guide.md")).toList();
        int type = beforeAssets.indexOf("missions/qa/qa.mission-type.yaml");

        assertThat(beforeAssets.stream().filter(file -> !file.endsWith(".yaml")))
                .hasSize(10)
                .allSatisfy(file ->
                        assertThat(file).matches("toolguides/.*\\.guide\\.md|missions/qa/steps/.*\\.prompt\\.md"));
        assertThat(beforeAssets.subList(type + 1, type + 8))
                .containsExactly(
                        "missions/qa/steps/discovery.prompt.md",
                        "missions/qa/steps/specify.prompt.md",
                        "missions/qa/steps/plan.prompt.md",
                        "missions/qa/steps/tasks.prompt.md",
                        "missions/qa/steps/implement.prompt.md",
                        "missions/qa/steps/review.prompt.md",
                        "missions/qa/steps/accept.prompt.md");

        assertThat(assetFiles).hasSize(3).noneSatisfy(file -> assertThat(file).endsWith(".md"));
        assertThat(digested).hasSizeGreaterThan(10).endsWith(payloads.toArray(String[]::new));
        assertThat(guides).hasSize(3).allSatisfy(guide -> {
            String toolguide = beforeAssets.get(beforeAssets.indexOf(guide) - 1);
            assertThat(toolguide).endsWith(".toolguide.yaml");
            // The guide is the one the toolguide before it names, not merely some guide: read its id and guide_path
            // (the file stem is no proxy, two qa toolguides' ids differ from their stems).
            Map<?, ?> declared = yaml(qa, toolguide);
            assertThat(guide)
                    .as("the guide after %s", toolguide)
                    .isEqualTo("toolguides/" + declared.get("id") + ".guide.md")
                    .isEqualTo(toolguide.substring(0, toolguide.lastIndexOf('/') + 1) + declared.get("guide_path"));
        });
        assertThat(digested).doesNotContain("README.md", "PROVENANCE.md");
        assertThat(digested)
                .as("binding the skill kind adds nothing to a pack that has no skill")
                .containsExactlyElementsOf(PackFileSet.digested(qa, withoutSkills));
    }

    private static Map<?, ?> yaml(Path pack, String relative) throws IOException {
        Object document = ParsedYaml.parse(Files.readString(pack.resolve(relative)), relative)
                .document();
        assertThat(document).as(relative).isInstanceOf(Map.class);
        return (Map<?, ?>) document;
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
