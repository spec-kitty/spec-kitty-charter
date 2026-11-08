---
work_package_id: WP20
title: 'A · Docs for PR A: host requirement, guides, status codes'
dependencies:
- WP18
- WP19
requirement_refs:
- FR-008
- C-008
- NFR-011
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T099
- T100
- T101
- T102
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/changelog/2026-11-07-a-pack-registration.md
execution_mode: code_change
model: ''
owned_files:
- README.md
- docs/guides/run-in-a-container.md
- docs/guides/compose-packs.md
- docs/development/http-status-codes.md
- docs/architecture/hexagonal-service.md
- docs/changelog/2026-11-07-a-pack-registration.md
- cspell.json
role: documentarian
tags: []
tracker_refs: []
---
# WP20: A · Docs for PR A: host requirement, guides, status codes

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `documentarian`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Document PR A in the PR that ships it (DIRECTIVE_037): the git host requirement (≥ 2.50.1, no JGit, no work tree) in
README.md and `run-in-a-container.md` (the image must carry it); registration and removal in `compose-packs.md`;
`413`/`415`/`202`/`503` in `http-status-codes.md`; the `infra.archive` ring and the process rule in
`hexagonal-service.md`; the A13 note; a docs changelog fragment.

Implementation command:
`spec-kitty agent action implement WP20 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-12 (PR A). **Requirements:** FR-008, C-008, NFR-011.
- **A13** (`DM-01M6RA8XSM9CZZX1ADX6G80T3X`): a cascading deactivate removes `t` and what its walk reached unless
  something else active keeps it; prior selections are not remembered. Put it in `compose-packs.md` (or the
  activation guide it links to).

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.



### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `docs/conformance/ruleset-scenario-trace.md` — fill the PR A rows (A6, A8, A10 `DELETE`, A11, A12; US1, US2) (owned by WP09)


## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** Write the failing test or `@contract` scenario first, watch it fail for the right
  reason, then write the code. A contract change lands with its `contracts/charter/changes/` fragment before the code
  that serves it.
- **No disabled tests, no new suppressions (C-006, NFR-001).** No `@Disabled`, no `@WIP` left on a scenario this WP
  makes green, no `@SuppressWarnings`/Checkstyle/PMD/SpotBugs suppression added, and the suppression register does not
  grow. Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines and nested-if depth ≤ 1.
- **Hexagonal slices, no shadow logic (C-002).** Merges and walks live in `packs`; `activations` reaches `packs` only
  through the `AvailablePacks` port and `activations.infra.packs`. `HexagonalArchitectureTest` must stay green.
- **No core changes (C-003).** Nothing in spec-kitty core is edited, and no PR or issue is opened in core.
- **git is a host binary (C-008).** Only the system `git` ≥ 2.50.1, never JGit, never a work-tree checkout.
- **Gradle.** Run module tasks, not the full suite (`NO_FULL_HEAVY_SUITES_IN_MISSION`):
  `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none <module>:check`.
  On a Maven Central `429`, wait and re-run the same command; never turn dependency verification off.
- **Divergences (C-007).** Every behaviour that differs from core gets a divergence-register entry in the PR that
  ships it (the docs WP of that PR writes it; tell it what you changed in your hand-off note).
- **Commits** end with the session's attribution lines; no model id in commits or PR text.

## Branch Strategy

- Planning branch: `issue-10-layer-merge-effective-reads`; final merge target: `issue-10-layer-merge-effective-reads`.
- Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Dependencies are a claim gate: this WP can be claimed only when its dependencies are approved or done.
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A).

## Subtasks

### T099: Host requirement

README.md (requirements section) and `run-in-a-container.md`: git ≥ 2.50.1 on `PATH` or `charter.pack-sources.git.executable` (contributors: the `git-host` tests run only with it, in the `git-source` CI job; `check` uses a shim); without it the git source answers `503 pack_source_unavailable`; the container image must carry it (no image is published yet, issue [#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234), so the guide states the requirement for whoever builds one and names the `503` fallback; adding git to a published image is #234's scope); registration is off by default (`charter.registration.enabled`).

### T100: Guides

`compose-packs.md`: register (PUT archive, POST source, polling the status URI), remove (DELETE; the relationship `DELETE` removes a registered edge only, an authored or derived edge answers `409 relationship_not_registered`), the version rule, the impact-check refusals (PR A is strict: #404's repair writes are not applied), A13.

### T101: Reference pages

`http-status-codes.md` and `hexagonal-service.md`.

### T102: Changelog and spelling

`docs/changelog/2026-11-07-a-pack-registration.md`; cspell words (`curlopt`, …) in `cspell.json`; run the docs checks.

## Test strategy

`python3 scripts/docs/check_docs.py`, markdownlint, cspell, lychee on changed files.

## Risks

- `README.md` and `cspell.json` are touched by other PRs rarely; keep edits minimal.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- **Exit check:** `python3 scripts/docs/check_docs.py`, markdownlint, cspell and lychee pass on the changed files; the README states the git ≥ 2.50.1 requirement; every PR A row of the trace names an existing scenario.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.

## Added by the runner (2026-11-07)

- `contracts/charter/schemas/Link.yaml` still says a pack write is never `DELETE`; reword it for the two `DELETE`s of PR A (decision `DM-01M6TVMGH94ZF07KSWSCANEASV`), and update the `edit` relation row in `docs/architecture/hypermedia-hateoas.md` (WP11 hand-off).

## Hand-off from WP14 (2026-11-07)

Divergence-register entries (core merges per artifact; PR A replaces the whole pack):
- A registration replaces the stored pack rather than merging into it; the same revision answers `unchanged`.
- The same version with another revision is `409 pack_version_conflict`.
- `REMOVE` joins the write gate, and edges are gated as `RELATIONSHIP` writes on their source.
- A stored pack that cannot be read is repaired by a whole-pack replace. Corrected 2026-11-08: the "every uploaded
  artifact is a replace and nothing is removed" rule was the fail-open behaviour WP14's review removed
  (DM-01M6V4WMF8CD8NBN7A065E9ZQ5, 01M6V6N74ER28H8S6TSVZ8ZEY2). Document `salvageStored` as built: the stored pack is
  read file by file (an artifact counts by its declared id and by its file stem; graph files entry by entry); an
  artifact the upload leaves out is a `REMOVE` through the gate and the inbound scan; a dropped edge is gated as
  `RELATIONSHIP` on its source; a stored artifact that does not bind counts as a `REPLACE`; and if the stored artifact
  set cannot be determined at all, the registration is refused `pack_unreadable`.
- From WP15: the version history (IC-16: a downgrade is refused, the history is written inside the swap) and the strict
  impact check (a registration whose resulting closures fail to compose or break a relation rule is refused; core
  warns) are divergences to register. The start-up census WARNs for stored packs that would now be refused.
- From WP15's review: under the strict rule (DM-01M6TQ67C9ZTNGZB9CY28JS54F), one stored dependent that cannot be read
  makes every registration of its ancestor fail `pack_unreadable`; say so, with the repair (fix or remove the dependent).
- From WP15's review: a history entry restored by the heal carries the time of the heal, not of the lost registration.

## Hand-off from the WP16 review, cycle 1 (2026-11-07)

Document these, from review-cycle-1 of WP16:

- A re-send of an archive with the same `pack_version` after a per-artifact `PUT` moved the revision answers `409 pack_version_conflict`, not `412`: the version rule (IC-04 step 5) runs before the precondition (step 6). The 412 race needs a new version.
- The 90 s registration deadline counts the time a job waits in the queue, not only its run.
- Known limit, accepted for this mission: a registration slot is reserved before the request body is read, so a slow upload holds a slot for its whole transfer, and a few slow uploads can make other callers get `503` with `Retry-After`. Say so in the operator docs, with the slot counts (2 running, 8 waiting).

## Operator ruling 2026-11-07: notices name activatable kinds only (decision `01M6VXMCDQJWBJXGRQNMF5G6SV`)

`PackRegistrationMapper.kindOf` drops a notice entry whose kind is not an `ActivatableKind` (template, asset, guard, lifecycle). The operator kept the drop. In this WP:

- Say in the contract (the `PackRegistrationNotices` description, as a description change only, with no schema change) that notices name activatable kinds only, and that entries of other kinds are left out. Add an entry under `### Changed` in the registration fragment if the wording changes the documented meaning.
- Have the mapper log each dropped entry (WARN, naming the URN). Write the unit test first: a notice on a template is not in the response and is logged.
- Point to #417, which tracks widening the notice kinds.

