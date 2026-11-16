# Op 01M7GC59321986H9FQHMXXCCGX: every identifier on the shared kernel grammar (#552)

Profile: java-jenny. Kind: feature and tidy. Size: S/M. Parent epic: #549 (ADR-0075 decision 2, operator ruling
"everything strict", amended by ADR-0077: `MissionStepContractIdentifier` is retired and leaves the list).

## Grounding (main at `9798572`)

### Identifier types and their grammar before this Op

| Type or site | Module | Grammar before | Accepts `a--b`? | This Op |
|---|---|---|---|---|
| `PackIdentifier` | kernel | not blank | yes | holds `slug(63)` as `isPackId` and `GRAMMAR_DETAIL` (Q3) |
| pack id in `PackRegistrationService` (l.202) | packs | `^[a-z0-9][a-z0-9-]{0,62}$` | yes | copy removed; `PackIdentifier.isPackId` |
| pack id in `RegistrationAdmission` (l.54) | packs | `^[a-z0-9][a-z0-9-]{0,62}$` | yes | copy removed; `PackIdentifier.isPackId` |
| `ParadigmIdentifier` | packs | `^[a-z][a-z0-9-]*$`, 64 | yes | `letterFirstSlug(64)` |
| `StyleguideIdentifier` | packs | `^[a-z][a-z0-9-]*$`, 64 | yes | `letterFirstSlug(64)` |
| `ProcedureStep.role` | packs | `^[a-z][a-z0-9-]*$`, no cap | yes | `RoleBearer.isRole` (letter-first slug, 64) |
| `AgentProfile` role, alt-roles | profiles | `^[a-z][a-z0-9-]*+$`, 64 | yes | `RoleBearer.isRole` |
| `MissionStep.role` | missions | `[a-z][a-z0-9-]*`, 64 | yes | `RoleBearer.isRole` |
| `RolePins` role (slice G) | packs | `^[a-z][a-z0-9-]{0,63}$` | yes | `RoleBearer.isRole` |
| `SlotId` parts | kernel | `[a-z][a-z0-9-]*` each, no cap | yes | each part `isLetterFirstSlug` (64) |
| `StepEnhancement` slot | packs | own copy of the slot regex | yes | `SlotId.tryParse` |
| `StepEnhancement` id | packs | own copy of the letter-first slug, no cap | no | `isLetterFirstSlug` (adds the 64 cap) |
| Tool guide, skill, glossary context and term, preset, mission type, mission step id, substep | packs, missions | `isLetterFirstSlug` since #546 and #106 | no | unchanged |
| Guard, procedure, lifecycle | packs, missions | `isSlug` | no | unchanged |
| `TacticIdentifier` | packs | not blank, 64 (schema `^[a-z][a-z0-9-]*$`) | yes | `letterFirstSlug(64)`, tactic schema tightened (Q1 a) |
| `DirectiveIdentifier` | packs | `^[A-Z][A-Z0-9_-]*$` | n/a | out: a different grammar (upper case) |
| `PackQualifiedReference` pack name, activations `PackRef` | kernel, activations | `^[a-z0-9][a-z0-9_-]*$` | yes | left as they are (Q2 a); follow-up drafted below |
| profile-id (`RolePins`, `MissionSubstepDelegation`), kind tokens | packs, contract | `^[a-z0-9][a-z0-9_-]*$` | yes | out: not in decision 2, underscores are part of the grammar |
| `ActivationId` | activations | `[A-Za-z0-9][A-Za-z0-9-]{0,63}` | yes | out: opaque id (ADR-0020 D2), not a pack identifier |
| `SubstepCandidate.ID_GRAMMAR` | missions | `[A-Za-z0-9][A-Za-z0-9_-]{0,63}` | yes | out: a delegation candidate's id spans kinds (directive ids are upper case), not one identifier type |
| `StepEnhancementRules.STEP_ID` | packs | `^[^/\s]+/[^/\s]+$` | yes | out: a reference to `<mission>/<step>`, whose parts are judged by the mission type and step ids |
| `IdentifierGrammar.DOTTED_KEY` | kernel | first segment `[a-z][a-z0-9-]*` | yes | out: an artifact key, not in decision 2 |
| descriptor `name` (`pack-descriptor.schema.json`) | contract | `^[a-z0-9][a-z0-9_-]*$` | yes | with Q2: `PackSourceStagers` requires it to equal the `packId`, so a source registration already refuses a `name` off the pack-id grammar |

