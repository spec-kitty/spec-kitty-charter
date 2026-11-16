### Breaking

- A refused registration (`422 validation_failed`) names each fault of the pack by its specific code, in the one
  wording validate mode uses for the same fault (#554, operator ruling R2, the R2 table in
  `docs/development/notes/pack-code-unification.md`). A detail names the file pack-relative and is never prefixed with
  the pack's name. The violation codes change as follows (old → new):
  - `schema` → `artifact_schema_error` for an artifact file that breaks its schema or a rule of its kind (a file stem,
    a preset entry's path in the manifest), and the detail names the key in validate mode's words: `pattern violated at
    /role` becomes `'role' does not match its pattern`, `required violated at the document` becomes `missing
    'explicit_allowances'`, `additionalProperties violated at /x: undeclared key 'k'` becomes `unknown key 'k'` (rows 2,
    3 and 31). A graph document's faults keep `drg_schema_error` and take the same vocabulary: `edge endpoint 'X' is not
    a URN`, `node directive:X declares kind 'tactic'`, `node x has unknown kind 'mission_type'` (row 4). A schema fault
    is one violation per fault, and two identical faults at one place are one.
  - `domain` → `artifact_schema_error`, at the artifact's file, not at its id (row 17).
  - `reference` → `artifact_schema_error`, for an artifact that names a file, a template or a guard reference the pack
    does not hold (row 18; the kept code is checked against a plant of its own before the slice lands, open case 4).
  - `template` → `duplicate_artifact_id`, for a template read twice; the detail is unchanged (row 16).
  - A repeated artifact id was the aggregate's `validation_failed` at pointer `pack` (`Constraint breached: A pack must
    not hold two tactics with the same identifier.`); it is now `duplicate_artifact_id` at the second file, `<file>:
    tactic id 'same' is already declared in <first file>` (row 10). The composing read answers the same fault as `500
    pack_unreadable` naming the file, where it answered a 500 with no detail (operator ruling S5).
  - A `pack.yaml` that is missing, is not YAML or breaks its schema is still `pack_unreadable`, now at pointer
    `pack.yaml` instead of `pack`, in validate mode's words: `pack.yaml of qa: unknown key 'mystery'`, `pack.yaml of qa
    has no pack_version`, `pack.yaml of qa is not valid YAML: <parser line>` and `pack qa has no pack.yaml` (rows 22 to
    24). The refusal's `detail` and the composing read's `pack_unreadable` detail carry the same wording.
  - A violation code is always a typed code's token: the `invalid` fallback for a kind that was no snake-case token is
    gone (row 27).
- A composing read's `500 pack_unreadable` detail embeds the fault's new wording (`the file <f> of qa cannot be read:
  'role' does not match its pattern`); its code and its choice of fault are unchanged.
- Core's seam: a client that matched `schema`, `domain`, `reference`, `template` or `invalid` reads the codes above; a
  client that read the pack's descriptor fault at pointer `pack` reads it at `pack.yaml`.
- Scenarios rewritten (9): `registration/whole-pack-registration.feature` (the retired `active_charter` key: pointer
  `pack.yaml`, `unknown key 'active_charter'`), `prompt-fragment-refusals.feature` (three step enhancements that break
  their schema or stem: `artifact_schema_error`), `offering.feature` (an agent profile's role pattern, in the composing
  read's detail) and `pack-id-length.feature` (four over-long values, in the composing read's detail: `'/id' breaks
  schema keyword 'maxLength'`, the fixed fallback of a keyword the vocabulary has no row for).
