### Breaking

- Validate mode (`PackValidation`, the pack doctor's check) answers each fault with the code and the wording the
  registration answers for it (#554, operator ruling R2, the R2 table in
  `docs/development/notes/pack-code-unification.md`). Its findings change as follows (old → new):
  - A detail names the file pack-relative and no longer opens with the pack's name: `qa/tactics/a.tactic.yaml: 'role'
    does not match its pattern` becomes `tactics/a.tactic.yaml: 'role' does not match its pattern`, for every schema,
    graph, endpoint, mission-step, substep-delegation and prompt-fragment finding (rows 2, 4 to 8 and 20). The
    descriptor's findings keep `pack.yaml of qa`.
  - An artifact file that is not YAML or not UTF-8: `artifact_schema_error`, `qa/<file> is not valid YAML: <why>` →
    `unreadable`, `<file>: <why>` (row 1).
  - A glossary context whose id is not its file stem: `'id' has an unsupported value 'b'` → `glossary id 'b' is not its
    file stem 'a'`, the store's words; a misnamed artifact, a misnamed preset and a manifest preset entry at the wrong
    path read the same way (rows 3 and 31).
  - A retired file: `artifact_schema_error`, `qa/<file> is retired: <why>` → `retired`, `<file>: <why>` (row 11).
  - An asset sidecar whose payload is missing: `artifact_schema_error` → `asset`, `<file>: payload <path> does not
    exist` (row 12).
  - An asset path that escapes `assets/`: the two findings `artifact_schema_error` and `asset_path_escape` → one
    `asset`, `<file>: asset path '<p>' escapes the pack's assets/ root` (row 13). The code `asset_path_escape` is no
    longer answered.
  - A slot that holds text in a pack whose descriptor declares no `schema_version`: the warning
    `prompt_slot_not_empty_unversioned` → the warning `prompt_slot_not_empty`, the registration's diagnostic (row 21).
  - A `registered.graph.yaml` no registration wrote: `drg_schema_error` → `reserved_shard`, in the registration's words
    (row 28).
  - A `pack.yaml` that is not YAML: `pack.yaml of qa is not valid YAML` → `pack.yaml of qa is not valid YAML: <parser
    line>` (row 23, open case 5).
  - A finding's severity is the kernel's: `blocking` is spelled `violation`.
- Core's seam: a client of the pack doctor that matched `asset_path_escape`, `prompt_slot_not_empty_unversioned` or
  `artifact_schema_error` for a parse failure, a retired file or an asset payload reads the codes above.
