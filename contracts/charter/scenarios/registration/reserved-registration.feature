# The 501 pins of the whole-pack registration operations still reserved (ADR-0065; ADR-0020 D8). WP16 built the PUT, the
# stored-pack read and the status read, and deleted their pins with their x-reserved and 501; the three that remain
# (POST /packs, and the two DELETEs) are declared in full and reserved (x-reserved, contract set pack-registration):
# until a serving work package builds one, PackRegistrationController answers it 501, a Problem with no code, before the
# request is bound. ReservedOperationCoverageTest keeps these pins and the reserved operations of the set one to one, and
# requires the operation that takes a body (POST /packs) to be pinned before its body is bound.
# Each pin is deleted by the work package that builds its operation, together with the operation's x-reserved and 501.
# The behavior scenarios of the operations are the @WIP scenarios of whole-pack-registration.feature and
# pack-removal.feature. These pins are not @WIP: they run in the suite and do not count toward NFR-002.
# Issue: #10.

@contract @registration
Feature: The whole-pack registration operations still reserved answer 501
  Until its service is built, each operation of the whole-pack registration answers 501 Not Implemented
  with a Problem that has no code, before it reads its request (ADR-0020 D8). Each scenario below is a 501 pin: it sends
  a well-formed request to one operation and is removed when that operation is built.

  @reserved
  Scenario: Registering a pack from a source is reserved before its body is bound
    The body of this request cannot be bound (a number where the schema has a string, a missing kind): without the
    interceptor it is a 400 before the operation runs. While the operation is reserved it is answered 501.
    When I POST "/packs" as JSON with the body:
      """
      {"kind": 5}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Removing an artifact from a pack is reserved
    When I DELETE "/packs/acme/artifacts/directive/DIRECTIVE_001" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

  @reserved
  Scenario: Removing a registered relationship from a pack is reserved
    When I DELETE "/packs/acme/relationships?from=directive:A&to=tactic:B" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented
