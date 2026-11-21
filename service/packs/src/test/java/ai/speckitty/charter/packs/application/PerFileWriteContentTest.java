package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.MutabilityCheck;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A per-file write or removal never stores a pack that the registration's full read would refuse (#605, operator
 * ruling of 2026-11-18). The commit store judges the copy it would swap in with the one reader, so the rules a per-file
 * path does not state itself still hold. Each case writes through the real service and judges the stored pack with the
 * one reader, beside a control on the same fixture.
 */
class PerFileWriteContentTest {

    private static final FileSystemPackInspector READER = new FileSystemPackInspector();
    private static final PackIdentifier ORG = new PackIdentifier(ExamplePacks.ORG);
    private static final String ENHANCEMENT = "step_enhancement:spdd-plan";
    private static final String STEP = "built-in::action:software-dev/plan";
    private static final String ENHANCEMENT_FILE = "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml";
    private static final String ENHANCES_EDGE =
            "- source: " + ENHANCEMENT + "\n  target: " + STEP + "\n  relation: enhances\n";
    private static final String PARADIGM = "paradigm:structured-prompt-driven-development";
    private static final String DIRECTIVE = "directive:DIRECTIVE_038";
    private static final String REQUIRES_EDGE =
            "- source: " + PARADIGM + "\n  target: " + DIRECTIVE + "\n  relation: requires\n";

    /**
     * The org-base example pack with two of its authored edges moved from {@code drg/fragment.yaml} to the registered
     * shard, where a relationship removal can reach them: the {@code spdd-plan} step enhancement's one {@code enhances}
     * edge, and a {@code requires} edge no rule needs.
     */
    private static StagedPack orgBaseWithTwoEdgesRegistered(Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host.resolve("upload"));
        Path fragment = pack.resolve("drg/fragment.yaml");
        String text = Files.readString(fragment);
        assertThat(text)
                .as("the fixture holds both edges in its authored shard")
                .contains(ENHANCES_EDGE)
                .contains(REQUIRES_EDGE);
        Files.writeString(fragment, text.replace(ENHANCES_EDGE, "").replace(REQUIRES_EDGE, ""));
        Files.writeString(pack.resolve(RelationshipShard.SHARD_PATH), """
                schema_version: '1.0'
                generated_at: STATIC
                generated_by: charter-registration
                nodes: []
                edges:
                """ + ENHANCES_EDGE + REQUIRES_EDGE);
        return new StagedPack(pack);
    }

    private static Rig registered(Path host) throws IOException {
        Rig rig = new Rig(host);
        ExamplePacks.copyOf(ExamplePacks.BUILT_IN, rig.packs);
        rig.register(ExamplePacks.ORG, orgBaseWithTwoEdgesRegistered(host), Preconditions.none());
        assertThat(violationsOfTheStoredPack(rig))
                .as("the registered pack reads clean")
                .isEmpty();
        return rig;
    }

    private static List<Reason> violationsOfTheStoredPack(Rig rig) {
        return READER.read(new PackRoot(
                        ExamplePacks.ORG,
                        Optional.empty(),
                        rig.packs.resolve(ExamplePacks.ORG).toString()))
                .rationale()
                .violations();
    }

    private static PackRevision remove(Rig rig, String from, String to) {
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations), rig.generation);
        MutabilityCheck gate = new PackMutabilityPolicy(
                new OfferingReadService(rig.repository, rig.repository, rig.repository, read),
                rig.repository,
                rig.repository,
                read);
        return new RelationshipRemovalService(rig.repository, rig.writeStore, gate, rig.impact)
                .remove(ORG, PackQualifiedReference.parse(from), PackQualifiedReference.parse(to), "*");
    }

    @Test
    @DisplayName("removing a step enhancement's one registered enhances edge is refused, and the pack stays as it was")
    void removingAnEnhancementsOnlyEnhancesEdgeIsRefusedAndNothingChanges(@TempDir Path host) throws IOException {
        Rig rig = registered(host);
        Map<String, String> before = rig.filesOf(rig.packs.resolve(ExamplePacks.ORG));
        PackRevision revision = rig.writeStore.currentRevision(ORG);

        CharterRefusal refused = refusal(() -> remove(rig, ENHANCEMENT, STEP));

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo(ENHANCEMENT_FILE);
            assertThat(violation.code()).isEqualTo("prompt_fragment_invalid");
            assertThat(violation.detail()).contains("0 enhances edges, but needs exactly one");
        });
        assertThat(rig.filesOf(rig.packs.resolve(ExamplePacks.ORG)))
                .as("nothing is written")
                .isEqualTo(before);
        assertThat(rig.writeStore.currentRevision(ORG)).isEqualTo(revision);
        assertThat(violationsOfTheStoredPack(rig))
                .as("the store still holds a pack the registration accepts")
                .isEmpty();
    }

    @Test
    @DisplayName("the control: removing a registered edge no rule needs, on the same fixture, goes through")
    void removingAnEdgeNoRuleNeedsGoesThrough(@TempDir Path host) throws IOException {
        Rig rig = registered(host);
        PackRevision revision = rig.writeStore.currentRevision(ORG);

        PackRevision after = remove(rig, PARADIGM, DIRECTIVE);

        assertThat(after).isNotEqualTo(revision).isEqualTo(rig.writeStore.currentRevision(ORG));
        assertThat(rig.writeStore.registeredRelationships(ORG))
                .as("the enhances edge stays registered")
                .singleElement()
                .satisfies(edge -> assertThat(edge.from().toString()).isEqualTo(ENHANCEMENT));
        assertThat(violationsOfTheStoredPack(rig))
                .as("the store holds a pack the registration accepts")
                .isEmpty();
    }
}
