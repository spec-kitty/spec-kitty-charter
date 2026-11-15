# Decision Moment `01M7FFDY3YP3VA3EA5VGRZF28V`

- **Mission:** `pack-closure-rulesets-01M7FA90`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.registration-contents`
- **Input key:** `registration_judged_contents`
- **Status:** `open`
- **Created:** `2026-11-15T11:28:09.854840+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which contents does registration's PACK run judge? (a) the inspector's PackContents of the staged tree, with the inspector gaining the facts it lacks (domain value-object breaches, reference faults); the store's reader then only builds the aggregate, and a fault it finds that PACK missed fails closed. (b) PackContents built by an adapter from the store's own read: no second read, but two derivations of PackContents kept equal by hand.

## Options

- (a) inspector contents of the staged tree; inspector gains the missing facts
- (b) adapter from the store read
- Other

## Final answer

_(none)_

## Rationale

_(none)_

## Change log

- `2026-11-15T11:28:09.854840+00:00` - opened
