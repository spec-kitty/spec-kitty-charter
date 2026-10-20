---
okf_version: "0.2"
title: HTTP status codes
description: Which HTTP status the charter API answers with, what each one means here, the body it carries and whether a client may retry.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: Guide
kind: Reference
related:
  - development/rest-resource-conventions
  - development/hypermedia-hateoas
  - adr/0018-action-context-read-and-seam-access-surface
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you choose a status or a refusal code for a new response.
  - profile: contract-consumer
    guidance_text: Read this to dispatch on a status and a body without guessing.
  - profile: core-maintainer
    guidance_text: Read this for how the seam should map each status and code onto core's errors.
---

# HTTP status codes

This page is the rule for status codes in the charter API. It describes the contract as it is:
`contracts/charter/schemas/CharterRefusalCode.yaml` (its `x-refusal` map gives every code its
status, whether a client may retry and whether this version raises it), the shared responses in
`contracts/charter/responses/`, and [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/).
Where the contract and this page disagree, the contract wins and this page is wrong.

## How a client dispatches

1. **2xx:** the success body the operation declares.
2. **Non-2xx with a `code` in the body:** a **charter refusal**, a `CharterRefusal` (`Problem` +
   `code`). Branch on `code`, never on `title`. The same request is refused the same way again
   unless the code is retryable (`internal_error`, `stale_input`) or the packs change.
3. **Non-2xx without a `code`:** a **transport or routing failure**, a plain `Problem`: an unknown
   path, a missing method, an unacceptable media type, a bad parameter, a missing token or a
   browser request. It says nothing about the charter.

Every non-2xx body is `application/problem+json`, and every response carries the `Charter-*`
headers ([REST conventions](/development/rest-resource-conventions/#headers-every-response-carries)).

## 2xx: success

| Code | Meaning | Used here | Body |
|---|---|---|---|
| 200 OK | The read or the action succeeded | Every operation | The declared success schema |
| 201 Created | A new resource exists; `Location` names it | Not used yet. Proposed for register (`POST /packs`), with `Location: /packs/{packId}` | The created resource |
| 202 Accepted | Work was queued, not done | Not used. Only if an action becomes asynchronous; it then links to a status resource | A status resource |
| 204 No Content | Success with nothing to return | Not used. Every operation answers a body | None |

## 3xx: redirection

| Code | Meaning | Used here | Body |
|---|---|---|---|
| 304 Not Modified | The client's cached copy is current (`If-None-Match` matched the `ETag`) | Not used yet. `Charter-Source-Fingerprint` and `governanceFingerprint` are the natural validators if conditional reads are added | None |

## 4xx: the request is wrong

| Code | Meaning | Used here | Body | Retry |
|---|---|---|---|---|
| 400 Bad Request | The request is malformed | A `depth` outside 0 to 2; a non-empty body on `POST /packs/refresh` | `Problem`, no code | No, fix the request |
| 401 Unauthorized | No valid credentials | No worktree token, a malformed `Authorization` header or a wrong token. Answered before routing, so an unknown path is a 401 before a 404. Carries `WWW-Authenticate: Bearer` | `Problem`, no code | After fixing the token |
| 403 Forbidden | Credentials do not matter: the request is refused | A browser request: an `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode` header, or a `Host` other than `localhost`, `127.0.0.1` or `[::1]`. Checked before the token | `Problem`, no code | No |
| 404 Not Found | The resource does not exist | A path the contract does not define (no code); a mission type or action that is not a graph node (`mission_type_not_found`, `action_not_found`) | `Problem`, or `CharterRefusal` | No |
| 405 Method Not Allowed | The path exists, the method does not | A method a path does not declare; carries `Allow` | `Problem`, no code | No |
| 406 Not Acceptable | No representation the client accepts | `Accept` excludes `application/json` | `Problem`, no code | No |
| 409 Conflict | The request conflicts with current state | Not used yet. The candidate for register when the pack id or slug is already taken | — | — |
| 410 Gone | The resource existed and was removed for good | Not used. Only if registered packs can be revoked and their ids are not reused | — | — |
| 412 Precondition Failed | An `If-Match` precondition failed | Not used. Only with conditional writes | — | — |
| 415 Unsupported Media Type | The request body's type is not accepted | Not used yet. Applies once an operation takes a body (register) | — | — |
| 422 Unprocessable Content | The body is well formed but invalid | Not used yet. The candidate for a register body that fails validation | — | — |
| 429 Too Many Requests | Rate limited; carries `Retry-After` | Not used. The service is per worktree, on loopback | — | — |

## 5xx: the service or its offering failed

| Code | Meaning | Used here | Body | Retry |
|---|---|---|---|---|
| 500 Internal Server Error | The service cannot answer | Every offering refusal: `pack_level_mismatch`, `pack_unreadable`, `drg_schema_error`, `unresolved_edge_endpoint` (not retryable); `internal_error` and `stale_input` (retryable). `governance_policy_violation` is reserved (500, not raised yet) | `CharterRefusal` | Only for `internal_error` and `stale_input` |
| 503 Service Unavailable | Temporarily unable to serve; may carry `Retry-After` | Not used. See the open questions | — | — |

`internal_error` is the only code for a failure no other code names. Its `detail` is a generic
sentence, and the failure is logged. Every 500 the contract answers is therefore a refusal with a
code.

## Rules for a new response

- **Pick the code from this page.** If none fits, raise it as a decision; do not invent a mapping
  in one operation.
- **A charter refusal gets a `code`;** a transport failure does not.
- **A new refusal code is a breaking change** once released (closed enums, ADR-0009). Reserve it
  before the release, with its `x-refusal` entry.
- **Declare every status** the operation can answer in the contract, with the `Charter-*` headers
  on each.

## Open questions

These are for review, not decisions. They belong to the follow-up of [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/), which proposes the
operations that will need new codes.

1. **Offering-configuration refusals are 500.** `pack_level_mismatch`, `pack_unreadable`,
   `drg_schema_error` and `unresolved_edge_endpoint` describe a misconfigured offering. A 500 is
   defensible: the request was fine, and the server's configuration is not. The alternative view
   answers 503 (the service cannot serve until the packs are fixed) or 422 (the configured input is
   invalid).
2. **`stale_input` is a retryable 500.** The conventional alternatives are 503 with `Retry-After`,
   or 409 (the state changed under the request).
3. **Register and distribute refusals.** ADR-0007 decision 7 names `PACK_ENGINE_INCOMPATIBLE`,
   `INTEGRITY_FAILURE` and `SOURCE_UNREACHABLE`. Which status each takes is open: 422 or 409 for an
   incompatible or conflicting pack, 502 for an unreachable source.
