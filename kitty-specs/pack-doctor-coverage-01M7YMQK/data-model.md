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
(`PackCompositionFacts.held`). Declared items are every graph-node URN plus the held items
(`PackCompositionFacts.declared`). These two fields replace `defined` outright.

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

## `--full` phase (domain) — held pending the operator's ruling (plan D5)

The phase's faults are the existing codes:

- `pack_reference_unqualified`;
- `pack_reference_pack_missing`;
- `pack_reference_artifact_missing`;
- `specialization_cycle`.

It runs in this order: references, enhancement targets, cycle. Every fault is collected, and each pack is judged
within its own closure. `PackSetVerdict.Check` gains `CLOSURE_WALK`.

## pack-doctor report (`PackDoctorReport`: JSON on stdout, YAML or XML with `--report`)

One tree, the existing stdout problem JSON renamed, with three new top-level keys:

```
tool: {name: pack-doctor, version}            # new
inputs: {config_root | packs: [...], options: {full, round_trip}}   # new
checks: [PACK, CLOSURE, ROUND_TRIP?, ...]     # as today, plus the --full value if ruled
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
