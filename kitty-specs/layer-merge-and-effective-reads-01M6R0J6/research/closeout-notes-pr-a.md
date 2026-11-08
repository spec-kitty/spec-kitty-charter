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

## From the WP17–WP20 reviews (runner, 2026-11-08)

- Confirm the "service's understanding of core" lines of DIV-601 to DIV-613 against core if core's registration
  behaviour can be found; otherwise leave them marked and list them in the PR handover as open.
- Confirm the first green run of the `git-source` CI job on the PR, and that `CI gate / gate` requires it through the
  `service` result.
- WP18 leftovers, unchecked: native-image hints for `ProcessBuilder` in `infra.fetch.git`, and whether the PIT floor
  group `infra.fetch` covers the `git` subpackage. Check both; fix if cheap, else list as follow-ups.
- WP17 follow-up: the double load of the staged tree (name check, then registration) costs time only; list it.
- WP19 follow-ups: `pack-manifest.yaml` goes stale after per-artifact writes and DELETEs (documented as DIV-608);
  `impact.committed` is not called for per-artifact writes or removals (notices are PR B, WP25).
- Integration: lanes p and q conflicted; the lane-r merge 0682e960 (and c7417db9's dead-helper fix) is the resolution
  to carry into the rebuilt history.

## Pre-merge squad, 2026-11-08 (runner)

Four lenses (reviewer-renata seams, architect-alphonso trust boundaries, doctrine-daphne contract and docs fidelity, qa-strategist-quinn test adequacy), all "ready with follow-ups". Findings and dispositions, verbatim from the runner's record:



### architect-alphonso (trust/architecture) — ready with follow-ups
- A1 HIGH StagingSweeper deletes any stale child of staging root; staging-dir unvalidated (config-root => data loss). Fix: sweep ULID-named only; refuse overlapping/system staging-dir at startup; tests.
- A2 MEDIUM staging root/job dirs/uploaded archive/extract dirs default perms; plan requires owner-only.
- A3 MEDIUM upload body held in heap via ResourceHttpMessageConverter (10 x 32 MiB, x2 peak).
- A4 MEDIUM slot reserved before body read (slow upload holds slot) — known/accepted limit (WP16 review); LOW: expired RESERVED job -> controller Files.copy 500.
- A5 MEDIUM git memory unbounded (index-pack inflation); pin core.bigFileThreshold etc.
- A6 MEDIUM free-space check late, packs volume only, 2x margin vs ~4x zip peak.
- A7 LOW SourceUrls userinfo regex misses '/' in userinfo -> credential in audit log for malformed URL.
- A8 LOW application ring does fs/thread work; follow-up StagingArea port.

### doctrine-daphne (contract/docs fidelity) — ready with follow-ups
- D1 HIGH ADR-0065 D13 says DELETEs need charter.registration.enabled; code gates only PUT/POST. Decide: gate DELETEs (spec/ADR say so) or reword.
- D2 MEDIUM 11 lock-taking writes (9 per-artifact/relationship + 2 DELETEs) answer 503+Retry-After but contract declares no 503; add RegistryBusy.
- D3 MEDIUM per-artifact 500->503 and activation writes' new 503 RegistryBusy filed under Changed; should be Breaking.
- D4 MEDIUM CharterRefusalCode.yaml:60, 128-131 stale (12 operations answer 422).
- D5 MEDIUM http-status-codes.md:58 (201 "Proposed: POST /packs"), :83 ("only"), :94 (Retry-After on every coded-less 503).
- D6 MEDIUM whole-pack-registration.feature header says all @WIP/reserved.
- D7 LOW pack-registration.md:54 (@WIP) and :16 (git 428 wording).
- D8 LOW ADR-0064 amendment misses POST /packs and artifact DELETE.
- D9 LOW getPack description lacks built-in 404.
- D10 LOW stale javadoc RegistryWriteLock:20, GitCommand:27.
- D11 LOW changelog "new required CI job" -> counted by the required gate.
- D12 LOW contract-delta.md:126 edit link (planning).
- D13 LOW in-PR fix fragments read as changes in release notes (url pattern, PackSourceRecord.url, edit link).

### qa-strategist-quinn (test adequacy) — ready with follow-ups
- Q1 MEDIUM RemovalConcurrencyTest isBetween(0,RUNS) can't fail; no overlap proof.
- Q2 MEDIUM RegistrationJobs constants (2, 8, 90 s, 1 h, 1000) pinned only by symbol; add literal-pin test.
- Q3 MEDIUM NFR-010 audit completeness not proven wired (AuditLog bean; filter order).
- Q4 LOW RegistrationJobsTest running-limit not asserted; await at :487 immediately true (flake risk).
- Q5 LOW pack-removal 422 scenario pins different property; add removal control with an activation not listing the pack.
- Q6 LOW exception checks without discriminator (ZipBoundsTest:264, PackRegistrationServiceTest:175, PackCommitStoreTest:532).
- Q7 LOW git-source required? (gate counts it via service result — confirm on PR).

### reviewer-renata (seams) — ready with follow-ups
- R1 HIGH = D1 DELETEs not flag-gated (plan.md:964-967 security squad 8, ADR-0065 D13, FR-037 say gated).
- R2 MEDIUM DELETE audit entries carry no revision before/after (NFR-010).
- R3 MEDIUM history heal adds (v1,revB) after a per-artifact PUT/DELETE, making v1 unrestorable with either revision.
- R4 LOW relationship DELETE checks If-Match before 404; artifact DELETE the reverse.
- R5 LOW per-pack generation key: per-artifact uses pack.value(), whole-pack configured name (no reader in PR A; PR B facts cache).
- R6 LOW activation listing IOException -> 500 through ActivationImpactAdapter.
- R7 LOW no removal/per-artifact test pins the refusal on an unreadable stored pack.
- R8 LOW stale javadoc RegistryWriteLock:20; RefusalCode javadocs end "Reserved."

### Dispositions (runner, 2026-11-08)
Folded into PR A (accepted), each as its own commit:
- F1 R1/D1: gate both DELETEs on charter.registration.enabled (503 pack_source_unavailable), declare it, fragment, flag-off test/scenario.
- F2 A1: sweeper sweeps only job-id-named entries; staging-dir validated at start-up.
- F3 A2: owner-only staging root, job dirs, uploaded archive file, extract dirs.
- F4 R2+Q3: DELETE audit carries revision before/after; one wired audit test with a recording AuditLog.
- F5 A7: SourceUrls cuts userinfo up to the last '@' of the authority.
- F6 D2+D3: declare 503 RegistryBusy on the 11 lock-taking writes; Breaking fragment for 500->503 and activation RegistryBusy.
- F7 R3: heal only when the history has no entry for the stored version; test with PUT then registration.
- F8 Q1+Q2: RemovalConcurrencyTest proves overlap; literal pins for RegistrationJobs constants.
- F9 docs/contract wording: D4, D5, D6, D7, D8, D9, D10, D11, R4 (state order in contract), R8; changelog page "Core warns"/ADR bullets.
Deferred with rationale (tracked in a follow-up issue): A3 stream upload body; A4 slow-upload slot holding (accepted limit, documented) + expired-reservation 500; A5 git memory pins (needs real-git CI to verify); A6 free-space margin/staging volume; A8 StagingArea port; Q4, Q5, Q6 test sharpening; R5 generation key (PR B handover, must fix before facts cache); R6 activation listing 500; R7 salvage refusal tests; D12 planning contract-delta (runner fixes); D13 in-PR fix fragments (release-notes polish).

Deferred items are tracked in #418.
