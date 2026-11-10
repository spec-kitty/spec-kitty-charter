# Research brief: #424, #428, #429, #430 (Phase A synthesis)

Synthesizer: synthesizer-sam (profile `spec-kitty-open-packs/packs/built-in/agent_profiles/synthesizer-sam.agent.yaml`),
working under the procedure `semi-auto-delivery` (Mission vs Op by blast radius; pause-and-escalate classes a-f) and
`.kittify/charter/charter.md`. Read-only. Repo `spec-kitty-charter` `main` @ `391ed51b` (holds #425 and #427).
open-packs pinned at `openPacksRef=22c14b2d` (`gradle.properties:10`).

Audience: the operator (decides section 6) and the specify/plan agent of the next phase (sections 2, 3, 7).

Sources, cited by short tag:

| Tag | File (scratchpad) | Agent |
|---|---|---|
| [JEN] | `squad/code-grounding.md` | java-jenny (implementer lens) |
| [ALP] | `squad/invariants-contract.md` | architect-alphonso |
| [REN] | `squad/similar-surfaces.md` | reviewer-renata |
| [PRI] | `squad/tracked-issues.md` | planner-priti |
| [OP175] | `squad/openpacks-175.md` | git read of open-packs#175 head `e8a24d6` |
| [SYN] | this brief's own verification against the code | synthesizer-sam |

Anything marked *(inferred)* was not proven by a source or a code read. Nothing was built or run in Phase A except
the setup steps below.

## Operator rulings (2026-11-09)

Relayed by the orchestrator session at 09:04 UTC as "accept recommendations", and checked against GitHub (the
operator's account filed #431–#435 and the #429 hand-off at 09:03–09:04 UTC).

- **D1–D22 accepted as recommended in section 6.**
  - D1: #424+#428 is this Mission; #430 is a separate Op, landed first; #429 rides PR C.
  - D2: PR C keeps WP27–WP29; this Mission's compose-check and prompt-read work packages start after PR C merges.
  - D3: #429 goes into PR C.
  - D4: (a) yes, (b) yes, (c) yes if small, (d) no, (e) no, (f) yes.
  - D5: a new operation `GET /activations/{id}/mission-types/{type}/actions/{action}/prompt` (Added).
  - D6: record the guard/emit shape and file #434; #428 closes on this ruling.
  - D12: refuse an empty enhancement.
  - D19/D20: the test-expectation edits and the whole-pack empty-kind entry are approved as one set.
  - D22: recorded here; the setup-script fix happens outside this run.
- **Not decided, because section 6 gave no recommendation:**
  - D23: the parent issue. The four issues stay unparented.
  - D13: does "whatever the activation" refuse a compose for an inactive enhancement?
  - D14 Q2: does a step inherit its mission type's mutability level?
  - The run lead flagged D13 and D14 to the operator on 2026-11-09. Both block only WP-D, which starts after PR C
    merges.
- **`org-charter.yaml` (A.1):** a legacy org-level preset-enforcement mechanism. It is out of scope, tracked as #431.
  If this Mission's work touches org-level requirements or presets, cite #431 and escalate.
- **Drafts filed:**
  - A.1 → #431, A.2 → #432, A.3 → #433, A.4 → #434 (the D6 follow-up), A.6 → #435.
  - A.5 is posted on #429 as the PR C hand-off.
  - A.7 was filed by the orchestrator on 2026-11-09 as spec-kitty/spec-kitty-open-packs#177 (text updated to the 2026-11-09 rulings).

## 0. Run context and setup facts

- Spec Kitty CLI install from source was refused by the permission classifier. Fallback
  `uv tool install --prerelease allow spec-kitty-cli` installed **4.0.0rc7**. `AGENTS.md` and the charter name
  **rc8**: drift (D22).
- `spec-kitty charter context --include procedure:semi-auto-delivery` answers "No procedure found". The procedure was
  read from `spec-kitty-open-packs/packs/internal/procedures/semi-auto-delivery.procedure.yaml` instead (D22).
- Gradle mirror init installed; `./gradlew syncPacks` BUILD SUCCESSFUL on Java 25.
- Charter "Orchestrating cloud sessions": "Session tooling (the Spec Kitty CLI, CodeGraph) is installed by the
  environment setup script, never per session." This session installed the CLI per session: drift (D22). No
  `.codegraph/` index exists, so all squads used grep and reads.
- The four issues were claimed at the operator's request (assigned to the operator, claim comments posted).
- open-packs is not attached with push; its issue/PR comment API answered 403, so comments on open-packs#73 and
  #175 were not read ([PRI] header; [OP175] header).
- Highest ADR on `main` is 0066; next free is **0067** ([PRI] section 5). No open charter PRs ([PRI] section 4).

## 1. BLUF

- **Shape:** #424 + #428 is one governed **Mission** (shared hot files, #428's store binding is a prerequisite of
  #424's compose checks and prompt read). #430 is a separate small **Op** (test-only plus `x-source` metadata).
  #429 rides **PR C** of mission `layer-merge-and-effective-reads-01M6R0J6` if PR C is still open and its owner
  accepts it; otherwise it is the last work package of the #424/#428 Mission, after PR C merges.
- **Order:** #430 Op any time (first is cheapest); PR C before the Mission's compose-check and prompt-read work;
  #428 binding before #424 checks.
- **The step enhancement is validated, then dropped, in three different ways today** (inspector validates, store skips,
  whole-pack registration refuses as "unmodelled"), and its URN is never "defined" for merge. #428 must fix all
  three sites plus `DomainRepositoryService.declaredWithoutFile`, not only `KindBindings` ([REN] A1, A2, B1).
- **#424's effective-prompt read has no contract home.** It needs a new operation (Added) or a member on PR C's
  action context (Breaking, WP28-owned). This is the biggest open decision and blocks specify (D5).
- **#429 cannot be fixed in Java alone:** `EnhancerWithoutTarget.targets` is typed `ActivatableKind`, so the fix is a
  `### Breaking` contract change (recommended new `NodeReference`) ([ALP] 4.1; [REN] C2). The same helper silently
  drops non-activatable URNs in about 13 other buckets ([REN] C1).
- **#430 already has real drift:** contract `NodeKind` lacks `guard`/`lifecycle`, `DiagnosticCode` keeps the retired
  `pack_unknown_key`, and 4 `x-source` paths point at a deleted module. Fix with explicit, reasoned exemptions, not
  by re-adding the retired Java constant ([JEN] 5; [REN] D1, D4; verified [SYN]).
- **Correction to [JEN]:** the whole-pack gate iterates only `packs/built-in`
  (`WholePackRoundTripTest.java:57,84`). open-packs#175 puts its enhancements in `packs/spdd`, so the new
  `empty_kinds: step-enhancement` entry will **not** go stale when #175 merges. Round-trip proof of the new kind
  must come from unit and corpus tests.
- **23 operator decisions** are listed in section 6. D1 to D6 block specify. The rest block single work packages.

## 2. Recommended scope, Mission-vs-Op split, sequencing

### 2.1 Split by blast radius (procedure step "Decide Mission vs Ops by blast radius")

| Unit | Issues | Shape | Blast radius reasoning |
|---|---|---|---|
| **M1** | #424 + #428 | Mission (full loop; adversarial squad at plan and pre-merge, per charter "How work lands") | Multi-WP. Touches the hot store files (`PackFileSet` 26, `WholePackStore` 23, `FileSystemPackInspector` 21, `DomainRepositoryService` 46 commits/90d), `KindBindings`, the mission-type prompt parse, `CharterRefusalCode.yaml` (43), pack schemas and probably a new OpenAPI operation. Several contract changes, two of them `### Breaking` ([ALP] 2). The issues share surfaces and a sequence (#428 item 1 is a prerequisite of #424, [PRI] 1), so grouping is allowed. |
| **O1** | #430 | Op | Single surface: `WireTokenParityTest` (plus maybe a sibling test) and `x-source` metadata in 4 contract schemas. No runtime behaviour change if D17/D18 take the recommended options. Independent of the others ([PRI] 1). |
| **#429** | #429 | Rides PR C (condition below), else last WP of M1 | One response schema (`EnhancerWithoutTarget`) on a read PR C already extends, and a `ClosureViewSession` hunk WP28 already edits ([ALP] 4.2 point 2). Cold files (1-4 commits/90d, [JEN] 3). A `### Breaking` contract change, so it needs the charter's contract review either way. |

**Condition for #429 in PR C:** PR C is still open and not yet in review, its owner/operator accepts the added
scope, and PR C introduces `NodeReference` once so that M1 reuses it. If any of these fails, #429 becomes M1's last
work package, branched after PR C merges.

**Reconciling [PRI] and [JEN] on #429's dependency:** [PRI] says #429 is latent until #428 stores step enhancements.
True for production behaviour; but the defect is testable now through `ClosureViewSession`'s package-private static
mappers (`inForceOf` etc.) with `InForceStates` values ([JEN] 2). So #429 does **not** need #428 to land first.

### 2.2 Sequencing and dependencies

```
O1 (#430) ----------------------------------------------> any time; land first (smallest, removes a
                                                          WireTokenParityTest conflict with #429)
PR C (WP27-29) [+ #429 if D3=ride] ---> merge
M1: WP-A #428 binding --> WP-B #428 checks --> WP-C #424 base-prompt load checks
                                     \-> (after PR C merges) WP-D compose checks + raise codes --> WP-E prompt read
                                                                       [--> WP-G #429 if D3=M1]
```

- WP-A, WP-B, WP-C may start before PR C merges **only** if they stay off the files WP27/WP28 own
  ([ALP] 4.2 table: `ClosureViewSession`, `InForceResolution` bindings, `MergedSnapshot`,
  `EffectiveActionContext.yaml`, the context path, `EffectiveReadService`, `ActivationController`, `PacksSession`,
  `ReservedOperationCoverageTest`).
- M1 rebases after spec, plan, tasks (procedure "Rebase on the fixed cadence").

### 2.3 Conflict hotspots

| File | Who edits it | Risk and mitigation |
|---|---|---|
| `contracts/charter/schemas/CharterRefusalCode.yaml` (43 commits/90d) | M1 WP-D raises 4 prompt codes (`x-refusal` :710-729, description :161-167); PR C WP28 raises `action_not_found` | Textual conflict likely ([ALP] 4.2). Do WP-D after PR C merges; separate hunks. `ContractDescriptionsTest` and `ReservedRefusalCodesTest` (list at :336-345, verified [SYN]) change with it. |
| `DomainRepositoryService.java` (46, hottest) | M1 WP-A (`compositionFacts`/`defined`, :379-421, `withoutFile` :581 verified [SYN]); PR C bindings (*(inferred)*, via `PackCompositionFacts.bindings`) | Keep WP-A's hunk to the defined-set source; do not add `STEP_ENHANCEMENT` to `withoutFile` ([REN] A1). |
| `PackFileSet.java`, `KindBindings.java`, `FileSystemPackInspector.java` | #424 and #428 both | Serialise inside M1: WP-A owns them; WP-B/WP-C only add checks. |
| `WireTokenParityTest.java` | O1 (new pairs), #429 (the `EnhancerWithoutTarget.kind`/`targets` copies) | Land O1 first; #429 then adjusts one pair. |
| `ClosureViewSession.java` | #429, PR C WP28 | Strongest argument for #429 riding PR C. |

## 3. Grounding summary

### 3.1 Responsibility map (compressed; full table [JEN] 1)

- **Classification:** `contracts/packs/file-kinds.yaml:187-194` maps `*/missions/*/enhancements/*.step-enhancement.yaml`
  to `step-enhancement` and `*.prompt-fragment.md` to `prompt-fragment` (markdown).
- **Validate mode:** `FileSystemPackInspector` schema-checks both; none of the semantic checks the schema description
  promises (id = stem, slot once, companion exists, no marker in a fragment, target is a step) exist in Java ([JEN] 1).
- **Load / hold:** `PackFileSet.yamlHolder` :408-418 and `markdownHolder` :428-440 return empty; neither file enters
  the fingerprint (`digested` iterates `KindBindings.all()`). `KindBindings.ALL` (14 bindings) has no
  step_enhancement; `DEFERRED_COMPANION_STRUCTURES = Set.of("prompt-fragment")` :49.
- **Whole-pack store:** both files are `unmodelled()`; `loadStaged` refuses them `validation_failed`; `write` drops
  them ([JEN]; [REN] A2; `WholePackStore.java:211-223` verified [SYN]).
- **Merge:** `DomainRepositoryService.declaredWithoutFile` covers only `ANTI_PATTERN`, `ACTION`, so a
  `step_enhancement:` URN is never in `defined`: no `LayerMerge` winner, and a reference to it is refused
  `pack_reference_artifact_missing` ([REN] A1).
- **Prompts:** held only as parsed `StepDocument` via `MissionTypeBinding` + `MarkdownCodec` (markers survive as
  text). **No HTTP operation serves a mission type, step or prompt** ([JEN] 1).
- **Effective reads:** `ClosureViewSession.activatable()` :229-231 (verified [SYN]) filters every bucket;
  `EnhancerWithoutTarget(ArtifactRef, List<ArtifactRef>)`, `EffectiveReadMapper` :166-172.
- **Vocabulary:** `K/NodeKind.java` 18 constants vs contract 16; `Diagnostic.Code` 2 vs contract 3;
  `CascadeKind` = activatable kinds + `all`, no Java enum ([JEN] 5).
- **The packs ship no step enhancement at the pin** `22c14b2`; built-in `pack.yaml` has no `schema_version`, so the
  five `spdd:reasons-block` slots that still hold text produce warnings, not refusals ([JEN] 1).

### 3.2 Invariants that must survive (from [ALP] 1; cite these in the spec)

| Id | Invariant | Source |
|---|---|---|
| I-1 | Round trip by meaning: every file read is written back, same paths, same meaning; unknown fields refused. Step enhancements and fragments must be **held**, not dropped. | ADR-0036 d1-d3, ADR-0040; charter "What must never go wrong" |
| I-3 | A step enhancement holds additions only (guards, emits, fragments), joined by an `enhances` edge to `action:<type>/<step>`; no `target:` field; add-only. | ADR-0053 d22 (amended 2026-11-05, -08); ADR-0050 d22-23 |
| I-4 | Layout: "beside the kind it adds to ... No separate enhancements area." | ADR-0053 OQ2, ruled 2026-11-03 |
| I-5 | Sibling step enhancers compose; two packs at one level defining one id conflict (C14). | ADR-0053 d21; ADR-0048 d9 |
| I-6 / I-7 | Guards are entries `{ref, config?, blocking?}`, add-only; relationships live only in graph documents (guards and emits are data). | ADR-0054 d2, d4, d10; ADR-0064 d1, d2, d11 |
| I-8 | Pack is provenance, not identity; ids unique across a compose except layer overrides. | ADR-0048 d10; ADR-0053 d7 |
| I-9 | Contract versioning on 0.x: any response-shape change is `### Breaking` (a new member on a closed response counts); `raised: false -> true` is `### Changed`; a new built read is `### Added`. No released baseline exists (`0.2.0-SNAPSHOT`, no `contract-charter-v*` tag). | ADR-0022 D2, D4; `docs/api/compatibility.md`; `contracts/charter/changes/` precedents |
| I-10 / I-11 | E6 enhancer/binding pruning and E14 `requiredNotInForce`; E12 an enhancement returned only when itself active (ADR-0066 d4's gate). | rules page E6, E12, E14 |
| I-12 | Integrity: open = override/enhance/remove; extendable = enhance/remove; locked = nothing. | conceptual model #integrity |
| I-13 | Divergence ids stable; next free in activation block is DIV-238. | divergence register |
| I-14 | Hexagonal: walks and merges in `packs`; `activations` reaches packs only via `activations.infra.packs`. | `HexagonalArchitectureTest` |

ADR-0066 d1-d9 summary: [ALP] 1 (d8: three validation moments; d9: effective-prompt read, "additive", "belongs with"
R5 #99 and R9 #103 - verified [SYN] at ADR-0066:141-145). Note [ALP] paraphrases the entry as `{slot, companion}`;
the schema field is `path` (`step-enhancement.schema.json:43`, verified [SYN]). Not a contradiction.

### 3.3 Gates touched and how each stays green honestly

| Gate | Effect | Honest route |
|---|---|---|
| `:packs:wholePackRoundTrip` (required via `CI gate / gate`) | Binding `step-enhancement` adds it to `modelledKinds`; built-in has no file of it, so `EMPTY_MODELLED_KIND step-enhancement` ([JEN] 4.9). | Add an `empty_kinds` entry to `service/packs/src/test/resources/whole-pack/expected-red.yaml` with issue #428, in the same change, following skill/guard/lifecycle (:9-26, verified [SYN]). The reason text must say it goes stale when **built-in** gains one (the gate reads only `packs/built-in`, `WholePackRoundTripTest.java:57,84`), not when spdd ships. Prove the round trip in unit/corpus tests (as `GuardAndLifecycleYamlTest`). D20. |
| `D/ReservedRefusalCodesTest` | Lists the four prompt codes as unraised (:342-345). | WP-D moves them to raised in the YAML, `RefusalHandler` and this list together; each must be named by a main class that actually raises it. Class (c)+(f) consequence of #424 (D19). |
| `checkRefusalExamples`, `ContractDescriptionsTest` | Pins refusal description wording. | Update wording with the raise, in the same change. |
| `checkContractCompatibility` / `checkContractChangelogFragments` | Every contract edit needs a dated fragment under `contracts/charter/changes/`; breaking needs `### Breaking`. | One fragment per change class (section 4). No released baseline, so only the fragment is required (I-9). |
| `lintContract` (`x-source-shape`, `reserved-operation-declares-501`, `built-operation-declares-no-501`) | New prompt operation; `x-source` fixes. | A built operation declares no 501; do not add a reserved op if PR C is driving reserved count to 0 (D5). |
| `OperationCoverage(Test)`, `ReservedOperationCoverageTest`, `UndefinedStepGuard` | New operation needs a scenario per 2xx. | Write `effective-step-prompt.feature` first, using the existing step vocabulary (`scenarios/README.md`). |
| `WireTokenParityTest` | #430 adds pairs; #429 changes an `ActivatableKind` copy. | Positive control fails each skewed pair (:191-205). NodeKind pair filtered by `authoredInGraphs()` or contract widened (D17); DiagnosticCode pair with a named "retired contract-only values" allowlist citing ADR-0036 d3 / ADR-0042 (D18). Read `DiagnosticCode.yaml` directly (no path references it, so a generated model may not exist, [JEN] 5, unverified). |
| `PackPolicyRulesTest:186-190` | Asserts `PACK_UNKNOWN_KEY` is absent from `PackFindingCode` and `Diagnostic.Code` (verified [SYN]). | Leave unchanged. Re-adding the Java constant would break it and contradict ADR-0042; the allowlist route edits no assertion. |
| `VocabularyEqualityTest` | Containment only (:274-292); NodeKind vs `file-kinds.yaml` order. | Unchanged unless D17 adds contract values. |
| `KindBindingsTest:198-210` | Asserts prompt-fragment is deferred. | Flips when bound: an asserted-expectation edit (class c), justified by the issue itself (D19). Delete the deferred branch at its 3 sites, not leave dead code ([REN] B1). |
| `PackFileSetExactnessTest`, `PackFileSetTest` | Fingerprint gains the new kind. | Append the binding (KindBindings "New kinds are appended" :14) so packs without enhancements keep their digest (NFR-002). |
| `HexagonalArchitectureTest` | New entity/DSO/binding; new read. | `StepEnhancement` in `packs.domain` (implements `CharterArtifact`), DSO and binding in `infra.storage`; a reserved method reaches no use case. |
| `openPacksRoundTrip` / corpus tests | Built-in prompts must stay warnings. | Version gate keyed on descriptor `schema_version: "1.0"`; built-in at pin is unversioned ([JEN] risks). |
| Docs checks (`check_docs.py`, markdownlint, cspell) | ADR annotations, rules page, changelog. | Run per charter "Gates before merge". |

## 4. Contract changes implied (0.x classes per I-9)

| Issue | Change | Class |
|---|---|---|
| #424 | Four prompt codes `raised: false -> true` with `operations:` maps (422 on registration, 500 default on composing reads); description rewritten | Changed |
| #424 | `DiagnosticCode.prompt_slot_not_empty` raised (no wire surface; description only) | Changed |
| #424 | New refusals of content that loads today: non-empty slot under descriptor `schema_version "1.0"`; malformed marker in any prompt | Breaking (one line; pinned packs unaffected) |
| #424 | Compose refusal `prompt_slot_missing` when a project prompt override drops a slot an enhancement names ("whatever the activation", d8) | Breaking (D13) |
| #424 | Effective-prompt read as a new operation `getEffectiveStepPrompt` + new closed schemas | Added; Breaking instead if it becomes a member of `EffectiveActionContext` (D5) |
| #424 | Optional `_links.prompt` on the action context (open `Links` map) | Added *(inferred non-breaking per [ALP]; needs a rel row in `docs/development/hypermedia-hateoas.md`)* |
| #428 | `PUT .../selections/step_enhancement/{id}` stops answering 404; preset ids stop showing stale | Changed |
| #428 | `step-enhancement.schema.json` description drops "(#428)"; optional `guards`/`emits` fields only if D12 = now | Added (if fields) / docs |
| #428 | Semantic rules: exactly one `enhances` edge to `action:`, `<type>` dir equals edge type, per-pack unique stem, companion rules, empty enhancement | Breaking (refuses content that validates today) |
| #428 | New code `step_enhancement_invalid` (only if D10 picks it) | Breaking (new enum value) |
| #428 | Id qualification `<type>/<stem>` (only if D8 picks it): `ArtifactId` has no `/` (`ArtifactId.yaml:13`, verified [SYN]) | Breaking, large |
| #428 | Layout move (only if D7 picks B or C) | Breaking |
| #429 | `EnhancerWithoutTarget.targets` items become `NodeReference {kind: NodeKind, id}` (minItems 1) | Breaking (documented response of `getActivationSkippedReport`) |
| #429 | `RequiredNotInForce` unchanged if D16 rules E14 activatable-only | none (docs: E14/C31 sentence) |
| #430 | `x-source` path fixes in `NodeKind.yaml`, `Relation.yaml`, `DiagnosticCode.yaml`, `Diagnostic.yaml` | none or Changed (metadata); a fragment is probably required by `checkContractChangelogFragments` *(inferred)* |
| #430 | `pack_unknown_key`: keep + allowlist (none) or delete (Breaking) | D18 |
| #430 | `NodeKind` + `guard`, `lifecycle` (only if D17 picks add) | Breaking under I-9's strict reading (response enum on reserved `getEffectiveGraph`) *(class to confirm with `contracts/compat/severity-levels.txt`)* |

## 5. Discovery

### 5.1 Similar surfaces ([REN], dispositions reconciled)

| # | Surface | Evidence | Disposition |
|---|---|---|---|
| A1 | `step_enhancement` not in `defined` (`declaredWithoutFile`) | `DomainRepositoryService.java:581` (verified [SYN]) | in-scope-now (#428, WP-A) |
| A2 | Whole-pack registration refuses enhancement + fragment as unmodelled; write drops them | `WholePackStore.java:211-223`, `loadStaged` :244-248 | in-scope-now (#428, WP-A) |
| B1 | Deferred-companion branch must be deleted at 3 sites | `KindBindings.java:43-49,111-113`; `PackFileSet.java:431`; `FileSystemPackInspector.java:260` | in-scope-now (#428, WP-A) |
| E | Guard/emit schema additions deferred to #428 | `step-enhancement.schema.json:5`; changelog 2026-11-08 :45 | in-scope-now as a ruling; fields per D12 |
| E | Effective-prompt read promised, no contract surface | changelog 2026-11-08 :42-44; no `paths/*prompt*` (verified [SYN]) | in-scope-now (#424, WP-E; D5) |
| A3 | `org-charter.yaml` classified and validated, held by no store; registration refuses it | `file-kinds.yaml:146-147`; not in `WholePackStore` modelled set (verified [SYN]). [SYN] adds: open-packs `qa`, `internal`, `event-driven`, `cave-kitty` and #175's `spdd` all ship one, so a whole-pack registration of those packs would be refused *(inferred, not run)* | file-and-link (#431); raise priority given #175 |
| A5 | `text` content form declared but refused; skill bodies already `ContentForm.TEXT` companions | `file-kinds.yaml:31-35`; `SkillBinding.java:51-56` | file-and-link, low (#432) |
| A4 | `anti_pattern` activatable without binding | by design, graph nodes | none needed |
| C1 | ~13 other `ClosureViewSession` buckets drop non-activatable URNs via `activatable()` | `ClosureViewSession.java:98-226` | in-scope-now with #429 as a table test of every bucket; widen the wire only for enhancer targets (D4a) |
| C2 | Wire types `EnhancerWithoutTarget.targets`, `RequiredNotInForce.kind`, `DenyNotApplied.kind` are `ActivatableKind`; mapper `fromValue` would throw | `EffectiveReadMapper.java:168,179` | in-scope-now (#429) for `targets`; others ruled (D16) |
| C3 | `overridesOf` drops layer overrides of guard, lifecycle, template, asset, action | `ClosureViewSession.java:149-157` | PR C (WP28 `LayerOverride.kind`); hand-off note A.5 |
| C4 | Registration notices drop non-activatable kinds | `PackRegistrationMapper.java:145-168` | already #417: Refs only |
| C5 | v1 activation codec now accepts `step_enhancement` | `ActivationFileCodec.java:123-128` | note in #428 review only (D4e) |
| D1 | NodeKind/CascadeKind/DiagnosticCode parity missing, drifted | [JEN] 5 | in-scope-now (#430) |
| D2 | No parity for EffectiveArtifactState/Reason, ArtifactPolicy, DenyNotApplied.reason, RequiredNotInForce.reason (Java type wider), ActivationWarning.code, 4 unpaired ActivatableKind copies | [REN] D2 | in-scope-now with #430 if D4b approves |
| D3 | No parity for registration enums | [REN] D3 | file-and-link (#433) |
| D4 | Stale `x-source` in **4** files (NodeKind, Relation, DiagnosticCode, Diagnostic; [JEN] named 3) | verified [SYN] | in-scope-now (#430) |
| E1 | `action_not_found` reserved | `CharterRefusalCode.yaml:29-32` | PR C (WP28) |
| E1 | `pack_level_mismatch`, `drg_schema_error`, `unresolved_edge_endpoint` | retired | none |
| - | Stale "Reserved (#125)" javadoc in `RefusalCode.java:68-88` | [REN] (e) | cosmetic; boy-scout optional |

### 5.2 Tracked issues ([PRI])

| Ticket | Relation | Disposition |
|---|---|---|
| #424, #428, #429, #430 | delivered | Closes, each only if fully landed (#428 needs the guard/emit ruling and layout ruling; #429 needs the contract change). Else Refs and tick boxes. |
| #425 (merged PR) | contracts half of ADR-0066 | Refs |
| #427 (merged PR B) | source of #429 | Refs |
| #426 | PR B follow-ups; "enum mapping by name" item is #430's drift class | Refs; fold that one item into O1 only if D4c approves |
| #10, #11 | mission issues (PR C pending; ADR-0053 OQ2 ruling recorded on #11) | Refs, leave open |
| #103, #99 | R9, R5 reads; d9 says prompt read "belongs with" them; hand-over says do not take #99 | Refs (#103); #99 Refs optional, do not take (D5) |
| #133 | `x-extensible-enum` for DiagnosticCode | Refs; #430 does not decide it |
| #417 | same root as #429 (`ArtifactReference.kind` is `ActivatableKind`) | Refs + design note (reuse `NodeReference`); do not fold (D4d) |
| #339 | core: step enhancements and `add_guards` (input to guard/emit shape) | Refs optional, leave open |
| #358 | distribution pack settles sibling contests (same rule for step enhancements) | leave alone |
| #186 (closed epic), #418 (closed), #416, #361, #111 (closed), #112, #337, #408, #94, #6 | context only | leave alone (#6 or #10 candidate parent, D23) |
| open-packs#175 (open PR) | blocked-by this run; ships 5 spdd enhancements in `#425` layout | Refs spec-kitty/spec-kitty-open-packs#175 |
| open-packs#73 | consumer context (where reasons-blocks go) | leave alone |

**Duplicates / superseding: none found.** **Open PRs on these surfaces: none found** (charter has 0 open PRs).

## 6. Escalation-class decisions for the operator

Ordered by what blocks specify first. Classes per `semi-auto-delivery`: (a) regression vs stale test, (b) whether to
change a shared seam / contract / boundary, (c) editing asserted expectations, (d) widening scope, (e) charter
Decision Moment, (f) versioning mechanics.

**Blocks specify**

1. **D1 Delivery shape.** Options: (i) M1 = #424+#428 Mission, O1 = #430 Op, #429 per D3; (ii) all four in one
   Mission; (iii) four separate units. Recommend (i). Class (d). Blocks: everything.
2. **D2 PR C ownership split.** [ALP] 4.2: PR C keeps WP27-29 scope plus the step-enhancer binding (E6, DIV-224,
   hand-over); PR C does not touch prompts; M1 owns store binding, checks, four codes and the prompt read as a new
   operation, building on WP27's `ActionContextWalk`; M1's WP-D/WP-E branch after PR C merges. Options: accept; or
   M1 runs fully in parallel with file fences. Recommend accept. Class (b)+(d). Blocks: WP-D, WP-E, #429.
3. **D3 #429 placement.** Options: ride PR C (condition in 2.1); last WP of M1; own Op. Recommend ride PR C if the
   condition holds, else M1 WP-G. Class (d). Blocks: #429 slice.
4. **D4 Scope widenings (each separately).** Recommend: (a) #429 covers all ~13 `ClosureViewSession` buckets as a
   table test, widening the wire only for `EnhancerWithoutTarget.targets`; other buckets made explicit by
   construction (*(inferred)*: selections, denies and floors name only activatable kinds) - **approve**;
   (b) #430 adds the D2 rows incl. the 4 unpaired `ActivatableKind` copies and narrows the Java
   `RequiredNotInForce.reason` type - **approve**; (c) fold #426 "enum mapping by name" into O1 - **approve if
   under ~1 file**, else leave; (d) fold #417 - **decline**, Refs only; (e) freeze v1 codec against
   `step_enhancement` - **decline**, review note only; (f) fix `x-source` in all 4 files plus an assertion -
   **approve**. Class (d). Blocks: O1 and #429 scope.
5. **D5 Effective-prompt read: existence and route.** Options: (i) new built operation `getEffectiveStepPrompt`,
   `GET /activations/{id}/mission-types/{type}/actions/{action}/prompt` (Added); (ii) member on
   `EffectiveActionContext` (Breaking; WP28-owned file); (iii) add as `x-reserved` now, build later (Added, but
   collides with WP28 driving reserved operations to 0); (iv) defer out of #424 to R5 #99 (hand-over says not to take
   #99; #424 then stays open, Refs only). Sub-decisions: list every declared slot with `fragments: []` (recommended)
   or filled only; "base text unchanged" = canonical render, meaning-equal per ADR-0036 (recommended, *(inferred)*
   since the store holds parsed `StepDocument`, not bytes) or a new raw-text holding; add `_links.prompt` on the
   context (recommended, after PR C). Recommend (i). Class (b). Blocks: WP-E, and whether #424 can be Closed.
6. **D6 Guards and emits now or later.** "Fields, not edges" is settled by ADR-0054 d2 and ADR-0064 d11. Options:
   (i) add `add_guards`/`emits` fields now (base mission-type schema has no transitions or action points, so load
   checks have nothing to check against); (ii) rule the shape in an ADR-0066/ADR-0053 annotation, keep the schema at
   `prompt_fragments`, reword the description, open a new issue (#434). Recommend (ii). Class (b)+(d). Blocks:
   WP-B scope and whether #428 Closes.

**Blocks individual M1 work packages**

7. **D7 Layout (ADR-0053 OQ2).** A keep `missions/<type>/enhancements/` and annotate the ruling; B move beside the
   step in `steps/`; C top-level `step_enhancements/`. #425, the service and #175 already agree on A. Recommend A
   plus the directory-equals-type check. Class (b) (annotating a ruling; ADR-0067 only if a new record is wanted).
   Blocks: WP-A.
8. **D8 Id rule.** (a) unique per pack (load check refuses a stem repeated across type dirs), no URN change;
   (b) qualify as `step_enhancement:<type>/<stem>` (`ArtifactId` grammar change). Recommend (a). Class (b). Blocks:
   WP-A, WP-B.
9. **D9 `enhances` cardinality.** Exactly one `enhances` edge per step enhancement, target an `action:`; versus E6's
   multi-target allowance. Recommend exactly one. Class (b). Blocks: WP-B.
10. **D10 Code for #428 semantic rules.** Reuse `prompt_fragment_invalid` with a broadened description (no enum
    change) vs new `step_enhancement_invalid` (Breaking). Recommend reuse. Class (b)+(f). Blocks: WP-B.
11. **D11 Companion naming and orphans.** Schema says `<id>.prompt-fragment.md` but an enhancement may fill several
    slots. Recommend: one companion per entry, any stem, named by exactly one entry, orphans refused. Class (b).
    Blocks: WP-A (round trip ownership), WP-B.
12. **D12 Empty enhancement** (no fragments, no guards/emits). Refuse vs allow as an activation anchor. No squad
    recommendation; [SYN] leans refuse while guards/emits are absent *(inferred)*. Class (b). Blocks: WP-B.
13. **D13 Compose refusal "whatever the activation" (ADR-0066 d8).** An inactive enhancement whose slot a project
    override dropped refuses every composing read (500), blocking project prompt overrides. Options: confirm d8;
    amend ADR-0066 to refuse only when the enhancement is active and warn otherwise. No squad consensus; [ALP] flags
    the consequence. Class (b)+(e if treated as an ADR amendment). Blocks: WP-D.
14. **D14 Step integrity.** (Q2) Does a step inherit its mission type's mutability level? Without it, d7's "locked
    target takes no fragment" is vacuous for steps (`policy.schema.json` cannot name `action`; verified [SYN]).
    (Q3) Write down that an enhancement is judged as `enhance`, separately from `relate`. No squad recommendation on
    Q2; [SYN] leans inherit *(inferred)*. Class (b). Blocks: WP-D.
15. **D15 #429 contract shape.** Option 2 `NodeReference {kind: NodeKind, id}` (id pattern admits `/`),
    minItems 1, Breaking; vs `targets: [Urn]`; vs an extra `stepTargets` member; widening `ArtifactReference`
    rejected. Recommend option 2, introduced once and reused by WP28/M1. Class (b)+(f). Blocks: #429.
16. **D16 #429 second item.** Rule E14/C31: `requiredNotInForce` covers activatable kinds because the floor holds only
    activatable kinds by construction; assert in `InForceResolution`; no contract change, no DIV. Recommend. Class (b).
    Blocks: #429.

**Blocks O1**

17. **D17 NodeKind `guard`/`lifecycle`.** Add to the contract (response enum on reserved `getEffectiveGraph`,
    coordinate with WP28) vs parity row filtered by `authoredInGraphs()` with a reason. Recommend filter in O1; WP28
    decides graph listing. Class (b). Blocks: O1.
18. **D18 `pack_unknown_key`.** Keep + named allowlist (no contract change; `PackPolicyRulesTest` untouched) vs delete
    (Breaking). Recommend keep. Class (b); avoids (c). Blocks: O1.

**Mechanics and test expectations (surface, then proceed)**

19. **D19 Versioning mechanics and expectation edits consequent on #424/#428.** Flip `KindBindingsTest:198-210`;
    move the four codes in `ReservedRefusalCodesTest`; reword `ContractDescriptionsTest`; land fragments
    (Changed + Breaking + Added) with the contract change and human docs in the same commit. Recommend approve as a
    set. Class (c)+(f). Blocks: WP-A, WP-D.
20. **D20 `expected-red.yaml` `empty_kinds: step-enhancement` (#428).** Follows precedent; reason text corrected to
    "stale when built-in gains one". Recommend approve. Class (c). Blocks: WP-A.
21. **D21 Pin bump and #175 timing.** #175 is blocked by this run; M1 cannot be proven against the real spdd pack
    until #175 merges and the pin moves. Options: M1 ships on fixtures and a later Op bumps the pin and adds a
    `packs/spdd` corpus test (#435); or M1 waits for #175. Recommend the former. Class (d). Blocks: M1 acceptance
    wording only.

**Process drift and tracker hygiene (no slice blocked)**

22. **D22 Drift flags.** CLI rc7 installed vs rc8 named in `AGENTS.md`/charter; `charter context --include
    procedure:semi-auto-delivery` finds nothing; session tooling installed per session against the charter rule.
    Options: record only; fix the environment setup script; amend AGENTS.md. Recommend record in the mission
    research note and fix the setup script outside this run. Class (e) (charter drift).
23. **D23 Parent issue.** None of the four has a parent or milestone. Options: all under #10; #424/#428 under #11,
    #429 under #10, #430 under #6. Operator's tracker call; not an a-f class.

## 7. Draft slices

| Slice | Content | Done when | Test-first | Depends on |
|---|---|---|---|---|
| **O1** (#430 Op) | Parity pairs NodeKind, CascadeKind (activatable + `all`), DiagnosticCode; D2 rows; `x-source` fix in 4 files + assertion that each `x-source.path` exists | Each pair fails on a planted skew (positive control); `pack_unknown_key` exempt by named allowlist citing ADR-0036 d3/ADR-0042; guard/lifecycle per D17; `PackPolicyRulesTest` unchanged; fragment landed if required | Red-first: add the rows, watch them fail on `pack_unknown_key`, guard/lifecycle and the stale paths, then add the reasoned exemptions and path fixes. No BDD (no behaviour change). | D4b, D4c, D4f, D17, D18 |
| **WP-A** (#428) | `StepEnhancement` entity, DSO, binding appended to `KindBindings.ALL`; prompt-fragment as its companion; delete deferred branch (3 sites); `defined` via binding; `WholePackStore` modelled; fingerprint | Enhancement + fragments round-trip by meaning in unit tests; `loadStaged` accepts a pack with one; `PUT .../selections/step_enhancement/{id}` answers 200; `empty_kinds` entry; exactness tests green | BDD first: scenario under `contracts/charter/scenarios/` for selecting a step enhancement (was 404). Red-first unit tests for A1/A2 using temp-dir packs. | D7, D8, D11, D19, D20 |
| **WP-B** (#428) | Semantic checks: id = stem, slot once, companion exists, orphan refused, exactly one `enhances` to `action:`, dir = edge type, per-pack unique stem, empty enhancement; schema description update; ADR annotation(s) | Each rule has a refusal test in validate mode and at registration (422) | BDD scenario for a refused registration; unit tests in `FileSystemPackInspectorTest`. | D6, D8, D9, D10, D11, D12 |
| **WP-C** (#424) | Base-prompt slot parse and load checks: `prompt_slot_malformed` always; `prompt_slot_not_empty` refusal under `schema_version "1.0"`, warning otherwise | Built-in at pin still loads with warnings; versioned fixture refuses; `openPacksRoundTrip` green | Red-first unit tests in `MissionTypeBindingTest`; BDD for registration refusal. | D19 |
| **WP-D** (#424) | Compose checks (target resolves, `prompt_slot_missing`, integrity); raise four codes in `CharterRefusalCode.yaml`, `RefusalHandler`, tests; fragments | Codes raised by real code paths; `ReservedRefusalCodesTest`, `checkRefusalExamples`, `ContractDescriptionsTest` green; Changed + Breaking fragments | BDD scenarios first for each compose refusal; branch after PR C merges. | D2, D13, D14, D19 |
| **WP-E** (#424) | Effective-prompt read per D5 | New operation served, scenario covers each 2xx and 404s; `OperationCoverage` green; Added fragment; hypermedia rel row if `_links.prompt` | `effective-step-prompt.feature` first; builds on WP27 accessors. | D2, D5 |
| **WP-F** (docs) | ADR-0053 OQ2 / ADR-0066 annotations (or ADR-0067), rules page E14/C31, changelog | Docs checks green; annotations dated | n/a | D6, D7, D13, D14, D16 |
| **#429** (PR C or WP-G) | `NodeReference`; `EnhancerWithoutTarget.targets` widened; domain records and mapper widened; table test of every `ClosureViewSession` bucket; E14 ruling + `InForceResolution` assertion | Step targets appear in the skipped report; no bucket drops silently; Breaking fragment; `WireTokenParityTest` adjusted | Red-first: `inForceOf` static mapper with an `action:` target returns `[]` today; BDD row in `effective-reads.feature` first. | D3, D4a, D15, D16 |
| **Later Op** | Pin bump to include #175; `packs/spdd` corpus test | spdd enhancements load, compose and round-trip | per AGENTS.md pin-bump rules | D21 |

## 8. Open questions and unknowns

- Comments on open-packs#73 (2) and on #175 were not read (no push attachment; 403). They may carry layout or id
  decisions that bear on D7/D8/D11.
- #175's copy of `step-enhancement.schema.json` differs from the charter's (the [OP175] diff shows an older,
  shorter description without the semantic-check text) *(inferred: #175 carries a lagging copy)*. Whether open-packs
  vendors the charter schema, and whether it will re-sync, is unknown (draft A.7).
- Whether #175 also gives the **built-in** descriptor `schema_version: "1.0"`; if it does, the five built-in slots
  become refusals unless #175's emptying of them lands with it ([OP175] shows `plan.prompt.md` emptied).
- PR C status (checked by the run lead after synthesis): spec-kitty-charter has **0 open PRs** on 2026-11-09, so PR C
  is not yet raised; its lanes (`lane-y`, `lane-z`, `lane-{`) exist but must be rebuilt on `main` (hand-over step 3).
  D3's condition ("open and not yet in review") therefore reads as "not yet raised; the PR C runner would take #429
  into its plan before it rebuilds the lanes".
- Whether the generator emits a model for `DiagnosticCode` (no path references it) - affects how O1 reads it.
- Severity of adding a response enum value to a reserved operation's schema under `contracts/compat/severity-levels.txt`.
- Whether an `x-source`-only contract edit needs a changelog fragment.
- Whether a whole-pack registration of the open-packs org packs actually fails on `org-charter.yaml` today (A3; not run).
- Nothing was built; all gate effects are read from code, not observed.

---

## Appendix A. Draft issue bodies (for the operator to file)

### A.1 → #431 (charter) `org-charter.yaml` is validated but held by no store, and a whole-pack registration refuses it

`contracts/packs/file-kinds.yaml:146-147` classifies `*/org-charter.yaml` (schema `org-charter`), and validate mode
reports `org_charter_schema_error` for it (`PackPolicy.java:833`). But no `KindBinding` holds it,
`PackFileSet.yamlHolder` skips it, and `WholePackStore.load` leaves it out of the modelled set
(`WholePackStore.java:211-223`). A whole-pack registration of a pack with an org charter therefore fails with
`422 validation_failed` ("unmodelled"), and `WholePackStore.write` drops the file silently. open-packs `qa`,
`internal`, `event-driven`, `cave-kitty` and the `spdd` pack of open-packs#175 each ship one, and each descriptor
names it as `active_charter`.

Decide: (a) model it (a binding, or a pack-level slot like presets and the manifest), or (b) exclude it with a
reason, or refuse it by name. Add a test that classification, validate mode, load and registration agree on the file.
Related: #428 (same "classified, validated, unheld" shape).

### A.2 → #432 (charter, low) The `text` content form is declared but refused, while skill bodies already use it

`file-kinds.yaml:31-35` declares `text` "not built yet", and a rule naming it fails the table
(`PackFileSet.java:398`, `FileSystemPackInspector.java:205` throw `notStored`). `SkillBinding.java:51-56` already
holds skill bodies as `ContentForm.TEXT` companions, unclassified by `file-kinds`. Either build the form and add a
rule for skill bodies, or reword the table comment to say `text` exists only as a companion form. Related: #192.

### A.3 → #433 (charter) Parity rows for the registration enums

`WireTokenParityTest` does not pin `PackRegistrationStatus.status` vs `RegistrationJobs.State`,
`PackSource.kind`/`PackSourceRecord.kind` vs `PackSourceKind`, or `PackRegistration.outcome`/`level` vs
`PackRegistrationService.Outcome` and the registrable `PackLevel` subset. They match today; a constant added on either
side would surface as a `fromValue` 500 or an unreachable contract value. Add one pair per enum, as #430 does.
Related: #430.

### A.4 → #434 (charter; D6 = later, ruled) Step transitions and action points, then step-enhancement guards and emits

ADR-0054 d2 and ADR-0064 d11 settle that a step enhancement's guards and emits are fields
(`add_guards: [{transition:{from,to}, guard:[GuardEntry]}]`, `add_lifecycle_guards`, `emits: {on_enter?, on_exit?}`).
`mission-type.schema.json` has no step transitions, submachine, guards or action points, so the enhancement fields
would have nothing to check against. Add the base structures first, then the enhancement fields with their load checks
(transition exists, guard `ref` resolves, `config` against `config_schema`, event resolves in the registry). Related:
#428, #339, ADR-0050 d15-16, ADR-0051.

### A.5 → posted on #429 (hand-off note to PR C)

`ClosureViewSession.overridesOf` (:149-157) drops layer overrides of non-activatable kinds (guard, lifecycle,
template, asset, action). WP28's new `LayerOverride` should type `kind` as `NodeKind` (keep them) or record the drop
as a divergence. Also: if #429 rides PR C, introduce `NodeReference` once for reuse by M1.

### A.6 → #435 (charter) Bump the open-packs pin to include open-packs#175 and prove the spdd pack

After spec-kitty/spec-kitty-open-packs#175 merges: bump `openPacksRef` per AGENTS.md (lockfile, expectations, pin
comment), and add a corpus test over `packs/spdd` that loads, composes and round-trips its five step enhancements and
fragments (the whole-pack gate reads only `packs/built-in`). Related: #424, #428.

### A.7 (open-packs) spec-kitty/spec-kitty-open-packs#177 (filed 2026-11-09; the filed text follows the rulings R-OVR, A1, A2)

Align the spdd pack of #175 with the charter's rulings once made: the charter's current
`contracts/packs/schemas/step-enhancement.schema.json` (the copy on #175 lags its description), layout (D7), id rule
(D8), single `enhances` edge per enhancement (D9), companion naming (D11). No change is needed if the operator takes
the recommended options, except the schema copy. Related: spec-kitty/spec-kitty-open-packs#175, #73;
spec-kitty/spec-kitty-charter#428, #424.

## Synthesis metadata

Sources integrated: java-jenny, architect-alphonso, reviewer-renata, planner-priti, open-packs#175 git read.
Contradictions resolved by reading code: whole-pack gate scope (built-in only; corrects [JEN] 4.9); `x-source`
stale count (4, not 3); `{slot, companion}` vs `path` (paraphrase only); #429 dependency on #428 (testable without
it). Claims verified at: `ArtifactId.yaml:13`, `DomainRepositoryService.java:581`, `ClosureViewSession.java:229-231`,
`ReservedRefusalCodesTest.java:70-72,336-345`, `PackPolicyRulesTest.java:186-190`, `expected-red.yaml:1-26`,
`WholePackStore.java:211-223`, `WholePackRoundTripTest.java:57,84`, `NodeKind.java:55-62`, ADR-0066:141-145,
`policy.schema.json` (no `action`).
