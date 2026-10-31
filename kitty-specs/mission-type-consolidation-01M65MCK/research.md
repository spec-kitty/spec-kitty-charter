# Research: mission-type consolidation (#194 PR-B)

Pre-spec research squad, 2026-10-30, read-only, against `origin/main` at `feeff0a` and the pinned open packs
(`openPacksRef` `5df92a5d`). Three profile-loaded lenses; their full reports are under `research/squad/`:

| Lens | Profile | Report |
|---|---|---|
| Responsibility map, ADRs, test seams, design options | architect-alphonso | `research/squad/architect.md` |
| Inventory, field census, mapping, divergence table | researcher-robbie | `research/squad/divergence.md` (census scripts in `research/scripts/`) |
| Gates and ledgers, churn, sibling runs, docs | analyst-annie | `research/squad/gates.md` |

Baseline: `./gradlew verifyAll` on `feeff0a` passes on Java 25 (BUILD SUCCESSFUL, 92 tasks).

## Binding inputs

- Operator decisions on #194 of 2026-10-28 and 2026-10-29: the sanitized DSO shape wins over a round trip as-is
  (ADR-0036 decision 5); every DSO gets a designed schema under `contracts/packs/schemas/`; the sanitized mission model
  is `mission_type` plus `mission_step`, retiring `mission-runtime.yaml`, `actions/*/index.yaml`,
  `governance-profile.yaml` and `expected-artifacts.yaml` in that model; #194 may remove its own exclusion entries.
- Dispatch brief: migrate the pinned packs with the service's own writer (a throwaway harness) rather than pin known
  failures; the migration and the open-packs issue drafts go to the orchestrator, who lands them and returns the sha to
  re-pin. No tolerance logic. No core changes. The ADR is written first.
