# Term-id map: `spec-kitty-core`

<!-- cspell:ignore behaviour teamspace -->

**Mission**: `glossary-contexts-and-terms-01M7B1PP` (#106). **Ruling**: `DM-01M7B1Q77QRB2PNDG0CDBNVVH3` (research R4).
**Source**: the example pack `built-in`, `glossaries/spec-kitty-core.glossary.yaml` (WP02, commit `3a918d57`),
checked row for row against the WP02 review map: 139 terms, 139 distinct ids, none hand-picked.

## The rule

A term id is minted from the term's surface form (the old `surface`, the new `name`): lower-case it, replace each
run of characters other than `[a-z0-9]` with one `-`, then trim `-` from both ends. The result must be a letter-first
kebab slug of at most 64 characters (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`), unique within its context. A surface whose
slug breaks either condition gets a hand-picked id, recorded in this map; none of the 139 needed one, and the longest
id has 28 characters. The term's URN is `term:spec-kitty-core/<id>`, and an old `see_also` that names a surface
becomes the URN of the term that surface mints. The same rule applies to the other open-packs glossaries
([open-packs draft](open-packs-draft-glossary-contexts.md)).

Statuses: 112 `active`, 15 `deprecated`, 12 `draft`.

## Map

Ordered by term id.

| Surface (`name`) | Term id | Status |
| --- | --- | --- |
| accepted work package | `accepted-work-package` | active |
| action context | `action-context` | active |
| activation ledger | `activation-ledger` | active |
| activation preset | `activation-preset` | active |
| active charter | `active-charter` | active |
| adversarial squad | `adversarial-squad` | active |
| advise mode | `advise-mode` | draft |
| agent skills | `agent-skills` | active |
| approved | `approved` | active |
| base branch | `base-branch` | active |
| behaviour-driven development | `behaviour-driven-development` | active |
| branch strategy gate | `branch-strategy-gate` | active |
| build | `build` | active |
| build_id | `build-id` | active |
| bulk edit | `bulk-edit` | active |
| canonical issue-matrix | `canonical-issue-matrix` | active |
| catastrophic backtracking | `catastrophic-backtracking` | active |
| ceremony commit | `ceremony-commit` | deprecated |
| change mode | `change-mode` | active |
| characterization test | `characterization-test` | active |
| charter | `charter` | active |
| charter bundle | `charter-bundle` | active |
| charter-content migration | `charter-content-migration` | active |
| charter lint | `charter-lint` | draft |
| charter offering | `charter-offering` | active |
| charter pack | `charter-pack` | active |
| charter selection | `charter-selection` | deprecated |
| conflict prediction | `conflict-prediction` | draft |
| consolidation state | `consolidation-state` | draft |
| coordination worktree | `coordination-worktree` | active |
| current branch | `current-branch` | active |
| decision moment | `decision-moment` | draft |
| deliberate linearity | `deliberate-linearity` | active |
| dependency gate | `dependency-gate` | active |
| dependency readiness | `dependency-readiness` | active |
| diff compliance gate | `diff-compliance-gate` | active |
| directive | `directive` | active |
| doctrine catalog | `doctrine-catalog` | deprecated |
| doctrine pack | `doctrine-pack` | deprecated |
| doctrine pack id | `doctrine-pack-id` | deprecated |
| doctrine reference graph | `doctrine-reference-graph` | active |
| done | `done` | active |
| drg | `drg` | active |
| encoding chokepoint | `encoding-chokepoint` | active |
| encoding provenance | `encoding-provenance` | active |
| enhances | `enhances` | active |
| evidence bundle | `evidence-bundle` | draft |
| execution lane | `execution-lane` | active |
| feature branch | `feature-branch` | deprecated |
| feature slug | `feature-slug` | deprecated |
| friendly_name | `friendly-name` | active |
| gate binding | `gate-binding` | active |
| gate handler | `gate-handler` | active |
| glossary chokepoint | `glossary-chokepoint` | draft |
| harness | `harness` | active |
| harness isolation worktree | `harness-isolation-worktree` | active |
| implementation concern | `implementation-concern` | active |
| integrating worktree | `integrating-worktree` | active |
| issue-matrix schema drift | `issue-matrix-schema-drift` | active |
| lane | `lane` | active |
| lane branch | `lane-branch` | active |
| lane worktree | `lane-worktree` | active |
| lightweight mode | `lightweight-mode` | active |
| machine-level runtime | `machine-level-runtime` | active |
| main repo | `main-repo` | deprecated |
| main repository | `main-repository` | deprecated |
| main repository root | `main-repository-root` | deprecated |
| merge-pending work package | `merge-pending-work-package` | active |
| merge state | `merge-state` | deprecated |
| merge_target_branch | `merge-target-branch` | active |
| mid8 | `mid8` | active |
| migration | `migration` | active |
| minimal viable trail | `minimal-viable-trail` | draft |
| mission | `mission` | active |
| mission_id | `mission-id` | active |
| mission_number | `mission-number` | active |
| mission run | `mission-run` | active |
| mission_slug | `mission-slug` | active |
| mode mismatch | `mode-mismatch` | active |
| node_id | `node-id` | active |
| occurrence classification | `occurrence-classification` | active |
| occurrence map | `occurrence-map` | active |
| overrides | `overrides` | active |
| pack default charter | `pack-default-charter` | deprecated |
| phase | `phase` | active |
| pipeline-shape | `pipeline-shape` | active |
| plan_concern_refs | `plan-concern-refs` | active |
| planning_base_branch | `planning-base-branch` | active |
| planning repository | `planning-repository` | deprecated |
| post-merge mode | `post-merge-mode` | active |
| pr-bound mission | `pr-bound-mission` | active |
| preflight validation | `preflight-validation` | draft |
| primary branch | `primary-branch` | active |
| primitive | `primitive` | active |
| project | `project` | active |
| project layer | `project-layer` | active |
| project root checkout | `project-root-checkout` | deprecated |
| project_uuid | `project-uuid` | active |
| provenance | `provenance` | active |
| provenance sidecar | `provenance-sidecar` | active |
| qualified citation | `qualified-citation` | active |
| repo_slug | `repo-slug` | active |
| repository | `repository` | active |
| repository_label | `repository-label` | active |
| repository root checkout | `repository-root-checkout` | active |
| repository_uuid | `repository-uuid` | active |
| requirement id | `requirement-id` | active |
| resolved status surface | `resolved-status-surface` | active |
| review.py package | `review-py-package` | active |
| rule pipeline | `rule-pipeline` | active |
| scope | `scope` | active |
| semantic conflict | `semantic-conflict` | active |
| skill | `skill` | active |
| skill package | `skill-package` | active |
| sonar quality gate | `sonar-quality-gate` | active |
| start branch | `start-branch` | active |
| status commit | `status-commit` | active |
| status-writing operation | `status-writing-operation` | deprecated |
| step contract | `step-contract` | draft |
| strictness | `strictness` | active |
| structural debt | `structural-debt` | active |
| styleguide | `styleguide` | active |
| success criterion | `success-criterion` | active |
| synthesis manifest | `synthesis-manifest` | active |
| tactic | `tactic` | active |
| target branch | `target-branch` | active |
| target-owned bookkeeping | `target-owned-bookkeeping` | active |
| teamspace | `teamspace` | draft |
| tool-surface drift | `tool-surface-drift` | active |
| topic branch | `topic-branch` | active |
| transition gate | `transition-gate` | active |
| unsafe bypass | `unsafe-bypass` | active |
| widen mode | `widen-mode` | draft |
| work package | `work-package` | active |
| workspace | `workspace` | active |
| wp | `wp` | active |
| wp state | `wp-state` | active |
| write branch | `write-branch` | active |
| write checkout | `write-checkout` | active |
