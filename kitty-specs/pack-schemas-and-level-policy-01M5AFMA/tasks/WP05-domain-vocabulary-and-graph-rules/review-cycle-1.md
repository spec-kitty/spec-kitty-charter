---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T19:13:59Z'
reviewer_agent: claude
wp_id: WP05
---

# WP05 review — cycle 1 — CHANGES REQUESTED

Reviewer: reviewer-renata (opus). Reviewed commit: `9aedefb` only.
Gate at lane HEAD `8f5f11c`: `./gradlew :charter-domain:check :charter-read-app:check :adapter-in-rest:check :adapter-out-yaml:check :charter-service:test -PopenPacksDir=/home/user/spec-kitty-open-packs` exited 0. `:charter-domain:test --rerun` also exited 0. ArchUnit (`HexagonalArchitectureTest`) is green, and the domain has no networknt or Jackson import.
Red evidence (`wp05-red.txt`): 67 of 125 tests failed on assertions against compiling stubs. That satisfies C-007.

Most of the work is solid: the graph rules, the `in_tension_with` → `impacts` -1 mapping, NaN and ±Infinity via `Double.toString`, the R14 plurals, the comparator, dedup and the locale-free fallback. Four required changes remain. They are listed below with exact instructions, and nothing else is pending, so cycle 2 can be the last one.

The facts in issues 2 and 3 come from running networknt 3.0.8 (the jar in the Gradle cache) on the **bundled** `graph-shard`, `org-fragment` and `pack-descriptor` schemas. The probe output is reproduced below so you do not have to guess.

```
pattern   loc=/nodes/0/urn      args=[^tactic:]                                 (per-kind if/then)
pattern   loc=/edges/0/source   args=[^(directive|tactic|...):[A-Za-z0-9_/.-]+$]
type      loc=/nodes/0/label    args=[integer, string]   \  nullable anyOf[string,null]:
type      loc=/nodes/0/label    args=[integer, null]     /  NO "anyOf" error is emitted
type      loc=/requires_core    args=[integer, string]   \  same on pack.yaml
type      loc=/requires_core    args=[integer, null]     /
type      loc=(root)            args=[array, object]
minItems  loc=/nodes            args=[2, 1]              ({0}=min, {1}=found)
minLength loc=/nodes/0/id       args=[1]   + pattern loc=/nodes/0/id args=[^[A-Za-z0-9_/.-]+$] on ""
enum      loc=/nodes/1/kind     args=[[...]]             (no sibling id/urn in the args)
required  loc=(object)  prop=<missing key>    additionalProperties loc=(object) prop=<key>
```

The message templates in `jsv-messages.properties` confirm the order: `type = {0} found, {1} expected`; `pattern = ... {0}`; `minItems = at least {0} ... found {1}`.

---

## Issue 1 (BLOCKING, ruling R17): the parse-failure code must follow the file kind

R17 (research.md R11, contract-deltas D8): a graph shard that is not valid YAML is refused with `drg_schema_error`, not `pack_unreadable`. The text does not change. Today, `PackPolicy.unreadable` hard-codes `PackFindingCode.PACK_UNREADABLE` for every parse failure, and also for the "unreadable with neither a parse failure nor violations" branch.

Make these changes:
1. In `PackPolicy.unreadable(...)`, choose the code with the existing `codeFor(file)` in **both** the parse-failure branch and the "is unreadable" branch:
   - `pack.yaml` → `PACK_UNREADABLE`;
   - `*.graph.yaml` and `drg/fragment.yaml` → `DRG_SCHEMA_ERROR`;
   - `org-charter.yaml` → `ORG_CHARTER_SCHEMA_ERROR`;
   - any other file → `ARTIFACT_SCHEMA_ERROR`. No artifact parse code existed before, because artifacts were never parsed on the load path, so this is consistent with R11.

   Keep both texts exactly as they are: `<o> is not valid YAML: <first line>`, `pack.yaml of <n> is not valid YAML` and `<o> is unreadable`.
2. Rewrite the Javadoc sentence "A parse failure is `pack_unreadable` for every file". It should say that the code follows the file kind, like every other finding (R17).
3. Tests, all in WP05-owned files:
   - In `PackPolicyVocabularyTest.aParseFailureNamesTheOriginAndTheParsersFirstLine`, expect `DRG_SCHEMA_ERROR` for the shard.
   - Add a fragment parse failure (`drg/fragment.yaml` → `DRG_SCHEMA_ERROR`, text `qa/drg/fragment.yaml is not valid YAML: <line>`).
   - Keep `aDescriptorParseFailureHasNoParserLine` on `PACK_UNREADABLE`.
   - Add one artifact parse failure → `ARTIFACT_SCHEMA_ERROR`, for example `tactics/x.tactic.yaml`.
   - Add the shard and fragment cases of the "neither parse nor violations" branch → `DRG_SCHEMA_ERROR`.
   - In `PackPolicyTest`, add one `load(contents)` test with an `Unreadable` shard that carries a `ParseFailure`. It must refuse with `RefusalCode.DRG_SCHEMA_ERROR` and the message `qa/a.graph.yaml is not valid YAML: <line>`. This proves the wire projection.
   - Each new or flipped assertion must be seen red against the current code first.
