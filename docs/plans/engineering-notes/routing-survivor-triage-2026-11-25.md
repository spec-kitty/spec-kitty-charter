---
title: "Routing survivor triage, 2026-11-25"
description: The verdict on every :routing mutant that survived after #655's tests, so a later run can tell a known equivalent from a new gap (ADR-0062 decision 7).
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0062-nightly-mutation-floor
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you triage a :routing survivor; the five equivalents below are known.
---

# Routing survivor triage, 2026-11-25

The triage of every `routing.domain` mutant that survived `:routing:pitest` after #655 (#644, ADR-0062 decision 7).
The first measurement of `:routing` (weekly run 45101937079) scored 83.5 % against its floor of 90. The operator ruled
on 2026-11-25 to keep 90 and add the missing tests. Main code is unchanged; no test method was added: two existing
tests gained assertions, one renamed (`aBlankTitleIsRefused` to `aBlankTextIsRefused`).

## Before and after

`:routing:pitest`, whole module, narrowed local run under the tuning exception (ADR-0062, amendment of 2026-11-20):

| | Detected / mutants | Score | Survived | No coverage |
|---|--:|--:|--:|--:|
| Before (weekly run 45101937079) | 66 / 79 | 83.5 % | 12 | 1 |
| After (#655, dispatch 45652654509) | 74 / 79 | 93.6 % | 5 | 0 |

The margin is 2 mutants: `detected − ⌈90 × 79 / 100⌉` = 74 − 72.

## Verdicts

- **killed**: killed by a strengthened existing test.
- **equivalent (analytical)**: no input can tell the mutant from the original, for the reason given.

| Class | Line | Mutant | Verdict |
|---|--:|---|---|
| `ModelCost` | 36 | removed `ifPresent(Checks::notNegative)` on the output price | killed: `theBoundsAreAdmitted` refuses a negative output price |
| `ModelCost` | 37 | removed `ifPresent(Checks::text)` on the currency | killed: `aBlankTextIsRefused` |
| `ModelCost` | 38 | removed `ifPresent(Checks::text)` on the pricing source | killed: `aBlankTextIsRefused` |
| `ModelEntry` | 48 | removed `ifPresent(Checks::text)` on the family | killed: `aBlankTextIsRefused` |
| `CatalogSource` | 33 | removed `ifPresent(Checks::text)` on the license notes | killed: `aBlankTextIsRefused` |
| `TaskType` | 34 | removed `ifPresent(Checks::text)` on the description | killed: `aBlankTextIsRefused` |
| `TaskFit` | 30 | removed `ifPresent(Checks::text)` on the rationale | killed: `aBlankTextIsRefused` |
| `RoutingCatalog` | 51 | removed `ifPresent(Checks::text)` on the description | killed: `aBlankTextIsRefused` |
| `Checks` | 42 | `id` returns `""` | equivalent (analytical): every caller calls `Checks.id` as a statement and ignores the return; the guard runs before the return |
| `Checks` | 47 | `text` returns `""` | equivalent (analytical): as `id`; callers call it as a statement or through `ifPresent`, which discards the result |
| `Checks` | 52 | `dateTime` returns `""` | equivalent (analytical): as `id`; `RoutingCatalog` keeps its own `generatedAt` |
| `Checks` | 66 | `unit` returns `null` | equivalent (analytical): `TaskFit` and `RoutingWeights` call it as a statement |
| `Checks` | 71 | `notNegative` returns `null` | equivalent (analytical): its only caller is `ifPresent`, which discards the result |

**Equivalent mutants still count as SURVIVED in PIT's score**, so the five stay in the denominator. Making the guards
`void` would remove them; it is left as is, as a change of main code that no test needs.
