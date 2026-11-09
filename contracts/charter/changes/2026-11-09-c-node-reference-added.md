### Added

- `NodeReference` (`{kind: NodeKind, id}`, closed), the reference of a graph node that may not be an activatable
  artifact (#429, D15). `id` admits the slash of a step (`^[A-Za-z0-9][A-Za-z0-9_./-]*$`). It is introduced once and
  reused: `ArtifactReference` stays the reference of an artifact an activation can select or deny, `NodeReference` names
  a node that may not be activatable, and `Urn` names the identity of a graph node. `NodeKind` lists the authored kinds
  only, so no carrier of `NodeReference` can name a guard or a lifecycle.
  Adding guard or lifecycle to `NodeKind` later is a Breaking change for `EffectiveGraphNode`, `NodeReference` and every
  `SkippedReport` carrier.
