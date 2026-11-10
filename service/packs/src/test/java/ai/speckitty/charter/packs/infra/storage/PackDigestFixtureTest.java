package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The algorithm and order guard of the source fingerprint (NFR-001): a small fixed pack in the test resources
 * ({@code digest-baseline/fixture}) holds two artifact kinds, a toolguide with its guide, a mission type with two step
 * prompts, an asset with its sidecar, a template, a mission template, a preset, a graph shard and the pack document.
 * Its digested file list and its fingerprint are frozen here. The pack is not the open-packs checkout, so a pin move
 * never touches these values.
 *
 * <p>If this goes red, the digest moved for every client: a hash or encoding change, a kind or companion reordered,
 * or a binding inserted mid-order (append, never insert). Moving it is a decision, not a re-measure: change the
 * values only with that decision, and say so in the commit. To read the new values, print
 * {@code PackFileSet.digested(fixture, "digest-fixture")} and
 * {@code FileSystemPackSources.digestOfPack(fixture, "digest-fixture")}. The values were captured from the digest
 * code as shipped when this guard was added, before any change to it.
 */
@DisplayName("A fixed pack keeps its fingerprint and its digested file order (NFR-001)")
class PackDigestFixtureTest {

    private static final String PACK_NAME = "digest-fixture";

    private static final List<String> DIGESTED = List.of(
            "pack.yaml",
            "tactics/alpha.tactic.yaml",
            "tactics/beta.tactic.yaml",
            "directives/gamma.directive.yaml",
            "toolguides/tool.toolguide.yaml",
            "toolguides/tool.guide.md",
            "assets/data.txt.asset.yaml",
            "missions/m/m.mission-type.yaml",
            "missions/m/steps/first.prompt.md",
            "missions/m/steps/second.prompt.md",
            "tactic.graph.yaml",
            "assets/data.txt",
            "missions/m/templates/note-template.md",
            "templates/writing/meeting.md",
            "presets/default.yaml",
            "pack.md");

    private static final String FINGERPRINT = "sha256:526a4ffab6f539ac0c41e2e067dcf774d14a6f0006cbb99a08b533a0d0201c06";

    @Test
    void theFixedPackKeepsItsDigestedFilesAndItsFingerprint() throws URISyntaxException {
        Path fixture = Path.of(PackDigestFixtureTest.class
                        .getResource("/digest-baseline/fixture/pack.yaml")
                        .toURI())
                .getParent();

        assertThat(PackFileSet.digested(fixture, PACK_NAME)).containsExactlyElementsOf(DIGESTED);
        assertThat(FileSystemPackSources.digestOfPack(fixture, PACK_NAME)).isEqualTo(FINGERPRINT);
    }
}
