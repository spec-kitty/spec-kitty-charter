---
title: "Codebase assessment, 2026-11-20"
description: Where change concentrates in the code, how costly it is to change there, how good the tests and static analysis are, and the improvement actions ranked.
doc_status: draft
updated: 2026-11-20T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - plans/codebase-health-2026-11-20
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
  - adr/0062-nightly-mutation-floor
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the hotspots, the findings of each lens with their evidence, and the ranked actions with their issues.
  - profile: governance-lead
    guidance_text: Read this for what moved since 2026-11-12, the ranked actions and the questions that need an operator ruling.
---

# Codebase assessment, 2026-11-20

**Status: assessment, not a decision.** The operator asked for an evidence-backed evaluation of
the code in the style of *Your Code as a Crime Scene*. Version control tells us where change
concentrates; complexity tells us how costly those places are to change. This note puts the two
together, then adds the design, test and static-analysis lenses. It changes no code and files no
issue. The short version, with the scorecard and the top 10 actions, is the
[codebase health summary](/plans/codebase-health-2026-11-20/).

It follows the [code and test suite quality assessment of 2026-11-12](/plans/engineering-notes/code-and-test-quality-assessment-2026-11-12/),
and reports what moved on its top 5.

## Baseline and method

- **Pinned commit:** `25e24c372bae838d95a9328be57ee2063f6c05d1` (`main`, 2026-11-19). Every number,
  path and line refers to it. The trend baseline is `fd20faacb` (2026-11-12), the commit the last
  assessment used for its line references.
- **History:** 2,235 commits, no merge commits. 350 bookkeeping commits (`chore(`, `op(`,
  `docs(mission`, `record-analysis`) and the paths `kitty-specs/**` and `kitty-ops/**` are left out.
  No generated code is committed: the contract-generated server and client sources are written to `build/`.
- **Five lenses, run independently and then merged:**

  | Lens | Profile | Page |
  |---|---|---|
  | L1. Hotspots and churn | researcher-robbie | [Hotspots and churn](/plans/engineering-notes/codebase-assessment-2026-11-20/hotspots-and-churn/) |
  | L2. Under-designed and clunky surfaces | architect-alphonso with randy-reducer | [Design and structure](/plans/engineering-notes/codebase-assessment-2026-11-20/design-and-structure/) |
  | L3. Test quality | reviewer-renata | [Test quality](/plans/engineering-notes/codebase-assessment-2026-11-20/test-quality/) |
  | L4. Static analysis | java-jenny | [Static analysis](/plans/engineering-notes/codebase-assessment-2026-11-20/static-analysis/) |
  | L5. Synthesis | architect-alphonso | this page |

- **What was run:** `git log --numstat`, lizard 1.17.13 (corrected for two Java parsing faults, below),
  an import-graph script, a Java port of the test-quality scan, and PMD CPD 7.28.0 through the
  [ADR-0079](/adr/0079-dependencies-resolve-through-a-maven-central-mirror/) mirror. CI numbers come
  from the runs that already exist; PIT and `verifyAll` were not re-run.
- **Two lizard corrections.** lizard counts a Java wildcard `?` as a decision, and it reads a whole
  `record` body as one function. The scripts rewrite both before measuring. After the correction no
  main function exceeds a cyclomatic complexity of 15, which matches the Checkstyle gate. Raw lizard
  output would show values up to 37. L1 and L2 correct records differently, so a file's total can
  differ between their pages (`FileSystemPackInspector` is 260 in L1, 295 in L2); L1's figures
  govern the hotspot ranking.
- **Squash merges distort churn.** Four missions landed as one squash commit each; `aa1ae67b`
  alone carries 319 files and 12,150 lines. Commits of more than 30 files are 6.2% of commits but
  51.7% of file changes. Coupling therefore counts only commits of 30 files or fewer. The top 20
  hotspots barely move under that filter (19 of 20 stay). `routing` and `routing-edge` have only
  the squash commit as history, so their churn is understated and their coupling cannot be measured.
