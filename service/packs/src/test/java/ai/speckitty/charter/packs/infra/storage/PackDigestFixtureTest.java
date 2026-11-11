package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The algorithm and order guard of the source fingerprint (NFR-001): a small fixed pack in the test resources
 * ({@code digest-baseline/fixture}) holds one file of every kind {@code KindBindings} binds (with the Markdown
 * companions a toolguide, a skill, a mission type and a step enhancement name), an asset with its sidecar, a mission
 * template, a template, a preset, a graph shard and the pack document, the pack manifest and the policy. Its digested
 * file list and its fingerprint are frozen here. The pack is not the open-packs checkout, so a pin move never touches
 * these values. A newly bound kind fails the coverage check below until the fixture holds a file of it.
 *
 * <p>If this goes red, the digest moved for every client: a hash or encoding change, a kind or companion reordered,
 * or a binding inserted mid-order (append, never insert). Moving it is a decision, not a re-measure: change the
 * values only with that decision, and say so in the commit.
 *
 * <p>Recapturing is legitimate only for one of these triggers, and the commit names it: a new kind is bound (add its
 * fixture file first), a new companion class is digested, a fixture file is edited on purpose, or the digest order
 * is changed by ruling. The fixture files must stay byte-stable otherwise: the fingerprint hashes their bytes, so do
 * not reformat them, and {@code .gitattributes} keeps line-ending conversion off them. To read the new values, print
 * {@code PackFileSet.digested(fixture, "digest-fixture")} and
 * {@code FileSystemPackSources.digestOfPack(fixture, "digest-fixture")}, and replace both constants below in one
 * commit. The values were captured from the digest code as shipped when the fixture covered every bound kind.
 */
@DisplayName("A fixed pack keeps its fingerprint and its digested file order (NFR-001)")
class PackDigestFixtureTest {

    private static final String PACK_NAME = "digest-fixture";

    private static final List<String> DIGESTED = List.of(
            "pack.yaml",
            "tactics/alpha.tactic.yaml",
            "tactics/beta.tactic.yaml",
            "directives/gamma.directive.yaml",
            "styleguides/delta.styleguide.yaml",
            "toolguides/tool.toolguide.yaml",
            "toolguides/tool.guide.md",
            "paradigms/epsilon.paradigm.yaml",
            "agent_profiles/zeta.agent.yaml",
            "mission_step_contracts/m-first.step-contract.yaml",
            "glossary_packs/eta.glossary-pack.yaml",
            "skills/theta.skill.yaml",
            "skills/theta.skill.md",
            "assets/data.txt.asset.yaml",
            "procedures/iota.procedure.yaml",
            "missions/m/m.mission-type.yaml",
            "missions/m/steps/first.prompt.md",
            "missions/m/steps/second.prompt.md",
            "guards/subtasks-ready.guard.yaml",
            "lifecycles/kappa.lifecycle.yaml",
            "missions/m/enhancements/first-lambda.step-enhancement.yaml",
            "missions/m/enhancements/first-lambda.prompt-fragment.md",
            "tactic.graph.yaml",
            "assets/data.txt",
            "missions/m/templates/note-template.md",
            "templates/writing/meeting.md",
            "presets/default.yaml",
            "pack.md",
            "pack-manifest.yaml",
            "policy.yaml");

    private static final String FINGERPRINT = "sha256:c533b50ea4e0d21e198158e6cf009c8330c97882ea54ebac41936284faeb7832";

    @Test
    void theFixedPackKeepsItsDigestedFilesAndItsFingerprint() throws URISyntaxException {
        Path fixture = Path.of(PackDigestFixtureTest.class
                        .getResource("/digest-baseline/fixture/pack.yaml")
                        .toURI())
                .getParent();

        assertThat(PackFileSet.digested(fixture, PACK_NAME)).containsExactlyElementsOf(DIGESTED);
        assertThat(FileSystemPackSources.digestOfPack(fixture, PACK_NAME)).isEqualTo(FINGERPRINT);
        // Every bound kind contributes at least one file the digest lists, so a kind bound later cannot go unfrozen.
        Set<String> kindless = new HashSet<>(PackFileSet.digested(fixture, Set.<ArtifactKind>of()));
        for (ArtifactKind kind : KindBindings.kinds()) {
            assertThat(PackFileSet.digested(fixture, Set.of(kind)))
                    .as("%s: the fixture holds a file of every bound kind", kind)
                    .anyMatch(file -> !kindless.contains(file));
        }
    }
}
