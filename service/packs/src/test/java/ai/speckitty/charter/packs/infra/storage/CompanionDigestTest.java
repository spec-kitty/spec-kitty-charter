package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The source fingerprint covers companion files (ADR-0039): a skill's body is digested right after the skill, under
 * the same rule the load applies, and a pack without companions digests exactly what it did before.
 */
@DisplayName("The source fingerprint digests companion files with their artifact (#192)")
class CompanionDigestTest {

    private static final String PACK_YAML = """
            pack_id: 01M5YVECC0MPAN10ND1GEST00A
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
    @Tag("open-packs")
    @DisplayName(
            "binding skills adds nothing to the digest of a pack with no skill; each Markdown file sits where the rule puts it")
    void packsWithoutCompanionsAreUnchanged() throws IOException {
        // The checkout's qa pack holds many kinds (agent profiles, directives, a glossary pack, step contracts,
        // tactics, styleguides, ...) and Markdown beside them, and no skill. The assertions are about roles, not
        // counts or positions: PackFileSet digests the YAML inputs (each bound file followed by its companions), then
        // the asset files, then the templates, then the pack-level tail (presets, pack.md, manifest, policy).
        OpenPacksCorpus.assertPinned();
        Path qa = OpenPacksCorpus.packsDirectory().resolve("qa");
        Set<ArtifactKind> withoutSkills = EnumSet.copyOf(KindBindings.kinds());
        withoutSkills.remove(ArtifactKind.SKILL);

        List<String> digested = PackFileSet.digested(qa);
        List<String> assetFiles = PackFileSet.assetFiles(qa);
        List<String> payloads = new ArrayList<>(assetFiles);
        payloads.addAll(PackFileSet.templates(qa));
        assertThat(payloads)
                .as("the qa pack's asset files and templates, which the digest ends with")
                .isNotEmpty();
        assertThat(assetFiles).noneSatisfy(file -> assertThat(file).endsWith(".md"));

        assertThat(digested)
                .as("the asset files, then the templates, in one run")
                .containsSequence(payloads);
        int payloadStart = digested.indexOf(payloads.get(0));
        List<String> tail = digested.subList(payloadStart + payloads.size(), digested.size());
        assertThat(tail)
                .as("after the payloads only the pack-level tail: presets, the pack document, the manifest, the policy")
                .allSatisfy(file ->
                        assertThat(file).matches("presets/[^/]+\\.yaml|pack\\.md|pack-manifest\\.yaml|policy\\.yaml"));
        List<String> presetsOnDisk = OpenPacksCorpus.filesEndingWith(qa.resolve("presets"), ".yaml").stream()
                .map(file -> "presets/" + file)
                .toList();
        assertThat(presetsOnDisk).as("presets in the qa pack").isNotEmpty();
        assertThat(tail).as("every preset on disk is in the tail").containsAll(presetsOnDisk);

        List<String> beforePayloads = digested.subList(0, payloadStart);
        List<String> markdown =
                beforePayloads.stream().filter(file -> !file.endsWith(".yaml")).toList();
        assertThat(markdown)
                .as("every non-YAML file before the payloads is a toolguide guide or a mission step prompt")
                .isNotEmpty()
                .allSatisfy(file -> assertThat(file)
                        .matches("toolguides/.*\\.guide\\.md|missions/[^/]+/steps/[^/]+\\.prompt\\.md"));

        List<String> guides =
                markdown.stream().filter(file -> file.endsWith(".guide.md")).toList();
        assertThat(guides).as("toolguide guides in the qa pack").isNotEmpty().allSatisfy(guide -> {
            String toolguide = beforePayloads.get(beforePayloads.indexOf(guide) - 1);
            assertThat(toolguide).endsWith(".toolguide.yaml");
            // The guide is the one the toolguide before it names, not merely some guide: read its id and guide_path
            // (the file stem is no proxy, two qa toolguides' ids differ from their stems).
            Map<?, ?> declared = yaml(qa, toolguide);
            assertThat(guide)
                    .as("the guide after %s", toolguide)
                    .isEqualTo("toolguides/" + declared.get("id") + ".guide.md")
                    .isEqualTo(toolguide.substring(0, toolguide.lastIndexOf('/') + 1) + declared.get("guide_path"));
        });

        List<String> prompts =
                markdown.stream().filter(file -> file.endsWith(".prompt.md")).toList();
        List<String> missionTypes = beforePayloads.stream()
                .filter(file -> file.endsWith(".mission-type.yaml"))
                .toList();
        assertThat(missionTypes).as("mission types in the qa pack").isNotEmpty();
        List<String> declaredPrompts = new ArrayList<>();
        for (String type : missionTypes) {
            // The step prompts follow their mission-type file, in the order the file declares its steps.
            String missionDir = type.substring(0, type.lastIndexOf('/') + 1);
            Map<?, ?> declared = yaml(qa, type);
            List<String> expected = new ArrayList<>();
            for (String key : List.of("steps", "on_demand_steps")) {
                if (declared.get(key) instanceof List<?> steps) {
                    for (Object step : steps) {
                        if (step instanceof Map<?, ?> fields && fields.get("prompt_path") != null) {
                            expected.add(missionDir + fields.get("prompt_path"));
                        }
                    }
                }
            }
            int at = beforePayloads.indexOf(type);
            assertThat(beforePayloads.subList(at + 1, at + 1 + expected.size()))
                    .as("the step prompts after %s, in its declared step order", type)
                    .containsExactlyElementsOf(expected);
            declaredPrompts.addAll(expected);
        }
        assertThat(prompts)
                .as("mission step prompts in the qa pack: exactly the declared ones, none stray")
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(declaredPrompts);

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
