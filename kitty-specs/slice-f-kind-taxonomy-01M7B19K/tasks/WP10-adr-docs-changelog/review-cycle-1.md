---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T18:02:31Z'
reviewer_agent: claude-reviewer
wp_id: WP10
---

# WP10 review, cycle 1: changes requested

Reviewed commit 4216e31fa in lane-j against its parent. The ADR, the notes, the docs, the fragments and the folded LOWs
are thorough and nearly all accurate. One MEDIUM blocks approval: ADR-0077 records an operator ruling as built when the
build did not carry it out.

## MEDIUM

**M1. ADR-0077 says slice C's `OverrideSourceRule` is folded into the bridge. It is not.**
`docs/adr/0077-kind-taxonomy-and-step-authority.md:185-186` says "Slice C's `OverrideSourceRule` is folded into the
bridge." In lane-j:

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/OverrideSourceRule.java` still exists with its own
  predicate (`inactivatableKind`) and its own `CODE` constant.
- `OverrideSourceRule` is still the reporter on the load path (`FilesystemPackStore.java:376-432`,
  `overrideSourceViolations`) and on the write path (`RelationshipRegistrationService.java:149-156`,
  `refuseInactivatableOverrider`).
- `EndpointRule.bind` (`EndpointRule.java:79-81`, `inactivatableArtifact`) repeats the same predicate and defines a
  second `OVERRIDES_SOURCE_NOT_ACTIVATABLE` constant. `RelationshipEndpointRule` takes `OverrideSourceRule.Edge` as its
  input and drops the bridge's overrides-source fault, so nothing is reported twice.

The fault order and the wire code hold as ruled. But research Q7 and operator ruling `DM-01M7B1Z03VF9A36FBV65431HR3`
say "F folds `OverrideSourceRule` into the endpoint rule". WP05's prompt also asked for "one predicate and one code
string", and there are two of each. Neither the WP05 reviews nor the ADR record that the fold was not done.

Fix in WP10: make decision 5's "one checked bridge" bullet say what was built: `OverrideSourceRule` stays as the load
and write reporter, the bridge checks the same predicate first, and two equal code constants exist. Then add the
departure to "Reviewed calls of the build", or flag it as an open review item for the operator (charter: "A call made
without review is logged as an open review item").

**Operator decision needed:** accept the two-predicate state as recorded, or have a follow-up work package fold the
class into the bridge. That is a code change outside WP10's owned files.

## LOW (fold now, cheap)

- **L1.** `docs/adr/0077-kind-taxonomy-and-step-authority.md:53-54` says ADR-0044 is amended at decision 3. The note
  in `docs/adr/0044-mission-types-consolidated-into-one-sanitized-shape.md:110-116` amends decisions 2 and 3. Make
  them agree.
- **L2.** `docs/adr/0064-graph-documents-are-the-only-source-of-relationships.md:106` reads "decision 11's per-value
  `impacts` and the relations: ... are retired". The per-value `impacts` list on an artifact (ADR-0028) stays. Only the
  `impacts` relation and the edge strength retire. Reword so the value list is not read as retired.
- **L3.** `docs/adr/0064-graph-documents-are-the-only-source-of-relationships.md:102-103` says
  `overrides_source_not_activatable` "is checked first". The step-enhancement rules come first (ADR-0077 decision 5).
  Say "before the table".
- **L4.** `docs/architecture/descriptors.md:329` still says "`delegates_to` edges declare runtime handoff" for agent
  profiles. The relation is retired; a handoff is a substep's `delegates_to` data.
- **L5. Issue cites.** The spec maps #515 to the endpoint table (FR-012) and #520 to `action_sequence`/`template_set`
  (FR-009).
  - `docs/changelog/2026-11-14-kind-taxonomy-and-step-authority.md:30` cites #520 for substeps.
  - The same file at line 35 cites #515 for the step links; the ruling there is DM-01M7B1YQR5ZKQPDW6DX64KPNRN.
  - WP05's `contracts/charter/changes/2026-11-13-relationship-endpoint-table.md:3` cites #514 for the endpoint table,
    which should be #515.

  Align these cites; closeout closes issues by them.
- **L6.** FR-019 says "one dated `### Breaking` fragment". The mission has four: WP05, WP06, WP07, and WP10's
  `-breaking`, which cross-references the others. `ChangelogFragmentsTest` forces one block per file, so this is fine.
  Say so in the PR body so that FR-019 reads as met.

## Not-done items (judgement)

- **Tension constructors unguarded:** rightly deferred, LOW. The only production construction is
  `PackPolicy.java:966-969`, behind `bindTension`. Relationship writes cannot name `in_tension_with`. This is a type
  gap, not a live risk.
- **`refuseStrayStepLink` vs. the load:** rightly deferred, LOW. It fails closed: a spurious 422 with nothing written.
  It is not a data or security risk. Record it in the PR's Not done.
- **The load names the type by id, validate mode by file:** a diagnostic inconsistency only. LOW, carry.
- **`PackContents` legacy constructors:** LOW, carry. They are convenience constructors, not a legacy data shape.
  The one production use, the 8-argument one at `FileSystemPackInspector.java:175`, is the load-path scope by design.
- **`.kittify/charter/charter.yaml` keeps `TEMPLATE_SET`:** rightly deferred. Core's CLI generates the file, and
  ADR-0077 records it under Risks.
- **Usability script:** LOW, but fold it at landing rather than carry it. `scripts/usability/service-usability-run.sh`
  (lines 744 and 831-845, self-check lines 1006-1012) and `scripts/usability/README.md` (lines 74 and 78) still expect
  five `step-contract` links. A run now reports a false FAIL. It is not in CI, so no issue is needed. It is outside
  WP10's owned files.
- **Historical records as written:** correct; ADRs are immutable apart from notes.

None of the Not-done items is a correctness, data or security risk that warrants a follow-up issue.

## Checks run (lane-j, 4216e31fa)

- `check_docs.py`:
  - frontmatter, related, freshness, links, terms, changelog and orphans: ok.
  - plantuml: ok, with the pinned jar.
  - adr: "gap in numbering: no ADR 0076". This is the known #106 reservation and not counted.
- `scripts/ci` unittest: 29 tests, OK. `scripts/docs/tests` was not run: pytest is not installed.
- markdownlint-cli2 0.18.1: 0 errors. cspell 9.2.0: 0 issues (347 files).
- `:contracts:check`: green (135 fragments well formed).
- `:packs:test`: green (5163 tests, 4 skipped).
- `:deployable:test`: green (1992 tests, the new pack-removal scenario included).
