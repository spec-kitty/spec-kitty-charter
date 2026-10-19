# Research: Action-context read and the reserved 1.0 surface

Grounded on charter `main` 61d0980, core 9554e7b2 and open-packs 7f063ae (the commit
`verifyAll` pins). Three profile-loaded lenses ran read-only on 2026-10-19:

- architect (architect-alphonso);
- implementer (java-jenny);
- reviewer (reviewer-renata).

Paths are relative to the charter repository unless prefixed `core:`.

## 1. Are the issues still real?

None of the items is resolved or superseded. `git log -S serviceVersion` and `git log -S securitySchemes` find nothing.

| Claim | Evidence |
|---|---|
| `getActionContext` walks scope edges only, at depth 1, from the action node and the mission-type node, with no activation filter, and is `x-provisional` | `paths/mission-types_…_context.yaml:4-22`; `ActionContextQuery.java:19-27,69-82` |
| `depth` is checked and echoed, but never drives the walk | `ActionContextQuery.java:52-54`, `ContextController.java:37-38` |
| `depth` means scope hops here; in core it means `suggests` hops | `parameters/Depth.yaml`; core `offering/drg/query.py:184-232`, `activation/context_state.py:40,99-104` |
| `ServiceInfo` has no service version, although ADR-0009 promised one | `schemas/ServiceInfo.yaml:4-7`; `docs/adr/0009-…md:71`; `service-lifecycle.md:50` |
| The fingerprint covers pack files only; the service reads neither `config.yaml` nor `charter.yaml` | `FileSystemCharterPackRepository.java:53-80`; `CharterProperties.java`; ADR-0011 D4 |
| No security scheme, and ADR-0011 contradicts itself | "read operations only" (`0011:74,159`) against the post-write push (`0011:84,162`); `service-lifecycle.md:68` is a third version |
| The unreadable-pack scenario plants `internal_error` | `service.feature:54-63` plants `failUnexpectedly()` (`ScenarioPacks.java:69-75`) |
| `pack_unreadable` is raised but pinned by no scenario | raised at `GraphDocumentReader.java:67` and `FileSystemCharterPackRepository.java:59,85,147,155,166` |
| `governance_policy_violation` and `stale_input` are raised by nothing and pinned by nothing | `RefusalCode.java`; `RefusalHandler.java:79-80` maps titles only |

**Release window.** There is no git tag, so `ReleaseGate` answers `NoBaseline`
(`ReleaseGate.java:42-43,210-216`). Shape changes are allowed until
`contract-charter-v1.0.0` is tagged. After the tag every closed-schema change here is breaking
(`contracts/compat/severity-levels.txt:6-7`).

## 2. Core's walk (evidence, not specification)

`resolve_context` (core `offering/drg/query.py:184-243`) uses `walk_edges` (`:77-121`). That is a
breadth-first walk filtered by relation that never visits a node twice.

1. **Scope:** one hop from the **action node only**, then the action itself is removed
   (`:218-220`). Core does not walk the mission-type node's scope edges in this read.
2. **`requires`:** transitive, with no limit, seeded from the scoped set (`:223`).
3. **`suggests`:** up to `depth` hops, seeded from the scoped set only (`:226`). The result is
   unioned with the `requires` result (`:229`).
4. **`vocabulary`:** one hop from everything found so far. It goes into a separate
   `glossary_scopes` output (`:232-233`).
5. **Tension annotation** over `in_tension_with` pairs delivered together (`:236`, `:150-181`).

The output is frozensets: no order and no provenance.

Between the walk and what `charter context --json` delivers, core also:

- adds selection roots (charter-selected, org-required and catalog defaults) and their closure
  over `requires` and `suggests` together (`action_governance_bundle.py:394-415`,
  `delivery_table.py:270-330`); the closure, not the walk, drops artifacts another action owns
  (`delivery_table.py:297-330`);
