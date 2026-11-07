# The 501 pin of the whole-pack registration operation still reserved (ADR-0065; ADR-0020 D8). WP16 built the PUT, the
# stored-pack read and the status read, and WP19 the two DELETEs, and deleted their pins with their x-reserved and 501;
# the one that remains (POST /packs) is declared in full and reserved (x-reserved, contract set pack-registration):
# until a serving work package builds it, PackRegistrationController answers it 501, a Problem with no code, before the
# request is bound. ReservedOperationCoverageTest keeps this pin and the reserved operations of the set one to one, and
# requires the operation that takes a body (POST /packs) to be pinned before its body is bound.
# The pin is deleted by the work package that builds its operation, together with the operation's x-reserved and 501.
# The behavior scenarios of the operation are the @WIP scenarios of whole-pack-registration.feature. This pin is not
# @WIP: it runs in the suite and does not count toward NFR-002.
# Issue: #10.

@contract @registration
Feature: The whole-pack registration operations still reserved answer 501
  Until its service is built, each operation of the whole-pack registration answers 501 Not Implemented
  with a Problem that has no code, before it reads its request (ADR-0020 D8). The scenario below is a 501 pin: it sends
  a well-formed request to the operation and is removed when the operation is built.

  @reserved
  Scenario: Registering a pack from a source is reserved before its body is bound
    The body of this request cannot be bound (a number where the schema has a string, a missing kind): without the
    interceptor it is a 400 before the operation runs. While the operation is reserved it is answered 501.
    When I POST "/packs" as JSON with the body:
      """
      {"kind": 5}
      """
    Then the operation is refused as not implemented
