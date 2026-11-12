### Added

- The switch-gate register, `scenarios/switch-gate.yaml` (ADR-0059 D1, amended 2026-11-12; issues #9 and #72). It
  says, per operation, whether core may switch to it: `ready` when every status its responses declare is asserted on
  it by a `@contract` scenario, or `held` with the `statuses` it is held for and a reason. The contract suite fails
  when the register and the scenarios disagree, and writes a per-operation report of the scenarios of each answer. 44
  operations are ready; `deleteActivation` and `getPackRegistration` are held, because their declared `500` is a failure of storage
  the scenarios cannot fail. The register ships in `charter-scenarios.tar.gz`; changing its keys, or moving an
  operation from ready to held, is a breaking change.
- Scenarios that assert, on the operation itself, each declared answer no scenario asserted there before:
  `every-operation.feature` (the worktree guard's `401` and `403` on all 46 operations), `unexpected-failure.feature`
  (`500 internal_error` on the 40 operations whose declared 500 a pack read can raise), `activation-write-refusals.feature`,
  `pack-write-refusals.feature`, `read-refusals.feature` and `registration/pack-source-refusals.feature`. The service's
  answers are unchanged.
