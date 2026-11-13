package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.artifacts.application.ReferenceReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The adapter maps each reference of the pack read one for one: the written target, the relation, the URN, the holding
 * pack's configured id, descriptor id and name, and the kind of a target the pack read links by its one gate
 * ({@link ArtifactReadService#listed()}); a kind outside it carries none.
 */
class PacksReferencesTest {

    private static final PackUnderResolution CORE = new PackUnderResolution(
            new ConfiguredPack(new PackIdentifier("core"), PackLevel.BUILT_IN),
            new PackDescriptor(
                    "01M4P01D5TWB5FKZGNNQG3943R",
                    "1.0.0",
                    Optional.empty(),
                    "core-pack",
                    Optional.empty(),
                    Optional.empty()),
            List.of(),
            List.of());

    @Test
    void aHeldReferenceOfAListedKindKeepsEveryMemberAndItsKind() {
        Urn tactic = new Urn(NodeKind.TACTIC, "T");

        ReferenceReads.Reference mapped = PacksReferences.referenceOf(new ArtifactReadService.Reference(
                PackQualifiedReference.parse("core-pack::tactic:T"), RelationType.REQUIRES, Optional.of(CORE), tactic));

        assertThat(mapped)
                .isEqualTo(new ReferenceReads.Reference(
                        PackQualifiedReference.parse("core-pack::tactic:T"),
                        RelationType.REQUIRES,
                        tactic,
                        Optional.of(new ReferenceReads.Holder(
                                new PackIdentifier("core"), "01M4P01D5TWB5FKZGNNQG3943R", "core-pack")),
                        Optional.of(ArtifactKind.TACTIC)));
    }

    @Test
    void aTargetOfAKindTheReadsDoNotListCarriesNoKindAndAnUnheldOneNoHolder() {
        Urn template = new Urn(NodeKind.TEMPLATE, "notes");

        ReferenceReads.Reference mapped = PacksReferences.referenceOf(new ArtifactReadService.Reference(
                PackQualifiedReference.parse("template:notes"), RelationType.SUGGESTS, Optional.empty(), template));

        assertThat(mapped.listed()).isEmpty();
        assertThat(mapped.holder()).isEmpty();
        assertThat(mapped.urn()).isEqualTo(template);
        assertThat(mapped.relation()).isEqualTo(RelationType.SUGGESTS);
    }

    @Test
    void theKindsOfTheListAreCarriedTheAssetSkillAndMissionKindsIncludedAndNoOther() {
        for (NodeKind listed : new NodeKind[] {
            NodeKind.DIRECTIVE, NodeKind.AGENT_PROFILE, NodeKind.ASSET, NodeKind.SKILL, NodeKind.MISSION_TYPE,
            NodeKind.MISSION_STEP_CONTRACT
        }) {
            assertThat(listedOf(new Urn(listed, "x"))).as("%s", listed).isEqualTo(listed.artifactKind());
        }
        for (NodeKind unlisted : new NodeKind[] {NodeKind.TEMPLATE, NodeKind.GUARD, NodeKind.LIFECYCLE}) {
            assertThat(listedOf(new Urn(unlisted, "x"))).as("%s", unlisted).isEmpty();
        }
    }

    private static Optional<ArtifactKind> listedOf(Urn urn) {
        return PacksReferences.referenceOf(new ArtifactReadService.Reference(
                        PackQualifiedReference.parse(urn.toString()), RelationType.REQUIRES, Optional.empty(), urn))
                .listed();
    }
}
