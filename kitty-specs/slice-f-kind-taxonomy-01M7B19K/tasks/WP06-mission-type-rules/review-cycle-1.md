---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T08:09:52Z'
reviewer_agent: claude-reviewer
wp_id: WP06
---

# WP06 review, cycle 1 (claude-reviewer): changes requested

Reviewed e9986ede5 against 557260c99. `:packs:test` green. The rule, the EdgeIndex compose check, template_set removal,
the fixture/scenario carry-over and the contract registration are sound. The fixture diff is mechanical and honest:
no assertion was removed or weakened, and the only changed expectations got stronger. The issues below must be fixed
before approval.

**Issue 1 (MEDIUM): validate mode judges mission types that the load does not hold. Validate and load disagree.**
`FileSystemPackInspector.java:504-522`: `factsOf` adds `MissionTypeFacts` for every schema-valid mission type file,
including one that `MissionTypeChecks.refusal` then marks unreadable (a missing prompt or template file, a duplicate
step id). The load (`FilesystemPackStore.readKind`, lines ~817-831) drops such a file from `elements`, so
`missionStepViolations` never judges its steps or its edges. Two results follow:
- A refused type that also lacks a step edge gets an extra MISSION_STEP_UNLINKED finding in validate mode, so it is
  reported twice there and once in the load.
- If the type's graph names a step the type lacks, validate mode reports a stray at the graph document. The load
  reports nothing at that file, so the two refuse different files.
The step-enhancement facts already handle this case: a refused file is marked unreadable "so no rule judges it again".
Fix: record the facts only when the file is fully read (no `MissionTypeChecks` refusal). Add a parity test: one type
with a missing prompt and an unlinked step must give the same findings, at the same files, in validate mode and in the
load.

**Issue 2 (MEDIUM): deletePackRelationship and putPackRelationship can leave a pack that every read refuses.**
A type's step links may live in `registered.graph.yaml`; `ArtifactRemovalServiceTest` puts them there.
`RelationshipRemovalService.removeUnderLocks` (lines ~120-130) checks only the impact on the composition facts, which
does not re-run `MissionStepLinkRule`, and `PackCommitStore.commitEdited` does not validate the result. A
`DELETE` of `mission_type:T` -> `action:T/s` therefore commits, and the next load fails with `mission_step_unlinked`
(500 `pack_unreadable` on every read). `putPackRelationship` of `mission_type:T requires <action that is not a step of T>`
has the same gap whenever the target resolves. WP05 wired the endpoint and source rules into `putPackRelationship`
for exactly this reason ("every composing read of the pack would refuse it"). Refuse both as
`422 validation_failed` with `mission_step_unlinked`, add tests, and add a line on putPackRelationship and
deletePackRelationship to the CharterRefusalCode prose.

**Issue 3 (MEDIUM): T027's composition scenario is missing, and the contract claims a check the code does not make.**
- The WP asks for "a composition scenario where an org pack's override of a mission type drops a step and leaves its
  edge. It is refused." The scenario in mission-types.feature (~line 975) is a single-pack whole registration with no
  override and no compose. No scenario covers `500 pack_unreadable` / `mission_step_unlinked` on a composing read:
  neither the load path of a configured pack nor the EdgeIndex refusal of another pack's stray edge. Add both, under
  an activation.
- `CharterRefusalCode.yaml:106-110` and `changes/2026-11-14-mission-step-requires-edges.md` say a composing read
  refuses "also when the winning type of a compose lacks an edge for one of its steps". `EdgeIndex` refuses only
  another pack's stray `requires` edge (`refuseStrayMissionTypeEdges`). The winner's own links are covered by its
  pack's load. A step that the winner keeps from a replaced version (A7) has no edge from the winner in the merged view,
  and nothing refuses that. Make the prose describe what is enforced, or enforce what it says.

**Issue 4 (MEDIUM): removal drops another pack's qualified edge.**
`PackRemovalStore.Authored.isOwnStepLink` (line 65) compares `edge.from()` (the URN only) and ignores
`edge.fromPack()`. When pack P removes its type T, an authored `base::mission_type:T requires base::action:T/x` in P's
documents is treated as P's own link. `names()` exempts it, and `CommitStorePackWriteStore.withoutOwnStepLinks`
silently deletes it from the document. Before this change it was refused with registration_conflict. The ruling
(DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ) drops only the type's own links and refuses "any other authored reference". Require
`fromPack` to be empty or the pack's own name, and add a test.

**Issue 5 (checklist item 1, dead code): the new 11-arg `PackContents` constructor (`PackContents.java:~133`) has no
production caller.** Its own javadoc calls it the constructor for "call sites that predate the mission step link rule".
That is a legacy shape kept beside the canonical one (charter: no legacy shape, tests use the canonical shape). Move
the test callers to the canonical constructor and delete the overload.

LOW (fold in now or carry to a later WP):
- `MissionStepLinkRule.java:18` cites FR-009 for the step-link rule; it is FR-011. The same mix-up appears in the
  EdgeIndexTest `linked` javadoc and the ArtifactRemovalServiceTest `link` javadoc.
- A qualified target: `MissionStepLinkRule.stepOf` (line 131) and `EdgeIndex.ownSteps` accept
  `mission_type:T requires other::action:T/s` as the link for step s. `DomainRepositoryService.ownStepLinkTargets:760`
  exempts only a bare target. Decide whether a step link may name another pack's action, then make the three agree.
- `MissionStepLinkRule.judge`: when two files hold the same type id, the first file's steps win (`putIfAbsent`), so the
  second file's steps and edges are reported as unlinked or stray on top of the duplicate-id fault.
- MissionTypePlantsTest `aStrayEdgeIsAFaultOfTheGraphDocument` uses `anySatisfy` and checks only validate mode. Assert
  the exact findings (unlinked a plus stray c) and the load as well.
- `withoutOwnStepLinks` rewrites the whole document with `GraphDocumentYaml.write`, which loses comments and
  formatting. The removal test checks only that the other edge survives. Also assert that nodes and edge
  `when`/`reason` survive.
- `.kittify/charter/charter.yaml` still lists the reference `TEMPLATE_SET:software-dev-default` (`kind: template_set`).
  It is harmless to the schema, but core should drop it when it aligns.
- Test names such as EdgeIndexTest `theWinningMissionTypeWithEveryStepLinkedMerges` suggest a compose check of the
  winner's links, which the compose does not make.
