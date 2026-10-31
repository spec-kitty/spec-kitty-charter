# Pre-PR squad: findings and dispositions

The squad ran on 2026-10-31 under procedure `adversarial-squad-deployment`, over branch
`issue-194-mission-type-consolidation` at 6f929e3: four commits on origin/main 80dc882, after the rebase over #260.

**The lenses.** Four, each read-only, each with its profile loaded:

| Lens | Profile | Verdict |
|---|---|---|
| Architecture | `architect-alphonso` | ship-after-fixes |
| Java correctness and test strength | `java-jenny` | ship-after-fixes |
| Failure modes and security, probe-backed | `debugger-debbie` | ship-after-fixes |
| Curated-surface consistency | `curator-carla` | ship-after-fixes |

None found anything CRITICAL or HIGH.

**Convergence.** Two pairs of lenses found the same gap independently:
- Validate mode and the load could disagree on a mission type (java-jenny, architect-alphonso).
- The retired globs were unanchored (architect-alphonso, debugger-debbie).

Both are fixed.

**Dispositions.** Each finding is *fixed* (in the fold commits), *deferred* (to a row of
`docs/development/epic-186-sanitized-shape-follow-ups.md`, with the reason), or *no change* (with the reason).

## Architecture (architect-alphonso)

| Finding | Disposition |
|---|---|
| M: the domain holds a pack file path (`ExpectedArtifact.templatePath`) | **Deferred.** ADR-0044 decision 7 now states the one exception: the value is a reference kept as written, checked on read and write. Resolving it to the `Template` is a follow-up row. |
| M: a template's place has two owners (its id prefix and `missionType`) | **Deferred**, to a follow-up row. The read already refuses a duplicate id. Giving the place one owner is a design change for #186. |
| M: validate mode and the load do not match for mission types | **Fixed.** `MissionTypeChecks` runs the load's checks in validate mode: a repeated step id, a non-derived or missing companion, and a named file that is not a template. `ParseFailure` carries a typed refusal. Tested both ways. |
| L: an orphan step companion is never refused or digested | **Deferred**, to a follow-up row together with the failed-create orphans. |
| L: the retired globs are unanchored | **Fixed.** There are now 13 rules, each anchored to a layout the pinned packs held. `RetiredFilesTest.aNamesakeIsNotRetired` covers payloads that only share a name. |
| L: the header's overlap invariant is contradicted | **Fixed.** The known exception is stated in the header and the Javadoc, and pinned by `RetiredFilesTest`. |
| L: residuals missing from the #186 follow-ups, and "the open-packs issue" in the singular | **Fixed.** Rows were added for open-packs #96–#100, the manifest hash and mission-type HTTP operations. ADR-0044 names the issues. |
| N: the `Preset.ACTIVATION_KINDS` Javadoc | **Fixed.** |
| N: stale "before the bundled table has a retired rule" seams | **Fixed.** |

## Java correctness (java-jenny)

| Finding | Disposition |
|---|---|
| M: validate mode and the load do not match | **Fixed**, with the architecture lens's finding of the same gap. |
| L: an in-pack linked template reads as "missing" | **Fixed.** A link is followed while its real place stays in the pack, and a directory gets its own wording. Tested. |
| L: `realPath` throws out of the read | **Fixed.** It now returns empty, which reads as "leaves the pack". |
| L: `templateId` has no guard | **Fixed.** It throws `IllegalArgumentException` for a file under neither templates directory. Tested. |
| L: `isRelativeInside` is blind to `\` and to a drive | **Fixed.** Both are refused, and a template path must also start with `templates/`, as the schema says. Tested. |
| L: `Template` throws `IllegalArgumentException` while the Javadoc says `ConstraintBreachException` | **Fixed** in the Javadoc. `TemplateBinding.toEntityAt` documents both. |
| L: the neighbour assertion is vacuous, and `\` is untested | **Fixed.** The neighbour is now a real write that reads back, and `a\b` is refused. |
| L: a blank title in `StepDocument`, and a `null` sections list | **Fixed.** |
| N: the duplicate on-demand step, the message assertions, the negative equality | **Fixed.** |
| N: fully-qualified types | **Fixed** in `PackFileSet` and `MissionTypeBinding`. |

## Failure modes (debugger-debbie)

| Finding | Disposition |
|---|---|
| M: a named file can be excluded, retired or undigested | **Fixed.** A named file must classify as a `template` payload. Tested with a README and a directory. |
| M: the retired rules refuse namesake payloads, and `files` rules beat stale files | **Fixed** by anchoring. The `files`-beats-retired overlap is **deferred** to a row: no pinned file hits it, and it needs a check when the table loads. |
| M: a write succeeds that the next read refuses | **Fixed.** The write checks named files before anything is written and refuses with `VALIDATION_FAILED`. Tested. |
| L: a failed create leaves orphan companions | **Deferred**, to a row. The only production writer, the commit store, stages and drops the half-written copy. |
| L: a template write does not refuse a duplicate id | **Deferred**, to the template-ownership row. No HTTP path writes a template. |
| L: an in-pack link reads as missing | **Fixed**, with the Java lens's finding. |
| L: `realPath` throws | **Fixed**, with the Java lens's finding. |
| L: a linked `missions/` directory makes every type vanish without a violation | **Deferred**, to a row. The walk fails closed: it reads nothing outside the pack. The same holds for every kind. |
| N: hidden temp files under `missions/` | **Fixed.** Two exclusions were added, each with a sample in `PackFileKindTest`. |

## Curated surface (curator-carla)

| Finding | Disposition |
|---|---|
| M: ADR-0036, ADR-0037, ADR-0040 and ADR-0042 state what ADR-0044 reverses | **Fixed.** Dated notes and rows were added, and the README index was updated. |
| M: the conceptual model's step-contract sentence | **Fixed.** |
| L: "Step prompt" against "Step template" | **Fixed.** The domain terms use the glossary term, Step template. The docs check refuses an alias in running text, so no alias was added. |
| L: the header's overlap invariant | **Fixed**, with the architecture lens's finding. |
| L: the stale `PackFileKind` Javadoc | **Fixed.** |
| L: the `contracts/packs/README.md` intro and the "two added later" sentence | **Fixed.** |
| L: "13 kinds" in the descriptor schemas, and the undrawn Markdown schemas | **Fixed.** |
| L: the `NodeKind.yaml` description | **Deferred**, to a row. It is a contract file; the description is text only, and its correction belongs in a contract change of its own. |
| L: the build guide's "#194 harness" | **Fixed.** It names both pull requests and the evidence directory. |
| L: the glossary's "endpoints" | **Fixed.** No operations exist; a follow-up row records them. |
| L: the duplicate homes beside the action node | **Fixed.** The #189 row now names all three. |
| L: spec FR-003, FR-009 and the out-of-scope line are superseded | **Fixed**, with a dated note in the spec. The requirement text is kept for the record. |
| N: the issue-matrix titles | **Fixed**, with the real GitHub titles. |
| N: the acceptance-matrix TODO notes | **No change.** It is tool boilerplate shared by every mission; the evidence fields are real. |
| N: the user-story scenario order | **Fixed.** |
| N: the `ArtifactKind.MISSION_STEP_CONTRACT` Javadoc | **Fixed.** |
| N: the ADR-0044 reference dates | **Fixed.** |
| N: the scenarios README list | **Fixed.** `relationships.feature` was added. |
