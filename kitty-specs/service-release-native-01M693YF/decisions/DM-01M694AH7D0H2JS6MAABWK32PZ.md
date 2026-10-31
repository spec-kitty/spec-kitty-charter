# Decision Moment `01M694AH7D0H2JS6MAABWK32PZ`

- **Mission:** `service-release-native-01M693YF`
- **Origin flow:** `specify`
- **Slot key:** `specify.release.version-scheme`
- **Input key:** `version_scheme`
- **Status:** `resolved`
- **Created:** `2026-10-31T14:02:55.597392+00:00`
- **Resolved:** `2026-10-31T14:02:57.860422+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

D1: service tag form and version scheme?

## Options

_(none)_

## Final answer

Operator 2026-10-31: service-vX.Y.Z[-pre], pre suffix [0-9A-Za-z.] only; tag equals gradle.properties version (no -SNAPSHOT); service SemVer of its own (major: launcher/operator-visible break; minor: new contract minor or feature; patch: fixes); 0.x until contract 1.0.0; one bundled contract version per release; a pre-release may bundle a -SNAPSHOT contract, a final release requires contract-charter-v<info.version> merged into HEAD.

## Rationale

_(none)_

## Change log

- `2026-10-31T14:02:55.597392+00:00` — opened
- `2026-10-31T14:02:57.860422+00:00` — resolved (final_answer="Operator 2026-10-31: service-vX.Y.Z[-pre], pre suffix [0-9A-Za-z.] only; tag equals gradle.properties version (no -SNAPSHOT); service SemVer of its own (major: launcher/operator-visible break; minor: new contract minor or feature; patch: fixes); 0.x until contract 1.0.0; one bundled contract version per release; a pre-release may bundle a -SNAPSHOT contract, a final release requires contract-charter-v<info.version> merged into HEAD.")
