# The vocabulary reads of an activation (#106; FR-014 to FR-017, FR-019, SC-004, NFR-005; ADR-0076 decisions 1 to 9):
# listActivationVocabularyTerms, getActivationVocabularyContextive and listActivationVocabularyTaboos read the terms of
# the glossary contexts in force, each context the copy the layer merge keeps, whole. The 400, 401, 403 and 500 answers
# of the three operations are asserted in read-refusals.feature, every-operation.feature and unexpected-failure.feature
# too.
#
# Fixture: core (built-in) holds spec-kitty-core with mission and the deprecated lane, planning with its own mission,
# and qa; proj (project) holds its own copy of spec-kitty-core with mission alone, which wins whole. a-1 builds on both,
# selects spec-kitty-core and planning and deactivates qa; a-2 builds on core alone with the same selection, the
# positive control of the whole override; a-3 builds on core and selects no glossary.

@contract @activations
Feature: The vocabulary in force under an activation is read as terms, a Contextive file and a taboo list
  The terms in force under an activation are the terms of the winning copy of each glossary context in force: a project
  copy replaces a built-in one whole, so a term only the replaced copy holds is not listed. Every status is listed. Two
  contexts that define one name give two terms. The Contextive file and the taboo list are projections of those terms.
  Each read answers its own ETag and a 304 to a matching If-None-Match, judged after its refusals; the Contextive file
  is application/yaml, and an Accept that excludes it is a 406.

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
          name: Mission
          definition: A unit of planned work, as the built-in pack defines it.
          avoid: [epic]
          status: active
        - id: lane
          name: lane
          definition: The old name of a work package's worktree.
          avoid: [track]
          status: deprecated
      """
    And the built-in pack "core" with the file "glossaries/planning.glossary.yaml":
      """
      schema_version: '1.0'
      id: planning
      name: Planning
      description: The vocabulary of roadmaps.
      applies_to:
        paths: [docs/plans/**]
      terms:
        - id: mission
          name: Mission
          definition: A milestone of a roadmap, its étape.
          avoid: [epic]
          status: draft
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
          avoid: [random failure]
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
      applies_to:
        paths: [kitty-specs/**]
      terms:
        - id: mission
          name: Mission
          definition: A unit of planned work, as this project defines it.
          examples: [Each mission has one specification.]
          aliases: [feature]
          avoid: [epic]
          status: active
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"glossary": ["spec-kitty-core", "planning"]}, "deactivated": {"glossary": ["qa"]}}
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}], "selected": {"glossary": ["spec-kitty-core", "planning"]}}
      """
    And the activation "a-3" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}]}
      """

  Scenario: The project copy of a context wins whole, so the terms lack lane; a shared name is two terms
    spec-kitty-core is held by core, with mission and lane, and by proj, with mission alone, which replaces it at the
    project level: lane, which only core's copy holds, is not in force. planning's Mission and spec-kitty-core's Mission
    share a name and are two terms. qa is deactivated and gives none. The terms are by context id, then term id.
    When I GET "/activations/a-1/vocabulary/terms"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the field at "/activationId" is "a-1"
    And the field at "/terms" holds exactly 2 items
    And the field at "/terms/0/urn" is "term:planning/mission"
    And the field at "/terms/0/contextId" is "planning"
    And the field at "/terms/0/id" is "mission"
    And the field at "/terms/0/name" is "Mission"
    And the field at "/terms/0/status" is "draft"
    And the field at "/terms/0/packId" is "core"
    And the field at "/terms/0/level" is "built-in"
    And the field at "/terms/0/examples" is absent
    And the field at "/terms/1/urn" is "term:spec-kitty-core/mission"
    And the field at "/terms/1/name" is "Mission"
    And the field at "/terms/1/definition" is "A unit of planned work, as this project defines it."
    And the field at "/terms/1/packId" is "proj"
    And the field at "/terms/1/level" is "project"
    And the field at "/terms/1/examples/0" is "Each mission has one specification."
    And the field at "/terms/1/aliases/0" is "feature"
    And the field at "/terms/1/avoid/0" is "epic"
    And the field at "/terms/1/_links/effective-term/href" is "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core/terms/mission"
    And the field at "/terms/1/_links/effective-glossary/href" is "/api/v1/activations/a-1/artifacts/glossary/spec-kitty-core"
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/vocabulary/terms"
    And the field at "/_links/activation/href" is "/api/v1/activations/a-1"
    And following the link "effective-term" of every element of "terms" answers 200
    When I GET "/activations/a-1/vocabulary/terms"
    Then following the link "effective-glossary" of every element of "terms" answers 200

  Scenario: Without the project pack, the built-in copy is in force and its deprecated term lane is listed (the positive control)
    When I GET "/activations/a-2/vocabulary/terms"
    Then the response status is 200
    And the field at "/terms" holds exactly 3 items
    And the field at "/terms/0/urn" is "term:planning/mission"
    And the field at "/terms/1/urn" is "term:spec-kitty-core/lane"
    And the field at "/terms/1/status" is "deprecated"
    And the field at "/terms/1/packId" is "core"
    And the field at "/terms/2/urn" is "term:spec-kitty-core/mission"
    And the field at "/terms/2/definition" is "A unit of planned work, as the built-in pack defines it."

  Scenario: The Contextive file is application/yaml, the contexts in force by id with their terms by id
    When I GET "/activations/a-1/vocabulary/contextive"
    Then the response status is 200
    And the response header "Content-Type" is "application/yaml"
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is a Contextive v1 document
    And the response body is the text:
      """
      contexts:
      - name: Planning
        domainVisionStatement: The vocabulary of roadmaps.
        paths:
        - docs/plans/**
        terms:
        - name: Mission
          definition: A milestone of a roadmap, its étape.
          meta:
            urn: term:planning/mission
            status: draft
      - name: Spec Kitty core
        domainVisionStatement: Canonical Spec Kitty terminology, as this project uses it.
        paths:
        - kitty-specs/**
        terms:
        - name: Mission
          definition: A unit of planned work, as this project defines it.
          examples:
          - Each mission has one specification.
          aliases:
          - feature
          meta:
            urn: term:spec-kitty-core/mission
            status: active
      """

  Scenario: The taboo list has one entry per avoided word per term, so a word two terms avoid appears twice, in order
    epic is avoided by planning's Mission and by spec-kitty-core's Mission. qa is deactivated, so random failure is not
    listed; under a-2, lane is in force and its deprecated term's track is listed, after epic.
    When I GET "/activations/a-1/vocabulary/taboos"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/activationId" is "a-1"
    And the field at "/entries" holds exactly 2 items
    And the field at "/entries/0/word" is "epic"
    And the field at "/entries/0/urn" is "term:planning/mission"
    And the field at "/entries/0/termName" is "Mission"
    And the field at "/entries/1/word" is "epic"
    And the field at "/entries/1/urn" is "term:spec-kitty-core/mission"
    And the field at "/entries/1/termName" is "Mission"
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/vocabulary/taboos"
    And the field at "/_links/activation/href" is "/api/v1/activations/a-1"
    When I GET "/activations/a-2/vocabulary/taboos"
    Then the response status is 200
    And the field at "/entries" holds exactly 3 items
    And the field at "/entries/0/urn" is "term:planning/mission"
    And the field at "/entries/1/urn" is "term:spec-kitty-core/mission"
    And the field at "/entries/2/word" is "track"
    And the field at "/entries/2/urn" is "term:spec-kitty-core/lane"
    And the field at "/entries/2/termName" is "lane"

  Scenario Outline: Over one activation, the Contextive URNs are the terms' URNs and the taboo entries are the terms' avoid entries (SC-004, <activation>)
    When I GET "/activations/<activation>/vocabulary/terms"
    Then the response status is 200
    When I remember the URNs and the avoid entries of the terms
    And I GET "/activations/<activation>/vocabulary/contextive"
    Then the response status is 200
    And the meta.urn values of the Contextive document are the remembered URNs
    When I GET "/activations/<activation>/vocabulary/taboos"
    Then the response status is 200
    And the taboo entries are the remembered avoid entries

    Examples:
      | activation |
      | a-1        |
      | a-2        |
      | a-3        |

  Scenario: An activation with no glossary context in force answers empty lists and an empty contexts list
    When I GET "/activations/a-3/vocabulary/terms"
    Then the response status is 200
    And the field at "/terms" holds exactly 0 items
    When I GET "/activations/a-3/vocabulary/taboos"
    Then the response status is 200
    And the field at "/entries" holds exactly 0 items
    When I GET "/activations/a-3/vocabulary/contextive"
    Then the response status is 200
    And the response body is a Contextive v1 document
    And the response body is the text:
      """
      contexts: []
      """

  Scenario: Every term of the built-in example vocabulary is served, and the projections are of them (NFR-005)
    The built-in example pack holds spec-kitty-core with 139 terms of every status. The read count per winning pack is
    pinned by ExampleVocabularyInForceTest; here the whole service serves every term.
    Given the example pack "built-in" of the charter
    And the activation "a-9" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"glossary": ["spec-kitty-core"]}}
      """
    When I GET "/activations/a-9/vocabulary/terms"
    Then the response status is 200
    And the field at "/terms" holds exactly 139 items
    When I remember the URNs and the avoid entries of the terms
    And I GET "/activations/a-9/vocabulary/contextive"
    Then the response status is 200
    And the response body is a Contextive v1 document
    And the meta.urn values of the Contextive document are the remembered URNs
    When I GET "/activations/a-9/vocabulary/taboos"
    Then the response status is 200
    And the taboo entries are the remembered avoid entries

  Scenario Outline: <operation> answers 304 to a matching If-None-Match, and its tag is not the tag of <other>
    When I GET "<path>"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 304
    When I GET "<other path>" with If-None-Match the remembered header "ETag"
    Then the response status is 200

    Examples:
      | operation                         | other          | path                                   | other path                             |
      | listActivationVocabularyTerms     | the taboo list | /activations/a-1/vocabulary/terms      | /activations/a-1/vocabulary/taboos     |
      | getActivationVocabularyContextive | the terms      | /activations/a-1/vocabulary/contextive | /activations/a-1/vocabulary/terms      |
      | listActivationVocabularyTaboos    | the Contextive | /activations/a-1/vocabulary/taboos     | /activations/a-1/vocabulary/contextive |

  Scenario: An Accept that excludes application/yaml is a 406 on the Contextive file, a Problem with no code
    When I GET "/activations/a-1/vocabulary/contextive" accepting "application/json"
    Then the response status is 406
    And the problem has no code
    And the response header "Charter-Contract-Version" is the contract version
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/activations/a-1/vocabulary/contextive" accepting "application/yaml"
    Then the response status is 200

  Scenario Outline: <operation> under an activation that is not stored is 404 activation_not_found, also against If-None-Match
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "activation_not_found"

    Examples:
      | operation                         | path                                    |
      | listActivationVocabularyTerms     | /activations/nope/vocabulary/terms      |
      | getActivationVocabularyContextive | /activations/nope/vocabulary/contextive |
      | listActivationVocabularyTaboos    | /activations/nope/vocabulary/taboos     |

  Scenario Outline: <operation> over two packs of one level that hold one context is refused, before any If-None-Match
    The org packs left and right both hold the context shared, with no settlement: the closure cannot merge. The
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
    And the stored file of the activation "a-4" holds:
      """
      {"packs":[{"packId":"left"},{"packId":"right"}],"schemaVersion":2}
      """
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"

    Examples:
      | operation                         | path                                   |
      | listActivationVocabularyTerms     | /activations/a-4/vocabulary/terms      |
      | getActivationVocabularyContextive | /activations/a-4/vocabulary/contextive |
      | listActivationVocabularyTaboos    | /activations/a-4/vocabulary/taboos     |

  Scenario Outline: The activation links its vocabulary read <relation>, and the link answers
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/_links/<relation>/href" is "/api/v1/activations/a-1/vocabulary/<leaf>"
    When I follow the link "<relation>"
    Then the response status is 200

    Examples:
      | relation              | leaf       |
      | vocabulary-terms      | terms      |
      | vocabulary-contextive | contextive |
      | vocabulary-taboos     | taboos     |

  Scenario Outline: After its glossary context is edited, <operation> answers 200 with a new ETag to the old tag
    The tag covers the packs of the activation's closure: acme's context g is edited and acme 1.1.0 is registered over
    1.0.0, so the tag taken before no longer matches. The 304 with the old tag before the re-registration is the control
    that the tag matched until then.
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "glossaries/g.glossary.yaml":
      """
      schema_version: '1.0'
      id: g
      name: G
      description: The context the edit changes.
      terms:
        - id: word
          name: word
          definition: The word, at version 1.
          avoid: [term]
          status: active
      """
    And the archive "acme-v2" is a copy of the archive "acme-v1"
    And the archive "acme-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v2" holds the file "glossaries/g.glossary.yaml":
      """
      schema_version: '1.0'
      id: g
      name: G
      description: The context the edit changes.
      terms:
        - id: word
          name: word
          definition: The word, at version 2.
          avoid: [term]
          status: active
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-5" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"glossary": ["g"]}}
      """
    Then the response status is 201
    When I GET "<path>"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    When I PUT "/packs/acme" with the archive "acme-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header

    Examples:
      | operation                         | path                                             |
      | listActivationVocabularyTerms     | /activations/a-5/vocabulary/terms                |
      | getActivationVocabularyContextive | /activations/a-5/vocabulary/contextive           |
      | getEffectiveGlossaryTerm          | /activations/a-5/artifacts/glossary/g/terms/word |
