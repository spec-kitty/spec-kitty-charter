# Mission Specification: Action-context read and the reserved 1.0 surface

**Mission Branch**: `feat/action-context-reserved-surface`
**Created**: 2026-10-19
**Status**: Draft
**Input**: Issues #95 (cutover read R1, child of epic #94) and #113 (reshape the reserved contract 1.0 surface), dispatched as cloud mission runner A by the operator's planning session.

## Intent Summary

**Discovery mode.** The runner brief tells the runner to work autonomously and to stop only for decisions that are genuinely the operator's. Discovery was therefore minimized. The intent comes from:

- the two issues;
- the operator's standing rulings of 2026-10-19 (ADR-0015: this repository is canonical for the contract);
- a three-lens research squad (architect, implementer, reviewer) that grounded every claim on `main` 61d0980, core 9554e7b2 and open-packs 7f063ae.

Each design call the runner makes is listed under Assumptions as provisional, for the operator to confirm at review.

- **Primary actor:** core's integration seam (`charter.offering.api`). After the cutover it reads action-scoped governance from the charter service instead of from core's Python charter.
- **Trigger:** an agent starts a mission action (for example `software-dev/plan`), and core asks the charter for the governance that applies.
- **Core is evidence of intent, not a spec (operator steer, 2026-10-19).** Where core's code contradicts its own stated intent, the charter follows the intent and registers the difference. Core's bugs are not replicated.
- **Desired outcome:**
  - The contract answers that read in full: scope, `requires`, `suggests` and `vocabulary`, with a `depth` whose meaning is the charter's own and documented against core's.
  - Every 1.0 reservation the cutover depends on is shaped right before contract 1.0 is tagged (#45):
    - the service version;
    - the fingerprint inputs;
    - the per-worktree token;
    - the post-write refresh;
    - the reserved refusal codes.
- **Invariant:** the contract never breaks after 1.0. Anything a 1.0 response leaves out costs a 2.0, so every closed response shape this mission touches is settled now. Behavior that is not designed yet is handed to a named later contract set, never guessed into an existing response.
- **Boundary:**
  - The pack-level policy, the pack schemas and the validator (#48, #114, #115) belong to the parallel runner B.
  - Activation data (active and effective sets, exclusions, the skipped report) belongs to the activation contract set (R9, #103).
  - Launching the daemon belongs to core (#70).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The seam reads the full governance for an action (Priority: P1)

Core's seam asks for the governance of one action of one mission type. The answer includes:

- the artifacts the action owns (scope);
- everything those artifacts require, transitively;
- what they suggest, up to a chosen number of hops;
- the glossary vocabulary they use.

Each entry says how it was reached.

**Why this priority**: without it, core keeps walking the graph in Python after the cutover, a second authority for the same answer (#95).

**Independent Test**: a contract scenario plants packs whose graph has scope, `requires` chains, `suggests` chains and `vocabulary` edges. It reads the context at `depth` 0, 1 and 2 and compares the exact entries, order and provenance.

**Acceptance Scenarios**:

1. **Given** an action that scopes a tactic which requires a directive which requires a styleguide, **When** the seam reads the context, **Then** all three are listed. The directive and the styleguide carry the relation `requires`, the artifact that required each as `via`, and their distance from the action as `hops`.
2. **Given** a scoped artifact with a two-hop `suggests` chain, **When** the seam reads at `depth` 2, **Then** both suggested artifacts are listed. At `depth` 1 only the first is listed, and at `depth` 0 neither is.
3. **Given** an artifact with a `vocabulary` edge to a glossary pack, **When** the seam reads the context, **Then** the glossary pack is listed with the relation `vocabulary`.
4. **Given** an artifact reached both by scope and by a `requires` edge, **When** the seam reads the context, **Then** it appears once, as its scope entry: the first walk step that reaches an artifact lists it.
6. **Given** a `suggests` diamond (a node reachable at 1 and at 2 hops), **When** the seam reads at `depth` 2, **Then** that node's child is listed, because the walk is breadth-first.
7. **Given** a suggested artifact that requires another, **When** the seam reads the context, **Then** the required one is listed with relation `requires` and the suggested artifact as `via`. Its hard dependency comes with it, as core's own docstring intends; core's code omits it (registered divergence).
9. **Given** a required artifact that suggests another, **When** the seam reads at `depth` 1, **Then** the suggested one is not listed through it. Core's stated intent walks `suggests` from the scoped artifacts only.
10. **Given** any entry, **When** its `via` chain is followed, **Then** it reaches the action node in exactly `hops` edges.
8. **Given** the mission type's own scope edges, **When** the seam reads the context of one of its actions, **Then** they are not walked. Type-wide guidance reaches an action through the activation set's selection (#103).
5. **Given** a read without `depth`, **When** the seam reads the context, **Then** the answer is computed at the documented default and echoes it.

---

### User Story 2 - The seam checks it talks to the right, current service (Priority: P1)

Before a session, the seam reads the service info. It checks:

- the contract version;
- the service build;
- the worktree;
- a fingerprint of every input the answers depend on.

After core writes a charter file, the seam tells the service to refresh its packs.

**Why this priority**: ADR-0011 makes the seam fail closed. It can only do that if the service identifies itself fully and can be told that its inputs changed (#113).

**Independent Test**: contract scenarios read `/info`. They change a pack file or a project configuration file, then read `/info` or call `POST /packs/refresh`, and compare fingerprints.

**Acceptance Scenarios**:

1. **Given** a running service, **When** the seam reads the service info, **Then** the answer carries the contract version, a semantic-version service version, the worktree id and the source fingerprint.
2. **Given** a service configured with a project configuration directory, **When** `config.yaml` or `charter.yaml` changes, **Then** the source fingerprint changes. An unchanged project gives the same fingerprint twice.
3. **Given** a pack file changed after the last read, **When** the seam calls `POST /packs/refresh`, **Then** the answer is the refreshed pack listing with the new fingerprint in its header, and calling it again changes nothing.

---

### User Story 3 - Only the seam of the right worktree can read (Priority: P2)

The service requires the per-worktree token on every operation, and refuses to start without one unless it is explicitly started in an insecure development mode. A request without the token, or with a wrong one, is refused as a transport failure. A request that comes from a browser, or names a host other than loopback, is refused as forbidden.

**Why this priority**: ADR-0011 accepts a CLI-started daemon only under this scoping. The 1.0 contract must declare it, because adding a required credential after 1.0 breaks every client that never sent one (#113).

**Independent Test**: contract scenarios run against a service configured with a token. They send no token, a wrong token, the right token, and the right token with a browser `Origin` header.

**Acceptance Scenarios**:

1. **Given** a service started with a worktree token, **When** a request carries no token or a wrong one, **Then** the answer is 401, a Problem without a charter code, with the contract-version and worktree headers.
2. **Given** a service started with a worktree token, **When** a request carries the right token and an `Origin` or `Sec-Fetch-Site` header, or a `Host` that is not loopback, **Then** the answer is 403, a Problem without a charter code.
4. **Given** no token and no development flag, **When** the service starts, **Then** it refuses to start.
3. **Given** the right token and no `Origin`, **When** any operation is called, **Then** it answers as before.

---

### User Story 4 - Every refusal code a client must handle is pinned (Priority: P2)

Each refusal code in contract 1.0 is pinned by a contract scenario. Codes that something raises are pinned by a real input that raises them. A code reserved for later work is pinned so that its contract (status, retry class, "raised by no operation yet") cannot drift silently.

**Why this priority**: ADR-0009 requires a scenario per reservation. The unreadable-pack scenario plants `internal_error` today, and `pack_unreadable` has no scenario at all (#113).

**Independent Test**: run the contract scenarios. Each code in `CharterRefusalCode` is named by at least one scenario.

**Acceptance Scenarios**:

1. **Given** a pack whose graph file is not valid YAML, **When** any read is made, **Then** it is refused with 500 and `pack_unreadable`.
2. **Given** a pack file that changes while a read is being answered, **When** the read completes, **Then** it is refused with 500 and `stale_input`, which the contract marks as retryable.
3. **Given** the contract of this version, **When** a client reads the refusal codes, **Then** `governance_policy_violation` is declared, is a 500 and is not retryable. A unit test enforces that no operation of this version raises it.
4. **Given** a pack file rewritten while a load parses it, **When** the load fails, **Then** the read answers `stale_input`, not `pack_unreadable`.

### Edge Cases

- `depth` below 0 or above the maximum is a 400 Problem, not a charter refusal.
- A `requires` cycle terminates. Each artifact is listed once.
- A walk target the graph does not declare is listed with a null label, as today; it is kept as a registered divergence.
- An action that is not a node of the graph is 404 `action_not_found`; a registered divergence.
- When no project configuration directory is configured, the fingerprint covers the pack files only, and the service info says nothing about where files are on the host.
- No 401, 403 or refusal carries the source fingerprint. Only a 200 carries it, the digest that read verified.
- A service started without a token refuses to start, unless it is started with the explicit insecure development flag, which logs a warning.
- A configured project configuration directory that does not exist fails startup.

## Domain Language *(optional)*

| Term | Meaning here | Avoid |
|------|-------------|-------|
| Action-scoped context | The governance that applies to one action of one mission type, as the `getActionContext` read answers it | "bundle" (core's activation-level object), "prompt context" |
| Walk | Following edges of one relation from a set of nodes | "resolve" (ambiguous with URN resolution) |
| `depth` | The number of `suggests` hops the walk follows from the scoped artifacts | "load depth", "render tier" (core's seam concepts) |
| `hops` | An entry's distance from the action node within the walk that produced it | "level", "distance" |
| Divergence | A deliberate difference between the charter's answer and core's, registered for the corpus (ADR-0016) | "bug", "deviation" |
| Reserved code | A refusal code clients must handle from 1.0 that no operation of this version raises | "unused code" |

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Record the R1 decision | As the operator, I want the R1 decision (contract operation, exempt, or out of the cutover) recorded with its rationale in a decision record and in the service-contract page, so that #95's acceptance is met. | High | Open | [build] | no — the page names no decision today |
| FR-002 | Walk requires transitively | As the seam, I want every artifact the scoped and the suggested artifacts require, transitively, listed with relation `requires`, so that no listed artifact comes without its hard dependencies. | High | Open | [build] | no — today's answer lists scope entries only; the planted `requires` chain scenario is red |
| FR-003 | Walk suggests to depth | As the seam, I want `suggests` followed up to `depth` steps from the scoped artifacts, so that I choose how much optional guidance a read carries. | High | Open | [build] | no — `depth` is never used by the walk today; scenarios at depth 0, 1 and 2 differ |
| FR-004 | Walk vocabulary | As a consumer, I want the glossary packs reached by one `vocabulary` edge from every listed artifact listed with relation `vocabulary`, so that the glossary an action uses has one source (core computes and discards it; the seam drops these entries from delivery). | High | Open | [build] | no — no vocabulary entry is listed today |
| FR-005 | Provenance and dedup | As the seam, I want each entry once, listed by the first walk step that reaches it (scope, requires, suggests, requires of suggested, vocabulary), with `via` its parent in that step (the fewest steps, then the fewest `hops`, then the smallest URN) and `hops = hops(via) + 1`, so that the answer is deterministic and every `via` chain is a real path. | High | Open | [build] | no — `hops` does not exist today |
| FR-006 | Redefine depth | As a contract consumer, I want `depth` documented as `suggests` hops, with its minimum, default and maximum, and its relation to core's first-load depth, so that core's seam maps onto it. | High | Open | [build] | no — today it means scope hops with a maximum of 1 |
| FR-007 | Hand project effects on | As the operator, I want it recorded that this read is the offering's walk for good, and that activation, selection roots, the language filter and the directive allowlist belong to an effective-context operation of the activation set (#103) with the semantics it inherits, so that a 1.0 answer never changes meaning. | Medium | Open | [build] | no — no record names it today |
| FR-008 | Register divergences and corpus cases | As the corpus harness (#9), I want every deliberate difference of this read from core registered and every corpus case named, so that the harness can gate them. | Medium | Open | [build] | no — the register lists three entries today, none for the new walks |
| FR-009 | Service version | As the seam, I want the service info to carry the service build version, so that I can report which build answered. | High | Open | [build] | no — the ServiceInfo scenario asserts the new field |
| FR-010 | Fingerprint inputs | As the seam, I want the source fingerprint to cover `config.yaml` and `charter.yaml` when the service is given the project configuration directory, as well as every pack file, so that a configuration edit is detected. | High | Open | [build] | no — editing `config.yaml` leaves today's fingerprint unchanged |
| FR-011 | Worktree token | As the operator, I want a required per-worktree bearer token declared on every operation, with 401 and 403 answers, and enforced when the service is started with a token, so that 1.0 never has to add a credential. | High | Open | [build] | no — a tokenless request answers 200 today |
| FR-012 | Refresh operation | As the seam, I want `POST /packs/refresh` to re-read the packs and the project configuration, discard any built read model, and answer the refreshed pack listing with its verified fingerprint, so that I can push after a write (ADR-0011 D4). | High | Open | [build] | no — the path does not exist today |
| FR-013 | Read-only reconciled | As the operator, I want ADR-0011's "read operations only" reconciled with the refresh operation in a decision record, so that the trust model states what the daemon can change. | Medium | Open | [build] | no — ADR-0011 contradicts itself today |
| FR-014 | pack_unreadable scenario | As a consumer, I want a contract scenario that raises `pack_unreadable` from a real unreadable pack, and the scenario that plants `internal_error` titled for what it tests. | High | Open | [build] | no — no scenario names `pack_unreadable` today |
| FR-015 | stale_input raised and pinned | As the seam, I want a read whose inputs change while it is answered refused with `stale_input`, pinned by a scenario. | High | Open | [build] | no — nothing raises `stale_input` today |
| FR-016 | governance_policy_violation pinned | As a consumer, I want a scenario pinning `governance_policy_violation` as declared, 500 and not retryable, and a unit test enforcing that no operation of this version raises it. | Medium | Open | [build] | yes for the scenario (a declaration pin); no for the unit test, which a planted throw site fails |
| FR-018 | Diagnostics on the read | As the seam, I want the offering's load diagnostics on the context answer, so that a non-fatal finding is visible on the hot read. | Medium | Open | [build] | no — the field does not exist today |
| FR-019 | One verified fingerprint | As the seam, I want every 200 to carry exactly one fingerprint header, the digest its read verified, and no refusal to carry one. | High | Open | [build] | no — today the filter sets it on refusals too |
| FR-017 | Records updated | As the operator, I want ADR-0009 and ADR-0011 amended where a reservation changed, a new ADR for the design choices, and the contract CHANGELOG, the docs and a dated changelog entry updated. | High | Open | [build] | no — the docs checks pin the new pages |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gates green | `./gradlew verifyAll` passes on Java 25 with strict dependency verification, with 0 skipped or disabled tests added. | Quality | High | Open |
| NFR-002 | Docs clean | `check_docs.py all`, markdownlint-cli2 0.18.1 and cspell 10.4.0 report 0 findings. | Quality | High | Open |
| NFR-003 | Walk terminates | The walk of a graph with a `requires` or `suggests` cycle terminates and visits each node at most once per relation walk. | Reliability | High | Open |
| NFR-004 | Token comparison | Token comparison takes time independent of where a wrong token first differs (constant-time comparison), and the token never appears in a response or log line. | Security | High | Open |
| NFR-005 | Red first | Every behavior requirement has a scenario or test committed and seen failing before the change that makes it pass. | Quality | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Runner B boundary | No edit to `contracts/packs/**`, `PackDescriptor*`, `CharterPack*`, or pack-level code in `adapter-out-yaml`; a shared file is touched minimally. | Technical | High | Open |
| C-002 | Additive later | Activation data, tensions and selection roots are not added to existing 1.0 responses (ADR-0009 D4); they arrive as later operations. | Technical | High | Open |
| C-003 | Pure read model | The service keeps no state about earlier reads; first-load depth stays in core's seam (ADR-0011 D3). | Technical | High | Open |
| C-004 | Core is evidence | Core's walk and activation code are evidence, not the spec; each deliberate difference is registered (ADR-0015, ADR-0016). | Technical | High | Open |
| C-005 | Before the tag | Every closed-schema change lands before `contract-charter-v1.0.0` is tagged; after that it is breaking. | Business | High | Open |

### Key Entities

- **ActionContext**: the answer to one read. The action and mission-type URNs, the `depth` used, the governance fingerprint, and the ordered guidance entries.
- **GuidanceEntry**: one artifact in the answer. URN, kind, label, the relation and `via` node of the edge that brought it in, its `hops`, and the `impacts` strength (null for every relation this read walks).
- **ServiceInfo**: what the service says about itself. Contract version, service version, worktree id, source fingerprint.
- **Source fingerprint**: a digest of every input the answers depend on.
- **Refusal code**: the closed list of reasons a read is refused, each with a status and a retry class.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A recorded evidence run over open-packs at the pinned commit, comparing core's `resolve_context` with the service's answer for every action at depths 0, 1 and 2, differs only by registered divergences — [build] · no-op passable: no
- **SC-002**: Of the 9 refusal codes, 8 are pinned by a behavioral scenario and 1 by a declaration pin with an enforcing unit test (today 6 of 9 are named by any scenario) — [build] · no-op passable: no
- **SC-003**: 0 statements of `depth`, the fingerprint inputs or the read-only rule contradict each other across the contract, the decision records and the docs — [build] · no-op passable: no
- **SC-004**: Each of the six #113 items and the #95 decision is traceable to a decision record, a contract change and a scenario — [build] · no-op passable: no

## Assumptions

These are the runner's provisional design calls, recorded for operator review.

1. **R1 is a contract operation:** `getActionContext` itself, grown before 1.0. A new operation would leave a first-slice operation in 1.0 that nobody should call.
2. **`depth`:** counts `suggests` hops, minimum 0, default 2, maximum 2. Default 2 is core's full first-load answer. Raising the maximum later is compatible.
3. **Scope:** one hop from the action node only. Core documents mission-type scope edges as the activation cascade's entry point; walking them added 30 to 49 artifacts per action on three mission types (plan-point squad).
4. **`suggests` seeding:** seeded from the scoped artifacts only, as core does.
5. **Vocabulary:** entries with relation `vocabulary`, not a separate list. A consumer filters by relation.
6. **Project effects:** this read answers the merged offering for good. Activation filtering, selection roots, the language filter and the directive allowlist belong to an effective-context operation of the activation set (R9, #103), which the seam needs before cutover stage 3.
7. **Tensions:** core's tension annotation is handed to a later operation in the creed set (#16, #77).
8. **Token and its answers:**
   - HTTP bearer, required on every operation including `/info`; startup is refused without a token unless an explicit development flag is set.
   - 401 for a missing, malformed or wrong token.
   - 403 for a request with an `Origin` or `Sec-Fetch-*` header, or a non-loopback `Host`.
   - Both are shared Problems without a charter code, so no refusal code is added.
9. **Refresh (operator ruling):** `POST /packs/refresh` without a request body answers the refreshed `PackListing` and its verified fingerprint. An optional paths hint can be added later compatibly.
10. **Fingerprint inputs:** packs plus `config.yaml` and `charter.yaml` under a configured project configuration directory. Reading pack locations from `config.yaml` stays with #73.
11. **Declared-only pin:** `governance_policy_violation` is pinned by its declaration only. Its behavior lands with #48 (runner B).
