---
audience: org
maintainers:
- '@spec-kitty/core'
---
# Alternative REASONS writing pack

An org pack that holds its own REASONS canvas writing styleguide, with the same id as `org-base`'s. The two packs
contest that styleguide at org, and the `project` pack settles the contest as their distribution pack: it depends on
both, and no org pack depends on it (#358).

## Provenance

Authored in this repository as the charter's own example pack (ADR-0070). It is a fixture of the sibling settlement,
not a copy of any organization's pack.
