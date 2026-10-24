@contract
Feature: The tactic reads answer with the source fingerprint they verified
  Every tactic read digests its inputs before and after it loads them (ADR-0018 D11). A 200
  carries that digest once as Charter-Source-Fingerprint, equal to what /info answers for the
  same inputs; inputs that change while the read is answered refuse it with stale_input, and
  no refusal carries the fingerprint.

  Background:
    Given the org pack "qa" with the file "rehearse.tactic.yaml":
      """
      id: rehearse
      schema_version: '1.0'
      name: Rehearse
      steps:
        - title: Only step
      """

  Scenario: A tactic list answers with the source fingerprint it verified
    When I GET "/packs/qa/tactics"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is a list with an element whose field "id" is "rehearse"

  Scenario: One tactic answers with the source fingerprint it verified
    When I GET "/packs/qa/tactics/rehearse"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field "id" of the response body is "rehearse"

  Scenario: A tactic list whose tactic file changes while it is answered is refused with stale_input
    Given a tactic file changes while the next read is answered
    When I GET "/packs/qa/tactics"
    Then the response status is 500
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the tactics were read; read them again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/qa/tactics"
    Then the response status is 200

  Scenario: One tactic whose file changes while it is answered is refused with stale_input
    Given a tactic file changes while the next read is answered
    When I GET "/packs/qa/tactics/rehearse"
    Then the response status is 500
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the tactic was read; read it again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/qa/tactics/rehearse"
    Then the response status is 200

  Scenario: A read of an absent tactic whose inputs change is stale, not not-found
    Given a tactic file changes while the next read is answered
    When I GET "/packs/qa/tactics/no-such-tactic"
    Then the response status is 500
    And the problem code is "stale_input"
    When I GET "/packs/qa/tactics/no-such-tactic"
    Then the response status is 404
    And the problem code is "tactic_not_found"

  Scenario: A tactic read that watches for a change and sees none answers with the digest it verified
    Given no pack file changes while the next read is answered
    When I GET "/packs/qa/tactics"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once

  Scenario: A tactic read of a pack whose directory is missing is refused with pack_unreadable
    Given the directory of the pack "qa" is missing
    When I GET "/packs/qa/tactics"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A missing sibling pack directory refuses a tactic read of a healthy pack
    Given the org pack "other" with the file "notes.md":
      """
      Nothing to see.
      """
    And the directory of the pack "other" is missing
    When I GET "/packs/qa/tactics"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/info"
    Then the response status is 500
    And the problem code is "pack_unreadable"
