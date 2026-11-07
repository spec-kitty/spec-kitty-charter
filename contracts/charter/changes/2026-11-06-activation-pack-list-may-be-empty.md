### Changed

- `ActivationPackList.packs` no longer says it is never empty. A document that names packs has at least one, but a
  document without packs builds on every configured pack, and a service with no configured pack answers `[]`. The
  schema was always `minItems`-free; its description is corrected, and says the closure follows `parent_pack` as well
  as `depends_on`. The behavior is unchanged.