The roles had four copies of one grammar, each a little different (a cap or none). `RoleBearer`, the kernel interface
every role-bearing artifact already shares, now holds `isRole` and `ROLE_MAX_LENGTH`.

### Alignment with #106 and slice G

- **#106 (glossary contexts and terms)** already uses `IdentifierGrammar.isLetterFirstSlug` for the context and term
  ids, and the contract's `ContextId`, `TermId` and term references write `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`. This Op
  uses the same rule and the same written pattern; nothing there changes.
- **Slice G (#573)** wrote the role grammar four times: `RolePins.ROLE`, `MissionStep.ROLE_SHAPE`, the policy schema's
  `role_pins` property names, and the lookahead-free pattern of `MissionStep.role`, `ProfileSelectionRead.role` and the
  `selectAgentProfile` path parameter. All move to the letter-first slug. The lookahead-free pattern is rebuilt on the
  slug, still refusing `generic`: it was checked against `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` minus `generic` on every
  string of up to six characters over `abcegnir0-9z` and 200,000 random strings, with no mismatch. Its length cap moves
  to `maxLength: 64`.

### The inline not-blank checks

ADR-0075 counted five `text != null && !text.isBlank()` sites; the issue says about 20. 45 rule sites wrote a not-blank
check by hand: 4 with the null guard, 12 more on a value object's text, and 29 on an aggregate's field. All now use
`Constraints.notBlank()`, stated in the type's own words through the new `Constraint.described(String)`, and narrowed
with `on(...)` for a field. A breach says the same detail as before. The only behaviour change: a `null` is now a breach
where it was a `NullPointerException`. Left as they are: `Preset`'s check over a captured variable, the `Optional` and
list checks (`content.map(...)`, `noneMatch(String::isBlank)`), which are not a single text.

## Similar surfaces, dispositioned

- **Contract.** `PackId`, the `packId` path parameters of `packs_packId` and `packs_packId_doc`, `PackSource.packId`,
  `AgentProfile` role and alt-roles, `ProcedureStep.role`, `SlotId`, `MissionStep.role`, `ProfileSelectionRead.role`
  and the `selectAgentProfile` role: changed. Fragment `changes/2026-11-15-identifiers-on-the-shared-grammar.md`
  (`### Breaking`).
- **Pack schemas.** agent-profile, mission-type, paradigm, styleguide, policy (`role_pins` keys), procedure, and
  step-enhancement (slot): changed; packs `CHANGELOG.md` `0.1.0-SNAPSHOT` `### Changed`. tactic.schema.json: changed too (Q1 a).
- **Scenario.** `whole-pack-registration.feature` gains the refused rows `acme--x` and `acme-`.
- **Fixtures, example packs, contract examples.** No id, role or slot in them breaks (grep and the full test run).
- **Architecture diagrams.** `descriptor-agent-profile.puml`, `descriptor-doctrine-kinds.puml` and
  `descriptor-schemas.md` still write `^[a-z][a-z0-9-]*$`. They are drawn from the descriptor schemas, and redrawing
  needs the SVGs regenerated. Recorded under "Not done".
- **ADR-0078** decision 3 writes the role pin key as `^[a-z][a-z0-9-]*$`. ADR-0075 decision 2 supersedes it, so the
  ADR text is left as decided.

## Tracked issues, dispositioned

- **#552:** delivered by this Op (`Closes #552`), with the rulings on Q1 to Q3 below; the Q2 and Q3 remainders are drafted as follow-ups.
- **#549:** tick slice 3 on landing.
- **#485** (directive file slugs as directive ids): a different grammar, untouched.
- **#466** (real packs register as they are): the census below shows no open-packs id breaks, so #552 does not
  regress it.
- **Open PRs:** none overlap (#554 had no PR when this Op started).

## Operator rulings (relayed by the orchestrator, 2026-11-16)

- **Q1 (a):** `TacticIdentifier` moves to `letterFirstSlug(64)`, and `tactic.schema.json` is tightened, under the same
  Breaking note. The census counts it: 0 of 138 tactic ids break.
- **Q2 (a):** the pack-name reference grammars that allow `_` stay. They are recorded under "Not done", and a
  follow-up is drafted for the orchestrator to file, because such a reference can never resolve.
- **Q3 (b):** as applied. `PackIdentifier.isPackId` holds the rule, and registration, admission and the contract
  refuse through it. The type split is drafted as a follow-up under #549, after #554.

## open-packs census

Read-only clones of spec-kitty-open-packs at `openPacksRef` `1692a3b` and at `main` `c0ec640` (2026-11-14). Each value
was tested against the new grammar and cap.

| Site | Where it lives | Values (pinned / main) | Breaking |
|---|---|---|---|
| pack id | directory, `pack.yaml` `name`, `<name>.source.yaml` | 19 / 19 each | 0 |
| paradigm `id` | `*.paradigm.yaml` | 11 / 11 | 0 |
| styleguide `id` | `*.styleguide.yaml` | 39 / 40 | 0 |
| procedure step `role` | `*.procedure.yaml` | 29 / 30 | 0 |
| agent profile `role` | `*.agent.yaml` | 33 / 33 | 0 |
| agent profile `alt-roles` | `*.agent.yaml` | 13 / 13 | 0 |
| mission step `role` | `*.mission-type.yaml`, `steps` and `on_demand_steps` | 32 / 32 | 0 |
| role pin keys | policy `role_pins` | 0 / 0 (none exist) | 0 |
| step enhancement `slot` | `*.step-enhancement.yaml` | 5 / 5 | 0 |
| slot markers | step prompts under `missions/software-dev/steps/` | 10 / 10 | 0 |
| tactic `id` (Q1) | `*.tactic.yaml` | 138 / 138 | 0 |

**Count: 0 open-packs ids break**, at the pin and at `main`. Fixtures under `tests/fixtures/` were checked by grep and
pass too. The descriptor `pack_id` is a ULID and is not a pack id in this sense (Q3).

### Draft: open-packs#TBD-identifier-grammar (informational)

> **Title:** Identifiers follow charter's shared slug grammar (ADR-0075 decision 2)
>
> charter #552 refuses a doubled, leading or trailing hyphen in a pack id (`slug(63)`), a role, a paradigm or
> styleguide id and each part of a slot id (letter-first slug, 64). A census of this repository at charter's pin
> `1692a3b` and at `main` `c0ec640` found no id that breaks, so nothing needs fixing now. The pack schemas charter
> publishes (packs contract `0.1.0-SNAPSHOT`) carry the stricter patterns; once `validate_pack_schemas.py` takes them
> up, a new id that breaks the grammar is refused here as it is in charter.

Since nothing breaks, the orchestrator may file it as a notice or not file it; Rule 4 asks for an issue only where pack
content must change.

## Not done

- **The split of `PackIdentifier`** (Q3): the registered pack id, the descriptor ULID and the descriptor `name` share
  one type, so its constructor checks only that the id is not blank, and a stored directory is not checked against
  `isPackId` on read. Proposed as a `follow-up` under #549, after #554.
- **The descriptor diagrams** `descriptor-agent-profile.puml` and `descriptor-doctrine-kinds.puml` still write
  `^[a-z][a-z0-9-]*$`: their SVGs must be regenerated with PlantUML, which this session does not have. The Markdown
  beside them (`descriptor-schemas.md`) and the divergence register (DIV-401) are fixed.
- **The pack-name references** that allow `_` (Q2): `PackQualifiedReference.PACK_NAME_GRAMMAR`, the activations
  `PackRef`, the contract's `PackQualifiedReference(Write)` and the glossary term-reference patterns. A pack id is
  `slug(63)` now, so a reference whose pack name holds `_`, `a--b` or more than 63 characters names no pack. Follow-up
  drafted below.

## Draft follow-ups for the orchestrator to file

### Draft: the pack-name part of a reference follows the pack-id grammar (follow-up, under #549)

> **Labels:** `follow-up`, `priority:P3`. **Type:** Task. **Parent:** #549.
>
> Since #552 a pack id is `slug(63)` (`PackIdentifier.isPackId`, ADR-0075 decision 2). The pack-name part of a
> reference still takes `^[a-z0-9][a-z0-9_-]*$` with a cap of 64: `PackQualifiedReference.PACK_NAME_GRAMMAR`
> (kernel), the activations `PackRef` (`[a-z0-9][a-z0-9_-]{0,63}`), the contract's `PackQualifiedReference.yaml` and
> `PackQualifiedReferenceWrite.yaml`, and the glossary term-reference patterns in `GlossaryTerm.yaml` and
> `GlossaryContextTerm.yaml`. A reference whose pack name holds `_`, a doubled or trailing hyphen, or 64 characters
> can never resolve, yet it is accepted and fails later as an unresolved reference.
>
> Done when the pack-name part of every reference is `slug(63)` through `PackIdentifier.isPackId`, the contract
> patterns follow under `### Breaking`, and a test shows a reference to `a_b::...` refused at parse with the
> grammar's detail. The descriptor `name` (`pack-descriptor.schema.json`, `^[a-z0-9][a-z0-9_-]*$`) follows too, since
> `PackSourceStagers` requires it to equal the pack id. Count the open-packs references that break first: the #552
> census found no pack name with `_`.

### Draft: split PackIdentifier into the registered pack id and the descriptor identity (follow-up, under #549, after #554)

> **Labels:** `follow-up`, `priority:P2`. **Type:** Task. **Parent:** #549. **Blocked by:** #554.
>
> `PackIdentifier` carries three values: the registered pack id (`slug(63)`, ADR-0075 decision 2), the descriptor's
> `pack_id` (a ULID, the "immutable sole runtime identity" of `pack-descriptor.schema.json`) and the descriptor's
> `name`. So its constructor checks only that the id is not blank (#552 Q3, ruling b), and a pack directory seeded on
> disk as `a--b` is read and served (`StorageRootLocations.isPack`, `DomainRepositoryService` l.374); every write
> path refuses it. The sites that build a `PackIdentifier` from something other than a registered id:
>
> - the descriptor ULID: `EdgeIndex.idOf` (l.649) and l.218, `ClosureView` l.364, `ClosureViewSession` l.533,
>   `WholePackStore` l.282 and `ArtifactFiles` l.42;
> - the descriptor `name`: `InboundReferences` l.214;
> - staging directory names (`.pack-commit-...`): `PackCommitStore` l.297 and l.417;
> - `dropped.pack()`: `ClosureView` l.885 and `ProfileEnhancementRule` l.95.
>
> Done when `PackIdentifier`'s constructor guards `slug(63)`, the descriptor identity has a kernel type of its own
> (for example `PackUlid`) that the edge and closure indexes key by, the staging and dropped-pack sites name what they
> hold, a stored directory whose name is not a pack id is refused on read with a named code, and the test fixtures
> that build a `PackIdentifier` from a ULID (about 48 literals) use the type that fits. Lands after #554, which reworks
> the closure rules over these indexes.
