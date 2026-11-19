---
affected_files: []
cycle_number: 1
mission_slug: pack-check-cli-01M7SASC
reproduction_command:
reviewed_at: '2026-11-19T11:08:44Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

# WP08 review, cycle 1: rejected (reviewer-renata)

Reviewed commit `801abb839` on `feat/478-pack-check` (the derived range `fa62a4d9..a004a83d` holds only status commits).

## What passed

- **Runbook (`release-runbook.md`):** every step checks out against `docs/guides/service-build.md:551-631`,
  `.github/workflows/service-native.yml` (inputs `cache-disabled`, jobs `contract-bundle` and `native (<target>)`),
  `.github/workflows/service-release.yml` (jobs `gate`, `jvm`, `native`, `assemble`, `publish`; title
  `Charter service <version>`; `--latest=false`; `--prerelease`; the attestation command in the release notes),
  `build-logic/src/main/kotlin/ServiceReleaseGate.kt:39-46` (the contract note is word for word) and
  `charter.service-release.gradle.kts` (`gate.properties`). The 11/13 file count and 6 `SHA256SUMS` lines match
  `scripts/release/service-release-assets.sh`. The precedent commits `b886d9886` and `bbf7f4385` are cited correctly.
- **Safety:** no step has a session push a tag (step 3 hands the command to the operator); #478 stays `Refs` until the
  release exists; `0.1.0-SNAPSHOT` after rc.2 matches the guide's step 5 and `bbf7f4385`.
- **Closeout code facts:** every file path, class, method, test name, fixture and reader line range exists on lane-a
  and is accurate.
- **Draft:** placeholder `open-packs#TBD-pack-entry-linked`, labels `follow-up` and `priority:P3` (Rule 3), D6 facts
  (76 links: 72 `.agents/`, 2 `.github/`, `CHANGELOG.md`, `CONTRIBUTING.md`) match `research.md` "D6 landing risk";
  the refusal wording matches `LinkRules.LINKED`; no personal information.
- **Commit:** SSH-signed (`gpgsig`), no Co-Authored-By trailer, no model name.
- **Lint:** `markdownlint-cli2` and `cspell` report 0 issues on the three files.

## Findings

### F1 (MEDIUM): the closeout cites WP01 commits that no ref reaches, and tells the orchestrator to "correct" a row that is already right

- `closeout.md:30` cites WP01 as `ed662d8d..1fc6004d` on lane-a.
- `closeout.md:65-66` says the `#597` row of `issue-matrix.json` cites `89c6da5e..92408ae7` and asks to correct it to
  `ed662d8d..1fc6004d`. Step 2 of "For the orchestrator" (`closeout.md:122`) repeats the ask.

WP01 was re-signed after this commit. Lane-a is now `da2a9a16..6741fa29`, with a tree identical to `1fc6004d`.
`git for-each-ref --contains ed662d8d` and `--contains 1fc6004d` return nothing. The mission branch already corrected
the row to `da2a9a16..6741fa29` (commit `11140ad15`). An orchestrator who follows lines 65-66 would replace correct
evidence with unreachable SHAs.

**Fix:**

- Cite WP01's commits in a form that survives a re-sign or rebase: the current range, or "WP01's commits as merged in
  #608, to confirm at closeout".
- Drop or reword the issue-matrix instruction. The row is already corrected on `kitty/mission-pack-check-cli-01M7SASC`.
  The only thing left is to cite the merged commits at closeout.

### F2 (LOW): "the `pack_entry_linked` entry" of `CharterRefusalCode.yaml`

`closeout.md:39-40` and the draft at line 34 call it an entry. `pack_entry_linked` is not an enum value. It is a
violation code named in the `validation_failed` description (`contracts/charter/schemas/CharterRefusalCode.yaml:407`
on lane-a). Say "named under `validation_failed` in …".

### F3 (LOW): the draft names no issue type

Rule 7 makes the native issue type the only kind carrier. The draft gives labels but no type (`Task`). It also does
not say "no milestone" (Rule 3). Optional: add `**Type:** Task`.

### F4 (LOW): a doc still names the rc.1 service

`docs/architecture/contract-and-dtos.md:25` says "the service `0.1.0-rc.1` bundles the second". `bbf7f4385` touched
that file. The runbook's snapshot PR (step 5) could update the status line to name `0.1.0-rc.2`. This is an optional
mention.
