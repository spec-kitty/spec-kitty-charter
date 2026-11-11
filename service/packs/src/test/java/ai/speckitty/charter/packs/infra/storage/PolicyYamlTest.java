package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
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
    }

    @Test
    void aWrittenPolicyReadsBackAsTheSamePolicy() {
        MutabilityPolicySource policy = MutabilityPolicySource.builder()
                .kindDefault(NodeKind.TEMPLATE, MutabilityLevel.EXTENDABLE)
                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.OPEN)
                .entry(new Urn(NodeKind.AGENT_PROFILE, "generic-agent"), MutabilityLevel.LOCKED)
                .entry(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_001"), MutabilityLevel.LOCKED)
                .build();

        assertThat(PolicyYaml.read(PolicyYaml.write(policy))).isEqualTo(policy);
    }

    @Test
    void aPolicyIsWrittenInOneOrderWithEveryKeyItDeclares() {
        MutabilityPolicySource policy = MutabilityPolicySource.builder()
                .entry(new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_001"), MutabilityLevel.LOCKED)
                .entry(new Urn(NodeKind.AGENT_PROFILE, "generic-agent"), MutabilityLevel.LOCKED)
                .kindDefault(NodeKind.DIRECTIVE, MutabilityLevel.OPEN)
                .kindDefault(NodeKind.TEMPLATE, MutabilityLevel.EXTENDABLE)
                .build();

        assertThat(PolicyYaml.write(policy))
                .as("kind defaults in kind order, artifacts by URN, so equal policies write equal text")
                .isEqualTo("""
                        schema_version: 1
                        kind_defaults:
                          directive: open
                          template: extendable
                        artifacts:
                          agent_profile:generic-agent: locked
                          directive:DIRECTIVE_001: locked
                        """);
    }

    @Test
    void theEmptyPolicyIsWrittenAsItsVersionAlone() {
        assertThat(PolicyYaml.write(MutabilityPolicySource.empty())).isEqualTo("schema_version: 1\n");
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
