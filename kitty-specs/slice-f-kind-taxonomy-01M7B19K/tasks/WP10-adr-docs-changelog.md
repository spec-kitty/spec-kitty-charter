---
work_package_id: WP10
title: ADR, docs and changelog
dependencies: [WP09]
requirement_refs:
- FR-019
- C-007
- C-002
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T047
- T048
- T049
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: docs/
create_intent:
- contracts/charter/changes/2026-11-14-kind-taxonomy-and-step-authority-breaking.md
execution_mode: code_change
owned_files:
- docs/adr/**
- docs/api/service-contract.md
- docs/architecture/**
- docs/context/**
- docs/development/hypermedia-hateoas.md
- docs/development/epic-186-sanitized-shape-follow-ups.md
- docs/glossary/**
- docs/_meta/terminology.yaml
- docs/conformance/**
- docs/changelog/**
- contracts/charter/changes/2026-11-*-kind-taxonomy-and-step-authority*.md
- site/.contextive/definitions.yaml
role: implementer
---

# Work Package Prompt: WP10 - ADR, docs and changelog

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-05.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

One new ADR records slice F's decisions (plan decision `DM-01M7B3Q8R1C6J89MM3YWSMF8XZ`). The ADRs it touches carry short notes that point at it. The docs describe the model as built. One change fragment lists every breaking and added item.

## Context

- The decisions are listed in `spec.md`'s Intent Summary and research R14.
- The doc footprint is in `research/footprint-mission-step-contract.md`, section "Docs needing an amendment note".
- The changes-fragment format is enforced by `ChangelogFragmentsTest`: one dated `###` block per heading.
- Pick the ADR number against `main` and the open PRs when you write it (0076 was free on 2026-11-13).

## Subtasks

### T047: The ADR and its notes

- `docs/adr/00NN-kind-taxonomy-and-step-authority.md`, in the format of the other ADRs (front matter, Status, Context, Decision, Consequences, Alternatives, References). Status: Accepted, with the operator and the date 2026-11-13. Its decisions:
  1. R18's shape.
  2. Substeps on the step: every field, the grammar, and delegations as data plus `scope` edges.
  3. `mission_step_contract` retired; the gate becomes a lifecycle guard.
  4. Complete authored type-to-step edges.
  5. The endpoint table, typed by kind markers, with one bridge and the fault order.
  6. `impacts`, `replaces` and `delegates_to` retired, with the note that `replaces` was used to mean `overrides`.
  7. `template_set` dropped.
  8. Consequences: the M10 effect on the effective context; open-packs follows; core aligns through the generated client; #513 is narrowed.
- Add dated amendment notes, pointing at the new ADR, to ADR-0044, 0050, 0054, 0064 (decision 13's table), 0074 (decision 12's #533 gap is closed) and 0075 (decision 2's `MissionStepContractIdentifier`). Add inline notes to the other ADRs that name the kind: 0024, 0036, 0037, 0042, 0048, 0049, 0053, 0071, 0073.
- Update `docs/adr/README.md`'s index.

### T048: Docs

- `docs/api/service-contract.md`: R18 is decided and built, and the "deferred to the merge minor" text goes. The step-contract rows go. The relation list follows.
- `docs/architecture/resolution-model.md:100-104`: point at the read.
- `pack-schemas/mission-step-contract.md` is removed. The pack-schemas README, `logical-model.md`, `descriptors.md`, `descriptor-schemas.md`, `composition-and-activation-rules.md` and `pack-registration-distribution.md` follow.
- The diagrams (`.puml`): the six that name the kind. Regenerate the `.svg` files if the repository's tooling does; otherwise note it.
- `docs/context/*`, the glossary (add `substep`; remove the step-contract terms), `docs/_meta/terminology.yaml`, `site/.contextive/definitions.yaml`, and the conformance register (divergences that cite the kind or `impacts`).
- `hypermedia-hateoas.md`: `kinds` is in the contract, and `step-contract` is gone from every row.
- Run `python3 scripts/docs/check_docs.py all`, markdownlint-cli2 and cspell.

### T049: The change fragment

Add one fragment with:

- `### Breaking`, listing:
  - the retired kind and everything under it, including the switch-gate key;
  - the retired relations and the `impacts` strength;
  - the new refusals (`relationship_endpoint_not_allowed`, `mission_step_unlinked`, `substep_delegation_*`, and the same-kind rule on `overrides`);
  - `template_set` dropped;
  - the renamed delegation components.
- `### Added`, listing `getKindTaxonomy`, `kinds` links, and `MissionStep.substeps`.

Note for #559 that the packs schemas break too.

## Validation

- `python3 scripts/docs/check_docs.py all` is clean, and markdownlint and cspell pass.
- `ChangelogFragmentsTest` is green.

## Definition of Done

- T047 to T049 are done, and every touched ADR points at the new one.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **An ADR number collision.** Check `main` and the open PRs immediately before you commit.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP10 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
