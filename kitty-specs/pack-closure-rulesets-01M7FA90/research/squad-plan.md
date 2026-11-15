# Squad: plan point (2026-11-15)

Three read-only lenses reviewed `plan.md` at `d3e02cb0`, on `main` `67ddbe03`, after slice F: architecture (A),
contract and wire (C), and migration and tests (M). The binding rulings were not relitigated. Each finding is
dispositioned below.

- **FOLD**: the plan changes.
- **RULING**: an operator question, R1 to R4 below.
- **NOTE**: recorded, no change.

## Findings and dispositions

| ID | Sev | Finding (short) | Disposition |
|---|---|---|---|
| A-B1 / C-1 | BLOCKER | P6's org-fragment rule refuses every org pack with no `drg/fragment.yaml`, at registration and at the compose read. That breaks the `v1` and `base` Background archives and about 15 features. The level rules are dead or harmful when the staged root has no configured level. | **RULING R1** |
| C-2 | BLOCKER | P6's asset rules report the sidecar escape twice (`asset` and `asset_path_escape`). `asset_mime_invalid` would refuse packs that are accepted today (`assets.feature:150`). | **RULING R1** |
| A-B2 / C-4 | BLOCKER | The surfaces differ in more than the reserved shard. The schema faults (`schema`, one per violation, against `artifact_schema_error` with the detail vocabulary), the unreadable `pack.yaml` (pointer `pack`, stop), `duplicate_step_id`'s place and the problem-detail format all differ. So `PackRefusals` is a code-and-wording table. | **RULING R2** |
| A-B3 | BLOCKER | P1 would make the inspector reach `@Service` classes (`DomainRepositoryService`, and `WholePackStore` through `ManifestMembership.derivedAt`). That breaks `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK`. `ManifestMembership` is package-private in `infra.storage`. | **FOLD**: extract a Spring-free assembly function and the manifest derivation into `packs.domain`. "`THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` stays green" becomes IC-02's acceptance criterion. |
| M-1 | BLOCKER | The `:packs` store tests (63 `new WholePackStore(` sites) and the whole-pack gate lose their judge, because `:packs` may not depend on `pack-validation`. A no-op `PackRules` would make the gate pass trivially. | **RULING R3** |
| A-M1 | MAJOR | P1's list of facts the inspector lacks is incomplete. It misses 10 kinds of reader fault, among them `domain`, `reference`, the aggregate `build()` breach, the descriptor and not-a-pack refusals, the policy file, a misplaced preset, the store's graph read, the companion remap, the unmodelled list and the manifest file. | **FOLD**: IC-02 takes this as its acceptance checklist, with one planted fixture per row. |
| A-M2 | MAJOR | The stores are built with `new` throughout `packs`, so `PackRules` cannot be injected there. | **FOLD**: judge in `packs.application`. `PackRegistrationService.register` and `PackSourceStagers` take `PackInspector` and `PackRules` beans. `loadStaged` stays a pure reader. |
| A-M3 / C-5 | MAJOR | The compose read is undecided. "Stop judging" breaks the `judged()` withholding that keeps `Pack.guardKind` from breaching. Running `PACK` on the compose path re-reads every stored pack. | **FOLD**, under R1(a): the compose read judges exactly today's reader faults, in reader order, keeping the endpoint prefix. The withholding stays, as aggregate shaping. A test lists every compose-read 500 detail. |
| A-M4 / C-6 / C-7 | MAJOR | `ClosureFacts` cannot judge sibling or preset contests, and it is a parallel model. One `CLOSURE` batch changes the first refusal, because the reference checks interleave. A `Reason` has no `RefusalPack` list. IC-04's callers are wrong. The offering's descriptor check (`OfferingReadService:549`) has no route. | **FOLD**: each resolver check becomes a collect-all function in `packs.domain` over `PackUnderResolution`. The offering takes the first refusal in today's interleaved order, with its `RefusalPack` list. `CLOSURE` in `pack-validation` wraps those functions for `pack-check`. `ClosureFacts` is dropped, and so is `PackRules.closure`. The descriptor check becomes one `packs.domain` function, called by the descriptor rule, the offering and the resolver precondition. |
| A-M5 / M-4 | MAJOR | About 20 classes use `PackPolicy`'s non-rule members: `TENSION`, `joinsItself`, `bodyPathEscape`, `assetPathEscape`, `registeredGraph`, `descriptorFindings` and the constants. The graph build is a reader derivation. | **FOLD**: an early work package moves this residue into named `packs.domain` functions. `PackContents` carries the built graphs. The graph rule reads them, so no rule builds a graph. |
| M-2 / C-8 | MAJOR | The "unchanged" parity tests name deleted types (82 test files). | **FOLD**: WP01 takes a golden snapshot of both surfaces: code, severity, file or pointer, detail and order, plus the diagnostics, for every planted, example and scenario pack. The switch reproduces it byte for byte, apart from an enumerated diff list. The parity tests are rewritten onto `PackCodes` and the mappers, with the same assertions. NFR-002 is reworded. |
| M-3 | MAJOR | P6 is a second wire change that is not announced. | **RULING R1** |
| M-5 | MAJOR | The IC order cannot stay green: `unmodelled` has no code between IC-01 and IC-06, and IC-05 is a big bang. | **FOLD**: ten cut points, each green under `verifyAll`. Only the switch is atomic. See the breakdown below. |
| M-6 | MAJOR | A long-lived branch against #555 (#578), #556 and slice G. | **RULING R4** |
| M-7 | MAJOR | "One test per rule" loses the behaviours of rules interacting: suppression, dedup, the edges-read gate, the manifest gate, `PRECEDENCE` tie-breaks and the detail vocabulary. | **FOLD**: a third category of tests, `PACK` composition tests, one per interaction, listed in the tasks. `PackPolicyOrderTest` and `PackPolicyVocabularyTest` stay whole, as plain-function tests. |
| M-9 | MAJOR | Mutation floors: tests that kill mutants must stay in their subject's module. `pack-validation` grows from 1 mutant to more than 1,000. | **FOLD**: a test lives in its subject's module. Each work package runs a narrowed PIT before and after. The `pack-validation` target is a score of at least 90. Lowering a floor needs an ADR-0062 amendment. |
| C-3 | MAJOR | A duplicate artifact id today answers the aggregate shape (`{pack, validation_failed, message}`), not `domain`. | **FOLD**: P6's text is corrected. The fail-closed guard answers exactly today's aggregate shape, pinned by a test. Under R1(a) the duplicate-id answer at registration is unchanged. |
| M-8 | MEDIUM | The planted-pack test is not viable as described. `planted/` is not in testFixtures. There are 74 plants. `expected.json` holds validate-mode output only, and `"severity": "blocking"` is the vocabulary of the deleted enum. | **FOLD**: move `planted/` into testFixtures, generate a `registration` block from the golden, and compare the surfaces by fault identity. The severity token becomes `violation`, and `core_verdicts.py` follows, under the canonical-shape rule. |
| M-10 | MEDIUM | NFR-005, "the test count falls", rewards deletion. | **FOLD**: replace it with an enumerating test. Every `PackCodes` constant has at least one failing and one passing fixture, and the interaction list is covered. The test count is reported, not targeted. |
| M-11 | MEDIUM | ArchUnit's single-owner rules A to C, and their bite fixture, name `PackFindingCode`. | **FOLD**: the switch re-points them to the new owners and keeps the bite tests. NFR-001 says so. |
| A-D1 / C-12 | MEDIUM | `Rule.when` tests the subject, not earlier results, and `ModularRuleset` has no `when`. The derived manifest set must be shared with the stamp. | **FOLD**: the manifest gate is a plain sequence in `PackRulesets.pack`, over every violation registration counts today. Derivation is one function, shared with the stamp. |
| A-D2 | MEDIUM | The not-held list has two derivations. | **FOLD**: the removal reads the set from `PackContents`. `ExcludedFilesAgreementTest` gains unmodelled files. |
| A-D3 / M-12 | MEDIUM/MINOR | The fail-closed guard is undefined, and real parts never reach it. | **FOLD**: the guard fires when the reader recorded a violation or a build breach while `PACK` passed. It answers today's aggregate shape. A subset test runs over every pack: the reader's faults are a subset of `PACK`'s. |
| C-9 | MEDIUM | Q1's diagnostic detail and order. | **FOLD**: `PackRefusals` owns the diagnostics: the load's warnings in today's order, then one path-sorted not-held list. An unmodelled file's reason is `no pack file kind models this file`. |
| C-10 | MEDIUM | The full list of `unmodelled` assertions. Q1's records also cover `packs_packId.yaml:35` and the unreleased fragment of 2026-11-11, and the packs contract (`file-kinds.yaml`, `README.md`, `classifier-cases.yaml`). The fragment name needs a real date and exactly one `###` block. | **FOLD**: the classifier outcome stays `unmodelled`, because it is a classification, not a verdict. Only the wording changes, with a packs CHANGELOG line. The unreleased 2026-11-11 fragment is amended, and Q1 goes under `### Breaking`. |
| C-11 | MEDIUM | The `domain` pointer is an artifact id. The staged pack's name leaks into texts. | **FOLD**: keep the pointer, so the wire holds. Inspect the staged tree as `PackRoot(name = packId, configuredLevel = empty)`. |
| A-m1 | MINOR | Build the `CLOSURE` and refusal `ReasonCode`s from `RefusalCode.token()`. Classify `refuseUnresolvedDelegations`, `checkStepEnhancementTargets` and `refuseCaseOnlyMatches`. `deployable` builds a native image. | **FOLD**: the first two stay in the offering's sequence. Case-only matches go to #556, because they are reference checks. The native build joins NFR-001. |
| C-12 (token) | MINOR | The two `prompt_slot_not_empty` codes. | **FOLD**: the mappers switch on the constant, never on `value()`. A token-grammar test is added. |

