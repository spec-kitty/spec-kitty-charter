---
okf_version: "0.2"
title: Rules for filing and closing issues
description: Squad minors are folded or dropped instead of filed, closeouts close what they delivered, follow-ups expire, and pack and core work is tracked where it lands.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - development/issue-tracker
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you file an issue from a Mission, an Op or a review squad.
---

# 2026-11-11 — Rules for filing and closing issues

**The issue tracker gets rules for filing and closing.** The 2026-11-11 triage found 191 open
issues, of which 21 were already delivered and 64 had been filed from Mission closeouts, landings
and squad deferrals. Issues were opened faster than they were closed every week except a triage
week. The new page [Keep the issue tracker small](/development/issue-tracker/) sets the rules, and
`charter.md` ("How work lands") and `AGENTS.md` point to it:

- A squad finding below MEDIUM is folded, recorded in the pull request, or dropped, not filed.
- Every closeout and landing closes the issues it delivered and ticks the parent epic.
- An auto-filed follow-up carries the new `follow-up` label and expires after 21 days without a
  milestone or assignee.
- Pack content is filed in `spec-kitty-open-packs`; core work is not filed here until
  incorporation.
- An epic is broken into child issues when it is scheduled, not before.
- Each fact has one place: the native issue type carries the kind, one `priority:*` label the
  priority, a sub-issue link the parent and the milestone the schedule. The `type:*`,
  `enhancement` and `bug` labels are no longer used.
