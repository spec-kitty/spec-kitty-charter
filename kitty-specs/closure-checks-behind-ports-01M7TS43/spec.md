# Mission Specification: The walk and closure checks behind ports (#556)

**Mission Branch**: `feat/556-closure-checks-behind-ports`
**Created**: 2026-11-19
**Status**: Draft
**Input**: Issue [#556](https://github.com/spec-kitty/spec-kitty-charter/issues/556) (P1, epic #549, ADR-0075 slice 7,
decision 6), with #517's `PackDependents` seam (ADR-0081 D1) and #478's `PackRulesets.closure()` composition.

## Intent Summary (operator rulings, 2026-11-19)

The brief is the issue body, read against ADR-0075 as amended on 2026-11-13, 2026-11-15 and 2026-11-16. The open
decisions were put to the operator through the orchestrating session and ruled on 2026-11-19. Each ruling is a
resolved Decision Moment under `decisions/`.

- **Actor.** A client that writes packs through the service: a whole-pack registration, an artifact or relationship
  write or removal, or a pack removal. Indirectly, a pack author reading the refusal.
- **Trigger.** A write would leave a closure that does not hold together, a fallback profile or role pin that does not
  hold, a write the mutability policy forbids, or a pack that others still depend on.
- **Outcome.** The write is refused once, with every fault the checks find, not the first. Each check is a kernel
  `Rule` behind a port in the packs domain, so a composition root composes it, or leaves it out by not composing it.
