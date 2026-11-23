---
affected_files: []
cycle_number: 1
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-22T13:06:29Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

**Issue 1 (MEDIUM, a doc claim that is wrong): the pack-author guide says pack-doctor ships in `service-v0.1.0-rc.3`, and it does not.**

- `docs/guides/check-packs.md:38`: "The jar is an asset of every service release, `service-v<version>`, from
  `service-v0.1.0-rc.3` on".
- `docs/guides/check-packs.md:43-46`: the download recipe sets `tag=service-v0.1.0-rc.3` and downloads
  `pack-doctor-$version.jar`.
- `docs/guides/check-packs.md:81, 84, 87, 104`: `java -jar pack-doctor-0.1.0-rc.3.jar validate ...`. Line 84
  (`... --round-trip --full`) was added by WP08 in c17d9fdbb.

The facts: the `service-v0.1.0-rc.3` release has the asset `pack-check-0.1.0-rc.3.jar` and no `pack-doctor-*` asset
(`gh api repos/spec-kitty/spec-kitty-charter/releases`). Under ruling R-w, no `service-v*` release is cut until this
mission lands. The rc.3 jar also has no `validate` command, no `--full`, no `--report` and no `remediate`. Someone who
copies the recipe gets a `gh release download` with no matching asset. WP08's own changelog
(`docs/changelog/2026-11-22-pack-doctor.md`, "What to do") says to repin "to the first `service-v*` release that
carries pack-doctor", so the guide also contradicts it.

Fix: in the Download section and every example, say the jar is an asset of every service release from the first one
that carries pack-doctor (the release after this mission lands, R-w). Use a `<version>` placeholder, or a `tag=` line
the reader fills in, and remove `0.1.0-rc.3` from the pack-doctor examples. Do not name the old asset: C-002 forbids
the old name, and NoPackCheckSurfaceTest scans this file.

**LOW (fix while you are in the files; these do not block on their own)**

- L1 `docs/adr/0083-...md:250` (R-t) and `:252` (R-v): the "verbatim" rationales are cut short with no ellipsis. R-t
  leaves out "It changes an existing code: contract changelog fragment and PR body note it; red-first tests; WP04 gets
  a fresh review cycle. PR writes Refs #636 for the slice." R-v leaves out "Rename the retired-word carriers (...) A
  file-less node (directive:GHOST) does not resolve." R-p (`:246`) and R-w (`:253`) drop their "Operator ruling R-x
  (date)" lead-in, which is fine, but say so. Mark each cut with "…", or quote the rationale whole.
- L2 `docs/adr/0083-...md:258-264` ("Where rulings differ"): one conflict is missing. R-q's rationale says
  "#629/#632 still close in this PR", and R-s says "the PR writes Refs #632 naming what is left" (decision 9: "#632 is
  referenced, not closed"). Add: R-s governs over R-q's "#632 still close": #632 is referenced, not closed.
- L3 `NoPackCheckSurfaceTest.java` (the ADR-0083 entry in KEPT): the reason, "quotes the operator's rulings verbatim,
  which name the old tool", is incomplete. The ADR's own prose names the tool too: line 3 (description), line 75
  ("noticed only by `pack-check --round-trip`") and line 311. Word the reason as "the record of the rename: it quotes
  the rulings and names the tool it renames (C-004)". The narrowing is correct: an explicit list is stricter than the
  old `docs/adr/` glob, and each of the 8 kept ADRs holds only historical body text (checked).

Verified as correct: every other ruling quote matches its DM file; the supersessions of R-q over R-p, R-r over R-q,
R-s over R-d and R-h, and R-t over R-s match the decisions; the USAGE, exit codes, `--full` → `REFERENCES`, and the
skipped and `inputs.options` shape; the XML `type` attributes; `remediate` on a file exits 2 and on a directory with no
`pack.yaml` exits 1, refused with `pack_unreadable` (run against the built jar); `NOT_HELD_WRITTEN` with no alias;
PresetItemRules' order (case-only first, no violation); `manifest_stale` fields and placement; the anti-pattern schema
(6 required fields, severity with no default); 18 example files; the PlantUML SVGs are current.
