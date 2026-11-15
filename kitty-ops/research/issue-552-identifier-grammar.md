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
| `TacticIdentifier` | packs | not blank, 64 (schema `^[a-z][a-z0-9-]*$`) | yes | **Q1, pending** |
| `DirectiveIdentifier` | packs | `^[A-Z][A-Z0-9_-]*$` | n/a | out: a different grammar (upper case) |
| `PackQualifiedReference` pack name, activations `PackRef` | kernel, activations | `^[a-z0-9][a-z0-9_-]*$` | yes | **Q2, pending** |
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
  step-enhancement (slot): changed; packs `CHANGELOG.md` `0.1.0-SNAPSHOT` `### Changed`. tactic.schema.json: Q1.
- **Scenario.** `whole-pack-registration.feature` gains the refused rows `acme--x` and `acme-`.
- **Fixtures, example packs, contract examples.** No id, role or slot in them breaks (grep and the full test run).
- **Architecture diagrams.** `descriptor-agent-profile.puml`, `descriptor-doctrine-kinds.puml` and
  `descriptor-schemas.md` still write `^[a-z][a-z0-9-]*$`. They are drawn from the descriptor schemas, and redrawing
  needs the SVGs regenerated. Recorded under "Not done".
- **ADR-0078** decision 3 writes the role pin key as `^[a-z][a-z0-9-]*$`. ADR-0075 decision 2 supersedes it, so the
  ADR text is left as decided.

## Tracked issues, dispositioned

- **#552:** delivered by this Op (`Closes #552`), apart from Q1 to Q3 below.
- **#549:** tick slice 3 on landing.
- **#485** (directive file slugs as directive ids): a different grammar, untouched.
- **#466** (real packs register as they are): the census below shows no open-packs id breaks, so #552 does not
  regress it.
- **Open PRs:** none overlap (#554 had no PR when this Op started).

## Operator questions (sent to the orchestrator)

- **Q1.** Does `TacticIdentifier` join the letter-first slug? Recommended: yes.
- **Q2.** Do the pack-name references that allow `_` (`PackQualifiedReference`, activations `PackRef`) tighten to
  `slug(63)`? Recommended: no, leave them and record why.
- **Q3.** `PackIdentifier` carries three values: the registered pack id (a slug), the descriptor `pack_id` (a ULID,
  in `EdgeIndex.idOf`, `EdgeIndex` l.218, `ClosureView` l.364 and `ClosureViewSession` l.533) and the descriptor
  `name` (`InboundReferences` l.214, which may hold `_`). The squad found more: the descriptor ULID also in
  `WholePackStore` l.282 and `ArtifactFiles` l.42; staging directory names (`.pack-commit-…`) in `PackCommitStore`
  l.297 and l.417; `dropped.pack()` in `ClosureView` l.885 and `ProfileEnhancementRule` l.95. A `slug(63)` constructor
  throws on every real pack in the edge and closure reads. A stored pack directory is served under any name that does
  not start with a dot (`StorageRootLocations.isPack`, `DomainRepositoryService` l.374), so a directory seeded by hand
  as `a--b` is read with no grammar check; every write path (archive upload, from-source) refuses it. Recommended: the type holds the rule as `isPackId` and `GRAMMAR_DETAIL`, registration,
  admission and the contract refuse through it, the constructor stays not blank, and the split of the type is a
  `follow-up` after #554.

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
- **The tactic id** waits on Q1, and the pack-name references on Q2.