- applies the language scope filter (`action_governance_bundle.py:462`, #5357);
- excludes kinds by delivery slot (paradigm, template, agent_profile, step contract,
  anti-pattern, skill; `delivery_table.py:116-125`);
- applies the directive allowlist (`delivery_table.py:346`);
- adds reach-only tactics (`action_governance_bundle.py:400`);
- answers empty with a warning on a load error (`:454-460`).

`glossary_scopes` has no consumer in core outside `query.py`. Core computes it and discards it;
glossary packs are delivered only when scope, `requires` or `suggests` reach them
(`delivery_table.py:112`).

Core documents `mission_type`-sourced scope edges as the entry point of the activation cascade,
not of the action-context walk (core `offering/drg/models.py:205-218`).

**Activation filtering** (`drg_activation.py:446-484`):

- It runs at node level, **before** the walk.
- Edges survive only when both ends do, so a filtered intermediate node cuts every path
  through it.
- Kinds outside the activation map pass, and that includes `action` and `mission_type`.

**First load:**

- Depth is 2 with no recorded load and 1 after one. An explicit depth wins
  (`context_state.py:40,89-110`).
- The state is keyed by action name only.
- The public bundle read uses depth 0 (`action_governance_bundle.py:290-297`).
- Depth below 2 renders compact (`context.py:348`).

**Open-packs at the pinned commit** declares:

- 201 `scope` edges;
- 355 `requires` edges;
- 475 `suggests` edges;
- **0 `vocabulary`** edges;
- 2 legacy `in_tension_with` edges.

The vocabulary walk is therefore pinned by scenario fixtures, not by open-packs content.

## 3. Decisions

Each decision is provisional. The runner made them under the operator's 2026-10-19 steer ("design
it on its own merits, not as a copy of core"), and revised them after the plan-point
adversarial squad (§8). They are recorded in ADR-0018 and the amendments named in
[plan.md](plan.md), and listed for operator confirmation.

### D1. R1 is a contract operation: `getActionContext` is the offering's walk

**Decision.** The read stays a contract operation in the cutover, and grows before 1.0. It
answers **what the offering says governs an action**: the walk over the merged offering. It
stays that, for good. What applies in one project (activation, exclusions, selection roots) is
a separate read. The activation contract set (R9, #103) adds it as a new operation, built on
this walk inside the service.

**Rationale:**

- Core keeps no second walker: both reads run in the service (ADR-0010).
- A 1.0 answer never changes meaning without the client opting in (ADR-0009 D4, ADR-0013 D7).
- Growing the existing operation before the tag costs nothing.

**Alternatives considered:**

- **Exempt like compile:** rejected. The read is per action and on the hot path.
- **Out of the cutover:** rejected. It keeps two walkers.
- **Filter this read once R9 lands:** rejected by the plan-point squad. It changes a 1.0
  answer's content and its `governanceFingerprint` silently.
- **An `activationFiltered` flag:** rejected. It is a field whose only purpose is a later
  change of meaning.

### D2. Walks

**Operator steer (2026-10-19):** some of core's behavior may be unintended, so the charter
does not replicate core's bugs. Core is evidence of intent: its docstrings, its other
closures, its ADRs. Where its code contradicts its own stated intent, the charter follows the
intent and registers the difference.

| Step | From | Reach | Entry relation |
|---|---|---|---|
| 1. scope | the action node | 1 hop | `scope` |
| 2. requires | the scoped artifacts | transitive | `requires` |
| 3. suggests | the scoped artifacts | `depth` suggests-steps | `suggests` |
| 4. requires of suggested | the suggested artifacts | transitive | `requires` |
| 5. vocabulary | every artifact listed by steps 1 to 4 | 1 hop | `vocabulary` |

**How the walks run:**

- Each walk is breadth-first with minimum-step semantics, as core's `walk_edges` is.
- Each walk visits a node at most once.
- A node reached at 1 and at 2 steps is expanded from 1 step. A depth cap therefore never hides
  a child that a shorter path reaches.
- A node an earlier step already listed is never listed again by a later step. It still seeds
  the later steps.
- Step 4's artifacts are not walked for `suggests`. The walk ends there, so `depth` bounds the
  answer.

**Where this differs from core's code, and why it follows core's intent:**

- **Hard dependencies of suggested artifacts (step 4).**
  - **Core's code** lists a suggested artifact without its `requires` (`query.py:226`).
  - **Core's stated intent:** its own docstring calls `requires` "hard dependencies"
    (`query.py:194-195`), and its selection closure walks `requires` and `suggests` together
    (`delivery_table.py:284-287`).
  - Delivering an artifact without what it hard-depends on contradicts both, so the charter
    treats it as unintended (DIV-010).
- **Suggestions of required artifacts: not followed, as in core.**
  - Core's docstring states that `suggests` is walked "from scoped artifacts"
    (`query.py:196-197`). That is intent, not a bug.
  - Seeding from required artifacts was considered and **rejected after measurement**. Over
    every open-packs action it adds about 30% more governance (all actions, depth 1: 434 → 550
    entries; `software-dev/tasks`: 28 → 69).
  - That is a design change the operator has not asked for. Revisit if a consumer needs it.

**The mission-type node is not walked.** Core documents its scope edges as the entry point of
the activation cascade, not of this walk (`models.py:205-218`). This is core's intent, not a
bug. Type-wide guidance reaches an action through R9's selection. Walking it here adds 30 to
49 artifacts on `plan`, `documentation` and `research` actions, against core's 0 to 14 (§8,
O1). This reverses the first slice's rule. `action_context.feature` is rewritten before the
tag.

**Vocabulary** targets are entries with relation `vocabulary`, not a separate list. Core
computes the same set and discards it (`glossary_scopes` has no consumer, which looks
unintended). The seam drops these entries when it builds core's delivery (DIV-006).

### D3. Provenance and deduplication

**Each entry carries:**

- **`relation`:** the relation of the step that listed it.
- **`via`:** its parent in that step's walk. For a scope entry, this is the action node.
  Otherwise it is always **another entry of the answer**.
- **`hops`:** 1 for a scope entry, and `hops(via) + 1` for every other entry.

**Invariant.** Following `via` from any entry reaches the action node, along a real path of
the graph, in exactly `hops` edges. This holds because:

- a step never lists a node an earlier step listed;
- a step's seeds keep their final entries.

**Within a walk:**

- a node takes its parent from the minimum-step frontier;
- among equal-step parents, the one with the fewest `hops`;
- then the smallest URN string.

**Precedence.** An artifact the steps could reach several times is listed by the first step
that reaches it. That gives scope, then `requires`, then `suggests`, then `requires` of
suggested, then `vocabulary`. The strongest reason an artifact applies is the one the answer
gives.

Entries are sorted by full URN string.

### D4. `depth`

**Definition.** The request parameter `depth` is the number of `suggests` hops: minimum 0,
default 2, maximum 2. `ActionContext.depth` echoes it with minimum 0 and **no maximum**, so
raising the request maximum later stays compatible. A compat fixture pins that
(`compatible-depth-max-raised`).

**The seam maps core's concepts onto it:**

- first load → 2;
- after `--mark-loaded` → 1;
- the public bundle read → 0.

Render tiers stay in core. The service stays a pure read model (ADR-0011 D3).

`hops` and `depth` count different things:

- `depth` caps the suggests-steps;
- `hops` is the full path length from the action.

The docs keep the two apart.

**A core bug not replicated.** Core keys its first-load state by action name only
(`context_state.py:98`), so `software-dev/plan` and `plan/plan` share one "loaded" flag. The
service keeps no state (C-003). ADR-0018's requests to core ask the seam to key the state by
mission type and action.

### D5. What applies in a project belongs to R9 (#103)

R9 adds an effective-context operation in the activation contract set. It owns:

- activation filtering;
- exclusions;
- the skipped report;
- selection roots and their closure;
- the language filter;
- the directive allowlist.

It inherits core's semantics as evidence:

- filtering at node level before the walk;
- edges dropped with their endpoints;
- the `action` and `mission_type` kinds exempt;
- roots closed over `requires` and `suggests` together;
- the other-action ownership drop.

It may reuse `GuidanceEntry` or define its own entry shape for roots, which have no edge
`via`. Roots never enter `getActionContext`.

**Cutover gate (operator ruling, 2026-10-19):** the seam switches its context read to the
service only when R9's operation exists (cutover stage 3, ADR-0010). Until then, the corpus
compares this read with core in two ways:

- **Cases without activation keys:** must match core exactly, with no register exemption.
  Without activation keys the filter is the identity.
- **Activated cases:** assert charter = core(the same fixture without activation keys), and
  core(activated) ⊆ charter.

### D6. Tensions go to a later operation

Core's tension annotation goes to a later operation in the creed contract set (#16, #77), built
over negative `impacts`, which 1.0 already models. The seam cannot reproduce it from this read
alone until then. Operator ruling, 2026-10-19: accepted.

### D7. Service version

**Decision:** a required `ServiceInfo.serviceVersion`, the build's own version, separate from
`contractVersion`, with a semantic-version pattern. Its source is a `version` property in
`gradle.properties`, generated into a resource as the contract version is.

**Contract sets:** the charter contract is one document. An additive contract set (ADR-0009 D4)
is new operations in it and a minor version bump, so `contractVersion` names everything a
service serves. No `contractSets` field is needed. ADR-0018 records this. If a separately
versioned document ever appears, its discovery is a new operation.

### D8. Source fingerprint inputs

**Inputs.**

- every pack file the service loads;
- `config.yaml` and `charter.yaml` under a configured project configuration directory
  (`charter.project-config-dir`, which the launcher sets to the worktree's `.kittify`). Each
  file is digested under a fixed, domain-tagged label with a presence byte, so an absent file
  and an empty one differ, and no pack name can collide with the label.

**Configuration rules.**

- A configured directory that does not exist fails startup.
- Without a directory, the fingerprint covers the pack files only.
- The contract names the inputs. Adding an input later changes values, not shape.

**Accepted consequence.** The configuration files are digested but not loaded (#73 reads pack
locations from `config.yaml`; R9 reads activation from `charter.yaml`). An edit to them during a
read refuses that read with `stale_input`, although its answer could not have changed. This is
intended: the seam then retries.

### D9. Per-worktree token and browser refusal

**Contract.**

- `components.securitySchemes.charterWorktreeToken` is an HTTP bearer scheme.
- It is declared as root `security` and required on every operation.
- **401** is a missing, malformed or wrong token. It declares `WWW-Authenticate: Bearer`.
- **403** is a browser request: a `Host` whose name is not a loopback name (`localhost`, `127.0.0.1`, `[::1]`; the port is not checked, because MockMvc and `server.port=0` make a port check fragile and the name alone defeats rebinding),
  or an `Origin`, `Sec-Fetch-Site` or `Sec-Fetch-Mode` header. The `Origin` check alone does
  not stop DNS rebinding, because a same-origin GET carries no `Origin`.
- 401 and 403 are shared `Problem` bodies without a charter code. They are transport failures,
  so no refusal code is added.
- They carry `Charter-Contract-Version` and `Charter-Worktree`, never
  `Charter-Source-Fingerprint`.
- Unknown paths answer 401 before 404, so routes are not disclosed.

**Service.**

- **Ordering:** a guard filter runs at the highest precedence, before anything reads a file. A
  guarded request never digests the packs.
- **Token required:**
  - The service refuses to start without a non-blank token of at least 32 characters.
  - The only exception is an explicit `charter.insecure-no-token=true` development flag, which
    logs a warning at every start.
  - The browser checks are always on.
- **Where the token comes from:** it is read from the environment (`CHARTER_WORKTREE_TOKEN`). A
  token set on the command line or as a system property fails startup.
- **How the token is held and compared:**
  - It is held in a type whose `toString` is redacted.
  - It is compared with `MessageDigest.isEqual` over SHA-256 digests, so neither content nor
    length leaks through timing.
  - The `Bearer` scheme is matched case-insensitively.

**Not an identity proof.** The token proves the client to the service, not the service to the
client. Server identity stays with the launcher's process identity (ADR-0011 D2). A process that
squats the port receives the seam's token. That is a residual risk recorded for the launcher's
security review (#70).

### D10. Refresh operation, and "read operations only" reconciled

**Operator ruling (2026-10-19):** keep refresh, under the packs domain (`packs/refresh`, like
`packs/register`). It forces a retrieval of the packs and clears the cache.

**Shape.**

- `POST /packs/refresh` (`refreshPacks`, tag Packs) takes no request body. A non-empty body is
  a 400, so a later optional paths hint is a clean addition.
- It re-reads every configured pack and the project configuration, and discards any built read
  model.
- It answers 200 with the refreshed `PackListing` (the `GET /packs` schema) and exactly one
  `Charter-Source-Fingerprint`, the digest it verified. The seam gets the new fingerprint and
  the listing it was computed from.
- It refuses like a read: 500 with the pack refusal codes, or `stale_input`.
- It is idempotent.

**This version** keeps no built graph, so refresh re-reads and re-verifies. Once #73 adds a
built graph, refresh rebuilds it.

**ADR-0011 D2** is reworded: "no operation changes governance or anything on disk; refresh only
tells the service to rebuild its read model, and every read after it answers what the
fingerprint check alone would answer".

**History.** The plan-point squad proposed dropping refresh from 1.0 (A7). The operator kept it
and placed it in the packs domain. Answering the listing rather than `ServiceInfo` also removes
the duplicate DTO the generator produced for a second `ServiceInfo` response.

### D11. `stale_input`, and the refusal scenarios

**`stale_input` is defined independently of the reload model.** It means: the inputs changed
while the answer was computed, so the answer and the fingerprint it would report may disagree.

**The rule today:**

- Every operation that reads inputs digests them before and after it computes its answer, and
  refuses on a difference. That covers `/info`, `/refresh`, `/packs` and the context read.
- When the load itself fails (for example a half-written file parsed as `pack_unreadable`), the
  read digests again. If the digest differs from the first one, it answers `stale_input`, not
  the load's code.
- A 200 carries the digest it verified as `Charter-Source-Fingerprint`, set once, by the read.
- No refusal carries the fingerprint.

**Residual window.** A change after the second digest, or a change reverted between the two
(ABA), goes unseen. It is documented.

**`pack_unreadable`:** a real scenario, with a `*.graph.yaml` shard that is not valid YAML. It
avoids `pack.yaml`, which is runner B's. The scenario that plants `internal_error` is retitled.

**`stale_input` scenario:** a test seam in test sources wraps the real adapters and rewrites a
real pack file on disk during the load. Production code computes both digests. Its negative
control is the same seam with no change, which answers 200 with the verified digest.

**`governance_policy_violation`:**

- It is pinned in two places:
  - a `@contract @reserved` scenario asserts the declaration: in the enum, a 500, not
    retryable;
  - a unit test enforces "raised by no operation of this version": no main class references
    `RefusalCode.GOVERNANCE_POLICY_VIOLATION` except the enum and its title mapping.
- Its red-first proof is a planted throw site that fails the unit test.
- It is counted as declared, not behavioral: 8 behavioral plus 1 declared of 9 codes.
- Runner B's #48 deletes the unit test when it raises the code.

### D12. The divergence register gets one home

**Decision:** `docs/api/divergence-register.md` with stable ids. It moves the three CHANGELOG
entries there and adds this mission's entries.

**Limit:** the machine-readable format stays #72's.

### D13. `ActionContext.diagnostics`

**Decision:** a required `diagnostics: Diagnostic[]`, the offering's load findings, as
`PackSummary` carries them per pack. It reuses the existing schema. Today it carries
`pack_level_missing`. The hot read then shows non-fatal findings without a second call.

**Not a diagnostic:** a scope target the graph does not declare stays a null label. A new
`DiagnosticCode` would be a guess.

### D14. Records

- **ADR-0018 (new):** D1 to D14. It supersedes:
  - ADR-0009 D3 bullet 1 (the depth reservation);
  - ADR-0011 D2's "read operations only";
  - ADR-0011 D4's "every answer carries" the fingerprint;
  - ADR-0011 D4's fingerprint input list.
- **ADR-0009, ADR-0011 and ADR-0016:** each gains a dated "Amendment" note that points to
  ADR-0018. ADR-0016 changes "at both depths" to "at depths 0, 1 and 2".
- **Not superseded:** ADR-0011's built graph (warm reads) and reading pack locations from
  `config.yaml`. They stay #73's work, and ADR-0018 says so.

## 4. Seams and parallel authorities

**Code path:**

- `ContextController` → `ActionContextQuery` (read-app) → `DoctrineGraph`.
- `outgoing(source, relation)` is a linear scan. Build an index once per answer.
- `GraphDocumentReader:83-88` already loads every relation.

**Contract to code:**

- openapi-generator `interfaceOnly`, so controllers implement the generated `ContextApi` and
  `ServiceApi`.
- A new operation forces an implementation.

**Scenarios:**

- `CharterSteps` sends GET only, with no headers.
- `ContractConformance` hardcodes `"get"` (`CharterSteps:66`).
- `charter-service/build.gradle.kts:58-66` copies the contract into test resources.
- Scenarios need POST, a request header step, and a token configured for the suite.
- The suite runs `@contract` only, so every new scenario carries `@contract`.

**Lint:**

- `charter-headers-on-every-response` (`ruleset.yaml:88-102`) requires the headers declared on
  401 and 403 too. The fingerprint is declared, optional, and described as "on 200 answers
  only".
- `error-responses-are-problems` makes 200 the only success, which refresh satisfies.
- `contract-and-dtos.md:104` gains `components` and `security`.

**ArchUnit:**

- `ReadApp` is reached only by RestIn and Service.
- Contract DTOs are reached only by RestIn.
- `@NullMarked` on every main package.

**Parallel authorities to bring into line,** with the contract file as the authority:

- **`depth`:** `Depth.yaml`, `ActionContext.yaml`, `GuidanceEntry.yaml`,
  `action_context.feature`, the example, CHANGELOG, `service-contract.md:45`,
  `contract-and-dtos.md:135`, `resolution-model.md:38,94`, ADR-0009, ADR-0011.
- **Fingerprint inputs and presence:** `ServiceInfo.yaml`, the header, `service.feature`,
  CHANGELOG, `service-contract.md`, `service-lifecycle.md:52`, ADR-0011.
- **Reserved codes:** `CharterRefusalCode.yaml`, CHANGELOG, `service-contract.md:92-93`,
  `contract-and-dtos.md:134`, `resolution-model.md:66`, `service-lifecycle.md:67,81`.
- **The token:** ADR-0011, `service-lifecycle.md:55`, `open-questions.md:34`.

## 5. Divergence register entries

| Id | The charter answers | Core answers | Corpus case |
|---|---|---|---|
| DIV-001 | 404 `action_not_found` for an action that is not a graph node | its non-bootstrap context | `action-not-a-node` |
| DIV-002 | a scope target the graph does not declare, with a null label | skips it (and R9's filter drops such edges) | `undeclared-scope-target` |
| DIV-003 | a load error refused (500, pack code) | an empty answer with a warning | `unreadable-pack` |
| DIV-005 | an ordered answer with `relation`, `via`, `hops` and `diagnostics` | unordered sets | every case (mapping) |
| DIV-006 | vocabulary targets as entries with relation `vocabulary`; the seam drops them from delivery | computes `glossary_scopes` and discards it | `vocabulary-walk` |
| DIV-007 | the offering's walk only; activation, roots, language filter and directive allowlist are R9's operation | one filtered, selected answer | `activated-project`, `selected-roots`, `language-filtered`, `directive-allowlist` |
| DIV-008 | no tension annotation (until the creed set) | an annotation over `in_tension_with` pairs | `tension-pair` |
| DIV-009 | every walked kind, including paradigm, template and agent_profile | delivery slots exclude those kinds (seam-side) | `undelivered-kind` |
| DIV-010 | the hard dependencies (`requires`, transitive) of suggested artifacts are listed: core's docstring calls `requires` hard dependencies, and its selection closure follows them | a suggested artifact without its `requires` | `suggested-artifact-requires` |

DIV-004 (walking scope from the mission-type node) was withdrawn after the plan-point squad (D2).

**Corpus cases named for the harness (#9).**

- **Every mission type and action:** at depths 0, 1 and 2, against `charter context --json`
  with and without `--mark-loaded`, through the mapping. Cases without activation keys match
  exactly.
- **Walk cases:**
  - `requires-chain`;
  - `requires-cycle`;
  - `suggests-two-hops`;
  - `suggests-diamond`, a node reachable at 1 and at 2 hops whose child must appear at depth 2;
  - `suggested-artifact-requires`, whose `requires` **is** listed (DIV-010);
  - `required-artifact-suggests`, whose `suggests` is not listed, as in core;
  - `other-action-owned-via-requires`, which core includes;
  - `plan-actions-empty`, for `plan/*`, which core answers empty.
- **The public bundle read:** `public-bundle-depth-0`.
- **The register cases in the table above.**

**Evidence for SC-001.** No core CLI exposes the bare walk, and ADR-0016 D2 forbids new export
code in core. This mission therefore records a one-off evidence run in
`kitty-specs/…/evidence/walk-comparison.md`. A script runs core's `resolve_context` over
open-packs at the pinned commit for every action at depths 0, 1 and 2, and compares it with
the service's answer. It is evidence, not a gate. The gate is #9.

## 6. Overlap with runner B (#48, #114, #115)

**Shared files:**

- `contracts/charter/CHANGELOG.md`;
- `CharterRefusalCode.yaml` (description text);
- `RefusalCode.java`;
- `RefusalHandler.java`;
- `docs/api/service-contract.md`;
- `docs/changelog/README.md`;
- ADR-0009;
- the ADR index.

**Mitigation:**

- This mission adds ADR-0018 and amends ADR-0009, ADR-0011 and ADR-0016 under dated
  "Amendment" notes. A rebase onto B renumbers ADR-0018 if B lands first.
- The refusal code enum values do not change.
- Fingerprint widening lives in a new class outside `FileSystemCharterPackRepository`.
- The `governance_policy_violation` unit test names #48 as its removal point.

## 7. Research squad findings disposition

| Finding | Disposition |
|---|---|
| The `depth` meaning differs, and oasdiff will never flag it (reviewer) | D4. A scenario pins the new meaning. |
| Selection roots need `origin`/`hops` if delivered (architect) | `hops` adopted (D3). Roots never enter this read (D1, D5). |
| Tensions now or never (architect) | A later operation (D6); open decision. |
| Optional token is weak (reviewer) | Required token (D9). |
| Fingerprint leak on 401 (implementer) | Not sent on 401/403 or any refusal (D9, D11). |
| Fake-green risk for reserved codes (reviewer) | `stale_input` made real; `governance_policy_violation` pinned and enforced (D11). |
| `pack.yaml` is runner B's scope (implementer) | The scenario uses a graph shard (D11). |
| Fingerprint code is runner B's class (implementer) | A new composite class (D8). |
| `governanceFingerprint` must cover activation inputs (architect) | Moot: this read never filters (D1). R9's operation defines its own. |
| The divergence register has no home (all) | D12. |
| ADR numbering collision with runner B (reviewer) | §6. |
| The token scheme needs a disposition before the launcher's security review (reviewer) | D9. The launcher's own review stays with #70. |

## 8. Plan-point adversarial squad findings disposition

Three lenses ran on 2026-10-19:

- **contract design (A):** architect-alphonso;
- **security and runtime (S):** reviewer-renata;
- **oracle fidelity and test honesty (O):** researcher-robbie.

| Id | Sev | Finding | Disposition |
|---|---|---|---|
| O1 | blocker | Walking mission-type scope goes against core's meaning of the edge; adds 30 to 49 artifacts on `plan`, `documentation` and `research` actions | **Accepted.** Scope from the action node only (D2); DIV-004 withdrawn |
| A1 | blocker | Filtering this read after R9 silently changes a 1.0 answer | **Accepted.** This read never filters; R9 adds its own operation (D1, D5) |
| S1 | blocker | The token check fails open when no token is configured | **Accepted.** Startup refused without a token unless an explicit dev flag (D9) |
| S2 | blocker | `Origin` alone does not stop DNS rebinding | **Accepted.** Host-name allowlist (name only, not port) and `Sec-Fetch-*` refusal (D9) |
| A2 | major | Selection roots cannot fit `GuidanceEntry` later | **Accepted.** Roots never enter this read (D5) |
| A3 | major | `hops`/`via` ill-defined after dedup | **Accepted.** First-listing precedence makes `hops = hops(via) + 1` with `via` always an entry or the action (D3); a scenario pins the invariant |
| A4 | major | A maximum on the response `depth` makes raising it breaking | **Accepted.** No maximum on `ActionContext.depth`; compat fixture (D4) |
| A5 | major | No `diagnostics` on `ActionContext` | **Accepted.** D13 |
| A6 | major | `ServiceInfo` cannot name contract sets | **Accepted** as a record: one document, one version (D7) |
| A7 | major | Refresh freezes a shape for no benefit | **Operator ruling:** kept, as `POST /packs/refresh` answering the refreshed `PackListing` (D10) |
| A8 | major | Token reasoning: identity claim inverted; `WWW-Authenticate` | **Accepted** (D9) |
| A9 | major | ADR-0011 contradicted without amendment | **Accepted.** ADR-0018 supersedes the named points; `stale_input` redefined (D11, D14) |
| A10 | minor | Suggests seeding rationale inconsistent | **Accepted, then resolved** under the operator's steer not to replicate core bugs. The `requires` of suggested artifacts is listed (DIV-010). Suggests stays seeded from scope, which is core's stated intent; the alternative was measured at +30% and rejected (D2) |
| A11 | minor | Provenance has no oracle; `serviceVersion` pattern | **Accepted.** One scenario per tie-break; semver pattern (D3, D7) |
| S3 | major | Filter order; `CharterHeaders` digests before any guard | **Accepted.** Guard at highest precedence; the header is set only by reads (D9, D11) |
| S4 | major | Two fingerprint headers; refusals' fingerprint undefined | **Accepted.** Set once by the read; no refusal carries it; a scenario asserts exactly one header (D11) |
| S5 | major | A concurrent write reported as `pack_unreadable`; ABA | **Accepted.** Re-digest on load failure; ABA documented (D11) |
| S6 | major | The seam tests the comparison, not the detection | **Accepted.** The seam wraps the real adapters and rewrites a real file (D11). The "loaded set = digested set" property test is on runner B's class: follow-up |
| S7 | major | Config-dir inputs can quietly stop being watched | **Accepted.** Missing dir fails startup; domain-tagged labels with a presence byte (D8) |
| S8 | major | The token leaks into logs and argv | **Accepted.** Redacted holder; argv and system-property sources refused; hashed compare (D9) |
| S9 | minor | Error paths bypass the guard | **Accepted.** Guard mapped to all REQUEST dispatches; unknown path → 401; container 400s documented as unguarded |
| S10 | minor | Refresh CSRF; torn digest on `/info` and refresh | **Accepted.** Two-digest check on every operation; non-empty body → 400 (D10, D11) |
| S11 | minor | A derived worktree id is guessable | **Recorded** as a residual risk for #70; the launcher always sets an opaque id |
| O2 | major | §2 factual errors: `:462`, `glossary_scopes`, edge counts | **Accepted.** §2 corrected; DIV-006 re-described |
| O3 | major | Core's delivered answer is not `resolve_context`; SC-001 has no oracle | **Accepted.** DIV-007 and DIV-009 widened; SC-001 re-based on a recorded evidence run (§5) |
| O4 | major | DIV-007 is acceptable only as a cutover gate | **Accepted.** Recommended gate and two-sided corpus comparison (D5) |
| O5 | major | Walk under-specified (DFS could pass) | **Accepted.** Breadth-first minimum steps; diamond scenario; the follow-through scenarios pin DIV-010 (D2) |
| O6 | major | The declared-only pin counts as coverage | **Accepted.** Enforcing unit test; `@contract @reserved`; score reported as 8 + 1 (D11) |
| O7 | minor | The stale seam is honest only if built carefully | **Accepted.** Negative control; ArchUnit rule that no test seam lives in main (D11) |
| O8 | minor | Red-first must be an assertion failure | **Accepted.** Every WP records an assertion failure and the suite's rising scenario count |
| O9 | minor | Missing corpus cases | **Accepted** (§5) |

## 9. Post-tasks adversarial check disposition

planner-priti, 2026-10-19. Every finding is accepted and folded into the WP prompts.

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | blocker | The reserved-code ArchUnit rule trips on javac's synthetic `RefusalHandler$1` | WP03 T015 excludes `RefusalHandler(\$.*)?` by name pattern |
| 2 | blocker | The "rewritten while parsed" scenario restored the file before the re-digest | WP03 T012 leaves the shard rewritten, so the re-digest differs |
| 3 | major | Glue classes cannot see each other's response | WP02 adds a shared scenario-scoped `ScenarioHttp`; every glue class uses it |
| 4 | major | `CharterSteps` ownership ran backwards | `CharterSteps`, `ContractConformance` and the scenarios README move to WP02; later edits are out-of-map |
| 5 | major | WP01 breaks `service.feature:44-52` (`depth=2` → 400) | WP01 changes it to `depth=3`, out-of-map |
| 6 | major | WP02 T010: fixed path vs supplier, startup check, ambiguous `PackSources` | Supplier injected; property checked at startup; the composite injected by its own type |
| 7 | major | WP02 red run would be a compile error | The type and property are created first, with no effect, before the red run |
| 8 | major | The `@reserved` scenario cannot go red | WP03 adds a machine-readable `x-refusal` map (status, retryable) on `CharterRefusalCode`. The steps read it; red first |
| 9 | major | T003's fixture proves nothing | WP01 commits `breaking-response-depth-max-raised` (if oasdiff fails it) and a test that `ActionContext.depth` has no maximum |
| 10 | major | Wrong Gradle project paths | Fixed (`:charter-service:…`) |
| 11 | major | Host check port vs name | Name only (D9 and data-model) |
| 12 | major | S9 dropped | WP04 writes "container-level 400s answer before the guard and carry no Charter headers" into the `securitySchemes` description |
| 13 | minor | Coverage gaps | Dated changelog entry: a closeout item in tasks.md. The S6 follow-up issue is filed at closeout. Vocabulary tie-break scenarios and a `config.yaml` edit scenario are added |
| 14 | minor | No ready `Problem` writer for the guard | WP04 serializes the generated `Problem` with the context's `JsonMapper` |
| 15 | minor | Stale `addHeader` note | Corrected. The "appears once" step is a regression guard |
| 16 | minor | README step vocabulary | Each WP that adds a step lists it in the README |
| 17 | minor | plan.md counts; "lens C" | Fixed. "Lens C" is defined as the 2026-10-19 contract scope audit's contract lens |
| 18 | minor | WP03 needs result types | A `PackListing` result record (create_intent); `ActionContext` gains the verified fingerprint, out-of-map |

## 10. Operator rulings (2026-10-19, semi-auto session)

| Topic | Ruling |
|---|---|
| DIV-010 (the `requires` of suggested artifacts) | **Keep** |
| R9 effective-context operation | **Gates cutover stage 3.** The seam does not switch its context read to the service before #103's operation exists |
| Refresh | **Keep**, under the packs domain: `POST /packs/refresh` (D10) |
| Tensions | **Handed to the creed set** (#16, #77). The gap is recorded in ADR-0018 |
