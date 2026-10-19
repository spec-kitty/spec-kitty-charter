@contract
Feature: Failures outside the charter's refusals
  A request the service cannot answer for a reason that is not a charter refusal is answered with
  a Problem body too, never with a framework's own error document. A 4xx Problem carries the
  title and the status of the HTTP status and nothing else: no code, because no refusal applies.
  Every 500 is a CharterRefusal: an unexpected failure is refused with internal_error and a
  generic detail that says nothing about the failure.

  Scenario: A path the contract does not define is answered with a not-found problem
    When I GET "/pack"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Not Found", "status": 404}
      """

  Scenario: A request that accepts no representation the operation has is answered with a not-acceptable problem
    The problem is served as application/problem+json although the request does not accept it,
    as RFC 9457 allows for an error response.
    When I GET "/packs" accepting "text/csv"
    Then the response status is 406
    And the response body is:
      """
      {"title": "Not Acceptable", "status": 406}
      """

  Scenario: An unexpected failure is refused with internal_error and says nothing about it
    Given the service fails unexpectedly while it reads the packs
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "internal_error"
    And the response body is:
      """
      {"title": "Internal error", "status": 500, "code": "internal_error", "detail": "The charter service failed unexpectedly; the failure is logged."}
      """
