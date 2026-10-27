# Research: Mission step contract round trip

The evidence and its dispositions are in [research/grounding-squad.md](research/grounding-squad.md).
This page records the decisions taken from it.

## D1 — How a binding names its schema

- **Decision**: `KindBinding.schema()`, defaulting to the token, and overridden by this kind
  (`mission-step-contract`). The store and the strategy resolve schemas through `KindBindings`. A
  test ties every binding's file token and schema to `file-kinds.yaml`.
- **Rationale**: it mirrors the existing `fileToken()` hook. It is checked against the single
  authority, needs no kernel change, and #190 can reuse it.
- **Alternatives considered**:
  - Rename the schema to the token. This changes its `$id`, the gate kind name and the kebab
    convention.
  - Derive the name by replacing `_` with `-`. That leaves three spellings agreeing by coincidence,
    unchecked.

## D2 — What the domain holds of a gate binding

- **Decision**: every field, as inert values. `fail_open` is kept as a written-or-absent boolean.
- **Rationale**: the pinned pack carries one binding, so the round trip must keep it. Its meaning
  is #155's.
- **Alternatives considered**: list `review.step-contract.yaml` as expected red for #155. Rejected,
  because it leaves a file of a modelled kind red for no reason.

## D3 — Schema defaults

- **Decision**: the DSO and the entity keep whether `handler_kind`, `fail_open` and `optional` were
  written. Nothing is filled from a schema default.
- **Rationale**: the gate's comparator does not apply defaults, and keeping what was written is
  exact under any reading of ADR-0036.

## D4 — Identity and relationships

- **Decision**: the local id is the file's `id`, and the entity mints no relationships.
- **Rationale**: `idOf`, the duplicate-id invariant and file naming all use the `id`. The pinned
  shard has no step-contract edges. Minting edges would create copies the operator has not ruled on.
- **Open**: the CRG nodes use `<mission>/<action>`. Asked on #191 (A2).

## D5 — Duplicate step ids

- **Decision**: they stay a validate-mode rule (`PackPolicy`), not an entity invariant.
- **Rationale**: one owner for the rule, and the planted fixture `duplicate-step-id` keeps loading.

## D6 — How the gate learns the new kind

- **Decision**: `DomainRoundTrip` takes its artifact kinds, writes and file tokens from the registry.
- **Rationale**: each Wave 1 mission then flips only its own kind, by binding it, without editing
  the gate adapter.

## D7 — The `qa` pack

- **Decision**: a corpus test reads, writes and compares every pinned `*.step-contract.yaml`
  directly.
- **Rationale**: `qa` has no `pack.yaml`, so no pack-level gate loads it. That is a known pack fix,
  raised on #189.

## Supply chain

No dependency is added, upgraded or removed, so DIRECTIVE_051 has nothing to check.
