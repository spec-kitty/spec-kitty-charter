### Changed

- `Link` no longer says that a pack write is never `DELETE`. Its descriptions name the two removals of PR A,
  `deletePackArtifact` and `deletePackRelationship`, beside the activation `DELETE`; no other pack write is a `DELETE`,
  and no link uses `PATCH`. A description change only: the `method` pattern is unchanged, and no link advertises either
  removal (decision DM-01M6TVMGH94ZF07KSWSCANEASV).
