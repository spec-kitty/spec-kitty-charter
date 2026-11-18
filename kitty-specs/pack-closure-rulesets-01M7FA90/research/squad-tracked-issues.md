# Squad: tracked issues

This read-only lens ran before the spec on 2026-11-15. It read #554, epic #549 with its three comments (the
2026-11-13 handover and the 2026-11-15 operator rulings), the siblings #550 to #557, #478 and #547, slice F's draft
PR #567 and its plan, slice G's draft PR #573, and `docs/development/issue-tracker.md`. It then searched the open
issues, and the issues closed since 2026-11-06, for the pipeline's terms: `PackPolicy`, `PackValidation`, validate
mode, `PackFinding`, `PackElementViolation`, `RefusalViolation`, refusal codes, `FileSystemPackInspector`,
`WholePackStore`, `DomainRepositoryService`, `PackCompositionResolver`, unmodelled files, manifest membership, the
reserved shard, orphan fragments, `kernel.validation`, `pack-check`, the mutation floor and expected codes.
Dispositions:

- **IN**: delivered and closed by #554's PR.
- **TOUCH**: referenced with `Refs`.
- **SEQUENCE**: an ordering constraint.
- **OBSOLETED**: closed with evidence.
- **OUT**: untouched.

| Issue | Disposition |
|---|---|
| #554 PACK and CLOSURE rulesets, one pipeline | **IN**. Its title and body still say `FileSystemPackInspector` is deleted. Ruling 1 (2026-11-15) keeps it as the read the rulesets run over. Amend the title and body at landing. |
| #549 epic (ADR-0075) | **TOUCH**. Tick slice 5 at landing. The checklist lags: slices 2 and 4 (#551, #553) and #547 are merged but not ticked. |
| #515 endpoint kinds (slice F) | **SEQUENCE**. Ruling: #554 starts once F's PR #567 merges. #567 is still a draft with no code. F parks the read check in `PackPolicy.endpoints` and the load path; #554's graph rule calls F's `EndpointRule` bridge. Whoever lands second rehomes it. `relationship_endpoint_not_allowed` does not change. |
| #537 slice F, PR #567 | **SEQUENCE**. Spec and plan can proceed. Implementation waits for #567 to merge (ruling, "Next step"). |
| #555 round trip into main code | **SEQUENCE**. Runs in parallel (ruling 2). Both touch `packs.infra.storage` and `WholePackStore`. The second to land rebases. |
| #556 walk and closure checks behind ports | **SEQUENCE**. Needs #554. The endpoint rule stays in #554's graph ruleset, not in `ImpactCheck`. The open-packs expected-code parity test is #556's, not #554's. |
| #478 `pack-check` jar | **SEQUENCE**. Needs #554, which delivers the `PACK` and `CLOSURE` rulesets it runs. Not closed by #554. |
| #552 identifier grammar | **SEQUENCE**, not blocking. Held until slice G lands. Its new refusals reach `PACK` as findings; the second to land rebases. |
| #538 slice G, draft PR #573 | **SEQUENCE**. G adds `operator_role_not_human` as a schema violation at the item, which #554 turns into a typed code. G's other new codes sit in `RegistrationImpact` (with #556). The second to land carries the new codes. |
| #557 remove `open-packs-drift.yml` | **OUT**. Blocked on open-packs#252. |
| #355 validate mode skips the guard, lifecycle and mission-template closure | **TOUCH**, and a scope risk. "One pipeline" means validate mode and the load run the same rules. If `GuardClosure` and `MissionTemplates` become `PACK` rules, the validate-mode half of #355 is delivered. The per-file write half, the domain invariants and its open questions stay. Write `Refs #355` and say on the issue which part is left. Do not take the per-file write half in silently; the spec states which half it carries. |
| #106 glossary contexts and terms (PR #577, merged 2026-11-15) | **SEQUENCE**, satisfied. It is in this branch's base. Its glossary file kind needs a home in the per-kind rulesets, with its codes unchanged. |
| #547 drift test misses unmodelled kinds | Closed by PR #564. Background: the `file_not_held` rule must not reopen its gap. No action. |
| #110 manifest membership (PR #542) | Closed. Background: the membership check that only the load runs today becomes a rule. No action. |
| #461 mutation nightly | **OUT**. Constraint noted: `pack-validation`'s floor is 85 with margin 0 (one mutant). #554's code must widen the margin, or one surviving mutant turns the nightly red. |
| #535 file-name collision across layers | **OUT**. It could become a `CLOSURE` rule. Not in #554's list; adding it widens scope. |
| #358 distribution pack settles a sibling contest | **OUT**. Needs ADR-0048's distribution pack. The `CLOSURE` sibling rule is where it lands later. |
| #518 glossary cross-pack collision | **OUT**. Same reason as #535. |
| #52 closure checker and YAML adapter follow-ups | **OUT**. Item 4 (a blank fragment id) fits the fragment ruleset. It is below MEDIUM and not in scope. |
| #130, #133 refusal and diagnostic code enums | **OUT**. Contract-level. #554 does not change wire codes. |
| #416 lookup cache; #417 registration notices; #154 `anti_pattern`; #466 slice B; #531 PIT test cost | **OUT** |

Proposed closeout: `Closes #554`. Refs #549 (tick slice 5), #355 (validate-mode half only, if the spec takes it),
#515 (the read check rehomed, if F lands first).

## Conflicts with the rulings and the charter

- **#554's text versus ruling 1.** The issue deletes `FileSystemPackInspector`; ruling 1 keeps it. The ruling wins.
  The converters between `PackFinding`, `PackElementViolation` and `RefusalViolation` still go.
- **The registration load's own reader.** Ruling 1 keeps it, running the same `PACK` ruleset through a port in
  `packs.domain`. This is not a legacy reader beside a new one: it is operator-ruled, and the rules are shared. The
  spec says so, so a reviewer does not read it as one.
- **Typed codes.** The string sub-codes (`schema`, `domain`, `unmodelled`) are replaced outright. No mapper keeps
  the old strings beside the typed codes. Only the wire codes and their order stay, as #554 says.
- **Severity.** Ruling 4 (2026-11-15): a code owns its severity. `file_not_held` is declared at `WARNING` on its
  code, not set at each call.

## Findings

No finding is filed as a new issue. None is a correctness, data or security risk beyond what the slice already
delivers (issue-tracker.md Rule 1). The #554 title and body amendment is done at landing, in the same pass (Rule 6).