4. **Gate safety: do NOT touch the adapter.** Today `FileSystemCharterPackRepository.loadPack` never builds a `FileRead.Unreadable` for a shard. `GraphDocumentReader.read` throws `CharterRefusal(PACK_UNREADABLE, ...)` itself, before `PackPolicy` runs, and the adapter passes `load(contents, graph)` empty `RawGraph`s. So `FileSystemCharterPackRepositoryTest.aMalformedShardIsRefusedNaming...` (which pins `PACK_UNREADABLE`) stays green after this change, and must stay as it is in WP05. Flipping that pin, the scenario and the contract CHANGELOG line (D8) belongs to **WP07**, when the adapter routes shard parse failures through `PackPolicy`. Do not edit any file outside WP05's `owned_files`.

## Issue 2 (BLOCKING): networknt's positional argument for `pattern` is `"0"`, not `"1"`

`patternDetail` reads `arg(v, "pattern", "1")`. networknt 3.0.8 puts the regex at index **0**, and WP04's `PackSchemas.violation` fills positional keys only. In production, then, the per-kind `if/then` violation (`pattern ^tactic:` on `/nodes/i/urn`) would never yield `<o>: node <urn> declares kind '<kind>'`. It would fall through to `node urn '<v>' is not <kind>:<id>`, which is the wrong text for that row of the plan table.

The other two positional guesses are correct: `type` → `"1"` is the expected type, and `minItems` → `"0"` is the minimum.

Required:
- **One key per fact.** Read the facts networknt produces from their **verified positional index only**: `type` expected → `"1"`, `pattern` regex → `"0"` and `minItems` minimum → `"0"`. Drop the named aliases `expected`, `pattern` and `min`. The dual lookup is what hid this bug: the tests passed on the named key and never exercised the index production uses.
- Keep named keys **only** for the facts the adapter must enrich, which networknt does not emit: `kind`, the sibling plural kind of a fragment node (for `minLength` and `pattern` on its `id`), and `id`/`urn`, the sibling of a node (for `enum` on its `kind`).
- In the `schemaFindings` Javadoc, document exactly this contract: which positional indices are read, and which named keys WP07 must add.
- Rewrite the vocabulary tests to the real networknt fact shape:
  - `type` → `Map.of("0", <found>, "1", <expected>)`;
  - `pattern` → `Map.of("0", <regex>)`;
  - `minItems` → `Map.of("0", "1", "1", "0")`.

  Include the per-kind case `Map.of("0", "^tactic:")` → `qa/tactic.graph.yaml: node directive:a declares kind 'tactic'`.

## Issue 3 (BLOCKING): the nullable `anyOf` collapse never fires on real networknt output

`collapseNullable` only acts when an `anyOf` violation is present. On the bundled schemas, networknt 3.0.8 emits **no** `anyOf` error for a failing `anyOf[{type:T},{type:null}]`. It emits just the two branch `type` errors at the same pointer (see the probe: `label`, `reason`, `requires_core`).

With the current code, `label: 7` produces two findings: `'label' must be a string`, plus the fallback `'/nodes/0/label' breaks schema keyword 'type'` for the null branch. The plan's collapse rule is therefore not met for every nullable field: `label`, `provenance`, `when`, `reason`, `parent_pack`, `requires_core`, `active_charter` and `pack_drg`.

Required:
- Collapse by facts that actually occur. At one `(file, pointer)`, when there are two or more `type` violations and at least one of them expects something other than `null` (positional `"1"`), drop the ones that expect `null`. Also drop any `anyOf` violation at that same pointer if one is present (defensive). A lone `type` violation that expects `null` is kept, and therefore gets the fallback.
- Update the Javadoc rule to match.
- Tests:
  1. the real shape, with no `anyOf`: `type {"0":"integer","1":"string"}` plus `type {"0":"integer","1":"null"}` on `/nodes/0/label` → exactly `qa/tactic.graph.yaml: 'label' must be a string`;
  2. the descriptor variant on `/requires_core` → exactly `pack.yaml of qa: requires_core must be a string`;
  3. keep the variant that includes an `anyOf` violation;
  4. keep `anAnyOfWithoutNestedTypeViolationsUsesTheFallback`.

## Issue 4 (BLOCKING, small): no invented facts — use the fixed fallback when a fact is missing

Several paths invent a value when a fact is absent, and produce a text that looks like a pinned text but is wrong:
- `kindArg` → `'unknown'`, giving "a node of kind 'unknown' has an empty id";
- `enumDetail` → `node unknown has unknown kind ...`;
- `typeDetail` → `inferredType(key)`, which guesses `object`, `array`, `number` or `string` from the key name.

This is the "silent default" anti-pattern. It would also mask a WP07 adapter that forgets to fill `kind` or `id`/`urn`.

