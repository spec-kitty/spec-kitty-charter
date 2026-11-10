# Phase A: tracked issues and related-ticket discovery

Role: planner-priti (read-only discovery). Snapshot: 2026-11-09, `main` at `391ed51b`.
Sources: REST `gh api repos/.../issues/{n}` + `/comments` + `/timeline` + `/sub_issues` for charter; `gh api search/issues` for both repos.

**Access limit:** spec-kitty-open-packs is not attached to this session. The REST issue/comment endpoints and the MCP `issue_read` both answer 403/denied. I read open-packs #73 and #175 bodies from search results only. I could not read open-packs #73's 2 comments or any comments on #175. Note that open-packs **#175 is a PR**, not an issue: "refactor(spdd)!: move REASONS guidance into prompt fragments", open, Refs #73.

## 1. The four delivered issues: state and hierarchy

| # | Title (short) | State | Labels | Milestone | Parent | Sub-issues | Cross-referenced by |
|---|---|---|---|---|---|---|---|
| 424 | Align the service to ADR-0066 prompt-fragment contracts | open | none | none | none | 0 | op#175, #425, #428 |
| 428 | Step enhancements: store binding, guard/emit, layout, identity rules | open | enhancement, area:service, P2 | none | none | 0 | #425, #427, #429 |
| 429 | Effective reads: `action:` URNs filtered out of the skipped report | open | enhancement, area:service, P2 | none | none | 0 | #427 |
| 430 | Wire-token parity: NodeKind, CascadeKind, DiagnosticCode | open | enhancement, area:service, P2 | none | none | 0 | #425 |

None of the four has a parent, a milestone or sub-issue links. #424 has no labels at all. They are linked only by `Refs` in their bodies.
**Dependency order** (from the bodies):
- #428 item 1 (store binding) is a prerequisite for #424's compose/effective-prompt checks.
- #428 item 1 also makes #429 live: #429 is latent until `step_enhancement` is stored.
- #430 is independent.

**Suggested hanging point** (operator decision, not done here). ADR-0066 has no epic: #186 is closed, and #424 and the follow-ups sit outside any tree. Options:
- (a) Put all four under **#10** (parent #6 epic, milestone *4.x cutover*). That is where #427 and #426 belong. #429 fits there naturally.
- (b) Put #424/#428 under **#11** (org/project layers, `enhances` merges; ADR-0053 OQ2 ruling quoted there), and #429/#430 under #10, and #430 under **#6** next to #133.
- The sibling follow-up issue #426 also has no parent or milestone, so the landing follow-ups are consistently un-parented today.

## 2. Context issues read

