---
title: Building the service
description: How to build and test the charter service, what each verification task checks, how to change the contract or a dependency, and how a release is cut.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Guide
kind: How-To
related:
  - architecture/hexagonal-service
  - architecture/contract-and-dtos
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0046-service-release-line-and-native-packaging
  - adr/0060-native-build-runs-nightly-and-per-release
  - guides/generate-a-client
  - adr/0062-nightly-mutation-floor
  - guides/compose-packs
  - guides/run-in-a-container
  - conformance/divergence-register
  - changelog/2026-11-07-a-pack-registration
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you build, test or change the contract.
---

# Building the service

How to build the charter service and run every check CI runs.

## Prerequisites

- **Java 25.** Gradle never downloads a toolchain (`org.gradle.java.installations.auto-download=false`).
  A machine without Java 25 can pass `-Pcharter.javaVersion=21` for a local build; CI never
  does.
- **No open-packs checkout for the build.** `verifyAll` and every required check read the charter-owned example packs
  in `service/packs/src/testFixtures/resources/example-packs` ([ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/)).
  A `spec-kitty-open-packs` checkout (by default beside this repository, `../spec-kitty-open-packs`, or anywhere with
  `-PopenPacksDir=<path>`, absolute or relative to the repository root) is needed only for two things:
  `./gradlew syncPacks`, for this repository's own Spec Kitty governance, and the nightly
  [open-packs drift watch](#the-open-packs-drift-watch) (`.github/workflows/open-packs-drift.yml`).
- **The synced packs**, once per worktree, for governance only: `./gradlew syncPacks`. `packs/internal` and `packs/qa`
  are not committed; they are copied from the open-packs checkout at the `openPacksRef` pin in `gradle.properties` and
  checked against `packs/open-packs.lock.yaml`. They are needed for this repository's own Spec Kitty governance
  (`.kittify/config.yaml` and the `qa-*` agent profiles), not for `verifyAll`. See [Syncing the packs](#syncing-the-packs).
- **The full Git history and tags.** The breaking-change check compares the contract with its
  last released version (see [the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline)) and refuses a shallow clone. In a clone
  you cannot deepen, pass `-PcontractBaseline=none` to skip that one check on purpose; CI
  never does. `verifyAll` in any shallow clone needs `-PcontractBaseline=none`, because the build
  refuses a shallow clone before it looks at tags. In a full clone, the check needs the
  `contract-charter-v*` tag of its baseline, merged into HEAD: the newest release the CHANGELOG
  records with the contract's major version. A release whose tag is not yet merged (as on a
  release PR) is skipped, and a new major version has no baseline, so it needs no tag. Fetch the
  tags if one is missing.
- **A system `git` of 2.50.1 or later, only for the `git-host` tests.** `verifyAll` does not need it: the git source's
  argument list, environment, version check and deadline run against a fake git. The tests that run the real git
  are tagged `git-host` and run with `./gradlew :packs:gitHostTest -PrequireGitHost=true`. With that property they
  fail when git is missing or older, so they cannot pass by skipping. CI runs them in the `git-source` job (below).
- **Network access on the first run.** The contract checks download vacuum and oasdiff at
  pinned versions and verify each archive's SHA-256 before using it (Linux and macOS, x86-64
  and arm64).

## Run everything

```bash
./gradlew verifyAll
```

`verifyAll` runs the `check` task of every project:

