---
title: "ADR-0071: Procedures are a served read kind"
description: "The service reads procedures by pack and under an activation, on the seam of the kinds already served, with artifact_not_found and a revision ETag."
doc_status: active
updated: 2026-11-11T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-11'
type: ADR
kind: ADR
related:
  - adr/0030-removal-is-distinct-from-deactivation
  - adr/0064-graph-documents-are-the-only-source-of-relationships
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before serving another kernel kind through the generic artifact reads.
---

# ADR-0071: Procedures are a served read kind

## Status

**Accepted**, 2026-11-11 (operator, on PR #486), on the pull request that closes
[#484](https://github.com/spec-kitty/spec-kitty-charter/issues/484).

## Context

`procedure` is an `ActivatableKind` and a `CascadeKind`, so an activation can select procedures, but the artifact
reads refused the kind with `kind_not_found` (#484). The domain entity (`Procedure`), its YAML binding
(`ProcedureBinding`) and the pack schema already existed; only the read path and the contract were missing. A live
run on 2026-11-11 asked whether the action context drops procedures, styleguides and toolguides. A contract scenario
written for this ADR showed that it does not: the walk is kind-neutral and lists every selected, scoped artifact. The
gap was elsewhere. A procedure entry had no links, because the context links only the listed kinds, and nobody could
read the procedure it named.

## Decision

1. **`procedure` joins the served kinds** of `ArtifactReadService.SERVED`, after `toolguide` and before
   `agent_profile`. Being listed gives it the generic list and its filter, the item read, the references read,
   the removal (`DELETE /packs/{packId}/artifacts/procedure/{artifactId}`), the activation reads and listing, and the links of graph and context entries, all through the existing
   gates. No per-kind route is added.
2. **The read follows the toolguide seam**: a `ProcedureRepository` port that `DomainRepositoryService` implements
   over `ProcedureBinding`, and a `ProcedureReadService` that reads in one `ConsistentRead`.
3. **Contract shape**: the `Procedure` schema carries the pack file's fields in camel case. It uses `ProcedureStep`
   (title, description, role, onSuccess, onFailure) and `ProcedureAntiPattern`, and every optional field is sent as
   `null` or `[]`, as `Tactic` does. What a procedure references stays in the graph documents and is read through
   `/references` (ADR-0064). The schema has no reference member.
4. **Absence is `artifact_not_found`.** The per-kind not-found codes are kept for the five kinds that have them; a kind
   served after them uses the generic code, as `agent_profile` does. No refusal code is added.
5. **The pack item carries the pack revision as its `ETag`**, although a procedure has no `PUT` yet. A tactic has none *(superseded by the Amendment 2026-11-11 below, #487)*.
   The revision is a correct validator of the read, it is the `If-Match` of a removal, and a later procedure write will
   need it as well.

   **Amendment 2026-11-11** ([#487](https://github.com/spec-kitty/spec-kitty-charter/issues/487)). The tactic, the
   mission step contract and the mission-type family (the mission type, its steps and its skeletons) now carry the
   pack revision as their `ETag` too, so every pack read of one thing does and the asymmetry is gone. For the
   mission-type family the tag is a concurrency token naming the pack, not a cache validator: the answer can carry
   content resolved from dependency packs that the revision does not cover, so no pack read takes `If-None-Match`.

6. **An action context or graph entry of a procedure links its reads** (`effective-artifact`, and `artifact` where the
   entry names its pack), as entries of every listed kind do. It is a response addition for clients that already
   received the entry without links.

## Consequences

- A client can read every procedure an activation selects, and the action context links each one.
- #350 (an `action:` URN in a procedure reference id) no longer applies to the reader: the procedure schema has no
  `Reference` member since ADR-0064. Operator ruling, 2026-11-11: #350 is redirected to the graph-document edges of a
  procedure, and stays open.
- Operator ruling, 2026-11-11: a procedure can be removed. Listing it among the served kinds enables the generic
  removal (ADR-0030), with the usual referrer, closure and `If-Match` checks; the ruling accepts that.
- Other kernel kinds (`template`, `asset` and the rest) stay unserved and keep `kind_not_found`.
