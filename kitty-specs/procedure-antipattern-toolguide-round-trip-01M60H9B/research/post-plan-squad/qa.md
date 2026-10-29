# Squad 3 - QA / test-strategy lens (qa-strategist-quinn)

Profile applied: qa-strategist-quinn, strategy step only. Directives/tactics: QA_001, QA_004, QA_005;
premortem-risk-identification, requirements-validation-workflow, stopping-conditions. Charter context (plan action):
NO_FULL_HEAVY_SUITES_IN_MISSION, DIRECTIVE_010/030/034 etc. Per profile I define strategy, risks and exit criteria; I do
not write test cases. The concrete class names below are proposals for the later design step, not specs of test bodies.
Conceded: no source file was run; evidence is from reading the plan and the existing test code.

Paths are relative to /home/user/spec-kitty-charter. T = service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage.

## A. Blocking / high findings (read these first)

[HIGH] plan.md:121-128 (IC-02, IC-06) - The repo's own vendored packs/internal and packs/qa are not in the plan.
  Evidence: packs/ holds 18 *.procedure.yaml and 6 *.toolguide.yaml (guide_path repo-rooted, e.g.
  packs/internal/.../toolguides/test-quality-triage.toolguide.yaml:5 "packs/internal/toolguides/TEST_QUALITY_TRIAGE.md");
  T/VendoredPacksTest requires packs/internal and packs/qa to equal the pinned open-packs checkout byte for byte except
  LOCAL_DELTA (which lists internal/procedures/semi-auto-delivery.procedure.yaml, a local edit pending open-packs#59).
  WholePackOpenPacksRoundTripTest.packDirectories() also loads repositoryPacks(). After the re-pin the vendored copies
  must be re-vendored (same migrated bytes) or both tests go red; the semi-auto-delivery delta must be migrated by the
  same harness and the LOCAL_DELTA entry either kept (re-derived) or dropped if #59 merged.
  Recommendation: add to IC-06 an explicit "re-vendor packs/internal + packs/qa" step and a verification
  (VendoredPacksTest green, LOCAL_DELTA recomputed). Add the 18+6 files to the migration harness input set.
  Spec SC-002 "41 of 41" counts open-packs only; state the repo-local count separately so a reviewer can tell.

[HIGH] plan.md:94-97, 162-168; T/WholePackCensus.java:52-72; T/DomainRoundTrip.java:73-100,230-260 - The gate has no
  way yet to classify, pair and compare a companion file; the plan says "gate comparison of companions" without the
  mechanism. Evidence: today a guide .md has no files/exclusions/payloads rule, so the census names it other:toolguides
  (WholePackCensus.kindOf fallback) and expected-red.yaml:kinds[other:toolguides] covers it; "remove this mission's own
  guide exclusion" (plan.md:178) refers to something that does not exist in file-kinds.yaml (no guide exclusion there;
  the red is the baseline entry). Also DomainRoundTrip writes artefacts at the pack root and pairs by kind+id, while
  WholePackRoundTripTest.unpaired() fails on any written file no source pairs with - a guide written at a toolguide-relative
  path that differs from the source path is an "unpaired" extra file AND the source guide has no outcome
  ("the round trip gave no outcome for this file" in WholePackExpectation.evaluate:244).
  Recommendation: decide and record before tasks: (1) how a guide gets a gate kind (a files rule `**/toolguides/*.md`
  with schema toolguide-guide + content_form markdown, or a census rule tied to guide_path - the glob form makes an
  orphan .md (spec US1-3) a modelled kind with no outcome, i.e. red, which is the "classified and reported" behaviour
  wanted, but then READMEs under toolguides/ need an exclusion); (2) DomainRoundTrip emits Outcome Written(path of
  the companion) for each guide, and `observe()` branches on contentFormOf(...)==MARKDOWN (see section D); (3) the
  written location equals the source location (relative guide_path preserved), so unpaired() stays empty. Add the
  expected-red.yaml/known-failures.yaml interplay to the plan.

[HIGH] plan.md:216-230 (IC-06), spec FR-006 - Migration output can silently change MEANING while still "round-tripping".
  The harness converts with the service's own writer and the gate then compares the migrated tree to ITSELF. A round
  trip of the migrated tree proves writer/reader consistency, not that the migration preserved the content of the old
  files. Nothing in the plan compares old-pin content to new-pin content. Recommendation: add a migration-fidelity check
  (section C) - a one-off field-and-text mapping verifier whose output is the diff-review artifact, not production code.