- **The "team" is the issue stream.** Almost every commit is written by an agent, so authorship
  says nothing about knowledge spread. Commits were attributed to Missions, Ops and issues by the
  tags in their subjects. 63% of the analyzed commits cite neither an issue nor a Mission.

## Short answer

**The code got better at the method level and worse at the class level, and change is
concentrated in one package.**

- **The complexity gate is on** (#371, `6451dc59`), and `PackPolicy`, the 11-12 hotspot, is gone
  (#554, `60ed8cbe`). No method is over the limit.
- **The weight stayed in large classes, and they grew** (9,462 lines in the classes over 1,000 lines,
  against 7,093 on 11-12). `packs.infra.storage` is 49.4% of
  the hotspot score, and `packs` as a whole is 80.4%. Main-code complexity in `packs` rose 24% in
  7.6 days (6,430 to 7,997). Seven classes are over 1,000 lines (six on 11-12, `PackPolicy` among them), and 13 of the top 25
  hotspots are heating.
- **The packs read path decides fault identity by matching text.** Faults are matched by their
  wording, and the reader still parses every file twice. The double parse and the lossy
  `PackElementViolation` are limits ADR-0075 records; the text matching is new here. They sit in
  hotspots 1, 2 and 6.
- **The weekly mutation run is green, with every floor met** (run 43794141037). But only one such run
  exists, `routing` and `routing-edge` have never been measured, and `:profiles` is 3 mutants above its floor.
- **Monitors fail silently.** The CPU-time budget nightly has been cancelled at its job timeout for two
  nights, and nobody was told. Its green runs already used 19.5 of 20 minutes, and two fixtures broke
  on the evening of 2026-11-19. The SonarCloud gate fails on every nightly checked without naming its
  condition.
- **The test gaps the 11-12 assessment proved are mostly still open:** 5 of 6 planted-break gaps,
  and all 8 timing items. The poll copied across `deployable` grew from 4 copies to 6, in 5 files.

No lens found a correctness, data or security defect in production code.

## Top hotspots

Hotspot score = revisions × total cyclomatic complexity of the file (L1). Revisions leave out the
bookkeeping commits. Trend compares each file's share of commits after 2026-11-12 with its share in
the 30 days before.

| # | Module | File (`ai/speckitty/charter/…`) | Revisions | Code lines | Total CCN | Score | Trend since 11-12 |
|---|---|---|---:|---:|---:|---:|---|
| 1 | packs | `packs/infra/storage/FileSystemPackInspector` | 56 | 987 | 260 | 14,560 | heating (CCN +96) |
| 2 | packs | `packs/infra/storage/FilesystemPackStore` | 52 | 946 | 212 | 11,024 | stable (+21) |
| 3 | packs | `packs/domain/PackCompositionResolver` | 40 | 941 | 197 | 7,880 | heating (+58) |
| 4 | packs | `packs/infra/storage/DomainRepositoryService` | 82 | 403 | 69 | 5,658 | stable (−64) |
| 5 | packs | `packs/infra/storage/WholePackStore` | 51 | 573 | 107 | 5,457 | heating, but shrinking (−33) |
| 6 | packs | `packs/infra/storage/PackCommitStore` | 38 | 710 | 136 | 5,168 | heating (+27) |
| 7 | packs | `packs/infra/storage/DomainRoundTrip` | 31 | 664 | 160 | 4,960 | heating (+14) |
| 8 | activations | `activations/application/EffectiveReadService` | 31 | 781 | 101 | 3,131 | heating (+56) |
| 9 | packs | `packs/infra/storage/PackFileSet` | 32 | 357 | 88 | 2,816 | cooling |
| 10 | packs | `packs/application/RegistrationJobs` | 17 | 744 | 151 | 2,567 | stable |

The full top 20, the package and module aggregates, and the change-coupling tables are on the
[hotspots page](/plans/engineering-notes/codebase-assessment-2026-11-20/hotspots-and-churn/).
The strongest couplings across rings or modules:

| Pair | Coupling | Reading |
|---|---:|---|
| `RegistrationJobs` (application) ↔ `PackRegistrationController` (infra/api) | 58% | The controller knows the jobs' lifecycle; it has 7 collaborators, 2 of them other controllers |
| `OfferingReadService` (application) ↔ `PackCompositionResolver` (domain) | 55%, 14 commits | The strongest production pair by volume |
| `MutationFloors.kt` (build-logic) ↔ `ci/mutation_summary.py` (scripts) | 50% | One floor list kept in two languages |
| `CharterRefusalCode.yaml` ↔ kernel `RefusalCode` ↔ `ReservedRefusalCodesTest` | 28–41% | Contract-first by design; a parity test guards it |
| The seven storage hotspots with each other | 19–38% | A change to storage ripples across the read path |
| Paradigm, Styleguide, Directive and Toolguide `ControllerTest` | 80–100% | One rule edited in four per-kind copies |

The edge modules are not change-coupled to their domains (missions ↔ missions-edge 5%,
profiles ↔ profiles-edge 12%, packs ↔ artifacts-edge 1%). Coupling runs through `contracts` and `kernel`.

## The 2026-11-12 top 5, eight days on

| # | 2026-11-12 weakness | Now | Verdict |
|---|---|---|---|
| 1 | Six rules tested but not guarded, proved by planted breaks | `DrgEdge` is guarded (`fcfe0854`). The other five are open, the most serious still the template-path escape check at `ExpectedArtifact.java:49`. The type-only refusal pattern behind it spreads into new code: 15 of 52 `:missions` test methods check only the exception type (L3 finding 5) | **Still open** |
| 2 | No `:packs` mutation report; the suite slower than it needs to be | Run 43794141037 (2026-11-16) is green with every group above its floor. `RegistrationJobsTest` no longer waits 30 s (`cad3f433`). `PackFileSetExactnessTest` is mitigated (8.0 s under PIT). But it is the only run of the weekly workflow, `routing` and `routing-edge` were never measured, and `ExampleVocabularyReadTest` costs 45.7 s under PIT | **Improved** |
| 3 | Old shapes and retired names beside the canonical model | kernel `Relation` is deleted. The activation store's version 1 reader, the unversioned-descriptor branch (moved to `PromptSlotRules.java:119-123`) and the `DoctrineGraph`/`DrgNode`/`DrgEdge` names remain, and none has a ruling. A new test-only port joined them (`PackMutabilityPolicy.java:153-163`) | **Still open** (1 of 4 done) |
| 4 | Timing preconditions slept on, not waited for | All 8 items open. The registration poll grew from 4 copies to 6 (in 5 files), and one copy returns silently at its deadline (`ActivationListingFaultTest:214-225`). #593 (`aa6babfc`) now gives the pattern to copy | **Regressed** |
| 5 | Complexity concentrated in `packs`, with its gate off | The gate is on and `PackPolicy` is deleted. Method complexity is fixed. But `packs` complexity grew 24%, and it now sits in seven classes over 1,000 lines that no gate measures | **Moved, not solved** |

## Findings by theme

Severity: HIGH is a risk to correctness or to a gate's ability to see a regression; MEDIUM is a
cost on every change to a hotspot; LOW is local. Each finding cites its lens page, which holds the evidence.

### Monitors that fail without telling anyone

- **HIGH. The CPU-time budget nightly is dead** (L4 F1).
  - Runs 44457638021 and 44673807037 (2026-11-19 and 2026-11-20) were cancelled at the 20-minute
    job timeout (`timing-nightly.yml:33`). The last success was 44236574406 (2026-11-18). The green
    runs of 11-17 and 11-18 took 19 min 33 s and 19 min 45 s, so the timeout has no headroom. The
    11-19 run, at `8ba750f8`, predates both fixture breaks below; they add a second fault from 11-20.
  - `ResolverCostBoundTest.java:140` builds a 46-character pack id. `PackIdentifier` refuses it since
    #583 (`8a44dfc3`).
  - `ReachabilityBudgetTest.java:100` breaks the scope-edge rule that #602 added (`Relationship.java:206`).
  - `PackFileSetBudgetTest` took 10.68 s against a 10 s budget.
  - A cancelled scheduled run skips the report step and notifies no one, and `verifyAll` excludes the
    `timing` tag, so every gate stays green.
- **MEDIUM. The SonarCloud gate fails on every nightly checked, and nobody can see why** (L4 F2, F3). Nightly
  44674623934 logs "Quality Gate has FAILED" and never names the condition. `service/pack-check`
  is missing from the Sonar sources, and the packs test fixtures are missing from its test settings.
  #463 needs only the five false positives marked; #494 is 4 of 8 done.
- **MEDIUM. Mutation coverage has a blind spot** (L3 findings 3 and 4). `:routing` (floor 90) and
  `:routing-edge` (floor 85) arrived in the `aa1ae67b` squash with floors but no measurement, and
  ADR-0062 has no amendment for them. `:profiles` is 3 mutants above its floor. Surviving mutants
  are mostly boundary conditions, the same kind of gap as the planted breaks 3 and 5 of 11-12.

### The packs read and write path

- **MEDIUM. Fault identity is decided by matching text** (L2 F1).
  - `AssemblyRules.java:171-293` matches faults with `endsWith`, fixed phrases and a split at `": "`
    to recover a companion path.
  - `FilesystemPackStore.java:1522-1537` strips a text prefix and rebuilds a `PackElementViolation`
    with an empty pointer, which loses the pointer and the severity.
  - `PackCommitStore.requireNoNewViolation` (`:429-450`) treats a new fault as held when the live
    pack has one with the same code in the same file. So a write that swaps one fault for another of
    the same code in that file is accepted. This is by design (the pack is no more broken), but the
    javadoc at `:419-421` claims more than the code checks.
  - Sits in hotspots 1, 2 and 6.
- **MEDIUM. The "one reader" still reads several times** (L2 F2).
  - Every file is parsed twice (`FileSystemPackInspector.java:358-361`, `:915`, `:1162`).
  - `pack.yaml` is parsed twice (`:344` and `FilesystemPackStore.java:864`).
  - Companions are resolved twice (`CensusEntries.java:100`, `PackFileSet.java:204-208`).
  - `WholePackStore.salvageStored` (`:410`) is a third, lenient reader.
  - A per-file write fully re-reads the staged copy, and also the live pack when the copy has a
    violation (`PackCommitStore.java:430-435`).
  - ADR-0075 defers these limits to its single-parse unification, which has no issue yet. #556,
    slice 7, moves the closure checks behind ports.
- **MEDIUM. Global mutable state and test hooks in main code** (L2 F3).
  - There are six static read counters, against two on 11-12: `FilesystemPackStore` ×2,
    `PackFileSet`, `StoreRead` and two in `InForceResolution`, a `packs.domain` class its javadoc calls pure.
  - The test seam `JobGate` is wired as a production bean (`RegistrationWiring.java:83`).
  - Some constructors exist only for tests: 7 in `PackCompositionFacts` and 3 in `ClosureView`.
- **MEDIUM. Seven classes are over 1,000 lines and no gate measures class size** (L2 F8, L1). On
  11-12 there were six, with 7,093 lines between them; now there are 9,462.
  - The seven: `FilesystemPackStore` 1,627 lines, `PackCompositionResolver` 1,480,
    `FileSystemPackInspector` 1,438, `EffectiveReadService` 1,435, `RegistrationJobs` 1,212,
    `ClosureView` 1,171 and `PackCommitStore` 1,099.
  - Checkstyle has no `FileLength`, `ParameterNumber` or class fan-out module.
  - `PackDescriptorDso.java:81` suppresses a `ParameterNumber` check that does not exist.

### Duplicated concepts

- **MEDIUM. A per-kind read path duplicates the kind-keyed one** (L2 F4).
  - Six near-identical `*ReadService` classes; `ParadigmReadService.java:17` calls itself "a
    faithful copy of DirectiveReadService".
  - Six per-kind repository ports, each with one caller.
  - `ArtifactReadService` takes 12 constructor parameters, and `DomainRepositoryService` implements 14 ports.
  - L1 sees the cost: the four per-kind controller tests change together 80–100% of the time.
- **MEDIUM. Effective reads pass through four mirror layers** (L2 F5): `ClosureView`, `PacksSession`,
  `ClosureViewSession` and `EffectiveReadService`. Each edge module uses one to three of their
  methods, and `stepModelTier` appears in six files. `EffectiveReadService` is the fastest-heating
  hotspot outside `packs`: its CCN went from 45 to 101.
- **LOW. Knowledge kept in several places** (L2 F10, L4).
  - Code-point ordering now has 9 copies, against 5 on 11-12.
  - The not-found refusal codes are listed twice and the lists differ: `RefusalCode.java:297-314`
    has 17 and `RefusalHandler.java:371-389` has 18.
  - `LimitedStream` is still copied.
  - #517 added a 29-line copy between `PackRemovalController` and `PackUninstallController`.
  - Overall duplication is low: CPD finds 0.17% at 100 tokens.

### The model and the architecture

- **MEDIUM. Legacy readers beside the canonical model, still without a ruling** (L2 F6). These are
  the 11-12 operator questions 1 to 3, plus the test-only `MutabilityPolicyRepository` port.
- **MEDIUM. Gaps in the hexagonal gate** (L2 F7).
  - No ArchUnit rule forbids package cycles, and two exist: `packs.domain ⇄ packs.domain.rules`
    and `packs.infra.storage ⇄ storage.markdown`.
  - `hexagonal-service.md` describes modules and sub-packages that do not exist, and names a rule
    differently from the code.
  - missions-edge and profiles-edge declare a `web-transport` dependency they do not use.
  - Every module has ArchUnit coverage, no edge module depends on another, and there is no module cycle.
- **LOW. Readability** (L2 F11 to F13).
  - The retired "Doctrine" names are still in type names: `DoctrineGraph`, `DrgNode` and `DrgEdge`
    appear on 24, 31 and 9 lines.
  - Some public methods are called only from tests.
  - The comment-to-code ratio is 0.61, with 1,405 issue or task citations in javadoc. In a sample of
    22 javadoc comments, about 9 restate the code, and three javadoc comments make claims the code does not keep.

### Tests

- **MEDIUM. Five rules are still unguarded** (L3 finding 1). This includes the template-path
  containment rule (`ExpectedArtifact.java:49`), which the schema refuses on the YAML route but no
  domain test reaches.
- **MEDIUM. Sleep-timed tests** (L3 finding 2). `RegistryLockOrderTest:110` sleeps 300 ms beside
  its own `awaitQueued` helper (`:143`). #593's `MixedReadConcurrencyTest` shows the shape to copy:
  - a bounded wait under the lock's own limit;
  - a named failure;
  - `@Timeout` only to catch a hang;
  - a control case that proves the bound fails when it should.
- **LOW.** These fit in small PRs (L3 findings 6 and 7):
  - the wiring-existence tests;
  - the `KindBindingsTest` `hasSize(15)` friction;
  - the root-user skips that have no CI switch;
  - the slowest test under PIT, which #531 does not list.
- **Strengths:**
  - 1,001 contract scenarios, against 653 on 11-12;
  - the ADR-0059 D1 switch gate is enforced by an `@AfterAll` hook with controls;
  - routing catalogs and tensions have example-pack coverage (ADR-0070);
  - test-to-main ratio of 1.46 by lines;
  - sentence-style names and AssertJ throughout.

### Build hygiene

- **MEDIUM. Dependencies are not updated by any bot** (L4 F4).
  - No Dependabot or Renovate.
  - No dependency locking.
  - Verification checks checksums but not signatures.
  - The Gradle wrapper has no `distributionSha256Sum`.
  - Spring Boot is at 4.1.1 while 4.1.3 is out, and the build tools are each a release or more behind.
- **LOW. Suppressions are few, and some lack a reason** (L4).
  - Main code has 7, the same as on 11-12; one of them suppresses a check that does not exist.
  - Test code has 48 `@SuppressWarnings`, and 24 of the `unchecked` ones have no reason.
- **LOW. `verifyAll` takes 13 min 18 s against a 20-minute CI budget** (L4 F10). #596 tracks the build-image remedy.

## Ranked actions

Ranked by risk × cost to change × hotspot heat. Size: S is up to a day, M a few days, L a Mission.
"New" means no open issue covers it; it is a proposal, not filed.

| # | Action | Findings | Issue | Size | Risk if left |
|---|---|---|---|---|---|
| 1 | Revive the CPU-time budget nightly: fix the two fixtures, give the timing step its own timeout below the job's, report when the step does not succeed, then decide on `PackFileSetBudgetTest`'s 10 s budget | L4 F1 | new (adjacent: #494 covers the mutation and Sonar nightly runs) | S | HIGH: a cost regression lands unseen |
| 2 | Make the SonarCloud gate legible and green: export the failing condition (list in L4 §6), mark the five false positives, add `pack-check` and the packs test fixtures to the Sonar scope, print the gate's conditions to the job summary | L4 F2, F3 | #463, #494 | S | MEDIUM: a real regression hides behind a known-red gate |
| 3 | Guard the five open planted-break rules, template path first, each with its planted break as the red-first proof. Stop the type-only refusal pattern in `:missions` | L3 findings 1 and 5 | new (11-12 R1 was never filed) | S | MEDIUM: a refactor can delete a containment rule with every gate green |
| 4 | Measure what the floors claim: dispatch the mutation workflow now, so `:routing` and `:routing-edge` get a first score; amend ADR-0062 for them; strengthen `:profiles`; close #461 on run 43794141037 | L3 findings 3, 4, 6 and 10 | #461, #530, #531 | S | MEDIUM: two floors guard nothing |
| 5 | Give faults a typed identity: one `Reason` with code and location from read to refusal; delete `PackElementViolation` and the text matching in `AssemblyRules` and `FilesystemPackStore.reasonOf`; correct the `PackCommitStore` javadoc | L2 F1 | new (ADR-0075 AA2; #614 for `CompositionFault`) | M | MEDIUM: hotspots 1, 2 and 6 break on a wording edit |
| 6 | Finish the one reader: parse each file and `pack.yaml` once, resolve companions once, retire the lenient salvage read, and read once per write | L2 F2 | new (ADR-0075 single-parse unification; related: #416, a cache across requests) | L | MEDIUM: the hottest file stays the costliest to change |
| 7 | Replace the sleeps with state waits on the #593 pattern; one shared registration await helper for the six copies; a CI switch that fails the root-user skips | L3 findings 2 and 9 | new | S–M | MEDIUM: vacuous passes on a loaded runner |
| 8 | Take the test hooks out of main code: the six static counters behind a recorder port, `JobGate` out of production wiring, the test-only constructors and public methods removed | L2 F3, F12 | new (ADR-0075 AA5) | S | MEDIUM: global state across parallel tests |
| 9 | Collapse the per-kind read path onto the kind-keyed one: one read service and one repository port, then split `PackRegistrationController`'s collaborators | L2 F4, L1 coupling | new | M | MEDIUM: shotgun surgery across four per-kind copies |
| 10 | Bound class size and reconnect the docs: add `FileLength` and `ParameterNumber` to Checkstyle at today's maximum, add an ArchUnit no-cycle rule, refresh `hexagonal-service.md`, then split `FilesystemPackStore`, `PackCompositionResolver` and the effective-read layers along the seams L2 lists | L2 F5, F7, F8, F9 | new (#205 covers the PlantUML diagrams only) | S for the gates, L for the splits | MEDIUM: the class-level debt keeps growing unmeasured |

Further actions, below the top 10:

| Action | Findings | Issue | Size |
|---|---|---|---|
| Put the legacy readers, the unversioned branch and the retired graph type names to the operator (questions below), then one Mission per ruling | L2 F6, F11 | new; #397 retires the artifact-field graph walk | ruling first |
| Dependency updates by a bot, dependency locking, a wrapper checksum, and the signature decision | L4 F4 | #51 for the wrapper checksum and signatures; new for the bot and locking | S |
| One kernel `CodePointOrder`; `RefusalHandler` reads `RefusalCode.notFound()`; one `LimitedStream`; one removal and uninstall helper | L2 F10 | #49 for the hand-kept list; new for the rest | S each |
| Derive the floor list once for `MutationFloors.kt` and `mutation_summary.py` | L1 coupling | #49 | S |
| Fill in the reasons of the 24 bare `unchecked` suppressions in tests and drop the dead `ParameterNumber` suppression | L4 | new | S |
| Cut `verifyAll` time: `validateContract` costs about 2 min each run | L4 F10 | new (#596 is the build image; #50 the build structure) | M |
| Javadoc: keep intent, move provenance to ADRs and commits, fix the three false claims | L2 F13 | new | S, as touched |

The issues #582, #52 and #433 were checked against the findings. No finding falls in their scope,
so no action is tied to them.

## Questions for the operator

1. **The open model rulings of 2026-11-12** (questions 1 to 3 there) are still open: the activation
   store's version 1 reader, the unversioned pack descriptor and the "Doctrine" type names. Rule on
   each, or record that they wait for the RC.
2. **SonarCloud.** Export the gate status and the issue facets listed in L4 §6, for project
   `spec-kitty_spec-kitty-charter`. Nothing here guesses those numbers. Then decide whether the
   nightly should fail loudly on a gate it cannot name.
3. **Class-size gate.** Should Checkstyle bound file length and parameter count at today's maximum
   (ratchet), as it did for method complexity in ADR-0058? Or should it wait until the splits of action 10?
4. **Mutation cadence.** The workflow runs weekly, on Monday, so a module that lands on a Tuesday waits almost
   a week for its first score. Should a new module's first mutation run be part of its landing?

## Reproduce

Run from the repository root with full history (`git fetch --unshallow` on a shallow clone).

```bash
git checkout 25e24c372bae838d95a9328be57ee2063f6c05d1
# L1: churn, complexity at both commits, hotspots, coupling, trend -> build/analysis/hotspots/tables.md
bash scripts/analysis/hotspots/run-all.sh
```

- **L1:** `scripts/analysis/hotspots/` (`churn.py`, `complexity.py`, `analyze.py`, `run-all.sh`).
  Two runs gave identical tables.
- **L2:** `scripts/analysis/design/`. It covers:
  - the record rewrite that corrects lizard (`derecord.py`);
  - the method, class-size and parameter tables;
  - the import graph and its cycles;
  - the public methods called only from tests;
  - the javadoc sample.

  The commands are on the [design page](/plans/engineering-notes/codebase-assessment-2026-11-20/design-and-structure/).
- **L3:** `scripts/analysis/tests/jscan.py`, an approximate Java port of the test-quality scan. Its
  method count compares with 11-12; its flag counts do not, because its type-only rule is looser.
- **L4:** the GitHub Actions runs named on the [static analysis page](/plans/engineering-notes/codebase-assessment-2026-11-20/static-analysis/),
  the `git grep` for suppressions, and PMD CPD 7.28.0 resolved through the ADR-0079 mirror.

Raw outputs (logs, CSV files, extracted sources) are not committed; the commands regenerate them.
The CI artifacts (the JaCoCo aggregate and the PIT reports) could not be downloaded from the
assessment session, so the aggregate coverage percentage is not reported.
