# Pre-spec grounding squad (2026-10-27)

Procedure `adversarial-squad-deployment`, point-cut **pre-spec**. Question: *what exists, what is
missing and what is at risk, for a decision on round-trip fidelity that #187 and #189 can build on
without re-deciding?* Three profile-loaded, read-only lenses ran in parallel: `researcher-robbie`
(evidence inventory), `architect-alphonso` (decision integrity and conflicts), `planner-priti`
(scope, deliverables, process). Grounded against `main` at `0b2503b` and
`spec-kitty-open-packs` at `openPacksRef` `01edb4e`. Probe scripts were run from the session
scratchpad; their counts are quoted here.

## What exists

| Area | Evidence | State |
|---|---|---|
| Closed schemas | `contracts/packs/schemas/*.schema.json` | 13 of 14 schemas set `additionalProperties: false`; `pack-descriptor.schema.json:4` is open. Intentional open maps: styleguide `tooling` and `structural_lint_config`, charter `overrides`, org-charter `interview_defaults`. |
| Unknown keys in `pack.yaml` | `PackDescriptorReader.java:40-45`, `PackDescriptorDso.java:84-86`, `PackPolicy.java:230-235`, `PackRoundTripTest.java:217-233` | Names kept as `undeclaredKeys()`, warned as `pack_unknown_key`, dropped on write; a test pins the drop. |
| Contract on unknown keys | `contracts/charter/schemas/DiagnosticCode.yaml:7-12`; ADR-0017 R5/R13; DIV-005, DIV-006; open question #29 | "Reported and ignored, never refused". |
| Refusal path | `FileRead.Unreadable(file, parse, List<SchemaViolation>)` (`PackContents.java`); `PackPolicy.codeFor` (`:666-679`); `additionalProperties` already rendered "unknown key 'x'" (`:723-725`) | Exists per file kind: `PACK_UNREADABLE`, `DRG_SCHEMA_ERROR`, `ORG_CHARTER_SCHEMA_ERROR`, `ARTIFACT_SCHEMA_ERROR`; wire `pack_unreadable`, `drg_schema_error`. |
| Writer semantics | `YamlSerializationStrategy.java`, `YamlDumpEmitter.java` | `null` read as absent; empty list or empty optional not written; list order and duplicates kept; writer key order fixed, not the source's. |
| Duplicate YAML keys | `Yaml.java:108` | Refused at parse. |
| Round-trip tests | `PackDoctrineCorpusRoundTripTest.java:44-88`, `:packs:openPacksRoundTrip` (`service/packs/build.gradle.kts:88-104`) | Record → write → record over directive, styleguide, paradigm only. Both sides pass the same reader, so a dropped field is invisible. The build comment ("every YAML file must survive unchanged") overstates it; `PackRoundTripTest.java:20` cites a byte-level test that no longer exists. |
| DRG comparison | `DoctrineGraph.java:94-106`, `RelationshipShardRoundTripTest` | Ordered list comparison; merge appends without de-duplication. |
| Lossy graph readers | `GraphDocumentReader.java:63-84`, `GraphShardWriter.java:42-77` | Node `provenance`/`tags` (13 nodes) and fragment `body_path` dropped; `schema_version` and `generated_at` hard-coded. |
| File-kind classification | `contracts/packs/file-kinds.yaml:103-150` | Maps files to schemas; excludes mission types, presets, skills, glossary packs, `pack-manifest.yaml`, `*.config.yaml`, mission sidecars, `built-in.source.yaml`. |
| Upstream-fix convention | `packs/internal/PROVENANCE.md:32`, charter "Known gaps", open-packs #47 / PR #59 | Fix upstream, bump `openPacksRef`, re-vendor; a self-expiring known-difference list (R21) keeps it honest. |

## Probes on the pinned data

- **Unknown fields:** 397 schema-mapped files across 13 kinds validate against closed schemas with
  **0 violations**; both `pack.yaml` files hold only declared keys. Refusing unknown fields breaks
  nothing today.
