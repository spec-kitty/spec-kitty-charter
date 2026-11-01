---
okf_version: "0.2"
title: The changelog and ADR indexes are generated, so no pull request edits them
description: The changelog list and the ADR table are built from each entry's frontmatter when the site is built; a pull request adds its own file and leaves the shared hubs alone.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0047-generated-indexes-and-merge-queue
  - guides/docsite
  - guides/contributing
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a changelog entry or an ADR.
---

# 2026-11-01 — The changelog and ADR indexes are generated, so no pull request edits them

Under [ADR-0047](/adr/0047-generated-indexes-and-merge-queue/).

**Before:** every pull request added its line to `docs/changelog/README.md` or a row to
`docs/adr/README.md`. With several sessions landing at once, each merge put the other open pull
requests into conflict over the same lines. The two hubs took 83 edits on `main` in 14 days.

**Now:**

- `scripts/docs/generate_indexes.py` builds both lists from the entries' frontmatter, and the site
  build runs it.
  - A changelog entry gives its date (from the filename) and its `title`.
  - An ADR gives its `title`, `decision_status` and `decision_date`. All 46 ADRs carry these two
    keys, copied verbatim from the table they replace.
- Each hub commits an empty generated block. `check_docs.py` checks the hub as it regenerates. It
  fails on an entry the index cannot list, and never compares the committed block.
- The hubs now show the files' wording, which had drifted for six ADR titles and two changelog
  titles. Entries from the same day are listed by filename.

**What to do:** add your entry file, and nothing else. For an ADR, set `decision_status` and
`decision_date` in its frontmatter. This entry is the first to land without touching
`docs/changelog/README.md`.
