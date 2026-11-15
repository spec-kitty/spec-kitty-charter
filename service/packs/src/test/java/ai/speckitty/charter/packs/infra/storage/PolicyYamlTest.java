package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code policy.yaml} read into the domain's {@link MutabilityPolicySource} (ADR-0063, FR-022). */
class PolicyYamlTest {

    @Test
    void aValidFileResolvesTheUrnEntryThenTheKindDefaultThenOpen() {
        MutabilityPolicySource source = PolicyYaml.read("""
                schema_version: 1
                kind_defaults:
                  directive: extendable
                  mission_type: locked
                artifacts:
                  directive:DIRECTIVE_001: locked
                  tactic:acceptance-test-first: open
                role_pins:
                  reviewer: reviewer-renata
                  implementer: java-jenny
                """);

        assertThat(source.levelOf(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_001")))
                .as("the URN entry wins over its kind default")
                .isEqualTo(MutabilityLevel.LOCKED);
        assertThat(source.levelOf(new Urn(NodeKind.DIRECTIVE, "OTHER")))
                .as("the kind default")
                .isEqualTo(MutabilityLevel.EXTENDABLE);
        assertThat(source.levelOf(new Urn(NodeKind.MISSION_TYPE, "software-dev")))
                .isEqualTo(MutabilityLevel.LOCKED);
        assertThat(source.levelOf(new Urn(NodeKind.TACTIC, "acceptance-test-first")))
                .isEqualTo(MutabilityLevel.OPEN);
        assertThat(source.levelOf(new Urn(NodeKind.STYLEGUIDE, "s")))
                .as("neither names it")
                .isEqualTo(MutabilityLevel.OPEN);
        assertThat(source.rolePins().byRole())
                .as("the pins, in authored order")
                .containsExactly(entry("reviewer", "reviewer-renata"), entry("implementer", "java-jenny"));
    }

    @Test
    void aPolicyIsWrittenInOneOrderWithEveryKeyItDeclares() {
        MutabilityPolicySource policy = MutabilityPolicySource.builder()
                .entry(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_001"), MutabilityLevel.LOCKED)
                .entry(new Urn(NodeKind.AGENT_PROFILE, "generic-agent"), MutabilityLevel.LOCKED)
                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.OPEN)
                .kindDefault(NodeKind.TEMPLATE, MutabilityLevel.EXTENDABLE)
                .rolePin("reviewer", "reviewer-renata")
                .rolePin("implementer", "java-jenny")
                .build();

        assertThat(PolicyYaml.write(policy))
                .as(
                        "kind defaults in kind order, artifacts by URN, pins as authored, so equal policies write equal text")
                .isEqualTo("""
                        schema_version: 1
                        kind_defaults:
                          directive: open
                          template: extendable
                        artifacts:
                          agent_profile:generic-agent: locked
                          directive:DIRECTIVE_001: locked
                        role_pins:
                          reviewer: reviewer-renata
                          implementer: java-jenny
                        """);
        assertThat(PolicyYaml.read(PolicyYaml.write(policy)))
                .as("the text reads back as the policy")
                .isEqualTo(policy);
        assertThat(PolicyYaml.write(MutabilityPolicySource.empty()))
                .as("the empty policy is its version alone")
                .isEqualTo("schema_version: 1\n");
    }

    @Test
    void theGlossaryKindDefaultMapsThroughItsArtifactKindToTheGlossaryContextNodeKind() {
        MutabilityPolicySource source = PolicyYaml.read("""
                schema_version: 1
                kind_defaults:
                  glossary: locked
                """);

        assertThat(source.kindDefaults())
                .as("the key is the artifact kind token glossary; its node kind is glossary_context (ADR-0053)")
                .containsExactly(Map.entry(NodeKind.GLOSSARY_CONTEXT, MutabilityLevel.LOCKED));
        assertThat(source.levelOf(new Urn(NodeKind.GLOSSARY_CONTEXT, "spec-kitty-core")))
                .isEqualTo(MutabilityLevel.LOCKED);
        assertThat(PolicyYaml.write(source))
                .as("written back under the artifact kind token, never the URN token")
                .isEqualTo("schema_version: 1\nkind_defaults:\n  glossary: locked\n");
        assertThatThrownBy(() -> PolicyYaml.read("schema_version: 1\nkind_defaults: {glossary_context: locked}\n"))
                .as("the URN token is no kind_defaults key")
                .isInstanceOfSatisfying(
                        PolicyYaml.Unreadable.class,
                        failure -> assertThat(failure.getMessage()).contains("glossary_context"));
    }

    @Test
    void aFileWithNeitherMapIsTheEmptySource() {
        assertThat(PolicyYaml.read("schema_version: 1\n")).isEqualTo(MutabilityPolicySource.empty());
    }

    @Test
    void aFileThatBreaksTheSchemaIsATypedFailureCarryingTheSchemaMessage() {
        assertThatThrownBy(() -> PolicyYaml.read("schema_version: 1\nroles: {implementer: locked}\n"))
                .isInstanceOfSatisfying(
                        PolicyYaml.Unreadable.class,
                        failure -> assertThat(failure.getMessage())
                                .contains("additionalProperties")
                                .contains("roles"));
        assertThatThrownBy(() -> PolicyYaml.read("schema_version: 1\nartifacts: {directive:D1: frozen}\n"))
                .isInstanceOfSatisfying(
                        PolicyYaml.Unreadable.class,
                        failure -> assertThat(failure.getMessage()).contains("enum"));
    }

    @Test
    void aFileThatIsNoYamlIsATypedFailure() {
        assertThatThrownBy(() -> PolicyYaml.read("schema_version: [1\n")).isInstanceOf(PolicyYaml.Unreadable.class);
    }
}
