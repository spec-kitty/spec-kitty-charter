# The 501 pins of the whole-pack registration operations still reserved (ADR-0065; ADR-0020 D8). WP16 built the PUT, the
# stored-pack read and the status read, and WP17 built POST /packs for the https_archive and filesystem sources (the git
# source answers 503 pack_source_unavailable until WP18 builds it); each deleted its pin with its x-reserved and 501. The
# two that remain, the DELETEs of an artifact and of a relationship, are declared in full and reserved (x-reserved,
# contract set pack-registration): until a serving work package builds one, PackRegistrationController answers it 501, a
# Problem with no code, before the request is bound. ReservedOperationCoverageTest keeps these pins and the reserved
# operations of the set one to one, and requires an operation that takes a body to be pinned before its body is bound
# (neither DELETE takes one).
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
  Scenario: Removing an artifact from a pack is reserved
    When I DELETE "/packs/acme/artifacts/directive/DIRECTIVE_001" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

  @reserved
  Scenario: Removing a registered relationship from a pack is reserved
    When I DELETE "/packs/acme/relationships?from=directive:A&to=tactic:B" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented
