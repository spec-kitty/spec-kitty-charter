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
    @DisplayName("binding skills adds nothing to the digest of a pack with no skill, and no Markdown is digested")
    void packsWithoutCompanionsAreUnchanged() throws IOException {
        // The vendored qa pack holds many kinds (agent profiles, directives, a glossary pack, step contracts, tactics,
        // styleguides, ...) and Markdown beside them, and no skill.
        Path qa = Path.of(System.getProperty("charter.repositoryRoot", "../..")).resolve("packs/qa");
        Set<ArtifactKind> withoutSkills = EnumSet.copyOf(KindBindings.kinds());
        withoutSkills.remove(ArtifactKind.SKILL);

        List<String> digested = PackFileSet.digested(qa);

        assertThat(digested)
                .hasSizeGreaterThan(10)
                .allSatisfy(file -> assertThat(file).endsWith(".yaml"));
        assertThat(digested).doesNotContain("README.md", "PROVENANCE.md");
        assertThat(digested)
                .as("binding the skill kind adds nothing to a pack that has no skill")
                .containsExactlyElementsOf(PackFileSet.digested(qa, withoutSkills));
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
