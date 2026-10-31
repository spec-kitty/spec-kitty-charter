# Research: the activation set

The pre-spec findings are in `research/pre-spec-research.md`. This file records the rulings and the
design decisions the plan rests on.

## Operator rulings (2026-10-31, all as recommended)

Relayed by the orchestrator session; recorded as decision `DM-01M691JAA6ZCQZMNH8G6ZKRVAT`.

| # | Question | Ruling |
|---|---|---|
| 1 | Per-entry provenance (ADR-0020 open question 1) | Left out of `ActivationConfig`; may come in a later 0.x minor |
| 2 | `contextActivations` (open question 2) | Left out; ADR-0033 D4 puts "for this Mission also include X" in the client's activation context; the effective-context read is where it surfaces (#103) |
| 3 | `appliedPresets` | Left out; `ActivationChange` reports the preset applied; a timestamp would make the revision depend on time |
| 4 | `selected.skill` absent | Means only the required skills (ADR-0031/0042 must-have items); `missionTypes` absent and `[]` both mean none but stay distinct on the wire |
| 5 | Kinds | The 11 activatable kinds, snake_case `NodeKind` tokens: every artifact kind except `template` and `asset` |
| 6 | `cascade` | A non-empty array of kind tokens, or `["all"]`; absent means no cascade |
| 7 | `packs` | Items `{packId, version?}` (ADR-0033 D6) |
| 8 | `requiredBy`, `violations` | Optional members of the vendored shared `Problem`, recorded in `_shared/VENDORED.md` |
| 9 | Effective reads | All four declared now with closed, minimal schemas; #103 may reshape them in a later 0.x minor |
| 10 | JSON Schema file | Canonical `contracts/offering/schemas/ActivationConfig.schema.json` plus an OpenAPI mirror, with a parity test |
| 11 | `@reserved` scenarios | The README admits a 501 pin besides the declaration pin; one controller answers 501 |
| 12 | Version | **Changed by the operator, 2026-10-31:** both #125 and #126 land inside the unreleased `0.1.0-SNAPSHOT`, under its `### Breaking` subsection; `info.version` stays `0.1.0-SNAPSHOT`. (First ruled: `0.2.0-SNAPSHOT`.) |
| 13 | #46 `x-stability-level` | Does not touch reserved operations; `x-reserved` is their marker |
| 14 | ADR and security | No new ADR: ADR-0020 amended in place; no oauth2 scopes in this minor |

## Design decisions

### D-1. `selected` and `deactivated` are closed objects with one property per kind

- **Decision**: `SelectedArtifacts` and `DeactivatedArtifacts` each declare the 11 kinds as
  properties, `additionalProperties: false`.
- **Rationale**: a map with `propertyNames` is not supported by ClosureChecker or
  ContractConformance, counts as open, and generates a `Map<String, List<String>>` that loses
  the kind vocabulary. An unknown kind is then a schema failure, which ADR-0020 D9 already rules
  `422 activation_invalid`.
- **Alternatives**: `additionalProperties: {array}` with `propertyNames: {enum}`; rejected above.

### D-2. The JSON Schema file is canonical; the OpenAPI component mirrors it

- **Decision**: `ActivationConfig.schema.json` (2020-12, `$id`
  `https://schemas.spec-kitty.ai/charter/offering/ActivationConfig.schema.json`, `$defs`,
  self-contained) is canonical; the OpenAPI component and its parts mirror it. Checked three ways:
  1. **Structure** (`contracts/src/test`): both sides resolved into one tree (`$ref` to a file on
     the mirror side, `$ref` to `#/$defs/...` on the JSON side), then exactly these keys are
     stripped: `title`, `description`, `examples`, `$comment`, `$schema`, `$id`, `$defs` (after
     resolution) and every `x-` key; the trees must be equal. A negative control changes one
     keyword in a copy of the mirror and requires the comparison to fail.
  2. **Corpus, networknt** (`service/packs`): the shared corpus against the JSON file and against
     the resolved mirror; every valid document passes and every invalid one fails, on both.
  3. **Corpus, ContractConformance** (`service/deployable`): the same corpus against the mirror
     with the scenario validator.
- **Rationale**: four tools would have to learn external JSON `$ref`s and `$defs`; the mirror
  keeps them unchanged, and the three checks keep it honest (squad S8).
- **Alternatives**: `$ref` the JSON file from OpenAPI (rejected: tool changes); generate the YAML
  from the JSON at build time (rejected: a generated file in the split contract hides the diff).

### D-3. Content alignment with the charter's own vocabulary

