---
affected_files: []
cycle_number: 2
mission_slug: procedure-antipattern-toolguide-round-trip-01M60H9B
reproduction_command:
reviewed_at: '2026-10-29T12:33:35Z'
reviewer_agent: claude
wp_id: WP09
---

# WP09 review cycle 2: changes requested (reviewer-renata)

Cycle 1 is addressed. Detection is correct and complete, the order check and the relative-target refusal work, and the
upstream text corrections are in place. One change remains. It is narrow: it concerns where the one authored comment
goes, not whether the harness detects it.

## Verified (no action)

- **Detection.** I ran an independent PyYAML token scan (every `#` outside a scalar token span) over all 39 procedure
  and 24 toolguide YAML files of open-packs `origin/main` `72cb7dbc`. It finds exactly 4 comment lines, one block,
  `built-in/procedures/refactoring.procedure.yaml:59-62`. The other 42 `#` characters in those files sit inside
  scalars. `FieldMappingVerifier.verifyComments` makes an unreported comment a problem, cross-checks every `#` outside
  a scalar against the parsed comment lines, and checks the reported text. `MigrationVerifierChecksTest` covers this.
- **Regeneration.** `:packs:packMigration` on an archive of `72cb7dbc` gives output byte-identical to
  `feat/charter-194-sanitized-procedures-toolguides` `24df2c10` for all three packs. The verifier reports 39/39, 24/24,
  24 guides, 2226 checks, 246 drops, 4 comment lines reported, 42 manual decisions and 0 problems, and the run is
  idempotent. The reports equal the prepared artifacts apart from the source path.
- **Order check.** It is tested by `linesReorderedInsideASectionAreAProblem`,
  `freeformSectionsOutOfTheirSourceOrderAreAProblem` and `fixedBlocksMovingToTheirPositionIsTheDeclaredTransform`
  (10/10 pass under packMigration). The report lists the 12 moves in 7 guides.
- **Relative target.** `-PmigrationTarget=relout` fails with "-PmigrationTarget must be an absolute path". Nothing is
  written.
- **`:packs:test --rerun`.** 1538 tests: 0 failed, 3 skipped, and no migration class runs.
- **Old-shape knowledge.** The WP09 commits touch only `migration/**` in `src/test` and `build.gradle.kts`.
- **Upstream texts.** `qa/README.md:86` is in pr.md Risk 3 and in the stale-references issue. The `check-built-in`
  digest and the manifest `content_hash` are Risk 4 and the first section. The Guides bullet names the 7 guides, and
  these match the verifier's move table. The folded-code-block paragraph and its issue are present. The separator
  title says "24 rules in 4 guides, plus 2 rules before unheaded tails".

## Issue 1 (blocking): fold the refactoring comment into the procedure's `notes`, once, not into nine `reason`s

The procedure schema (`contracts/packs/schemas/procedure.schema.json`) has a top-level `notes` (string, minLength 1).
The production writer and `LegacyProcedureReader` already carry it, and 13 migrated procedures use it.
`refactoring.procedure.yaml` has no `notes`. This field is the faithful home for the comment, for these reasons:

- The comment is procedure-level rationale addressed to the author. It names step 2 and tells the author to "Cite the
  catalog entries the procedure can select". It is not a reason for any one reference. As nine `reason` values it
  claims a per-reference rationale that the source does not make, and the PR and issue texts already say it reads
  that way.
- With `notes` the text is stored once, not 9 times, about 2.4 KB of identical text that is rendered wherever the
  references are listed.
- The references then stay unchanged, as the "Procedures" bullet promises without an exception.

Do the following:

1. In `YamlComments.decide`, a comment block inside a procedure's root `references` goes to `notes` when the
   procedure has no `notes`. Write it verbatim, with the lines joined by spaces as now, and give the treatment as
   "folded verbatim into `notes`". When the procedure already has `notes`, drop the block and list it, as is done for
   every other comment. Do not append to authored notes. Keep the `yaml-comment` decision row.
2. `FieldMappingVerifier`: the folded path becomes `notes`, and the target `notes` must equal the comment text. The
   "two comments folded into one path" check then also covers two reference blocks in one procedure. The second
   block should be dropped and listed, not raise a problem; adjust `decide` so that only the first block takes
   `notes`.
3. Update `MigrationVerifierChecksTest`. `foldsAReferenceCommentIntoTheReasonsAndDropsTheRest` and
   `aReferenceThatAlreadyHasAReasonKeepsItAndTheCommentIsDropped` become tests for the `notes` fold, the drop when
   notes already exist, and the drop of a second block.
4. Regenerate the open-packs branch on `72cb7dbc` (operator ruling: same base), and update the prepared texts:
   - pr.md "Procedures": references unchanged, with no exception.
   - pr.md manual-decisions table row for `yaml-comment`.
   - `issue-decision-yaml-comments.md`: the treatment, and the reviewer options (keep in `notes`; move into reasons
     or shorten them; drop).
   - `artifacts/procedures.diff`, `manual-decisions.md` and `verifier-report.md`.

Folding the comment into the first reference only is not an alternative. It attaches procedure-level guidance to one
arbitrary reference.
