---
affected_files: []
cycle_number: 1
mission_slug: procedure-antipattern-toolguide-round-trip-01M60H9B
reproduction_command:
reviewed_at: '2026-10-29T12:10:46Z'
reviewer_agent: claude
wp_id: WP09
---

# WP09 review: changes requested (reviewer-renata)

The harness is mostly sound. I re-ran `:packs:packMigration` on an archive of the pin `01edb4eb` into a scratch
target, and the output for built-in, internal and qa is byte-identical to open-packs `0cd66922`. The verifier
reports 0 problems, 41/41 procedures, 24/24 toolguides and guides, 2259 checks and 253 drops, and the migration
is idempotent. An independent PyYAML check of all 65 YAML files against the declared mapping found 0 differences.
The target guard refused all 6 cases I tried, and nothing was written: inside the pinned checkout, equal to it, a
symlink into it, a `..` path into it, inside `-PmigrationSource`, and a non-empty target. `:packs:test`,
`openPacksRoundTrip` and `wholePackRoundTrip` each report "No tests found" for the migration package.
`:packs:test` is green (1538 tests, 0 failed).

One issue blocks approval.

## Issue 1 (blocking): authored YAML comments are dropped silently

`packs/built-in/procedures/refactoring.procedure.yaml` has a four-line comment at lines 59-62, inside `references`,
in both the pin and open-packs `main` (`72cb7dbc`):

```
  # Step 2 ("Select the relevant refactoring tactics") orchestrates selection
  # across the built-in Fowler refactoring catalog. Cite the catalog entries the
  # procedure can select so the catalog is reachable from the procedure that
  # governs it (DRG inbound wiring, not metric-gaming — these are real choices).
```

This comment explains why the procedure cites the refactoring tactics. The production writer cannot keep comments,
so the migrated file has lost it. The loss does not appear in `verifier-report.md` (drops), in `manual-decisions.md`
or in the prepared PR or issue bodies. The verifier compares parsed values, so it cannot see the loss. This is
exactly the "dropped without being listed" case: the harness made a decision no human was asked to review.

Fix it in the harness, because the operator regenerates on `main` by re-running it.

- In `LegacyProcedureReader` and `LegacyToolguideReader`, or in `FieldMappingVerifier`, detect comment lines and
  trailing `# ...` comments outside scalars. snakeyaml-engine can find them: comment events
  (`LoadSettings.setParseComments(true)`), or the marks of scalar events.
- Report each comment as a `GuideRestructurer.Decision` (or a sibling record) of its own class, for example
  `yaml-comment`, with the treatment chosen. The treatment can be "dropped", or "kept as `notes` text" if you
  prefer. Only the decision has to be listed.
- Make an unreported comment a verifier problem, so that a new comment on `main` cannot disappear silently.
- Add the new class to the manual-decisions issue set and to the PR table, which the orchestrator posts (T048).

## Non-blocking (fix in this pass if cheap, otherwise hand to the orchestrator for T048)

2. **The verifier ignores order.** `FieldMappingVerifier.guide` compares lines as a multiset, so it cannot see
   lines reordered within a section, or section content reordered. The production codec emits content verbatim,
   so the risk is low. Still, a per-section check would close the gap: each target section's lines should equal its
   origin's lines in order, after the declared edits and drops. Alternatively, say in the verifier report that
   order is not verified.
3. **A relative `-PmigrationTarget` resolves against the test JVM's working directory**, which is the `service/packs`
   project directory, inside the source tree. Consider refusing a relative target, or resolving it against the
   directory Gradle was invoked from.

## Corrections the prepared upstream bodies need (T048, orchestrator-owned)

- **pr.md, "Risks" item 4, and issue-decision-stale-guide-references.md:** add `packs/qa/README.md:86`. It is a
  Markdown link, `[toolguides/QA-PROMISE-TRACE-LINT.md](toolguides/QA-PROMISE-TRACE-LINT.md)`, which breaks after
  the rename. `docs/packs/{built-in,internal,qa}.md` are already covered as generated pages.
- **pr.md, "Risks":** add a fifth risk. `scripts/ci/check-built-in.sh` will fail: the `packs/built-in/` tree digest
  no longer matches `packs/built-in.source.yaml`'s `tree_digest` (checked with core's `snapshot_sha256`: matches on
  the pin, mismatches on `0cd66922`). Also, the `content_hash` of every migrated constituent in
  `packs/built-in/pack-manifest.yaml` is stale. Both need a re-seed or regeneration with the merge.
- **pr.md, "Guides" bullet:** "the other sections are kept in order" is true, but the PR should say what
  issue-guide-structure.md already says: mapped fixed blocks move to their canonical position, before or after the
  freeform sections. Name the guides where content moves: powershell-syntax (Usage before Quick Reference;
  Cautions last), branded-document-generation (Usage before "What the pipeline enforces"), mermaid and plantuml
  (Usage before Core Diagram Types and Theming), qa-result-ingest (Scope after Setup and Usage),
  test-quality-triage (Known limitations after "Shallow clones") and python-mutation-tools (Cautions after the
  mutmut gotchas). I read these and found no case where the reorder changes meaning: "see ... below" references
  still point the right way.
- **pr.md, "Formatting":** say that switching from folded to literal style makes a latent upstream bug visible.
  In `built-in/procedures/post-merge-arch-gate-adjudication.procedure.yaml` (step "Establish the pre-existing
  baseline") and `internal/procedures/spec-kitty-arch-gate-adjudication.procedure.yaml`, a fenced ```` ```bash ````
  block sits inside a `>` folded scalar. YAML had already folded the block onto one line in the parsed value, and
  the migrated literal block now shows that. The value is unchanged. Worth its own small issue, to restore the
  block as a literal scalar.
- **issue-decision-separator-rules.md:** the title says "4 guides", but the list has 5 bullets (Drupal's rule is
  counted under the attribution). Say "24 rules in 4 guides, plus 2 rules before unheaded tails".
