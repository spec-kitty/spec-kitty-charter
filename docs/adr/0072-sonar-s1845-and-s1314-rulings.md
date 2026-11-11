---
title: "ADR-0072: Sonar S1845 and S1314 rulings"
description: "A constant that clashes with a member is renamed, never the component or method; octal literals are written with an explicit 0_ prefix."
doc_status: active
updated: 2026-11-11T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-11'
type: ADR
kind: ADR
related:
  - adr/0062-nightly-mutation-floor
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before renaming a constant that Sonar flags as S1845, or writing a permission-bit literal.
---

# ADR-0072: Sonar S1845 and S1314 rulings

## Status

**Accepted**, 2026-11-11 (operator, on PR #506), for [#465](https://github.com/spec-kitty/spec-kitty-charter/issues/465).

## Context

The SonarCloud nightly of 2026-11-10 raised S1845 twelve times (a constant and a member differ only in case, such as
`TOKEN` and `token`, or `PACKS` and `packs()`) and S1314 three times (octal literals for zip permission bits in
`ZipReader`). #465 asked for a ruling: rename, or disable the rules in the quality profile with a reason. The
S1845 pairs looked like a deliberate pattern, which is why the issue did not rewrite them unasked.

## Decision

1. **S1845: rename the constant, never the component or method.** The member is part of the API (a record component,
   an accessor, a method), so the constant gives way. A pattern constant becomes `*_SHAPE`, a wire token `*_TOKEN`
   and a minimum `MINIMUM_VERSION`. The rule stays on in the quality profile.
2. **S1314: write an octal literal with an explicit `0_` prefix.** The value is unchanged, and the prefix marks the
   octal as intended. The comment at `ZipReader.java:33` points here.
3. **Eight sites are renamed in PR #506:** `EffectiveTag.TOKEN`, `Revision.TOKEN`, `RefusalViolation.CODE`,
   `GuardImplementation.SPELLING`, `MissionStep.ROLE` and `ProcedureStep.ROLE` become `*_SHAPE`;
   `CascadeScope.ALL` becomes `ALL_TOKEN`; `GitVersionCheck.MINIMUM` becomes `MINIMUM_VERSION`.
4. **Five sites are deferred** so as not to collide with running slices: `PackFileSet.TEMPLATES`,
   `ConfigRoot.PACKS`, `ConfigRoot.ACTIVATIONS` and `Preset.ID` (slice B), and `StepEnhancement.ID` (slice C).
   They follow this rule when those slices land, and #465 stays open until then.

## Consequences

- The rename is not a deprecation: the old constant names are gone, with no alias beside the new ones.
- S9428 on `Fingerprint.java` is not part of this ruling; it moved to #463 ([#494](https://github.com/spec-kitty/spec-kitty-charter/issues/494)).
