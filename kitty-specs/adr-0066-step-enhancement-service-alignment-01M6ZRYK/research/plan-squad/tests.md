# Plan squad: test adequacy, failure modes and gates

- **Lens:** test adequacy, failure modes and gates.
- **Profile:** `debugger-debbie`, squad-delegate mode. The profile was read from the YAML as a degraded fallback,
  without the CLI, so overlays were not applied. Applied: DIRECTIVE_030 (test and typecheck gate), the falsifier and
  matrix-maker habits (look for dormant masks and enumerate failure cells), and review-intent-and-risk-first.
- **Scope:** the plan-point artifacts of `kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/` at
  `92026b5d`. Read-only.
- **Charter rules applied:** test first; no fake green; BDD scenarios first; the whole-pack round trip as a gate; the
  operator's rulings D1–D22 (D19/D20 is the approved set of expectation edits).

Each finding follows the procedure's form: `[SEVERITY] file:line - issue - recommendation`, then whether it needs the
operator. Dispositions are left to the orchestrator under the findings-disposition contract.

---

## BLOCKER

### T-01 [BLOCKER] plan.md:122, :254, :472 - The plan's assertion edits go beyond the approved D19 set

The plan says "Expectation edits are the approved set D19" (plan.md:122). D19 approves only these: the
`KindBindingsTest:198-210` flip, the `ReservedRefusalCodesTest` list, the refusal-description wording, and the
fragments (research.md:90-98). The plan also forces these assertion edits, which nobody has approved:

1. **`KindBindingsTest.java:64`.** `assertThat(kinds).hasSize(14)` turns red when the binding is appended (15). The
   display name and comment at `:83-90` ("the guard and the lifecycle are appended last") become false, even though
   `subList(12, 14)` still passes.
