# Mission Specification: Verified source fingerprint on every read

**Mission Branch**: `fix/verified-source-fingerprint`  
**Created**: 2026-10-24  
**Status**: Draft  
**Input**: Issues [#120](https://github.com/spec-kitty/spec-kitty-charter/issues/120) (the source fingerprint must digest exactly the files the pack load reads) and [#173](https://github.com/spec-kitty/spec-kitty-charter/issues/173) (the tactic reads send no `Charter-Source-Fingerprint` on their 200), folded with the Phase 1 grounding squad's synthesis.

## Intent Summary

The **primary actor** is core's seam (the Spec Kitty CLI talking to the charter service). It
reads tactics and service info and uses the source fingerprint to know whether an answer is
fresh. The **trigger** is any successful artifact read. The **outcome**: every successful read
answers with exactly one fingerprint, the one that read verified before and after loading,
covering exactly the pack files the service loads plus the declared graph inputs; a read whose inputs changed while it was
answered is refused with `stale_input` and carries no fingerprint. The **invariant**: a pack file
cannot be loaded without being digested, because the load and the fingerprint take their file
list from one place.

**Confirmation basis.** The operator dispatched this mission semi-autonomously on 2026-10-24
("decide, record the decision, and continue; ask only when a decision would change the
contract's shape or widen the scope"). The Phase 1 squad (architect, implementer, reviewer
lenses, profile-loaded) grounded both issues on `main` 555f923. The decisions below are the
runner's, recorded here and in the plan; none changes the contract's shape, and none widens the
scope beyond #120 and #173. The open points are listed for the operator under
[Open decisions](#open-decisions-for-the-operator).

## Grounding (main 555f923)

- `listPackTactics` and `getPackTactic` answer 200 with no `Charter-Source-Fingerprint`, and they
  verify no digest. Only `/info` digests twice and sets the header.
- The fingerprint digests `pack.yaml`, the root graph shards and `drg/fragment.yaml`. The pack
  load reads `pack.yaml` and every tactic, directive, styleguide and paradigm file (the last
  three since PR #170). None of the four loaded kinds is digested; the shards and the fragment
  are digested although no served read loads them.
- Four places enumerate "the files of a pack" independently, with different walks and orders.
- The before-and-after check that every read had before PR #139 (`ConsistentLoad`) was deleted
  with nothing in its place except an inline copy in `/info`.
- The scenario harness already checks that every 200 carries exactly one fingerprint equal to
  what `/info` answers right after; no scenario receives a tactic 200, so nothing catches the gap.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A tactic read answers with the fingerprint it verified (Priority: P1)

Core's seam lists a pack's tactics, or reads one tactic, and gets the source fingerprint on the
200 so it can tell whether that answer is still fresh, exactly as it does for `/info`.

**Why this priority**: The contract declares the header on both tactic 200s and core's freshness
work (#73) relies on it. Without it the seam cannot tell a fresh tactic answer from a stale one.

**Independent Test**: A contract scenario reads the tactics of a pack that holds a tactic file and
asserts the 200 carries the header once, equal to `/info`'s `sourceFingerprint` for the same
inputs. It fails on `main` and passes after the change.

**Acceptance Scenarios**:

1. **Given** a configured pack with a tactic file, **When** core lists the pack's tactics, **Then** the answer is 200 and carries `Charter-Source-Fingerprint` exactly once, equal to the `sourceFingerprint` that `/info` answers for the same inputs.
2. **Given** the same pack, **When** core reads that one tactic, **Then** the 200 carries the header exactly once, with the same value.
3. **Given** a pack with no such tactic, **When** core reads it, **Then** the 404 refusal carries no fingerprint.

---

### User Story 2 - A tactic read whose inputs change while it is answered is refused (Priority: P1)

A pack file the read loads is rewritten between the two digests. The service refuses with
`stale_input` instead of answering from inputs it cannot vouch for; asked again, it answers.

**Why this priority**: The fingerprint is only worth sending if the read verified it (ADR-0011,
ADR-0018 D11, fail closed).

**Independent Test**: A scenario arms a change to a **tactic file** after the read's first digest,
reads the tactics, and asserts `stale_input` with no fingerprint, then a 200 on the retry. A
negative control arms no change and gets a 200.

**Acceptance Scenarios**:

1. **Given** a tactic file changes while the next read is answered, **When** core lists the tactics, **Then** the answer is 500 `stale_input` with no fingerprint, and the same read asked again answers 200.
2. **Given** a tactic file changes while the next read is answered, **When** core reads one tactic, **Then** the answer is 500 `stale_input` with no fingerprint, and the same read asked again answers 200.
3. **Given** a pack file changes while a read for an absent tactic is answered, **When** core reads it, **Then** the answer is `stale_input`, not the 404 (a refused read whose inputs changed is stale).
4. **Given** no file changes while the next read is answered, **When** core lists the tactics, **Then** the answer is 200 with the verified fingerprint.

---

### User Story 3 - The fingerprint covers exactly the files the service loads (Priority: P1)

A pack author edits any tactic, directive, styleguide or paradigm file, or `pack.yaml`. The
fingerprint changes. Editing a file the service neither loads nor declares as a graph input (an
unrelated YAML file, a toolguide or procedure, a nested graph shard) does not change it.

**Why this priority**: Without this, a stale tactic answer passes the freshness check (#120), and
every new artifact kind would silently fall outside the digest.

**Independent Test**: Over generated pack trees (a seeded generator that plants loaded files and
decoys and so knows the expected set itself), mutating each loaded file changes the fingerprint
and mutating each decoy does not; the files the load reads equal the files the fingerprint
digests. Dropping one kind from the fingerprint's input (a planted omission) makes the test fail.

**Acceptance Scenarios**:

1. **Given** a pack holding one file of each loaded kind and decoys, **When** any loaded file's bytes change, **Then** the fingerprint changes.
2. **Given** the same pack, **When** a decoy's bytes change, **Then** the fingerprint is unchanged.
3. **Given** a generated pack tree, **When** the service loads it and digests it, **Then** the files the load read are the planted loaded files, and the files the digest covers are those plus `pack.yaml` and the declared graph inputs, and nothing else.
4. **Given** the fingerprint's input with one loaded kind left out, **When** the test runs, **Then** it fails, naming the undigested file.

---

### Edge Cases

- A loaded file nested below the pack root (`sub/x.tactic.yaml`), inside a hidden directory (`.h/x.tactic.yaml`) or under an excluded directory whose file rule wins (`presets/x.tactic.yaml`) is loaded and so digested, as the store loads it today.
- A hidden file whose name matches a kind's pattern (`.x.tactic.yaml`) is loaded and so digested.
- `x.tactic.config.yaml`, `x.tactic.yaml.bak`, a directory named `d.tactic.yaml`, a nested `sub/y.graph.yaml`, a nested `pack.yaml` and kind files reached through a directory symlink are neither loaded nor digested; a file symlink is followed by both.
- A configured pack whose directory is missing, or any pack holding an unreadable file or unlistable directory, refuses the first digest: every read answers 500 `pack_unreadable` (a tactic read of a missing pack directory answered 404 `pack_not_found` before).
- Files ending in `.yml` or upper-case `.YAML` are neither loaded nor digested.
- A file symlinked into a pack is digested by the bytes the load reads.
- The order of digested files is one canonical order, independent of the file system's listing order.
- A broken sibling pack makes the global digest refuse (`pack_unreadable`), so a tactic read of a healthy pack is refused too (fail closed; see open decisions).
- A read that fails to load digests again; if the digest differs, or the second digest cannot be read, the answer is `stale_input`.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | Requirement | Priority | Status | Delivery | No-op passable? |
|---|---|---|---|---|---|---|
| FR-001 | Tactic list header | As core's seam, I want `listPackTactics` to answer 200 with `Charter-Source-Fingerprint` exactly once, equal to `/info`'s `sourceFingerprint` for the same inputs, so that I can check the answer's freshness. | High | Open | [build] | no |
| FR-002 | Tactic read header | As core's seam, I want `getPackTactic` to answer 200 with `Charter-Source-Fingerprint` exactly once, with the same value, so that a single-tactic answer is checkable too. | High | Open | [build] | no |
| FR-003 | No fingerprint on a refusal | As core's seam, I want no refusal of a tactic read (`pack_not_found`, `tactic_not_found`, `stale_input`, a pack code) to carry the fingerprint, so that only a verified answer carries one. | High | Open | [ratchet] | no — paired with FR-001's 200 on the same pack fixture as the positive control |
| FR-004 | Verified tactic reads | As core's seam, I want each tactic read to digest its inputs before and after it loads, and to refuse with `stale_input` when the two differ, so that a 200 never answers from inputs that changed while it was read. | High | Open | [build] | no |
| FR-005 | Stale beats a refusal | As core's seam, I want a tactic read that would refuse (pack or tactic not found, a load failure) to answer `stale_input` instead when its inputs changed meanwhile or the second digest cannot be read, so that a refusal is never caused by a half-written pack. | Medium | Open | [build] | no |
| FR-006 | Loaded files are digested | As a pack author, I want a change to `pack.yaml` or to any tactic, directive, styleguide or paradigm file the service loads, at any depth, to change the source fingerprint, so that a stale answer is caught. | High | Open | [build] | no |
| FR-007 | Only declared files are digested | As a pack author, I want the fingerprint to digest `pack.yaml`, the loaded files and the declared graph inputs (the root `*.graph.yaml` shards and `drg/fragment.yaml`, digested today and kept so `/info`'s meaning does not change), and nothing else: a change to an unclassified YAML file, a `.yml` file, a nested graph shard, or a toolguide, procedure or other unloaded kind leaves it unchanged, so that edits that cannot affect an answer do not refuse reads. | Medium | Open | [build] | no — paired with FR-006 on the same generated fixture |
| FR-008 | One file-set authority | As a maintainer, I want the kinds the service loads and the graph inputs declared once, the pack load able to read only a declared kind (enforced by type for kind reads, and for the whole set by the FR-009 exactness test), and both the pack load and the fingerprint to take their files from that one declaration with one walk and one order, so that a kind cannot be loaded without being digested. | High | Open | [build] | no — the planted-omission test fails when the fingerprint's input drops a kind |
| FR-009 | Digested set is exact | As a maintainer, I want a test over seeded generated pack trees asserting that the files the load reads are exactly the loaded files the generator planted, and that the files the fingerprint digests are exactly `pack.yaml`, those loaded files and the declared graph inputs, with every expected set taken from the generator rather than from the code under test, so that drift is caught. | High | Open | [build] | no |
| FR-010 | One verified-read rule | As a maintainer, I want `/info` and the tactic reads to share one before-and-after verification, so that the refusal rule is written once and every later artifact read (#126) uses it. | Medium | Open | [build] | no — `/info`'s existing stale_input scenario and its negative control keep running against the shared rule |
| FR-012 | Fingerprint follows a tactic file | As core's seam, I want a contract scenario showing that editing a tactic file changes `/info`'s `sourceFingerprint` and editing an unloaded file does not, so that the coverage of #120 is pinned at the contract level and not only in unit tests. | High | Open | [build] | no — the unloaded-file edit on the same fixture is the control |
| FR-011 | Records follow the behavior | As a reader of the docs, I want the freshness page, the service contract page, the contract CHANGELOG, the fingerprint's contract prose and ADR-0018 to state the built behavior, so that the docs do not promise what the service does not do. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|---|---|---|---|---|---|
| NFR-001 | Generated-tree coverage | The loaded = digested test runs at least 200 seeded generated pack trees per run, each with at least one file of every loaded kind and at least one decoy, with a fixed seed reported on failure; it completes in under 10 seconds on the CI runner. | Correctness | High | Open |
| NFR-002 | Two digests per read | Each tactic read and each `/info` read computes the input digest exactly twice on its success path and on its load-failure path, pinned by a unit test that counts digests. | Correctness | Medium | Open |
| NFR-003 | Gate green | `./gradlew verifyAll` on Java 25 passes, including `HexagonalArchitectureTest`, the contract scenarios with their per-exchange fingerprint check, spotless and the docs checks. | Maintainability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|---|---|---|---|---|---|
| C-001 | No contract shape change | No schema, operation, parameter, response or header declaration changes; only behavior, prose, scenarios and the contract CHANGELOG (ADR-0021/0022). | Technical | High | Open |
| C-002 | Project config inputs stay | The `config.yaml` and `charter.yaml` inputs stay in the composite fingerprint; dropping them is #127 (ADR-0020 D12). | Business | High | Open |
| C-003 | No new test dependency | No property-testing library is added (jqwik is excluded, C-008 of an earlier mission); the generator is plain JUnit with a seeded `java.util.Random`. | Technical | High | Open |
| C-004 | Rings hold | The onion rings of ADR-0006 hold: the file-set authority stays in `infra.storage`, the verification rule in `application`, the header in `infra.api`. | Technical | High | Open |
| C-005 | Scope boundary | No new HTTP read is added for directives, styleguides or paradigms (#126); they are covered by the digest only. Removing the header declaration from refusal responses and making it required on 200s are contract changes, out of scope. | Business | High | Open |
| C-006 | Test first | Each behavior lands with its failing scenario or test run red before the fix. | Technical | High | Open |

## Key Entities

- **Source fingerprint**: the SHA-256 digest of, per configured pack in order, `pack.yaml`, every loaded file and the declared graph inputs (root `*.graph.yaml` shards, `drg/fragment.yaml`), each as its path under the pack's configured name and its bytes; and, when configured, `config.yaml` and `charter.yaml` with presence. One definition, used verbatim in the contract prose. `/info` reports it as `sourceFingerprint`; a 200 sends it as `Charter-Source-Fingerprint`.
- **Loaded file set**: the files of a pack the service loads, `pack.yaml` plus every file of a loaded kind.
- **Verified read**: a read that digests its inputs, loads, digests again, and answers only when both agree.

## Domain Language

- Canonical: **source fingerprint** (not "governance fingerprint" or "hash"); **loaded file set** (not "pack files" without qualification); **stale input** / `stale_input`.

## Success Criteria *(mandatory)*

- **SC-001**: Every successful read in the contract scenarios carries exactly one source fingerprint equal to what service info answers for the same inputs (100% of 200 exchanges). — [build] · no-op passable: no
- **SC-002**: Across every generated pack tree, zero loaded files fall outside the fingerprint and zero unloaded files fall inside it. — [build] · no-op passable: no
- **SC-003**: A planted omission (one loaded kind left out of the fingerprint) is detected in 100% of runs. — [build] · no-op passable: no
- **SC-004**: A pack file changed during a read is refused as stale for both tactic operations, and the retried read succeeds. — [build] · no-op passable: no

## Assumptions

- The fingerprint is global (all configured packs plus the project config), so a tactic read's value equals `/info`'s; a per-read subset would break that equality and ADR-0011.
- The root graph shards and `drg/fragment.yaml` stay in the digest as declared graph inputs: no served read loads them yet, but `/info` digests them today and two `@contract` scenarios pin that the fingerprint follows a graph shard. Keeping them avoids a behavior change for existing consumers; the cost is a refused read when only a shard changes during it, the same accepted consequence ADR-0018 D8 records for `config.yaml`.

## Open decisions for the operator

- **The digest now walks every pack tree.** `/info` and every tactic read take two recursive walks of all configured packs, and an unreadable file or unlistable directory anywhere in any pack refuses them with `pack_unreadable`. Before, `/info` listed only the pack root, and a tactic read of a missing pack directory answered 404. Fail closed per ADR-0011 and ADR-0018 D11; the 500 is declared (`OfferingUnreadable`).

- **Global digest fails every read when a sibling pack is broken.** A read of a healthy pack is refused with `pack_unreadable` when another configured pack cannot be read. This is fail closed and consistent with `/info`; scoping the digest per pack would break the header-equals-`/info` rule.
- **Graph inputs stay in the digest although no served read loads them.** Dropping them until the graph read lands (#73) would make the digest equal to the loaded set, at the cost of changing `/info`'s value and two `@contract` scenarios. Kept for now; see the follow-ups.

## Follow-ups (not in this mission)

- Remove `Charter-Source-Fingerprint` from refusal response declarations and adjust the `charter-headers-on-every-response` lint rule (contract change).
- Make the header required on 200 answers (contract change).
- The full kind registry (#169) beyond the loaded-kinds declaration.
- Directive, styleguide and paradigm HTTP reads (#126) go through the shared verified read.
- Dropping the project config inputs (#127).
- Dropping the declared graph inputs until a served read loads them, or moving them into the loaded kinds when the graph read lands (#73).
