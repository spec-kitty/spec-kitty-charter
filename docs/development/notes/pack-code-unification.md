---
title: "Note: one code per pack fault (the R2 table)"
description: "The R2 code table of #554: for each pack fault, what validate mode and the registration answer today, and the one code and wording kept."
doc_status: draft
updated: 2026-11-22T00:00:00Z
type: Guide
kind: Reference
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change a pack check's code or wording. Each row says which surface changes and which Breaking fragment carries it.
  - profile: core-maintainer
    guidance_text: Read this for the codes validate mode and the registration answer for each pack fault, today and after #554.
---

# Note: one code per pack fault (the R2 table)

**Status: the decision table of #554 (ADR-0075 slice 5), work package WP01.** It records what each surface answers
today and the one code and wording each fault keeps. The code changes land in WP04 and WP09. Nothing here changes
code.

## The rule

The operator's R2 ruling (b), as folded by design-r3 §7 F3, is that one fault answers one code and one wording on
every surface. Each fault is decided by this rule:

- **The specific code wins over a catch-all.** The catch-alls are `domain`, `reference`, the `invalid` fallback, the
  aggregate `{pack, validation_failed, message}`, the load refusal at pointer `pack`, and `schema` reported once per
  violation. Where registration answers a catch-all, registration changes.
- **Registration's specific codes are adopted by validate mode.** These are `retired`, `manifest_membership`,
  `reserved_shard`, `asset`, `prompt_fragment_invalid` and `relationship_endpoint_not_allowed`. Otherwise, where
  both surfaces answer a specific code for one fault, the registration token stays, because it is the contract wire
  (design-r3 §3).
- **A fault only validate mode judges keeps its code.** These are the level, the org fragment, the descriptor and the
  asset mime. R1 keeps them off the registration surface.
- **The wording follows the kept code.** A detail names the file pack-relative, as registration's details do, and
  never starts with the pack's name (`qa/…`, squad finding C-11). A schema fault keeps validate mode's detail
  vocabulary (`'id' does not match its pattern`), which names the key. Registration's keyword form
  (`pattern violated at /id`) goes.
- **The compose read is unchanged.** Its `pack_unreadable` refusal names the first fault in the reader's order, and
  that read keeps the faults it reports today (R1, squad A-M3). Its detail embeds a fault's wording, so a changed
  wording shows there too, with the same code.

Each row was checked against `service/packs/src/test/resources/golden/surfaces.json`. A row with no golden subject
says so, and gives the code that answers it instead. `+pack.yaml` marks a plant judged again with the clean
control's descriptor, because its own pack has none.

Fragments:

- **R** is `contracts/charter/changes/2026-11-16-registration-names-the-fault.md`.
- **V** is `contracts/charter/changes/2026-11-16-validate-mode-speaks-the-wire-codes.md`.
- **Q1** is `contracts/charter/changes/2026-11-16-unmodelled-files-are-not-held.md` (WP02).

## The table

V and R are today's answers. Validate mode places a finding at a file and a place. Registration places a violation
at a pointer, which is the file unless the row says otherwise.