- Charter: designed without regard to core (epic #186); the round trip is equality of meaning (ADR-0036).

## Findings

### F1. The mission tier as pinned

Only three packs hold mission-tier files: built-in (4 types), cave-kitty (1) and qa (1); the charter's vendored
`packs/qa` is byte-identical to the pin for every one. Per type, the tier is spread over up to eight files:
`mission_types/<t>.yaml`, `missions/<t>/mission.yaml`, `mission-runtime.yaml`, `governance-profile.yaml`,
`expected-artifacts.yaml`, `actions/<a>/index.yaml`, `mission-steps/<t>/<s>/step.yaml` with its `prompt.md` and
`guidelines.md`, plus cave and qa `command-templates/*.md` and the graph shards. Counts: divergence report §1.

### F2. Every pinned file breaks the sanitized rules

- **One concept, five homes, conflicting values.** The label differs ("Software Development" vs "Software Dev Kitty"),
  the version differs (`mission.yaml` 1.0.0 vs runtime 2.2.0; plan the other way round).
- **Two sequences.** `step.yaml` `sequence_index`/`in_action_sequence` and the runtime `steps` order differ for
  software-dev and research; 15 of 37 `depends_on` lists contradict the (strictly linear) runtime chains.
- **Ids do not line up.** `tasks_outline` vs `tasks-outline`, a `done` step that exists nowhere, runtime `discovery`
  joined to step dir `research` only through a prompt file name, an action index (`software-dev/retrospect`) with no step.
- **Always empty.** `guidance: null` and `delegates_to: []` in 37 of 37 steps; three governance-profile lists empty in 6
  of 6.
- **Profiles where ADR-0032 says roles**, and 62 directive references by file stem instead of id.
- **Missing prompts.** The 7 qa steps name a `prompt.md` that does not exist (their prompts are `command-templates/`);
  cave-dev has no `step.yaml` at all; research `accept` has no prompt; two retrospect prompts are empty.

Per-file divergence table: divergence report §5. Field-by-field mapping into the two shapes: §4.

### F3. Registry and HTTP

`KindBinding`, `CharterArtifact.kind()`, `Pack` and `PackLayout` key on `ArtifactKind` (ADR-0037), so both
`mission_type` (today a node kind only) and `mission_step` (new) become artifact kinds, as #148 targets. That diverges
from core's `MissionTypeNotAnArtifactKind` and gets a divergence entry. No HTTP operation exposes mission types or
actions: PR-B needs no contract change. Visible on the wire: `/relationships` accepts mission URNs, fingerprints and
ETags change once for packs with mission content, and `NodeKind` gains `mission_step` (a CHANGELOG line).

### F4. Gates

- `:packs:openPacksRoundTrip` runs in `check`/`verifyAll`: every open pack and vendored copy must load with zero
  refusals except built-in refusals pinned in `known-failures.yaml`. Removing the exclusions without migrating the packs
  would red `check` through cave-kitty and qa.
- `:packs:wholePackRoundTrip` (built-in, not in `check`): unlisted reds, listed greens and stale entries all fail; a
  modelled kind cannot be expected red as a whole, so each file is green or listed on its own. `other:missions` is
  #194's entry.
- `ProcedureAndGuideCorpusTest#theMigrationHarnessIsGone` fails while any class is in
  `ai.speckitty.charter.packs.migration`: the harness is deleted in the re-pin commit, as PR-A did.
- `VocabularyEqualityTest` (about eight places per node kind), `PackFileKindTest` (one sample per rule; exclusion
  samples match the rules exactly), Checkstyle, Error Prone, NullAway, Spotless, doclint: zero suppressions.
- Hazard: a retired file whose exclusion is removed and that no rule matches is skipped silently, not refused. A guard
  test is needed so a stale retired file cannot hide.

### F5. Naming

The fragment plural `mission_steps` is refused under ruling R14 (DIV-011, `docs/_meta/terminology.yaml`), because core
uses it for step contracts. Giving it to the new kind flips its meaning; R14, DIV-011 and the terminology rule change
with it. The step contract kind stays as is; the glossary calls it "the detailed version of a mission step".

### F6. Siblings and churn

#260 (pack.md) and this PR both edit `file-kinds.yaml`, `expected-red.yaml`, `REQUIRED_KINDS`, the census sample in
`WholePackExpectationTest`, the changelog index and the ADR index. #260's footprint (relayed by the orchestrator): one
rule after the toolguide-guide rule, deletes only the `other:pack.md` block, appends `pack-document` last to
`REQUIRED_KINDS`, adds a kind-routed Markdown store path. This PR keeps its edits in one block per shared file, touches
only `other:missions`, and puts mission checks in new test classes. 90-day churn: 211 commits on the surface;
`expected-red.yaml` 9 commits in 3 days, `file-kinds.yaml` 8.

## Decision points (for the operator)

See the decision record under `decisions/` once ruled. The open questions are listed in the hand-off to the operator;
the squad's full list (DP-1 to DP-18) is divergence report §7 and architect report §5.

## Post-plan squad dispositions (2026-10-30)

Three profile-loaded lenses reviewed ADR-0044, the spec, the plan and the data model against the code:
`research/post-plan-squad/{architect,reviewer,naming}.md`. Every finding is folded below; Q1 and Q2 went to the
operator with recommendations (ADR-0044 Status).

| Finding | Disposition |
|---|---|
| R-B1 prompt frontmatter `description` is not a repeat (24 of 24 differ) | Q1 to the operator; rec: the frontmatter text becomes the step description, the runtime text is a listed S6 drop with an open-packs issue. The verifier checks equality for every drop labelled a repeat. |
| R-B2 red window misdescribed; `retired:` silently ignored | Exclusions stay until the re-pin commit; the retired mechanism is built and tested on fixture tables; exclusion removal, `retired` rules and re-pin land together. `PackFileKind.parse` refuses unknown top-level keys. The harness lives in its own `migration` source set. |
| A-1 / R-M2 `newFile` refuses nested paths | WP03 lets a binding's file name carry `missions/<id>/`, creating the directories, after the id grammar has refused `/` and `..`; create and traversal tests. |
| A-2 retired has no path through the code | A `Retired` classification in the sealed hierarchy, one listing in `PackFileSet`; store violation, validate-mode finding (both YAML and Markdown), `claimedByRule` refusal, census bucket `retired:`. |
| A-3 retired files not fingerprinted | Digested in the tail after the manifest, in path order; `PackFileSetTest` case. |
| A-4 `prompt_path` three ways; domain knows paths | S8-derived in ADR-0044 §3; the domain holds documents only; the binding refuses a non-derived path. |
| A-5 unowned companion has no mechanism | The gate reports an unowned `steps/*.md` red (census as an unmodelled companion); the spec's scenario is restated to that, matching toolguide guides. |
| A-6, A-7, R-M4 glob too narrow; placement | Rule `**/*.mission-type.yaml`; placement folded into the stem check (`FileStems`): the path must be `missions/<id>/<id>.mission-type.yaml`. Fixtures per wrong location. |
| A-8 fragments' `body_path` point at retired files | Migration rewrites them; issue draft. |
| A-9, N-8 parallel `ReferenceType`; Markdown rules copied | References use kernel `ArtifactKind`, restricted by an invariant; `StepDocument` holds title, preamble and sections only, and the Markdown rules stay in the binding. |
| A-10 NFR-001 untrue | Reworded: no schema or operation change; the `/relationships` 409-to-accepted change is recorded with a scenario and a CHANGELOG line. |
| A-11 facts | `MISSION_TYPE` facts written to the enum's real shape; fact tests updated. |
| A-12, N-4, N-5 template base, terminology, second key vocabulary | Q2 to the operator; rec: `expected_artifacts[].template_path`, relative to the mission-type file, existence checked at load. |
| A-13, R-m5, N-14 `on_demand_steps` set vs list | A list, authored order kept, ids unique; not a run order. |
| A-14, R-m3 `other:missions`, `REQUIRED_KINDS` | The narrowed reason names templates, READMEs, `plan-field-declaration.yaml`, `__init__.py`; `mission-type` joins `REQUIRED_KINDS`. |
| A-15, R-m4 ArchUnit and hygiene | `DOMAIN_RINGS`, `SLICES`, purity rule, bad fixture, `package-info` with `@NullMarked`. |
| R-M1 WP ordering | `files` rules and companion schemas move into the binding WP; every affected test named in its WP. |
| R-M3 `MarkdownCorpusTest` | Glob rewritten to the new layout and its count pinned at the re-pin. |
| R-M5 positive controls | Committed: step title changed, steps swapped, on-demand step dropped, prompt section edited, reference reordered; a verifier self-test with planted mapper defects. |
| R-M6 verifier not independent | The verifier reads the output as raw YAML, checks it against its own expectation table, pins counts, checks reference ids resolve, and defines idempotence as load-and-write byte-identical; its report is committed under `research/migration/`. |
| R-M7 mapper ambiguity, counts | Precedence: the `discovery`/`research` merge first; retrospect steps on demand with no prompt. Pinned counts: 40 sequenced steps (7/4/6/8/8/7), 6 on demand, 43 prompts, 17 guidelines, 153 retired files, verified by the harness. |
| R-M8 no static guard | A source scan forbids retired field names in main sources; `GeneratedPackTree` gains a mission-type shape and a retired-file shape. |
| R-M9 manifest enum | `mission_type` is a manifest constituent kind; the enum is widened with a vocabulary-equality assertion. |
| R-m1 retired families | The spec enumerates every retired glob, `.md` families included. |
| R-m2 stale non-model files | The harness deletes only retired-glob files and asserts the rest unchanged; README and PROVENANCE updates in the drafts and the re-pin. |
| N-1, N-2, N-12 expected-artifact identity | `artifact_key` kept verbatim, an opaque dotted key, unique within one list; not an identity. |
| N-3 unmapped fields | `mission.yaml` `artifacts`, qa's state-machine block, `custom_validators` dropped with reasons; plan's runtime `mission.description` mapped. |
| N-6 "guidelines" clash | Kept (the pack authors' word); ADR-0044 §5 and the glossary disambiguate. |
| N-7 step contract vs mission step | ADR-0044 §2 says what each owns; the glossary entry updated. |
| N-9 recipe ingredients | ADR-0044 §3 says the type-level references are the recipe ingredients, their activation carried by `scope` edges until #189. |
| N-10 qa profile list | Dropped (ADR-0032); open-packs issue asks qa for a preset. |
| N-11 `requires_substantive_spec` | Absent means not required; the pinned values match core's built-in defaults (`mission_type_profiles.py:388`). |
| N-13 stale enums, `domain.md` | Manifest enum widened; `domain.md` and glossary entries for mission step, on-demand step and action updated. |
| N-15 grammar details | Letter-first id grammar; `path_pattern` and `path_conventions` described as workspace paths; `content` described; real example values. |