- **DRG:** shards 1069 edges, fragments 153. **0** exact duplicates, **0** duplicate
  `(source, target, relation)` triples. Under ADR-0027's unordered-pair identity, **55** built-in pairs
  carry more than one edge: 27 `requires`+`suggests`, 24 reverse `suggests`, 3
  `reconciles_tension`+`suggests`, 1 `in_tension_with`+`requires`. The internal fragment still carries
  the known reverse pair (`packs/internal/drg/fragment.yaml:311-319`).
- **Comments:** 96 open-packs YAML files carry comments; `built-in/pack.yaml` is heavily commented.
- **Explicit null:** `built-in/pack.yaml` writes `parent_pack: null`.
- **Presets:** carry `activated_<plural>` and `mission_type_activations`; no `required_*` key today.

## Gaps and risks

1. The published contract and ADR-0017 promise unknown `pack.yaml` keys are never refused (ruling 1 reverses it).
2. The charter's "round-trips exactly" and PROJECT_002 "without loss or rewrite" read as byte fidelity (ruling 2 rejects it).
3. ADR-0027 D2 "lossless" and "does not silently drop a second edge" must be read against ruling 4.
4. Record-against-record tests cannot detect a dropped field: meaning must be judged source against written.
5. Forward compatibility: a newer core or pack adding a field makes an older service refuse (the reason DIV-005 existed).
6. `pack-manifest.yaml` hashes cover bytes; meaning-level re-serialisation invalidates them.
7. Comments are dropped by any write.
8. Schemaless kinds cannot refuse unknown fields until they get a schema.
9. ADR numbering collides if a sibling mission also takes 0036.

## Findings and dispositions

| # | Lens | Finding | Disposition |
|---|---|---|---|
| G1 | architect, researcher | Unknown-key tolerance in contract, ADR-0017 R5/R13, DIV-005/006, OQ #29, open `pack-descriptor` schema | accepted: ADR-0036 supersedes in part, names the follow-ups (`spec.md` FR-004, FR-009) |
| G2 | architect | Charter text reads as byte fidelity | changed: asked as operator question 2 on #188; ADR records the reading (FR-010) |
| G3 | architect | ADR-0027 D2 vs ruling 4 | accepted: canonical key is the directed triple; one-per-pair stays a domain invariant (FR-006) |
| G4 | researcher | Record-against-record tests are blind to dropped fields | accepted: meaning is judged source against written (FR-002) |
| G5 | researcher | Lossy graph readers (`tags`, `body_path`) | deferred_with_rationale: implementation for #187/#189; the ADR's rules make the loss visible |
| G6 | architect, planner | Absent / null / default, list order, Markdown, comments, binaries undefined | accepted: per-dimension rules and per-kind table (FR-003, FR-005) |
| G7 | architect, researcher | Unknown field: file or whole pack? | accepted: a schema violation like any other, on its kind's existing path (FR-004) |
| G8 | architect | Forward-compatibility cost | accepted: recorded as an accepted cost with the existing mitigations (ADR Consequences) |
| G9 | planner | Expected failures while an upstream fix is pending | changed: asked as operator question 1 on #188 |
| G10 | planner | Schemaless and excluded kinds, `pack-manifest.yaml` | changed: asked as operator question 3 on #188; ADR scopes them (FR-008) |
| G11 | planner | Filing convention against open-packs | accepted: ADR prescribes the filing record (FR-007) |
| G12 | planner | ADR number collision | accepted: 0036 reserved on #188 |
| G13 | researcher | Stale build comment and stale test reference | deferred_with_rationale: production/test code, out of scope (no production code); noted for #187 |
| G14 | architect | Stale branch `feat/pack-roundtrip-io` pins the drop of undeclared keys | deferred_with_rationale: branch housekeeping is the operator's; noted in the hand-off |
