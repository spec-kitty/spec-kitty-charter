@contract @registration
Feature: A registration from a source answers each refusal it declares before a job exists
  registerPackFromSource (POST /packs) declares a 412, a 413 and a 415 that are answered at acceptance, before any
  registration job exists, and the switch gate (switch-gate.yaml, ADR-0059 D1) holds the operation ready only when a
  scenario asserts each of them on it. whole-pack-registration.feature states the same refusals for registerPackFromArchive;
  this file states them for the source body, each with the request that is accepted as its control:
  - If-None-Match: * on a pack that exists: 412, a Problem with no code;
  - a JSON body over the 1 MiB request body limit: 413, a Problem with no code, answered before the body is read;
  - a body that is not application/json: 415, a Problem with no code.

  Background:
    Given registration is switched on
    And the archive "v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: The directive the pack carries
      enforcement: required
      """
    And the filesystem source root holds the directory "packs/acme" with the archive "v1"

  Scenario: If-None-Match: * on a pack that exists is 412 before a job exists, and on a new pack it is accepted
    When I POST "/packs" as JSON with the header "If-None-Match": "*" and the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I POST "/packs" as JSON with the header "If-None-Match": "*" and the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 412
    And the problem has no code
    And the response has no "Location" header

  Scenario: A source body over the request body limit is 413 before it is read, and one under it is accepted
    The control is a small body, not one just under the limit as directives.feature's is: every field of a source
    is checked against its own grammar, so no field can be padded to the limit and still be accepted.
    When I POST "/packs" as JSON with the body, its "path" padded to 1048576 bytes:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 413
    And the problem has no code
    When I POST "/packs" as JSON with the header "If-None-Match": "*" and the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202

  Scenario: A source body that is not JSON is 415, and as JSON it is accepted
    When I send POST "/packs" as "text/plain" with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 415
    And the problem has no code
    When I send POST "/packs" as "application/json" with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202
