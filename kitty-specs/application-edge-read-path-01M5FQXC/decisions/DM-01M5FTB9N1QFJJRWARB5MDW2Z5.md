# Decision Moment `01M5FTB9N1QFJJRWARB5MDW2Z5`

- **Mission:** `application-edge-read-path-01M5FQXC`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.charterpack-reconciliation`
- **Input key:** `charterpack_reconciliation`
- **Status:** `resolved`
- **Created:** `2026-10-21T18:07:34.049057+00:00`
- **Resolved:** `2026-10-21T18:57:44.808561+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Tactics-only is in scope, but pack-listing + action-context endpoints run on CharterOffering/CharterPack and their new models aren't ruled. How to reconcile with 'delete CharterPack outright'?

## Options

- Delete CharterPack + retire the not-yet-modeled endpoints
- Keep CharterPack as bounded interim for those endpoints only
- Other

## Final answer

Delete CharterOffering/CharterPack/CharterPackRepository/FileSystemCharterPackRepository outright AND retire the pack-listing (PacksController/PackListingQuery) and action-context (ContextController/ActionContextQuery) endpoints plus ConsistentLoad, since their Pack-model read model is not yet ruled. Tactic retrieval becomes the only live read endpoint this mission; InfoController/ServiceInfoQuery stays. One canonical aggregate root, no coexistence. Removing those endpoints is an inbound REST contract change (handle the OpenAPI contract + regeneration in plan). pack-listing + action-context are a tracked follow-up mission once their models are ruled.

## Rationale

_(none)_

## Change log

- `2026-10-21T18:07:34.049057+00:00` — opened
- `2026-10-21T18:57:44.808561+00:00` — resolved (final_answer="Delete CharterOffering/CharterPack/CharterPackRepository/FileSystemCharterPackRepository outright AND retire the pack-listing (PacksController/PackListingQuery) and action-context (ContextController/ActionContextQuery) endpoints plus ConsistentLoad, since their Pack-model read model is not yet ruled. Tactic retrieval becomes the only live read endpoint this mission; InfoController/ServiceInfoQuery stays. One canonical aggregate root, no coexistence. Removing those endpoints is an inbound REST contract change (handle the OpenAPI contract + regeneration in plan). pack-listing + action-context are a tracked follow-up mission once their models are ruled.")
