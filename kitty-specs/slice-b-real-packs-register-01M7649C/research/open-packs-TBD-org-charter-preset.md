# Draft issue for spec-kitty-open-packs: `open-packs#TBD-org-charter-preset`

**Title:** Retire `org-charter.yaml` and `active_charter`: express org enforcement as an activation preset

**Type:** Task · **Filed for:** charter slice B (spec-kitty/spec-kitty-charter#466, ruling on #431)

## Why

The charter service retires `org-charter.yaml` outright, following the operator ruling of 2026-11-11 on
spec-kitty/spec-kitty-charter#431. The file's intent was org-level enforcement. That intent is now expressed with the
mechanism the service already enforces:

- An **activation preset** whose items carry `must_have: true`. Such a preset is a governing preset. The service
  applies its must-have items as the required floor of every project whose closure holds the pack, and a deactivation
  of a floor item is refused with `409 artifact_required`.
- **`policy.yaml`** levels (ADR-0063: `open`, `extendable`, `locked`), where an artifact must not change.

A pack that still ships `org-charter.yaml` is refused at registration by name (`retired`). A `pack.yaml` that still
declares `active_charter` is unreadable, because the descriptor is closed and the key no longer exists. The
registration answers `pack_unreadable` at `pack`, whose detail names the key ("pack.yaml of `<pack>` cannot be read:
… undeclared key 'active_charter'"), and lists none of the pack's other faults until the key is gone. The charter's
how-to is `docs/guides/enforce-org-presets.md` in spec-kitty/spec-kitty-charter. It lands with the charter's slice B
PR (#501).

A dry run of the slice B service against `main` `a6cfeab` ran the registration load (`WholePackStore.loadStaged`,
IC-04 step 2) on every pack. The 12 packs below all fail it today with `pack_unreadable`. With this change and
`open-packs#TBD-readme-doc` applied, all 19 packs pass the registration load with no refusal. With this change alone,
the 35 `pack.md` and `PROVENANCE.md` files of `open-packs#TBD-readme-doc` are still refused.

## Field mapping: `org-charter.yaml` to the charter model

This mapping covers every field of the retired `org-charter` schema. The "Used at `a6cfeab`" column says which
open-packs packs use the field.

| `org-charter.yaml` field | Becomes | Used at `a6cfeab` |
| --- | --- | --- |
| `required_directives`, `required_tactics`, `required_paradigms`, `required_styleguides`, `required_toolguides`, `required_procedures`, `required_agent_profiles`, `required_mission_step_contracts`, `required_glossary_packs`, `required_skills` (each `required_<plural>`) | Must-have items of a governing activation preset, `presets/<id>.yaml`. Each id becomes `{id: <id>, must_have: true}` in the matching `activated_<plural>` list (`activated_directives`, `activated_procedures`, and so on). An id already in the list only gains `must_have: true`. | internal only |
| `required_assets` | Dropped. An asset is not activated, so a preset has no `activated_assets` list. A pack's assets are held with the pack and served by path. | none |
| `activations` (context-scoped entries: `charter_pack_id`, `artifact_id`, optional `artifact_kind`, `activation_context`) | An item of an activation preset, in the `activated_<plural>` list that `artifact_kind` names. Add `must_have: true` only when the entry was meant as a requirement. The `activation_context` (mission type, action) has no equivalent and is dropped: a preset activates for the project, not per action. | none |
| `governance_policies` (`field`, `value`, `enforcement`) | `policy.yaml` levels (ADR-0063), when the policy's intent is that an artifact or kind must not change: `artifacts: {"<kind>:<id>": locked}` (or `extendable` when extending stays allowed), or `kind_defaults: {<kind>: locked}`. Set `mutability_policy: policy.yaml` in `pack.yaml`. An advisory field/value policy with no artifact target is dropped. It was advisory only and no service enforced it. | none |
| `skill_namespace` | Dropped. The charter model has no home for it, and the charter service never read it (it was the reserved key of spec-kitty/spec-kitty-charter#381, which closes with this change). If core still needs a render prefix for a pack's skills, raise it with core when core takes up the charter's model. Do not keep it in a pack file. | internal (`kitty`) |
| `org_name` | Dropped. `pack.yaml`'s `name` names the pack. | all 12 |
| `schema_version` | Dropped with the file. | 11 (internal has none) |
| `extends` | Dropped. A pack builds on another through `pack.yaml` (`parent_pack`, `depends_on`). | none |
| `interview_defaults` | Dropped. They are core's charter-interview answers, and the charter service has no interview model. | none |

## What

Twelve packs ship an `org-charter.yaml` and name it as `active_charter`. This is the state at `main` `a6cfeab`, the
same as at `faa4770`.

| Pack | Fields | Work |
| --- | --- | --- |
| cave-kitty, drupal, event-driven, go, java, javascript, pack-authoring, python, qa, rust, spdd | `schema_version`, `org_name` only. Deliberately inert ("activates nothing on its own"). | Delete `org-charter.yaml` and `active_charter`. No preset is needed. |
| internal | `org_name`, `skill_namespace: kitty`, `required_procedures` (7), `required_directives` (3), `required_glossary_packs` (1), `required_toolguides` (2), `required_skills` (5) | Convert as described below. |

### internal

`internal`'s lists were checked against the pack at `a6cfeab`. All 18 ids are held in `internal` itself, so the preset
needs no cross-pack reference.

1. **Extend the governing preset `presets/base.yaml`.** It already holds `NO_FULL_HEAVY_SUITES_IN_MISSION` and
   `SPEC_KITTY_DOCS_CONVENTIONS` as must-haves. Mark every item of the `required_*` lists `must_have: true`:

   ```yaml
   schema_version: "1.0"
   id: base
   description: The required floor of every Spec Kitty core-team project that registers this pack.
   activated_directives:
     - id: NO_FULL_HEAVY_SUITES_IN_MISSION
       must_have: true
     - id: SPEC_KITTY_DOCS_CONVENTIONS
       must_have: true
     - id: CLOUD_SESSION_ORCHESTRATION
       must_have: true
   activated_procedures:
     - id: landing-contributor-prs
       must_have: true
     - id: project-evolution-postmortem
       must_have: true
     - id: memory-curation-and-escalation
       must_have: true
     - id: executive-debrief-generation
       must_have: true
     - id: test-suite-quality-assessment
       must_have: true
     - id: cloud-session-dispatch
       must_have: true
     - id: semi-auto-delivery
       must_have: true
   activated_glossary_packs:
     - id: spk-internal-glossary
       must_have: true
   activated_toolguides:
     - id: terminology-guard
       must_have: true
     - id: test-quality-triage
       must_have: true
   activated_skills:
     - id: land-pr
       must_have: true
     - id: issue-triage
       must_have: true
     - id: mission-from-issue
       must_have: true
     - id: report-debrief
       must_have: true
     - id: semi-auto
       must_have: true
   ```

   Rewrite the file's header comment (lines 1 to 4). It now carries the org's required floor, not a mirror of
   `required_directives` in `org-charter.yaml`. The dry run's registration load accepted `internal` with exactly this
   preset.
2. **Add `policy.yaml` only where the intent was "must not change".** None of `internal`'s lists says so. A
   `required_*` list is a floor, and the must-have mark already enforces a floor. If the team wants the required
   directives locked against edits, add `policy.yaml`:

   ```yaml
   schema_version: 1
   artifacts:
     "directive:CLOUD_SESSION_ORCHESTRATION": locked
   ```

   Then set `mutability_policy: policy.yaml` in `pack.yaml`.
3. **Drop `skill_namespace: kitty`**, as the mapping above says. The README's layout tree, its "Pack skills" section
   and the skill comments use the `kitty-<id>` names (`kitty-land-pr` and so on). That is core's rendering, so say so,
   or drop the prefix.
4. **Delete `org-charter.yaml` and the `active_charter` line.** Also update:
   - `pack.yaml`'s header comment (lines 6 to 9, "a single drg/fragment.yaml and an org-charter.yaml active charter …
     the active-charter and DRG pointers");
   - the README layout tree: the `org-charter.yaml` line (line 24) and the `presets/base.yaml` comment (line 40,
     "must-have items mirror required_directives");
   - the README "Pack skills" section (line 149), "`required_skills` puts all five in force for every project that
     registers this pack": the `base` preset's must-have items do that now.

### Every other pack in the table

1. Delete `org-charter.yaml` and the `active_charter: org-charter.yaml` line.
2. Rewrite the `pack.yaml` header comment that says the pack "carries a single drg/fragment.yaml and an
   org-charter.yaml active charter". This affects cave-kitty, drupal, event-driven, java, javascript, pack-authoring,
   python and qa.
3. Fix the text that names the org charter:
   - **go, rust.**
     - README: "The pack's `org-charter.yaml` activates nothing, so registering the pack alone changes nothing" becomes
       "Registering the pack activates nothing on its own".
     - Preset description in `presets/go.yaml` (lines 5 to 6) and `presets/rust.yaml` (lines 5 to 6): "The pack's org charter activates
       nothing, so this preset is the opt-in" becomes "Registering the pack activates nothing, so this preset is the
       opt-in".
     - `pack.md`: "the pack's org charter activates nothing on its own". `open-packs#TBD-readme-doc` drops that
       paragraph. If this change lands first, give it the same wording as the README.
   - **event-driven.** The README's advice "list them in the consuming org's charter: `required_directives: [EVD_001,
     …]`" becomes: to put the rules in force for every project, add a governing preset to the org's own pack, with
     `activated_directives` items marked `must_have: true`. Without one, the pack's `event-driven` preset stays opt-in
     per project.
   - **qa.** Make the same change to "add to the consuming org's charter: `required_directives: [QA_007, QA_008]`,
     `required_procedures: [qa-description-to-acceptance-ci]`" and to the known-limitations bullet "Put the directives
     in force through `required_directives` in your org charter".

### Repository changes

1. **`packs/README.md`.** The layout tree (line 33) lists `org-charter.yaml` as "optional policy / required
   artifacts". The canonical root holds `pack.yaml`, `README.md` and, optionally, `policy.yaml`: show those. Line 46
   says the bundles have "no `org-charter.yaml`". No pack has one now, so drop the remark.
2. **`tests/fixtures/valid/example-pack/`.** Delete `org-charter.yaml` and the `active_charter` line, and rewrite the
   header comment (line 5). The fixture is the canonical shape and must not keep the retired one.
3. **`scripts/ci/charter_service_check.py`.** Remove `org-charter.yaml` from `NOT_REGISTERED`. Update
   `tests/ci/test_charter_service_check.py`.
4. **Any other CI, validator or script that reads `org-charter.yaml` or `active_charter`.** At `a6cfeab`,
   `scripts/ci/validate_pack_schemas.py` names neither. It takes the file rules from the pinned charter, so the
   `retired` rule reaches it with the `charter.lock.yaml` bump. Check again at the time of the change.
5. **Generated pack pages.** `docs/packs/go.md` (line 35) and `docs/packs/rust.md` (line 34) render the `pack.md`
   preamble, org-charter sentence included. After the text changes, run `python3 scripts/docs/gen_pack_reference.py`
   and commit `docs/packs/*.md`. Both `validate-packs.yml` (line 75) and `docs-site.yml` (line 125) run
   `gen_pack_reference.py --check`, which fails on any drift.
6. **Every other file outside ADRs and the changelog that presents `org-charter.yaml`, `active_charter` or the
   `required_<kind>` lists as live.** At `a6cfeab` these are:
   - the root `README.md`, lines 102 to 104, which say an org pack's `org-charter.yaml` makes items mandatory
     (`required_<kind>`) and namespaces the pack's skills;
   - `docs/packs/index.md`, line 51 ("an `org-charter.yaml` declaring activation defaults") and
     `docs/packs/catalog-proposal.md`, lines 233 and 248;
   - `docs/architecture/docsite-design.md`, lines 46 and 59, and `docs/architecture/overview.md`, lines 58 and 71;
   - `docs/guides/author-a-charter-pack.md` (the layout tree at line 44 and the section from line 188),
     `use-a-charter-pack.md` (lines 68 and 82), `compose-packs.md` (line 69, an `active_charter` example) and
     `validate-a-pack.md` (line 124);
   - `docs/contracts/index.md` (the layout tree at line 25 and the "`org-charter.yaml` — org policy" section from
     line 92), `docs/organisations/index.md` (lines 41 to 60, 75 and 88), `docs/glossary/index.md` (the "Org charter"
     entry, line 24) and `docs/research/docsite-feasibility-and-generator.md` (lines 31, 59 and 119);
   - `docs/artefacts/index.md`, lines 121 to 123 (a pack that ships skills needs a
     `skill_namespace`);
   - `docs/_meta/sections.yaml`, lines 31 and 37;
   - `.kittify/charter/charter.md` (line 58) and the comments in `.kittify/config.yaml` (lines 27, 28 and 33);
   - `packs/foundation/glossary_packs/spec-kitty-core.glossary-pack.yaml`, line 996: the "Charter Pack" definition
     says an org pack "may also enforce activations through required_<kind> lists in its org-charter.yaml". This
     glossary is seeded from core, so agree the new wording with core before changing it.

   Point org enforcement at the charter's `docs/guides/enforce-org-presets.md`. ADRs and the changelog keep their
   history.
7. **Tree digests.** Run `python3 scripts/ci/pack_digest.py --update`.

### Order

This change can land before or after `open-packs#TBD-readme-doc`, and before the `charter.lock.yaml` bump: a charter
older than slice B treats `org-charter.yaml` and `active_charter` as optional. Every pack registers only when both
changes and the bump are in.

## Acceptance

- [ ] No pack under `packs/` and no fixture holds `org-charter.yaml`, and no `pack.yaml` declares `active_charter`.
- [ ] `internal`'s `presets/base.yaml` lists the 18 items above, each with `must_have: true`.
- [ ] No file in the repository outside ADRs and the changelog presents `org-charter.yaml`, `active_charter`,
      `required_<kind>` lists or `skill_namespace` as a live mechanism. The core-seeded glossary entry is changed
      or tracked with core.
- [ ] `check-pack-digests.sh`, the validators and `gen_pack_reference.py --check` pass.
- [ ] Open-packs CI, run against the charter release that carries slice B, registers every pack with no `retired`
      violation and no `pack_unreadable` refusal for these.

## Related

- spec-kitty/spec-kitty-charter#431 (ruling), #381 (closed with the schema), #466 (slice B), and the charter's slice B
  PR #501, which carries `docs/guides/enforce-org-presets.md`.
- `open-packs#TBD-readme-doc` is the other half: `pack.md` and `PROVENANCE.md`. Both are needed for every pack to
  register.
- Spec Kitty core still reads `org-charter.yaml` today. After this change, core no longer sees `internal`'s
  `required_*` lists or `skill_namespace`, until core aligns with the charter's model through the generated client.
  Projects that run `internal` through core should expect that gap.
- open-packs #201 (built-in `policy.yaml`) and #202 (the `jvm` tag, possibly settled by open-packs PR #198) are not
  part of this change.
