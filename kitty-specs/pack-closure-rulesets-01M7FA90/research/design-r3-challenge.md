# R3 design: the challenge pass (2026-11-16)

One adversarial reader checked [design-r3.md](design-r3.md) against the R3 brief's binding rulings, the
[plan-point squad](squad-plan.md), `spec.md`, `plan.md`, ADR-0075, ADR-0062 and the code on this branch (`995e1538c`,
on `main` `97985727a`). The rulings were not relitigated. No Gradle run; every claim below cites a file and line.

**Short answer to the brief's questions:**

- **ADR-0075's premise.** Keeping the rules in `packs.domain` reverses the premise, and the operator should be told
  that plainly (F1).
- **Judging in `packs.application`.** It is safe for every caller of `loadStaged`, but the inspector changes two wire
  answers (F4), and the inspect-and-judge sequence should be one function, not two copies (F8).
- **The guard.** Different codes do not defeat it, because it fires on any fault. The subset test does need unified
  codes (F6).
- **The compose read.** It keeps a second source of facts. Ruling 1 allows that, but NFR-004 has to say so (F7).
- **"Registration token wins".** It is wrong for registration's catch-all answers (F3).
- **Deferring `PackSetCheck`.** This hides that `CLOSURE`'s subject cannot be built without Spring today (F5).
- **#578's mutation figure.** It is unverified (F9).
- **ArchUnit.** One planned rule cannot be enforced as written, and one ruling-1 row breaks the onion rule (F2, F10).

## Findings

### F1. MAJOR: the design reverses ADR-0075's premise and withdraws text of an operator ruling; say so plainly

- **Evidence: what the operator asked for.** The steer that founded ADR-0075 was: "The pack validator is a module the
  service itself uses. It is then repackaged as a standalone CLI" (`docs/adr/0075-…md:31-33`; also its title, line
  2-3, and decision 3, line 199).
- **Evidence: what the design delivers.** No service main code uses `pack-validation`. `deployable` keeps it on the
  test classpath only (`design-r3.md:104-106`; `service/deployable/build.gradle.kts:20-24`). `pack-check` becomes
  `:packs` plus a thin library.
- **Evidence: the ruling text.** The design also withdraws the port from operator ruling 1 *as the spec records it*:
  "runs `PACK` through a port that `packs.domain` declares" (`spec.md:34`; ADR-0075 amendment `:78-80`). FR-006
  (`spec.md:156`), C-003 ("Only the composition root wires the port", `spec.md:182`) and the "Judging port" key entity
  rest on that port.
- **What the design says.** §6 withdraws the ADR's port. It does not say that a ruling-1 sentence, FR-006 and C-003 are
  rewritten. It does not say that ADR-0075's title and description stop being true.
- **Why it matters.** The operator's R3 answer probably covers this. Still, it changes the meaning of a ruling the brief
  calls binding.
- **Fold.** Add one plain line to the operator questions:
  > The service will no longer use `pack-validation`. `pack-check` runs the service's own rules out of `:packs`.
  > Ruling 1's "through a port" goes. FR-006, C-003, the key entity and ADR-0075's title and description are rewritten.

  Get a yes before WP04.

### F2. MAJOR: `PackAssembly` and `PackManifest` cannot live in `packs.domain`

- **Evidence: the assembly.** The assembly consumes infra types only:
  - `FilesystemPackStore.PackRead`, `TemplateBinding`, `KindBindings` and `GuardClosure`
    (`DomainRepositoryService.java:909-950`), all in `packs.infra.storage`;
  - the manifest derivation, which takes and returns `PackManifestDso` (`ManifestMembership.java:58, 86, 131`), also
    `infra.storage`.
- **Evidence: the onion rule.** `ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD` refuses a domain class that names `..infra..`
  (`HexagonalArchitectureTest.java:98-103`).
- **The defect.** The A-B3 row (`design-r3.md:31`) cannot be built as written. Moving the codecs inward would be the
  split that ruling 2 and the 2026-11-13 amendment refused (ADR-0075 `:60-62`).
- **Fold.** Put the Spring-free assembly and manifest derivation in a non-bean class in `packs.infra.storage`.
  `DomainRepositoryService`, `WholePackStore` and `FileSystemPackInspector` all call it.
  `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` tests what a class reaches, not which ring it is in (`:389-394`), so it stays
  green. Correct the table row and ADR text.

### F3. MAJOR: "registration token wins" is wrong where registration's answer is a catch-all or has a different shape

Registration does not always have a fault-specific token. Its answers include several generic forms:

