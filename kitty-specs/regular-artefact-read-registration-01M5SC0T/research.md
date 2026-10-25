# Research: Regular artifact read & registration

Phase 0. Decisions are grounded in the mission ADRs and the post-specify review squad. No
`[NEEDS CLARIFICATION]` remains. No new dependency is introduced, so the supply-chain controls
(DIRECTIVE_051) report **no dependency change to examine**; an adversarial plan-point squad has
already run (below).

## Decisions

### D1 — Relationship identity: bounded unidirectional collapse
- **Decision**: one unidirectional `Relationship` per unordered `{from,to}` pair (ADR-0027).
- **Rationale**: the operator accepts the collapse as a deliberate normalisation; real data has one
  multigraph pair with no reader of the reverse direction yet.
- **Alternatives**: ordered `(from,to)+type` multigraph-with-dedup (rejected by operator); strict
  unordered with drop-on-read (rejected — breaks PROJECT_002).
- **Condition**: realised by **upstream open-packs normalisation**, never a charter read-time rewrite;
  **revisit trigger** when `impacts` (tension) or bidirectional `delegates_to` is minted.

### D2 — Relationship persistence: DRG shard via GraphShardWriter
- **Decision**: registered relationships persist as `DrgEdge` entries in the pack's DRG shard; the
  domain `Relationship` maps to/from `DrgEdge` at the YAML boundary; `DrgEdge` becomes a storage DSO.
- **Rationale**: reuses the existing `GraphShardWriter`/shard reader and keeps the serialized shape
  canonical (ADR-0025 D5); `DrgEdge` record-equality remains the round-trip assertion (ADR-0024).
- **Alternatives**: store relationships on each artifact's reference fields (rejected — couples the
  edge to one endpoint and cannot hold a standalone `PUT /relationships` edge).

### D3 — Write surface: functional registration, aggregate-atomic
- **Decision**: `POST/PUT /packs/{id}/{kind}[/{id}]` and `PUT /packs/{id}/relationships` (ADR-0026);
  every write does **load → mutate → revalidate the whole `Pack`** and commits the pack atomically
  via **stage + atomic directory swap**.
- **Rationale**: closes the single-child `save()` bypass so aggregate invariants gate writes;
  stage+swap delivers NFR-002's zero-partial-write on the existing filesystem substrate without a
  journal's complexity.
- **Alternatives**: per-file temp+rename (rejected — not aggregate-atomic); write journal with
  rollback (heavier; revisit only if a swap proves unworkable on the target FS).

### D4 — Refusals and concurrency, grounded
- **Decision**: new `RefusalCode` tokens for validation (`422`) and conflict/policy (`409`);
  `RefusalHandler` gains a real **code→status map** (today it maps every non-404 to 500); `412`/`428`
  are HTTP preconditions carried as a plain `Problem` body (per ADR-0020), **not** `RefusalCode`s;
  optimistic concurrency uses a **pack/artifact revision digest** distinct from the read-path
  `ConsistentRead` fingerprint.
- **Rationale**: corrects the grounding the code-review lens flagged; the mutability model (ADR-0012)
  and the write-side ETag are **net-new**, not reuse.

### D5 — Per-kind slices, no shared registry (#169 deferred)
- **Decision**: copy the proven Tactic slice per kind; no kind registry.
- **Rationale**: operator decision (plan DM 01M5SJ83) — keep kinds detached and independently
  evolvable; a shared abstraction risks over-coupling and cascade issues; accept near-duplication to
  avoid premature generalization. ADR-0024's registry stays deferred to #169.

### D6 — Resolver behaviour on unwired/external kinds
- **Decision**: `ComponentResolver` returns empty (the walk continues) for a target whose kind is out
  of scope or unwired (procedure/asset/agent_profile); independent tests exercise in-scope targets.
- **Rationale**: 44% of real edges target currently-unwired kinds; a walk must not fail on them.

### D7 — Procedure deferred (#150)
- **Decision**: no procedure entity/endpoint; out of scope pending the #150 ruling.

## Plan-point adversarial squad (findings disposition)

Per `adversarial-squad-deployment`, the plan-point/charter-mandated squad ran at post-specify. Every
finding is dispositioned (full record: `docs/development/notes/relationship-identity-collapse-review.md`):

- **Unordered-pair collapse loses real multigraph data** → *changed*: resolved by ADR-0027 (bounded
  collapse + upstream normalisation + revisit trigger).
- **Aggregate atomicity undeliverable on per-file rename** → *accepted*: D3 (stage + atomic swap).
- **`when` round-trip loss; refusal-code mis-grounding; mutability/ETag "reuse"; impacts-minting claim;
  terminology drift; relationship-persistence gap** → *accepted*: folded into ADR-0025/0026 + spec +
  D2/D4 (commit 3381cfd).
- **Reverse-suggests discovery + upstream source normalisation** → *deferred_with_rationale*: owned by
  the CLI/open-packs side, tracked at spec-kitty/spec-kitty#6667 (milestone 11).
- **Procedure/#150** → *deferred_with_rationale*: C-004; awaits the #150 ruling.
- **Provenance divergence (fragment `requires` vs body typed-ref `SUGGESTS`)** → *deferred_with_rationale*:
  reconcile against core's extractor (charter #167), tracked in #6667.
