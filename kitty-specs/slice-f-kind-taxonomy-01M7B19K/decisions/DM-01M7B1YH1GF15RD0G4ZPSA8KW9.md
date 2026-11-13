# Decision Moment `01M7B1YH1GF15RD0G4ZPSA8KW9`

- **Mission:** `slice-f-kind-taxonomy-01M7B19K`
- **Origin flow:** `specify`
- **Slot key:** `specify.step-contract.retirement`
- **Input key:** `step_contract_retirement`
- **Status:** `resolved`
- **Created:** `2026-11-13T18:15:35.729005+00:00`
- **Resolved:** `2026-11-13T18:43:16.078200+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Q2: Retire mission_step_contract by moving delegations to scope edges, the gate to the lifecycle guard and substep text into prompts (substep structure lost until #513)?

## Options

- Retire now as proposed
- Fold substeps into Step (pulls #513)
- Keep kind until #513/#434
- Other

## Final answer

Fold substeps into the mission-type Step, then retire the kind. Follow-ups: flat ordered list substeps[] in the step (one level, no new relation, #513 narrowed to recursion and DECOMPOSES_INTO); all fields kept (id, description, command, inputs, delegates_to, guidance); delegates_to kept as substep data AND a scope edge from the action to every candidate is required (open-packs adds the 72 missing).

## Rationale

_(none)_

## Change log

- `2026-11-13T18:15:35.729005+00:00` - opened
- `2026-11-13T18:43:16.078200+00:00` - resolved (final_answer="Fold substeps into the mission-type Step, then retire the kind. Follow-ups: flat ordered list substeps[] in the step (one level, no new relation, #513 narrowed to recursion and DECOMPOSES_INTO); all fields kept (id, description, command, inputs, delegates_to, guidance); delegates_to kept as substep data AND a scope edge from the action to every candidate is required (open-packs adds the 72 missing).")
