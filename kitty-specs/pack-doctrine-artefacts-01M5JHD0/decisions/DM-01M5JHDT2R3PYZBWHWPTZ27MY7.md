# Decision Moment `01M5JHDT2R3PYZBWHWPTZ27MY7`

- **Mission:** `pack-doctrine-artefacts-01M5JHD0`
- **Origin flow:** `specify`
- **Slot key:** `specify.modelling.entity-topology`
- **Input key:** `entity_topology`
- **Status:** `resolved`
- **Created:** `2026-10-22T19:29:22.520375+00:00`
- **Resolved:** `2026-10-22T21:39:24.843590+00:00`
- **Resolved by:** `stijn@spec-kitty.ai`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Model directive/styleguide/paradigm as per-kind entities (each like Tactic) or one generic DoctrineArtifact child entity with a kind discriminator — and does the existing bespoke Tactic fold in or stay special?

## Options

- Per-kind entities (3 classes, each like Tactic)
- Generic DoctrineArtifact with a kind discriminator
- Decide in the plan phase

## Final answer

Per-kind concrete child entities (Directive, Styleguide, Paradigm) each modelled like Tactic and carrying its own schema-distinct body, unified by a thin CharterArtifact interface in kernel.domain — NOT one generic DoctrineArtifact. CharterArtifact spine: kind(), packId(), localId(), schemaVersion(), label() (title|name), description() (Optional: intent|summary|purpose), edges() (references/procedures/directive_refs -> DRG edges), default urn(). Tactic is retrofitted to implement CharterArtifact and stays special (keeps steps/failureModes). 'doctrine' is not canonical; family + interface are named CharterArtifact and code uses American 'artifact'.

## Rationale

Operator rejected over-generalizing into one class after we confirmed against the canonical per-kind schemas (ADR-0017 / open-packs) that directive (intent+enforcement+integrity/validation rules), styleguide (scope+principles+patterns+structural_lint_config) and paradigm (summary+directive_refs) share almost no fields beyond id/schema_version/references — even the human label splits title vs name. A shared interface captures the real common spine for aggregate/repository/DRG/round-trip binding without collapsing distinct concepts.

## Change log

- `2026-10-22T19:29:22.520375+00:00` — opened
- `2026-10-22T21:39:24.843590+00:00` — resolved (final_answer="Per-kind concrete child entities (Directive, Styleguide, Paradigm) each modelled like Tactic and carrying its own schema-distinct body, unified by a thin CharterArtifact interface in kernel.domain — NOT one generic DoctrineArtifact. CharterArtifact spine: kind(), packId(), localId(), schemaVersion(), label() (title|name), description() (Optional: intent|summary|purpose), edges() (references/procedures/directive_refs -> DRG edges), default urn(). Tactic is retrofitted to implement CharterArtifact and stays special (keeps steps/failureModes). 'doctrine' is not canonical; family + interface are named CharterArtifact and code uses American 'artifact'.")
