# Design decisions

> Reconstructed on 2026-11-24 from the orchestrating session's record, not appended as it happened (see
> `tooling-friction.md` for why). Every ruling is quoted in full in ADR-0083 and in `decisions/`. This is the
> index with the reason for each.

- **[2026-11-21][plan] Registration is the model's judge; pack-doctor gives its answer offline.** Every divergence
  found later was settled toward registration's answer (R-z, R-ae), except what an offline tool cannot see (stored
  activations).
- **[2026-11-21][plan] R-r: all of #154 in this PR.** That is the anti-pattern file kind, refusal of file-less nodes,
  and dropping the inline blocks outright, with no deprecation window (charter C-002). Open-packs breaks as expected
  and aligns once against the first release (R-w). On open-packs `main` `a7243e2`, measured at `fd41585bd`,
  `validate --full` reports 79 violations: 61 `artifact_schema_error`, 15 `drg_schema_error` and 3
  `substep_delegation_unresolved`. `--round-trip` reports 144: the same, plus 40 `round_trip_red` and 25
  `round_trip_written_unpaired`. R-ae does not change this, since `REFERENCES` is skipped there.
- **[2026-11-21][plan] R-s and R-u: `--full` is narrowed to delegations, references and enhancement targets, named
  REFERENCES.** The rest of #632 item 3 goes to #636.
- **[2026-11-21][plan] R-t: the round-trip codes fold into WP04.** `EXCLUDED_WRITTEN` is retired with no alias.
- **[2026-11-22][plan] R-v: references resolve against the derived `referable()` view.** That is the held files plus
  the steps of held mission types.
- **[2026-11-23][accept] R-x: ClosureVerdict stays a fault list until #636 slice 7.** A Reason has no slot for packs
  and violations. #614 is therefore Refs, not Closes.
- **[2026-11-23][accept] R-y and R-z: one case-only body, `CaseOnlyRules`, judged per root over the composed
  closure.** Preset-item not_held and undeclared keep R-k's own closure.
- **[2026-11-23][land] R-aa and R-ab: REFERENCES is gated only on composition faults, with one ordered body
  (`PackRulesets.references`).** Registration and pack-doctor both call it.
- **[2026-11-23][land] R-ac: the XML report escapes characters XML 1.0 cannot carry, reversibly.** Pack content must
  never turn exit 1 into exit 2.
- **[2026-11-23][land] R-ad: a special file in a pack is refused as `unreadable`.** The existing violation code was
  reused rather than a new one. Registration used to hang opening a FIFO.
- **[2026-11-23][land] Size bounds, one guard (`ParsedYaml`).** The bounds are a 1 Mi code-point cap on text and on
  the expanded size through aliases, numbers up to 1 000 characters, and a float's exact plain-form length. The
  canonical form is judged where it is written. Reason: pack content must never exhaust memory or CPU, or yield exit
  2.
- **[2026-11-24][land] F2 is not folded.** A listed unreadable constituent's second fault needs a new round-trip
  outcome type. That is recorded under "Not done" and is the natural pickup with #636's round-trip changes. N6, the
  quadratic violation collection, was filed as #648.
- **[2026-11-24][land] R-ae: `--full` judges references per root over the composed closure.** This follows R-z and
  closes the only false pass against registration.
