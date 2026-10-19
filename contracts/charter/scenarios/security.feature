@contract
Feature: The worktree token and the refusal of browsers
  The service answers one client, the launcher's core process of one worktree, which holds the
  worktree token (ADR-0011, DM D9). A request without that token is a 401. A request a browser
  makes is a 403, whatever it carries: an Origin or Sec-Fetch header, or a Host whose name is not
  a loopback name, which is how DNS rebinding reaches a loopback service. Both refusals are the
  shared Problem without a charter code, carry the contract version and the worktree and never
  the source fingerprint, and are answered before any pack file is read.
  The steps send the suite's token unless the scenario says otherwise; "{token}" in a header
  value stands for it.

  Background:
    Given the built-in pack "built-in" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:software-dev
        kind: mission_type
        label: software-dev
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """

  Scenario: A request without a token is a 401 that asks for a bearer token
    When I GET "/packs" without the worktree token
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"
    And the response body is:
      """
      {"title": "Unauthorized", "status": 401}
      """
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: The info operation needs the token too
    When I GET "/info" without the worktree token
    Then the response status is 401
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A wrong token is a 401
    When I GET "/info" with the Authorization "Bearer not-the-worktree-token-not-the-worktree-token"
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario Outline: A malformed Authorization header is a 401
    When I GET "/info" with the Authorization "<header>"
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"
    And the response body is:
      """
      {"title": "Unauthorized", "status": 401}
      """

    Examples:
      | header                |
      | Basic dXNlcjpwYXNzd2Q= |
      | Bearer                |
      | Bearer {token} extra  |
      | Token {token}         |
      | {token}               |

  Scenario Outline: The Bearer scheme is matched without regard to case
    When I GET "/info" with the Authorization "<scheme> {token}"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once

    Examples:
      | scheme |
      | Bearer |
      | bearer |
      | BEARER |

  Scenario: An unknown path is a 401 before it is a 404
    The routes of the service are not disclosed to a client that holds no token.
    When I GET "/no-such-path" without the worktree token
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"

  Scenario: An unknown path with the token is still a 404
    When I GET "/no-such-path"
    Then the response status is 404
    And the response has no "WWW-Authenticate" header

  Scenario: A request with an Origin header is a 403 even with the right token
    When I GET "/info" with the header "Origin": "https://evil.example"
    Then the response status is 403
    And the response body is:
      """
      {"title": "Forbidden", "status": 403}
      """
    And the response has no "WWW-Authenticate" header
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario Outline: A request with a Sec-Fetch header is a 403 even with the right token
    When I GET "/packs" with the header "<header>": "<value>"
    Then the response status is 403
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | header         | value      |
      | Sec-Fetch-Site | cross-site |
      | Sec-Fetch-Site | same-origin |
      | Sec-Fetch-Mode | navigate   |
      | Sec-Fetch-Mode | cors       |

  Scenario Outline: A host name that is not a loopback name is a 403 even with the right token
    A same-origin GET carries no Origin, so the Host check is what stops DNS rebinding. The name alone is
    judged, never the port.
    When I GET "/info" with the header "Host": "<host>"
    Then the response status is 403
    And the response body is:
      """
      {"title": "Forbidden", "status": 403}
      """
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | host                           |
      | attacker.example               |
      | attacker.example:8080          |
      | localhost.attacker.example     |
      | 127.0.0.1.attacker.example     |
      | 192.168.1.5                    |
      | [::2]:8080                     |

  Scenario Outline: A loopback host name is accepted at any port
    When I GET "/info" with the header "Host": "<host>"
    Then the response status is 200

    Examples:
      | host            |
      | localhost       |
      | LOCALHOST:8080  |
      | 127.0.0.1:54321 |
      | [::1]:8080      |

  Scenario: The browser refusal comes before the token check
    A browser request without a token is a 403, not a 401: it is not told that a token would let it in.
    When I GET "/info" without the worktree token and with the header "Origin": "https://evil.example"
    Then the response status is 403
    And the response has no "WWW-Authenticate" header

  Scenario: A rebound host with a wrong token is a 403
    When I GET "/info" with the headers "Host": "attacker.example" and "Authorization": "Bearer wrong"
    Then the response status is 403
