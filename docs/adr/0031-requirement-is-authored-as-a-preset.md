---
title: "ADR-0031: Requirement is authored as a preset, and the required set is derived from it"
description: An org states what must be on the plate through a preset's must-have items; the service derives the required set; ADR-0043 answers its two open questions.
doc_status: active
updated: 2026-10-28T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0012-pack-mutability-policy
  - adr/0013-persistent-deactivation
  - adr/0020-offering-and-activations
  - adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - context/conceptual-model
audience:
  - profile: governance-lead
    guidance_text: Read this for how you declare what every project must activate, and the questions still open.
  - profile: pack-author
    guidance_text: Read this for the two jobs a preset has and how a required set is written once.
  - profile: core-maintainer
    guidance_text: Read this for what happens to required_* lists and the open compatibility question.
---

# ADR-0031: Requirement is authored as a preset, and the required set is derived from it

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Three independent levers", "Presets have two jobs" and "Requirement, removal and the operator"
in the [conceptual model working notes](/context/conceptual-model/)).

Supersedes in part [ADR-0012](/adr/0012-pack-mutability-policy/) decision 7: `required_*` is no
longer the authoring form of a requirement. What "required" means (presence only, deactivation
refused) is unchanged.

Two questions are **open** (see [Open questions](#open-questions)).
*(Answered by ADR-0043, 2026-10-28, proposed: see the amendment below.)*

### Amendment 2026-10-28 (#195): the open questions are answered

Answered by [ADR-0043](/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest/)
(Proposed, 2026-10-28) on the operator's rulings of 2026-10-27 and 2026-10-28 on
[#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195) and the mission's decisions
under `kitty-specs/pack-level-files-round-trip-01M60H1A/decisions/`:

- `DM-01M60H6Y9SG0B1J1D0F82CJ9GE`: a preset has a closed, sanitized shape, and each of its items
  may carry a must-have mark;
- `DM-01M60H7469CBSGA1YSYQN4K8CE`: a preset with at least one must-have item governs, and one with
  none is a convenience preset. Nothing else says so;
- `DM-01M60H79MAHAVFSPCBHDXFXW3Y`: a preset has no `required_<plural>` lists. Migrating
  `org-charter.yaml`'s lists, then refusing them, is #206.

The decision text below is unchanged. The points this note answers are annotated where they stand:
decision 6 and the two open questions. The answers are in force for the code #195 ships, which
implements them. ADR-0043 itself is Proposed, pending the operator's acceptance, which the pull
request for #195 asks for.

## Context

Today an org declares requirements as `required_<plural>` lists in `org-charter.yaml`. Presets
are a separate, pack-shipped convenience: a one-shot selection written into a project's active
charter. ADR-0012 decision 7 defined required as presence only, and refused its deactivation.

The concept session settled three independent levers (requirement, integrity, and the project's
own selection) and two jobs for a preset:

- **Governor**: an org states what must be on the plate.
- **Convenience**: an author offers sane defaults ("don't want to customize? take these").

It ruled that requirement is declared through a preset that includes the non-optional
selections, written one way.

## Decision

1. **Requirement is authored as a preset.** A preset may mark items as must-have. Those items
   are the required set.
2. **The service derives the required set from the preset.** The required list is computed, not
   authored a second time.
3. **What required means is unchanged.** A required artifact is always active; deactivating it
   is refused locally in open-core, naming the requiring pack (ADR-0012 decision 7, ADR-0013
   decision 5). Its content is as open as its level says: requirement and integrity stay
   independent.
4. **Prohibition is not a separate mechanism.** It is a requirement phrased inversely, for
   example a required, `locked` directive that says what to avoid.
5. **Cross-repository policing is out of scope.** The service declares and honours requirement
   per project; checking that every repository of an org complies belongs to org CI or a
   commercial package.
6. **Representation is a candidate only.** How a must-have item is marked in a preset file is
   left to a later record. No contract path changes (C-003).
   *(Answered by ADR-0043, 2026-10-28: an item is marked must-have with `must_have: true`, per
   item. There is no `false` spelling. The required set is derived from the marks. See the
   amendment under Status.)*

## Open questions

- **Compatibility reading of `required_*`.** Whether the service keeps reading existing
  `required_<plural>` lists (for example as a preset it derives from them), for how long, and
  how a pack that has both is treated. Not decided.
  *(Answered by ADR-0043, 2026-10-28: a preset carries no `required_*` key; its closed schema
  refuses one. Migrating `org-charter.yaml`'s `required_<plural>` lists into presets, and then
  refusing them, is [#206](https://github.com/spec-kitty/spec-kitty-charter/issues/206).)*
- **Telling a governing preset from a convenience preset.** How the service, a project and a
  reader tell a preset that governs (must-have items) from one that only offers defaults: a
  marker on the preset, on each item, or the presence of any must-have item. Not decided.
  *(Answered by ADR-0043, 2026-10-28: a marker on each item. A preset with at least one must-have
  item governs; one with none is a convenience preset. "Governing" is derived, never declared.)*

## Consequences

### Positive

- One idea, written one way: an org's "must take" and its suggested selection are the same
  kind of thing.
- A reader finds an org's requirements where its selections already are.

### Negative

- Existing `required_*` lists need a migration or a compatibility reading (open question).
- Presets gain a second meaning; a reader must tell the two apart (open question).

### Risks

- Applying a convenience preset could be mistaken for accepting a requirement. Mitigation: the
  second open question is answered before the representation is built.

## Alternatives considered

### Keep `required_*` as the authoring form

Not chosen: it states the same selection twice, once as a preset and once as a list.

### A separate requirement file

Not chosen: it adds a third place an org's intent lives.

## References

- [Conceptual model working notes](/context/conceptual-model/)
- [ADR-0012](/adr/0012-pack-mutability-policy/), [ADR-0013](/adr/0013-persistent-deactivation/),
  [ADR-0020](/adr/0020-offering-and-activations/)
- [ADR-0043](/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest/): the answers to
  the open questions (amendment 2026-10-28)
- Core ADR [`4.x/2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md)
