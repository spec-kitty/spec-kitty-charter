### Removed

- The response components `ActionNotFound` and `NotImplemented` (issue #312): no operation referenced either, so no
  bundle held them and no check judged them. `ActionNotFound` named `mission_type_not_found` and `action_not_found`
  for the action-context read #139 removed; those codes are raised through `MissionStepNotFound` and the activation
  step reads. `NotImplemented` was kept for a reservation to come, and no operation is reserved today; a reserved
  operation declares its 501 when it is reserved. The example `CharterRefusal.action-not-found` stays: three responses
  use it. A contract test, `ReachableContractFilesTest`, now fails on any definition file no `$ref` from `openapi.yaml`
  reaches. No operation changes; core's seam changes nothing.
