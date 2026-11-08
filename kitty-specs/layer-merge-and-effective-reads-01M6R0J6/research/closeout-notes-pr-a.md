# PR A closeout notes

Items to apply when PR A is rebuilt on `main` (they need files that are on `main` but not on the planning branch).

## ADR-0064 amendment (from WP10)

Append under the Status of `docs/adr/0064-graph-documents-are-the-only-source-of-relationships.md`, and set
`decision_status` to name "Amendment 2026-11-07":

> ### Amendment 2026-11-07
>
> [ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/) amends decision 4 ("One write path"). `putPackRelationship` is no longer the only operation that writes an edge. A whole-pack `PUT /packs/{packId}` writes graph documents: the author's shards and `drg/fragment.yaml`, replaced whole. `registered.graph.yaml` is included in the replace only when it carries the registration stamp (`generated_by: charter-registration`); an unstamped one is refused. The relationship `DELETE` removes a registered edge, from `registered.graph.yaml` and from no other file; an authored or derived edge answers `409 relationship_not_registered` and a `DELETE` never rewrites an author file. `putPackRelationship` remains the only operation that writes one edge into a pack without re-sending the pack. Decisions 1 and 2 stand: no artifact field mints an edge, and the author's graph documents remain the home of an authored edge. An artifact registration still writes the artifact's own content only.

Expected until then: `check_docs.py adr` reports a numbering gap at 0064 on the planning branch only.

## Gate scope (operator, 2026-11-07)

The gate runs `verifyAll` (and targeted module checks during implementation). It does not run per-target-environment builds (native or per-OS service builds) or the timing checks (`:packs:timingBudgets`, `:activations:timingBudgets`). This applies to PR A and to the B/C runner.
The whole-pack round trip (`:packs:wholePackRoundTrip`) runs only at closeout, not in the per-WP gate.

## Supply chain re-check (DIRECTIVE_051), 2026-11-07

OSV (api.osv.dev, which aggregates GHSA) reports 0 known vulnerabilities for org.apache.commons:commons-compress 1.28.0, commons-codec:commons-codec 1.21.0 and commons-io:commons-io 2.20.0 (WP13's new dependencies). Put this in the PR A supply-chain section.

## Fragments written per work package (runner, 2026-11-08)

The PR A fragments were written one work package at a time and each says what "stays reserved" at that point:
`2026-11-07-a-registration-put-and-reads-served.md` (POST and the DELETEs stay reserved),
`2026-11-07-a-pack-source-served.md` (the DELETEs stay reserved; git answers 503 until WP18) and
`2026-11-07-a-removal-served.md` (POST stays reserved). At PR A's end nothing in the `pack-registration` set is
reserved (the lane-r integration merge deleted `reserved-registration.feature`). Reword them at closeout so the
changelog describes the PR's end state, not the intermediate ones.