| Task | Checks |
|---|---|
| `:contracts:check` | The charter's own contract checks: `bundleContract`, `lintContract`, `lintFixtures`, `checkClosedResponseObjects`, `compatibilityFixtures` and `checkContractCompatibility` ([`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md) says what each one does) |
| `:charter-api:validateContract` | The split OpenAPI document parses and validates |
| `:charter-api:generateContract` | Spring interfaces and DTOs are generated; controllers that drift from the contract fail to compile |
| `:client-java:check` | The Java client is generated from the contract bundle and compiles for Java 21; `ProblemDetails` passes every gate and its tests; two generations are byte-identical ([The Java client](#the-java-client)) |
| `:<module>:spotlessCheck` | Hand-written main and test sources are formatted by palantir-java-format |
| `:<module>:checkstyleMain`, `checkstyleTest` | The Checkstyle rules in `config/checkstyle/checkstyle.xml`; any violation fails |
| `:<module>:compileJava` | `-Xlint:all -Werror`, Error Prone, and NullAway on `@NullMarked` code (test sources get Error Prone without NullAway) |
| `:<module>:javadoc` | Javadoc of main sources with doclint on and warnings as errors |
| `:<module>:test`, `jacocoTestReport` | Unit tests of each module, and its coverage report |
| `:deployable:test` | The ArchUnit rules (dependency direction, a plain-Java core, `@NullMarked` packages) and every `@contract` scenario in `contracts/charter/scenarios/` |
| `:deployable:test` (switch gate) | After the last scenario, the switch gate ([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1, amended 2026-11-12) holds `contracts/charter/scenarios/switch-gate.yaml` to the run: it fails when an operation has no entry, when a `ready` operation has a declared status no scenario asserts on it, when a `held` one would pass, or when a success answer is exercised by no scenario (R-20). The scenarios of each answer are written to `service/deployable/build/reports/switch-gate/switch-gate.md` |
| `:packs:test`, `:deployable:test` (example packs) | The corpus tests over the charter-owned example packs ([ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/)): every artifact file reads back equal after a write, and the coverage tests (`ExamplePackCoverageTest`, `ExampleCoverageTest`) fail when the examples stop exercising a kind, field or edge the model has |
| `:packs:wholePackRoundTrip` | The whole-pack round-trip gate over the example packs (not part of `check`; CI requires it, see [below](#the-whole-pack-round-trip-gate)) |
| `:packs:driftTest` | Not required and not run by `check` (which only compiles and lints it): the nightly [open-packs drift watch](#the-open-packs-drift-watch) |
| `:charter-reports:aggregateJavadoc` | One Javadoc site over the three hand-written service modules, doclint as errors ([Java API reference](/api/javadoc/)) |
| `:charter-reports:testCodeCoverageReport` | One JaCoCo report over the same modules |

The quality gates come from the `charter.hand-written` convention plugin, applied by every
hand-written module. The generated `charter-api` does not apply it: it applies
`charter.contract-api`, which reaches `charter.java-base` through `charter.spring-conventions`,
so it is exempt by construction and still compiles. A new hand-written module under `service/` must
also be listed in `service/charter-reports/build.gradle.kts`; the build fails, naming it,
until it is.

Not in `verifyAll`: mutation testing. It runs nightly, against a floor per module, and no pull request runs it; see
[Mutation testing](#mutation-testing).

Not in `verifyAll` either: the whole-pack round-trip gate (below), which CI runs as its own required group.

Not in `verifyAll` by design: the CPU-time budgets, the tests tagged `timing`
([#316](https://github.com/spec-kitty/spec-kitty-charter/issues/316)). A budget only means something on a quiet
machine, and `verifyAll` runs every module's tests at once. Run them in an invocation of their own, as the only task
named; CI runs both modules' tasks with `--no-parallel`, nightly and on demand in a workflow of their own
(`timing-nightly.yml`), not on pull requests. A gating step compiles the tests first. Only the following budget step is **report-only**: a failure there raises a warning and a job-summary line and
never fails the job ([ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/)). An overrun opens no issue
and sends no notification; inspect the Actions page for a slowdown that persists across nights. Each budget prints the
CPU time it measured, and its task fails rather than skips where symbolic links cannot be made:

```bash
./gradlew :packs:timingBudgets --no-parallel
./gradlew :activations:timingBudgets --no-parallel
```

For example, the packs task includes `PackFileSetBudgetTest`, the 10 s CPU-time budget of the generated-tree checks
(NFR-001 of the verified source fingerprint); activations has its own timing-tagged budgets. The generated-tree checks
themselves, whether the fingerprint changes exactly when it must and the store loads exactly the planted files, stay in
`:packs:test` (`PackFileSetExactnessTest`). A test that holds work to a time or
CPU-time budget is tagged `timing`. A hang cap (`@Timeout`) and a deadline check, which proves a guard cuts work off
within a bound far above the work's own time, are not budgets and need no tag. A deadline check is written as a hang
cap of 30 s, next to an assertion of the cause it proves; the cap stops the test's wait, not work that ignores an
interrupt; the deadline tests with no cap of their own stop at a whole-test cap of 90 s ([#320](https://github.com/spec-kitty/spec-kitty-charter/issues/320)).

## Mutation testing

PIT mutates the service's logic and checks each module's mutation score against a fixed floor
([ADR-0062](/adr/0062-nightly-mutation-floor/), [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239)).
It is its own task, never part of `check` or `verifyAll`:

```bash
./gradlew pitest --continue                            # every mutated module
./gradlew :kernel:pitest                               # one module
```

`--continue` lets every module run and report even when one is below its floor.

**What is mutated.** Every module that applies `charter.mutation-conventions` (`build-logic`), which holds PIT's
versions, the HTML and XML reports and PIT's default mutators:

| Module | Floor group | Mutated | Floor |
|---|---|---|--:|
| kernel | `kernel` | the whole module | 75 % |
| missions | `missions` | the whole module | 70 % |
| profiles | `profiles` | the whole module | 90 % |
| activations | `activations` | the whole module | 85 % |
| profiles-edge | `profiles-edge` | `profiles.application` and `profiles.infra`, without its four `*.config` packages (Spring wiring) | 85 % |
| missions-edge | `missions-edge` | `missions.application` and `missions.infra`, without its four `*.config` packages (Spring wiring) | 85 % |
| artifacts-edge | `artifacts-edge` | `artifacts.application` and `artifacts.infra`, without its four `*.config` packages (Spring wiring) | 85 % |
| pack-validation | `pack-validation` | the whole module (`packvalidation`) | 85 % |
| packs | `domain+application` | `packs.domain`, `packs.application`, without `application.config` | 85 % |
| packs | `infra.storage` | `packs.infra.storage`, `infra.storage.config` included | 85 % |
| packs | `infra.fetch` | `packs.infra.fetch`, without `infra.fetch.config` | 70 % |

Kernel, missions and profiles apply the plugin through `charter.domain-conventions`; packs, activations and
the three edge modules apply it directly (an edge cannot take the domain convention: its rings carry external
dependencies).
PIT runs a module's unit tests as `test` does. The convention passes the `test` task's system properties and the JUnit
tags it leaves out to PIT's minion JVMs, so no module copies them by hand (#461). A module may leave out more tags for
PIT alone, such as `stress` in packs.
Packs never mutates the Spring wiring (`application.config`, `infra.fetch.config`) or `infra.api` (the REST adapter), and
PIT runs only the unit tests of the mutated packages: no `*WiredTest`, and no test tagged `whole-pack`, `git-host` or
`timing`. A whole `:packs:pitest` took 1h06m34s on an idle 4-core machine (2026-11-12, 7129 mutants), so the mutation
run splits the packs groups across two shards (below). In the first sharded run (43209733136, 2026-11-12) the packs
shards took 41 and 47 minutes, and every other module together took 13.

**The floors** live in each module's `build.gradle.kts`, in a `mutationFloors { group("...") { ... } }` block. A
floor is raised by a pull request once the nightly score clears it by 5 points, and lowered only by an amendment to
ADR-0062.

**The floor check.** `pitest` is finalized by `checkMutationFloors`, which reads `build/reports/pitest/mutations.xml`
leniently (PIT writes raw `<` into it), logs each group's mutants, score, test strength and floor, and writes
`build/reports/mutation-floors/floors.json`. The score is PIT's mutation score: the detected mutants (KILLED,
TIMED_OUT, NON_VIABLE, MEMORY_ERROR, RUN_ERROR, EQUIVALENT) over all mutants, uncovered ones included. Test strength,
detected over covered mutants, is reported and never gated. A group passes when its exact score is at or above its
floor; printed figures are truncated to one decimal, never rounded up. A group below its floor fails the task:

```text
:kernel kernel: mutation score 74.9 % is below its floor 75 % (#239, ADR-0062)
```

A group with no mutants, a missing `mutations.xml` and `<mutation>` elements without a status or a mutated class each
fail the check too.

**Narrowing a run.** `service/packs/pit-targets.init.gradle` narrows PIT to the classes and tests named with
`-Dpit.classes` and `-Dpit.tests`, for one work package's classes; its header has the full command. A narrowed run is
not a floor measurement: it skips `checkMutationFloors`, and the `floors.json` left from an earlier run may be stale.

**The mutation run.** `.github/workflows/mutation-testing.yml` runs on its schedule and on `workflow_dispatch` only;
the schedule is the operator's to set. PIT runs in three shards, each on its own runner with a 120-minute timeout, 110 of them for the
mutation step (#461): `:packs`'s floor groups in two halves (`-PmutationGroups=domain+application,infra.fetch` and
`-PmutationGroups=infra.storage,infra.archive`) and every other module. A shard mutates and scores only its groups, so
each group's score is the one a whole-module run gives, and writes `floors-<shard>.json`. Each shard syncs the packs at
the pin, then runs `./gradlew mutationModules <its tasks> --continue`. `mutationModules` writes the modules that apply
`charter.mutation-conventions`, with their floor groups (#388). A summary job then runs, whatever the shards' results:
it runs the summary script's unit tests, merges the shards' reports, and
`scripts/ci/mutation_summary.py --modules` turns every module's floors files into a job-summary table (score,
strength, uncovered and timed-out mutants, floor, margin to the floor, verdict and the classes with the most surviving
or uncovered mutants), shows "no report" for a listed module or group that wrote none, and quotes PIT's lines about
any test that fails with no mutation applied (#461). The script exits 1 when a listed module or group has no
readable report, so the summary job is red and the alert fires. The module list, each shard's output (`pitest-<shard>.log`) and
every module's report go up as the `mutation-report-<shard>` artifacts, kept for 14 days. When a scheduled run does
not succeed, a timeout or a red summary included, the workflow opens one issue labelled `mutation-testing`, or
comments on the open one; a `workflow_dispatch` with `force_issue` tests that path.

**Never in the required check.** PIT runs only in the nightly (ADR-0060). The root build fails any invocation in which
`verifyAll` or a `check` depends on `pitest` or `checkMutationFloors`, and build-logic's `MutationWorkflowGuardTest`
pins the nightly's triggers and that no other workflow runs PIT (#387).

**A surviving mutant** is triaged in this order: kill it by strengthening an existing test's assertion; mark it
equivalent, with a one-line reason; or accept it, naming an owner. Never add a test whose only purpose is killing a
mutant (DIRECTIVE_041).

## Syncing the packs

This repository does not commit copies of open-packs content, so they cannot drift. The packs that the repository's own
Spec Kitty governance uses (`.kittify/config.yaml` lists `packs/internal` and `packs/qa`) are synced:

```bash
git clone https://github.com/spec-kitty/spec-kitty-open-packs.git ../spec-kitty-open-packs   # once
./gradlew syncPacks                           # once per worktree; sync, then verify
./gradlew syncPacks -PcheckPacks=true         # verify the synced trees only
./gradlew syncPacks -PopenPacksDir=<path>     # another checkout, absolute or relative to the repository root
```

`syncPacks` runs `scripts/packs/sync_packs.py`, which also runs as a bare `python3 scripts/packs/sync_packs.py`. It reads
`packs/internal` and `packs/qa` at `openPacksRef` with `git archive`, never from the checkout's working tree, so a dirty
checkout cannot leak in. It then compares each tree's digest with `packs/open-packs.lock.yaml`, whose header defines the
algorithm. A mismatch fails and leaves the synced trees as they were. The tests read the charter-owned example packs and
need neither the checkout nor a synced copy; CI runs the sync to guard the lockfile.

Bumping the pin is one commit:

1. Change `openPacksRef` in `gradle.properties`.
2. Run `./gradlew syncPacks -PupdatePacksLock=true`, which syncs and rewrites the lockfile's commit and digests.
3. Run `./gradlew verifyAll`. Never weaken an expectation to make a bump green.

`verifyAll` itself does not run the sync, so step 2's run is the local lock check; CI repeats it before its own
`verifyAll`. Use `-PcheckPacks=true` when you only want to re-verify the synced trees against the lockfile.

Neither `packs/internal` nor `packs/qa` is edited here; a change goes to spec-kitty-open-packs and arrives with a pin bump.

## The open-packs drift watch

```bash
./gradlew :packs:driftTest -PopenPacksDir=<path>   # any spec-kitty-open-packs checkout, usually main
```

`driftTest` is its own source set ([ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/)):
`check` compiles and lints it but never runs it, and no required check runs it. It puts every pack of the checkout
through the whole-pack round trip below, with no expected-red baseline, and also reports the files the store does not
model and the load warnings. Every file of a pack that is not excluded needs a round-trip outcome: a file of a kind
the round trip does not model fails it, naming the kind and the path (#547). spec-kitty-open-packs runs the same task
as a required gate from its pinned charter checkout (ADR-0070 decision 4). The nightly `.github/workflows/open-packs-drift.yml` runs it against open-packs `main` and
opens or comments on an `open-packs-drift` issue when it is red.

## The whole-pack round-trip gate

```bash
./gradlew :packs:wholePackRoundTrip          # reads the example packs; no open-packs checkout
```

The gate ([#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187), epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)) reads every file of every example pack
(`built-in`, `org-base` and `project`, one run each of a single parameterized test, #547) into the domain, writes it to a fresh directory through the storage service, reads it back, and compares each
written file with its source **by meaning**, as [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
defines it: quoting, comments, layout and key order do not count, and for a declared field an absent value, `null`
and an empty list or map are the same (inside an open map such as a styleguide's `tooling` they are data); a missing,
extra or changed key or value counts. Numbers compare by value. Graph-shard edges compare as a set (order and
identical repeats do not count) and nodes by URN.

It lands red first. `service/packs/src/test/resources/whole-pack/expected-red.yaml` lists what does not round-trip
yet: a single file of a modelled kind with every place it differs (`files:`, its path starting with the example
pack's name, `at:` listing each difference path, joined by ` ; `, or holding the whole detail of a refused file), with
a reason and the issue that owns it. It also lists each `file-kinds.yaml` schema that has no reader and writer
(`kinds:`), with the reason; that entry excuses no file, so a file of such a kind is red wherever it appears.
Because `at:` holds every difference, a new loss in a file that is already expected red still fails the gate. `exclusions.yaml` next to it is the gate's
own append-only list of files it does not compare. The gate passes only when the result matches its lists exactly (the third,
`known-failures.yaml`, is below):

- an unlisted red file fails it (`UNEXPECTED_RED`), and so does a listed file whose differences changed
  (`CHANGED_RED`);
- a listed file that now round-trips fails it too (`UNEXPECTEDLY_GREEN`): remove the entry in the same change that
  turns it green;
- a file entry for a missing or excluded file (`STALE_BASELINE`) or for a file of an unmodelled kind
  (`FILE_ENTRY_UNMODELLED`), an exclusion that matches nothing or a modelled file, a `kinds:` entry for a modelled
  kind (`BASELINED_MODELLED_KIND`) and an empty pack fail it, so it cannot pass vacuously.

Three checks look at the example packs together, and at the file-kind table (#547):

- every `file-kinds.yaml` schema is modelled by the round trip or listed in `kinds:` with a reason, and every
  `kinds:` entry is a schema the round trip does not model (`UNMODELLED_SCHEMA`, `STALE_BASELINE kind`);
- every modelled kind has a file in some example pack (`EMPTY_MODELLED_KIND`);
- every file entry, known failure and exclusion starts with the name of an example pack, so some pack's run
  judges it.

### Known failures: the pack is wrong, not the service

A file of a modelled kind can fail the round trip because the pack's own data is inconsistent. That is neither a
service defect (expected red) nor a file the gate does not compare (an exclusion). Such a file goes in
`service/packs/src/test/resources/whole-pack/known-failures.yaml`, the third list next to `expected-red.yaml` and
`exclusions.yaml`. It ships empty. One entry per file, with four fields:

- `path`: the file, its example pack's name first (`built-in/…`);
- `at`: where it differs, exactly as the report's detail shows it before the colon; for a refused file, the whole
  `refused: …` detail;
- `reason`: one line;
- `issue`: the open-packs issue that owns the fix, as `spec-kitty/spec-kitty-open-packs#<number>`.

To list a file:

1. File the issue in spec-kitty-open-packs, as
   [ADR-0036 decision 5](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) describes.
2. Add the entry to `known-failures.yaml`.
3. When the fix merges, move `openPacksRef` to the commit with the fix and remove the entry in the same change. Until
   you do, the gate fails with `UNEXPECTEDLY_GREEN`: the list expires by itself.

The gate fails on:

- a malformed entry, or an issue that is not in the form above (the list does not load);
- `CHANGED_RED`, when the differences of a listed file no longer match `at`;
- `UNEXPECTEDLY_GREEN`, when a listed file round-trips;
- `STALE_KNOWN_FAILURE`, when the file does not exist in the pack or is excluded;
- `KNOWN_FAILURE_UNMODELLED`, when no adapter models the file's kind, so the entry could never expire;
- `AMBIGUOUS_ENTRY`, when the file is also in `expected-red.yaml`.

The report counts known failures on their own: a `known failure` column per kind, and a `known failure` count in the
totals line, both after `red`.

Where the gate still differs from ADR-0036, on purpose and only towards false reds, never false greens:

- written artifacts are paired with their source by kind and id (the binding's id key: `profile-id` for an agent
  profile), not path for path, because the store writes a new
  artefact at the pack root; any written file nothing pairs with fails the gate;
- an absent field is not yet treated as its schema default;
- the open maps are listed per kind in `MeaningComparator` (styleguide `tooling` and `structural_lint_config`, agent-profile
  `excluding`), not derived from the schemas; a kind that brings an open map adds it there.

The report, per kind and per file, is written per example pack to
`service/packs/build/reports/whole-pack/<pack>.md`. CI runs the gate
in its own workflow, `.github/workflows/whole-pack.yml`, and requires it: `CI gate / gate` calls the workflow for its
`wholepack` group whenever a pull request changes `contracts/`, `service/`, the build or the workflow, so every such
pull request has to keep the packs round-tripping (epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186),
operator ruling of 2026-11-05).

### When a shape change refuses the pinned packs

A known-failure entry is for a few files that a pack gets wrong. When a deliberate shape change
refuses a whole class of pinned files, as the sanitized procedure and toolguide shapes did (#194),
listing them does not work: the gate can only pin files of a modelled kind, and a refused toolguide
leaves its guide unowned. Migrate instead
([ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) decision 6):

1. Change the schema and the binding here first.
2. Convert the pinned files with the service's own writer, through a throwaway test-scope harness
   that checks the result field by field, never by hand.
3. Land the output in spec-kitty-open-packs, generated on its `main`, and move `openPacksRef` to it
   in the same change that removes the harness.

The gate is red for that class between the first step and the last; keep that window to one pull
request.

The harness of #194's first pull request is historical: it was deleted before that pull request, and the task below does not
exist on `main`, so the command does not run there. It ran as:

```bash
# historical: the task existed only on the mission's lane branch
./gradlew :packs:packMigration -PmigrationTarget=<an absent or empty directory>
```

Its code was not kept (it was throwaway by design). Its output, the verifier report and the list of
manual decisions are in
[spec-kitty-open-packs#91](https://github.com/spec-kitty/spec-kitty-open-packs/pull/91). A later
migration writes a harness of its own on that model, as #194's second pull request did for the
mission tier (ADR-0044 decision 8): its output landed as
[spec-kitty-open-packs#95](https://github.com/spec-kitty/spec-kitty-open-packs/pull/95), and its
independent verifier, self-test, gate evidence and re-pin script are kept in
`kitty-specs/mission-type-consolidation-01M65MCK/research/migration/`.

To fix formatting rather than report it, run `./gradlew spotlessApply`.

## Fetching toolguide guides

A toolguide registration may name its guide with `guideUri` instead of sending it as `guide`. The
service then fetches it once per registration, before it takes the pack's write lock. The fetcher is
configured under `charter.guide-fetch` (`GuideFetchProperties`):

| Property | Default | What it does |
|---|---|---|
| `charter.guide-fetch.allowed-hosts` | empty | The hosts a guide may be fetched from, matched exactly after lower-casing and stripping a trailing dot. Empty refuses every `guideUri` (`HOST_NOT_ALLOWED`). |
| `charter.guide-fetch.timeout` | `10s` | One deadline over resolution, every redirect and the body. |
| `charter.guide-fetch.max-size` | 1 MiB (`1MB`, which Spring reads as 1,048,576 bytes) | The largest guide accepted, fetched or inline: counted while a fetched body streams, and the cap of an inline `guide`. A toolguide registration body is capped at six times this plus 1 MiB; a directive, paradigm, styleguide or relationship registration body at a fixed 1 MiB. |
| `charter.guide-fetch.max-redirects` | `3` | The most redirects followed; each target is checked again. |

The fetch is direct: it never uses a proxy, whatever `https.proxyHost` says. A service that reaches
the guide host only through a proxy cannot fetch, and the client sends the guide as content. Only
HTTPS, no user information and no IP literals are accepted, and every resolved address must be
global unicast. An allowlist entry must be a plain host name: a URI, a `host:port`, a wildcard or
an IP literal fails the start; a Unicode host name is listed in its ASCII (`xn--`) form. Only
port 443 is fetched, and the response must be `text/markdown`, `text/x-markdown` or `text/plain`
(an absent `Content-Type` is refused). A JVM-wide resolver guard, `GlobalAddressResolverProvider`, enforces that again when
the client connects. The JDK loads it through
`META-INF/services/java.net.spi.InetAddressResolverProvider` on the JVM's first name lookup; when
`allowed-hosts` names a host and the guard is not the JVM's resolver, the service refuses to start.
A refused fetch is 422 `validation_failed`, naming the reason and the host, never the URI's path or
query.

A guide, sent or fetched, is Markdown in the structured form: no frontmatter, an H1 equal to the
toolguide's title (optional when sent, since the service writes it; required in a stored guide, where
it is derived from the title), no text before `## Overview`, then the fixed blocks and freeform sections
(ADR-0043 decision 4). The `markdown` content form reads it, and diverges from CommonMark on
purpose: an HTML comment is text, so a column-0 `##` inside one is a heading.

## Registering packs

Whole-pack registration (`PUT /packs/{packId}`, `POST /packs`), the two removals of a pack and the sources of a registration are configured under
`charter.registration` and `charter.pack-sources`. Every kind of source is off by default, and each flag is read at every
request, so an operator can switch a kind off without a restart. How a client uses it is in
[Compose packs](/guides/compose-packs/#register-a-pack). The differences from core are
[DIV-609 to DIV-614](/conformance/divergence-register/#registration-div-601699-adr-0065).

| Property | Default | What it does |
|---|---|---|
| `charter.registration.enabled` | `false` | Allows registration and the two removals of a pack (`DELETE` of an artifact and of a relationship). While it is off, each answers `503 pack_source_unavailable`. It needs `charter.config-root`. |
| `charter.registration.staging-dir` | `<config-root>/staging` | Where a registration spools and extracts its upload. Keep it on the volume of the packs. A registration needs about three times its archive free (at most 96 MiB on one volume; on two, 64 MiB here and 32 MiB on the packs volume), else `503 pack_storage_full`. The service refuses to start when it is `charter.config-root`, holds it, is or lies inside the `packs` or `activations` directory, or lies under `/proc`, `/sys` or `/dev`: the sweep removes the job directories it finds there, and only those. |
| `charter.pack-sources.https.enabled` | `false` | Allows `POST /packs` with an `https_archive` source. |
| `charter.pack-sources.https.allowed-hosts` | empty | The hosts an archive may be fetched from, matched exactly after lower-casing and stripping a trailing dot. Empty refuses every URL (`host_not_allowed`). |
| `charter.pack-sources.https.timeout` | `60s` | One deadline over resolution, every hop and the body. |
| `charter.pack-sources.https.max-size` | `32MB` | The largest archive accepted. At most 32 MiB: a larger value stops the start, because the free-space check of a registration is sized from that bound. |
| `charter.pack-sources.https.max-redirects` | `3` | The most redirects followed; each target is checked again. |
| `charter.pack-sources.git.enabled` | `false` | Allows `POST /packs` with a `git` source. |
| `charter.pack-sources.git.allowed-hosts` | empty | The hosts a repository may be fetched from. Empty refuses every URL. |
| `charter.pack-sources.git.executable` | unset | The absolute path of the git binary. Unset, the service looks for `git` on the `PATH`. |
| `charter.pack-sources.git.timeout` | `60s` | One deadline over the lookup, the wait for a slot and every git command. |
| `charter.pack-sources.filesystem.enabled` | `false` | Allows `POST /packs` with a `filesystem` source. |
| `charter.pack-sources.filesystem.root` | unset | The directory a path is read under. Switching the kind on with no root stops the start. |

- **The filesystem root** must not lie under `/proc`, `/sys` or `/dev`, and must not overlap `charter.config-root`. A
  path is relative, has no empty, `.` or `..` segment, and is read under the root. Every local-path refusal answers one
  `path_refused`; the reason is in the operator log only.
- **The resolver guard** (`GlobalAddressResolverProvider`) acts on the union of the guide, https archive and git hosts,
  as for guides (see [Fetching toolguide guides](#fetching-toolguide-guides)). An archive fetch and a git fetch are direct,
  use no proxy, and accept only https on port 443 with no redirect, user information or IP literal. The git source
  follows no redirect at all, not even to the same host.
- **Git.** The git source needs the system `git`, version 2.50.1 or later, on the `PATH` or at
  `charter.pack-sources.git.executable`. The service never uses JGit and never checks out a work tree: it runs the host
  binary into a bare work repository, with a fixed argument list and a cleaned environment. It checks the version at
  every start. An old or missing git makes the git kind answer `503 pack_source_unavailable` while the other kinds
  work. A link, a submodule or an odd mode in the fetched tree is refused `422 validation_failed` with the violation
  `entry_type`, as for an archive. Repositories that use the sha256 object format are untested: the work repository is
  created as sha1.
- **Git's memory.** Every git run carries `-c` pins that bound what git inflates and maps: `core.bigFileThreshold=1m`
  (a larger blob is streamed, not inflated whole), `pack.windowMemory=16m`, `pack.threads=1`, `core.packedGitLimit=64m`,
  `core.packedGitWindowSize=16m` and `core.deltaBaseCacheLimit=16m`. They keep the resident memory of a fetch to tens of
  MiB, and a pack up to the 32 MiB source bound is still fetched, only more slowly. **Residual risk:** the pins do not cap
  address space (git maps pack windows with `mmap`) or the time git spends, which the one deadline bounds. Run the
  service with an operating-system memory limit as well.
- **A refused source** ends the registration `422 pack_source_refused`. The detail is `<token>: <fixed sentence>`, with
  no URL, path or upstream text. The tokens are `host_not_allowed`, `address_not_global`, `url_refused`,
  `redirect_refused`, `wrong_media_type`, `too_large`, `digest_mismatch`, `name_mismatch`, `source_malformed`,
  `path_refused`, `bad_ref` and `commit_mismatch`. An upstream failure is a plain `502` and a timeout a plain `504`.
- **Known limit of the local source.** The file-key and link-count checks look a file up by name, not on the open handle,
  because the JDK has no `fstat` on a channel. A race between the check and the open needs write access under the root.
  Give the root to the service alone.

The `git-source` job of `service.yml` runs the real-git tests on a git of 2.50.1 or later. It is a job of the `service`
workflow, so the `service` result that `CI gate / gate` counts includes it. Branch protection requires only
`CI gate / gate` ([ADR-0023](/adr/0023-ci-gate-required-check/)), so no setting changes for the new job.

## Exceptions to a gate

`config/checkstyle/suppressions.xml` is the one register of exceptions to the hand-written
gates. It holds no entry today. An entry names the rule, the file, the reason and a sunset
date; no gate is relaxed anywhere else, and blanket suppressions are not allowed.

`checkCheckstyleRegister`, part of `check`, keeps the register in order. It fails when an entry's comment
lacks one of `rule:`, `file:`, `reason:` or `sunset:`, when a sunset date has passed, or when the `Size:`
line in the header does not count the entries. An entry is a `<suppress>` or `<suppress-xpath>`
element or a comment-only entry (a comment with a `rule:` line); all are checked and counted alike. It does
not run Checkstyle and does not ask whether an entry is still needed; an empty register passes.

The four complexity limits decided in
[ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) are not on yet:
cyclomatic complexity 15, NPath 1000, 60 non-empty lines per method and nested-if depth 1. No build
fails on them today, so keep new methods within them.
[#367](https://github.com/spec-kitty/spec-kitty-charter/issues/367) first refactors the methods over
them, with no register entry, and
[#371](https://github.com/spec-kitty/spec-kitty-charter/issues/371) then turns them on.

## The nightly SonarCloud report

`.github/workflows/sonar.yml`, shown in Actions as **Sonar (nightly, reported)**, analyzes `main` at
04:23 UTC every day, and on a manual run from `main`. It never runs on a pull request.

It is report-only. It is not part of `CI gate / gate`, and a red build, scan or quality gate never
turns the run red. With the token, each run writes a job summary, a table of the outcome of the pack sync, the
build, the coverage report, the scan and the quality gate. The quality-gate verdict appears in that
table and on SonarCloud.

Until the operator creates the SonarCloud project and the `SONAR_TOKEN` repository secret, the run
skips green: its summary says that the token is not configured. The workflow assumes the project key
`spec-kitty_spec-kitty-charter`, which the operator confirms when creating the project.

## Change the contract

1. Edit the split files under `contracts/charter/` and list new files in the directory's
   `_index.yaml`.
2. Write or change the scenario in `contracts/charter/scenarios/` that pins the behavior.
3. Add a changelog fragment, `contracts/charter/changes/YYYY-MM-DD-<slug>.md`, holding one `###`
   block; a scenario change gets one too. Do not edit `contracts/charter/CHANGELOG.md`: the
   release pull request assembles the fragments into it. The format is in
   [`contracts/charter/changes/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/changes/README.md).
4. Run `./gradlew :contracts:check`, then `./gradlew verifyAll`.

The contract checks follow the conventions in
[`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md).
A new lint rule lands with at least one planted fixture that fails it, and only it; that page
describes the steps.

Adding a property to a response schema is a breaking change, because response schemas are
closed ([Contract and DTOs](/architecture/contract-and-dtos/#versioning)).

## Release a contract version

Releasing a contract version is a short procedure with its own rules, kept in one place:
[the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline). Follow it from a topic branch, and run
`./gradlew :contracts:check` before you open the pull request.

`contracts/_shared/` started as a copy of core's shared vocabulary. This repository's copy is
canonical ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)): change
it here when there is reason to, and list the change in `contracts/_shared/VENDORED.md`.

## Release a service version

The service releases on its own line, `service-v<version>`, separate from the contract's
([ADR-0046](/adr/0046-service-release-line-and-native-packaging/)). A release attaches:

- a native executable for each of linux-x64, linux-arm64, macos-arm64 and windows-x64;
- the jar, for every other host with Java 25;
- one `.sha256` file per asset, and `SHA256SUMS`;
- a build-provenance attestation of every asset.

Core's service pin fetches and verifies these assets by name.

1. **Choose the version** by the service's own SemVer (ADR-0046 decision 2):
   - **major:** a command-line option, configuration key, environment variable or token rule is removed or
     changed;
   - **minor:** a new contract minor, or a new capability;
   - **patch:** fixes only.

   The service stays at 0.x until contract 1.0.0. A pre-release uses only letters, digits and dots
   (`0.2.0-rc.1`), because core's tag rule refuses `0.2.0-rc-1` and any `+build` metadata.
2. **For a final release, release the bundled contract first.** It is `info.version` of
   `contracts/charter/openapi.yaml`, released as `contract-charter-v<info.version>`
   ([Release a contract version](#release-a-contract-version)). A pre-release may bundle an unreleased
   contract; its release notes say so.
3. **In a pull request, set `version` in `gradle.properties` to the release** (`0.2.0`, no `-SNAPSHOT`). Check
   the gate locally:

   ```bash
   git fetch --tags origin
   ./gradlew checkServiceReleaseTag -PreleaseTag=service-v0.2.0
   ```

   It names the cause of a refusal, or writes `build/service-release/gate.properties`.
4. **Merge the pull request.** Run `service-native.yml` on `main` with `workflow_dispatch` at the merge commit, and tag
   only when it is green. `CI gate / gate` no longer proves the native build
   ([ADR-0060](/adr/0060-native-build-runs-nightly-and-per-release/)). The operator then tags the merge commit on
   `main` and pushes the tag:

   ```bash
   git tag service-v0.2.0 <merge-commit> && git push origin service-v0.2.0
   ```

   A tag ruleset on `service-v*` limits who can push one.
5. **Set the next development version.** In a follow-up pull request, set `version` to the next
   `-SNAPSHOT`.

Pushing the tag runs `.github/workflows/service-release.yml`.

- **What it does:**
  - judges the tag;
  - runs `verifyAll`;
  - builds the jar and the four native executables, and starts each one with
    `scripts/release/smoke-service.sh`;
  - assembles the assets with `scripts/release/service-release-assets.sh`;
  - checks them, attests them, and creates a GitHub release that is never marked latest. A tag with a
    pre-release suffix makes a pre-release.
- **When it publishes nothing:**
  - the tag's form is wrong;
  - the commit is not on `main`;
  - the tag is not the build version;
  - a final release's contract is unreleased;
  - any target fails;
  - the tag moved while the assets were built;
  - a release of the tag already exists.
- **Rehearsals:**
  - A manual run of the workflow with a tag name builds the assets as a workflow artifact and publishes
    nothing.
  - `scripts/release/rehearse-service-release.sh` rehearses every gate refusal and the asset set without
    any remote. It runs in CI on every change.
  - **Before the very first `service-v*` tag,** do one manual `workflow_dispatch` run against a candidate
    tag name. The publish job and the two remote gate-checks (on-main, tag-still-names-the-commit) run only
    on a real tag push, so a dispatch run is their first exercise end to end, while still publishing nothing.

If a run fails after its assets were built:

- **Re-running only the failed jobs works for 7 days.** The build's artifacts are kept for 7 days; after that,
  re-run the whole workflow.
- **A failed upload can leave a partial release.** If `gh release create` fails partway, the next run stops at
  "release exists". Delete that release, not the tag, and re-run.

To check a download:

```bash
sha256sum --check --ignore-missing SHA256SUMS
gh attestation verify spec-kitty-charter-service-0.2.0-linux-x64 --repo spec-kitty/spec-kitty-charter \
  --signer-workflow spec-kitty/spec-kitty-charter/.github/workflows/service-release.yml \
  --source-ref refs/tags/service-v0.2.0
```

The native executables are built in CI only, on each runner, by `.github/workflows/service-native.yml`. It runs
nightly on `main` and for each service release, not on a pull request, and it is not part of `CI gate / gate`
([ADR-0060](/adr/0060-native-build-runs-nightly-and-per-release/)). To build them for a branch whose change
risks the native build (a new reflective path, a dependency bump, a change to `service-native.yml`,
`scripts/release/smoke-service.sh` or `charter.native-image.gradle.kts`), run the workflow on that branch with
`workflow_dispatch`. Locally,
`./gradlew :deployable:nativeCompile -Pcharter.native=true`
needs a GraalVM 25 with `native-image` on `JAVA_HOME`. `verifyAll` never needs one. On Windows, where the contract
tools have no pinned binary, pass a bundle built elsewhere with `-PcontractBundle=<file>`, absolute or relative to the
repository root, as the Windows job does. A path that names no file fails `processResources` with
`-PcontractBundle names no file: <path>`.

## The Java client

`client-java/` generates the charter client for Java consumers ([#218](https://github.com/spec-kitty/spec-kitty-charter/issues/218)),
with openapi-generator's `java` generator and `okhttp-gson` library at the version pinned in `gradle/libs.versions.toml`.
Its build is the `charter.client-java` convention plugin.

The nightly SonarCloud report does not cover `client-java`: `sonar.yml` names the service modules only.

- **Input:** the single-file bundle `:contracts:bundleContract` writes, through the `contractBundle` variant, never the
  split files under `contracts/charter/`. The client matches what is published, byte for byte.
- **Tolerance:** a client generated today must read what a later 0.x minor adds. `TolerantClientNormalizer` reads every
  closed object (`additionalProperties: false`) as open, so a member the client does not know lands in the model's
  additional properties instead of failing the decode. `enumUnknownDefaultCase=true` maps an enum value it does not
  know to `UNKNOWN_DEFAULT_OPEN_API`. The server build keeps the closed objects. Two consequences: a model that
  carries an unknown member serializes it back (a request built from such a model can carry a member an older service
  refuses), and it compares unequal to one without it.
- **Java 21:** everything compiles with `--release 21` on the Java 25 toolchain; `ClientOutputTest` fails if a class
  file targets a later release.
- **Header:** `client-java/templates/licenseInfo.mustache` puts `SPDX-License-Identifier: MIT` and a do-not-edit note
  in every generated file.
- **Gates:** generated sources sit in their own source set, `generated`, outside the gates. The one hand-written class,
  `ProblemDetails.from(ApiException)`, reads an error body as the contract's `Problem` and keeps every gate.
- **Reproducible:** `checkClientReproducible` generates a second time and fails unless the sources are identical.

Never edit generated code: change the contract, and the next build regenerates the client. The coordinates are group
`ai.speckitty.charter`, artifact `charter-client`, package `ai.speckitty.charter.client`, version the contract's
`info.version`. Nothing is published yet: the release zip, the JDK 21/25 matrix and the quickstart wait for contract
0.1.0 ([#217](https://github.com/spec-kitty/spec-kitty-charter/issues/217)). Consumers in other languages, or who want
their own Java client, follow [Generate your own client](/guides/generate-a-client/).

## Change a dependency

Versions live in `gradle/libs.versions.toml`, tool plugins (Spotless, Error Prone, PIT)
included; convention plugins read them from the catalog. Dependency verification is strict,
so a new or changed artifact needs its checksum recorded:

```bash
./gradlew --write-verification-metadata sha256 verifyAll
```

Review the diff of `gradle/verification-metadata.xml`; never edit it by hand, except to empty
`<components>` when pruning ([below](#pruning-stale-entries)). The vacuum and
oasdiff binaries are not Gradle dependencies: their versions and SHA-256 checksums are pinned
in `contracts/build.gradle.kts`.

A module that is not a Spring module may still import the Spring Boot BOM as a platform, to
pin a transitive dependency to the version the service uses. `packs` does this for
the Jackson and slf4j versions that networknt's JSON Schema validator brings in.

### Pruning stale entries

Gradle adds entries to the metadata but never removes them. To prune, copy the file aside
first, then empty the `<components>` element and keep `<configuration>` as it is. Then
regenerate over every task family CI runs. `help` records every configuration Gradle can
resolve, including those `-Pcharter.native=true` adds; the other lines add what the CI tasks
resolve while they run. Some entries are therefore resolved by no CI job today, such as
`junit-platform-native` through `nativeImageTestClasspath`:

```bash
./gradlew --write-verification-metadata sha256 help
./gradlew --write-verification-metadata sha256 -Pcharter.native=true help
./gradlew --write-verification-metadata sha256 verifyAll -PrequireSymlinks=true
./gradlew --write-verification-metadata sha256 pitest
./gradlew --write-verification-metadata sha256 :packs:wholePackRoundTrip :packs:timingBudgets \
  :deployable:bootJar :charter-reports:aggregateJavadoc :contracts:bundleContract
./gradlew --write-verification-metadata sha256 :deployable:nativeCompile -Pcharter.native=true
```

The last line is there for the dependencies the native build resolves. Without GraalVM's
`native-image` it fails after it has resolved them; that is enough.

Run the whole set twice: once with your usual `GRADLE_USER_HOME`, and once with a fresh, empty one
(`GRADLE_USER_HOME=$(mktemp -d)`). A warm cache skips the BOM `.module` files a cold CI runner
downloads, so a warm-only run drops entries that runner needs. Then review the diff: a removed
entry should be a version the build no longer selects, and no kept artifact's checksum should change.

The regeneration writes `origin="Generated by Gradle"` on every entry. From the copy, re-apply
every other origin (one recorded by hand when the artifact was checked) to each artifact whose
sha256 did not change.

Run every command from the repository root. With `-p build-logic`, Gradle treats `build-logic` as a
build of its own and writes a separate `build-logic/gradle/verification-metadata.xml`; delete that
file. `verifyAll` already covers `build-logic` as an included build, against the root file.
