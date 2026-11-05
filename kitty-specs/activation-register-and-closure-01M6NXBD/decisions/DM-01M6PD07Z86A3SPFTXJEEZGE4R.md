# Decision Moment `01M6PD07Z86A3SPFTXJEEZGE4R`

- **Mission:** `activation-register-and-closure-01M6NXBD`
- **Origin flow:** `plan`
- **Slot key:** `plan.domain.policy-file-format`
- **Input key:** `policy_file_format`
- **Status:** `resolved`
- **Created:** `2026-11-05T17:44:43.240840+00:00`
- **Resolved:** `2026-11-05T17:46:00.233287+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Mutability policy file: descriptor key, file name and shape

## Options

- mutability_policy key -> policy.yaml with kind_defaults + artifacts
- Other

## Final answer

Optional descriptor key mutability_policy (pack-relative path, e.g. policy.yaml); closed schema policy.schema.json with kind_defaults {kind: level} and artifacts {urn: level}; role locks/pins reserved; named-but-missing/unreadable file makes the pack unreadable; recorded in a new ADR

## Rationale

_(none)_

## Change log

- `2026-11-05T17:44:43.240840+00:00` - opened
- `2026-11-05T17:46:00.233287+00:00` - resolved (final_answer="Optional descriptor key mutability_policy (pack-relative path, e.g. policy.yaml); closed schema policy.schema.json with kind_defaults {kind: level} and artifacts {urn: level}; role locks/pins reserved; named-but-missing/unreadable file makes the pack unreadable; recorded in a new ADR")
