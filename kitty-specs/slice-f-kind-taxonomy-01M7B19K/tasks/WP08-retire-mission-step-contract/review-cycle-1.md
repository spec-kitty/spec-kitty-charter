---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T14:39:57Z'
reviewer_agent: claude-reviewer
wp_id: WP08
---

# WP08 review, cycle 1: changes requested

Reviewed commit 25875f871 (base 0a9a816a3) in lane-h. The retirement itself is complete and correct. Main sources and
`contracts/` hold no leftover step-contract name outside the allowed records. The removed routes, the retired rules
and the scan all hold. `:kernel:test` (2961), `:packs:test` (5137, 4 skipped), `:deployable:test` (1981, every new
removed-routes example and the four rewritten effective-reads scenarios ran), `:missions-edge:test` (64) and
`:activations:test` (956) are green. `:packs:spotlessCheck`, `checkstyleMain` and `checkstyleTest` are green.

The rejection is for one MEDIUM finding: a classification change that goes beyond the scope of this WP.

## Issue 1 (MEDIUM): exclusions now beat `files` rules, so hidden content files are silently excluded

`PackFileKind.classify` (service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileKind.java:409-425)
now checks every exclusion and retired rule before the `files` rules. The intent was to let the retired
`**/mission_step_contract.graph.yaml` win over `**/*.graph.yaml`. Moving the exclusions forward as well changes how
other files are classified. The code comment says "a files rule and an exclusion never overlap", but that is false.
The hidden-file exclusions overlap every `**/*.<kind>.yaml` files rule. I checked against the bundled table on this
commit:

| path | before | now | `matchingRules` |
|---|---|---|---|
| `qa/assets/.x.asset.yaml` | asset (files rule) | Excluded `*/assets/**/.*` | `**/*.asset.yaml`, `*/assets/**/.*` |
| `qa/templates/.a.directive.yaml` | directive | Excluded `*/templates/**/.*` | `**/*.directive.yaml`, `*/templates/**/.*` |
| `qa/missions/m/.m.mission-type.yaml` | mission type (refused at the wrong place) | Excluded `*/missions/*/.*` | `**/*.mission-type.yaml`, `*/missions/*/.*` |

Files that were read before, or refused by name, are now neither held nor refused. This is silent, and it directly
contradicts the contract: `contracts/packs/file-kinds.yaml:239-241` still says "A hidden file a `files` rule maps (a
`.x.asset.yaml`) keeps that kind: `files` rules are consulted first." No test pins the old behaviour, which is why
the suite stays green.

Fix: keep `files` before `exclusions`, and give only the retired rules priority over a `files` match. Keep the
pinned README overlap, where the exclusion wins over the retired rule. For example:

```
first exclusion match present and first retired match present -> the exclusion   (pinned README overlap)
first retired match present                                    -> Retired
first files match present                                      -> Mapped
first exclusion match present                                  -> Excluded
payloads, then Unclassified
```

This keeps every current outcome: the graph fragment is retired, `mission_types/x.mission-type.yaml` is retired, and
the README is excluded. It also restores the files-first outcome for hidden content files. Then:

- add a regression test pinning `qa/assets/.x.asset.yaml` (and one more, such as `qa/templates/.a.directive.yaml`)
  as `Mapped` to its files schema;
- fix the `classify` comment;
- fix the file-kinds.yaml header (lines 19-28). It now says "two known overlaps", but there are three:
  `*/mission_types/*.yaml` versus `**/*.mission-type.yaml` is a retired-versus-files overlap too, and
  RetiredFilesTest:188 already pins it. Name it there.

## LOW findings (fold in while you are there, or record under "Not done")

1. `PackFileKind.java:411-413`: the comment has a stray reflow ("files rule and an" / "// exclusion"). Rewrite it
   when you fix Issue 1.
2. `contracts/packs/file-kinds.yaml:265`: the retired block header says "Each rule is anchored to a layout the pinned
   packs held, so a payload elsewhere ... is never retired". The two new `**` rules are deliberately unanchored.
   Say so in the header (decision 4 itself is fine).
3. The `step` link relation is no longer defined by any schema or emitted by any code: `MissionRelation.STEP` is
   gone. It is still listed in `contracts/lint/ruleset.yaml:312` (link-relations-are-known), and its row in
   `docs/development/hypermedia-hateoas.md:162` describes a step contract. The `mission-type` row also still
   mentions a step contract. RelationTableParityTest keeps the two in step, so either remove both now or record the
   `step` relation explicitly as a Breaking item for WP09 (ruleset) and WP10 (doc row, change fragment). The commit
   message does not list it.
4. `contracts/charter/scenarios/effective-reads.feature` (the research-gather enhancements at :338, :1114, :1191 and
   :1943): the enhancement fills `spdd:reasons-block`, but the research `gather` step has no prompt that declares
   the slot. The scenarios still prove the binding: had the enhancement not been bound to research, it would be in
   force and the assertion would fail. But the fixture is content that would be refused (`prompt_slot_missing`) once
   research is in force. Give research/gather a prompt with the slot, as software-dev/implement has, so the fixture
   is valid canonical content.
5. `planted/duplicate-step-id` now has no record in `core-verdicts.json`. The script is not gating, so this is fine,
   but note it for the next run of `scripts/packs/core_verdicts.py`.

## Decisions the implementer asked to have confirmed

1. Removing `PackCompositionFacts.bindings`, `ClosureView.MergedView.bindings` and `MergedSnapshot.bindings`:
   confirmed. Only the step contract fed them. The step-enhancement binding is derived in `resolutionEdges` (OD-6) and
   still drives `mission_type_not_in_force`. `InboundReferences` still covers a removed type through the edges to its
   action nodes. No orphaned callers remain.
2. Retired rules before `files` rules: the goal is confirmed, and refusing `mission_types/x.mission-type.yaml` under
   its retired family is acceptable (it is still refused by name). Moving the exclusions forward with it is not
   confirmed (Issue 1), and the header must name this overlap.
3. DRG_SCHEMA_ERROR for the retired graph fragment in validate mode: confirmed. It follows the existing `codeFor`
   rule (a `.graph.yaml` is a graph document's fault), and RetiredFilesTest pins it.
4. `**/*.step-contract.yaml` refused anywhere, assets included: confirmed. The old files rule already claimed that
   name everywhere, so no payload is lost. See LOW 2 for the header.
5. The scan skips `removed-routes.feature`: confirmed. That file must name the retired kind to pin its routes.
6. `selected_mission_step_contracts` dropped from `.kittify/charter/charter.yaml`: confirmed. The charter schema no
   longer declares it, and the charter gives core's format no compatibility.
7. Stale record removed from `planted/core-verdicts.json`: confirmed (see LOW 5).
8. Only the `step-contract` row removed from hypermedia-hateoas.md: acceptable for this WP only if the dead `step`
   relation is recorded for WP09 and WP10 (LOW 3).

The four rewritten effective-reads scenarios still prove what they proved before. An artifact bound to a mission
type is in force only while that type is. Each scenario keeps a control: the enhancement of software-dev's step, or
the directive D. The binding now comes through the derived step-enhancement path, which is the only binding path
left.
