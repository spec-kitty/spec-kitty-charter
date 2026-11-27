---
okf_version: "0.2"
title: Next release candidates, 2026-11-25
description: The proposed next candidates (charter 0.2.0-rc.4, packs 0.1.0-rc.3, service 0.1.0-rc.4), what changed, the rehearsals, the order and the gates.
doc_status: draft
updated: 2026-11-27T00:00:00Z
type: Plan
kind: Planning
related:
  - guides/service-build
  - development/contract-consumers
  - adr/0046-service-release-line-and-native-packaging
audience:
  - profile: governance-lead
    guidance_text: Decide the questions at the end, then cut the candidates in the order given; no tag is cut by this proposal.
  - profile: charter-service-contributor
    guidance_text: Read what the release pull request changes and what you must not merge while the tags are out.
---

# Next release candidates, 2026-11-25

A proposal for the operator (#468, #45). It cuts no tag: only the operator pushes release tags. The
next candidate is **not one number** across the three release lines:

| Line | Last tag (commit, date) | Next candidate | Final it leads to |
|---|---|---|---|
| Charter contract, `contract-charter-v*` | `0.2.0-rc.3` (`33f3ad46e`, 2026-11-20) | `0.2.0-rc.4` | `0.2.0` |
| Packs contract, `contract-packs-v*` | `0.1.0-rc.2` (`33f3ad46e`, 2026-11-20) | `0.1.0-rc.3` | `0.1.0` |
| Service, `service-v*` | `0.1.0-rc.3` (`cb9b15426`, 2026-11-20) | `0.1.0-rc.4` | `0.1.0` |

After a candidate the next version is the same `X.Y.Z` (operator ruling R1; `contracts/lint/README.md` and
`contracts/packs/README.md`), so no line moves to a new minor.

## Gating

- **open-packs#343 must be green against `main`** before the release pull request merges. It is not yet: its
  Mission is at WP01 review, with WP09 to WP11, consolidation and a rebase to follow (dispatcher, 2026-11-25).
- **#196, the tag ruleset on all three tag families, must be in place before any FINAL tag** (`0.2.0`, `0.1.0`,
  `0.1.0`). A release candidate may be tagged without it (operator ruling, 2026-11-25). The ruleset is not applied
  today, so the release procedures now say so instead of describing it as in place.
- **Met: PR #664 (#645), PR #668 (#663) and PR #671 (#661) have landed** (operator rulings, 2026-11-25). The
  classifier cases and the letter-case classification ship in `0.1.0-rc.3`, and the packs contract's released-heading
  gate is in place before its tag. doc-kitty re-syncs against that release.

## What changed since the last tags

### Charter contract: 35 fragments in `contracts/charter/changes/`

