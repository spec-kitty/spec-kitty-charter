package ai.speckitty.charter.profiles.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AgentProfile entity")
class AgentProfileEntityTest {

    private static final PackIdentifier PACK = new PackIdentifier("built-in");

    private static AgentProfile.Builder valid(String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(PACK)
                .name("Name of " + id)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(
                        new Specialization("Focus of " + id, Optional.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("it is a CharterArtifact of kind agent_profile, named agent_profile:<profile-id>")
    void identityAndKind() {
        CharterArtifact profile = valid("architect-alphonso").build();

        assertThat(profile.kind()).isEqualTo(ArtifactKind.AGENT_PROFILE);
        assertThat(profile.localId()).isEqualTo("architect-alphonso");
        assertThat(profile.urn()).isEqualTo(new Urn(NodeKind.of(ArtifactKind.AGENT_PROFILE), "architect-alphonso"));
        assertThat(profile.label()).isEqualTo("Name of architect-alphonso");
        assertThat(profile.packId()).isEqualTo(PACK);
    }

    @Test
    @DisplayName("an undeclared defaulted field stays undeclared: no default is invented")
    void defaultsAreNotInvented() {
        AgentProfile profile = valid("p").build();

        assertThat(profile.schemaVersion()).isEmpty();
        assertThat(profile.description()).isEmpty();
        assertThat(profile.routingPriority()).isEmpty();
        assertThat(profile.maxConcurrentTasks()).isEmpty();
        assertThat(profile.sentinel()).isEmpty();
        assertThat(profile.initializationDeclaration()).isEmpty();
        assertThat(profile.collaboration()).isEqualTo(Collaboration.NONE);
        assertThat(profile.specializationContext()).isEqualTo(SpecializationContext.NONE);
        assertThat(profile.excluding()).isEmpty();
    }

    @Test
    @DisplayName("declared values are kept as declared, lists in authored order with duplicates")
    void declaredValuesAreKept() {
        AgentProfile profile = valid("p")
                .schemaVersion(Optional.of("1.0"))
                .routingPriority(Optional.of(0))
                .maxConcurrentTasks(Optional.of(1))
                .sentinel(Optional.of(false))
                .role("reviewer")
                .role("implementer")
                .directiveReference(new DirectiveReference("003", "Decision documentation", "why"))
                .directiveReference(new DirectiveReference("003", "Decision documentation", "why"))
                .build();

        assertThat(profile.schemaVersion()).contains("1.0");
        assertThat(profile.routingPriority()).contains(0);
        assertThat(profile.maxConcurrentTasks()).contains(1);
        assertThat(profile.sentinel()).contains(false);
        assertThat(profile.roles()).containsExactly("implementer", "reviewer", "implementer");
        assertThat(profile.directiveReferences()).hasSize(2);
    }

    @Test
    @DisplayName("lineage is not minted from the profile: it has no relationships of its own")
    void mintsNoRelationships() {
        AgentProfile profile = valid("java-jenny")
                .directiveReference(new DirectiveReference("003", "Decision documentation", "why"))
                .tacticReference(new ArtifactReference("tdd-red-green", "why"))
                .build();

        assertThat(profile.relationships()).isEmpty();
    }

    @Test
    @DisplayName("a role outside the schema pattern, or no role at all, is rejected")
    void rolesFollowTheSchema() {
        assertThat(valid("p").role("qa_lead-2").build().roles()).contains("qa_lead-2");

        assertThatThrownBy(() -> valid("p").role("Implementer").build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> AgentProfile.builder()
                        .id(new AgentProfileIdentifier("p"))
                        .packId(PACK)
                        .name("n")
                        .purpose("p")
                        .specialization(new Specialization("f", Optional.empty(), Optional.empty(), Optional.empty()))
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("routing priority 0..100, concurrency above 0 and schema version 1.0 or 2 hold at their bounds")
    void boundsFollowTheSchema() {
        assertThat(valid("p").routingPriority(Optional.of(100)).build().routingPriority())
                .contains(100);
        assertThat(valid("p").schemaVersion(Optional.of("2")).build().schemaVersion())
                .contains("2");

        assertThatThrownBy(() -> valid("p").routingPriority(Optional.of(101)).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("p").routingPriority(Optional.of(-1)).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("p").maxConcurrentTasks(Optional.of(0)).build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("p").schemaVersion(Optional.of("1.1")).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a blank profile id is rejected")
    void blankIdRejected() {
        assertThatThrownBy(() -> new AgentProfileIdentifier(" ")).isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("the map form of excluding keeps key order and an entry with no values")
    void exclusionKeepsItsData() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("capabilities", List.of("deploy"));
        values.put("roles", List.of());

        Exclusion.FieldValues exclusion = new Exclusion.FieldValues(values);

        assertThat(exclusion.values())
                .containsExactly(Map.entry("capabilities", List.of("deploy")), Map.entry("roles", List.of()));
        assertThat(valid("p")
                        .excluding(Optional.of(new Exclusion.WholeFields(List.of("capabilities"))))
                        .build()
                        .excluding())
                .contains(new Exclusion.WholeFields(List.of("capabilities")));
    }

    @Test
    @DisplayName("equality is by identity, not by other fields")
    void identityEquality() {
        assertThat(valid("p").build()).isEqualTo(valid("p").name("Other").build());
        assertThat(valid("p").build()).isNotEqualTo(valid("q").build());
    }
}
