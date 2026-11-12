### Breaking

- A whole-pack registration derives `pack-manifest.yaml`'s lists and refuses an uploaded manifest whose lists differ
  (#110, ADR-0042 amendment of 2026-11-12, ADR-0074 decision 7). The manifest lists one constituent per artifact the
  pack holds (its kind token, id and file) and one entry per preset, each list in path order. A registration whose
  uploaded manifest lists an entry the pack does not hold, or leaves out one it holds, answers `422 validation_failed`
  with one violation per entry, code `manifest_membership`, at `pack-manifest.yaml`, for example
  `pack-manifest.yaml: leaves out constituent directive:B at directives/B.directive.yaml, which the pack holds`. The
  order of the entries and every derived hash are not judged. Every write then stores a manifest with the derived lists
  and fresh hashes: a whole-pack registration, a per-artifact `PUT` and a `DELETE`, and a pack that held none gets one.
  So the stored pack, its revision and its `ETag` include a manifest the upload may not have carried. A
  `pack-manifest.yaml` that is a directory or a link is refused (`unreadable`). Core's seam: a pack whose manifest lists
  a subset of its artifacts, which registered before, is refused until its lists are complete; a client that wrote the
  manifest by hand can leave it out and let the service generate it. A pack stored before this change gets a new
  revision on its next write, so re-registering it unchanged at the same `pack_version`, which answered `unchanged`,
  now answers `409` (same version, another revision): send a new `pack_version`. Pack authors: regenerate each
  `pack-manifest.yaml` from the derived lists, or drop it and let the service generate it. open-packs's `built-in`, the
  one pack there that holds a manifest, differs from its derived lists and is refused until it does.
