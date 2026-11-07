# The 501 pins of the whole-pack registration operations (ADR-0065; ADR-0020 D8). The six operations are declared in
# full in the contract and reserved (x-reserved, contract set pack-registration): until a serving work package builds
# one, PackRegistrationController answers it 501, a Problem with no code, before the request is bound.
# ReservedOperationCoverageTest keeps these pins and the reserved operations of the set one to one, and requires the
# two operations that take a body (PUT /packs/{packId}, POST /packs) to be pinned before their body is bound.
# Each pin is deleted by the work package that builds its operation, together with the operation's x-reserved and 501.
# The behavior scenarios of the operations are the @WIP scenarios of whole-pack-registration.feature and
# pack-removal.feature. These pins are not @WIP: they run in the suite and do not count toward NFR-002.
# Issue: #10.

@contract @registration
Feature: The whole-pack registration operations are reserved
  Until the registration service is built, each operation of the whole-pack registration answers 501 Not Implemented
  with a Problem that has no code, before it reads its request (ADR-0020 D8). Each scenario below is a 501 pin: it sends
  a well-formed request to one operation and is removed when that operation is built.

  @reserved
  Scenario: Registering a pack from an archive is reserved before its body is bound
    The archive body is required: a request with none is 400 once the body is bound. While the operation is reserved
    it is answered 501 before the body is looked at.
    When I PUT "/packs/acme" with the headers "Content-Type": "application/gzip" and "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

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
  Scenario: Reading a stored pack is reserved
    When I GET "/packs/acme"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading the status of a registration is reserved
    When I GET "/pack-registrations/01M6R0J6FZ2K9Y3B7C5D8E4G1H"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Removing an artifact from a pack is reserved
    When I DELETE "/packs/acme/artifacts/directive/DIRECTIVE_001" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

  @reserved
  Scenario: Removing a registered relationship from a pack is reserved
    When I DELETE "/packs/acme/relationships?from=directive:A&to=tactic:B" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented
