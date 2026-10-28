---
title: Divergence register
description: The single register of every deliberate difference between the charter service and core, each with a stable id, the ruling behind it and the case or evidence that pins it.
doc_status: draft
updated: 2026-10-20T00:00:00Z
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
- **Activation (DIV-201..202, ADR-0020)** is proposed, below; its operations are not built yet.
- **Pack-level files (DIV-301..399, ADR-0043)** is last.

The register has two forms of one list. This page is for readers. The file
[`divergences.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/conformance/divergences.yaml)
is for programs: it holds every entry of all four blocks (pack schemas, context read, the
proposed activation block and pack-level files) and documents its own schema at the top. The
proposed activation entries carry status `proposed`, so the gate does not enforce them until their
operations are built. Each entry has a status:

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
- **Ruling:** R5 (2026-10-19); plan call P1
- **Status:** open, since 2026-10-19
- **Evidence:**
  - Core: `offering/packs/pack_descriptor.py:88` (`extra="forbid"`)
  - Core: `offering/drg/org_pack_loader.py:547-560` (the second report, when `drg/fragment.yaml` exists)
  - Charter: `contracts/packs/schemas/pack-descriptor.schema.json: the top level stays open, so an unknown key is reported by the adapter and not refused (import change C9; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`

### DIV-006: Retired pack.yaml fields, such as accompanies_doctrine_pack

- **Core:** Refused with a blocking finding (offering/packs/retired_fields.py:84-91).
- **Service:** Warned like any unknown key (pack_unknown_key).
- **Rationale:** One rule for every key the schema does not declare. This is laxer than core and is accepted.
- **Ruling:** R13 (2026-10-19)
- **Status:** open, since 2026-10-19
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
  - Charter: `contracts/packs/schemas/agent-profile.schema.json and org-charter.schema.json: the lenient conversions are dropped (import change C10; see kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md, Import changes C1-C16)`
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
- **Service:** Models both. The schema's Relation enum is core's 15 values, including the legacy in_tension_with, plus impacts, 16 in all, and an edge may carry a numeric impacts strength. The legacy in_tension_with stays in the schema and is read by the service as impacts with strength -1.
- **Rationale:** The contract leads and core aligns at the cutover. The service's own Relation type has 15 values, the same set without in_tension_with, which is a known difference.
- **Ruling:** ADR-0009 D1; ADR-0014 D-2; plan call P11
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

- **Core:** Never reads it: `charter pack validate` has no leg for charter.yaml (only org-charter.yaml, offering/packs/pack_validator.py:595-604).
- **Service:** Validates it with charter.schema.json, including cli_timeout_seconds, which refuses .nan and .inf.
- **Rationale:** A file the pack ships should be checked against the schema that describes it, and a non-finite timeout is never meant.
- **Ruling:** R11 register decision (2026-10-20, operator)
- **Status:** open, since 2026-10-20
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:595-604` (the only charter leg is org-charter.yaml)
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

## Activation (DIV-201..202, ADR-0020), proposed

Proposed with [ADR-0020](/adr/0020-offering-and-activations/) on #123, and agreed with core's
activation ledger mission on 2026-10-20. Core's conformance-case table carries the same entries
(core response, mirrored on #9). Both are to be settled before the first activation operation is
routed to the service ([ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) D5).

| Id | The charter answers | Core answers | Corpus case |
|---|---|---|---|
| DIV-201 | A required or locked artifact is active on read, even under an explicit allow list that omits it (ADR-0020 D7) | Does not add it under such an allow list; doctor and list warn | `required-under-allow-list` |
| DIV-202 | The required floor is derived from the packs at read time and never stored (ADR-0020 D4) | Promotes org `required_*` into the allow list at write time | `required-promoted-at-write` |

Not divergences, because the contract matches core:

- `activate` and `deactivate` semantics (ADR-0020 D6, by ruling);
- preset `--force` (`{"force": true}`);
- derived `languages`, which core's seam does not send.

Unknown and stale deny entries are refused by the contract and dropped by core's seam at export.
That is an export rule, not a divergence.

## Pack-level files (DIV-301..399, ADR-0043)

The block of mission `pack-level-files-round-trip-01M60H1A`
([#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195),
[ADR-0043](/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest/)).

### DIV-301: Validate mode, presets and the pack manifest

- **Core:** Checks each `presets/<name>.yaml` in its own format, keyed by `name`, with `mission_type_activations` and bare-string lists. It reports `preset_format`, `preset_unresolved_id` (an id that resolves neither in the built-in pack nor in this pack) and `preset_kind_gate`. It does not schema-check `pack-manifest.yaml` (offering/packs/pack_validator.py:592-593, 662-690).
- **Service:** Holds presets and the manifest to the closed, sanitized schemas of ADR-0043. A preset or manifest in core's shape, or a preset whose `id` is not its file stem, is an `artifact_schema_error` on its file. It does not resolve preset ids against the offering or check the kind gate against the listed kinds. A preset that breaks only a domain rule (an item id listed twice) passes validate mode and is refused by the load.
- **Rationale:** Validate mode and the load refuse the same files (#195, D9). Id resolution and the kind gate need the activation model, which this service does not build yet.
- **Ruling:** operator requirement on #195 (2026-10-28)
- **Status:** open, since 2026-10-28
- **Evidence:**
  - Core: `offering/packs/pack_validator.py:592-593` and `:662-690` (`_validate_presets`); no manifest check
  - Charter: corpus case: the pinned open-packs `built-in` pack (`openPacksRef`), whose two presets and manifest validate mode refuses: 283 manifest and 14 preset findings, pinned in `FileSystemPackInspectorTest`
  - Charter: `FileSystemPackInspectorTest`, a preset whose id is not its stem, refused in validate mode and by the load
