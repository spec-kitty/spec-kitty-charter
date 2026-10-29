# Research: Serve the contract document

Grounding squad (2026-10-29): a code-map lens and an ADR lens, both read-only.

## R1 — Digest

- **Decision**: SHA-256 over the raw bundle bytes with `MessageDigest`, rendered `"sha256:<64 lowercase hex>"`
  (`contracts/charter/headers/ContractETag.yaml`).
- **Rationale**: it equals `sha256sum` and `bundleContract`'s `.sha256` file, so a client can check it against
  a release.
- **Alternatives**: `kernel.domain.Fingerprint` — rejected: it length-prefixes each field, so its digest is not
  the file's.

## R2 — Body type

- **Decision**: keep the generated `ResponseEntity<String>`; decode the bytes as strict UTF-8 at startup
  (malformed input fails the start); set `Content-Type: application/yaml` on the entity so the converter adds
  no `charset` and writes UTF-8.
- **Rationale**: no contract or generator change (C-001); RFC 9512 defines no `charset` parameter. The body
  is UTF-8 because Boot's `spring.http.converters.string-encoding-charset` defaults to UTF-8 (Spring's own
  default is ISO-8859-1); a test sends non-ASCII text through the real converter chain to pin it.
- **Alternatives**: `format: binary` in the contract (contract change), a generator `typeMappings` entry
  (shared build change for one operation) — rejected.

## R3 — Conditional request

- **Decision**: the service's own matcher is the **only** evaluator. It parses `If-None-Match` per the
  contract (`*`, or a list of entity tags compared weakly; anything not an entity-tag list matches nothing)
  and answers 304 itself. The 200 `ResponseEntity` carries **no** ETag, so Spring's built-in conditional check
  (`HttpEntityMethodProcessor.isResourceNotModified`, which runs on every GET/HEAD 200 with an ETag) has
  nothing to compare; a `ResponseBodyAdvice` scoped to `getOpenApiDocument` adds the ETag when the body is
  written, which is after that check. The 304 entity carries the ETag itself (Spring checks 200s only).
- **Rationale** (Spring 7.0.9 bytecode, plan squad): `*` on a GET is not a match in Spring
  (`ServletWebRequest.matchRequestedETags` short-circuits the wildcard for unsafe methods only), and
  `ETag.parse` skips unparseable characters, so `garbage "sha256:…"` and lower-case `w/"sha256:…"` would be
  304 where the contract says 200. Two evaluators would be a split brain.
