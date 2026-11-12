---
title: Re-scope proposals, triage 2026-11-02
description: "Proposed new scopes for the 66 issues the 2026-11-02 triage marked for re-scoping, for the operator to approve, amend or reject before any issue body changes."
doc_status: draft
updated: 2026-11-04T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/triage-2026-11-02
  - plans/milestones
  - plans/pack-ecosystem-work-breakdown
  - adr/0055-cutover-sequencing-the-java-service-first
audience:
  - profile: governance-lead
    guidance_text: Read each proposal, answer its question if it has one, and approve, amend or reject it.
  - profile: charter-service-contributor
    guidance_text: Read the proposal for an issue before you pick it up; until it is approved the issue body is not current.
---

# Re-scope proposals, triage 2026-11-02

The [2026-11-02 triage](/plans/triage-2026-11-02/) found 66 open issues whose body no longer matches the ADRs or
what has shipped. On 2026-11-03 the operator chose to review proposed re-scopes before any issue body changes. This
page holds one proposal per issue. Unless [Applied since](#applied-since) says otherwise, nothing here is applied: an
issue body changes only after its proposal is approved.

## How to review

- Each proposal says what the issue asks for today, what is wrong with it, and what it should ask for instead, with
  the ADR, PR or file that drives the change.
- **Kind** is `factual` when the edit only corrects facts, terms, citations or delivered items, and `scope-change`
  when it changes what the issue asks for.
- **Recommendation** is one of "re-scope", "re-scope + retitle" or "close instead".
- A proposal with a **Question** needs an operator answer before its re-scope can be finished.
- Approve, amend or reject per issue or per group; approved proposals are then written into the issue bodies.

Drafted by four planner agents on 2026-11-03 against `origin/main` at `d395470` (ADR-0055), each loaded with the
planner profile and the charter's plan-action context. Claims that something is delivered cite a file on `main`, a
merged PR or a closed issue.

## Counts

| | Count |
|---|---|
| Proposals | 66 |
| `scope-change` | 57 |
| `factual` | 9 |
| re-scope + retitle | 44 |
| re-scope | 20 |
| close instead | 2 |
| with an open question | 29 |

## Decisions taken on 2026-11-03

Applied on the tracker before this page was written:

- **Priority labels:** `priority:P0` to `priority:P3` created; every open issue carries one (41 P1, 77 P2, 42 P3 at
  the time of writing).
- **Merges:** #167 into #240, #46 into #128, #144 into #58 and #323 into #291. Each target's body gained a "Folded
  in from" section, and each source closed as a duplicate.
- **Core follow-ups:** #333 to #342 moved under the new epic #361 (core adopts the pack-ecosystem design). #332 and
  #343 stay under #69.
- **#13** moved from 4.0 GA to the 4.x cutover (ADR-0055 D1); the milestone page now says so.
- **Still open:** the parents of #182, #184 and #185 (the triage proposed #94, #6 and #6). They stay under #142
  until the operator decides.

## Applied since

On 2026-11-04 the operator approved this cleanup, after each item was checked:

- **#210** is closed as covered. Its closing comment names what stays uncovered: a neighboring-path 404 scenario and a
  general check for undeclared headers.
- **#333 to #342:** the `Parent: #69` line in each body now says `Parent: #361`.
- **#111** is decided: a contract operation with no separate shape. The reason differs from the proposal's; see the
  correction at [#111](#issue-111).

On 2026-11-12 the re-scopes of #14 to #23, #42, #61, #69, #82, #152, #154, #157 to #159, #163, #164 and #205 were
written into the issue bodies; see [Re-scopes applied, 2026-11-12](/plans/triage-2026-11-12-rescopes-applied/).

## Summary

| # | Title | Group | Kind | Recommendation | Question |
|---|---|---|---|---|---|
| [142](#issue-142) | Epic: Charter Pack ecosystem: pack structure, dependencies and … | A | `scope-change` | re-scope | yes |
| [145](#issue-145) | Epic: Split built-in charter content into packs + compound packs … | A | `scope-change` | re-scope + retitle | yes |
| [148](#issue-148) | Promote mission types + step contracts to full charter artifact kinds | A | `scope-change` | re-scope + retitle | yes |
| [149](#issue-149) | Standalone pack validator as a lightweight CI entry point | A | `scope-change` | re-scope + retitle | yes |
| [154](#issue-154) | Promote the inline `anti_patterns` corpus to first-class `DRG` nodes | A | `scope-change` | re-scope + retitle |  |
| [157](#issue-157) | Packs declare executable `paths/requirements` + ship `SBOMs` for … | A | `scope-change` | re-scope + retitle | yes |
| [160](#issue-160) | Built-in pack policy file and the replaceable-builtins deprecation … | A | `scope-change` | re-scope + retitle | yes |
| [164](#issue-164) | Mission-type requirement-kind declaration: schema, ingestion and … | A | `scope-change` | re-scope + retitle |  |
| [178](#issue-178) | Align artifact schema field names across kinds (`name`/`title`, … | A | `scope-change` | re-scope | yes |
| [206](#issue-206) | Migrate `required_<plural>` lists to presets with per-item must-have … | A | `factual` | re-scope | yes |
| [36](#issue-36) | Follow-ups from the charter-offering bootstrap review (#35) | A | `factual` | re-scope | yes |
| [136](#issue-136) | Ask core to carry the pack terms in its glossary | A | `scope-change` | re-scope + retitle |  |
| [203](#issue-203) | Mirror the red-main procedure-to-directive move from open-packs#74 | A | `factual` | re-scope + retitle |  |
| [335](#issue-335) | Core: rename `glossary_pack` to glossary in activation keys and tokens, … | A | `scope-change` | re-scope |  |
| [338](#issue-338) | Core: load the work-package lane machine from the declared lifecycle | A | `scope-change` | re-scope |  |
| [342](#issue-342) | Core: remove the software-dev special-casing from the mission runtime | A | `factual` | re-scope |  |
| [42](#issue-42) | Mission-step profile-role resolution is a hardcoded central table in … | A | `scope-change` | re-scope + retitle | yes |
| [52](#issue-52) | Closure checker and YAML adapter follow-ups: array declared types, … | A | `scope-change` | re-scope + retitle |  |
| [94](#issue-94) | Epic: Cutover read inventory: every read core makes of the offering, … | B | `scope-change` | re-scope |  |
| [96](#issue-96) | Cutover read R2: Artifact bodies: decide and shape | B | `scope-change` | re-scope + retitle |  |
| [97](#issue-97) | Cutover read R3: Merged `DRG` and graph queries: decide and shape | B | `scope-change` | re-scope |  |
| [98](#issue-98) | Cutover read R4: Pack listing and presets: decide and shape | B | `scope-change` | re-scope |  |
| [99](#issue-99) | Cutover read R5: Mission types and the action index: decide and shape | B | `scope-change` | re-scope + retitle |  |
| [100](#issue-100) | Cutover read R6: Mission step contracts and gate bindings: decide and … | B | `scope-change` | re-scope + retitle |  |
| [103](#issue-103) | Cutover read R9: Active charter and effective activation sets: decide … | B | `scope-change` | re-scope + retitle | yes |
| [105](#issue-105) | Cutover read R11: Assets: decide and shape | B | `scope-change` | re-scope |  |
| [106](#issue-106) | Cutover read R12: Glossary packs: decide and shape | B | `scope-change` | re-scope + retitle | yes |
| [109](#issue-109) | Cutover read R15: Override and pack policy: decide and shape | B | `scope-change` | re-scope | yes |
| [110](#issue-110) | Cutover read R16: Built-in pin, manifest freshness and snapshot … | B | `scope-change` | re-scope | yes |
| [111](#issue-111) | Cutover read R17: `SPDD/REASONS` blocks: decide and shape | B | `scope-change` | close instead | answered |
| [112](#issue-112) | Cutover read R18: Kind taxonomy facts: decide and shape | B | `scope-change` | re-scope |  |
| [70](#issue-70) | Launcher and service lifecycle: core starts and stops the charter … | B | `scope-change` | re-scope + retitle | yes |
| [71](#issue-71) | Cross-OS packaging of the charter service | B | `scope-change` | re-scope | yes |
| [74](#issue-74) | Java authoritative reads (strangler stages 3 and 4, shipped with #76) | B | `scope-change` | re-scope + retitle | yes |
| [16](#issue-16) | Epic: Creeds and Values (FoundationalValues and the charter creed) | C | `factual` | re-scope |  |
| [17](#issue-17) | Creeds and Values (1/7): Re-baseline the design against 4.x and this … | C | `scope-change` | re-scope + retitle |  |
| [23](#issue-23) | Creeds and Values (7/7): N-generic first-order matrix and ranking … | C | `scope-change` | re-scope | yes |
| [29](#issue-29) | Epic: Behavior-first charter-offering overhaul (bootstrap, P1-P3) | C | `scope-change` | re-scope + retitle | yes |
| [30](#issue-30) | P1: Author the offering's `boundary/role` scenarios beyond the three … | C | `scope-change` | re-scope + retitle |  |
| [31](#issue-31) | P1: Wire the in-repo Layer-B acceptance harness (Cucumber-JVM) … | C | `scope-change` | re-scope + retitle |  |
| [33](#issue-33) | P1: Render the BDD scenarios and PROMISES map in the docsite | C | `scope-change` | re-scope + retitle |  |
| [55](#issue-55) | Epic: Charter Pack governance: override, extend and enforce policy … | C | `factual` | re-scope + retitle |  |
| [56](#issue-56) | Epic: Persistent deactivation: deactivated charter elements stay gone | C | `scope-change` | re-scope |  |
| [57](#issue-57) | ADR and schema: a mutability policy per artefact, declared by the … | C | `scope-change` | re-scope + retitle | yes |
| [58](#issue-58) | Policy loader and enforcement in `merge_three_layers` | C | `scope-change` | re-scope + retitle |  |
| [59](#issue-59) | Cross-tier `charter pack validate`: a lower-tier pack that breaks … | C | `scope-change` | re-scope + retitle |  |
| [60](#issue-60) | Reconcile `required_*` with policy: `enforced` means required and … | C | `scope-change` | re-scope + retitle | yes |
| [61](#issue-61) | Docs: amend the "project always wins" statements | C | `scope-change` | re-scope + retitle |  |
| [65](#issue-65) | Projection and resolution honour exclusions for every origin, … | C | `scope-change` | re-scope + retitle | yes |
| [67](#issue-67) | Deactivation interacts with `enforced` and `required_*` | C | `scope-change` | re-scope + retitle |  |
| [77](#issue-77) | Epic: `impacts` vocabulary for the `DRG` (Foundational Values and … | C | `scope-change` | re-scope + retitle | yes |
| [79](#issue-79) | B1: add the `impacts` relation to the `DRG` vocabulary | C | `scope-change` | re-scope + retitle |  |
| [80](#issue-80) | B2: migrate `in_tension_with` edges and retire the extractor | C | `scope-change` | re-scope + retitle | yes |
| [82](#issue-82) | Frozen, stamped projection as the only read path for value data | C | `scope-change` | re-scope | yes |
| [15](#issue-15) | Nightly acceptance tests using BDD / Gherkin + Allure | D | `scope-change` | re-scope + retitle | yes |
| [140](#issue-140) | ArchUnit: slice-coverage meta-rule so a new slice cannot land ungated | D | `scope-change` | re-scope + retitle |  |
| [185](#issue-185) | Pack commit swap: readers in the swap window, staging leftovers, … | D | `scope-change` | re-scope + retitle |  |
| [196](#issue-196) | Protect `contract-charter-v*` tags with a ruleset before the first … | D | `scope-change` | re-scope + retitle |  |
| [205](#issue-205) | Re-render class-diagram and c4-components PlantUML for the ADR-0035 … | D | `scope-change` | re-scope + retitle |  |
| [210](#issue-210) | Contract scenarios for `GET /openapi.yaml` | D | `factual` | close instead | answered |
| [211](#issue-211) | Add IntelliJ HTTP Client examples … | D | `scope-change` | re-scope + retitle | yes |
| [220](#issue-220) | Release: attach Java client zip + .sha256 to the contract release and … | D | `scope-change` | re-scope + retitle |  |
| [226](#issue-226) | Hosted service configuration (profiles, validated properties, … | D | `factual` | re-scope + retitle | yes |
| [231](#issue-231) | Profile guard tests: loopback invariant locally, missing-secret … | D | `scope-change` | re-scope + retitle |  |
| [243](#issue-243) | Report three spec-kitty CLI (4.0.0rc8) charter-synthesis defects … | D | `factual` | re-scope | yes |
| [254](#issue-254) | PackPolicy: report an org-fragment `body_path` that escapes the pack … | D | `scope-change` | re-scope + retitle |  |

## Open questions

The answers these need, by issue. Each is repeated under its proposal.

- **#142:** Do you want one task for the whole ADR-0053 `d23/OQ3` kebab breaking set, with #178's field rename folded into it, or two tasks?
- **#145:** Track the resolver (item 3) by widening #146 to items 2 and 3 (the mission covers both), or file a separate resolver task under #145?
- **#148:** For item 9: widen #164 to the whole mission-type schema, or keep #164 as the requirement-kinds slice and file a separate item-9 schema issue?
- **#149:** Ship it as a mode of the service binary (ADR-0046 native packaging) or as a separate artifact? If you do not want a separately shipped validator, close it into #38 instead.
- **#157:** The interim mark (a trusted-packs field in the activation file) has no tracker and no schema field yet. Track it under #291 (activation document) or in #157? And should #157 (and #143) move from Backlog to Post-cutover, or is the interim mark enough for the first release?
- **#160:** If the policy file is needed before the split lands, ship one interim file on today's single `built-in` and split it with P1, or wait for P1?
- **#178:** Move #178 into the 0.x window (4.x cutover, before #128 mints 1.0.0), or keep Post-cutover and accept a major version? Also: does the glossary context keep `name` (ADR-0049 d2), or join the rename?
- **#206:** ADR-0055 D3 lists `required_<kind>` among the rules the design-derived packs exercise. Confirm that means the preset must-have set, so refusing org-charter `required_*` still stands, and whether #206 should move from 4.0 GA into the 4.x cutover so #11 (org layer) is built without `required_*`.
- **#36:** #136 also adds pack terms to definitions.yaml. Merge #136 into #36 item 2 (one glossary pass), or keep both?
- **#42:** ADR-0032's open questions (ties; how a role pin combines with the selection order) must be ruled before this can be built in full. Rule them now, or build without pins and ties first? And parent it under #142, #102's epic #94, or leave it without a parent?
- **#103:** Does #103 build the effective reads, or does it only fix their semantics while #291 (or a new issue) builds them? service-contract.md says #103 builds them.
- **#106:** Once the item 4 issue ('glossary contexts and terms') is filed, close #106 into it, or keep #106 for the contract record?
- **#109:** Is the mutability-policy read out of the cutover (needs an ADR-0010 amendment), given #55 and its children are Post-cutover?
- **#110:** Are the pin and snapshot reads exempt pack tooling (like compile, ADR-0010 d3), or fields of the R4 pack listing?
- **#111 (answered 2026-11-04, for a different reason):** Close R17 as decided ('covered by R2 + R9'), with the decision recorded in #94 and service-contract.md?
- **#70:** Does the hosted-profile client path (configured endpoint and login) belong in #70, or in #127/#233 with #70 kept to the local launcher?
- **#71:** Is a `service-v*` pre-release enough to close #71, or does it wait for a final release (which needs a released contract, ADR-0046 d3, and 0.1.0 is held)?
- **#74:** What gates each switch (ADR-0055 open question 1)? #74 cannot state its per-operation acceptance until it is ruled.
- **#23:** D-6 (asymmetric matrix pair) is still open in core's design. Rule it here, now that the value layer is this repository's, or keep waiting on core?
- **#29:** Milestone for #29, #30, #31 and #33: milestones.md keeps them in 4.x cutover as 'the conformance net'. Under ADR-0055 D3 the contract scenarios and design-derived packs gate the service. Move all four to Post-cutover, or keep #31 on the cutover path because binding the promises supports the D3 acceptance?
- **#57:** ADR-0030 d7 and ADR-0032 d7 leave the shape of removal entries, role locks and role pins to later records. Should #57 decide those shapes in its schema PR, or should an ADR record them first?
- **#60:** #60's remainder is small. Keep it as its own issue, or fold 'locked implies required' into #58 and close #60?
- **#65:** ADR-0055 D1 puts the effective reads in the cutover. Move #65 to 4.x cutover as its own issue, or fold it into #10 / #103 and close it?
- **#77:** ADR-0045 is still Proposed. Accept it before #77, #79 and #80 are re-scoped?
- **#80:** Does the rename ride the named-packs split re-pin, or ship on its own? And confirm no dual reader (ADR-0053 OQ3 by analogy) despite the operator note.
- **#82:** Keep the anti-pattern sign-flip rule (vector = minus the rejecting artifact's), now that ADR-0052 makes anti-patterns authored artifacts and they could carry their own `impacts` list?
- **#15:** Must the nightly gating pass run black-box over loopback against the started jar (scripts/`release/smoke-service.sh` already starts one), or is the existing in-process `MockMvc` binding enough for the evidence trail?
- **#210 (answered 2026-11-04: closed):** Close #210 as covered, or keep it for one neighboring-path 404 scenario (and, if wanted, a check that no undeclared header appears)?
- **#211:** Which directory for the .http files: `contracts/charter/http/` (proposed) or another?
- **#226:** ADR-0041 D1 keeps 'hosted' as the deployment-option name. Retitle the epic to 'Containerized service configuration' (proposed), or keep 'Hosted' in the title and fix only the body and #231/#232?
- **#243:** Is `spec-kitty/spec-kitty`#6768 the same defect as defect 1 (additive-only check on synthesize again of an activated `PROJECT_` directive)? I could not read it.

## Group A: Pack-ecosystem rulings (ADR-0048 to ADR-0055)

### Issue #142

**Epic: Charter Pack ecosystem: pack structure, dependencies and artifact kinds**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Lists children recreated from core (#2467, #5228, #3358, #2468, #2472, #2847, #3418, #2597, #2656, #5960, #5956, #5364, #2471). Done when every child is decided and closed, and each schema change is in an additive contract set.
- **Problem:** The scope list predates ADR-0048..0055: it still names the `gate` kind (replaced by guards, ADR-0054), the gate-binding schema (#155 closed into #156), the mission-instance addendum (#153 closed, ADR-0053 d24) and the mission.yaml tier (#161 closed). The AC says 'additive contract set', but ADR-0048 Consequences, ADR-0053 d23 and ADR-0054 Follow-ups make several changes breaking on 0.x (ADR-0022).
- **Proposed scope:**
  - Scope = work-breakdown items 2-14 (docs/`plans/pack-ecosystem-work-breakdown.md`), one tracker per row: #145/#146 (2, 3; 4.x cutover per ADR-0055 D2), #154 (5), #148 (9-11), #164 (9), #163 (12), #355 (gate for 13), #349, #350; items 1, 6, 8 and 14 marked done (ADR-0053, #353, #354).
  - File the four still-not yet filed children: glossary contexts and terms (item 4), event registry kind (item 7), step enhancements and overrides (item 10, under #148), contract sets per kind (item 13). Do not file 'lifecycle kind' (item 8): delivered by #354, #156 closed.
  - File one task for the ADR-0053 d23 + OQ3 breaking 0.x set (kebab `{kind}` path token and kebab URN kind tokens), and link #178 to it.
  - Note that the `<pack>::` reference grammar and closure check (ADR-0053 d7) are FR-013..FR-017 of the pack-composition mission on #145, so they need no separate issue.
  - AC: each schema change is in the canonical schemas and in a contract set, additive or with a `### Breaking` entry on 0.x (ADR-0022), before 1.0.0 (#128).
  - Link open-packs P1-P6 issues once filed, and #361 for core adoption (C2-C11).
- **Drop:**
  - The recreated-from-core child list (#2467 ... #2471) and its core numbering note.
  - 'gate as a kind', 'gate-binding schema', 'mission-instance governance addendum', '`mission.yaml/mission-runtime.yaml` tier'.
  - Children #182, #184, #185 (storage hardening, not pack-ecosystem design): re-parent #182 to #94 and #184, #185 to #6 per the triage table.
- **Evidence:** ADR-0053 d7, d23, OQ3, d24; ADR-0054 Follow-ups; ADR-0055 D2; work breakdown items 1-14 and 'Proposed issues (not filed)' 1-5; #354 closed #156 (guard + lifecycle kinds); #153, #155, #161 closed; branch issue-145-pack-composition spec.md FR-013..FR-017 (pack-qualified refs, closure check); #128 body: 1.0.0 needs 'the last two minors held no ### Breaking'
- **Question:** Do you want one task for the whole ADR-0053 `d23/OQ3` kebab breaking set, with #178's field rename folded into it, or two tasks?

### Issue #145

**Epic: Split built-in charter content into packs + compound packs (pack keystone)**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Epic: Pack composition: the `depends_on` resolver and the named-packs split (ADR-0048)
- **Today:** Core's #2467 text: pack identity, `depends_on` DAG with cycle detection, compound packs, and splitting `packs/built-in/` into named packs; status as of core 2026-09-27 plus a 2026-11-02 rulings note.
- **Problem:** The split layout is decided (ADR-0048 d11-17; ADR-0053 d1-3, d9: foundation + writing in `built-in`, opt-in packs are `org`, id `foundation`), and the ruling note still says 'semver range' where ADR-0053 d4 rules PEP 440. The resolver (item 3, size L) has no task of its own, and ADR-0055 D2 puts items 2 and 3 on the 4.x cutover path before the layer merge (#10, #11).
- **Proposed scope:**
  - Add an 'Updated 2026-11-03' banner: decided by ADR-0048 and ADR-0053 d1-10; sequenced by ADR-0055 D2 (before #10 and #11).
  - Charter children: #146 (item 2, descriptor checks: PEP 440 range, ULID ids, `pack_level` required with `depends_on`) and the resolver (item 3: ordering, cycles, ranges, dedupe by `pack_id`, sibling refusal, single-tree shim), delivered by the pack-composition mission on branch issue-145-pack-composition (FR-001..FR-023).
  - List the deferred mission follow-ups: #358 (child), #357 (under #10), #359 and #360 (under #291).
  - Open-packs P1 (named-packs split; depends on charter item 3) needs an issue in spec-kitty-open-packs, linked here.
  - Done when: the resolver and descriptor checks are on main, the design-derived fixture packs resolve as ADR-0048 says (ADR-0055 D3), and P1 has landed and been re-pinned.
- **Drop:**
  - The 'Original issue text' status block (core paths `org_pack_config.py`, `pack_descriptor.py`), kept only as a link to core #2467.
  - Core #3511 and #3518 as sub-issues: they are core integration work for ADR-0055 phase 2; keep them as references or move them under #361.
- **Evidence:** ADR-0048 d1-18; ADR-0053 d1-10; ADR-0055 D2, D3; work breakdown items 2, 3 and open-packs P1; PR #352 (closed unmerged 2026-11-02; branch issue-145-pack-composition carries 3c35227 'resolve one `depends_on` closure'); #357-#360 bodies ('deferred by operator ruling 2026-11-03'); #147 closed (folded into #146, ADR-0048 d5)
- **Question:** Track the resolver (item 3) by widening #146 to items 2 and 3 (the mission covers both), or file a separate resolver task under #145?

### Issue #148

**Promote mission types + step contracts to full charter artifact kinds**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Epic: Mission types as declared state machines (ADR-0050, work-breakdown items 9-11)
- **Today:** Core's #2468 text: promote `mission-type` to a full `ArtifactKind` in core's Python, retire `MissionTypeNotAnArtifactKind`, and decide nested vs flat layout for `packs/built-in/missions/mission_types/`.
- **Problem:** In the service `ArtifactKind.MISSION_TYPE` already exists (ADR-0044; ArtifactKind.java:43), the core promotion is now #336/#337 under #361, and the nested-vs-flat fork is settled by the bundle layout (ADR-0050 d4, ADR-0048 d16). What is left is items 9-11, about three weeks of work on one issue.
- **Proposed scope:**
  - Make it a functional epic (type:epic) for items 9, 10 and 11.
  - Item 9 child: mission-type schema with `transitions` (event key `event`, terminal `end`), guard entries `{ref, config?, blocking?}`, action points with `emit` and asset actions behind trust (ADR-0053 d17), `submachine` (ADR-0053 d19), requirement kinds (#164), `term:` links. Needs items 4 and 7; item 8 is done.
  - Item 10 child (to file): step enhancements as an additions artifact plus a CRG `enhances` edge, add-only (ADR-0053 d21-22, OQ2 ruled 2026-11-03), and `overrides` with the drift warning (ADR-0050 d24).
  - Item 11 child: mission type as an ordinary activatable kind (presets, deny list), dropping the preset's separate mission-type slot (ADR-0050 d6; ADR-0042 note).
  - Done when the three children are closed and the mission-type read serves the declared machine.
- **Drop:**
  - The core 'Design' and 'Surfaces' sections (`artifact_kinds.py`, `pack_paths.py`, `org_pack_loader.py`, `activation_engine.py`): now #336 and #337.
  - The nested-vs-flat sub-decision (settled by ADR-0050 d4).
  - 'Depends on: #145': item 10 depends on item 3; items 9 and 11 do not.
- **Evidence:** ADR-0050 d3-11, d15-18, d22-26; ADR-0053 d15-25, OQ2; `service/kernel/.../ArtifactKind.java:43` `MISSION_TYPE`; `contracts/packs/schemas/mission-type.schema.json` has no `transitions/submachine` yet; #336, #337 under #361; #155 closed into #156; #354
- **Question:** For item 9: widen #164 to the whole mission-type schema, or keep #164 as the requirement-kinds slice and file a separate item-9 schema issue?

### Issue #149

**Standalone pack validator as a lightweight CI entry point**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Standalone pack-validate entry point for CI, with the `depends_on` closure check
- **Today:** Core's #2471: extract `validate_pack()` and `DoctrineHealthReport` from `specify_cli` into a dependency-light Python package and ship `doctrine-validate <pack-dir>`.
- **Problem:** The Python extraction is core's and is stale here. The service has validate mode (#115 closed; CharterWiring.java:69) but no entry point a pack author can run on a directory. Validate mode also skips the guard, lifecycle and mission-template closures (#355) and does not yet run the `depends_on` closure (ADR-0048 d2, ADR-0053 d7).
- **Proposed scope:**
  - A command that runs validate mode on one pack directory (or a set) and exits non-zero on any blocking finding, with findings as text and JSON.
  - It runs the same checks as the load, including the closures of #355 and the `depends_on` / `<pack>::` closure check of the pack-composition mission.
  - It runs without a configured project or a running daemon.
  - #38 (CI guard on vendored packs) uses it.
  - Tests: a planted pack with a missing dependency, a dangling cross-pack reference, and a guard naming a missing asset each fail.
- **Drop:**
  - The Python extraction, `pack_validator.py`, `_doctrine_health.py` and the shared-package-boundary risk.
- **Evidence:** #115 closed (validate mode); CharterWiring.java:69; #355 (validate mode skips closures); ADR-0048 d2, ADR-0053 d7; #38 open
- **Question:** Ship it as a mode of the service binary (ADR-0046 native packaging) or as a separate artifact? If you do not want a separately shipped validator, close it into #38 instead.

### Issue #154

**Promote the inline `anti_patterns` corpus to first-class `DRG` nodes**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** The `anti_pattern` kind: its own file, the rejects list, and dropping inline `anti_patterns` (ADR-0052)
- **Today:** Core's #2847: keep the inline `anti_patterns` blocks as source, teach core's extractor to mint thin `anti_pattern` nodes and `rejects` edges, add a severity tag, and curate about 131 entries.
- **Problem:** ADR-0052 d8-16 reverses 'retain, don't migrate': an anti-pattern is its own file and inline blocks are dropped. ADR-0053 d30-32 make `bad_example` required, refuse fragment-only nodes, and drop the default severity. No `anti-pattern.schema.json` exists, and procedure and styleguide schemas still carry `anti_patterns`.
- **Proposed scope:**
  - `anti_pattern` schema at `anti_patterns/<id>.anti-pattern.yaml`: `schema_version`, `id`, `title`, `description`, `severity` (smell\|severe\|breaking, required), `bad_example` (required), `good_example` (ADR-0052 d8, ADR-0053 d30).
  - A `rejects` list `{type: anti_pattern, id, reason}` on every guidance kind, minting `Relation.rejects`; `references` entries of `type: anti_pattern` migrate into it (ADR-0052 d9).
  - Procedure and styleguide schemas drop `anti_patterns` in the same contract-family change (ADR-0052 d16).
  - After migration, an `anti_pattern` node declared only in a graph fragment is refused (ADR-0053 d31).
  - Migration runs through the service's writer, then a re-pin. Curation (dedupe, severity, bad examples) is open-packs P4, linked here.
- **Drop:**
  - The 'converged approach' (retain inline, extractor-mint, severity tag on inline blocks).
  - Core paths (`extractor.py`, `hand_authored_overlay.py`, golden counts) and the 131-entry ground truth.
  - The default severity `smell`.
- **Evidence:** ADR-0052 d8-16; ADR-0053 d30-32; `contracts/packs/schemas`: no anti-pattern.schema.json; procedure and styleguide still have `anti_patterns`; work breakdown item 5 and open-packs P4; #343 waits on severity

### Issue #157

**Packs declare executable `paths/requirements` + ship `SBOMs` for code-bearing assets**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Trust policy for executable pack assets (guards and actions), and `SBOMs`
- **Today:** Core's #2542: a pack declares its executable entrypoints and runtime requirements, and ships an `SBOM`, so an operator can judge supply-chain risk before activation.
- **Problem:** Entrypoints and runtimes are now the asset `executable` block (ADR-0054 d5), delivered by #354. ADR-0053 d17 and ADR-0054 d7 name #157 as the trust policy that asset-backed guards and actions must pass. The interim mark is ruled: the project's activation file names the trusted packs (ADR-0054 OQ3), and #157 may widen the policy, not move the mark.
- **Proposed scope:**
  - The trust policy: what makes a pack's executable asset trusted beyond the interim mark (for example provenance, the pack fingerprint of ADR-0054 d11, signatures from #158/#159).
  - A failed trust check is an indeterminate result for a guard (ADR-0054 d7) and a refusal for an action asset (ADR-0053 d17).
  - An `SBOM` per pack that ships an asset with an `executable` block; format chosen, location in the pack, and a load check.
  - Activation shows the `SBOM` and runtimes before a pack is trusted.
  - Core's trust baseline (core #2540) stays a reference, not a dependency, under ADR-0055.
- **Drop:**
  - Declaring entrypoints and runtime requirements (delivered as `executable`, ADR-0054 d5, #354).
  - 'Depends on core trust baseline #2540'.
- **Evidence:** ADR-0054 d5, d7, d11, OQ3 (ruled 2026-11-02); ADR-0053 d17; #354; asset.schema.json `Executable`; #340 (guard runner) relies on trust; #40 overlaps on runtime requirements
- **Question:** The interim mark (a trusted-packs field in the activation file) has no tracker and no schema field yet. Track it under #291 (activation document) or in #157? And should #157 (and #143) move from Backlog to Post-cutover, or is the interim mark enough for the first release?

### Issue #160

**Built-in pack policy file and the replaceable-builtins deprecation (ADR-0012 D5)**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Member pack policy files and the replaceable-builtins deprecation (ADR-0012 D5)
- **Today:** Specify one policy file for the built-in pack (kind defaults `extendable`, every URN sanctioned by a released `replaceable-builtins.yaml` listed `open`), the one-release deprecation rule, and the amendment text for core ADR 2026-10-05-3.
- **Problem:** After the split `built-in` is a distribution pack with no artifacts of its own (ADR-0048 d12); its artifacts are owned by its members, `foundation` and `writing` (ADR-0053 d3), and some move to opt-in packs. ADR-0012 d8 says the owner is the pack that introduced the artifact, so the `open` URNs belong in each owning member's policy file.
- **Proposed scope:**
  - One policy file per owning member (`foundation`, `writing`), kind defaults `extendable`.
  - Each URN sanctioned by a released `replaceable-builtins.yaml` is listed `open` in the policy file of the pack that owns it after the census split (ADR-0053 d2), including opt-in packs that now own it.
  - Each member with a policy file declares `requires_core` (ADR-0012 d4).
  - The one-release deprecation rule stays as written (redundant, conflicting naming the owning pack, ignored after the release).
  - Authored in spec-kitty-open-packs against #57's schema, with the split (open-packs P1).
- **Drop:**
  - 'a built-in policy file' as one file on `built-in`.
- **Evidence:** ADR-0012 d2-5, d8; ADR-0048 d12-13; ADR-0053 d2-3; #181 is the runtime wiring, not a duplicate; work breakdown open-packs P1
- **Question:** If the policy file is needed before the split lands, ship one interim file on today's single `built-in` and split it with P1, or wait for P1?

### Issue #164

**Mission-type requirement-kind declaration: schema, ingestion and gate-config validation**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Mission-type requirement kinds: the declaration in the mission-type schema
- **Today:** The charter half of core #5956: a kind declaration file beside the mission-type spec (D1/Q5), whole-file override with drift (D2), the full-set rule (D3), and validation of gate-binding config (Q3).
- **Problem:** ADR-0053 d24 and ADR-0050 d26 put the requirement kinds into the mission-type schema (item 9), not a separate file. Q3 is now the guard `config_schema`, delivered by #354 (guard.schema.json:80). D2 is the `overrides` rule of item 10 (ADR-0050 d24). `D1/Q5` glossary links are `term:` URNs (ADR-0049 d4).
- **Proposed scope:**
  - A requirement-kinds field in `<type>.mission-type.yaml` (bundle layout, ADR-0050 d4), including `must_map_to_wp`.
  - D3: a declared list is the full set; `FR/NFR/C/SC` are the default only when the field is absent.
  - Kind terms link to glossary terms with `term:<context>/<term>` URNs.
  - Reference example: the research mission type's kinds (DR, AR, QR, SC) in the fixture packs (ADR-0055 D3).
  - Core #5956 can read the kinds from the mission-type read without hardcoding a type.
- **Drop:**
  - Q3 gate-binding config validation (delivered as guard `config_schema`, #354).
  - D2 whole-file override and drift (item 10, under #148).
  - The separate `<name>.mission.yaml` declaration file.
  - 'gate' in the title.
- **Evidence:** ADR-0053 d24; ADR-0050 d4, d24, d26; ADR-0049 d4; `contracts/packs/schemas/guard.schema.json:80` `config_schema`; work breakdown item 9

### Issue #178

**Align artifact schema field names across kinds (`name`/`title`, `purpose`/`intent`/`summary`/`description`)**. Kind `scope-change`, recommendation: re-scope, confidence: medium.

- **Today:** Pick one label and one descriptor name across six authoring kinds, with back-compat aliasing during a deprecation window, after GA.
- **Problem:** The evidence table is stale. On `origin/main` procedure, skill, mission type, guard and lifecycle already use `title`/`description` (ADR-0043, ADR-0044, #354); tactic (`name`/`purpose`), paradigm (`name`/`summary`), agent profile (`name`/`purpose`), directive (`title`/`intent`), toolguide (`title`/`summary`) and styleguide (`title`, no descriptor) differ. Aliasing is ruled out ('no split-migration reader', ADR-0053 OQ3). Post-cutover lands after 1.0.0, which #128 mints inside the 4.x cutover, so the rename would need a major.
- **Proposed scope:**
  - Converge on `title` and `description` for tactic, paradigm, agent profile, directive, toolguide and styleguide.
  - One breaking 0.x change with a `### Breaking` entry (ADR-0022), bundled with the ADR-0053 `d23/OQ3` kebab set.
  - The response DTOs (Tactic, Directive, Styleguide, Paradigm, Toolguide and the generic Artifact) follow.
  - Open-packs content migrates through the service writer, then a re-pin (as ADR-0043, ADR-0044).
  - No aliasing or compatibility reader.
- **Drop:**
  - The back-compat aliasing and deprecation window.
  - The stale evidence table and the 'Tactic is the only DTO' note.
  - Procedure and skill from the kinds to change.
- **Evidence:** `contracts/packs/schemas/*.schema.json` at d395470; ADR-0043; ADR-0053 d23, OQ3; ADR-0022; #128 (1.0.0 in 4.x cutover)
- **Question:** Move #178 into the 0.x window (4.x cutover, before #128 mints 1.0.0), or keep Post-cutover and accept a major version? Also: does the glossary context keep `name` (ADR-0049 d2), or join the rename?

### Issue #206

**Migrate `required_<plural>` lists to presets with per-item must-have marks, then refuse them**. Kind `factual`, recommendation: re-scope, confidence: high.

- **Today:** Record the two rulings in an ADR, add the per-item preset marker and derive the required set, convert open-packs `required_*` lists to presets, refuse `required_*` at load, and coordinate core.
- **Problem:** The first two boxes are done: ADR-0042 records the rulings and the `must_have` shape, preset.schema.json:15 carries it, and `Preset` derives `required()` (Preset.java:147). `org-charter.schema.json:124-194` still accepts `required_*`, and this repo's `packs/internal/org-charter.yaml` still carries five lists.
- **Proposed scope:**
  - Tick the ADR and preset-marker boxes, citing ADR-0042 and preset.schema.json.
  - Convert every `required_*` list in spec-kitty-open-packs into a preset with marked items, and mirror it in `packs/internal`.
  - Refuse `required_*` in `org-charter.schema.json` and at load, naming the file and the fix.
  - Do it before the glossary rename, so `required_glossary_packs` is removed rather than renamed (ADR-0049 Consequences).
  - Core coordination stays a note; under ADR-0055 it is phase-2 work.
- **Drop:**
  - The 'Ordering: after epic #186 / #195 may bring in the marker' paragraph (done).
- **Evidence:** ADR-0042 (preset with must-have items; '#206' named); `contracts/packs/schemas/preset.schema.json:15`; org-charter.schema.json:124-194; `packs/internal/org-charter.yaml` `required_*` lists; `service/packs/.../Preset.java:147`
- **Question:** ADR-0055 D3 lists `required_<kind>` among the rules the design-derived packs exercise. Confirm that means the preset must-have set, so refusing org-charter `required_*` still stands, and whether #206 should move from 4.0 GA into the 4.x cutover so #11 (org layer) is built without `required_*`.

### Issue #36

**Follow-ups from the charter-offering bootstrap review (#35)**. Kind `factual`, recommendation: re-scope, confidence: medium.

- **Today:** Item 1: a canonical `packs/qa/pack.yaml` (`pack_level: org`, minted `pack_id`) upstream, then re-vendor. Item 2: glossary entries for the bounded-context sense of 'charter offering', promise-first acceptance scenario, conformance net, agent harness, and reconcile 'governance resolution'.
- **Problem:** Item 1 is still open (`packs/qa` has no `pack.yaml`), and ADR-0053 d1 and d8 now back it: opt-in packs are `org`, and `pack_level` becomes required with `depends_on`. Item 2 says 'the glossary owners' court', but this repo owns the glossary (ADR-0029 d1); 'Governance Resolution' is already reworded (definitions.yaml:373), while the other terms are missing.
- **Proposed scope:**
  - Item 1: unchanged, citing ADR-0053 d1 and d8 and a ULID `pack_id` (pack-composition FR-003). Confirm the service's `pack_level_missing` diagnostic clears on the re-vendored pack.
  - Item 2: add to `site/.contextive/definitions.yaml`: the subsystem sense of 'charter offering', 'promise-first acceptance scenario', 'conformance net', 'agent harness' / 'harness-independent'.
  - Tick the 'governance resolution' box (definitions.yaml:373).
- **Drop:**
  - 'in the glossary owners' court' (ADR-0029 d1).
- **Evidence:** git ls-tree `origin/main` `packs/qa` (no pack.yaml); ADR-0053 d1, d8; ADR-0029 d1; `site/.contextive/definitions.yaml:48`, :373; Diagnostic.java:22 `PACK_LEVEL_MISSING`
- **Question:** #136 also adds pack terms to definitions.yaml. Merge #136 into #36 item 2 (one glossary pass), or keep both?

### Issue #136

**Ask core to carry the pack terms in its glossary**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Add the pack-finding and pack-level terms to the charter glossary
- **Today:** Open a request to core to add seven pack terms to its glossary, then regenerate the glossary pages here.
- **Problem:** ADR-0029 makes this repo own the glossary (d1), retires the core import (d2) and says 'no core change now' (d5), so the ask is moot. 'Mission type', 'Step contract' and 'Anti-pattern' are in definitions.yaml; 'pack finding', 'declared level', 'configured level' and 'action' are only in `docs/context/domain.md:97-109`.
- **Proposed scope:**
  - Add 'Pack finding', 'Declared level', 'Configured level' and 'Action' to `site/.contextive/definitions.yaml`, from `docs/context/domain.md:97-99`, :109.
  - Check the 'Pack Level' entry against the two level terms, so they do not repeat each other.
  - The generated glossary pages show the four terms.
- **Drop:**
  - The request to core and the core-regeneration AC.
  - The `mission_steps` / `mission_step_contracts` note (DIV-011); the ADR-0053 OQ3 kebab token supersedes it.
- **Evidence:** ADR-0029 d1, d2, d5; `site/.contextive/definitions.yaml` (34, 136, 214, 391); `docs/context/domain.md:97-99`, :109

### Issue #203

**Mirror the red-main procedure-to-directive move from open-packs#74**. Kind `factual`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Fix the stale red-main entries in `packs/internal/README.md`
- **Today:** Mirror open-packs#74: replace the internal pack's red-main procedure with the `SPEC_KITTY_RED_MAIN_POLICY` directive and update every reference.
- **Problem:** The move is mirrored: `packs/internal/directives/spec-kitty-red-main-policy.directive.yaml` exists, the fragment declares it, and no red-main procedure file is left. The README is stale in three places.
- **Proposed scope:**
  - README.md:50: remove `spec-kitty-red-main-policy.procedure.yaml` from the procedures list.
  - Add `spec-kitty-red-main-policy.directive.yaml` to the directives list (README.md:27-29).
  - README.md:158: 'red-main-release-discipline procedure' becomes the directive `RED_MAIN_RELEASE_DISCIPLINE` (fragment.yaml:233).
  - Close the issue with that fix.
- **Drop:**
  - The file move and id updates (done).
- **Evidence:** `packs/internal/directives/spec-kitty-red-main-policy.directive.yaml`; `packs/internal/drg/fragment.yaml:18-21`, :233; `packs/internal/README.md:27-29`, :50, :158

### Issue #335

**Core: rename `glossary_pack` to glossary in activation keys and tokens, with a migration**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Core renames `glossary_pack` to `glossary` in activation keys and CLI tokens, adds an upgrade migration, and retires core's own `glossary`/`glossary_scope` node kinds before the seam carries `glossary:` URNs.
- **Problem:** ADR-0053 d13 makes a context's URN `glossary_context:<id>`, not `glossary:<id>`, so the seam no longer waits for core to retire its `glossary` node kind. The body says Parent #69; it is now under #361.
- **Proposed scope:**
  - Why: the kind and operator token become `glossary`; a context is `glossary_context:<id>`; a term is `term:<context>/<term>` (ADR-0049 d1, d4; ADR-0053 d13).
  - Rename `glossary_pack_ids`, `activated_glossary_packs`, `selected_glossary_packs` and `--include glossary-pack:` to the `glossary` token, with an upgrade migration.
  - Charter side: the same stored-activation migration, because the `sha256:` revision changes (ADR-0049 Consequences).
  - Retiring core's `glossary`/`glossary_scope` node kinds is optional cleanup, not an AC.
  - Parent #361; depends on the charter glossary kind (item 4, not filed yet).
- **Drop:**
  - The third bullet of What and the third AC as gating.
  - 'Parent: #69'.
- **Evidence:** ADR-0053 d13; ADR-0049 d1, d4 and Consequences; #361 children table (C4)

### Issue #338

**Core: load the work-package lane machine from the declared lifecycle**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Core's `wp_state.py` reads states, transitions and guards from the active lifecycle artifact; each guard id resolves through a registry of evaluators like `GATE_REGISTRY`; unknown guard ids fail closed.
- **Problem:** It depends on a `gate` kind that ADR-0054 removed. Lane guards are guard artifacts `implemented_by: builtin:<id>` (ADR-0054 d6), from a closed list of six (OQ1 ruling; guard.schema.json:76), run through #340's table, so a second registry here overlaps #340. Item 8 (guard and lifecycle kinds) is done (#354). Parent is now #361.
- **Proposed scope:**
  - Depends on: the shipped work-package lifecycle (open-packs P6), #340 (`builtin:` table and runner), the spec-kitty-events lane-name agreement.
  - `wp_state.py` reads states and transitions from the lifecycle (event key `event`, `"*"` sources, ADR-0050 d17 as amended).
  - Guard entries resolve to guard artifacts and run through #340; no second registry.
  - AC: with the shipped lifecycle every transition behaves as today, with core's guards mapped to the six `builtin:` ids; fail-closed per ADR-0054 d7, with the pre-review `SKIPPED` counted as pass (ADR-0054 d12).
  - AC: a test lifecycle with another edge set changes `allowed_targets()`.
- **Drop:**
  - 'lifecycle and `gate` kinds' as a dependency (item 8 done).
  - 'registry of evaluators, following `GATE_REGISTRY`'.
  - The AC 'unknown guard id fails closed': it belongs to #340.
  - 'Parent: #69'.
- **Evidence:** ADR-0054 d6, d7, d12, OQ1; ADR-0053 d16; `contracts/packs/schemas/guard.schema.json:76`; lifecycle.schema.json; #354, #340, #361

### Issue #342

**Core: remove the software-dev special-casing from the mission runtime**. Kind `factual`, recommendation: re-scope, confidence: high.

- **Today:** Core derives work-package-driven steps from `work_package_lifecycle` on the step, drops the `software-dev` defaults, and adds a ratchet test.
- **Problem:** ADR-0053 d19 replaced `work_package_lifecycle` with `submachine: {lifecycle, per: work_package, add_guards?}` (amends ADR-0050 d18). The body says Parent #69; it is now under #361.
- **Proposed scope:**
  - Why and What: a step is work-package driven when it carries `submachine` with `per: work_package`.
  - Add the charter dependency: the `submachine` field in the mission-type schema (item 9, #148).
  - Otherwise unchanged; parent #361.
- **Drop:**
  - `work_package_lifecycle`.
  - 'Parent: #69'.
- **Evidence:** ADR-0053 d19; ADR-0050 d18 (amended); mission-type.schema.json has no submachine yet; #361

### Issue #42

**Mission-step profile-role resolution is a hardcoded central table in core; make it pack-declared and charter-resolved**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Select an agent profile by role in the service (ADR-0032)
- **Today:** A problem report on core's `_ACTION_PROFILE_DEFAULTS` table, with a proposed direction: packs declare recommended roles per step, the activated charter maps roles to profiles, and core's table is retired.
- **Problem:** The direction is decided: ADR-0032 d1-d4 (steps name roles, selection order, declared fallback, role pins in the policy file). Steps already carry `role` in the mission-type schema (mission-type.schema.json:69, ADR-0044). What is unbuilt is the selection in the service; ADR-0032's two open questions (ties; pin vs selection order) block a full build.
- **Proposed scope:**
  - The service selects a profile for a step's role: role fit, then specialization (`specializes_from`, `routing-priority`), then the declared fallback, then not-found (ADR-0032 d3).
  - A pack declares its fallback profile; built-in ships a role-neutral `locked` one (d3.3).
  - Role pins come from the pack-root policy file (d4, ADR-0030).
  - Fixture packs per rule (ADR-0055 D3); the contract operation is #102's decision.
  - Core follow-up (phase 2, ADR-0055 D1): retire `_ACTION_PROFILE_DEFAULTS` and the per-step `agent_profile` path; file it in core.
- **Drop:**
  - The 'Proposed direction (not a settled decision)' section.
  - The core code quotes, kept only as background for the core follow-up.
- **Evidence:** ADR-0032 d1-d4 and Open questions; mission-type.schema.json:69 `role`; #102 (R8, contract decision); ADR-0055 D1
- **Question:** ADR-0032's open questions (ties; how a role pin combines with the selection order) must be ruled before this can be built in full. Rule them now, or build without pins and ties first? And parent it under #142, #102's epic #94, or leave it without a parent?

### Issue #52

**Closure checker and YAML adapter follow-ups: array declared types, smaller fixtures, ambiguous bare ids, blank fragment ids**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Closure checker follow-ups: the declared-type convention for arrays, and smaller fixtures
- **Today:** Four items: extend the declared-type convention to arrays, shrink the 12 closure fixtures, decide ambiguous bare ids in `GraphDocumentReader`, and refuse whitespace-only fragment ids.
- **Problem:** Item 3 is moot: edge endpoints must be URNs (`unresolved_edge_endpoint` no longer raised, `contracts/charter/CHANGELOG.md:755`), `GraphDocumentReader` has no bare-id map, and ADR-0053 d7 refuses cross-pack bare references. Item 4 is covered by the schema: org-fragment node `id` has pattern `^[A-Za-z0-9_/.-]+$`. The checker moved to Java (contracts/`src/main/java/.../closure`); its `declared` facet is still object-only (Closure.java:45), and 12 of 76 fixtures are still 243-258 lines.
- **Proposed scope:**
  - Item 1: decide whether `Facet.declared` covers array item schemas; if not, extend it, with a planted fixture (array-without-items and items-true already report).
  - Item 2: shrink the 12 large fixtures (anonymous, items-true, nested-`allof`, nullable-`anyof`, open-array-item, open-first-part, open-named-object, open-tuple-item, open-tuple-item-closed-tail, pattern-properties, scalar-first-`allof`, untyped-map) to minimal documents.
  - Item 4: add one test that a whitespace-only fragment node id is refused (the schema already refuses it).
- **Drop:**
  - Item 3 (ambiguous bare ids) and its AC.
  - References to `contracts/build.gradle.kts` as the checker's home.
- **Evidence:** `contracts/charter/CHANGELOG.md:755`; `service/packs/.../GraphDocumentReader.java` (no putIfAbsent); `contracts/packs/schemas/org-fragment.schema.json` `_OrgDRGNode.id` pattern; `contracts/src/main/java/.../closure/Closure.java:45`; `contracts/src/test/resources/closure/` (76 files); ADR-0053 d7

## Group B: Cutover read inventory and cutover tasks

### Issue #94

**Epic: Cutover read inventory: every read core makes of the offering, decided and shaped**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Lists R1-R18; each child decides contract operation, exempt or out of the cutover, then shapes it with scenarios and planned corpus cases. 'Why' says the corpus is the only gate for the switch-over.
- **Problem:** ADR-0055 D3 supersedes the corpus over core's CLI as the acceptance oracle and the 'corpus alone gates the switch' rule (ADR-0010 d5, ADR-0021 D5 amended); #9 is on hold. The inventory also predates ADR-0048..0054, which add the kinds glossary, `anti_pattern`, `event_registry`, guard and lifecycle with no R-step.
- **Proposed scope:**
  - Add an 'Updated 2026-11-03' note: reads are accepted against the design-derived packs and contract scenarios (ADR-0055 D3), not corpus cases over core's CLI.
  - Note ADR-0055 D1: phase 1 builds the decided reads in the service (merge, activation, effective reads, register); core's routing is phase 2. The per-switch gate is ADR-0055 open question 1.
  - New-kind reads are covered by R2 (generic artifact tree) plus R18 (kind table), or by the per-kind contract sets of work-breakdown item 13. No new R-step unless a read is not a generic artifact read.
  - Children are #96-#112; #95 (R1) is closed (ADR-0018).
  - Done when: every child decided; service-contract.md 'Planned read surface' has a shape for each decided read; each decided operation names its scenarios and fixture packs.
- **Drop:**
  - 'The corpus is the only gate for the switch-over.'
  - 'planned corpus cases' in Steps and Done-when
- **Evidence:** `docs/adr/0055` D1, D3, open question 1; `docs/adr/0021` D5 amendment 2026-11-03; #9 body note 2026-11-03 (held); `docs/plans/pack-ecosystem-work-breakdown.md` item 13; `docs/plans/triage-2026-11-02.md` row '#94, #96 to #112'; #95 closed

### Issue #96

**Cutover read R2: Artifact bodies: decide and shape**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Cutover read R2: Merged artifact bodies and the new kinds: decide and shape
- **Today:** Decide and shape the read of artifact bodies (directives through profiles); coverage says the contract has only `GuidanceEntry` and the docs one shapeless row.
- **Problem:** Coverage is stale: per-pack bodies are served for five kinds by `listPackArtifacts/getPackArtifact` (PR #303, `contracts/charter/schemas/Artifact.yaml`); `GuidanceEntry`.yaml no longer exists. What is open is the merged `/offering/artifacts` read and the kinds not yet served (procedure, `agent_profile`, `mission_step_contract`, glossary, skill, `anti_pattern`, guard, lifecycle, `mission_type`), with the kebab {kind} token (ADR-0053 d23).
- **Proposed scope:**
  - Record the per-pack read as decided and built for tactic, directive, styleguide, paradigm, toolguide (PR #303).
  - Decide and shape GET `/offering/artifacts` and `/offering/artifacts/{kind}/{artifactId}`: merged across the `depends_on` closure and the levels (ADR-0053 d6, ADR-0055 D2), with provenance naming the winning pack.
  - List which remaining kinds join the generic read, and which need their own contract set (work-breakdown item 13: glossary, anti-pattern, event registry, guard, lifecycle).
  - Use the kebab operator token in {kind} (ADR-0053 d23).
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - Coverage row citing `schemas/GuidanceEntry.yaml`
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** `contracts/charter/schemas/Artifact.yaml` (oneOf five kinds); `docs/api/service-contract.md` 'Planned read surface' R2 row; ADR-0053 d6, d7, d23; ADR-0055 D2, D3; work breakdown item 13

### Issue #97

**Cutover read R3: Merged `DRG` and graph queries: decide and shape**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Decide and shape core's graph reads (load_built_in_graph, `merge_three_layers`, `validate_dangling_references`, `scan_project_artifacts`).
- **Problem:** The merged graph is now a `depends_on` closure per level plus the layer merge (ADR-0053 d6, ADR-0055 D2), with <pack>:: qualified cross-pack edges (d7), new relations `implemented_by` (ADR-0054 d3) and step enhances (d22), and event registries are not nodes (d28). `contracts/charter/schemas/NodeKind.yaml` still lists `glossary_pack` and lacks guard and lifecycle.
- **Proposed scope:**
  - Decide and shape GET `/packs/{packId}/graph` (the declared nodes and edges, cross-pack refs as <pack>::urn) and GET `/offering/graph` (merged).
  - Decide whether core's dangling-reference check becomes a read or stays the load-time closure check (bare cross-pack refs refused at load, ADR-0053 d7).
  - Align NodeKind and Relation with the decided kinds and relations: `glossary_context`, guard, lifecycle, `implemented_by`, enhances on action nodes; no `event_registry` node.
  - Sequence after the resolver (#145, #146) and #10 (ADR-0055 D2).
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0053 d6, d7, d22, d28; ADR-0054 d3; ADR-0055 D2; `contracts/charter/schemas/NodeKind.yaml`; service-contract.md: R3 'deferred to the minor that brings merge'

### Issue #98

**Cutover read R4: Pack listing and presets: decide and shape**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Decide and shape the pack listing and presets reads; coverage says listPacks has no presets.
- **Problem:** listPacks was removed by PR #139; GET `/offering` (PR #303) now lists id, level, name and version only. After the split, built-in is a distribution pack over foundation and writing (ADR-0053 d3, ADR-0048 d12), and an empty charter puts nothing in force (ADR-0053 d10), so presets become the normal way to activate.
- **Proposed scope:**
  - Record GET `/offering` as the built pack listing (PR #303).
  - Decide which composition facts the listing carries: `depends_on` (with PEP 440 range, d4), `parent_pack`, and the members of a distribution pack.
  - Decide and shape GET `/packs/{packId}/presets` and `/offering/presets` against `contracts/packs/schemas/preset.schema.json`.
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - Coverage row about listPacks
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** service-contract.md 'PR #139 ... removed listPacks'; `contracts/charter/schemas/OfferingPack.yaml`; ADR-0053 d3, d4, d10; ADR-0048 d12, d15

### Issue #99

**Cutover read R5: Mission types and the action index: decide and shape**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Cutover read R5: Mission types (with steps, transitions and lifecycle): decide and shape
- **Today:** Decide and shape core's mission-type and action-index reads; coverage says mission types are only path keys.
- **Problem:** A mission type is an ordinary kind read at `/packs/{packId}/artifacts/mission-type/{id}` (ADR-0050 d3, d6; ADR-0053 d23). The action index is folded into the mission type's step references (ADR-0044, `actions/<a>/index.yaml` -> references). ADR-0050 d19 allows an aggregate read with the lifecycle embedded.
- **Proposed scope:**
  - Decide the plain `mission_type` read as covered by R2 (generic artifact read, kebab token).
  - Decide and shape one aggregate read: a mission type with its steps, transitions, guard entries and embedded lifecycle (ADR-0050 d19).
  - Keep `/offering/mission-types/{missionType}` only as the aggregate and as the parent of the action-context path (#95, ADR-0018); drop any other `/offering/mission-types` tree.
  - Sequence after the mission-type schema (work-breakdown item 9).
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - The separate 'action index' read
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0050 d3, d6, d19; ADR-0053 d23; ADR-0044 (action index -> step references); service-contract.md '`/offering/mission-types/{missionType}/actions/{action}/context`'

### Issue #100

**Cutover read R6: Mission step contracts and gate bindings: decide and shape**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Cutover read R6: Step contracts, guards and lifecycles: decide and shape
- **Today:** Decide and shape core's read of mission step contracts and their gate bindings (`MissionStepContractRepository`, `gate_bindings.py`).
- **Problem:** Gate bindings are gone: there is no gate kind (ADR-0053 d16); guards are artifacts named from transitions (ADR-0054 d1, d2), and `gates/GateBinding` leave the step-contract schema (ADR-0054 d12; still present in `contracts/packs/schemas/mission-step-contract.schema.json` at `origin/main`). Step contracts stay a kind in the mission-type bundle (ADR-0050 d5). Guard and lifecycle kinds are in the domain (#156, closed).
- **Proposed scope:**
  - Decide step contracts, guards and lifecycles as generic artifact reads (R2), or name what needs a per-kind contract set (item 13).
  - Shape how a reader gets the guard entries of a transition ({ref, config?, blocking?}), including step `add_guards` (ADR-0054 d2, d10).
  - Decide together with R7 (#101): blocking expected artifacts are the structural guard (ADR-0050 d13).
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - 'gate bindings' and the `gate_bindings.py` read
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0053 d16; ADR-0054 d1, d2, d10, d12; ADR-0050 d5, d13; #156 closed (PR #354); `contracts/packs/schemas/mission-step-contract.schema.json` ($defs.GateBinding)

### Issue #103

**Cutover read R9: Active charter and effective activation sets: decide and shape**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Cutover read R9: Effective reads of an activation: semantics and acceptance
- **Today:** Decide and shape the active-charter and effective-set reads (resolve_effective_sets, `filter_graph_by_activation`).
- **Problem:** The decision is taken: the effective reads are declared and reserved (501) by #125 (closed), and ADR-0055 D1 puts them in phase 1. The semantics changed: an absent activation key means none (ADR-0053 d10; its open question 1 is ruled, so the triage note 'must be ruled' is out of date). The kernel still says `InForceWhenAbsent`.ALL, ActivatableKind excludes `mission_type` and keeps `glossary_pack`.
- **Proposed scope:**
  - Build listEffectiveArtifacts, getActivationSkippedReport, getEffectiveGraph and getEffectiveActionContext on the merge (#10, #11) and the register (#291).
  - Pin the semantics: absent key = none (ADR-0053 d10); `mission_type` activatable (ADR-0050 d6); event registries in force with their pack (d26); key glossary, not `glossary_pack` (ADR-0049).
  - Read the interim trust mark from the activation file (ADR-0054 d7, open question 3 ruled).
  - Accept against fixture packs and activations that exercise each rule (ADR-0055 D3).
  - Record the semantics in service-contract.md.
- **Drop:**
  - The 'Decide' options (already decided by #125)
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** #125 closed; service-contract.md activation table; ADR-0053 d10, d26, open question 1; ADR-0054 d7; ADR-0055 D1, D3; `service/kernel/.../ArtifactKind.java` (`InForceWhenAbsent`.ALL); `contracts/charter/schemas/ActivatableKind.yaml`
- **Question:** Does #103 build the effective reads, or does it only fix their semantics while #291 (or a new issue) builds them? service-contract.md says #103 builds them.

### Issue #105

**Cutover read R11: Assets: decide and shape**. Kind `scope-change`, recommendation: re-scope, confidence: medium.

- **Today:** Decide and shape core's asset read (AssetRepository).
- **Problem:** An asset sidecar can now declare an executable block (ADR-0054 d5; `contracts/packs/schemas/asset.schema.json`) and is the target of `implemented_by` (d3). A client must fetch an executable payload to run a guard (core #340). The path-anchoring defect is open (open-packs#75; ADR-0054 d5 says fix it first).
- **Proposed scope:**
  - Decide the sidecar read as R2 (generic read of kind asset), carrying the executable block.
  - Decide and shape the payload read: GET `/packs/{packId}/assets/{path}` (ADR-0019 table), with refusals for escaping paths.
  - State that guard-backing assets need the anchoring fix (open-packs#75) first.
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0054 d3, d5; `contracts/packs/schemas/asset.schema.json` (executable); `docs/development/epic-186-sanitized-shape-follow-ups.md:72` (open-packs#75); service-contract.md 'Decided, not built: packs resources'

### Issue #106

**Cutover read R12: Glossary packs: decide and shape**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Cutover read R12: Glossary contexts and terms: record and specify
- **Today:** Decide and shape the glossary pack read.
- **Problem:** The shape is decided: GET `/packs/{packId}/artifacts/glossary`[/{contextId}] (ADR-0049 d7-9), active terms on the activation side (d10), URN `glossary_context:<id>` with kind token glossary (ADR-0053 d13). The work-breakdown item 4 issue is not filed yet.
- **Proposed scope:**
  - Record the decision and shape in service-contract.md, citing ADR-0049 d7-10 and ADR-0053 d13.
  - List the contract scenarios and the fixture packs (ADR-0055 D3).
  - Hand the active-terms read to R9 (#103) or to the item 13 glossary contract set.
- **Drop:**
  - The 'Decide' options
  - 'glossary packs' wording
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0049 d7-10; ADR-0053 d13; work breakdown item 4 ('proposed', not filed)
- **Question:** Once the item 4 issue ('glossary contexts and terms') is filed, close #106 into it, or keep #106 for the contract record?

### Issue #109

**Cutover read R15: Override and pack policy: decide and shape**. Kind `scope-change`, recommendation: re-scope, confidence: medium.

- **Today:** Decide and shape core's override adjudication and pack-policy reads (adjudicate_overrides, `load_effective_override_policy`).
- **Problem:** Overrides now include sibling conflicts settled by the distribution pack's overrides edge (ADR-0053 d5), ids unique per `depends_on` closure (d6), sibling step enhancements refused (d21) and step overrides with a drift warning (work-breakdown item 10). Adjudication is part of the merge (#10, #145); the mutability policy is Post-cutover (#55, #57-#61).
- **Proposed scope:**
  - Decide override adjudication as part of the merge: its result shows as provenance and diagnostics on the merged reads (R2, R3), not as a read of its own.
  - Decide the mutability-policy read: out of the cutover (with the ADR-0010 amendment) or a contract operation.
  - List the fixture packs for d5, d6 and d21 refusals (ADR-0055 D3).
- **Drop:**
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0053 d5, d6, d21; work breakdown item 10; ADR-0012; #55, #57-#61 (Post-cutover)
- **Question:** Is the mutability-policy read out of the cutover (needs an ADR-0010 amendment), given #55 and its children are Post-cutover?

### Issue #110

**Cutover read R16: Built-in pin, manifest freshness and snapshot fingerprint: decide and shape**. Kind `scope-change`, recommendation: re-scope, confidence: medium.

- **Today:** Decide whether core's built-in pin, manifest freshness and snapshot fingerprint reads are operations, exempt or out.
- **Problem:** Built-in is a distribution pack over foundation and writing (ADR-0053 d3); cross-pack pinning is a PEP 440 `depends_on` range plus the pack fingerprint (d4, ADR-0054 d11); an activation pins packs with PackPin (ADR-0033 D6). The service already answers a source fingerprint (Charter-Source-Fingerprint, ServiceInfo), pack files only after ADR-0020 D12.
- **Proposed scope:**
  - Decide pack vendoring reads (pack_vendor.py, snapshot.py) as exempt pack tooling or as R4 fields.
  - If R4: the listing carries `pack_version` and a per-pack fingerprint; shape them there.
  - Record that freshness for reads is the existing source fingerprint and `stale_input` refusal (ADR-0011, #175).
- **Drop:**
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0053 d3, d4; ADR-0054 d11; `contracts/charter/schemas/PackPin.yaml`; ADR-0020 D12; service-contract.md 'Security and freshness'
- **Question:** Are the pin and snapshot reads exempt pack tooling (like compile, ADR-0010 d3), or fields of the R4 pack listing?

### Issue #111

**Cutover read R17: `SPDD/REASONS` blocks: decide and shape**. Kind `scope-change`, recommendation: close instead, confidence: medium.

- **Today:** Decide and shape core's read of the `SPDD/REASONS` blocks (command_renderer.py).
- **Problem:** SPDD moves to the opt-in spdd topical pack (ADR-0053 d3; work breakdown: paradigm, reasons-canvas tactics, directive 038). The blocks are bodies of that pack's artifacts (R2) in force only when activated (R9). There is no read of its own.
- **Proposed scope:**
  - Record the decision 'covered by R2 + R9, no own operation' in #94 and in the service-contract.md row.
  - Close the issue with that decision.
- **Drop:**
  - The whole 'Decide and shape' body
- **Evidence:** ADR-0053 d2, d3; ADR-0048 (topical pack spdd); work breakdown 'Foundation membership' spdd entry
- **Question:** Close R17 as decided ('covered by R2 + R9'), with the decision recorded in #94 and service-contract.md?
- **Correction, 2026-11-04:** the operator approved closing this, but the reason above is wrong. The blocks are text in
  the built-in pack's `software-dev` step prompts, not bodies of the `spdd` pack's artifacts, and R2 plays no part. The
  prompt text comes with the mission-type read (R5, #99), and the activation answer comes from R9 (#103). The decision
  on #111 has the evidence.

### Issue #112

**Cutover read R18: Kind taxonomy facts: decide and shape**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Decide and shape the kind-table read (ArtifactKind, plurals, `effective_when_absent`, `selection_overlayable`).
- **Problem:** The kind set changed: `glossary_pack` -> glossary (URN `glossary_context`, ADR-0053 d13), new `anti_pattern`, `event_registry` (not activatable, not a node, d26, d28), guard, lifecycle; no gate kind (d16); `mission_type` ordinary (ADR-0050 d6). The URN-token question is ruled (ADR-0053 OQ3: kebab, the operator note's blocker is gone). The per-kind 'effective when absent' default is withdrawn (d10). Kernel ArtifactKind still has `GLOSSARY_PACK`, no `EVENT_REGISTRY`, no kebab lookup.
- **Proposed scope:**
  - Decide and shape GET `/offering/kinds` (or a schema fixture) with, per kind: snake token, kebab `operator/path` token, URN token, `directory/plural`, activatable, CRG node, `selection_overlayable`, org-requirable.
  - Cover exactly the decided kinds, including glossary, `anti_pattern`, `event_registry`, guard, lifecycle, `mission_type`.
  - Drop `effective_when_absent` (ADR-0053 d10).
  - Pair with the kebab {kind} contract change (ADR-0053 d23) and #49.
  - Replace 'corpus cases named for the harness (#9)' with: the design-derived fixture packs and contract scenarios that exercise the read (ADR-0055 D3).
- **Drop:**
  - `effective_when_absent`
  - 'corpus cases are named for the harness (#9)'
- **Evidence:** ADR-0053 d10, d13, d16, d23, d26, d28, open question 3; ADR-0054 d1; `service/kernel/.../ArtifactKind.java`; e1fe068

### Issue #70

**Launcher and service lifecycle: core starts and stops the charter service per worktree**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Launcher and service lifecycle: the local deployment profile, and the hosted endpoint
- **Today:** Decide the launcher shape (open question 8); core starts the service per worktree on loopback, stops it with the worktree; health and version handshake with a major-version refusal.
- **Problem:** Open question 8 is decided (ADR-0011, ratified 2026-10-27). The 2026-10-20 re-scope makes the per-worktree daemon one deployment profile of a hosted-first service (ADR-0020 D12, ADR-0034), and the 'starts on each OS in CI' criterion moved here from #71 on 2026-10-31. ADR-0055 D1 makes this phase-2 work, needed by the first route switch (ADR-0021 D5). `/info` already reports contractVersion and serviceVersion.
- **Proposed scope:**
  - Core's launcher (specify_cli) for the local profile: start on first use, stop when idle and with the worktree, loopback plus token (ADR-0011).
  - Hosted profile: core calls a configured endpoint with a login token and fails closed when unreachable (ADR-0034).
  - The seam refuses a service whose contract major.minor differs (service-lifecycle.md), using `/info` and Charter-Contract-Version.
  - The launcher starts each native executable and the jar on its OS in CI (moved from #71).
  - Close the launcher review residuals: port squatting, guessable worktree id (#121 for argv placeholders).
  - Timing: phase 2, before the first route switch (ADR-0055 D1, ADR-0021 D5).
- **Drop:**
  - 'Decide the launcher shape' (decided by ADR-0011)
  - 'per worktree' as the only deployment
- **Evidence:** ADR-0011 (Accepted 2026-10-27); ADR-0020 D12; ADR-0034; ADR-0021 D5; ADR-0055 D1; #70 comment 2026-10-20; #71 comment 2026-10-31; `docs/plans/open-questions.md` Q8; `contracts/charter/schemas/ServiceInfo.yaml`
- **Question:** Does the hosted-profile client path (configured endpoint and login) belong in #70, or in #127/#233 with #70 kept to the local launcher?

### Issue #71

**Cross-OS packaging of the charter service**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Decide native or JVM (open question 5); build and publish a pinned, checksummed artifact per OS; the launcher starts it on each OS in CI.
- **Problem:** Open question 5 is decided (ADR-0046). The release workflow is on main (f8690ec, PR #297). The launcher criterion moved to #70 and core's checksum check to spec-kitty#6909 (comment 2026-10-31). No `service-v*` tag or release exists yet (checked via the GitHub API), so the issue is not done.
- **Proposed scope:**
  - Cut the first `service-v*` release with the ADR-0046 d4 assets: four native executables, the jar, .sha256 files and SHA256SUMS, with attestations (d6).
  - Each executable and the jar are started in CI before release (ADR-0046 d5).
  - The tag ruleset on `service-v*` exists before the first tag (ADR-0046 d7).
- **Drop:**
  - 'Decide native binary or JVM artifact'
  - 'core's install verifies its checksum' (spec-kitty#6909)
  - 'The launcher (#70) starts it on each OS in CI' (moved to #70)
- **Evidence:** ADR-0046; PR #297 (f8690ec) merged 2026-11-01; #71 comment 2026-10-31; GitHub API: no tags matching `service-*`; `.github/workflows/service-release.yml`
- **Question:** Is a `service-v*` pre-release enough to close #71, or does it wait for a final release (which needs a released contract, ADR-0046 d3, and 0.1.0 is held)?

### Issue #74

**Java authoritative reads (strangler stages 3 and 4, shipped with #76)**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Route every charter read to the service, one operation at a time (strangler stages 3 and 4)
- **Today:** Switch the seam adapter to answer from Java; keep a documented switch back to Python reads until stage 4, with a test.
- **Problem:** The body contradicts itself: the 2026-10-19 note says no Python fallback, the AC keep a switch back. ADR-0021 D5 settles it: each operation moves to remote on its own, fails closed with no runtime fallback, and stays reversible (route back to in-process) until #76. ADR-0055 D1 makes this phase 2, after the service answers the cutover reads. 'Depends on: #71, #72' is out of date: #72 is held (ADR-0055 D3).
- **Proposed scope:**
  - For each read operation, move its route in core's proxy from in-process or shadow to remote (ADR-0021 D3, D5).
  - A routed operation fails closed: no runtime fallback to Python (ADR-0011 D1, ADR-0021 D5).
  - Until #76, a route can be set back to in-process by configuration; a test covers it.
  - Done when every read operation is remote.
  - Depends on: phase 1 (#10, #11, #103, #291), #13, #14, #70, #73, #128 (1.0.0 before the first remote route, ADR-0022 D6), and the per-switch gate of ADR-0055 open question 1.
- **Drop:**
  - 'Python answers only as a loud, observable fallback'
  - 'One documented switch sends reads back to Python' as a single global switch
  - Depends on #71 and #72 as written
- **Evidence:** ADR-0021 D3, D5; ADR-0011 D1; ADR-0055 D1, D3, open question 1; ADR-0022 D6; `docs/plans/milestones.md` 'Stages 3 and 4'
- **Question:** What gates each switch (ADR-0055 open question 1)? #74 cannot state its per-operation acceptance until it is ruled.

## Group C: Governance, values and the behavior-first epic

### Issue #16

**Epic: Creeds and Values (FoundationalValues and the charter creed)**. Kind `factual`, recommendation: re-scope, confidence: high.

- **Today:** Epic for the charter value layer: re-baseline (#17), prose layer, AMMERSE unification, value-set kind, probe, creed section and ranking (#18-#23), with core missions `B1/B2/C/D` as dependencies.
- **Problem:** Ordered steps omit #82 (moved here by ADR-0014 d6), #23 still waits on core B1 (#2949, absorbed into #79), and the source design cites core ADR `3.x/2026-07-26-3`, whose relation naming ADR-0028 supersedes here (impacts = per-value list, tension = relationship).
- **Proposed scope:**
  - Ordered steps: #17, #18, #19, #20, #21, #22, #82 (frozen projection, after #20 and #21), #23; #23 depends on #20's per-value `impacts` list (ADR-0028 d1), not on core B1.
  - Source design: cite ADR-0014 (D-1 to D-5) and ADR-0028 as the authorities here; core design and 2026-07-26-3 are history only.
  - State the split with #77: #77 owns the `tension` relationship; this epic owns value sets, the per-value `impacts` list, the creed and ranking (ADR-0014 d6, ADR-0028 d1-d2).
  - Note ADR-0014 d3 and d7: numeric work is research-grade until a delta producer exists, and creed data reaches the API as a post-cutover contract set.
  - Done when unchanged.
- **Drop:**
  - Core missions B1 (#2949) and B2 (#2950) as dependencies.
  - The 'impacts subsumes `in_tension_with`' ADR link as an authority.
- **Evidence:** ADR-0014 d3, d6, d7; ADR-0028 d1, d2; #82 milestone row in `docs/plans/milestones.md:150`; #79 absorbed core #2949 (#79 body)

### Issue #17

**Creeds and Values (1/7): Re-baseline the design against 4.x and this repository**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Creeds and Values (1/7): Plan page mapping the value-layer increments to this repository
- **Today:** Re-trace the core design's citations against 4.x, decide which increments land here versus core, record D-1 and D-5, and write a plan page in `docs/plans/`.
- **Problem:** D-1 to D-5 are recorded by ADR-0014 (second criterion met). ADR-0014 d2's re-price of the `impacts` relation is moot: ADR-0028 renamed it to `tension`, at no data migration cost here. No values plan page exists in `docs/plans/` on `origin/main`.
- **Proposed scope:**
  - A plan page in `docs/plans/` maps each remaining increment (I8, I9, I10, I13, creed, projection, prose layer) to an issue in #16 and to this repository or core.
  - The page uses ADR-0028 terms: `impacts` is an artifact's per-value list; `tension` is the relationship (#77).
  - The page lists which core design sections and paths are stale and which ADR replaces them (ADR-0014, ADR-0028).
  - The page names an owner for D-6 (asymmetric matrix pair), which ADR-0014 left open in core.
- **Drop:**
  - 'Record D-1 and D-5' acceptance criterion (done: ADR-0014 d1, d5).
  - The re-price of the signed `impacts` relation (ADR-0014 d2, superseded by ADR-0028).
- **Evidence:** ADR-0014 d1, d2, d5; ADR-0028 d1, d2, Context ('costs no data migration'); no values plan in `docs/plans/` at d395470

### Issue #23

**Creeds and Values (7/7): N-generic first-order matrix and ranking function**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Build I13 (a symmetric first-order matrix in the value-set artifact, with a validator) and I10 (the ranking function), blocked by #19-#22 and core B1's `impacts` relation.
- **Problem:** The core B1 dependency is stale: ADR-0028 d1 makes `impacts` a per-value list on one artifact, so ranking reads #20's lists, not a signed edge. ADR-0014 d3 restricts ranking to the corpus and fixtures until a delta producer exists; the body does not say so. D-6 is still open in core's design.
- **Proposed scope:**
  - I13: the matrix inside the value-set artifact (#20), with a validator for range, zero diagonal and symmetry; built only after D-6 is ruled.
  - I10: ranking over creed weights and per-value `impacts` lists (ADR-0028 d1); it must not rebuild the row-sum collapse (design §10.1).
  - Ranking runs on the corpus and fixtures only; no production path reads it (ADR-0014 d3).
  - Blocked by #19, #20, #21 (go result), #22 and #82.
- **Drop:**
  - Dependency on core Mission B1 (spec-kitty#2949) and the `impacts` relation.
- **Evidence:** ADR-0028 d1, d2; ADR-0014 d3 and Status ('D-6 stays open there'); #21 `go/stop` gate
- **Question:** D-6 (asymmetric matrix pair) is still open in core's design. Rule it here, now that the value layer is this repository's, or keep waiting on core?

### Issue #29

**Epic: Behavior-first charter-offering overhaul (bootstrap, P1-P3)**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Epic: Charter acceptance promises: re-cut, bind to the service suite, render
- **Today:** Frames phases P1 to P3 of the behavior-first overhaul: boundary scenarios, an in-repo harness, the charter in force, docsite rendering, and a P3 storage spike; P2 is #7 and P4 is deferred to #6.
- **Problem:** P2 (#7) and the P3 spike (#34) are closed, #32 is closed, and port-vs-rewrite is decided (ADR-0008). ADR-0055 D3 makes the design and the design-derived packs the acceptance oracle, and the contract scenarios (contracts/`charter/scenarios`, incl. journeys 01-11) are the bound suite; `tests/scenarios/charter` is unchanged since its copy (fb5ac98) and still cites core's Python as authority.
- **Proposed scope:**
  - Children: #30 (re-cut boundary promises), #31 (bind or retire `tests/scenarios/charter`), #33 (render scenarios in the docsite), #36 and #38 (bootstrap follow-ups).
  - Done when every promise in `tests/scenarios/charter/PROMISES.md` is either bound as a @contract scenario run by `verifyAll`, or retired with a pointer to the contract scenario or journey that covers it.
  - PROMISES.md authorities cite this repository's ADRs, not core paths (e.g. PR-CHARTER-SHARING-002 carries ADR-0012's 'unless the pack author locked it'; `PR-CHARTER-GOVERNANCE-001/002` follow ADR-0053 d10 and ADR-0031).
  - Milestone set by the operator answer below.
- **Drop:**
  - The P1-P4 phase table and 'port-vs-rewrite is deferred' (ADR-0008).
  - P3 design spike (closed #34) and 'what is already done' list.
  - 'The scenarios are the conformance net the cutover relies on' (ADR-0055 D3).
- **Evidence:** ADR-0008; ADR-0055 D3; ADR-0016 (superseded as oracle); #7, #32, #34, #37 closed; `docs/architecture/behavior-and-scenarios.md` 'Where they live' table; `tests/scenarios/charter` last touched fb5ac98
- **Question:** Milestone for #29, #30, #31 and #33: milestones.md keeps them in 4.x cutover as 'the conformance net'. Under ADR-0055 D3 the contract scenarios and design-derived packs gate the service. Move all four to Post-cutover, or keep #31 on the cutover path because binding the promises supports the D3 acceptance?

### Issue #30

**P1: Author the offering's `boundary/role` scenarios beyond the three seeded `features`**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Author the service's boundary promises as contract scenarios
- **Today:** Add `boundary/exclusion` scenarios to `tests/scenarios/charter`: the offering does not author doctrine, does not write events, is not the activation layer, and refuses pack-authoring concerns; tag each @promise and trace it in PROMISES.md.
- **Problem:** Two of the listed boundaries no longer hold as written. The service holds the activation set (ADR-0020 D1 `/activations`, ADR-0033 d1), though the `/offering` resource still never deals with a client's choices (ADR-0020 D1). The service accepts functional registration writes (ADR-0026 d1), so 'does not author or mutate doctrine' needs that qualifier. Unbound scenarios in `tests/scenarios/charter` are not run.
- **Proposed scope:**
  - Re-cut list: `/offering` answers what can be used and never a client's choices (ADR-0020 D1); the service writes no mission events; registration is functional only, with no `DELETE/PATCH` (ADR-0026 d1-d2); a pack's file or asset outside its folder is refused.
  - Write each as a @contract scenario under `contracts/charter/scenarios/` against a contract operation, or mark it @provisional where no operation exists yet.
  - One promise per scenario, with a @promise tag and a PROMISES.md row citing an ADR of this repository.
  - Skip any boundary already pinned by an existing contract scenario or journey; list those in the PR.
- **Drop:**
  - 'The offering is not the activation layer' as a service boundary.
  - 'Does not author or mutate doctrine' without the ADR-0026 registration qualifier.
  - Adding the scenarios to unbound `tests/scenarios/charter`.
- **Evidence:** ADR-0020 D1; ADR-0033 d1; ADR-0026 d1, d2; `docs/architecture/behavior-and-scenarios.md` (only @contract runs)

### Issue #31

**P1: Wire the in-repo Layer-B acceptance harness (Cucumber-JVM) against the current charter surface**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Bind or retire the `tests/scenarios/charter` acceptance promises
- **Today:** Stand up a Cucumber-JVM runner with charter step definitions that runs `tests/scenarios/charter` against the current Python charter via core's CLI, independent of #8/#10.
- **Problem:** A Cucumber-JVM suite already runs the @contract scenarios in `service/deployable` (cucumber dependencies in its build.gradle.kts). Running against core's Python surface is the corpus-over-core-CLI approach that ADR-0055 D3 supersedes as the oracle (ADR-0016; #9 is on hold). The nine promises' Acts drive core commands (doctor charter-packs, charter synthesize).
- **Proposed scope:**
  - For each of the nine promises in PROMISES.md: map it to an existing contract scenario or journey, rewrite it as a @contract scenario in `contracts/charter/scenarios/` against a contract operation, or retire it with a reason.
  - Rewritten promises use the current rules: ADR-0012 lock qualifier on SHARING-002, ADR-0053 d10 and ADR-0031 on `GOVERNANCE-001/002`.
  - `tests/scenarios/charter/` is removed or reduced to a pointer once every promise is mapped; behavior-and-scenarios.md 'Where they live' is updated.
  - `verifyAll` runs every bound promise.
- **Drop:**
  - A second runner against core's Python surface via the CLI.
  - Reconciling with #15's nightly half (the service suite already exists).
  - Pinned-toolchain and how-to items that the existing service build already covers.
- **Evidence:** ADR-0055 D3; ADR-0016 d2, d5; `service/deployable/build.gradle.kts:17-19` (cucumber); #9 updated 2026-11-03 (hold); `tests/scenarios/charter/PROMISES.md` 'Binding status'

### Issue #33

**P1: Render the BDD scenarios and PROMISES map in the docsite**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Render the contract scenarios and the promise map in the docsite
- **Today:** Auto-render the `tests/scenarios/charter` `.feature` files and the PROMISES.md map in the docsite, with a clean `mkdocs` build.
- **Problem:** The site is Astro with doc-kitty, not `mkdocs`; `site/astro.config.mjs:40-43` renders gherkin fences only and calls `feature`-page generation for `contracts/charter/scenarios` 'a separate choice'. The bound suite is `contracts/charter/scenarios` (incl. journeys), not `tests/scenarios/charter`, which #31 may retire.
- **Proposed scope:**
  - Generate docsite pages from `contracts/charter/scenarios/*.feature` (incl. `journeys/`) at build time, not hand-copied, using doc-kitty's gherkin support or a generator script.
  - Render the promise map (after #31) as a page linked from `docs/architecture/behavior-and-scenarios.md`.
  - The docsite build passes with no new warnings.
- **Drop:**
  - `mkdocs`.
  - Rendering `tests/scenarios/charter` as the source (unless #31 keeps it).
- **Evidence:** `site/astro.config.mjs:40-43`; `docs/architecture/behavior-and-scenarios.md` 'Where they live'

### Issue #55

**Epic: Charter Pack governance: override, extend and enforce policy declared by the pack author**. Kind `factual`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Epic: Charter Pack governance: the owning pack's open / extendable / locked levels
- **Today:** Let the owning pack author declare per artifact whether later packs may override, enhance or deactivate it, enforce that at merge, fail cross-tier validation, reconcile `required_*`, and fix the docs; current state describes core's merge.py and `pack_validator.py`.
- **Problem:** The body describes core code and the `enforced` name. ADR-0012 names the level `locked`; ADR-0030 adds removal as its own operation and puts role `locks/pins` in the policy file; ADR-0031 moves requirement to preset must-have marks; ADR-0015 puts enforcement in the service, where PackMutabilityPolicy already gates registration writes with an empty MutabilityPolicySource.
- **Proposed scope:**
  - Current state: `service/packs` PackMutabilityPolicy (gate on `register/replace/relationship` writes, raises `governance_policy_violation`), MutabilityLevel, MutabilityPolicySource.empty(); no policy file schema in `contracts/packs`.
  - Children: #57 policy file schema, #181 runtime policy source, #160 built-in policy file and replaceable-builtins deprecation, #58 enforcement over merge order, #59 validation verdict, #60 locked-implies-required, #61 pack-author how-to; #67 (locked deactivation) consumes the levels.
  - Acceptance: given a pack marks X `locked`, when a later pack overrides, enhances or removes X, pack validation fails with a finding naming X, the level, the owning pack and the violating pack (ADR-0012 d9).
  - Rule over merge order (ADR-0012 d8), including packs in one `depends_on` compose (ADR-0048 d8-d9).
- **Drop:**
  - Core file and line citations as 'current state'.
  - 'The tier ladder is fixed at three' gap (ADR-0012 d8 settles it).
  - `enforced` wording.
- **Evidence:** ADR-0012 d1, d8, d9; ADR-0030 d1-d3, d5; ADR-0031 d1-d3; ADR-0015; `service/packs/.../PackMutabilityPolicy.java`, MutabilityPolicySource.java; triage-2026-11-02.md:66-67 (#181 under #55)

### Issue #56

**Epic: Persistent deactivation: deactivated charter elements stay gone**. Kind `scope-change`, recommendation: re-scope, confidence: high.

- **Today:** Epic for persisted exclusions so upgrade, interview, cascade and regeneration never reinstall a deactivated element; current state and children E3.1-E3.6 describe core code.
- **Problem:** The core leg is delivered (#62, #63, #64, #66 closed; core PR #6567). ADR-0030 separates removal (pack-wide) from deactivation (per project). ADR-0053 d10 replaces 'absent means all' at the split, so ADR-0013 d2's deny-list no longer guards against defaults; ADR-0048 d18 rules out whole-pack deactivation.
- **Proposed scope:**
  - Remaining children: #65 (the service never serves an excluded artifact) and #67 (deactivating a locked artifact is refused).
  - Current state: the activation contract set declares deactivateActivationArtifact and the skipped read (reserved, built by #291).
  - Acceptance: given a project deactivates artifact E, every effective read for that activation omits E and reports it as skipped, and only an explicit reactivate brings it back (ADR-0013 d3, d4).
  - Cite ADR-0030 (removal is not deactivation), ADR-0048 d18 and ADR-0053 d10.
- **Drop:**
  - Core current-state section and reinstall paths 1-6.
  - Children E3.1, E3.2, E3.4, E3.6 (delivered in core).
  - Primary scenario's 'deactivates or removes' (removal is ADR-0030's own act).
- **Evidence:** ADR-0013 d2-d6; ADR-0030 d1; ADR-0048 d18; ADR-0053 d10; #62/#63/#64/#66 closed; `contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml`

### Issue #57

**ADR and schema: a mutability policy per artefact, declared by the pack author**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Pack policy file schema and its pack.yaml reference
- **Today:** Write the ADR for `open/extendable/enforced` levels, a schema and loader-ready model, and the replaceable-builtins.yaml migration path.
- **Problem:** The ADR is ADR-0012 (Accepted); the Java model exists (MutabilityLevel, MutabilityPolicySource). The migration is #160. No policy file schema is in `contracts/packs/schemas` and pack-descriptor.schema.json has no policy key. ADR-0030 `d2/d5` and ADR-0032 d4 add role locks and pins to the file.
- **Proposed scope:**
  - A JSON schema in `contracts/packs/schemas` for the pack-root policy file: a default level per kind, a level per artifact URN, removal entries (ADR-0030 d1), role locks and role pins (ADR-0030 d5, ADR-0032 d4).
  - A pack.yaml key naming the file, added to pack-descriptor.schema.json; a pack with a policy file must declare `requires_core` (ADR-0012 d4).
  - A file-kinds.yaml entry and a round-trip test (read, write, equal meaning per ADR-0036).
  - Cross-pack references in the file use `<pack>::<urn>` (ADR-0053 d7).
- **Drop:**
  - The ADR (ADR-0012).
  - The replaceable-builtins migration (#160).
  - The runtime loader into MutabilityPolicySource (#181).
- **Evidence:** ADR-0012 d2, d4; ADR-0030 d2, d5, d7; ADR-0032 d4; ADR-0053 d7; `contracts/packs/schemas` (no policy schema); `service/packs/.../MutabilityPolicySource.java`
- **Question:** ADR-0030 d7 and ADR-0032 d7 leave the shape of removal entries, role locks and role pins to later records. Should #57 decide those shapes in its schema PR, or should an ADR record them first?

### Issue #58

**Policy loader and enforcement in `merge_three_layers`**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Enforce the owner's mutability level over merge order in the service
- **Today:** Load every higher tier's policy in core's `merge_three_layers`, classify `overrides/enhances/deactivations` against the owner's level, and fail closed with `governance_policy_violation`; plus #144's edge-form and losing-pack cases.
- **Problem:** Enforcement belongs in the service (ADR-0015), where PackMutabilityPolicy already raises `governance_policy_violation` on registration writes but its policy source is empty (#181). It must also cover removal (ADR-0030 d3) and the `depends_on` compose (ADR-0048 d8-d9), and per-project deactivation belongs to #67, not the merge.
- **Proposed scope:**
  - When the layer merge (#10, #11) applies a later pack, each override, enhance and removal of another pack's artifact is checked against the owner's level, including levels a later pack tightened (ADR-0012 d3).
  - extendable: enhance passes, override refused, removal passes; locked: override, enhance and removal refused; open: unchanged.
  - Refusal is `governance_policy_violation` naming the artifact, the level, the owning pack and the violating pack (ADR-0012 d9).
  - Keep #144's two cases: edge-form `overrides`/`enhances` is checked like a node replacement; a losing sibling's edges are not merged around the check.
  - Fixture packs per ADR-0055 D3 cover each case.
- **Drop:**
  - Core's `merge_three_layers`, `org_override` and `layer_rule_violation` references.
  - Deactivation as a merge-time operation (#67).
  - 'Warnings stay as they are' for open (the service has no such warnings).
- **Evidence:** ADR-0012 d3, d8, d9; ADR-0030 d3; ADR-0048 d8, d9; ADR-0053 d5; ADR-0015; `service/packs/.../PackMutabilityPolicy.java`; #144 fold-in (triage-2026-11-02.md:130)

### Issue #59

**Cross-tier `charter pack validate`: a lower-tier pack that breaks policy fails**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Pack validation reports a later pack that breaks an owner's level
- **Today:** Make core's `charter pack validate` and `doctor charter-packs` check a pack against higher-tier policies, exit non-zero with structured findings.
- **Problem:** Those are core CLI commands. The service has a validate-mode seam (PackValidation/PackVerdict, PackFinding, PR #314). The triage's '#115 parity' is void: #115 was closed not planned on 2026-11-02.
- **Proposed scope:**
  - Validating a pack in the context of the packs it builds on (depends_on closure and lower levels) yields a PackFinding for each level breach: {artifact, level, `owner_pack`, `violating_pack}`.
  - The verdict is the same rule #58 enforces at merge, shared code, not a second implementation.
  - A compliant pack gets a clean verdict; tests use the ADR-0055 D3 fixture packs.
  - CLI wiring (`charter pack validate`, `doctor`) is noted as a core request under #361.
- **Drop:**
  - Core CLI exit codes and doctor parity.
  - Parity with #115.
- **Evidence:** ADR-0015; ADR-0012 d9; PR #314 (PackVerdict); #115 closed `not_planned` 2026-11-02; `service/packs/.../PackFinding.java`

### Issue #60

**Reconcile `required_*` with policy: `enforced` means required and content-locked**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** A locked artifact is in the derived required set
- **Today:** Treat enforced as required, define `required_*` without enforced, and remove the overlay path that changes an enforced artifact's content.
- **Problem:** Semantics are ruled: required = presence only and locked implies required (ADR-0012 d7); required is derived from preset must-have marks (ADR-0031 d1-d2, ADR-0042); the `required_*` migration is #206. Content protection of locked is #58. The only open item is adding locked artifacts to the derived required set.
- **Proposed scope:**
  - The service's derived required set for an activation includes every artifact whose effective level is locked, beside preset must-have items.
  - Effective reads report a locked artifact as required, naming the locking pack.
  - Test: a locked artifact absent from every preset is still in force.
- **Drop:**
  - Defining `required_*` semantics (ADR-0012 d7).
  - Blocking overlay content changes (#58).
  - `required_*` authoring (#206).
- **Evidence:** ADR-0012 d7; ADR-0031 d1-d3; ADR-0042; #206
- **Question:** #60's remainder is small. Keep it as its own issue, or fold 'locked implies required' into #58 and close #60?

### Issue #61

**Docs: amend the "project always wins" statements**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Pack-author how-to: declaring open, extendable and locked
- **Today:** Update core doc passages that say the project always wins, add a pack-author how-to for the levels, and add the terms to the glossary.
- **Problem:** The passages are in core docs and ADR-0012 lists them under 'Requests to core'. The level and removal terms are already in `docs/glossary/charter/README.md` (:302, :472). No how-to exists in `docs/guides/`.
- **Proposed scope:**
  - A how-to in `docs/guides/` covering the policy file, the three levels, tightening, removal versus deactivation, role locks and pins, with one example per level.
  - Examples validate against #57's schema.
  - The core doc correction is filed as a core request under #361 and linked here.
- **Drop:**
  - Editing core docs from this repository.
  - Glossary terms (present).
- **Evidence:** ADR-0012 'Requests to core'; `docs/glossary/charter/README.md:302`, :472; ADR-0030 d1

### Issue #65

**Projection and resolution honour exclusions for every origin, built-in included**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** The service never serves an excluded artifact in effective reads
- **Today:** Filter exclusions out of `skill/command` projection (retiring projected copies, built-in included), charter context and resolution, with a gate against bypassing callers.
- **Problem:** Skill and command projection runs in core's harness; the service only answers the R13 read (#107). ADR-0013 d6 placed this 'once reads go through the API', but ADR-0055 D1 has the service build activation, cascade and the effective reads before core integrates, so the filter is part of the cutover path. The contract still says 'an absent kind keeps its default', which ADR-0053 d10 replaces.
- **Proposed scope:**
  - `/activations/{id}/artifacts`, `/graph` and the action context omit every excluded artifact, of any origin, built-in included.
  - `/activations/{id}/skipped` lists each excluded artifact with the reason (ADR-0013 d4).
  - One domain filter serves all effective reads; an architecture test fails a read path that bypasses it.
  - Effective-set semantics follow ADR-0053 d10 (absent key means none).
- **Drop:**
  - Skill and command projection and retiring projected copies (core harness; note under #361 or #107).
  - charter context CLI wording.
- **Evidence:** ADR-0013 d4, d6; ADR-0055 D1; ADR-0053 d10; ADR-0020 D1; `contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml`
- **Question:** ADR-0055 D1 puts the effective reads in the cutover. Move #65 to 4.x cutover as its own issue, or fold it into #10 / #103 and close it?

### Issue #67

**Deactivation interacts with `enforced` and `required_*`**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Deactivating a locked artifact is refused with `artifact_locked`
- **Today:** Refuse deactivating an enforced element (naming the owner pack) and make `required_*` deactivations always reported, never silent.
- **Problem:** The required half is delivered in core (core #6500) and in the contract (artifact_required, 409). #291 builds deactivateActivationArtifact with `artifact_locked`, but no policy source feeds levels yet (#181, #57), so `artifact_locked` cannot fire. The milestone is 4.x cutover while its dependencies are Post-cutover.
- **Proposed scope:**
  - With levels loaded (#57, #181), deactivateActivationArtifact on an artifact whose effective level is locked answers 409 `artifact_locked`, naming the locking pack, and records no exclusion.
  - A cascade that reaches a locked artifact skips it and reports it (ADR-0013 d4).
  - Test through the HTTP route with a fixture pack that locks one directive.
  - Move to Post-cutover.
- **Drop:**
  - The `required_*` half (delivered: core #6500; contract `artifact_required`).
  - `enforced` wording.
- **Evidence:** ADR-0012 d1, d7; ADR-0013 d5; `contracts/charter/schemas/CharterRefusalCode.yaml:185` (artifact_locked, raised: false); #291 refusal list; core #6500 closed

### Issue #77

**Epic: `impacts` vocabulary for the `DRG` (Foundational Values and Creed, graph layer)**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Epic: the tension relationship and retiring the `impacts` relation
- **Today:** Epic for a signed `impacts` edge replacing `in_tension_with` (tension = impacts < 0), with B1 (#79), B2 (#80) and the full value model.
- **Problem:** ADR-0028 d1-d2 reverse the model: `impacts` is a per-value list on one artifact (value data, #16) and `tension` is the authored relationship. ADR-0045 d1-d2 hold tension as a domain value beside the relationships, spelled `in_tension_with` in pack files. The Tension value landed (#235 closed); `impacts` is still in Relation.yaml and the pack schemas. ADR-0045 is still Proposed.
- **Proposed scope:**
  - Children: #79 (retire `impacts` as a relation; `reconciles_tension` checks), #80 (storage spelling `in_tension_with` to tension), #152 (experiment).
  - Acceptance: `impacts` is not a relation value in `contracts/charter/schemas/Relation.yaml` or `contracts/packs` graph schemas; tensions round-trip as written (ADR-0045 d2); `reconciles_tension` targets pairs joined by a tension (ADR-0028 d4).
  - Value data (the per-value `impacts` list, value sets, creed) is in #16, not here.
  - Precondition: ADR-0045 accepted.
- **Drop:**
  - The Model section (P1/`P2/P3`, `value_impact`, `value_bias`, creed).
  - 'tension = impacts < 0' and '`in_tension_with` absent from all packs'.
  - E1.4-E1.8 children (closed or moved to #16).
- **Evidence:** ADR-0028 d1-d5; ADR-0045 d1, d2 (Proposed 2026-10-31); `service/kernel/.../Tension.java`; `contracts/charter/schemas/Relation.yaml` (impacts present); #235 closed
- **Question:** ADR-0045 is still Proposed. Accept it before #77, #79 and #80 are re-scoped?

### Issue #79

**B1: add the `impacts` relation to the `DRG` vocabulary**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Retire `impacts` as a relation; `reconciles_tension` targets tension pairs
- **Today:** Add a signed IMPACTS relation validated at load, define tension as impacts < 0, and re-point `reconciles_tension` at impacts<0 pairs.
- **Problem:** The ask is reversed by ADR-0028 d1, d2, d4 and ADR-0045 d1. The Tension domain value exists (service/kernel Tension.java, #235). IMPACTS is still in the kernel `Relation/RelationType`, Relation.yaml (described as retired by ADR-0028) and the graph-shard and org-fragment schemas. Nothing checks that `reconciles_tension` endpoints are sides of a tension.
- **Proposed scope:**
  - Remove `impacts` from Relation.yaml, the kernel Relation and RelationType, and the relation enums in graph-shard and org-fragment schemas; drop the edge `impacts` strength field.
  - Contract change on 0.x with a `### Breaking` CHANGELOG entry (ADR-0022), before 1.0.0 (#128).
  - An `impacts` edge in a pack file is a finding (ADR-0045 d2 already flags `in_tension_with` plus impacts).
  - A pack check reports a `reconciles_tension` edge whose target is not a side of a tension (ADR-0028 d4).
- **Drop:**
  - A signed IMPACTS relation and 'tension = impacts < 0'.
  - Core mission `drg`-relation-impacts-vocabulary references.
- **Evidence:** ADR-0028 d1, d2, d4; ADR-0045 d1, d2, Risks; `contracts/charter/schemas/Relation.yaml`; `contracts/packs/schemas/graph-shard.schema.json:46`, :434; `service/kernel/.../RelationType.java:52`

### Issue #80

**B2: migrate `in_tension_with` edges and retire the extractor**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Rename the tension storage spelling from `in_tension_with` to tension
- **Today:** Migrate every `in_tension_with` edge to impacts, remove `IN_TENSION_WITH` from core's models.py, and retire core's extractor YAML writer.
- **Problem:** ADR-0028 d2 and ADR-0045 d2 make this a storage-only rename to `tension`, not a migration to impacts. The extractor is core code. The operator note's one-release dual read cites ADR-0053 OQ3, but OQ3 was ruled the other way: no split-migration reader, because the service has no consumers yet.
- **Proposed scope:**
  - spec-kitty-open-packs writes `tension` for every `in_tension_with` edge, in the named-packs split re-pin (open-packs P1).
  - PackPolicy.TENSION and the shard and fragment writers read and write `tension` in the same change; no dual reader (by analogy with ADR-0053 OQ3).
  - The pinned packs and the round-trip gate are green after the re-pin; a gate test pins no `in_tension_with` in `packs/`.
  - Core's extractor retirement is filed as a core follow-up under #361.
- **Drop:**
  - Migration to `impacts`.
  - Core models.py and extractor changes.
  - regenerate-graph step.
- **Evidence:** ADR-0028 d2; ADR-0045 d2; ADR-0053 open question 3 (ruled 2026-11-02); `docs/plans/pack-ecosystem-work-breakdown.md:63` (P1 split)
- **Question:** Does the rename ride the named-packs split re-pin, or ship on its own? And confirm no dual reader (ADR-0053 OQ3 by analogy) despite the operator note.

### Issue #82

**Frozen, stamped projection as the only read path for value data**. Kind `scope-change`, recommendation: re-scope, confidence: medium.

- **Today:** Declare `value_impact/value_bias` `frozensets` in core's `artifact_kinds.py`, derive anti-pattern vectors by sign-flip, and add a frozen stamped projection plus a no-P1-persistence gate.
- **Problem:** The body contradicts its own banner: per-kind fields (I14) are closed, core's `artifact_kinds.py` is not this repo, Parent #77 and dependency #81 are stale. ADR-0028 d1 names the value data an artifact's per-value `impacts` list.
- **Proposed scope:**
  - A frozen projection in the service domain is the only read path for value data (per-value `impacts` lists, creed weights); it is stamped with the value-set id and version.
  - No API or storage path writes P1 (the value-to-value matrix); an architecture test pins it.
  - Which kinds may carry an `impacts` list follows ADR-0014 d1 (no toolguide).
  - Parent #16; depends on #20 and #21.
- **Drop:**
  - `value_impact` / `value_bias` `frozensets` in core `artifact_kinds.py`.
  - Python-specific 'no `model_dump`' criterion (restate as immutability).
  - Parent #77, dependencies #81 and #79.
- **Evidence:** ADR-0014 d1, d3, d6; ADR-0028 d1; #81 closed; milestones.md:150
- **Question:** Keep the anti-pattern sign-flip rule (vector = minus the rejecting artifact's), now that ADR-0052 makes anti-patterns authored artifacts and they could carry their own `impacts` list?

## Group D: Partly delivered or stale wording

### Issue #15

**Nightly acceptance tests using BDD / Gherkin + Allure**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Nightly contract-scenario passes (gating and WIP) with Allure results and an evidence file
- **Today:** Build a Cucumber-JVM runner, a `tests/scenarios` tree, a nightly workflow with gating and wip passes, Allure results, evidence files and a doc page, copied from core's acceptance-jvm setup.
- **Problem:** The runner and the scenarios already exist: `CharterContractSuite` runs `contracts/charter/scenarios` (@contract, excluding @WIP) in service.yml via verifyAll, with cucumber dependencies in `service/deployable/build.gradle.kts`. The triage reason calls this black-box; it is not: `CucumberSpringConfiguration` drives the app in-process through `MockMvc`. The 'Why' cites the conformance corpus, which ADR-0055 D3 supersedes, and the blocker #8 is closed.
- **Proposed scope:**
  - A nightly workflow (schedule + `workflow_dispatch`) runs the existing contract scenarios in two passes: gating (@contract, not @WIP), which fails the job on a red scenario with no retries; and WIP (@WIP), continue-on-error, with its own results folder.
  - Each pass uploads Allure results and an evidence.json, also when the gating pass is red.
  - Use the tags the scenarios already carry (@contract, @WIP, @reserved); do not introduce `@wip/@manual` unless the operator asks.
  - New dependencies (Allure Cucumber plugin) enter under the existing strict sha256 dependency verification.
  - A short section in `contracts/charter/scenarios/README.md` (or the service build guide) says how to run each pass locally.
- **Drop:**
  - Creating a `tests/scenarios/` tree and a new Cucumber runner (`CharterContractSuite` exists).
  - 'Scenario authoring can start after #7' and 'Blocked by #8' (both closed).
  - The conformance-corpus framing in 'Why' (ADR-0055 D3).
- **Evidence:** `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CharterContractSuite.java` (@`IncludeTags` contract, @`ExcludeTags` WIP, plugins pretty+summary only); `bdd/CucumberSpringConfiguration.java` (@`SpringBootTest` + @`AutoConfigureMockMvc`); `.github/workflows/service.yml` (verifyAll; no schedule); no Allure reference anywhere at `origin/main` d395470; ADR-0055 D3; #8 closed
- **Question:** Must the nightly gating pass run black-box over loopback against the started jar (scripts/`release/smoke-service.sh` already starts one), or is the existing in-process `MockMvc` binding enough for the evidence trail?

### Issue #140

**ArchUnit: slice-coverage meta-rule so a new slice cannot land ungated**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** ArchUnit: derive every slice-scoped rule from one slice definition (SLICE_COVERAGE follow-ups)
- **Today:** Add a meta-rule that fails the build when a slice is missing from SLICES, and parameterize `APPLICATION_IS_EDGE` and `PACKS_DOMAIN_IS_PURE` over SLICES.
- **Problem:** The meta-rule landed (SLICE_COVERAGE / `EVERY_SLICE_IS_REGISTERED` with a biting control, PR #141), and SLICES is now packs, profiles, missions. Domain purity is three hand-written rules (PACKS_/`PROFILES_/MISSIONS_DOMAIN_IS_PURE`), `APPLICATION_IS_EDGE` is still pinned to packs.application, and DeployableConfiguration's @`ComponentScan` is a third hand-kept list. The body's premise 'exactly one slice' is out of date.
- **Proposed scope:**
  - Replace the per-slice `*_DOMAIN_IS_PURE` constants with one rule generated over SLICES; each slice keeps a known-bad control.
  - Generate `APPLICATION_IS_EDGE` over every registered slice that has an application ring (today only packs); a slice without one is skipped, not failed.
  - Define 'slice' once (ArchUnit slices() or one helper) so SLICES, `NON_SLICE_OWNERS` and `domainSliceOwner` no longer encode it separately.
  - Assert each registered slice's config packages are in DeployableConfiguration's @`ComponentScan`, with a biting control.
  - Fail loudly if a service module exists that deployable does not depend on (the classpath assumption in the 2026-10-22 comment, item 4).
- **Drop:**
  - The meta-rule itself (delivered by PR #141).
  - 'With exactly one slice (packs)' and 'before the second slice exists' framing.
- **Evidence:** HexagonalArchitectureTest.java:68 SLICES = packs, profiles, missions; :142-160 three hand-written `DOMAIN_IS_PURE` rules; :240 `APPLICATION_IS_EDGE` on `PACKS_APPLICATION` only; :524-566 `SLICE_COVERAGE` + control; :503 `NON_SLICE_OWNERS`; DeployableConfiguration.java:19 @`ComponentScan` lists packs packages only; issue comment 2026-10-22 items 1-4; PR #141

### Issue #185

**Pack commit swap: readers in the swap window, staging leftovers, repair by pack_id**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Pack commit store: remove leftover `.pack-commit-*` and `.pack-discard-*` residue
- **Today:** Three hardening items after PR #180: readers in the swap window, cleanup of `.pack-commit-*` staging leftovers, and repair when a pack is addressed by `pack_id`.
- **Problem:** PR #302 (closes #281) delivered items 1 and 3: the repair runs on the listing in StorageRootLocations.all(), before id resolution, and a read meeting an in-flight swap waits for it (at most 10 s). Item 2 remains: nothing deletes a `.pack-commit-*` staging directory left by a crash; StorageRootLocations only hides it. PR #302 added a second residue, `.pack-discard-*`, which a failed delete leaves behind (PackCommitStore:419).
- **Proposed scope:**
  - On startup, before the first listing, delete `.pack-commit-*` and `.pack-discard-*` entries in the packs root (and beside a linked pack's real target that the service may write).
  - Rely on the single writer process per pack root (#183) so startup cleanup cannot remove another process's in-flight staging; state that in the PackCommitStore javadoc and ADR-0038.
  - A delete that fails is logged once and never fails the start.
  - One focused test per residue kind, plus one showing a live pack and its `.pack-old-*` are untouched.
- **Drop:**
  - Item 1, readers in the swap window (PR #302).
  - Item 3, repair by `pack_id` (PR #302: repair runs on the listing).
- **Evidence:** PR #302 body: 'The repair runs on the listing', 'A read that meets the half-swapped state ... waits for the swap'; PackCommitStore.java:169 `createTempDirectory` '.pack-commit-'; StorageRootLocations.java:47 hides `.pack-commit-*`; PackCommitStore.java:419-426 `.pack-discard-*`; ADR-0038:254

### Issue #196

**Protect `contract-charter-v*` tags with a ruleset before the first contract release**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Protect `contract-charter-v*` and `service-v*` tags with a ruleset before the first release
- **Today:** Add a tag ruleset on `contract-charter-v*` limited to the operator, optionally a publish environment with a reviewer, and name it in `contracts/lint/README.md`.
- **Problem:** Still undone: the only ruleset is 'Protect main' (branch) and no tag exists. service-release.yml (ADR-0046 decision on tags) has the same tagged-commit trust gap and relies on a `service-v*` ruleset that does not exist. The README note asked for is already written, so the docs now describe a ruleset that is missing.
- **Proposed scope:**
  - One tag ruleset (or two) on `contract-charter-v*` and `service-v*` that lets only the operator create, move or delete those tags.
  - Optionally, put the publish jobs of contract-release.yml and service-release.yml behind an environment with a required reviewer.
  - Verify with the REST API that the ruleset is active and targets both patterns; record the ruleset id in the release procedure.
  - Done before the first contract-charter-v0.1.0 or `service-v*` tag.
- **Drop:**
  - 'note the ruleset in `contracts/lint/README.md`' (already there, line 109).
- **Evidence:** GET `/repos/.../rulesets`: only 24998670 'Protect main' (branch); no tags in the repo; `contracts/lint/README.md:109`; `.github/workflows/service-release.yml:17-18`; `docs/adr/0046:147`; `docs/guides/service-build.md:318`; `docs/plans/milestones.md:77`

### Issue #205

**Re-render class-diagram and c4-components PlantUML for the ADR-0035 kernel move**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Refresh class-diagram and c4-components PlantUML: kernel move plus the profiles and missions slices
- **Today:** Move CharterArtifact and PackIdentifier into kernel.domain in the two diagrams and re-render them in CI's environment only.
- **Problem:** Still stale: class-diagram.puml keeps CharterArtifact (:79) and PackIdentifier (:125) in packs.domain, and both diagrams draw only kernel and packs, while `service/profiles` and `service/missions` slices exist (ADR-0044). The 'render only in CI' constraint is out of date: `check_docs.py` now compares SVGs semantically, ignoring fonts and geometry (fb5ac98, `_normalise_svg`), and the job uploads its renders on failure.
- **Proposed scope:**
  - class-diagram.puml and c4-components.puml place CharterArtifact and PackIdentifier in kernel.domain; the five concrete entities stay in packs.domain.
  - Both diagrams add the profiles and missions slices (domain rings only today) over the shared kernel.
  - Re-render with the pinned jar from `scripts/docs/plantuml_pins.json` plus Graphviz; 'PlantUML diagrams are current' is green on the PR.
- **Drop:**
  - 're-rendered in CI's environment (not locally)' and the font-drift explanation (superseded by the semantic comparison).
- **Evidence:** `docs/architecture/diagrams/class-diagram.puml:65`,79,125; c4-components.puml:19,31-38; `service/profiles`, `service/missions` at `origin/main`; `scripts/docs/check_docs.py:643` `_normalise_svg` (added fb5ac98, 2026-11-02); `.github/workflows/docs-cleanliness.yml:140-176`; ADR-0035, ADR-0044

### Issue #210

**Contract scenarios for `GET /openapi.yaml`**. Kind `factual`, recommendation: close instead, confidence: medium.

- **Today:** Contract scenarios for byte identity and ETag, 401, 403, 304, closed responses, and a neighboring unknown path answered 404.
- **Problem:** The triage reason is too generous to what remains. OpenApiDocumentTest covers byte identity, sha256 ETag, 304, 401, 403, HEAD and 406. The closed-response check already runs on every request a scenario sends (README: content type and schema per declared status), including service`.feature` following service-desc to `/openapi.yaml`; and errors`.feature` and root`.feature` pin that an undefined path answers the shared 404 Problem. What is not covered is a header allow-list and a 404 for a path next to `/openapi.yaml` specifically.
- **Proposed scope:**
  - Close as covered by #209's tests and the existing contract scenarios.
  - If the operator wants more: one @contract scenario that GETs `/openapi.json` (or `/openapi.yml`) and expects the shared 404 Problem.
- **Drop:**
  - Byte identity, 401, 403, 304 items (OpenApiDocumentTest).
  - Closed-response item (ContractConformance on every request).
- **Evidence:** `service/deployable/src/test/java/ai/speckitty/charter/deployable/OpenApiDocumentTest.java:87-189`; `contracts/charter/scenarios/service.feature:44-55`; `contracts/charter/scenarios/README.md:128-136`; errors`.feature`:27-32; root`.feature`:88-94; #208, #209 closed
- **Question:** Close #210 as covered, or keep it for one neighboring-path 404 scenario (and, if wanted, a check that no undeclared header appears)?
- **Applied, 2026-11-04:** closed as covered, without the neighboring-path scenario or the header check.

### Issue #211

**Add IntelliJ HTTP Client examples (`contracts/charter/examples/*.http`) and env files**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Add IntelliJ HTTP Client requests (`contracts/charter/http/*.http`) and env files
- **Today:** Hand-written .http requests in `contracts/charter/examples/`, a public env file with local and hosted URLs, a `gitignored` private env file for the token, and a secret-scan rule.
- **Problem:** Not started. `contracts/charter/examples/` already holds 26 OpenAPI example payloads (*.yaml), so the named directory would mix two kinds of file. A secret scan already exists as ShippedConfigurationTest (tracked-path and value checks) and .gitignore covers .env files only; the new private env file should join those, not a new tool. 'hosted' as an environment name sits beside ADR-0041's container profile.
- **Proposed scope:**
  - Requests in their own directory (proposed `contracts/charter/http/`), starting with GET `/openapi.yaml` and the read operations; each sends Authorization: Bearer {{token}} and asserts status and Content-Type.
  - Public http-client.env.json with local and container base URLs.
  - http-client.private.env.json is in .gitignore; ShippedConfigurationTest's tracked-path check refuses it if committed, with a positive control.
  - #212 and #214 updated to the same directory.
- **Drop:**
  - `contracts/charter/examples/` as the location.
  - 'Add a secret-scan rule' as a separate tool.
- **Evidence:** git ls-tree `origin/main` `contracts/charter/examples` (26 `*.yaml`); no `*.http` or `http-client*` file at `origin/main`; .gitignore:41-44; ShippedConfigurationTest.java:300-345; ADR-0041 D1; #212, #214 open
- **Question:** Which directory for the .http files: `contracts/charter/http/` (proposed) or another?

### Issue #220

**Release: attach Java client zip + .sha256 to the contract release and verify it**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Release: attach the Java client sources zip + .sha256 to the contract release and verify it
- **Today:** Build a client zip (jar, sources and javadoc, pending #217), attach it and its .sha256 to the `contract-charter-v*` release, and verify it in the release job.
- **Problem:** The later operator ruling on #217 (2026-10-27 11:09, superseding the 07:43 one) makes the zip sources only: generated sources, the Problem helper source, LICENSE, README, .sha256; jars move to #225. :client-java does not exist yet (#218 open).
- **Proposed scope:**
  - Build charter-client-<version>-sources.zip holding the generated sources, the Problem helper source, LICENSE and a README, plus its .sha256.
  - `scripts/release/contract-release-assets.sh` adds both to the release assets; contract-release.yml lists them in gh release create.
  - The verify job runs sha256sum --check --strict on the zip and fails when it is missing or mismatched.
  - `scripts/release/rehearse-contract-release.sh` produces and checks the zip.
- **Drop:**
  - jar, sources jar and javadoc jar (moved to #225).
  - 'per the open question on #217' (answered).
- **Evidence:** #217 comment 2026-10-27T11:09 'Release zip contents: sources only ... This changes #220's scope'; no `client-java/` at `origin/main`; #218 open; `scripts/release/contract-release-assets.sh`; `.github/workflows/contract-release.yml:117-153`

### Issue #226

**Hosted service configuration (profiles, validated properties, secrets; no Spring Cloud Config)**. Kind `factual`, recommendation: re-scope + retitle, confidence: medium.

- **New title:** Containerized service configuration (container profile, validated properties, secrets; no Spring Cloud Config)
- **Today:** Epic for the hosted configuration model: hosted profile, `charter.hosted.*` validated record, secrets from env or `configtree`, guards, metadata table, actuator, multi-instance spike.
- **Problem:** ADR-0041 (accepted 2026-10-29, for #227) named the profile 'container', the record `charter.container.*`, the file application-container.yaml; the epic still says hosted, `charter.hosted.*` and application-hosted.yaml. #227, #228 and #229 are closed but only #229 is ticked. The three open questions were answered by the 2026-10-27 rulings recorded in ADR-0041. Later children #121, #183 and #268 are not listed.
- **Proposed scope:**
  - Add an 'Updated 2026-11-03' note: the model is ADR-0041; profile 'container', record `charter.container.*`, application-container.yaml; 'hosted' stays the deployment-option name (ADR-0041 D1).
  - Tick #227, #228, #229; list #121, #183, #268 and #234 as children.
  - Replace the Decision section with a pointer to ADR-0041 and mark the open questions answered (platform-neutral container; tenancy in #233; no runtime-changed settings).
  - Retitle open children that name the profile: #231 and #232 ('hosted' to 'container profile'); leave #233, which is about the hosted deployment option.
- **Drop:**
  - The architect-assessment Decision text, now ADR-0041.
  - The 'Open questions (operator)' checklist.
- **Evidence:** `docs/adr/0041-containerized-configuration-model.md` (Status, Context, D1-D3); `service/deployable/src/main/resources/application-container.yaml`; ContainerProperties.java; #227, #228, #229 closed; #230-#234, #268 open
- **Question:** ADR-0041 D1 keeps 'hosted' as the deployment-option name. Retitle the epic to 'Containerized service configuration' (proposed), or keep 'Hosted' in the title and fix only the body and #231/#232?

### Issue #231

**Profile guard tests: loopback invariant locally, missing-secret failure hosted**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** Local profile refuses a non-loopback bind (ADR-0041 D4), with bind tests under both profiles
- **Today:** A start-up assertion tying the bind address to the profile in both directions, and tests for default bind, wrong binds, missing secret under hosted, and hosted not active by default.
- **Problem:** Most is delivered: missing token and missing secrets directory fail the start (`GuardWiringTest`:401, ContainerSecretsTest:154, `ContainerPropertiesTest`:151); application.yaml binds 127.0.0.1:0 with no spring.profiles key (ShippedConfigurationTest:223); the container profile binds 0.0.0.0:8080 (ContainerSecretsTest:201). Missing: `ContainerStartupCheck` runs only under the container profile, so nothing refuses a non-loopback bind locally. The body's 'hosted with a loopback address fails' contradicts ADR-0041 D4, which allows loopback inside a container.
- **Proposed scope:**
  - A start-up check without the container profile refuses a server.address that is not a loopback address, naming the property; it stays eager under lazy initialization like `ContainerStartupCheck`.
  - Tests: local with 0.0.0.0 or a LAN address fails; local with 127.0.0.1 or ::1 starts; container with 127.0.0.1 starts (ADR-0041 D4).
  - Host-check tests under both profiles stay with #268, which coordinates with this issue.
- **Drop:**
  - 'hosted with a loopback address fails' (contradicts ADR-0041 D4).
  - Missing-secret tests (delivered).
  - 'hosted is never active by default' test (ShippedConfigurationTest:223, `NoBuildTimeProfilesTest`).
  - The 'hosted' profile name.
- **Evidence:** `ContainerStartupCheck`.java `afterPropertiesSet` returns early without the profile; no loopback check in `service/deployable/src/main`; `GuardWiringTest`.java:318,401; ContainerSecretsTest.java:154,201; `ContainerPropertiesTest`.java:151; ShippedConfigurationTest.java:223-241; ADR-0041 D4

### Issue #243

**Report three spec-kitty CLI (4.0.0rc8) charter-synthesis defects upstream to spec-kitty/spec-kitty**. Kind `factual`, recommendation: re-scope, confidence: low.

- **Today:** File three rc8 charter-synthesis defects on `spec-kitty/spec-kitty`, link them here, and confirm synthesize again works without the local patch once fixed.
- **Problem:** Per the 2026-11-02 triage, defect 1 may already be `spec-kitty/spec-kitty`#6768; defects 2 and 3 have no upstream issue. I could not confirm #6768: this session has no read access to `spec-kitty/spec-kitty`. The issue has no comments, and the installed CLI is still 4.0.0rc8.
- **Proposed scope:**
  - Add a note linking `spec-kitty/spec-kitty`#6768 for defect 1, once confirmed to be the same defect.
  - File defects 2 (legacy-rename collision check) and 3 (charter generate drops styleguides) upstream and link them.
  - Keep the second acceptance criterion: plain synthesize again --topic `directive:PROJECT_002` succeeds without the local patch.
- **Evidence:** triage-2026-11-02.md:193; gh api `issues/243/comments` = 0; spec-kitty --version = 4.0.0rc8; `spec-kitty/spec-kitty` not readable from this session (403)
- **Question:** Is `spec-kitty/spec-kitty`#6768 the same defect as defect 1 (additive-only check on synthesize again of an activated `PROJECT_` directive)? I could not read it.

### Issue #254

**PackPolicy: report an org-fragment `body_path` that escapes the pack root (parity with core spec-kitty#6418)**. Kind `scope-change`, recommendation: re-scope + retitle, confidence: high.

- **New title:** PackPolicy: refuse a fragment `body_path` that escapes the pack root
- **Today:** Add a blocking `FRAGMENT_BODY_PATH_ESCAPE` finding matching core's doctor check, add a shared conformance-corpus fixture, and sequence it with core's validator.
- **Problem:** The check is still missing: PackPolicy lists `body_path` in `FRAGMENT_NODE_KEYS` (:90) but only assets get a containment check (ASSET_PATH_ESCAPE, :302). The core-parity framing and 'sequence with core' conflict with the 2026-10-28 ruling on #186, and ADR-0055 D3 replaces the conformance corpus with design-derived fixture packs.
- **Proposed scope:**
  - A blocking PackFindingCode (e.g. `FRAGMENT_BODY_PATH_ESCAPE`) for a fragment node whose `body_path` is absolute (`POSIX`, Windows, UNC), contains '..', or resolves outside the pack root, with the same semantics as the asset escapes().
  - A fixture pack per refusal form, plus one inside-the-root `body_path` that passes.
  - The finding names the file and the node id.
- **Drop:**
  - 'Parity with core spec-kitty#6418' in the title and the core-behavior paragraph as the reason.
  - 'Sequence this with the core validator follow-up'.
  - 'shared conformance-corpus fixture' (ADR-0055 D3).
- **Evidence:** `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java:90`,294-310; PackFindingCode.java:28; #186 operator ruling 2026-10-28; ADR-0055 D3
