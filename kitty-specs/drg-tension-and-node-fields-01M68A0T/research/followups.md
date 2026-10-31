# Follow-up drafts (for the orchestrator to file)

## 1. Operator decision: a tension in `registered.graph.yaml` (ADR-0044 D5)

A hand-edited tension in the service-owned `registered.graph.yaml` loads and validates clean. A
registration into the pack is refused instead (`pack_unreadable`), rather than the tension being
dropped in silence. Two options:

- **(a)** Refuse it in validation and load as well. The pack stops loading until the edit is
  removed.
- **(b)** Carry tensions through a registration rewrite (`RelationshipShard` read and write,
  `CommitStorePackWriteStore`). Nothing is refused and nothing is lost.

Today the paths differ on purpose; a test pins that.

## 2. `YamlFields.strings` should refuse a non-string entry

A non-string entry is skipped in silence (`text()` turns null, map and list entries into empty). It is
safe today only because the schemas type every string list. Make it throw `IllegalStateException`, as
`mappings()` does, so a schema change cannot become a silent loss.

## 3. A duplicate graph node is dropped without a finding

`DoctrineGraph` keeps the first declaration of a URN. A second declaration with other tags or
provenance is lost in silence, and ADR-0036 calls same-identity-different-value an inconsistency. Add
a `PackPolicy` finding.

## 4. A tension self-loop's finding text names a relationship

"a relationship cannot join X to itself" is kept for text stability. Consider "a tension cannot join X
to itself".

See also `followup-load-fixture-runner.md`.