[MEDIUM] quickstart.md:12-13; contracts/README.md - Contract scenarios are run by Cucumber in service/deployable
  (service/deployable/src/test/java/.../bdd/CharterContractSuite.java, RegistrationSteps.java), not by `:contracts:check`
  (which only bundles, lints, closed-response-objects and breaking-change checks). The quickstart command would not run
  the new scenarios. Recommendation: quickstart and tasks use `./gradlew :deployable:test --tests '*CharterContractSuite*'`
  (module-scoped, compliant with NO_FULL_HEAVY_SUITES_IN_MISSION) plus `:contracts:check` for lint/breaking. Existing
  scenarios that must be rewritten because `guidePath` leaves the request: contracts/charter/scenarios/toolguides.feature
  (several PUT/POST bodies with "guidePath", and the "guidePath of the response body" assertions) and
  journeys/journey-05-remove-python.feature:24. Java tests that build registrations with guidePath:
  ToolguideRegistrationServiceTest, ToolguideReadServiceTest, ToolguideControllerTest, CompositeComponentResolverTest,
  PackCommitStoreTest, PackAggregateTest, ToolguideEntityTest, ToolguideDsoRoundTripTest, DomainRoundTripTest,
  GeneratedPackTree (all grep hits). Missing from plan's "affected surfaces": these are an update list, and
  GeneratedPackTree (the exhaustive tree used by PackFileSetExactnessTest) must gain procedure and guide-companion files.

[MEDIUM] OpenPacksCorpus.java:24 YAML_FILE_COUNT=516, DIAGNOSTICS table; PackFileKindTest:210 `fileRules().hasSize(16)`;
  PackSchemasTest:61 `bundledKinds().hasSize(16)` (new toolguide-guide schema is a 17th; may count as a kind);
  MissionStepContractCorpusTest etc. These hard-coded counts will move and are not listed in the plan. See section D.

## B. Per-concern test map

Conventions reused from the repo: unit tests next to the class; corpus tests tagged "open-packs" (run only by
:packs:openPacksRoundTrip, ordinary :packs:test does not have the checkout); positive-control pattern from
GlossaryPackAndSkillCorpusTest.aLossyWriterIsCaught and WholePackRoundTripTest.aLossyWriterTurnsTheRealCorpusRed;
"corpus covers the schema" pattern from GlossaryPackAndSkillCorpusTest.theCorpusCoversTheSchemas.

### IC-01 Markdown content form (PR-A1)
CREATE T/MarkdownCodecTest (unit, no checkout) and T/MarkdownDocumentTest.
- Parse positives (fixtures inline text blocks): no frontmatter / frontmatter+H1+preamble+2 H2; H3+ stays in content;
  fence with ``` and ~~~ and 4-backtick; fence indented 0..N spaces.
- Structure non-vacuity (all three requested):
  a) planted change inside a section body (one char; one line moved from section A to B; section title edit; a section
     reordered; a section duplicated) -> compare says unequal at a path naming the section (e.g. sections[1].content).
  b) heading-like line in a fence (`## Not a section` inside ``` at col 0 and indented 4) -> sections.size unchanged and
     the line is in the enclosing section's content. Paired negative: the SAME line outside the fence DOES split. This
     pair prevents a scanner that ignores all `##` or splits on all of them from passing.
  c) blank-line-only changes (extra blank between blocks, trailing blank lines, missing final newline) -> equal; but a
     blank line INSIDE a fenced block or between list items inside content that the model keeps verbatim: decide and
     test (research D6 says "blank lines between blocks ignored"; blank lines inside a section's content are content
     if the model keeps strings exactly - this boundary needs an explicit example both ways; see risk R1).
- Refusals (one test row each, assert refusal text names line/cause): unclosed fence, CR, BOM, H1 not first block,
  indented heading 1-3 spaces, setext underline (=== and ---), frontmatter unclosed, frontmatter not a mapping,
  duplicate H1. Positive control for each: the same text minus the defect parses.
- Write: parse(write(d)) == d for generated docs (ParameterizedTest over edge shapes: empty preamble, empty section
  content, section title with trailing '#', title containing backticks/pipes, content whose first line looks like a
  heading inside fence). write() refusing a model that cannot round-trip (title with newline) is a required negative.
- Idempotence: write(parse(write(d))) byte-equal write(d).
EXTEND T/PackFileKindTest: a files rule naming content_form markdown now loads (replaces the "declared but not built"
  refusal cases around lines 219-310; the existing tests asserting markdown/text are refused must be rewritten, text
  remains refused); a payloads rule naming markdown stays refused; bytes-kind-of-other-kind refusal unchanged.
EXTEND T/MeaningComparatorTest (or a new MarkdownMeaning section): comparator dispatch by form.
ArchUnit: none needed for IC-01 beyond the existing HexagonalArchitectureTest (MarkdownCodec in infra.storage; confirm
  no domain dependency on it).
Concession: corpus coverage for IC-01 alone is the 24 guides + pack.md + 47 prompts at the pin (research D6); a
  T/MarkdownCorpusTest (tag open-packs) that parses ALL 72 files and asserts parse(write(parse(x))) equals parse(x) and,
  for the pinned canonical files, write(parse(x)) is BYTE-equal to x (the research claim "already canonical") is cheap
  and lands in PR-A1 so #195 inherits it. Without it the claim is untested.

