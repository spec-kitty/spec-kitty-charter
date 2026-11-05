---
okf_version: "0.2"
title: "The Checkstyle exception register is checked in every build"
description: "checkCheckstyleRegister fails a build when a register entry lacks a field, is past its sunset, or the Size header miscounts. It runs no Checkstyle."
doc_status: active
updated: 2026-11-06T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the exception register check fails on, and what it leaves alone.
---

# 2026-11-06 — The Checkstyle exception register is checked in every build

**The exception register is checked.** `checkCheckstyleRegister`, part of `check`, fails when an entry lacks a
comment field, its sunset has passed, or the `Size:` header miscounts. An entry is a `<suppress>` or
`<suppress-xpath>` element, or a comment with a `rule:` line that records an exception to another gate. A commented-out
`<suppress>` is not an entry. It runs no Checkstyle, and an empty register passes.