Required:
- When a needed fact is absent, return `fallback(o, v)`, the fixed locale-free text:
  - `type` without positional `"1"`;
  - `minLength` or `pattern` on a fragment node `id` without `kind`;
  - `enum` on a node `kind` without `id`/`urn`.
- Delete `inferredType`.
- Update the tests that currently build `type` violations without args (`at(file, "type")`, for example in `typeObjectAtTheRootExpectsAMapping`, `typeObjectAtANodeOrEdgeItemExpectsAMapping`, `aDescriptorSaysItIsNotAMapping...`, `theCodeFollowsTheFileKind` and `atOnePlaceTypeComesFirst...`) so they carry the networknt shape, for example `Map.of("0","array","1","object")`.
- Add one test per missing-fact case, asserting the fallback text.

---

## Judgement calls: verdicts

1. Origin `<packName>/<file>`, and descriptor `pack.yaml of <n>`: **accepted**. Both match `FileSystemCharterPackRepository` (`location.name() + "/" + relativized path`) and `PackDescriptorReader`.
2. Parse-failure code: **superseded by R17** (Issue 1).
3. Named-then-positional args: **rejected** (Issues 2 and 4). `type` "1" and `minItems` "0" are right, and `pattern` "1" is wrong. Use positional keys for networknt facts and named keys only for the adapter-enriched `kind` and `id`/`urn`.
4. `<kind>:<id>` kept literally in the `-safe` text: **accepted**. It mirrors today's literal `is not <kind>:<id>` and the plan table.
5. Required and unknown-key findings at the object's pointer: **accepted**. networknt reports `required` and `additionalProperties` at the object, with `property` set to the key (probe).
6. Descriptor key order follows the schema `properties` order: **accepted**. No pinned test or scenario combines two descriptor defects, and `PackDescriptorReaderTest` pins single defects only. Be aware of one consequence for WP07 (non-blocking): a root `required` finding (empty pointer) now precedes a `/pack_level` enum or type finding, while today's reader checked `pack_level` first.
7. The `IllegalStateException` for an `Unreadable` descriptor in `loadWith`: **accepted**. It is unreachable by construction, because `unreadable()` always yields at least one blocking `pack.yaml` finding and neither the collapse nor dedup can empty it. It is a documented fail-loud invariant guard. After the Issue 3 change it must stay unreachable: the collapse must keep at least one finding.
8. The XOR of `Unreadable` parse and violations not enforced: **accepted to defer**. `PackContents` is not a WP05-owned file, and `PackPolicy` already gives the parse failure precedence. Hand it to WP07 or WP01's owner.

## Checked and fine (no action)

- `NodeKind.fromPlural`: maps the 13 `ArtifactKind` plurals plus `mission_types` and `actions`. It refuses `mission_steps`, `mission_type`, `anti_pattern` and `""`, and the Javadoc cites R14.
- `in_tension_with` → `impacts` -1; with `impacts` present it gives "an in_tension_with edge has no 'impacts'". The edge fault order is relation, then strength, then endpoints, as in today's reader. NaN and ±Infinity are formatted by `Double.toString`. An edge with a finding is not added, so `DrgEdge`'s guard never fires.
- Comparator: group, then file, then pointer segments, with a stable sort that keeps the in-place keyword rank (type, then additionalProperties, then required, then the rest). Indices compare numerically, `nodes` comes before `edges`, unknown keys sort last and lexically, and the key tables match the bundled schemas' `properties` order. Matches the plan.
- Identical findings are deduplicated (`LinkedHashSet` of records). The `minLength` and `pattern` pair on an empty id gives one text.
- The fallback is locale-free (tested under `tr-TR`). Every keyword in the bundled schemas has a row or the fallback (`minimum`, `maximum` and `exclusiveMinimum` included).
- The PackFileKind/schema vocabulary equality test (including the single allowed `in_tension_with` difference: the schema's relation enum has it, and `Relation` does not) was moved to **WP07** by the WP prompt. It is not expected here.

## Anti-pattern checklist

1. Dead code: PASS. `load(PackContents)` has no production caller yet, by design: WP07 switches the adapter. `fromPlural`, `graph`, `unreadable` and `schemaFindings` are reached via `judge` → `load(contents, graph)`.
2. Synthetic fixture: FAIL. The `type`/`pattern` facts in the vocabulary tests do not have networknt's shape (Issues 2–4).
3. Silent empty return or default: FAIL. `'unknown'` and `inferredType` (Issue 4).
4. FR coverage: FAIL for FR-008 under R17 (Issue 1). FR-003 literals: PASS.
5. Frozen surface: PASS.
6. Locked decision: FAIL (R17).
7. Shared-file ownership: `PackPolicy.java` and `PackPolicyTest.java` are shared with WP01 (and WP06 will touch `PackPolicy`). They are coordinated via the lane, with no conflict.
8. Production fragility: PASS. The single new `IllegalStateException` is a documented invariant guard.

## Scope reminder

Touch only WP05 `owned_files`. Commit with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Re-run the gate command above; it must exit 0.
