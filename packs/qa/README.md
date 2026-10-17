# `qa` — QA Charter Pack

An open Spec Kitty Charter Pack for running quality assurance as governed work, and for keeping
a product's acceptance tests tied to what the product promises.

It does two jobs:

1. **A `qa` mission type.** The seven canonical steps redefined in QA terms (discovery, test
   strategy, test plan, test design, test execution, test report review, acceptance), with five
   QA agent profiles, step contracts, templates, and two evidence tools. Ported from
   `spec-kitty-qa-pack`; see [PROVENANCE.md](PROVENANCE.md).
2. **Description to acceptance CI.** A standing loop from a human-readable product description to
   an automated release gate:

   ```
   promise page (DocKitty)  ->  Given/When/Then scenario  ->  bound acceptance test  ->  CI gate
        ratified by product        @promise:<id>              project's own runner     trace lint + run
   ```

The pack names no test runner. Your project runs its own tests; the pack governs what they must
cover and how honestly they report.

## What ships

| Kind | Artifacts |
| --- | --- |
| Directives | QA_001 traceability, QA_002 defect report quality, QA_003 no fake green, QA_004 exit criteria first, QA_005 risk-based prioritisation, QA_006 timeboxed exploratory charters, **QA_007 promise traceability**, **QA_008 reproductions gate releases** |
| Tactics | seven test-design tactics, plus **`qa-promise-elicitation`** |
| Procedures | coverage gap analysis, defect triage, go/no-go review, test plan review, plus **`qa-description-to-acceptance-ci`** |
| Styleguides | **`qa-promise-page`** |
| Toolguides and assets | `qa-carrier-lint`, `qa-result-ingest`, **`qa-promise-trace-lint`** (Python, stdlib plus ruamel.yaml) |
| Agent profiles | strategist, test designer, executor, reporter, defect triager |
| Mission | `qa` mission type, step contracts, command templates, artifact templates, **promise page and scenario templates** |
| Glossary | ISTQB-grounded QA glossary |

The scenario-writing doctrine itself is not duplicated: the procedure points at Spec Kitty's
built-in `bdd-scenario-formulation` tactic, `given-when-then-authoring` styleguide and `gherkin`
toolguide.

## Adopt

Requires Spec Kitty 4.x with the Charter Pack cutover (`spec-kitty charter pack validate --help`
works). Register the pack in the consuming project's `.kittify/config.yaml`, pinned to a release
of this repository:

```yaml
charter_packs:
  org:
    packs:
      - name: qa
        local_path: "${SPEC_KITTY_PACK_HOME}/qa"
        source_type: git
        url: "https://github.com/spec-kitty/spec-kitty-open-packs.git"
        ref: "<commit SHA or pack/qa/vX.Y.Z tag>"   # never a branch; SPEC_KITTY_PACK_HOME is any directory
        subdir: packs/qa
```

Then:

```bash
spec-kitty charter fetch --pack qa
spec-kitty doctor charter-packs                       # pack healthy, no dangling endpoints
spec-kitty charter activate mission-type qa           # if you run QA missions
spec-kitty agent mission create <slug> --mission-type qa
spec-kitty charter context --include procedure:qa-description-to-acceptance-ci
```

See [Use a Charter Pack](../../docs/guides/use-a-doctrine-pack.md) for the configuration fields.

The pack activates nothing by itself. To put the promise layer in force for every mission, add
to the consuming org's charter:

```yaml
required_directives: [QA_007, QA_008]
required_procedures: [qa-description-to-acceptance-ci]
```

## The promise trace lint in CI

```bash
LINT="$(spec-kitty charter pack asset path qa-promise-trace-lint)"
python "$LINT" --promises docs --scenarios tests/scenarios --release "$VERSION" --json --out reports/promise-trace.json
```

Exit 0 when every ratified promise is checked and every scenario cites a ratified promise; 1 with
the list of what to fix; 2 when it cannot measure. Details: [toolguides/QA-PROMISE-TRACE-LINT.md](toolguides/QA-PROMISE-TRACE-LINT.md).

## Status

Pre-release. On Spec Kitty 4.0.0rc7 (`spec-kitty/spec-kitty@3b7c6dde`, plus spec-kitty#6004 where
`charter activate` is used on typer ≥ 0.26) the pack validates cleanly, and a scratch `qa` mission was driven through all seven steps to `terminal: done`:
`next` served the pack's own step prompts, the pack's plan-field declaration refused the unfilled
test-plan template, `review` held until `qa-traceability.yaml` and `test-report.md` existed,
`accept` held until `qa-coverage.json` existed, and `qa-result-ingest` plus
`qa-traceability-lint` turned a template-shaped work package and a JUnit file into
`verdict: ready`. `QA_001`–`QA_008` resolve for the `specify`, `plan` and `review` actions. Not
yet proven: a real QA mission with real content, and the promise loop run against a real
product. The first intended run is the Spec Kitty CLI itself.

### Known limitations

- **Step artifacts are read from two places.** In the probe run the `plan` guard read `plan.md`
  from the primary checkout's `kitty-specs/<mission>/`, while the `review` and `accept` guards
  read the coordination worktree. Write each artifact where the blocking `next` result's
  `guard_failure_paths` points.

- **`discovery` gets no charter governance.** It is a QA-only step that aliases the built-in
  `research` action, and `charter context --action discovery` resolves no directives. The step
  prompt itself still carries its directive citations.
- **`charter activate directive QA_00N` refuses the id** ("Unknown directive ID") although
  `charter context --include directive:QA_00N` resolves it. Put the directives in force through
  `required_directives` in your org charter, as above.
- **Only the first org pack answers `charter context --include`**
  ([spec-kitty/spec-kitty#5779](https://github.com/spec-kitty/spec-kitty/issues/5779)). With
  this pack registered after another org pack (for example `spec-kitty-internal`),
  `--include directive:QA_007` reports "No directive found" while the action context and
  `doctor charter-packs` are fine. List `qa` first, or wait for the fix.
- **`charter activate mission-type qa` needs typer < 0.26 or spec-kitty/spec-kitty#6004.** A
  `pip`/`uv tool` install that resolves typer 0.26+ refuses every positional `charter activate`
  with `--pack, --force, --json only apply with --preset.` until that fix ships.
