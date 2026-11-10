---
affected_files: []
cycle_number: 1
mission_slug: agent-profile-reads-01M70ZDM
reproduction_command:
reviewed_at: '2026-11-10T09:04:13Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP09
---

# WP09 review, cycle 1: changes requested (reviewer-renata)

Reviewed: 59fb6bf4 and 25a59918 on lane-i (HEAD e12ad132). Pin a2a93794. Final surface bbedd88b.

## What holds

- The red baseline is red for the right reason. At the WP01 base, the list contract
  (`contracts/charter/paths/packs_packId_artifacts.yaml`) says that a `kind` that is not served is "a 400 Problem
  without a code". A link-only client reaches the profile through the list, so P2's bare 400 is the contracted answer.
  T048's "404 `kind_not_found`" is the answer of the item path, which a link-only walk never reaches first. WP10
  records this as a deviation from T048's wording, with its reason. I reproduced the run at e12ad132: P2 400, A1
  missing `activations`, exit 1.
- Fold U2:
  - built-in is extracted with `git archive`, and its git tree is checked against `HEAD:packs/built-in`;
  - internal and qa are staged copies without exactly the files the service names (#451);
  - the project pack is skipped and named (#452).

  Every workaround prints and is summarised. I confirmed the 7 unheld selections against the real service: the
  pointers `/selected/directive/11,12` and `/selected/styleguide/0,3,4,5,8` are PROJECT_001, PROJECT_002 and the five
  project style guides. The pointer-to-id mapping is correct.
- Safety:
  - the token travels in the environment and a mode-600 curl `-K` file, never in argv;
  - the temp dir comes from `mktemp -d`;
  - the trap stops the service and removes the dir. I tested a normal exit, `--keep` followed by a manual kill, and
    SIGTERM mid-run (rc 143). No jar was left running and no temp dir was left behind;
  - the p95 is a correct nearest-rank calculation;
  - pagination follows `next`;
  - the exit codes match the README.
- The slug table has 16 entries, which matches all 16 `activated_directives` of `charter.yaml`. Every id exists in
  the pinned packs: QA_003 is in qa, NO_FULL_HEAVY_SUITES_IN_MISSION is in internal, and PROJECT_00x is in the project
  pack. The script runs under `python3 -I`.

## Blocking

**B1: the governance walk fails on the approved surface for a script reason.**

- **Where:** `scripts/usability/service-usability-run.sh:414-441` (`walk_entries`, the missing-relation branch at
  427-436), and `README.md:38-45`.
- **Failure:** I ran the script with `--jar` built from bbedd88b, the approved WP01–WP07 code:
  ```
  [FAIL] P7 expected relation 'artifact' in _links of a governance entry of the pack-scoped read (first seen on procedure/canonical-source-discipline)
  (P7: 35 held entries ... 15 failed)
  RESULT FAIL hops=29/45 failed=16
  ```
  The 15 failures are all `procedure` entries. `ProfileRead.linksArtifact` and `EffectiveProfileRead.linksArtifact`
  at bbedd88b link `artifact` only for a held entry of a served kind. `procedure` has no pack-scoped read, so there
  is nothing for the link to point to. The script requires `artifact` (and, in A9, `effective-artifact`) on every held
  entry. WP10's green run would therefore be red even after WP08 lands.
- **Fix:** Expect the relation only where the contract promises it: on a held entry of a served kind.
  - Stay link-only. Decide whether a kind is served by following the root `artifacts` link with
    `packId=<entry.packId>` and `kind=<entry.kind>`. A 200 means the kind is served, so a missing link fails the hop.
    A 400 means the kind is not served, so the absence is expected. Cache the answer per kind.
  - Alternatively, keep an explicit served-kind table that cites the contract.
  - Either way, never skip silently. Print and summarise "not linked, kind not served: procedure×15", and keep
    failing on a served kind without the link.
  - Update the README walk text (lines 39-43) to match.

**B2: an option given without a value makes the script loop forever.**

- **Where:** `service-usability-run.sh:33-39`.
- **Failure:** `service-usability-run.sh --profile` runs until it is killed (`timeout 3` gives rc 124). `shift 2`
  fails when only one argument is left. Without `-e`, the loop then repeats on the same argument.
- **Fix:** Check `$# -ge 2` for every option that takes a value, and `die 2 "<opt> needs a value"` otherwise.

**B3: `expand` corrupts an href that has two or more filled query variables under bash 5.2.**

- **Where:** `service-usability-run.sh:157` and `:166`.
- **Failure:** bash 5.2 turns `patsub_replacement` on by default, so an unquoted `&` in the replacement is replaced
  by the matched text. I checked this here (bash 5.2.21): `h='/x{?kind,state}'; q='kind=a&state=b';
  ${h/\{\?*\}/${q:+?$q}}` gives `/x?kind=a{?kind,state}state=b`. Today's walk fills only one query variable per link,
  so the bug is latent. The activation `artifacts` link is `{?kind,state}`, and the mission-types session extends this
  script.
- **Fix:**
  - Quote the replacement: `href=${href/\{\?*\}/"${query:+?$query}"}` and `href=${href//"{$k}"/"$v"}`.
  - Percent-encode the values (RFC 6570).
  - Add a two-variable case to a small self-check.

**B4: the green claim rests on a stand-in that does not match the approved surface (QA_003, no fake green).**

- **Where:** the 25a59918 commit body ("Checked green against a stand-in server that answers the planned shapes
  (--attach): 23/23 hops, exit 0").
- **Failure:** The real surface does not answer those shapes:
  - B1 above;
  - the Activation answer carries only `self` (see the WP08 carry note below).

  The stand-in is not in the repo, so the claim cannot be audited.
- **Fix:**
  - In the fix commit, state that the stand-in diverged and how.
  - Record a real run against a bbedd88b jar (or the current mission branch). After B1, the pack-scoped walk
    (P1–P7, TP) should be green there. The activation walk stops at A1 until WP08 lands.
  - Do not claim green from a stand-in.

**B5: the slug self-test does not pin the table's values.**

- **Where:** `scripts/usability/charter-to-activation.py:125-129`.
- **Failure:** A mutation from `"DIRECTIVE_032"` to `"DIRECTIVE_23"` passes `--self-test`. The service would then
  refuse the wrong id with 422 `unknown_artifact`, and the script's `unheld-selections` workaround would drop it. A
  client bug would be reported as a pack gap.
- **Fix:**
  - The self-test checks every entry. For example, each `NNN-…` slug maps to `DIRECTIVE_NNN`, and the rest are
    listed explicitly.
  - It also checks that every id in the live charter's `governance.charter.selected_directives` appears among the
    table's values.

## Should fix (not blocking on their own)

- **S1: unknown `activated_*` keys are dropped silently.** `charter-to-activation.py:45-53,98`. A future
  `activated_tactics` or `activated_agent_profiles` would vanish from the document without a word. Fail with exit 3 on
  any top-level `activated_*` key that `KIND_OF_KEY` does not map. The same applies to an indented list that
  `read_sections` would miss (lines 69-89).
- **S2: the RESULT line does not show the unheld selections.** `service-usability-run.sh:564`. Add `unheld=<n>`, so
  that WP10's acceptance record shows a qualified pass on its last line. In the README, say that exit 0 with unheld
  selections is a pass with named gaps, to report to the operator.
- **S3: `rm -rf` runs on paths the service returns.** `service-usability-run.sh:301-302`. Refuse a pointer that is
  absolute or contains `..` before you remove it.
- **S4: `follow` builds the URL as `BASE + href`.** `service-usability-run.sh:186,447,230`. An absolute href would
  break. Resolve the href as a relative reference against `BASE`, or fail with a clear message when it is absolute.
- **S5: the Gradle daemon is left running.** The `./gradlew` run leaves a daemon behind, which the repo's own rules
  accept (never `--stop`). Say so in the README.

## Anti-pattern checklist

| # | Item | Result | Reason |
|---|---|---|---|
| 1 | Dead code | PASS | Both scripts are entry points. The `.sh` calls the `.py`. |
| 2 | Synthetic fixture | FAIL | B4: the only green evidence is a stand-in shaped differently from the contract. |
| 3 | Silent empty return | PASS with S1 | S1 is a silent drop. |
| 4 | FR coverage | FAIL | FR-011 is not yet provable on the approved surface (B1). |
| 5 | Frozen surface | PASS | Only `scripts/usability/**` is touched. |
| 6 | Locked decision | PASS | No URL building in the walk. |
| 7 | Shared files | PASS | |
| 8 | Fragility | PASS | |
