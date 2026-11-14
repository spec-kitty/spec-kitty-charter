# The activation twins of the glossary reads (#106; FR-011 to FR-013, research R5 and R7, ADR-0074 decision 12):
# listEffectiveGlossaryContexts, getEffectiveGlossaryContext and getEffectiveGlossaryTerm answer the copy of each
# glossary context the layer merge keeps across the levels of the activation's closure, whole, whatever its state; a
# term carries its context's state. The 400, 401, 403 and 500 answers of the three operations are asserted in
# read-refusals.feature, every-operation.feature and unexpected-failure.feature.
#
# Fixture: core (built-in) holds spec-kitty-core with the terms mission and lane, and qa; proj (project) holds its own
# copy of spec-kitty-core with mission alone, which wins whole. The activation a-1 builds on both, selects
# spec-kitty-core and deactivates qa; a-2 builds on core alone, the positive control of the whole override.

@contract @activations
Feature: The glossary contexts of an activation are read as the copies the layer merge keeps, whole
  Under an activation, a glossary context is answered as the copy the layer merge keeps: a project copy over a built-in
  one, whole, so a term only the replaced copy holds is not in force. A context is answered with the state and reason
  the activation gives it, whatever they are, and a term carries its context's state, since it has none of its own.
  Each read answers its own ETag and a 304 to a matching If-None-Match. The segments are judged in path order: the
  activation, then whether a pack of the closure holds the context, then whether the winning copy holds the term.

  Background:
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "glossaries/spec-kitty-core.glossary.yaml":
      """
      schema_version: '1.0'
      id: spec-kitty-core
      name: Spec Kitty core
      description: Canonical Spec Kitty terminology.
      terms:
        - id: mission
          name: mission
          definition: A unit of planned work, as the built-in pack defines it.
          status: active
        - id: lane
          name: lane
          definition: The old name of a work package's worktree.
          status: deprecated
      """
    And the built-in pack "core" with the file "glossaries/qa.glossary.yaml":
      """
      schema_version: '1.0'
      id: qa
      name: Quality assurance
      description: The vocabulary of testing and its reports.
      terms:
        - id: flaky-test
          name: flaky test
          definition: A test that passes and fails on the same code.
          status: active
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "glossaries/spec-kitty-core.glossary.yaml":
      """
      schema_version: '1.0'
      id: spec-kitty-core
      name: Spec Kitty core
      description: Canonical Spec Kitty terminology, as this project uses it.
      tags: [spec-kitty]
      terms:
        - id: mission
          name: mission
          definition: A unit of planned work, as this project defines it.
          aliases: [feature]
          status: active
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"glossary": ["spec-kitty-core"]}, "deactivated": {"glossary": ["qa"]}}
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}], "selected": {"glossary": ["spec-kitty-core"]}}
      """

  Scenario: The glossary contexts of the activation are the winning copies, in id order, linked under the activation and in their pack
    When I GET "/activations/a-1/artifacts/glossary"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "items": [
          {
            "id": "qa",
            "name": "Quality assurance",
            "description": "The vocabulary of testing and its reports.",
            "termCount": 1,
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/glossary/qa"},
              "artifact": {"href": "/api/v1/packs/core/artifacts/glossary/qa"}
            }
          },
          {
            "id": "spec-kitty-core",
            "name": "Spec Kitty core",
            "description": "Canonical Spec Kitty terminology, as this project uses it.",
            "tags": ["spec-kitty"],
            "termCount": 1,
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"},
              "artifact": {"href": "/api/v1/packs/proj/artifacts/glossary/spec-kitty-core"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/glossary"},
          "up": {"href": "/api/v1/activations/a-1"}
        }
      }
      """
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts/glossary"
    Then following the link "artifact" of every element of "items" answers 200

  Scenario: The project copy of a context wins whole: its terms are the project's, and a term only the built-in copy holds is not in force
    spec-kitty-core is held by core, with mission and lane, and by proj, with mission alone, which replaces it at the
    project level. The context is proj's copy, and lane, which only core's copy holds, is refused, naming the term.
    When I GET "/activations/a-1/artifacts/glossary/spec-kitty-core"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the field at "/activationId" is "a-1"
    And the field at "/kind" is "glossary"
    And the field at "/artifactId" is "spec-kitty-core"
    And the field at "/packId" is "proj"
    And the field at "/level" is "project"
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"
    And the field at "/required" is false
    And the field at "/policy" is "open"
    And the field at "/glossary/description" is "Canonical Spec Kitty terminology, as this project uses it."
    And the field at "/glossary/terms" holds exactly 1 item
    And the field at "/glossary/terms/0/id" is "mission"
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/glossary/spec-kitty-core"
    And the field at "/_links/collection/href" is "/api/v1/activations/a-1/artifacts/glossary"
    And the field at "/_links/term/href" is "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/{termId}"
    And the field at "/_links/term/templated" is true
    And the field at "/_links/up/href" is "/api/v1/activations/a-1"
    When I follow the templated link "term" with:
      | termId | mission |
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/kind" is "glossary"
    And the field at "/artifactId" is "spec-kitty-core"
    And the field at "/packId" is "proj"
    And the field at "/level" is "project"
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"
    And the field at "/term/id" is "mission"
    And the field at "/term/definition" is "A unit of planned work, as this project defines it."
    And the field at "/term/aliases/0" is "feature"
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/glossary/spec-kitty-core/terms/mission"
    And the field at "/_links/up/href" is "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/definition" is "A unit of planned work, as this project defines it."
    When I GET "/activations/a-1/artifacts/glossary/spec-kitty-core/terms/lane"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    And the field at "/detail" is "term `lane` is not held in glossary context `spec-kitty-core`"

  Scenario: Without the project pack, the built-in copy is in force and its term lane answers (the positive control)
    When I GET "/activations/a-2/artifacts/glossary/spec-kitty-core"
    Then the response status is 200
    And the field at "/packId" is "core"
    And the field at "/level" is "built-in"
    And the field at "/glossary/terms" holds exactly 2 items
    When I GET "/activations/a-2/artifacts/glossary/spec-kitty-core/terms/lane"
    Then the response status is 200
    And the field at "/packId" is "core"
    And the field at "/term/id" is "lane"
    And the field at "/term/status" is "deprecated"

  Scenario: A context the activation deactivates is answered with its state and reason, and so is its term
    When I GET "/activations/a-1/artifacts/glossary/qa"
    Then the response status is 200
    And the field at "/state" is "excluded"
    And the field at "/reason" is "deactivated"
    And the field at "/packId" is "core"
    And the field at "/glossary/name" is "Quality assurance"
    When I GET "/activations/a-1/artifacts/glossary/qa/terms/flaky-test"
    Then the response status is 200
    And the field at "/state" is "excluded"
    And the field at "/reason" is "deactivated"
    And the field at "/term/name" is "flaky test"

  Scenario: A context the activation does not select is answered with its state and reason, not refused
    When I GET "/activations/a-2/artifacts/glossary/qa"
    Then the response status is 200
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"

  Scenario: Two packs of one level that hold one context are refused, never answered in part
    The org packs left and right both hold the context shared, with no settlement: the closure cannot merge, and each
    glossary read of an activation over both refuses 500 with the composition code pack_sibling_conflict. The
    activation is stored past the API, as a pack change after the write would leave it.
    Given the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      """
    And the org pack "left" with the file "glossaries/shared.glossary.yaml":
      """
      schema_version: '1.0'
      id: shared
      name: Shared, left
      description: The left copy.
      terms:
        - id: word
          name: word
          definition: The left word.
          status: active
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      """
    And the org pack "right" with the file "glossaries/shared.glossary.yaml":
      """
      schema_version: '1.0'
      id: shared
      name: Shared, right
      description: The right copy.
      terms:
        - id: word
          name: word
          definition: The right word.
          status: active
      """
    And the stored file of the activation "a-3" holds:
      """
      {"packs":[{"packId":"left"},{"packId":"right"}],"schemaVersion":2}
      """
    When I GET "/activations/a-3/artifacts/glossary/shared"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    When I GET "/activations/a-3/artifacts/glossary/shared/terms/word"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    When I GET "/activations/a-3/artifacts/glossary"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"

  Scenario Outline: <operation> under an activation that is not stored is 404 activation_not_found
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "activation_not_found"

    Examples:
      | operation                     | path                                                         |
      | listEffectiveGlossaryContexts | /activations/nope/artifacts/glossary                         |
      | getEffectiveGlossaryContext   | /activations/nope/artifacts/glossary/spec-kitty-core         |
      | getEffectiveGlossaryTerm      | /activations/nope/artifacts/glossary/spec-kitty-core/terms/x |

  Scenario Outline: <operation> of a <segment> no pack of the closure holds is 404 artifact_not_found naming it, also against If-None-Match
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    And the field at "/detail" is "<detail>"
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "artifact_not_found"

    Examples:
      | operation                   | segment | path                                                        | detail                                                        |
      | getEffectiveGlossaryContext | context | /activations/a-1/artifacts/glossary/ghost                   | glossary context `ghost` is not held                          |
      | getEffectiveGlossaryTerm    | context | /activations/a-1/artifacts/glossary/ghost/terms/mission     | glossary context `ghost` is not held                          |
      | getEffectiveGlossaryTerm    | term    | /activations/a-1/artifacts/glossary/spec-kitty-core/terms/x | term `x` is not held in glossary context `spec-kitty-core`    |

  Scenario Outline: <operation> answers 304 to a matching If-None-Match, and its tag is not the tag of <against>
    When I GET "<path>"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 304
    When I GET "<other>" with If-None-Match the remembered header "ETag"
    Then the response status is 200

    Examples:
      | operation                     | against             | path                                                              | other                                                             |
      | listEffectiveGlossaryContexts | listEffectiveSkills | /activations/a-1/artifacts/glossary                               | /activations/a-1/artifacts/skill                                  |
      | getEffectiveGlossaryContext   | its term            | /activations/a-1/artifacts/glossary/spec-kitty-core               | /activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission |
      | getEffectiveGlossaryTerm      | its context         | /activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission | /activations/a-1/artifacts/glossary/spec-kitty-core               |

  Scenario: The effective list filters on kind glossary, each context once, as its winning copy with its state
    When I GET "/activations/a-1/artifacts?kind=glossary"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      glossary:qa
      glossary:spec-kitty-core
      """
    And the field at "/items/1/packId" is "proj"
    And the field at "/items/1/level" is "project"
    And the field at "/items/1/state" is "active"
    And the field at "/items/0/state" is "excluded"