### IC-02 Companions carry a content form
CREATE T/MarkdownCompanionTest (or extend T/CompanionFilesTest, which already has the full refusal matrix for text
  companions: missing, escape, absolute, yaml, symlink, binary, case-only rename, shared owner, upsert create/rewrite/
  remove/refuse-taken). Extend rather than fork: re-run the matrix against a Fixture binding that declares a markdown
  companion. New negatives: companion that parses with an unclosed fence is a store violation naming the path and the
  structural cause; companion failing the structure schema is a violation, artifact left out (matches existing
  behaviour for unreadable companions); schema registered for a companion with an unknown keyword is refused by
  PackSchemas (SchemaKeywordCoverageTest).
EXTEND T/CompanionDigestTest / PackFileSetExactnessTest + GeneratedPackTree: a markdown companion's bytes are in the
  source fingerprint; changing one byte of a guide changes the fingerprint (existing "changes iff" oracle).
EXTEND T/KindBindingsTest.boundKindsDeclareTheirCompanions(CompanionFilesTest:262): toolguide declares a markdown
  companion; skill still declares TEXT and its read/write is byte-identical (regression: GlossaryPackAndSkillCorpusTest
  .everySkillRoundTripsWithItsBody unchanged and green - this is the plan's stated risk, plan.md:169).
Positive control (gate): see section D, a lossy markdown writer must turn the guide kind red.

### IC-03 Structured guide
CREATE T/GuideCodecTest (unit): per fixed block parse -> value -> write; order enforced; duplicate block refused;
  missing overview refused; out-of-order fixed block refused; H1 != toolguide title refused; preamble refused;
  freeform sections keep authored order; a freeform section titled like a fixed block with different case ("## when to use")
  - decide: is "## When To Use" a fixed block or a freeform section? The plan says headings are canonical text, so it
  becomes freeform; add the example so it is a recorded decision, not an accident.
  Each refusal needs its positive control (the one-edit-away valid doc).
CREATE T/ToolguideGuideCorpusTest (tag open-packs): all 24 guides at the pin AFTER migration (and the 6 repo-vendored),
  assert 24/24 read, write, re-read equal; exactly-24 count (NFR-001) derived from `find *.toolguide.yaml`, not hard-coded
  only; assert every pinned toolguide's guide_path resolves; coverage assertion in the theCorpusCoversTheSchemas
  style: at least one guide has each optional block, one has a freeform section, one has a heading-like line in a fence
  (15 do per research), one has H3 content - so the non-vacuity examples exist in the real corpus.
EXTEND T/ToolguideDsoRoundTripTest, domain/ToolguideEntityTest, PackAggregateTest: guide value in the aggregate.
EXTEND T/DomainRoundTripTest: toolguide with guide round-trips; orphan .md under toolguides/ reported (US1-3);
  missing guide -> read refusal naming toolguide and guide (US1-2); two toolguides, one guide -> refusal naming both
  (edge case); guide_path leaving the pack / case-only collision refused (ADR-0039 matrix; reuse CompanionFilesTest).
Contract/schema: SchemaKeywordCoverageTest picks up toolguide-guide.schema.json; add a schema test that the schema
  rejects a model with an extra key and one with empty overview.

### IC-04 Sanitized procedure
CREATE T/ProcedureYamlTest (unit, in the style of SkillYamlTest/ToolguideDsoRoundTripTest): sanitized fixture round
  trip; minimal fixture (required fields only, absent lists normalize); full fixture (every optional field);
  unknown-field refusal per RETIRED name as separate rows with a positive control each: `name`, `purpose`, `actor`,
  anti-pattern `name`/`description`, top-level unknown, step-level unknown, reference-level unknown; step naming an
  agent profile id vs role (ADR-0032: how is "no profile id accepted" tested? `role` is an open token per data-model,
  so it cannot be refused by schema - see risk R5; either test that it is kept verbatim and say so, or add the
  closed-role check). schema_version must be string "1.0" (number 1.0 refused - mirrors the comparator rule that '1.0'
  is not 1.0); id != file stem refused; blank step title refused; empty steps refused (min 1).
  Anti-pattern: title only; with content; with bad_example/good_example; with multi-line content; ordering preserved.
CREATE T/ProcedureCorpusTest (tag open-packs): every *.procedure.yaml in the three packs (41) and the repo packs (18):
  read, domain, write, re-read equal; count derived from the filesystem, asserted equal to 41/18 (so a moved pin fails
  loudly); each either round-trips or has a known-failure entry (NFR-001). Coverage assertions: at least one with
  references, one with anti_patterns with examples, one with role on steps, one with on_success/on_failure.
EXTEND KindBindingsTest (append procedure; eachKindIsBoundOnce, everyBoundKindHasASchema, entityTypesMatchTheKinds,
  theDefaultFileNameClassifiesBackToTheKind), PackFileSetExactnessTest via GeneratedPackTree (procedure planted),
  DomainRoundTripTest, PackSchemasTest.
FR-008 (references not minted) - non-vacuity: see section B2.
FR-003 styleguide side: EXTEND the existing styleguide round trip with an anti-pattern-with-examples fixture; today's
  coverage of styleguide anti_patterns in the built-in pack is already green (check GlossaryPackAndSkill/DoctrineRoundTripTest);
  add a coverage assertion that the pinned styleguides contain at least one entry with and one without examples so SC-004
  ("every inline entry survives") is a counted claim: count entries on disk, count entries after round trip, equal.
FR-007: no new test needed - expected-red.yaml keeps the anti_pattern.graph.yaml entry; the gate's STALE/UNEXPECTEDLY_GREEN
  logic already guards it. Add one assertion in a procedure test that standalone anti-pattern files are NOT created
  (ADR-0037 decision 4): after a procedure with anti_patterns is saved, the written tree contains only the procedure file.

### B2. Non-vacuity of "references kept, not minted"
- Fixture: a procedure with references [{type: directive, id: DIRECTIVE_001}, {type: tactic, id: x}] where those
  targets EXIST in the pack. Assert (a) the references appear on the domain Procedure and survive write/read;
  (b) the pack's relationship set derived by ArtifactEdges (find the API via `codegraph explore "ArtifactEdges mint
  SUGGESTS"`) contains NO edge whose source is the procedure's URN.
- Positive control (essential): the SAME fixture shaped as a TACTIC or STYLEGUIDE with equivalent references MUST mint
  SUGGESTS edges. This proves the harness can see minting and that the procedure exclusion is the cause, not an empty
  resolver or unresolvable targets. Also a mutation-style check: temporarily (in the test) pass the procedure through the
  tactic code path / remove the exclusion - the test must fail. Without the control, "no edges" is also what you get
  from a broken edge builder.
- Pin-level: on the built-in pack, the edge set of procedure-owned URNs from ArtifactEdges is empty while
  procedure.graph.yaml still carries the `requires` edges unchanged (gate green for graph-shard kind).
- Also assert the exclusion is explicit (a named rule), not "procedure has no case": ArchUnit/unit test that
  ArtifactEdges handles every ArtifactKind exhaustively (switch with no default) so a future kind cannot silently mint.

### IC-05 Guide over HTTP
CREATE T/.../application/ToolguideRegistrationServiceTest (EXTEND, exists): exactly-one-of guide/guideUri (both ->
  refusal, neither -> refusal, empty string guide -> refusal); guide content stored as companion at <id>.md and read back
  equal (US4-1); structural refusal names the problem (no overview; US4-4); nothing stored on any refusal
  (assert the pack is unchanged, ETag unchanged - NFR-002 pattern in toolguides.feature); id from path vs body.
CREATE .../infra/fetch/HttpsGuideFetcherTest (unit with the transport injected; no network):
  Design requirement (plan says "injectable resolver" - make it two seams): `HostResolver` (name -> InetAddress[]) and the
  HTTP transport (connect to the CHECKED address). Without both seams the redirect and pinning checks cannot be tested.
  Tests use a JDK com.sun.net.httpserver.HttpServer on 127.0.0.1 AND a fake resolver, because the production guard
  refuses 127.0.0.1; so the guard needs a test-only seam that is NOT a "allow loopback" production flag (ArchUnit or
  visibility test: constructor with the seam is package-private; no property turns the guard off).
  Matrix, each with a positive control (same request to an allowed public-looking address succeeds against the stub):
   - scheme: http:// refused; https:// accepted (stub speaks TLS? A TLS stub needs a self-signed cert; prefer testing
     the scheme check at the URI-validation layer and the fetch loop against a transport fake that records the
     connect address - say so, do not spin TLS in unit tests unless the transport is replaced);
   - allowlist: host not on list refused; empty allowlist refuses everything (default); wildcard/suffix tricks
     (evil-example.com vs example.com, example.com.evil.net, userinfo trick https://example.com@evil.net, uppercase,
     trailing dot, IDN/punycode, port); 
   - address class matrix, each refused: 127.0.0.1, ::1, 10/8, 172.16/12, 192.168/16, 169.254/16 (incl. 169.254.169.254
     metadata), fe80::/10, fc00::/7, 0.0.0.0, ::, IPv4-mapped IPv6 (::ffff:127.0.0.1, ::ffff:10.0.0.1), 100.64/10 CGNAT,
     multicast, broadcast; DNS name resolving to a mix (one public, one private) -> refused (all-or-nothing);
   - after redirect: allowlisted host A answers 302 -> host B whose resolver answer is private -> refused with the
     redirect cause; 302 -> http:// refused; 302 -> non-allowlisted refused; 4th redirect refused (3 ok);
     redirect loop; relative Location; Location with credentials; 
   - DNS rebinding: resolver returns public on first call and private on second; assert the connection uses the address
     validated on the first (transport fake records the connect IP) and the second lookup is never consulted;
   - limits: body of exactly 1 MiB accepted, 1 MiB + 1 refused, chunked/no Content-Length streamed past the cap aborts
     (assert bytes read <= cap+buffer), declared Content-Length over cap refused before reading; slow-loris stub that
     trickles bytes: total 10 s deadline enforced - inject a Clock/deadline or use a small configured timeout in the test
     (production default stays 10 s; test with 200 ms) so the suite stays fast; response with non-2xx; non-UTF-8 body;
     content-type ignored or enforced (decide).
   - no credentials: request carries no Authorization, Cookie, or the service's own auth headers; stub echoes headers and
     the test asserts the absence; Set-Cookie from the stub is not replayed on the redirect.
   - typed refusals: each cause maps to a distinct refusal code/text naming the cause (NFR-004). If new codes are minted,
     service/deployable ReservedRefusalCodesTest must be extended (it exists; grep hit) and CHANGELOG lists them.
