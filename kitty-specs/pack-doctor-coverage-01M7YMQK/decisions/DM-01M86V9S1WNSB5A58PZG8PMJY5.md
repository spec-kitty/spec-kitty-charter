# Decision Moment `01M86V9S1WNSB5A58PZG8PMJY5`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `full_references_scope`
- **Input key:** `full_references_scope`
- **Status:** `resolved`
- **Created:** `2026-11-24T13:18:08.444943+00:00`
- **Resolved:** `2026-11-24T13:18:11.243489+00:00`
- **Resolved by:** `claude`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

Which scope does validate --full judge references (delegations, references, enhancement targets) in?

## Options

- (a) Per root over the composed closure, every pack in it, as registration does
- (b) Keep each pack's own closure and document the divergence

## Final answer

(a) Per root over the composed closure, every pack in it, as registration does

## Rationale

Operator ruling R-ae (2026-11-24), source: operator (in this session, answering the Tier R B2 options), answer: "1" (option (a)). validate --full judges REFERENCES per root over the composed closure, every pack in it, through PackRulesets.references(rooted.order(), rooted.order(), factsOf), faults found from several roots reported once, as CaseOnlyRules does (R-z precedent). Registration is unchanged. ReferencesPhaseTest's own-closure pin flips; a FullPassParityTest row pins the dependency's bare reference only its dependent holds (pack_reference_unqualified on both surfaces).

## Change log

- `2026-11-24T13:18:08.444943+00:00` - opened
- `2026-11-24T13:18:11.243489+00:00` - resolved (final_answer="(a) Per root over the composed closure, every pack in it, as registration does")
