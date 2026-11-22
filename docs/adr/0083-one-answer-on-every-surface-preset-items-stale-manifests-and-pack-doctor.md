---
title: "ADR-0083: One answer on every surface: preset items, stale manifests and the pack-doctor CLI"
description: "Registration and pack-doctor refuse an unheld or undeclared preset item and a stale manifest hash alike; pack-check becomes pack-doctor; #154 lands whole."
doc_status: active
updated: 2026-11-22T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-22'
type: ADR
kind: ADR
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0046-service-release-line-and-native-packaging
  - architecture/composition-and-activation-rules
  - architecture/architectural-patterns-applied
  - guides/check-packs
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change the preset-item rule, the manifest gate, pack-doctor's commands or report, or the anti-pattern kind.
  - profile: pack-author
    guidance_text: Read this for why a preset item your closure lacks, a stale manifest hash, an inline anti-pattern or a file-less anti-pattern node is now refused, and what pack-doctor checks.
  - profile: core-maintainer
    guidance_text: Read this for the new refusal codes, the report schema and the contract fragments core regenerates from.
---

# ADR-0083: One answer on every surface: preset items, stale manifests and the pack-doctor CLI

## Status

**Accepted**, 2026-11-22. Decider: Stijn Dejongh (operator). It records the operator's rulings R-a to R-w of mission
`pack-doctor-coverage-01M7YMQK` (2026-11-21 and 2026-11-22), held as Decision Moments under that mission's
`decisions/`, except R-o, which the operator gave in review and the mission's WP01 task file records. Each ruling is
quoted below, verbatim. The mission delivers issues
[#614](https://github.com/spec-kitty/spec-kitty-charter/issues/614),
[#629](https://github.com/spec-kitty/spec-kitty-charter/issues/629),
[#630](https://github.com/spec-kitty/spec-kitty-charter/issues/630) and
[#154](https://github.com/spec-kitty/spec-kitty-charter/issues/154), and part of
[#632](https://github.com/spec-kitty/spec-kitty-charter/issues/632), under epics
[#549](https://github.com/spec-kitty/spec-kitty-charter/issues/549) and
[#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).

**Relation to ADR-0082.** This record follows
[ADR-0082](/adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface/): every rule it adds lives in
the domain factory (`PackRulesets`) or, for a storage format, at the storage edge, and no new walk over packs is
written. It supersedes nothing in ADR-0082.

**What it amends.** Each amended record carries a dated amendment or note where the point stands, and its original text
stays:

- [ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/), decisions 6, 7 and 9
  (what the CLI composes, `CLOSURE`'s preset phase, and pack-doctor);
- [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/), its amendment of 2026-11-12
  (uploaded hashes are verified);
- [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/), a status note (which decisions
  are now applied);
- [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/), R14 (superseded in part for `anti_pattern`);
- rules C31 and G9 of [Composition and activation rules](/architecture/composition-and-activation-rules/), and the
  scope table of [Architectural patterns applied](/architecture/architectural-patterns-applied/).

The release asset of [ADR-0046](/adr/0046-service-release-line-and-native-packaging/)'s amendment of 2026-11-19 is
renamed by decision 6; that record's text is history and is not edited.

## Context

ADR-0075 decision 9 asks for one answer on every surface. ADR-0082 asks that the domain own every rule and every walk.
The research of the mission (`research/rule-coverage.md`, at `main` `a5d5f9a`) found decision 9 broken in both
directions:

- **Accepted by both, though the operator now rules they must be refused.** A preset's `must_have` item no pack of the
  closure holds was dropped from the floor and reported (A10, C31). A preset item no pack declares was accepted. A stale
  `content_hash` or `manifest_hash` was restamped by registration and noticed only by `pack-check --round-trip`, under a
  generic `round_trip_red` (ADR-0042, amendment of 2026-11-12).
- **Refused by registration, passed by the checker.** A profile's reference, a step enhancement's target and a substep
  delegation are judged by the compose that registration runs, and not by the checker, which composed no closure walk
  (ADR-0075 decision 6). So is a `specializes_from` cycle.

spec-kitty-open-packs still runs a Python copy of these cross-pack rules (`validate_pack_schemas.py`) for that reason.
The aim is that it can retire them.

Two more problems sat beside these. The checker had no way to repair a manifest or write a structured report. The
`anti_pattern` kind had no file form, so a preset's `must_have` anti-pattern could never be held as a file (R-p).

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### The preset-item rule (#629, #632 item 1)

1. **The own closure** (R-k). A preset item is judged against its declaring pack's own closure: the pack and every pack
   it reaches over `depends_on` and `parent_pack`, never a dependent. It is the domain's composition closure
   (`PackCompositionResolver.compositionClosure`) rooted at the pack, so no walk is added.
2. **Declared and held** (R-l, R-v). An item is *declared* when a pack of the own closure holds it as an artifact file
   or as a graph node of its kind (`PackCompositionFacts.declared`). It is *held* when a pack of the own closure holds
   it as an artifact file (`PackCompositionFacts.held`). These two fields replace `defined` outright. References resolve
   against a derived view, `PackCompositionFacts.referable()`: the held files plus the step (`action:`) nodes of the
   mission types the pack holds. It is never stored, so a file-less graph node is declared but neither held nor
   referable.
3. **The codes** (R-m). One rule, `PresetItemRules`, judges each item once, in this order:
   - an item that names an artifact of the closure only by letter case gets the existing case-only answer,
     `pack_reference_artifact_missing`, naming the packs, with no violation of its own, and nothing else (spec US1
     scenario 3);
   - else a `must_have` item that is not held answers `preset_item_not_held`;
   - else an item that is not declared answers `preset_item_undeclared`.

   Each new code is a `CharterRefusalCode` value with status 422 and one violation at
   `presets/<id>.yaml#/activated_<kinds>/<index>`. An item of another kind never satisfies the entry.
4. **Where it runs** (R-a). `PackRulesets` exposes it, and every surface that judges a write takes it from there:
   - pack-doctor, in `CLOSURE`'s preset phase, after the compose and the preset contests;
   - registration and every per-artifact write, in `ClosureImpactCheck`, over each single-root closure the write joins,
     after the own-closure walk;
   - never a composing read. A stored pack whose preset now breaks the rule still composes, and `requiredNotHeld` still
     reports the unheld item. The floor of a read stays restricted to held items (C31).

   It supersedes A10 and C31 for writes, and the operator's 2026-11-06 ruling that a preset id no pack declares is
   accepted at registration.

### The stale manifest (#630)

5. **`manifest_stale`** (R-b). A `content_hash` (of a constituent or a preset) or a `manifest_hash` of
   `pack-manifest.yaml` that does not match the incoming bytes is one violation per field, code `manifest_stale`, at
   `pack-manifest.yaml`, its detail naming the field (`constituents{<path>}.content_hash`,
   `presets{<path>}.content_hash` or `manifest_hash`). It is judged at the storage edge, in the manifest gate after
   `manifest_membership` and only when nothing else failed; a path membership already names gets none. So whole-pack
   registration and `pack-doctor validate` refuse it alike. Every write still stamps the manifest from the bytes it
   writes. With `--round-trip`, the round trip no longer reports a source-stale field: the cause is the one
   `manifest_stale`. A written-stale field, where the writer itself is wrong, still reports `round_trip_red`.

### pack-doctor (#632 item 2)

6. **The checker is renamed pack-doctor, in this pull request** (R-c, R-f). The module, the package, the main class, the
   jar (`pack-doctor-<version>.jar`) and its checksum, the release asset, the CI steps, the release scripts, the report
   schema (`PackDoctorReport`) and the live docs are renamed outright. No alias, no old name and no deprecation window
   remain (C-002). History keeps the old name (C-004): mission and Op records, released changelog entries and ADR bodies.
   pack-validation's `PackCheck`, validate mode over one pack, is a domain term and keeps its name.
7. **Two commands** (R-e). `pack-doctor validate` takes `--config-root <dir>` or repeated `--pack <dir>[:<level>][=<id>]`,
   and `--round-trip`, `--full`, `--report <file>` and `--format yaml|xml`. `pack-doctor remediate <dir>...` takes pack
   directories and no option. A command line with no command word is a usage fault: the old flat form is no alias.
8. **`--full` is the `REFERENCES` check** (R-h, narrowed by R-s; named by R-u). Per pack of the set, within its own
   closure, it runs three existing domain functions in registration's phase order:
   1. the substep delegations (`PackCompositionResolver.delegationFaultsOf`), answering `validation_failed` with the
      violation `substep_delegation_unresolved` at the mission type file, as registration refuses them
      (`ClosureFault.asRegistrationRefuses()`);
   2. the references (`PackCompositionResolver.referenceFaultsOf`), answering `pack_reference_unqualified`,
      `pack_reference_pack_missing` or `pack_reference_artifact_missing`;
   3. the step enhancements' targets (`EnhancementCompose.targetFaultsOf`), answering `pack_reference_pack_missing` or
      `pack_reference_artifact_missing`.

   Each check collects every fault. A later check runs only when the earlier ones pass, as a later phase of registration
   does, since it would answer the same cause again. `REFERENCES` runs only when `CLOSURE` ran and found no fault; when
   `CLOSURE` is skipped, `REFERENCES` is skipped too, naming the same packs. Registration and the composing read take the
   first fault of the same collect-all functions, so each check has one body.
9. **What `--full` does not judge yet** (R-s). The `specializes_from` cycle check (it needs the set's composed closure,
   since siblings are not in each other's own closure, and `ImpactRelations.cyclesIn` moved into `PackGraph`), the
   relation checks (`ImpactRelations`), the edge-index refusals (`EdgeIndex`) and the own-closure walk
   (`OwnClosureWalk`: the fallback profile, the role pins and the profile enhancements) land in the domain factory with
   epic [#636](https://github.com/spec-kitty/spec-kitty-charter/issues/636) slices 2 to 4 and 7. Registration keeps
   refusing each of them. The own-closure rules (`FallbackProfileRule`, `RolePinRule`, `ProfileEnhancementRule`) join
   `PackRulesets` beside `PresetItemRules` in #636 slice 3. #632 is referenced, not closed, for that remainder.
10. **The report** (R-i, R-n). The report file is the same tree as the standard-output problem JSON, schema
    `contracts/charter/schemas/PackDoctorReport.yaml`, with `Problems.Problem` reused as it was. Standard output gains
    three required keys, `tool`, `inputs` and `summary`; the keys it had (`checks`, `packs`, `skipped`, `problems`) are
    unchanged. `checks` gains `REFERENCES`, and `skipped[].check` may name it. `--report <file>` writes the tree as YAML,
    or as XML with `--format xml`. The XML is our own element-per-key mapping, stated in the schema's description, not
    JUnit's. The JSON Schema is the only normative schema: the contract test parses the XML back into the tree and
    validates it against that schema, so no XSD is kept by hand. An xUnit-compatible form is a later option (R-n).
11. **`remediate`** (R-j). For each pack, in the order given, `remediate` refuses a pack that holds a fault it does not
    repair: any violation of the storage verdict except the manifest gate's (`manifest_membership`, `manifest_stale`).
    Then it rewrites each modelled file in the round trip's canonical form (ADR-0036: the meaning stays, the bytes may
    change) and derives and stamps `pack-manifest.yaml` as every service write does. It writes a staged copy and swaps
    it in, so a refused or failing pack is left as it was. A file the census does not hold is left untouched. Each pack's
    outcome is `rewritten`, `unchanged` or `refused`; then `remediate` runs `validate --round-trip` over the same set. It
    is `PackRemediation`, a facade at the storage edge (`packs.infra.storage`) that composes the existing writer and
    stamp, judges nothing new, and takes no lock.

### #154 delivered whole

12. **All of #154 lands in this pull request** (R-p, R-q, R-r). The `anti_pattern` kind gets its file form,
    `anti_patterns/<id>.anti-pattern.yaml` (ADR-0052 decision 8, as amended by ADR-0053 decision 30), closed, with
    `schema_version`, `id` (the file stem), `title`, `description`, `severity` (`smell`, `severe` or `breaking`, no
    default) and `bad_example` required and `good_example` optional (WP10). A graph node of the same URN in the same
    pack declares the anti-pattern and is a `rejects` edge's endpoint; the file holds it. A graph node with no file in
    its pack is refused `drg_schema_error` at `/nodes/<n>` of its document (ADR-0052 decision 14; WP11). The procedure
    and styleguide shapes drop `anti_patterns` outright, so a file that still carries the key is refused
    `artifact_schema_error`, unknown key (ADR-0052 decision 16; WP12). The example packs carry no inline anti-pattern and
    no file-less node: their 18 inline entries are files of `built-in`, each with a node and a `rejects` edge.
13. **No code is minted for #154.** Each refusal is an existing answer: a schema breach is the closed schema's
    `artifact_schema_error` at the file and the breaching pointer; a misplaced file or a stem mismatch is the existing
    stem and placement rule; a graph node a pack may not declare is already `drg_schema_error` at `/nodes/<n>`, as for a
    guard, lifecycle or routing-catalog node.
14. **What stays for spec-kitty-open-packs.** The curation of its inline entries and its file-less nodes (ADR-0052
    decision 15) is open-packs' work. Until it is done, its styleguides and procedures with inline entries, and its
    graph documents with file-less nodes, are refused at its repin.

### The rest of the mission

15. **One closure fault type** (#614, slice 1 of #636; R-o). `CompositionFault` is deleted, and one `ClosureFault.of(...)`
    factory builds every closure fault. `Reason` gains an `Origin`: an opaque element identifier owned by the kernel
    (ADR-0082 decision 4), never a copy of `Reason.rule`; `Origin.NONE` is the origin of a reason about no element.
    `ClosureVerdict` stays a fault list with a derived `Rationale`, because a reason has no slot for the packs and
    violations a refusal names; #636 slice 7 removes it. No wire code, status, detail or order changed.
16. **The round-trip codes** (R-t). `EXCLUDED_WRITTEN` is retired outright, with no alias: an excluded file the store
    wrote is `NOT_HELD_WRITTEN`, like a file no rule covers and a mapped file nothing names, its detail giving the
    census's reason. The `STALE_BASELINE` and `STALE_KNOWN_FAILURE` wordings for such an entry merge into one. These are
    the detail texts of `round_trip_red`; no violation code changes.
17. **#535 and #52 item 3 are not the charter's to judge** (R-g). The charter keys an artifact by its kind and id, never
    by its file name, and checks a file stem only for the kinds whose id is their stem; #535's file-name collision across
    layers is core's activation-token concern, and is closed as not planned. Every edge endpoint is a `<kind>:<id>` URN
    (ADR-0017 R6, DIV-007), and no map of bare ids remains, so #52 item 3, a bare id under two kinds, cannot arise and
    is closed as obsolete.
18. **Out of scope** (C-005). An id re-declared across levels (a layer override, ADR-0053 decision 6), a bare edge
    endpoint no pack holds (DIV-007), a prompt slot outside an activation, and #158 beyond `manifest_stale` stay
    spec-kitty-open-packs house rules.
19. **No release before this lands** (R-w). No `service-v*` release is cut until this mission lands, so
    spec-kitty-open-packs aligns once, against the release that carries pack-doctor.
<!-- markdownlint-enable MD029 -->

## The rulings, verbatim

<!-- The operator's words are quoted as written, spelling included (R-w). -->
<!-- cspell:ignore untill allign -->

Each is the Decision Moment's final answer as the operator gave it, with the rationale's own words where the final
answer is an option's label. A rationale is quoted whole, except its lead-in ("Operator ruling R-x (date)", and the
source and operator words where the table gives them), which is dropped.

| Ruling | Decision Moment | Verbatim | What it decides |
|---|---|---|---|
| R-a | `01M7YMR9XAC827CY36P86TYAM4` | "R-a Both refuse: registration and pack-check refuse a must_have no pack of the declaring pack's depends_on closure holds, and an activated_* id no closure pack declares as that kind; supersedes A10/C31 and the 2026-11-06 ruling for writes; reads keep reporting requiredNotHeld" | Decision 4 |
| R-b | `01M7YMRDZG8NGRET9SGVK4F9JQ` | "R-b Refuse everywhere: manifest_stale at pack-manifest.yaml in the manifest gate beside manifest_membership; validate, pack-doctor and whole-pack registration refuse; round trip drops its duplicate; amends ADR-0042" | Decision 5 |
| R-c | `01M7YMRHYNJTS7P90ZXJAKK1RB` | "R-c the same pack-check jar, renamed pack-doctor, in accordance with the doctor surfaces of the as-is system" | Decision 6 |
| R-d | `01M7YMRP50MACE284ACZT18ZWC` | "R-d Close reverse gap: compose reference, enhancement-target and specialization-cycle checks per pack within its depends_on closure, same codes; cross-level re-declaration, unheld bare endpoints and slots outside an activation are not the charter's to judge" | Decisions 8, 9 and 18; narrowed by R-s |
| R-e | `01M7YMRTDZH9N51PMPPW7MTXYS` | "R-e pack-doctor validate and pack-doctor remediate; validate has a full-closure option and an option to write findings to a structured yaml/xml report file" | Decision 7 |
| R-f | `01M7YMRYNM40MWH7YNYAZ8Y4BV` | "R-f Same PR" | Decision 6 |
| R-g | `01M7YMS2VT660SKB35KYDRMTBQ` | "R-g Not charter's; close: #52.3 obsolete; #535 recorded as core's activation-token concern, closed not_planned" | Decision 17 |
| R-h | `01M7YMS6WDSC1HHQ1WVZ3F6MH3` | "R-h --full adds the closure walk; default PACK + CLOSURE; --round-trip stays separate" | Decision 8; narrowed by R-s |
| R-i | `01M7YMSB4RZHY4RBNSVSE679J5` | "R-i --report <file> --format yaml\|xml, YAML default" | Decision 10 |
| R-j | `01M7YMSF18MBD43VA8S1CMV9MG` | "R-j Manifest + canonical form: derive and stamp pack-manifest.yaml and rewrite modelled files in the round trip's canonical form (ADR-0036)" | Decision 11 |
| R-k | `01M7Z19RJNGXJBZ0V1C70379GX` | "R-k depends_on + parent_pack: the declaring pack's own composition closure, not dependents" | Decision 1 |
| R-l | `01M7Z19XBA72B1FCEPVJQZ3Z7J` | "R-l Declared vs held: activated item declared as its kind (graph node or artifact file) in the closure; must_have held as an artifact file; fixtures fixed" | Decision 2 |
| R-m | `01M7Z1A226EPVJPKBBKSE3J0FM` | "R-m New preset codes: preset_item_undeclared and preset_item_not_held in CharterRefusalCode" | Decision 3 |
| R-n | `01M7Z1D80KMV4X0YTFWW1HFNAD` | "R-n own schema at first, see if a XUnit compatible version is possible later on" | Decision 10 |
| R-o | (WP01 task file) | the operator ruled "element id" | Decision 15 |
| R-p | `01M7ZPVTTGHYCSC2Y39BZV0FXT` | "Build the file kind first"; rationale: "the anti_pattern file kind (ADR-0052 d8 shape) is built first as its own slice or mission; WP03 (#629, #632.1) is held until it lands; #629 does not close in this PR." | Decision 12; its "#629 does not close" is overruled by R-q |
| R-q | `01M7ZQGHVNQXAQ0S3DMTK7EZCB` | "New WP in this mission"; rationale: "a new WP builds the anti_pattern file kind (schema, file-kinds rule, storage binding, kernel facts, contract, the built-in fixture file) before WP03; WP03 then runs as planned and #629/#632 still close in this PR." | Decision 12; widened by R-r |
| R-r | `01M801H54DXR0447M3F2ZQWFPZ` | "All of #154 now"; rationale: "this PR delivers all of #154: the anti_pattern file kind, the graph-only anti_pattern node refusal, and dropping the inline anti_patterns entries from the procedure and styleguide schemas (Breaking packs-contract change). open-packs styleguides/procedures with inline entries are refused until curated (draft open-packs#TBD). The PR closes #154." | Decisions 12 to 14 |
| R-s | `01M801NHF0BFAX05EA3F19KHV7` | "(a) delegations, references, enhancement targets now; the rest with #636"; rationale, operator words "1. as recommended": "validate --full = delegations + references + enhancement targets only, each an existing domain function run per own closure in registration's phase order, no new walk; registration/offering use the same collect-all forms. The spec narrows 'full pass' to these; ADR-0083 records specializes_from cycle, relations, edge-index and own-closure-walk checks as landing in the factory with #636 slices 2-4/7; the PR writes Refs #632 naming what is left. WP05 unblocked. The #636 round-trip-codes fold into WP04 stays unanswered: not folded." | Decisions 8 and 9; its last sentence is overruled by R-t |
| R-t | `01M801QGCV2GW4DCF0KTRFBNHH` | "Yes, fold into WP04"; rationale, operator words "2. as recommended": "Fold #636's round-trip-codes slice into WP04 so WholePackExpectation changes once: retire EXCLUDED_WRITTEN beside NOT_HELD_WRITTEN (~506) and merge the STALE_* wordings (~241/246/251). It changes an existing code: contract changelog fragment and PR body note it; red-first tests; WP04 gets a fresh review cycle. PR writes Refs #636 for the slice." | Decision 16 |
| R-u | `01M8035FAMHXG0A04SCBD7QTKD` | "REFERENCES"; rationale: "the --full check value is REFERENCES, naming what the phase checks; CLOSURE_WALK stays free for #636's own-closure walk." | Decision 8 |
| R-v | `01M80YAS1QJABJ74REAXWNNZF9` | "Derived referable()"; rationale: "references resolve against a derived referable() view = held files plus held mission types' step (action:) nodes; no stored third field. Rename the retired-word carriers (PackUnderResolution.definedArtifacts, PackRegistrationService Before.held/heldBy, DomainRepositoryServiceTest.definedArtifactsNameEveryKindThePackHolds) to referable. A file-less node (directive:GHOST) does not resolve." | Decision 2 |
| R-w | `01M812KK99P3T1FQ7ZBXT4Z3T1` | "No release until this mission lands"; operator words: "yes, no release yet, untill the charter validation module and cli are finalized, so open packs can allign properly."; rationale: "No release until WP01-WP09 land; open-packs aligns once against that release. WP09 draft (a) widened to the full alignment specification." | Decision 19 |

Two further Decision Moments confirm without a letter: the intent and the alignment squad over ADR-0082
(`01M7Z1A6RXQB141EKZHB3BD2AH`) and the order of the work packages (`01M7Z2EXFG0K83SMFBVBGFEGBC`).

**Where rulings differ, the later one governs:**

- R-q governs over R-p's "#629 does not close in this PR": #629 closes.
- R-r governs over R-q's scope, the file kind alone: all of #154 lands, and the operator declined "File kind only".
- R-s governs over R-q's "#629/#632 still close in this PR" for #632: the PR writes `Refs #632`, and #632 is
  referenced, not closed (decision 9).
- R-s governs over R-d and R-h: `--full` is the three checks of decision 8, and the `specializes_from` cycle is not
  among them yet.
- R-t governs over R-s's last sentence: the round-trip-codes slice of #636 is folded (decision 16).

## Consequences

### Positive

- Registration, every write and `pack-doctor validate` give one code for the same preset item and the same stale hash,
  and `--full` gives registration's code for a reference, an enhancement target and a delegation.
- spec-kitty-open-packs can retire six of its Python rules: undeclared preset ids, unheld `must_have` items, stale
  hashes, profile references, enhancement targets and manifest derivation (through `remediate`). Its
  `specializes_from` cycle rule stays until #636 lands it in the factory.
- An anti-pattern has one home, its file, with a severity; nothing is inline any more.
- A CI system can keep a report file, in YAML or XML, under one schema.

### Negative

- **Breaking, each with a contract fragment (ADR-0022).** A preset item the own closure lacks, a stale manifest hash, an
  invalid anti-pattern file, a file-less `anti_pattern` node and an inline `anti_patterns` key are now refused where
  they registered before. `antiPatterns` leaves `Styleguide`, `StyleguideRegistration` and `Procedure`, and two schemas
  are deleted. The report schema is renamed.
- spec-kitty-open-packs' content is refused at its repin until it is curated: its inline entries, its file-less nodes,
  and any preset item or manifest that breaks the new rules. `remediate` restamps manifests; the rest is curation.
- The `PackDoctorReport` on standard output gains three keys; a strict reader of the old shape must accept them.
- `--full` is narrower than its first ruling (R-h): a pack it passes can still be refused at registration for a
  `specializes_from` cycle, a relation, an edge-index refusal or its own-closure walk.

### Risks

- **Order of findings.** The preset-item phase runs after the compose in pack-doctor and after the own-closure walk in
  registration. A pack with both kinds of fault can lead with a different first code on each surface. Mitigation: the
  parity table (`FullPassParityTest`) pins each planted fault's code on both surfaces.
- **A case-only policy-lock URN.** Registration's case-only check (`OfferingReadService.judged`, phase 6) covers the
  URNs of a pack's presets and of its policy file; pack-doctor's preset phase checks preset items only, so a policy
  lock that differs from a held id only by case may answer differently. Mitigation: a parity row, or the check moved
  into the factory with #636.

## Alternatives considered

The Decision Moments of R-a to R-m record no options, so only the alternatives the mission recorded are listed here.

### Batch 1 and 2 (2026-11-21, specify)

- **The rename in its own pull request** (against R-f). Split pull requests were offered and declined (plan,
  Complexity Tracking).
- **YAML only for the report** (against R-i). Offered and declined (plan, Complexity Tracking).
- **The composed closure, the one a read composes** (against R-k). The pre-spec squad found that it differs from the
  declaring pack's own closure; the operator ruled the own closure, without dependents.
- **Leave registration accepting and refuse in pack-check alone** (research finding F1). Not chosen (R-a, R-b): a
  second answer is what ADR-0075 decision 9 forbids.

### Batch 3 (2026-11-21, plan)

- **JUnit XML** (against R-n). Not chosen first; an xUnit-compatible form stays a later option.
- **For the `must_have` anti-pattern** (R-p): "Node counts for graph kinds" and "Drop must_have in fixture" were
  declined.
- **Where to build the file kind** (R-q): "Separate mission; PR ships without WP03" and "Split WP03" were declined.
- **How much of #154** (R-r): "File kind only" and "Plus graph-only refusal" were declined.
- **`--full`** (R-s): "(b) all closure-walk checks now" and "(c) design-only" were declined.
- **The round-trip-codes fold** (R-t): "No, leave for #636" was declined.
- **The check's name** (R-u): `CLOSURE_WALK` and `FULL` were declined.

### Batch 4 (2026-11-22)

- **What a reference resolves against** (R-v): "Steps go into held" and "Compose reads declared" were declined.
- **Release now** (R-w): declined.

## References

- Mission `pack-doctor-coverage-01M7YMQK`: `spec.md`, `plan.md` (D1 to D14), `data-model.md`, `decisions/`,
  `research/rule-coverage.md` and `research/squad-dispositions.md`.
- Issues [#614](https://github.com/spec-kitty/spec-kitty-charter/issues/614),
  [#629](https://github.com/spec-kitty/spec-kitty-charter/issues/629),
  [#630](https://github.com/spec-kitty/spec-kitty-charter/issues/630),
  [#632](https://github.com/spec-kitty/spec-kitty-charter/issues/632),
  [#154](https://github.com/spec-kitty/spec-kitty-charter/issues/154),
  [#535](https://github.com/spec-kitty/spec-kitty-charter/issues/535),
  [#52](https://github.com/spec-kitty/spec-kitty-charter/issues/52),
  [#636](https://github.com/spec-kitty/spec-kitty-charter/issues/636) and
  [#158](https://github.com/spec-kitty/spec-kitty-charter/issues/158).
- [Check packs](/guides/check-packs/): the pack author's guide to pack-doctor.
- `contracts/charter/schemas/PackDoctorReport.yaml`, `contracts/charter/schemas/CharterRefusalCode.yaml` and the
  mission's fragments under `contracts/charter/changes/`.
