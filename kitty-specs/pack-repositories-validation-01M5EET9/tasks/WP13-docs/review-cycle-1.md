---
affected_files: []
cycle_number: 1
mission_slug: pack-repositories-validation-01M5EET9
reproduction_command:
reviewed_at: '2026-10-21T15:56:09Z'
reviewer_agent: claude
wp_id: WP13
---

# WP13 review — REJECTED (diagrams show the superseded pre-slice structure)

Reviewer: reviewer-renata · mission pack-repositories-validation-01M5EET9 · commit 75ec59b

The prose in this WP is excellent and accurate to the realized architecture — **keep all of it
unchanged**. The rejection is for one MAJOR accuracy defect in the canonical architecture
diagrams, plus one minor prose-consistency fix to bundle into the same revision.

---

## BLOCKER (MAJOR) — both diagrams depict the SUPERSEDED layer-module design, not the realized slices

`docs/architecture/diagrams/c4-components.puml`, `docs/architecture/diagrams/class-diagram.puml`
and their rendered SVGs (embedded in `architectural-patterns-applied.md`) show the pre-slice,
role-named LAYER-module design — the design that ADR-0006's own amendment and this page's prose
explicitly call **superseded**.

What the diagrams currently show (WRONG for the cornerstone):
- **c4-components.puml** — top-level boundaries `web`, `application`, `domain`, `validation`
  (as a *separate* top-level CORE module), `persistence`, `charter-api`, `charter-service`
  (composition root). Container labels name `adapter-in-rest`, `charter-read-app`,
  `charter-domain`, `adapter-out-yaml`, `charter.persistence`, `charter-service`.
- **class-diagram.puml** — packages `domain «CORE»`, `validation «CORE kernel»` (separate
  top-level), `application`, `persistence «EDGE»`, `web «EDGE»`, `charter-service «Composition
  Root»`.
- The embedded SVGs were rendered from these same sources, so they carry the same stale labels
  (confirmed: `adapter-in-rest`, `adapter-out-yaml`, `charter-domain`, `charter-read-app`,
  `charter-service`, `persistence`, `web` all appear in the SVG text).

