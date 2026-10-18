# Research findings — pack registration & distribution (design mission)

**Mission**: `pack-registration-distribution-01M56M49`
**Author**: researcher-robbie
**Date**: 2026-10-18
**Status**: Draft — research input for ADR-0005 and the public-surface design. No implementation.

## Scope note

This document gives, per open question: **Options** (2–3), **Trade-offs**, a **Recommendation**,
**Evidence** (file/source refs), and **Needs-operator-decision?** Recommendations are evidence-based;
the operator resolves every material trade-off (see the numbered Decision Points in the hand-back).
Prior art in core and open-packs is reference, not a constraint (per spec constraints and ADR-0005
Context). The loader lives in core; this repo designs the service contract that core consumes as a
generated Python client (ADR-0001/0004).

---

## Q1 — Source kinds precisely in scope; is a published-artifact source in or out?

### Options
- **A. Three kinds, published-artifact reserved (recommended).** Contract models exactly three
  registrable `source` kinds — `git` (pinned to a commit rev), `filesystem` (local path), and
  `service-stored` (uploaded/held blob). A published-artifact (package-registry) source is **out of
  v1** but the `source` discriminator is designed so a 4th kind can be added in a later contract
  version without reshaping existing responses.
- **B. Four kinds now.** Add `published-artifact` (e.g. a versioned package a client depends on) as a
  first-class source kind in v1.
- **C. Three registrable kinds + `bundled` as a resolution outcome only.** `bundled` (built-in shipped
  inside the installed distribution) is not something a user registers; it is what `service-stored`/`git`
  materialize into at release. Keep it out of the registration enum.

### Trade-offs
- A keeps v1 surface minimal and matches ADR-0005 decision §1 exactly, while leaving the north-star
  "published data-dependency" path open. Closed response schemas (`additionalProperties: false`,
  contract-and-dtos.md:96) mean adding a kind later is a new schema version — manageable and expected.
- B front-loads a mechanism with no demand yet and no prior art; ADR-0005 explicitly treats it as a
  *future* compatible source, not the primary mechanism. Extra conformance surface for no current user.
- C is the cleanest conceptual split (register-time kinds vs resolve-time materialization) and aligns
  with the design-advice `PackSource = local | git | bundled` where `bundled` is the build-time-fetched
  built-in, not a user registration.

### Recommendation
**A, with C's distinction folded in**: three registrable source kinds {`git`, `filesystem`,
`service-stored`}; `published-artifact` documented as out-of-scope-but-reserved; `bundled` treated as a
resolve-time materialization outcome, not a registration input. The `source` field is a versionable
discriminator so the reserved kind lands later without a breaking change.

### Evidence
- `kitty-specs/pack-registration-distribution-01M56M49/.../0005-...md` (draft ADR-0005) decision §1:
  source is one of git URL (pinned rev), filesystem, or service-stored; Alternative B: published
  data-dependency "remains compatible as one possible *source* kind later".
- `pack-sourcing-design-advice.md` §3.1: `PackSource = { local | git | bundled }`.
- `docs/architecture/contract-and-dtos.md:96` closed response schemas → new kind = new schema version.

### Needs-operator-decision? **Yes** (scope boundary; low-stakes). Decision Point 1.

---

## Q2 — Built-in's distribution path through the service, and migration given the ongoing core re-vendor

### Options
- **A. Built-in registers and resolves through the service like every other pack (ADR-0005 end-state).**
  Built-in is the "first catfooding consumer": fetched once per build/release from its versioned, tagged
  open-packs ref, materialized from a pin, distributed through the same API all tiers use. End-state
  retires committed production trees.
