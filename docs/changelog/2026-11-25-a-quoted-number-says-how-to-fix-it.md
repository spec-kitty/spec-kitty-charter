---
okf_version: "0.2"
title: A quoted number in a number field says how to fix it, and remediate repairs it
description: A pack refused for "15" where a number field takes 15 now says to write it without quotes, and pack-doctor remediate unquotes it; the service still accepts only the number.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/check-packs
  - changelog/2026-11-22-pack-doctor
audience:
  - profile: pack-author
    guidance_text: Read this if a pack is refused because a price, a score or a priority is written in quotes.
---

# 2026-11-25 — A quoted number in a number field says how to fix it, and remediate repairs it

**A quoted number gets a fix, not only a refusal** (operator ruling 2026-11-25, #651). A routing catalog's price written
`input_per_1m_usd: "15"` is a string where the schema takes a number. It was refused with
`'input_per_1m_usd' must be a number`, which did not say what to change, and `pack-doctor remediate` refused the pack
too.

## What changed

- **The refusal says how to fix it.** The violation keeps its code and its pointer, and its detail now reads:

  ```text
  routing_catalogs/cost-led.routing-catalog.yaml: 'input_per_1m_usd': "15" must be a number: write 15 without quotes, or run pack-doctor remediate
  ```

  An `integer` field says `must be an integer`. The registration, every write and `pack-doctor validate` give the same
  detail.
- **`pack-doctor remediate` repairs it.** It writes the value in place of the whole value as written (its quotes, an
  escape, a `!!str` tag or a block scalar), in the file's canonical form (`"1e-3"` ends as `0.001`), and restamps the
  manifest, a read-only file included. Its outcome line counts the repairs, and one line under it names each:
  `repaired at <file>#<pointer>: "15" is now a number` (`is now an integer` in an integer field). The repairs are in
  the printed lines only; the report's JSON has no field for them.
- **Only a real number is repaired.** A value that, written plain, is a finite number of the field's type gets the fix;
  an integer field takes integers only. `"fifteen"`, `"1.5"` in an integer field, any other fault beside the quoted
  numbers, and a number the field's range refuses once it is plain all still refuse the pack, and remediate writes
  nothing; the refusal says whether remediate repairs a quoted number once the rest is fixed, or why it cannot (an
  anchor, an alias). A quoted string in a field typed `string` is never touched, and a quoted number in `policy.yaml`
  says `write 1 without quotes` alone.

## What did not change

The service accepts only the number. `15` and `15.0` stay equally valid; a quoted `"15"` is still refused, and the round
trip still tells a string from a number. The schema is unchanged, and the violation keeps its code: no refusal code is
added or changed.

See [Check packs](/guides/check-packs/#repair-a-pack).