- **Decision**: a `service/packs` test asserts, both directions, that `SelectedArtifacts` and
  `DeactivatedArtifacts` declare exactly the `ArtifactKind`s with `activatable()` other than
  `MISSION_TYPE` (11), and that every `activated_<plural>` key of
  `contracts/packs/schemas/charter.schema.json` other than `activated_kinds` maps through
  `ArtifactKind.plural()` to one of them (9 keys today, 11 once PR #288 lands; with
  `activated_kinds` and `mission_type_activations` that is core's 13).
- **Rationale**: "content alignment, not file shape" (operator, 2026-10-20) is checked, not
  asserted in prose.

### D-4. The revision and key order

- **Decision**: `revision` is `sha256:` + hex of SHA-256 over the RFC 8785 (JCS) serialization
  of the canonical `config`. JCS sorts object keys itself, so the digest does not depend on the
  order in which members are written; the service writes `config` members in schema property
  order for readability, which is the order the schema declares.
- **Rationale**: the issue comment (item 13) asks that the key order be the schema's; JCS makes
  the revision order-independent anyway, so the two are compatible and both are stated.

### D-5. Conditional headers are declared optional

- **Decision**: `If-Match` and `If-None-Match` are `required: false`. A sub-resource write
  without `If-Match` is answered `428` (declared); with a stale one, `412`.
- **Rationale**: a required header parameter becomes a framework `400` before the operation
  runs, which the contract does not declare.

### D-6. `mission_type` is a path kind for selections only

- **Decision**: `{kind}` under `selections/` is one of the 11 activatable kinds or
  `mission_type` (`SelectableKind`); under `exclusions/` it is an activatable kind only. Selecting
  a mission type adds it to `missionTypes` and, with `cascade`, walks from it; unselecting removes
  it. The token is `mission_type`, the contract's snake_case spelling of ADR-0020's
  `mission-type`. Mission types have no deny list, so an exclusion of one would not be the
  inverse of its `DELETE` (squad S22).

### D-7. Statuses per operation follow ADR-0020 D9

- `GET` reads declare no `304`: ADR-0020 D9 does not list it; adding one later is a new answer
  the client can ignore, recorded as a follow-up for #103.
- `…/artifacts` pages like the other listings, with the `_shared` `PageInfo` and the page
  parameters (squad S19).
- Every operation declares `500` (`OfferingUnreadable`), as every existing operation does.
- `packs` is non-empty when present (`minItems: 1`), keeps merge order (never sorted), and a
  duplicate `packId` is `422 activation_invalid`.

### D-8. Links

- **Decision**: every new success schema has a required `_links` referencing the shared `Links`
  component of ADR-0019 D3 and `docs/development/hypermedia-hateoas.md`: open relation keys, a
  closed `Link` (`href`, `templated`, `method`, `title`), with the ClosureChecker `_links`
  exemption pinned by a fixture. The names `Links`/`Link` are agreed with #126 through the
  orchestrator; whoever lands second keeps one copy. The relation-key lint rule stays #126's. No
  relation points at a reserved operation from a built answer (ADR-0019 D3).

### D-9. `ContractConformance` learns `minItems`

- **Decision**: add `minItems` to the scenario validator's keywords, red-first with a fixture
  test. It is the only keyword the new schemas need that the validator lacks (`deactivated.<kind>`,
  `cascade`, `packs`). `schemaVersion` is `integer, minimum: 1, maximum: 1` (`enum: [1]` would
  fail `enum-snake-case`).

### D-10. Every new success schema carries `_links`

- **Decision**: following #126, `Activation`, `ActivationChange`, `ActivationSkippedReport`,
  `EffectiveArtifactList`, `EffectiveGraph` and `EffectiveActionContext` each carry a required
  `_links` (`Links`). The reserved effective reads carry no `Charter-Source-Fingerprint` until
  built (#126 narrows that header to verified `GET` 200s); until #126 lands, the current
  `charter-headers-on-every-response` rule applies as it stands.

## Plan-point adversarial squad (2026-10-31)

Three lenses ran on the plan, read-only, each with its profile loaded: contract/API
(`reviewer-renata`), architecture (`architect-alphonso`), test quality (`qa-test-designer-tessa`).
Every finding has a disposition; none is dropped.

| # | Lens | Finding | Disposition |
|---|---|---|---|
| S1 | API, arch | The plan still targeted `0.2.0-SNAPSHOT` | **Folded**: operator ruling moved the set into `0.1.0-SNAPSHOT` (commit `bec46fdd`) |
| S2 | API | A private closed `{self}` links object breaks ADR-0019 D3 and clashes with #126 | **Folded**: D-8 rewritten; `Links` (open keys) / `Link` (closed) per `hypermedia-hateoas.md`, with the ClosureChecker `_links` exemption and fixture; names agreed with #126 via the orchestrator |
| S3 | API, test, arch | Bean validation and body binding run before the controller, so "501 first" was false | **Folded**: D-11, a `@ReservedOperation` handler interceptor answers 501 in `preHandle`, before arguments are resolved; the controller methods throw the same. Malformed requests to a reserved operation then also answer 501. Build-time notes (enum path converter, unknown-property refusal, 400->422) recorded in D-15 |
| S4 | arch | NullOmissionTest fails on optional lists defaulting to `[]`; generated models cannot keep absent apart from `[]` | **Folded**: D-12, `containerDefaultToNull=true` plus a `NON_NULL` mix-in for the activation models; a round-trip test pins absent vs `[]` |
| S5 | test, arch | Kind set computed from the wrong source (13 not 11) | **Folded**: D-3 uses `ArtifactKind.activatable()` minus `MISSION_TYPE`; counts reconciled (9 `activated_<plural>` keys today, 11 after #288; 13 keys including `activated_kinds` and `mission_type_activations`) |
| S6 | test | Conformance hook falls back to `default`, so a missing 501 still passes | **Folded**: the 501 step looks up the declared `501` and `x-reserved` for the method and path, no fallback; a unit test asserts the `x-reserved` operations equal the operations the 501 scenarios call (13 = 13) |
| S7 | test | "Charter headers" on a 501 undefined; fingerprint check | **Folded**: the step asserts `Charter-Contract-Version` and `Charter-Worktree` present, `Charter-Source-Fingerprint` absent, and no `code` member at all |
| S8 | test, arch | Parity test placement and strength | **Folded**: D-2 rewritten: structural parity (exact stripped-key list, negative control) in `contracts/src/test`; the shared corpus run by networknt on the JSON file and on the resolved mirror (`service/packs`) and by `ContractConformance` on the mirror (`service/deployable`) |
| S9 | test | Corpus misses closed-ness cases | **Folded**: corpus list in D-13 |
| S10 | test | Lint rules pass vacuously on `clean.yaml`; count 4 vs 5 | **Folded**: `clean.yaml` gains a reserved operation and a `204`; one fixture per removed element plus `x-reserved: {}`; SC-004 says 5 |
| S11 | test, arch | Declaration pins vs README rule and `ReservedRefusalCodesTest` | **Folded**: the ArchUnit rule is parametrized over every `raised: false` code with a bite probe each; each pin names the test and the issue (#103 for the reads; the activation build issue drafted in `research/` for the writes) |
| S12 | test | Red-first not observable for `minItems` and fixtures | **Folded**: `ContractConformanceTest` learns `minItems` first (red), fixtures committed red; noted in the work packages |
| S13 | test | No test that the controller touches no storage | **Folded**: ArchUnit rule: `ActivationController` depends on no application or storage type |
| S14 | test, API | `CharterRefusalCode` prose ("every other code is 500"; `mission_type_not_found`/`action_not_found` "no longer raised") becomes wrong | **Folded**: descriptions rewritten and pinned in `ContractDescriptionsTest` |
| S15 | API | ADR-0020 D10 says the vendored `Problem` is unchanged | **Folded**: FR-018 amendment covers D10; descriptions say `requiredBy` appears only on 409 `artifact_required`/`artifact_locked` and `violations` only on 422 `activation_invalid` |
| S16 | API | One strong ETag for answers that also depend on the packs | **Folded**: D-14 |
| S17 | API | ETag format unpinned | **Folded**: D-14, quoted form pinned by pattern on `ETag`, `If-Match`, `If-None-Match` |
| S18 | API | No 500 declared | **Folded**: every operation declares `500` (`OfferingUnreadable`) |
| S19 | API | Closed enums and kind objects are 1.0 traps; paging | **Folded**: the ADR-0020 amendment rules that a new activatable kind, state, policy or reason is a contract minor before 1.0 and a major after; `EffectiveArtifactList` pages with the existing `_shared` `PageInfo`/`PageSize`/`PageCursor` like the other listings |
| S20 | API | Plan contradictions (mission_type, skipped name, sort of `packs`) | **Folded**: `mission_type` only under selections (D-6); one name `ActivationSkippedReport`; `packs` keeps merge order and refuses duplicate `packId` (422) |
| S21 | API | Op 13 and select use codes described as gone | **Folded**: S14; an unknown mission type on select is `mission_type_not_found` |
| S22 | API | `mission_type` under exclusions not REST-coherent | **Folded**: selections only |
| S23 | API | "all stands alone" is prose only | **Kept as a semantic rule**: a `oneOf` over two array shapes generates an unusable model; the 422 names `/cascade` in `violations`. Recorded so nobody tightens the parity corpus to fail on it |
| S24 | API | `enum: [1]` fails `enum-snake-case` | **Folded**: `schemaVersion` is `integer, minimum: 1, maximum: 1` |
| S25 | API | Two meanings of `requiredBy`; `ArtifactRef` naming | **Folded**: both kept (ADR names), each description names its item type and points at the other; `ArtifactReference` |
| S26 | API | Generic names; `self` on a change; `depth` bounds; body required-ness | **Folded**: operationIds prefixed (`replaceActivation`, `selectActivationArtifact`, ...), schemas `EffectiveGraphNode`/`EffectiveGraphEdge`, `RefusalViolation`; `ActivationChange._links` names the activation under `activation` and itself under `self` is not claimed; `depth` bounded as ADR-0018; request bodies `required: true` |
| S27 | arch | `contracts/offering/` is an orphan | **Folded**: the charter CHANGELOG versions it (its header names the file); `$id` follows the `https://schemas.spec-kitty.ai/charter/...` pattern; the release workflow adds it to the release assets |
| S28 | arch | Shared `Problem` coupling to `charter/schemas` | **Folded**: `RequiringPack` and `RefusalViolation` live under `_shared/schemas` with inline patterns; VENDORED.md records it; `### Breaking` lists it as a change to every generated `Problem` |
| S29 | arch | Controller placement unexplained | **Folded**: stays in `packs.infra.api` (ArchUnit's `PACKS_API`); the amendment records that the built set becomes its own slice and the `PACKS_*` constants are generalized then (#140) |
| S30 | arch | ADR README index row | **Folded**: the row gains "amended 2026-10-31" |
| S31 | arch | Tests that change in step (`WireTokenParityTest`, switches) | **Folded**: one work package changes kernel, contract codes and `RefusalHandler` together |

### D-11. The 501 is answered before binding

- **Decision**: a `@ReservedOperation` annotation on `ActivationController` and a Spring
  `HandlerInterceptor` that throws `ResponseStatusException(NOT_IMPLEMENTED)` in `preHandle` for
  any handler method of an annotated controller; `ProblemHandler` renders the closed `Problem`.
  The generated methods also throw it (they are never reached).
- **Rationale**: `preHandle` runs before argument resolution, so no body binding, bean
  validation or enum conversion pre-empts the `501`. A `415` for a non-JSON body is answered by
  the mapping's `consumes`, before the interceptor, which the contract declares.

### D-12. Absent stays apart from `[]` in the generated models

- **Decision**: `containerDefaultToNull=true` in the generator, and a `NON_NULL` mix-in for the
  new models (as `ProblemJson` does for `Problem`). A unit test deserializes and reserializes
  `{}` and `{"selected": {"directive": []}}` and finds them unchanged.

### D-13. The shared corpus

`contracts/offering/corpus/ActivationConfig/{valid,invalid}/*.json`, at least 10 of each:
valid: `{"schemaVersion":1}`, every member, `missionTypes: []`, `selected.skill: []`, `packs`
with and without `version`, `cascade`-free; invalid: unknown member at the top, in `PackPin`, in
`selected`; `selected.template`; `selected.mission_type`; `null` members; `deactivated.tactic: []`;
`packs: []`; `schemaVersion: 2` and `"1"`; missing `schemaVersion`. `cascade: ["all","tactic"]` is
a request-body case, valid by schema and refused semantically (S23).

### D-14. Validators

- `ETag` is a strong, quoted validator `"sha256:<64 hex>"` (pattern pinned, as `ContractETag`).
- On `Activation` answers it is the quoted `revision`; `If-Match` carries it (or `*`);
  `If-None-Match` on `PUT` is `*` only. `Activation` also carries `governanceFingerprint`, and
  `summary` is derived for that fingerprint: a `GET` is byte-stable for one revision and one
  fingerprint (ADR-0020 D4 amended).
- On the effective reads the `ETag` is opaque: a digest over `revision` and
  `governanceFingerprint`, so it changes when either changes.

### D-15. Notes for the build (not done here)

When an operation is built: a String-to-enum converter for snake_case path kinds; refuse unknown
JSON members on activation bodies (`422 activation_invalid`, never silently dropped); map a body
that fails bean validation to `422 activation_invalid` with `violations`. Recorded in the
ADR-0020 amendment.
