# Data Model: pack-doctor coverage

## Preset item fault (domain, `ClosureFault`)

| Field | Value |
|---|---|
| reason.code | `preset_item_not_held` (a `must_have` item that the own closure does not hold as a file) or `preset_item_undeclared` (an item that the own closure does not declare as its kind) |
| reason.detail | names the preset, the item `<kind>:<id>`, whether it is required, and the own closure's packs in order |
| packs | the declaring pack first |
| violations | one, pointing at `presets/<id>.yaml` and the item: `/activated_<kinds>/<index>` |

Invariants:

- Each preset item gets at most one fault.
- The case-only answer precludes a preset-item fault for the same item.
- An item of another kind never satisfies the entry.

The own closure is the declaring pack plus everything it reaches over `depends_on` and `parent_pack`
(`PackCompositionResolver.compositionClosure` rooted at the pack). Held items are the closure packs' artifact files
(`PackCompositionFacts.held`), so an `anti_pattern` is held only by its anti-pattern file. Declared items are every
graph-node URN plus the held items (`PackCompositionFacts.declared`). These two fields replace `defined` outright.

## Anti-pattern file (pack content, `anti_patterns/<id>.anti-pattern.yaml`; plan D12, FR-013; ruling R-r)

The file form of the existing `anti_pattern` kind (ADR-0052 decision 8, as amended by ADR-0053 decision 30). Schema
`contracts/packs/schemas/anti-pattern.schema.json`, closed (`additionalProperties: false`).

| Field | Required | Shape |
|---|---|---|
| `schema_version` | yes | `"1.0"` |
| `id` | yes | kebab-case, `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64; equal to the file stem |
| `title` | yes | string, not blank |
| `description` | yes | string, not blank |
| `severity` | yes | `smell` (worth a remark), `severe` (should block review) or `breaking` (must block); no default |
| `bad_example` | yes | string, not blank |
| `good_example` | no | string |

Domain: `AntiPattern` (a `CharterArtifact` of `ArtifactKind.ANTI_PATTERN`, URN `anti_pattern:<id>`), its
`AntiPatternIdentifier` and the closed `AntiPatternSeverity`. Storage: `AntiPatternBinding` (`fileToken` and
`schemaName` `anti-pattern`; `fileName` `anti_patterns/<id>.anti-pattern.yaml`; `idIsFileStem` and
`placedAtItsFileName` true), `AntiPatternDso` (a flat mirror, record equality is the round-trip assertion) and
`AntiPatternYaml` (schema key order), each appended last to its registry.

Invariants:

- The file is the only form of an anti-pattern, and it holds the artifact. A graph node of the same URN in the same
  pack declares it and is an edge endpoint; it holds nothing. A node without the file is refused (below).
- A file whose `id` is not its stem, or that is not at `anti_patterns/<id>.anti-pattern.yaml`, is refused by the
  existing stem and placement rule (`FileStems`).
- A schema breach is `artifact_schema_error` at the file and the breaching pointer, the existing answer of the one
  validated read: a missing `severity` (no default, ADR-0053 d32) or `bad_example` (ADR-0053 d30), an unknown
  `severity`, an unknown key.
- Guidance rejects it with a `rejects` edge in its own pack's graph documents (ADR-0064 d12). Across packs the
  target is pack-qualified and its pack is in the rejecting pack's `depends_on` (`pack_reference_pack_missing`
  otherwise, ADR-0052 d11).
- The kind is activatable; activating guidance that rejects it does not activate it (`rejects` is no cascade
  relation, ADR-0052 d13). It is served by no HTTP read, as `step_enhancement` is.

## File-less `anti_pattern` node (domain content rule, `GraphRules`; plan D13, FR-014)

| Field | Value |
|---|---|
| code | `drg_schema_error` (`PackCodes.DRG_SCHEMA_ERROR`, VIOLATION; an existing contract code) |
| location | the graph document (a `*.graph.yaml` shard or the fragment), pointer `/nodes/<n>` |
| detail | `<origin>: anti_pattern:<id> is declared with no file; an anti-pattern is held by anti_patterns/<id>.anti-pattern.yaml in the same pack` |

Invariants:

- One finding per `anti_pattern` node of the pack's own graph documents whose URN the pack holds no file for, in
  document order (shards, then the fragment), after the other graph rules.
- A pack-qualified edge endpoint is no node, so it is never judged here.
- `action` nodes are unaffected (a mission type step is a value of its type's file).

## Inline `anti_patterns` block (storage edge, closed schema; plan D14, FR-015)

| Field | Value |
|---|---|
| code | `artifact_schema_error` (the schema answer, `Wording.codeFor`) |
| location | the styleguide or procedure file, pointer the root |
| detail | `<origin>: unknown key 'anti_patterns'` |

The procedure and styleguide shapes have no `anti_patterns` (pack schemas) and no `antiPatterns` (charter contract:
`Styleguide`, `StyleguideRegistration`, `Procedure`). `AntiPatternEntry` and `StyleguideAntiPattern` do not exist.

## `manifest_stale` (storage edge, `Reason`)

| Field | Value |
|---|---|
| code | `manifest_stale` (`StorageCodes`, VIOLATION) |
| location | `pack-manifest.yaml`, pointer: the stale field (`constituents{<path>}.content_hash`, `presets{<path>}.content_hash`, `manifest_hash`) |
| detail | the field and the path whose bytes it does not match |

Invariants:

- Judged on incoming bytes only.
- A path that `manifest_membership` already names gets no `manifest_stale`.
- A written-stale round-trip red is a separate cause.

## `--full` phase (domain; plan D5, ruled R-s)

The phase's faults are the existing codes, from the existing collect-all functions, in registration's phase order:

1. delegations (`PackCompositionResolver.unresolvedDelegations`): `pack_unreadable` with the violation
   `substep_delegation_unresolved` at the mission type file;
2. references (`PackCompositionResolver.referenceFaults`): `pack_reference_unqualified`,
   `pack_reference_pack_missing`, `pack_reference_artifact_missing`;
3. enhancement targets (`EnhancementCompose.targetFaults`): `pack_reference_pack_missing`,
   `pack_reference_artifact_missing`.

Every fault is collected, and each pack is judged within its own closure. `PackSetVerdict.Check` gains one value for
the phase, named per the operator's answer to analysis Q2 (recommended `REFERENCES`). The `specializes_from` cycle,
relations, edge-index and own-closure-walk checks are not part of it (#636 slices 2-4 and 7).

## pack-doctor report (`PackDoctorReport`: JSON on stdout, YAML or XML with `--report`)

One tree, the existing stdout problem JSON renamed, with three new top-level keys:

```
tool: {name: pack-doctor, version}            # new
inputs: {config_root | packs: [...], options: {full, round_trip}}   # new
checks: [PACK, CLOSURE, ROUND_TRIP?, ...]     # as today, plus the --full value (D5)
packs: [...]                                  # as today
skipped: [...]                                # as today
problems: [Problem]                           # Problems.Problem, verbatim
summary: {packs, violations, warnings, notices, exit_code}   # new
```

`PackDoctorReport.yaml` (JSON Schema) is the only normative schema. The XML is an element-per-key mapping of the same
tree, and is validated by parsing it back and checking it against the same schema.

## remediate outcome

For each pack, remediate reports one of:

- `rewritten`: the manifest and the files written in canonical form;
- `refused`: the storage verdict's violations, except the manifest gate and form findings, with nothing written;
- `unchanged`: already canonical.

After writing, it validates again and reports the result.