- **Alternatives**: Spring as the only evaluator (breaks the contract's `*` and malformed rules); a request
  wrapper hiding `If-None-Match` from Spring (hides the header from the controller too, more moving parts).

## R4 — Packaging

- **Decision**: `:contracts` publishes a consumable `contractBundle` variant (custom `Category`) carrying
  `bundleContract`'s output; `:deployable` resolves it and `processResources` copies it beside
  `ContractDocumentWiring`. The deployable tests receive the resolved file path as a system property to
  compare bytes against the build output itself.
- **Rationale**: configuration-cache safe; follows the repo's "variants, never tasks" convention.
- **Alternatives**: read `build/contracts/contract/` by path (hidden task dependency); copy the file into
  `src/main/resources` (a hand copy) — rejected.

## R5 — Guards

- **Finding**: `WorktreeGuard` is registered at `HIGHEST_PRECEDENCE` for `/*` and answers 403/401 itself
  before the chain; `CharterHeaders` (lowest precedence) adds the contract version and worktree headers to
  every answer that passes. Nothing to change; tests prove they apply to this operation.

## R6 — Fail closed

- **Decision**: a missing or non-UTF-8 resource throws `IllegalStateException` from the bean method, so the
  context does not start (ADR-0011 D1; the `charter.project-config-dir` precedent).

## Plan squad dispositions (2026-10-29: architect-alphonso, reviewer-renata)

| # | Finding | Disposition | Evidence |
|---|---------|-------------|----------|
| A1 / R1 | Two `If-None-Match` evaluators; Spring 304s malformed and `w/` values | accepted | R3; tasks WP01 (malformed → 200, `w/` → 200 cases) |
| A2 | "validated" means `oasdiff validate` in `bundleContract`; lint and closure checks are not upstream of the variant | accepted (recorded) | plan IC-01 risks |
| A3 | Blast radius: `:charter-reports:javadoc`, `:deployable:test/bootRun/bootJar` now need the tool downloads; a future container build (#228) too | accepted | plan IC-01 risks |
| A4 | `ApiConfiguration` / `DeployableConfiguration` Javadocs promise slices; `InfoController` now needs a deployable bean | accepted | WP01 subtask |
| A5 / R7 | ETag and `info.version` checks must be independent of `ContractDocument` / `ContractVersion` | accepted | WP01: test reads the `.sha256` file and parses `info.version` from the build output |
| R2 | 304 cases with an exact tag would pass on Spring's check alone | changed | Spring's check is now off the path (R3); matcher unit-tested with fixture tags |
| R3 | 401/403 tests vacuous without a positive control; must hit `/api/v1/openapi.yaml`; no ETag on refusals | accepted | WP01 tests share one request builder with the 200 test |
| R4 | `Accept: application/problem+json` gets 200 YAML (preset content type skips negotiation) | deferred_with_rationale | RFC 9110 §12.5.1 lets a server disregard `Accept`; same for every operation that lists problem+json; #210 scenarios decide; noted in PR |
| R5 | UTF-8 comes from Boot's property | accepted | R2; non-ASCII fixture test through the converter chain |
| R6 | Bundle has 12 non-ASCII bytes only by accident | accepted | same fixture test |
| R8 | Fingerprint-absence assertion only beside positive 200 assertions | accepted | WP01 tests |
| R9 | HEAD unspecified | accepted | WP01: HEAD 200 no body with ETag, HEAD 304 |
| R10 | Several `If-None-Match` lines | accepted | WP01: two-line case |
| R11 | NFR-002 by structure, not a counter | accepted | digest computed in the constructor of an immutable value built by one bean |
| A-notes | ETag sources are one digest; resource path beside the wiring | accepted (no change) | — |

## Known gaps handed on

- Cucumber scenarios (#210): `CharterSteps`' `@After` conformance hook expects JSON bodies and a fingerprint on
  every GET 200; both need a carve-out there for this operation.
- A gateway stripping the `ETag` on the hosted path (#209 risk): no hosted deployment is in this repo to check;
  recorded in the PR hand-off.

## Pre-PR squad dispositions (2026-10-29: reviewer-renata, architect-alphonso)

| # | Finding | Disposition | Evidence |
|---|---------|-------------|----------|
| P1 | The own-matcher + advice pattern is recorded only here; ADR-0018 calls this operation the pattern for later conditional reads | accepted | ADR-0018, dated note 2026-10-29 |
| P2 | Textual overlap with #228 (`service/deployable/build.gradle.kts`, changelog hub); a container image (#234) inherits the vacuum/oasdiff download through `bootJar` | accepted | PR body and hand-off |
| P3 | HEAD assertions thinner than GET | accepted | `OpenApiDocumentTest#headAnswersAsGet` |
| P4 | No test of a `default` error on the route (406) carrying no ETag | accepted | `OpenApiDocumentTest#anAcceptItCannotAnswerIsA406ProblemWithoutTheETag` |
| P5 | `ContractDocumentETag.supports` matches by method name | deferred_with_rationale | a rename drops the ETag and the exact `containsExactly(ETAG)` assertion on the 200 fails; no further code earns its keep |
| P6 | `CharterSteps` `@After` hooks expect JSON and a fingerprint on every GET 200 | deferred_with_rationale | #210's scope; raised in the hand-off |
| P7 | Variant attribute string written in two scripts | deferred_with_rationale | two call sites; a build-logic constant is more code than the drift risk |
| WP01 review LOWs | `*` whitespace, unreadable-bundle exception, long Javadoc line, exact 401/403 bodies | accepted | fold commit |
