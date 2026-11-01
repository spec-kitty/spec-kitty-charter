# Research: landing PRs under parallel load

Research for Op `01M6B1A0F7DYRFAVQJQ5AFGZ08`. A three-lens read-only squad ran on 2026-11-01:
generated docs indexes, contract changelog fragments, and merge-queue readiness. Main was at
`819beb2`. No PR was open. This note records the findings and the decisions the operator rules on
before the three changes land. The remedy itself is the one spec-kitty/spec-kitty is adopting: no
hand-edited shared files, and readiness for a merge queue.

## 1. The docs indexes

### What the hubs hold

- `docs/changelog/README.md` (80 lines) holds frontmatter, an H1, two lines of prose, and then 62
  bullets, newest first, in the form `- [YYYY-MM-DD — <title>](/changelog/<stem>/)`. The order
  within one date is hand-chosen.
- `docs/adr/README.md` (95 lines) holds prose (L20-28). Then comes the table
  `| ID | Title | Status | Date |` with 46 rows (L30-77), and then a second hand-written table of
  core decisions (L78-95), which stays hand-written.

### Can the hubs be rebuilt from frontmatter?

- **Changelog entries.**
  - Each entry has `title`, and the date comes from the filename. The changelog has no `date` key,
    and `updated` differs from the filename date in 4 files.
  - The bullet text equals `title` in 60 of 62 entries. The 2 that differ
    (`2026-10-19-canonical-charter-ownership`, `2026-10-19-provisional-design-decisions`) are
    shortened in the hub.
- **ADRs.**
  - The Title column differs from the frontmatter `title` in 6 of 46 rows (0009-0014). The files
    carry the newer wording.
  - **No key holds Status or Date.** The status is in the body (`## Status`), and 27 rows carry
    hand-written suffixes such as "with Amendment 2026-10-28 (...)". The Date column matches neither
    `updated` (32 rows differ) nor the status-line date (19 rows differ).
  - So generating the table means a one-time migration: copy each row's Status and Date cells,
    verbatim, into new `status:` and `date:` frontmatter keys.

### What enforces the hubs (`scripts/docs/check_docs.py`)

- `check_adr` (:375): every `NNNN-*.md` is linked from the hub, and numbers are unique with no gaps.
- `check_changelog` (:402): filenames are well formed, every entry is listed, entries are listed
  newest first, and each is at most 3000 characters.
- `check_orphans` (:489): every page is linked from its directory's hub. If the list leaves the
  committed file, this check needs the generated list too.
- None of the three checks looks at the line text. Tests:
  `scripts/docs/tests/test_check_docs.py`, run in CI with pytest. pytest is not installed in this
  session, and nothing gets installed.

### The docsite

- **`generate-adr-index.mjs` is not in this repository, nor on disk.** `site/` uses
  `@spec-kitty/doc-toolkit` 0.5.0 from GitHub Packages, which needs `NODE_AUTH_TOKEN`. The site
  build has no node_modules and cannot run offline here. Starlight renders the hubs as written.
- `docs-site.yml` builds on PRs and on pushes to main, and deploys from main. It runs no index step.

### What tells contributors to list an entry

No document says "add the index line" outright; the checks force it. Pages to update:
- `docs/guides/contributing.md:44`
- `docs/guides/docsite.md:128-129,158`
- `docs/_meta/doc-rules.yaml:44-53` (rules `adr-index`, `changelog-style`)
- `docs/development/cloud-session-setup.md:280-287`
- `docs/_meta/templates/adr.template` (for the new keys)

## 2. The contract changelog

- `contracts/charter/CHANGELOG.md` (974 lines) has one version heading, `## 0.1.0-SNAPSHOT`. Below
  it sit `### Breaking`, about 11 per-change `###` blocks, then Added, Changed, the seam surface,
  Removed and Provisional. New entries go into the middle of the file (around L19 and L415), so
  contract PRs collide there. #303 and #290 did.
- **One reader.** `ReleaseGate.java` (`contracts/src/main/java/.../compat/ReleaseGate.java`), through
  `checkContractCompatibility` (in `verifyAll`) and `checkReleaseTag`, reads only the `##` headings
  and whether the top section has a `### Breaking` subsection. It reads no entry text.
- **No one else reads it.**
  - The `x-` lint (`contracts/lint/ruleset.yaml`) never reads the changelog.
  - `contract-release.yml` only links to the file at the tag, and it never writes to main.
  - `scripts/release/rehearse-contract-release.sh:54` renames the SNAPSHOT heading.
