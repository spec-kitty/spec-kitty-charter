---
title: "ADR-0013: Deactivation persists in one activation ledger, and nothing silently reinstalls it"
description: A deactivated charter element stays gone; one activation ledger holds allow and deny state, and cascade, upgrade and presets skip what a user excluded.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - architecture/resolution-model
  - architecture/logical-model
  - architecture/strangler-transition
audience:
  - profile: governance-lead
    guidance_text: Read this for what deactivation guarantees, and how required and locked artifacts interact with it.
  - profile: core-maintainer
    guidance_text: Read this for the requests to core; activation state is core's, so most of this lands there.
  - profile: charter-service-contributor
    guidance_text: Read this for the filtering the service owns once reads go through the API.
---

# ADR-0013: Deactivation persists in one activation ledger, and nothing silently reinstalls it

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-19). Decider: Stijn Dejongh (operator). Taken on
2026-10-19 and ratified on 2026-10-27, this decision is still revisited as the system evolves,
on the triggers under [Revisit when](#revisit-when). Tracked by epic
[#56](https://github.com/spec-kitty/spec-kitty-charter/issues/56) and its steps #62 to #67.

Activation state belongs to core: core ADR
[`4.x/2026-10-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-16-1-extract-charter-offering-first-activation-rework-post-ga.md)
keeps `charter.activation` in core until its post-GA rework. Most of this record is therefore
a request to core; the part this repository owns is called out.

Superseded in part by [ADR-0033](/adr/0033-local-active-charter-is-source-of-truth/) (2026-10-26): the project's local `activation.yaml` is the source of truth, written through to the service, which holds a copy (decision 1).

## Context

A user can deactivate a charter element, but nothing records the choice durably. Today:

- **No exclusion exists.** Deactivation only removes an id from the `activated_<kind>`
  allow-list. When that key is absent, which means "all active", deactivation raises
  `NoActivationRestrictionsError` (`src/charter/activation/activation_engine.py:366-429`).
- **Several paths reinstall a deactivated element.** "Absent means all" brings it back. The
  one-time upgrade reset turns an allow-list equal to a released default back into "absent"
  (`src/specify_cli/upgrade/migrations/_charter_pack_cutover_resets.py:12-20`). The cascade walk
  re-adds it and keeps no memory of exclusions (`cascade.py:376-410`). Org `required_*` entries
  are merged in silently (`interview.py:623-635`, `org_charter.py:681`).
- **Activation state has several homes.** Core already treats the `activated_*` keys as the
  only activation source: `answers.selected_*` is retired as a source
  (`compiler.py:422-430`) and `references.yaml` is retired (`compiler.py:745-760`). The live
  keys sit in `.kittify/config.yaml` or in the `charter.yaml` its pointer names, with one
  resolver choosing between them (`pack_manager.py:577-602`). The interview still writes
  `selected_*`, which can disagree with the store.
- **Cascade already reports skips.** `CascadeActivationResult` has `skipped_by_scope`
  (`cascade.py:346-375`), so another bucket fits the pattern.
- **Descriptors are closed.** `CharterYaml` rejects unknown keys (`schemas.py:383`), and core
  omits optional keys while they are empty (`schemas.py:410`).
- **Contract 1.0 carries no activation.** Its action-context operation answers from the merged
  offering without activation filtering.

## Decision

1. **One activation ledger is the single authority.** A new activation ledger file in the
   project's charter directory holds both the allow state and the deny state. Every other
   surface (`config.yaml` and `charter.yaml` activation keys, `answers.selected_*`, the compiled
   `graph.yaml` and catalog) is derived from it or checked against it, and doctor reports any
   surface that disagrees. A migration moves the existing `activated_*` keys into the ledger.
   *Superseded in part by ADR-0033: the local file is the source of truth and a write-through cache; the service-side copy answers activation and resolution.*
2. **Exclusions are a deny-list.** The ledger holds `deactivated_<kind>` lists. The effective
   set of a kind is its allow-list, or every element when the allow-list is absent, minus its
   deny-list — **except that a required or locked artifact ([ADR-0012](/adr/0012-pack-mutability-policy/))
   is never subtracted**, so an element deactivated while `open` and later promoted to required or
   locked wins over its stale deny-list entry and is active again. "Absent means all" stays, so new
   built-ins still arrive. A deny-list is omitted
   while it is empty. Migrations and presets never remove a deny-list entry, so the upgrade
   reset (#66) reduces to a gate test that proves it.
3. **Only the user's explicit choice changes a deny-list.** A user deactivation adds the named
   id. `charter activate <kind> <id>` removes it. Elements that a cascade deactivates along with
   the named id are not added.
4. **Cascade skips and reports.** An activation walk that reaches an excluded element skips it
   and lists it in a "skipped, deactivated" report beside `skipped_by_scope`. When it was
   reached over a `requires` edge, the report flags it as a requirement the parent no longer
   has. `--include-deactivated` takes such elements back in and clears their exclusions.
5. **A required artifact cannot be deactivated.** Deactivating an artifact an org requires, or
   one its owner locked, is refused, and the error names the owning or requiring pack
   ([ADR-0012](/adr/0012-pack-mutability-policy/)). No exclusion is recorded.
6. **The work is split by owner.**
   - **Core, now:** the ledger, the deny-list, cascade, the migrations and the refusal (#62,
     #63, #64, #66, #67). Activation is core's, and users hit the defect today.
   - **This repository, once reads go through the API:** projection and resolution filtering
     (#65). The offering does not serve an excluded element in context, resolution or the
     skill and command projections, for any origin, built-in included.
   - The conformance corpus is regenerated with the fixed answers, so the Java offering is held
     to them.
7. **Activation reaches the API additively.** Activation, exclusions and the skipped report
   arrive as a new contract set of additional operations (ADR-0009). Its first version carries
   exclusions and the skipped report from the start; no 1.0 response changes.

## Consequences

### Positive

- A deactivation survives upgrade, seeding, the interview, cascade and regeneration.
- One file answers "what is active here", and doctor proves the derived surfaces agree.
- The user-visible defect is fixed where it lives, without waiting for the cutover.

### Negative

- A new file and a migration in every project.
- An older CLI cannot read the ledger. A project that uses it needs a CLI that knows it.
- The fix changes Python read answers, so the conformance corpus must be regenerated, and the
  Java offering must match the new answers.

### Risks

- If the ledger migration and a user's hand edits of `config.yaml` race, the doctor check is
  the only guard; the migration must refuse to run on a store doctor reports as divergent.
- A deny-list entry for an element a pack later removes is dead weight. Doctor should report it.

## Alternatives considered

### Keep the existing config store as the authority

Core already uses the `activated_*` keys as the only source, so this needed the least change.
Not chosen: the operator wants activation and exclusion state in one dedicated place that can
later be handed to the service whole.

### An always-explicit allow-list, or a tri-state per element

Dropping "absent means all" reintroduces the missing-newer-built-ins defect (core #5323), and
cascade cannot tell "excluded" from "never chosen". A tri-state per element changes every
reader for little gain over a deny-list.

### Re-activate on cascade with a warning, or prompt

Re-activating discards the user's choice whenever a parent cascades. A prompt breaks
non-interactive and agent use, which is most use.

### Fix only in the Java offering after the cutover

Activation stays in core after the cutover, so most of the fix is core's anyway; waiting only
delays it.

## Revisit when

- The post-GA activation rework moves activation out of core; the ledger is then a candidate
  to be served through the charter API.
- Users need a temporary exclusion (until a date or a release), which a deny-list cannot express.
- Doctor reports frequent divergence between the ledger and a derived surface; then the derived
  surface should stop being written at all.

## Requests to core

- **Adopt the ledger and the deny-list** (#62, #63) with the migration from the `activated_*`
  keys, and make doctor report divergent surfaces.
- **Cascade** (#64): add the "skipped, deactivated" bucket, the `requires` flag and
  `--include-deactivated`; record exclusions only for user-named ids.
- **Upgrade** (#66): a gate test proving no migration or preset removes a deny-list entry. A
  shipped migration stays unedited; any fix is a new migration.
- **Refusal** (#67): refuse deactivation of a required or locked artifact, naming the org pack,
  in place of the silent union of `required_*`.
- **Corpus:** regenerate the conformance corpus fixtures once these land.

## References

- Epic [#56](https://github.com/spec-kitty/spec-kitty-charter/issues/56), steps #62 to #67;
  core [#2526](https://github.com/spec-kitty/spec-kitty/issues/2526).
- [ADR-0012](/adr/0012-pack-mutability-policy/) (mutability levels and `required_*`).
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) (contract 1.0 reservations and additive contract sets).
- [Strangler transition](/architecture/strangler-transition/).
