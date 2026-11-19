# Data model: pack-check

## PackCheckReport (the CLI's standard output; additive contract schema, D2)

| Field | Type | Rule |
|---|---|---|
| `checks` | array of `PACK` \| `CLOSURE` \| `ROUND_TRIP`, unique, in that order | The checks that ran over the whole set. `PACK` always; `CLOSURE` when every pack produced facts (D3); `ROUND_TRIP` with `--round-trip` |
| `packs` | array of `{ name, level?, path }` | Every pack checked, in argument order (or `packs/` name order for `--config-root`). `level` is the configured level, or the one `pack.yaml` declares; absent when neither is known |
| `skipped` | array of `{ check, reason }`, optional | Present when `CLOSURE` was skipped: `reason` names the packs without facts |
| `problems` | array of `Problem` (`contracts/_shared/schemas/Problem.yaml`) | Empty when valid. Order: per pack in `packs` order (that pack's `validation_failed`, then its round-trip problem), then the composition problems in the resolver's order |

Invariant: the exit code is `0` if and only if `problems` is empty; it is `1` otherwise. Exit `2` writes nothing to
standard output.

## Problem (as the API answers it)

- **A pack's faults:** one problem, `code: validation_failed`, `status: 422`, `title: "Validation failed"`.
  - `violations`: one per violation of the pack's verdict, `{ pointer: <pack-relative file>, code, detail }`, the
    detail in the one wording (`PackCodes.detail`).
  - Capped at 100; `detail` names the pack and ends with the count left out, as `RefusalHandler.leftOut` words it.
  - `instance`: the pack's path as given, so two packs' problems are told apart.
- **A composition fault:** one problem per `CompositionFault`, with its code (`pack_dependency_cycle`, …), the status
  the registration operation declares for that code, and `packs` (`RefusalPack`: `descriptorPackId`, `packId`,
  `name`). Its `detail` is the fault's.
- **A round-trip fault:** one `validation_failed` problem per pack whose round trip is red, with violations
  `round_trip_red` / `round_trip_written_unpaired` at their files.

## Warnings (standard error, never in the report)

One line each: `warning: <pack>: <code> <file>: <detail>`, for every `WARNING` reason (e.g. `file_not_held`).

## PackSetCheck (pack-validation)

- **Input:** an ordered list of `{ PackRoot, ConfiguredPack }` and whether to round-trip.
- **Per pack:** `PackCheck.check(root)` gives the readout and the `PackVerdict`. The readout's `facts` is
  `Built(PackCompositionFacts)` or `Unreadable(code, detail)` (any refusal the builder raises, `Assembly.None` as
  `pack_unreadable`), from the same read.
- **Over the set:** when every pack's facts are `Built`, `PackRulesets.closure(presetIdsOf)` over
  `PackUnderResolution(configured, descriptor, changed, defined, terms)`, with the preset ids from `facts.presets()`.
  It runs over the set as given (D5): a pack's dependencies must be in the set.
- **Otherwise:** `CLOSURE` is skipped (D3). An `Unreadable` pack whose verdict has no violation gets its refusal as
  its problem, so the exit is 1; a pack that already has a problem gets no second one.
- **Output:** a `PackSetVerdict` holding the per-pack verdicts, the composition faults, the checks run and the skips.

## Link rule (D1)

- **Subject:** the one read's census entries whose standing is `LINK` (a file or directory link below the pack root).
  The walk never lists a link among the regular files, so no read on any surface follows one (D6).
- **Finding:** `pack_entry_linked`, `VIOLATION`, at the link's pack-relative path, with detail
  `is a symbolic link; a pack holds no links`.
- **Surfaces:** registration and validate mode through the verdict; the configured load through the store half's own
  statement of the code (D6 A).
- **Scope:** the pack root itself may be a link and is entered as today (#174).

## Exit codes

| Code | When |
|---|---|
| 0 | No problem (warnings allowed) |
| 1 | At least one problem |
| 2 | Usage fault (no input, both inputs, unknown option, bad level, duplicate pack by real path, missing directory, empty config root, a dangling entry under the config root's `packs/`) an I/O failure outside a pack read (writing the output, the round trip's temporary directory), or an internal failure (an unexpected exception: `pack-check: internal error: …` and the stack trace on stderr) |
