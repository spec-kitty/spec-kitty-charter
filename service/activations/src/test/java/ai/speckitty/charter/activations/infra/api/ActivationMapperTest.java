package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationConfig;
import ai.speckitty.charter.api.model.ActivationPackList;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.DeactivatedArtifacts;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.OfferingPack;
import ai.speckitty.charter.api.model.PackPin;
import ai.speckitty.charter.api.model.ResolvedDependency;
import ai.speckitty.charter.api.model.SelectedArtifacts;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivationMapperTest {

    private final ActivationMapper mapper = new ActivationMapper("/api/v1/");

    @Test
    void anEmptyAllowListIsNoneOfTheKindAsAnAbsentOneIs() {
        ActivationConfig config = new ActivationConfig(2)
                .selected(new SelectedArtifacts().directive(List.of()).missionType(List.of()));

        ActivationDocument document = mapper.toDocument(config).document();

        assertThat(document.languages()).isEmpty();
        assertThat(document.packs()).isEmpty();
        assertThat(document.selected()).isEmpty();
        assertThat(document)
                .isEqualTo(mapper.toDocument(new ActivationConfig(2)).document());
    }

    @Test
    void everyMemberReachesTheDocument() {
        ActivationConfig config = new ActivationConfig(2)
                .packs(List.of(new PackPin("beta"), new PackPin("alpha").version("1.0.0")))
                .languages(List.of("java"))
                .selected(new SelectedArtifacts()
                        .agentProfile(List.of("p2", "p1"))
                        .missionType(List.of("software-dev")))
                .deactivated(new DeactivatedArtifacts().tactic(List.of("T1")));

        ActivationDocument.Built built = mapper.toDocument(config);

        assertThat(built.violations()).isEmpty();
        ActivationDocument document = built.document();
        assertThat(document.packs()).contains(List.of(PackRef.of("beta"), PackRef.pinned("alpha", "1.0.0")));
        assertThat(document.selected(ArtifactKind.MISSION_TYPE).orElseThrow()).containsExactly("software-dev");
        assertThat(document.languages().orElseThrow()).containsExactly("java");
        assertThat(document.selected(ArtifactKind.AGENT_PROFILE).orElseThrow()).containsExactly("p1", "p2");
        assertThat(document.deactivated(ArtifactKind.TACTIC)).containsExactly("T1");
    }

    @Test
    void anEmptyDenyListIsAShapeViolation() {
        ActivationDocument.Built built =
                mapper.toDocument(new ActivationConfig(2).deactivated(new DeactivatedArtifacts().directive(List.of())));

        assertThat(built.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo("/deactivated/directive");
            assertThat(violation.code()).isEqualTo("empty_deny_list");
        });
    }

    @Test
    void aConfigRoundTripsThroughTheDocumentInCanonicalForm() {
        ActivationConfig config = new ActivationConfig(2)
                .packs(List.of(new PackPin("beta"), new PackPin("alpha").version("1.0.0")))
                .selected(new SelectedArtifacts()
                        .directive(List.of("D2", "D1"))
                        .skill(List.of())
                        .missionType(List.of("b", "a", "b")))
                .deactivated(new DeactivatedArtifacts().antiPattern(List.of("X")));

        ActivationConfig canonical = mapper.toConfig(mapper.toDocument(config).document());

        assertThat(canonical.getSchemaVersion()).isEqualTo(2);
        assertThat(canonical.getPacks())
                .extracting(PackPin::getPackId, PackPin::getVersion)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("beta", null),
                        org.assertj.core.groups.Tuple.tuple("alpha", "1.0.0"));
        assertThat(canonical.getSelected().getMissionType()).containsExactly("a", "b");
        assertThat(canonical.getLanguages()).isNull();
        assertThat(canonical.getSelected().getDirective()).containsExactly("D1", "D2");
        assertThat(canonical.getSelected().getSkill())
                .as("an empty allow list is left out (FR-042)")
                .isNull();
        assertThat(canonical.getSelected().getTactic()).isNull();
        assertThat(canonical.getDeactivated().getAntiPattern()).containsExactly("X");
        assertThat(mapper.toDocument(canonical).document())
                .isEqualTo(mapper.toDocument(config).document());
    }

    @Test
    void anActivationLinksItselfUnderTheBasePath() {
        ActivationDocument document = ActivationDocument.builder().build().document();
        StoredActivation stored = StoredActivation.of(new ActivationId("A-1"), document);

        Activation activation = mapper.toActivation(stored);

        assertThat(activation.getActivationId()).isEqualTo("A-1");
        assertThat(activation.getRevision()).isEqualTo(document.revision().token());
        assertThat(activation.getLinks())
                .containsOnlyKeys(
                        "self",
                        "artifacts",
                        "offering",
                        "edit",
                        "delete",
                        "select",
                        "exclude",
                        "apply-preset",
                        "packs",
                        "graph",
                        "skipped",
                        "effective-context",
                        "assets",
                        "asset",
                        "skills");
        assertThat(activation.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/A-1");
        assertThat(mapper.self("a b")).isEqualTo("/api/v1/activations/a%20b");
    }

    @Test
    void anActivationLinksItsEffectiveListAsATemplateOfTheKindAndStateFilters() {
        StoredActivation stored = StoredActivation.of(
                new ActivationId("a-1"), ActivationDocument.builder().build().document());

        Link artifacts = mapper.toActivation(stored).getLinks().get("artifacts");

        assertThat(artifacts.getHref()).isEqualTo("/api/v1/activations/a-1/artifacts{?kind,state}");
        assertThat(artifacts.getTemplated()).isTrue();
        assertThat(Optional.ofNullable(artifacts.getMethod()))
                .as("a GET, the default, is not written")
                .isEmpty();
        assertThat(mapper.toActivation(stored, List.of()).getLinks()).containsKeys("self", "artifacts");
        assertThat(mapper.toReplaced(new ActivationService.Written(stored, false, List.of(), List.of()))
                        .getLinks())
                .containsKeys("self", "artifacts");
    }

    @Test
    void aReplaceAnswersWhatItDroppedAndOnlyWhenItDroppedSomething() {
        ActivationDocument document = ActivationDocument.builder().build().document();
        StoredActivation stored = StoredActivation.of(new ActivationId("a-1"), document);

        Activation plain = mapper.toReplaced(new ActivationService.Written(stored, false, List.of(), List.of()));
        Activation dropping = mapper.toReplaced(new ActivationService.Written(
                stored,
                false,
                List.of(new ArtifactRef(ArtifactKind.MISSION_TYPE, "research")),
                List.of(PackRef.pinned("alpha", "2.0.0"))));

        assertThat(plain).isEqualTo(mapper.toActivation(stored));
        assertThat(plain.getDropped()).isNull();
        assertThat(plain.getDroppedPins()).isNull();
        assertThat(dropping.getDropped())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.MISSION_TYPE, "research"));
        assertThat(dropping.getDroppedPins())
                .extracting(PackPin::getPackId, PackPin::getVersion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("alpha", "2.0.0"));
        assertThat(dropping.getConfig()).isEqualTo(plain.getConfig());
    }

    @Test
    void theClosureMapsToTheOfferingsPackShapeWithTheOfferingsLinks() {
        ClosureSnapshot snapshot = new ClosureSnapshot(
                List.of(
                        new ClosureSnapshot.PackEntry("beta", "PID-B", "org", "beta-pack", "1.0.0", List.of()),
                        new ClosureSnapshot.PackEntry(
                                "alpha",
                                "PID-A",
                                "built-in",
                                "alpha-pack",
                                "2.0.0",
                                List.of(new ClosureSnapshot.Dependency("PID-B", ">=1.0", "1.0.0")))),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                "sha256:f");

        ActivationPackList list = mapper.toPackList("project", snapshot);

        assertThat(list.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/project/packs");
        assertThat(list.getPacks()).hasSize(2);
        OfferingPack alpha = list.getPacks().get(1);
        assertThat(alpha.getPackId()).isEqualTo("alpha");
        assertThat(alpha.getDescriptorPackId()).isEqualTo("PID-A");
        assertThat(alpha.getLevel()).isEqualTo(OfferingPack.LevelEnum.BUILT_IN);
        assertThat(alpha.getName()).isEqualTo("alpha-pack");
        assertThat(alpha.getVersion()).isEqualTo("2.0.0");
        assertThat(alpha.getDependsOn()).containsExactly(new ResolvedDependency("PID-B", ">=1.0", "1.0.0"));
        assertThat(alpha.getLinks()).containsOnlyKeys("artifacts");
        assertThat(alpha.getLinks().get("artifacts").getHref()).isEqualTo("/api/v1/packs/alpha/artifacts");
        assertThat(list.getPacks().getFirst().getDependsOn()).isEmpty();
        assertThat(Optional.ofNullable(alpha.getLinks().get("artifacts").getMethod()))
                .isEmpty();
    }
}
