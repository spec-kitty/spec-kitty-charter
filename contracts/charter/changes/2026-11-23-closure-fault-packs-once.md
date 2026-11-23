### Changed

- `pack-doctor validate` names each pack of a closure fault once in the problem's `packs`, as registration names it,
  where it named a pack twice when one pack both mentions an artifact by case and holds it. No code, schema or HTTP
  behavior changes. Core's seam: none; the CLI and its report are not part of the API core calls, and core regenerates
  nothing.
