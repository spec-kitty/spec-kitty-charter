# Drafts for spec-kitty-open-packs (not filed)

WP09, T035 (C-006; `charter.md`, "Orchestrating cloud sessions"; `docs/development/issue-tracker.md`, Rule 4). Six
issue drafts for `spec-kitty/spec-kitty-open-packs`. **None is filed.** The orchestrator files each one at landing,
replaces its `open-packs#TBD-<slug>` placeholder with the real number here and in the PR body, and links (f) from
spec-kitty-charter#154.

## Sources

Every value below comes from shipped code, read-only:

- **Charter:** the integrated mission code on `kitty/mission-pack-doctor-coverage-01M7YMQK-lane-h` at `744df9b61`.
  `file:line` citations without a repository name are charter paths at that commit.
- **open-packs:** `/home/user/spec-kitty/spec-kitty-open-packs` at `fb71367`, the commit its lock pins to
  `service-v0.1.0-rc.3` (`cb9b15426`). Citations are prefixed `open-packs:`.
- **Run evidence:** the shipped jar, built from lane-h's main code (`service/pack-doctor/build/pack-doctor/`,
  `pack-doctor-0.1.0-SNAPSHOT.jar`; no main source changed after the build), was run over open-packs. It wrote only
  to a scratch directory, and `git status` in open-packs was clean before and after:
  - `validate --config-root <open-packs> --full` exits 1. Its summary is 19 packs and 76 violations in 16
    `validation_failed` problems: 60 `artifact_schema_error`, 13 `drg_schema_error` and 3
    `substep_delegation_unresolved`. `CLOSURE` and `REFERENCES` are skipped, because 15 packs cannot be read.
  - `validate --pack` over each of open-packs' five invalid fixtures.
  - The old flat form, `--config-root <open-packs>`, with no command word.

The new release does not exist yet. Ruling R-w: no `service-v*` release is cut until this mission lands
(`docs/adr/0083-...md:217-219`, decision 19). So every draft writes `service-v<version>` and `<version>`, never a
real tag.

---

## (a) `open-packs#TBD-pack-doctor-repin`

