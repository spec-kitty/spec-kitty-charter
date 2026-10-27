# Pre-spec grounding squad (2026-10-27)

Procedure `adversarial-squad-deployment`, point-cut **pre-spec**. Question: *what must a reader and
writer of `glossary_pack` and `skill` hold, and what does binding them touch, so that every
glossary pack and every skill (with its body) of the pinned packs round-trips by meaning (ADR-0036)
without colliding with the sibling Wave 1 missions?* Three profile-loaded, read-only lenses ran in
parallel; each loaded its profile with `spec-kitty agent profile show <id>`: `researcher-robbie`
(evidence inventory), `architect-alphonso` (decision integrity), `planner-priti` (scope, evidence,
sequencing). Grounded against `main` at `90de8f9`, `spec-kitty-open-packs` at `openPacksRef`
`01edb4e`, and core `spec-kitty` at `581b5c46da`.

## What exists

| Area | Evidence | State |
|---|---|---|
| File classification | `contracts/packs/file-kinds.yaml:130-139` | `**/*.skill.yaml` and `**/*.glossary-pack.yaml` are exclusions ("core has no model at 9554e7b2"). Core has both now. |
| Core skill | `src/charter/offering/schemas/skill.schema.yaml`, `pack_skills/models.py` | Closed JSON schema (YAML form). Prompt form requires `body_path`, wrapper form `expands_to`. The model forces `model_invocable` off when `side_effects` is set, and fills defaults (`tools: ["*"]`, `version: 1.0.0`). |
| Core glossary pack | `src/charter/offering/glossary_packs/models.py` | Pydantic only, `extra="forbid"`; no exported schema. Pack: `id`, `provenance`, `terms` (min 1), `description`. Term: `surface`, `definition`, `confidence` (float), `status` (free string) + five optional lists/strings. Unique surfaces. |
| Registry | `KindBindings.java:17-22`, `YamlFormats.java:11-16` | Five kinds; one line each to join. |
| Schema-name seam | `origin/feat/190-…`, `origin/feat/191-…` | Both add the same `KindBinding.schemaName()` and `KindBindings.bySchema`; #190 also `idKey()`. Not on `main` yet. |
| Non-YAML content | ADR-0037 decision 4, `PackFileSet.java:183`, `WholePackStore.java:253` | The walk takes `*.yaml`; one text per artifact; the fingerprint digests only bound kinds' YAML files. A skill body would be unmodelled and missing from the digest. |
| Gate | `WholePackRoundTripTest.java:45`, `expected-red.yaml:13-15` | Built-in only. `other:glossary_packs` expected red (#192). Built-in `skills/` holds only `.gitkeep`: the gate never sees a skill. |
| Seam test | `WholePackOpenPacksRoundTripTest` | Loads every pack with a `pack.yaml` (open-packs and `packs/`); `qa` has none (raised by #191). |
| Wire | `contracts/charter/openapi.yaml:31-53`, `NodeKind.yaml` | No path serves these kinds; `glossary_pack` and `skill` are already node kinds. No contract change. |

## Probes on the pinned data

- **4 glossary packs, 171 terms**: built-in (128), qa (19), event-driven (13), internal (11).
  Top-level keys always `id`, `provenance`, `description`, `terms`. Term keys used: the four
  required plus `see_also`, `synonyms_to_avoid`, `introduced_in_mission`, `aliases`;
  `banned_synonyms` in none. `confidence` always a float (0.6 to 1.0); `status` active, draft or
  deprecated. No empty lists, no explicit nulls, no duplicate surfaces. Multi-line definitions are
  folded or literal blocks.
- **5 skills**, all in `internal`, all prompt form, all valid against core's schema (jsonschema
  2020-12, 0 errors), id equal to file stem. No `tools`, `overrides`, `enhances`, `expands_to` or
  parameter `default` in use. `invocation` has `user_invocable` and non-empty `side_effects`, never
  `model_invocable`.
- **5 bodies**: no frontmatter, LF, one final newline, one non-ASCII character in the corpus (an em
  dash, in `semi-auto.skill.yaml`'s description).
- Vendored `packs/internal` and `packs/qa` copies of both kinds are byte-identical to open-packs.
- **id versus stem**: `internal/glossary_packs/spk-internal.glossary-pack.yaml` declares
  `id: spk-internal-glossary` (open-packs#47, known gap in `charter.md`). Core keys by `id` and has
  no stem rule.

## Findings and dispositions

| # | Lens | Finding | Disposition |
|---|---|---|---|
| G1 | all three | The default file token would create `<id>.glossary_pack.yaml`, which no rule classifies: a new glossary pack would never load again, silently. The schema name differs from the token too. | **accepted**: the glossary binding overrides `fileToken()` and `schemaName()` with `glossary-pack` (spec US3 scenario 2, FR-006). |
| G2 | alphonso | #190 and #191 each add the identical `schemaName()`/`bySchema` seam. | **accepted**: taken unchanged, not rebuilt (C-003); rebase on whichever merges first (A3). |
| G3 | alphonso vs priti | Skill body: model it (alphonso) or leave it unmodelled with a follow-up (priti). | **changed**: adjudicated from the source and put to the operator. Issue #192 says the pair is "kept as a pair on write"; the operator confirmed it in scope (decision `01M5YVEVMPMHPE0GDFQD0BZA6F`). Spec US2, FR-002/FR-005. |
| G4 | alphonso | A modelled but undigested body would let a body edit escape the fingerprint; an unmodelled one is lost on write. | **accepted**: the companion seam covers read, write, digest and the unmodelled list together (US2 scenarios 3-4, FR-004). |
| G5 | alphonso | #193 needs non-YAML content too (asset payloads, templates); two parallel widenings would collide. | **accepted**: #192 builds a text-only companion seam and announces it on #192 for #193 to extend to bytes (C-004). Recorded in a new ADR (0040; 0039 is earmarked for #227), not a third amendment to ADR-0037. |
| G6 | alphonso | A `body_path` that leaves the pack directory. | **accepted**: refused on read as on write (US2 scenario 2). |
| G7 | robbie | Core forces `model_invocable` off and fills defaults; replicating either breaks equality with the file. | **accepted**: neither is applied; absent stays absent (Edge Cases). |
| G8 | robbie | Core's checks beyond the schema (id length, `overrides` xor `enhances`, unique parameter names, cli metacharacters). | **deferred_with_rationale**: validation, not round-trip fidelity; not entity invariants here (A2). |
| G9 | robbie, priti | `terms` as sequence or set keyed by surface. | **accepted (sequence)**: ADR-0036 makes only edges and nodes sets; core never relies on order but a reorder is a change. Unique surface is an invariant. |
| G10 | all three | Internal glossary id differs from its stem. | **accepted**: kept as authored through the layout, no rule (C-002, A5). open-packs#47 is the upstream issue. |
| G11 | priti, alphonso | The gate never sees a skill; `qa` is loaded by no gate. | **accepted**: a corpus test over every pinned file of both kinds, with a lossy-writer positive control (US4). The gate is not widened. |
| G12 | priti | The gate's kind list and `DomainRoundTripTest` assert the modelled kinds: shared one-line edits. | **accepted**: append-only (C-003). |
| G13 | priti | Fakeable acceptance: an unread field passes; terms collected into a map would reorder. | **accepted**: fixtures carry every declared field with a non-default value; a fixture pins term order. |
| G14 | alphonso | Fingerprints of qa, internal, event-driven change once. | **accepted**: stated (A4); no test pins a real value. |
| G15 | alphonso | ADR-0029 is the docsite glossary, not glossary packs; no other slice owns either kind. | **accepted**: entities in `packs.domain` (NFR-001, C-005). |
| G16 | robbie | The built-in glossary header says "GENERATED, do not hand-edit"; comments are dropped on write. | **accepted**: comments carry no meaning (ADR-0036 E1); nothing to model. |

## Post-plan squad (2026-10-27)

Point-cut **post-plan**. Question: *is the plan's design, the companion-file seam above all,
minimal, free of split-brain and safe, and will it go green on the first full run?* Lenses:
`architect-alphonso` (seam design), `researcher-robbie` (plan facts against code and tests),
`randy-reducer` (duplication and over-engineering). Each loaded its profile.

| # | Lens | Finding | Disposition |
|---|---|---|---|
| P1 | alphonso | A companion path inside the pack could be `pack.yaml` or another artifact file; the upsert would overwrite it. | **accepted**: path rule (relative, inside the pack, not `*.yaml`); a new artifact refuses an existing companion target. Plan IC-01. |
| P2 | alphonso | The static digest reading through a `new YamlSerializationStrategy()` bypasses the injected strategy. | **changed**: the digest uses the store's schema-valid read path statically, as `PackFileKind` and `locate` already parse YAML statically; `companions(dso)` stays the one source of truth. Passing the strategy would change three callers and the deployable wiring for one kind. Recorded in ADR-0040. |
| P3 | alphonso | A changed `body_path` leaves the old body as an orphan. | **accepted**: a replaced artifact deletes the companions it no longer names; the commit swap makes it atomic. |
| P4 | alphonso | A direct live-directory save of a skill writes body and YAML as two renames. | **accepted**: companion-bearing kinds are written only through the commit path; stated in ADR-0040. No direct port exists. |
| P5 | alphonso | Two skills sharing one body load but fail the whole-pack write. | **accepted**: a companion named twice (case ignored) is a read violation. |
| P6 | alphonso, randy | Three hooks; paths and texts can drift. | **changed**: two hooks. `companions(dso)` returns path plus optional content; `withCompanions` fills it. |
| P7 | alphonso | `Map<String,String>` forces a signature change for #193's bytes. | **accepted**: a sealed `CompanionContent` with `Text` now; `Bytes` and the revision digest's move to bytes are #193's (ADR-0040). |
| P8 | alphonso | `KindBinding`'s javadoc says a binding knows no path. | **accepted**: the javadoc and ADR-0040 amend it. |
| P9 | alphonso | Read containment on the final file. | **accepted**: regular file, not followed through a link, real path in the pack. |
| P10 | randy | `Optional<List<…>>` contradicts ADR-0036 decision 2 (absent, null and empty mean the same). | **accepted**: plain lists, as the precedent DSOs; verified against the ADR text. |
| P11 | randy | `SkillForm` derivable from the invariants. | **deferred_with_rationale**: kept as an enum; `form` is a required schema field and the precedent types closed tokens. |
| P12 | randy | One corpus class for both kinds; unknown-key cases in schema tests. | **accepted** in part: one corpus test class covers both kinds; per-kind unknown-key tests stay small and sit with each format's tests. |
| R1 | robbie | Binding `skill` fails the gate with `EMPTY_MODELLED_KIND`: built-in has no skill. | **accepted**: an `empty_kinds` list in `expected-red.yaml`, ratcheted both ways, with positive controls. |
| R2 | robbie | `SchemaKeywordCoverageTest` fails on the skill schema's `not` and `maxLength`. | **accepted**: both join the fallback set, with a test of each detail. |
| R3 | robbie | Counts of 14 pinned in `PackFileKindTest`, `PackSchemasTest`, `PackSchemas` javadoc, README; sample paths in `EXCLUDED`. | **accepted**: updated to 16; samples move to `MAPPED`. |
| R4 | robbie | The digest could list a non-text or duplicate companion and break the revision read. | **accepted**: the digest applies the same text check and de-duplicates. |
| R5 | robbie | The gate assumes one file per artifact (`unpaired`, census `other:skills`); latent while built-in has no skill. | **deferred_with_rationale**: the `empty_kinds: skill` entry names it, and its ratchet fails the day built-in gains a skill, which forces the pairing then. |
| R6 | robbie | `WholePackStore`'s modelled set holds artifact files only. | **accepted**: companion paths join it (already in the plan); a test asserts no body is unmodelled. |
