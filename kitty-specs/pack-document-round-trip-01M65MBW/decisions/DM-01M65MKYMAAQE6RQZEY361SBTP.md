# Decision Moment `01M65MKYMAAQE6RQZEY361SBTP`

- **Mission:** `pack-document-round-trip-01M65MBW`
- **Origin flow:** `specify`
- **Slot key:** `specify.frontmatter_equality`
- **Input key:** `frontmatter_equality`
- **Status:** `resolved`
- **Created:** `2026-10-30T05:30:43.722499+00:00`
- **Resolved:** `2026-10-30T05:30:46.916619+00:00`
- **Resolved by:** `claude`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

ADR-0040 leaves full ADR-0036 numeric and empty-versus-absent frontmatter equality to #260. Which equality does pack.md frontmatter use?

## Options

- Map.equals on the loaded YAML values (ADR-0040 as built); absent and empty frontmatter stay different models, numbers compare as loaded
- Full ADR-0036 meaning comparison of the frontmatter (numeric equality, empty equals absent)
- Other

## Final answer

Map.equals on the loaded YAML values (ADR-0040 as built); absent and empty frontmatter stay different models, numbers compare as loaded

## Rationale

The gate compares a source and a written pack.md read by the same reader, and the canonical writer emits the loaded values, so Map.equals on loaded values is exactly ADR-0036 meaning for a round trip; no numeric or empty-vs-absent drift can arise from our own write. The built-in pack.md has no frontmatter. ADR-0040 is amended to record the ruling; logged as an open review item for the operator.

## Change log

- `2026-10-30T05:30:43.722499+00:00` — opened
- `2026-10-30T05:30:46.916619+00:00` — resolved (final_answer="Map.equals on the loaded YAML values (ADR-0040 as built); absent and empty frontmatter stay different models, numbers compare as loaded")
