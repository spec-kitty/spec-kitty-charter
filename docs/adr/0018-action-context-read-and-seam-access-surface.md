---
title: "ADR-0018: The action-context read is the offering's walk, and the seam reaches the service with a token, a fingerprint and a refresh"
description: Fixes the five-step walk, depth as suggests hops, the fingerprint inputs, the per-worktree token, refresh, stale_input and the divergence register before the 1.0 tag.
doc_status: active
updated: 2026-10-20T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0011-seam-runtime-fail-closed-daemon-and-freshness
  - adr/0016-conformance-corpus-harness-over-core-cli
  - api/service-contract
  - conformance/divergence-register
  - architecture/resolution-model
  - architecture/service-lifecycle
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the walk, depth, the fingerprint, the token, refresh and the refusal rules the service keeps.
  - profile: core-maintainer
    guidance_text: Read this for the depth mapping, the launcher and seam requests, and the gate before the seam switches its context read.
  - profile: contract-consumer
    guidance_text: Read this for what the action-context answer means and which 1.0 shapes were fixed before the tag.
---

# ADR-0018: The action-context read is the offering's walk, and the seam reaches the service with a token, a fingerprint and a refresh

## Status

**Accepted (provisional)** — 2026-10-19. The runner decided under the operator's steer ("design
it on its own merits, not as a copy of core"). Confirmation is pending in the pull request
handover. Four rulings of the same day are operator-confirmed and marked so below: decisions
D2 (DIV-110 kept), D5 (R9 gates cutover stage 3), D6 (tensions go to the creed set) and D10
(refresh as `POST /packs/refresh`).

**Amendment 2026-10-20** (R17 of the pack-schema mission): D11's note on which scenario pins
`pack_unreadable` is amended. The point is annotated where it stands.

This record supersedes, point by point:

- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) decision 3,
  bullet 1: `depth` as scope hops, maximum 1. `depth` now counts `suggests` hops (D4).
- [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/) decision 2, "read
  operations only". It is reworded (D10).
- ADR-0011 decision 4, "every answer carries" the three headers. The fingerprint header is on
  200 answers only (D11).
- ADR-0011 decision 4, the fingerprint input list. It is now the pack files plus `config.yaml`
  and `charter.yaml` under the configured directory (D8).
- [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/) decision 6, "at both depths".
  The corpus covers depths 0, 1 and 2 (D4). The decision text there stays verbatim.

**Not superseded.** ADR-0011's built graph (warm reads) and reading pack locations from
`config.yaml` stay with #73.

## Context

