### Changed

- The templated item read answers a `mission_step_contract` as it answers every other kind: a pack whose aggregate or
  revision cannot be read after its `pack.yaml` was read is `404 pack_not_found`, where a step contract answered
  `500 pack_unreadable`. A pack with no readable `pack.yaml` is still `500 pack_unreadable` for every kind.