## Operator rulings needed

- **R1. Which checks each surface runs** (A-B1, C-1, C-2, C-3, M-3).
  - (a) One `PACK`, with a surface profile. Each surface reports the faults it reports today: registration and the
    compose read gain no new refusals. Validate mode gains the load-only checks. Convergence of the rest is a later,
    explicit decision. The wire is unchanged apart from Q1. *Recommended.*
  - (b) Full convergence. Every surface runs every rule, about 15 features are rewritten, a `### Breaking` fragment is
    added, and open-packs org packs without a fragment are refused.
- **R2. How codes and wording differ by surface** (A-B2, C-4). It follows from R1.
  - (a) `PackRefusals` and `PackVerdicts` are explicit per-surface tables of code, pointer and wording, each tested.
    FR-007 is reworded: the converters between the old types go, and one table per surface stays. *Recommended.*
  - (b) Unify the codes on both surfaces, with a `### Breaking` fragment on one of them.
- **R3. How `:packs` tests and the whole-pack gate get real rules** (M-1).
  - (a) A test-only `testImplementation(project(":pack-validation"))` in `:packs`. That is a narrow exception to C-003
    and its main direction is unchanged. *Recommended.*
  - (b) The judging tests, the gate and `planted/` move to `pack-validation` or `deployable` tests, along with
    `wholePackRoundTrip` and the CI gate job.