> **Align with the charter release that carries pack-doctor: jar, lock field, command line, report and refusals**
>
> **Why.** Charter mission `pack-doctor-coverage-01M7YMQK` makes three changes (ADR-0083):
>
> - It renames the standalone checker from pack-check to **pack-doctor**, outright, with no alias (decisions 6 and
>   7).
> - It gives the checker a `--full` check, `REFERENCES` (decision 8).
> - It refuses new faults on every surface, registration and `pack-doctor validate` alike (decisions 1-5, 12-14).
>
> Ruling R-w: no `service-v*` release is cut until that mission lands (decision 19). This repository therefore
> aligns **once**, against the first release after it, `service-v<version>`. This issue is the whole alignment.
> Nothing in it is optional, and the charter keeps no old name or old shape beside the new one.
>
> **Timing.** When the mission merges into charter `main`, before any release, the nightly main track
> (`open-packs:.github/workflows/charter-track.yml:27-34`, cron `23 2 * * *` at `:55`) goes red. It builds
> `:pack-check:packCheckJar` (`open-packs:scripts/ci/charter_pack_check_jar.sh:55-56`), and that task no longer
> exists. It also meets the refusals below. The red track opens or updates the `charter:main-red` issue (#343). The
> release track stays green on `service-v0.1.0-rc.3` until `service-v<version>` is published. Land this alignment
> against that release, in one pull request with the curation of `open-packs#TBD-anti-pattern-files`. Do not repin
> before the curation lands: the release refuses the current tree (see "New refusals" below).
>
> **What changes: the touchpoints.** Each touchpoint below gives the current value in open-packs, then the new value.
>
> 1. **The jar on a release pin:** `open-packs:scripts/ci/charter_pack_check_jar.sh:35-36`.
>    - Today: it downloads `releases/download/<tag>/pack-check-<version>.jar` to `$work/pack-check-<version>.jar`.
>    - New: `pack-doctor-<version>.jar`. That is the release asset charter `scripts/release/service-release-assets.sh:47`
>      assembles. Its checksum is `pack-doctor-<version>.jar.sha256` (`:55`, `:69-70`), and the jar is listed in
>      `SHA256SUMS` (`:57`).
>    - Also in that file: `:30` reads the lock key, and `:37` passes the lock key name to `download_verified`. Both
>      follow the lock rename (2). The comments at `:2`, `:7-8` and `:12` name pack-check.
>    - The file name `charter_pack_check_jar.sh` is open-packs' call. Renaming it to `charter_pack_doctor_jar.sh` keeps
>      one name per tool.
> 2. **The jar on a `main` pin:** `open-packs:scripts/ci/charter_pack_check_jar.sh:55-60`.
>    - Today: it builds with `./gradlew :pack-check:packCheckJar` and globs
>      `service/pack-check/build/pack-check/pack-check-*.jar`.
>    - New: `./gradlew :pack-doctor:packDoctorJar` and `service/pack-doctor/build/pack-doctor/pack-doctor-*.jar`.
>      Charter evidence: `settings.gradle.kts:51` names the module `pack-doctor`; the task `packDoctorJar` writes to
>      `build/pack-doctor/` (`build-logic/src/main/kotlin/charter.pack-doctor-jar.gradle.kts:119-122`); and
>      `archiveBaseName` is `pack-doctor` (`:96`).
>    - Line `:60`'s error message names the old directory too.
> 3. **The lock field:** `open-packs:charter.lock.yaml`.
>    - Today: `pack_check_sha256` is the value at `:34`. Comments name it or the pack-check jar at `:7-9`, `:23` and
>      `:27-30`. The `:30` comment's "Releases before `service-v0.1.0-rc.3` ship no pack-check" becomes "releases
>      before `service-v<version>` ship no pack-doctor".
>    - New: the new key's name is open-packs' call. We recommend `pack_doctor_sha256`. Its value is the 64-hex digest
>      in the release's `pack-doctor-<version>.jar.sha256`.
>    - Every file that carries the key moves in the same commit:
>      - `open-packs:scripts/ci/charter_lock.py:9`, `:13`, `:24` (`KEYS`) and `:66-75` (the check that sets
>        `release_tag` and both digests together).
>      - `open-packs:scripts/ci/charter_pin.py`:
>        - `:10`: the usage, `--pack-check-sha256`.
>        - `:135`: `PACK_CHECK_JAR = "pack-check-{version}.jar"`, which becomes `"pack-doctor-{version}.jar"`.
>        - `:171-204`: `write_lock`, its parameter `:171`, its check `:180-181`, its values `:182-183` and the key
>          regex `:188`.
>        - `:246`: the changelog line, "pack-check `.sha256` assets".
>        - `:308`: `--pack-check-sha256`.
>        - `:332-333`: `resolve-release` emits `pack_check_sha256=jar_digest(args.tag, PACK_CHECK_JAR)`.
>        - `:335`: `resolve-ref`.
>        - `:337`: `apply`.
>      - `open-packs:.github/workflows/validate-packs.yml`: the `workflow_call` input `charter_pack_check_sha256`
>        (`:40-41`), the env and apply lines `:87`, `:90`, `:172` and `:173`, and the comments `:141-143` and
>        `:202-206`.
>      - `open-packs:.github/workflows/charter-track.yml`: the output `:117`, the call input `:222`, env `:317`, apply
>        `:342`, the summary `:364`, and comments `:14`, `:17` and `:25`.
>      - The tests that pin these names: `open-packs:tests/ci/test_charter_pin.py`, `test_workflows.py`,
>        `test_charter_pack_check.py` and `test_charter_service_check.py`.
> 4. **The command line:** `open-packs:scripts/ci/charter_pack_check.py:224-238` (`command_for`).
>    - Today: `java -jar <jar> --pack <dir> ... --round-trip`.
>    - New: the command word comes first, then the options: `java -jar <jar> validate --pack <dir> ... --round-trip
>      --full`. The old flat form is no alias. It exits 2 with `pack-doctor: no command: give validate or remediate
>      before the options` and the usage (`service/pack-doctor/.../Arguments.java:179-194`; measured: exit 2).
>    - The usage, `Arguments.java:49-72`, gives the options:
>      - `validate` takes `--config-root <dir> | --pack <dir>[:<level>][=<id>] ...`, then `[--round-trip] [--full]
>        [--report <file> [--format yaml|xml]]`.
>      - `remediate <dir>[:<level>][=<id>] ...` takes pack directories and no option.
>      - `--help` alone prints the usage.
>    - `--full` (ruling R-s; check value `REFERENCES`, R-u) checks each pack's substep delegations, references and step
>      enhancements' targets within its own closure, as registration does (`Arguments.java:63-64`).
>    - `--report <file> --format yaml|xml` writes the same report to a file, YAML by default (R-i;
>      `Arguments.java:65-67`).
>    - Use `--full` in the gate: it is what lets (d) retire the reference and enhancement-target rules.
> 5. **Exit codes and `--round-trip`**, copied from the shipped contract:
>    - The codes are in `Arguments.java:71` and `PackDoctorMain.java:46-49` and `:75-81`. `0` means valid, with
>      warnings allowed. `1` means at least one problem. `2` means a usage fault, a failure outside a pack read (the run
>      directory, the report file) or an internal error. With `2`, nothing is on standard output.
>    - What a pack holds is never `2` (`PackDoctorMain.java:62-66`). A malformed `pack_version` is a problem (`1`),
>      not the rc.3 crash (spec-kitty-charter#624). So `CRASH_ISSUE`, `FAILED` and `CRASH_SIGN`
>      (`open-packs:scripts/ci/charter_pack_check.py:58-63`) and the `failed` branch of `judge_invalid` (`:181-190`)
>      retire, with the `failed` expectation (see 8).
>    - `--round-trip` adds the `ROUND_TRIP` check, which gives one problem per cause. A stale `content_hash` or
>      `manifest_hash` is now the gate's `manifest_stale`, judged without `--round-trip`. The round trip no longer
>      reports it as `round_trip_red`. A *written*-stale field, where the writer itself is wrong, still reports
>      `round_trip_red` (ADR-0083 decision 5; charter `contracts/charter/changes/2026-11-21-manifest-stale.md`).
>    - In the round trip's detail text, `EXCLUDED_WRITTEN` is retired for `NOT_HELD_WRITTEN`, with no alias (decision
>      16; `service/pack-validation/.../roundtrip/WholePackExpectation.java:497`). A tool that matched
>      `EXCLUDED_WRITTEN` in a `round_trip_red` detail matches `NOT_HELD_WRITTEN`.
> 6. **The report keys.** Standard output is one JSON object, the tree of `contracts/charter/schemas/PackDoctorReport.yaml`
>    (it replaces `PackCheckReport`). Its top-level keys are `tool`, `inputs`, `checks`, `packs`, `skipped` and
>    `problems`, plus `summary`. All but `skipped` are required (`:22-28`).
>    - **New:** `tool` (`:30-47`), `inputs` (`:48-85`) and `summary` (`:165-200`).
>    - `checks` gains `REFERENCES`: the enum is `[PACK, CLOSURE, REFERENCES, ROUND_TRIP]`, in that order (`:86-101`).
>    - `skipped[].check` may be `REFERENCES` (`:125-144`).
>    - So the gate's `REPORT_KEYS = ("checks", "packs", "problems", "skipped")`
>      (`open-packs:scripts/ci/charter_pack_check.py:57`) fails closed on `tool`, `inputs` and `summary` (`:154-156`).
>      Add them.
>    - `CHECKS = ("PACK", "CLOSURE", "ROUND_TRIP")` (`:54`) gains `REFERENCES` when the gate runs `--full`.
>    - The layout of each problem is unchanged. It is the contract's `Problem`: `title` and `status` are required, with
>      `type`, `detail`, `instance`, `code`, `violations` and `packs`. Each violation is `pointer`, `code` and `detail`
>      (`contracts/_shared/schemas/Problem.yaml:7-55`, `RefusalViolation.yaml:5-36`). The full key list for any
>      parser is draft (e).
> 7. **New refusals open-packs content hits.** Measured with the jar above:
>    - **The inline `anti_patterns` blocks:** `artifact_schema_error`, "unknown key 'anti_patterns'", in 60 files of
>      15 packs. **The file-less `anti_pattern` nodes:** `drg_schema_error`, 13 nodes. These are draft (f).
>    - The 15 packs with inline blocks cannot be read, so `CLOSURE` and `REFERENCES` are skipped for the whole set.
>    - The three `substep_delegation_unresolved` violations are knock-on faults of those blocks:
>      - `foundation`'s `software-dev` substep `map-examples` delegates to `example-mapping-workshop`.
>      - `qa`'s substeps `review-test-plan` and `run-go-no-go-review` delegate to `qa-test-plan-review-workshop` and
>        `qa-go-no-go-readiness-review`.
>
>      Each delegate is a procedure with an inline block, so the procedure is refused and is not held. The three clear
>      with the curation.
>    - `preset_item_not_held` and `preset_item_undeclared`: none expected. See draft (b).
>    - `manifest_stale`: none today. After the curation, restamp with `remediate`. See draft (c).
> 8. **open-packs #343 (`charter:main-red`): two invalid-fixture expectations already lag charter `main`.** Charter
>    PR #626 landed after rc.3, in the commits `96ef87607` and `a04b94f18`.
>    - `open-packs:tests/fixtures/invalid/case-variant-readme/expected-code:8` says `pack-check: retired`. The jar now
>      answers `case_variant` (`service/packs/.../infra/storage/rules/StorageCodes.java:55`; contract
>      `CharterRefusalCode.yaml:423-425`). Measured: exit 1, with codes `case_variant` and `validation_failed`, and
>      no `retired`.
>    - `open-packs:tests/fixtures/invalid/malformed-pack-version/expected-code:14` says `pack-check: failed`, the
>      rc.3 crash. Its comment `:8-10` foresaw the change. The jar now exits 1 with `pack_version_invalid`
>      (`CharterRefusalCode.yaml:435`). Measured: exit 1, with codes `pack_version_invalid` and `validation_failed`.
>    - The other three invalid fixtures (`legacy-shapes`, `retired-pack-document`, `unknown-dependency`) still carry
>      their expected code under the new jar. The alignment pull request fixes the two lines. The key name
>      `pack-check:` in `expected-code` (`open-packs:scripts/ci/charter_service_check.py:42`, `:79-80`, `:143`,
>      `:147`) is open-packs' call; it follows the tool name.
> 9. **Names in CI and docs:**
>    - The `validate-packs.yml` step "Check every pack with the charter's pack-check" (`:207`) and its `REPORT_DIR`
>      (`:211`).
>    - The artifact `charter-pack-check-report` (`:218-223`; `charter-track.yml:468`, `:601`).
>    - `PACK_CHECK_JAR` in the `RealPackCheck` test run (`:217`).
>    - `open-packs:docs/guides/validate-a-pack.md:172-260` and `:299-351`.
>    - open-packs' own ADR `docs/architecture/adr/2026-11-19-3-...` is history, and is open-packs' call.
>    - Point readers at charter `docs/guides/check-packs.md` for the CLI, its report and its exit codes.
>
> **Done when.**
>
> - `charter.lock.yaml` pins `service-v<version>` with the new digest field.
> - The gate downloads and verifies `pack-doctor-<version>.jar` and runs `validate ... --round-trip --full`.
> - `charter_pack_check.py` reads the `PackDoctorReport` keys and requires `REFERENCES`.
> - The #624 crash branch is gone, and the two fixtures of #343 name `case_variant` and `pack_version_invalid`.
> - No live surface here names pack-check.
> - The release track, the nightly main track and the PR gate are green.
> - #343 is closed.

---

## (b) `open-packs#TBD-preset-item-refusals`

> **Preset items are judged against their pack's own closure: `preset_item_not_held`, `preset_item_undeclared`**
>
> **Why.** From `service-v<version>`, registration and `pack-doctor validate` (`CLOSURE`) judge every item of every
> preset against the **own closure** of the pack that declares the preset. The own closure is the pack and every pack
> it reaches over `depends_on` and `parent_pack`, never a dependent (ADR-0083 decisions 1-4; rulings R-a, R-k, R-l and
> R-m; contract `CharterRefusalCode.yaml:1131-1151`; charter guide `docs/guides/check-packs.md:131-141`). The rule
> applies in this order, one fault per item:
>
> | The item | The answer |
> | --- | --- |
> | names an artifact of the closure, but only in other letter case | `pack_reference_artifact_missing` (case-only, as before) |
> | is `must_have`, and no pack of the closure holds it as a **file** | `preset_item_not_held` |
> | no pack of the closure declares it as its kind (a file or a graph node) | `preset_item_undeclared` |
>
> Each answer is 422 at registration. Its one violation is at `presets/<id>.yaml#/activated_<kinds>/<index>`. An item
> of another kind never satisfies an entry. An `anti_pattern` is held only by its file. A composing read is not
> refused; it keeps reporting the item as `requiredNotHeld`. These rules replace two rules this repository runs in
> Python today (see (d)):
>
> - Python judges "declared" against **all** packs. The charter judges it against the own closure, which is stricter:
>   an id that only a pack outside the closure declares is now refused.
> - Python's `must_have` closure follows `depends_on` only. The charter's also follows `parent_pack`.
>
> **Which presets break.** None, as measured. A read-only script used the charter's own-closure definition. It took
> declared and held from open-packs' `declared_nodes` and `held_artifacts`, classified files with the charter's
> lane-h `contracts/packs`, and counted an anti-pattern as held by its file only. It judged every preset of `packs/`
> and `tests/fixtures/valid/`: 9 presets, 65 items, 18 `must_have`. Every item is declared in its own closure, every
> `must_have` is held as a file, and no item matches only by case. The 9 presets are:
>
> - `built-in` (`default`, `minimal`)
> - `event-driven`, `go`, `internal` (`base`), `qa`, `rust` and `spdd`
> - `tests/fixtures/valid/example-pack`
>
> No preset activates an anti-pattern (no `activated_anti_patterns` in `packs/` or `tests/`).
>
> The jar could not confirm this. Its `CLOSURE` is skipped for the whole set until the inline blocks of (f) are gone.
> Re-run `pack-doctor validate --full` once (f) lands.
>
> **What changes.**
>
> - Nothing in the content, unless the re-run after (f) finds a fault.
> - Retire the two Python preset rules (d).
> - In `open-packs:docs/guides/validate-a-pack.md:245-249`, the "What pack-check covers" table rows for the two
>   rules now read "refused" (`preset_item_not_held`, `preset_item_undeclared`).
>
> **Done when.** `pack-doctor validate --full` over every pack and valid fixture reports no `preset_item_*` problem.
> The Python preset rules are gone.

---

## (c) `open-packs#TBD-manifest-stale`

> **A stale manifest hash is `manifest_stale` on every surface; restamp with `pack-doctor remediate`**
>
> **Why.** From `service-v<version>`, a `content_hash` (of a constituent or a preset) or a `manifest_hash` in
> `pack-manifest.yaml` that does not match the pack's bytes is refused (charter #630; ruling R-b; ADR-0083 decision
> 5):
>
> - It is one `manifest_stale` violation per field, at `pack-manifest.yaml`. Its detail names the field:
>   `constituents{<path>}.content_hash`, `presets{<path>}.content_hash` or `manifest_hash`.
> - Whole-pack registration and `pack-doctor validate` refuse it, **without** `--round-trip`. It is judged after
>   `manifest_membership` and only when nothing else failed, and a path that membership names gets none.
> - With `--round-trip`, the same cause is no longer also a `round_trip_red`.
>
> Contract: `contracts/charter/changes/2026-11-21-manifest-stale.md` and `CharterRefusalCode.yaml:431-433`. Scenario:
> `contracts/charter/scenarios/registration/whole-pack-registration.feature:565`.
>
> The charter also ships the repair (ruling R-j; ADR-0083 decision 11; charter guide `docs/guides/check-packs.md:182-216`).
> `pack-doctor remediate <dir>[:<level>][=<id>] ...` does three things:
>
> - It derives and stamps each pack's `pack-manifest.yaml` exactly as every service write does.
> - It rewrites each modelled file in the round trip's canonical form. The meaning stays the same; comments, quoting,
>   key order and folding may change (ADR-0036).
> - It then runs `validate --round-trip` over the same set.
>
> It refuses, writing nothing, a pack with a fault it does not repair: any storage-verdict violation except
> `manifest_membership` and `manifest_stale`. Files the charter does not hold are left as they are. It takes no lock.
>
> **Today.** All 20 manifests here (`packs/` and `tests/fixtures/valid/`) verify, measured with open-packs' own
> `stale_manifest_fields`, so nothing is refused at the repin for this. The curation of (f) adds the anti-pattern
> files as manifest constituents and edits 60 files, so it must restamp.
>
> **What changes.**
>
> - `open-packs:scripts/ci/charter_pin.py:214-222` (`rederive`) runs `validate_pack_schemas.py --derive-manifest
>   <pack>` for every pack (`:220`). Replace that call with the pinned jar's `remediate <pack> ...` over the same
>   packs, so the pin derives manifests with the charter's own derivation, not a Python copy (#632 item 2). This also
>   gives `charter_pin.py apply` a jar to obtain: on both pins, reuse the jar script of (a).
> - Replace the "regenerate" remedy that `validate_pack_schemas.py` names (`REGENERATE_COMMAND`, used at `:1075-1080`)
>   and `--regenerate-manifest`/`--derive-manifest` in the docs with `pack-doctor remediate`. (d) retires the Python
>   derivation and the stale check.
> - Note: `remediate` also canonicalizes the form of every modelled file. Run it once over the tree in the alignment
>   pull request, after the curation, and review that diff on its own (meaning unchanged; ADR-0036).
>
> **Done when.**
>
> - The pin re-derives manifests with `pack-doctor remediate`.
> - No Python manifest derivation or stale check remains.
> - `pack-doctor validate` reports no `manifest_stale` or `manifest_membership` over the tree.

