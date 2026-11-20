---
title: "Hotspots and churn, 2026-11-20"
description: Where change and complexity meet in the charter's main code at 25e24c37, with hotspots, change coupling, the trend since 2026-11-12, and squash-merge caveats.
doc_status: draft
updated: 2026-11-20T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the files that change most and are most complex, which files change together, and how the hotspots moved since 2026-11-12.
---

# Hotspots and churn, 2026-11-20

Lens L1 of the [codebase assessment of 2026-11-20](/plans/engineering-notes/codebase-assessment-2026-11-20/), pinned
at `25e24c37`. It reads the git history and complexity metrics to find hotspots (files that change often and are
complex), change coupling and trends; it changes no code.

**Baseline:** `25e24c372bae838d95a9328be57ee2063f6c05d1` (origin/main, 2026-11-19 21:44 +0100). History: 2,235 commits,
**0 merge commits** (main is linear: landings arrive by rebase or squash). Trend baseline: `fd20faacb` (2026-11-12 06:52,
the "line references" commit of the 2026-11-12 assessment). `ac6af296` does **not** exist in this clone (`git cat-file`
fails), so it could not be used. Read-only: sources were measured from `git archive` extractions, not from a checkout.
No correctness, data or security defect was found by this lens (it reads history and metrics, not behavior).

## Method and definitions