- **R4. One pull request, or four** (M-6).
  - (a) Four, each green:
    - PR1: the golden snapshot and Q1 on today's pipeline. This gives #578 `notHeld()` early.
    - PR2: the residue, the typed codes, the inspector facts and the mappers.
    - PR3: the `CLOSURE` functions.
    - PR4: the switch, the rulesets, the deletions, the test collapse and the floors.

    Only PR4 changes who judges, so "one pipeline" still lands atomically. *Recommended.*
  - (b) One pull request, as planned. The work packages are the same.

## Work-package breakdown (after the folds)

| WP | Content | Depends on |
|---|---|---|
| WP01 | Golden snapshot of every surface; `planted/` moved to testFixtures | none |
| WP02 | Q1 on today's pipeline: `notHeld()`, the scenarios, the fragments, the packs-contract wording, the open-packs draft | WP01 |
| WP03 | `PackPolicy`'s residue moved into `packs.domain` functions; the descriptor check as one function | none |
| WP04 | `PackCodes`; `PackElementViolation`'s kind typed; the `invalid` fallback gone; the `PackRules` port | WP02 |
| WP05 | Inspector facts (A-M1 checklist), the Spring-free assembly, the shared manifest derivation, the subset test | WP03, WP04 |
| WP06 | `PackVerdicts` and `PackRefusals` per-surface tables (R2), with the diagnostics order | WP04 |
| WP07 | Resolver checks collect all; the offering keeps its first refusal; `CLOSURE` wraps them | WP03, WP04 |
| WP08 | Per-kind rulesets and `PACK` with a surface profile (R1), unwired; rule and composition tests against the golden | WP05, WP06 |
| WP09 | The switch: validate mode, registration in `packs.application`, the compose read, the wiring, the deletions, the ArchUnit rules, the re-homed tests (R3) | WP07, WP08 |
| WP10 | Test collapse; the enumerating code test; narrowed and full PIT; floors and ADR annotations; closeout | WP09 |