2. **`FileSystemPackInspectorTest.java:384-400`.** The test
   `anEnhancementAndItsPromptFragmentAreLoadedAndValidatedWithoutAFailureAndHeldByNoStore` asserts "no store holds the
   enhancement yaml or its fragment yet (#424)" (`noneMatch(file -> file.startsWith(ENHANCEMENT_DIR))`). This is the
   exact behaviour IC-01 inverts.
3. **`FileSystemPackInspectorTest.java:402-416`.** The test
   `aPromptFragmentThatBreaksItsStructureIsUnreadableInValidateAndNotAFailureOnLoad` asserts two things:
   - The frontmatter fragment and the blank fragment are `unreadableFiles` in validate mode. FR-004 and US3 AS1 instead
     want `prompt_fragment_invalid`.
   - Neither is a failure on load. D11 now refuses `blank.prompt-fragment.md` at load as an orphan, and the plan has
     `checkCompanion` refuse frontmatter.
4. **`ActivationControllerTest.java:58`** (IC-07). `operations()).hasSize(14)`, the `BUILT` list and the method name
   `everyOneOfTheFourteenOperations…` all change with `getEffectiveStepPrompt`.

**Fix:**
- List all four in the plan's gate map with their before and after assertions.
- Bring them to the operator as an addendum to D19 before IC-01 starts.
- For item 3, decide in the plan which code validate mode answers for a structure-breaking fragment: unreadable/schema,
  or `prompt_fragment_invalid`. Pin that choice with a twin test.

**Needs the operator:** yes (charter: escalation class for test-expectation edits).

---

## MAJOR

### T-02 [MAJOR] plan.md:255, :477 - `PackFileSetExactnessTest` does not stay green just because the binding is appended

- `PackFileSetExactnessTest.boundKinds()` returns `KindBindings.kinds()` (:142-144).
- The parameterized test then calls `Kind.valueOf(omitted.name())` (:137) on `GeneratedPackTree.Kind`, which has no
  `STEP_ENHANCEMENT` (`GeneratedPackTree.java:105-120`). So it throws at IC-01.
- `plantFile` (`GeneratedPackTree.java:520-531`) only knows the placed paths of the mission type, guard and lifecycle.
- So the exhaustive oracle, which is the only test that proves every loaded file is digested, will not cover the step
  enhancement or its fragment companion unless the generator learns to plant them:
  - `missions/<t>/enhancements/<id>.step-enhancement.yaml`;
  - its fragment companion;
  - its `enhances` edge, because the load rules refuse it without one;
  - an invalid twin.

**Fix:**
- Add `STEP_ENHANCEMENT` to `GeneratedPackTree.Kind` with a placed plant. This is a scaffold extension, not an
  assertion edit.
- Name it in IC-01's affected surfaces.
- Correct the gate-map row: "appended" protects the digest order, not test coverage.

**Needs the operator:** no.

### T-03 [MAJOR] plan.md:257; data-model.md:38-39 - The fragment round trip is not proven "by meaning", and the tests can pass vacuously

1. **The comparison does not look at fragment bodies.** The round-trip comparators `WholePackStoreTest.meaning`
   (:121-140) and `WholePackOpenPacksRoundTripTest.meaning` (:84-100) compare `binding.dsoOf(artifact)` per kind. If
   `StepEnhancementDso` carries `{slot, path}` but not the fragment body, a write that empties or drops a fragment body
   still compares equal.
   **Fix:** the meaning must include each companion body. Add a mutant test with the same fixture, where the written
   fragment is altered or dropped, and show that the round-trip assertion then fails (tactic
   `acceptance-criteria-non-vacuity`).
2. **The data model and the codec disagree on line endings.** data-model.md:39 says bodies are compared "as text with
   LF line endings", which implies normalisation. But `MarkdownCodec.read` refuses any `\r` and a BOM
   (`MarkdownCodec.java:139-145`). The plan pins neither:
   - which refusal code a CRLF or BOM fragment gets: `prompt_fragment_invalid`, or the companion-violation /
     `artifact_schema_error` path;
   - which runs first, PromptSlots on the raw text or the codec.
   **Fix:** add a twin per case (CRLF, BOM, no trailing newline, two trailing blank lines, a non-ASCII and a non-BMP
   character, an H1 versus the H2 sections the structure schema admits). Each states the code and whether the written
   bytes are canonical.
3. **The served fragment text is unspecified.** `EffectivePromptFragment.text` is either the authored bytes or the
   canonical render. The plan rules this for prompts (D5) but not for fragments.
   **Fix:** state the reading and pin it in the IC-07 unit test.

**Needs the operator:** no, unless the reviewer wants raw bytes, which D5 rejects for prompts.

### T-04 [MAJOR] plan.md:93-94, :477; spec.md:204 - NFR-001 ("0 digest changes") has no stated baseline, so an after-only check is vacuous

- The digest is a file list with no per-kind header (`PackFileSet.java:266-305`; `FileSystemPackSources.addPack`).
  Stability for packs without enhancements therefore holds by construction.
- The plan never says what its "0 digest changes" assertion compares against. A test that recomputes the production
  digest compares it with itself.

**Fix:** either one works.
- **(a)** Capture the `digestOfPack` values of built-in, `qa` and `internal` at `openPacksRef=22c14b2d` on `main`
  before IC-01, and pin them as constants in an `open-packs`-tagged test. `PackFileSetTest:100-109` does this with a
  value captured before payloads were digested.
- **(b)** Use the existing seam `digested(packDir, Set<ArtifactKind>)` (code-grounding §2). Assert `kinds()` equals
  `kinds() minus STEP_ENHANCEMENT` on the three pinned packs.

Either way, add a positive control: the same check on a fixture pack with one enhancement must show a different digest.
Also add a test that editing only a fragment's bytes changes the fingerprint and the ETag of the new read. Otherwise a
binding whose `companions()` misses the fragment would serve stale prompts behind `304`.

**Needs the operator:** no.

### T-05 [MAJOR] plan.md:190-192, :288-289, :298-302 - The slot parser has no edge-case table, and "agree by construction" is not proven

- The load check parses the raw companion text. The read parses the canonical render, which trims and re-spaces
  blocks (`MarkdownCodec` javadoc :30-42, `trimmed()` :218-224).
- The two inputs differ, so agreement is a property to test, not a construction.
- Only one edge case is planned: a marker in a fence, and only for fragments (plan.md:288).

**Fix:**
1. **A parameterized "refused / accepted neighbour" table** for `PromptSlots`, in the style of
   `MarkdownCodecTest.refusalsWithNeighbours`, with at least these rows:
   - spacing: `<!--a:b:start-->` (no spaces), `<!--   a:b:start   -->`;
   - case and id: `<!-- A:b:start -->` (a candidate with a bad id, so malformed);
   - prose that ends like a marker: `<!-- note: see x:start -->` (candidate or not?);
   - a comment spanning lines: `<!--\na:b:start\n-->`;
   - two markers on one line (an inline empty slot);
   - a marker inside an HTML block;
   - a marker inside inline code and inside a fenced block, in a **base prompt** as well as in a fragment;
   - an end with no start, and an end before its start;
   - `<!-- decision_id: x -->` (not a candidate);
   - text in a slot that is only whitespace: NBSP U+00A0, zero-width space U+200B, a tab, CRLF. State which of these
     count as whitespace.
2. **A property test** over the five pinned built-in prompts and every fixture prompt:
   `PromptSlots(raw)` equals `PromptSlots(render(parse(raw)))` in ids, order and emptiness. Include a mutant that
   breaks the agreement.

**Needs the operator:** no. Whether a marker inside a fenced block counts in a base prompt is a ruling candidate if the
reviewer disagrees with "any marker counts".

### T-06 [MAJOR] contracts/effective-step-prompt.openapi.yaml (`getEffectiveStepPrompt` description; `prompt` description); plan.md:418-422 - The read of a non-empty legacy slot is unspecified and untested

- The pinned built-in pack, the real corpus, keeps text in its five `spdd:reasons-block` slots and loads with a
  warning (NFR-002).
- The contract tells renderers to "replace each slot's marker pair with its fragments". On a legacy slot that would
  delete the base text between the markers, which contradicts ADR-0066 d7 ("never replaces base text").
- No IC-07 test or scenario reads a step whose slot holds legacy text.

**Fix:**
- Rule what the read and the renderer contract say for a non-empty slot. Options include: the slot is listed and the
  text stays; or the slot is flagged.
- Add a test and a scenario on an unversioned fixture prompt whose slot holds text and has one active fragment.

**Needs the operator:** yes. This is a contract-meaning question that D5 does not settle.

### T-07 [MAJOR] plan.md:487-493; contracts/README.md:384-386 - N1 default (a) describes a shape the code does not produce

- `WholePackStore.loadRefusingAsInvalid` (:472-481) turns a thrown load refusal into **one** violation with
  `pointer: "pack"`. The file appears only in the detail.
- Only non-throwing `pack().violations()` and unmodelled files become per-file violations (`loadStaged` :239-249).
- The plan has the IC-02 and IC-03 rules raise through `PackFindingCode.refusal()` in `WholePackStore.load`, which is
  a throw. That gives neither "every fault at once" nor `pointer: <file>`.

**Fix:**
- Correct N1 for the operator.
- Choose one mechanism:
  - per-file `Pack` violations, so the registration lists every fault; or
  - a fail-fast throw, so the registration has one violation with pointer `pack`.
- Pin the choice in a registration scenario with `Then the field at "/violations/0/pointer" is "…"` and
  `holds exactly <n> item(s)`, using a multi-fault fixture.

**Needs the operator:** yes. It feeds the open item N1.

### T-08 [MAJOR] plan.md:285-287; spec.md:161-162; research.md:82-88 - The plan does not settle which refusal wins when several rules fail

The plan pins only one precedence: the D8 stem rule runs before `DUPLICATE_ARTIFACT_ID`. These are open:

1. **D12 has two shapes with two codes.**
   - `prompt_fragments: []` is already a schema `minItems` violation (`PromptFragmentContractsTest:111-119`), so it
     answers the schema path, not `prompt_fragment_invalid`.
   - An absent `prompt_fragments` key is schema-valid (`PromptFragmentContractsTest:53-55`, "an enhancement without
     fragments stays valid"), so it needs the Java rule.
   - FR-006 and the spec edge case promise `prompt_fragment_invalid` for "adds nothing".
   - **Fix:** test both shapes, and accept and document the schema code for `[]`. Changing the schema would be a
     contract change.
2. **A schema violation and a semantic fault in the same file.** Say which is reported, and whether the semantic rules
   run at all.
3. **A prompt that both breaks the slot grammar and is refused by `MarkdownCodec`**, for example an unclosed fence
   that holds a marker, or CRLF. The slot check runs on raw text first (plan.md:308), so pin the resulting code.
4. **Read-path determinism.** When a configured pack has both `prompt_slot_malformed` and `prompt_fragment_invalid`,
   the read's single `500` code must be deterministic. Define the order (for example `PackFindingCode` order, then
   path) and test it.
5. **Companion rules.** Add these twins: two enhancements naming one companion; one enhancement naming the same path
   in two entries; a companion that is named but missing; and an orphan beside a valid pair. Also say whether the
   generic companion rule (`Companions.resolve`) fires first with a different code.

**Needs the operator:** no, apart from item 1's wording if they want `prompt_fragment_invalid` for `[]`.

### T-09 [MAJOR] plan.md:84-86, :430-431; spec.md:206 - The NFR-003 permutation test can pass vacuously, or fail on the fingerprint

1. **The fingerprint may depend on configured order.** `FileSystemPackSources.fingerprintOf` digests packs in
   configured-location order (:40-51). `ClosureQueries.closureFingerprint` adds ids in closure order (:112-118). The
   body includes `governanceFingerprint` and the ETag derives from it. Unless both are permutation-invariant, the test
   either fails or is written "apart from" the fingerprint, which is fake green.
   **Fix:** the test compares the whole body and the ETag. If it fails, that is a real defect to fix, not to exclude.
2. **The test proves nothing if the fixture's orders coincide.** Within a level, the merge order is already pack id by
   code point (scenarios README:210). If the fixture's names equal its ids and its enhancement ids follow file order, an
   implementation with no comparator passes.
   **Fix:** build the fixture so that:
   - a dependency forces the merge order away from name order (composition.feature:201);
   - the configured name, the descriptor name and the `pack_id` sort differently, which pins the meaning of "pack
     name";
   - enhancement ids are authored in reverse order;
   - names exercise `-`, `_` and digits.

**Needs the operator:** no. "Pack name" against pack id is a spec-wording clarification for the reviewer.

### T-10 [MAJOR] plan.md:87-89, :432-434; spec.md:207 - The NFR-004 operation-count gate is under-specified

1. **The counts are not tied to existing hooks.** `EffectiveReadOperationBudgetTest` counts through `cache.loads()`,
   `InForceResolution.resolutions()` and `walkVisits()`, plus the digest counters. The plan names no counter for
   "enhancement visits" or "slot parses". Each needs a production hook, and adding one is a change to
   `EffectivePromptQuery`.
2. **One size cannot show linearity.** "At most linear" is not proven at a single N. The plan also contradicts itself:
   one pass "over the enhancements of the target step" (:89) against "visits ≤ the number of enhancements of the
   closure" (:433).
3. **The per-slot sort is O(k log k).**
4. **Digest counts are missing.** The plan omits the digest counts that the effective-read gate asserts (3 digests).

**Fix:**
- Name the hooks.
- Assert exact counts derived from the fixture at two sizes, for example 1,000 and 2,000, with visits growing by at
  most the delta.
- Count comparisons, or exclude the sort explicitly with a rationale.
- Assert one compose, one resolution, one render and one parse, and the digest counts, cold and warm.
- Build the 2,000 enhancements as valid ones: a companion each, an edge each, and a declared empty slot. Otherwise the
  IC-02 and IC-06 rules refuse the fixture.

**Needs the operator:** no.

### T-11 [MAJOR] plan.md:244-245, :356-358 - The planned BDD coverage has gaps

- **The new operation.** The plan names US4 AS1–AS4, the slot-less prompt and US5. It does not name these, all of
  which `effective-reads.feature` covers for its reads (:2061-2287):
  - `304` on a matching `If-None-Match`;
  - a new ETag after a change to a fragment of the closure;
  - the tag kept after a change to a pack outside the closure;
  - `500 internal_error` (`Given the service fails unexpectedly while it reads the packs`);
  - `500 stale_input` (`Given a pack file changes while the next read is answered`);
  - `400` for an id that breaks the pattern.

  `401` and `403` are covered generically by `security.feature`: no finding there.
- **IC-01's red selection scenario has no positive control.** In the same fixture, a tactic selection should answer
  `200`, so the `404` is shown to come from the kind and not from a broken fixture.
- **Behaviours with no named scenario:**
  - preset ids kept and not stale (US2 AS2), in `activation-presets.feature`;
  - a registration that succeeds today but answers 422 "unmodelled" (US1 AS1), in `registration/`;
  - a sibling conflict for `step_enhancement`, and its positive twin (sibling enhancers with different ids compose)
    (US2 AS3);
  - a read-path `500` naming each load code on `GET /offering`. SC-004 needs a scenario per code. For
    `prompt_slot_not_empty`, the fixture's `pack.yaml` must carry `schema_version: "1.0"`;
  - under D13 branch A, an activation write that answers `500`.

**Fix:** list these scenarios per IC, using README vocabulary only (`Given the <level> pack … with the file`,
`When I PUT … with the body:`, `Then the problem code is`, `If-None-Match the remembered header`).

**Needs the operator:** no.

### T-12 [MAJOR] plan.md:244-245, :356-358 - Early fixtures must already satisfy later rules, or later ICs will edit earlier scenarios

- The IC-01 scenario and unit fixtures are written before IC-02 (one `enhances` edge, a stem equal to the id, a
  companion), IC-03 (the slot grammar) and IC-06 (the target step resolves in the closure, and the prompt in force
  declares the slot).
- An IC-01 fixture without the edge, or whose target step lacks a declared empty slot, turns red at IC-02 or IC-06. The
  fix would then be an edit to a scenario's expected outcome. Under D13 branch A even an unselected enhancement refuses.
- Separately, the `spdd` shape is an **enhancing pack that holds no mission type**, only
  `missions/software-dev/enhancements/…`, and it depends on built-in. SC-001's fixture must be exactly that. A pack that
  also holds the mission type tests a different path.

**Fix:** state in the plan that every fixture conforms from IC-01 onwards to the full rule set, in a two-pack shape: a
base pack with a prompt that declares the slot empty, and an enhancing pack that `depends_on` it.

**Needs the operator:** no.

### T-13 [MAJOR] plan.md:225-226, :470 - A dormant mask in the whole-pack harness surfaces at #435

- `DomainRoundTrip.writtenByKindAndId` (:337-360) looks for written artefact files in subdirectories only for bindings
  with `placedAtItsFileName()`. The comment says "every other kind directly in it".
- The plan sets `placedAtItsFileName()` false for the step enhancement, which is nested at
  `missions/<t>/enhancements/`.
- The first pack with an enhancement that goes through `DomainRoundTrip` will report it as unpaired or missing. That
  happens at #435's `packs/spdd` corpus test, or earlier on any fixture.
- D20 keeps the gate green today only because built-in has none, so this stays latent.

**Fix:**
- Add a fixture-level `DomainRoundTrip` test (not the tagged gate) over an enhancing pack in IC-01.
- Extend the harness's pairing to use the held `PackLayout` place.

**Needs the operator:** no.

---

## MINOR

- **T-14** `[MINOR]` plan.md:236, research.md:100-103. The `empty_kinds` reason "stale when built-in gains one"
  explains nothing. The existing entries name where the kind round-trips instead (`expected-red.yaml:10-26`).
  **Fix:** "Step enhancements and their fragments round-trip in StepEnhancementYamlTest, WholePackStoreTest and the
  DomainRoundTrip fixture test; built-in has none at the pin (open-packs#175 ships them in packs/spdd)." Also add the
  entry in the same commit as the binding: `WholePackExpectation:392` turns an entry for an unmodelled kind into
  STALE_BASELINE. The plan already does this; keep it. Not for the operator (the D20 text is a reason, not an
  assertion).
- **T-15** `[MINOR]` plan.md:231-233, :254-258, :471. **IC-boundary ordering.**
  - IC-01's orphan refusal raises `prompt_fragment_invalid` from `WholePackStore`. That class may name only
    `DRG_SCHEMA_ERROR` (`ReservedRefusalCodesTest` `OFF_WIRE` :69-71). So `ReservedRefusalCodesTest` is red at IC-01
    unless the raised flag, the changelog fragment and the test list move with it.
  - The gate map lists `ReservedRefusalCodesTest` for IC-02, IC-03 and IC-06 only.
  - **Fix:** move the orphan and `checkCompanion` refusals into IC-02, or put the IC-04 hunk for
    `prompt_fragment_invalid` into IC-01's commit.
  - Not for the operator.
- **T-16** `[MINOR]` FR-009, plan.md:376-380. Only the `<pack>::`-qualified target is covered. A **bare**
  `action:<t>/<s>` target must also resolve. In which pack: its own, or the closure? Add a twin for each, plus one for
  an unknown mission type against an unknown step of a known type.
- **T-17** `[MINOR]` FR-011, quickstart.md:305-307. The warning must be asserted positively. NFR-002 ("0 new
  refusals") also passes with a no-op that never warns.
  **Fix:** the corpus test asserts exactly 5 `prompt_slot_not_empty` warnings on the pinned built-in, one per
  software-dev prompt, each naming its file.
- **T-18** `[MINOR]` plan.md:395-397. The D14 branch B "documented no-op" test needs a positive control: the policy
  schema accepts a `mission_type:` key in the same fixture and refuses an `action:` key.
- **T-19** `[MINOR]` FR-012, plan.md:463-464. Docs checks do not pin the text of the ADR annotations. Either pin the
  dated annotation with a doc test, or drop "positive control" for the ADR part and keep it for the schema description
  (the `PromptFragmentContractsTest` style).
- **T-20** `[MINOR]` quickstart.md:327.
  - The `curl` has no `Authorization: Bearer` token, so `security.feature` answers 401. Add the header with the
    daemon's token, and the worktree header if required.
  - The filtered `--tests` lists per flow miss the tests that will break: `FileSystemPackInspectorTest` in Flow 1,
    `NoArtifactFieldMintsAnEdgeTest`, `CompanionFilesTest`, `DomainRoundTripTest`, `PackFileSetExactnessTest`'s
    generator, and `ActivationControllerTest`.
  - **Fix:** run all of `:packs:test` per IC (plain JUnit, cheap), and `:activations:test` at IC-07.

## NIT

- **T-21** `[NIT]` `PromptFragmentContractsTest.java:53-55`. `.as("an enhancement without fragments stays valid")`
  stays true at the schema level only. A reader will misread it once D12 refuses at load. Leave the assertion as it is
  and add a cross-reference comment in the D12 rule test, so no assertion is edited.

---

## Areas that hold (no finding)

- **The descriptor-version gate, both sides.** `schema_version` is `const "1.0"`
  (`pack-descriptor.schema.json:7-12`), so there are exactly two sides: versioned, refused; unversioned, warned. The
  plan covers both in a twin. Add the registration (staged) moment beside validate and load.
- **Ordering by code point.** Ids and pack names are ASCII by grammar, so `String.compareTo` equals code-point order.
  The level order is the `PackLevel` enum (built-in, org, project). See T-09 for the test design.
- **`ContractDescriptionsTest`.** The plan only adds to it. The reserved-reads line is PR C's. Rewriting the "Four codes
  are reserved…" paragraph breaks no current assertion. A new assertion pinning the rewritten paragraph is recommended,
  as an addition.
- **`VocabularyEqualityTest` and `WireTokenParityTest`.** No enum value is added (D10). Containment holds.
- **`HexagonalArchitectureTest`.** The placement is sound. `NoArtifactFieldMintsAnEdgeTest` walks every bound entity, so
  it gives C-003 (no target field) a free regression gate once `StepEnhancement` is bound. Cite it in IC-01.
- **`:packs:wholePackRoundTrip` at the IC-01 boundary.** It is honest if the `empty_kinds` entry lands in the binding's
  commit. Built-in has no enhancement at the pin, and the gate reads only `packs/built-in`.
- **401 and 403 on the new operation.** `security.feature` covers them generically.
- **The quickstart's Gradle invocations.** `syncPacks`, `-PopenPacksDir`, `-PcontractBaseline=none` (refused under
  CI, fine locally), `:contracts:check`, `scripts/docs/check_docs.py`, `:packs:wholePackRoundTrip` (tag `whole-pack`,
  outside `check`) and `verifyAll` all exist as named.

## Verdict

**Not ready to finalize tasks.**
- One BLOCKER needs the operator: the D19 addendum for the four unapproved assertion edits.
- Three MAJORs need the operator: T-06 (the legacy slot), T-07 (N1 corrected), and the T-08 item-1 wording.
- The other MAJORs are plan edits that need no ruling: T-02, T-03, T-04, T-05, T-09, T-10, T-11, T-12 and T-13. Most
  are about non-vacuity (same-fixture mutants and positive controls) and about the harness and generator learning the
  new kind.
