# Pre-spec squad — planner lens (scope, slicing, risk) — charter #194

Delegate: planner-priti (built-in profile, loaded via `spec-kitty agent profile show planner-priti`).
Charter context loaded: `spec-kitty charter context --action plan --json` (bootstrap).

**Profile boundaries applied.** Avoidance boundary: no code, no architectural decisions, no product strategy, no managing agents directly. I propose slicing and sequencing and flag architecture choices as questions for the architect (and ADR WPs). I do not decide them. Directive refs: 003 (decision documentation: every call I make gets a recorded rationale, and calls I cannot make become open questions) and 053. Success definition: a WBS that respects dependencies, with acceptance criteria per WP, parallel where possible.
**Charter directives applied.** Non-trivial work runs as a mission. A contract, architecture or domain change gets an adversarial squad. A call made without review is logged as an open item. `verifyAll` on Java 25 is the authority. Docs checks (check_docs all, markdownlint, cspell). BDD/scenarios first.

**Operator decision received mid-task (binding):** the sanitized DSO shape + one JSON Schema per DSO wins over #194's "round-trip it as-is" wording. Divergent pack content goes to an open-packs issue and an entry in `known-failures.yaml`, with no tolerance logic. That settles the reconciliation question, so this report does not reopen it.

Evidence base: open-packs at `01edb4e` (= `gradle.properties:8` openPacksRef); `contracts/packs/file-kinds.yaml`; gate resources `service/packs/src/test/resources/whole-pack/{expected-red,known-failures,exclusions}.yaml`; `WholePackCensus.java`; PRs #242/#245/#248/#253; issues #148, #150, #154, #168, #195, #236, #247.

---

## 1. Scope statement

### Ground truth: what the gate sees today

`WholePackCensus.kindOf` (WholePackCensus.java:62-68) gives a file its schema name when a `files`/`payloads` rule maps it. Every other file gets `other:<top-level dir>`. Pack-vocabulary *exclusions* in file-kinds.yaml are **not** "Mapped", so the gate still counts the files they cover, under `other:missions`.