- **Invariants.**
  - One refusal per check. The mutability check runs first, and its `409` ends the write. The impact check's refusal
    then names every fault of every closure. Each refusal's code is its first fault's specific code, and its
    violations name every fault.
  - A read failure (`pack_unreadable`) is never collected. It propagates as today, so #517's dry run still rethrows
    it.
  - A write with one fault is refused as today: the same code, status and packs.
  - Each closure is composed once per write, and each stored pack is read once per session, as today.
  - `pack-check` and `PackRulesets.closure()` do not change (#478).

| # | Ruling | DM |
|---|--------|----|
| D1 | The problem code is the first fault's specific code (fixed rule order). `violations` lists every fault with its own code. `packs` is the union. Additive contract change, recorded as `Changed`. | `01M7TS5S0KZYWF2ZGYG9WPXJYT` |
| D2 | Collect everything related: every closure a write joins, the relation rules, the specialization cycle, the fallback profile, the role pins, the profile enhancements, every refused write of a registration, and the qualified-reference checks | `01M7TS5V3JRBRA969FDN7QVN54` |
| D3 | `ImpactCheck` absorbs `PackDependents`. `PackDependents` and `ClosurePackDependents` are deleted. `409 pack_has_dependents` stays its own code. | `01M7TS5XGH84S9ND3RM7RZTPCR` |
| D4 | One pass: the impact port returns the verdict and the notices, and exposes the kernel `Rule` view | `01M7TS5ZN2B9G0GCXKSBQHBXC1` |
| D5 | `MutabilityCheck` replaces `WritePolicyGate` outright and collects every refused write | `01M7TS61T1BVT5EE0MSZ1026KA` |
| D6 | No parity test here. open-packs' `scripts/ci/charter_service_check.py` already checks every `tests/fixtures/invalid/*/expected-code` against the pinned charter. | `01M7TS640K3TT3RXYDCS7NSWZW` |

Settled readings, accepted with the rulings:

- **S1.** The ports are declared in `packs.domain`, implemented in `packs.application` and wired by `deployable`
  (ADR-0075 amendment 2026-11-13; #554 `design-r3`). The issue's "in `pack-validation`" would be a cycle. The
  2026-11-16 amendment "replaces the 2026-11-13 amendment's ports". This Mission reads that as `PackInspector` and its
  `Scope` only, not decision 6's three ports. The slice-7 amendment of ADR-0075 records this as a flagged ambiguity.
- **S2.** `pack-check` composes none of the ports (decision 6), so its CLI does not change (#478, PR #608).
- **S3.** ADR-0068 is amended for where its rule runs. ADR-0075 gains a slice-7 amendment. ADR-0081 gains a note that
  `ImpactCheck` absorbed `PackDependents`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A registration sees every relation fault at once (Priority: P1)

A pack author registers a pack whose relationships break two rules in one closure, for example a self-override and an
enhances cycle.

**Why this priority**: This is the issue's core: today the author fixes one fault, registers again, and meets the next.

**Independent Test**: Register an example pack with two relation faults, and read one `422` naming both.

**Acceptance Scenarios**:

1. **Given** a pack with a self-override and an enhances cycle, **When** it is registered, **Then** the answer is
   `422 self_override`, and its violations are a `self_override` and an `enhances_cycle`, in that order.
2. **Given** a pack with one relation fault, **When** it is registered, **Then** the answer has the same code, status,
   detail and packs as before this Mission, and one violation.
3. **Given** a pack that builds on two packs whose closures each break a rule, **When** the shared base is replaced,
   **Then** the one refusal names the faults of both closures, each detail naming its closure.

---

### User Story 2 - The fallback profile, the role pins and the enhancements report every breach (Priority: P1)

A pack declares a `fallback_profile` that is neither locked nor role-neutral, and pins a role to an unknown profile.

**Why this priority**: The issue names `FallbackProfileRule` explicitly. ADR-0068's checks today stop at the first
breach.

**Independent Test**: Register an example pack whose fallback profile breaks two of ADR-0068's checks and whose role
pin breaks one, and read every breach in one refusal.

**Acceptance Scenarios**:

1. **Given** a fallback profile that is held but neither locked nor role-neutral, **When** the pack is registered,
   **Then** the refusal is `422 fallback_profile_not_locked`, with violations `fallback_profile_not_locked` and
   `fallback_profile_not_role_neutral`.
2. **Given** a fallback profile that no pack of the closure holds, **When** the pack is registered, **Then** the only
   fallback violation is `fallback_profile_unknown`: nothing more can be judged of a profile that is not there.
3. **Given** that pack also pins a role to a profile its closure does not hold, **When** it is registered, **Then** the
   role-pin violation follows the fallback violations in the same refusal.

---

### User Story 3 - A pack removal names every dependent, then every broken closure (Priority: P2)

An operator removes a pack (ADR-0081).

**Why this priority**: #517 built the dependents check as a seam that this slice absorbs. The behaviour stays, and
the port changes.

**Independent Test**: The #517 `@contract` scenarios of `DELETE /packs/{packId}` and its dry run pass unchanged.

**Acceptance Scenarios**:

1. **Given** a pack that two packs depend on and one activation names, **When** it is deleted, **Then** the answer is
   `409 pack_has_dependents`, naming all three, as today.
2. **Given** a pack with no dependent whose removal breaks two closures, **When** it is deleted, **Then** one `422`
   names the faults of both closures.

---

### User Story 4 - A registration names every write the mutability policy forbids (Priority: P2)

A whole-pack registration replaces two artifacts that a pack it builds on declares `locked`.

**Why this priority**: `authorizeStaged` stops at the first refused write today. D5 folds it in.

**Independent Test**: Register an example pack that rewrites two locked artifacts, and read one
`409 governance_policy_violation` naming both.

**Acceptance Scenarios**:

1. **Given** a registration whose diff writes two locked artifacts, **When** it is registered, **Then** the refusal is
   `409 governance_policy_violation`, with one violation per refused write, in diff order.
2. **Given** a per-artifact write onto a locked artifact, **When** it is written, **Then** the answer is unchanged: one
   `409 governance_policy_violation`.

---

### User Story 5 - Every reference fault of a compose at once (Priority: P3)

A pack writes two qualified references to packs outside its `depends_on` closure.

**Why this priority**: #554 assigned the reference checks to `ClosureWalk` (D5 of its research). They are on the read
path too.

**Independent Test**: Compose an example closure with two reference faults, and read one refusal naming both.

**Acceptance Scenarios**:

1. **Given** two `pack_reference_pack_missing` faults and one `pack_reference_unqualified` fault in one closure,
   **When** it is composed (read or write), **Then** the refusal's code is the first fault's, and its violations name
   all three in the artifacts' order.

### Edge Cases

- A closure that cannot be composed at all (`pack_unreadable`, a `CLOSURE` descriptor fault such as
  `pack_dependency_unknown`) contributes its compose refusal and ends that closure's judgement. The other closures of
  the write are still judged.
- A refusal that already carries violations of its own (the substep delegation's `validation_failed`, the compose's
  `requiredBy`) keeps them. Its faults join the collected list in place, and its code is kept when it is first.
- The activations gate (`requireComposable`, `prompt_slot_missing`) runs after every closure check, as today, and its
  refusal is the write's when nothing earlier failed.
- A refused write lists at most 100 violations, and its detail counts the rest, as every refusal does.
- The census (startup log) logs every finding of each stored pack, not the first.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| FR-001 | `packs.domain` declares three ports, `ClosureWalk`, `ImpactCheck` and `MutabilityCheck`, each exposing a kernel `Rule` over its subject, with no Spring type. | Draft |
| FR-002 | `ImpactCheck` judges a write (a whole pack, an artifact or relationship write or removal) and a pack removal. It returns the verdict (`Rationale`) and the FR-027 notices from one compose per closure (D4). | Draft |
| FR-003 | `ImpactCheck` judges every closure the write joins. In each, it reports every relation fault: `self_override`, `conflicting_relations`, `override_not_permitted` (the edge and the locked step), `override_cycle`, `enhances_cycle` (the cycle and the ungrounded enhancer) and `specialization_cycle`, in that rule order. Within a rule, faults are in edge order. A fault implied by an earlier one is not reported again: each cycle is reported once, a place on a reported enhances cycle is not judged ungrounded, and a self-override edge is not an override cycle. | Draft |
| FR-004 | For a removal, `ImpactCheck` names every dependent first (`409 pack_has_dependents`, as ADR-0081 D7) and composes the closures only when there are none. `PackDependents` and `ClosurePackDependents` are deleted (D3). | Draft |
| FR-005 | `ClosureWalk` judges each declaring pack's own closure: the ADR-0068 fallback profile, then the role pins, then the profile enhancements. Each reports every breach. `fallback_profile_unknown` alone ends the fallback's checks. | Draft |
| FR-006 | Each phase of the compose reports every fault of its phase in one refusal, on the read and the write path: the resolver's composition faults, the substep delegations, the qualified references (`pack_reference_unqualified`, `pack_reference_pack_missing`, `pack_reference_artifact_missing`), the step enhancement targets, the preset contests and the case-only matches. The phases stay sequential, so a phase runs only when the earlier ones pass. | Draft |
| FR-007 | `MutabilityCheck` replaces `WritePolicyGate` outright. A registration's refused writes are each one violation of one `409 governance_policy_violation`. A single write's refusal is unchanged (D5). | Draft |
| FR-008 | One mapping turns a failing verdict into the write's `CharterRefusal`. Its code is the first violation's specific code, with that code's status. Its violations name every fault, each with its own code, file pointer and detail. Its packs are the union, in first-seen order, then each closure's roots (D1). | Draft |
| FR-009 | The order of the impact verdict is today's phase order: every closure in the order it is found, each with its compose fault or its relation faults (FR-003); then the closure walk (FR-005) of every single-root closure that composed and held its relations; then the activations gate, only when nothing failed. A closure that does not compose, or breaks a relation, gets no snapshot, walk or notices. | Draft |
| FR-010 | `deployable` composes the ports for the service. `pack-validation`, `pack-check` and `PackRulesets.closure()` compose none of them and do not change. | Draft |
| FR-011 | The census keeps one finding per stored pack, carrying every fault, and logs each fault. | Draft |
| FR-012 | The contract records that a pack-write refusal's violations name every fault: the codes' descriptions in `CharterRefusalCode.yaml`, and a `Changed` change fragment. | Draft |
| FR-013 | ADR-0068 is amended for where its rule runs (behind `ClosureWalk`). ADR-0075 gains a slice-7 amendment with the S1 ambiguity flagged. ADR-0081 notes that `PackDependents` was absorbed. | Draft |
| FR-014 | `@contract` scenarios on charter's own example packs (ADR-0070) cover User Stories 1, 2, 4 and 5, and pass the ADR-0059 D1 gate. The #517 delete scenarios pass unchanged. | Draft |

### Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | Each closure a write joins is composed once, and each stored pack's files are read once per session, as today. A counting test pins it. | Draft |
| NFR-002 | Every relation walk stays iterative and linear in the edges, so a long chain cannot exhaust the stack. | Draft |
| NFR-003 | The mutation floors of `:packs`' domain+application group hold (ADR-0062). | Draft |

### Constraints

| ID | Constraint | Status |
|----|------------|--------|
| C-001 | The charter's model is canonical. The old ports and first-fault results are replaced outright, with no legacy reader or switch. | Draft |
| C-002 | The ArchUnit gate holds, and no rule is widened: the onion rules, `PACK_VALIDATION_IS_FRAMEWORK_FREE`, `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK`, `PACK_CODES_HAVE_ONE_OWNER`. | Draft |
| C-003 | No CLI change and no change to `PackRulesets.closure()` or `PackCheck` (#478 slots in as is). | Draft |
| C-004 | No parity test of open-packs codes is added here (D6). | Draft |
| C-005 | Tests and examples carry only the canonical shape, except error-handling tests (invalid packs). | Draft |
| C-006 | The wire codes do not change: no code is added, removed or renamed. | Draft |

### Key Entities

- **`ImpactCheck`** (`packs.domain`): judges a pack write or removal. It yields `Impact(verdict, notices)` and its
  `Rule` view.
- **`ClosureWalk`** (`packs.domain`): the facts of one declaring pack's own closure (fallback profile, role pins,
  profile enhancements). The reference checks run in the compose it walks.
- **`MutabilityCheck`** (`packs.domain`): a write's operations judged against the closure's mutability policies.
  Replaces `WritePolicyGate`.
- **`CharterRefusal`** (unchanged shape): code, packs, violations. A pack write's violations now name every fault.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Each acceptance scenario of User Stories 1 to 5 maps to at least one passing test, with `@contract`
  scenarios for 1, 2, 4 and 5.
- **SC-002**: Every existing single-fault refusal test passes with the same code, status and packs.
- **SC-003**: `./gradlew verifyAll` is green on Java 25, with the whole-pack round trip and the ArchUnit gate.

## Assumptions

- **A1, the "required floor".** Decision 6 lists a required floor under `ClosureWalk`. No pack write refuses on the
  floor today: the floor is only read for the FR-027 notices, and an activation's floor is judged by the activations
  slice (`DocumentRules`, `artifact_required`). This Mission adds no floor rule and leaves that check in activations.
  The ADR-0075 slice-7 amendment says so.
- **A2.** The open-packs parity of codes is delivered by open-packs' `charter_service_check.py` at its pin (D6). The
  issue is closed with a note saying so.
