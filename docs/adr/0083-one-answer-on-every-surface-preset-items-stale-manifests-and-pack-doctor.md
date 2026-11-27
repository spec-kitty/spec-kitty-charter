---
title: "ADR-0083: One answer on every surface: preset items, stale manifests and the pack-doctor CLI"
description: "Registration and pack-doctor refuse an unheld or undeclared preset item and a stale manifest hash alike; pack-check becomes pack-doctor; #154 lands whole."
doc_status: active
updated: 2026-11-25T00:00:00Z
decision_status: Accepted, with an amendment of decision 11 (2026-11-25, #651)
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

**Accepted**, 2026-11-22. Decider: Stijn Dejongh (operator). It records the operator's rulings R-a to R-ae of mission
`pack-doctor-coverage-01M7YMQK` (2026-11-21 to 2026-11-24), held as Decision Moments under that mission's
`decisions/`, except R-o, which the operator gave in review and the mission's WP01 task file records. Each ruling is
quoted below, verbatim. The mission delivers issues
[#629](https://github.com/spec-kitty/spec-kitty-charter/issues/629),
[#630](https://github.com/spec-kitty/spec-kitty-charter/issues/630) and
[#154](https://github.com/spec-kitty/spec-kitty-charter/issues/154), and part of
[#614](https://github.com/spec-kitty/spec-kitty-charter/issues/614) (all but its `ClosureVerdict` clause, deferred
to #636 slice 7 by R-x) and of
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
- rules C31, C35 and G9 of [Composition and activation rules](/architecture/composition-and-activation-rules/), and
  the scope table of [Architectural patterns applied](/architecture/architectural-patterns-applied/).

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
   (`PackGraph.compositionClosure`) rooted at the pack, so no walk is added.
2. **Declared and held** (R-l, R-v). An item is *declared* when a pack of the own closure holds it as an artifact file
   or as a graph node of its kind (`PackCompositionFacts.declared`). It is *held* when a pack of the own closure holds
   it as an artifact file (`PackCompositionFacts.held`). These two fields replace `defined` outright. References resolve
   against a derived view, `PackCompositionFacts.referable()`: the held files plus the step (`action:`) nodes of the
   mission types the pack holds. It is never stored, so a file-less graph node is declared but neither held nor
   referable.
3. **The codes** (R-m). One rule, `PresetItemRules`, judges each item once, in this order:
   - an item that names an artifact of the closure only by letter case gets no preset-item fault: the case-only answer
     (decision 20), `pack_reference_artifact_missing`, naming the packs, with no violation of its own, is its one
     answer (spec US1 scenario 3);
   - else a `must_have` item that is not held answers `preset_item_not_held`;
   - else an item that is not declared answers `preset_item_undeclared`.

   Each new code is a `CharterRefusalCode` value with status 422 and one violation at
   `presets/<id>.yaml#/activated_<kinds>/<index>`. An item of another kind never satisfies the entry.
4. **Where it runs** (R-a). `PackRulesets` exposes it, and every surface that judges a write takes it from there:
   - pack-doctor, in `CLOSURE`'s preset phase, after the compose and the preset contests, and before the case-only
     answer (decision 20);
   - registration and every per-artifact write, in `ClosureImpactCheck`, over each single-root closure the write joins,
     after the own-closure walk;
   - never a composing read. A stored pack whose preset now breaks the rule still composes, and `requiredNotHeld` still
     reports the unheld item. The floor of a read stays restricted to held items (C31).

   It supersedes A10 and C31 for writes, and the operator's 2026-11-06 ruling that a preset id no pack declares is
   accepted at registration. Each closure rule this record adds has one body in the factory, which registration and
   pack-doctor both call: `PresetItemRules` here, `CaseOnlyRules` (decision 20) and `PackRulesets.references`
   (decision 8, ruling R-ab).

### The stale manifest (#630)

5. **`manifest_stale`** (R-b). A `content_hash` (of a constituent or a preset) or a `manifest_hash` of
   `pack-manifest.yaml` that does not match the incoming bytes is one violation per field, code `manifest_stale`, at
   the field in `pack-manifest.yaml` (`pack-manifest.yaml#/constituents/<n>/content_hash`, `#/presets/<n>/content_hash`
   or `#/manifest_hash` on the wire, since main's in-file pointers, `a18fb90f4`), its detail naming the field
   (`constituents{<path>}.content_hash`, `presets{<path>}.content_hash` or `manifest_hash`). It is judged at the storage edge, in the manifest gate after
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
   and `--round-trip`, `--full`, `--report <file>` and `--format yaml|xml`. `pack-doctor remediate
   <dir>[:<level>][=<id>]...` takes pack directories, each written as a `--pack` value is, and no option. A command
   line with no command word is a usage fault: the old flat form is no alias.
8. **`--full` is the `REFERENCES` check** (R-h, narrowed by R-s; named by R-u; scope by R-ae). Per root of the set, each
   pack in configured-id order (the id each pack is configured under: in pack-doctor its `--pack` id, the directory name
   or `=<id>`; not the descriptor's `pack_id` ULID), over the closure the root composes, every pack of it judged, it
   runs three existing domain functions in registration's phase order:
   1. the substep delegations (`PackCompositionResolver.delegationFaultsOf`), answering `validation_failed` with the
      violation `substep_delegation_unresolved` at the mission type file, as registration refuses them
      (`ClosureFault.asRegistrationRefuses()`);
   2. the references (`PackCompositionResolver.referenceFaultsOf`), answering `pack_reference_unqualified`,
      `pack_reference_pack_missing` or `pack_reference_artifact_missing`;
   3. the step enhancements' targets (`EnhancementCompose.targetFaultsOf`), answering `pack_reference_pack_missing` or
      `pack_reference_artifact_missing`.

   Each check collects every fault. A later check runs only when the earlier ones pass, as a later phase of registration
   does, since it would answer the same cause again. That order and that stop have one body,
   `PackRulesets.references(closure, members, factsOf)` (ruling R-ab): registration's composing judge
   (`OfferingReadService.judged`, phases 2 to 4) calls it with every member of the closure it composes, and
   pack-validation's `ReferencesPhase` calls it the same way per root of the set (ruling R-ae, Tier R review B2),
   `references(rooted.order(), rooted.order(), factsOf)`, so the stop rule spans the closure as it does there. A
   fault several roots find is reported once, as decision 20 reports a case-only fault. So a dependency's bare
   reference that only a dependent holds is `pack_reference_unqualified` from the dependent's root, as registering the
   dependent answers it; and a qualified reference into a pack outside the referrer's `depends_on` but inside a root's
   closure is one problem per root, each in that root's words, as registering each root answers it. Packs that only a
   stored activation lists together are never one closure in pack-doctor, which is blind to activations, so that
   shape stays registration's alone, as for the case-only answer (decision 20). Registration and the composing read
   take the first fault it answers.

   `REFERENCES` runs when `CLOSURE` ran and found no composition fault (ruling R-aa): a closure that does not
   compose (`CompositionRules`) has no order to judge, so it gates `REFERENCES`. A preset contest, a preset-item fault or
   a case-only answer does not: it is reported beside `REFERENCES`' own faults. When `CLOSURE` is skipped, `REFERENCES`
   is skipped too, naming the same packs.
9. **What `--full` does not judge yet** (R-s). The `specializes_from` cycle check (it needs the set's composed closure,
   since siblings are not in each other's own closure, and `ImpactRelations.cyclesIn` moved into `PackGraph`), the
   relation checks (`ImpactRelations`), the edge-index refusals (`EdgeIndex`) and the own-closure walk
   (`OwnClosureWalk`: the fallback profile, the role pins and the profile enhancements) land in the domain factory with
   epic [#636](https://github.com/spec-kitty/spec-kitty-charter/issues/636) slices 2 to 4 and 7. Registration keeps
   refusing each of them. The own-closure rules (`FallbackProfileRule`, `RolePinRule`, `ProfileEnhancementRule`) join
   `PackRulesets` beside `PresetItemRules` in #636 slice 3. #632 is referenced, not closed, for that remainder.

   *Proposed amendment 2026-11-27 (#632 item 3, operator ruling D3 of mission adr-0082-graph-walk-slices, FR-012):
   `--full` adds a second check, `RELATIONS`, after `REFERENCES`. Per root whose closure passed `REFERENCES`, over the
   closure the root composes, it runs the factory's relation rules, the `specializes_from` cycle among them
   (`PackRulesets.relationFaults`), with the registration's codes, a fault several roots find reported once. The
   edge-index refusals and the own-closure walk stay here: they need the layer merge and a profile reader, which
   pack-doctor gets in #636 slice 7. [Check packs](/guides/check-packs/#check-what-registration-checks) holds the
   detail.*
10. **The report** (R-i, R-n). The report file is the same tree as the standard-output problem JSON, schema
    `contracts/charter/schemas/PackDoctorReport.yaml`, with `Problems.Problem` reused as it was. Standard output gains
    three required keys, `tool`, `inputs` and `summary`; the keys it had (`checks`, `packs`, `skipped`, `problems`) are
    unchanged. `checks` gains `REFERENCES`, and `skipped[].check` may name it. `--report <file>` writes the tree as YAML,
    or as XML with `--format xml`. The XML is our own element-per-key mapping, stated in the schema's description, not
    JUnit's. The JSON Schema is the only normative schema: the contract test parses the XML back into the tree and
    validates it against that schema, so no XSD is kept by hand. An xUnit-compatible form is a later option (R-n).
    A string holding a character XML 1.0 cannot carry (a control character other than tab, line feed and carriage
    return, `U+FFFE`, `U+FFFF`, or a surrogate that is not half of a pair) is written as an element with
    `type="escaped"` (ruling R-ac): each such character is `\uXXXX`, the upper-case hex of its UTF-16 code unit, and
    each backslash is `\\`. `XmlReport.read` undoes it and refuses a malformed escape. A string with no such character is
    never escaped, so pack content, a file name with `U+0001` included, never makes the report fail (`2`). The report
    file must lie outside every pack the run checks, by real path, links followed (a usage fault, `2`, otherwise). It is
    written to a hidden sibling before anything reaches standard output, and renamed into place once standard output
    took the report; a report file that cannot be written exits `2` with nothing on standard output, and when standard
    output cannot be written the file is left as it was.
11. **`remediate`** (R-j). For each pack, in the order given, `remediate` refuses a pack that holds a fault it does not
    repair: any violation of the storage verdict except the manifest gate's (`manifest_membership`, `manifest_stale`).
    Then it rewrites each modelled file in the round trip's canonical form (ADR-0036: the meaning stays, the bytes may
    change) and derives and stamps `pack-manifest.yaml` as every service write does. A file the census does not hold is
    left untouched. A pack whose modelled file the round trip refuses (decision 23) cannot be written in canonical form:
    it is `refused` too, each such file named with the round trip's reason (`the round trip refused <file>: ...`), and
    nothing is written (Tier R review B4). Each pack's outcome is `rewritten`, `unchanged` or `refused`; then
    `remediate` runs `validate --round-trip` over the same set. It is `PackRemediation`, a facade at the storage edge
    (`packs.infra.storage`) that composes the existing writer and stamp, judges nothing new, and takes no lock.

    *Amended 2026-11-25 (operator ruling of 2026-11-25, [#651](https://github.com/spec-kitty/spec-kitty-charter/issues/651)):
    `remediate` no longer refuses every violation but the manifest gate's. A quoted number in a field the schema types
    `number` or `integer` (`"15"` where the field takes `15`), whose detail now ends `write 15 without quotes, or run
    pack-doctor remediate`, is repaired too: written plain in the work copy, after which the copy is judged again and
    the pack refused, nothing written, when anything is left. The pack schemas do not change and the service still
    accepts only the number. The repairs are printed on the outcome lines, and, by operator ruling Q14 of the same day,
    listed in the report's new `repairs`.
    [Check packs](/guides/check-packs/#repair-a-pack) holds the detail.*
    - **Staging.** The work is done in a copy under the JVM's temporary directory. A copy equal to the pack, byte for
      byte, is `unchanged`, and nothing is written in or beside the pack, so a canonical pack needs no write access.
      Otherwise the copy is written to a hidden sibling, `.<pack>.remediate-<uuid>`, with the pack's file modes; the pack
      is moved aside to `.<pack>.aside-<uuid>`, the sibling is moved in, and the aside copy is deleted, each of its
      directories made writable by its owner first, since it carries the pack's own modes (a read-only directory
      included). If it still cannot be deleted, the swap stands: the pack is `rewritten`, the exit code is unchanged,
      and a warning on standard error names the copy to delete. Rewriting needs write access to the parent of the
      pack's real directory. A mode is carried and made writable only through `OwnerOnly` (`carryMode`,
      `ownerWritable`), each a no-op without a POSIX view (#510).
    - **A link root.** A pack root that is a symbolic link is remediated in the directory it resolves to, which is
      swapped; the link is kept.
    - **The working directory.** A pack that is, or holds, the working directory is a usage fault (`2`): a swap would
      move the directory the process stands in.
    - **A failure.** A failure after the precondition passed (an I/O failure, a manifest the stamp refuses), never
      what the pack holds, leaves the pack as it was when the process survives the
      failure, and standard error says so (`it is unchanged`). When the pack was moved aside and could not be put back,
      it says where the pack as it was is (`.<pack>.aside-<uuid>`) and prints each suppressed failure. A shutdown hook
      deletes the copy and the sibling on `SIGINT` or `SIGTERM`, and waits for a swap under way. A process killed
      outright can leave a hidden sibling: `.<pack>.remediate-<uuid>` is a copy and can be deleted, and
      `.<pack>.aside-<uuid>` holds the pack as it was; a `pack-remediation-*` directory can be left in the temporary
      directory.

### #154 delivered whole

12. **All of #154 lands in this pull request** (R-p, R-q, R-r). The `anti_pattern` kind gets its file form,
    `anti_patterns/<id>.anti-pattern.yaml` (ADR-0052 decision 8, as amended by ADR-0053 decision 30), closed, with
    `schema_version`, `id` (the file stem), `title`, `description`, `severity` (`smell`, `severe` or `breaking`, no
    default) and `bad_example` required and `good_example` optional (WP10). A graph node of the same URN in the same
    pack declares the anti-pattern and is a `rejects` edge's endpoint; the file holds it. A graph node with no file in
    its pack is refused `drg_schema_error` at the node, `<document>#/nodes/<n>` on the wire (main's in-file pointers,
    `a18fb90f4`), the detail naming the node, on every surface (ADR-0052 decision 14; WP11). An anti-pattern file that is there but refused for a fault of its own
    is that one fault: its node gets no second one. The procedure
    and styleguide shapes drop `anti_patterns` outright, so a file that still carries the key is refused
    `artifact_schema_error`, unknown key (ADR-0052 decision 16; WP12). The example packs carry no inline anti-pattern and
    no file-less node: their 18 inline entries are files of `built-in`, each with a node and a `rejects` edge.
13. **No code is minted for #154.** Each refusal is an existing answer: a schema breach is the closed schema's
    `artifact_schema_error` at the file and the breaching pointer; a misplaced file or a stem mismatch is the existing
    stem and placement rule; a graph node a pack may not declare is already `drg_schema_error` at the node
    (`<document>#/nodes/<n>`), as for a guard, lifecycle or routing-catalog node.
14. **What stays for spec-kitty-open-packs.** The curation of its inline entries and its file-less nodes (ADR-0052
    decision 15) is open-packs' work. Until it is done, its styleguides and procedures with inline entries, and its
    graph documents with file-less nodes, are refused at its repin.

### The rest of the mission

15. **One closure fault type** (#614, slice 1 of #636; R-o, R-x). `CompositionFault` is deleted, and one `ClosureFault.of(...)`
    factory builds every closure fault. `Reason` gains an `Origin`: an opaque element identifier owned by the kernel
    (ADR-0082 decision 4), never a copy of `Reason.rule`; `Origin.NONE` is the origin of a reason about no element.
    `ClosureVerdict` stays a fault list with a derived `Rationale` by ruling R-x, because a reason has no slot for the
    packs and violations a refusal names; #636 slice 7 removes it. No wire code, status, detail or order changed.
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
20. **The case-only answer, in registration's scope** (R-y, R-z). pack-doctor's `CLOSURE` answers a mention that names
    an artifact only by letter case as registration's phase 6 does: `pack_reference_artifact_missing`, naming the
    mentioning pack then the holder, with the resolver's case-only detail and no violation of its own. A mention is a
    preset item or a policy lock (a URN key of the pack's mutability policy file). One body, `CaseOnlyRules`
    (`closure.case-only`), holds it: `mentionsOf` lists each pack's preset items, then its locks in URN order, and
    `matches` runs `caseOnlyMatches` over one composed closure, every member's mentions against every member's holdings,
    the holder the first in merge order. Registration's phase 6 calls `matches` over the closure it composes.
    pack-doctor calls it over the closure rooted at each pack of the set, in configured-id order, after the preset
    items; a fault two roots find is reported once, and a root whose closure reaches a pack outside the set is not
    judged. So a dependency's lock or preset item that a dependent holds only by case is refused by both surfaces. Only
    the case-only answer takes this scope: `preset_item_not_held` and `preset_item_undeclared` keep the own closure of
    decision 1. A mention no pack of the closure holds, in any case, is no fault. Each pack of a closure fault is named
    once, as registration names it. Registration's own behavior is unchanged, but for the order of several case-only
    locks of one pack, now URN order. Registration also composes the root set of every stored activation, so it refuses
    a case-only mention between sibling packs that only an activation lists together; pack-doctor, offline and blind to
    activations, passes that shape, which only registration judges.

### The storage edge

21. **A special file is refused** (R-ad). A pack holds only regular files and directories. An entry that is neither,
    and is not a link (a FIFO, a socket, a device), is never opened: the walk lists it apart, and a storage rule,
    `SpecialFileRules`, refuses each one as `pack_entry_linked` refuses a link (D1, D6 of mission
    `pack-check-cli-01M7SASC`), with the violation `unreadable` at its path and the detail `<file>: is not a regular
    file or a directory; a pack holds only regular files and directories`. No code is added: `unreadable` already
    answers something at the manifest's path that is not a regular file. At its path only that refusal is stated, and
    a mission type that names it as a step's prompt or guidelines is not refused beside it (Tier R review N3). So
    registration, `pack-doctor validate` and `remediate`'s precondition refuse it alike (exit `1`), the store half
    records it so the composing read of a configured pack refuses the pack (`pack_unreadable`, naming the file), and a
    `pack.yaml` that is a special file is refused naming it. The copies of `remediate` and of a registration's commit
    still refuse such an entry, as a second line of defense.
22. **A YAML document has a size bound.** A pack's YAML file, or a Markdown file's front matter, of more than
    1 048 576 code points (`Yaml.MAX_CODE_POINTS`) is refused before it is parsed, with the detail `the document is too
    large`, as a document past the parser's alias or nesting budget is refused (`ParsedYaml`): `unreadable` at an
    artifact file, `pack_unreadable` at `pack.yaml`. It holds at registration and in `pack-doctor` alike.
    - **The expanded size** (Tier R review B3). The strings a loaded document holds, keys and values, a shared
      (aliased) one counted at each reach, may hold at most the same 1 048 576 code points; past it the document is
      refused with the same detail. The alias budget counts only collection aliases and the node budget only nodes,
      so one long scalar aliased thousands of times passed both, and every serialization of it (the canonical writer's)
      ran out of memory. The bound is the cap itself, not a multiple of it: a string is never longer than the text that
      writes it, so a document without aliases is inside the bound whenever its text is inside the cap, and the bound
      refuses only an expansion. It is counted in one place, `ParsedYaml`'s guard, which every read of pack content
      goes through: a registration, `pack-doctor validate`, the round trip's loads and `remediate`'s precondition.
    - **Numbers** (Tier R review B3′). A number's digits count in the same budget at each reach, by a lower bound from
      its bit length, so no reach renders it. And a number scalar, an explicit `!!int` or `!!float` or a plain scalar
      the loading schema reads as one, whose text holds more than 1 000 code points (`ParsedYaml.MAX_NUMBER_TEXT`) is
      refused with the same detail on the parser's events, before the loader converts it. The loader converts digits
      in time quadratic in their count (500 000 digits in about 4 s), and each reach of such a number cost about 1 s
      more downstream, so a 500 000-digit integer reached 30 000 times ran `validate` for hours. A pack's numbers are a
      few digits each; 1 000 digits convert in microseconds. The same digits quoted are a string, bounded by the cap.
      A boolean, a null and a non-finite float carry no text of their own length, and the node budget bounds them.
    - **A float's plain form** (Tier R review B3″). A float's size lives in its exponent too: `1e-999999999` is 12
      characters and a billion digits in the plain notation the canonical writer and the wire form use. On the
      parser's events, a float scalar's plain-form length is worked out from its mantissa and exponent text, and past
      `MAX_NUMBER_TEXT` the document is refused with the same detail, before the loader converts it or anything
      renders it. The length is exactly that of `BigDecimal.toPlainString()`, the text the canonical writer renders
      (Tier R review F1): `precision - scale` for a scale of 0 or less, `precision + 1` for a scale below the
      precision, `scale + 2` (`0.`, then zeros, then the digits) for a scale at or past it, plus one for a negative
      value. So `1e-998` (1 000) is read and its canonical form reads back, and `1e-999` (1 001) is refused. The
      budget counts a decimal by the same length at each reach. A float past the double range stays a non-finite value with no plain
      form and is not judged. Routing catalogs reach the routing arithmetic only through this reader; the routing API
      is read-only.
23. **The round trip reads through the same guards.** Every load the whole-pack round trip makes, of the source and of
    what it wrote, goes through `ParsedYaml`'s guarded parse (aliases, nesting, size). A side the guard refuses is
    answered as a refusal of that file (`the source file does not parse: ...`, `read-back: ...`), never an internal
    error or a crash, so `validate --round-trip` and `remediate` answer such a file as a problem (`1`).
    - **The canonical form is judged by the same cap** (Tier R review B4). A source inside the cap can grow past it in
      canonical form (a tab of a plain scalar is written `\t` in a double-quoted one). The round trip measures every
      YAML text it writes before it writes it (an artefact's, a preset's, the policy's, the descriptor's, a graph
      shard's or the fragment's, and the manifest's; Tier R review N5); past the cap, the text is not written and its
      source file is refused, `not written: its canonical form holds <n> code points, past the cap of 1048576: the document is too
      large`: one `round_trip_red` at the file, and no unreadable copy left `written but unpaired`. `remediate`
      answers the pack `refused` naming the file (decision 11), and exits `1` with the full report. The cap is judged
      where the canonical form is made, not at a read: a registration stores the source text, which every reader
      judges, and the canonical form exists only where the round trip writes it. Pack content never makes either
      command exit `2`. A service write cannot store such a text either: the commit store re-reads its staged copy
      through the same guard before the swap (`PackCommitStore.requireNoNewViolation`), so the write is refused
      `validation_failed` (422).
<!-- markdownlint-enable MD029 -->

## The rulings, verbatim

<!-- The operator's words are quoted as written, spelling included (R-w). -->
<!-- cspell:ignore untill allign behaviour -->

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
| R-w | `01M812KK99P3T1FQ7ZBXT4Z3T1` | "No release until this mission lands"; operator words: "yes, no release yet, untill the charter validation module and cli are finalized, so open packs can allign properly."; rationale: "No service-v\* release until WP01-WP09 land; open-packs aligns once against that release. WP09 draft (a) widened to the full alignment specification." | Decision 19 |
| R-x | `01M83MFVJZX16G6NBM4YPB9XYG` | "Defer to #636 slice 7 (Recommended)"; rationale: "The ClosureVerdict clause of FR-001 (becomes a view over a Rationale) is deferred to #636 slice 7, which removes the type once the ledger carries what faults hold. FR-001's wording is aligned; its acceptance verdict is recorded deferred with follow-up #636; the PR writes Refs #636 for it. No code change." | Decision 15 |
| R-y | `01M83MFXWH2NZQ8CJWFW1JTPY6` | "Fold into this PR"; rationale: "pack-doctor's CLOSURE gains the case-only check for policy-lock URNs through the domain factory, so validate answers a case-only policy lock as registration's phase 6 does (pack_reference_artifact_missing, no violation of its own), with a FullPassParityTest row. Added as WP13." | Decision 20; its scope widened by R-z |
| R-z | `01M84SG9JY2B1VX25M9CG9ZDKV` | "Registration's composed scope (Recommended)"; rationale: "pack-doctor's CLOSURE judges the case-only answer as registration's phase 6 does: per root, over the rooted composed closure, every member's mentions (preset items and policy locks) against every member's holdings. Preset-item and lock faults keep R-k's own closure; only the case-only answer widens. Registration is unchanged. Parity rows for cross-pack shapes." | Decision 20 |
| R-aa | `01M84SGF4G2KHDJYK0209M3TMQ` | "Only composition faults (Recommended)"; rationale: "REFERENCES runs whenever no own closure fails to compose (no CompositionRules fault); preset-item, contest and case-only faults no longer hide reference faults. ADR-0083 decision 8 and the guide are updated." | Decision 8 |
| R-ab | `01M84SGMXPNKF4Y8H2TJF1YJE8` | "Fix now (Recommended)"; rationale: "One PackRulesets entry owns the ordered list (delegations, references, enhancement targets) and the stop-at-first rule; registration's composing judge and pack-validation both call it; ClosureWalkPhase is renamed or removed (R-u reserved the walk name for #636). Registration's behaviour is unchanged." | Decisions 4 and 8 |
| R-ac | `01M84SGTK9J0BMS9GW5QNGMTA8` | "Reversible escape (Recommended)"; rationale: "The XML mapping escapes characters XML 1.0 cannot hold in a defined, reversible way, stated in PackDoctorReport.yaml's mapping and inverted by XmlReport.read, so pack content never makes the exit 2. Contract test with a control character." | Decision 10 |
| R-ae | `01M86V9S1WNSB5A58PZG8PMJY5` | "(a) Per root over the composed closure, every pack in it, as registration does"; rationale, operator words "1": "validate --full judges REFERENCES per root over the composed closure, every pack in it, through PackRulesets.references(rooted.order(), rooted.order(), factsOf), faults found from several roots reported once, as CaseOnlyRules does (R-z precedent). Registration is unchanged. ReferencesPhaseTest's own-closure pin flips; a FullPassParityTest row pins the dependency's bare reference only its dependent holds (pack_reference_unqualified on both surfaces)." | Decision 8 |
| R-ad | `01M852YMVX6134N2SAT259FG5Z` | "Refuse it as a pack fault (Recommended)"; rationale: "A pack entry that is neither a regular file nor a directory is refused by a storage rule, as pack_entry_linked refuses a link, on validate, remediate and registration alike; exit 1 with the file named. Rule, tests and a change fragment." | Decision 21 |

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
- R-z governs over R-y's scope, the own closure: the case-only answer is judged over each root's composed closure, and
  R-y's `PolicyLockRules` is not kept (decision 20).
- R-aa governs over R-h's and R-s's gate as decision 8 first read it ("only when `CLOSURE` ran and found no fault"):
  only a composition fault gates `REFERENCES`.
- R-ae governs over R-s's scope, "run per own closure": `REFERENCES` is judged per root over the composed closure,
  every pack in it (decision 8).

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
  and any preset item or manifest that breaks the new rules. `remediate` restamps manifests (and, since 2026-11-25, writes quoted numbers plain, #651); the rest is curation.
- The `PackDoctorReport` on standard output gains three keys; a strict reader of the old shape must accept them.
- `--full` is narrower than its first ruling (R-h): a pack it passes can still be refused at registration for a
  `specializes_from` cycle, a relation, an edge-index refusal or its own-closure walk.

### Risks

- **Order of findings.** The preset-item phase runs after the compose in pack-doctor and after the own-closure walk in
  registration. A pack with both kinds of fault can lead with a different first code on each surface. Mitigation: the
  parity table (`FullPassParityTest`) pins each planted fault's code on both surfaces.
- **Two scopes for one preset item.** A preset item's own faults are judged over the declaring pack's own closure
  (decision 1), its case-only answer over each root's composed closure (decision 20, R-z). A dependency's item that a
  dependent holds only by case is refused when the set holds the dependent, and passes when the dependency is checked
  alone. Mitigation: the parity table (`FullPassParityTest`) pins the cross-pack shapes on both surfaces, packs and
  detail included, and the guide states both scopes.
- **A `REFERENCES` fault beside a closure fault.** Since R-aa, a contest, a preset-item or a case-only fault no longer
  hides a reference fault, so `validate --full` can report more problems at once than registration, which stops at its
  first failing phase. Since R-ae, `REFERENCES` itself is judged as registration judges it, per root over the composed
  closure: within it, `--full` reports neither more nor less than registering each root would. A qualified reference
  into a pack outside the referrer's `depends_on` but inside a root's closure is one problem per root, each with that
  root's detail, which can read as a duplicate. Packs that only an activation lists together stay registration's
  alone. The codes of each fault are registration's.
- **A special file at a racing path.** The walk lists a special file and the storage rules refuse it; a regular file
  swapped for a FIFO between the walk and a read can still block that read. The copies of `remediate` and of a commit
  refuse a special file, so neither stores one.

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

### Batch 5 (2026-11-23)

- **FR-001's `ClosureVerdict` clause** (R-x): "Do it in this mission" was declined.
- **The case-only policy lock** (R-y): "Follow-up via #636 (Recommended)" was declined.

### Batch 6 (2026-11-23, the pre-merge squad)

- **The case-only scope** (R-z): "Own closure everywhere" and "Accept and document" were declined.
- **The `REFERENCES` gate** (R-aa): "Keep the broad gate" was declined.
- **The one `REFERENCES` body** (R-ab): "Defer to #636" was declined.
- **A character XML 1.0 cannot carry** (R-ac): "Document exit 2" was declined.
- **A special file in a pack** (R-ad): "Keep exit 2 and document" was declined.
- **A new code for a special file** (R-ad, not ruled): a sibling of `pack_entry_linked` was not minted, since
  `unreadable` already answers a manifest that is not a regular file, and git cannot carry a special file into a
  planted fixture that would pin a code of its own.

### Batch 7 (2026-11-24, the Tier R review)

- **The `REFERENCES` scope** (R-ae): "(b) Keep each pack's own closure and document the divergence" was declined.

## References

- Mission `pack-doctor-coverage-01M7YMQK`: `spec.md`, `plan.md` (D1 to D15), `data-model.md`, `decisions/`,
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
