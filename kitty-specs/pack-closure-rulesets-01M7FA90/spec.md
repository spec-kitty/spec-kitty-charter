# Mission Specification: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline

**Mission Branch**: `kitty/mission-pack-closure-rulesets-01M7FA90` (lands on `slice-5-554-pack-closure-rulesets`, then
on `main` by pull request)
**Created**: 2026-11-15
**Status**: Draft
**Input**: #554, slice 5 of epic #549 (ADR-0075 decisions 3, 4, 5 and 7, as amended on 2026-11-13). Kind `feature`,
size L, profile java-jenny. Grounding: [research.md](research.md).

## Intent Summary (operator, 2026-11-15)

The orchestrator sent the operator the summary below on 2026-11-15. The operator ruled on Q1 and raised no other
change. The 2026-11-15 rulings on #549 also bind it.

A pack author or an operator registers a pack, or asks validate mode for a verdict on one. Later, `pack-check` asks too
(#478). Today the same pack is judged by two pipelines kept equal by hand:

- validate mode (`PackPolicy`);
- the registration load (`WholePackStore` and `DomainRepositoryService`).

Converters glue the two, and each runs checks the other misses.

This Mission makes every pack check a rule of the kernel's rule vocabulary, over the `packs` domain objects:

- **Per kind.** One ruleset per pack file kind. They compose into one `PACK` ruleset.
- **Across packs.** One `CLOSURE` ruleset over the descriptors of a set of packs with levels.
- **One pipeline.** Validate mode and the registration load run the same rules.

The two answers keep their wire codes and their order, with one exception, which the operator ruled: an unmodelled
file.

Rulings that bind this Mission:

| Ruling | Effect |
| --- | --- |
| Operator ruling 1, 2026-11-15 (#549) | The `packs` domain stays canonical: no parallel model. `FileSystemPackInspector` stays. The registration load keeps its reader and runs `PACK` through a port that `packs.domain` declares. |
| Operator ruling 3, 2026-11-15 (#549) | Kept compatible for #556: an external check that cannot decide fails closed later. This Mission adds no external check. |
| Operator ruling 4, 2026-11-15 (#549) | A code owns its severity. |
| `DM-01M7FARH7YMGS2X0BRV1HKH47B` (operator, Q1) | An unmodelled file is a `file_not_held` warning. Pack authors may add docs and reference files that do not affect the service: warn, then ignore. The file is not held and not round-tripped. |
| ADR-0075 amendment, 2026-11-13 | `pack-validation` depends on `:packs` and names only `packs.domain`. Nothing moves out of `packs`. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One verdict, whichever way a pack is judged (Priority: P1)

A pack author runs validate mode on a pack, then registers the same pack. Both name the same faults, each with its own
code. Today validate mode misses the unmodelled, manifest-membership and reserved-shard checks. Registration misses the
level, org-fragment, duplicate-id and asset checks.

**Why this priority**: it is the point of the slice. One rule exists once, and parity by hand goes.

**Independent Test**: For each planted invalid pack, validate mode's findings and the registration's violations name
the same set of faults.

**Acceptance Scenarios**:

1. **Given** a pack with a duplicate id and a manifest that lists a missing file, **When** it is validated and then
   registered, **Then** both answers name both faults.
2. **Given** a valid example pack, **When** it is validated and registered, **Then** both answers are clean, apart
   from the warnings each answered before.

---

### User Story 2 - The wire does not move (Priority: P1)

A client reads the registration refusal and validate mode's verdict. Codes, pointers and their order stay as they are
today. The one exception is the unmodelled-file ruling (User Story 3).

**Why this priority**: the contract is a "must never go wrong" (charter), and open-packs' expected-code fixtures pin
the codes.

**Independent Test**: The existing contract scenarios, refusal-code tests and wire-token parity tests pass unchanged,
apart from the scenarios User Story 3 rewrites.

**Acceptance Scenarios**:

1. **Given** the registration scenario with two broken graph documents, **When** it runs, **Then** the
   `drg_schema_error` violations come first, in path order, as today.
2. **Given** a pack with another fault and a manifest mismatch, **When** it is registered, **Then** the manifest
   mismatch is not reported, because manifest membership is judged only when nothing else failed, as today.

---

### User Story 3 - An unmodelled file is warned about, then ignored (Priority: P1)

A pack author adds `notes.txt`, a `LICENSE` or a reference spreadsheet beside the pack content. No pack file kind
models it.

**Why this priority**: the operator's Q1 ruling. Packs carry author aids that do not affect the service.

**Independent Test**: Register a valid pack plus `notes.txt`. The registration succeeds with a `file_not_held` warning
naming `notes.txt`. The stored pack does not hold the file, and the round trip does not see it.

**Acceptance Scenarios**:

1. **Given** a valid pack with `notes.txt`, **When** it is registered, **Then** it is accepted with one `file_not_held`
   diagnostic at `notes.txt`. The stored pack and its revision are those of the pack without the file.
2. **Given** the same pack, **When** it is validated, **Then** the verdict is valid, with one `file_not_held` warning.
3. **Given** a retired `PROVENANCE.md`, or an orphan prompt fragment, **When** it is registered, **Then** it is still
   refused with its own code (`retired`, or the orphan's). The ruling covers only files that nothing models.

---

### User Story 4 - Cross-pack conflicts, every one at once (Priority: P2)

An operator composes a set of packs with levels: built-in, org and project. Each composition fault is reported, not
just the first.

**Why this priority**: `pack-check` (#478) runs `CLOSURE` without the graph walk. The service needs it in the same
shape.

**Independent Test**: A set with a dependency cycle and a name conflict reports both, each with its existing code.

**Acceptance Scenarios**:

1. **Given** packs with a cycle and an unknown dependency, **When** the set is composed, **Then** both faults are
   reported, with their codes unchanged.
2. **Given** a set whose only fault is an unresolved qualified reference, **When** `CLOSURE` runs, **Then** it reports
   nothing. Reference checks belong to #556's closure walk.

---

### Edge Cases

- **A typo hidden as an unmodelled file.** For example, `directives/x.yml` where `.yaml` is modelled. It becomes a
  warning and is ignored (Q1). The warning names the file, so the author sees it.
- **A pack whose only faults are unmodelled files.** It is now accepted. Scenarios that used `notes.txt` as a
  convenient second fault are rewritten to use a fault that still refuses.
- **The reserved shard.** Validate mode reports it as `drg_schema_error`, and registration as `reserved_shard`. Each
  surface keeps its current code.
- **A pack the inspector cannot read.** It is still `unreadable` or `pack_unreadable`, as today.
- **Slice F's endpoint check.** If F has landed, the graph rule calls F's endpoint bridge, and
  `relationship_endpoint_not_allowed` is unchanged.

## Domain Language

| Term | Meaning | Avoid |
| --- | --- | --- |
| **rule** | A kernel `Rule` that returns every `Reason` it finds | "validator", "check class" |
| **ruleset** | A `ModularRuleset`, itself a rule. Per kind, `PACK` and `CLOSURE` | "policy" for the new code |
| **finding** | One `Reason`: a code, its severity, a location and a detail | "breach", "error" |
| **validate mode** | Judging a pack without registering it (`PackValidation`) | "dry run" |
| **not held** | A file the registration removes before storing, reported as `file_not_held` | "skipped", "dropped" |

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Per-kind rulesets | As a maintainer, I want one ruleset for each pack file kind: the descriptor, graph, step, artifact, asset, fragment, prompt slot and glossary. Each expresses today's checks for its kind, so that a rule exists once. | High | Open | [build] | no |
| FR-002 | `PACK` ruleset | As a maintainer, I want the per-kind rulesets composed into one `PACK` ruleset over the pack's contents. It also holds the checks that only the load runs today: manifest membership (judged only when nothing else failed), the reserved shard and orphan fragments. It is the one judgement of a pack. | High | Open | [build] | no |
| FR-003 | `CLOSURE` ruleset | As an operator, I want a `CLOSURE` ruleset over the descriptors of a set of packs with levels. It covers the dependency cycle, the unknown and out-of-range dependency, the parent, the level, and the name, sibling and preset conflicts. It reports every fault, needs no graph, and keeps today's codes. | High | Open | [build] | no: a valid set on the same fixture reports nothing |
| FR-004 | Plain functions stay plain | As a maintainer, I want classification, ordering and precedence, and the manifest set difference kept as plain functions that rules call, so that a rule adds no code where a function suffices. | Medium | Open | [ratchet] | yes: covered by FR-001 and FR-002's tests, not by its own |
| FR-005 | Validate mode runs `PACK` | As a pack author, I want validate mode's verdict to come from `PACK`, so that it finds what registration finds. | High | Open | [build] | no: the load-only fault is found in validate mode, where it was missed before |
| FR-006 | Registration runs `PACK` through a port | As a maintainer, I want the registration load to keep its reader and run `PACK` through a port that the `packs` domain declares and the service's composition root wires, so that the packs domain stays canonical (ruling 1). | High | Open | [build] | no: registration refuses a validate-only fault it accepted before |
| FR-007 | Converters deleted | As a maintainer, I want the converters between `PackFinding`, `PackElementViolation` and `RefusalViolation` deleted, with no replacement shim, so that one pipeline remains. | High | Open | [build] | no: a search for the converter methods returns nothing |
| FR-008 | Two mappers, each keeping its order | As a client, I want one mapping from a ruleset's findings to the API problem, and one to the validate-mode verdict. Each orders its findings as its surface does today. | High | Open | [build] | no |
| FR-009 | Typed codes | As a maintainer, I want the string sub-codes (`schema`, `retired`, `manifest_membership`, `asset`, `reserved_shard`, `domain`, `reference`, `unreadable`, `companion`, `invalid` and the like) to become typed codes. Each owns its severity, and its wire token is unchanged. | High | Open | [build] | no |
| FR-010 | An unmodelled file is not held | As a pack author, I want a file that no pack file kind models to be reported as a `file_not_held` warning and not held. The registration accepts it, and neither the stored pack nor the round trip carries it (Q1). The `unmodelled` violation code goes. | High | Open | [build] | no: `notes.txt` was refused before and is accepted with a warning after |
| FR-011 | Endpoint check rehomed | As a maintainer, I want the graph rule to call slice F's endpoint bridge, so that #515's check lives in `PACK` with its code `relationship_endpoint_not_allowed` unchanged. | Medium | Open | [build] | no |
| FR-012 | Tests per rule and per mapper | As a maintainer, I want the tests of `PackPolicy`, the inspector's verdicts, the resolver's refusals and the converters collapsed to one test class per rule and one per mapper. The planted invalid packs are reused as rule fixtures. | Medium | Open | [build] | no |
| FR-013 | Records follow | As a maintainer, I want these records to say what the service does: the contract change fragment for FR-010, under `### Breaking`; the rewritten scenarios; the ADR-0075, ADR-0062 and ADR-0065 annotations; and #554's issue text amended to keep `FileSystemPackInspector`. | Medium | Open | [build] | no |
| FR-014 | Open-packs draft | As the orchestrator, I want a draft issue for `spec-kitty-open-packs` in the Mission research (`open-packs#TBD-unmodelled-not-held`). Its expected-code fixtures that expect `unmodelled` change, and its Python port of the rule follows. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gates green | `./gradlew verifyAll` passes on Java 25, with strict dependency verification and no new dependency. That includes the whole-pack round trip, the contract checks, the style checks and the architecture gate, with `PACK_VALIDATION_IS_FRAMEWORK_FREE` and the onion rules unchanged. The docs checks pass. | Quality | High | Open |
| NFR-002 | Wire parity | 100% of the existing contract scenarios and refusal-code tests pass unchanged, except those that assert `unmodelled`, which FR-010 rewrites. The count of rewritten scenarios is recorded in the pull request. | Quality | High | Open |
| NFR-003 | Mutation margin widened | The `pack-validation` mutation floor has a margin above 0 (ADR-0062), measured after the move. No floor is lowered. The `packs` floor is measured again and set honestly. | Quality | High | Open |
| NFR-004 | No second derivation | Rules read only the pack contents and census the load produced. They never parse a file again. Review checks it, and a test proves that a rule run reads no file. | Maintainability | High | Open |
| NFR-005 | Fewer tests | The test count over the collapsed classes falls from today's 474, with no rule left uncovered. Every rule has at least one failing and one passing case. | Maintainability | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical model | The rules run over the `packs` domain objects. There is no parallel model, no legacy reader and no old-string mapper kept (charter; ruling 1). | Technical | High | Open |
| C-002 | Implement after slice F | Implementation starts once slice F's PR #567 merges and this branch rebases onto `main`. Specify and plan run now. | Process | High | Open |
| C-003 | Module direction | `pack-validation` depends on `:packs` and names only `packs.domain`. `:packs` never depends on `pack-validation`. Only the composition root wires the port. | Technical | High | Open |
| C-004 | Out of scope | Out of scope: #556's external checks (the qualified references, the fallback profile, impact and mutability) and its `ERROR`-severity fail-closed codes; #552's identifier grammar; #555's round-trip move; #478's jar; #535, #518 and #358. | Business | High | Open |
| C-005 | Contract versioning | The 0.x line may break on a minor, with a `### Breaking` CHANGELOG entry (ADR-0022). | Technical | High | Open |
| C-006 | Process | Commits are signed, with no Co-Authored-By trailer and no model names. Integrate `main` by rebase only. Gradle runs in the foreground with `--max-workers=1`, one run at a time. | Process | High | Open |

### Key Entities

- **Pack contents**: what the inspector reads from one pack: its files, census and domain objects (`packs.domain`).
- **`PACK`**: the ruleset over one pack's contents.
- **`CLOSURE`**: the ruleset over the descriptors of a set of packs with levels.
- **Finding**: a kernel `Reason`, whose code owns its severity.
- **Judging port**: what `packs.domain` declares so that the registration load runs `PACK`, wired by the service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Validate mode and registration name the same faults for 100% of the planted invalid packs. — [build] ·
  no-op passable: no
- **SC-002**: Every wire code and its order are unchanged across the existing scenarios, apart from the `unmodelled`
  rewrite. — [ratchet] · no-op passable: yes, so it is paired with SC-001, which a do-nothing change fails
- **SC-003**: A pack that differs from a valid one only by a file nothing models is accepted with one warning, and is
  stored identical to the valid one. — [build] · no-op passable: no
- **SC-004**: The three converter families and the string sub-codes are gone from main code. — [build] · no-op
  passable: no
- **SC-005**: #554 is closed with evidence, and epic #549 is ticked. #355's validate-mode half is referenced. —
  [build] · no-op passable: no

## Assumptions

- **An unmodelled file is any file that no rule models.** It is neither retired nor an orphan fragment, which keep
  their own codes. The Markdown fallback of 2026-11-11 (#451) becomes the general rule.
- **"Not round-tripped" is met by not holding the file.** The round trip runs over the stored pack. #555, which runs in
  parallel, is told through the orchestrator.
- **Slice F's shapes.** The relayed facts in research.md are checked against `main` once F merges.
