---
affected_files: []
cycle_number: 1
mission_slug: procedure-antipattern-toolguide-round-trip-01M60H9B
reproduction_command:
reviewed_at: '2026-10-29T12:16:19Z'
reviewer_agent: claude
wp_id: WP11
---

# WP11 review feedback (cycle 1)

Overall the work is strong. ADR-0043 matches the built code on every point checked: the id and role
patterns, `guide_path`, the GuideBlock order, the H1 rule, the oneOf contract and the 1 MiB cap, the
422 and 409 codes, the fetch outside the lock, and the fetcher's direct client, allowlist, global
unicast check, JEP 418 guard, start-up refusal, 3 redirects, 10 s deadline and 1 MiB cap. The ADR-0039
amendment, glossary, guide, changelog and #186 list are accurate. One factual error blocks approval.
The other two points are small and should be fixed in the same pass.

## Issue 1 (blocking): wrong procedure count in ADR-0043 Context

`docs/adr/0043-...md` line 56 says: "At `openPacksRef` `01edb4e` the open packs hold 23 procedures and
24 toolguide guides".

At `01edb4e` there are 41 `*.procedure.yaml` files: 23 in built-in, 13 in internal and 5 in qa. The
24 guides are counted across the same three packs: 18 in built-in, 3 in internal and 3 in qa. The
sentence therefore mixes two scopes. The spec (NFR-001, SC-002) and data-model both say 41.

Fix: "hold 41 procedures and 24 toolguide guides across the built-in, internal and qa packs".

## Issue 2 (fix in the same pass): incomplete drop list in decision 6

Decision 6 lists the justified drop as "an `actor: agent` step that carries no role". The harness
drops both agent and system (`LegacyProcedureReader.ROLELESS_ACTORS = {agent, system}`;
`FieldMappingVerifier`: "actor agent/system carries no role"). Decision 2 of the same ADR also says
"`agent` and `system` steps carry no role".

The pin holds no `actor: system`, so the effect is nil, but the ADR contradicts itself. Fix: "an
`actor: agent` or `actor: system` step that carries no role".

Optional, given WP09's rework: you could add that YAML comments in the old files are reported as
manual decisions, and folded into a reference `reason` where that is faithful. Nothing in decision 6
contradicts this.

## Note for the orchestrator (not a WP11 defect)

Merging lane-k into `origin/feat/194a-procedure-antipattern-toolguide` conflicts in
`docs/adr/README.md` and `docs/changelog/README.md`, because the lane lacks the 0041/0042 rows and the
#195 changelog rows. It also conflicts in `docs/adr/0040-...md` and `contracts/charter/CHANGELOG.md`,
which come from mission-branch divergence, not from this WP.

Resolve each by keeping both sides:

- the ADR README: the landing rows, the 0039 row "with Amendments 2026-10-27 and 2026-10-29", and
  the 0043 row;
- the changelog README: both 2026-10-29 rows plus the landing rows.

With that resolution, `check_docs.py all` and cspell are green. markdownlint reports only the
inherited MD018 at ADR-0031 line 55.
