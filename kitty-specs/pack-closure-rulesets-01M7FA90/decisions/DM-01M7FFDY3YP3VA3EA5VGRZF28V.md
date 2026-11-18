# Decision Moment `01M7FFDY3YP3VA3EA5VGRZF28V`

- **Mission:** `pack-closure-rulesets-01M7FA90`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.registration-contents`
- **Input key:** `registration_judged_contents`
- **Status:** `resolved`
- **Created:** `2026-11-15T11:28:09.854840+00:00`
- **Resolved:** `2026-11-15T18:10:40.932064+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which contents does registration's PACK run judge? (a) the inspector's PackContents of the staged tree, with the inspector gaining the facts it lacks (domain value-object breaches, reference faults); the store's reader then only builds the aggregate, and a fault it finds that PACK missed fails closed. (b) PackContents built by an adapter from the store's own read: no second read, but two derivations of PackContents kept equal by hand.

## Options

- (a) inspector contents of the staged tree; inspector gains the missing facts
- (b) adapter from the store read
- Other

## Final answer

(a) Registration's PACK run judges the inspector's PackContents of the staged tree. The inspector gains the facts it lacks (domain value-object breaches, reference faults). The store's reader only builds the aggregate; a fault it finds that PACK missed fails closed as validation_failed, pinned by a test. (operator, 2026-11-15)

**Superseded in part by S1–S3 (2026-11-16), see research/squad-shaping.md.** S1: one reader produces the facts
every surface judges, and the aggregate is assembled from that read, so the store's second read and the fail-closed
guard go. The one read is a `TreeRead` (`PackContents`, `TreeCensus`, read faults); see data-model.md.

**S6 note (2026-11-16), see research/squad-shaping.md ("Port, no rule change").** Ruling 1's port that
`packs.domain` declares returns for the read only: registration judges the one read through the domain port
`PackReading`, which `FileSystemPackInspector` implements. The core's rules are plain rulesets with no judging port;
S6 adds one reading port (`PackReading`) whose readout carries the storage edge's verdict. `PackReading` replaces
`PackInspector`, and no ArchUnit rule is widened.

## Rationale

_(none)_

## Change log

- `2026-11-15T11:28:09.854840+00:00` - opened
- `2026-11-15T18:10:40.932064+00:00` - resolved (final_answer="(a) Registration's PACK run judges the inspector's PackContents of the staged tree. The inspector gains the facts it lacks (domain value-object breaches, reference faults). The store's reader only builds the aggregate; a fault it finds that PACK missed fails closed as validation_failed, pinned by a test. (operator, 2026-11-15)")
