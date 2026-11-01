---
title: "ADR-0047: No pull request edits a shared index; main lands through a merge queue"
description: The changelog and ADR indexes are generated from frontmatter, contract changes add a changelog fragment, and main takes pull requests through a merge queue.
doc_status: active
updated: 2026-11-01T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-01'
type: ADR
kind: ADR
related:
  - adr/0022-contract-versions-start-at-0-x
  - adr/0023-ci-gate-required-check
  - guides/contributing
  - guides/docsite
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a changelog entry, an ADR or a contract change, or touch a workflow that feeds the CI gate.
---

# ADR-0047: No pull request edits a shared index; main lands through a merge queue

## Status

**Accepted**: 2026-11-01.

- **Decider:** Stijn Dejongh (operator).
- **The remedy.** The operator approved the remedy spec-kitty/spec-kitty is adopting: no hand-edited
  shared files, and readiness for a merge queue.
- **The rulings.** On 2026-11-01 the operator ruled on the decisions in the research note
  `kitty-ops/research/parallel-load-remedies.md`, all as recommended there:
  - the indexes are generated, and no bot commits them;
  - contract changes add fragments;
  - only `ci.yml` runs on the merge queue, with the queue settings below;
  - a `pr:pause-ci` guard stops CI on a parked pull request;
  - stale docsite runs are cancelled.
- **Sequence.** The changes land in the order 1, 3, 2.
- **The ADR keys.** The proposal named the two keys `status` and `date`. They were built as
  `decision_status` and `decision_date`, so they cannot collide with a `status` or `date` meaning in
  OKF or doc-kitty, and the operator accepted those names on 2026-11-01.

## Context

- Several cloud sessions open pull requests at once. Whenever one merges, each pull request that edited
  the same shared file falls into conflict and has to be rebased by hand.
- Three files took most of those edits on `main` in the last 14 days:
  - `docs/changelog/README.md` (45 edits). Every pull request added its line at the top, although the
    entries themselves are already one file per change.
  - `docs/adr/README.md` (38 edits). Every ADR added a table row.
  - `contracts/charter/CHANGELOG.md` (22 edits). Contract changes insert entries into one
    `0.1.0-SNAPSHOT` section; #303 and #290 conflicted there.
- `CI gate / gate` is the one required check ([ADR-0023](/adr/0023-ci-gate-required-check/)). Pull
  requests merge on green with `gh pr merge --rebase --auto`, so each one is tested against a `main`
  that may have moved since.
- GitHub Actions stalled for about an hour on 2026-10-31, with 18 runs queued and none running. Nothing
  stops CI on a pull request that is parked.

## Decision

1. **The indexes are generated, never hand-kept.**
   - `scripts/docs/generate_indexes.py` builds the changelog list and the ADR table from each entry's
     frontmatter:
     - a changelog entry gives its date (from the filename) and its `title`;
     - an ADR gives its `title`, `decision_status` and `decision_date`.
   - Each hub keeps its prose and a generated block between two markers. The block is committed
     empty. The site build fills it before `astro build`.
   - `check_docs.py` checks each hub as it regenerates, in memory. An entry the generator cannot list,
     or a hub without its block, fails the check. What the committed block holds is never compared.
   - The ordering rules are fixed:
     - changelog entries are listed newest date first, then by filename;
     - ADRs are listed in number order.
2. **A contract change adds a fragment.** It does not edit `contracts/charter/CHANGELOG.md`.
   - The `## <version>` heading and its `### Breaking` marker stay in the file, so the release gate
     reads the same text.
   - The fragments are assembled into the file in the release pull request.
   - The detail lands with that change.
3. **`main` takes pull requests through a merge queue.**
   - The required check stays `CI gate / gate`.
   - `ci.yml`, the workflow behind that check, also runs on `merge_group`, and on a queue entry it diffs
     `merge_group.base_sha` against `head_sha`. The workflows it calls inherit the event.
   - Workflows that produce no required check do not run on `merge_group`.
   - A maintainer can label a pull request `pr:pause-ci` to stop its CI during a pile-up.
   - The queue settings are set by the operator on `main`:
     - merge method rebase;
     - build concurrency 3;
     - a group of 1 to 4 pull requests, waiting 5 minutes to fill;
     - a 60-minute status-check timeout;
     - only pull requests that do not fail are merged.
   - On a paused pull request the gate fails. Label events about any other label do not cancel a
     running check.
   - On a pull request, the docsite workflow cancels its stale runs and does not build the service a
     second time.

## Consequences

### Positive

- A pull request that adds a changelog entry or an ADR touches only its own file, so merging it puts no
  other pull request into conflict over an index.
- The indexes cannot drift from the entries. Six ADR titles and two changelog titles had drifted; the
  generated hubs carry the files' wording.
- The queue tests each pull request against the `main` it will land on, not the one it branched from.

### Negative

- On GitHub, the committed hubs show no list. The published site shows the list, and the directory
  listing shows the files in date or number order.
- An ADR carries its status and date twice: once in the frontmatter, for the index, and once under
  `## Status`, for the reader. A status change edits both.
- The changelog loses its hand-chosen order within one day.

### Risks

- A tool that reads the committed hubs, rather than the built site, sees an empty list. None does
  today: the site build regenerates the hubs, and `check_docs.py` regenerates them in memory.

## Alternatives considered

### Generate the indexes in the doc-kitty site build

- doc-kitty's `generate-adr-index.mjs` is not part of this repository.
- The toolkit needs a GitHub Packages token and the network, so neither `check_docs.py` nor a
  contributor could run it offline.

### Keep the indexes committed and regenerate them after merge

- Regenerating after every merge needs either a bot push to `main`, which bypasses the ruleset, or a bot
  pull request through the queue.
- Either way it is one more commit per merge, for a list the site already builds.

### Keep hand-kept indexes and rebase on conflict

- That is the cost this record removes. Its price grows with the number of parallel sessions.

## References

- `kitty-ops/research/parallel-load-remedies.md`: the research and the decisions put to the operator.
- [ADR-0023](/adr/0023-ci-gate-required-check/): the one required check.
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/): the contract's versioning and its CHANGELOG.