| item | definition |
|---|---|
| History | `git log 25e24c37 --no-merges -M --numstat`; each path is resolved through `-M` renames to its name at 25e24c37 |
| Bookkeeping commits (excluded) | subject matches `^(chore\(\|op\(\|docs\(mission\|docs\(record-analysis)`: 350 commits. Only one of them touches main Java (`412125e0`, 2 files), so the filter removes process noise, not code history |
| Excluded paths | `kitty-specs/**`, `kitty-ops/**`, `.kittify/charter/generated/**`, `**/build/**`: 3,424 of 21,605 file-touches |
| Generated code | **None committed.** `charter-api` server interfaces/DTOs are generated into `build/generated/openapi` (`build-logic/src/main/kotlin/charter.contract-api.gradle.kts:16,25`); the Java client's `generated` source set likewise (`charter.client-java.gradle.kts`, `charter.hand-written.gradle.kts:1-10`). `service/charter-api` holds only `build.gradle.kts`; `client-java/src/main` holds one hand-written file (`ProblemDetails.java`). `.kittify/charter/generated/*` (doctrine YAML) is excluded. ADR-0047 doc indexes are generated *blocks inside* hand-written docs: not separable, docs are outside the code metrics anyway |
| Complexity | lizard 1.17.13 on `service/*/src/main/**/*.java` + `client-java/src/main` (949 files). **Two lizard corrections:** (a) Java wildcard `?` (`Map<?, ?>`) is counted by lizard as a ternary; 808 wildcards rewritten before measuring (raw sum CCN 11,908 → 11,577); (b) lizard 1.17.13 does not parse `record R(...) { body }`: the whole body is one pseudo-function. 383 files (10,930 NLOC, 1,060 CCN) are affected: kept in sum CCN, kept out of max CCN and function count. After the correction **no main function exceeds CCN 15**, matching the Checkstyle gate (`config/checkstyle/checkstyle.xml:75-77`, max 15, no grandfathering) |
| revisions(f) | non-bookkeeping commits touching f |
| rev≤30f(f) | same, counting only commits with ≤ 30 non-excluded files (`code-maat`'s default max changeset; the squash/sweep filter) |
| **Hotspot score** | `revisions × sum CCN` (sum of lizard CCN over the file's functions). `norm = (rev/max rev) × (CCN/max CCN)` |
| **Coupling** | `shared(a,b) / ((rev(a)+rev(b))/2)`, over commits ≤ 30 files, files with rev≤30f ≥ 8, pairs with shared ≥ 5. Code files only (`.java .kt .kts .py .sh`, `contracts/**/*.yaml`). A test↔its own production class (`FooTest`↔`Foo`) is reported separately |
| Trend label | windows by graph: after = `fd20faacb..25e24c37` (7.6 days, 465 non-bookkeeping commits, 427/wk); before = commits reachable from `fd20faacb` in the 30 days before it (1,420, 331/wk, effectively the whole prior history, repo starts 2026-10-13). `rel = (rev_after/465)/(rev_before/1420)`: **heating** rel ≥ 1.5 and ≥ 3 revs after, **cooling** rel ≤ 0.67, else stable. Share-based so the faster commit rate after 11-12 does not read as heating |

### Squash-merge and sweep distortion (quantified)

- 4 commits are mission squash merges (`feat(kitty/mission-…): squash merge of mission`):
  `aa1ae67b` (model-task-routing) **319 files / 98 main Java / 12,150 lines**; `36859cc4` 184 / 56 / 16,473;
  `e97d261d` 144 / 57 / 13,352; `e05fb652` 9 / 1 / 1,556. Every other mission landed as rebased WP commits.
- Large commits dominate volume: commits > 30 files are **6.2% of commits but 51.7% of file-touches and 54.9% of
  lines changed**; > 100 files: 1.3% of commits, 25.6% of touches. The largest is `e934c18c` (599 files, the [ADR-0006](/adr/0006-onion-vertical-slices-archunit/)
  vertical-slice move), then `aa1ae67b`, `8a44dfc3` (252, PackIdentifier split, #583), `fb87f516` (237, #240/#403).
- Effect on this lens: (1) revisions: a squash *under*-counts (a mission's WP history becomes 1 revision) and a
  sweep adds 1 revision to hundreds of files; (2) coupling: a 300-file commit creates ~45k spurious pairs.
- **Correction:** coupling and module coupling use only commits ≤ 30 files. Hotspot revisions are reported raw *and*
  ≤30f; the top-20 is robust: **19/20 files are the same under the filter** (only `InForceResolution` enters).
- **Caveat (cannot be corrected):** `routing` and `routing-edge` (41 main files) have **only** the `aa1ae67b` squash
  as history, so they have rev = 1 and no coupling evidence at all; 8 artifacts-edge and 8 packs files are
  squash-only too. Per module, the share of main files whose every revision is a > 30-file commit: routing/routing-edge
  100%, pack-validation 71%, artifacts-edge 67%, activations 53%, kernel 48%, packs 33%. Their churn is understated.
- Landing batches are rebased with one committer date (e.g. 2026-11-11: 226 commits by committer date vs 148 by
  author date), so the trend windows use the commit graph, not dates, for the "after" side.

## Headline tables

### Top 20 hotspots (score = revisions × sum CCN, at 25e24c37)

| # | module | file (`ai/speckitty/charter/…`) | rev | rev≤30f | NLOC | lines | sum CCN | max CCN (fn:line) | score | trend |
|---|---|---|---:|---:|---:|---:|---:|---|---:|---|
| 1 | packs | packs/infra/storage/FileSystemPackInspector.java | 56 | 35 | 987 | 1438 | 260 | 15 (parseAndValidate:1162) | 14,560 | heating (rel 2.84, CCN +96) |
| 2 | packs | packs/infra/storage/FilesystemPackStore.java | 52 | 30 | 946 | 1627 | 212 | 12 (planCompanions:1165) | 11,024 | stable (1.24, +21) |
| 3 | packs | packs/domain/PackCompositionResolver.java | 40 | 33 | 941 | 1480 | 197 | 12 (cyclesOf:1366) | 7,880 | heating (1.83, +58) |
| 4 | packs | packs/infra/storage/DomainRepositoryService.java | 82 | 51 | 403 | 680 | 69 | 4 (edges:422) | 5,658 | stable (1.19, **−64**) |
| 5 | packs | packs/infra/storage/WholePackStore.java | 51 | 32 | 573 | 856 | 107 | 9 (salvageEdge:534) | 5,457 | heating (2.51, **−33**) |
| 6 | packs | packs/infra/storage/PackCommitStore.java | 38 | 26 | 710 | 1099 | 136 | 9 (missingLiveDirectory:880) | 5,168 | heating (1.78, +27) |
| 7 | packs | packs/infra/storage/DomainRoundTrip.java | 31 | 22 | 664 | 912 | 160 | 13 (outcomeOf:302) | 4,960 | heating (2.51, +14) |
| 8 | activations | activations/application/EffectiveReadService.java | 31 | 12 | 781 | 1435 | 101 | 6 (artifact:895) | 3,131 | heating (4.23, +56) |
| 9 | packs | packs/infra/storage/PackFileSet.java | 32 | 20 | 357 | — | 88 | 10 (digested:368) | 2,816 | cooling (0.57, +9) |
| 10 | packs | packs/application/RegistrationJobs.java | 17 | 14 | 744 | 1212 | 151 | 10 (work:1064) | 2,567 | stable (1.27, +1) |
| 11 | packs | packs/infra/storage/CommitStorePackWriteStore.java | 32 | 21 | 387 | — | 70 | 7 (withoutLinks:485) | 2,240 | heating (2.09, +37) |
| 12 | activations | activations/infra/packs/ClosureViewSession.java | 21 | 7 | 542 | 779 | 96 | 7 (profileCandidates:272) | 2,016 | heating (3.36, +35) |
| 13 | packs | packs/infra/storage/PackFileKind.java | 18 | 12 | 399 | — | 99 | 8 (classify:413) | 1,782 | stable (0.87, +24) |
| 14 | pack-validation | packvalidation/roundtrip/WholePackExpectation.java | 15 | 9 | 495 | 761 | 112 | 15 (entries:711) | 1,680 | heating (2.04, +13) |
| 15 | pack-validation | packvalidation/roundtrip/MeaningComparator.java | 13 | 9 | 304 | — | 122 | 11 (differences:155) | 1,586 | cooling (0.56, +2) |
| 16 | packs | packs/application/PackRegistrationService.java | 18 | 9 | 521 | 834 | 87 | 7 (writesOf:646) | 1,566 | heating (4.80, +11) |
| 17 | packs | packs/infra/storage/StorageRootLocations.java | 15 | 12 | 350 | — | 98 | 14 (repairInterrupted:192) | 1,470 | stable (1.11, +10) |
| 18 | packs | packs/application/OfferingReadService.java | 27 | 18 | 331 | — | 47 | 5 (withFacts:450) | 1,269 | stable (1.07, +4) |
| 19 | packs | packs/application/ActionContextWalk.java | 14 | 11 | 499 | 901 | 89 | 11 (reachOf:768) | 1,246 | cooling (0.23, +26) |
| 20 | packs | packs/application/ArtifactReadService.java | 25 | 17 | 412 | 720 | 48 | 7 (servedItem:580) | 1,200 | stable (0.96, −3) |

(Paths are `service/<module>/src/main/java/ai/speckitty/charter/<file>`; pack-validation's package root is `packvalidation`.
"—" lines: not in the largest-15 list; see `build/analysis/hotspots/cx-25e24c37.json`, written by the commands under [Reproduce](#reproduce).)

### Hotspot score by Gradle module (main code at 25e24c37)

| module | files | NLOC | sum CCN | sum rev | % of total score |
|---|---:|---:|---:|---:|---:|
| packs | 498 | 44,911 | 7,997 | 2,406 | **80.4%** |
| activations | 86 | 8,176 | 1,123 | 380 | 9.0% |
| kernel | 99 | 3,286 | 666 | 307 | 2.8% |
| pack-validation | 14 | 1,254 | 305 | 49 | 2.6% |
| deployable | 18 | 1,240 | 222 | 95 | 1.1% |
| web-transport | 12 | 995 | 188 | 44 | 1.1% |
| artifacts-edge | 63 | 3,612 | 374 | 113 | 0.7% |
| missions-edge | 37 | 1,126 | 133 | 115 | 0.6% |
| missions | 23 | 956 | 127 | 66 | 0.6% |
| profiles | 26 | 1,149 | 174 | 37 | 0.6% |
| profiles-edge | 30 | 1,408 | 156 | 83 | 0.5% |
| routing / routing-edge | 20 / 21 | 491 / 558 | 59 / 45 | 20 / 21 | 0.0% (squash-only history) |

By package: `packs.infra.storage` alone is **49.4%** of the score (131 files, 16,066 NLOC, CCN 3,028); then
`packs.application` 13.3%, `packs.domain` 10.4%, `activations.application` 3.5%, `packs.infra.api` 3.4%,
`packvalidation.roundtrip` 2.6%, `kernel.domain` 2.1%. Full list: "Hotspot aggregate by package" in the generated
`build/analysis/hotspots/tables.md` (not committed; regenerate with the commands under [Reproduce](#reproduce)).

### Top change-coupling pairs (commits ≤ 30 files; own-test pairs excluded)

| # | A | B | shared | revA | revB | coupling | crossing |
|---|---|---|---:|---:|---:|---:|---|
| 1 | packs `ParadigmControllerTest` | packs `StyleguideControllerTest` | 8 | 8 | 8 | 100% | sibling tests (+ Directive/Toolguide: 80–94%, a 4-file clique) |
| 2 | contracts `charter/paths/packs_packId_artifacts_toolguide.yaml` | `…_toolguide_artifactId.yaml` | 7 | 8 | 8 | 88% | sibling contract paths |
| 3 | contracts `compat/CheckReleaseGate.java` | `compat/ReleaseGate.java` | 9 | 11 | 14 | 72% | + `contracts/build.gradle.kts` 47–53% |
| 4 | packs `application/RegistrationJobs` | packs `infra/api/PackRegistrationController` | 7 | 14 | 10 | 58% | **ring: application↔infra(api)**; also ↔ `RegistrationAdmission` 43% |
| 5 | packs `application/OfferingReadService` | packs `domain/PackCompositionResolver` | 14 | 18 | 33 | 55% | **ring: application↔domain**; strongest prod pair by volume |
| 6 | build-logic `MutationFloors` | scripts `ci/mutation_summary.py` | 5 | 9 | 11 | 50% | **module** build-logic↔scripts |
| 7 | deployable `ReservedRefusalCodesTest` | kernel `RefusalCode` | 6 | 12 | 17 | 41% | **module**; with contracts `CharterRefusalCode.yaml`↔kernel `RefusalCode` 35% (11 shared), ↔ReservedRefusalCodesTest 28% |
| 8 | contracts `packs/file-kinds.yaml` | packs `PackFileKindTest` / `PackSchemasTest` | 5 | 11 | 12/14 | 43% / 40% | **module** contracts↔packs |
| 9 | contracts `charter/paths/offering.yaml` | packs `domain/PackCompositionResolver` | 7 | 11 | 33 | 32% | **module** contracts↔packs domain |
| 10 | packs `infra/storage` cluster: FileSystemPackInspector, FilesystemPackStore, WholePackStore, DomainRepositoryService, DomainRoundTrip, PackFileSet, PackCommitStore | | 7–13 | | | 19–38% | same package; a 7-file co-change web among the top-7 hotspots |

Own test↔production pairs: 20 pairs, median coupling **58%**, 16 at ≥ 50% (e.g. `ReleaseGate`↔`ReleaseGateTest` 92%,
`PackCompositionResolver`↔its test 63%, 18 shared): tests move with their code, which is healthy.

### Module-level coupling (code files, commits ≤ 30 files)

| pair | shared | commits A | commits B | coupling |
|---|---:|---:|---:|---:|
| missions ↔ profiles | 6 | 20 | 17 | 32% |
| contracts ↔ deployable | 51 | 260 | 169 | 24% |
| contracts ↔ packs | 111 | 260 | 699 | 23% |
| kernel ↔ missions | 11 | 97 | 20 | 19% |
| contracts ↔ kernel | 32 | 260 | 97 | 18% |
| kernel ↔ packs | 71 | 97 | 699 | 18% |
| kernel ↔ profiles | 10 | 97 | 17 | 18% |
| deployable ↔ packs | 76 | 169 | 699 | 18% |
| activations ↔ contracts | 29 | 93 | 260 | 16% |
| activations ↔ profiles-edge | 7 | 93 | 15 | 13% |
| **missions ↔ missions-edge** | 1 | 20 | 19 | **5%** |
| **profiles ↔ profiles-edge** | 2 | 17 | 15 | **12%** |
| **routing ↔ routing-edge** | 0 | 0 | 0 | — (squash-only) |
| **packs ↔ artifacts-edge** | 4 | 699 | 15 | **1%** |

The edge modules are *not* change-coupled to their domains; the coupling that exists runs through `contracts`
(the contract-first seam) and `kernel` (shared vocabulary), and the edges co-change more with `activations` (11–13%)
than with their own domain module. Caveat: the edge modules have only 15–19 small commits each.

### Trend: the 2026-11-12 assessment's named hotspots, then (`fd20faacb`) vs now (`25e24c37`)

| file | NLOC then → now | CCN then → now | max CCN then → now | rev before (30d) / after (7.6d) | verdict |
|---|---|---|---|---|---|
| `packs/domain/PackPolicy` | 861 → **deleted** | 283 → — | 25 → — | 33 / 26 | **Split and deleted** on 2026-11-17 in `60ed8cbe` under **#554** (the "one read"), not #367: #367 touched it once after 11-12 (`a696265f`). Its rules went to `packs.domain.rules` (18 files, 1,417 NLOC, CCN 303, max 13; staged by `6303488c` "move PackPolicy's residue into packs.domain.rules"), `packs.infra.storage.rules` (9 files, 611 NLOC, CCN 134, max 12) and `infra/storage/Wording` (267 NLOC, CCN 112). Net CCN of the decomposition is higher (≈549 vs 283) but max per function fell 25 → 13 and the code sits in 28 small classes (package score 1.0% + 0.8%) |
| `packs/domain/PackCompositionResolver` | 670 → 941 (+271) | 139 → 197 (+58) | 15 → 12 | 25 / 15 | **heating** (rel 1.83); now #3 hotspot, 1,480 lines; its streams are mostly untagged (25 of 40 revisions carry no issue or mission ref) |
| `packs/infra/storage/FilesystemPackStore` | 818 → 946 | 191 → 212 | 12 → 12 | 37 / 15 | stable; still the largest main file (1,627 lines) |
| `packs/infra/storage/FileSystemPackInspector` | 602 → 987 (+385) | 164 → 260 (+96) | 15 → 15 | 29 / 27 | **heating hardest**: #554 made it the single `PackReading` reader (`:117`), absorbing PackPolicy's reading duties; the inner `FullRead` is at `:697` |
| `packs/infra/storage/WholePackStore` | 742 → 573 (−169) | 140 → 107 (−33) | 8 → 9 | 28 / 23 | heating in revisions but **shrinking** (refactoring heat) |
| `kernel/domain/Pep440Range` | 320 → 329 | 88 → 90 | 14 → 14 | 5 / 2 | cold, essential complexity |

Main CCN by module, `fd20faacb` → now: packs 6,430 → 7,997 (+1,567, +24%), activations +178, kernel +170, new modules
artifacts-edge (+374), pack-validation (+305), routing (+59), routing-edge (+45); missions-edge −9, deployable −1.
Heating among the top 25: 13 files; cooling: 4 (PackFileSet, MeaningComparator, ActionContextWalk, Pack); stable: 8.

### Streams on the hotspots ("team" dimension)

Authors: `Claude` 1,369 commits; `Stijn Dejongh` (operator identity, 3 e-mails) 843, of which 337 carry a
`Co-Authored-By: Claude` trailer and the rest are mostly `*(landing)` / `op(…)` / runtime commits; `spec-kitty auto-rebase` 23.
**Effectively all code is agent-written**; an author-based knowledge map is meaningless, so streams are issues and missions.
Of 1,885 analyzed commits, 674 cite an issue, 26 a mission slug, and **1,186 neither**; 514 carry the `(landing)` scope, which names a landing batch, not a stream.

| hotspot | revs | distinct issues | dominant issues | revs with no issue/mission ref | top-issue share |
|---|---:|---:|---|---:|---:|
| FileSystemPackInspector | 56 | 29 | #554 (16), #194 (5), #195 (3) | 9 | 29% |
| FilesystemPackStore | 52 | **33** | #554 (8), #194 (5), #189 (4) | 10 | 15% |
| PackCompositionResolver | 40 | 5 | #358 (5), #554 (4), #106 (3) | **25** | 12% |
| DomainRepositoryService | 82 | **37** | #554 (11), #189 (3), #537 (2) | **34** | 13% |
| WholePackStore | 51 | 24 | #554 (15), #189 (4) | 13 | 29% |
| PackCommitStore | 38 | 17 | #605, #174 (3 each) | 15 | 8% |
| EffectiveReadService | 31 | 11 | #106 (6), #602 (2) | 14 | 19% |
| RegistrationJobs | 17 | 3 | #418 (8), #517 (4) | 4 | 47% (single-stream) |

## Findings

No HIGH finding: nothing here is a correctness, data or security defect. Severities rank maintenance risk.

1. **MEDIUM: `packs.infra.storage` is the change centre of the codebase.** It holds 49.4% of the total hotspot score
   (131 files, CCN 3,028); 6 of the top 7 hotspots are in it, and those 7 form a co-change web (pairs at 19–38%, e.g.
   `DomainRepositoryService`↔`FilesystemPackStore` 13 shared, `FileSystemPackInspector`↔`PackFileSet` 36%,
   `FilesystemPackStore`↔`WholePackStore` 32%). The `packs` module is 80.4% of the score with 65% of main NLOC.
   *Evidence:* `build/analysis/hotspots/tables.md` "Hotspot aggregate by package" and "Top production↔production
   pairs"; command `bash scripts/analysis/hotspots/run-all.sh`. *Reading:* storage changes still ripple across the reader, the store, the round trip and
   the commit store together; this is where a change is most likely to need several files and to break something.

2. **MEDIUM: `FileSystemPackInspector` is now hotspot #1 and is heating hardest.** `fd20faacb`→`25e24c37`: NLOC
   602→987 (+64%), CCN 164→260 (+96), 27 revisions in 7.6 days (rel share 2.84), 16 of its 56 revisions are #554.
   It became the one `PackReading` implementation (`service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java:117`)
   and keeps the 240-line-class shape the last assessment flagged (`FullRead`, `:697`); its worst method is
   `parseAndValidate` (`:1162`, CCN 15, at the Checkstyle limit). *Command:* trend table in `build/analysis/hotspots/tables.md`;
   `git log --oneline fd20faacb..25e24c37 -- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java`.
   *Reading:* deleting `PackPolicy` moved reading responsibility here; the reader is the next class to split.

3. **MEDIUM: `PackCompositionResolver` grew instead of shrinking, and its history is the least traceable.** NLOC
   670→941, CCN 139→197, 1,480 lines (`…/packs/domain/PackCompositionResolver.java:45`); heating (rel 1.83). It is coupled
   across rings to `application/OfferingReadService` (55%, 14 shared commits, the strongest production pair by volume)
   and `application/CompositeComponentResolver` (30%), and across modules to `contracts/charter/paths/offering.yaml` (32%).
   25 of its 40 revisions cite no issue or mission. *Reading:* the offering/composition behavior straddles the domain
   resolver and two application services; changes arrive as untagged landing fixes, so its design intent is hard to recover.

4. **LOW (improvement): `PackPolicy` was split and deleted, by #554, not by #367.** `60ed8cbe` (2026-11-17) deleted it
   (861 NLOC, CCN 283, max function CCN 25 at `fd20faacb`), after `6303488c` moved its residue into `packs.domain.rules`.
   #367 touched it once after 2026-11-12 (`a696265f`). Successors: `packs.domain.rules` (18 files, CCN 303, max 13),
   `packs.infra.storage.rules` (9 files, CCN 134, max 12), `infra/storage/Wording` (CCN 112). *Command:*
   `git log --format='%h %ad %s' --date=short fd20faacb..25e24c37 -- '*/PackPolicy.java'`. *Reading:* the previous
   assessment's R5 is delivered in substance (no function above CCN 13; the parts are cold: package score ≤ 1%), at the
   cost of ~2× total CCN in the replacement and of new weight in the reader (finding 2).

5. **LOW (holds): no main function exceeds CCN 15; complexity is now breadth, not depth.** After correcting lizard's
   wildcard and record miscounts, 0 of 5,139 functions exceed 15 and 42 exceed 10, matching the Checkstyle gate
   (`config/checkstyle/checkstyle.xml:75-77`). The risk has moved to file size: five main files exceed 1,200 lines
   (`FilesystemPackStore` 1,627, `PackCompositionResolver` 1,480, `FileSystemPackInspector` 1,438,
   `EffectiveReadService` 1,435, `RegistrationJobs` 1,212) and all five are top-10 hotspots. *Reading:* the
   method-level gates work; nothing gates class size, and that is where the hotspots grow.

6. **MEDIUM: `activations/application/EffectiveReadService` is the fastest-growing hotspot outside `packs`.** NLOC +394,
   CCN 45→101 since 2026-11-12, rel share 4.23 (18 revisions in 7.6 days), 1,435 lines; #106 is its main stream (6).
   `activations/infra/packs/ClosureViewSession` follows (CCN 61→96, rel 3.36). *Reading:* the effective-read path is
   where new contract surface lands now; it is on the same trajectory `PackCompositionResolver` was on.

7. **LOW: refusal codes live in three places that change together.** `contracts/charter/schemas/CharterRefusalCode.yaml`
   (45 revisions, one of the most-changed contract files) ↔ `kernel` `RefusalCode` (35%, 11 shared) ↔ `deployable`
   `ReservedRefusalCodesTest` (28–41%). *Reading:* deliberate contract-first duplication, guarded by a parity test; a
   new refusal code costs three edits, which is the price of the seam, not a defect.

8. **LOW: per-kind sibling tests change as a clique.** `ParadigmControllerTest`, `StyleguideControllerTest`,
   `DirectiveControllerTest`, `ToolguideControllerTest` co-change at 80–100% (8 shared of 8–11); the matching contract
   path files (`packs_packId_artifacts_toolguide*.yaml`) at 88%. *Reading:* a duplication signal for the test lens: one
   rule change is edited four times; a parameterized per-kind test would remove the clique.

9. **LOW (healthy, thin evidence): the `*-edge` modules are not change-coupled to their domains.** missions↔missions-edge
   5%, profiles↔profiles-edge 12%, packs↔artifacts-edge 1%; the coupling that exists goes through `contracts` (23–24%
   with packs and deployable) and `kernel` (18%). Own test↔production coupling is healthy (20 pairs, median 58%).
   *Caveat:* each edge module has only 15–19 commits ≤ 30 files; routing↔routing-edge cannot be measured (finding 10).

10. **LOW (method caveat): mission squash merges blind behavioral analysis for whole modules.** `aa1ae67b` alone
    carries 319 files, 98 main Java files and 12,150 lines; all 41 main files of `routing` and `routing-edge` have it as
    their only revision. Commits > 30 files are 6.2% of commits but 51.7% of file-touches. *Correction applied:*
    coupling uses commits ≤ 30 files; hotspot ranks are stable under that filter (19/20). *Reading:* rebased WP commits
    (the other missions' style) keep the history these tools need; squash landings lose it.

11. **LOW: hotspots are many-hands, and most of their changes cite no stream.** `DomainRepositoryService`: 82 revisions
    from 37 distinct issues, 34 revisions untagged (its CCN halved 133→69 meanwhile, so the churn is refactoring);
    `FilesystemPackStore` 33 issues over 52 revisions. Across all 1,885 analyzed commits, 1,186 (63%) cite neither an
    issue nor a mission slug. Authors are agents (Claude 1,369; the operator identity 843, 337 with a Claude trailer;
    auto-rebase 23), so the "team" signal is issue streams, and only one hotspot (`RegistrationJobs`, #418 + #517) is
    single-stream. *Reading:* knowledge lives in issues and Ops, not people; untagged landing fixes are the gap.

12. **LOW: the registration job and its HTTP edge move together.** `packs/application/RegistrationJobs` ↔
    `packs/infra/api/PackRegistrationController` 58% and ↔ `RegistrationAdmission` 43%. *Reading:* an
    application↔adapter coupling; the job's API shape and its orchestration are not yet separated by a stable port.

## Java LOC by module at 25e24c37 (for reuse)

main = `src/main`; test = `src/test` + `src/testFixtures` + `src/driftTest`. NLOC by lizard; lines = physical.

| module | main files | main lines | main NLOC | test files | test lines | test NLOC | test/main NLOC |
|---|---:|---:|---:|---:|---:|---:|---:|
| packs | 498 | 76,346 | 44,911 | 410 | 106,522 | 87,751 | 1.95 |
| activations | 86 | 15,167 | 8,176 | 89 | 22,048 | 17,924 | 2.19 |
| artifacts-edge | 63 | 6,636 | 3,612 | 31 | 6,627 | 5,644 | 1.56 |
| kernel | 99 | 6,958 | 3,286 | 32 | 3,676 | 3,045 | 0.93 |
| profiles-edge | 30 | 2,387 | 1,408 | 17 | 3,896 | 3,312 | 2.35 |
| pack-validation | 14 | 1,989 | 1,254 | 15 | 4,327 | 3,527 | 2.81 |
| deployable | 18 | 1,950 | 1,240 | 245 | 22,928 | 17,339 | 13.98 |
| profiles | 26 | 2,231 | 1,149 | 7 | 1,653 | 1,420 | 1.24 |
| missions-edge | 37 | 2,140 | 1,126 | 10 | 1,713 | 1,473 | 1.31 |
| web-transport | 12 | 1,667 | 995 | 10 | 1,423 | 1,163 | 1.17 |
| missions | 23 | 1,849 | 956 | 5 | 1,127 | 991 | 1.04 |
| routing-edge | 21 | 972 | 558 | 6 | 498 | 408 | 0.73 |
| routing | 20 | 997 | 491 | 5 | 762 | 653 | 1.33 |
| client-java (hand-written) | 1 | 51 | 26 | 6 | 526 | 428 | 16.46 |
| pack-check | 1 | 13 | 3 | 0 | 0 | 0 | — |
| **total** | **949** | **121,353** | **69,191** | **888** | **177,726** | **145,078** | **2.10** |

`deployable`'s ratio is high because it holds the cross-module architecture, contract and acceptance tests.
`charter-api` has no committed sources (generated); `charter-reports` holds only a Javadoc overview.

## Reproduce

The scripts are in `scripts/analysis/hotspots/` (`churn.py`, `complexity.py`, `analyze.py` and `run-all.sh`). Run them
from the repository root, with full (not shallow) history. They are read-only on the repository: sources come from
`git archive`, not from a checkout. Python output is deterministic (verified by two runs). Outputs go to
`build/analysis/hotspots/` and are not committed; regenerate them with the commands below.

```bash
bash scripts/analysis/hotspots/run-all.sh   # = the three steps below
# 1. churn: git log 25e24c37 --no-merges -M --numstat, rename-resolved, bookkeeping/path flags -> commits.json
# 2. complexity at 25e24c37 and fd20faacb (lizard 1.17.13 through uvx, wildcard + record corrections),
#    from git archive extractions -> cx-25e24c37.json, cx-fd20faac.json
# 3. hotspots, coupling, trend, streams, LOC -> tables.md (the full generated tables)
```

Spot checks used in the text:

```bash
git rev-list --count --merges 25e24c37                       # 0
git show --numstat --format= aa1ae67 | awk '{a+=$1;d+=$2;n++} END{print n,a,d}'   # 340 files incl. kitty-specs; 12722+/368-
git log --format='%h %ad %s' --date=short fd20faacb..25e24c37 -- '*/PackPolicy.java'
git show --stat 60ed8cbe | head -40
git cat-file -t ac6af296                                      # fails: not in this clone
```

Thresholds are constants at the top of `scripts/analysis/hotspots/analyze.py` (`MAXCS=30`, `MINREV=8`,
`MINSHARED=5`). The full generated tables (churn summary, top 25 hotspots, aggregates by package and module, every
coupling list, the trend, streams, squash-hidden history, lizard caveats, LOC, largest files and most complex functions)
are in `build/analysis/hotspots/tables.md`, generated by `analyze.py`; they are not reproduced here.