## Hand-off from the WP17 review (2026-11-08)

Document, and add divergence-register entries (C-007) for:

- The pack-source configuration keys and defaults: `charter.pack-sources.https.{enabled, allowed-hosts, timeout 60s, max-size 32MB, max-redirects 3}`, `charter.pack-sources.git.{enabled, allowed-hosts}`, `charter.pack-sources.filesystem.{enabled, root}`. Each kind is off by default and read per request. The filesystem root must not lie under `/proc`, `/sys` or `/dev` or overlap `charter.config-root`. The resolver guard covers the union of the guide, archive and git hosts. Add them to `docs/guides/service-build.md` and the container guide.
- The fixed refusal reasons of `pack_source_refused` (the detail is "<token>: <fixed sentence>", with no URL, path or upstream text), `502`/`504` as plain problems for upstream failures and timeouts, and every local-path refusal as one `path_refused` (the reason is in the operator log only).
- The `PackSource.url` pattern fix (`^https://\S+$`).
- For `POST`, the descriptor `name` must equal the target pack id; for `PUT` it need not.
- The residual race of the local source: the file-key and link-count checks look the file up by name, not on the open handle (the JDK has no fstat on a channel); exploiting it needs write access under the root.
- The status of a successful registration does not keep a URL's query, fragment or user information (WP18 builds this).

## Hand-off from the WP19 review (2026-11-08)

