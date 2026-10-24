---
work_package_id: WP01
title: Record the verified fingerprint in the docs and the contract prose
dependencies: []
requirement_refs:
- FR-011
- C-001
- C-002
- C-005
planning_base_branch: fix/verified-source-fingerprint
merge_target_branch: fix/verified-source-fingerprint
branch_strategy: Planning artifacts for this mission were generated on fix/verified-source-fingerprint. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/verified-source-fingerprint unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-verified-source-fingerprint-01M5PG7Q
base_commit: 06dc39cb6f990eb5b920c9361856863e0153f83d
created_at: '2026-10-24T08:51:31.456976+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
phase: Phase 1 - Record the design
history:
- at: '2026-10-24T09:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent: []
execution_mode: code_change
model: sonnet
owned_files:
- docs/adr/0018-action-context-read-and-seam-access-surface.md
- docs/architecture/service-lifecycle.md
- docs/api/service-contract.md
- contracts/charter/headers/CharterSourceFingerprint.yaml
- contracts/charter/schemas/ServiceInfo.yaml
- contracts/charter/schemas/CharterRefusal.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/responses/TacticNotFound.yaml
- contracts/charter/paths/packs_packId_tactics.yaml
- contracts/charter/paths/packs_packId_tactics_tacticId.yaml
- contracts/charter/CHANGELOG.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 — Record the verified fingerprint in the docs and the contract prose

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

The operator's brief requires the design to be recorded **before** code. This WP changes words
only: docs, one ADR amendment, and `description` text in the contract. No schema, operation,
parameter, response or header **declaration** changes (spec C-001).

Read first, in this order: [spec.md](../spec.md) (Intent Summary, Edge Cases, Key Entities, Open
decisions), [plan.md](../plan.md) (Summary, Design details, Global digest consequence),
[research.md](../research.md) (R1-R3, R7), [data-model.md](../data-model.md) (digested file list,
refusal outcomes). Also ADR-0011, ADR-0018 (D8, D10, D11, Risks), ADR-0020 D12 and ADR-0022.

Done means:

- One definition of the source fingerprint appears everywhere (copy it, do not paraphrase it):
  *per configured pack in order, `pack.yaml`, every loaded file (tactic, directive, styleguide and
  paradigm files, at any depth) and the declared graph inputs (root `*.graph.yaml` shards, then
  `drg/fragment.yaml`), each as its path under the pack's configured name and its bytes; and, when
  configured, `config.yaml` and `charter.yaml` under the project configuration directory, with
  presence.*
- Every built artifact read (`getServiceInfo`, `listPackTactics`, `getPackTactic`) is described as
  digesting before and after loading, answering 200 with that digest once, and refusing with
  `stale_input` when the two differ, including when the load itself refused.
- A configured pack whose directory is missing, or a pack holding an unreadable file or an
  unlistable directory, is described as 500 `pack_unreadable` on every read.
- Docs checks and contract checks are green.

## Context & Constraints

- **Behavior lands in WP02 and WP03.** This WP describes the behavior they build. The words must
  match plan.md exactly; if you find a contradiction, stop and report it rather than inventing.
- **Graph inputs stay in the digest** although no served read loads them (spec Assumptions, Open
  decisions). Say so plainly; do not claim the digest equals the loaded set.