- **B. Built-in stays a committed, pinned, drift-gated tree in core (core #3504 Option B); the service
  only handles org/consumer/private.** Matches what core is building right now.
- **C. Staged migration toward A, honoring B transitionally (recommended).** The service contract models
  built-in as a first-class registered pack now (design); core keeps its committed tree until the service
  exists and is proven; the swap is sequenced: resolver-behind-loader → integrity + lockfile → built-in
  as `bundled`/service-stored source fetched at release → de-vendor.

### Trade-offs
- A is the DIRECTIVE_044 "one canonical path" goal and dogfoods the distribution path, but core's ADR
  amendment (core #6045, decision #1) **already rejected** materialize/fetch-at-build for concrete
  reasons: every checkout/worktree/CI shard would fetch before any test, offline dev breaks, and the
  nightly gate would test a tree the release never ships.
- The design-advice reopening argues those objections fall **under the service frame**: tests read
  `src/test/resources` fixtures (not the whole tree), nightly verifies the pin, and fetch is
  once-per-release — making B′ (materialize-from-pin, retire the committed tree) viable. ADR-0005
  decision §2 and its "Negative/Risks" adopt this (a cold no-network non-test env needs one bootstrap
  fetch; the cache + fixtures + release bundle mitigate).
- B is low-risk and already in flight, but leaves the service's core function un-dogfooded by the most
  controlled pack (ADR-0005 rejects this as Option C — the DIRECTIVE_044 inversion) and keeps the
  duplicate-source-of-truth + drift-gate machinery.
- C decouples design from core's timeline: the contract can express built-in-as-registered-pack without
  forcing core to stop vendoring before the service is real. Sequencing is explicit in design-advice §6.

### Recommendation
**C** — adopt A as the recorded end-state (ADR-0005) but ship it as a staged migration that respects
core's in-flight Option B until the service + resolver + cache exist and are proven. The contract models
built-in as a registered pack with a `service-stored`/`bundled` source and a `requires_core` compat
field now; retiring any committed tree is a later, sequenced step gated on the resolver landing. The
offline/bootstrap story (where built-in bundles *to*) is the live sub-question.

### Evidence
- draft ADR-0005 decision §2 ("built-in is the first catfooding consumer … fetched once per build/release
  … not vendored as a special case"); Negative/Risks (cold-env bootstrap fetch; release bundling).
- `pack-sourcing-design-advice.md` §3.2 and §8: core ADR `2026-08-16-2` amended to Option B by core
  #6045 (merged 2026-10-11), amendment decision #1 rejects fetch-at-build; core #3504 in-flight on
  `feat/builtin-revendor-pin-gate`; reopening frame under the service.
- `spec-kitty-open-packs/built-in.source.yaml`: `seeded_from` (repo+path+commit), `tree_digest`
  (`spec-kitty-snapshot-sha256`), `requires_core: '>=4.0.0rc7,<5'` — the pin + integrity + compat
  primitives already exist upstream (open-packs #31/#32 merged).
- `docs/context/domain.md:64`: built-in source of truth is open-packs; core re-vendors a pinned,
  checksummed ref (Option B); content seeded (open-packs #32), pipeline (core #3504) in progress.

### Needs-operator-decision? **Yes** (the mission's central tension: end-state vs in-flight core reality
+ offline/bootstrap story). Decision Points 2 and 3.

---

## Q3 — Trust & consent model for consumer/private git sources (resolve open-packs #23's thin trust model)

### Options
- **A. Explicit allowlist in config.** Operator pre-approves source URLs / orgs; unlisted sources fail
  closed. Static, headless-friendly.
- **B. Interactive operator consent on first fetch (trust-on-first-use).** Prompt + record the pin on
  first encounter; later fetches verify against the recorded pin.
- **C. Signed sources only (provenance chain).** Org-admin signs; verify a signature, not just a hash.
  Strongest, but needs key infrastructure that does not exist in core today.

### Trade-offs
- open-packs #23 / ADR `2026-07-18-3` establishes the current trust primitive is a build-time content
  hash = **tamper-evidence, not tamper-proofing** (detects post-publication modification; is not a
  signed provenance chain). Core's canonical verified-distribution scheme (#2539/#2543) is unstarted.
  So C's tamper-proofing is not buildable on existing primitives without new key infra.
- A is simple, deterministic, CI/automation-friendly and fail-closed (DIRECTIVE_051, DIRECTIVE_043); it
  does not prove authorship but bounds *who* can be a source. Good default.
- B gives good ad-hoc UX but is hostile to headless/CI runs and weaker to record-poisoning if the first
  fetch is already compromised.
- All options sit on the same floor: **pin-to-rev (never a floating ref)** + **integrity-verify every
  materialized constituent against `content_hash`** + **offline cache** + **fail-closed on
  unlisted/unreachable**. Trust tiers differ above that floor (built-in/open = high trust; consumer/
  private git = gated).

### Recommendation
**A as v1** (allowlist) on the shared floor (pin-to-rev + content-hash verify + offline cache +
fail-closed), with built-in/open packs as a high-trust tier that bypasses the allowlist. Reserve **C
(signing)** as a higher trust tier to adopt when core's verified-distribution scheme lands; B optional
as an interactive convenience layered on A for non-headless use. This resolves #23 by binding the
service to "tamper-evidence + allowlist consent now, tamper-proof signing later", recorded in ADR-0005
§6 as accepted supply-chain risk (DIRECTIVE_051, DIRECTIVE_003).

### Evidence
- `spec-kitty-open-packs/docs/architecture/adr/2026-07-18-3-build-time-checksum-tagging.md`:
  interim content hash is "tamper-evidence, not tamper-proofing"; canonical scheme (#2539/#2543)
  unstarted/P3/exec-asset-scoped; recipe = sha256 over sorted (relative-path + bytes); migrates to
  canonical scheme when it lands; trust model authored in `docs/security/pack-trust-model.md`.
- `pack-sourcing-design-advice.md` §4.1: pin-to-rev, integrity-verify, trust tiers (built-in bundled =
  highest; org/project git behind allowlist/consent), offline/air-gapped fail-closed; "resolves #23".
- draft ADR-0005 decision §6 (trust tiers, allowlist/consent, offline cache) and Risks (supply chain).

### Needs-operator-decision? **Yes** (which consent mechanism, and the tier boundary). Decision Point 4.

---

## Q4 — Pin/integrity lockfile: granularity (per-pack vs repo-level); reuse of snapshot_sha256 / content_hash

### Options
- **A. Per-pack lock file** (one provenance file per registered pack: source + rev + tree digest),
  mirroring `built-in.source.yaml`.
- **B. Single repo/config-level lockfile** aggregating all registered packs in one file.
- **C. Two-tier, reusing both existing primitives (recommended).** One config-level lockfile whose
  entries are per-pack `{source, rev, tree_digest = snapshot_sha256}`; per-constituent `content_hash`
  stays in each pack's generated `pack-manifest.yaml` for granular verification.

### Trade-offs
- Two integrity primitives **already exist and should be reused, not reinvented** (DIRECTIVE_051): the
  per-pack **tree digest** (`snapshot_sha256`, in `built-in.source.yaml`) and the per-constituent
  **`content_hash`** (in `pack-manifest.yaml`). The algorithm is `spec-kitty-snapshot-sha256`
  (length-prefixed path + size + bytes over every file except `pack-manifest.yaml`), implemented in
  core `charter.offering.packs.pack_manifest.snapshot_sha256` and enforced by
  `scripts/ci/check-built-in.sh`.
- A matches current practice (`built-in.source.yaml` is exactly a per-pack lock) but scatters the
  reproducibility fact across many files — harder to get one deterministic "what is this install made
  of" view.
- B gives one committed reproducibility fact (good for byte-reproducible builds/installs, design-advice
  §4.2) but couples independently-versioned packs into one file.
- C gives both: one lockfile = the single reproducibility fact; each entry defers to the pack's own
  manifest for file-level verify. No new primitive invented — the service's lockfile entry *is* the
  `built-in.source.yaml` shape generalized to any source.

### Recommendation
**C** — a config/repo-level lockfile with one entry per registered pack carrying `source` + pinned
`rev` + `snapshot_sha256` tree digest; verification of individual constituents uses the pack's
`pack-manifest.yaml` `content_hash`. Reuse the `spec-kitty-snapshot-sha256` recipe verbatim; do not
introduce a competing hash.

### Evidence
- `spec-kitty-open-packs/built-in.source.yaml`: `tree_digest.algorithm: spec-kitty-snapshot-sha256`,
  `seeded_from.commit`, `requires_core` — the per-pack lock shape.
- `spec-kitty-open-packs/built-in/pack-manifest.yaml`: per-constituent `content_hash`, `id`, `kind`,
  `path`, `provenance_path`.
- `spec-kitty-open-packs/scripts/ci/check-built-in.sh`: computes `snapshot_sha256(Path("built-in"))`
  via the install's own interpreter and compares to the recorded digest.
- `docs/architecture/descriptors.md:69-74` (`pack-manifest.yaml` generated, `content_hash` per
  constituent, regenerated by `spec-kitty charter pack regenerate-graph`).
- `pack-sourcing-design-advice.md` §4.2 (reproducibility: lockfile committed, byte-reproducible),
  §7-Q3 (per-pack vs repo-level is a genuine decision).

### Needs-operator-decision? **Yes** (granularity is a flagged genuine design choice). Decision Point 5.

---

## Q5 — Storage model for service-held consumer/private packs (where/how, privacy boundary, retention)

### Options
- **A. File-based blob store, content-addressed by digest**, with a metadata index; the default matching
  today's file-based storage.
- **B. Database-backed** (blobs + metadata rows).
- **C. A storage *port* in the domain, file-based default implementation, DB implementation later
  (recommended).** The domain declares a repository port returning plain domain models; an outbound
  adapter implements it (file-based first, SQL later) without the domain, inbound adapters or core seam
  changing.

### Trade-offs
- The operator-set architecture already mandates C's shape: storage infra layer is **agnostic of
  storage solution**, returns **plain domain models** (never storage-shaped DTOs), and ArchUnit pins
  infra→domain only. The hexagonal design already names `adapter-out-yaml` with a later SQL projection
  able to sit beside it "without touching the domain".
- A is the pragmatic default and matches current reality; content-addressing dovetails with the
  `content_hash`/`snapshot_sha256` integrity model (Q4) and dedupes identical constituents.
- B suits multi-tenant privacy/ACL and query-heavy metadata but is premature before demand; it is the
  "later" impl behind the same port.
- **Privacy boundary**: private packs must carry an ownership/visibility attribute and must not leak
  across consumers/tenants; the boundary is enforced at the storage port *and* the API (access control),
  not by directory presence (resolution invariant: "configured inputs, never directory presence").
- **Retention**: retain any pinned rev / digest referenced by a live lockfile; GC unreferenced
  materializations; versioned so a pin always resolves. Retention policy and tenancy model are
  operator-owned (no prior art).

### Recommendation
**C** — a domain storage port returning domain models, with a file-based content-addressed default
implementation; stored packs carry an explicit ownership/visibility attribute enforced at the port and
the API; retention keeps anything a lockfile pins and GCs the rest. The concrete backend (file vs DB),
the privacy/tenancy model, and the retention policy are operator calls (least prior art here).

### Evidence
- `design-mission-architecture-brief.md`: storage infra layer "agnostic of storage solution", file-based
  or DB, returns "plain domain models"; ArchUnit enforces infra→domain.
- `docs/architecture/hexagonal-service.md:37-63`: `charter-domain` holds repository **ports**;
  `adapter-out-yaml` implements them; "a later SQL projection can replace or sit beside the YAML adapter
  without touching the domain".
- draft ADR-0005 decision §1 (service-stored = uploaded/held, the mechanism for consumer and private
  packs); §7 (resolver stores + hands downstream a verified local materialization + manifest).
- `docs/architecture/resolution-model.md:26` resolution invariant ("configured inputs, never directory
  presence").

### Needs-operator-decision? **Yes** (privacy/tenancy boundary + retention + backend; material, little
prior art). Decision Point 6.

---

## Q6 — Engine/compatibility gating (requires_core-style) in the contract, enforced at resolve time

### Options
- **A. Compat range as a contract field on the registered pack / source descriptor; enforced at resolve
  time with a canonical refusal code (recommended).**
- **B. Enforce at merge/activate time** (alongside layer merge).
- **C. Advisory warning only** (do not refuse).

### Trade-offs
- The primitive exists: `requires_core: '>=4.0.0rc7,<5'` lives in `built-in.source.yaml`, deliberately
  **not** in `pack.yaml` because core's `PackDescriptor` schema forbids extra fields. So in the service
  contract it belongs on the registration/source descriptor (or lockfile entry), not inside `pack.yaml`.
- A fails closed early (DIRECTIVE_043): a pack that cannot run on this engine is rejected before any
  merge work, with a clear diagnostic — the design-advice and ADR-0005 both call for a code "sibling to
  `PACK_LEVEL_MISMATCH`". Canonical diagnostics are part of the contract, not implementation detail
  (contract-and-dtos.md item 3), so the code (e.g. `PACK_ENGINE_INCOMPATIBLE`) ships in the contract.
- B detects the same skew later, after wasted work, and muddles two concerns (compat vs merge).
- C violates the fail-loud rule the domain inherits.

### Recommendation
**A** — express the engine compatibility range (reuse `requires_core` semantics) on the registration/
source descriptor and the lockfile entry, and enforce it at **resolve time** with a canonical refusal
code in the contract (sibling to `PACK_LEVEL_MISMATCH`, e.g. `PACK_ENGINE_INCOMPATIBLE`). Fail closed.
This is well-determined by evidence; only the exact code string is a naming detail for the contract.

### Evidence
- `spec-kitty-open-packs/built-in.source.yaml:37-40`: `requires_core: '>=4.0.0rc7,<5'`, with the note
  that it lives there because `PackDescriptor` forbids extra fields.
- `pack-sourcing-design-advice.md` §4.4: reject at resolve time, clear code sibling to
  `PACK_LEVEL_MISMATCH`.
- draft ADR-0005 Risks ("Version/engine skew … gate `requires_core` at resolve time, not at merge").
- `docs/architecture/contract-and-dtos.md` item 3 (canonical diagnostics are part of the contract);
  `docs/architecture/resolution-model.md` (fail-loud rules, `PACK_LEVEL_MISMATCH`/`PACK_LEVEL_MISSING`).

### Needs-operator-decision? **No** (recommend directly; only the diagnostic-code string is a contract
detail to ratify alongside the contract).

---

## Q7 — REST vs GraphQL surface parity — same domain operations through both, or divergent?

### Options
- **A. Full parity.** Both REST and GraphQL expose the identical set of domain operations (reads +
  registration/writes); GraphQL is an alternative transport over the same use cases.
- **B. OpenAPI/REST authoritative, GraphQL read-focused (recommended).** OpenAPI/REST is the single
  source of record (the contract core generates its client from, the DTOs, the conformance corpus).
  GraphQL is an additional entry-point adapter over the *same* domain services, giving full parity on
  the read/traversal side (DRG queries, listings, resolution) where it adds most value; write/
  registration parity through GraphQL is optional and operator-scoped.
- **C. REST only for v1, GraphQL deferred.**

### Trade-offs
- ADR-0001/0002/0004 fix the **OpenAPI contract as the source of record** and core's interaction as a
  **generated Python client** of that contract. GraphQL cannot be the thing core pins/generates from —
  so GraphQL is inherently a secondary surface, never the contract of record. This alone makes strict
  "both are equal" parity (A) misleading.
- The hexagonal design already treats inbound surfaces as thin adapters over shared use cases
  (`adapter-in-rest`, `adapter-in-mcp` both over read-app; `adapter-in-write` over write-app). A GraphQL
  adapter (`adapter-in-graphql`) fits the same pattern — the *domain operations* are shared regardless
  of surface, so "same domain operations" is already guaranteed at the use-case layer; the question is
  only which operations each surface *exposes*.
- The DRG is literally a graph; GraphQL is a natural fit for flexible read/traversal (action-scoped
  context, profile resolution, graph queries, listings) and client-shaped field selection. Registration
  is a small set of write operations well-served by REST + HATEOAS already.
- A doubles the contract-maintenance and conformance surface (every write op dual-specified) for a
  write set that is small and already canonical in REST. C under-delivers on the brief's two-API intent.

### Recommendation
**B** — OpenAPI/REST is authoritative (source of record, core's generated client, DTOs, conformance
corpus). GraphQL is an additional entry-point adapter over the same domain services with full parity on
reads/traversal; write/registration parity through GraphQL is an explicit operator choice (and may be
deferred past v1). Both surfaces adapt the same domain use cases — no business logic in adapters
(ArchUnit-pinned) — so domain-operation parity holds by construction even where surface exposure
differs. Record the authoritative-vs-secondary relationship and the GraphQL write-parity scope.

### Evidence
- `design-mission-architecture-brief.md`: two API versions, both entry-point adapters over the same
  domain — OpenAPI-based HATEOAS REST and GraphQL.
- ADR-0001/0002/0004 (via `docs/architecture/contract-and-dtos.md`): OpenAPI is source of record; core
  ingests a pinned release and generates its Python client/DTOs; "no Python code hand-writes a charter
  response shape".
- `docs/architecture/hexagonal-service.md:37-65`: inbound adapters (`adapter-in-rest`, `adapter-in-mcp`,
  `adapter-in-write`) are thin over shared read/write applications; ArchUnit pins infra→domain; domain
  imports no HTTP/framework types.
- No GraphQL prior art exists in `docs/` (grep: 0 hits) — GraphQL is net-new from the brief, so its
  scope is an open design call.

### Needs-operator-decision? **Yes** (whether GraphQL must reach write/registration parity or is
read-focused, and whether it ships in v1). Decision Point 7.

---

## Cross-cutting observations (not open questions, but load-bearing)

- **Reuse, don't reinvent.** The pin (`rev`), per-pack tree digest (`snapshot_sha256`), per-constituent
  `content_hash`, and `requires_core` compat range all already exist upstream and should be lifted into
  the contract as-is (DIRECTIVE_044 canonical source; DIRECTIVE_051 supply chain).
- **Resolver / loader boundary (DIRECTIVE_001/031).** The resolver (fetch/materialize/verify/store) is a
  distinct concern from merge/activate; the loader must only ever see a verified local directory +
  manifest, never a URL. This keeps the "configured inputs, never directory presence" invariant.
- **Fail-closed everywhere (DIRECTIVE_043).** Two sources claiming the same `pack_id` or a colliding
  constituent slug → refuse, never last-write-wins. Unreachable + uncached source → refuse legibly.
- **BDD scope** (spec open question 8, not in this research set) remains for the analyst/specify step;
  these findings feed the public-surface scenarios.
