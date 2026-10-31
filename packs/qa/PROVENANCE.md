# Provenance

This pack is a port of [`spec-kitty/spec-kitty-qa-pack`](https://github.com/spec-kitty/spec-kitty-qa-pack)
at commit `e9ed31c` (2026-09-02), authored almost entirely by MOES-Media through eleven Spec Kitty
missions. It was first ported to `spec-kitty/private-packs` (draft PR #1, commit `e1aadcb`), which
added the promise layer, and then moved here: the source pack was prepared for donation upstream
(its `pack-donation-readiness` mission), and this repository is its public home. The source
repository carries no licence. **The author, MOES-Media, agreed on 2026-10-10 to distribute the pack
under this repository's MIT licence** (confirmed to the maintainers on that date).

The source repository keeps the full mission trail (`kitty-specs/`, `_rnd/`, `docs/install/`), the
authoring conventions (`CONVENTIONS.md`) and the pack's own 21-gate CI; this port does not copy
them. Read them there for the reasoning behind each artifact.

## What changed in the port

| Change | Why |
| --- | --- |
| Directive ids `QA-00N` became `QA_00N`. | Current Spec Kitty resolves org-pack directives only in the underscore form. Before the change, `charter context --include directive:QA-001` found nothing for any of the six directives. |
| `spec-kitty doctrine asset path` became `spec-kitty charter pack asset path`. | The `doctrine` command group was removed in the 4.x Charter Pack cutover. |
| `drg/fragment.yaml` carries no provenance header (the pack name `qa` comes from the consumer's registration); `org-charter.yaml` names the org `spec-kitty-qa` and uses schema version 2. | Namespacing, and the schema version `charter org init` writes. |
| Added QA_007 (promise traceability), QA_008 (reproductions gate releases), the `qa-promise-elicitation` tactic, the `qa-promise-page` styleguide, the `qa-description-to-acceptance-ci` procedure, the `qa-promise-trace-lint` asset and toolguide, and two templates. | The promise -> scenario -> acceptance CI layer (from private-packs PR #1). |
| `qa_result_ingest.py` reads test cases from fenced ```yaml blocks in the work-package body, with self-test Pair15. | Fixes spec-kitty-qa-pack#5: the template puts cases in the body (WPMetadata forbids them in the frontmatter), but the ingester read only the frontmatter, so a template-conformant mission ingested `cases_total: 0` with exit 0. |
| Removed `qa.graph.yaml`, `missions/qa/governance-profile.yaml`, the `qa-reviewer-quinn` placeholder profile, the empty `paradigms/` and every `.gitkeep`. | The empty pack-root graph and the duplicate governance profile are not read (the mission type still activates and creates missions without them); the placeholder profile carried no content and nothing referenced it. |
| Step-contract bootstrap commands are `charter context --action <a> --mission-type qa --json`. | The source's `--role` flag and `--profile`/`--tool` inputs do not exist on 4.x; all seven bootstrap commands failed with "No such option". |
| `qa-test-designer-tessa` cites the tactic `bdd-scenario-formulation`. | Its old reference, `behavior-driven-development`, was retired in 4.0.0rc5 in favour of that tactic. |
| The `test-case-template.md` examples (`TC-014`, `TC-015`) use `text` fences, and the ingester reads every CommonMark yaml fence form (`YAML`, `yml`, info-string attributes, tildes, longer and indented fences), warning on a repeated case id or an unclosed fence (self-test Pair16). | With the #5 fix the template's own examples would otherwise be ingested as cases, and other valid fence forms were dropped without a word. |
| Internal finding ids (`SK-NNN`) replaced by plain descriptions. | They point at a ledger outside this repository. |
| Not ported: `scripts/`, `kitty-specs/`, `_rnd/`, `kitty-ops/`, `.kittify/`, `docs/install/`, `CONVENTIONS.md`. | History and tooling pinned to older Spec Kitty builds; `pack_ci.py` imports APIs that no longer exist (`DoctrineService`, `save_pack_registry`). This repository does not yet reproduce that pack-validation step (tracked as a follow-up). |

At port time `mission-steps/` stayed although its `step.yaml` files named a `prompt.md` that was not
shipped: the runtime built the `qa` action sequence from those files, and the step prompts came from
`missions/qa/command-templates/`. Since spec-kitty/spec-kitty-open-packs#95 (2026-10-31) the mission tier is
one file, `missions/qa/qa.mission-type.yaml`, with its seven steps in run order and each step's prompt beside it
as `missions/qa/steps/<step>.prompt.md` (the former command templates); `mission-steps/`, the command templates,
the governance profile and the expected-artifacts manifest are retired (charter ADR-0044). Core still reads the
old layout, so the port-time verification below no longer applies as written.

## Verified at port time

Against Spec Kitty `main` at `3b7c6dde` (4.0.0rc7), in a scratch project with this pack registered
under `charter_packs.org.packs`:

- `spec-kitty charter pack validate packs/qa` and `charter org validate packs/qa`: 0 errors,
  0 advisories. `doctor charter-packs`: no collisions, no dangling endpoints, alone and together
  with `spec-kitty-internal` (core `packs/internal`) in either order. With both registered,
  `charter context --include` resolves only the first-listed pack's artifacts
  (spec-kitty/spec-kitty#5779).
- `charter activate mission-type qa` succeeds (with spec-kitty/spec-kitty#6004 on typer 0.27);
  `agent mission create --mission-type qa` creates a `qa` mission, not `software-dev`; `next`
  serves the `discovery` step from `missions/qa/command-templates/discovery.md`.
- `charter context --mission-type qa` resolves `QA_001`–`QA_008` for `specify`, `plan` and `review`.
- A scratch `qa` mission ran all seven steps to `terminal: done` (`discovery` → `accept`). The
  plan-field declaration refused the unfilled test-plan template; `review` and `accept` held until
  their blocking artifacts existed; on a work package written to `test-case-template.md`,
  `qa-result-ingest` reported `cases_total: 1` (0 before the #5 fix) and `qa-traceability-lint`
  reported `verdict: ready`, 1 of 1 requirements covered.
- Asset self-tests: `qa_result_ingest.py` 16 of 16, `qa_carrier_lint.py` 38 of 38,
  `qa_promise_trace_lint.py` 12 of 12.

## Vendored copy

Re-vendored from `spec-kitty-open-packs` `packs/qa` at commit `86d9284695a18a71f9f7cc934f0305b1b03de19e` (main, after PR #95 landed: the mission tier consolidated into one mission-type file, charter ADR-0044) on 2026-10-31. This file and `README.md` are local and differ from upstream on purpose; everything else in this directory is the upstream copy. The commit is pinned by `openPacksRef` in `gradle.properties`.
