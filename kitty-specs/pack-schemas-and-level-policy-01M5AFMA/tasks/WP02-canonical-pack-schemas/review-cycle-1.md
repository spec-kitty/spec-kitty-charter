---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T18:28:53Z'
reviewer_agent: claude
wp_id: WP02
---

# WP02 review feedback (cycle 1) — reviewer-renata

Re-verified and fine: `sync_schemas.py --check` is clean (14 schemas reproduce from core 9554e7b2).
`--probe` on open-packs 0b19a83 gives mapped=409, excluded=107, unclassified=0, invalid=0. The
38-sample harness reports 0 unexpected. All 14 files pass Draft 2020-12 `check_schema`. The 14
`$id`s are unique, use the right prefix and end in `.json`, and every `$ref` is local and
resolves. The raw-export→committed diff matches VENDORED.md C1-C16 with nothing undocumented.
Kind lists come from file-kinds.yaml, and node_kinds equals NodeKind.java. C11 `minItems: 1`
matches core `directives/models.py:135` (`not self.explicit_allowances`). markdownlint passes.
Scope: the WP commit d087036 touches only contracts/packs/** and scripts/packs/sync_schemas.py.

## Issue 1 (BLOCKING): `pack_version` accepts null and the empty string (R4)

R4 says `pack_version` is required at every level. The orchestrator ruled that it must be a
non-null, non-empty string. `contracts/packs/schemas/pack-descriptor.schema.json` keeps
`pack_version` as `anyOf [string, null]` with `default: null`. With it in `required`,
`{"pack_id": "...", "name": "x", "pack_version": null}` VALIDATES, and so does
`"pack_version": ""`. Both checked by hand with jsonschema 4.26.

Fix:
- `scripts/packs/sync_schemas.py`, `c9_pack_descriptor`:
  - Assert the premise that the export has `anyOf [{"type":"string"},{"type":"null"}]` with
    `default: null`.
  - Replace the property with `{"title": "Pack Version", "description": ..., "type": "string",
    "minLength": 1}`, with no `anyOf` and no `default`.
  - Keep the ledger entry.
- `contracts/packs/VENDORED.md` C9: record the change ("pack_version is a non-null string with
  minLength 1; the export's nullable anyOf and null default are removed, an exception to C13").
  - Remove the sentence "`pack_version` stays `anyOf [string, null]` ... still validates".
  - Remove the "C9: required and nullable at once" bullet under "Open points".
  - Add one line to C13 noting this exception.
- Regenerate. Confirm that `--check` is clean and that `--probe` still gives 409/107/0/0 (both
  open-packs pack.yaml files have `pack_version: 1.0.0`).
- Add samples to the scratch harness: `pack_version: null` (raw valid, new refused) and
  `pack_version: ""` (raw valid, new refused).

## Issue 2 (required wording fix): in_tension_with must read as intentional, not open

`contracts/packs/VENDORED.md`, "Open points", C7 bullet: it currently says "The WP05 equality test
must treat it as a known difference or the service must add it". That leaves it undecided.
Orchestrator ruling: this is an accepted, intentional difference. Reword the bullet, and C7's
last sentences, to state the following:
- The schema keeps core's legacy `in_tension_with` (16 values) on purpose, so existing graph
  documents still validate.
- The service's `Relation.java` has 15 values. Its reader reads `in_tension_with` as `impacts`
  with strength -1 (see GraphDocumentReader and the OpenAPI relation description).
- WP05's equality test must treat it as the one known difference (schema = Relation.java +
  {in_tension_with}).

Move it out of "Open points" (for example to a "Known differences" heading) so it is not read as
pending.

Anti-pattern checklist: dead code N/A (script and contract data; consumers are WP04/05/08);
synthetic-fixture PASS (probe validates the real corpus); silent empty return PASS; FR coverage
FR-005 FAIL on Issue 1 (R4 not fully enforced); frozen surface PASS; locked decision PASS;
shared-file ownership PASS (lane-b alone); production fragility PASS (every SyncError raise
is a documented fail-loud premise check).
