---
okf_version: "0.2"
title: Keep the issue tracker small
description: When a Mission, an Op or a review squad files an issue, when it folds or drops a finding instead, how a closeout closes what it delivered, and where pack and core work is tracked.
doc_status: draft
updated: 2026-11-11T00:00:00Z
type: Guide
kind: How-To
related:
  - development/cloud-session-setup
  - plans/milestones
audience:
  - profile: charter-service-contributor
    guidance_text: Follow this before you file an issue from a Mission, an Op, a landing or a review squad, and at every Mission closeout.
---

# Keep the issue tracker small

This page is for contributors and agents who file or close issues in this repository. It holds the
rules the operator approved after the 2026-11-11 triage, which found 191 open issues: 21 already
delivered, 14 overtaken by later decisions, 20 that were core work, and 64 filed from Mission
closeouts, landings and squad deferrals. Issues were opened at 45 to 55 a week and closed at 18 to
45, and only a triage week closed more than it opened.

## Rule 1: fold or drop a squad minor; do not file it

A review squad's finding below MEDIUM is not filed as an issue. Do one of these instead:

- **Fold** it into the pull request under review, when it is in that pull request's scope.
- **Record** it in the pull request body, under "Not done", with one line of reason.
- **Drop** it, when it is cosmetic.

File a separate issue only when the finding is a correctness, data or security risk, or when the
operator asks for it. Such an issue carries the `follow-up` label (Rule 3).

The boy-scout rule (DIRECTIVE_025) still holds for defects: a failing test, a wrong answer or a
broken reference outside the Mission's domain is filed as an issue. Tidy-ups outside the domain
are not filed.

## Rule 2: close what you delivered, in the same closeout

Before a Mission's closeout pull request merges, and in every landing pass:

1. List each issue the Mission touched: the issues in its spec, the ones the pull requests name,
   and the follow-ups filed during it.
2. Write `Closes #N` for an issue the pull request fully delivers. Use `Refs #N` only when part of
   the issue is left, and say on the issue which part.
3. Close a delivered issue that no pull request closes, with `completed` and a comment that names
   the pull request and the file or scenario that shows the behavior.
4. Tick the parent epic, and close it when its last child closes.
5. Close a follow-up that the merged work answers.

## Rule 3: an auto-filed follow-up expires

An issue filed by a Mission, an Op, a landing or a squad carries the `follow-up` label, priority
`priority:P3` and no milestone. When it has had no milestone and no assignee for 21 days, a triage
pass closes it as `not_planned` with a one-line comment. A follow-up the operator schedules loses
the label and gets a milestone.

## Rule 4: pack content goes to spec-kitty-open-packs

A fix that lands in pack YAML, doctrine wording, a pack manifest or open-packs CI is filed in
`spec-kitty/spec-kitty-open-packs`, not here. A session without write access there writes the
draft into its Mission research directory with an `open-packs#TBD-<slug>` placeholder, as
`charter.md` describes, and the orchestrator files it. File an issue here only when the service or
the contract must change too.

## Rule 5: core work is not tracked here until incorporation

Until the service is incorporated into core, core changes are planned at incorporation
(`charter.md`, "Designing during the rewrite"). Record a core consequence in the design record,
[pack-ecosystem work breakdown](/plans/pack-ecosystem-work-breakdown/), not as an issue. The core
issues that already exist are parked under epic #361 in the Post-cutover milestone.

## Rule 6: break an epic down when it is scheduled

An epic that is not yet in a slice or a delivery milestone keeps its breakdown as a checklist in
its body or in a plan page. File its children when it is scheduled. A triage that re-scopes an
issue edits that issue, or closes it, in the same pass; it does not leave the re-scope only in a
plan page.

## Rule 7: one place for each fact

- **Kind:** the native issue type (`Bug`, `Feature` or `Task`) is the only kind carrier. The
  `type:task`, `type:epic`, `enhancement` and `bug` labels are retired.
- **Priority:** `priority:P0` to `priority:P3`, one per issue.
- **Parent:** a native sub-issue link to an open epic, never to a closed one.
- **Milestone:** every open issue has one; see [Milestones](/plans/milestones/).
