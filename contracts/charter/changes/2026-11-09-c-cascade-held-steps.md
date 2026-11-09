### Changed

- The write cascade follows only held steps (operator ruling OD-9, DIV-242): a mission type reaches `action:M/s` when a
  pack of the closure defines `mission_type:M` and declares `action:M/s`. An `action:M/s` declared where no pack defines
  `mission_type:M` is no longer selected, so `applied` and `selected` can differ from before on the five per-artifact
  writes (`selections`, `exclusions` and `presets`). No operation, schema or response shape changes. The effective
  reads' tag domain moved to v2, so an `ETag` a client cached before misses once and the read answers `200`.