| # | Title | State | Relation to this run | Disposition |
|---|---|---|---|---|
| 425 | feat(contracts): ADR-0066 prompt-fragment and slot contracts (PR) | merged 2026-11-09 | parent work: the contracts half. It reserves `prompt_slot_malformed`, `prompt_slot_not_empty`, `prompt_fragment_invalid` and `prompt_slot_missing` with `raised: false` until #424 | Refs. The service PR flips `raised` |
| 427 | feat: layer merge, in-force resolution, effective reads (PR B) (PR) | merged 2026-11-09 | source of #429; PR C (WP27–29) still to come | Refs |
| 426 | Effective reads: follow-ups from PR B pre-PR review | open | related. Shares `ClosureViewSession`. Item "Enum mapping by name" (`valueOf(other.name())`) is the same drift class as #430. Its "For PR C" section belongs to WP27–29 | **Refs**. Fold only the enum-mapping item if the operator approves; that is scope widening |
| 10 | Java charter (4/8): domain, merge, activation, cascade | open, P1, 4.x cutover, parent #6 | mission issue (PR C pending) | Refs, leave open (PR C remains) |
| 11 | Java charter (5/8): org and project layers | open, P1, 4.x cutover, parent #6 | mission issue. Records the ADR-0053 OQ2 ruling ("enhancing artifact lives beside the kind it adds to"), which #428 item 3 must reconcile | Refs, leave open |
| 103 | Cutover read R9: effective activation sets | open, P1, 4.0 GA, parent #94 | mission issue. 2026-11-04 comment: SPDD/REASONS blocks gate on this read (R17 input). 2026-11-06 comment lists ADR-0059 D2.2/D3/D4 follow-ups (skipped-report buckets) | Refs, leave open (graph/context reads are PR C) |
| 186 | Epic: whole-pack round trip | **closed** (22/22) | historical. The kind-modelling epic; step_enhancement binding (#428) follows its KindBinding pattern | leave alone (closed epic; do not reopen or parent to it) |
| 416 | Lookup performance: cache pack location listing | open, P2 | related only. Binding a new kind adds to `compositionFacts` reads | leave alone |
| 417 | Registration notices: carry non-activatable kinds | open, P2 | **related, same root cause as #429 item 1**: `ArtifactReference.kind` is `ActivatableKind`, so non-activatable kinds and steps cannot be named. If #429 widens `EnhancerWithoutTarget.targets` to a node reference, the same shape could serve #417 | **Refs** (a design note on the shared reference shape). Do not fold: it needs its own contract fragment and mapper change, so folding is scope widening |
| 418 | Pack registration: PR A pre-merge follow-ups | **closed** (completed via #423) | none | leave alone |
| 358 | Only the distribution pack may settle a sibling contest | open, P1, parent #145 | related. ADR-0053/#339 says two sibling enhancements of one step are refused unless the distribution pack settles them. That is the same rule applied to step enhancements | leave alone |
| op#73 | Split the built-in pack (open-packs) | open | consumer context. Open item 2: "where the SPDD reasons-blocks go" via ADR-0064 edges | leave alone (cross-repo; Refs optional) |
| op#175 | refactor(spdd)!: REASONS guidance into prompt fragments (open-packs **PR**) | open | **blocked-by** this run (#424 service half). It ships 5 step enhancements, `spdd:reasons-block` slots, `missions/<type>/enhancements/<id>.step-enhancement.yaml` layout, descriptor `schema_version: "1.0"` as the empty-slot gate, and `activated_step_enhancements` | Refs spec-kitty/spec-kitty-open-packs#175. Its layout choice is the one #428 item 3 must ratify or move |

## 3. Related tickets found by search

| # | Title | State | Relation | Disposition | Reason |
|---|---|---|---|---|---|
| 339 | Core: replace GateBinding with guard entries; step enhancements and add_guards | open, Post-cutover, parent #361 | related / **blocks** #339 (it depends on "step enhancements (work breakdown item 10)") | Refs (optional) | It defines the step-enhancement form (references, guards, emits; `enhances` edge; add-only). That is input to the #428 guard/emit shape decision. It is core-side, so leave the issue open |
| 361 | Epic: core adopts the pack-ecosystem design (C2–C11) | open, Post-cutover | grandparent of #339 | leave alone | core-side epic, not a home for service work |
| 111 | Cutover read R17: SPDD/REASONS blocks | **closed** | related precursor. Decision: prompt text comes with R5 (#99), and the gate comes with R9 (#103). It notes that core's `next` prompt builder does not gate the blocks | leave alone | closed; context for the #424 effective-prompt read |
| 99 | Cutover read R5: mission types and the action index | open, 4.0 GA, parent #94 | related. Per #111, the step prompt text (now with fragments) is served by the mission-type read | Refs (optional) / leave alone | #424's effective-prompt read is part of what R5 will serve, but this run does not decide R5 |
| 133 | Decide x-extensible-enum for DiagnosticCode before contract 1.0 | open, 4.x cutover, parent #6 | related to #430 (it was created when `pack_unknown_key` was added in #114) | Refs | The parity test and the extensible-enum decision touch the same enum. Leave it open; #430 does not decide extensibility |
| 112 | Cutover read R18: kind taxonomy facts | open, parent #94 | related to #430 (NodeKind as the kind table) | leave alone | decision issue, not a parity test |
| 337 | Core: read mission types from the `mission_types/{type}/` bundles | open, Post-cutover | related to #428 layout (op#73 renames `missions/` to `mission_types/`) | leave alone | the layout ruling should note the coming rename; the work is core-side |
| 408 | Cascade controls: depth, language filter, suggests at read | open | related only (skipped report / ADR-0059) | leave alone | different surface |
| 94 | Epic: cutover read inventory | open, 4.x cutover | grandparent of #103, #99 | leave alone | not a home for the four |
| 6 | Epic: Java charter service against an OpenAPI contract | open, 4.x cutover | parent of #10, #11, #133 | candidate parent for #430 (or via #10) | see §1 |

**Duplicates / superseding:** none found. No open or closed issue duplicates #424, #428, #429 or #430, and nothing supersedes them.

**Queries that returned nothing relevant beyond the issues above** (all `repo:spec-kitty/spec-kitty-charter is:issue …`):
- `WireTokenParity`: 0 results
- `prompt_slot`: 0 results
- `CascadeKind`: only #430
- `EnhancerWithoutTarget`: only #429
- `requiredNotInForce`: only #429
- `"effective prompt"`: only #424
- `ADR-0066`, `prompt_fragments`, `"prompt fragment"`: only #424 and #428
- `step_enhancement`: only #428, #429, #430
- `"step enhancement"`: those plus #339
- `ClosureViewSession`: #429, #426, and #410 (closed, unrelated cascade)
- `slot`: #424, #430, plus #260 and #418 (closed, unrelated)
- other queries run: `parity`, `NodeKind`, `DiagnosticCode`, `pack_unknown_key`, `activatable`, `"skipped report"`, `mission-step-prompt`, `"enhancements"`, `guard emit`, `ADR-0053 "open question 2"`, `"action context"`, `WP27`

**open-packs searches** (`repo:spec-kitty/spec-kitty-open-packs is:issue|is:pr …`):
- `"step enhancement"`, `step_enhancement`, `prompt_fragments`, `ADR-0066`, `slot`: **0 issues**
- `"prompt fragment"`: #73 only
- PR searches: #175 only (plus closed #166, #95, #133, which are unrelated)
- `mission-step-prompt`: content issues #176, #164, #163, #149 (prompt text). None concerns slots or fragments, so leave alone. #164 (implement/review prompts name charter sections) touches the same prompt files as the slot markers, so it is a possible merge-conflict risk for op#175 only.

## 4. Open PRs touching these surfaces

**None.** spec-kitty-charter has 0 open PRs (`GET /pulls?state=open` returned an empty list). The latest merges were #427 and #425 (2026-11-09), #422 (ADR-0066), #423 and #419.

## 5. ADR numbering

- **Highest ADR on `main`: 0066** (`docs/adr/0066-a-step-enhancement-may-contribute-prompt-fragments.md`, checked by local ls and the remote contents API).
- No open PRs, so no ADR is in flight. **The next free number is 0067**, if the #428 layout or guard/emit decision needs a new ADR rather than an annotation on ADR-0053 OQ2 or ADR-0066.

## 6. Recommended PR trailer for this run

```
Closes #424
Closes #428
Closes #429
Closes #430
Refs #425, #427, #426, #10, #11, #103, #133, #417, #339
Refs spec-kitty/spec-kitty-open-packs#175
```
Caveats:
- `Closes #428` only if all four checklist items land, including the guard/emit schema shape and the layout ruling. Otherwise use `Refs` and tick the boxes that land.
- `Closes #429` requires the contract change on `EnhancerWithoutTarget.targets`, or a recorded divergence for item 2.

**Scope-widening candidates** (operator approval needed; none recommended by default):
- #426 "enum mapping by name" item (fits with #430)
- #417 (shares the widened reference shape with #429)