- **Breaking (12):** registration and validate mode judge a letter-case variant of a pack file in lower case
  (#661); `case_variant` replaces `retired` for a root letter-case variant (#624); the pack-name part of a
  reference is a pack id (#582); the anti-pattern file is held and judged, an `anti_pattern` node without its file is
  refused, and styleguides and procedures drop inline anti-patterns (#154); `manifest_stale` is refused; preset-item
  refusals are judged over the closure; `PackDoctorReport` is the one validate report, with `tool`, `inputs` and
  `summary` required; the pack checker is renamed pack-doctor with no alias; a special file (FIFO, socket, device) is
  refused; YAML or front matter over 1 048 576 code points is refused.
- **Added (2):** `500 pack_unreadable` lists `violations` (#617); `PackDoctorReport.repairs` (#651).
- **Changed (21):** closure refusals name every fault (#556); pack-doctor's `skipped` reasons (#624), `--full`
  (#632) and its `REFERENCES` gate, the case-only scope and the policy lock's case-only check, policy-lock URN order,
  packs named once and by id, `--report` write guards, XML escaping, guarded round-trip reads and the remediate
  writes; violation collection no longer quadratic in the number of violations (#648); the scenario step `Given the
  registration workers are busy` (#659); the round-trip code (#636), the `pack_unreadable` detail (#628), the in-file pointer (#617), the
  quoted-number fix (#651) and the write-revalidation pointer (#654); one fault per anti-pattern file.

The 0.2.0 line has no final release yet, so the compatibility check has no baseline; the `### Breaking` list rests on
the fragments alone.

### Packs contract: the `## 0.1.0-SNAPSHOT` section

- **Breaking:** the `<pack>::` qualifier and the descriptor `name` are pack ids (#582); the lifecycle guard `ref` is
  at most 135 characters; procedures and styleguides drop `anti_patterns` (#154); a pack file in another letter case
  is judged as its lower-case spelling (#661): an exclusion takes its file in any letter case, a retired family is
  refused wherever it stands, and every spelling has its lower-case spelling's kind, with the cases of #645.
- **Added:** `schemas/anti-pattern.schema.json` and its `files` rule.
- **Changed:** the README and comments name `case_variant` (#624).

Packs contract files changed since `contract-packs-v0.1.0-rc.2` (`file-kinds.yaml`, `schemas/`, `conformance/`), so
under #650 a service release now needs a new packs release: the "unchanged since its release" rule no longer applies.

### Service: 150 commits since `service-v0.1.0-rc.3`

pack-doctor replaces the old pack checker (with `validate --full`, `--report`, `--format`, `remediate` and its
repairs), one answer on every surface for the refusals above, and the #650 release gate. The checker's release asset
is renamed `pack-doctor-<ver>.jar` (ADR-0083 decision 6, now on ADR-0046's status line). `service-release.yml` writes
fixed release notes that do not mention it (ruling 3). No configuration key or environment variable of the service
changed.

## Rehearsals on `main`

Each rehearsal script ran on `main` at `f6f21e4c0` on 2026-11-25 (Java 25, `_JAVA_OPTIONS` cleared), in a
throwaway clone with no remote, and published nothing. All three exited 0. Three charter fragments have landed
since (#648, #659, #661), so the release pull request assembles 35, not 32.

| Rehearsal | What it showed |
|---|---|
| `rehearse-contract-release.sh` | A tag is refused while unreleased, while a fragment is left, or when malformed. The next unused candidate it chose is `0.2.0-rc.4`; it assembled the 32 fragments then on `main` and built the four assets, with a matching checksum. `0.2.0-SNAPSHOT` reopens over the candidate, and the compatibility check passes. |
| `rehearse-packs-contract-release.sh` | The unreleased and malformed tags are refused. It assembled `0.1.0-rc.3` (`packs-contract.tar.gz`: OK), refused a version above the candidate, and showed that `0.1.0-SNAPSHOT` reopens. |
| `rehearse-service-release.sh` | Every local case behaved: malformed tags, unreleased and changed contracts, a final over a packs candidate, and the 13-file asset set with the pack-doctor jar. The checks that need the remote (the commit on `main`, both contract releases published, the tag still naming the built commit) are `service-release.yml`'s and were not rehearsed. |

The rehearsals prove the procedure, not this release: they run on synthetic versions in the clone. The release pull
request runs the same scripts on its own branch, as `service.yml` does on every change.

## Order of the release

1. **One release pull request** with all three versions:
   - `./gradlew :contracts:assembleContractChangelog` (the 35 fragments into `## 0.2.0-SNAPSHOT`), then
     `## 0.2.0-rc.4` and `info.version: 0.2.0-rc.4`;
   - `## 0.1.0-rc.3` in `contracts/packs/CHANGELOG.md`;
   - `version=0.1.0-rc.4` in `gradle.properties`;
   - each changed section's opening paragraph rewritten by hand, and the docs that name the current release
     (contract consumers, generate a client, pin the packs contract) moved to the new tags.
2. **Merge it.** From here until step 6, merge nothing else: `check` refuses any commit under the released charter
   heading, and `check` refuses a packs contract file changed under `## 0.1.0-rc.3` once its tag is merged (#663).
3. **The operator tags** `contract-charter-v0.2.0-rc.4` and `contract-packs-v0.1.0-rc.3` at the merge commit, and
   waits until both GitHub pre-releases are published with all their assets.
4. **Run `service-native.yml`** with `workflow_dispatch` on `--ref contract-charter-v0.2.0-rc.4` and
   `-f cache-disabled=true` (as a release builds), and wait for green; [Building the service](/guides/service-build/)
   step 4 also allows `--ref main` while the merge commit is its head.
   After `git fetch --tags` (the gate needs both contract tags merged into HEAD), run `./gradlew checkServiceReleaseTag -PreleaseTag=service-v0.1.0-rc.4` at the merge commit.
5. **The operator tags** `service-v0.1.0-rc.4` at the same commit, and once its release publishes, adds one line to
   its notes naming the checker's jar rename to `pack-doctor-<ver>.jar` (operator ruling, 2026-11-26).
6. **Reopen** in one pull request: `## 0.2.0-SNAPSHOT` with `info.version: 0.2.0-SNAPSHOT`, `## 0.1.0-SNAPSHOT` in
   the packs CHANGELOG, and `version=0.1.0-SNAPSHOT`.

## Operator rulings

Ruled (2026-11-25 and 2026-11-26):

1. **The service's version order.** The next service candidate is `0.1.0-rc.4`: the checker's rename is absorbed into
   the unreleased `0.1.0` line, and `service-build.md` step 1 now says that after a candidate the next version is the
   same `X.Y.Z`.
2. **#661 before `0.1.0-rc.3`.** It landed before the next packs tag, with #663, as the BROAD fold (rulings of
   2026-11-25 and 2026-11-26, recorded on #661): every exclusion takes its file in any letter case, a retired rule
   its family wherever it stands, and a `files` rule its kind over a payload, an exclusion or another `files` rule, so
   every spelling of a pack file has the kind of its lower-case spelling.
3. **The checker's rename in the release notes.** After `service-v0.1.0-rc.4` publishes, the operator adds one line to
   its GitHub release notes naming the checker's jar rename to `pack-doctor-<ver>.jar` (step 5 of the order above).
4. **The dangling `service-v0.1.0-rc.2` tag.** The tag stays; [For contract consumers](/development/contract-consumers/)
   now says it was never published and to pin `service-v0.1.0-rc.3` or later.
