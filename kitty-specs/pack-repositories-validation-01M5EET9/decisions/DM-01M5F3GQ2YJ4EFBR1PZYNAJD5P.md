# Decision Moment `01M5F3GQ2YJ4EFBR1PZYNAJD5P`

- **Mission:** `pack-repositories-validation-01M5EET9`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.adr0006-slice-vs-layer-rename`
- **Input key:** `adr0006_rename_shape`
- **Status:** `resolved`
- **Created:** `2026-10-21T11:28:34.398449+00:00`
- **Resolved:** `2026-10-21T11:56:55.352569+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How should the screaming-architecture rename reconcile with ADR-0006's proposed vertical-slice (domain-concern) layout?

## Options

- Layer-role rename now, ADR-0006 slices deferred (amend ADR-0006)
- Adopt ADR-0006 vertical slices now (packs slice + kernel)
- Other

## Final answer

Adopt ADR-0006 vertical-concern slices NOW: restructure into kernel + packs slice modules with ai.speckitty.charter.<concern>.<layer> packages (domain/application/infra.storage/infra.api) + a bootstrap composition root; validation lives kernel-side; whole-service migration accepted (DIRECTIVE_035 occurrence map). The pack descriptor+tactic repositories/validation feature is delivered ON this slice structure.

## Rationale

_(none)_

## Change log

- `2026-10-21T11:28:34.398449+00:00` — opened
- `2026-10-21T11:56:55.352569+00:00` — resolved (final_answer="Adopt ADR-0006 vertical-concern slices NOW: restructure into kernel + packs slice modules with ai.speckitty.charter.<concern>.<layer> packages (domain/application/infra.storage/infra.api) + a bootstrap composition root; validation lives kernel-side; whole-service migration accepted (DIRECTIVE_035 occurrence map). The pack descriptor+tactic repositories/validation feature is delivered ON this slice structure.")
