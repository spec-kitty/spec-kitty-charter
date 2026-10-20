---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-20T03:35:33Z'
reviewer_agent: claude
wp_id: WP08
---

# WP08 review, cycle 1: changes requested (reviewer-renata)

The implementation is strong. Validate mode, the FULL scope, the open-packs acceptance, the plants,
the derived core agreement and ScopeParityTest all hold up. The review re-ran the gates and the
core regeneration, bit the derived agreement three ways, and probed symlinks. Every finding is in
the evidence section at the end.

The only required work is operator ruling **R18**, made after implementation (research.md R11 on
`feat/pack-schemas-and-level-policy`, commit 01a3291), plus two small follow-ons. Everything this
cycle needs is listed below, so cycle 2 should be the last.

Do not edit `docs/conformance/**`. The orchestrator registers DIV-020 to DIV-025 after approval.

---

## Required changes

### 1. R18: YAML `~`, `Null` and `NULL` are null, as in core

Today the reader uses snakeyaml-engine's default `JsonSchema`. Under it, a plain `~`, `Null` or
`NULL` is a **string**. Core (ruamel `YAML(typ="safe")`) reads all three as `None`. The reviewer
confirmed this with both parsers. Under the current code, `name: Null` in `pack.yaml` validates
with zero findings.

- **Where.** Fix it once, in the `LoadSettings` that `Yaml.settings()` returns. That object is
  shared by:
  - `Yaml.load` and `Yaml.loadAll`;
  - `ParsedYaml`, both the event-depth pass and the load;
  - the `YamlDocument` scanner;
  - `ScalarWriter.rereadsAsValue`.

  This makes every reader and the writer's re-read check agree. `Yaml.java` is outside WP08's
  owned files; R18 ("Aligned in WP08") authorizes this edit.
- **Scope.**
  - Only **plain** `~`, `Null` and `NULL` change, joining `null` and the empty value already
    handled.
  - Quoted `'~'`, `"Null"` and the like stay strings.
  - **Do not** switch wholesale to snakeyaml-engine's `CoreSchema`. It would also change
    `True`/`TRUE`, `0o17`/`0x1F`, `.inf`/`.nan` and `+12`, which R18 does not rule on. The
    reviewer found 0 uses of those forms in the corpus at 01edb4e. Leave them, and list them in
    your report as a remaining scalar difference for the operator.
  - A small custom `ScalarResolver`, or a `Schema` that delegates to the JSON resolver and adds
    the three null forms, is enough.
- **Tests, red first (C-007), with an assertion failure and not an exception:**
  - a reader-level parametrized test, in a `YamlTest` or the existing `ParsedYaml`/
    `GraphDocumentReaderTest` style:
    - `a: ~`, `a: Null`, `a: NULL`, `a: null` and `a:` all read as null;
    - `a: '~'` and `a: "Null"` read as strings;
    - `a: True`, `a: 0x1F` and `a: .inf` stay strings. This pins that the change is null-only.
  - a writer test (`YamlDocumentTest` or a `ScalarWriter` test): putting the value `~`, `Null`
    or `NULL` into a plain scalar writes it quoted, and it re-reads as that string. This falls
    out of `rereadsAsValue` once the settings change, but pin it.
- **Blast radius checked by the reviewer.**
  - The open-packs corpus at 01edb4e has 0 plain `~`/`Null`/`NULL` values. (The one hit,
    `name: Returning Null`, is a multi-word string.)
  - So do the repository's `*.yaml`/`*.yml`, `.kittify/**`, `contracts/**`, `*.feature` and
    the test resources, apart from the tilde plant itself.
  - `GraphDocumentReaderTest.theReaderNeverThrowsOnContent("~\n")` still holds: null is still
    not a mapping.
  - Re-run `OpenPacksRoundTripTest` and `ScopeParityTest` (the `openPacksRoundTrip` task) to
    confirm.

### 2. The tilde plant agrees with core; DIV-026 disappears

- **Rename** `planted/pack-yaml-tilde-is-the-string-tilde`, for example to
  `pack-yaml-tilde-is-null`.
- **Its `expected.json`:**
  - `covers: ["R18"]`. It says `R5` today, which is wrong.
  - The finding is what an explicit null `name` gives: `pack_unreadable`, blocking, `pack.yaml`,
    place `/name`, matching the R4 text "`pack.yaml of <name>: name must be a string`".
  - Add a `load` expectation (`refusal: pack_unreadable`), so the load path is pinned too.