---

## (d) `open-packs#TBD-retire-python-rules`

> **Retire the Python rules that pack-doctor now judges; keep the house rules**
>
> **Why.** Charter mission `pack-doctor-coverage-01M7YMQK` makes registration and `pack-doctor validate` give one
> answer (ADR-0075 decision 9; ADR-0083). That covers six of the rules
> `open-packs:scripts/ci/validate_pack_schemas.py` runs (charter SC-002). Under the charter's canonical model, the
> charter's answer is the rule. A Python copy can only drift from it.
>
> **Retire with `service-v<version>`.** Every row below is judged by the pinned jar's `validate --full`. Remove the
> Python rule, its tests, and its line in the `--cross-pack` help (`:1567-1570`) and in
> `open-packs:scripts/ci/validate-packs.sh:24-30`.
>
> | Python rule (`validate_pack_schemas.py`) | Charter answer |
> | --- | --- |
> | `cross_pack_violations` (`:1460-1548`), the preset id no pack declares (`:1515-1518`) | `preset_item_undeclared` at `CLOSURE`, own closure (b) |
> | the same, the `must_have` not held in the `depends_on` closure (`:1519-1526`) | `preset_item_not_held` at `CLOSURE`, own closure with `parent_pack` (b) |
> | `stale_manifest_fields` (`:384-398`) and its call in `validate_pack` (`:1074-1080`) | `manifest_stale`, default `validate` (c) |
> | `derived_manifest`/`derive_manifest` (`:466-550`), `membership_mismatch` (`:515-532`) under `--derived-manifests`, and `regenerate_manifest` (`:417-443`) | `manifest_membership` and `manifest_stale` judge it; `pack-doctor remediate` writes it (c) |
> | `profile_reference_violations` (`:1301-1346`), the bare reference held only by another pack, the qualifier outside the closure, the named pack not holding it | `pack_reference_unqualified`, `pack_reference_pack_missing`, `pack_reference_artifact_missing` at `REFERENCES` (`--full`) |
> | `prompt_fragment_composition_violations` (`:1347-1417`), the target step that does not exist (`:1380-1402`) | `pack_reference_pack_missing` / `pack_reference_artifact_missing` at `REFERENCES` (`--full`; `EnhancementCompose.targetFaultsOf`) |
>
> Also retire the old anti-pattern model in the same file, with (f):
>
> - `MANIFEST_EXEMPT_NODE_KINDS` (`:84-88`) and the `CONSTITUENT_KIND_BY_SCHEMA` note (`:445-455`) leave
>   `anti_pattern` out of the manifest. An anti-pattern **file** is now a constituent.
> - `held_artifacts` (`:1183`) holds an `anti_pattern` by its graph node. Only the file holds it now.
>
> **Keep as house rules.** The charter does not judge these, by ruling (C-005, ADR-0083 decision 18; ruling R-s,
> decision 9):
>
> - The id re-declared at another level (`cross_pack_violations`, `:1493-1498`). The charter takes it as a layer
>   override (ADR-0053 decision 6).
> - The bare DRG edge endpoint that resolves to no node of any pack (`:1527-1540`; DIV-007).
> - The prompt fragment slot that the target step's prompt does not declare, the slot part of
>   `prompt_fragment_composition_violations` (`:1403-1417`). The charter refuses it only for a stored activation that
>   has it in force (C38).
> - The `specializes_from` cycle (`specialization_cycle_violations`, `:1418-1459`). It is the `--full` remainder of
>   ruling R-s. The charter's registration still refuses it per composed closure (`specialization_cycle`), but
>   pack-doctor does not judge it until charter epic #636 slices 2-4 land it in the domain factory. Keep this rule
>   until a charter release lists it under `REFERENCES` (or its successor check). Note that the Python rule pools
>   every pack's edges, which is stricter than the charter's per-closure check.
>   - The same remainder covers the relation checks, the edge-index refusals and the own-closure walk (the fallback
>     profile, role pins and profile enhancements, slice 7). Python does not run those today, so nothing is kept for
>     them here.
> - Two parts of `profile_reference_violations` are not charter rules, per `research/rule-coverage.md` row 6:
>   - It refuses a bare reference that **no** pack holds, which the charter accepts (the same family as the unheld
>     bare endpoint).
>   - It refuses a reference qualified with the profile's own pack, which the charter allows.
>
>   Keep them as house rules or drop them. That is open-packs' call; the retired rows above go either way.
>
> **Done when.**
>
> - The rows under "Retire" are gone, with their tests.
> - The house rules above remain, each with a comment that names its charter ruling.
> - `validate-packs.sh` and `docs/guides/validate-a-pack.md:239-260` state the split.
> - The gate runs `pack-doctor validate --round-trip --full`.

