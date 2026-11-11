package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The source fingerprint covers companion files (ADR-0039): a skill's body is digested right after the skill, under
 * the same rule the load applies, and a pack without companions digests exactly what it did before.
 */
@DisplayName("The source fingerprint digests companion files with their artifact (#192)")
class CompanionDigestTest {

    /** The built-in example's pack-level tail, digested last (ADR-0042 decision 9). */
    private static final List<String> TAIL =
            List.of("presets/default.yaml", "presets/minimal.yaml", "pack.md", "pack-manifest.yaml", "policy.yaml");

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
    @DisplayName("binding skills adds nothing to the digest of a pack with no skill; only companions are Markdown")
    void packsWithoutCompanionsAreUnchanged() throws IOException {
        // The built-in example (ADR-0070) holds every kind and Markdown beside them; a copy without its one skill is a
        // pack with many kinds and no skill.
        Path builtIn = builtInWithoutSkills();
        Set<ArtifactKind> withoutSkills = withoutSkills();

        List<String> digested = PackFileSet.digested(builtIn);
        // #193 digests the pack's asset files and then its templates; #194 makes the mission type's templates
        // templates. The pack-level tail follows them (ADR-0042 decision 9): the presets, pack.md, the manifest and the
        // policy.
        List<String> assetFiles = PackFileSet.assetFiles(builtIn);
        List<String> payloads = new ArrayList<>(assetFiles);
        payloads.addAll(PackFileSet.templates(builtIn));
        payloads.addAll(TAIL);
        // #194: every file before them is YAML, except the companions: the toolguide's guide, right after its
        // toolguide, the software-dev mission type's step prompts and guidelines, right after the mission-type file in
        // step order, and the step enhancement's prompt fragment, right after the enhancement.
        List<String> beforeAssets = digested.subList(0, digested.size() - payloads.size());
        List<String> guides =
                beforeAssets.stream().filter(file -> file.endsWith(".guide.md")).toList();
        int type = beforeAssets.indexOf("missions/software-dev/software-dev.mission-type.yaml");
        int fragment = beforeAssets.indexOf("missions/software-dev/enhancements/plan-risk-notes.prompt-fragment.md");

        assertThat(beforeAssets.stream().filter(file -> !file.endsWith(".yaml")))
                .hasSize(21)
                .allSatisfy(file -> assertThat(file)
                        .matches("toolguides/.*\\.guide\\.md|missions/software-dev/steps/.*\\.md"
                                + "|missions/software-dev/enhancements/.*\\.prompt-fragment\\.md"));
        assertThat(beforeAssets.subList(type + 1, type + 20))
                .allSatisfy(file -> assertThat(file).startsWith("missions/software-dev/steps/"))
                .startsWith(
                        "missions/software-dev/steps/discovery.prompt.md",
                        "missions/software-dev/steps/specify.prompt.md",
                        "missions/software-dev/steps/specify.guidelines.md");
        assertThat(beforeAssets.get(fragment - 1))
                .isEqualTo("missions/software-dev/enhancements/plan-risk-notes.step-enhancement.yaml");

        assertThat(assetFiles).hasSize(4).noneSatisfy(file -> assertThat(file).endsWith(".md"));
        assertThat(digested).hasSizeGreaterThan(21).endsWith(payloads.toArray(String[]::new));
        assertThat(guides).hasSize(1).allSatisfy(guide -> {
            String toolguide = beforeAssets.get(beforeAssets.indexOf(guide) - 1);
            assertThat(toolguide).endsWith(".toolguide.yaml");
            // The guide is the one the toolguide before it names, not merely some guide: read its id and guide_path.
            Map<?, ?> declared = yaml(builtIn, toolguide);
            assertThat(guide)
                    .as("the guide after %s", toolguide)
                    .isEqualTo("toolguides/" + declared.get("id") + ".guide.md")
                    .isEqualTo(toolguide.substring(0, toolguide.lastIndexOf('/') + 1) + declared.get("guide_path"));
        });
        assertThat(digested).doesNotContain("README.md", "PROVENANCE.md");
        assertThat(digested)
                .as("binding the skill kind adds nothing to a pack that has no skill")
                .containsExactlyElementsOf(PackFileSet.digested(builtIn, withoutSkills));
    }

    @Test
    @DisplayName("failing control: with its skill, the built-in example digests more than without the skill kind")
    void aPackWithASkillDigestsItsSkillAndBody() {
        Path builtIn = ExamplePacks.pack(ExamplePacks.BUILT_IN);

        List<String> digested = PackFileSet.digested(builtIn);

        assertThat(digested).isNotEqualTo(PackFileSet.digested(builtIn, withoutSkills()));
        assertThat(digested)
                .containsSubsequence("skills/review-checklist.skill.yaml", "skills/review-checklist.skill.md");
    }

    private static Set<ArtifactKind> withoutSkills() {
        Set<ArtifactKind> kinds = EnumSet.copyOf(KindBindings.kinds());
        kinds.remove(ArtifactKind.SKILL);
        return kinds;
    }

    private Path builtInWithoutSkills() throws IOException {
        Path copy = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host.resolve("copy"));
        try (Stream<Path> walk = Files.walk(copy.resolve("skills"))) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
        return copy;
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
