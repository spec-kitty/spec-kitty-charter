package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.domain.InForceRequest;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The effective agent profile over charter's own example packs (R8; rulings D5, D6 and D7 of ADR-0078, ADR-0070): the
 * org-base enhancement of reviewer-renata is merged into her when it is in force and not when it is not, an enhancer is
 * answered as stored with the profile it enhances, and java-jenny's lineage is implementer-ivan at depth 1 when he is in
 * force. The views are built as the service builds them, with the store and the profile merge.
 */
@DisplayName("The effective agent profile over the example packs")
class ResolvedProfileCorpusTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    private static ClosureView view(String root) {
        List<PackLocation> packs = ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
        DomainRepositoryService store = new DomainRepositoryService(() -> packs, new YamlSerializationStrategy());
        ConsistentRead read = new ConsistentRead(() -> DIGEST);
        OfferingReadService offering = new OfferingReadService(store, store, store, read);
        return new ClosureQueries(offering, store, read, store, store, AgentProfileEnhancements.INSTANCE)
                .snapshot(Optional.of(List.of(new PackIdentifier(root))));
    }

    private static InForceStates selecting(ClosureView view, String... profiles) {
        Set<Urn> selected = Arrays.stream(profiles)
                .map(id -> new Urn(NodeKind.AGENT_PROFILE, id))
                .collect(Collectors.toSet());
        return view.inForce(new InForceRequest(selected, Set.of(), Optional.empty(), Map.of()));
    }

    private static List<String> directiveCodes(CharterArtifact profile) {
        return ((AgentProfile) profile)
                .directiveReferences().stream().map(DirectiveReference::code).toList();
    }

    @Test
    @DisplayName("the org-base enhancement in force is merged into reviewer-renata: her content and its addition")
    void theEnhancementInForceIsMerged() {
        ClosureView view = view(ExamplePacks.ORG);

        ClosureView.ResolvedProfile renata = view.resolvedProfile(
                        "reviewer-renata", selecting(view, "reviewer-renata", "reviewer-renata-org"))
                .orElseThrow();

        AgentProfile merged = (AgentProfile) renata.profile();
        assertThat(merged.localId()).isEqualTo("reviewer-renata");
        assertThat(merged.name())
                .as("the enhancer's name is its own, never merged")
                .isEqualTo("Reviewer Renata");
        assertThat(directiveCodes(merged)).containsExactly("001", "024", "030", "032", "041", "051", "038");
        assertThat(merged.capabilities())
                .as("a field the enhancer leaves absent stays the base's")
                .isNotEmpty();
        assertThat(renata.lineage()).isEmpty();
        assertThat(renata.enhances()).isEmpty();
    }

    @Test
    @DisplayName("an enhancement out of force adds nothing")
    void anEnhancementOutOfForceAddsNothing() {
        ClosureView view = view(ExamplePacks.ORG);

        ClosureView.ResolvedProfile renata = view.resolvedProfile("reviewer-renata", selecting(view, "reviewer-renata"))
                .orElseThrow();

        assertThat(directiveCodes(renata.profile())).doesNotContain("038");
    }

    @Test
    @DisplayName("an enhancer is answered as its pack stores it, with the profile it enhances")
    void anEnhancerIsAnsweredAsStored() {
        ClosureView view = view(ExamplePacks.ORG);

        ClosureView.ResolvedProfile enhancer = view.resolvedProfile(
                        "reviewer-renata-org", selecting(view, "reviewer-renata", "reviewer-renata-org"))
                .orElseThrow();

        assertThat(enhancer.profile().localId()).isEqualTo("reviewer-renata-org");
        assertThat(((AgentProfile) enhancer.profile()).capabilities())
                .as("nothing of the base is folded into the enhancer")
                .isEmpty();
        assertThat(enhancer.enhances()).containsExactly(new Urn(NodeKind.AGENT_PROFILE, "reviewer-renata"));
    }

    @Test
    @DisplayName("java-jenny's lineage is implementer-ivan at depth 1 when he is in force, and nothing when he is not")
    void javaJennysLineage() {
        ClosureView view = view(ExamplePacks.BUILT_IN);

        ClosureView.ResolvedProfile jenny = view.resolvedProfile(
                        "java-jenny", selecting(view, "java-jenny", "implementer-ivan"))
                .orElseThrow();
        ClosureView.ResolvedProfile alone = view.resolvedProfile("java-jenny", selecting(view, "java-jenny"))
                .orElseThrow();

        assertThat(jenny.lineage())
                .containsExactly(new ProfileGovernance.LineageLink(
                        new Urn(NodeKind.AGENT_PROFILE, "implementer-ivan"), new PackIdentifier("built-in"), 1));
        assertThat(((AgentProfile) jenny.profile()).name()).isEqualTo("Java Jenny");
        assertThat(alone.lineage()).isEmpty();
    }

    @Test
    @DisplayName("a profile no pack of the closure holds is not answered")
    void anUnknownProfileIsEmpty() {
        ClosureView view = view(ExamplePacks.BUILT_IN);

        assertThat(view.resolvedProfile("nobody", selecting(view))).isEmpty();
        assertThat(view.heldIds().get(ArtifactKind.AGENT_PROFILE)).doesNotContain("nobody");
    }

    @Test
    @DisplayName("a view built without the profile merge cannot compose an effective profile")
    void aViewWithoutTheMergeRefusesToCompose() {
        List<PackLocation> packs = ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
        DomainRepositoryService store = new DomainRepositoryService(() -> packs, new YamlSerializationStrategy());
        ConsistentRead read = new ConsistentRead(() -> DIGEST);
        OfferingReadService offering = new OfferingReadService(store, store, store, read);
        ClosureView view = new ClosureQueries(offering, store, read, store, store)
                .snapshot(Optional.of(List.of(new PackIdentifier(ExamplePacks.BUILT_IN))));

        assertThatThrownBy(() -> view.resolvedProfile("java-jenny", selecting(view, "java-jenny")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without the profile merge");
    }
}
