# Pre-spec grounding squad (2026-10-27)

Procedure `adversarial-squad-deployment`, point-cut **pre-spec**. Question: *what must a reader and
writer of `mission_step_contract` hold, and what does binding the kind touch, so that every
`*.step-contract.yaml` of the pinned packs round-trips by meaning (ADR-0036) without colliding with
the sibling Wave 1 missions?* Three profile-loaded, read-only lenses ran in parallel; each loaded its
profile with `spec-kitty agent profile show <id>`: `researcher-robbie` (evidence inventory),
`architect-alphonso` (decision integrity), `planner-priti` (scope, deliverables, sequencing).
Grounded against `main` at `90de8f9` and `spec-kitty-open-packs` at `openPacksRef` `01edb4e`.

## What exists

| Area | Evidence | State |
|---|---|---|
| Schema | `contracts/packs/schemas/mission-step-contract.schema.json` | Closed at every level. Top level: `id`, `schema_version`, `action`, `mission`, `steps` (required), `gates`. Step: `id`, `description` (required), `command`, `inputs`, `delegates_to`, `guidance`. `delegates_to`: `kind` (artifact-kind enum), `candidates` (non-empty). Gate binding: `on_transition`, `handler`, `schema_version` (required), `handler_kind` (default `mission_step_contract`), `fail_open` (boolean, default `true`), `provenance`. Input: `flag`, `source`, `optional` (boolean, default `false`). |
| File classification | `contracts/packs/file-kinds.yaml:94-95` | `**/*.step-contract.yaml` maps to schema `mission-step-contract`. |
| Registry | `KindBindings.java:17-22`, `YamlFormats.java:11-16` | Five kinds bound; a kind joins by one line in each. |
| Schema-name assumptions | `YamlSerializationStrategy.java:35`, `PackFileSet.java:141`, `KindBindingsTest.java:50,60` | Each assumes the schema name equals the kind token. For this kind the token is `mission_step_contract`, the schema `mission-step-contract`, the file token `step-contract`. Binding the kind as-is would load **no** file, silently. ADR-0037:73-74 leaves the mapping to this mission. |
| Whole-pack seam | `WholePackStore.java:113` | Generic over `KindBindings.all()`; keeps each artifact's source path in the layout. Needs no edit. |
| Gate | `DomainRoundTrip.java:48,125,224`, `expected-red.yaml:10-12` | Kinds, writes and written-file matching hard-coded per kind; matching uses `.<kind>.yaml`, which is not this kind's file token. The kind is expected red as a whole. |
| Validate mode | `FileSystemPackInspector.java:205`, `PackPolicy` step rules | Owns the duplicate-step-id rule (core parity, planted fixture `duplicate-step-id`). |

## Probes on the pinned data

- **28 files, 147 steps**: 21 in `built-in/missions/built_in_step_contracts/`, 7 in
  `qa/mission_step_contracts/`; no other pack has any. All 28 pass the schema with 0 violations.
- Top level: `schema_version`, `id`, `action`, `mission`, `steps` in all 28; `gates` in 1
  (`built-in/.../review.step-contract.yaml`, one binding with all six fields written, `fail_open: true`).
- Steps: `id` 147, `description` 147, `delegates_to` 101, `command` 46, `guidance` 10, `inputs` **0**.
  `delegates_to.kind`: directive 59, tactic 36, procedure 5, paradigm 1.
- No explicit nulls, no empty lists, no duplicate step ids, no duplicate contract ids; every id
  equals its file stem. Multi-line values are folded (`>`, clip) and keep one trailing newline.