| Registration answer | Where | Validate mode today |
| --- | --- | --- |
| `domain`, for any guard breach | `FilesystemPackStore.java:406`; `DomainRepositoryService.java:920, 939, 948, 1262` | specific codes, such as `artifact_schema_error` and `duplicate_artifact_id` |
| `reference` | `FilesystemPackStore.java:789`; `DomainRepositoryService.java:1249, 1254` | a specific code |
| the `invalid` fallback | `WholePackStore.java:763` | none |
| the aggregate `{pack, validation_failed, message}` (C-3) | squad finding C-3 | `duplicate_artifact_id` |
| the load's refusal at pointer `pack` (not a file), with the refusal code's token, after the graph faults | `WholePackStore.java:737-748` | `pack_unreadable` at `pack.yaml` |
| `schema`, one finding per violation | | one `artifact_schema_error` per file |

- **What adopting registration's tokens would do.** Validate mode would get less specific.
- **`validation_failed`.** A `RefusalCode` would become a fault code inside `PackCodes`.
- **The `pack` pointer.** A `PackVerdict` `Location` would have to carry a pointer that names no file.
- **The `schema` row.** It changes the number of findings, not just the token.
- **The `invalid` fallback.** The squad's WP04 already drops it (`squad-plan.md`, WP04 row). So registration changes
  for at least one fault, and "registration never changes" cannot hold. FR-009's "wire token unchanged"
  (`spec.md:159`) also needs rewording under R2(b).
- **Unverified claim.** "open-packs' expected-code fixtures pin the registration tokens" (`design-r3.md:70`) cannot be
  checked from this repository.
- **Records the design misses.** `docs/conformance/divergences.yaml`, the author guides that name the validate tokens
  (`docs/guides/author-a-procedure.md:74`) and ADR-0040 and ADR-0042.

**Fold.**

- WP06 holds a per-fault table with these columns: fault, registration code and pointer, validate code and file, the
  surviving code, and the surface that breaks.
- The rule: the *specific* code survives. The catch-alls (`domain`, `reference`, `invalid`, `validation_failed` as a
  fault) leave the fault vocabulary.
- The rows where registration changes go under a registration `### Breaking` fragment. Show the table to the operator,
  because it touches open-packs' fixtures.
- The `pack`-pointer shape stays registration-only. It is shaping in `PackRefusals`, not a code.

### F4. MAJOR: judging registration on the inspector changes two wire answers and needs a two-way agreement

- **I/O faults.** `FileSystemPackInspector.inspect` throws `CharterRefusal(PACK_UNREADABLE)` in two cases
  (`FileSystemPackInspector.java:155-157, 912-918`):
  - the root is not a directory;
  - any file read raises an `IOException`.

  Today `loadStaged` folds every load refusal into a 422 `validation_failed`, with a violation at `pack`
  (`WholePackStore.java:737-748`). Run first in `register` and in `PackSourceStagers.requireNamed`
  (`PackSourceStagers.java:72-77`), the inspector lets a bare `pack_unreadable` escape: a status and code change on
  `POST /packs` and on the source jobs.
- **Links.** The inspector tests `Files.isRegularFile` and follows links (`:161`). The store uses `NOFOLLOW_LINKS`
  (`WholePackStore.java:386-387`).
- **The direction the tests miss.** The A-D3 subset test proves that the reader's faults are a subset of `PACK`'s, which
  is the guard's direction. R1(a) needs the other direction too: "registration … gain[s] no new refusals", so
  `PACK(REGISTER)` over the inspector must find nothing that today's reader accepts.
- **What is safe.** `StagedPackViewFactory` and the stored reads (`loadStored`, `salvageStored`) do not call `loadStaged`,
  and are unaffected. The impact check and the gate run on `upload.contents()` (`PackRegistrationService.java:290-292`).
  They are safe only because the guard refuses before them, so the guard is load-bearing, not defensive.
- **Fold.**
  - `PackRefusals` maps an inspector refusal to today's `pack` shape, and a test pins it.
  - Add the reverse subset test: `PACK(REGISTER)` over the inspector is a subset of today's reader faults, over every
    planted, example and scenario pack.
  - Make the golden snapshot (M-2) the equality check of record.
  - Add a symlink row to the A-M1 checklist.

### F5. MAJOR: `CLOSURE`'s subject is not descriptor-only, so deferring `PackSetCheck` hides a second derivation

- **Evidence: the facts it needs.** `PackUnderResolution` carries `changedArtifacts`, `definedArtifacts` and `heldTerms`
  (`PackCompositionResolver.java:68-93`). The offering fills them from the compose reader's `PackCompositionFacts`
  (`OfferingReadService.java:462-465`). `ClosureRoots` uses the descriptor-only constructor, with empty facts
  (`ClosureRoots.java:102`).