ArchUnit (EXTEND service/deployable/.../HexagonalArchitectureTest): packs.domain and packs.application must not depend on
  java.net.http / java.net.. (add a rule with the repo's badfixture known-bad-control pattern: a class in a badfixture
  package that uses HttpClient must make the rule fail); only packs.infra.fetch may. GuideFetcher port lives in
  application; adapter in infra.fetch; the controller (infra.api) must not call the adapter directly.
Contract scenarios (EXTEND contracts/charter/scenarios/toolguides.feature; run by CharterContractSuite):
   - PUT with guide content -> 201, GET returns structured guide / path <id>.md (replace the guidePath assertions);
   - PUT with neither / both -> 4xx typed refusal; PUT with guide lacking overview -> refusal naming structure, pack unchanged;
   - guideUri scenarios: the BDD harness cannot reach the network, so deployable test config must provide a stub
     GuideFetcher bean (test @Configuration) keyed by URI: allowlisted -> content; private-address URI -> refusal. The
     SSRF logic itself is proven by the unit matrix above; the scenario proves wiring and the refusal envelope only.
     State this split in the scenario header so nobody reads the scenario as SSRF proof.
   - a security.feature scenario for the refused-URI envelope (no internal detail leaked: the problem body must not echo
     the resolved private IP or response body).
   - the `### Breaking` CHANGELOG entry and contracts:check breaking-change check: confirm how the baseline-release gate
     treats a 0.x break without a released tag (plan asserts "no released tag"); add a planted-pair expectation if the
     checker requires one (contracts/compat/fixtures).

