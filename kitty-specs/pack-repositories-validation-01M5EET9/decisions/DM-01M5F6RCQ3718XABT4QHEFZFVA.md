# Decision Moment `01M5F6RCQ3718XABT4QHEFZFVA`

- **Mission:** `pack-repositories-validation-01M5EET9`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.validation-kernel-placement`
- **Input key:** `validation_kernel_placement`
- **Status:** `resolved`
- **Created:** `2026-10-21T12:25:11.651314+00:00`
- **Resolved:** `2026-10-21T12:30:35.082117+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Where does the pure validation library live, given ADR-0006 has a single kernel ring and DOMAIN_IS_PURE only admits java + ..kernel.domain.. + own domain?

## Options

- Separate top-level validation module (two inner rings; amend DOMAIN_IS_PURE + ADR-0006)
- Nest as kernel.validation inside the single kernel module (broaden ..kernel.domain.. to ..kernel..)
- Other

## Final answer

Nest validation inside the single kernel module as ai.speckitty.charter.kernel.validation (NOT a separate top-level module). ADR-0006 single-kernel model unchanged; DOMAIN_IS_PURE broadens ..kernel.domain.. -> ..kernel..; a validation-purity ArchUnit rule gates ..kernel.validation.. -> {java, jspecify} with a known-bad control. The kernel module holds two pure sub-packages: kernel.domain (Pack/DRG vocab) + kernel.validation (guard lib).

## Rationale

_(none)_

## Change log

- `2026-10-21T12:25:11.651314+00:00` — opened
- `2026-10-21T12:30:35.082117+00:00` — resolved (final_answer="Nest validation inside the single kernel module as ai.speckitty.charter.kernel.validation (NOT a separate top-level module). ADR-0006 single-kernel model unchanged; DOMAIN_IS_PURE broadens ..kernel.domain.. -> ..kernel..; a validation-purity ArchUnit rule gates ..kernel.validation.. -> {java, jspecify} with a known-bad control. The kernel module holds two pure sub-packages: kernel.domain (Pack/DRG vocab) + kernel.validation (guard lib).")