- **What the ADR and spec say.** ADR-0075 decision 7 (`:234-242`) and FR-003 (`spec.md:153`) say that `CLOSURE` needs
  "every `pack.yaml`, never the graph", and that it covers sibling and preset conflicts.
- **Why `pack-check` cannot reuse the offering's route.** `DomainRepositoryService` is a `@Service`
  (`DomainRepositoryService.java:112`), so `pack-check` may not reach it (`PACK_VALIDATION_IS_FRAMEWORK_FREE`).
- **The two outcomes.** `pack-check` has two choices:
  - run the conflict checks over empty facts, where they pass silently;
  - derive composition facts from `PackContents`, which is a second derivation that nobody has planned.
- **Where the design stands.** Deferring `PackSetCheck` to #478 (`design-r3.md:36`) is compatible with decision 7 in
  form, because the CLI is #478. But it pushes this decision to a slice that cannot change `packs.domain` cheaply.
- **An inconsistency.** The design keeps a `CLOSURE` *ruleset* with no main caller. That is the same "speculative, only
  tests call it" objection it used against `PackSetCheck`.
- **Fold.**
  - #554 delivers one Spring-free `packs.domain` function, `PackUnderResolution` built from a `ConfiguredPack` and
    `PackContents`.
  - Add a parity test against `compositionFacts` over the example packs.
  - Amend decision 7's wording: "every `pack.yaml`, plus each pack's held artifact ids and terms, from the load; never
    the walk".
  - Either keep the `CLOSURE` ruleset and its function together under that test, or defer both.

### F6. MEDIUM: the fail-closed guard works across different codes, but its answer throws away what it knows

- **How the guard fires.** It fires when the reader recorded *any* fault while `PACK` passed (`design-r3.md:51-52`).
  Code equality plays no part, so different codes cannot defeat it.
- **Where unified codes do matter.** The A-D3 subset test compares fault identity, so it needs the reader to emit
  `PackCodes`. FR-009 and SC-004 remove the string kinds. So the reader stays a judge, with typed codes. That is
  acceptable under P1, but the design should say it.
- **The design's answer.**
  - **It reports the wrong shape.** The guard answers in "today's aggregate shape", but a reader fault that `PACK`
    missed has its own file and code. Today registration answers exactly those, so the aggregate shape would degrade a
    real answer.
  - **It ignores severity.** The reader also carries warnings: the load's `file_not_held` and `pack_level_missing`
    (`WholePackStore.java:800-801`).
- **Fold.**
  - The guard answers the reader's own violations through `PackRefusals`, which is byte-equal to today.
  - The aggregate shape is kept for a `build()` breach only.
  - The guard counts `VIOLATION` severity only.
  - One test per branch.

### F7. MEDIUM: the compose read keeps a second fact source; that is legal under ruling 1, but NFR-004 must say it

- **What stays.** Rules are called today from the inspector, the store's reader and the compose reader
  (`FileSystemPackInspector`, 6 call sites; `FilesystemPackStore`, 7; `DomainRepositoryService`, 11). The compose read
  keeps its reader and inline calls (`design-r3.md:56-58`).
- **The consequence.**
  - Today registration and the compose read share one reader, so they agree by construction.
  - After the switch, registration judges inspector facts and the compose read judges reader facts. They agree only by
    test (F4).
- **NFR-004.** It says "rules read only the pack contents" (`spec.md:173`). That is false as worded for the compose read.
  It is not a re-parse, so P1 holds, but it is two derivations of the same kinds of fact.
- **`COMPOSE_READ`.** It is named a profile, yet it never runs as a ruleset: the calls stay inline, in reader order. It
  is a dead profile.
- **Fold.**
  - Reword NFR-004: each surface's rules read only the facts that surface's load produced, and never parse.
  - Drop `COMPOSE_READ` as a `PACK` profile. Keep it as a declared list of the rules the reader calls, with a test that
    the reader calls exactly that list.

### F8. MEDIUM: one registration judgement, not two copies, and a cheaper test migration

- **Two copies.** Inspect, then `PACK(REGISTER)`, then refuse, then `loadStaged`, then the guard: the design writes
  this into `register` and into `PackSourceStagers` (`design-r3.md:47-52`). Two copies of an ordering is how the two
  pipelines arose in the first place.
- **A slower source registration.** A git or archive source is inspected in full twice and loaded twice, under NFR-009.
- **The test migration.** About 50 `loadStaged` calls in 14 test files assert refusals, 26 of them in
  `WholePackStoreTest`. Moving them onto `PackRegistrationService` means building its locks, views, gate and impact
  check.
- **Not-held files.** `removeNotHeld` still takes the reader's `Contents`. A-D2 said the not-held set comes from
  `PackContents`.
