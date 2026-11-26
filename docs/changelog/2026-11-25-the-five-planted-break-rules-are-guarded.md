---
okf_version: "0.2"
title: The five open planted-break rules are guarded
description: Deleting the template-path containment, a null refusal's field name or any mix-in's runtime hints, or moving the ULID or body-size bound by one, now fails a test.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/codebase-health-2026-11-25
  - plans/engineering-notes/codebase-assessment-2026-11-20/test-quality
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to see which containment and boundary rules a unit test now guards, and what each test plants.
---

# 2026-11-25 — The five open planted-break rules are guarded

**The five rules no longer break with every gate green** (action 5 of the 2026-11-25
[codebase health summary](/plans/codebase-health-2026-11-25/), #658). The 2026-11-12 assessment
planted six breaks that no test caught; one was fixed on 2026-11-15, and the other five each have a
test now. Each test failed with its planted break in place before it was committed.

## What changed

| Gap of 2026-11-12 | Rule | Test | Planted break that now fails |
|---|---|---|---|
| 1 | A template path that starts with `templates/` still may not climb out | `MissionValuesTest` | Drop the containment check from the template rule |
| 2 | A relationship's null refusal names the field, from the constructor and from `Relationship.of` | `RelationshipTest` | Drop the name from the message, or name the wrong field |
| 3 | An activation id is minted at the last 48-bit millisecond | `ActivationIdMintTest` | Refuse the bound itself (`>=`) |
| 5 | The body size cap accepts `Integer.MAX_VALUE - 8` and `1`, and refuses one more and one less | `GuardedHttpsExchangeTest` | Refuse the cap itself, or move it by one |
| 6 | The mission JSON configuration registers and imports its runtime hints | `MixInHintsGateTest` | Delete the hint, or the `@ImportRuntimeHints` |

Gap 6 had the same shape in four more configurations (`RoutingJson`, `GlossaryJson`, `EffectiveJson`
and `PresetJson`), which had no test of their runtime hints. A gate test in `deployable`,
`MixInHintsGateTest`, now covers all ten configurations that declare a mix-in, and any added later: it finds every abstract nested class that carries a Jackson annotation,
on itself or on a member, and the class that declares the mix-in must import runtime hints that
register it. Its known-bad controls fail on a missing import and on a forgotten hint.

Deleting the ULID time bound or the body size cap outright already failed a test; what was unguarded
was the bound moved by one.

## What did not change

No main code changed: each rule held, and only its test was missing. The template path rule is
still guarded upstream by the mission type schema too.
