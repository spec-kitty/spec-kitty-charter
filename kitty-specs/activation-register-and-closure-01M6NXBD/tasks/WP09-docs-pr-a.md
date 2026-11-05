---
work_package_id: WP09
title: Documentation and divergences for PR A
dependencies:
- WP08
requirement_refs:
- C-006
- FR-008
- FR-007
- FR-023
- C-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T057
- T058
- T059
- T060
- T061
agent: claude
history: []
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-05-activation-register-and-closure.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/conformance/divergence-register.md
- docs/conformance/divergences.yaml
- docs/adr/0020-offering-and-activations.md
- docs/adr/0013-persistent-deactivation.md
- docs/adr/0012-pack-mutability-policy.md
- docs/architecture/offering-and-activation.md
- docs/architecture/logical-model.md
- docs/architecture/resolution-model.md
- docs/changelog/2026-11-05-activation-register-and-closure.md
role: implementer
tags: []
tracker_refs: []
---

# WP09: Documentation and divergences for PR A

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make the docs say what PR A ships, and record every deliberate difference from core (C-006):

- divergence entries DIV-203 to DIV-205, in both register forms:
  - absent means none, skill included;
  - a deny entry on a required or locked artifact is refused at write;
  - the required floor is closure-scoped;
- amendment notes on ADR-0020 D6, ADR-0013 D2 and ADR-0012 D2 (pointing to ADR-0061);
- `docs/architecture/offering-and-activation.md`, which no longer says the CRUD "answers 501", plus `logical-model.md`
  and `resolution-model.md` where they describe the activation or the compose roots;
- a docs changelog fragment for PR A, including the #360 acceptance re-point.

Docs only: this WP changes no code, contract or test.

Implementation command:
`spec-kitty agent action implement WP09 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** A (its last WP). **Concern:** IC-10 (A half). **Research:** "Open review items"; squad B3, B8, B9.
- **Rulings:** ADR-0020 (D4, D6, D7, D9), ADR-0013 D2, ADR-0012 D2 and D7, ADR-0053 d10, ADR-0059 D1 (the register
  informs and does not gate), D4 and D5, and ADR-0061 (written in WP03).
- **Collisions:** PR #390 touches `docs/architecture/hexagonal-service.md` (not this WP). PR #376 adds ADR-0059;
  links to `/adr/0059-switch-gate-cascade-edges-and-activation-rulings/` resolve only after it merges. If it has not,
  cite it by number without a link, and add the link in the rebase.
- **Grounding:**
  - The register has **two forms that a test keeps equal**: `docs/conformance/divergence-register.md` (tables) and
    `docs/conformance/divergences.yaml` (one item per id: `id`, `surface`, `core`, `service`, `rationale`, `ruling`,
    `status` and the rest of its schema; read the header comment of the YAML). `DivergenceRegisterTest`
    (`service/packs/src/test/.../infra/storage/`) fails when they differ.
  - The activation block is headed "Activation (DIV-201..202, ADR-0020), proposed", and the summary line near the top
    (~line 38) repeats the range. Both change to cover DIV-203 onwards.
  - `offering-and-activation.md` (~lines 70 and 195) says every activation resource answers `501` and that one pin
    per operation is bound.
  - Changelog fragments carry `okf_version` frontmatter (see
    `docs/changelog/2026-11-05-nightly-sonarcloud-report.md`) and are listed by a generated index; never edit
    `docs/changelog/README.md`.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`.
- Lane worktree from `lanes.json`. It depends on WP08, so the docs describe merged behaviour.
- Rebase onto the lane base before review.

## Subtask Guidance

### Subtask T057: Divergence entries DIV-203 to DIV-205

**Steps:**
1. Read the register's "how to add an entry" paragraph (~line 418) and follow it: the next free id, both answers, the
   corpus case and the ruling.
2. Add the rows in `divergence-register.md`'s activation table, and items in `divergences.yaml` with the same ids:
   - **DIV-203:** an absent `selected.<kind>`, `activatedKinds` or `missionTypes` means none, the same as `[]`; for
     `skill` too, which loses its "only the required skills" default. Core: per-kind default (every artifact, or
     required skills only). Ruling: ADR-0053 d10, decision `DM-01M6NXQJ61D2GSFJFK02XW6F9N`. It holds until core's
     named-packs split; say so. Corpus case: `absent-kind-means-none`.
   - **DIV-204:** a write whose deny list names a required or locked artifact is refused `409` (`artifact_required`,
     `artifact_locked`), and nothing is stored. Core: accepts and ignores the entry, or warns. Ruling: ADR-0020 D9,
     ADR-0059 D4. Corpus case: `deny-required-refused-at-write`.
   - **DIV-205:** the required floor is the must-have items of every governing preset in the **activation's
     closure**. Core: org `required_*` lists. Ruling: planning decision `DM-01M6PCWSA65497T9A7J80K6V56`, an assumed
     default flagged to the operator (open review item 1). Corpus case: `floor-closure-scoped`.