| Sub-area | Files in built-in @01edb4e | Gate kind today | Baseline entry |
| --- | --- | --- | --- |
| procedure | 23 (`built-in/procedures/*.procedure.yaml`); 41 across built-in/internal/qa | `procedure` (schema exists, mapped by `**/*.procedure.yaml`) | expected-red.yaml:8 `kind: procedure` (#194) |
| anti_pattern (graph) | 1 shard, 13 nodes: `anti_pattern.graph.yaml` | `graph-shard` (modelled) | expected-red.yaml:29: red **only** because node `tags` are dropped (#236) |
| anti_pattern (inline) | `anti_patterns:` blocks in 23+1 styleguides and 16 procedures (built-in) | styleguide (green), procedure (red) | rides with its host kind |
| mission types | ~150 files under `built-in/missions/**` (181 minus 21 step-contracts, `__init__.py`, READMEs) | `other:missions` | expected-red.yaml:11 `kind: other:missions` (#194) |

The mission tree at 01edb4e holds:
- 4 `mission_types/*.yaml`
- 4 each of `mission.yaml`, `mission-runtime.yaml`, `expected-artifacts.yaml` and `governance-profile.yaml`
- 24 `actions/*/index.yaml`
- 30 `mission-steps/**/step.yaml`, 30 `prompt.md` and 17 `guidelines.md`
- about 30 files under `missions/<type>/templates/` (mostly `.md`, plus 2 `.csv`, 2 `.template` and 1 `.gitkeep`)

That is **7 distinct YAML shapes** and about **80 non-YAML files**.

### In scope
- **Procedure.** Domain entity, DSO, YAML format and binding for `*.procedure.yaml`, with the inline `anti_patterns` (2-field) held. This absorbs #168's procedure half. Kept as a standalone kind because #150 is open (Backlog).
- **anti_pattern.** *Prove* it round-trips where it lives, with no new kind binding or standalone file:
  - inline in styleguides: already held by `StyleguideDso.AntiPattern` (StyleguideDso.java:85), so this is a proof only
  - inline in procedures: via the new procedure DSO
  - the `anti_pattern.graph.yaml` shard: already read as a graph shard
  - the deliverable is a test or gate assertion that the kind has no standalone file and that every node is accounted for
- **Mission types.** Every file the gate currently counts as `other:missions`, including the `missions/<type>/actions/*/index.yaml` and `missions/<type>/templates/**` content. **They are not covered by existing kinds:**
  - The `template` payload rule is `*/templates/**/*` (file-kinds.yaml:188). Its leading `*` is a single segment, so it matches only a pack's *top-level* `templates/` and not `built-in/missions/<type>/templates/...`.
  - The `mission_step_contract` kind covers only `**/*.step-contract.yaml` (`missions/built_in_step_contracts/`), which is already green per #245.
  - `actions/*/index.yaml` is explicitly *excluded* (file-kinds.yaml:132), so it falls into `other:missions`.
  - The expected-red entry's own reason names "mission types, mission steps, actions and mission templates" against #194.
- Sanitized DSO + JSON Schema per DSO under `contracts/packs/schemas/` for each new YAML shape: procedure already exists, plus about 7 mission-side schemas.
- For content that diverges from a sanitized schema: an open-packs issue and a `known-failures.yaml` entry, one per file (the format at known-failures.yaml:6-8 is per path).

### Out of scope (explicit)
- **#154**: promoting the 131 unique inline anti-patterns into DRG nodes and `rejects` edges. That issue's own sizing calls it "~22×" and it needs human curation. Backlog.
- **#236**: graph-shard node tags. That is a service-wide DRG defect owned by its own issue. `anti_pattern.graph.yaml` stays expected-red under #236, so **anti_pattern does not "flip green"** in this mission. Say so in the spec or it reads as a miss (see Q2).
- **#148**: promoting mission types to full charter artifact kinds (Post-cutover). This mission is read/write fidelity only: no HTTP surface and no relationship semantics beyond what URN validity already gives.
- **#150**: the fate of the procedure kind.
- **`other:toolguides`**: guide_path Markdown (expected-red.yaml:23, owned by #186). #168 also names toolguide, but the toolguide entity already ships. The guide_path companion is not part of "absorbs #168 (procedure)". See Q3.
- All of #195's files (presets, pack.md, pack-manifest, pack.yaml) and any HTTP contract change.

---

## 2. Size estimate and PR slicing

Precedent sizes (from `get` on each PR; non-dossier counts estimated from the bodies):

| PR | Kind(s) | Files / +lines | New seam? |
| --- | --- | --- | --- |
| #242 | agent_profile | 60 / +3.1k | binding seam + new slice |
| #245 | mission_step_contract | 57 / +4.3k | schemaName seam |
| #248 | glossary_pack + skill | 95 / +8.0k | companion seam (ADR-0039) |
| #253 | asset + template | 127 / +10.4k (57 of the files are the dossier) | content_form (ADR-0040) |

| Sub-area | Estimate | Rationale |
| --- | --- | --- |
| **Procedure** | S-M: ~35-45 files, +3-4k | Closest twin is tactic (steps; `Tactic.java` is 377 lines, `TacticDso` 107). The schema exists and all **41/41 procedure files validate against the closed `procedure.schema.json` today** (checked locally with jsonschema). No new seam, so expect few or no known failures. About #245 minus the seam work. |
| **anti_pattern** | XS: ~3-6 files, +200-400 | Proof tests plus the doc of where the kind lives. No binding. |
| **Mission types** | L-XL: ~110-150 files, +10-14k (dossier excluded) | 7 new schemas + DSOs + bindings. Plus ~80 non-YAML files that need a home: `prompt.md` is referenced by `step.yaml` (`prompt_template: prompt.md`), so it can reuse the ADR-0039 companion. `guidelines.md` is **referenced by no step.yaml**, so ADR-0039 cannot carry it. Mission templates sit outside the `*/templates/**` payload rule, and ADR-0040 allows a `bytes` rule only for the kinds `asset-payload`/`template`. Pack layout also differs between packs: built-in nests `missions/mission-steps/` and `missions/mission_types/`, while qa has top-level `mission-steps/` and `mission_types/` (38 qa files) and cave-kitty has `command-templates/`. **Needs at least one ADR or amendment.** Larger than #253. |

**Recommendation: 2 PRs, with a pre-agreed option to cut a third.**
- **PR-A: procedure + anti_pattern** (one ADR amendment at most). Small and low-risk, so it lands first. It flips `kind: procedure` out of expected-red. anti_pattern stays tied to #236.
- **PR-B: mission types.** Starts in parallel (the code is disjoint) and merges after PR-A, so the shared-file edits stack in order. If the WP06 schema census shows more than ~5 shapes need new seams, split it into:
  - PR-B1: the runtime definitions (mission_type, mission, mission-runtime, expected-artifacts, governance-profile, action index), all YAML
  - PR-B2: mission steps + companions + mission templates (the non-YAML handling and the ADR)

Slicing within the gate works: when a new file-kinds rule maps a file, the census moves it from `other:missions` to its schema kind. A partial PR can therefore flip some files and leave the `other:missions` entry for the rest. The **last** PR removes the entry. No tolerance logic is needed.

**Dependency order:** PR-A ∥ PR-B in development. PR-A merges first. PR-B rebases onto PR-A and onto whichever #195 PR landed.

---

## 3. Draft WP decomposition

Gate proof in every case: `./gradlew :packs:wholePackRoundTrip` (CI job `whole-pack`), plus `:packs:openPacksRoundTrip` for the non-built-in packs, plus `verifyAll` on Java 25 in CI.

### PR-A — procedure + anti_pattern

| WP | Title | Depends | Owns | Acceptance / proving tests |
| --- | --- | --- | --- | --- |
| WP01 | Procedure domain model | — | `packs/domain/Procedure*.java` (entity, identifier, step, reference, repository port, inline anti-pattern VO) + unit tests | Every field of `procedure.schema.json` is held; invariants mirror the schema; no invented defaults (the #242 rule); `ProcedureTest` |
| WP02 | Procedure storage binding | WP01 | `ProcedureDso/Yaml/Binding.java`; **one appended line each** in `KindBindings`, `YamlFormats`, `DomainRepositoryService` | `ProcedureYamlTest`; `ProcedureCorpusTest` (41 files across built-in/internal/qa round-trip by meaning); a lossy-writer positive control catches a dropped `anti_patterns[].description` |
| WP03 | anti_pattern placement proof | WP02 | test only, e.g. `AntiPatternPlacementTest`, plus the doc line | The 13 shard nodes are read (tags-only diff, still pinned under #236); inline styleguide and procedure anti-patterns survive the round trip; file-kinds has no rule for a standalone `*.anti-pattern.yaml`; in a pack with none, the kind is reported as having no standalone file (not `empty_kinds`) |
| WP04 | Gate flip: procedure | WP02 | `expected-red.yaml`: remove **only** the `kind: procedure` block (lines 8-10) | Gate row `procedure 23/23 green`; totals 0 red; any divergent file → `known-failures.yaml` entry plus an open-packs issue (expected: none) |
| WP05 | Docs + changelog (PR-A) | WP04 | `docs/changelog/<date>-procedure-round-trip.md` (+ hub line appended); ADR-0037 or 0036 amendment line if the per-kind table needs one; glossary `definitions.yaml` term appended | `check_docs all`, markdownlint and cspell are green; the changelog states that the fingerprint/ETag changes once for packs with procedures; no HTTP change |

### PR-B — mission types

| WP | Title | Depends | Owns | Acceptance / proving tests |
| --- | --- | --- | --- | --- |
| WP06 | Mission content census + sanitized shapes (research) | — | `kitty-specs/.../research.md`, `data-model.md` | For each of the 7 shapes: key union across built-in/qa/cave-kitty/internal, the sanitized DSO shape, and the list of divergent files. Layout variants (built-in nested vs qa top-level vs cave-kitty `command-templates/`) mapped to globs. Checked by the architect. |
| WP07 | ADR: mission content forms | WP06 | `docs/adr/00NN-mission-type-content.md` (+ index line appended) | Decides: (a) prompt.md as an ADR-0039 companion via `prompt_template`; (b) the home for unreferenced `guidelines.md` and `missions/<type>/templates/**` (a new payload kind vs widening ADR-0040's bytes rule vs building the `text`/`markdown` form); (c) id scheme for the nested step paths. Status Proposed. |
| WP08 | Schemas + vocabulary | WP06, WP07 | `contracts/packs/schemas/{mission-type,mission,mission-runtime,expected-artifacts,governance-profile,action-index,mission-step}.schema.json`; file-kinds.yaml: append `files`/`payloads` rules, remove **our own** exclusions (lines 128-152, 163) | `VocabularyEqualityTest`, `PackFileKindTest` (no overlap, sample paths include qa/cave-kitty layouts); the table loads; every corpus file validates or is listed as divergent |
| WP09 | Mission domain model | WP06 | `packs/domain/Mission*.java`, `Action*`, `MissionStep*` | Unit tests per entity; no invented defaults; inert data only (#148 semantics are not taken) |
| WP10 | Storage bindings + companions | WP08, WP09 | DSO/Yaml/Binding per shape; companion wiring; appended lines in `KindBindings`/`YamlFormats` | Corpus tests per shape over every pack; a lossy-writer control per shape; companion byte-for-byte tests; a missing `prompt.md` is a named violation |
| WP11 | Divergent content → open-packs | WP08 (or WP10) | open-packs issues (filed by the operator, or by the agent only with operator OK); `known-failures.yaml` entries appended | One entry per file with exact `at`, `reason`, `issue: spec-kitty/spec-kitty-open-packs#N`; no tolerance code; the gate shows each as a known failure |
| WP12 | Gate flip: missions | WP10, WP11 | `expected-red.yaml`: remove `other:missions` (lines 11-13) only once no file remains in it; gate `exclusions.yaml` untouched unless STALE | Gate: every mission kind green or known-failure; 0 red; 0 `other:missions` |
| WP13 | Docs + changelog (PR-B) | WP12 | changelog + hub line, glossary terms, `docs/architecture/*`, `contracts/packs/README.md` | Docs checks green; fingerprint change stated; `CharterSourceFingerprint.yaml` prose deferral recorded as #245/#248 did |
| WP14 | Reconcile with main / #195 | all | rebase only | Append-only conflicts resolved by keeping both sides; gate totals re-checked after the rebase |

Parallelism:
- PR-A: WP01 ∥ WP06 ∥ WP09 (domain and research are disjoint).
- PR-B: WP07 ∥ WP09. WP11 can start as soon as WP08's validation lists the divergent files.

---

## 4. Findings and risks

- **[HIGH] contracts/packs/file-kinds.yaml:128-165 — the "append-only" rule for shared files conflicts with what PR-B has to do.** The mission files are under explicit *exclusions*, so PR-B must **delete** those exclusion entries, not just append. #195 deletes the adjacent `presets` (153) and `pack-manifest` (156) exclusions, so the hunks are textually adjacent. Recommendation: agree a ruling up front that "each mission removes only its own exclusion entries; appended rules go at the end of each section." Name the touched line ranges in both specs. Rebase-resolve by keeping both sides (#248 set the precedent of dropping exclusions).
- **[HIGH] Mission-template / guidelines content has no admitted content form.** file-kinds.yaml:23-31: a `bytes` rule is allowed only for `asset-payload`/`template`, and `markdown`/`text` fail at table load. About 80 files cannot be classified without an ADR-0040 amendment or a new form. Recommendation: make this an ADR WP (WP07) *before* the schema/vocabulary WP. The architect lens owns the decision. Do not let an implementer pick it inside a storage WP.
- **[HIGH] Size: mission types alone exceeds #253 (127 files).** Recommendation: two PRs (A/B), with B1/B2 as the escape hatch, decided at the end of WP06 against an explicit trigger (more than 5 shapes need a new seam, or more than ~120 non-dossier files).
- **[MEDIUM] service/packs/src/test/resources/whole-pack/known-failures.yaml:13-14 — self-expiry.** A listed file that round-trips fails `UNEXPECTEDLY_GREEN` (WholePackExpectation.java:282-286). Risk: an open-packs fix plus an `openPacksRef` bump by #195 or anyone else turns this mission's entries red on main. Recommendation: every entry cites its open-packs issue, and the changelog tells the next ref-bumper to remove them. Never pin entries "just in case": the `at` has to match exactly or the gate fails.
- **[MEDIUM] Layout divergence across packs (qa/cave-kitty).** qa uses top-level `mission-steps/` and `mission_types/` (38 files) and cave-kitty uses `missions/cave-dev/command-templates/`. The gate covers only built-in, so a built-in-shaped glob passes CI while qa/cave-kitty stay unclassified. Unclassified files are "left unclassified, not refused" (file-kinds.yaml:44-46). Recommendation: put qa and cave-kitty sample paths in `PackFileKindTest`, and run the corpus tests over all packs.
- **[MEDIUM] built-in/missions/built_in_step_contracts/__init__.py — a Python file inside pack content.** It is gate-excluded (exclusions.yaml:12), so it does not belong to this mission. Note it so no WP "fixes" it.
- **[MEDIUM] Gate flip correctness.** Remove `other:missions` only when the census shows zero files left in it. The gate enforces this anyway: a listed kind that has no red fails "remove it". Each WP removes only its own expected-red block and never edits a #195 entry (`other:presets`, `other:pack.md`, `other:pack-manifest.yaml`).
- **[MEDIUM] Shared hotspots** `KindBindings`, `YamlFormats`, `DomainRepositoryService`, ADR index, changelog hub, `site/.contextive/definitions.yaml`. These are append-last edits, but #253 shows that append order changes fingerprint digest order ("appended last so unchanged packs keep their digest"). Recommendation: append after #195's lines if #195 lands first, and re-run `KindBindingsTest` after the rebase.
- **[MEDIUM] ADR number collision with #195.** Both may want ADR-0041. Recommendation: the coordinator reserves numbers (e.g. #194 → 0041, #195 → 0042), or both pick the number at landing.
- **[LOW] HTTP contract.** No change. As in #245/#248, `CharterSourceFingerprint.yaml`/`ServiceInfo.yaml` prose goes stale: record that as Deferred, do not edit it. The fingerprint/ETag of built-in/qa/internal changes once and the changelog says so.
- **[LOW] Java 25 vs local 21.** Build locally with `./gradlew -Pcharter.javaVersion=21 verifyAll` (precedent #242). CI `service / verify` on Java 25 is the authority. `:contracts:checkContractCompatibility` refuses a shallow clone (#248). Do not use Java-25-only APIs. The style check runs in `verifyAll`.
- **[LOW] Scope creep vectors:**
  - #236 tags ("anti_pattern isn't green, so fix tags")
  - #154 promotion
  - #148 mission semantics (lifecycle/selection)
  - toolguide guide_path
  - #251 (validate mode)

  Recommendation: the spec names each as a non-goal.
- **Concession.** The procedure risk is genuinely low (41/41 files validate against the closed schema, and tactic is a direct template). My lens adds little there beyond sequencing it first.

---

## 5. Open questions for the operator (blocking only)

1. **Exclusion removal in file-kinds.yaml.** PR-B has to *delete* nine exclusion entries that #195's edits sit next to (lines 128-165). Is "each mission removes only its own exclusions" an accepted exception to the append-only rule for shared files? (Blocks WP08.)
2. **anti_pattern acceptance criterion.** It cannot flip green while #236 (node tags) is open. Is "proved round-trip except the tags pinned under #236" acceptable for #194's anti_pattern item? If not, does #236 come into scope? It is a DRG-wide change, so I recommend against. (Blocks the WP03 acceptance wording.)
3. **Toolguide half of #168.** "Absorbs #168" covers procedure only, and toolguide guide_path Markdown (`other:toolguides`, #186) stays out. Confirm, or name the owner. (Affects whether #168 can close with #194.)
4. **Filing open-packs issues.** May the mission agent file the open-packs issues for divergent content (WP11), or does the operator file them? The gate entries need real issue numbers before merge.

(The question of reconciling "as-is" with the sanitized shape is withdrawn: the operator ruled that the sanitized DSO + JSON Schema wins.)

---

## Verdict

**Proceed, as two PRs.**
- PR-A (procedure + anti_pattern proof; ~5 WPs, small, low risk) lands first.
- PR-B (mission types; ~9 WPs, L-XL, with a research WP and an ADR WP up front) follows, with a pre-agreed B1/B2 split trigger.

The real schedule risks are the ~80 non-YAML mission files (they need an ADR-0040-level decision) and the exclusion-deletion overlap with #195 in file-kinds.yaml. Settle both before tasks are finalized.
