# WP03 review feedback 1 (reviewer-renata, commit c4439ed)

Build: `:packs:check :deployable:test :packs:wholePackRoundTrip` green in an isolated worktree, except one timing flake
(`HttpsGuideFetcherLoopbackTest.aBodyThatDripsPastTheDeadline...`, unrelated, green on rerun). Zero suppressions.
Placement check (load + validate via `FileStems`), derived-path refusal, frontmatter refusal, `newFile` guard (other
kinds keep the old branch), `DomainRoundTrip` nested walk (only `placedAtItsFileName` kinds considered nested), the
`empty_kinds` entry (goes STALE_BASELINE when the pack gains a mission-type file) and the five positive controls are fine.

Checklist: dead code PASS; synthetic fixture PASS; silent empty return PASS; FR coverage FAIL (FR-007 template_path);
frozen surface PASS; locked decision FAIL (Issue 1); shared-file ownership FAIL (Issue 2); production fragility PASS.

**Issue 1 (HIGH) — `template_path` existence check dropped, governing ADR rewritten by the implementer.**
spec.md US3 AS-5 ("a `template_path` naming no file or leaving the pack ... is refused naming the step and the path"),
plan.md IC-04 ("`toEntity` refuses ... a `template_path` that leaves the pack or names no file"), data-model.md
("checked to resolve by the binding") and tasks.md T011 ("template paths") all require it. `MissionTypeBinding` has no
template check and `MissionTypeBindingTest` has no template_path test; instead c4439ed edits
`docs/adr/0044-...md:168` to say the load does not check existence. Fix: implement the check in the load (the binding
or the store, which can see the pack) refusing a missing/escaping template naming step and path, with a refused test
beside its accepted neighbour — or obtain and record an operator decision and amend spec/plan/data-model/ADR through
that decision, not inside an implementation WP. Revert the ADR edit otherwise.

**Issue 2 (MEDIUM) — files outside `owned_files` changed without a coordination note.**
`contracts/packs/schemas/mission-type.schema.json` (WP02-owned: `expected_artifacts` split into
`expected_input_artifacts`/`expected_output_artifacts`), `KindBinding.java` (`placedAtItsFileName`), and ADR-0044.
The schema alignment to ADR-0044 §3 looks correct, but it needs an explicit coordination note (move-task note or
commit message) naming why WP03 touches WP02's schema.

**Issue 3 (LOW) — loosened assertions in `KindBindingsTest.theMissionTypeIsAppendedLast` (~l.58-91).**
The old test's `KindBindings.of(PROCEDURE)`, `YamlFormats.of(ProcedureBinding)`, `ProcedureBinding.schemaName()` and
`idKey()` assertions were removed and are not covered elsewhere. Keep them beside the new mission-type assertions.

**Issue 4 (LOW) — weak assertions in `MissionTypeBindingTest`.**
`anIdThatLeavesItsPlaceIsRefusedOnCreate` (l.164-174) asserts only the exception type; l.173 (`fixture.id()` is
"research") is vacuous. Assert the refusal message (`does not name a file in the pack`) and that nothing was written
outside `missions/`. `frontmatterIsRefused` (l.226) asserts only the file; assert the reason too.

**Issue 5 (LOW) — no validate-mode test for a misplaced mission type.**
`FileSystemPackInspector.misnamedArtifact` (l.366-376) does refuse it through `FileStems` (const at `/id`), but no test
pins it. Add one beside the load-mode `aMisplacedMissionTypeIsRefused`.

**Issue 6 (LOW) — stale Javadoc.** `FilesystemPackStore.newFile` Javadoc still says the file is created
"directly in the pack directory"; describe the nested case.

## Response (implementer)

1. HIGH, template_path existence: implemented, not dropped. `KindBinding.namedFiles` lets a binding declare pack files
   its shape names but does not own; `FilesystemPackStore` refuses an artifact naming one that is missing or leaves the
   pack (`reference` violation). `MissionTypeBinding.namedFiles` returns every output template. ADR-0044 §3 is restored
   to "the load refuses a template_path that leaves the pack or names no file". Test:
   `MissionTypeBindingTest.outputTemplatesNameAFile` (missing refused, present read, climbing refused by the schema).
2. MEDIUM, files outside the owned list: the schema and domain changes after c4439ed follow the operator's Q2 ruling
   and its data correction (decision moments plan.q1/plan.q2; commit "fix(missions): every pinned per-step artifact is
   a postcondition"). `KindBinding.java` and `FileStems.java` carry the placement and named-file hooks this WP needs;
   they are added to WP03's owned files.
3. LOW, KindBindingsTest: the procedure's format, schema name and id key assertions are restored beside the mission
   type's.
4. LOW, weak assertions: the create refusal checks its message; the frontmatter refusal checks its reason.
5. LOW, validate mode: `validateModeRefusesAMisplacedMissionType`.
6. LOW, Javadoc: the upsert Javadoc names the nested create.
