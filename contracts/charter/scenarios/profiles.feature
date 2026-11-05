# Decided, not built (#380; operator ruling 2026-11-05, decision 01M6NE62FJ43XT0VC78YJ128NP):
# - PR-CHARTER-REFUSAL-001 of #374. The service does not resolve an agent profile's enhances yet (ADR-0032;
#   AgentProfile in service/profiles), so the effective profile this refusal judges does not exist, and no
#   operation of ../openapi.yaml reads it.
# - Steps are enhanced by additions only (ADR-0053 decision 22), so a drop cannot happen to a step: this
#   concerns agent profiles alone, the one kind whose enhances keeps its field-merge meaning.
# - So the scenario is tagged @WIP, and the file carries no @contract because its steps are not bound: the
#   suite selects none of it (CharterContractSuite, @IncludeTags("contract"), @ExcludeTags("WIP")). When
#   #380 builds the effective profile, the steps are bound with the vocabulary of README.md, @WIP comes off
#   and @contract goes on.
# - Target answer, provisional until #380 decides it: the refusal is the configured packs' fault, not the
#   request's, so the read answers 500 with a refusal code of the pack composition, like
#   pack_sibling_conflict (../schemas/CharterRefusalCode.yaml): deterministic, not retryable, its detail a
#   sentence naming the enhancing pack, its packs member listing that pack first. No code of this version
#   names this refusal, so #380 adds one. Should the enhancement be registered through a write instead, the
#   request is at fault, and the write answers 422 validation_failed before any file is written, as every
#   registration write answers a body it refuses.

@WIP
Feature: An agent profile is only ever added to, never quietly weakened
  A pack's agent profile that enhances another profile may add to it, never silently drop part of it:
  governance that would quietly weaken a profile is refused, naming the pack and the fields it would drop.

  @promise:PR-CHARTER-REFUSAL-001
  Scenario: An agent profile enhancement that would drop fields of the profile it enhances is refused
    Given the org pack "base" holds the agent profile "reviewer" with the specialization "review"
    And the org pack "team" holds an agent profile that enhances "reviewer" and leaves out its specialization
    When I read the effective agent profile "reviewer"
    Then the request is refused naming the pack "team" and the field "specialization"
