---
title: Divergence register
description: The single register of every deliberate difference between the charter service and core, each with a stable id, the ruling behind it and the case or evidence that pins it.
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: API
kind: Reference
related:
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0016-conformance-corpus-harness-over-core-cli
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0018-action-context-read-and-seam-access-surface
  - api/service-contract
audience:
  - profile: contract-consumer
    guidance_text: Read this to know where the service answers differently from core on purpose, and how the seam maps each difference.
  - profile: charter-service-contributor
    guidance_text: Read this before changing a walk rule or a pack rule, and add an entry here when an answer differs from core on purpose.
  - profile: pack-author
    guidance_text: Read this to see where a pack is judged differently from core's validator.
---

# Divergence register

This is the single register that [ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/)
decision 4 means (operator ruling R10, 2026-10-19). It lists where the service answers
differently from core's Python charter on purpose, and why. Every such difference is reviewed
and recorded here, never hidden in a hand-edited expected answer. The conformance gate (#72)
consults this register. Its format stays #72's. Every entry has a stable id, and ids are never
reused.

Entries are grouped by block, one block per mission, so that two missions never share an id:

- **Context read (DIV-101..110, ADR-0018)** is below.
- **Pack schemas (DIV-001..025, ADR-0017)** is below, after the context-read block. DIV-001 and
  DIV-002 are withdrawn: they described the same differences as DIV-101 and DIV-102 and are
  recorded there.
- **Activation (DIV-201..244, ADR-0020)** is below. DIV-201 and DIV-202 were proposed until the effective reads were
  built and are open since PR B; DIV-203 to DIV-207 are open, because the register, the closure read and the
  per-artifact writes are built. DIV-216 to DIV-237 are PR B's: the layer merge and the effective reads. DIV-239 to DIV-244 are PR C's (DIV-238 is claimed
  by the step-enhancements mission): the graph, the action context and the rulings of 2026-11-09.
- **Pack-level files (DIV-301..399, ADR-0042)** is after the activation block.
- **Procedures and toolguide guides (DIV-401..499, ADR-0043)** is after the pack-level files block.
- **Mission types (DIV-501..599, ADR-0044)** is after the procedures block.
- **Registration (DIV-601..699, ADR-0065)** is last.

The register has two forms of one list. This page is for readers. The file
[`divergences.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/conformance/divergences.yaml)
is for programs: it holds every entry of every block (pack schemas, context read, the proposed
activation block, pack-level files, procedures and toolguide guides, and mission types) and
documents its own schema at the top. The
proposed activation entries carry status `proposed`, so the gate does not enforce them until their
operations are built. Since [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1 the register
informs core of what changes when it switches; it does not gate the switch. Each entry has a status:

- `open`: the difference stands.
- `aligned`: core now agrees, so the difference is gone.
- `retired`: the surface no longer exists or no longer matters.
- `withdrawn`: the entry should not have been made, for example because another entry records
  the same difference. Its id stays taken.
- `proposed`: the difference is recorded ahead of its operation being built; it is page-only
  intent, and the gate does not enforce it until the status becomes `open`.

The conformance gate (#72) and the pack doctor parity test (#115) read `divergences.yaml`; a
difference from core that the register does not list with status `open` fails them. The parity
test's design is in the mission plan, section
[#115 doctor parity test design](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-schemas-and-level-policy-01M5AFMA/plan.md#115-doctor-parity-test-design-fr-016-designed-not-implemented).

The first three entries of the context-read block lived in the provisional section of
[`contracts/charter/CHANGELOG.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md).
That section still links them. They were decided in [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/).

## Context read (DIV-101..110, ADR-0018)

| Id | The charter answers | Core answers | Corpus case |
|---|---|---|---|
| DIV-101 | 404 `action_not_found` for an action that is not a graph node | its non-bootstrap context | `action-not-a-node` |
| DIV-102 | a scope target the graph does not declare, with a null label | skips it (and R9's filter drops such edges) | `undeclared-scope-target` |
| DIV-103 | a load error refused (500, pack code) | an empty answer with a warning | `unreadable-pack` |
| DIV-104 | withdrawn, see below | | |
| DIV-105 | an ordered answer with `relation`, `via`, `hops` and `diagnostics` | unordered sets | every case (mapping) |
| DIV-106 | vocabulary targets as entries with relation `vocabulary`; the seam drops them from delivery | computes `glossary_scopes` and discards it | `vocabulary-walk` |
| DIV-107 | the offering's walk only; activation, selection roots, the language filter and the directive allowlist are the effective-context operation's (R9, #103) | one filtered, selected answer | `activated-project`, `selected-roots`, `language-filtered`, `directive-allowlist` |
| DIV-108 | no tension annotation until the creed set (#16, #77) | an annotation over `in_tension_with` pairs | `tension-pair` |
| DIV-109 | every walked kind, including paradigm, template and agent profile | delivery slots exclude those kinds (seam-side) | `undelivered-kind` |
| DIV-110 | the hard dependencies (`requires`, transitive) of suggested artifacts are listed | a suggested artifact without its `requires` | `suggested-artifact-requires` |

**DIV-104 (withdrawn).** It registered walking `scope` from the mission-type node. The plan-point
squad found that core documents those edges as the entry point of the activation cascade, not of
this walk. Walking them adds 30 to 49 artifacts on `plan`, `documentation` and `research`
actions against core's 0 to 14. The service therefore walks scope from the action node only, and
no divergence remains.

**DIV-110 and why it is kept.** Core's own docstring calls `requires` "hard dependencies", and its
selection closure follows them, yet its walk lists a suggested artifact without them. The
operator ruled on 2026-10-19 to keep the difference. Suggestions of required artifacts are
**not** followed, as in core. The walk evidence (72 cells over open-packs) is 65 identical and 7
differing only by DIV-110; core minus charter is empty in every cell.

## Corpus cases named for the harness (#9)

- **Every mission type and action** at depths 0, 1 and 2, against `charter context --json` with and
  without `--mark-loaded`, through the seam's mapping. Cases without activation keys match core
  exactly.
- **Walk cases:** `requires-chain`, `requires-cycle`, `suggests-two-hops`, `suggests-diamond` (a
  node reachable at 1 and at 2 hops whose child must appear at depth 2),
  `suggested-artifact-requires` (listed, DIV-110), `required-artifact-suggests` (not listed, as in
  core), `other-action-owned-via-requires` (core includes it) and `plan-actions-empty` (`plan/*`,
  which core answers empty).
- **The public bundle read:** `public-bundle-depth-0`.
- **The register cases** in the table above.

## Activated cases are compared two ways

Without activation keys the filter is the identity, so those cases must match core exactly and
need no entry here. For an activated case the harness asserts both sides (ADR-0018 D5):

1. charter = core run on the same fixture without activation keys;
2. core run on the activated fixture is a subset of the charter's answer.

The seam does not switch its context read to the service until R9's effective-context operation
exists (cutover stage 3).

## Pack schemas (DIV-001..025, ADR-0017)

The pack-schema mission (#114) decided these in [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/)
and its amendment of 2026-10-19. Core's behavior is cited at the pinned core commit `9554e7b2`.
The schemas are canonical in this repository and edited in place (R20). The changes the one-time
import made to core's export, C1-C16, are tabled in the mission's
[`research.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16),
and the evidence lines below cite them by id. Each entry states core's behavior, the service's, the rationale, the
ruling, its status and the evidence.

### DIV-001: An action that is not a graph node, in the action-context answer

- **Core:** Falls back to the non-bootstrap context (activation/context.py).
- **Service:** Answers 404 action_not_found.
- **Rationale:** A silent fallback hides a wrong action name. The contract refuses it and the shadow comparison maps the refusal to core's fallback.
- **Ruling:** contracts/charter/CHANGELOG.md, Provisional (the second item); ADR-0016 decision 4
- **Status:** withdrawn. The same difference is recorded as DIV-101 in the context-read block above.
- **Evidence:**
  - Core: `activation/context.py`
  - Charter: [`contracts/charter/CHANGELOG.md`, Provisional, the second item](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md#provisional)

### DIV-002: A scope edge to a node the graph does not declare

- **Core:** Skips that artifact (activation/context_renderers/delivery_table.py).
- **Service:** Lists the artifact with a null label.
- **Rationale:** The contract states the dangling edge instead of dropping it, so a consumer can see the defect. The scenario pins this answer and says core omits it.
- **Ruling:** contracts/charter/CHANGELOG.md, Provisional (the third item); ADR-0016 decision 4
- **Status:** withdrawn. The same difference is recorded as DIV-102 in the context-read block above.
- **Evidence:**
  - Core: `activation/context_renderers/delivery_table.py`
  - Charter: [`contracts/charter/CHANGELOG.md`, Provisional, the third item](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md#provisional)

### DIV-003: pack_level_missing, the diagnostic for a missing pack level

- **Core:** An advisory, not at every level and not for a pack with no pack.yaml (offering/packs/pack_validator.py:727-767).
- **Service:** A non-blocking diagnostic at every level and for a pack with no pack.yaml. It becomes a refusal when core ADR 2026-10-11-2 closes its deprecation window.
- **Rationale:** The service is deliberately stricter than core. Open-packs was brought into line.
- **Ruling:** open-questions Q13 (2026-10-18); contracts/charter/CHANGELOG.md, Provisional (the PackSummary.diagnostics item)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:727-767`
  - Core: `offering/drg/org_pack_loader.py:519-566`
  - Charter: [`contracts/charter/CHANGELOG.md`, Provisional, the `PackSummary.diagnostics` item](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md#provisional)
  - Charter: `docs/plans/open-questions.md, Q13`

### DIV-004: pack.yaml, pack_version

- **Core:** Required for built-in packs only (offering/packs/pack_descriptor.py:143-148).
- **Service:** Required at every level, as a non-empty string. A missing, null or empty value is refused by the schema.
- **Rationale:** One rule for every pack level. Q13 already requires it and open-packs declares it everywhere, so no real pack changes.
- **Ruling:** open-questions Q13 (2026-10-18); R4 (2026-10-19)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/packs/pack_descriptor.py:143-148`
  - Charter: `contracts/packs/schemas/pack-descriptor.schema.json: pack_version is required at every level as a non-empty string (import change C9, and its C13 exception; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`
  - Charter: `docs/adr/0017-pack-schemas-imported-as-canonical-json-schema.md, Amendment 2026-10-19`

### DIV-005: Unknown keys in pack.yaml

- **Core:** Reported as an error. Core reports the key twice, as a drg and as a pack finding, when the pack has a drg/fragment.yaml, and once otherwise.
- **Service:** Tolerated, with a warning on the pack listing: the diagnostic pack_unknown_key. The schema's top level stays open for this.
- **Rationale:** A newer pack should still register on an older service, and the author is told about the key. The operator chose tolerance with a warning.
- **Ruling:** R5 (2026-10-19); plan call P1; superseded by ADR-0036 ruling 1 (2026-10-27)
- **Status:** retired, 2026-10-28. The schema is closed and an unknown key makes `pack.yaml` unreadable, as in core;
  the warning is no longer raised (#195, ADR-0036 decision 3).
- **Evidence:**
  - Core: `offering/packs/pack_descriptor.py:88` (`extra="forbid"`)
  - Core: `offering/drg/org_pack_loader.py:547-560` (the second report, when `drg/fragment.yaml` exists)
  - Charter: `contracts/packs/schemas/pack-descriptor.schema.json: the top level stays open, so an unknown key is reported by the adapter and not refused (import change C9; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-006: Retired pack.yaml fields, such as accompanies_doctrine_pack

- **Core:** Refused with a blocking finding (offering/packs/retired_fields.py:84-91).
- **Service:** Warned like any unknown key (pack_unknown_key).
- **Rationale:** One rule for every key the schema does not declare. This is laxer than core and is accepted.
- **Ruling:** R13 (2026-10-19); superseded by ADR-0036 ruling 1 (2026-10-27)
- **Status:** retired, 2026-10-28. A retired field is an unknown key of the closed schema, refused as unreadable like
  core's blocking finding (#195, ADR-0036 decision 3).
- **Evidence:**
  - Core: `offering/packs/retired_fields.py:84-91`
  - Charter: `contracts/packs/schemas/pack-descriptor.schema.json: the top level stays open and pack_level is optional, so a retired key is reported by the adapter (import change C9; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-007: Edge endpoints in graph shards and org fragments

- **Core:** A fragment endpoint may be a bare local id, resolved first against the pack's own nodes, then as a URN, then against the built-in layer (offering/drg/merge.py:580-660).
- **Service:** URNs everywhere. A bare local id is refused with drg_schema_error by the schema pattern.
- **Rationale:** One form for every endpoint, checked by the schema alone. The open-packs migration rewrote the 84 bare endpoints it had.
- **Ruling:** R6 (2026-10-19)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/merge.py:580-660`
  - Charter: `contracts/packs/schemas/graph-shard.schema.json and org-fragment.schema.json: every edge endpoint matches the URN pattern, so a bare local id is refused (import change C3; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-008: Org-fragment header fields pack_name, source_kind, source_ref, layer_index, provenance_marker

- **Core:** Accepted, and overwritten from the operator's configuration (offering/drg/org_pack_loader.py:648-655).
- **Service:** Not authored. A fragment that carries one is refused as an unknown key.
- **Rationale:** Core's own comment calls the pack-side copies advisory. Authored data should not carry values the loader replaces.
- **Ruling:** R7 (2026-10-19)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/org_pack_loader.py:648-655`
  - Charter: `contracts/packs/schemas/org-fragment.schema.json: the five header fields are removed from the closed top level (import change C6; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-009: Graph shard generated_by

- **Core:** Required (offering/drg/models.py:441).
- **Service:** Optional. generated_at stays required.
- **Rationale:** The service is laxer than core here. Nothing reads the value, and a hand-written shard has no generator.
- **Ruling:** R8 (2026-10-19)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/models.py:441`
  - Charter: `contracts/packs/schemas/graph-shard.schema.json: generated_by is not required (import change C8; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-010: Lenient value conversions

- **Core:** Converts a scalar role to roles, accepts schema_version "1" as an integer, lowercases role names and reads a quoted number as an integer.
- **Service:** None of these is done. Each is refused by the schema.
- **Rationale:** A file is judged as written. No open-packs file relies on a conversion.
- **Ruling:** R9 (2026-10-19); ADR-0017 Amendment 2026-10-19
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/agent_profiles/profile.py:294-327` (scalar role to roles)
  - Core: `offering/agent_profiles/profile.py:54-55` and `:93` (role lowercasing in `Role`)
  - Core: `activation/org_charter.py:217-235` (`schema_version` "1" to an integer)
  - Core: pydantic lax mode, no strict config (a quoted number to an integer)
  - Charter: `contracts/packs/schemas/agent-profile.schema.json: the lenient conversions are dropped (import change C10; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`; `org-charter.schema.json`, which dropped them too, is deleted with the retired file (#431, [DIV-303](#div-303-org-charteryaml-and-the-packyaml-key-active_charter))
  - Charter: `contracts/packs/schemas/glossary-pack.schema.json and skill.schema.json (#192): a quoted confidence or a quoted boolean, which core's pydantic models convert, is refused (ADR-0039)`

### DIV-011: Org-fragment node kinds

- **Core:** Canonical fragment kind name is mission_steps, with mission_step_contracts kept as the back-compat alias; also accepts the mission_type singular alias, and refuses actions and anti_patterns (offering/drg/org_pack_loader.py:72-100, 118-139).
- **Service:** One name per concept, 15 plurals. The canonical name is the reverse of core's: mission_step_contracts is the one name, and core's canonical mission_steps is refused, as is mission_type. actions and anti_patterns are accepted.
- **Rationale:** One name per concept, with no aliases. The glossary records the canonical names.
- **Ruling:** R14 (2026-10-19); plan call P9
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/org_pack_loader.py:72-100` (`mission_steps` is the canonical plural; `mission_step_contracts` is the input alias)
  - Core: `offering/drg/org_pack_loader.py:118-139`
  - Charter: `contracts/packs/schemas/org-fragment.schema.json: the node kind is an enum of the 15 fragment plurals (import change C5; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-012: URN node kinds

- **Core:** Also accepts the kinds glossary and glossary_scope, both NodeKind members (offering/drg/models.py:61-66).
- **Service:** The 15 node kinds the service models. glossary and glossary_scope are refused.
- **Rationale:** The service has no node of those kinds, and the URN pattern is generated from the kinds it models.
- **Ruling:** R15 (2026-10-19)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/models.py:61-66`
  - Charter: `contracts/packs/schemas/graph-shard.schema.json: the URN pattern and one prefix-equals-kind branch per node kind (import changes C3 and C4; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`
  - Charter: `contracts/packs/file-kinds.yaml`

### DIV-013: Nested sections of an agent profile and of charter.yaml

- **Core:** Ignores unknown keys in nested sections (extra="ignore" on 8 agent-profile and 13 charter.yaml sections).
- **Service:** Refuses them. Every nested section with declared properties is closed.
- **Rationale:** A misspelled nested key should not pass in silence. No open-packs file is affected, which the corpus probe re-checks.
- **Ruling:** spec open review item 1 (the closure of nested sections)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Charter: `contracts/packs/schemas/agent-profile.schema.json and charter.schema.json: the nested sections are closed with additionalProperties false (import change C2, 8 and 13 closed schemas; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`
  - Charter: `kitty-specs/pack-schemas-and-level-policy-01M5AFMA/spec.md, open review item 1`

### DIV-014: Nested tactics under built-in/tactics/<sub>/

- **Core:** validate_pack does not scan the 89 nested files, although the runtime loads them (offering/packs/pack_validator.py).
- **Service:** Validated like any tactic.
- **Rationale:** A file the runtime loads should be checked.
- **Ruling:** spec open review item 2 (nested tactics are validated)
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:291-295` (`_scan_files`: recursive glob only for styleguides and assets)
  - Charter: `kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Corpus`

### DIV-015: The relation alias extends in a fragment edge

- **Core:** Aliased at merge time (offering/drg/merge.py:100-102).
- **Service:** Refused. The relation is a Relation value with no aliases.
- **Rationale:** R14's one-name rule, applied to relations. The Java reader refuses it today.
- **Ruling:** plan call P2
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/merge.py:100-102`
  - Charter: `contracts/packs/schemas/org-fragment.schema.json: the edge relation is a reference to the Relation enum, which has no extends (import change C7; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-016: The asset MIME type check

- **Core:** Asks the platform for the type, so the answer can depend on the system's mime.types files (offering/packs/pack_validator.py:1377-1510).
- **Service:** Uses a fixed extension table taken from Python's built-in table of MIME types, without the system files.
- **Rationale:** A verdict must not depend on the machine it runs on. A possible difference is recorded. An example: .xml is text/xml in the fixed table but application/xml in Debian's /etc/mime.types, and .yaml, .yml, .ttf and .woff2 are unknown to the fixed table while Debian's file lists them (application/yaml, font/ttf, font/woff2).
- **Ruling:** plan call P8; R16
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:1377-1510`
  - Charter: `kitty-specs/pack-schemas-and-level-policy-01M5AFMA/plan.md, call P8`

### DIV-017: Validate mode, an invalid pack.yaml

- **Core:** Stops at an invalid pack.yaml, so defects in the other files go unreported (research R3).
- **Service:** Validate mode collects every finding. Loading stays fail-fast.
- **Rationale:** The doctor wants the full list in one run. The comparison for #115 lists this as a known difference.
- **Ruling:** FR-010; plan call P12; plan section "#115 doctor parity test design"
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Charter: `kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, R3 and R5`
  - Charter: `kitty-specs/pack-schemas-and-level-policy-01M5AFMA/plan.md, rule row P14`

### DIV-018: The impacts relation and the edge impacts strength

- **Core:** Has neither yet. The relation enum has no impacts member (offering/drg/models.py:83-148).
- **Service:** Models both. The pack schemas' Relation enum is core's 15 values, including in_tension_with, plus impacts, 16 in all, and an edge may carry a numeric impacts strength. in_tension_with is today's spelling of the ADR-0028 tension: the service holds it as a tension beside the relationships and writes it back as written (ADR-0045); it is never read as impacts.
- **Rationale:** The contract leads and core aligns at the cutover. The service's own Relation type has 15 values, the same set without in_tension_with, which is a known difference: a tension is not a relation type (ADR-0045).
- **Ruling:** ADR-0009 D1; ADR-0014 D-2; plan call P11; ADR-0028; ADR-0045
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/models.py:83-148`
  - Charter: `contracts/packs/schemas/graph-shard.schema.json and org-fragment.schema.json: the Relation enum adds impacts and keeps in_tension_with, and an edge has an optional impacts number (import changes C7 and C12, and the known C7 difference; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-019: Edge provenance

- **Core:** A merge-time marker that core stamps on each edge (offering/drg/models.py:409-412).
- **Service:** Not authored. An edge in a graph shard that carries provenance is refused.
- **Rationale:** Runtime provenance is not authored data. This applies the principle of R7. No open-packs edge carries it.
- **Ruling:** plan call P10; R7
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/drg/models.py:409-412`
  - Charter: `contracts/packs/schemas/graph-shard.schema.json: the edge has no provenance property (import change C16; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-020: Fragment node id grammar

- **Core:** Accepts any string as a fragment node id (offering/drg/org_pack_loader.py:371, `id: str`); the plant `bad id!` passes `pack validate`.
- **Service:** Requires minLength 1 and the pattern ^[A-Za-z0-9_/.-]+$ (C5, P9). The plant is refused.
- **Rationale:** A node id becomes part of a URN and a path, so it takes a safe grammar. No open-packs node id falls outside it.
- **Ruling:** R11 register decision (2026-10-20, operator); plan call P9
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/drg/org_pack_loader.py:371`
  - Charter: `service/packs/src/test/resources/planted/c5-fragment-node-id-unsafe` (core verdict in planted/core-verdicts.json: ok, no findings)
  - Charter: `contracts/packs/schemas/org-fragment.schema.json: the node id has minLength 1 and the pattern ^[A-Za-z0-9_/.-]+$ (import change C5; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-021: charter.yaml inside a pack

- **Core:** Never reads it: `charter pack validate` has no leg for charter.yaml (only org-charter.yaml, offering/packs/pack_validator.py:607-609 and 2133-2157 at 78c48ce9).
- **Service:** Validates it with charter.schema.json, including cli_timeout_seconds, which refuses .nan and .inf.
- **Rationale:** A file the pack ships should be checked against the schema that describes it, and a non-finite timeout is never meant.
- **Ruling:** R11 register decision (2026-10-20, operator)
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:607-609` (the call) and `:2133-2157` (`_check_org_charter`), at `78c48ce9`: the only charter leg is org-charter.yaml
  - Charter: the service has no `org-charter.yaml` leg either: the file is retired and refused by name (#431, [DIV-303](#div-303-org-charteryaml-and-the-packyaml-key-active_charter))
  - Charter: `service/packs/src/test/resources/planted/charter-invalid` and `.../charter-cli-timeout-not-a-number` (core verdicts in planted/core-verdicts.json: ok, no findings)

### DIV-022: org_fragment_missing in validate mode on a bare path

- **Core:** validate_pack is ok for a pack with no drg/fragment.yaml; only the loader refuses it, with OrgPackMissingError (offering/drg/org_pack_loader.py:626-628; offering/packs/pack_validator.py:893-900).
- **Service:** Validate mode blocks with org_fragment_missing on a bare path.
- **Rationale:** The doctor should report at validation time what the load refuses later.
- **Ruling:** R11 register decision (2026-10-20, operator)
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/drg/org_pack_loader.py:626-628`
  - Core: `offering/packs/pack_validator.py:893-900`
  - Charter: `service/packs/src/test/resources/planted/org-fragment-missing` (core verdict in planted/core-verdicts.json: ok, no findings)

### DIV-023: A root *.graph.yaml in a pack

- **Core:** Reads only drg/*.graph.yaml and drg/fragment.yaml (offering/packs/pack_validator.py:507, 845); a root shard is never validated.
- **Service:** Validates a root *.graph.yaml like any shard.
- **Rationale:** A graph file the pack ships should be checked wherever it sits.
- **Ruling:** R11 register decision (2026-10-20, operator)
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:507` (drg_dir = pack_dir / "drg")
  - Core: `offering/packs/pack_validator.py:845` (_DRG_GRAPH_GLOB)
  - Charter: `service/packs/src/test/resources/planted/c3-root-shard-urn-malformed` (core verdict in planted/core-verdicts.json: ok, no findings)

### DIV-024: pack_level_missing on a bare path with no configured level

- **Core:** Advises PACK_LEVEL_MISSING whenever pack.yaml has no pack_level key (offering/packs/pack_validator.py:746-766).
- **Service:** Raises no pack_level_missing on a bare path, where no level is configured to compare with.
- **Rationale:** Without a configured position the service has no level to expect, so it stays silent instead of advising.
- **Ruling:** R11 register decision (2026-10-20, operator)
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:746-766`
  - Charter: `service/packs/src/test/resources/planted/control-no-pack-level-on-a-bare-path` (core verdict in planted/core-verdicts.json: one PACK_LEVEL_MISSING advisory)

### DIV-025: UTF-16 with a byte order mark

- **Core:** Decodes it: the artifact scan hands the path to the YAML parser, which detects the encoding (offering/packs/pack_validator.py:288, 301), and accepts the plant.
- **Service:** A UTF-16 file with a byte order mark is not UTF-8 and so not valid YAML here; it is refused.
- **Rationale:** Pack files are UTF-8, and one reading of every file keeps a verdict the same on every machine.
- **Ruling:** R11 register decision (2026-10-20, operator)
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:288` and `:301` (`_yaml_parser().load(path)`, no explicit encoding)
  - Charter: `service/packs/src/test/resources/planted/artifact-utf16-with-a-byte-order-mark` (core verdict in planted/core-verdicts.json: ok, no findings)

## Adding an entry

Give it the next free id in its block, state both answers, name the corpus case, and cite the ruling. Link the
entry from the contract scenario that pins it. Add it to
[`divergences.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/conformance/divergences.yaml)
too, with the same id and status.

## Activation (DIV-201..244, ADR-0020)

DIV-201 and DIV-202 were proposed with [ADR-0020](/adr/0020-offering-and-activations/) on #123, and agreed with
core's activation ledger mission on 2026-10-20. Core's conformance-case table carries the same entries (core
response, mirrored on #9). They were `proposed` while the effective reads answered `501`; PR B built the reads on
2026-11-08, so they are `open` and the gate enforces them.

DIV-203 to DIV-205 were added on 2026-11-05 by mission `activation-register-and-closure-01M6NXBD`
([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291),
[#360](https://github.com/spec-kitty/spec-kitty-charter/issues/360)), which builds the activation register
(`createActivation`, `getActivation`, `replaceActivation`, `deleteActivation`) and the closure read
(`listActivationPacks`). They are `open`: the operations that carry them are built, and the behavioral scenarios in
`contracts/charter/scenarios/activation-register.feature` pin them. Under
[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D1 they tell core what changes when it
switches; they do not gate the switch. No change to core's code is part of this mission.

DIV-206 and DIV-207 were added on 2026-11-06 by the same mission's second pull request, which builds the five
per-artifact writes (`selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
`reactivateActivationArtifact` and `applyActivationPreset`). They record where the write cascade departs from core's
under [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D2.4 as revised on 2026-11-05 and
D2 as amended on 2026-11-06. They are `open`: `contracts/charter/scenarios/activation-selections.feature` pins them.
The 2026-11-06 amendment replaced the `enhances` pull of D2.2 with `enhances` as one more cascade relation, and ruled
that a read returns an enhancement only when it is itself active; the reads' entry is DIV-224.

DIV-209 to DIV-215 were added on 2026-11-07 by the same mission's PR A0: the rulings that change what #399 built (a
replace that drops what the packs no longer hold, the kind model, and a `depth` on the cascade). They are `open`:
`activation-ruling-changes.feature`, `activation-kind-model.feature` and `activation-cascade-depth.feature` pin them.
DIV-208 was recorded and withdrawn on 2026-11-06, before it landed (a write normalizes every known spelling of an id),
so its id stays taken and the new entries start at DIV-209.

DIV-216 to DIV-237 were added on 2026-11-08 by the same mission's PR B, which builds the layer merge and two effective
reads, `listEffectiveArtifacts` and `getActivationSkippedReport` (the effective graph and the effective action context
are PR C, which builds them). They are `open`; `layer-merge.feature` and `effective-reads.feature` pin them, and
the unit tests named in `divergences.yaml` pin the rest. The entries say, for each, whether core's behavior is
confirmed (a cited core ADR or file) or is the service's understanding, to be confirmed against core. None is
verified by running core, which this mission does not change (C-003).
The entries group as follows:

- **The merge:** DIV-216 (whole replacement), DIV-217 (the mutability gate), DIV-218 and DIV-219 (settlement and its
  gap until #358), DIV-220 (a graph node holds), DIV-230 (the write cascade over merged edges).
- **What is in force:** DIV-221 (overrides), DIV-222 and DIV-223 (the refusals of a cycle and of an unstable answer),
  DIV-224 (enhancers and bound artifacts), DIV-225 and DIV-226 (a required artifact), DIV-227 (the language filter).
- **What is reported:** DIV-228 (the typed skipped report), DIV-229 (the two reasons that are gone), DIV-235
  (suggestions), DIV-237 (write warnings and the registration notice).
- **Validators and cost:** DIV-231 to DIV-234 (the tags, the cursor, `getActivation`'s tag and the either-tag
  `If-Match`) and DIV-236 (the facts cache).

DIV-239 to DIV-244 were added on 2026-11-09 by the same mission's PR C, which builds the effective graph and the effective
action context and the write rule of operator ruling Q9, and folds the operator rulings of that date (OD-6, OD-9, the node
predicate, D16). DIV-204 is updated for the write rule. DIV-238 is claimed by the step-enhancements mission
(`adr-0066-step-enhancement-service-alignment-01M6ZRYK`), which was checked on its branch before these ids were taken.
`activation-deny-satisfied.feature` and `effective-graph-and-context.feature` pin them, and the unit tests named in
`divergences.yaml` pin the rest. Their entries group as follows:

- **The write:** DIV-204 (a deny entry a required artifact's override satisfies).
- **What a node and a step are:** DIV-240 (the kinds absent from the reads), DIV-243 (the two forms of a held step),
  DIV-242 (the cascade follows held steps only).
- **What is in force and reported:** DIV-239 (the floor of activatable kinds, `NodeReference` targets), DIV-241 (a step
  enhancer's binding), DIV-244 (the context's suggestions, `enhancements` and order).

Not divergences, because they are PR B behavior as found: when several artifacts in force satisfy or hide one artifact,
`satisfiedBy` and `overriddenBy` name the smallest URN. No ruling settles that tie, so it is not a difference from core
either; it is tracked in #439 for an operator ruling.

| Id | The charter answers | Core answers | Ruling | Corpus case |
|---|---|---|---|---|
| DIV-201 | A required or locked artifact is active on read, even under an explicit allow list that omits it (ADR-0020 D7); the exceptions are those of E9: a required or locked enhancer with no target in force, or an artifact bound to a mission type not in force, stays out of force (DIV-224, DIV-225) | Does not add it under such an allow list; doctor and list warn | ADR-0020 | `required-under-allow-list` |
| DIV-202 | The required floor is derived from the packs at read time and never stored (ADR-0020 D4) | Promotes org `required_*` into the allow list at write time | ADR-0020 | `required-promoted-at-write` |
| DIV-203 | An absent `selected.<kind>` (a mission type included) means none, the same as `[]`; `activatedKinds` and `missionTypes` are no longer members (DIV-212, DIV-214). This holds for `skill` too, which loses its "only the required skills" default. An absent `packs` still means every configured pack | A per-kind default when the key is absent: every available artifact, or only the required skills for `skill` | ADR-0053 d10; decision `DM-01M6NXQJ61D2GSFJFK02XW6F9N`. Holds until core's named-packs split ([ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/)) | `absent-kind-means-none` |
| DIV-204 | A create or replace whose deny list names a locked artifact, or a required one that no active override satisfies, or a deactivate that adds such an entry, is refused `409` (`artifact_locked`, `artifact_required`), naming the pack, and nothing is stored (A1, DIV-205: The requirement of a governing preset id is the union, across the levels (built-in, org, project), of the must-have items of the copy in force at each level. Within one level the copy of the pack last in closure order replaces the others, a child pack's copy its parent's, and a replaced copy requires nothing. A deny of an artifact only an earlier level's copy requires, which a later level's copy replaced, is 409 artifact_required naming that earlier level's pack.). A deny entry on a required, not locked artifact is accepted when an active override of it satisfies the requirement in the document as it will be stored (operator ruling Q9): the in-force resolution judges it (E5, DIV-225), so an enhancement never satisfies and a deny entry accepted on write is one the next read applies; a locked artifact is never satisfied by an override. A create judges every deny entry of the body; a replace judges only the deny entries it adds; a deactivate judges the named target whether or not its deny entry is already stored; a preset applied with `force` and a select with `includeDeactivated` add no deny entry and judge none; and a replace that adds the override and the deny entry together is accepted. A later write that removes the override is not refused: the read lists the deny entry in `denyNotApplied`. The floor judgement of a create, a replace and a deactivate asks the resolution only for a deny entry on a required artifact; the per-artifact writes ask it whenever they apply anything (DIV-230). A layer merge that refuses (DIV-217, DIV-218) therefore reaches a write as `500 pack_unreadable`, where it answered `409` before PR C. The write and the read can still disagree in three places ([#447](https://github.com/spec-kitty/spec-kitty-charter/issues/447)), the first two failing closed on the write: an overrider of a non-activatable kind (a template, a guard, a lifecycle) hides a required artifact in the read but satisfies nothing on the write, so the deny entry is refused; a deny entry on a required artifact that pruning already removed from force is refused by the write, while the read applies it silently; and a deny entry stored over a closure whose layer merge refuses is accepted (`201`), while every effective read of it is `500` (the merge-refusal control of `activation-deny-satisfied.feature`). A stored entry is tolerated by every write that neither adds nor names it | Accepts the entry and ignores it, or warns | ADR-0020 D9; ADR-0059 D4 and amendment R6; decisions `01M6YKZRM7VF19A57E40D59N81`, `01M6ZCXAJ9MTTF9XJSR89F3NYG`, `DM-01M6R3YX23H46PXGNEFC31E6CT` | `deny-required-refused-at-write` |
| DIV-205 | The required floor is the must-have items of every governing preset in the activation's closure; since PR B the effective reads take it from the merged closure, by one rule (A1): The requirement of a governing preset id is the union, across the levels (built-in, org, project), of the must-have items of the copy in force at each level. Within one level the copy of the pack last in closure order replaces the others, a child pack's copy its parent's, and a replaced copy requires nothing. The writes' floor refusal (DIV-204) and the registration notices `floorAdded` and `floorRemoved` follow the same rule | The org packs' `required_*` lists | Operator ruling of 2026-11-06, decision `DM-01M6R0TK6F4CC0Z00CZTS21V72`, which ratifies the closure scope that decision `DM-01M6PCWSA65497T9A7J80K6V56` had assumed; decision `DM-01M6RA7FHRC5K8QPX7KN5FZ4S8` (A1); decision `01M6YKZRM7VF19A57E40D59N81`; ruling `01M6ZCXAJ9MTTF9XJSR89F3NYG` | `floor-closure-scoped` |
| DIV-206 | A cascade passes through every `specializes_from` ancestor of a profile it reaches, up the whole chain, following only `requires` and `scope` from each ancestor. An ancestor passed through is neither put in force nor reported. A denied ancestor stops the pass-through and is reported in `skippedDeactivated`; what lies only behind it is in `unreachedBehindDeactivated`. With `includeDeactivated` a denied ancestor of a kind in the `cascade` scope is passed through and never selected, and its deny entry is kept, because `includeDeactivated` clears only the deny entries of what the walk reaches and selects (B5, 2026-11-07); one outside the scope stops the pass-through. `requiredBy` never names an ancestor passed through; it leads back to the descendant. A cascading deactivate's own walk passes through the lineage as a select's does, so the two round-trip, but stops at no deny entry: it crosses a denied ancestor a select would stop at, as it crosses a denied artifact on the forward relations | Does not follow `specializes_from` in its cascade | ADR-0059 D2.4, its 2026-11-06 amendment and its 2026-11-07 amendment (B5) | `cascade-lineage-pass-through` |
| DIV-207 | The write cascade walks `enhances` as one of its relations, from the enhancer to what it enhances: a cascading select of an enhancer selects its target, a select of the target selects no enhancer, and a cascading deactivate of an enhancer unselects its target unless another active artifact needs it (`skippedShared`). There is no `enhances` pull, at write or at read | Does not follow `enhances` in its cascade | ADR-0059 D2 as amended on 2026-11-06 (A4) | `cascade-enhances-relation` |
| DIV-209 | `replaceActivation` drops an id of the body that the closure no longer holds and that the replaced document named, and lists it in `dropped`; an unknown id the replaced document did not name is still `422`. A replace, the four per-artifact writes and `applyActivationPreset` drop a version pin no pack of the closure holds and list it in `droppedPins`, where they answered `422 version_not_held`; `createActivation` keeps that `422` for a pin in its own body. A replace judges the floor only on the deny entries it adds, so a GET then PUT of one body is never refused without a pack change | No stored document to replace: its seam drops a stale deny entry at export and keeps no pack version pin | ADR-0059 amendment of 2026-11-07 (ruleset A4 and B1); decisions `DM-01M6RA7XC5HC8915GZT2R3H83W`, `DM-01M6RA9BKJY7X9BFV93QW1KM1T`, `DM-01M6RN5Y406SB4KA13QYMQ2P8J` | `replace-drops-stale` |
| DIV-210 | A mission type id is read in any spelling (the declared id, its full URN or another case) in a path and in a document, and stored as the declared id. A document that names one the closure does not hold is `422 unknown_artifact` at `/selected/mission_type/<i>`; `unknown_mission_type` is gone | A mission type is not an artifact kind (DIV-501), so its id is matched as the mission-tier path spells it | ADR-0020 D4 note of 2026-11-06; decisions `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC` | `mission-type-id-spellings` |
| DIV-211 | A preset carrying `activated_kinds` refuses the pack as `500 pack_unreadable`, as the preset item id grammar does; a `422` on registration is planned for PR A. A preset may name any kind, `skill` and `glossary_pack` included (`activated_skills`, `activated_glossary_packs`), and `activated_mission_types` is a plain member list applied as `selected.mission_type` | A preset may carry `activated_kinds`, the kind gate, and its member lists exclude skill and glossary pack | Decisions `DM-01M6RCY40CQQTHC130RWF43W0D` (Q3), `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; ADR-0042 D2 note of 2026-11-07 | `preset-any-kind` |
| DIV-212 | Core's `activated_kinds` narrowing is not honoured: nothing is in force because its kind is activated, a select brings its artifact into force, and `activatedKinds` is refused on a create or replace. A core project that narrows it sees a larger effective set | A project narrows `activated_kinds`, and only the kinds it lists are in force | Decisions `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY` (A7), `DM-01M6RCY8MYNGNPJ247M2Y4880N` (Q4) | `activated-kinds-narrowing` |
| DIV-213 | A document stored at `schemaVersion` 1 is answered as its version 2 upgrade (`missionTypes` become `selected.mission_type`, every selection is kept, `activatedKinds` is discarded). A read upgrades in memory; the next write stores version 2, so every `ETag` changes once | No stored document of this shape and no version to upgrade | Decisions `DM-01M6RMJHDKNTXAM055Q8WR6SDN`, `DM-01M6RFGMDH859CJP3RHHF9YC3H` | `v1-upgraded-on-read` |
| DIV-214 | A mission type is selected under `selected.mission_type` and denied under `deactivated.mission_type` unless it is required or locked (`409 artifact_required`, `artifact_locked`). Selecting one with a cascade crosses to its steps like `requires` and selects what they reach (W20); a cascading deactivate unselects what only it kept (W23) | Mission types are a plain `mission_type_activations` list with no deny list (ADR-0020 D4 table); selecting one does not put what its steps reach into `activated_<plural>` | Decisions `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S` (Q1), `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC` (Q2); #410 | `mission-type-selected-like-any-kind` |
| DIV-215 | A cascading select and a cascading deactivate take an optional `depth` that counts `suggests` hops only: every other relation, the lineage pass-through and a mission type's steps are walked in full, `0` follows no `suggests` edge, and absent is unlimited. `skippedByDepth` lists the next `suggests` frontier. The deactivate's own walk honours it and the shared walk does not, so a deactivate at depth N undoes a select at depth N only in what it removes | No write-cascade depth; the activation cascade is unlimited | Operator ruling on #408 of 2026-11-07; decisions `DM-01M6TDH0SY5Q224GX709DXWG8K`, `DM-01M6TF3BA808JMW0W0A0CPXBNW` | `cascade-depth` |
| DIV-216 | The version from the level that applies (built-in, then org, then project) is the artifact in force, whole: no field of a losing version is read and nothing is inherited. The answer names the winner's `packId` and `level`. An edge that starts at a losing version is dropped with it; an edge that ends at the overridden id, written bare or as `<pack>::<id>`, resolves to the winner. One winner function picks the version of an artifact and the copy of a preset. `/offering` stays the unmerged catalog and merges nothing; only the effective reads and the writes build the merge. | Merges the layers field by field (core ADR `3.x/2026-05-16-1`, as ADR-0059 D3 cites it); not re-run against core's code here. | ADR-0059 D3; ADR-0053 d6; decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A` (provenance); FR-009, FR-010, FR-023 | `layer-override-replaces-whole` |
| DIV-217 | A layer override is allowed only when the overridden artifact is `open`. The level is judged along the closure with the winning pack left out, so the losers' own files and any pack that raised the level count, and a pack never blocks its own override. Otherwise the read refuses `500 pack_unreadable`, naming the artifact, both packs and the level; it is not `governance_policy_violation`, which ADR-0012 d9 names. Every write that builds the merge answers it too (DIV-230). | A collision warning (ADR-0059 D3). The service's understanding of core, to be confirmed against core: no mutability level refuses a layer override. | ADR-0012 d1, d8; ADR-0059 D3; ADR-0063 D3; FR-011; research R3 | `layer-override-mutability-gate` |
| DIV-218 | A contest at one level that exactly one descriptor `overrides` entry settles puts the named member's version in force (#357, ADR-0053 d5), and no load order decides. A step (`action:<mission type>/<step>`) shares its mission type's settlement. The compose refuses an unsettled contest of every artifact but a step, so a step contest no entry settles reaches the merge, which refuses it at read as `500 pack_sibling_conflict` (the compose lets it through). A settled loser has no `LayerOverride` record; the winner's provenance shows the settlement. Two copies of one preset at one level are parent and child, and the child holds it. | Merges two org packs that declare one URN by load order: the later-declared pack wins (core `59d190a7`, #87). The service's understanding of core, to be confirmed against core: core has no settlement entry. | ADR-0048 d9; ADR-0053 d5; FR-012; research R3; WP21 review, cycle 1 | `sibling-settlement-in-force` |
| DIV-219 | Any pack of the compose may declare the settling `overrides` entry, and the settlement is not gated by level: C21 judges cross-level overrides only. Until #358 lets only the distribution pack settle a sibling contest, a pack of the closure can settle a contest in favour of a sibling and displace an artifact that is locked, with no level check. A known gap, recorded, not ruled acceptable. | The service's understanding of core, to be confirmed against core: core has no settlement entry (DIV-218). | ADR-0053 d5 as amended 2026-11-03; research R3; WP21 review, cycle 1, ruling (a); #358 | `sibling-settlement-not-level-gated` |
| DIV-220 | The merge counts every holder of a URN, a node a graph document declares included. An org pack whose graph only declares the node of a built-in step replaces that step whole at its level and drops the step's built-in edges. A qualified source (`built-in::action:<mission type>/<step>`) restates an edge without holding the node. | The service's understanding of core, to be confirmed against core: core merges artifacts by file, and a graph document's node holds nothing. | ADR-0059 D3; ADR-0064; WP21 review, cycle 1 | `graph-node-holds` |
| DIV-221 | Overrides compose transitively from the top: in `F -> E -> T` only `F` stays in force, and `F` satisfies a requirement on `E` and on `T`. A hidden artifact that was selected is `inactive` with reason `overridden`, in the `overridden` bucket with its `overriddenBy`; an unselected one stays `not_selected`. A required target an override satisfies keeps its state and gains `satisfiedBy`. A target that is not `open` (locked, or `extendable`) is never hidden: it stays, and the pair is in `overrideNotApplied`. The edge is judged at registration and again at read, because a later pack may tighten the target. | The service's understanding of core, to be confirmed against core: core's read does not hide an artifact because another overrides it, and a requirement on T is not satisfied by an override. | Decisions `DM-01M6R0TGX3DSR54EVS58Q1V8FV`, `DM-01M6R2X2SW8KVAJCDX23PGJBMT`, `DM-01M6RA7RPNZS810FTFC98Z13GH` (A3), `DM-01M6RA86QPBM1VE5QZTB9AZ195` (A6); research R11, R13; FR-016 | `override-hides-and-satisfies` |
| DIV-222 | A cycle of `overrides` edges among `open` artifacts that the candidates or the required floor reach is refused `500 pack_unreadable`, naming a node of it (`override_cycle`); no pick breaks it. A cycle that nothing reaches is not judged. A registration refuses the same cycle (PR A). | The service's understanding of core, to be confirmed against core: core's read applies no `overrides` edge, so it has no cycle to refuse. | Decisions `DM-01M6RA7M56M1409EV9JX1EW83K` (A2), `DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6`; FR-016; research R13 | `override-cycle-refused` |
| DIV-223 | What is in force is solved in four strata to a fixed point (research R13): candidates, overrides, the requirement, then enhancers. An overrider that the enhancer or binding pruning removes loses its overrides, and the requirement and the pruning run again until no overrider is newly removed. When an overrider is pruned while its overrides apply and is in force once they are withdrawn, the answer has no stable value, and the read refuses `500 pack_unreadable` (`override_unstable`). This replaces the earlier rule of one re-run. | The service's understanding of core, to be confirmed against core: core resolves no enhancer or override at read. | Decisions `DM-01M6RA8S9G57W4BW55SNVHBQ10` (A11), `DM-01M6RA8G0RGRWSFW4DERH8W319` (A8); research R13 | `in-force-fixed-point` |
| DIV-224 | An artifact that declares `enhances` is in force only when a target of its enhancement is in force and the support chain reaches an artifact that enhances nothing (a least fixed point), so an `enhances` ring is not in force. Otherwise it is `inactive` with reason `enhanced_target_not_in_force`, in the `enhancedTargetNotInForce` bucket naming its targets. A step contract is bound to its mission type and is in force only while the mission type is (`mission_type_not_in_force`); only step contracts are bound, and the binding of a step enhancer is PR C. A denied artifact that the rule prunes (a denied enhancer whose target is out of force) is `inactive`, listed in `enhancedTargetNotInForce` and never in `skippedDeactivated`, and not `excluded`: only the reason `deactivated` is `excluded`. A write that selects such an enhancer is accepted and answers a warning (`warnings`, `enhanced_target_not_in_force`). | The service's understanding of core, to be confirmed against core: core returns an enhancement without asking whether its target is in force. | Operator ruling Q8 of 2026-11-06; decisions `DM-01M6R3YTNT1P2MBERBN7WV71ND`, `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ`, `DM-01M6RA8G0RGRWSFW4DERH8W319` (A8), `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`; FR-015 | `enhancer-needs-target` |
| DIV-225 | A required artifact is satisfied by itself in force or by an artifact in force that overrides it, directly or through a chain (`satisfiedBy` names it), never by an enhancement. Otherwise it is put in force with reason `required`, and a deny entry on it is listed in `denyNotApplied`. A locked artifact is in force unless the enhancer or binding rule prunes it (E9). The requirement is the union, across the levels, of the must-have items of the copy of a preset id in force at each level, read from the merged closure (DIV-205); a must-have item no pack holds is in `requiredNotHeld`. The writes' floor refusal and the registration notices judge the floor by the same rule (DIV-204, DIV-205): a deny of an artifact only an earlier level's copy requires, which a later level's copy replaced, is refused. A required or locked artifact that the enhancer or binding rule prunes stays out of force and is listed once, in `requiredNotInForce`. A required, unselected artifact an override hides is `not_selected` with `satisfiedBy`. | Promotes org `required_*` into the allow list at write time (DIV-202). The service's understanding of core, to be confirmed against core: an override does not satisfy a required artifact. | Operator rulings Q6 and Q8 of 2026-11-06; decisions `DM-01M6R0TK6F4CC0Z00CZTS21V72`, `DM-01M6RA7FHRC5K8QPX7KN5FZ4S8` (A1), `01M6YKZRM7VF19A57E40D59N81`, `01M6ZCXAJ9MTTF9XJSR89F3NYG`, `DM-01M6R3YTNT1P2MBERBN7WV71ND`, `DM-01M6RFGFW3S0YJ222QMHD2W11B`; research R11 | `required-is-a-requirement` |
| DIV-226 | The language filter narrows the candidates, and the requirement then adds a required artifact whatever its `applies_to_languages` say; a locked artifact is a candidate whatever the filter says. Such an artifact is `active` with reason `required` or `locked`, and is not in the `languageFiltered` bucket, which lists only a selected artifact the filter excluded. | The service's understanding of core, to be confirmed against core: the sources of this mission do not say how core combines its language filter with a required artifact. | Rules E3 and E5 (the Unclear note of E5 resolved on 2026-11-08); decision `DM-01M6R2X2SW8KVAJCDX23PGJBMT` | `required-despite-language-filter` |
| DIV-227 | The activation document stays language-neutral, and a write applies no language filter. The two effective reads apply it: an artifact with a non-empty `applies_to_languages` that shares none with the document's `languages` is `inactive` with reason `language_filtered`. An absent `languages` filters nothing; `[]` filters every tagged artifact. This is the one absent list that does not mean none (DIV-203). A read with no activation applies no filtering. | The service's understanding of core, to be confirmed against core: core applies `languages` where it builds the active list, not only on a read. | Operator ruling on #408 of 2026-11-07 (ruling 2); FR-013, FR-017 | `language-filter-at-read-only` |
| DIV-228 | `getActivationSkippedReport` lists each artifact that is not in force once, in the bucket of the first rule that decided it. A required or locked artifact that the enhancer or binding rule prunes is listed once, in `requiredNotInForce`, and `summary.skipped` counts it once. `SkippedReport` gains ten required buckets: `staleIds`, `denyNotApplied`, `languageFiltered`, `overridden`, `missionTypeNotInForce`, `overrideNotApplied`, `enhancedTargetNotInForce`, `pinNotHeld`, `requiredNotHeld` and `requiredNotInForce`. `skippedByScope`, `unreachedBehindDeactivated` and `skippedShared` stay and are always empty at read; at read `skippedDeactivated` has `viaRequires` false and `requiredBy` empty. A write's `skipped` and the action context answer every new bucket as `[]`. | The skipped report takes core's structure (ADR-0020 D6): `skippedByScope`, `skippedDeactivated`, `unreachedBehindDeactivated` and `skippedShared`, which describe a cascade. The service's understanding of core, to be confirmed against core: core has no typed bucket for what a read leaves out. | Decisions `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`, `DM-01M6RN5MTWC98PXEVJNFQSRRJX`, `01M6YM02EG3X5V8G33T1HT5N0K`; ADR-0059 D4; FR-017 | `skipped-report-typed` |
| DIV-229 | `EffectiveArtifactReason` loses `kind_default` and `kind_not_activated`. Nothing is in force because its kind is activated, and an absent list means none, so neither reason can arise. The enum gains `overridden`, `mission_type_not_in_force` and `enhanced_target_not_in_force`. A client that switches on the closed enum must regenerate. | Has a per-kind default (DIV-203) and a kind gate, `activated_kinds` (DIV-212). The service's earlier contract named a reason for each; whether core reports them as reasons is not confirmed. | ADR-0053 d10; decisions `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, `DM-01M6RCY8MYNGNPJ247M2Y4880N`; FR-022 | `reasons-without-kind-gate` |
| DIV-230 | The cascade of a select or a deactivate walks the edges of the merged closure, so it never follows an edge a losing version declared. Where no layer override exists the answer is unchanged. A write over a closure whose layer merge refuses (DIV-217, DIV-218) answers `500 pack_unreadable` or `pack_sibling_conflict`, as a read does; before PR B no write could. A write's preconditions are judged before the packs are read, except that an `If-Match` that is a closure tag reads the closure to judge its `412` (DIV-234). | The service's understanding of core, to be confirmed against core: the cascade walks the one graph core merges (DIV-216). | Research R2 (open point, resolved); decision `DM-01M6R4W1H9P3451V89KHP97XKM`; FR-038; rule E20 | `write-cascade-over-merged-edges` |
| DIV-231 | The `ETag` of `listEffectiveArtifacts` and of `getActivationSkippedReport` is `sha256:` over a domain tag, the read, the activation's revision, the digest of the packs in the activation's closure and the normalized query (`kind`, `state`, cursor, page size). A page, a filter or the other read never matches the tag of another, and a pack outside the closure changes nothing. The body's `governanceFingerprint` is that closure digest, no longer `ServiceInfo.sourceFingerprint`. A read carries three digests: the revision, the closure digest and, in `Charter-Source-Fingerprint`, the digest of the whole source. A matching `If-None-Match` answers `304`. | The service's understanding of core, to be confirmed against core: no activation read carries a validator that covers a closure. | ADR-0059 D5; research R5; decision `01M6Y11E55YP5TG1EJ1MDKCFRP`; FR-020 | `effective-tag-per-representation` |
| DIV-232 | `listEffectiveArtifacts` is paged by a keyset cursor over `(kind, id)`. The cursor binds the tag of the set it pages (the same kind and state, no cursor, no page size), so a cursor from another set, or after the revision or a pack of the closure changed, is `400` without a code, as a malformed one is. `summary` counts the whole set and is identical on every page. The `self`, `first` and `next` links repeat `pageSize` only when the request named it. | The service's understanding of core, to be confirmed against core: core has no paged effective list. | Decision `DM-01M6R0TNEBR7KGJMW2SQXQGJ0A`; FR-013, FR-020 | `effective-list-keyset-cursor` |
| DIV-233 | The `ETag` of `getActivation` is no longer the bare quoted revision. It is the tag of the revision and the closure's digest, because the answer now carries `denyNotApplied`, which is judged against the packs of the closure. It changes when a pack of the closure changes, and not when a pack outside it does. `getActivation` can also answer `500` with a composition code or `pack_unreadable`. A document that names a pack no longer configured, a closure whose layer merge refuses and an in-force refusal are `500` too, so the "served unchanged" of ADR-0059 D4 holds only while the closure can be read. It sends no `Charter-Source-Fingerprint`, as before. A write's answer keeps the revision as its `ETag` and carries no `denyNotApplied`. | The service's understanding of core, to be confirmed against core: core stores its activation in files and has no such read. | Operator decision `01M6Y11E55YP5TG1EJ1MDKCFRP` (amends ADR-0059 D5); FR-027 | `get-activation-tag-covers-closure` |
| DIV-234 | A write (`PUT`, `DELETE` and the five sub-resource writes) accepts `If-Match` carrying the quoted revision or the current `getActivation` tag, so a client may send what it read; `*` still matches any stored document. A closure tag that no longer matches is `412`. The revision is checked first and costs no read. A tag-shaped header that is not the revision costs one verified read of the closure to compute the current tag, and the write then reads the packs again for its own judgment; a pack change between the two reads could let a just-stale tag pass. | The service's understanding of core, to be confirmed against core: core's seam sends no `If-Match` that names a closure. | Operator decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3` (amends ADR-0020 D6); FR-027 | `if-match-either-tag` |
| DIV-235 | `EffectiveArtifact.suggestion {when, reason}` is present only for an artifact that is out of force with reason `not_selected` and that a `suggests` edge of an active artifact reaches; the edge's `rationale` is served as `reason`. A denied, overridden, language-filtered or mission-type-filtered artifact is out of force for its own reason, and its reason says so. `enhances` is not a hard relation, so an artifact an active one enhances keeps its suggestion. PR C's action context uses the same definition. | The service's understanding of core, to be confirmed against core: core's action context lists a `suggests` target without a reason carrier. | Operator ruling on #408 of 2026-11-07 (ruling 3); decisions `01M6Y0X5E9ANR25MC8RPFXYM72`, `01M6Y6VE6VZQ4MXD98N93JB3GJ`; FR-013 | `suggestion-only-not-selected` |
| DIV-236 | `CompositionFactsCache` keeps the composition facts of each pack between reads, keyed by the pack's configured name and valid for one write generation and one path-inclusive digest of the pack's files together. It never fills or serves from a read whose generation was odd or moved, from a holder of the registry write lock, or from a failure, and by default it keeps at most 256 packs. It reads the path-inclusive digest on every call, in the one pass that takes the pack revision, because a change no write bracketed (an out-of-band edit or rename) must miss: the digest covers each file's path as well as its bytes. PR B therefore claims no gain on #416. | The service's understanding of core, to be confirmed against core: core has no composition facts to cache. | Decision `DM-01M6XT2FKK7YPV1GCESFW1ANZ7`; NFR-007; #416, #418 R5 | `composition-facts-cache` |
| DIV-237 | `ActivationChange.warnings` (`[{code, artifact}]`, `[]` when none) lists each artifact the write puts in (a select, or the additions of a preset) that a read will not put in force: `enhanced_target_not_in_force` and `overridden`. A removal (an unselect), a deny and a reactivation never warn. A write still succeeds, but the warnings come from one in-force resolution of the stored document asked before anything is stored, so a closure whose resolution refuses (DIV-222, DIV-223) refuses the write; a write that applies nothing asks nothing. `PackRegistration.notices.activationsAffected` lists, bounded and sorted, the stored activations whose in-force set a registration changes, a change of the version in force of an artifact (the pack that holds it, its level or its mutability level) included; an activation whose closure cannot be merged counts as changed. | The service's understanding of core, to be confirmed against core: core's writes answer neither. | Decisions `DM-01M6RAA2B2G9PNXN2F9SJ7B1CR` (B6, B7), `DM-01M6RA9SAKCBSKGX165JM3CAGS` (B4), `01M6YKZXGFGS340D1D9TE5T809`; FR-027, FR-032 | `write-warnings` |
| DIV-239 | The required floor holds activatable kinds only, so `requiredNotInForce` never names a step (`action:`): the policy and preset schemas cannot name one. Every part of the skipped report that the contract types as an `ArtifactReference` drops a step reference and keeps the artifact references beside it; the targets of `enhancedTargetNotInForce` are `NodeReference {kind, id}`, so a step enhancer's target step is named (`EnhancerWithoutTarget.targets`, a breaking change of that response). | The service's understanding of core, to be confirmed against core: core's required lists name artifacts of any kind, and whether a step can be required is not confirmed. | Plan "PR C scope added by operator rulings (2026-11-09)" (#429 D3, D4(a), D15, D16); rules C31, E14 | `required-floor-activatable-kinds` |
| DIV-240 | The effective graph and the effective action context leave out every kind that has no activation state: a template, an asset, a guard and a lifecycle are never nodes, entries or layer overrides, even when a lock puts one in force inside the packs. The graph's nodes are the artifacts in force and the held steps of the mission types in force (a step is a node, an edge may start at one, and its layer override is listed); `layerOverrides` lists only the overrides of those nodes. | The service's understanding of core, to be confirmed against core: core's merged graph holds every node of every kind and reports no layer override at all. | Decisions `01M702A71YF7WNDTG2AA89K8ZA`, `01M7037ZNKJ6JTRKGD7G695ZV1`; FR-018; rule E15 | `effective-graph-nodes-in-force` |
| DIV-241 | A step enhancer, a `step_enhancement` with exactly one distinct `enhances` target that is an action `action:M/s`, is in force exactly when the mission type `M` is in force, and is pruned with the reason `mission_type_not_in_force` (bucket `missionTypeNotInForce`) otherwise. When no pack holds the target step it keeps its edge and is pruned with `enhanced_target_not_in_force`. Every other shape of enhancer keeps the rule of DIV-224. | The service's understanding of core, to be confirmed against core: core applies a step enhancement at the prompt read and has no in-force state for it. | Operator ruling OD-6 (decision `01M702A8XYB588PSGTXRKQ4NKK`); decision `01M7037V93Y077H8M4W9V8Y6B3`; rule E6 | `step-enhancer-bound-to-mission-type` |
| DIV-242 | The write cascade follows only held steps: a mission type reaches `action:M/s` when a pack of the closure defines `M` and declares the action, so an action declared where no pack defines its mission type, and one declared by a pack beside a mission type another pack defines, is no longer followed. | The service's understanding of core, to be confirmed against core: core's cascade follows the steps of the mission type its graph declares. | Operator ruling OD-9 (decision `01M7037V93Y077H8M4W9V8Y6B3`); rule W20 | `cascade-follows-held-steps` |
| DIV-243 | A step is held by one predicate in two forms, used by the cascade, by `action_not_found` and by the action context's walk: per pack (the pack defines `mission_type:M` and declares `action:M/s`), and on the closure (some pack holds it, with the merged winner as its place, so a layer override of a held step counts). A winning mission type keeps the unique steps of the versions it replaced (A7). The mission type in one pack and the action in another pack alone is not a held step. | The service's understanding of core, to be confirmed against core: core reads a step by the mission type's `steps` list and reports no per-pack form. | Decision `01M7037V93Y077H8M4W9V8Y6B3` (the architect's C17/C18 dissent is recorded in the PR C dispositions); rules W20, E16 | `step-held-per-pack-and-on-the-closure` |
| DIV-244 | The effective action context lists the artifacts in force the ADR-0018 walk reaches and, beside them, suggestions: an artifact the listing gives a suggestion that a listed entry reaches through `suggests`, with the nearest edge's `when` and `reason`, never walked through, so the suggestions are a subset of the listing's. It carries `enhancements` (`[{target, enhancedBy}]`, one per node with an enhancer in force, the start step included), sorts its entries by full URN string, and answers `missionTypeNotInForce` with the mission type itself. An unknown mission type or action is `404` before a `304`. `depth` is 0 or 1, and absent is 0. | The service's understanding of core, to be confirmed against core: core's action context has no `enhancements` member, orders entries by step, and allows `depth` 0 to 2, defaulting to 2 (ADR-0018 D4). | Decisions `01M7059T4K64TAVKXN3T1FKWYG`, `01M70CTZP7P2EHSCK2G0RET74B`, `01M70C42KC78KB9H6KB7TK9224`, `01M6Y6VE6VZQ4MXD98N93JB3GJ`; ADR-0018 D3; FR-019; rule E16 | `effective-context-enhancements-and-suggestions` |

Core's seam, to switch: read an absent list as none and write the default it means explicitly (DIV-203); clean a
deny entry on a required or locked artifact out of a document before writing it (DIV-204); and take the required set
from the closure's governing presets, not from `required_*` (DIV-205). A project with a profile whose parent
`requires` or `scope`s more (DIV-206), or with an artifact that enhances another (DIV-207), sees a different write
cascade after the switch; core's seam takes the service's answer, `applied`, `cleared` and `skipped`, as it is.

For PR A0, core's seam regenerates its strict DTOs from the contract and then: reads `dropped` and `droppedPins` on a
replace answer instead of handling a `422` (DIV-209); sends mission type ids in any spelling (DIV-210); registers no
preset with `activated_kinds` (DIV-211); sends neither `activatedKinds` nor `missionTypes`, each of which is refused,
and expects a larger effective set where a project narrowed `activated_kinds` (DIV-212); expects one `412` per
activation after the storage upgrade (DIV-213); selects mission types under `selected.mission_type` (DIV-214); and may
send `depth` (DIV-215). The generated client's optional-list getters return `null` when a list is absent
(`containerDefaultToNull`), so a GET then PUT through it leaves the stored document and its revision unchanged. Nothing is
edited in core (C-003).

For PR B, core's seam regenerates its strict DTOs from the contract again, because the closed objects `EffectiveArtifact`,
`SkippedReport`, `ActivationChange`, `Activation` and `PackRegistrationNotices` gain members and `EffectiveArtifactReason`
loses two values (DIV-228, DIV-229, DIV-237). Then it may read the two effective reads, and
expects a layer override to replace whole (DIV-216), a required artifact to be satisfied by an override (DIV-225) and an
enhancer to need its target (DIV-224); stops comparing `getActivation`'s `ETag` with `revision`, and sends either as
`If-Match` (DIV-233, DIV-234); expects `500` with `pack_unreadable` or `pack_sibling_conflict` from a write over a
closure that does not merge (DIV-230); and reads `suggestion` and `warnings` (DIV-235, DIV-237). Nothing is edited in
core (C-003).

For PR C, core's seam regenerates its strict DTOs once more, because `EffectiveGraph` and `EffectiveActionContext` are served
(each gains `layerOverrides` or `enhancements` as a required member), `EnhancerWithoutTarget.targets` becomes a list of
`NodeReference {kind, id}` (the member `artifactId` is `id`, and `kind` admits `action`), and `LayerOverride` and
`ActionEnhancement` are new. Then it may read the graph and the context (DIV-240, DIV-244), no longer cleans a deny entry on a
required artifact out of a document when an active override satisfies it (DIV-204), reads a step enhancer as bound to its
mission type (DIV-241) and expects the cascade to follow held steps only (DIV-242, DIV-243). Nothing is edited in core (C-003).

Some A0 rulings change what #399 built and have no counterpart in core's seam, so they are no divergence: a pack's
policy locks only the artifacts of the pack and of the packs it builds on (A15), a pack's own policy never refuses its
own write (B8), the required floor holds only the must-have items a pack of the closure holds (A10), and two packs that
`enhances` one artifact compose (A14). The changes fragment `2026-11-07-a0-ruling-changes.md` records them.

A pack pinned in a `createActivation` body to a version other than the one the service holds is refused `422 activation_invalid` (`version_not_held`, naming both versions), by operator ruling of 2026-11-06, so a pin is never recorded for a version the closure does not hold. Since 2026-11-07 every other write drops such a pin and reports it in `droppedPins` (DIV-209).

`getActivation` and `listActivationPacks` declare no `Charter-Source-Fingerprint` on their `200`. Both report a
change through their `ETag`, which covers the revision and the closure's packs: the closure read's since #360, and
`getActivation`'s since PR B (DIV-233), because its answer carries `denyNotApplied`, which is judged against the packs
of the closure ([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) D5 as amended on 2026-11-08).
This is an exception to the service's own fingerprint rule, not a different answer from core, so it has no id here.
Core's seam reads the fingerprint only on the `200` of a verified read, and these two reads are not among them.

Not divergences, because the contract matches core:

- `activate` and `deactivate` semantics (ADR-0020 D6, by ruling);
- preset `--force` (`{"force": true}`);
- derived `languages`, which core's seam does not send.

An unknown id in the body of `createActivation`, and one in the body of `replaceActivation` that the replaced document did not
name, are refused and core's seam drops stale deny entries at export. That is an export rule, not a divergence; what a
replace drops of a stored document is DIV-209. The per-artifact and preset writes drop the stale ids of a
stored document themselves and report them in `ActivationChange.dropped` (ADR-0059 amendment of 2026-11-06), which
core's seam reads as part of the service's answer.

## Pack-level files (DIV-301..399, ADR-0042)

The block of mission `pack-level-files-round-trip-01M60H1A`
([#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195),
[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)), and DIV-302 and DIV-303 of mission `slice-b-real-packs-register-01M7649C`
([#451](https://github.com/spec-kitty/spec-kitty-charter/issues/451),
[#431](https://github.com/spec-kitty/spec-kitty-charter/issues/431)).

### DIV-301: Validate mode, presets and the pack manifest

- **Core:** Checks each `presets/<name>.yaml` in its own format, keyed by `name`, with `mission_type_activations` and bare-string lists. It reports `preset_format`, `preset_unresolved_id` (an id that resolves neither in the built-in pack nor in this pack) and `preset_kind_gate`. It does not schema-check `pack-manifest.yaml` (offering/packs/pack_validator.py:592-593, 662-690).
- **Service:** Holds presets and the manifest to the closed, sanitized schemas of ADR-0042. A preset or manifest in core's shape, or a preset whose `id` is not its file stem, is an `artifact_schema_error` on its file. It does not resolve preset ids against the offering or check the kind gate against the listed kinds. A preset that breaks only a domain rule (an item id listed twice) passes validate mode and is refused by the load.
- **Rationale:** Validate mode and the load refuse the same files (#195, D9). Id resolution and the kind gate need the activation model, which this service does not build yet.
- **Ruling:** operator requirement on #195 (2026-10-28)
- **Status:** open, since 2026-10-28
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:592-593` and `:662-690` (`_validate_presets`); no manifest check
  - Charter: corpus case: the pinned open-packs `built-in` pack (`openPacksRef`), whose two presets and manifest validate mode refuses: 283 manifest and 14 preset findings, pinned in `FileSystemPackInspectorTest`
  - Charter: `FileSystemPackInspectorTest`, a preset whose id is not its stem, refused in validate mode and by the load

### DIV-302: The pack document, README.md, and the retired pack.md and PROVENANCE.md

- **Core:** Reads no pack document: the pack loader (`charter/offering/packs` at `78c48ce9`) neither models nor refuses a pack's `README.md` or `pack.md`. Core does use a root `PROVENANCE.md`: `charter synthesize` writes `.kittify/charter-packs/PROVENANCE.md`, the project pack's root, as a required artifact of the fresh project seed (`_fresh_project_layer.py:22,34`; `synthesize.py:157`), and the freshness check reads it as the legacy seed marker (`freshness/computer.py:739`). The service refuses that file as `retired`, so core's fresh project pack does not register as it is ([#452](https://github.com/spec-kitty/spec-kitty-charter/issues/452)).
- **Service:** `README.md` at the pack root is the pack document: held as a `PackDocument`, validated by the `pack-document` structure schema, digested in the pack-level tail and served at `GET /packs/{packId}/doc`. `pack.md` and `PROVENANCE.md` at the root are retired and refused by name (violation code `retired`), as is a root file that differs from a rule's file only in letter case. Other Markdown that no rule models is excluded and reported as `file_not_held`.
- **Rationale:** Every pack already ships a README; one document, served, replaces a second file few authors keep, and the charter's model is canonical (no reader of the old file beside the new).
- **Ruling:** operator ruling on #451 (2026-11-11); [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) decision 8 as amended 2026-11-11; [ADR-0073](/adr/0073-real-packs-register-as-they-are/)
- **Status:** open, since 2026-11-12
- **Evidence:**
  - Core: `charter/offering/packs/` at `78c48ce9`: no reader of `README.md` or `pack.md`
  - Core: `src/specify_cli/cli/commands/charter/_fresh_project_layer.py:22,34` and `synthesize.py:157` (the fresh project seed writes `.kittify/charter-packs/PROVENANCE.md`); `src/specify_cli/charter_runtime/freshness/computer.py:739` (reads it), at `78c48ce9`
  - Charter: `contracts/packs/file-kinds.yaml` (the `*/README.md` files rule; the `*/pack.md` and `*/PROVENANCE.md` retired rules; the `ignored` fallback)
  - Charter: `contracts/charter/scenarios/registration/whole-pack-registration.feature` (retired `pack.md` and `PROVENANCE.md` refused as `retired`); `pack-document.feature`

### DIV-303: org-charter.yaml and the pack.yaml key active_charter

- **Core:** Reads `org-charter.yaml` (`OrgCharterPolicy`, `activation/org_charter.py:136`; loaded by `load_org_charter_policy`, `:487`) through the descriptor's `active_charter` pointer or the org-level convention (`offering/packs/pack_layout.py:81-88`; `pack_descriptor.py:122`), and unions its `required_<plural>` lists into the project's selection.
- **Service:** Both are retired. A pack that ships `org-charter.yaml` at its root is refused by name (violation code `retired`); the closed descriptor refuses `active_charter` as an unknown key (`pack_unreadable`). The `org-charter` schema and the validate-mode finding `org_charter_schema_error` are deleted. Org enforcement is a governing preset whose must-have items are the required floor, plus `policy.yaml` levels ([Enforce presets on an organization's projects](/guides/enforce-org-presets/)).
- **Rationale:** A requirement is authored as a preset (ADR-0031); a second, list-shaped authoring form would say one thing two ways. The charter's model is canonical, so the old file is not read beside it.
- **Ruling:** operator ruling on #431 (2026-11-11); ADR-0042 amendment 2026-11-11; [ADR-0073](/adr/0073-real-packs-register-as-they-are/) decision 10
- **Status:** open, since 2026-11-12
- **Evidence:**
  - Core: `activation/org_charter.py:487` (`load_org_charter_policy`); `offering/packs/pack_layout.py:81-88`; `offering/packs/pack_descriptor.py:122` (`active_charter`), at `78c48ce9`
  - Charter: `contracts/packs/file-kinds.yaml` (the `*/org-charter.yaml` retired rule); `contracts/packs/schemas/pack-descriptor.schema.json` (closed, no `active_charter`)
  - Charter: `contracts/charter/scenarios/registration/whole-pack-registration.feature` (`org-charter.yaml` refused as `retired`; `active_charter` refused)

## Procedures and toolguide guides (DIV-401..499, ADR-0043)

The block of mission `procedure-antipattern-toolguide-round-trip-01M60H9B`
([#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194),
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/)). The service
operates as if core does not exist (the #186 ruling): core follows when it cuts over to the service.
Core paths are at spec-kitty `3131fffa`.

### DIV-401: The procedure shape

- **Core:** Requires `name` and `purpose`; a step names who acts with `actor` (`human`, `agent` or `system`, default `agent`); an anti-pattern is `{name, description}`, both required; a reference is `{type, id, reason?}`; an optional list may be empty; the `id` is `^[a-z][a-z0-9-]*$` and need not equal the file stem (`offering/schemas/procedure.schema.yaml`).
- **Service:** Requires `title` and `description`; a step names the acting `role` (`^[a-z][a-z0-9-]*$`, absent when any role may act), never `actor`; an anti-pattern is `{title, content?, bad_example?, good_example?}`; a reference is `{type, id, when?, reason?}`; an optional list holds at least one entry; the `id` is `^[a-z0-9]+(-[a-z0-9]+)*$` and equals the file stem. A procedure in core's shape, or one whose `id` is not its stem, is an `artifact_schema_error` in validate mode and is refused by the load.
- **Rationale:** The sanitized artifact shapes of ADR-0043 (S1, S10 to S16); a step names a role, not an agent kind (ADR-0032).
- **Ruling:** operator rulings on #194 (2026-10-28, 2026-10-29); #186 "as if core does not exist"
- **Status:** open, since 2026-10-29
- **Evidence:**
  - Core: `offering/schemas/procedure.schema.yaml` (`name`, `purpose`, `actor`, `procedure_anti_pattern`)
  - Charter: `contracts/packs/schemas/procedure.schema.json`; `ProcedureSchemaTest`; `ProcedureEdgesTest`, a procedure whose id is not its stem, refused by the load and in validate mode
  - Corpus case: the 39 pinned procedures at `openPacksRef` `5df92a5d`, migrated by open-packs#91, which core's own check refuses

### DIV-402: Toolguide guide naming and toolguide id

- **Core:** A toolguide's `guide_path` is any relative Markdown path (`^[^/].*\.md$`), and its `id` is `^[a-z][a-z0-9-]*$` (`offering/schemas/toolguide.schema.yaml`).
- **Service:** `guide_path` must be `<id>.guide.md` beside the toolguide, and the `id` is `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` (no trailing or doubled hyphen), so every id names a guide file the schema accepts. Any other `guide_path` or id is an `artifact_schema_error`; the guide is an ADR-0039 companion admitted only at its own `files` rule, `**/toolguides/*.guide.md`.
- **Rationale:** One file per guide with one name; an id the service accepts never names a guide file it refuses (ADR-0043 decision 4).
- **Ruling:** operator rulings on #194 (2026-10-28, 2026-10-29)
- **Status:** open, since 2026-10-29
- **Evidence:**
  - Core: `offering/schemas/toolguide.schema.yaml` (`guide_path` pattern `^[^/].*\.md$`)
  - Charter: `contracts/packs/schemas/toolguide.schema.json`; `ToolguideIdentifier`; `ToolguideControllerTest`, the ids `a-` and `a--b` refused 422
  - Corpus case: the 24 pinned toolguides and guides at `openPacksRef` `5df92a5d`

### DIV-403: The toolguide guide's structure

- **Core:** Reads a guide as free Markdown; validate mode does not judge its structure.
- **Service:** A guide is structured (ADR-0043 decision 4): no frontmatter, its H1 equal to the toolguide's `title` (required in a stored guide), no preamble, a non-blank `## Overview`, the fixed blocks in order and freeform sections between `## Usage` and `## Cautions`. The load and validate mode both refuse a guide that breaks a rule; validate mode reports it as `artifact_schema_error` on the guide's file.
- **Rationale:** One structure across all guides, so a guide's meaning round-trips (ADR-0043); validate mode and the load refuse the same files.
- **Ruling:** operator ruling on #194 (2026-10-29), structured guide
- **Status:** open, since 2026-10-29
- **Evidence:**
  - Charter: `contracts/packs/schemas/toolguide-guide.schema.json`; `GuideMarkdown`; `FileSystemPackInspectorTest`, a guide with a preamble, without an overview or without an H1 reported in validate mode; `ToolguideGuideTest`
  - Corpus case: the 24 pinned guides at `openPacksRef` `5df92a5d`

## Mission types (DIV-501..599, ADR-0044)

The block of mission `mission-type-consolidation-01M65MCK`
([#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194),
[ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/)). As for the previous
block, the service operates as if core does not exist (the #186 ruling): core follows when it cuts
over to the service. Core paths are at spec-kitty `c678037c`.

### DIV-501: A mission type is an artifact kind

- **Core:** `mission-type` is a charter kind token but not an `ArtifactKind`: `ArtifactKind.from_operator_token` raises `MissionTypeNotAnArtifactKind` for it, and callers route it to the mission-tier handling path (`offering/artifact_kinds.py:53-61`, `:325-329`).
- **Service:** `mission_type` is an artifact kind (`ArtifactKind.MISSION_TYPE`, plural `mission_types`; activatable like any kind: selected under `selected.mission_type`, deniable unless required or locked, and none in force when absent, DIV-203 and DIV-214, updated 2026-11-07), bound in the kind registry and a pack-manifest constituent kind. Its node kind is artifact-backed. A pack holds one file per type, `missions/<id>/<id>.mission-type.yaml`, whose steps are sub-items of `steps` and `on_demand_steps`; a mission step is a domain type, not an artifact kind, and has no fragment plural (DIV-011 is unchanged).
- **Rationale:** A mission type is offered and activated like an artifact and has its own domain model, the shape #148 asks for; every binding, `CharterArtifact.kind()` and the pack aggregate are keyed by `ArtifactKind` (ADR-0037).
- **Ruling:** operator rulings on #194 (2026-10-28, 2026-10-30); #186 "as if core does not exist"
- **Status:** open, since 2026-10-30
- **Evidence:**
  - Core: `offering/artifact_kinds.py:53-61` (`MissionTypeNotAnArtifactKind`) and `:325-329` (`from_operator_token`)
  - Charter: `ArtifactKind.MISSION_TYPE`; `ArtifactKindTest`, `NodeKindTest`, `VocabularyEqualityTest`; `contracts/packs/schemas/mission-type.schema.json`; `MissionTypeSchemaTest`, `MissionTypeBindingTest`
  - Corpus case: the six mission types of the open packs (built-in 4, cave-kitty 1, qa 1), migrated by the service's own writer (spec-kitty/spec-kitty-open-packs#95) and re-pinned at `openPacksRef` `86d92846`

### DIV-502: The retired mission files

- **Core:** Reads a mission type from up to eight file families: `mission_types/<t>.yaml`, `missions/<t>/mission.yaml`, `mission-runtime.yaml`, `governance-profile.yaml`, `expected-artifacts.yaml`, `actions/<a>/index.yaml`, `mission-steps/<t>/<s>/step.yaml` with its `prompt.md` and `guidelines.md`, and `command-templates/*.md` (`offering/missions/mission_type_repository.py`, `mission_step_repository.py`, `action_index.py`, `repository.py`; `activation/mission_type_profile_repository.py`).
- **Service:** These files are retired. Each family has a `retired` rule in `contracts/packs/file-kinds.yaml` with its reason. The store's load and validate mode refuse a retired file by name, YAML and Markdown alike; it is never a companion; the whole-pack gate reports it in its own `retired:` bucket; and it is digested in the fingerprint tail, so removing one changes the fingerprint.
- **Rationale:** One file says what a mission type is (ADR-0044 decisions 2 and 6); the families contradicted each other on labels, versions, step order and ids. A stale file cannot hide: it is refused, not skipped.
- **Ruling:** operator ruling on #194 (2026-10-28), decision 5: the mission exclusions are removed and the retired files refused
- **Status:** open, since 2026-10-30
- **Evidence:**
  - Core: `offering/missions/action_index.py:61`; `offering/missions/mission_step_repository.py:158` and `:270`; `offering/missions/repository.py:394`; `offering/missions/mission_type_repository.py:449`; `activation/mission_type_profile_repository.py:50`
  - Charter: `contracts/packs/file-kinds.yaml` (`retired`); `RetiredFilesTest`, every family refused by the load and in validate mode, never a companion, bucketed by the census
  - Corpus case: the 153 retired files of the pinned built-in (121), cave-kitty (13) and qa (19) packs, removed by the migration (spec-kitty/spec-kitty-open-packs#95, at `openPacksRef` `86d92846`)

### DIV-503: A relationship from a mission type

- **Core:** Has no relationship registration. A graph edge from `mission_type:<id>` is authored in a graph shard (`mission_type.graph.yaml`) or an org fragment, and resolves when the graph declares the node.
- **Service:** `PUT /packs/{packId}/relationships` registers an edge whose `from` is `mission_type:<id>` when the pack holds that mission type's file. Before ADR-0044 it refused every such edge with 409 `registration_conflict`, since a mission type was a node kind only and no artifact of the pack had that URN. A mission type the pack does not hold is still refused 409. No schema or operation changes.
- **Rationale:** The `from` of a registered edge must resolve to an artifact of the pack (`putPackRelationship`); a mission type is now one (DIV-501).
- **Ruling:** ADR-0044 (consequences); spec NFR-001 of the mission
- **Status:** open, since 2026-10-30
- **Evidence:**
  - Charter: `contracts/charter/scenarios/relationships.feature`; `RelationshipRegistrationService`
  - Charter: `contracts/charter/CHANGELOG.md`, 0.1.0-SNAPSHOT, "Mission types are an artifact kind (issue #194)"

## Registration (DIV-601..699, ADR-0065)

The block of PR A of mission `layer-merge-and-effective-reads-01M6R0J6`: whole-pack registration, the pack sources, and the
removal of an artifact or an edge. As for the previous blocks, the service operates as if core does not exist (the #186
ruling), and core follows when it cuts over. The entries are `open`: `registration/whole-pack-registration.feature` and
`pack-removal.feature` pin the behavior they describe, and the unit tests named in each entry pin the rest. A reader's
guide is [Compose packs](/guides/compose-packs/#register-a-pack); the operator's is
[Building the service](/guides/service-build/#registering-packs).

### DIV-601: A registration replaces the stored pack

- **Core:** The service's understanding of core, to be confirmed against core: it merges per artifact: a registration adds or updates the artifacts it carries and leaves the others.
- **Service:** `PUT /packs/{packId}` and `POST /packs` replace the stored pack whole. An artifact, an edge, a preset item or a file the upload does not carry is gone, and the same version with the same revision ends unchanged. The registration is asynchronous: 202 and a status to poll. Two registrations run and eight wait, a ninth waiter is 503 with `Retry-After`, and the 90 second deadline counts the time spent in the queue. A slot is taken after the body is spooled, so a slow upload holds none while it arrives (what it holds is a connection, up to 32 MiB of staging and one of ten places for uploads open at once; the eleventh is 503 with `Retry-After`); the queue and `If-Match` are checked before the body is read and again then. The free-space check (503 `pack_storage_full`) runs at admission, on top of the declared bytes of the uploads already open, and when the job runs, on the staging and the pack volume, against the per-job peak of about three times the archive bound. The status is kept in memory for one hour, at most 1,000 entries.
- **Rationale:** A registration is the whole truth about a pack, so nothing a client dropped can survive in it, and one request is one atomic swap.
- **Ruling:** ADR-0065 D1, D8; SC-003; FR-024
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `contracts/charter/scenarios/registration/whole-pack-registration.feature` (A replace drops what the new archive leaves out and keeps what it carries; Sending the identical archive again changes nothing)
  - Charter: `PackRegistrationServiceTest`; `RegistrationAcceptanceTimingTest`; `RegistrationJobsTest`

### DIV-602: The version is a label, and a version is never reused with other content

- **Core:** The service's understanding of core, to be confirmed against core: it registers per artifact and keeps no history of the versions a pack name held.
- **Service:** `pack_version` is a label, not an order: a registration may go back to an older version. The service keeps every (`pack_version`, revision) a pack name was registered with, in the same swap as the pack, and a DELETE keeps the history. The same version with another revision is refused 409 `pack_version_conflict`, whether or not it is the stored version. This check runs before the `If-Match` precondition, so a re-send of an archive with the same version after a per-artifact PUT moved the revision is 409, not 412; the 412 race needs a new version. A history entry that the service restores after a repair carries the time of the repair, not of the lost registration. The service restores the stored pair only when the history has no entry for the stored `pack_version`: a per-artifact write moves the revision without registering anything, so a version registered with revision A stays restorable with A after a PUT moved the stored revision.
- **Rationale:** A version that names content must keep naming it, or a client that pins a version reads different packs under one label.
- **Ruling:** FR-026; decision `DM-01M6RA9BKJY7X9BFV93QW1KM1T` (B1); IC-16
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `contracts/charter/scenarios/registration/whole-pack-registration.feature` (The same version with other content is refused, and a new version is accepted)
  - Charter: `PackVersionHistoryTest`; `PackRegistrationServiceTest`

### DIV-603: The write gate covers removal and each edge, on its source

- **Core:** The service's understanding of core, to be confirmed against core: it judges the artifacts a write adds or changes.
- **Service:** REMOVE joins the gate's operations. An edge a write adds or drops is a RELATIONSHIP write on its source, in a whole-pack registration and in the relationship PUT and DELETE alike. The relationship DELETE is refused 409 `governance_policy_violation` for an edge whose source is a locked artifact of an ancestor, as a whole-pack replace is refused for dropping it. An artifact DELETE is refused the same way for an id an ancestor locks.
- **Rationale:** A lock must hold whichever door the write comes through; removal and edge writes are writes.
- **Ruling:** ADR-0012; decision `DM-01M6TR1KDAJ64GMJD6AYNZKT59` (item 4)
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `PackRegistrationServiceTest` (anEdgeRemovedFromALockedArtifactIsGatedAndOneFromAnOpenArtifactIsNot); `RelationshipRemovalServiceTest` (anEdgeWhoseSourceIsALockedArtifactOfAnAncestorIsNotRemovedAndOneFromAnOpenArtifactIs); `ArtifactRemovalServiceTest`

### DIV-604: The impact check is strict and an unreadable pack is repaired by a whole-pack replace

- **Core:** The service's understanding of core, to be confirmed against core: it warns when a write leaves a closure that no longer composes or breaks a relation rule.
- **Service:** A registration, a per-artifact write, the relationship write and both DELETEs are refused 422, with the composition code or one of the five relation codes naming the packs, when a closure the pack joins no longer composes or breaks a rule after the write. PR A does not apply the repair writes of #404. One stored dependent that cannot be read makes every registration of its ancestor fail `pack_unreadable`; the repair is to fix or remove the dependent. A write into a stored pack that cannot be read, or whose dependency is missing, is refused even when it would repair it. A whole-pack replace is the repair path, and the stored pack is salvaged for it: it is read file by file, an artifact file counts by the id it declares and by the id its path names, and each readable graph entry is an edge. An artifact the upload leaves out is a REMOVE through the gate and the inbound scan, a dropped edge is a RELATIONSHIP write on its source, and a stored artifact that does not bind counts as a REPLACE. When the set of stored artifacts cannot be determined at all (the directory cannot be listed), the registration is refused `pack_unreadable`; the pack is never taken as empty. At start-up the service logs a WARN for each stored pack that would now be refused.
- **Rationale:** A write that leaves a broken closure would make every composed read fail; refusing it keeps the offering readable.
- **Ruling:** Decisions `DM-01M6TQ67C9ZTNGZB9CY28JS54F`, `DM-01M6VG6GC40AKYYFRR35SV8HVW`, `DM-01M6V4WMF8CD8NBN7A065E9ZQ5`, `DM-01M6V6N74ER28H8S6TSVZ8ZEY2`, `DM-01M6RA8225EXG6GAT1MM2R9T6C` (A5); FR-025
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `contracts/charter/scenarios/registration/whole-pack-registration.feature` (A registration that leaves a closure unable to compose is refused naming the packs); `contracts/charter/scenarios/pack-removal.feature` (A removal that leaves a closure of a stored activation broken is refused 422)
  - Charter: `RegistrationImpactTest`; `PerArtifactImpactTest`; `PackRegistrationServiceTest` (aStoredPackThatCannotBeReadIsReplacedByAFixedUpload)

### DIV-605: Registration notices name activatable kinds only

- **Core:** The service's understanding of core, to be confirmed against core: it has no registration notices.
- **Service:** The notices list the must-have items added to or removed from the required floor and the artifacts put under or released from a lock. They name activatable kinds only. An entry of another kind (a template, an asset, a guard, a lifecycle) is left out and logged at WARN with its URN. Issue #417 tracks widening the kinds.
- **Rationale:** The contract's artifact reference names activatable kinds, and a notice must be a reference a client can follow.
- **Ruling:** Operator ruling 2026-11-07, decision `01M6VXMCDQJWBJXGRQNMF5G6SV`; #417
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `PackRegistrationMapperNoticesTest`; `contracts/charter/changes/2026-11-08-a-notices-activatable-kinds.md`

### DIV-606: Removing one artifact from a pack

- **Core:** The service's understanding of core, to be confirmed against core: it has no operation that removes one artifact from a pack; an author deletes its files.
- **Service:** `DELETE /packs/{packId}/artifacts/{kind}/{artifactId}` removes the artifact, its companion files, its registered edges and the preset items of the pack that name it, and deletes a preset key whose last item goes. It needs `If-Match` (428 without, 412 stale; * is accepted) and answers 204 with the new revision. It is refused 409 `artifact_referenced` while another artifact or pack refers to it (another pack's preset item and a descriptor overrides entry count), 409 `governance_policy_violation` for an id an ancestor locks, and 409 `registration_conflict` when an author-written graph document names the artifact. It never rewrites an author graph file. It does re-serialize a preset file when it removes a preset item, so comments and formatting are lost. A removal that leaves a closure broken is 422.
- **Rationale:** A client can remove what it registered without sending the whole pack, and a refused removal changes nothing.
- **Ruling:** ADR-0065 D4; FR-005, FR-007; decision `DM-01M6TQ62HXYF362GAFS2BZ3PGF`
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `contracts/charter/scenarios/pack-removal.feature`; `ArtifactRemovalServiceTest`

### DIV-607: Removing one registered relationship

- **Core:** The service's understanding of core, to be confirmed against core: it has no operation that removes one edge; an author edits a graph document.
- **Service:** `DELETE /packs/{packId}/relationships` removes an edge only from `registered.graph.yaml`. It is 409 `relationship_not_registered` for a pair an author wrote, a derived one, or one held both in the registered file and in an author-written document, and 404 `relationship_not_found` when no graph document holds the pair. `If-Match` is required (428, 412). A reference that names a pack matches only an edge end that names the same pack, so with the edges A to `base::X` and A to `other::X` a DELETE naming `base::X` removes one; a reference that names no pack matches by its URN, as the kernel compares. The removed edge is gated as a RELATIONSHIP write on its source (DIV-603). A removal that leaves a closure broken is 422 (strict; #404 is out of scope).
- **Rationale:** Only the edge the client named may go, and a 204 must never leave the edge in force.
- **Ruling:** ADR-0065 D4; FR-006; decisions `DM-01M6TQ62HXYF362GAFS2BZ3PGF`, `DM-01M6TR1KDAJ64GMJD6AYNZKT59`
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `contracts/charter/scenarios/pack-removal.feature` (A registered relationship is removed, whichever end the request names first; An edge the author wrote into a source shard is refused); `RelationshipRemovalServiceTest`

### DIV-608: Known gaps of the per-artifact writes

- **Core:** The service's understanding of core, to be confirmed against core: it has no per-artifact write; the manifest is derived when the pack is written.
- **Service:** A per-artifact write or DELETE does not update `pack-manifest.yaml`, so its hashes go stale and after a DELETE it still lists the removed file. Reads do not verify the manifest; a round trip and validate mode report it. A per-artifact PUT with `If-Match: *` satisfies the 428 requirement. A DELETE is judged on the facts overlay; the edited tree is not read again. A per-artifact PUT or POST and the relationship write can answer 422 with composition and relation codes (DIV-604).
- **Rationale:** Recorded as known limits of PR A so a consumer does not rely on the manifest after a per-artifact write.
- **Ruling:** ADR-0042; plan IC-06; WP19 review
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `ArtifactRemovalServiceTest`; `RelationshipRemovalServiceTest`

### DIV-609: Pack-source configuration

- **Core:** The service's understanding of core, to be confirmed against core: it has no service-side source configuration.
- **Service:** Each source kind is off by default and its flag is read at every request. https: enabled, allowed-hosts (empty), timeout 60s, max-size 32MB, max-redirects 3. `git`: enabled, allowed-hosts (empty), executable, timeout 60s. filesystem: enabled and root. Registration itself is off (`charter.registration.enabled`) and spools under `charter.registration.staging-dir`. A filesystem root must not lie under /proc, /sys or /dev or overlap `charter.config-root`. The resolver guard covers the union of the guide, archive and `git` hosts. A filesystem kind switched on with no root stops the start.
- **Rationale:** A service that was never configured for a source must refuse it, and an outbound fetch is reachable only to hosts an operator named.
- **Ruling:** ADR-0065 D10; FR-037; NFR-010; plan IC-18
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `docs/guides/service-build.md` (Registering packs); `PackSourceRegistrationTest`; `SourceUrlsTest`

### DIV-610: Refusals of a source

- **Core:** The service's understanding of core, to be confirmed against core: it has no source refusals.
- **Service:** A source the service refuses ends the registration 422 `pack_source_refused`. The detail is <token>: <fixed sentence>, with no URL, path or upstream text. The tokens are `host_not_allowed`, `address_not_global`, `url_refused`, `redirect_refused`, `wrong_media_type`, `too_large`, `digest_mismatch`, `name_mismatch`, `source_malformed`, `path_refused`, `bad_ref` and `commit_mismatch`. An upstream failure is a plain 502 and a timeout a plain 504. Every local-path refusal is one `path_refused`; its reason is in the operator log only. `PackSource.url` matches ^https://\S+$. For POST the descriptor name must equal the target pack id; for PUT it need not.
- **Rationale:** A refusal must not echo an address or a path, and must not tell a client which host or path exists.
- **Ruling:** ADR-0065 D10; IC-05; decisions of WP17 and its review
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `SourceRefusalContractTest`; `PackSourceStagersTest`; `contracts/charter/scenarios/registration/whole-pack-registration.feature` (A source the service refuses ends the registration refused with pack_source_refused)

### DIV-611: The recorded source of a registration

- **Core:** The service's understanding of core, to be confirmed against core: it has no registration status.
- **Service:** The status of a succeeded registration records its source. `source.url` keeps no query, no fragment and no user information. For a `git` source, `source.commit` is the commit the ref resolved to.
- **Rationale:** A query or user information can carry a credential and must not be stored or echoed.
- **Ruling:** ADR-0065 D10; WP18 and its review
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `PackRegistrationMapperSourceTest`; `PackSourceRegistrationTest`

### DIV-612: The race of the local source

- **Core:** The service's understanding of core, to be confirmed against core: it has no local source.
- **Service:** The file-key and link-count checks look a file up by name, not on the open handle, because the JDK has no `fstat` on a channel. A race between the check and the open needs write access under the root.
- **Rationale:** A known limit: closing it needs a call the JDK does not offer, and the threat needs the attacker to write under the operator's root.
- **Ruling:** ADR-0065 D10; WP17 review
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `LocalSourceReader`; `PackSourceStagersTest`

### DIV-613: The git source

- **Core:** The service's understanding of core, to be confirmed against core: it has no `git` source.
- **Service:** The service runs the system `git`, 2.50.1 or later, on the PATH or at `charter.pack-sources.git.executable`, into a bare work repository; there is no JGit and no work tree. It checks the version at every start. An old or missing `git` makes the `git` kind answer 503 `pack_source_unavailable` while the other kinds work. It accepts https only, follows no redirect, and refuses links and submodules in the tree (422 `validation_failed`, violation `entry_type`, as for an archive). A branch ref without a commit is 428 unless `If-Match` or `If-None-Match: *` is sent. The reasons `bad_ref` and `commit_mismatch` are new. sha256 repositories are untested. CI has a new required job, `git`-source, that runs the real-`git` tests. Every run carries config pins that bound the memory git inflates and maps (a 1 MiB big-file threshold, 16 MiB of pack window and window memory, 64 MiB of mapped packs, a 16 MiB delta base cache, one thread), so a fetch stays within tens of MiB. They do not cap address space or time beyond the one deadline. With `core.bigFileThreshold=1m` and `transfer.fsckObjects=true` `git` refuses a `.gitmodules` blob over 1 MiB (`gitmodulesLarge`).
- **Rationale:** A host binary is the operator's ruling (no JGit); the version floor and the fixed command line bound what it can do.
- **Ruling:** C-008; NFR-011; ADR-0065 D10; WP18 and its review
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `contracts/charter/scenarios/registration/whole-pack-registration.feature` (A git ref is pinned to the commit it points at; A hostile git ref is refused); `NetworkCodeBoundaryTest`; `.github/workflows/service.yml` (git-source)

### DIV-614: A fault of the activation store ends a pack write as an internal error

- **Core:** The service's understanding of core, to be confirmed against core: it has no activation store to list.
- **Service:** The registration impact check lists the stored activations. When the store cannot be listed (the platform refuses to read its directory), a per-artifact write, a `DELETE` or a registration job ends as `500 internal_error`, the job as refused with `internal_error` and `failed:internal_error` in the audit log. Nothing is written, the body is the generic one and the cause is in the operator log. This is how a stored document that cannot be read back is answered. No refusal code names a fault of the activation store, and none is added.
- **Rationale:** A fault of the store is the service's, not the client's, and a code that blames neither the pack nor the request would mislead.
- **Ruling:** ADR-0065; issue 418 (R6)
- **Status:** open, since 2026-11-08
- **Evidence:**
  - Charter: `ActivationListingFaultTest`; `FileActivationRepositoryListingFailureTest`; `ActivationImpactAdapterTest`
