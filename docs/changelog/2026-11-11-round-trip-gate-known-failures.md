---
okf_version: "0.2"
title: The whole-pack round trip writes policy.yaml, and the project example carries a cross-level overrides edge
description: The round-trip gate writes and compares a pack's policy.yaml, so its known-failures list is empty, and the project example carries a cross-level overrides edge.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0063-pack-mutability-policy-file
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add an entry to the whole-pack gate's expected-red.yaml, or when a qualified edge is refused as outside a depends_on closure.
---

# 2026-11-11 — The round-trip gate has no known failures left

**The whole-pack round trip now writes `policy.yaml`**
([#476](https://github.com/spec-kitty/spec-kitty-charter/issues/476)). The gate writes a pack back through
`DomainRepositoryService`, which could read a pack's mutability policy (ADR-0063) but not write one. The built-in
example pack holds a policy that locks its fallback profile, so the gate listed `policy` as a known failure.

- **What changed.** `PolicyYaml.write` words a `MutabilityPolicySource` as a policy file: `schema_version`, then
  `kind_defaults` in kind order and `artifacts` by URN, each left out when it is empty. The gate writes the policy the
  source pack declares, reads the written file back through the `policy` schema, and compares the two files by meaning
  (ADR-0036). A control test drops one locked entry before the write and turns the file red. The `policy`
  entry is gone from `whole-pack/expected-red.yaml`, and the list is empty.
- **What did not change.** The production write path still copies the policy file as it was read
  (`WholePackStore`). The new write is the gate's seam, like the preset and pack-document writes.

**A qualified `overrides` edge onto a dependency's skill composes**
([#479](https://github.com/spec-kitty/spec-kitty-charter/issues/479)). The project example pack's edge from
`skill:project-triage` onto `org-base::skill:issue-triage` was taken out after a refusal: "org-base is outside its
depends_on closure". The refusal does not reproduce: with the edge in place, all three example packs compose. The
refusal came from a compose test that removes the project pack's `depends_on` on purpose, and with it removed, the
refusal is right. The edge is back in `project/project.graph.yaml`. That test now removes the edge too, and a new
test pins the refusal when the dependency is gone.