- **Config inputs stay** (#127, ADR-0020 D12): do not remove `config.yaml`/`charter.yaml` from any
  description; ADR-0020 D12 already records that they go before 1.0.
- **Writing style**: the repository's docs use short declarative sentences, American spelling, no
  marketing words. Match the surrounding text. Front matter `updated:` fields follow the existing
  convention (bump to `2026-10-24T00:00:00Z` where a page has one and you change it).
- The contract is 0.x (ADR-0022): description changes are not breaking, but
  `:contracts:checkContractCompatibility` must stay green.

## Branch Strategy

- Planning/base branch: `fix/verified-source-fingerprint`; final merge target:
  `fix/verified-source-fingerprint` (the mission PR then goes to `main`).
- Execution worktrees are allocated per computed lane from `lanes.json`. Run
  `spec-kitty agent action implement WP01 --agent claude --mission verified-source-fingerprint-01M5PG7Q`
  and work only in the worktree it gives you.

## Subtasks

### T001 — ADR-0018 dated amendment

**Purpose**: ADR-0018 D8 and D11 already state the rule ("every pack file the service loads";
"every operation that reads inputs digests them before and after"). The amendment records how it
is now built and corrects the Risks bullet that names a class PR #139 deleted.

**Steps**:
1. Under `## Status`, after the existing `**Amendment 2026-10-20**` paragraph, add
   `**Amendment 2026-10-24** (#120, #173): ...` in the same style: one short paragraph naming
   what changes and that points are annotated where they stand.
2. Annotate D8 with an italic dated note, as the 2026-10-20 amendment did in D11:
   - the fingerprint's pack inputs are now `pack.yaml`, every file of a loaded kind and the
     declared graph inputs, taken from one file-set authority in the storage ring that the load
     also reads from, so a kind cannot be loaded without being digested;
   - the graph inputs stay digested although no served read loads them yet (until the graph read,
     #73), with the same accepted consequence D8 records for the two config files.
3. Annotate D11:
   - `/info` and both tactic reads now share one verified-read rule in the application ring (the
     role `ConsistentLoad` had before PR #139);
   - a refusal of the load (`pack_not_found`, `tactic_not_found`) whose inputs changed meanwhile
     is `stale_input`;
   - the first digest is global, so a missing pack directory or an unreadable file in any
     configured pack refuses every read with `pack_unreadable`;
   - the residual ABA window now covers tactic files too.
4. In `### Risks`, replace the bullet "Nothing yet pins that the fingerprint digests exactly the
   files the load reads ... is issue #120" with one that says it is pinned by a generated-tree
   exactness test with a planted omission, and names the remaining risk: a storage read of a file
   outside the declared kinds is caught by that test, not by the type system.
5. Bump `updated:` in the front matter.

**Validation**: `python3 scripts/docs/check_docs.py all` clean; the decision text of D8 and D11
stays verbatim (annotations are added, not rewritten).

### T002 — Freshness in `docs/architecture/service-lifecycle.md`

**Purpose**: The `## Freshness` section (about lines 72-96) is the reader's statement of the rule.

**Steps**:
1. Replace the first bullet's input list with the one definition above.
2. State that every read verifies its inputs twice and answers with the verified digest, and
   that a read whose load refused while the inputs changed is `stale_input`.
3. Remove or correct the `POST /packs/refresh` bullet: PR #139 removed the operation (check
   `contracts/charter/paths/` and `contracts/charter/openapi.yaml`; there is no refresh path).
   Say refresh is not served in this version, if you keep a mention at all. Keep the change
   minimal and inside this section and the version-handshake bullet above it.
4. Add the global-digest consequence: a broken or missing configured pack refuses every read.

**Validation**: no other section changes; docs checks clean.

### T003 — Built surface and headers in `docs/api/service-contract.md`

**Steps**:
1. The status note (line ~23), the operations table (line ~47) and the later mentions (lines
   ~119 and ~149) claim a refresh is built. Make
   them name what is served: `getServiceInfo`, `listPackTactics`, `getPackTactic`. Do not touch
   designed-only operations beyond what the table says is built.
2. The headers paragraph (line ~53) and the fingerprint bullet (line ~87): use the one
   definition; say every built read sets `Charter-Source-Fingerprint` once on its 200 and no
   refusal does.
3. The `stale_input` description: every built read, not only `/info`.

**Validation**: docs checks clean; no designed-only operation is described as built.

### T004 — Contract prose (descriptions only)

Edit only `description` (and the one `x-source` path noted below). Files:

1. `headers/CharterSourceFingerprint.yaml`: the one definition; "On every 200 answer of an
   artifact read, the digest that read verified ..." (keep "Never on a refusal or a failure; set
   once").
2. `schemas/ServiceInfo.yaml`, `sourceFingerprint.description`: the one definition, including the
   order (packs in configured order; within a pack `pack.yaml`, loaded files by kind then path,
   root shards by name, `drg/fragment.yaml`). Fix the dead `x-source` near line 36 that points at
   `service/charter-read-app/...ServiceInfoQuery.java`: the class is
   `service/packs/src/main/java/ai/speckitty/charter/packs/application/ServiceInfoQuery.java`.
   Check every other `x-source` in that file points at an existing file and symbol; fix only the
   broken ones.
3. `schemas/CharterRefusal.yaml` / `schemas/CharterRefusalCode.yaml`: wherever `stale_input` is
   described as raised by the service info only ("The service info digests its inputs twice"),
   name the tactic reads too. The `pack_not_found` description must no longer say "or
   unreadable" for a configured pack directory that is missing: that is now `pack_unreadable`
   (500). A pack directory that exists but whose `pack.yaml` is missing or invalid still answers
   404 on tactic reads only if the store refuses after the digest; check
   `TacticReadService.packRefusal` and describe exactly: an identifier that names no configured
   pack is `pack_not_found` (tactic reads never open `pack.yaml`; directory and listing failures
   are caught by the first digest as `pack_unreadable`, and a race is `stale_input`).
4. `responses/TacticNotFound.yaml`: same correction for "absent or unreadable".
5. `paths/packs_packId_tactics.yaml` and `paths/packs_packId_tactics_tacticId.yaml`: the
   operation `description` says "A pack that is absent or cannot be read is a 404"; align it with
   item 3 and add one sentence: the answer is verified against the source fingerprint and carries
   it; inputs that change while it is read are `stale_input`.
6. **Do not** change `x-refusal` maps, `required`, schemas, `$ref`s, response codes or examples.
7. **Pinned phrases** — `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractDescriptionsTest.java`
   asserts these substrings; keep each verbatim (you may add sentences around them):
   - in `CharterRefusalCode.yaml`: `internal_error is a fault of the service and may be retried`,
     `stale_input is raised: the packs or the project configuration changed while the answer was computed, so the answer and the Charter-Source-Fingerprint it would report might disagree`,
     `The read may be retried`;
   - in `responses/OfferingUnreadable.yaml`: `stale_input, which may be retried`.
   Read the whole test first; it may pin more.

**Validation**: `./gradlew :contracts:check` and
`./gradlew :deployable:test --tests '*ContractDescriptionsTest*'` green (bundle, lint, closed
responses, compatibility, pinned phrases). `git diff contracts/charter -- ':!contracts/charter/CHANGELOG.md'` shows only
`description:` text and the `x-source` path.

### T005 — Contract CHANGELOG and checks

**Steps**:
1. Read the top of `contracts/charter/CHANGELOG.md` for its format (sections, provisional
   blocks, how scenarios are listed). Add under the unreleased section:
   - **Fixed**: `listPackTactics` and `getPackTactic` send `Charter-Source-Fingerprint` on 200,
     the digest the read verified; they refuse with `stale_input` when their inputs change while
     they are read (#173). The source fingerprint covers the tactic, directive, styleguide and
     paradigm files the service loads (#120).
   - **Changed**: a configured pack whose directory is missing, or a pack with an unreadable file,
     refuses every read with 500 `pack_unreadable` (a tactic read answered 404 `pack_not_found`
     before). A load refusal whose inputs changed meanwhile is `stale_input`. The fingerprint's
     value changes for packs that hold loaded-kind files.
   - **Scenarios**: list the new scenarios by these exact titles (WP03 corrects the entry if a
     title changes when it lands):
     - `service.feature`: "The source fingerprint follows a tactic file";
     - `tactics.feature` (new feature, "The tactic reads answer with the source fingerprint they verified"):
       "A tactic list answers with the source fingerprint it verified",
       "One tactic answers with the source fingerprint it verified",
       "A tactic list whose tactic file changes while it is answered is refused with stale_input",
       "One tactic whose file changes while it is answered is refused with stale_input",
       "A read of an absent tactic whose inputs change is stale, not not-found",
       "A tactic read that watches for a change and sees none answers with the digest it verified",
       "A tactic read of a pack whose directory is missing is refused with pack_unreadable",
       "A missing sibling pack directory refuses a tactic read of a healthy pack".
2. Run the docs checks from the repository root:
   `python3 scripts/docs/check_docs.py all`, `npx --yes markdownlint-cli2@0.18.1`,
   `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`. All must be clean. Add any new word
   cspell rejects to the project dictionary only if it is a real term already used in the repo.

## Definition of Done

- [ ] ADR-0018 amended (dated, annotations, Risks bullet), D8/D11 decision text verbatim.
- [ ] `service-lifecycle.md` and `service-contract.md` state the built behavior and the one definition.
- [ ] Contract descriptions use the one definition; only descriptions and the dead `x-source` changed.
- [ ] Contract CHANGELOG has Fixed, Changed and Scenarios entries.
- [ ] `./gradlew :contracts:check`, `ContractDescriptionsTest` and the three docs checks are green.

## Risks

- A description edit that changes YAML structure (indentation under `description: >-`). Re-run
  the bundle and lint.
- Claiming more than WP02/WP03 build. Everything you write must be checkable in plan.md.

## Reviewer Guidance

- Diff `contracts/` for anything that is not description text or the one `x-source`.
- Check the one definition is identical in spec Key Entities, the header, `ServiceInfo` and the docs.
- Check ADR-0018's decision text is untouched and the annotations are dated.
