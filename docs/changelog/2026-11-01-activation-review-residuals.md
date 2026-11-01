---
okf_version: "0.2"
title: A 204 is declared only on a DELETE, and the activation corpus pins its reasons
description: A lint rule lets only a DELETE declare 204, and each invalid activation corpus document fails for exactly the reasons it is given.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0020-offering-and-activations
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new lint rule on 204 and for the reasons file of the activation corpus.
---

# 2026-11-01 — A 204 is declared only on a DELETE, and the activation corpus pins its reasons

Two residuals of the activation-set review are closed
([#295](https://github.com/spec-kitty/spec-kitty-charter/issues/295)):

- **The lint rule `no-content-only-on-delete`.** Only a `delete` operation may declare a `204`. The
  rule `no-content-has-no-body` held a `204` to having no body, but allowed it on any method. The
  contract's one `204`, on `deleteActivation`, already complies.
- **`contracts/offering/corpus/ActivationConfig/invalid-reasons.json`.** It gives each invalid
  activation document the keyword, location and member it fails on. Both corpus tests now check that
  the document fails for exactly those reasons, not only that it fails.

The third item of #295, the 64 MiB snakeyaml limit, was resolved by #328, which removed the limit.
