# Release handoff: charter conceptual model, journeys and scenarios

Publish step, 2026-10-26. Gate: `audit-report.md`, verdict **ready-to-publish** (contract suite deferred
to CI).

## What ships

- `docs/context/conceptual-model.md`: the conceptual model (meaning only), appendices A and B.
- `docs/context/user-journeys/`: hub and eleven journey pages, each with a diagram.
- `contracts/charter/scenarios/journeys/`: eleven scenario files; `@WIP` scenarios excluded by the
  contract suite.
- The charter glossary, now owned here: `site/.contextive/definitions.yaml` and the generated
  `docs/glossary/` pages.
- ADR-0028 to ADR-0034, with inline "superseded in part" annotations on ADR-0009, 0011, 0012, 0013,
  0014, 0017, 0020, 0025, 0026 and 0027.
- Architecture and context pages brought in line with the ADRs (`logical-model.md` as the
  concept-to-representation translation).

The full file list is `docs/README.md` in this mission directory.

## Where it ships

The docsite (doc-kitty, GitHub Pages), built by `.github/workflows/docs-site.yml` when pull request
[#199](https://github.com/spec-kitty/spec-kitty-charter/pull/199) merges to `main`. The operator merges;
publication follows the merge. Source revision: the PR's head at merge.

Generator: doc-kitty glossary generator, at the version `site/package.json` pins after the rebase onto
`main`.

## Owner

The operator (Stijn Dejongh) owns publication, support questions and revalidation of these pages.

## Caveats accepted in validate

- The contract suite runs in CI on PR #199, not locally (no JDK 25 here).
- Agent readings that are not operator rulings are marked open in their ADRs and listed in the PR
  for the operator.
- Core still carries the old glossary and ADR `2026-10-03-1`; cleaned up in a later documentation effort.

## Living documentation

- The conceptual model and journeys are explanations: revisit them when an ADR changes a concept, and
  when a surface marked envisioned or missing lands (DIRECTIVE_037: update in the same change).
- A journey scenario loses `@WIP` in the change that builds its surface.
- The glossary is edited in `site/.contextive/definitions.yaml` and regenerated, never edited in the
  generated pages.
- Open questions (conceptual model "Open questions" section; ADR-0031, 0032, 0033) feed the next
  mission's discover step.
