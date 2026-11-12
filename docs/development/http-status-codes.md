---
okf_version: "0.2"
title: HTTP status codes
description: Which HTTP status the charter API answers with, what each one means here, the body it carries and whether a client may retry.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Guide
kind: Reference
related:
  - development/rest-resource-conventions
  - development/hypermedia-hateoas
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0020-offering-and-activations
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
Where the contract and this page disagree, the contract wins and this page is wrong. Rows marked
*proposed* come from [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) and are not in the contract yet.
Rows marked *reserved* are the activation set of [ADR-0020](/adr/0020-offering-and-activations/): declared in the
contract (#125), answered `501` until each operation is built.

## How a client dispatches

1. **2xx:** the success body the operation declares.
2. **Non-2xx with a `code` in the body:** a **charter refusal**, a `CharterRefusal` (`Problem` +
   `code`). Branch on `code`, never on `title`. The same request is refused the same way again
   unless the code is retryable (`internal_error`, `stale_input`) or the packs change.
3. **Non-2xx without a `code`:** a **transport or routing failure**, a plain `Problem`: an unknown
   path, a missing method, an unacceptable media type, a bad parameter, a missing token, a
   browser request, a failed precondition or an operation not built yet. It says nothing about
   the charter.

Retry classes: **no** (fix the request), **yes** (`internal_error`, `stale_input`), and **after a
re-read** (412: fetch the resource again, then write against the new `ETag`).

Every non-2xx body is `application/problem+json`. Every response declares the `Charter-*` headers,
except the servlet container's own 400s
([REST conventions](/development/rest-resource-conventions/#headers-every-response-carries)).

## 2xx: success

| Code | Meaning | Used here | Body |
|---|---|---|---|
| 200 OK | The read or the action succeeded | Every built operation. Built: `getActivation`, `listActivationPacks` and a `replaceActivation` that replaces, with `ETag`. Built: the five sub-resource writes, which answer an `ActivationChange` with the new revision as the `ETag` | The declared success schema |
| 201 Created | A new resource exists; `Location` names it | Every registration that creates. Built: an activation created by `PUT` or `POST`, with `Location` and `ETag` | The created resource |
| 202 Accepted | Work was queued, not done | Built: `registerPackFromArchive` (`PUT /packs/{packId}`) and `registerPackFromSource` (`POST /packs`), once the synchronous checks pass. The body is a `PackRegistrationStatus` (`pending`) and `Location` is the URI of `GET /pack-registrations/{registrationId}`, which the client polls until `succeeded` or `refused`. A refused registration answers `200` on that read, with the problem in the body | A status resource |
| 204 No Content | Success with nothing to return | Built: `DELETE` of an activation, and the two `DELETE`s of a pack (`deletePackArtifact`, `deletePackRelationship`), which carry the pack's new revision as the `ETag` (`no-content-only-on-delete`, `no-content-has-no-body`). Every other operation answers a body | None |

## 3xx: redirection

| Code | Meaning | Used here | Body |
|---|---|---|---|
| 304 Not Modified | The client's cached copy is current (`If-None-Match` matched the `ETag`) | `getOpenApiDocument` (`GET /openapi.yaml`): an `If-None-Match` that is `*` or matches the bundle's `ETag`. Built: `getActivation`, `listActivationPacks`, `listEffectiveArtifacts` and `getActivationSkippedReport`, when `If-None-Match` is `*` or lists a tag that weakly matches the answer's `ETag` (each covers the revision and the packs of the activation's closure; an effective read's also names its `kind`, `state`, cursor and page size); the answer keeps the `ETag` and has no body. Proposed: `Charter-Source-Fingerprint` and `governanceFingerprint` are the natural validators if more conditional reads are added. Built (slice C, 2026-11-12): `getEffectiveStepPrompt`, by the same rule, after every refusal of the read | None |

## 4xx: the request is wrong

| Code | Meaning | Used here | Body | Retry |
|---|---|---|---|---|
| 400 Bad Request | The request is malformed | A `depth` outside 0 to 2; a non-empty body on `POST /packs/refresh`. Built, on `getActivation`, `replaceActivation`, `deleteActivation`, `listActivationPacks`, `listEffectiveArtifacts`, `getActivationSkippedReport` and the sub-resource writes: an `activationId` outside its pattern; on a write, an `If-Match` that is neither `*` nor one quoted sha256 tag (the revision or the `getActivation` tag); on `listEffectiveArtifacts`, a cursor that belongs to another set (the activation's revision or a pack of its closure changed, or another `kind` or `state`), a malformed cursor, a `kind` or `state` outside its enum and a `pageSize` out of range; on `replaceActivation`, `If-Match` with `If-None-Match` on one request, or an `If-None-Match` other than `*`. Proposed: a body that is not valid JSON | `Problem`, no code | No, fix the request |
| 401 Unauthorized | No valid credentials | No worktree token, a malformed `Authorization` header or a wrong token. Answered before routing, so an unknown path is a 401 before a 404. Carries `WWW-Authenticate: Bearer` | `Problem`, no code | After fixing the token |
| 403 Forbidden | The request is refused whatever its credentials | A browser request: an `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode` header, or a `Host` other than `localhost`, `127.0.0.1` or `[::1]`. Checked before the token. Proposed: this guard moves to a local deployment profile (ADR-0020 D12); a token lacking a scope is a 403 | `Problem`, no code | No |
| 404 Not Found | The resource does not exist | A path the contract does not define (no code); a mission type or action that is not a graph node (`mission_type_not_found`, `action_not_found`). Built: `activation_not_found` on `getActivation`, `deleteActivation` and `listActivationPacks` (also for an activation the token may not see). Built, on the sub-resource writes: `activation_not_found`, `artifact_not_found` (`mission_type_not_found` for kind `mission_type`) and `preset_not_found`. Built (slice C, 2026-11-12), on `getEffectiveStepPrompt` as on `getEffectiveActionContext`: `activation_not_found`, then `mission_type_not_found` and `action_not_found`, judged on the merged closure whether or not the mission type is in force | `Problem`, or `CharterRefusal` | No |
| 405 Method Not Allowed | The path exists, the method does not | A method a path does not have; carries `Allow`. Service behavior: the contract does not declare it per path | `Problem`, no code | No |
| 406 Not Acceptable | No representation the client accepts | `Accept` excludes the operation's media type: `application/json`, or `application/yaml` for `getOpenApiDocument` | `Problem`, no code | No |
| 409 Conflict | The request conflicts with current state | Raised by the registration writes: `registration_conflict` (a duplicate id on POST, a relationship whose `from` does not resolve, a toolguide guide file another artifact claims, an artifact `DELETE` whose artifact an author-written graph document names, a registration of a built-in level pack) and `governance_policy_violation` (a write the owning pack's mutability level does not permit, ADR-0012; a `REMOVE` and an edge write on its source included, so the relationship `DELETE` can answer it). Also: `pack_version_conflict` (a version already registered with another revision), `artifact_referenced` (an artifact `DELETE` while another artifact or pack refers to it) and `relationship_not_registered` (a relationship `DELETE` of an edge the author wrote, a derived one, or one held both there and in the registered file). These three, like the registration conflicts above, answer in the problem of a refused registration status when the write is a registration. Built: `artifact_required`, `artifact_locked`: a deactivation the pack policy forbids, by a registered document (`createActivation`, `replaceActivation`) with a deny entry on a required or locked id; `requiredBy` names every requiring pack. Built: the same refusals for a deactivate call (`deactivateActivationArtifact`). Also register when the pack id is taken. Built (slice C, 2026-11-12): `mission_type_not_in_force`, raised by `getEffectiveStepPrompt` alone, for a mission type the activation's merged closure holds and the activation does not put in force; judged after the `404`s and before the closure's `500`s and any `304`. The action context answers `200` with an empty context for the same mission type: a step of a mission type that is not in force has no prompt to render ([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/), annotation of decision 9; operator ruling OD-5). The client fixes the activation, not the request | `CharterRefusal` | No |
| 410 Gone | The resource existed and was removed for good | Not used. Only if registered packs can be revoked and their ids are not reused | — | — |
| 412 Precondition Failed | `If-Match` or `If-None-Match` failed | Built: a `PUT` or `DELETE` whose `If-Match` names no stored revision, an id nothing is stored under included (RFC 9110 §13.1.1), or a create-only `PUT` on an existing activation; an `If-Match` that is a `getActivation` tag naming a closure a pack change has since altered (a stale closure tag), and one whose closure cannot be read or names a pack no longer configured, which has no current tag. On a pack: a stale `If-Match` on a registration or a `DELETE`, or `If-None-Match: *` on a pack that exists; and, in the status of a refused registration, a revision that moved after the `202`. A same-version re-send after a per-artifact `PUT` moved the revision is `409 pack_version_conflict`, not `412`: the version rule runs first | `Problem`, no code | After a re-read |
| 413 Content Too Large | The body is over the limit | Built: a pack archive over 32 MiB, and a `POST /packs` JSON body over the shared body limit. Answered before the body is read | `Problem`, no code | No, send less |
| 415 Unsupported Media Type | The request body's type is not accepted | Built: an activation write whose `Content-Type` is not `application/json`; a pack archive whose `Content-Type` is none of the four archive types, or whose leading bytes do not match it | `Problem`, no code | No |
| 422 Unprocessable Content | The body is well formed but invalid | `validation_failed`; and, on `putPackRelationship`, the three reference refusals `pack_reference_pack_missing`, `pack_reference_unqualified` and `pack_reference_artifact_missing`, when the relationship's `to` cannot resolve in the compose of the pack it is registered into (the same codes are 500 on the reads; the `operations` map of the code's `x-refusal` entry declares the 422, [ADR-0056](/adr/0056-a-refusal-code-may-declare-a-status-per-operation/)), with the `packs` member naming the pack registered into first. Also built: the composition codes (`pack_sibling_conflict` among them) and the relation codes (`override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`) on the per-artifact writes, the relationship write and both `DELETE`s, and in the status of a refused registration, because the strict impact check refuses a write after which a closure the pack joins no longer composes. `pack_source_refused` (with a fixed reason in the detail), an archive path or entry the extractor refuses and `validation_failed` with violations end a registration the same way. Built: `activation_invalid` on `createActivation` and `replaceActivation`: an unknown pack, kind or artifact, a pack pinned to a version the service does not hold, a deny key for an unknown kind included, a body over 1 MiB, an unknown member, a null or a duplicate key; with `violations` (JSON Pointer, code, detail). Built (slice C, 2026-11-12): `prompt_slot_missing`, when the write would leave a step enhancement in force without the slot it fills, on `getPackRegistration` (a refused registration), the per-artifact registrations, `putPackRelationship`, both pack `DELETE`s and the seven activation writes, each listed in the `operations` map of the code; for a pack write the detail names the stored activation under which the enhancement is in force. `override_not_permitted` also for an enhancement of a step whose mission type is locked. `pack_reference_pack_missing` and `pack_reference_artifact_missing` also for a step enhancement whose step the pack it names does not hold. `validation_failed` with the violation `overrides_source_not_activatable` for an `overrides` edge from a template, an asset, a guard or a lifecycle, at registration and on `putPackRelationship` | `CharterRefusal` | No |
| 428 Precondition Required | The write needs a precondition | Built: a sub-resource write on an activation without `If-Match`, both pack `DELETE`s and the per-artifact writes without `If-Match` (`*` satisfies it); and `POST /packs` with a git source whose ref is a branch and that names no commit, with neither `If-Match` nor `If-None-Match: *`. A whole-pack registration needs no `If-Match` | `Problem`, no code | After adding `If-Match` |
| 429 Too Many Requests | Rate limited; carries `Retry-After` | Not used. A hosted deployment may rate-limit; the contract does not declare it yet | — | — |

## 5xx: the service or its offering failed

| Code | Meaning | Used here | Body | Retry |
|---|---|---|---|---|
| 500 Internal Server Error | The service cannot answer | Every offering refusal: `pack_level_mismatch`, `pack_unreadable`, `drg_schema_error`, `unresolved_edge_endpoint` (not retryable); `internal_error` and `stale_input` (retryable). `pack_level_mismatch`, `drg_schema_error` and `unresolved_edge_endpoint` are declared but raised by no operation of this version. Also the eleven refusals of the pack composition, which refuse every composed read (the offering, and `listArtifactReferences`) because the configured packs do not compose, not the request: `pack_dependency_cycle`, `pack_dependency_unknown`, `pack_dependency_out_of_range`, `pack_parent_unresolved`, `pack_level_violation`, `pack_name_conflict`, `pack_duplicate_id`, `pack_sibling_conflict`, `pack_reference_unqualified`, `pack_reference_pack_missing`, `pack_reference_artifact_missing` (not retryable). On the activation set, built: `getActivation`, which also answers it for a document that names a pack no longer configured and for an in-force refusal, `listEffectiveArtifacts` and `getActivationSkippedReport` answer these and the layer-merge refusals (`pack_unreadable` for an override the mutability level forbids, an override cycle, an unstable answer or an `overrides` edge that ends at its own source), and the sub-resource writes (`selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`, `reactivateActivationArtifact`, `applyActivationPreset`) raise the composition and merge codes when the closure they walk does not compose or merge. Each names the violating pack in `detail` and lists the packs it names, that pack first, in the `packs` member (each with its `descriptorPackId`, its configured `packId` and its `name`). Built (slice C, 2026-11-12), on every composing read, `getEffectiveStepPrompt` included: `prompt_slot_missing` (not retryable) for a step enhancement in force whose slot the prompt in force of its step does not declare, judged after the composition codes and the merge's `pack_unreadable`; `pack_reference_pack_missing` and `pack_reference_artifact_missing` for a step enhancement of a step the pack it names does not hold; and `pack_unreadable` for an enhancement of a step whose mission type is locked, and for a stored `overrides` edge whose source is not activatable | `CharterRefusal` | Only for `internal_error` and `stale_input` |
| 501 Not Implemented | The operation is declared but not built | Every operation marked `x-reserved` (ADR-0020 D8): no operation of the activation set remains reserved: PR C (2026-11-09) built the effective graph and the effective action context, the last two (#125), so the interceptor holds no operation and keeps a planted-contract control for a later reservation. A reserved operation is answered before the request is read. The lint rules `reserved-operation-declares-501` and `built-operation-declares-no-501` pair the two. Never a 405: the contract declares the method. (`getOpenApiDocument` answered a 501 through `default` from #208 until #209 served the bundle.) | `Problem`, no code | No |
| 502 Bad Gateway | The upstream a source names failed | Built: in the status of a refused registration, a plain problem with no code, when the https archive or the git host fails | `Problem`, no code | Maybe, later |
| 503 Service Unavailable | Temporarily unable to serve; may carry `Retry-After` | Built, on a registration, on a per-artifact write, on the two pack `DELETE`s (`deletePackArtifact`, `deletePackRelationship`) and on the eight activation writes (`createActivation`, `replaceActivation`, `deleteActivation`, `selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`, `reactivateActivationArtifact`, `applyActivationPreset`): the registration queue is full or the registry lock was not free in time (no code; `Retry-After` goes with every 503 that has no code); registration is switched off (which refuses both pack `DELETE`s too), a source kind is switched off, or the system git is missing or older than 2.50.1 for a git source (`pack_source_unavailable`, no `Retry-After`); a volume has less than the per-job peak (about three times the archive bound) free, at admission or when the job runs (`pack_storage_full`, retryable) | `Problem` | After `Retry-After`, or after the operator acts |
| 504 Gateway Timeout | The upstream a source names did not answer in time, or a registration passed its deadline | Built, in the status of a refused registration: a plain problem with no code when the upstream did not answer, and `registration_timed_out` (retryable) when the job passed its deadline of 90 s (the 60 s source deadline plus 30 s, NFR-008), which counts the time it waited in the queue | `Problem`, or `CharterRefusal` | Yes, for `registration_timed_out`; maybe later for the plain one |

`internal_error` is the only code for a failure no other code names. Its `detail` is a generic
sentence, and the failure is logged at ERROR with its stack trace. `pack_unreadable` and the composition refusals
are logged at WARN, with the code, the detail and the packs they name and no stack trace: the configured packs are
broken, not the service. Every 500 the contract answers is therefore a refusal with a
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

These are for review, not decisions. They belong to the follow-up of ADR-0019 and ADR-0020.

1. **Offering-configuration refusals are 500.** `pack_level_mismatch`, `pack_unreadable`,
   `drg_schema_error`, `unresolved_edge_endpoint` and the eleven pack composition refusals (operator ruling of
   2026-11-03: a configured offering that does not compose is a server-side fault, not a bad request) describe a misconfigured offering. A 500 is
   defensible: the request was fine, and the server's configuration is not. The alternative view
   answers 503 (the service cannot serve until the packs are fixed) or 422 (the configured input is
   invalid).
2. **`stale_input` is a retryable 500.** The conventional alternatives are 503 with `Retry-After`,
   or 409 (the state changed under the request).
3. **Register and distribute refusals.** ADR-0007 decision 7 names three failures. On the
   wire they become snake_case codes: `pack_engine_incompatible`, `integrity_failure` and
   `source_unreachable`. Which status each takes is open: 422 or 409 for an
   incompatible or conflicting pack, 502 for an unreachable source.