Document, and add divergence-register entries for:

- Per-artifact `PUT`/`POST` and the relationship write can answer `422` with composition and relation codes. A write into a stored pack that cannot be read, or whose dependency is missing, is refused even when the write would repair it; whole-pack registration is the repair path.
- The artifact `DELETE` answers `409 registration_conflict` for an artifact named in an author-written graph document, and never rewrites an author graph file. It does re-serialize a preset file when it removes a preset item (comments and formatting are lost), and deletes a preset key whose last item goes.
- The relationship `DELETE` removes only from `registered.graph.yaml` and answers `409 relationship_not_registered` for authored, derived or doubly-held pairs. `If-Match` is required on both `DELETE`s. A removal that leaves a closure broken is `422` (strict; #404 is out of scope).
- `PUT` with `If-Match: *` satisfies the `428` requirement.
- Known gap: `pack-manifest.yaml` is not updated by per-artifact writes or `DELETE`s, so its hashes go stale and a `DELETE` leaves it listing a removed file. Reads do not verify the manifest; round trip and validate report it.
- The `DELETE` result is judged on the facts overlay; the edited tree is not re-read.

Two code fixes, runner decision 2026-11-08, each with a red test first:

1. **The relationship `DELETE` runs the edge gate.** Today it skips the write gate, so a `DELETE` can drop an edge whose source is a locked ancestor artifact, which a whole-pack replace is refused for dropping (DM-01M6TR1KDAJ64GMJD6AYNZKT59 item 4). Gate the removed edge as a `RELATIONSHIP` write on its source, with the refusal a replace gives. If this needs a response the contract does not list for the `DELETE`, add it with a `### Changed` fragment line.
2. **The relationship `DELETE` compares pack-qualified targets.** Today the pair compare ignores pack qualifiers, so with registered edges `A→base::X` and `A→other::X` one `DELETE` removes both. Remove only the edge the request names; keep the kernel's comparison for an unqualified reference.

## Hand-off from WP18 (2026-11-08)

Document, and add divergence-register entries for:

- The host requirement: git 2.50.1 or later on `PATH` or at `charter.pack-sources.git.executable`; no JGit; a container image must carry it (README and `docs/guides/run-in-a-container.md`). The new keys `charter.pack-sources.git.executable` and `charter.pack-sources.git.timeout` (60 s). The version is checked at every start-up; an old or missing git makes the git kind answer `503 pack_source_unavailable` while the other kinds work.
- The filesystem kind switched on with no root is a start-up failure.
- The git source runs the host git binary into a bare work repository, never a work tree.
- The `428` for a git branch ref without a commit, satisfied by `If-Match` or `If-None-Match: *`.
- The reasons `bad_ref` and `commit_mismatch`.
- A link, a submodule or an odd mode in the fetched tree is `422 validation_failed` with violation `entry_type`, as for an archive.
- The new required CI job `git-source` in `service.yml` (check the aggregate gate and branch protection pick it up).

## Hand-off from the WP18 review (2026-11-08)

- `source.url` in the registration status no longer echoes the URL "as sent": its query, fragment and user information are cut. Say so wherever the docs describe the status.
- `source.commit` records the commit the git ref resolved to.
- The git source accepts https only, follows no redirect (not even to the same host), and refuses links and submodules in the tree.
- sha256 repositories are untested (the work repository is created sha1); say so as a known limit.

## Review cycle 1 (2026-11-08): what cycle 2 must do

The blocker (see `review-cycle-1.md`), and three items the runner folds in now rather than at closeout:

1. **Blocker.** Rewrite the fail-open repair text in `docs/guides/compose-packs.md` (about lines 269-271),
   `docs/conformance/divergence-register.md` (DIV-604, about line 664) and `docs/conformance/divergences.yaml` (DIV-604,
   about line 1089) to the salvage rule as built (the corrected WP14 hand-off bullet above), and cite
   DM-01M6V4WMF8CD8NBN7A065E9ZQ5 and 01M6V6N74ER28H8S6TSVZ8ZEY2 in DIV-604's ruling line.
2. **Core claims.** Nothing in the repository shows what core does for registration. Reword every `core:` line of
   DIV-601 to DIV-613 that states core behaviour as fact (DIV-601 "core merges per artifact", DIV-604 "core warns",
   and the "core has no such operation / keeps no history" lines) as the service's understanding of core, to be
   confirmed against core, unless you find evidence in the repository.
3. **`edit` link.** The service sends no `edit` link on a stored pack (WP16's "no edit relation" decision). Drop `edit`
   from the `getPack` description and example in `contracts/charter/paths/packs_packId.yaml` (about line 132) and from
   `contracts/charter/examples/PackRegistration.stored.yaml`, with a `### Changed` fragment line, and make the docs say
   the link is absent. Run the contract checks.
4. **`registration_timed_out`.** In the `504` row, write the deadline as "90 s (the 60 s source deadline plus 30 s,
   NFR-008)" so it reads the same as the `x-refusal` text.