- **`PlantedFilesTest`:**
  - add `"R18"` to the covered list in `everyChangeAndEveryRuleHasAPlantOrACitedProvingTest`;
  - remove `DIV-026` from `PROPOSED`, so that `PROPOSED` is exactly DIV-020 to DIV-025.
- **`scripts/packs/core_verdicts.py`:** remove the tilde entry from `REGISTER_IDS`.
- **Regenerate `core-verdicts.json`** with the script
  (`~/.venv-sk/bin/python -I scripts/packs/core_verdicts.py`), never by hand. The renamed plant's
  record must have no `register_id`, and the derived outcome must be `agree`. No record may cite
  DIV-026: `grep DIV-026` over the service, the scripts and the planted resources must return
  nothing.

### 3. Document the two load-path behavior changes in the contract CHANGELOG

`contracts/charter/CHANGELOG.md`, Unreleased, records every load-path behavior change of this
mission, such as the R4 explicit-null and R17 parse-code bullets. WP08 makes two more that reach the
wire through the refusal code or detail, and neither is recorded. Add two bullets beside the R4 and
R17 ones. This is an out-of-map edit of one file; the orchestrator should confirm it with the
cycle-2 dispatch.

- **(a) R18.** A plain `~`, `Null` or `NULL` in any pack file is now null. In `pack.yaml` it is
  therefore refused like an explicit `null`: `pack_unreadable`, "`pack.yaml of <name>: <key> must
  be a string`". Before, it was read as the string `~`/`Null`/`NULL`. Say "behavior change, no
  schema change", as the R4 bullet does.
- **(b) Non-UTF-8 bytes.** A pack file whose bytes are not valid UTF-8 is now a parse failure,
  coded by its kind (R17):
  - a shard or the fragment: `drg_schema_error`, "`<pack>/<file> is not valid YAML: the file is
    not valid UTF-8`";
  - `pack.yaml`: `pack_unreadable`, "`<pack>/pack.yaml is not valid YAML`".

  Before WP08, all of these were `pack_unreadable`, "`<pack>/<file> cannot be read`" (the WP07
  test `aFileThatCannotBeReadIsAnIoFailureAndRefusesNamingItRelativeToThePack`, which WP08
  replaced). An I/O failure keeps "cannot be read".

**Pin (b) at the load path.** Today it is pinned only at fact level
(`FileSystemPackInspectorTest.aFileThatIsNotUtf8IsNotYamlAndTheCodeFollowsItsKind`). Add a
repository-level assertion of the refusal code and the exact detail for a non-UTF-8 root shard.
Either one works:
- a `FileSystemCharterPackRepositoryTest` case;
- a load-path plant with a `load` expectation, which `PlantedFilesTest` and `ScopeParityTest`
  then also exercise.

### 4. The evidence in your report

- **T041 pin gate: re-run it and record the result.** It now **passes**. At 2026-10-20,
  `git -C /home/user/spec-kitty-open-packs fetch origin main` and
  `git merge-base --is-ancestor 01edb4ebab308249a10ed4a4a01d77741ac1d14b origin/main` both exit
  0. 01edb4e sits on main's first-parent line (origin/main = 51149ee), so #52 has landed and the
  pin is a main commit. Drop "BLOCKED on open-packs#52". Do not move `openPacksRef` in this WP:
  T041 makes that the orchestrator's step.
- **Re-run both gates and give their exit codes:**
  - `./gradlew :charter-domain:test :adapter-out-yaml:test --rerun :charter-domain:check :charter-read-app:check :adapter-in-rest:check :adapter-out-yaml:check :charter-service:test -PopenPacksDir=/home/user/spec-kitty-open-packs`;
  - `./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs -PcontractBaseline=none`;
  - both with `-Porg.gradle.java.installations.paths=/opt/jdk-25 --max-workers=2`.
  - Also run `:adapter-out-yaml:openPacksRoundTrip --rerun` so the corpus tests actually execute
    rather than being up to date, and paste the NFR-002 line.
- **Correct the load-fixture count.** ScopeParityTest runs 61 load-fixture cases (123 cases = 58
  plants + 61 fixtures + 4 bites), not 55.

---

## Optional (non-blocking; do them only if they cost minutes)

- **`OpenPacksAcceptanceTest.theTimingConstantsAreTheMeasuredOnes`** asserts constants against
  literals, which is tautological. Delete it, or leave it.
- **Unclassified files in a user pack.** The FULL scope drops a file no rule covers (for example
  `notes/x.yaml`) from `PackVerdict.files()` without a word. That matches core, which also
  ignores such files, and the corpus guard catches drift. But FR-010 says "the classification of
  every YAML file". One sentence on `PackVerdict.files()`/`PackValidation` saying that
  unclassified files are omitted would make it explicit for #115. No domain change is asked
  (`ClassifiedFile` cannot express "unclassified" today).

---

## Evidence (what the reviewer checked)

**Checklist:**

| Check | Result |
|---|---|
| Dead code | PASS. `PackValidation` is wired as a bean in `CharterWiring` (P12; read-app belongs to runner A, so it is out of map and justified) and tested by `CharterWiringTest`. |
| Synthetic fixtures | PASS. Every test goes through `PackValidation` and the real inspector. |
| Silent empty return | PASS. Unclassified files are dropped (see Optional); `Absent` arms are no-ops by design. |
| FR coverage | PASS. FR-010 (`ValidateModeTest`), FR-011 (acceptance), FR-012 (plants). |
| Frozen surface | PASS. |
| Locked decision | PASS. |
| Shared files | PASS. The out-of-map edits are `PackContents`, `PackPolicy`, three `PackPolicy*` tests, `CharterWiring` and its test; each is minimal and necessary. |
| Production fragility | PASS. The only throw is an I/O `CharterRefusal`. |

**Implementation:**
- **The ninth component, `unreadableFiles`.** It is necessary: the domain owns codes, so the
  adapter cannot turn a non-load-path parse or schema failure into a finding. It is minimal, and
  `judge` sorts by `PRECEDENCE`, so order is unaffected. LOAD_PATH passes an empty list.
  `ScopeParityTest` asserts that `judge(LOAD_PATH)` equals FULL restricted to the load path for
  every plant, fixture and corpus pack.
- **Agent profiles supply no artifact facts.** Confirmed in core 9554e7b2:
  `pack_validator.py:350` reads `data.get("id")`, and only the schema-success projection reads
  `profile_id`.
- **Asset containment.** It matches core's `resolve_relative_path_within_root`: a string-level
  `..` or an absolute path escapes, as does a symlink-resolved escape or a loop. The reviewer's
  own probe:
  - escapes: an out-of-pack directory link, a dangling link out, an `l1`/`l2` loop,
    `inlink/../../pack.yaml`, `missing/../in/i.png`;
  - contained: a relative link inside, and `up/assets/in/i.png` where `up` points to
    `in/../..`.

  All match core's rules.
- **NUL in a path gives `asset_path_escape`, where core crashes:** acceptable.
- **The probe over every mapped kind** (directive, tactic, styleguide, toolguide, paradigm,
  procedure, agent, step-contract, asset, org-charter, charter, `drg/*.graph.yaml`): each broken
  file yields blocking findings of its kind's code.
- **NFR-002.** The guard is absolute (load median at most 67 + 2000 ms), with backstops at twice
  the budgets. With that headroom it is not flaky on CI, and `service.yml` runs it via
  `verifyAll`. The reviewer's run: validate cold 2218 ms, warm median 291 ms; load median 65 ms.

**Core verdicts:**
- Regenerating with `scripts/packs/core_verdicts.py` (`python -I`, run in a scratch copy) gave a
  file **byte-identical** to the committed one: 62 records, core 9554e7b2, spec-kitty-cli 4.0.0rc8.
- **Bite checks**, against the committed file temporarily edited and then restored:
  - dropping DIV-013 from c2-agent-nested-unknown-key failed with "differs … no register id";
  - adding a register id to duplicate-step-id failed as "stale";
  - turning core's duplicate-artifact-id error into an advisory failed as "differs".

  All three bite. The severity mapping (error is blocking, advisory is a warning) is core's own
  and the plan's.

**Corpus and the repository's `charter.yaml`:**
- The pin 01edb4e is clean. 516 = 409 + 107 with 0 unclassified, and the per-class tables
  assert it. The two non-vacuity probes and the rule-removed probe are in the suite and pass.
- **The repository's `.kittify/charter/charter.yaml` fails `charter.schema.json`** with
  `additionalProperties`: `activated_glossary_packs` was unexpected. Confirmed with jsonschema.
  Core writes that key (`charter/activation/pack_context.py`) but its exported model lacks it.
  This is a follow-up issue, not a WP08 change; no WP08 test asserts on it.

**Gates:** both exit 0. Tests: ScopeParityTest 123/123, PlantedFilesTest 122/122,
ValidateModeTest 5/5, FileSystemPackInspectorTest 22/22, open-packs 530/530.
