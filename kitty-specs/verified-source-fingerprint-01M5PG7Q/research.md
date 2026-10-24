# Research: Verified source fingerprint on every read

Phase 1 squad (2026-10-24, main 555f923): architect-alphonso (opus), java-jenny (sonnet),
reviewer-renata (opus), each profile-loaded through the CLI, read-only. Full synthesis kept with
the mission runner; the decisions and their grounds are below.

## R1. Where the digest of a read lives

- **Decision**: Application edge, `ConsistentRead` over the `PackSources` port.
- **Rationale**: "A refused read whose inputs changed is stale" is a use-case rule; it must span
  every port call a use case makes and sit outside the not-found mapping. The test seam
  (`DriftingSources`) already wraps the port.
- **Alternatives considered**: Inside `DomainRepositoryService` (rejected: threads a fingerprint
  through domain port returns, breaking `STORAGE_RETURNS_DOMAIN`, covers one port call, every
  adapter re-implements it). Per controller (rejected: the header would come from a fresh digest,
  not the verified one).
- **History**: `application/ConsistentLoad` did this until `2bb3f76` (PR #139) deleted it with
  `FileSystemCharterPackRepository`; only `ServiceInfoQuery` kept an inline copy.

## R2. What the digest covers

- **Decision**: `pack.yaml` + every file of a declared loaded kind + the declared graph inputs
  (root shards, `drg/fragment.yaml`), from one `PackFileSet`.
- **Rationale**: #120 allows "digest every file kind the store loads"; keeping the graph inputs
  keeps `/info`'s value and the two `@contract` scenarios that pin "the fingerprint follows a
  graph shard". The test pins the exact equation, so "exactly" still holds as a stated set.
- **Alternatives considered**: Strict equality, dropping graph inputs (architect's
  recommendation; deferred to the operator, see spec Open decisions). Digesting the whole tree
  (rejected: false `stale_input` for unrelated files, changes `/info`). Digesting every
  `PackFileKind` mapped kind (rejected: same, for toolguides and others no read loads).

## R3. Global or per-read digest

- **Decision**: Global (`ProjectConfigSources`), so the header equals `/info`'s
  `sourceFingerprint`, which the scenario harness checks on every 200.
- **Consequence**: A broken sibling pack refuses a tactic read (`pack_unreadable`). Listed for
  the operator.

## R4. Property test without jqwik

- **Decision**: Plain JUnit with a seeded `java.util.Random` generator (200+ trees, seed in the
  failure message). The generator plants loaded files (each kind, root, nested, hidden names)
  and decoys (`.yml`, unclassified YAML, toolguide, procedure, nested shards) and so knows the
  expected sets. Observation by mutation: mutate one file, see whether the digest changes.
- **Planted omission**: The oracle check is a reusable assertion; a test feeds it a digest that
  leaves one loaded kind out and asserts it names the omitted file. On `main`, the same check
  is red because tactics are undigested (the red-first proof).

## R5. Parallel authorities found

| Place | What it enumerates | After this mission |
|---|---|---|
| `FileSystemPackSources.fingerprint` | pack.yaml, root shards, fragment | derives from `PackFileSet.digested` |
| `FilesystemPackStore.kindFiles` + four `*_KIND` constants | files per kind | `PackFileSet.files(LoadedKind)` |
| `FileSystemPackInspector` LOAD_PATH | pack.yaml, root shards, fragment | unchanged scope (validate mode, #115); shares `graphShards` |
| `contracts/packs/file-kinds.yaml` | the glob table | stays the classification authority |
| `ProjectConfigSources` | config.yaml, charter.yaml | unchanged (#127) |

## R6. Out of scope (follow-ups)

- The fingerprint header is declared on refusal responses (forced by lint rule
  `charter-headers-on-every-response`) although no refusal carries it: a contract change.
- `required: false` on the header for 200s: a contract change.
- The full kind registry (#169), directive/styleguide/paradigm reads (#126), config inputs (#127).

## R7. Plan-point adversarial squad (2026-10-24) and dispositions

Cast: architect-alphonso (architecture and contract), reviewer-renata (fakeability, fidelity),
qa-test-designer-tessa (test design); all opus, profile-loaded, read-only. All three: proceed
with changes; none asked for an operator stop.

| # | Finding | Disposition | Evidence |
|---|---|---|---|
| A1 | Global first digest turns a missing pack directory from 404 into 500 `pack_unreadable` | accepted | spec Edge Cases and Open decisions; plan IC-01 prose list (TacticNotFound, CharterRefusalCode, CHANGELOG "Changed"); errors.feature scenario |
| A2 / T1 | `TacticReadWiredTest` context cannot start; `ConsistentRead` must not be scanned | accepted | plan ConsistentRead bullets; IC-04 surfaces |
| A3 / R7 | Fingerprint definition inconsistent (spec, `ServiceInfo.yaml`, header prose); dead `x-source` | accepted | spec Key Entities and Intent; plan IC-01 |
| A4 | Classification prefix differs for a non-normalized configured root | accepted | plan PackFileSet bullets; generator case |
| A5 | Deep walk reaches `/info`; unreadable file anywhere refuses every read | accepted (recorded for the operator; behavior kept: fail closed) | spec Open decisions |
| A6 | `pack.yaml` constant owned by the authority; FR-008 wording | accepted | plan PackFileSet; spec FR-008 |
| A7 | `GuardWiringTest`, `WorktreeGuardTest` break | accepted | plan IC-04 |
| A8 | ABA window now covers tactic files | accepted | plan IC-01 (ADR amendment) |
| R1 / T4 | Planted omission tested only the checker | changed: production-path seam `digest(locations, filesOf)` and kind-set overload with `@EnumSource` mutants | plan Testing design |
| R2 / T3 | "Files the load reads" unobserved or circular | accepted: observed through `FilesystemPackStore.read` outputs only | plan Testing design |
| R3 / T6 / T7 | Glob subtleties, hidden directories, symlinks, rule-exclusion overlaps | accepted: explicit name-shape table with verdicts | plan Testing design; spec Edge Cases |
| R4 | Stale step could edit a shard named `tactic.graph.yaml` | accepted: strict `*.tactic.yaml`, exactly one match | plan Testing design |
| R5 | Tactic 200 scenarios could be vacuous | accepted: body holds the planted id | plan Testing design |
| R6 | Missing-directory regression unlisted | accepted (same as A1) | spec Edge Cases |
| R8 / T9 | Red-first versus green commits | accepted: red runs recorded as WP review evidence | plan Testing design |
| R9 | Single-tactic stale lacks retry | accepted | spec US2 #2 |
| R10 | NFR-002 category and failure path | accepted | spec NFR-002 |
| R11 | Hidden directories, exclusion precedence | accepted | spec Edge Cases |
| T2 | Harness equality cannot catch #120 | accepted: FR-012 scenario "fingerprint follows a tactic file" | spec FR-012; plan Testing design |
| T5 | Outcome table misses non-refusal rows | accepted | plan ConsistentRead bullets; data-model.md rows 6-7 |
| T8 | Red reason must be the header, not body conformance | accepted: the red evidence must show the fingerprint assertion | plan Testing design (red capture) |
| T10 | DriftingSources fires during the `/info` probe on `main` | accepted: javadoc | plan IC-04 |

## R8. Post-tasks squad (2026-10-24) and dispositions

Cast: planner-priti (decomposition, opus), profile-loaded, read-only. Verdict: fix first, then
finalize; no blocker. All step phrases the prompts rely on exist verbatim in the glue.

| # | Finding | Disposition | Evidence |
|---|---|---|---|
| P1 | `ContractDescriptionsTest` pins description phrases WP01 edits | accepted | WP01 T004 item 7, validation, DoD |
| P2 | Scenario titles not in plan; plan still names service/errors features | accepted | WP01 T005 titles; plan.md Project Structure, IC-02, Testing design |
| P3 | Sample YAML for three kinds lacked `schema_version`; typed-list half could be vacuous | accepted | WP02 T006 shapes and non-vacuity assertion |
| P4 | Missing-directory scenario ambiguous; sibling case unpinned | accepted: two scenarios | WP03 T011 |
| P5 | Stale-beats-404 drift step unnamed | accepted | WP03 T011 |
| P6 | Impossible `pack_not_found` case described | accepted | WP01 T004 item 3 |
| P7 | Deep-walk refusal unpinned | accepted | WP02 T009 item 4 |
| P8 | "valid" planted tactic ids | accepted | WP02 T006 |
| P9 | `service-contract.md` refresh mentions at ~119, ~149 | accepted | WP01 T003 |
| P10 | WP02/WP03 size | kept as is (cohesive; splitting breaks green commits) | tasks.md |

## R9. Pre-PR squad (2026-10-24) and dispositions

Cast: reviewer-renata (correctness), architect-alphonso (boundary and overlap), reviewer-renata
(contract compatibility and docs truthfulness); all opus, profile-loaded, read-only. Verdicts:
changes requested (correctness), proceed with changes (boundary, contract). No contract shape or
version-rule violation; no consumer in core breaks.

| # | Finding | Disposition | Evidence |
|---|---|---|---|
| C1 | HIGH: the deep walk throws when an entry vanishes mid-walk (atomic save), so every read answers a non-retryable `pack_unreadable` | accepted | `fix(packs): tolerate entries that vanish while a pack is walked`, red run 65 refusals before, 0 after |
| C2 | A symlinked pack root loads as an empty pack (predates the branch) | deferred_with_rationale: older behavior, separate fix | #174 |
| C3 | Shard order differs on Windows | accepted | `fix(packs): order graph shards by name on every OS` |
| C4 | An `Error` from the load skips the stale comparison | accepted: documented; load failure kept as suppressed | `fix(packs): keep the load failure when the second digest fails` |
| B1, B2 | Inspector and `PackFileSet` call each other; duplicated `PACK_YAML`/`FRAGMENT` | accepted | `refactor(packs): make PackFileSet the only owner of the pack file layout` |
| B3, B4 | `LoadedKind` duplicates kernel `ArtifactKind`; serializer kind strings | changed: `LoadedKind` kept as the typed loaded set (#169 grows it) but carries its `ArtifactKind` | `refactor(packs): derive LoadedKind from the kernel's ArtifactKind` |
| B5, B6 | Walks not cross-referenced; "what a load needs" | accepted | same refactor commit |
| B7 | Kind list repeated in prose | accepted: docs say "today"; contract prose keeps the list (adding an input changes values, not shape) | `docs(fingerprint): state exactly which unreadable files refuse a read` |
| B8 | The packs slice cannot wire `TacticReadService` alone | deferred_with_rationale: two `PackSources` beans until #127 | #127 |
| B9 | Fully qualified `Verified<Tactic>` | accepted | `docs(packs): javadoc follow-ups` |
| B10 | #126 is the generic artifact routes | accepted | spec C-005, Follow-ups |
| K1, K2 | CHANGELOG `pack_not_found` bullet stale; "unreadable file" over-claims; `/info` consequence missing | accepted | `docs(fingerprint): state exactly which unreadable files refuse a read` |
| K3, K4, K5 | `PackListing1`, "nine codes", drift-step wording | accepted | same docs commit |
| K6 | `OfferingUnreadable` examples name unraised codes | deferred_with_rationale: contract example change | #176 |
| K7 | Control file excluded by extension only | accepted | `test(scenarios): control the tactic-file scenario with an unclassified YAML file` |
| K8 | Sibling-directory refusal pinned as `@contract` | accepted as deliberate (fail closed, ADR-0011, ADR-0018 D11) | tactics.feature |
| K9 | Fingerprint prose names file-system globs | deferred_with_rationale: hosted-first reshape | #127 |
| K10 | `pack_unreadable` on `getPackTactic` unpinned | deferred_with_rationale | #176 |
| WP-review LOWs | listing refusal names the directory; wall-clock timeout; stale javadoc; event-order assertions | accepted | `fix(packs): name the configured pack ...`, `test(packs): budget the exactness test on CPU time`, `fix(packs): keep the load failure ...`, `docs(packs): javadoc follow-ups` |
| Validate-mode walk | `FileSystemPackInspector.yamlFiles` still uses `Files.walk` | deferred_with_rationale: validate mode is not wired to a route | #115 |
