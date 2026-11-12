---
title: Enforce presets on an organization's projects
description: "How an org pack puts artifacts in force for every project with a governing preset and locks them with policy.yaml, and how to convert an org-charter.yaml."
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0031-requirement-is-authored-as-a-preset
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0063-pack-mutability-policy-file
  - architecture/offering-and-activation
  - architecture/descriptors
  - guides/compose-packs
audience:
  - profile: governance-lead
    guidance_text: Follow this to make an artifact required, or unchangeable, for every project that uses your organization's pack.
  - profile: pack-author
    guidance_text: Follow this to write a governing preset and a policy file, or to convert a pack that still ships org-charter.yaml.
---

# Enforce presets on an organization's projects

This page is for whoever runs an organization's pack and wants every project that uses it to keep certain artifacts
in force, or unchanged. It shows the two files that do it, a governing preset and `policy.yaml`, how a project takes
the preset, and how to convert a pack that still ships `org-charter.yaml`.

`org-charter.yaml` and the `active_charter` key of `pack.yaml` are retired (#431). A pack that ships the file is
refused at registration with violation code `retired`. A `pack.yaml` that declares the key cannot be read, because
the descriptor is closed: the pack is `pack_unreadable`, and a registration answers `422 validation_failed` with one
`pack_unreadable` violation at `pack`, whose detail names the undeclared key (`undeclared key 'active_charter'`).
The pack's other faults show only once the key is removed. There is no fallback reader.

## What each file does

| You want | Write | The service answers |
|---|---|---|
| An artifact always in force for every project whose closure holds the pack | A **governing preset**: an item with `must_have: true` in `presets/<id>.yaml` | The item is on the closure's **required floor**: it is in force, and a deactivation of it is `409 artifact_required`, naming the requiring pack in `requiredBy` |
| An artifact (or a whole kind) no project may change or deactivate | `policy.yaml` with level `locked` (or `extendable`, which still allows extending) | A deactivation of a locked artifact is `409 artifact_locked`; any write to it (replace, extend or relate) is `409 governance_policy_violation` |
| A one-step way for a project to select a set | Any preset, governing or not | `PUT /activations/{activationId}/presets/{presetId}` selects its items |

A preset with at least one must-have item is a governing preset; one with none is a convenience preset. Nothing else
marks it ([ADR-0031](/adr/0031-requirement-is-authored-as-a-preset/),
[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)). The two levers are independent:
required says "always present", locked says "never changed" ([ADR-0063](/adr/0063-pack-mutability-policy-file/)).

## 1. Write the governing preset

An org pack declares `pack_level: org` in `pack.yaml`. Add `presets/<id>.yaml`; its `id` is its file stem. Each
`activated_<plural>` list holds items `{id}`; mark a required item with `must_have: true`. There is no `false`
spelling, and a list holds at least one item.

Charter's example pack `org-base` ships one
(`service/packs/src/testFixtures/resources/example-packs/org-base/presets/spdd.yaml`), shortened here:

```yaml
schema_version: "1.0"
id: spdd
description: >-
  Put Structured-Prompt-Driven Development and the REASONS canvas workflow in force.
activated_paradigms:
  - id: structured-prompt-driven-development
activated_directives:
  - id: DIRECTIVE_038
activated_step_enhancements:
  - id: spdd-specify
    must_have: true
  - id: spdd-plan
activated_skills:
  - id: issue-triage
    must_have: true
  - id: triage-status
```

`spdd-specify` and `issue-triage` are on the required floor of every project whose closure holds `org-base`. The
other items are offered, not required.

The floor is closure-scoped: only the presets of the packs in the project's closure count, and a must-have item that
no pack of the closure holds is reported (`requiredNotHeld`) and never put in force. When two packs of one level hold
the same preset id, the copy of the pack last in closure order governs, and the copy it replaces requires nothing
([Offering and activation](/architecture/offering-and-activation/)).

## 2. Lock what must not change

Add `policy.yaml` at the pack root and name it in `pack.yaml` with `mutability_policy: policy.yaml`:

```yaml
schema_version: 1
kind_defaults:
  directive: extendable
artifacts:
  "directive:DIRECTIVE_038": locked
```

`kind_defaults` sets a level per kind; `artifacts` sets one per artifact, keyed by its bare URN. A target's level is
its `artifacts` entry, else its kind default, else `open`. Along a closure the highest level wins. The schema is closed:
a key it does not declare makes the pack unreadable ([ADR-0063](/adr/0063-pack-mutability-policy-file/)).

## 3. A project takes the preset

The required floor applies as soon as the project's activation document names a pack whose closure holds the org
pack; the project need not apply the preset. To also select the preset's other items, the project applies it:

```http
PUT /api/v1/activations/{activationId}/presets/spdd
If-Match: "<activation revision>"
Content-Type: application/json

{}
```

The body is required; `{}` takes the defaults.

Each key the preset lists replaces that key of the document. A preset id no pack of the closure holds is
`404 preset_not_found`. The write needs `If-Match` and answers the new revision as its `ETag`. With `force: true` in the body
the deny entries of the preset's members are cleared and reported in `cleared`.

After that, a project that sends a document denying `issue-triage` is refused:

```text
409 artifact_required
requiredBy: [{ packId: org-base, ... }]
```

## Convert an `org-charter.yaml`

Each field of the retired file maps to the charter model, or is dropped:

| `org-charter.yaml` field | Becomes | Why |
|---|---|---|
| `required_<plural>` (`required_directives`, `required_procedures`, `required_skills` and the rest) | Items with `must_have: true` in the matching `activated_<plural>` list of a governing preset | A requirement is authored as a preset (ADR-0031); the floor is derived from the marks |
| `required_assets` | Dropped | An asset is not activated, so a preset has no `activated_assets` list; assets are held with their pack |
| `activations` | Items of a preset in the `activated_<plural>` list that `artifact_kind` names; `must_have: true` only where the entry was meant as a requirement | A preset activates for the project; `activation_context` (mission type, action) has no equivalent and is dropped |
| `governance_policies` | `policy.yaml` levels, where the intent is that an artifact or kind must not change | Locking is the mutability level (ADR-0063); an advisory policy with no artifact target was enforced by nothing and is dropped |
| `org_name` | Dropped | `pack.yaml`'s `name` names the pack |
| `extends` | Dropped | A pack builds on another through `pack.yaml` (`parent_pack`, `depends_on`) |
| `interview_defaults` | Dropped | They are core's charter-interview answers; the service has no interview model |
| `skill_namespace` | Dropped | The charter model has no home for a render prefix, and the service never read it (#381) |
| `schema_version` | Dropped with the file | |

Then delete `org-charter.yaml`, and delete the `active_charter` line from `pack.yaml`. An org charter that only named
the organization, as most did, needs no preset at all.

## Check it

- Register the pack (`PUT /packs/{packId}` or `POST /packs`, [Compose packs](/guides/compose-packs/#register-a-pack)).
  A leftover `org-charter.yaml` is `422` with a `retired` violation at `org-charter.yaml`; a leftover `active_charter`
  is `422` with one `pack_unreadable` violation at `pack` whose detail names the undeclared key.
- Read the project's effective artifacts (`GET /activations/{activationId}/artifacts?state=active`): each held
  must-have item is listed, whether or not the project selected it.

## See also

- [Descriptors](/architecture/descriptors/): the `pack.yaml`, preset and policy fields.
- [Offering and activation](/architecture/offering-and-activation/): the floor, `requiredBy` and the write order.
- [Compose packs](/guides/compose-packs/): registering a pack and reading a refusal.