- "Every contract change has a CHANGELOG line" is governance text only (directive 002, tactics for
  PROJECT_002 and directive 037, the documentation-policy styleguide). No check enforces it.
- **What fragments would cost.**
  - Minimal version: new entries go to `contracts/charter/changes/<slug>.md`. The SNAPSHOT heading
    and `### Breaking` stay in CHANGELOG.md, so the gate sees the same text. A script assembles the
    fragments in the release PR, and `checkReleaseTag` refuses a tag while fragments remain. About
    10-14 files, with no change to `ReleaseGate`.
  - Moving Breaking into fragments too means about 18-20 files, including the gate.

## 3. Merge-queue readiness

- **One required check.** It is `CI gate / gate` (`ci.yml`, ADR-0023). `ci.yml` triggers on
  `pull_request` alone. It calls `service.yml`, `service-native.yml` and `docs-cleanliness.yml`
  through `workflow_call`. Those three have no PR trigger of their own, so they inherit the
  caller's event and need no `merge_group:`.
- **One pull-request-only expression.** `ci.yml:41-42` reads `github.event.pull_request.base.sha`
  and `head.sha` for `git diff "$BASE...$HEAD"`.
  - On `merge_group` both are empty, so `changes` fails and the gate fails closed.
  - The replacement is `github.event.merge_group.base_sha` and `head_sha`.
- No workflow reads labels, draft, `head_ref` or `event.before`. `docs-site.yml:111,117` and the
  release workflows compare only `github.event_name`.
- `whole-pack.yml` and `docs-site.yml` are standalone and not required, with `pull_request` paths
  filters. `merge_group` takes no paths filter. With `merge_group:` added, both would run in full
  on every queue entry, which is load and no protection.
- **Two load observations.**
  - Every PR that touches the service builds it twice: once in the gate, and again in
    `docs-site.yml`, which calls `service.yml` with `cancel-in-progress: false`.
  - `whole-pack.yml` builds once more.
  - That is three JVM builds per service PR, a likely part of yesterday's 18-run stall.
- **No label logic exists**, and `.github/` holds only CODEOWNERS and the workflows. On a paused PR,
  if `changes` is skipped, `gate` sees `skipped` for `changes` and fails closed, which is the right
  outcome for a parked PR. Without `labeled`/`unlabeled` in the PR trigger types, adding the label
  stops nothing, and removing it starts nothing.
- No document mentions a merge queue. ADR-0023 describes `gh pr merge --rebase --auto`.

## 4. Decisions for the operator

The recommendations went to the parent session in one batched message. The rulings are recorded
in the ADR that lands with change 1.

1. **Where the indexes are generated.** Recommended:
   - A Python generator, `scripts/docs/generate_indexes.py`, writes each hub's list between
     markers. The committed hubs keep their prose and an empty generated block.
   - `check_docs.py` checks the regenerated hub in memory, and fails when an entry lacks the keys
     the index needs. `docs-site.yml` runs the generator before `astro build`.
   - Not the docsite toolkit: `generate-adr-index.mjs` is not in this repository, and the toolkit
     build needs a token and the network.
2. **Bot commit or PR.** Neither is needed if the list is not committed. If the operator wants it
   committed, a post-merge bot PR through the queue is recommended over a bot push to main, which
   would need a ruleset bypass.
3. **Contract fragments.** Recommended: worth it now, as the minimal version, landed last of the
   three. Assembly runs in the release PR, not in `contract-release.yml`, which stays read-only on
   main. `checkReleaseTag` refuses a tag that leaves fragments unassembled.
4. **Queue settings.** Recommended:
   - Require merge queue on `main`, with merge method **rebase** (ADR-0023's method). The required
     check stays `CI gate / gate`.
   - Build concurrency **3**. Group size min 1, max **4**.
   - Wait **5 min** to fill a group. Status-check timeout **60 min**.
   - Only merge non-failing PRs.
5. **Other open points.**
   - Add `merge_group:` to `ci.yml` alone. The called workflows inherit it. `whole-pack.yml` joins
     the gate through `ci.yml` when it becomes required, not by getting its own `merge_group:`.
   - Cancel stale PR runs of `docs-site.yml`, and skip its service build on PRs, because the gate
     already builds the service.
   - The ADR rows' Status and Date move to frontmatter, which is metadata, not the decision text.
   - Same-day changelog entries are ordered by filename. The hand-chosen same-day order is not
     kept.