---

## (e) `open-packs#TBD-report-format`

> **Parse pack-doctor's report: the standard-output JSON and the `--report` file**
>
> **Why.** From `service-v<version>`, pack-doctor writes one report tree. Its only normative schema is charter
> `contracts/charter/schemas/PackDoctorReport.yaml`, which replaces `PackCheckReport` (ADR-0083 decision 10; rulings
> R-i and R-n). The tree goes to standard output as JSON, always. With `--report <file>`, it is also written to that
> file, as YAML by default or as XML with `--format xml`. Standard output and the exit code are the same either way.
> Any parser here, `charter_pack_check.py` first, must read it by this schema.
>
> **The keys** (`PackDoctorReport.yaml`):
>
> - **Top level** (`additionalProperties: false`; `tool`, `inputs`, `checks`, `packs`, `problems` and `summary` are
>   required):
>   - `tool`: `{name: pack-doctor, version: <version>}` (`:30-47`).
>   - `inputs`: `config_root` *or* `packs` (the `--pack` values as written), and `options: {full, round_trip}`
>     (`:48-85`).
>   - `checks`: a subset of `[PACK, CLOSURE, REFERENCES, ROUND_TRIP]`, in that order. `PACK` always runs.
>     `REFERENCES` runs only with `--full` and only when `CLOSURE` ran and found no fault (`:86-101`).
>   - `packs[]`: `{name, level?, path}` (`:102-124`).
>   - `skipped[]` (only when a check was asked for and could not run): `{check: CLOSURE|REFERENCES, reason}`
>     (`:125-144`).
>   - `problems[]`: each a contract `Problem` (`:145-164`).
>   - `summary`: `{packs, violations, warnings, notices, exit_code}`, where `exit_code` is 0 or 1 (`:165-200`).
> - **A problem** (`contracts/_shared/schemas/Problem.yaml`): `title` and `status` are required, with `type`, `detail`,
>   `instance` (the `file:` URI of the pack's directory, for a pack's own problems), `code`, `violations[]` and
>   `packs[]` (a composition or `REFERENCES` fault). Per pack, there is one `validation_failed` problem for its
>   violations, then one for its round-trip reasons. A composition fault has its own code and names its packs.
> - **A violation** (`RefusalViolation.yaml`): `pointer`, `code` and `detail`, all required.
>
> **The XML** (`PackDoctorReport.yaml:12-19`) is the same tree in an element-per-key mapping of its own, not JUnit's:
>
> - The root is `pack-doctor-report`, in no namespace.
> - Each array item is named for the singular of its key: `checks/check`, `packs/pack`, `problems/problem`,
>   `violations/violation`, `skipped/skip`.
> - A `type` attribute appears only on an array, an integer, a boolean or an empty object.
>
> Parsed back by that rule, the XML gives the same tree. An xUnit-compatible form may come later (R-n).
>
> **Warnings** stay on standard error, one line each: `warning: <pack>: <code> <file>: <detail>`. They never change
> the exit code, and `summary.warnings` counts them.
>
> **What changes.**
>
> - `open-packs:scripts/ci/charter_pack_check.py:54`, `:57` and `:154-156` read the new keys (also (a) item 6).
> - Consider `--report <file>` for the uploaded artifact (`open-packs:.github/workflows/validate-packs.yml:211-223`)
>   in place of the gate's own report directory. That is open-packs' call.
> - Any check on `summary.exit_code` must agree with the process exit code.
>
> **Done when.** The gate parses the report by the `PackDoctorReport` schema, with no unknown-key failure on `tool`,
> `inputs` or `summary`. Any report artifact it keeps is the schema's tree.