| # | Fault | Validate mode today | Registration today | Kept | Changes | Fragment | Golden |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Artifact file is not YAML or not UTF-8 | `artifact_schema_error`, `qa/<file> is not valid YAML: <why>` | `unreadable`, `<file>: <why>` | `unreadable`, `<file>: <why>` | V | V | checked against golden: `artifact-not-yaml+pack.yaml`, `artifact-not-utf8+pack.yaml`, `artifact-utf16-with-a-byte-order-mark+pack.yaml` |
| 2 | Artifact breaks its schema | `artifact_schema_error` at the key's place, `qa/<file>: 'role' does not match its pattern` | `schema`, `<file>: pattern violated at /role` | `artifact_schema_error`, `<file>: 'role' does not match its pattern` | R (code, wording); V (prefix) | R, V | checked against golden: `c10-*+pack.yaml`, `c11-*+pack.yaml`, `c2-agent-nested-unknown-key+pack.yaml`, `glossary-context-id-not-kebab`, `glossary-see-also-not-a-urn`, `glossary-term-id-*`, `r33-authored-action-sequence`, `r34-authored-template-set`, `asset-id-not-kebab` |
| 3 | Glossary context id is not its file stem | `artifact_schema_error`, `qa/<file>: 'id' has an unsupported value 'b'` | `schema`, `<file>: glossary id 'b' is not its file stem 'a'` | `artifact_schema_error`, `<file>: glossary id 'b' is not its file stem 'a'` | R (code); V (wording) | R, V | checked against golden: `glossary-context-id-not-the-stem` |
| 4 | Graph document breaks its schema | `drg_schema_error` at the place, `qa/<file>: edge endpoint 'X' is not a URN` (and so on) | `drg_schema_error`, `<file>: pattern violated at /edges/0/source` | `drg_schema_error`, `<file>: edge endpoint 'X' is not a URN` | R (wording); V (prefix) | R, V | checked against golden: `c3-fragment-*`, `c3-root-shard-urn-malformed+pack.yaml`, `c5-fragment-*`, `c6-*`, `c7-fragment-relation-extends`, `glossary-term-as-*`, `r15-glossary-urn-as-an-edge-endpoint` |
| 5 | Edge relation does not allow its endpoints | `relationship_endpoint_not_allowed` at `edges/<n>`, `qa/<file>: a requires edge may not connect …` | `relationship_endpoint_not_allowed`, `<file>: a requires edge may not connect …` | registration's | V (prefix) | V | checked against golden: `r30-edge-procedure-requires-action`, `r31-edge-overrides-across-kinds` |
| 6 | `overrides` source is not activatable | `overrides_source_not_activatable` | `overrides_source_not_activatable` | registration's | V (prefix, if any) | V | no golden subject; codes from `PackPolicy.violationKind` and `OverrideSourceRule` |
| 7 | Mission step has no `requires` edge | `mission_step_unlinked`, `qa/<file>: the step b of …` | `mission_step_unlinked`, `<file>: the step b of …` | registration's | V (prefix) | V | checked against golden: `r32-mission-step-unlinked` |
| 8 | Substep delegation unresolved or unscoped | `substep_delegation_unresolved` or `substep_delegation_unscoped`, `qa/<file>: …` | the same code, `<file>: …` | registration's | V (prefix) | V | checked against golden: `r35-*`, `r36-*` |
| 9 | Duplicate step or substep id | `duplicate_step_id` at `steps/0/substeps/2/id`, `<file>: substep id 'run' is declared more than once …` | `duplicate_step_id`, same wording | unchanged | none | none | checked against golden: `duplicate-step-id` |
| 10 | Duplicate artifact id (two files of one kind) | `duplicate_artifact_id` at the second file, `<file>: tactic id 'same' is already declared in <first>` | aggregate: `validation_failed` at `pack`, `Constraint breached: A pack must not hold two tactics …` | `duplicate_artifact_id` at the second file, validate mode's wording | R | R | checked against golden: `duplicate-artifact-id+pack.yaml` (the compose read throws the breach: open case 3) |
| 11 | Retired file | `artifact_schema_error`, `qa/<file> is retired: <why>` | `retired`, `<file>: <why>` | `retired`, registration's | V | V | checked against golden: `glossary-pack-old-shape` |
| 12 | Asset sidecar's payload is missing | `artifact_schema_error`, `qa/<file> payload <path> does not exist` | `asset`, `<file>: payload <path> does not exist` | `asset`, registration's | V | V | checked against golden: `asset-mime-*+pack.yaml`, `asset-path-inside-through-a-symlink-is-contained+pack.yaml`, `control-a-clean-org-pack` |
| 13 | Asset path escapes `assets/` | two findings: `artifact_schema_error` and `asset_path_escape`, both `… asset path '<p>' escapes the pack's assets/ root` | one `asset`, `<file>: asset path '<p>' escapes the pack's assets/ root` | one `asset`, registration's | V (code, and the duplicate goes) | V | checked against golden: `asset-path-escape-absolute+pack.yaml`, `asset-path-escape-dot-dot+pack.yaml`, `asset-path-escape-windows-drive+pack.yaml` (open case 1) |
| 14 | Asset mime is malformed or does not match the extension | `asset_mime_invalid` | not judged | `asset_mime_invalid` (validate only, R1) | none | none | not judged: registration's code does not check the mime, so there is no registration answer to check against golden; validate mode's is in the golden at `asset-mime-*+pack.yaml` (landing fix W2) |
| 15 | Two asset sidecars declare one id | `duplicate_artifact_id` at the second sidecar | not reported (each sidecar answers its own `asset` fault) | `duplicate_artifact_id` (validate only, R1) | none | none | checked against golden: `asset-repeated-id-*+pack.yaml` (open case 2) |
| 16 | Template read twice | not judged as such | `template`, `template <id> is already read from <file>` | `duplicate_artifact_id`, registration's wording | R | R | no golden subject; `FilesystemPackStore.java:535` |
| 17 | Value-object breach in an artifact | not judged as such | `domain`, the breach's message, at the file (at the artifact id for one path, `DomainRepositoryService.java:1261`) | `artifact_schema_error` at the file, the breach's reason | R | R | no golden subject; `FilesystemPackStore.java:406`, `DomainRepositoryService.java:920, :939, :948, :1261` (open case 4) |
| 18 | Unresolved template or reference in an artifact | not judged as such | `reference` | the specific code of the reference rule | R | R | no golden subject; `FilesystemPackStore.java:789`, `DomainRepositoryService.java:1248, :1254` (open case 4) |
| 19 | Companion file of an artifact cannot be read | `prompt_fragment_invalid` (step enhancements) | `prompt_fragment_invalid` for an enhancement (the store's `companion` is remapped), `companion` otherwise | `prompt_fragment_invalid` for an enhancement; `companion` otherwise | none | none | no golden subject; `FilesystemPackStore.java:908`, `DomainRepositoryService.java:1199` |
| 20 | Step enhancement or orphan prompt fragment is wrong | `prompt_fragment_invalid` | `prompt_fragment_invalid` | registration's | V (prefix, if any) | V | no golden subject; contract scenario `prompt-fragment-refusals.feature` pins registration |
| 21 | Prompt slot malformed, or filled though not empty | `prompt_slot_malformed`, `prompt_slot_not_empty`; an unversioned descriptor makes it the warning `prompt_slot_not_empty_unversioned` | `prompt_slot_malformed`, `prompt_slot_not_empty`; the warning as diagnostic `prompt_slot_not_empty` | the two violations, and one warning answering `prompt_slot_not_empty` on both | V (warning token) | V | no golden subject; `PackFindingCode.diagnostic()` |
| 22 | `pack.yaml` breaks its schema (unknown key, missing `pack_version`) | `pack_unreadable` at `pack.yaml`, `pack.yaml of qa: unknown key 'mystery'` | refusal at pointer `pack`, `pack_unreadable`, `pack.yaml of qa cannot be read: additionalProperties violated …` | `pack_unreadable` at pointer `pack.yaml`, validate mode's wording | R (pointer, wording) | R | checked against golden: `control-unknown-pack-key-is-a-warning`, `control-retired-pack-key-is-a-warning`, `c9-pack-version-missing-at-the-org-level` |
| 23 | `pack.yaml` is not YAML | `pack_unreadable` at `pack.yaml`, `pack.yaml of qa is not valid YAML` | refusal at pointer `pack`, `pack_unreadable`, `pack.yaml of qa cannot be read: <parser line>` | `pack_unreadable` at pointer `pack.yaml` | R (pointer) | R | checked against golden: `pack-yaml-tilde-is-null` (open case 5, the wording) |
| 24 | No `pack.yaml` | not reported | refusal at pointer `pack`, `pack_unreadable`, `pack qa has no pack.yaml` | `pack_unreadable` at pointer `pack.yaml`, `pack qa has no pack.yaml` | R (pointer); V gains it (R1) | R, V | checked against golden: every plant without a descriptor, for example `artifact-not-utf8` |
| 25 | Root that cannot be read, or is not a directory | not reachable (the inspector reads the root) | refusal `validation_failed`, violation at `pack` with `pack_unreadable` | unchanged: refusal `validation_failed`, violation at `pack` with `pack_unreadable` (F4) | none | none | no golden subject; `WholePackStore.java:276, :743` |
| 26 | Any other aggregate invariant breach (`Pack.Builder.build()`) | not judged as such | aggregate `validation_failed` at `pack` | the specific code of the invariant; the fail-closed guard keeps the aggregate shape (F6) | R | R | no golden subject; `WholePackStore.java:299` (open case 4) |
| 27 | A kind token that is not snake case | not judged | `invalid` | the specific kind code; the fallback goes (IC-04) | R | R | no golden subject reaches it; `WholePackStore.java:763` |
| 28 | Reserved shard `registered.graph.yaml` without the stamp | `drg_schema_error`, `registered.graph.yaml is reserved for registered relationships and cannot be a source graph shard; rename the shard` | `reserved_shard`, `registered.graph.yaml is reserved for the registered relationships; … Register the edges in a shard of your own.` | `reserved_shard`, registration's | V | V | no golden subject; `FileSystemPackInspector.java:88`, `WholePackStore.java:780` |
| 29 | Manifest lists files the tree does not hold, or misses some | not judged | `manifest_membership` at `manifest.yaml`, only when nothing else failed | registration's; validate mode gains it (R1) | V | V | no golden subject; `WholePackStore.java:390-396`, contract scenario `whole-pack-registration.feature` |
| 30 | Manifest is not a regular file | not judged | `unreadable`, `the manifest is not a regular file` | registration's; validate mode gains it | V | V | no golden subject; `WholePackStore.java:388` |
| 31 | Manifest names a preset in the wrong place | not judged | `schema` at the manifest | `artifact_schema_error` at the manifest | R; validate mode gains it | R, V | no golden subject; `WholePackStore.java:850` |
| 32 | The policy file `mutability_policy` names cannot be read | not judged | `unreadable`, `named by mutability_policy, the file …` | registration's; validate mode gains it | V | V | no golden subject; `WholePackStore.java:869` |
| 33 | A file no pack file kind models | silently skipped | `unmodelled`, `no pack file kind models this file; …` | `file_not_held` warning, `no pack file kind models this file` (Q1, not R2) | R, V | Q1 | checked against golden: `c8-shard-without-generated-by-is-accepted+pack.yaml` and the other `drg/*.graph.yaml` and `charter.yaml` plants (open case 6) |
| 34 | A file an exclusion names (excluded) | lists the file, no finding | diagnostic `file_not_held` (warning), `<file>: not held (<reason>)` | registration's; validate mode gains it | V | V | checked against golden: `example-packs/built-in`, `example-packs/org-base` |
| 35 | Configured level missing from `pack.yaml` | `pack_level_missing` (warning) | not judged | unchanged (validate only) | none | none | checked against golden: `control-no-generated-by-and-no-pack-level` |
| 36 | Declared level differs from the configured one | `pack_level_mismatch` | not judged | unchanged (validate only) | none | none | no golden subject; `PackPolicy.java:155` |
| 37 | Org pack has no `drg/fragment.yaml` | `org_fragment_missing` | not judged | unchanged (validate only) | none | none | checked against golden: `org-fragment-missing` and every `+pack.yaml` org plant |
| 38 | `requires_core`, `pack_version` or a `depends_on` range is not PEP 440 | `requires_core_invalid`, `pack_version_invalid`, `dependency_range_invalid` | not judged (accepted); the offering answers the first as `pack_unreadable` | unchanged (validate only; one descriptor function, IC-03) | none | none | checked against golden: `requires-core-invalid` (registration accepts it) |
| 39 | A symbolic link below the pack root (D1, D6, #597) | not judged (a file link was read through) | not judged (a file link was read through, #597) | `pack_entry_linked`, `<link>: is a symbolic link; a pack holds no links`; owner `StorageCodes` (`LinkRules`, and the store half in `StoreRead`); surfaces: registration, validate mode, the configured load (the composing read answers `pack_unreadable` naming it); the pack root may be a link (#174) | R, V | R, V | checked against golden: `pack-entry-linked-file`, `asset-path-escape-through-a-symlink`, `asset-path-inside-through-a-symlink-is-contained`, `asset-path-symlink-loop` |
| 40 | A manifest `content_hash` or `manifest_hash` that does not match the pack's bytes (#630, ruling R-b, added 2026-11-22) | not judged (`--round-trip` reported it as `round_trip_red`) | not judged (restamped) | `manifest_stale` at `pack-manifest.yaml`, one per field, naming it (`constituents{<path>}.content_hash`, `presets{<path>}.content_hash`, `manifest_hash`); owner `StorageCodes` (`ManifestRules`), after `manifest_membership` and only when nothing else failed; a path membership names gets none | R, V | R, V | no golden subject; `ManifestRulesTest`, contract scenario `whole-pack-registration.feature` |

40 rows. 31 change a surface: 23 change validate mode and 16 change registration, 8 of them both. Row 33 changes under
Q1, row 39 under rulings D1 and D6 of #478 (added 2026-11-19), row 40 under ruling R-b of mission
`pack-doctor-coverage-01M7YMQK` (added 2026-11-22,
[ADR-0083](/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor/)), the others under R2.

## Open cases

None of these needs the orchestrator to decide R2. Each is decided by the rule or by R1, and each is listed so that
WP04, WP05 and WP08 see it.

1. **The asset escape is counted twice in validate mode** (row 13). Validate mode answers `artifact_schema_error` and
   `asset_path_escape` for one sidecar, and registration answers one `asset`. The kept answer is one `asset`, so
   validate mode loses a finding. Through a symbolic link (`asset-path-escape-through-a-symlink+pack.yaml`,
   `asset-path-symlink-loop+pack.yaml`), registration names the missing payload and validate mode names the escape.
   The kept reason is the escape, because a payload outside the root is never read.
2. **A repeated asset id** (row 15). Only validate mode reports it. Registration reports each sidecar's own `asset`
   fault and never the repeat, so the counts differ (validate mode 3 or 4, registration 2). Under R1 registration
   gains nothing.
3. **The compose read throws on a duplicate artifact id** (row 10). `DomainRepositoryService.compositionFacts`
   answers a `ConstraintBreachException`, which is a 500 with no `pack_unreadable` detail. The golden records it as
   `threw`. R1 keeps the compose read's faults, so this stays until a ruling says otherwise.
4. **The catch-alls with no golden subject** (rows 17, 18, 26). `domain`, `reference` and the aggregate breach give
   way to a specific code, but no planted pack reaches them. WP05's A-M1 checklist adds one plant per reader fault.
   The kept code for each must be checked against that plant before WP09.
5. **The wording for a `pack.yaml` that is not YAML** (row 23). Validate mode drops the parser's line, and
   registration keeps it. The proposed wording is validate mode's form plus the parser's line:
   `pack.yaml of qa is not valid YAML: <parser line>`. It is the only row where neither surface's wording is kept
   whole.
6. **Two surfaces classify files differently** (row 33). Validate mode reads `charter.yaml` and a shard under
   `drg/<kind>.graph.yaml` (it judges them, for example `charter-invalid+pack.yaml` and
   `c16-shard-edge-provenance+pack.yaml`). Registration calls the same files `unmodelled`. **Settled by WP02: the
   registration's classification holds.** The service's model holds the root shards and `drg/fragment.yaml`, and no
   `charter.yaml`: the compose read never reads either file (the golden records it `readable`), so neither affects
   the service, and Q1 says such a file is warned about, then ignored. `file-kinds.yaml` mapping them does not make
   them held: its `**/charter.yaml` rule exists "so that a stray one is validated, not silently ignored", and under Q1
   a stray file is no longer silently ignored, it is a `file_not_held` warning that names it. A misplaced shard is the
   spec's "typo hidden as an unmodelled file": the warning names it, and judging its contents would refuse a pack for
   a file the service discards. So:
   - **Registration (WP02, done).** Each of the 12 golden subjects (`c16-shard-edge-provenance`,
     `c2-charter-nested-unknown-key`, `c3-shard-edge-bare-endpoint`, `c3-shard-urn-malformed`,
     `c4-shard-urn-prefix-differs-from-kind`, `c7-shard-relation-extends`, `c8-shard-without-generated-by-is-accepted`,
     `charter-cli-timeout-not-a-number`, `charter-invalid`, `charter-template-set`, `charter-valid` and
     `r15-glossary-urn-in-a-shard`, each `+pack.yaml`) is accepted with one `file_not_held` warning,
     `<file>: not held (no pack file kind models this file)`. These are the golden's only Q1 rows.
   - **Validate mode (settled by WP11).** Validate mode judges nothing in a file the store does not hold, and answers
     the same `file_not_held` warning, so its verdict on each of these 12 plants is that one warning beside the
     descriptor's own findings (valid on the bare plant), as spec US3 scenarios 2 and 4 ask. The `drg_schema_error` and `artifact_schema_error`
     findings at those files are gone (row 33, V). The packs contract followed in the same change: the
     `**/charter.yaml` rule is removed and the graph-shard rule narrowed to `*/*.graph.yaml`, with their classifier
     cases (`core/graph/a.graph.yaml` and `project/charter.yaml` are `unmodelled`) and a packs `### Breaking` line;
     the open-packs draft (filed as open-packs#309) carries the same reclassification. A not-held file carries an
     explicit kind (`EXCLUDED` or `UNMODELLED`), a retired file is no longer also listed as not held, and
     `HeldSetAgreementTest` pins validate mode's not-held files to the store's until WP05's one census. Validate mode
     warns for every not-held file, an excluded one too, since holding is what counts and not the reason (WP11 review,
     orchestrator decision): `example-packs/built-in` and `example-packs/org-base` gain their one warning. The C4 and
     C16 plants (`c4-shard-urn-prefix-differs-from-kind`, `c16-shard-edge-provenance`) moved their shard to the pack
     root so that those rules keep a plant, so ten of the twelve stay open-case-6 subjects. DIV-021 is retired, and
     DIV-027 records that core refuses a nested shard the service does not hold.
7. **Asset faults are not all validate-only** (rows 12 to 15). A missing payload and a path escape are read faults
   on all three surfaces: registration answers `asset` for them (11 golden subjects) and the compose read refuses
   them. Only the asset mime and a repeated asset id are validate-only. The data model's R1 surface table counts
   "asset rules" as validate-only; WP08 builds its surface profiles from the narrower reading here.
8. **The clean control is not clean** (row 12). `planted/control-a-clean-org-pack` has a sidecar whose payload
   `assets/doc.txt` is missing, so all three surfaces refuse it, though its `expected.json` says it loads with no
   findings. This predates #554. **Routed to WP05** (WP01 review): its plant work adds the payload so the control is
   clean again, a golden row recorded as a fixed fixture defect, not a behavior change (NFR-002).
9. **A mapped file the load does not take** (WP02 review L3, WP11). A file a `files` rule maps can still be left
   not taken by the store's load with no fault of its own: a `policy.yaml` the descriptor does not name, and an orphan
   prompt fragment (which the registration then refuses as `prompt_fragment_invalid`). Today the store lists such a
   file as not held with kind `UNMODELLED` and the reason `no pack file kind models this file`, which is false for it,
   while validate mode judges it against its rule. A mapped file the load refused with a fault of its own is no longer
   also listed as not held (WP11). **Routed to WP05**: its census decides whether such a file is held, refused or not
   held, and with which reason; no planted or example pack holds one, so `HeldSetAgreementTest` does not meet it.
   **Settled by WP05:** the census decides once. A `policy.yaml` the descriptor's `mutability_policy` does not name is
   not held, with its own kind (`NotHeldFile.Kind.UNNAMED`) and reason (`the descriptor's mutability_policy does not
   name it`): both surfaces warn `file_not_held` for it, and validate mode no longer judges its contents. When the
   descriptor cannot be read, which file it names is unknown, and the file stays held and judged. An orphan prompt
   fragment is held by the census (a rule maps it), so it is no longer listed as not held, and it keeps its refusal,
   `prompt_fragment_invalid`, on both surfaces. A plant for each case is in `HeldSetAgreementTest`. No golden row
   changes. A companion a rule maps that no artifact names (a guide no toolguide names, a step prompt no mission type
   names) is not held either, with the same kind and the reason `no artifact of the pack names it` (WP05 review M1):
   both surfaces warn `file_not_held` for it, validate mode does not read it, and a registration removes it before
   the commit, as it did before WP05 (then as `unmodelled`). `HeldSetAgreementTest` plants an orphan guide and an
   orphan step prompt.

## Landing fixes

The pre-merge squad (reviewer-renata, architect-alphonso) and the acceptance verifier said LAND; their folded findings
were applied on the landing branch. Two of them deliberately change golden rows, recorded here as open case 8 was. No
other golden row moves, and `composition.json` is byte-equal.

- **W1: a fixed fixture defect, not a behavior change (NFR-002).**
  `planted/asset-path-inside-through-a-symlink-is-contained` declared `path: in/x.txt`, and no payload of that name exists anywhere in the plant, so it pinned a missing payload,
  not the through-a-link case its name claims. It now declares `in/keep.txt`, which `assets/in` (a link to `real`)
  reaches. R2 row 12 cites the plant as a golden subject of the missing payload. The plant is still refused, for a
  reason the old path hid: the store's walk enters no directory link (WP05, T026), so a payload reached only through
  a linked directory is not found, and the plant pins that a contained path through a link is a missing payload, never
  an escape (row 13 stays silent). Its `expected.json` now names that `asset` finding. Four rows change, each only in
  the payload path of the detail (`payload assets/in/x.txt does not exist` becomes `payload assets/in/keep.txt does
  not exist`): `surfaces.json` `planted/asset-path-inside-through-a-symlink-is-contained` and its `+pack.yaml` twin
  (the registration's `asset` violation, and the compose read's refusal), and the same two keys of `validate.json`.
- **R1: three root twins keep the graph-shard rules planted.** WP11 left `c3-shard-edge-bare-endpoint`,
  `c7-shard-relation-extends` and `r15-glossary-urn-in-a-shard` as `file_not_held` plants under `drg/` (open case 6),
  so `graph-shard.schema` lost its planted coverage of a bare endpoint, the `extends` relation and a glossary-kind
  node. Their root twins plant the same shard at the pack root, which the store holds:
  `c3-root-shard-edge-bare-endpoint`, `c7-root-shard-relation-extends` and `r15-glossary-urn-in-a-root-shard`. The `drg/` copies stay the `file_not_held`
  plants. Twelve rows are added, two per twin (the bare plant and its `+pack.yaml`) in each of `surfaces.json` and
  `validate.json`:
  - the C3 twin: `drg_schema_error` at `tactic.graph.yaml` `edges/0/source`, `edge endpoint 'QA_404' is not a URN`
    (row 4);
  - the C7 twin: `drg_schema_error` at `tactic.graph.yaml` `edges/0/relation`, `unknown relation 'extends'` (row 4);
  - the R15 twin: two `drg_schema_error` at `glossary.graph.yaml`, `node urn 'glossary:term' is not <kind>:<id>` at
    `nodes/0/urn` and `node glossary:term has unknown kind 'glossary'` at `nodes/0/kind` (row 4).

  Beside them, each bare plant answers `pack_unreadable` at `pack.yaml` (row 24) and each `+pack.yaml` subject
  `org_fragment_missing` in validate mode (row 37); the compose read refuses all six, `a graph document of qa cannot
  be read`. Core never reads a root shard, so the twins are DIV-023 subjects, with their core verdicts to record.
  (Since #617 and #628 the compose read names the document and the reason, and lists the registration's violations.)
- **The reverse subset names both.** `SurfaceSubsetTest` compares the registration with its frozen pre-WP12 baseline
  (`registration-before-wp12.json`, unchanged). It names the six twin subjects, which the baseline predates (the three
  bare ones are S8 rows, so the S8 count is WP12's 38 and those 3), and compares W1's `+pack.yaml` row with its
  baseline under the one payload-path fix, so every other field of that row is still compared.
- **W3: a plant's `expected.json` is not an oracle.** The golden snapshot is the oracle of what each surface answers.
  A plant's `expected.json` is a partial statement: the rule tests (`GraphRulesTest`, `AssetRulesTest`, and the
  others that call `PlantedFixtures.expecting`) read only the findings of their own codes from it, and no test
  compares a plant's whole `expected.json` with a surface. So an `expected.json` can omit a read fault, as open case 8
  and W1 show, and a disagreement with the golden is a fixture defect to fix, never a verdict.
- **W2:** row 14's golden column says "not judged", not "checked against golden": the registration did not check the
  mime when the table was written. Since S2 the mime is a precheck of the one reader, and the registration answers it.
- **AA1, AA2 (deferred half) and AA5** are recorded as known limits of the single-parse unification, under the outcomes
  of WP05.
- **AA3:** `HexagonalArchitectureTest` adds `STORAGE_RULES_STAY_IN_THE_STORAGE_EDGE` (nothing outside
  `packs.infra.storage` and the composition root names `packs.infra.storage.rules`), with the bite fixture
  `packvalidation.badfixture.ValidationNamesTheStorageRules`. No existing rule is widened.
- **AA4:** `WholePackStore.load` reads through the one reader (`FileSystemPackInspector.readWithStore`): the aggregate
  from its store half, the files not held from its census (`readTree(...).census().notHeld()`), so the store no longer
  computes them itself. `reader()` is documented as test-serving.

## Outcomes of WP10

WP10 closed the slice: the every-code coverage, the mutation floors, the ADR amendments, the drafts, and the items
carried from WP04, WP09 and WP13. No golden row moves: `surfaces.json`, `validate.json` and `composition.json` are
byte-equal.

- **Every code is exercised (T055, NFR-005).** `PackCodesCoverageTest` enumerates every `PackCodes` content code,
  every `StorageCodes` code and every `CLOSURE` code, and fails naming a code with no failing or no passing fixture;
  a test pins the enumeration to every code the two classes build. The fixtures are the planted and example packs,
  then ten small packs written for the codes no plant reaches (the prompt slot codes, `prompt_fragment_invalid`,
  `overrides_source_not_activatable`, `companion`, `pack_version_invalid`, `dependency_range_invalid`,
  `reserved_shard`, `manifest_membership`), each checked to state its code; a new plant would have added golden rows.
  `validation_failed` (row 26) has one hand-written fact, since no read reaches it. `PlacementCodesCoverageTest` does
  the same for `PlacementCodes`, beside its subject in `:pack-validation`.
- **The storage rules count with the core rules (squad-shaping Q6, the default).** `packs.infra.storage.rules` moved
  from `:packs`' `infra.storage` floor group to `domain+application`: it is pure, so it is held to the same floor.
- **PIT (T056, R3-b (i)).** Measured, not narrowed: `:pack-validation:pitest` kills 330 of 372 mutants (88.7 %), so the
  floor stays 85 and its margin widens from 0 to 13 mutants; `PackCheck`, `PackVerdicts` and the placement rules kill
  every mutant, and the survivors are the round trip's (#578). `:packs`' `domain+application` shard, with the storage
  rules, kills 3846 of 4240 (90.7 %), margin 242. The `infra.storage`
  shard, without them, kills 3134 of 3439 (91.1 %), margin 210. Both floors stay 85. The narrowed run of this mission's `:packs` classes
  (`packs.domain.rules`, `packs.infra.storage.rules`, `PackRefusals`, `StoreRead`, `Wording`, `CensusEntries`,
  `WholePackCensus`, `CompositionFactsBuilder`) kills 717 of 732 (98.0 %), against 698 of 733 (95.2 %) before WP10:
  `AssemblyRulesTest` gained the companion mappings' cases, `CompositionFactsTest` pins WP05's moved code (a type's own
  step link is no reference, the nodes of a kind with no file are defined, the typed edges' order, the graph refused
  before the descriptor), and `FileSystemPackInspectorTest` the manifest's and the named policy's trailing order,
  read unsorted as the registration answers it (WP10 review cycle 1), which kills `StorageRulesets.verdict`'s adding
  of the named policy to the trailing files.
  `Wording` and `GraphBinding`, WP03's, now kill every mutant. ADR-0062 has the amendment of 2026-11-17.
- **The 15 mutants left, named.**
  - *Equivalent.* `Precedence.withoutLeadingZeros` (a boundary: stripping every zero leaves an empty string, which
    still sorts before every non-zero index and equals every other zero); `Precedence.keyOrder`'s fragment-edge
    conditional (`FRAGMENT_EDGE_KEYS` is `SHARD_EDGE_KEYS`).
  - *Equivalent on every input the store produces.* The `colon == 0` boundaries of `AssemblyRules.why`, `declaredOf`,
    `namesCompanion` and `companionOf` (the store never words a companion fault with an empty path), and
    `CensusEntries.entry`'s `slash == 0` boundary (a pack-relative path never starts with `/`).
  - *No difference a read can show.* `CompositionFactsBuilder.ownStepLinkTargets`' three filters: every step a held
    type declares must be linked by that type, and a link that names no step of its type refuses the pack
    (`mission_step_unlinked`), so a target the filters would keep or drop is dropped by an own link anyway, or the
    pack never composes.
  - *Equivalent (WP10 review cycle 1).* `StoreRead.namedPolicyFaults`' filter forced to true: the extra store-only
    faults it admits are the manifest's (parse, schema, not UTF-8, a misplaced preset entry), and the reader states
    each of them in the same words (`FileSystemPackInspector.misplacedPresetEntries` for the last), so
    `AssemblyRules.sameFault` removes the duplicate; probes over eight manifest and policy fixtures showed no
    difference.
  - *Uncovered.* `StoreRead.of`'s aggregate breach is a fail-safe no read reaches (row 26), and so were
    `CompositionFactsBuilder.heldFacts`' missing facts until #617, whose self-joining edges reach them; `WholePackCensus.contentFormOf` is exercised only by `:pack-validation`'s
    round trip (`RoundTripControls`), outside `:packs`' PIT scope.
  - #556's code (the reference checks of the closure walk) was not touched.
- **The carried items.** WP04's companion cases (a Markdown refusal is one `companion` fault at the companion; a
  fragment that is not UTF-8 is `prompt_fragment_invalid` through `coded()`, and any other companion that is not
  UTF-8 is never held, so only the store half meets it and answers `companion`, `is not valid UTF-8`, beside its
  `file_not_held` warning); WP09's root shard that does not parse (one read fault; a schema breach stays one fault per
  violation), `CharterWiringTest`'s comment, "prechecks" restored and in the word list, and DIV-028's core line
  (`pack_validator.py:787-788` at `9554e7b2`); WP13's m1 to m5 and N1 to N3, and the stale test names in the fixture
  metadata.
- **WP13's m4.** `WholePackCensusAgreementTest`: over every golden subject, every path a walk meets has a gate kind
  in `WholePackCensus.of`, and `companionsOf` of an artifact the store holds is the store read's companion claims for
  it. The scenario packs are built inside Cucumber steps in `:deployable`, out of the module's reach.
- **WP13's N1.** WP13's T051 check is the word-bounded `git grep -w`; run again here,
  `git grep -wn -e PackPolicy -e PackFinding -e PackFindingCode -e PackInspector -- service` finds nothing.
- **Dead code.** `PackRefusals.asPackNotFound` had no caller left; it is deleted.
- **Not done here.** The native build cannot run without GraalVM in this session; CI runs it.
- **Edits outside WP10's owned files**, each a carried item or a survivor's triage: the R2 note, the word list,
  DIV-028 (`divergences.yaml`, the register page), two planted fixtures' metadata and `docs/plans/charter-example-packs.md`,
  `CharterWiringTest`, `HexagonalArchitectureTest` and two bite fixtures, the four storage rules (N3),
  `PackRefusals` (dead code), and the `:packs` and `:pack-validation` tests named above.

## Outcomes of WP13

WP13 deleted the glue and pinned the shape. `PackPolicy` (with `judge`, `Judgement` and its paired emitters),
`PackFinding` (`reason`, `of`, `toRefusal`, `toDiagnostic`) and `PackFindingCode` are gone, with
`RelationshipEndpointRule.violations()`, `NotHeldFile.finding()`, `Judged`, `PolicyReadouts`, `PlantedFixtures`'
policy readout and the `PackPolicy*Test` and `PackFindingTest` classes. No golden row moves: `surfaces.json`,
`validate.json` and `composition.json` are byte-equal.

- **One finding type in the core.** A pack fault is a kernel `Reason`. `Findings` is the one RFC 6901 helper: it
  encodes a place as a pointer and decodes one (`Findings.segments`, which `Precedence` and the fixtures read).
  `GraphBinding` states its findings through it.
- **The reader owns its wording.** `Wording`, which knows the JSON-Schema keywords and names the storage tokens
  (`pack_unreadable`, `unreadable`, `artifact_schema_error`), moved to `packs.infra.storage`; `Paths.origin` is the
  domain's one "where a finding comes from". `DescriptorChecks` stays a plain domain function (the resolver and the
  offering call it) and returns code-free faults; the reader states each as its precheck code (S2).
- **One rule function per check.** The store half, which the composing read answers from (S7), records the findings of
  the one rule function: `FragmentRules.judge(facts, edgesRead)`, `StepRules.repeatedSubstepIds` and
  `PromptSlotRules.faults`. The endpoint refusals it records come from `RelationshipEndpointRule.refusal`, in the order
  `GraphRules` lists them. `PackElementViolation` stays: it is the store half's and the aggregate's record of a fault,
  not a converter. Four storage-edge mappings record a rule's finding on it, and no other: `StoreRead.heldBack` (a
  substep id a mission type repeats, `StepRules.repeatedSubstepIds`), the `FragmentRules` lambda in `StoreRead.assembly`
  (`FragmentRules.judge`'s faults as `prompt_fragment_invalid`), `StoreRead.recordPromptSlotFaults`
  (`PromptSlotRules.faults`' violations) and `FilesystemPackStore.endpointViolations`
  (`RelationshipEndpointRule.refusal`), which repeats the order `GraphRules` uses: every source refusal in edge order,
  then every table refusal.
- **Store-only faults are typed (WP12 review cycle 2, NIT-B).** `FilesystemPackStore.PackRead.storeOnly` holds the
  faults only the store's read meets (a companion it cannot read, a template read twice, a value-object breach of an
  asset file's name or a preset), recorded where it meets them; `StoreRead` reads them from there, and no message
  prefix is matched.
- **`file_not_held` has one wording.** `NotHeldFile.detail()` is the sentence `NotHeldRules` states and the tests read.
- **The census gate reads the `TreeCensus` (operator, 2026-11-17).** The census carries every entry of the one walk
  (each regular file and each link, never followed) with how it stands (mapped with its schema and content form, a
  companion with its owner's schema, retired, excluded, or uncovered with its first segment) and every companion a
  readable artifact names, present or not; it is built whether or not the descriptor can be read
  (`CensusEntries`). `WholePackCensus` names the entries as gate kinds and walks and classifies nothing. The baseline
  lists and the drift test are unchanged, and `wholePackRoundTrip` is green.
- **Placement tokens from the contract.** `PlacementCodes` builds `pack_level_missing` from
  `Diagnostic.Code.PACK_LEVEL_MISSING` and `pack_level_mismatch` from `RefusalCode.PACK_LEVEL_MISMATCH` (WP09 carry).
  ArchUnit rules A and B now let `PlacementCodes` name those two constants, and rule C lets only `LevelRules` and
  `PlacementCodes` name the pack-level codes. `ReservedRefusalCodesTest`'s off-wire allowance names `PlacementCodes`,
  the one class that names `pack_level_mismatch`, which the service does not run.
- **ArchUnit (T052).** `PACK_CODES_HAVE_ONE_OWNER` (no class of the packs slice or `pack-validation` but `PackCodes`,
  `StorageCodes` and `PlacementCodes` builds a `ReasonCode`; the round trip's own verdict tokens are not pack tokens),
  `PACK_RULES_READ_NO_FILE` and `STORAGE_RULES_ARE_FRAMEWORK_FREE` (direct and transitive), each with a bite fixture.
  The onion rules, `PACK_VALIDATION_IS_FRAMEWORK_FREE`, `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and
  `STORAGE_RETURNS_DOMAIN` are unchanged; the transitive walk is factored so the new rule shares it.
- **Coverage kept.** The path-order corpus is `ArtifactRulesTest`'s and `PrecedenceTest`'s, the escape corpus
  `PackRelativePathsTest`'s, the graph binding cases `GraphBindingTest`'s, and the planted oracle (`expected.json`)
  judges `GraphRules`; the parity-by-hand "finds what validate mode finds" tests are gone with their oracle. The tests
  named after `LoadChecks` are `PromptSlotSurfacesTest`, `StepEnhancementSurfacesTest` and
  `PackFileSetBudgetTest.theWholePackLoadsAreWithinBudget`.
- **PIT.** The narrowed run on `packs.domain.rules`, `packs.infra.storage.rules`, `StoreRead`, `Wording`,
  `CensusEntries` and `WholePackCensus` kills 587 of 645 mutants (91 %), against 545 of 616 (88 %) before over the
  same packages without the two census classes.

## Outcomes of WP12

WP12 switched the registration onto the one read. `PackRegistrationService` reads the staged tree once through the
domain port `PackReading`, under the pack id it is registered as, and refuses through `PackRefusals` when the readout's
rationale fails (S1, S6); the store's `Loader` then assembles the aggregate from that read alone, strips its not-held
files and stamps the manifest from it, and judges nothing. `ExcludedFile` is gone for `NotHeldFile`, and `LoadChecks`
is gone: its withholding and faults are the store half's own assembly (`StoreRead.assembly`), which the composing read
answers from, unchanged (S7). The compose read runs `StoreRead` only, with no S4 filter.

- **The golden delta is 43 registration rows; the compose read's rows do not move.** 38 are S8 (rows 22 to 24): the 34
  plants without a `pack.yaml` and the four whose `pack.yaml` cannot be read (`c9-pack-version-missing-at-the-org-level`,
  `control-retired-pack-key-is-a-warning`, `control-unknown-pack-key-is-a-warning`, `pack-yaml-tilde-is-null`) answer
  every fault, the descriptor's after the graph faults, under `N fault(s), the first at <file>.`. 9 are S2 (rows 14, 15
  and 38): the four asset-mime and repeated-asset-id plants, each bare and `+pack.yaml`, and `requires-core-invalid`.
  Four rows are both. `SurfaceSubsetTest` pins the forward subset over the two golden files and names these rows.
- **The store half's own faults are read faults, each stated once** (rows 16 to 19 and 32, A-M1). The verdict joins
  the read faults, `registration()`, then the store half's store-only faults (`AssemblyRules.held`), then the manifest
  gate. The store-only faults are an allow-list the store half builds where it meets them
  (`Assembly.Assembled.storeOnly`, review cycle 1 MAJOR-2): a template read twice, a value-object breach, a reference
  or output template that does not close, a companion it cannot read, the named policy file, and the composition
  facts' refusal. The rule families the store half also judges for the composing read (the step enhancements,
  `duplicate_step_id`, the prompt slots, the step links, the substep delegations) are `content()`'s alone. A
  store-only fault is left out only when the read states the same fault (MAJOR-1), by an explicit mapping: the
  companion's own fault at the companion file (with the reader's `companion` or `artifact_schema_error`), a companion
  the reader names missing or named more than once at the artifact, a step id standing twice, which the reader words
  `step '<id>' stands twice ...` and the domain's breach names no step (review cycle 2 MAJOR-A: no other breach of a
  mission type is the reader's), or the same code at the same file in words that end with the store's. Something else
  at the same file never hides it: a preset whose id is not its stem answers its value-object breach too, and a blank
  fragment beside an enhancement that names a slot twice is answered beside the two "more than once" faults
  (`StoreHalfFaultsTest`). Over the 108 golden subjects no store fault was left unstated; the plants of
  `StoreHalfFaultsTest` reach each one: a template read twice (`duplicate_artifact_id`, row 16), a value-object breach
  (`artifact_schema_error`, row 17), a guard reference that does not close, at the lifecycle that names it, and a
  template a mission type names and the pack lacks, in the reader's words (`names templates/x.md (named by the type):
  missing`, row 18), and an edge that joins an artifact to itself through the pack's own name (`unreadable` at `pack`,
  as the registration answered it, and since #617 `drg_schema_error` at its graph document, as its bare form is;
  `GraphBinding` judges the bare self-loop only). Validate mode gains each of them.
- **Cascades the store half does not reach (review cycle 2; corrected in WP13).** The store half stops a mission type
  at the first fault it meets, so a second fault of the same type is met by neither half. A named output template the
  pack lacks refuses the type at the store's read, before its entity is built, so an artifact key standing twice in it
  is not reached. The entity build stops at the first fault it meets too: in `StoreHalfFaultsTest`'s plant that is the
  renamed step's prompt path, thrown while the builder is filled, so the artifact key standing twice is not reached; the
  step standing twice is the reader's fault, in its own words. (Had the build gone on, the domain's guards check the
  artifact keys before the step ids.) Each is answered once the first fault is fixed. `StoreHalfFaultsTest` pins both,
  and that a distinct fault the store does meet (the step's prompt path beside a step standing twice) is not hidden by
  the reader's step fault.
- ~~**Not done here, for WP13 (review cycle 2 NIT-B).** `StoreRead.metOnlyByTheStore` tells the store read's own faults
  (a template read twice, a value-object breach) by their message's start; a typed marker would need the store's
  `PackRead` to carry them apart, through `readKind` and its callers.~~ Done in WP13: `PackRead.storeOnly` carries them
  apart (see "Outcomes of WP13", "Store-only faults are typed").
- **Row 32 is one answer.** The policy file `mutability_policy` names is stated by the store half, `unreadable`,
  `<file>: named by mutability_policy, the file is missing` or `the file cannot be read: <why>`, on both surfaces; the
  reader's own parse or schema fault at that file is not stated beside it.
- **Row 19's answer.** A prompt fragment the store cannot read (blank, with frontmatter, a codec fault) is one
  `prompt_fragment_invalid` at the step enhancement that names it, in the store's words, as
  `prompt-fragment-refusals.feature` pins (the R2 rule: the registration's wire stays); the reader's faults at the
  fragment are not stated beside it, so validate mode changes. Any other companion is answered at the companion's own
  file, as validate mode placed it: in the reader's code and words where the reader reads it (`companion` for a file that
  does not parse, `artifact_schema_error` for a structure-schema breach, row 2's vocabulary), and `companion`,
  `missing` or `is not valid UTF-8`, where only the store meets it (a guide or a skill body that is missing or not
  UTF-8); the registration answered each at the artifact (`<artifact>: <companion>: <why>`), so it changes. One
  exception (review cycle 1 MINOR-3): an enhancement the store refuses itself (an id that is not its stem, a schema
  fault) has no fragment read by the store, so a fragment of it that cannot be read is answered at the fragment, one
  fault per finding, beside the enhancement's own fault. No golden
  subject reaches row 19; the `### Breaking` fragment `2026-11-17-registration-answers-every-fault.md` records both.
- **Row 25 (WP07 review MINOR-1).** The store's refusal of a root is not stated beside the reader's `pack_unreadable`
  at `pack`, whatever name the pack is read under.
- **Row 26 (WP07 review MINOR-4).** The aggregate's `build()` breach keeps `StorageCodes.ASSEMBLY_REFUSED`
  (`validation_failed`) at `pack`. No read reaches it: every invariant of `Pack` (a repeated id per kind, a repeated
  preset or asset file, an asset naming a file it does not hold or another asset's) is withheld by the store half and
  stated with its own code first (`duplicate_artifact_id`, `asset`), so the breach is the fail-safe of a state the read
  never builds, and no specific code is invented for it.
- **Order (WP07 review MINOR-3).** The read faults at `pack-manifest.yaml` and at the named policy file follow the rule
  faults and the store's, and come before the orphan fragments, as the registration listed them.
- **WP04 review MINOR-3 and MINOR-4.** A registration answers one violation per descriptor finding (two unknown keys are
  two violations), and the refusal path reads `pack.yaml` once: the store half places the descriptor's refusal with no
  second read.
- **NIT (WP07).** A `registered.graph.yaml` without the stamp that does not parse is a read fault of the root shard as
  well as the reserved shard: validate mode answers `drg_schema_error` at it beside `reserved_shard`, as the
  registration does.
- **Not done: `WholePackCensus` on the `TreeCensus` (WP05 review MINOR-3).** The whole-pack gate still walks and
  classifies on its own. Pointing it at the census needs the census to carry the link entries, the `other:`, `retired:`
  and `excluded:` buckets and the companions' owner kinds for every file, which the one read does not build for a pack
  whose descriptor it cannot read; it is left for the orchestrator to place.
- **One read per registration.** The registration reads and parses the staged tree once (`OneReadRegistrationTest`,
  `OneReadTest`): the strip and the stamp work from the read. The revision still lists the final tree to digest its
  bytes, after the strip and the stamp changed it.

## Outcomes of WP09

WP09 switched validate mode. `PackValidation` moved out of `packs.domain` to `pack-validation` as `PackCheck`, the
validation edge (S2, S6): it reads a pack once through `PackReading`, appends the placement rules' rationale to the
readout's with `Rationale.and`, and maps the findings with `PackVerdicts`. The registration and the compose read are
not switched (WP12, S7).

- **The golden delta is row 24 alone.** Validate mode's half of the snapshot moved to `pack-validation`'s
  `golden/validate.json`, byte for byte (the registration and the compose read stay in `golden/surfaces.json`, and the
  subjects are the packs test fixtures' `GoldenSubjects`). Regenerated over `PackCheck`, 34 of the 108 rows change,
  and each only by one finding: every plant without a `pack.yaml` gains `pack_unreadable` at `pack.yaml`, `pack qa has
  no pack.yaml`, first among its findings. No other row changes, and no finding moves: the retyped comparator keeps
  the order, and the content findings, the read faults and the files not held that the readout carries are exactly
  the ones `PackPolicy` judged. The refusal is DIV-028: core reports nothing for a missing descriptor (none of the 33
  core verdicts recorded for those plants names `pack.yaml`; 11 are accepted). A configured plant keeps its
  `pack_level_missing` warning beside the refusal (row 35 unchanged).
- **Rows 29 and 30 come by construction,** with no golden subject: a manifest that leaves out a held file answers
  `manifest_membership`, and a manifest that is not a regular file `unreadable`, in the registration's words
  (`PackCheckTest`). Row 26 came with WP07; row 31 was one answer already.
- **Row 32 does not come by construction.** The named policy file is read by the store half only
  (`StoreRead.policy`), and its fault stays in `Assembly.Assembled.violations()`, which the verdict does not carry
  (WP07, "the store half's own faults"). Validate mode answers nothing for a `policy.yaml` that `mutability_policy`
  names and the pack does not hold, where the registration answers `unreadable`, `policy.yaml: named by
  mutability_policy, the file is missing`; for one that does not parse it answers the read fault `unreadable`,
  `policy.yaml: <parser line>`, where the registration says `policy.yaml: named by mutability_policy, the file cannot
  be read: <parser line>`. No golden subject reaches either. Making the store half's faults read faults, once, is
  WP12's; until then row 32 is open.
- **Placement** is `packvalidation.placement`: `LevelRules` and `OrgFragmentRules` over `Placement(PackReadout,
  configured level)`, composed by `PackCheck.placement()` (the level rules first, as `PackPolicy` listed them), with
  their three tokens built in `PlacementCodes`. The mismatch's token is written there, not read from
  `RefusalCode.PACK_LEVEL_MISMATCH`: HexagonalArchitectureTest's rule B lets only the policy and the inbound edge name
  that refusal code, and no rule changes (S6). `PlacementCodesTest` pins that it is the refusal's token.
- **N2:** `Precedence.comparator(Set<ReasonCode>)` orders kernel `Reason`s, reading a reason's place as the decoded
  segments of its pointer; `group`, `comparePaths` and `comparePlaces` are public plain functions. `PackVerdict.findings`
  is a `List<Reason>`, and `PackVerdicts` passes `PlacementCodes.ALL` as the group it tests first.
- **`PackPolicy` until WP13.** `PackPolicy.judge` has no main caller. It returns its own staging record,
  `PackPolicy.Judgement`, sorted by a local comparator over `Precedence`'s plain functions, and is given the read
  faults alone (the tests' `PolicyReadouts` and `PlantedFixtures.policyReadout`), so WP07's staging subtraction
  `notJudgedHere` is deleted.
- **One port of the read (N6, N7).** `PackInspector` and its `Scope` are deleted, with `inspect(root, scope)` and
  `PackContents.loadPath()`; `PackLoad` is folded into `PackCheck`, and the `PackValidation` bean goes (C-003). Of the
  nine `LOAD_PATH` sites, eight read the whole tree and one, whose only subject was the scope, is deleted.
- **PIT.** The narrowed run on `packvalidation` kills 330 of 372 mutants (89 %), against 308 of 350 (88 %) before;
  every new class (`PackCheck`, `PackVerdicts`, `Placement`, `LevelRules`, `OrgFragmentRules`) kills all its 23
  mutants, and the survivors are the round trip's, unchanged. The comparator lives in `packs.domain.rules`: its
  narrowed run kills 76 of 78 `Precedence` mutants, the two survivors WP03's.

## Outcomes of WP07

WP07 wrote the rest of the content rules, the storage rules and the compositions, made the one reader implement
`PackReading` with the storage edge's verdict, and wrote `PackRefusals`' registration mapping. Nothing is switched:
registration still judges through `WholePackStore.loadStaged` (WP12), and validate mode through `PackPolicy.judge`
(WP09). The golden snapshot does not move.

- **`PackRulesets.content()`** runs `GraphRules` (`GraphBinding`'s faults, and slice F's endpoint bridge, which
  `RelationshipEndpointRule` feeds), `ArtifactRules`, `AssetRules`, `StepRules`, `PromptSlotRules`, the step
  enhancement half of `FragmentRules`, `MissionTypeRules`, then `FragmentRules`' orphans, in the order a registration
  lists them. It names no glossary and no asset-escape rule (WP06 D1, D2). `FragmentRules` is `StepEnhancementRules`
  moved into `packs.domain.rules`, one `Reason` per fault.
- **The edges-read gate is one function,** `GraphRules.graphsRead`: no graph document the read marked unreadable. The
  load's gate (`graphs.violations()` not empty) holds the same documents, since the read marks a fragment whose
  `body_path` leaves the pack unreadable as the store refuses it.
- **The reserved shard (row 28) is the census's fact.** The reader reads an unstamped `registered.graph.yaml` as any
  root shard, as the store does, and `ReservedShardRules` states the fault, once. Validate mode reports it as before;
  it now judges such a shard's edges too, as registration does (no golden subject).
- **The store's refusal of the pack as a whole is a storage rule,** `AssemblyRules`: the read's `Assembly.Refused` fact,
  unless a read fault states it already (a descriptor or a root the reader could not read). A missing `pack.yaml`
  (row 24) answers `pack_unreadable` at `pack.yaml`; the aggregate's own breach (row 26) answers its own token,
  `StorageCodes.ASSEMBLY_REFUSED` (`validation_failed`), at `pack`.
- **Registration's wire order** is `StorageRulesets.inRegistrationOrder`: the graph faults by path, the pack's refusal,
  the read and rule faults as given (the read faults first, so a binding-level fault leads a rule fault), then the
  reserved shard, a manifest that is not a regular file and the membership; the warnings as given, then the files not
  held by path. `PackRefusals` keeps that order and names no storage code.
- **Golden equivalence.** Over the 108 golden subjects, `PackRefusals` over the readout's verdict answers the
  registration's recorded answer exactly for 65. The other 43 are listed in `PackRefusalsTest`: the S2 rows (14, 15
  and 38), where registration gains `duplicate_artifact_id` at an asset sidecar and the reader's format checks, and 38 rows
  where today's store refuses the pack whole on its descriptor (rows 22 to 24).
- **Open for the orchestrator (before WP12): the descriptor rows.** Today a pack with no `pack.yaml`, or one the store
  cannot read, is refused with that one violation and the problem detail `The pack cannot be registered: <fault>`. The
  verdict is collect-all, as validate mode needs it to be (it judges the same verdict from WP09, and a bare plant keeps
  its own findings), so on the verdict registration answers the same fault after the graph faults and before the
  pack's other faults, under `N fault(s), the first at <file>.` 38 registration rows change when WP12 switches;
  NFR-002's list does not name this yet.
- **Open for WP12: the store half's own faults.** The faults only the store's assembly meets (a value-object breach,
  a reference that does not close, a template read twice, the named policy file: rows 16 to 18 and 32) stay in
  `Assembly.Assembled.violations()` and are not in the verdict. Joining them with the read faults reports some faults
  twice, because the reader states them in other words: row 19's companion placement, and the mission type checks
  (`names templates/x.md (named by step 'y'): missing` against `templates/x.md (named by step 'y'): missing`; a step
  standing twice against the aggregate's constraint breach). WP12 owns row 19's answer and deletes `LoadChecks`, so it
  makes these faults read faults, once.
- **Validate mode, staged until WP09.** `PackPolicy.judge` takes from the verdict what it does not judge itself: the
  read faults and the storage faults, less the content findings and the files not held (it still judges both) and less
  a missing descriptor's refusal (row 24 stays WP09's). So validate mode gains rows 29, 30 and 26 now, with no golden
  subject, and reports nothing twice.
- **PIT.** The narrowed run on `packs.domain.rules`, `packs.infra.storage.rules` and `PackRefusals` kills 423 of 447
  mutants (94.6 %). Every new class kills all its mutants; what is left is `GraphBinding`, `Precedence` and `Wording`
  (WP03's), and `PackRefusals`' read-service mapping, whose tests are outside the narrowed test set.

## Outcomes of WP06

WP06 wrote the first content rules in `packs.domain.rules` (`StepRules`, `ArtifactRules`, `AssetRules`,
`MissionTypeRules`), as kernel rule constants over `PackContents`. Nothing is wired yet: `PackPolicy` still judges, and
each rule's tests prove it finds what `PackPolicy` finds over every planted and example pack. The orchestrator decided
three cases under S2 (format and schema checks are the edge's; core holds only consistency logic, with no duplicate
implementations):

- **D1: no glossary content rule.** #106's glossary checks are the one reader's schema and stem prechecks, the storage
  format rule of a repeated term id (`GlossaryYaml.rules`), the retired `glossary_pack` shape and `GraphBinding`'s term
  endpoint rule. `PackContents` carries no glossary fact, and moving any of them into the domain would take a fault
  away from the compose read, which runs no `content()`. They stay where they are, and there is no `GlossaryRules`.
- **D2: the read owns the asset escape (row 13).** The store's sidecar rule (`SidecarPayloads`) refuses every sidecar
  whose path leaves `assets/`, with the code `asset` and row 13's wording, on every surface, so a domain escape rule
  could only repeat it. There is none. `AssetRules` holds the repeated asset id alone (S2, row 15), and
  `AssetRulesTest` pins every planted escape as that read fault: row 13's wording when the path leaves as written, the
  missing payload's through a symbolic link (open case 1, as WP04 settled it).
- **D3: the substep id check is `StepRules`' alone** (`duplicate_step_id`, row 9). `MissionTypeRules` holds slice F's
  step link and substep delegation rules, and judges no mission type that repeats a substep id.

**PIT and rule constants (for WP07).** PIT leaves out a private method that only a static initializer reaches, and a
rule constant names its function from the class's initializer, so a private rule function is never mutated and a
narrowed run reports nothing for it. The rule functions are package-private, so PIT mutates them.

## Outcomes of WP08

- **Every composition fault is collected (FR-003).** `PackCompositionResolver.faults` judges a set in today's order:
  every repeated `pack_id`, every repeated name, every pair of disjoint ranges, then pack by pack in the stable order
  its dependencies (itself, unknown, out of range, level) and its parent (itself, unresolved, level), then every
  sibling contest (changed artifacts, then defined ones), then the dependency cycles: the cycle the resolver has
  always refused with first, then the shortest cycle through each pack on a cycle that no earlier one names, and none
  for a pack only downstream of a cycle (review cycle 1, M1). `resolve` throws the first, so each refusal the offering
  answers is today's: `golden/composition.json` is unchanged, byte for byte, with no row changed. A fault leaves the
  rest judged: a repeated `pack_id` is read as its first holder, in the order and in the sibling contests alike, and
  an edge to the pack itself or to an unknown pack orders nothing.
- **`CLOSURE` is `CompositionRules.closure()`,** a `ModularRuleset<List<PackUnderResolution>>` in `packs.domain.rules`,
  its codes `PackCodes.CLOSURE`. `CompositionFault(code, packs, detail)` keeps the pack list a `Reason` cannot carry
  (A-M4). WP09 adds the one-line delegate `PackRulesets.closure()`. The preset contests are a phase of their own,
  `CompositionRules.presetFaults(packs, presetIdsOf)`, since a `PackUnderResolution` carries no preset ids; #478 joins
  the two phases in `PackRulesets.closureFaults` and `PackRulesets.closure(presetIdsOf)`. It orders the packs
  itself, by `PackCompositionResolver.mergeOrder` (the resolved order, then the unplaced packs in the stable order), so
  its answer does not depend on the input order; the offering's `presetContests` keeps the order it is given.
- **Classification (T045).** The offering keeps its interleaved order, answering the first fault of each phase: the
  resolve, the delegations, the references, the step-enhancement targets, the preset contests, then the case-only
  matches. `refuseUnresolvedDelegations` (`pack_unreadable`) and the step-enhancement targets stay in that sequence and
  are not `CLOSURE`. `refuseCaseOnlyMatches` and the three reference checks (`checkReferences`) are #556's closure
  walk, unchanged; `EdgeIndex` and `CompositeComponentResolver` call only `checkReferences`, and are unchanged.

## Outcomes of WP05

- **T026, links:** "the reader follows no symlinks under a staged tree, as the store does not" holds for the census's
  facts, as the store reads them today: the manifest (present and regular), the reserved shard and the files not held
  are read without following a link. The walk that lists the pack's files follows a file link and enters no directory
  link, as the store's listing always has, and the golden's symlink plants depend on it.
- **One read:** the reader walks the tree once, and the census, validate mode's facts and the aggregate's assembly
  input (`PackContents.Assembly`, with the load's faults: A-M1 rows 1 to 5) all come from that walk. The registration's
  load builds through `PackAssembly` from it alone. Validate mode's rationale is still today's read faults only, until
  WP09.
- **A-M1 row 3, the aggregate's `build()` breach:** the read records it as a fact (`PackContents.Assembly.Refused`,
  `validation_failed`, placed at `pack` after the graph faults), and a unit test builds that fact directly. No planted
  pack reaches it: the withholding and the repeated-id rule leave the aggregate nothing to breach.
- **The compose read (operator decision, 2026-11-17):** the compose read runs only the `StoreRead` half of the one
  reader, over one walk: the same DSO read and assembly the registration's `readTree` runs, with no second DSO reader.
  It never sees the reader's S2 prechecks, so no S4 filter is needed, and its answers are unchanged. Running the whole
  one read on it was measured first (warmed, 200 calls each): built-in 35.4 ms today against 161 ms (4.6x), org-base
  3.8 ms against 18.3 ms (4.8x), project 1.1 ms against 3.8 ms (3.5x). The cost is two parses of every file: the
  inspector's generic parse and validation, and the store's DSO parse. A single-parse unification, deriving the
  inspector's facts from the DSOs, is later work. **Known limits, deferred to that unification** (the pre-merge
  squad, architect-alphonso; recorded at the landing, with no code change):
  - **AA1: "held" is decided by two companion resolutions.** `PackFileSet.digested`, which the census's not-held list
    and the digest read, resolves companions with the hard-wired bundled bindings (`KindBindings`), while
    `CensusEntries` resolves them with the reader's own bindings and companion rules. They agree in production, where
    the reader's bindings are the bundled ones, but they can split under the binding seam a test uses (a binding of
    its own), and each resolution parses the companions' owners again. The unification gives "held" one resolution,
    over the reader's bindings.
  - **AA2, the deferred half: what the aggregate holds.** `PackElementViolation` is converted to a kernel `Reason`
    and back in three places (the store half's records, the verdict's `AssemblyRules.held`, and the aggregate's
    refusal). The landing removed the one synthetic conversion (`AssemblyRules.unreadFragments` now reads the
    `Reason`'s file and words directly). Whether the store half and the aggregate should hold a `Reason` in place of
    `PackElementViolation` is decided with the unification.
  - **AA5: the read counters are global statics.** `FilesystemPackStore`'s `LOOKUP_PARSES` and `READS`,
    `PackFileSet`'s `WALKS` and `StoreRead`'s `RUNS` are static `LongAdder`s that the one-read tests sample before
    and after a call. They count every read of the JVM, so a test that runs beside another read can miscount. The
    unification replaces them with a counter the reader is given.

## Outcomes of WP04

WP04 applied the table on today's pipeline: `PackCodes` and `StorageCodes` build each token once, and both surfaces
answer the kept code and wording of every row it changed. The golden snapshot's diff cites these rows. Where WP04
departed from a row or settled an open case, it is recorded here.

- **Rows 24 and 29 to 32: validate mode's gains are deferred to the one read (WP05, WP09).** Validate mode does not
  yet answer `pack_unreadable` for a pack with no `pack.yaml` (row 24), nor manifest membership, a manifest that is not
  a regular file or an unreadable policy file (rows 29, 30 and 32). Row 31 is already one answer: both surfaces
  report a misplaced preset entry as `artifact_schema_error` in the store's words. On
  today's pipeline each would need a second copy of the storage checks; with validate mode judging the one read's
  verdict they come by construction. Registration answers row 24 at pointer `pack.yaml` already. Row 24 will refuse
  34 bare plants that core accepts (core does not refuse a missing `pack.yaml`): that needs a divergence register
  entry when WP09 lands. The orchestrator accepted the deferral.
- **Row 18: `reference` becomes `artifact_schema_error`,** the code validate mode already answers for the same
  mission-type refusal (a named template or companion the pack does not hold). Open case 4's plant still checks it.
- **Open case 1: a path that escapes through a symbolic link reads `payload assets/<path> does not exist` on both
  surfaces.** The escape wording the case preferred would change registration's answer, which row 13 does not list;
  validate mode reports the read's refusal once, as `asset`, and its escape finding only for a sidecar the read did
  not refuse.
- **A schema keyword with no vocabulary row answers the fixed fallback on both surfaces.** `maxLength` has none, so an
  over-long value reads `<file>: '/id' breaks schema keyword 'maxLength'` (the `pack-id-length` scenarios pin it in
  the composing read's detail). Adding a row is a wording change of its own.
- **Row 19: a companion's own fault is `companion` on both surfaces** (review cycle 1). Validate mode answered a
  Markdown companion that does not parse, is not UTF-8 or breaks its kind's rule as `artifact_schema_error`; it now
  answers `companion`, and a prompt fragment's `prompt_fragment_invalid`, as the registration does. Two differences
  remain for the one read (WP05, WP12): validate mode places the fault at the companion file (`<companion>: <why>`),
  the registration at the artifact that names it (`<artifact>: <companion path>: <why>`); and a companion that breaks
  its structure schema is a schema fault in validate mode (`artifact_schema_error`) but `companion` (`structure:
  ...`) at registration. No golden subject reaches either.
- **Severity is the kernel's.** Validate mode's findings, the golden snapshot and the planted `expected.json` files
  spell a violation `violation` where they said `blocking` (ruling 4, M-8). NFR-002 lists it.
- **Edits outside WP04's owned files** (review cycle 1, MINOR-6), each for the retyping or a row of the table:
  `PackFinding`, `PackFindingCode`, `PackFindingTest` and `ReservedRefusalCodesTest` (WP13's: retyped, the view
  narrowed); `WholePackStore` and `DomainRepositoryService` (WP12's: typed kinds, the `invalid` fallback deleted, the
  descriptor's fault at `pack.yaml`, a repeated id as `duplicate_artifact_id`, a `domain` violation at its file);
  `PackPolicy`, `RelationshipEndpointRule`, `MissionTypeFacts`, `GraphBinding`, `Precedence` and `Wording` (typed
  codes, no pack prefix, the parse codes, one asset finding, the slot warning's token); `PackContents.ParseFailure`
  (an optional code); `FileSystemPackInspector`, `GraphDocumentReader`, `GraphDocumentYaml`, `RelationshipShard`,
  `ManifestMembership` and `DomainRoundTrip` (refusal codes, one reserved-shard and schema wording); about 45 `:packs`
  test classes, `SurfaceAnswers`, `GlossaryPlantedRefusalsTest`, `PrecedenceTest` (a lane-merge import),
  `BuildsFindingDirectly` and `CharterWiringTest`; the scenarios `whole-pack-registration`, `prompt-fragment-refusals`,
  `offering` and `pack-id-length`; `planted/*/expected.json` (WP01's); `ExamplePacksTest` (WP02's) and
  `NotHeldValidateTest` (WP11's); and DIV-027 in `docs/conformance/divergences.yaml` and its register page.