3. Status: follow the block's convention. The block is "proposed". Now that the operations are built, decide with
   the ADR-0059 D1 reading (the register informs, it does not gate) whether these become `open`. Keep DIV-201/202 as
   they are unless the code now serves them. It does not: the effective reads are reserved.
4. Rename the block heading and the summary line to the new range.
5. Run `./gradlew :packs:test --tests '*DivergenceRegister*'`.

### Subtask T058: ADR amendment notes

**Steps:** add a dated "Amendment (2026-11-05, #291/#360)" note at the end of each record's Decision, in that file's
existing amendment style (look for earlier "Amendment" headings in `0020-offering-and-activations.md`):
1. **ADR-0020 D6:** "an empty document means every kind's default" becomes "nothing is in force" (absent means none,
   DIV-203). The round trip holds for a document the offering has not made stale (ADR-0059 D4).
2. **ADR-0013 D2:** "absent means all" becomes "absent means none". The `force` interplay: a forced preset clears
   only the deny entries that match its members (ADR-0059 D6).
3. **ADR-0012 D2:** the per-pack policy file is now specified by ADR-0061 (`mutability_policy`, `policy.yaml`, the
   highest level along the closure). D5's built-in file and migration remain follow-ups.
4. Bump each file's `updated:`; do not change `decision_status`.

### Subtask T059: Architecture docs

**Steps:**
1. `offering-and-activation.md`:
   - the CRUD and the closure read are built; the five per-artifact writes and the four effective reads still answer
     `501` (until PR B and the resolution mission). Name `activation-register.feature` as the behavioural scenarios;
   - the closure read: `GET /activations/{id}/packs`, rooted at the activation's `packs`, in dependency order, in the
     offering's pack shape; `/offering` stays the available catalog;
   - storage: `<config-root>/activations/<id>/activation.json`, the JCS canonical bytes, an atomic move, one process
     (#349);
   - one verified closure snapshot per request (NFR-007).
2. `logical-model.md`: the activation entity's three-state lists (absent and `[]` mean none), the revision, and the
   closure as the only packs a document can name.
3. `resolution-model.md`: compose roots are the activation's packs (the closure); the offering composes every
   configured pack; the read-time pull and the layer merge are the next mission.
4. Add a mermaid diagram only if it replaces prose. The research R1 flowchart is a good candidate for
   `offering-and-activation.md`.

### Subtask T060: The docs changelog fragment

**Steps:**
1. Create `docs/changelog/2026-11-05-activation-register-and-closure.md` with the `okf_version` frontmatter: `title`,
   `description`, `doc_status: active`, `updated`, `type: Changelog`, `kind: Changelog`, `related` (ADR-0020,
   ADR-0059, ADR-0061, `architecture/offering-and-activation`) and `audience`.
2. Body: what core can now do (store, read, replace and delete an activation; read its closure), what is still `501`,
   the policy file (ADR-0061) with the open-packs note that no pack is required to ship one, and the divergences
   DIV-203 to DIV-205.
3. **#360 acceptance re-point** (a paragraph): #360's "absent from the offering" acceptance is met by the closure
   read, not by `/offering`, which stays the catalog (decision `DM-01M6NXNBA1J808JV4SHT743SCF`). The same paragraph is
   the text for the issue comment when PR A opens. Put it in the review notes too, so the orchestrator can paste it.

### Subtask T061: Docs gates

**Steps:**
```bash
python3 scripts/docs/check_docs.py all
npx cspell@9.2.0 --no-progress
./gradlew :packs:test --tests '*DivergenceRegister*'
```
A new word that fails spelling: reword, or record a `cspell.json` addition as an out-of-map edit in the review notes
(it is outside `docs/`).

## Definition of Done

- [ ] DIV-203 to DIV-205 are in both register forms with the same ids and status; `DivergenceRegisterTest` passes.
- [ ] ADR-0020 D6, ADR-0013 D2 and ADR-0012 D2 carry dated amendment notes.
- [ ] `offering-and-activation.md`, `logical-model.md` and `resolution-model.md` match PR A's behaviour; no "answers
      501" claim remains for the built operations.
- [ ] The changelog fragment exists with valid frontmatter, and the #360 re-point text is in it and in the review
      notes.
- [ ] The docs checks and spelling pass.

## Risks

- **Overclaiming.** The effective reads, the enhances pull and the layer merge are not built. Every sentence about
  them says "next mission".
- **ADR-0059 links** before #376 merges break the link check. Cite by number until then.

## Reviewer guidance (reviewer-renata)

- **Check both register forms** carry the same three ids, and run `DivergenceRegisterTest` yourself.
- **Check each divergence has both answers** (charter and core), a ruling and a corpus case name.
- **Grep for stale claims:** `git grep -n "answers 501\|answer 501" docs/architecture/offering-and-activation.md`
  must hit only the still-reserved operations.
- **Non-fakeable evidence:** the `check_docs.py all` output and the cspell summary line.
