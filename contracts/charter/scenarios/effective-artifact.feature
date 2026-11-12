# WP06 of agent-profile-reads: the generic activation-scoped read of one artifact, getEffectiveArtifact (FR-007, FR-010,
# NFR-002; plan fold A3, C4, C6; operator decisions 01M70ZFPHZTBH670GF7046XDW0 and 01M710DQEMV7VPKDNQPWKPGD4F). Written red
# first: the scenarios were committed with every step bound and run against the operation stubbed as a reserved one,
# where /activations/a-1/artifacts/directive/X answered 501 (all eight scenarios); the operation was then built.
#
# The read answers the winner of the layer merge with the facts the effective list gives it, and embeds no body: its
# `artifact` link is the pack-scoped read of the pack that holds the version in force, and the scenarios follow it. An
# agent_profile is answered by the literal operation of WP07, which is read before this templated path, so no scenario
# of this file reads one. The vocabulary is the existing one (README.md); nothing is added.
#
# Fixture: core (built-in) holds D, X and E, proj (project) holds its own copy of D. The activation selects D and X and
# leaves E out.

@contract @activations
Feature: The winner of one artifact is read under an activation, with its state and a link to the pack that holds it
  getEffectiveArtifact answers, for a kind the pack-scoped artifact routes serve, the version of the artifact that the
  layer merge keeps across the levels of the activation's closure: the pack and level that hold it and its state,
  reason, policy and required as the effective list gives them. It embeds no body; the artifact link is the pack-scoped
  read of the winner's pack. The activation is judged first, then the kind, then whether a pack of the closure holds the
  artifact, all before any If-None-Match. The ETag names the revision, the packs of the closure, the kind, the id and
  the depth, and a matching If-None-Match is a 304.

  Background:
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D at built-in
      intent: D at built-in
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: E
      enforcement: required
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D at project
      intent: D at project
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"directive": ["D", "X"]}}
      """

  Scenario: A selected directive is read with its pack, its state and a link to the pack-scoped read
    X is held by core alone and selected. The answer names core and its level, says the directive is in force and
    selected, and links the pack-scoped read of X in core, which answers 200 with the directive. The links are the
    request itself, that read and the activation. The answer carries the verified fingerprint and a strong ETag.
    When I GET "/activations/a-1/artifacts/directive/X"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the field at "/activationId" is "a-1"
    And the field at "/kind" is "directive"
    And the field at "/artifactId" is "X"
    And the field at "/packId" is "core"
    And the field at "/level" is "built-in"
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"
    And the field at "/required" is false
    And the field at "/policy" is "open"
    And the field at "/directive" is absent
    And the field at "/satisfiedBy" is absent
    And the field at "/suggestion" is absent
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/directive/X"
    And the field at "/_links/artifact/href" is "/api/v1/packs/core/artifacts/directive/X"
    And the field at "/_links/up/href" is "/api/v1/activations/a-1"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/kind" is "directive"
    And the field at "/id" is "X"
    And the field at "/packId" is "core"
    And the field at "/directive/title" is "X"
    When I GET "/activations/a-1/artifacts/directive/X"
    And I follow the link "up"
    Then the response status is 200
    And the field at "/activationId" is "a-1"

  Scenario: The project copy that won the layer merge is the one answered, and its link reads the project pack
    D is held by core and by proj, which replaces it at the project level. The answer names proj and project, and the
    artifact link reads proj's copy, whose title is the project's; core's copy, which lost, is named nowhere. X is the
    control in the same activation: it keeps core.
    When I GET "/activations/a-1/artifacts/directive/D"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/level" is "project"
    And the field at "/state" is "active"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/directive/D"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/directive/title" is "D at project"
    When I GET "/activations/a-1/artifacts/directive/X"
    Then the response status is 200
    And the field at "/packId" is "core"

  Scenario: An artifact the activation does not select is answered with its state and the reason, and still links its pack
    E is held by core and left out of the selection: it is read as inactive, not selected, which is what the effective
    list says of it. The answer is not a 404, and the artifact link still reads E in core. D is the control: it is active.
    When I GET "/activations/a-1/artifacts/directive/E"
    Then the response status is 200
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"
    And the field at "/packId" is "core"
    And the field at "/_links/artifact/href" is "/api/v1/packs/core/artifacts/directive/E"
    When I follow the link "artifact"
    Then the response status is 200
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:E
      """

  Scenario: A matching If-None-Match is 304 with the same tag, and the tag of another artifact or depth never matches
    The first answer, 200 with X, is the positive control: the 304 is not an empty store. The tag of D does not match a
    read of X, and the same read at another depth has another tag, as the ETag names the kind, the id and the depth.
    When I GET "/activations/a-1/artifacts/directive/X"
    Then the response status is 200
    And the response header "ETag" appears once
    When I remember the header "ETag"
    And I GET "/activations/a-1/artifacts/directive/X" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    When I GET "/activations/a-1/artifacts/directive/D"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/a-1/artifacts/directive/X?depth=1"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/directive/X?depth=1"
    When I GET "/activations/a-1/artifacts/directive/D" with If-None-Match the remembered header "ETag"
    Then the response status is 200

  Scenario: A depth outside 0 to 1 is a 400 without a code that names the bound
    When I GET "/activations/a-1/artifacts/directive/X?depth=2"
    Then the response status is 400
    And the field at "/detail" is "The query parameter depth is \"2\", which is greater than 1."
    When I GET "/activations/a-1/artifacts/directive/X?depth=-1"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The query parameter depth is \"-1\", which is less than 0."
    When I GET "/activations/a-1/artifacts/directive/X?depth=0"
    Then the response status is 200

  Scenario: An activation that is not stored is activation_not_found, whatever the kind and the id are
    When I GET "/activations/nope/artifacts/directive/X"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/nope/artifacts/procedure/X"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A kind the service does not serve is kind_not_found, and a kind that is no slug is a 400
    "template" is a kernel kind the artifact routes do not serve; "Directive" is no slug, so it breaks the parameter.
    D is the control: the same activation answers it 200.
    When I GET "/activations/a-1/artifacts/template/X"
    Then the response status is 404
    And the problem code is "kind_not_found"
    And the field at "/detail" is "The service serves no artifact kind template."
    When I GET "/activations/a-1/artifacts/Directive/X"
    Then the response status is 400
    When I GET "/activations/a-1/artifacts/directive/D"
    Then the response status is 200

  Scenario: An id that no pack of the closure holds is artifact_not_found, also against If-None-Match
    GHOST is held by no pack. An id of another kind is no match either: X is a directive. The refusal is judged before
    the If-None-Match, which `*` would otherwise match.
    When I GET "/activations/a-1/artifacts/directive/GHOST"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/a-1/artifacts/tactic/X"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/a-1/artifacts/directive/GHOST" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "artifact_not_found"
