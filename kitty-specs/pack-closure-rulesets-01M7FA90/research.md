# Research: ADR-0075 slice 5, the PACK and CLOSURE rulesets and one pipeline (#554)

Grounding note, written 2026-11-15 before the spec. Branch base: `origin/main` at `5decda0`.

## Sources read

- `AGENTS.md`, `.kittify/charter/charter.md`, `docs/development/issue-tracker.md`.
- ADR-0075, with its amendment of 2026-11-13 (#553).
- Epic #549: the handover (2026-11-13) and the operator rulings (2026-11-15). Slices #550 to #557, #478 and #547.
- Slice F's plan on draft PR #567 (`kitty-specs/slice-f-kind-taxonomy-01M7B19K/plan.md`, IC-01, and the risk table).
- Two squads, each read in full and dispositioned below:
  - [`research/squad-similar-surfaces.md`](research/squad-similar-surfaces.md);
  - [`research/squad-tracked-issues.md`](research/squad-tracked-issues.md).

## Gate

- Implementation waits until slice F's PR #567 merges. Today it is a draft with no code.
- Research, the spec and the plan go ahead now. When #567 merges, this branch rebases onto `main` before implementing.

## Decisions

**D1. The packs domain stays canonical** (operator ruling 1, 2026-11-15).
- The rulesets run over `packs.domain` objects (`PackContents`, the census and `Pack`), with no parallel model.
- `FileSystemPackInspector` stays. #554's issue text says to delete it; the ruling overrides that, and the issue text is
  amended at landing.

**D2. The registration load keeps its reader and runs the same `PACK` ruleset through a port.**
- `packs.domain` declares the port, `pack-validation` implements it, and `deployable` wires it.
- This direction is forced. `:packs` cannot depend on `:pack-validation`, because that would be a Gradle cycle.
  `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` lets only `deployable` compose an adapter.
- `deployable` gains `implementation(project(":pack-validation"))`. Today it is `testImplementation` only
  (`deployable/build.gradle.kts:24`).
- The registration port `WholePackWriteStore` sits in `packs.application`. The new judging port goes in
  `packs.domain`, as the ruling says (squad finding: port placement).

**D3. One ruleset, and each mapper keeps its own order** (squad blocker 5).
- Today the orders differ:
  - validate mode sorts by `PackPolicy.PRECEDENCE`;
  - registration (`WholePackStore.loadStaged`, `:360-404`) uses a fixed sequence: graph faults, the pack's violations,
    unmodelled files and orphans in path order, the reserved shard, then manifest membership only when nothing else
    failed.
- The `PACK` ruleset returns its findings in one order. The `Rationale`→`PackVerdict` mapper and the
  `Rationale`→API problem mapper each apply today's ordering as a plain function. That is how "wire codes and their
  order do not change" holds.
- Manifest membership stays gated on "no other fault", as a `when` on the ruleset.

**D4. String sub-codes become typed codes with the same wire tokens.**
- The tokens are `schema`, `retired`, `unmodelled`, `manifest_membership`, `asset`, `reserved_shard`, `domain`,
  `reference`, `unreadable`, `companion`, and `invalid` (the fallback at `WholePackStore:758`). 63 `violations/N/code`
  and 6 `diagnostics/N/code` scenario assertions pin them.
- No mapper from the old strings is kept. Each code owns its severity (`ReasonCode(value, severity)`; operator
  ruling 4).

**D5. Scope of `CLOSURE`.**
- 8 of `PackCompositionResolver.Failure`'s 11 kinds belong in `CLOSURE`: cycle, unknown and out-of-range dependency,
  parent, level, and the name, sibling and preset conflicts.
- The 3 reference kinds (`REFERENCE_UNQUALIFIED`, `REFERENCE_PACK_MISSING`, `REFERENCE_ARTIFACT_MISSING`) need the
  closure walk. They are #556's `ClosureWalk`.
- `CLOSURE` collects every fault. Today each resolver check throws on the first.

**D6. Divergence closes by construction.**
- Today validate mode alone runs the level, org-fragment, duplicate-id and asset rules.
- The load alone runs the unmodelled, manifest-membership and reserved-shard checks.
- The reserved shard surfaces as `drg_schema_error` in validate mode and as `reserved_shard` at registration. One rule
  gives one code per mapper. The plan fixes which code each surface answers, against today's scenarios.
- The compose read (`DomainRepositoryService:790`) refuses on the first violation as `pack_unreadable`. The plan
  decides whether it runs `PACK` or stays a read (open question Q3).

**D7. #515's endpoint check.**
- The graph rule calls slice F's `EndpointRule` bridge.
- Whichever of F and #554 lands second rehomes the check. The wire code `relationship_endpoint_not_allowed` does not
  change.
- F lands first by the gate, so #554 rehomes it.

**D8. Mutation margin.** `pack-validation` is at floor 85 with margin 0 (`pack-validation/build.gradle.kts`, ADR-0062
`:105` and `:349`). Moving `PackPolicy`'s rules in widens it. The `packs` `domain+application` group (margin 126) loses
those mutants. Both floors are measured again and ADR-0062 is amended.

**D9. Tests collapse to one per rule and one per mapper.**
- 17 classes, 474 tests and about 10,000 lines are in scope. The largest are `FileSystemPackInspectorTest` (58),
  `PackCompositionResolverTest` (56), `PackPolicyVocabularyTest` (49), `PackPolicyRulesTest` (43) and
  `WholePackStoreTest` (42).
- These stay green unchanged, as the proof that the wire codes held: `VocabularyEqualityTest`, `WireTokenParityTest`,
  `ReservedRefusalCodesTest`, `RefusalHandlerTest`, `ExcludedFilesAgreementTest`, and the contract scenarios.
- The planted packs with an `expected.json` (72; only the 10 `glossary-*` ones run in Java today) are reused as rule
  fixtures. That is the canonical shape, and an invalid pack only for an error path.

## Squad dispositions

| Finding | Severity | Disposition |
|---|---|---|
| Two orders, one ruleset | BLOCKER | **Folded** as D3 |
| `unmodelled` against `file_not_held` (decision 5) | BLOCKER | **Operator question Q1** |
| The pipelines diverge | MAJOR | **IN**, D6 |
| Port placement and the module cycle | MAJOR | **Folded** as D2 |
| #554's text says to delete `FileSystemPackInspector` | MAJOR | **IN**: amend the issue text at landing (D1) |
| #355: validate mode gains the load-only checks | MAJOR | **IN for its validate-mode half**, as a consequence of one pipeline. `Refs #355`, not `Closes` |
| The glossary file kind (#106, merged) needs a ruleset home | MEDIUM | **IN**: its own per-kind ruleset, with its codes unchanged |
| Slice G (#573) adds `operator_role_not_human` | MEDIUM | **SEQUENCE**: the second to land types it |
| The mutation floors | MEDIUM | **IN**, D8 |
| The planted packs | MEDIUM | **IN**, D9 |
| `PackFinding.toRefusal()` and `PackFindingCode.refusal()` have no main caller | MEDIUM | **IN**: deleted with the converters |
| `DomainRepositoryService:1116` puts an artifact id where the file should be | MINOR | **Fold** if the rule touches it, else drop |
| The pack-id regex | MINOR | **OUT**, #552 |
| #535, #518, #358, #52 item 4 | — | **OUT**, so that scope does not widen |
| `GuardClosure`, `GuideMarkdown`, `FallbackProfileRule`, `ImpactRelations`, `PackMutabilityPolicy` | — | **OUT**, #556 |

No issue is filed. Nothing found is a correctness, data or security risk beyond what the slice delivers
(issue-tracker.md Rule 1).

## Coordination

- **#555** runs in parallel in another session. Both touch `WholePackStore` and `packs.infra.storage`. The second to
  land rebases, and overlaps go through the orchestrator.
- **#556** and **#478** follow #554. #556's ruling 3 is kept compatible: `Severity` has no `ERROR` today. #556 adds
  it, or its named code, when it adds the external checks. #554 adds no external check.
- **#552** waits for slice G and is untouched here.

## Open questions

- **Q1 (operator).** Decision 5 says an unmodelled file is `file_not_held` at `WARNING`. Today a registration refuses
  an unmodelled file with the violation `unmodelled`, which scenarios pin. `file_not_held` is the warning for excluded
  files (`PackFindingCode.java:61`, `WholePackWriteStore.java:44`). #554 also says the wire codes do not change.
  - **(a)** Keep both, as typed codes: `unmodelled` stays a violation and `file_not_held` stays the excluded-file
    warning. Decision 5's "a code, not a severity" is met by typing the code. The wire is unchanged.
    *Recommended.*
  - **(b)** Apply decision 5 literally. An unmodelled file becomes a `file_not_held` warning, and a registration
    accepts it. That is a wire change and needs a `### Breaking` fragment.
- **Q2 (plan).** The name and shape of the judging port in `packs.domain`, for example `PackJudge`, which takes
  `PackContents` and returns `Rationale`.
- **Q3 (plan).** Whether the compose read (`DomainRepositoryService:790`) runs `PACK`, or stays a read that trusts
  the registration.