---

## (f) `open-packs#TBD-anti-pattern-files`

This draft is `research/open-packs-TBD-anti-pattern-files.md` (plan D12-D14, ruling R-r), carried over and updated
with what WP10, WP11 and WP12 shipped and with the jar run. That file stays as the WP10 record; this text supersedes
it for filing.

> **Adopt the `anti_pattern` kind of the packs contract: files, `rejects` edges, no inline entries (ADR-0052)**
>
> **Why.** `service-v<version>` closes spec-kitty-charter#154 whole (ruling R-r; ADR-0083 decisions 12-14):
>
> - **An anti-pattern is its own file**, `anti_patterns/<id>.anti-pattern.yaml` (WP10; charter
>   `contracts/packs/schemas/anti-pattern.schema.json`; `contracts/packs/file-kinds.yaml:228-229`). The schema is
>   closed:
>   - Required: `schema_version`, `id` (the file stem), `title`, `description`, `severity` and `bad_example`.
>   - `severity` is `smell`, `severe` or `breaking`, with no default (ADR-0052 decisions 8 and 10; ADR-0053
>     decisions 30 and 32).
>   - Optional: `good_example`.
>
>   A missing `severity` or `bad_example`, an unknown severity or an unknown key is `artifact_schema_error` at the
>   file. A file elsewhere, or one whose `id` is not its stem, is refused by the placement and stem rule.
> - **Guidance rejects it with a `rejects` edge** in its pack's graph documents (ADR-0064 decision 12). Across packs,
>   the target is pack-qualified (`<pack>::anti_pattern:<id>`) and its pack is in `depends_on`:
>   - Otherwise the answer is `pack_reference_pack_missing`.
>   - If that pack lacks the file, the answer is `pack_reference_artifact_missing` (ADR-0052 decision 11).
>   - Activating the rejecting guidance never activates the anti-pattern (decision 13).
> - **An `anti_pattern` node with no file in the same pack is refused** (WP11; ADR-0052 decision 14, ADR-0053
>   decision 31). The answer is `drg_schema_error` at the graph document, with the detail
>   `<doc>: anti_pattern:<id> is declared with no file; an anti-pattern is held by anti_patterns/<id>.anti-pattern.yaml
>   in the same pack`.
> - **`procedure.schema.json` and `styleguide.schema.json` no longer carry `anti_patterns`** (WP12; ADR-0052
>   decision 16). A file that still has the key is refused `artifact_schema_error`, "unknown key 'anti_patterns'".
>   The charter contract's `Styleguide`, `StyleguideRegistration` and `Procedure` lose `antiPatterns`.
> - A preset `must_have` anti-pattern counts only when a pack of the own closure holds its **file** (ruling R-l).
>
> **What the release refuses here, measured** with the jar over `--config-root` at `fb71367`:
>
> - Exit 1, with 16 `validation_failed` problems across 16 of 19 packs.
> - **60 files** (26 procedures, 34 styleguides, about 267 entries) answer `artifact_schema_error`, "unknown key
>   'anti_patterns'". This matches the charter packs CHANGELOG (`contracts/packs/CHANGELOG.md:46`).
> - **13 file-less nodes** answer `drg_schema_error`: the `kind: anti_patterns` nodes of
>   `packs/architecture/drg/fragment.yaml` (6) and `packs/refactoring/drg/fragment.yaml` (7).
> - **3 knock-on `substep_delegation_unresolved` violations.** They point at procedures that carry inline blocks and
>   so are not held: `foundation` `example-mapping-workshop`, and `qa` `qa-test-plan-review-workshop` and
>   `qa-go-no-go-readiness-review`. They clear with the curation.
> - **`CLOSURE` and `REFERENCES` are skipped for the whole set**, because the 15 packs with inline blocks cannot be
>   read. So no closure, preset or reference fault can show until the curation is done.
>
> The inline blocks, by pack:
>
> | Pack | Files (entries) |
> |---|---|
> | `architecture` | `procedures/event-storming-discovery.procedure.yaml` (4)<br>`styleguides/aggregate-design-rules.styleguide.yaml` (2) |
> | `drupal` | `styleguides/drupal-conventions.styleguide.yaml` (9)<br>`styleguides/drupal-security-performance.styleguide.yaml` (5) |
> | `event-driven` | `styleguides/evd-event-naming-and-payload.styleguide.yaml` (3) |
> | `foundation` | `procedures/adversarial-squad-deployment.procedure.yaml` (9)<br>`procedures/example-mapping-workshop.procedure.yaml` (4)<br>`procedures/post-merge-arch-gate-adjudication.procedure.yaml` (5)<br>`styleguides/boring-code-review.styleguide.yaml` (3) |
> | `go` | `styleguides/go-conventions.styleguide.yaml` (10) |
> | `internal` | `procedures/charter-offering-scenario-extraction.procedure.yaml` (6)<br>`procedures/cloud-session-dispatch.procedure.yaml` (9)<br>`procedures/issue-to-mission-delivery.procedure.yaml` (2)<br>`procedures/issue-triage-pass.procedure.yaml` (5)<br>`procedures/landing-contributor-prs.procedure.yaml` (5)<br>`procedures/mission-tracer-files.procedure.yaml` (3)<br>`procedures/mission-wrap-up-sequence.procedure.yaml` (7)<br>`procedures/semi-auto-delivery.procedure.yaml` (6)<br>`procedures/test-suite-quality-assessment.procedure.yaml` (4)<br>`procedures/tracker-organisation-workflow.procedure.yaml` (7)<br>`styleguides/executive-debrief.styleguide.yaml` (6)<br>`styleguides/feature-and-scenario-authoring.styleguide.yaml` (5)<br>`styleguides/report-writing.styleguide.yaml` (3)<br>`styleguides/tiered-standards.styleguide.yaml` (3) |
> | `java` | `styleguides/java-conventions.styleguide.yaml` (6) |
> | `javascript` | `styleguides/css-conventions.styleguide.yaml` (5)<br>`styleguides/html-conventions.styleguide.yaml` (5)<br>`styleguides/typescript-conventions.styleguide.yaml` (5) |
> | `pack-authoring` | `procedures/onboard-external-agent-to-pack.procedure.yaml` (9)<br>`styleguides/deployable-skill-authoring.styleguide.yaml` (4)<br>`styleguides/doctrine-instruction-voice.styleguide.yaml` (1) |
> | `planning-analysis` | `procedures/domain-aware-decision-interview.procedure.yaml` (4)<br>`procedures/issue-triage-state-machine.procedure.yaml` (4)<br>`procedures/legacy-codebase-triage.procedure.yaml` (5)<br>`procedures/spike-timebox-policy.procedure.yaml` (3)<br>`styleguides/planning-and-tracking.styleguide.yaml` (3) |
> | `python` | `styleguides/python-conventions.styleguide.yaml` (5) |
> | `qa` | `procedures/qa-go-no-go-readiness-review.procedure.yaml` (1)<br>`procedures/qa-test-plan-review-workshop.procedure.yaml` (1) |
> | `rust` | `styleguides/rust-conventions.styleguide.yaml` (9) |
> | `testing` | `procedures/bdd-scenario-lifecycle.procedure.yaml` (4)<br>`procedures/development-assist-test-cleanup.procedure.yaml` (5)<br>`procedures/disciplined-defect-diagnosis.procedure.yaml` (5)<br>`procedures/test-first-bug-fixing.procedure.yaml` (5)<br>`styleguides/given-when-then-authoring.styleguide.yaml` (2)<br>`styleguides/mutation-aware-test-design.styleguide.yaml` (4)<br>`styleguides/quadruple-a-test-format.styleguide.yaml` (2)<br>`styleguides/test-desiderata-and-boundaries.styleguide.yaml` (5)<br>`styleguides/testing-principles.styleguide.yaml` (4) |
> | `writing` | `procedures/meeting-minutes-pipeline.procedure.yaml` (7)<br>`styleguides/common-docs.styleguide.yaml` (2)<br>`styleguides/divio-type-discipline.styleguide.yaml` (2)<br>`styleguides/docs-accessibility.styleguide.yaml` (2)<br>`styleguides/docs-freshness-sla.styleguide.yaml` (2)<br>`styleguides/meeting-minutes-format.styleguide.yaml` (6)<br>`styleguides/plain-language.styleguide.yaml` (2)<br>`styleguides/professional-communications.styleguide.yaml` (3)<br>`styleguides/publication-authority.styleguide.yaml` (2)<br>`styleguides/research-citation-discipline.styleguide.yaml` (4)<br>`styleguides/writing/kitty-glossary-writing.styleguide.yaml` (4) |
>
> **Curation** (ADR-0052 decision 15; the open-packs curation issue `open-packs#TBD-anti-pattern-curation`, drafted
> in charter `docs/plans/triage-2026-11-12-rescopes-applied.md`, "Left to file"):
>
> 1. Move each entry to a file in the pack whose guidance rejects it (ADR-0052 decision 11).
> 2. Dedupe by meaning: about 150 distinct anti-patterns.
> 3. Give each a `severity` and a `bad_example`. None is defaulted.
> 4. Give each file-less node its file.
> 5. Add a `rejects` edge from each former owner.
> 6. Delete the inline blocks.
> 7. Restamp the manifests with `pack-doctor remediate` (c). The service's own writer emits the canonical form.
>
> The charter's example packs show the target shape: 18 inline entries became 18 files of `built-in`, each with a
> node and a `rejects` edge (`service/packs/src/testFixtures/resources/example-packs/built-in/anti_patterns/`).
>
> **Also changes in open-packs.**
>
> - `scripts/ci/validate_pack_schemas.py` treats `anti_pattern` as graph-only. `MANIFEST_EXEMPT_NODE_KINDS` (`:84-88`)
>   and the `CONSTITUENT_KIND_BY_SCHEMA` note (`:445-455`) leave it out of the manifest, and `held_artifacts`
>   (`:1183`) holds it by its node. Retire these with (d).
> - The docs describe the old model:
>   - `docs/artefacts/index.md:36` ("Anti-pattern | inside other artefacts / the DRG | ... not a standalone file").
>   - `:68` and `:87` (`anti_patterns` among the styleguide and procedure fields).
>   - `docs/guides/validate-a-pack.md:117` ("Actions and anti-patterns have no file").
> - No preset activates an anti-pattern today (no `activated_anti_patterns` in `packs/` or `tests/`), so the preset
>   rule refuses nothing here (b).
>
> **Order.** Curate on a branch pinned to `service-v<version>`, so the repin (a) and the curation land in one pull
> request. Either way, `service-v<version>` refuses the current tree: no deprecation window is offered (the charter's
> canonical model).
>
> **Done when.**
>
> - open-packs pins `service-v<version>`.
> - No styleguide or procedure carries `anti_patterns`.
> - Every `anti_pattern` node has its file, and every anti-pattern has a `severity` and a `bad_example`.
> - `pack-doctor validate --full` runs `CLOSURE` and `REFERENCES` over the whole set and exits 0.
> - The checker and docs here match.