- **Fold.**
  - Add one Spring-free `packs.application` function, `StagedPackJudgement.judge(staged, packId)`, which returns the
    judged `PackContents` and the reader's `Contents`, or refuses.
  - `register` and the stager both call it. The stager passes the result on in `Staged`, so the job does not judge
    twice.
  - The moved tests call the function directly.
  - `removeNotHeld` takes `PackContents.notHeld()`.

### F9. MEDIUM: #578's mutation figure is unverified, and NFR-003's "after the move" no longer has a move

- **The search.** Nothing in the repository records about 350 mutants or 88 % for `pack-validation`.
- **Where the record still says margin 0.**
  - The build file still reads "margin 0 until #554" (`service/pack-validation/build.gradle.kts:51-55`).
  - ADR-0062 still lists margin 0 (`docs/adr/0062-nightly-mutation-floor.md:105, 349-361`).
  - The #555 commits (`1af17980c`, `577dc13fa`, `4f6703212`) and `kitty-ops/research/issue-555-round-trip-into-main-code.md`
    give no figure.
- **What is there.** The module's main code is 1,688 lines in 6 classes, mostly `WholePackExpectation` (761) and
  `MeaningComparator` (456). A few hundred mutants is plausible, but it is **unverified**.
- **What R3-b chooses between.** Under this design #554 adds no `pack-validation` main code. R3-b's option (i) meets
  NFR-003's letter ("has a margin above 0"), but the margin would be #578's.
- **Fold.**
  - Recommend (i) to the operator, and say that #554 does not widen the margin itself.
  - Reword NFR-003 and the mission's "done when" to "measured and recorded", because there is no move.
  - WP10 runs PIT on `:pack-validation` and writes the measured figure into the build file comment and ADR-0062 row 105.
    Until then, the design's "about 350 / 88 %" is marked unverified.

### F10. MINOR: the new ArchUnit rules, as written, do not bite or do not cover

- **`PACK_CODES_HAVE_ONE_OWNER`** ("only `PackCodes` builds a pack `ReasonCode`"). It cannot tell codes apart:
  `ReasonCode` is a public record, and any `new ReasonCode(...)` is equal by value. Three places build or name pack
  codes elsewhere:
  - `WholePackRoundTripRule.java:28-31` builds `round_trip_red` and `round_trip_written_unpaired` in `pack-validation`;
  - the `CLOSURE` codes are built from `RefusalCode.token()` (A-m1);
  - `RelationshipRegistrationService.java:167` names `RelationshipEndpointRule.CODE`. That is a surface the design's
    flows omit.
- **`PACK_RULES_READ_NO_FILE`.** It covers `packs.domain.rules`, but `CLOSURE` sits in `packs.domain`
  (`design-r3.md:28`).
- **Spring reach.** Nothing yet proves that the rules `pack-check` will reach use no Spring. The existing walk is rooted
  at the inspector only (`HexagonalArchitectureTest.java:389-394`).
- **SC-001.** "Surface-parity tests (SC-001)" cannot be met as written under R1(a). Validate-only rules such as
  `org_fragment_missing` and `asset_mime_invalid` still differ.
- **Fold.**
  - State the rule as a ban on constructor calls of `ReasonCode` in `ai.speckitty.charter.packs..` and
    `..packvalidation..`, except in `PackCodes`. Either move the round-trip codes into `PackCodes` or name them as the
    one exception. Keep a bite fixture.
  - Widen `PACK_RULES_READ_NO_FILE` to the `CLOSURE` functions.
  - Root `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` at `PackRulesets` and the `CLOSURE` entry as well.
  - Restate SC-001: every fault both surfaces report has one code, and validate mode reports at least what registration
    reports.

## Verdict

**Sound with folds.**

- **What holds.** Placing the rules in `packs.domain` follows the operator's R3 answer. It removes the no-op judge and
  the port that `new`-built stores could not take. It keeps the onion rules and the module direction intact. Judging
  registration in `packs.application` is safe for every `loadStaged` caller, provided the fail-closed guard runs before
  the gate and the impact check.
- **What must be folded first.** Four MAJOR findings must be folded before tasks:
  - the infeasible domain home for the assembly (F2);
  - a per-fault R2(b) table in place of a blanket "registration wins" (F3);
  - the inspector's wire changes and a two-way agreement test (F4);
  - the `CLOSURE` subject, which `pack-check` cannot build without Spring today (F5).
- **What the operator must hear.** F1 must reach the operator in plain words. The service will no longer use
  `pack-validation`, and ruling 1's port sentence is withdrawn. That reverses the steer ADR-0075 was founded on. The
  operator should confirm it, not discover it in an amendment.
- **Unverified.** #578's mutation figure stays unverified until a PIT run records it.
