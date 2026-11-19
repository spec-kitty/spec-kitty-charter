---
title: "FR-009 inventory: pack-validation's main types and who uses them"
description: Each main type of pack-validation at WP04's head, what it does and its callers, showing that the CLI holds no check and pack-validation holds nothing that only renders for the CLI.
doc_status: active
updated: 2026-11-19T00:00:00Z
---

# FR-009 inventory (WP04, #478)

FR-009 asks that whatever `pack-validation` holds for the CLI alone lives in `pack-check`, and that the jar carries no
second copy of a check. This inventory lists every main type of `service/pack-validation` at WP04's head, with its
role and its callers in main and test code. No code moves.

## Reading the table

- **Judges** means the type decides whether a pack or a set fails. A check, so it belongs in `pack-validation` (the CLI
  holds none, FR-009).
- **CLI-only rendering** would be a type that parses arguments or words output for the CLI alone. There is none: every
  such type is in `pack-check` (`Arguments`, `ConfigRoot`, `Problems`, `ReportWriter`, `PackCheckMain`).
- The service runs no `pack-validation` main code (ADR-0075, amendment of 2026-11-16); `deployable` has the module on
  its test classpath only. So "main callers" outside the module are `pack-check` alone, or none.

## The types

| Type | Package | Role | Main callers outside the module | Test callers |
|---|---|---|---|---|
| `PackCheck` | `packvalidation` | Judges: validate mode over the one read, with the placement rules | none (`PackSetCheck` inside) | pack-validation, packs, deployable (`CharterWiringTest`) |
| `PackVerdicts` | `packvalidation` | Maps a `Rationale` to a `PackVerdict` | none (`PackCheck` inside) | pack-validation, packs |
| `LevelRules` | `packvalidation.placement` | Judges: the configured level, `pack_level_missing` | none (`PackCheck`, `PackSetCheck` inside) | pack-validation, deployable (ArchUnit) |
| `OrgFragmentRules` | `packvalidation.placement` | Judges: the org fragment | none (`PackCheck` inside) | pack-validation |
| `Placement` | `packvalidation.placement` | The placement rules' subject | none | pack-validation |
| `PlacementCodes` | `packvalidation.placement` | The placement codes, each built once | none (`packs.PackCodes` names it in javadoc only) | pack-validation, packs, deployable |
| `MeaningComparator` | `packvalidation.roundtrip` | Judges: equality of meaning (ADR-0036) | none | pack-validation, packs (codec oracle) |
| `RoundTripObservation` | `packvalidation.roundtrip` | The per-file observation the rule judges | none | pack-validation, packs |
| `RoundTripping` | `packvalidation.roundtrip` | The round-trip port the set check calls | pack-check (`PackCheckMain` supplies it) | pack-validation, deployable (ArchUnit) |
| `WholePackExpectation` | `packvalidation.roundtrip` | The expected-red baseline (`none()` for the CLI) | none | pack-validation, packs |
| `WholePackReport` | `packvalidation.roundtrip` | The gate's report over the baseline | none | pack-validation (gate and drift test) |
| `WholePackRoundTripRule` | `packvalidation.roundtrip` | Judges: `round_trip_red`, `round_trip_written_unpaired` | none (`PackSetCheck` inside) | pack-validation (gate, drift test), pack-check, deployable |
| `PackSetCheck` | `packvalidation.set` | Judges: validate mode per pack, `CLOSURE` over the set (D5), the D3 skip, the round trip | pack-check (`PackCheckMain`) | pack-validation, pack-check |
| `PackSetEntry` | `packvalidation.set` | One pack of a set; the one place its name is taken | pack-check (`Arguments`) | pack-validation, pack-check |
| `PackSetVerdict` | `packvalidation.set` | The set's verdict: per-pack results, faults, checks, skips, `failing()`, `unreportedRefusal()` | pack-check (`PackCheckMain`, `Problems`, `ReportWriter`) | pack-validation, pack-check |

## Finding

- No `pack-validation` type parses the command line or words the report: all of that is in `pack-check`. The jar holds
  no second copy of a check, since `pack-check`'s main code defines no rule; ArchUnit's direct half keeps it to the
  packs domain, the kernel and the two adapters its main plugs in.
- Four types have `pack-check` as their only main-code caller: `PackSetCheck`, `PackSetEntry`, `PackSetVerdict` and
  `RoundTripping`. Each judges, or is the input, output or port of a judgement (the set check). By FR-009's other half
  (the CLI holds no check) they belong in `pack-validation`, as ADR-0075's amendment of 2026-11-16 places "the pack-set
  check" there. FR-009's wording "no `pack-validation` type is CLI-only" holds for rendering, not for callers: those four
  have no other main caller because the service runs no `pack-validation` main code.