- **No step carries a role, `agent_profile` or profile field** (ADR-0032): there is nothing
  role-shaped to round-trip. The `agent_profile:` fields of ADR-0032's current state live in
  `mission-steps/**/step.yaml`, which file-kinds.yaml excludes (#194).
- **CRG identity differs from the file id.** `built-in/mission_step_contract.graph.yaml` keys its
  21 nodes `mission_step_contract:<mission>/<action>` (`software-dev/implement`), with no edges and
  no reference from another shard; the file ids are `implement`, `plan-plan`, … Core agrees with
  the shard in its extractor and contradicts it in a docstring.
- The `qa` pack has no `pack.yaml`, so no gate loads its 7 contracts (known pack fix, raised on #189).

## Findings and dispositions

| # | Lens | Finding | Disposition |
|---|---|---|---|
| G1 | all three | Binding needs a schema-name mapping; three code sites assume schema name = token, and a plain binding would load nothing, silently. | **accepted**: the binding names its schema as it names its file token (`KindBinding.schemaName()`, default the token); storage resolves schema → binding through `KindBindings`; a test ties each binding's schema and file token to `file-kinds.yaml`. Option A (rename the schema) rejected: `$id`, gate kind name and kebab convention change. Option C (`_`→`-` convention) rejected: three spellings agreeing by coincidence, unchecked. Recorded as an amendment to ADR-0037 decision 4 (still Proposed) — no new ADR. |
| G2 | alphonso, priti | #190 (`agent-profile`/`agent`) needs the same seam; both missions would edit the same lines. | **accepted**: the seam is its own first work package and commit, announced on #191 so #190 can reuse it; whoever merges second rebases. |
| G3 | robbie, priti | `gates` is #155's scope but the pinned pack carries one binding. | **accepted**: gate bindings are carried as inert data (every field, `fail_open` a boolean) so `review` round-trips; no semantics. |
| G4 | robbie | Schema defaults are not implemented by the gate's comparator. | **accepted**: the DSO keeps whether an optional field was written; nothing is filled from a default, so no comparator change is needed. |
| G5 | robbie | `inputs` is in the schema but in no pinned file. | **accepted**: modelled and tested with planted files; no corpus evidence claimed. |
| G6 | robbie, alphonso | The CRG URN (`<mission>/<action>`) differs from the artifact's `kind:id`. | **deferred to the operator** (#191): the entity keeps `localId = id` (file name, duplicate-id invariant, `idOf` all use it). The round trip is unaffected (CRG documents stay outside the aggregate, ADR-0037 decision 5). |
| G7 | alphonso, priti | `delegates_to` could be minted as CRG edges. | **accepted (none minted)**: the pinned shard has no edges; minting would create a second copy of edges the operator has not ruled on. Delegations stay values on the entity; edges are #148's. |
| G8 | robbie vs alphonso | Should the entity refuse duplicate step ids (core does)? | **changed**: adjudicated from the source — validate mode already owns the rule (`PackPolicy`), and an entity invariant would give it two owners and turn the planted fixture into a load violation. Not an entity invariant in this mission. |
| G9 | alphonso, priti | Binding changes the built-in and qa fingerprints and ETags once. | **accepted**: stated as a consequence (ADR-0037 already lists it). |
| G10 | priti | `contracts/charter/headers/CharterSourceFingerprint.yaml` and `ServiceInfo.yaml` enumerate the digested kinds; binding makes that prose incomplete. | **deferred to the operator** (#191): the brief forbids an HTTP contract change unless unavoidable. Raised on the issue; not edited here. |
| G11 | priti | Priti counted `schema_version` optional in 22 files and `gates` in two. | **rejected**: re-probed — `schema_version` is in all 28; `plan-review` mentions `gates:` only in a comment. |
| G12 | robbie | 15 built-in files lack a final newline. | **rejected** as a finding: meaning equality ignores presentation (ADR-0036). |

## Post-plan squad (2026-10-27)

Two profile-loaded lenses, `architect-alphonso` and `reviewer-renata`, reviewed the plan against the code.

| # | Lens | Finding | Disposition |
|---|---|---|---|
| P1 | alphonso | Rewriting `DomainRoundTrip` and widening `saveArtifact` are non-append shared edits that every Wave 1 sibling would collide on. | **changed**: moved into WP01's one enabling commit. C-003 now names it. |
| P2 | renata | `WholePackRoundTripTest.REQUIRED_KINDS` is checked with equality against seven kinds. | **accepted**: it becomes a floor (`containsAll`) plus equality with the registry's schemas (WP01). |
| P3 | renata | The written-file key and suffix rule is easy to mix up, and `endsWith` is ambiguous. | **accepted**: match the exact token segment and key by `schemaName()`, spelled out in WP01. |
| P4 | renata | `GeneratedPackTree` and `PackFileSetExactnessTest` need more than "decoy promotion". | **accepted**: WP04 T015 lists the touch points. |
| P5 | renata | The corpus test needs a positive control and coverage counts, and must fail when the checkout is missing. | **accepted**: WP04 T014. |
| P6 | renata | `wholePackRoundTrip` is not part of `verifyAll`. | **accepted**: run explicitly; its report is PR evidence. |
| P7 | alphonso | A relationship registration from `mission_step_contract:<id>` now resolves (409 becomes 201). | **accepted as a consequence**: a direct effect of binding any kind (ADR-0037). Recorded in the changelog and raised on #191. |
| P8 | alphonso | The contract's fingerprint prose becomes false after merge. | **deferred to the operator** (A3 on #191), flagged in the PR as a merge question. |
| P9 | alphonso | Validate mode keeps its own `-`→`_` mapping. | **deferred**: noted in the ADR amendment; aligning it is a later change. |
| P10 | alphonso | ADR-0037's Consequences/Positive needs an exception for the enabling commit. | **accepted**: covered by the amendment (WP05). |

## Pre-PR squad (2026-10-27)

Two profile-loaded lenses, `architect-alphonso` and `reviewer-renata`, reviewed the rebuilt branch.
Both approved: alphonso without conditions, renata with changes.

| # | Lens | Finding | Disposition |
|---|---|---|---|
| R1 | alphonso | Merging with #190 gives 12 content conflicts. `DomainRoundTrip` must take #190's `idKey()` threading into this branch's registry-driven loop, or agent profiles go unpaired. | **accepted**: the resolution notes are in the PR body for the second merger. |
| R2 | alphonso | Two ADR-0037 amendments would both settle decision 4. | **accepted**: noted in the PR body; the second merger folds them into one. |
| R3 | renata | The changelog claims a `mission_step_contract` URN now resolves for a relationship registration, but nothing tests it. | **changed**: `RelationshipRegistrationServiceTest.aRelationshipFromAMissionStepContractOfThePackResolves`. |
| R4 | renata | The mission record still names `schema()` and cites commit hashes from before the rebuild. | **changed**: renamed to `schemaName()`; acceptance and issue-matrix evidence remapped. The append-only event log and the review records keep their historical text. |
| R5 | renata | The registry-derived modelled-kind assertion is tautological. | **changed**: `mission-step-contract` is pinned in `DomainRoundTripTest`. |
| R6 | renata | The domain refuses some schema-valid values: blank step id, blank candidate, blank action or mission. | **deferred**: no pinned file trips them, and they fail closed as a violation. Listed in the PR as a known stricter-than-schema rule for the operator. |
| R7 | alphonso | Validate mode keeps its own `-`→`_` mapping. | **deferred**: a follow-up, named in the hand-off. |
| R8 | renata, alphonso | The DSO javadoc is imprecise about null versus absent; the `FilesystemPackStore` javadoc edit was undeclared. | **changed**: the DSO javadoc is precise. The `FilesystemPackStore` wording fix is declared in the PR. |