Issues [#95](https://github.com/spec-kitty/spec-kitty-charter/issues/95) and
[#113](https://github.com/spec-kitty/spec-kitty-charter/issues/113) ask what the
action-context read answers and which reserved 1.0 shapes are wrong. The 2026-10-19 scope audit
(its contract lens, "lens C") found that none of the items is resolved:

- `getActionContext` walked scope edges one hop from the action node and the mission-type node.
  `depth` was checked and echoed, but never drove the walk, and it meant scope hops here while
  it means `suggests` hops in core.
- `ServiceInfo` had no service version, although ADR-0009 promised one.
- The fingerprint covered pack files only. The service read neither `config.yaml` nor
  `charter.yaml`.
- The contract had no security scheme, and ADR-0011 said "read operations only" while it also
  described a post-write push.
- `stale_input` and `governance_policy_violation` were raised by nothing and pinned by nothing.

**Release window.** There is no git tag, so the release gate answers "no baseline". Shape
changes are allowed until `contract-charter-v1.0.0` is tagged. After the tag, every
closed-shape change is breaking.

Core's walk is evidence of intent, not a specification. Between the walk and what
`charter context --json` delivers, core also adds selection roots, filters by activation and
language, and drops kinds by delivery slot. The evidence is in the mission's research.

## Decision

### D1. The read is the offering's walk, for good

`getActionContext` stays a contract operation and answers what the offering says governs an
action: the walk over the merged offering. It never filters by activation, exclusion or
selection. What applies in one project is a separate read, the effective-context operation of
the activation contract set (R9, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)),
built on this walk inside the service. A 1.0 answer then never changes meaning without the
client opting in.

### D2. The walk has five steps (DIV-110 operator-confirmed)

| Step | From | Reach | Entry relation |
|---|---|---|---|
| 1. scope | the action node | 1 hop | `scope` |
| 2. requires | the scoped artifacts | transitive | `requires` |
| 3. suggests | the scoped artifacts | `depth` suggests-steps | `suggests` |
| 4. requires of suggested | the suggested artifacts | transitive | `requires` |
| 5. vocabulary | every artifact steps 1 to 4 list | 1 hop | `vocabulary` |

- Each walk is breadth-first with minimum-step semantics and visits a node at most once. A
  node reached at 1 and at 2 steps is expanded from 1 step.
- A node an earlier step listed is never listed again. It still seeds later steps.
- Step 4's artifacts are not walked for `suggests`, so `depth` bounds the answer.
- The mission-type node is **not** walked. Core documents its scope edges as the entry point of
  the activation cascade, not of this walk. Type-wide guidance reaches an action through R9.
- Suggestions of required artifacts are not followed, as in core. Seeding from them was
  measured at about 30% more governance and rejected.
- Step 4 is the one difference from core's code (DIV-110). Core's own docstring calls
  `requires` "hard dependencies", and its selection closure follows them, yet its walk lists a
  suggested artifact without them. The operator ruled on 2026-10-19 to **keep** step 4.
- Vocabulary targets are entries with relation `vocabulary`. Core computes the same set and
  discards it. The seam drops these entries from delivery (DIV-106).

Evidence: 24 actions at depths 0, 1 and 2 are 72 cells over open-packs. 65 are identical to
core, and 7 differ only by DIV-110. Core minus charter is empty in every cell. The via and hops
invariant holds in all 1,227 entries.

### D3. Provenance and deduplication

Each entry carries `relation` (the step that listed it), `via` (its parent in that step's walk;
the action node for scope, otherwise another entry of the answer) and `hops` (1 for scope,
otherwise `hops(via) + 1`).

**Invariant.** Following `via` from any entry reaches the action node along a real path of the
graph in exactly `hops` edges.

**Deduplication rule.** The first step that reaches an artifact lists it: scope, then `requires`, then
`suggests`, then `requires` of suggested, then `vocabulary`. Within a walk a node takes the
parent from the minimum-step frontier, then the parent with the fewest `hops`, then the
smallest URN. Entries are sorted by full URN string.

### D4. `depth` counts suggests hops

The request parameter `depth` is the number of `suggests` hops: minimum 0, default 2, maximum 2.
`ActionContext.depth` echoes it with minimum 0 and no maximum, so raising the request maximum
later is compatible. A compat fixture pins that.

`depth` caps the suggests steps. `hops` is the full path length from the action. They count
different things.

**Seam mapping:**

| Core's concept | `depth` |
|---|---|
| first load (no recorded load) | 2 |
| a load after `--mark-loaded` | 1 |
| the public bundle read | 0 |

Render tiers stay in core. The service keeps no state and stays a pure read model (ADR-0011
decision 3). Core keys its first-load state by action name only, so `software-dev/plan` and
`plan/plan` share one flag. That is a bug the service does not copy (request to core below).

### D5. What applies in a project belongs to R9 (gate operator-confirmed)

R9 adds an effective-context operation in the activation contract set. It owns activation
filtering, exclusions, the skipped report, selection roots and their closure, the language
filter and the directive allowlist. Roots never enter `getActionContext`.

**Operator ruling (2026-10-19): R9's operation gates cutover stage 3.** The seam does not
switch its context read to the service before #103's operation exists. Until then the corpus
compares this read with core in two ways:

- Cases without activation keys must match core exactly, with no register exemption.
- Activated cases assert charter = core(the same fixture without activation keys), and
  core(activated) is a subset of charter.

### D6. Tensions go to the creed set (operator-confirmed)

Core's tension annotation moves to a later operation of the creed contract set (#16, #77), built
over negative `impacts`, which 1.0 already models. Until then the seam cannot reproduce it from
this read (DIV-108). Operator ruling, 2026-10-19: accepted.

### D7. `serviceVersion`

`ServiceInfo.serviceVersion` is required: the build's own version (`gradle.properties`
`version`, currently `0.1.0-SNAPSHOT`), separate from `contractVersion`, with a semantic-version
pattern. Every module jar name now carries it.

