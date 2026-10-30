# Decision Moment `01M65MGKX68G0EGHV2AETWWKNX`

- **Mission:** `container-profile-secrets-01M65MC5`
- **Origin flow:** `specify`
- **Slot key:** `specify.secrets.import-seam`
- **Input key:** `secrets_import_seam`
- **Status:** `resolved`
- **Created:** `2026-10-30T05:28:54.438612+00:00`
- **Resolved:** `2026-10-30T05:52:46.300494+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

ADR-0041 D3 writes the required import as configtree:/run/secrets/. Tests (and CI) cannot create /run/secrets/. Resolve the location through a placeholder defaulting to /run/secrets/ (still required, no optional:), or keep the literal path?

## Options

- placeholder charter.container.secrets-dir defaulting to /run/secrets/
- literal /run/secrets/ only
- Other

## Final answer

placeholder charter.container.secrets-dir defaulting to /run/secrets/ (operator, 2026-10-30): configtree:${charter.container.secrets-dir:/run/secrets/}, no optional: prefix; recorded as an ADR-0041 D3 clarification

## Rationale

_(none)_

## Change log

- `2026-10-30T05:28:54.438612+00:00` — opened
- `2026-10-30T05:52:46.300494+00:00` — resolved (final_answer="placeholder charter.container.secrets-dir defaulting to /run/secrets/ (operator, 2026-10-30): configtree:${charter.container.secrets-dir:/run/secrets/}, no optional: prefix; recorded as an ADR-0041 D3 clarification")
