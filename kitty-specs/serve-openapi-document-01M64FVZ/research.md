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
- **Rationale**: no contract or generator change (C-001); RFC 9512 defines no `charset` parameter.
- **Alternatives**: `format: binary` in the contract (contract change), a generator `typeMappings` entry
  (shared build change for one operation) — rejected.

## R3 — Conditional request

- **Decision**: the service parses `If-None-Match` itself per the contract (`*`, or a list of entity tags
  compared weakly; anything not an entity-tag list matches nothing) and answers 304 itself.
- **Rationale**: the contract's `*` and malformed-value rules are explicit; Spring's built-in check is not
  documented to match them. Where Spring's own check on the 200 still applies, a test pins the observed
  answer.
- **Alternatives**: rely only on Spring's `checkNotModified` — rejected for the `*` uncertainty.

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

## Known gaps handed on

- Cucumber scenarios (#210): `CharterSteps`' `@After` conformance hook expects JSON bodies and a fingerprint on
  every GET 200; both need a carve-out there for this operation.
- A gateway stripping the `ETag` on the hosted path (#209 risk): no hosted deployment is in this repo to check;
  recorded in the PR hand-off.