### IC-06 Migration harness (throwaway)
See section C.

### IC-07 Docs
No code tests. Gate: python3 scripts/docs/check_docs.py all (plan has it). Add: a check that docs/adr/README.md index and
  glossary append-only (C-002) - a diff-based review item, not a test. Concession: outside my lens beyond that.

## C. Migration harness (IC-06): throwaway-ness and verification

Throwaway proof (all cheap, no new framework):
1. Location: test source set only (service/packs/src/test/java/.../migration/ProcedureAndGuideMigration.java), plus a
   `@Tag("migration")` JUnit test or a main() under src/test. Never src/main. Enforce: (a) ArchUnit rule "no class in
   src/main refers to the migration package" is vacuous because src/main cannot see test classes - instead enforce
   by CONTENT: a repo check (grep-style unit test or a Gradle task wired into check) that fails if any file under
   service/*/src/main contains a retired name literal (`"actor"`, `"purpose"` as a procedure key) or a class named
   *Migration*; this doubles as the SC-005 test ("zero tolerance code paths": no retired field name accepted anywhere in
   the procedure reader/writer/schema) - SC-005 is otherwise unproven by any listed test. Add that SC-005 test explicitly:
   a unit test that the schema `properties`/`required` of procedure.schema.json contain none of the retired names and that
   feeding each retired name to the reader is refused (IC-04 rows) - together they cover reader, writer (cannot emit),
   schema.
2. Tag exclusion: the migration test must be excluded from the default :packs:test and from verifyAll (tag `migration`
   not run by test, openPacksRoundTrip or check). Otherwise it runs against a checkout and may WRITE to a sibling repo
   during a gate run. It must refuse to run unless an explicit -PmigrateTo=<dir> is given and the dir is not the pinned
   checkout (OpenPacksCorpus.assertPinned requires a CLEAN pinned checkout - the harness must not write into it). Test
   that guard once while the harness exists.
3. Deletion is a PR-A2 exit criterion: a tasks.md checklist item "harness deleted" verified at review by `git ls-files |
   grep -i migrat` returning nothing (or the harness moved to the review artifact). The plan allows "or kept out of the
   main source set and excluded from the build" (plan.md:223) - tighten to "deleted before PR-A2 is reviewed"; the
   second option leaves untested code that rots and can be mistaken for tolerance logic (C-005).
4. Evidence preserved: the harness output (mapping table, per-file diff) is attached to the open-packs PR and to the
   mission under research/ (that is the artifact a future reader needs once the code is gone).

Verification of output BEFORE it lands in open-packs (all run against the working copy, in this order):
 a. Strict read: every migrated procedure/toolguide/guide reads with zero violations (the production reader; unknown
    fields refused) - NFR-002 precondition.
 b. Round trip of the migrated tree through DomainRoundTrip + MeaningComparator + the markdown comparator: 100% green,
    with the positive control (lossy writer) run on the migrated tree too, so green is not a no-op.
 c. Idempotence: running the harness a second time over its own output changes nothing (byte-identical tree). Catches
    non-deterministic ordering, timestamp, hash-ordered map output.
 d. Migration fidelity (the missing check, see A-3): for each old file, a verifier maps old fields to new fields by the
    declared mapping (name->title, purpose->description, actor->role, anti-pattern name->title, description->content,
    guide heading->block) and asserts every old scalar string appears byte-identically in exactly one new location; any
    old key with no mapping row -> hard fail (not warn). For guides: the multiset of non-blank content lines of old and
    new documents is equal except for the lines the mapping table says are changed (H1 rewrite, heading renames);
    emit the exception list = the human review list in the open-packs PR.
 e. Diff review artifact: git diff --stat plus a per-class-of-change digest ("N files: actor->role"; "24 guides:
    H1 set from title"; "K guides: sections reordered to fixed order - listed") committed beside the open-packs PR
    description; one open-packs issue per class (FR-006). The artifact enumerates content decisions the converter made
    (heading-to-block mapping, overview/summary overlap) with file+line so a reviewer samples them.
 f. Counts: 41 procedure files in, 41 out; 24 guides in, 24 out; 0 files added or dropped except the declared moves;
    toolguides' guide_path rewritten (repo-rooted -> relative) exactly for the toolguides whose old value was not already
    relative; sorted listing before/after.
 g. Re-vendor: copy migrated internal/qa into packs/; run VendoredPacksTest (must be green with LOCAL_DELTA recomputed).

## D. Gate mechanics

What must change (test side):
1. WholePackCensus: today contentFormOf returns YAML for any unmapped file and BYTES for payloads. Add MARKDOWN: a file
   classified by a files rule with content_form markdown (the guide rule) returns ContentForm.MARKDOWN. PackFileKind.Mapped
   already carries contentForm(); the test seam only needs the new case to flow. Without it the guide .md would be parsed
   as YAML by MeaningComparator.compareTexts (Yaml.load of Markdown: `#` lines become comments - the plan notes this
   hazard at plan.md:168 - and a document of plain text loads as one scalar, so a TOTALLY DIFFERENT guide could compare
   equal when both load to the same first scalar or null). This is the most dangerous silent-pass failure in the mission.
2. MeaningComparator: add `compareMarkdown(kind, sourceText, writtenText)` / differencesOfMarkdown(...) returning
   Difference paths like `frontmatter.title`, `title`, `preamble`, `sections[2].title`, `sections[2].content`, plus
   `sections[3]` present/absent. WholePackRoundTripTest.observe() gets a third branch next to BYTES:
   `if form == MARKDOWN -> comparator.differencesOfMarkdown(...)`. The known-failure `at:` machinery (path list joined by
   " ; ") then works unchanged. MeaningComparator's javadoc ("Both texts are parsed as YAML") must be updated.
   Make the dispatch exhaustive: a `switch` over ContentForm with no default so TEXT/future forms break compilation.
3. Positive controls to ADD to WholePackRoundTripTest (mirroring aLossyByteWriterTurnsEveryAssetFileAndTemplateRed):
   `aLossyMarkdownWriterTurnsEveryGuideRed`: through DomainRoundTrip's beforeSave hook, drop the last char of one fixed
   block, drop a freeform section, swap two sections, replace a fence-protected heading-like line; expect each guide that
   has the planted feature red at the right sections[i] path; `theMarkdownBranchComparesByStructure`: same fixtures as IC-01
   section tests but through observe(). And a negative: the faithful run yields green for the same guides.
   Also a "comparator chosen by form" test: a file with Markdown content and a `.yaml`-looking sibling cannot be compared
   as YAML (plant a guide whose first line is `key: value` - YAML load would produce a map; markdown model puts it in
   the preamble/section - and assert the markdown branch is used).
4. Exactness of the kind set: WholePackRoundTripTest REQUIRED_KINDS adds "procedure" and the guide kind;
   modelledKinds must equal bound (test asserts isEqualTo(bound)); adding a binding already flows via KindBindings.all(),
   but the guide kind is NOT a binding (it is a companion), so DomainRoundTrip.MODELLED must add it by hand and the
   `bound` set in the test as well. Make this explicit in the plan, otherwise the assertion at line 109-112 fails.

Re-pin sequence (do it in this order; each step must leave a green module-scoped check):
 1. PR-A1 (markdown form) lands against the OLD pin. No pin move. Gate unchanged; only unit tests + Markdown corpus test
    over the old pin's 72 files (proves the form against reality early).
 2. PR-A2 code, against the OLD pin, with feature tests on fixtures only. The corpus/gate tests for procedure/guides are
    red against the old pin by design (old shapes are refused). Keep them failing-expectedly or gated so the branch is
    reviewable: do NOT add known-failure entries for 41 procedures + 24 guides to get green (D7 rejects it; also the
    known-failure list can only pin modelled kinds, and a refused toolguide leaves its guide unowned). Practical:
    openPacksRoundTrip is red on the branch until step 4; say so in the PR and use module-scoped :packs:test (fixtures)
    meanwhile (compliant with NO_FULL_HEAVY_SUITES_IN_MISSION).
 3. Run the harness (throwaway) -> open-packs PR (+ issues) -> merge -> obtain merged SHA.
 4. In one commit: gradle.properties openPacksRef -> merged SHA; expected-red.yaml remove `kind: procedure` and
    `other:toolguides`; known-failures.yaml add residual divergences only (if any), each with an open-packs issue;
    OpenPacksCorpus.YAML_FILE_COUNT, DIAGNOSTICS if changed; re-vendor packs/internal+qa; delete the harness;
    VendoredPacksTest.LOCAL_DELTA recomputed; WholePackOpenPacksRoundTripTest counts re-asserted.
    The gate fails with UNEXPECTEDLY_GREEN / STALE_BASELINE if entries are left, which is the intended forcing function:
    treat the first run after the pin move as the checklist, and require its output in the PR description.
 5. Risk: ordering with sibling #195 (also moving the pin). A second pin move between 3 and 4 invalidates the harness
    output; the harness is idempotent (C-c) so it can be re-run on the new base - require that.
Hard-coded counts that move (grep evidence): OpenPacksCorpus.YAML_FILE_COUNT=516 (a procedure/toolguide rewrite changes no
 file count unless files are added/removed; guides are .md so YAML count unchanged - but if toolguide companions are
 renamed/added the count is stable; verify, do not assume); PackFileKindTest fileRules hasSize(16) -> +1 (guide rule);
 PackSchemasTest bundledKinds hasSize(16) -> +1 or +2; WholePackOpenPacksRoundTripTest: the `26` profiles, Counts(10,10,4,0),
 Counts(6,7,0,2) are asset/template/profile counts and do NOT move with this mission - the plan's "counts move" claim
 (plan.md:225 "WholePackOpenPacksRoundTripTest counts") is likely wrong for this test; what moves is the artifacts total
 and the `unpinned()`/known-failure pins. Recommend adding pinned counts there for procedures (23 built-in) and guides
 (built-in count) in the same style (`countsOf`), so deletion of a file in the migration cannot pass.
 FileSystemPackInspectorTest 217/386 and MissionStepContractCorpusTest 28 are unrelated fixtures (concede: not affected).

Self-expiring known-failure semantics (#247), what to test:
 - Existing machinery (WholePackExpectation.evaluate: UNEXPECTEDLY_GREEN, STALE_KNOWN_FAILURE, ambiguous path in both
   lists) already enforces it; WholePackExpectationTest exists. Add cases ONLY for the new surface: a known-failure entry
   for a guide (kind toolguide-guide, `at:` markdown path like `sections[2].content`) is accepted while red with the exact
   `at`, turns UNEXPECTEDLY_GREEN when the guide is fixed, and is STALE if the file is removed; an entry whose `at` differs
   from the observed markdown paths fails (exact path list is pinned - this is what stops "any red passes").
 - A refused toolguide whose guide is therefore unowned: the guide's outcome is Refused(...) too (not "no outcome"), so a
   known-failure entry on the toolguide alone does not leave `guide.md` red as an unlisted failure: test that case (it is
   the reason D7 gave for not using the list). If the design instead reports the orphan guide separately, one
   known-failure entry must be required per refused toolguide AND per unowned guide - say which.
 - After the migration, assert the known-failures.yaml has no procedure/toolguide entries unless each cites an open
   issue number that exists (format check already exists in WholePackExpectation; keep).

## E. Risks in the plan's test approach and missing test surfaces

R1 [HIGH] Blank-line semantics are under-specified (research D6, data-model "blank lines between blocks ignored"). If
  section content is compared "strings exactly", a blank line inside a section (between paragraphs, in a fence, in a list)
  is content; if the codec trims, it must trim only leading/trailing blank lines of a section. Define and test the
  boundary with a fenced block containing a double blank line (must survive exactly) - otherwise write()
  normalization silently changes meaning, and parse(write(d))==d hides it (the check compares model to model, both
  normalized). Hence the BYTE-level corpus assertion write(parse(x)) == x for the 72 canonical pinned files (IC-01 above)
  is the only thing that detects lossy normalization; make it a PR-A1 exit criterion.
R2 [HIGH] "parse(write(d)) == d checked on write" is a self-referential oracle: it cannot detect a consistent bug in
  both directions (e.g. dropping trailing spaces in both). Pair it with the byte-equal canonical corpus check and with
  independent hand-written expected-text fixtures (golden files in src/test/resources/markdown/*.md with an expected
  model written by hand, not generated by the codec).
R3 [MEDIUM] Frontmatter equality "by YAML meaning" reuses MeaningComparator's empty-equals-absent rule, which is
  ADR-0036 for declared fields. For Markdown frontmatter (#195's pack.md) is `title:` (null) equal to absent? Decide; a
  test either way. Also YAML-in-frontmatter anchors/duplicate keys/ts types (dates become Date in some loaders) need one
  row each; reuse YamlNullFormsTest/ParsedYamlTest patterns.
R4 [MEDIUM] Heading detection edge cases not in the refusal list: `#` inside a block quote or list item at column 0
  (`- ## x` no), `##` with trailing `#`s, `##` without space ("##x" is not a heading), tab after `##`, an H2 inside an HTML
  comment/ <details>, a fence opened with more backticks than the closing one, fence info string containing backticks
  (invalid), fence inside a list item (indented -> "fence at any indentation hides headings" is correct but
  closing-fence rules vary: closing needs >= opening length and no info string), "~~~" fence closed by "```" (not closed).
  Add a table-driven test of ~20 lines from the CommonMark spec as oracle; where the scanner deliberately deviates from
  CommonMark (the plan refuses rather than guesses), the test must assert the refusal, so deviations are visible.
R5 [MEDIUM] FR-001 "no step names an agent profile (ADR-0032)": `role` is an open token, so nothing structural prevents a
  profile id. The acceptance scenario US2-3 ("no step names an agent profile") has no mechanism. Either add a check against
  the pack's profile ids (a pack-level validation with a negative fixture) or amend the scenario to "role is kept verbatim".
R6 [MEDIUM] Corpus tests that read pinned checkouts are tagged open-packs and only run under :packs:openPacksRoundTrip;
  per charter, work packages run module tests. Each WP must name the exact gradle filter; the Quickstart's
  `--tests '*Procedure*' --tests '*Toolguide*'` under :packs:test will NOT run the tagged corpus tests (tag excluded) and
  will pass vacuously on that part. Specify `:packs:openPacksRoundTrip --tests '*ProcedureCorpus*'` for corpus WPs. Also
  note `openPacksRoundTrip` is a dependency of `check` (build.gradle.kts:170): a step-2 branch state is red under check.
R7 [MEDIUM] Hard-coded corpus counts as the only guard of "100% exercised": derive both sides (filesystem count vs
  round-tripped count) and also pin the literal, so a harness that dropped a file passes neither.
R8 [LOW] Windows/CRLF: the pack files are LF-only (research), refusing CR is correct, but git autocrlf in a contributor
  checkout would make the gate red with a CR refusal message that does not mention git; the refusal text should say
  "CR found at line N (check line-ending conversion)". Test the message once.
R9 [LOW] Performance: the 10 s total-timeout test and the 1 MiB streaming test must not make the module test slow;
  inject small values (see IC-05). The generated-tree CPU budget test in PackFileSetExactnessTest
  (assertWithinCpuBudget) gets more files; check the budget still holds, do not raise it silently.
R10 [LOW] Test names in the repo use a DisplayName/behaviour sentence style (see CompanionFilesTest); new tests should
  match so review is uniform. No further comment.

Missing test surfaces (summary): vendored packs re-sync (A-1); census/outcome pairing for companions (A-2); migration
fidelity (A-3); SC-005 zero-tolerance proof; ArchUnit rule for network code (and its known-bad control); contract
scenarios actually run in :deployable, plus rewrite of the existing guidePath scenarios and Java tests; ReservedRefusalCodesTest
if new codes; markdown byte-canonical corpus check; stub GuideFetcher bean for BDD; exhaustive ContentForm switch.

## Exit criteria I would freeze for this mission (QA_004 style)
 E1 PR-A1: MarkdownCodec unit matrix green incl. the three structural non-vacuity pairs; 72/72 pinned Markdown files
    parse and write byte-equal; PackFileKindTest updated; no change to any gate outcome.
 E2 PR-A2: gate run (openPacksRoundTrip, module-scoped) has zero problems at the NEW pin with expected-red lacking
    procedure and other:toolguides and with the anti_pattern.graph.yaml entry intact; positive controls (lossy YAML writer,
    lossy markdown writer, lossy procedure writer) red; VendoredPacksTest green; procedure edge-minting negative+positive
    control green; fetcher matrix green offline; contract suite green in :deployable; ArchUnit rule + bad-fixture green;
    harness absent from the tree; migration verification artifacts attached; verifyAll + docs check once before review.

## Verdict
CONDITIONAL GO for tasks: the plan's test intent is sound and fits the repo's existing gate patterns, but three
unaddressed items would make PR-A2 fail or pass vacuously: (1) packs/internal and packs/qa re-vendoring with
VendoredPacksTest and the 18+6 repo-local files; (2) the undefined mechanism by which a Markdown companion is classified,
paired and compared in the gate (Yaml.load of Markdown could silently pass); (3) no migration-fidelity check, so a
meaning-changing migration would round-trip green. Fix these in plan/tasks, correct the quickstart (corpus tests need
:packs:openPacksRoundTrip; scenarios need :deployable CharterContractSuite), and make the harness deletion a hard exit
criterion.
