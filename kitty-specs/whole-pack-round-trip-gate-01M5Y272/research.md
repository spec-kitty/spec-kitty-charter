# Research: Whole-pack round-trip gate

Pre-spec squad (2026-10-27): researcher-robbie, architect-alphonso, planner-priti, each profile-loaded through
`spec-kitty agent profile show`. Summary and operator questions posted on #187 (comment 6435722435).

## R-1 Census of the built-in pack at `01edb4e`

505 regular files. Modelled today: directive 38, tactic 121, styleguide 25, toolguide 18, paradigm 14, `pack.yaml`
1, graph shards 14 (`<kind>.graph.yaml` at the pack root). Not modelled: agent profiles (26 + README), procedures 23,
glossary pack 1, assets 15 (6 `.asset.yaml`), presets 2, templates 2, `missions/` 181 (21 step contracts, mission
types, mission steps, actions, templates), `pack.md`, `pack-manifest.yaml`. Non-YAML inside modelled directories:
18 toolguide companion `.md` files (referenced by `guide_path`), `toolguides/system_tools/__init__.py`,
`styleguides/writing/README.md`. `skills/` holds only `.gitkeep`.

## R-2 Existing round-trip prior art

- **Decision**: build a new gate; leave `PackDoctrineCorpusRoundTripTest` in place.
- **Rationale**: the existing test compares a read DSO with a re-read DSO for three kinds only. A field the reader
  drops passes on both sides, so it cannot serve as the oracle. The whole-corpus `OpenPacksRoundTripTest` (bytes)
  was deleted in 2bb3f76. The new gate compares source file to written file.
- **Alternatives**: extend the corpus test (rejected: wrong oracle, no report, tagged into `check`).

## R-3 Read → domain → write path

- **Decision**: `DomainRepositoryService` over the source location loads the `Pack` aggregate (descriptor + five
  kinds, with read violations); a second instance over the target location saves each artefact and the descriptor;
  reloading the target is the read-back. DRG: `GraphDocumentReader` → `PackPolicy.graph` → `GraphShardWriter`.
- **Rationale**: exercises the real domain mapping without touching `Pack.java` or `FilesystemPackStore` (C-001);
  `FilesystemPackStore` is used only through the public service.
- **Alternatives**: `SerializationStrategy` read/write per file (DSO only, skips the domain; rejected); a
  `FilesystemPackStore` call (package-private, owned by #189; rejected).
- **Consequence**: written artefacts land as `<id>.<kind>.yaml` at the target root, not at the source path. The
  adapter pairs source and written files by `(kind, id)`.

## R-4 Comparison policy (provisional until #188)

- Maps: key order does not count; key sets must match; values recurse.
- Lists: order counts (steps, references), per architectural-patterns-applied.md.
- Scalars: equal Java value and type after loading with the readers' null forms. Quoting style, comments,
  whitespace and key order do not count.
- Graph shards: `edges` are a set of canonical edge maps over every field (source, target, relation, impacts, when,
  reason); duplicates do not count (operator confirmation on #188); duplicates found in the source are reported as
  information. `nodes` compare as a map keyed by `urn`.
- Explicit `[]` / `null` versus absent: **different** (strict). Operator question 2 on #187.
- **Open review item**: these rules are my call, made without operator review; aligned with #188's ADR when it lands
  (C-005).

## R-5 Gate kinds for files without a schema

- **Decision**: a file mapped by `contracts/packs/file-kinds.yaml` takes its schema name as gate kind
  (`directive`, `graph-shard`, `pack-descriptor`, `agent-profile`, …); any other file takes
  `other:<top-level directory>` or `other:<root file name>` (`other:missions`, `other:pack.md`).
- **Rationale**: no new classification table to maintain; the contract's mapping wins; unmapped files are grouped by
  where they live, which is how the Wave 1 issues are scoped.
- **Open review item**: naming is my call.

## R-6 Expected red and the ratchet

- **Decision**: `expected-red.yaml` with `kinds:` (whole gate kind: no adapter yet) and `files:` (single files in a
  modelled kind) entries, each with `reason` and `issue`. Two-way ratchet: unexpected red fails; a baselined item
  that is green fails ("remove it"); a baselined path that no longer exists fails.
- **Rationale**: "no fake green" — red is declared, never skipped; a fix cannot sit unrecorded.
- **Alternatives**: `@Disabled` / `assumeTrue` (rejected: silent); failing tests in a non-required task (rejected:
  always red, no signal).

## R-7 Exclusions

- **Decision**: own `exclusions.yaml` in test resources, `match` (glob, pack-relative) + `reason`; append-only by
  convention, stale entries fail, an exclusion over a modelled gate kind fails.
- **Rationale**: `file-kinds.yaml` exclusions belong to the contract and cover schema coverage, not this gate.

## R-8 Build wiring

- **Decision**: new `Test` task `:packs:wholePackRoundTrip`, tag `whole-pack`, built like `openPacksRoundTrip` (pin
  validated at execution, checkout as input); excluded from `test` and `openPacksRoundTrip`; not a dependency of
  `check`. A separate non-required CI job runs it.
- **Rationale**: C-002; pitest is the precedent for a verification task outside `check`.

## Findings disposition (pre-spec squad)

| # | Finding (lens) | Disposition | Evidence |
|---|---|---|---|
| 1 | DSO-vs-DSO corpus test is fakeable (priti, alphonso, robbie) | accepted | spec FR-003; R-2 |
| 2 | Writer drops explicit `[]` in 16 files (priti) | deferred_with_rationale | strict policy, baseline entries; #187 Q2 |
| 3 | `pack.yaml` schema allows unknown keys (all) | deferred_with_rationale | #187 Q3; built-in has none |
| 4 | Built-in fixes need core → open-packs → pin (priti) | changed | baseline entry with upstream issue; #187 Q5 |
| 5 | `open-packs` tag is in `check` (all) | accepted | R-8, C-002 |
| 6 | `DoctrineGraph.equals` is order-sensitive; `Relationship.equals` is pair-only (robbie, alphonso) | accepted | R-4: own canonical edge comparison |
| 7 | `Pack.equals` is id-only (alphonso) | accepted | comparison never uses the aggregate |
| 8 | `OpenPacksCorpus.YAML_FILE_COUNT` dead; stale javadoc / build comment (alphonso, robbie) | changed | build comment corrected in this mission; dead constant left to its owner (noted in PR) |
| 9 | charter.md overclaims the round trip (robbie) | deferred_with_rationale | governance text; #187 Q4 |
| 10 | Toolguide companion `.md` inside a modelled directory (robbie) | accepted | R-5: `other:toolguides`, baseline entry |
| 11 | Scope: internal, `packs/`, required check (priti) | deferred_with_rationale | C-004; #187 Q1 |
