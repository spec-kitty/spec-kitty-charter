@contract @journey-12
# Journey: docs/context/user-journeys/12-a-mission-walk-under-one-activation.md
# The usability walk of 2026-11-14 as a contract journey (#572). Ruling (operator, 2026-11-14): a per-PR
# journey over charter's own example packs (ADR-0070), with an activation written for those packs; not a nightly over
# open-packs content or over this repository's charter.yaml. It runs nightly too once #15 lands.
#
# A client that knows only the API root walks one mission under one activation: the project's charter in force, the
# implementer's governance, the software-dev mission step by step with each step's action context and prompt, and the
# review squad. Every read after the root is reached by a link the glue follows (MissionWalkSteps); the scenarios name
# outcomes, not URLs. The activation selects nothing the example packs do not hold, so a refused selection fails the
# journey: nothing is dropped. The counts and ids below are the example packs' own, so a fixture change and its
# assertion land in one diff. Not asserted: hop counts, link names, URLs, latency (ADR-0057), exact body text, and step
# contracts (slice F, #537, retires them).
#
# Steps this file adds to the vocabulary of README.md (bound in MissionWalkSteps):
# - `Given the example pack "<name>" is registered from the filesystem source` copies the example pack under the
#   filesystem source root as packs/<name> and registers it through POST /packs; the registration must succeed.
# - `When the project activates its charter:` followed by the activation document: the root's activations link, followed
#   with its method; the service must create it.
# - `Then every selection of the charter is in force and nothing is refused`.
# - `When the team reads the <agent profile|mission type|procedure> "<id>" under the activation`, and `When the team
#   reads the step "<step>" of the mission type "<type>" under the activation`: the activation's artifacts of that kind,
#   then the item's effective-artifact link (and the step's self link).
# - `Then its steps are, in order:` with a table `step | runs`, `runs` being `in sequence` or `on demand`.
# - `Then the step names the role "<role>"`, `Then it is in force`, and `Then its governance requires the <kind>
#   "<id>", which is in force`.
# - `When the team walks every step of the mission type "<type>"`, then `Then every step has an action context that
#   links each item in force to its body` and `Then every step has a prompt that fills each slot it declares or lists
#   the slot as empty`: each names every step that fails, not only the first.

Feature: Journey 12 — a team walks a whole mission under one activation
  A team runs a software-dev mission against its charter. Before the first step it needs the charter in force over
  the packs it uses, the implementer profile's governance, the mission's steps in order, and, for every step, the
  governance and the prompt to act on; at the review, a review profile and the squad procedure it requires. The team's
  client starts at the API root and follows links.

  # The fixture: the example pack built-in configured, and the org example pack org-base, which depends on it,
  # registered from the filesystem source as an org would add its own pack. The charter selects the two profiles, the
  # software-dev mission type, the squad procedure and governance from both packs, and every step enhancement of
  # software-dev.
  Background:
    Given the example pack "built-in" of the charter
    And the example pack "org-base" is registered from the filesystem source
    When the project activates its charter:
      """
      {
        "schemaVersion": 2,
        "packs": [{"packId": "org-base"}],
        "selected": {
          "agent_profile": ["java-jenny", "reviewer-renata"],
          "directive": ["DIRECTIVE_001", "DIRECTIVE_003", "DIRECTIVE_010", "DIRECTIVE_038"],
          "mission_type": ["software-dev"],
          "paradigm": ["structured-prompt-driven-development"],
          "procedure": ["adversarial-squad-deployment"],
          "step_enhancement": ["plan-risk-notes", "spdd-implement", "spdd-plan", "spdd-review", "spdd-specify", "spdd-tasks"],
          "styleguide": ["boring-code-review", "java-conventions"],
          "tactic": ["autonomous-operation-protocol"],
          "toolguide": ["efficient-local-tooling"]
        }
      }
      """

  Scenario: The project's charter is in force on the example packs
    Then every selection of the charter is in force and nothing is refused

  Scenario: Java Jenny's governance is in force
    The implementer profile lists every artifact its walk reaches, each with how it applies: the relation, the artifact
    it was reached through and how far, its state and reason, and the edge's when and why. java-conventions is required.
    DIRECTIVE_001 is reached through DIRECTIVE_010 and is locked by built-in.
    When the team reads the agent profile "java-jenny" under the activation
    Then it is in force
    And the effective governance holds exactly the entries:
      """
      directive:DIRECTIVE_001 requires via directive:DIRECTIVE_010 hops 2 active/locked in built-in linked edge {}
      directive:DIRECTIVE_003 requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {when "when an implementation choice departs from the plan" because "An implementer records the decision it takes, so the reviewer reads why the code differs from the plan."}
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      procedure:adversarial-squad-deployment requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {because "A language-specialist profile requires the conventions styleguide of each of its applies_to_languages (java)."}
      """

  Scenario: The software-dev mission is governed step by step
    The example pack declares 13 steps: 8 in sequence, in this order, then 5 on demand.
    When the team reads the mission type "software-dev" under the activation
    Then it is in force
    And its steps are, in order:
      | step           | runs        |
      | discovery      | in sequence |
      | specify        | in sequence |
      | plan           | in sequence |
      | tasks          | in sequence |
      | analyze        | in sequence |
      | implement      | in sequence |
      | review         | in sequence |
      | accept         | in sequence |
      | charter        | on demand   |
      | tasks-finalize | on demand   |
      | tasks-outline  | on demand   |
      | tasks-packages | on demand   |
      | retrospect     | on demand   |

  # @WIP until slice F (#537, PR #567) adds the example packs' action nodes: built-in declares an action node for 5 of
  # software-dev's 13 steps (specify, plan, tasks, implement, review), so the other 8 answer action_not_found. Whoever
  # merges second drops the tag and closes #572.
  @WIP
  Scenario: Every step comes with the context and prompt to act on
    When the team walks every step of the mission type "software-dev"
    Then every step has an action context that links each item in force to its body
    And every step has a prompt that fills each slot it declares or lists the slot as empty

  Scenario: A review squad is available at the review step
    The review step names its role; the review profile is named here, since selecting a profile by role is slice G
    (#538, #42). The procedure names no squad cast, so only the procedure and the profile are asserted.
    When the team reads the step "review" of the mission type "software-dev" under the activation
    Then the step names the role "reviewer"
    When the team reads the agent profile "reviewer-renata" under the activation
    Then it is in force
    And its governance requires the procedure "adversarial-squad-deployment", which is in force
    When the team reads the procedure "adversarial-squad-deployment" under the activation
    Then it is in force
