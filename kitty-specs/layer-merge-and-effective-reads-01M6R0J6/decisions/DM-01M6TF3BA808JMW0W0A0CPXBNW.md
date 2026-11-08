# Decision Moment `01M6TF3BA808JMW0W0A0CPXBNW`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruling-408.shared-walk`
- **Input key:** `cascade_depth_shared_walk`
- **Status:** `resolved`
- **Created:** `2026-11-07T07:38:19.848744+00:00`
- **Resolved:** `2026-11-07T07:38:22.315772+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

A bounded cascading deactivate: is the shared walk (what other active artifacts still reach) bounded by the same depth?

## Options

_(none)_

## Final answer

No: the shared walk stays unlimited. A candidate another active artifact still reaches over any number of suggests hops is kept (skippedShared). A cascading deactivate at depth N therefore removes no more than a select at depth N reached, but may keep an artifact the select did not add when another active artifact suggests it; ExclusionRequest's round-trip text says so, and a scenario pins the case.

## Rationale

Plan squad finding 3 (2026-11-07). The write never drops an artifact another active artifact still reaches; the same gap exists today for a non-cascading select. Bounding the shared walk would make what is kept depend on the request's depth rather than on the document.

## Change log

- `2026-11-07T07:38:19.848744+00:00` - opened
- `2026-11-07T07:38:22.315772+00:00` - resolved (final_answer="No: the shared walk stays unlimited. A candidate another active artifact still reaches over any number of suggests hops is kept (skippedShared). A cascading deactivate at depth N therefore removes no more than a select at depth N reached, but may keep an artifact the select did not add when another active artifact suggests it; ExclusionRequest's round-trip text says so, and a scenario pins the case.")
