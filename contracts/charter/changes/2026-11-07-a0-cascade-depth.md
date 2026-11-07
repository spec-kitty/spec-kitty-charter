### Added

- A cascading select and a cascading deactivate take an optional `depth`: the number of `suggests` hops the walk may
  cross (IC-21, FR-043; issue #408 ruling 1, decision `DM-01M6TDH0SY5Q224GX709DXWG8K`). `SelectionRequest` and
  `ExclusionRequest` gain `depth` (integer, `format: int32`, `minimum: 0`). Absent means unlimited, exactly as before
  (ADR-0059 D2), so a client that does not send it sees no change.
  - Only `suggests` is counted, as ADR-0018 D4's `depth` counts it on the action-context walk. `requires`, the other
    hard relations, `enhances`, the `specializes_from` pass-through and a mission type's steps are walked in full.
    `depth: 0` follows no `suggests` edge; `depth: 1` follows one, and so on. An artifact the walk reaches over
    `requires` is selected at any depth, even when a `suggests` path also reaches it. Unlike ADR-0018 D4's read
    `depth`, this one has no default and no maximum.
  - A cascading deactivate honours `depth` in the target's own walk, so a deactivate at depth N undoes a select at
    depth N in what it removes. The walk of the other active artifacts, which decides what is kept, stays unlimited
    (decision `DM-01M6TF3BA808JMW0W0A0CPXBNW`), so the round trip is not exact when another active artifact reaches
    a candidate over `suggests`: that candidate is kept and reported in `skippedShared`. `ExclusionRequest.cascade` no
    longer says the two round-trip without that exception.
  - `SkippedReport` gains the optional `skippedByDepth` (a list of `ArtifactReference`): the `suggests` targets one
    hop past the bound, present only when a cascade and a `depth` were sent, so a client knows whether a deeper `depth` would reach
    more.
  - `depth` without `cascade` is accepted and has no effect. A `depth` that is not an integer (`"1"`, `1.5`) is
    `422 activation_invalid` with the violation `unreadable_body` at `/depth`; a negative one is `422 activation_invalid`
    with the violation `below_minimum` at `/depth`, and one past the int32 range with `above_maximum`.
  - A new client that sends `depth` to an older server gets `422 unknown_member` at `/depth`: the older body guard
    refuses a member its schema does not declare.
