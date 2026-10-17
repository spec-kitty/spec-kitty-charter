# QA Promise Trace Lint

`qa_promise_trace_lint.py` checks that the chain from product description to
acceptance scenario is intact. It reads two trees and runs nothing.

## Inputs

- `--promises DIR`: a DocKitty docs tree. Every `*.md` page whose frontmatter has
  `type: Promise` and a `promise:` block is a promise.
- `--scenarios DIR`: a tree of Gherkin `*.feature` files. A scenario cites the
  promises it checks with `@promise:<id>` tags, on the scenario or on its feature.
- `--release VERSION` (optional): the version under test. A manual result recorded
  for another version counts as stale.
- `--id-pattern REGEX` (optional): the shape every promise id must match. The
  default is `^PR-[A-Z0-9]+(?:-[A-Z0-9]+)*-\d{3,}$`, for example `PR-CLI-INIT-001`.

## Promise page frontmatter

```yaml
---
title: Init is idempotent
description: Running init again in an initialised project changes nothing and exits 0.
doc_status: active
updated: 2026-10-10
type: Promise
kind: Reference
moscow: { level: Must, rationale: Re-running setup must be safe. }
promise:
  id: PR-CLI-INIT-001
  status: ratified            # candidate | ratified | retired
  owner: product
  ratified_by: Jane Doe
  ratified_at: 2026-10-10
  surfaces: [docs/guides/install.md, "spec-kitty init --help"]
  # gap: { reason: no fixture yet, issue: "#123" }   # only while uncovered
---
```

## Scenario tags

| Tag | Meaning |
| --- | --- |
| `@promise:<id>` | The scenario checks this promise. Required on every gating scenario. |
| `@defect:<issue>` | A reproduction of a confirmed defect (QA_008). |
| `@manual` | Run by a person, not the runner. |
| `@manual-result:passed\|failed\|pending` | The last manual result. |
| `@manual-last-tested:<version>` | The version that result was recorded on. |
| `@manual-test-evidence:<link>` | Evidence for a passed manual result. |

The manual tags follow Serenity BDD's convention so the same files can feed a
living report.

## Findings

Every list is reported on every run, even when empty.

| Finding | Blocking | Meaning |
| --- | --- | --- |
| `untraced_scenarios` | yes | A scenario cites no promise. |
| `dangling_promise_refs` | yes | A scenario cites an id no page declares. |
| `cites_unratified_promise` | yes | A scenario cites a candidate or retired promise. |
| `uncovered_promises` | yes | A ratified promise has no scenario and no declared gap. |
| `malformed_promise_ids` / `duplicate_promise_ids` | yes | Ids do not match the pattern, or repeat. |
| `invalid_status` / `unratified_record` | yes | Bad status, or `ratified` without who and when. |
| `promise_pages_without_block` | yes | `type: Promise` with no `promise.id`. |
| `manual_findings` | yes | A manual scenario is incomplete, failed, pending or stale. |
| `declared_gaps` | no | Ratified promises with a declared gap. Listed, not failed. |

## Exit codes

`0` chain intact, `1` blocking findings, `2` cannot measure (a tree is missing,
holds no promises or no scenarios, or frontmatter does not parse).

## In CI

```bash
LINT="$(spec-kitty charter pack asset path qa-promise-trace-lint)"
python "$LINT" --promises docs --scenarios tests/scenarios \
  --release "$VERSION" --json --out reports/promise-trace.json
```

Run it before the gating scenario run, and let the release gate require both.