The charter contract is one document. An additive contract set (ADR-0009 decision 4) is new
operations in it and a minor bump, so `contractVersion` names everything a service serves. No
`contractSets` field is needed. If a separately versioned document ever appears, its discovery
is a new operation.

### D8. Fingerprint inputs

The source fingerprint covers every pack file the service loads, plus `config.yaml` and
`charter.yaml` under the directory `charter.project-config-dir` names (the launcher sets it to
the worktree's `.kittify`). Each file is digested under a fixed, domain-tagged label with a
presence byte, so an absent file and an empty one differ.

- A configured directory that does not exist fails startup.
- Without a directory, the fingerprint covers the pack files only.
- Adding an input later changes values, not shape.
- **Accepted consequence.** The two files are digested but not loaded (#73 reads pack locations
  from `config.yaml`; R9 reads activation from `charter.yaml`). An edit during a read refuses
  that read with `stale_input` although its answer could not have changed. The seam retries.

### D9. The per-worktree token and the browser refusal

- `components.securitySchemes.charterWorktreeToken` is an HTTP bearer scheme, declared as root
  `security` and required on every operation. **401** is a missing, malformed or wrong token and
  declares `WWW-Authenticate: Bearer`. **403** is a browser request: a `Host` whose name is not
  `localhost`, `127.0.0.1` or `[::1]` (the name only, not the port), or an `Origin`,
  `Sec-Fetch-Site` or `Sec-Fetch-Mode` header. The `Origin` check alone does not stop DNS
  rebinding.
- 401 and 403 are shared `Problem` bodies without a charter code. They are transport failures,
  so no refusal code is added. They carry `Charter-Contract-Version` and `Charter-Worktree`,
  never the fingerprint. An unknown path answers 401 before 404.
- A guard filter runs at the highest precedence, before anything reads a file.
- **A token is required.** The service refuses to start without a token of at least 32
  characters, unless `charter.insecure-no-token=true` is set (a development flag that logs a
  warning at every start). The browser checks are always on.
- **The token comes from the environment** (`CHARTER_WORKTREE_TOKEN`, or `SPRING_APPLICATION_JSON`
  in the environment). Startup is refused when it comes from argv or a system property, including
  through `spring.application.json` given on argv or with `-D`.
- The token is held in a type with a redacted `toString` and compared over SHA-256 digests with
  `MessageDigest.isEqual`.
- **Not an identity proof.** The token proves the client to the service, not the service to the
  client. Server identity stays with the launcher's process identity (ADR-0011 decision 2).

### D10. Refresh is `POST /packs/refresh` (operator-confirmed)

`refreshPacks` (tag Packs) takes no body (a non-empty body is a 400). It re-reads every
configured pack and the project configuration, and discards any built read model. It answers 200
with the refreshed `PackListing` and one `Charter-Source-Fingerprint`, the digest it verified. It
refuses like a read (500 with the pack codes, or `stale_input`) and is idempotent. This version
keeps no built graph, so refresh re-reads and re-verifies; once #73 adds one, refresh rebuilds
it.

ADR-0011 decision 2 is reworded: no operation changes governance or anything on disk; refresh
only tells the service to rebuild its read model, and every read after it answers what the
fingerprint check alone would answer. The path is not `/refresh` and the answer is not
`ServiceInfo`.

### D11. `stale_input` is raised, and the fingerprint header rule

- Every operation that reads inputs digests them before and after it computes its answer, and
  refuses with `stale_input` on a difference. This covers `/info`, the packs, the refresh and the
  context read.
- A read whose load fails digests again. If the digest differs from the first, the answer is
  `stale_input`, not the load's code. A refused second digest is `stale_input` too.
- A 200 carries the digest it verified as `Charter-Source-Fingerprint`, set once. **No refusal
  carries it.**
- **Residual window.** A change after the second digest, or one reverted between the two (ABA),
  goes unseen.
- `pack_unreadable` is pinned by a scenario with a graph shard that is not valid YAML.
  *(Amended 2026-10-20, R17: a graph shard that is not valid YAML is now `drg_schema_error`, so that
  scenario no longer pins `pack_unreadable`. The pin is the `errors.feature` scenario in which
  `config.yaml` is a directory. A `pack.yaml` that is not valid YAML keeps `pack_unreadable`. See
  [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) and the contract
  changelog.)*
- `governance_policy_violation` stays reserved. It is pinned by a `@contract @reserved`
  scenario and an enforcing unit test: no main class references it except the enum and its title
  mapping. Runner B's #48 deletes that test when it raises the code. The score is 8 behavioral
  codes plus 1 declared of 9.
- `CharterRefusalCode` carries a machine-readable `x-refusal` map (status, retryable, raised) for
  all nine codes.

### D12. The divergence register has one home

[`docs/conformance/divergence-register.md`](/conformance/divergence-register/) holds the entries,
each with a stable id. Operator ruling R10 (2026-10-19) governs the location: there is one
register, and runner B's pack-schema block (DIV-001..019) and `divergences.yaml` join it. This
mission uses the block DIV-101..110, with DIV-104 withdrawn. The contract CHANGELOG's provisional
section keeps its paragraphs and links the entries. The machine-readable format stays #72's.

### D13. `ActionContext.diagnostics`

`ActionContext` carries a required `diagnostics` list: the offering's load findings, as
`PackSummary` carries them per pack. A scope target the graph does not declare stays a null
label, not a diagnostic.

### D14. Records

This record supersedes the points listed under [Status](#status). ADR-0009, ADR-0011 and
ADR-0016 each gain a dated amendment that points here. ADR-0016's "at both depths" becomes "at
depths 0, 1 and 2".

## Consequences

### Positive

- One walk, defined once, with an invariant that a scenario and a property test check.
- The 1.0 shapes that would have cost a 2.0 are fixed before the tag.
- Every divergence from core is registered, not hidden.
- A request from a browser, or a stale read, is refused before it can answer.

### Negative

- The token and the launcher's environment handling add work for #70.
- The seam must map `depth` and drop `vocabulary` entries from delivery.
- Until R9, the service cannot answer what applies in one project.

### Risks

- **The ABA window** (D11) goes unseen.
- **A port squatter** receives the seam's token (#70, launcher review).
- **A derived worktree id is guessable.** The launcher must always set an opaque
  `charter.worktree-id` (#70).
- **A placeholder split.** `CHARTER_WORKTREE_TOKEN='${x}'` with the value on argv starts the
  service. It needs a deliberate launcher misconfiguration (launcher review #70; the hardening
  that refuses it is issue #121).
- **Nothing yet pins that the fingerprint digests exactly the files the load reads** (D8, D11).
  The property test "loaded set = digested set" belongs on runner B's pack repository class and
  is issue #120.
- **Tensions are not reproducible** until the creed set lands (D6).
- **R9 is a recommended cutover gate** (D5); the operator ruled it a gate, but nothing enforces
  it before #103.
- **Contract tooling.** The generator duplicates `Problem1` and `PackListing1` (a
  swagger-parser `additionalProperties: false` bug). It is a known follow-up for the contract
  tooling, not a contract problem (issue #119).

## Alternatives considered

- **Filter this read once R9 lands.** Rejected: it would change a 1.0 answer and its meaning
  silently.
- **Walk the mission-type node's scope.** Rejected: it adds 30 to 49 artifacts on some actions
  against core's 0 to 14.
- **Seed `suggests` from required artifacts.** Rejected after measurement (about +30%).
- **Drop refresh from 1.0.** Proposed by the plan-point squad; the operator kept it under packs.
- **An optional token.** Rejected: it fails open.
- **Check `Origin` only.** Rejected: it does not stop DNS rebinding.
- **An `activationFiltered` flag.** Rejected: a field whose only purpose is a later change of
  meaning.

## Revisit when

- R9's effective-context operation lands.
- The creed set lands, with the tension operation.
- #73 adds a built graph.
- A consumer needs the `requires` closure of a suggested artifact in a different shape.
- Core migrates its `in_tension_with` edges.

## Requests to core

- **The seam's mapping.** First load maps to depth 2, a recorded load to depth 1, and the
  public bundle read to depth 0. Drop `vocabulary` entries from delivery. Key the first-load
  state by mission type and action.
- **The launcher (#70).** Pass the token by environment, set `charter.project-config-dir`, and
  always set `charter.worktree-id`.
- **The seam.** Treat 401 and 403 as transport failures.

## References

- Issues #95, #113, #103 (R9), #70, #72, #73, #48, #16, #77, #119, #120 and #121.
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/),
  [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/),
  [ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/).
- [Service contract](/api/service-contract/) and [divergence register](/conformance/divergence-register/).