What they MUST show (the realized structure per `data-model.md` / `plan.md` §"target ADR-0006
slice layout" and the ADR-0006 amendment):
- `kernel` module with two packages: `kernel.domain` (shared Pack/DRG vocabulary) and
  **`kernel.validation` NESTED INSIDE the kernel module** — not a separate top-level `validation`
  module. The validation-purity gate is on `..kernel.validation..`.
- `packs` slice with the layer packages `packs.domain`, `packs.application`,
  `packs.infra.storage` (outbound/driven adapter — the current `persistence`),
  `packs.infra.api` (inbound/driving adapter — the current `web`).
- `bootstrap` = the composition root (the current `charter-service`).
- `charter-api` (generated DTOs) unchanged.
- `Pack`/`Tactic`/VOs/ports belong under **`packs.domain`**, not a generic top-level `domain`.

Per the review contract, shipping the superseded structure on the cornerstone's canonical
diagrams is a MAJOR defect **even with the mapping note** — a reader of the cornerstone reference
must see kernel/packs/bootstrap with `concern.layer` packages and validation nested in kernel, not
a prose footnote reconciling stale pictures.

### Required fix
1. **Re-draw both `.puml`** to the realized slice structure above. Keep the excellent content —
   the DDD stereotypes, the inward-arrow onion direction, the DIP realization, the round-trip
   fidelity note, the "future DB sibling adapter" note, the legend — but re-home every type into
   `kernel.{domain,validation}` / `packs.{domain,application,infra.storage,infra.api}` /
   `bootstrap`, and show `kernel.validation` as a package *within* the kernel boundary.
2. **Re-render both SVGs** with the pinned PlantUML (the same pin the repo's `plantuml` check
   uses) so the committed SVGs match the re-drawn sources.
3. In `architectural-patterns-applied.md`, the "## The diagrams" paragraph that currently says
   *"They were drawn before the final module rename, so two labels use the design names:
   persistence is packs.infra.storage, web is packs.infra.api, and charter-service is bootstrap"*
   is no longer needed once the diagrams are correct — remove or reduce it to a one-line note that
   the diagrams show the realized layout. Do not keep a mapping table as a substitute for correct
   diagrams.

---

## MINOR (bundle into the same revision) — ArchUnit rule names drift in how-to-ddd.md

`docs/development/how-to-ddd.md` → "## How to add each piece" gate column uses rule names that do
not match ADR-0006's canonical rule table (`docs/adr/0006-...md`) or the plan's IC-01 inventory:

- `PORT_INTERFACES_RESIDE_IN_DOMAIN` → ADR-0006 canonical name is **`PORTS_LIVE_IN_DOMAIN`**.
- `NO_FRAMEWORK_ANNOTATIONS_IN_DOMAIN` → ADR-0006 canonical name is
  **`NO_INFRA_ANNOTATIONS_IN_DOMAIN`** (and `DOMAIN_IS_PURE`).
- `PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES`, `PORT_IMPLEMENTATIONS_RESIDE_IN_STORAGE`,
  `APPLICATION_IS_PLAIN_JAVA` are not in ADR-0006's table or the plan inventory.

Align these to the actual rule identifiers in the realized `HexagonalArchitectureTest` (the lane
code is the ground truth; ADR-0006's table is the canonical design-name set). Pick ONE canonical
set and use it consistently across how-to-ddd.md, code-style-and-conventions.md,
architectural-patterns-applied.md and ADR-0006. (`VALIDATION_LIBRARY_IS_PURE`,
`ENTRYPOINT_OVER_APPLICATION`, `STORAGE_RETURNS_DOMAIN`, `DOMAIN_IS_PURE` already match — good.)

---

## NON-BLOCKING follow-up (do NOT fix in this WP; record as a follow-up)

- `glossary/ddd-patterns.md` is hand-authored in this repo (correctly stated in its body: "this
  one is authored in this repository, not imported from core"), but it sits under the generated
  glossary frontmatter/README convention. Raise a follow-up to reconcile the hand-authored vs
  generated-glossary distinction (README bullet) so the generator does not later clobber it. Not a
  blocker for WP13.

---

## What passed (no action needed)

- **Prose — code-style-and-conventions.md**: accurate. Type-by-nature table (record VOs/DSOs vs
  `final class` entities with package-private all-args ctor + fluent builder), guard/check dual
  strategy on `kernel.validation`, outcomes-as-values, `@NullMarked`, test conventions
  (behavior-sentence names, one non-vacuous test per concern, known-bad control per ArchUnit rule,
  round-trip fidelity on the DSO), modular-validators/TraMBU provenance — all correct.
- **Prose — how-to-ddd.md**: the three-question classification, the identity-vs-fidelity /
  DSO crux (#138), graph-is-a-projection, relationship-is-a-value-object, row-is-not-an-entity,
  and the 9 TraMBU "AVOID" lessons are all accurate and well-pitched. (Only the rule-name column
  needs the MINOR fix above.)
- **Prose — architectural-patterns-applied.md**: the layout block, the concern→pattern-role
  table, and the walkthrough correctly describe kernel/packs/bootstrap. Only the *diagrams it
  embeds* are stale.
- **glossary/ddd-patterns.md**: correctly headlined by Domain Entity vs Persistence Entity;
  full set (VO, Aggregate Root, Identifier VO, Port, Adapter, Projection, DTO vs DSO) accurate.
- **ADR-0006 amendment**: Proposed→Accepted (2026-10-21), realized module set listed
  (kernel{domain,validation} / packs / bootstrap / charter-api / charter-reports), superseded flat
  modules named, blocking ArchUnit gate in `bootstrap` referenced. `adr/README.md` index updated
  to Accepted / 2026-10-21. Correct.
- **Stale-reference sweep**: current-architecture docs read as slice equivalents; historical ADR
  Context/Alternatives correctly retain the old names as history. Correct.
- **Lints**: `check_docs.py all` → frontmatter/related/freshness/adr/changelog/terms/orphans/links
  all ok; `check_docs.py plantuml` → ok. (The plantuml check validates render, not realized-design
  accuracy — the diagrams render fine but depict the wrong structure.)
