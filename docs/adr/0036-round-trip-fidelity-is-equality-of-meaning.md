---
title: "ADR-0036: Round-trip fidelity is equality of meaning, and unknown fields are refused"
description: What the whole-pack round trip compares per file kind, how an unknown field is refused, the canonical CRG edge, and how pack inconsistencies go upstream.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0024-doctrine-artifact-family-contract
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
  - adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship
  - adr/0035-charter-artifact-interface-lives-in-the-kernel
  - architecture/descriptors
  - architecture/logical-model
  - conformance/divergence-register
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before building the round-trip gate or the whole-pack seam. It defines "equal", the refusal path and the per-kind table.
  - profile: pack-author
    guidance_text: Read this for what a pack must look like to pass the round trip, and how an inconsistency found in your pack reaches you.
  - profile: core-maintainer
    guidance_text: Read this for the retired pack_unknown_key diagnostic and the forward-compatibility cost of refusing unknown fields.
---

# ADR-0036: Round-trip fidelity is equality of meaning, and unknown fields are refused

## Status

**Accepted** — 2026-10-27. Decider: Stijn Dejongh (operator). Records the operator rulings of
2026-10-27 on [#188](https://github.com/spec-kitty/spec-kitty-charter/issues/188) (part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), and the operator's answers to
its three open questions the same day (see [Open questions](#open-questions)). The readings in
Decisions 2 to 7 stand.

This record supersedes in part
[ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) (Amendment 2026-10-19,
rulings R5 and R13: unknown `pack.yaml` keys tolerated with a warning) and
[ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/) decision 5 (`DrgEdge`
record equality as the round-trip fidelity assertion). It clarifies
[ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) decision 2 (what
"round-trips losslessly" compares). The charter's PROJECT_002 is amended in the same change to read
"round-trips without loss of meaning (ADR-0036)" (open question 2).

Extended in part by [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) (2026-10-27):
the per-kind table's `*.skill.yaml`, `*.glossary-pack.yaml` and `*.skill.md` rows, where a kind
binding carries companion files beside its YAML artifact.

## Context

Epic #186 reads every file of a Charter Pack into the domain and writes it back. A gate (#187)
compares what was written with what was read, so "the same" needs a definition first. Three facts
force the decision:

- **Unknown keys are tolerated and then lost.** `pack.yaml` keys outside the schema are kept as
  names only (`PackDescriptor.undeclaredKeys()`), warned as `pack_unknown_key` and dropped on write.
  The contract says such a key is "never refused" (DIV-005, DIV-006). The other 13 pack schemas are
  already closed (`additionalProperties: false`).
- **Today's round-trip test cannot see loss.** `PackDoctrineCorpusRoundTripTest` reads a file into a
  record, writes it, reads it back and compares the two records. A field the reader drops is missing
  on both sides, so the test passes. Today the graph writer drops node `provenance` and `tags` and
  fragment node `body_path`, and it hard-codes the shard header (`generated_at`).
- **The CRG sources do not fit the domain one to one.** Built-in shards carry 55 component pairs with
  more than one edge, where ADR-0027 keeps one structural relationship per pair. One of them is an
  `in_tension_with` beside a `requires`, which ADR-0028 allows, so 54 carry two structural edges.
  Edge lists repeat and reorder freely.

Probes on `spec-kitty-open-packs` at `openPacksRef` `01edb4e` (397 schema-mapped files, 1222 edges)
are in the [mission research](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/round-trip-fidelity-adr-01M5Y2A1/research/grounding-squad.md).
Refusing unknown fields breaks none of today's files.

## Decision

### 1. The operator rulings

These are recorded as given on #188 (2026-10-27):

1. **Unknown fields are refused.** A field outside the schema fails the load. The reader does not
   carry it through.
2. **Equality is by meaning, not bytes.** The round trip compares what is read, not the bytes of
   each file. Byte-for-byte reproduction of YAML is not a goal.
3. **Inconsistencies in source packs are fixed in the packs** (`spec-kitty-open-packs`). The service
   adds no special logic to keep or support an inconsistent structure.
4. **CRG edges compare in canonical form.** Order and duplicates do not count. A pack whose edges
   only work in another form is fixed in the pack.

Decisions 2 to 7 derive the rules from these rulings. Where a rule is the author's reading rather
than a ruling, it says so ("*Reading:*"). Those readings were posted on #188 for the operator to
overrule.

### 2. What "the same meaning" compares

**Source against written.** The gate compares the source file with the file the service writes,
both read into the *canonical form* below. It does not compare two records read by the service's
own reader, because a field that reader drops would be missing from both. A pack round-trips when
its written files are, path for path, the same set as its source files, and each pair has the same
meaning. A missing or extra file is a difference.

**Validation comes first.** A file is validated against its schema before it is compared. A value
of the wrong type is a schema violation and is refused, never converted (ADR-0017 R9: no lenient
conversions). The rules below apply only to files that passed.

**Canonical form of a YAML file.** The canonical form is the document as the service's reader
resolves it: the JSON schema of YAML 1.2, plus `~`, `Null` and `NULL` read as null (R18;
`packs.infra.storage.Yaml`). Duplicate keys are refused at parse. Under this resolver `True`,
`yes`, `0x10` and unquoted dates are strings, `1e3` is a number, and `<<` is a plain key
(refused in a closed schema, data in an open map). *Reading:* core reads packs with a YAML 1.1
resolver. A value the two resolvers read differently is a pack inconsistency, fixed in the pack by
writing it unambiguously. None exists at `01edb4e`.

| Dimension | Rule |
|---|---|
| Fields | Only the fields the file's schema declares exist; any other field is refused before comparison (Decision 3). Every declared field compares, header fields included (`schema_version`, `generated_at`, `generated_by`): none is exempt. |
| Absent, `null`, empty | For a **field the schema declares**, at any depth (an item of a set or a sequence that is an object has declared fields too: edge `when`, step `description`), where the schema accepts them, an absent field, an explicit `null` and an empty list or map mean the same. The schema decides where `null` is valid: `parent_pack: null` is valid, `pack_id: null` is refused. *Reading:* the service's reader already treats them so. |
| Schema defaults | *Reading:* an absent declared field equals the field written with its schema default. |
| Scalars | Compared as resolved values. A string and a number differ (`"1"` and `1`), as do a string and a boolean (`"true"` and `true`). *Reading:* numbers compare by value, so `1` and `1.0` are equal. JSON Schema 2020-12 accepts `1.0` as an integer too, and the reader holds an `impacts` edge's strength as a floating-point number. |
| Text | A string compares by its resolved value. Literal (`\|`), folded (`>`), quoted and plain styles are equal when they resolve to the same string, and differ when they do not (folding changes the string). |
| Key order | Never counts. |
| Presentation | Quoting, flow or block style, indentation, line width, document markers, a byte-order mark, anchors and aliases (compared expanded) and comments carry no meaning. A write therefore does not keep comments; under the amended PROJECT_002 that is not a loss (open question 2, answered). |
| Lists | A list is a **sequence**: order and repetition count. The exceptions are the **sets** named in the [per-kind table](#per-kind-table). A set compares by member identity. Repeating an identical member does not count. Two members with the same identity and a different value are a pack inconsistency (Decision 5), not two members. *Reading:* no list becomes a set without being named here, so no list loses its order by accident. |
| Open maps | *Reading:* a map whose schema sets `additionalProperties` to anything but `false` is an **open map**: it holds data, not fields. Examples are styleguide `tooling` and `structural_lint_config`, charter `overrides` and org-charter `interview_defaults`. Its keys are not refused. It compares deeply, by value: inside it, `null`, an empty map and an absent key are **not** the same (the writer keeps `{}` and nested nulls as data). Likewise a `null` item in a list is an item: `[x, null]` and `[x]` differ. |
| Documents | *Reading:* a pack YAML file holds one document. A multi-document file is a pack inconsistency. An empty file and a file that holds only `null` are equal. A schema that requires a mapping refuses both, and a top-level scalar too. |

**Other files.** *Reading:* a Markdown file compares its YAML frontmatter, if any, as YAML, and
its body as text, with line endings normalized to LF and one final newline. Any other file
(binaries, scripts, CSV, templates, asset payloads) compares by bytes, because no other meaning is
defined for it.

### 3. How an unknown field is refused

- **It is a schema violation like any other.** Every pack schema is closed
  (`additionalProperties: false` at every level the schema defines, except its open maps).
  `pack-descriptor.schema.json` is closed to match (ADR-0017 R20: the schemas are edited in place).
  This converges with core, which reports an unknown `pack.yaml` key as an error and refuses retired
  fields (DIV-005, DIV-006).
- **Where.** The storage adapter's schema validation detects it (`packs.infra.storage.PackSchemas`,
  the outer ring of [ADR-0006](/adr/0006-onion-vertical-slices-archunit/)). It hands the domain a
  `SchemaViolation`, and the domain's `PackPolicy` chooses the code and writes the text, as for every
  violation. The key never reaches a domain record. *Reading:* no new code path and no new finding
  code.
- **Which path.** An unknown field takes the path that file kind already takes for a schema
  violation:

  | File | Finding code | On load | In validate mode |
  |---|---|---|---|
  | `pack.yaml` | `PACK_UNREADABLE` | The pack is refused: `pack_unreadable` on the wire. | Reported as blocking. |
  | `*.graph.yaml`, `drg/fragment.yaml` | `DRG_SCHEMA_ERROR` | No operation loads a pack's graph today (`drg_schema_error` is `raised: false`). When the whole-pack seam (#189) loads it, the pack is refused as `drg_schema_error`. | Reported as blocking. |
  | `org-charter.yaml` | `ORG_CHARTER_SCHEMA_ERROR` | The file is skipped. The store returns the rest of the pack and lists the violation (`PackRead.violations()`). No load-path finding reaches the wire today. | Reported as blocking. |
  | Every other schema-mapped file (the artifact files: directive, tactic, styleguide, toolguide, paradigm, procedure, agent profile, step contract, asset, and a stray `charter.yaml`) | `ARTIFACT_SCHEMA_ERROR` | As for `org-charter.yaml`: skipped and listed by the store, not on the wire. | Reported as blocking. |

  *Reading:* "fails the load" (ruling 1) means the file fails to load. A file that does not load is
  missing from the written pack, so the round trip fails too. Refusing the whole pack for one bad
  artifact is not required by the ruling, and today `ARTIFACT_SCHEMA_ERROR` and
  `ORG_CHARTER_SCHEMA_ERROR` have no refusal and no diagnostic on the load path. Whether a skipped
  file should reach the wire, as a refusal or a diagnostic, is for #189 to propose and the operator
  to rule. It would be a contract change.
- **What the reader sees.** The finding carries the file, the JSON pointer, the keyword
  `additionalProperties` and the key, rendered as `<pack>/<file>[ node|edge]: unknown key '<name>'`
  (for `pack.yaml`: `pack.yaml of <pack>: unknown key '<name>'`).
- **Nothing is carried through.** `undeclaredKeys()` and the `pack_unknown_key` warning go.
  *Reading:* `pack_unknown_key` stays in the `DiagnosticCode` enum but is never raised, as ADR-0009
  retires a value. That is a contract change: a description change, a `CHANGELOG` entry and a minor
  version under [ADR-0022](/adr/0022-contract-versions-start-at-0-x/).

### 4. The canonical CRG edge

- **Edges are a set.** An edge's identity is the directed triple `(source, target, relation)`. Its
  value is every other field the schema declares (`when`, `reason`, `impacts` in a shard; `reason`,
  `impacts` in a fragment). Two edge lists are equal when they hold the same set of members, in any
  order, with any repetition of an identical member. *Reading:* the triple is directed, so a reverse
  edge is a different member, never a "duplicate".
- **Nodes are a set too.** *Reading:* ruling 4 names edges; nodes are a set for the same reason,
  since the graph holds no node order. A shard node's identity is its `urn`; a fragment node's is `(kind, id)`.
  Its value is every other declared field (`kind`, `label`, `provenance`, `tags`; `title`,
  `body_path`).
- **The one-relationship-per-pair rule stays a domain invariant**, separate from comparison (ADR-0027
  decision 1, with the `tension` relationship ADR-0028 allows beside one structural relationship).
  *Reading:* every relation is structural except tension (`in_tension_with` in today's files). So
  `suggests`, `reconciles_tension`, `rejects` and the `impacts` relation (which ADR-0028 retires) all
  count toward the one-per-pair rule. A source with two structural edges on one pair, in either direction, breaks the invariant. The reader
  does not collapse it or pick one edge. It is a pack inconsistency, fixed in the pack (ruling 3;
  [spec-kitty/spec-kitty#6667](https://github.com/spec-kitty/spec-kitty/issues/6667)). That keeps
  ADR-0027 decision 2's promise not to drop a second edge silently.
- **Tension.** In today's pack files the tension relationship of ADR-0028 is written
  `in_tension_with`. It is the one symmetric relation, and the schema documents its single canonical
  orientation: the lexicographically smaller URN is the `source`. *Reading:* the triple is compared
  as written, so a tension edge in the other orientation is a pack inconsistency fixed in the pack
  (ruling 4), not an edge the reader turns around.
- **Same identity, different value**: for example, two `requires` edges from A to B with a different
  `when` are a pack inconsistency, not two members.

### 5. Filing a pack inconsistency

A pack inconsistency is a source shape that the canonical form or a domain invariant cannot hold.
Examples: two structural edges on one pair, a set member repeated with different values, a tension
edge in the wrong orientation, or a multi-document file. When the round trip finds a difference:

1. **Decide where it belongs.** It is a pack inconsistency when the pack's data is wrong. It
   belongs to core ([`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty)) when a
   validator there is wrong or missing. It belongs here when this service's reader, writer, a schema
   (canonical here, ADR-0015) or `file-kinds.yaml` is wrong; for example, a YAML file that matches no
   rule.
2. **File it where it belongs.** A pack inconsistency goes to
   [`spec-kitty-open-packs`](https://github.com/spec-kitty/spec-kitty-open-packs/issues), using that
   repository's bug report template. A core gap goes to core's tracker. A service or schema defect
   is an issue here. A pack-inconsistency issue carries:
   - the pack and file paths;
   - the file kind;
   - the rule of this record it breaks;
   - the gate's output;
   - the pinned `openPacksRef`.

   Link it from the epic or kind issue in this repository.
3. **Do not work around it here.** No reader rule, normalization or exemption keeps the inconsistent
   shape (ruling 3).
4. **When the fix merges**, move `openPacksRef` in `gradle.properties` (the single pin, read by
   Gradle and CI) and re-vendor `packs/` if the vendored subset is affected
   (`packs/internal/PROVENANCE.md`).

Until then the file sits on the gate's self-expiring known-failure list (open question 1, answered).

### 6. Kinds without a schema yet

The rules of Decision 2 apply to every file the round trip covers. Unknown-field refusal needs a
schema. For each kind listed under `exclusions` in `contracts/packs/file-kinds.yaml`, refusal starts
when the mission that models the kind (#190 to #195) adds its schema. Every new schema is closed
except for the open maps it declares. *Reading:* the gate covers what the classification maps to a
schema, as epic #186's "done when" says. The generated hashes of `pack-manifest.yaml` are
left to #195 (open question 3, answered: decided there).

### 7. The charter's round-trip promise

The charter companion said "open-packs YAML always round-trips exactly", and `charter.yaml`
PROJECT_002 said "without loss or rewrite" before this change. *Reading:* under ruling 2 this means "without loss of
meaning", as Decision 2 defines it: nothing a reader can observe changes. It does not mean the same
bytes. The operator amended the charter text to say so, in the same change that accepted this
record (open question 2): PROJECT_002 now reads "round-trips without loss of meaning (ADR-0036)".

### Per-kind table

`contracts/packs/file-kinds.yaml` stays the single authority for which files exist and which schema
validates each. This table restates its rules only to attach what this record decides: how each is
compared, its sets and its open maps. When a kind mission adds a schema (#190 to #195), it records
the kind's sets and open maps against Decision 2, in its own record or as an amendment here. A
reader change never does that. "Sets" lists the only lists compared as sets; every other list is a
sequence. "Open maps" are found by the rule in Decision 2; the ones named are examples.

| Files (`match`) | Schema | Compared as | Sets (member identity) | Unknown fields refused |
|---|---|---|---|---|
| `*/pack.yaml` | pack-descriptor | YAML | none | yes, once the schema is closed (Decision 3) |
| `**/*.graph.yaml` | graph-shard | YAML | `edges` (`source`, `target`, `relation`); `nodes` (`urn`) | yes |
| `*/drg/fragment.yaml` | org-fragment | YAML | `edges` (`source`, `target`, `relation`); `nodes` (`kind`, `id`) | yes |
| `*/org-charter.yaml` | org-charter | YAML; open maps such as `interview_defaults`, `activation_context` | none | yes |
| `**/*.directive.yaml` | directive | YAML | none | yes |
| `**/*.tactic.yaml` | tactic | YAML | none | yes |
| `**/*.styleguide.yaml` | styleguide | YAML; `tooling`, `structural_lint_config` open | none | yes |
| `**/*.toolguide.yaml` | toolguide | YAML | none | yes |
| `**/*.paradigm.yaml` | paradigm | YAML | none | yes |
| `**/*.procedure.yaml` | procedure | YAML | none | yes |
| `**/*.agent.yaml` | agent-profile | YAML; the object form of `excluding` is an open map | none | yes |
| `**/*.step-contract.yaml` | mission-step-contract | YAML | none | yes |
| `**/*.asset.yaml` | asset | YAML; the payload it names compares by bytes | none | yes |
| `**/charter.yaml` | charter | YAML; open maps such as `overrides`, `activation_context` | none | yes (a project file; not expected in a pack) |
| `**/mission-steps/**/step.yaml`, `**/actions/*/index.yaml`, `**/missions/*/mission.yaml`, `**/missions/*/mission-runtime.yaml`, `**/missions/*/expected-artifacts.yaml`, `**/governance-profile.yaml` | none (excluded) | YAML | none until a schema names one | when a schema lands (#194) |
| `**/mission_types/*.yaml` | none (excluded) | YAML | none until a schema names one | when a schema lands (#194) |
| `**/presets/*.yaml` | none (excluded) | YAML | none until a schema names one | when a schema lands (#195; the per-item must-have mark, and `required_<plural>` migrated then refused in #206) |
| `**/*.skill.yaml`, `**/*.glossary-pack.yaml` | none (excluded) | YAML | none until a schema names one | when a schema lands (#192) |
| `**/*.config.yaml` | none (excluded) | YAML, all open: free-form tool configuration | none | never: it has no fields, only data |
| `*/pack-manifest.yaml` | none (excluded) | YAML; the treatment of `content_hash` is decided in #195 (open question 3) | none until a schema names one | when a schema lands (#195) |
| `built-in.source.yaml`, `**/templates/plan-field-declaration.yaml` | none (excluded) | YAML | none | when a schema lands |
| `*.md` (`pack.md`, `*.skill.md`, mission-step `prompt.md` and `guidelines.md`, `PROVENANCE.md`, `README.md`) | none | frontmatter, if any, as YAML; body as text (LF, one final newline) | none | frontmatter: when a schema lands |
| Any other file (asset payloads, templates, `.csv`, `.feature`, `.py`, images, fonts, `.gitkeep`) | none | bytes | none | not applicable |

### Worked examples

| # | Source | Written | Verdict | Rule |
|---|---|---|---|---|
| E1 | `id: x` then `title: "T"  # note` | `title: T` then `id: x` | equal | Key order, quoting and comments carry no meaning |
| E2 | `parent_pack: null` | no `parent_pack` | equal | Absent equals a valid `null` |
| E3 | `schema_version: "1.0"` | `schema_version: 1.0` | not equal (and the written file is refused: the schema wants a string) | String versus number; validation first |
| E4 | tactic `steps: [a, b]` | `steps: [b, a]` | not equal | Lists are sequences unless named a set |
| E5 | shard edges `[A→B requires, C→D suggests]` | `[C→D suggests, A→B requires, A→B requires]` | equal | Edges are a set; an identical repeat does not count |
| E6 | shard edges `[A→B requires, B→A suggests]` | (any) | pack inconsistency, filed upstream | Two structural edges on one pair (Decision 4) |
| E7 | edges `[A→B requires when x, A→B requires when y]` | (any) | pack inconsistency, filed upstream | Same identity, different value |
| E8 | `internal/directives/x.directive.yaml` with `surprise: 1` | (not written) | unreadable: `ARTIFACT_SCHEMA_ERROR`, `internal/directives/x.directive.yaml: unknown key 'surprise'`; the round trip fails, the file being missing | Decision 3 |
| E9 | `pack.yaml` with `accompanies_doctrine_pack: x` | (not written) | pack refused: `PACK_UNREADABLE`, `pack_unreadable` on the wire, `pack.yaml of <pack>: unknown key 'accompanies_doctrine_pack'` | Decision 3 (DIV-006 closes) |
| E10 | shard node with `tags: [t]` | node without `tags` | not equal | Every declared field compares; the reader must keep `tags` |
| E11 | `description: \|` block `a\nb\n` | `description: "a\nb\n"` | equal | Strings compare by resolved value |
| E12 | styleguide `tooling: {lint: ruff, types: mypy}` | `tooling: {types: mypy, lint: ruff}` | equal | Open maps compare deeply; key order never counts |
| E13 | an `impacts` edge with `impacts: 1` | `impacts: 1.0` | equal | Numbers compare by value |
| E14 | `structural_lint_config: {rules: {}}` | `structural_lint_config: {}` | not equal | Inside an open map an empty map is data |
| E15 | `tags: [b, a]` on a shard node | `tags: [a, b]` | not equal | `tags` is a sequence: only edges and nodes are sets |
| E16 | shard edge with `when: null` | the same edge without `when` | equal | `when` is a declared field of the edge item |

### Open questions

Asked on #188 and answered by the operator on 2026-10-27:

1. **Expected failures while an upstream fix is pending: a self-expiring known-failure list.** The
   gate keeps a list keyed by open-packs issue and file, and a listed file that starts passing fails
   the gate, as R21 does for vendored packs. It is test bookkeeping, not product logic, so ruling 3
   stands.
2. **Charter wording: amended in the same change that accepts this record.** PROJECT_002 reads
   "round-trips without loss of meaning (ADR-0036)". Dropping comments on write is not a loss of
   meaning, so the writer need not keep them.
3. **Kinds without a schema, and `pack-manifest.yaml`: Decision 6 stands.** Whether the manifest's
   `content_hash` is recomputed on write, or its constituents compare by bytes, is decided in #195,
   where the manifest is modelled.

## Consequences

### Positive

- #187 can build its comparator from Decision 2, Decision 4 and the per-kind table, with no further
  decision. Source-against-written comparison makes reader loss visible (E10).
- One refusal path for all schema violations. No new wire code, and the domain stays free of
  unknown-key handling.
- The per-kind differences are data (the sets and their identities), so a kind-keyed registry
  (#189) can carry them as entry data.
- Pack inconsistencies are fixed once, where every consumer of the packs benefits.

### Negative

- **Forward compatibility.** A newer core or pack that adds a field makes an older service refuse
  the pack, the case DIV-005 existed for. It is accepted: [ADR-0012](/adr/0012-pack-mutability-policy/)
  already accepts it for closed descriptors. The mitigations are `requires_core` and engine gating
  (`PACK_ENGINE_INCOMPATIBLE`), and contract-first schema changes (ADR-0015, ADR-0017).
- **Comments are lost on write.** An authored file the service writes (for example a commented
  `pack.yaml`) loses its comments and its layout. Authors who keep comments keep editing the source.
- **The epic finds upstream work.** The 54 built-in pairs that carry two structural edges, and the known
  reverse pair in the internal fragment, must be fixed in open-packs before the CRG gate is green.

### Follow-up work (implemented elsewhere; this record changes no code)

- Close `pack-descriptor.schema.json`; remove `undeclaredKeys()`, the `PACK_UNKNOWN_KEY` warning and
  `PackRoundTripTest`'s pin of the drop. Retire `pack_unknown_key` in the contract, with its scenario
  ("A pack descriptor with an undeclared key loads") and a `CHANGELOG` entry. Close DIV-005 and
  DIV-006 in the divergence register when the code lands. The operator places this in #189 or in its
  own issue.
- Readers keep every declared field: CRG node `provenance` and `tags`, fragment `body_path`, and the
  shard header fields the writer hard-codes today (#187 finds them; #189 or the kind missions fix
  them).
- The gate compares source against written in canonical form over every covered kind (#187). The
  build comment that promises "every YAML file must survive unchanged", and the stale reference to a
  byte-level test, are corrected there.
- When the whole-pack seam loads a pack's graph, `drg_schema_error` is raised again: its
  `raised: false` and description in `CharterRefusalCode.yaml` change, which is a contract change
  (#189).
- How the gate reports a pack inconsistency or a difference (its output format, and whether the
  comparator or the domain invariant detects two structural edges on a pair) is #187's to design.
- The gate compares CRG edges and nodes as sets (#187). Whether the domain's graph merge also
  becomes set-based is for #189 to decide; this record only defines comparison.

### Risks

- The canonical form could grow per-kind exceptions until it is special logic in disguise. A new set
  or normalization is decided in a record and checked against Decision 2, never added as a reader
  rule.
- A reading could be overruled on #188 after #187 builds on it. The readings are marked, so the
  change is local.

## Alternatives considered

### Byte equality

Rejected by ruling 2. It would make the writer reproduce quoting, comments and layout, which no
reader observes, and it would make every presentation change in open-packs a gate failure.

### Preserve unknown fields with their values

The open alternative in #188, rejected by ruling 1. It would need a value-carrying bag on every
record and a writer that places unknown keys back, which is special logic for data the schema says
does not exist.

### Record against record (today's test)

Rejected. It is blind to anything the reader drops, which is the main way a round trip loses
meaning.

### Unordered-pair edge identity for comparison

Rejected. It would make a reverse edge a "duplicate" and drop it silently, which ADR-0027 decision 2
refuses. The pair rule is kept as a domain invariant, and the comparison key is the directed triple.

### Treat every list whose order looks meaningless as a set

Rejected. It needs a judgment per list (`required_*`, tags, references), and each judgment is a
special rule. The writer keeps order, so sequences cost nothing.

### A dedicated `unknown_field` refusal code

Rejected. The schema-violation path already renders "unknown key", per file kind. A second code
would be a parallel path.

## References

- Operator rulings: [#188](https://github.com/spec-kitty/spec-kitty-charter/issues/188) (2026-10-27);
  presets: [#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195).
- `contracts/packs/file-kinds.yaml`; `contracts/packs/schemas/`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java` (`codeFor`,
  the `additionalProperties` rendering); `packs/domain/PackContents.java` (`SchemaViolation`).
- `contracts/charter/schemas/DiagnosticCode.yaml` (`pack_unknown_key`).
- [Divergence register](/conformance/divergence-register/): DIV-005, DIV-006.
- Mission research: [grounding squad](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/round-trip-fidelity-adr-01M5Y2A1/research/grounding-squad.md).
