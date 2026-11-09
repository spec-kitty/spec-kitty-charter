---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-08T21:29:49Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP26
---

# WP26 review, cycle 1: changes requested

Reviewer: claude:opus:reviewer-renata (reviewer-renata). Lane `lane-x`, base `1e104a29`, commits `a79d0e8b` (T131),
`4060ab4c` (T132) and `f644ddcb` (T133). The diff touches docs only.

## Verdict

**Changes requested.** The work is accurate almost everywhere. I checked every flipped row against the lane's code
and found no overclaim. One required fix remains: two PR B rules that the code builds are still marked unbuilt on the
rules page, one of them "PR C". That contradicts DIV-237, the trace and the changelog of this same WP, and keeping
statuses in sync is this WP's job. There is also one small stale passage in a section this WP edited. Both fixes are
a few lines.

## Findings

### F1 (Medium, required): W33 and G20 are built in PR B but the rules page says they are not

- `docs/architecture/composition-and-activation-rules.md:710-711`: W33 says **Ruled, not built** ("needs `Eff`;
  layer-merge PR C, with W26"). Table row `:1063` says "Ruled, planned (PR C)".
- `docs/architecture/composition-and-activation-rules.md:476`: G20 says **Ruled, not built** (layer-merge PR B).
  Table row `:1029` says "Ruled, planned (PR B)".
- The code builds both:
  - `ActivationChange.withWarnings` / `Code {ENHANCED_TARGET_NOT_IN_FORCE, OVERRIDDEN}`
    (`service/activations/.../domain/ActivationChange.java:50-55, 157-175`).
  - `RegistrationImpact.Notices.activationsAffected` (`service/packs/.../application/RegistrationImpact.java:61-66`).
- This WP's other docs say they are built: DIV-237, trace rows B6 and `activationsAffected`, the changelog "Writes"
  bullet, and `offering-and-activation.md` § Write-time verdicts.

**Fix:**

- W33: mark it **Partly built (PR B)** (DIV-237). Only the two codes `enhanced_target_not_in_force` and `overridden`
  are warnings. The rule text names `mission_type_not_in_force` as an example, which the built code never warns. Either
  reword the rule to the two built codes or state that gap. Update the table row to match.
- G20: mark it **Partly built**. The floor and lock notices are PR A (`floorAdded`, `floorRemoved`, `locksAdded`,
  `locksRemoved`) and `activationsAffected` is PR B. The `Unheld` items that G20 names are not in
  `PackRegistrationNotices`, so say so. Update the table row to match.

### F2 (Low, required: same section the WP edited): stale lead-in at `docs/development/rest-resource-conventions.md:134` and `:138`

- `:134` still says `getOpenApiDocument` is "the one conditional read built into the contract".
- `:138` still introduces the activation preconditions as "Proposed:".
- WP26 edited the two bullets directly under that heading to describe built behaviour. The page now reads "Proposed:"
  over decisions that are built.
- Built conditional reads: `getActivation`, `listActivationPacks`, `listEffectiveArtifacts` and
  `getActivationSkippedReport`.
- The `ETag`, `If-Match` (428, 412) and `If-None-Match: *` preconditions are built and pinned by
  `activation-register.feature` and `activation-register-guards.feature`.

**Fix:** name the built conditional reads at `:134`, and change "Proposed:" at `:138` to say the preconditions are
built. This answers the implementer's open point (c): fix it now.

### F3 (Low, optional): changelog Breaking line without a contract fragment

- `docs/changelog/2026-11-07-b-effective-reads.md:41`: "A write over a closure that does not merge answers
  `500 pack_unreadable`." No PR B fragment states it (open point (a)).
- The write paths already declare `500`, so the WP23 review asked for a `### Changed` fragment, not a Breaking one.
- WP26 owns docs only, so the fragment is a closeout item for the runner. When it lands as `### Changed`, this line
  should move from the Breaking list to the "Writes" bullet. WP26 may move it now, or leave it for closeout.

### F4 (Info, no change): "the gate enforces them"

- Flipping DIV-201 and DIV-202 from `proposed` to `open` is correct:
  - The register's lifecycle (`divergence-register.md:51-61`, the yaml schema header) says `proposed` becomes `open`
    when the operation is built.
  - DIV-201 (a required or locked artifact in force under an allow list) is E3 and E5, built.
  - DIV-202 (the floor derived at read, never stored) is built.
- The conformance gate (#72) is not implemented in this repository. The only check over the register is
  `DivergenceRegisterTest` (id parity and schema fields), which ignores `status`. That test is green.
- "Enforced by the gate" therefore matches the register's existing wording but is notional today. No change asked.

### F5 (Nit, optional): extra blank line

`docs/conformance/divergences.yaml:885`: a double blank line before the DIV-216 entry.

## Checked and correct (no change)

- **Rules page flips.** I checked C15, C17–C21, C27, C28, C31, C33, E1–E14 (E12 partly), E17–E21 and section 7
  against the code:
  - `InForceResolution` (C28 roots `base() ∪ required`, walking only `overridable` edges, so only reachable cycles are
    refused; E7 `solve`/`refuseUnstable` matches the R13 rewrite; E9 `reasonOf` puts in-force rows first and
    `not_selected` before `overridden`; E5 `step` ignores the language filter).
  - `EffectiveTag` (E17 preimage: domain, read, revision, `closureFingerprint`, normalized query).
  - `EffectiveReadService` (304 before the merge; one session; stale-input retry).
  - `CompositionFactsCache` (DIV-236 wording exact).
  - E15 and E16 correctly stay PR C.
  - No overclaim found.
- **Divergences.**
  - DIV-216 to DIV-237 is 22 entries, the page and the yaml agree, and DIV-205 is ratified.
  - Every `charter:` evidence path, test method and scenario title in the PR B entries exists.
  - Only DIV-216 and DIV-218 cite core; the rest say "the service's understanding of core, to be confirmed".
- **Trace.**
  - Every PR B row names a scenario that exists verbatim.
  - `effective-reads.feature` and `layer-merge.feature` carry no `@WIP`.
  - A2 and B7 state their limits honestly.
- **Changelog.**
  - The Breaking list matches `2026-11-07-b-effective-reads.md` (plus the F3 line), and the wrong-type and
    duplicate-member `422` note is present.
  - `updated: 2026-11-08`.
- **ADRs.**
  - ADR-0053 d5 note: #357 built, #358 open.
  - ADR-0059 Amendment 2026-11-08 (D5 closure tag, `denyNotApplied`) and its ticks.
  - ADR-0020 Amendment 2026-11-08 (either-tag `If-Match`).
  - All three are faithful to decisions `01M6Y11E55YP5TG1EJ1MDKCFRP` and `01M6Y3GZFRC5WEQQ7G6GT0YMW3`.
- **Out-of-map lines.** Every out-of-map file has an `out-of-map:` line in its commit.

## Rulings on the implementer's open points

- **(a)** Yes, a fragment is missing: a `### Changed` fragment saying the activation write cascade walks the merged
  edges and that a write over a closure whose merge refuses answers `500 pack_unreadable` or `pack_sibling_conflict`.
  It is a closeout item for the runner, not WP26's.
- **(b)** Correct. The three digests are:
  - the revision;
  - the closure digest, as the `ETag` input and the body's `governanceFingerprint` (`EffectiveReadMapper:70,88`);
  - the whole-source digest in `Charter-Source-Fingerprint` (`ActivationController:75,308`).

  `getActivation` carries only the first two, as DIV-233 says.
- **(c)** Fix now (F2).
- **(d)** Verified against the code. The facts cache, the C28 reachable-cycle wording and the E7 R13 wording all
  match.

## Anti-pattern checklist

- Items 1–4, 6 and 8: N/A (docs-only diff).
- Item 5, frozen surface: PASS.
- Item 7, shared-file ownership: PASS. The `out-of-map` lines are present.

## Commands

- `python3 scripts/docs/check_docs.py all`: all sections ok, exit 0.
- `npx --yes markdownlint-cli2@0.18.1 "docs/**/*.md"`: 294 files, 0 errors.
- `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`: 294 files, 0 issues.
- `./gradlew --max-workers=1 ... -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :packs:test --tests '*DivergenceRegisterTest*'`:
  BUILD SUCCESSFUL, 3 tests, 0 failures.
- I did not run `:deployable:test`. The diff touches no code, and no deployable test reads a changed file
  (`NO_FULL_HEAVY_SUITES_IN_MISSION`).
