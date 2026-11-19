# Issue draft for spec-kitty/spec-kitty-open-packs

**Placeholder:** `open-packs#TBD-pack-entry-linked`. The orchestrator files this issue in
`spec-kitty/spec-kitty-open-packs` and replaces the placeholder in charter's records before the closing pull request
(#608) merges (issue-tracker Rule 4, charter.md "Orchestrating cloud sessions"). This file is a draft only: nothing was
filed from the Mission. Written 2026-11-19 by WP08 of mission `pack-check-cli-01M7SASC` (spec-kitty-charter#478).

**Title:** `pack_entry_linked`: charter refuses a symbolic link inside a pack; teach `validate_pack_schemas.py` the code,
or retire it for `pack-check` (open-packs#252)

**Type:** `Task`

**Labels:** `follow-up`, `priority:P3`

**Milestone:** none (issue-tracker Rule 3: an auto-filed follow-up has no milestone until the operator schedules it)

**Refs:** spec-kitty-charter#478 (rulings D1 and D6), spec-kitty-charter#597, open-packs#252.

---

## Problem

The charter now refuses a symbolic link inside a pack with the new violation code `pack_entry_linked`
(`validation_failed`, `422`). No read follows a link: a link to a file, a directory or nothing is refused, one violation
per link, at the link's pack-relative path, in the words `<path>: is a symbolic link; a pack holds no links`. It is
refused on every surface that reads a pack:

- **Registration:** a staged tree that holds a link is refused, and nothing is stored (spec-kitty-charter#597).
- **Load:** the service's load of a pack in a configured directory refuses a pack that holds a link; the composing
  read answers `pack_unreadable` naming the link.
- **pack-check:** validate mode reports the same violation, so the `pack-check` CLI (spec-kitty-charter#478) refuses
  the pack as registration does.

The pack root itself may still be a link. Hard links and Windows junctions are not symbolic links and are out of this
rule.

The charter's record of the change is the contract fragment
`contracts/charter/changes/2026-11-19-pack-entry-linked.md` (`### Breaking`) and the code `pack_entry_linked`, named under
`validation_failed` in `contracts/charter/schemas/CharterRefusalCode.yaml`. In the service, the rule is `LinkRules` and the code is
`StorageCodes.PACK_ENTRY_LINKED`.

open-packs' Python mirror of the charter's classification and schema pre-check, `validate_pack_schemas.py`, does not
know the code. A pack that holds a link would pass the pre-check and be refused by the charter.

## Ask

Do one of these:

1. **Retire the mirror** in favor of `pack-check` at the charter pin, as open-packs#252 plans. The first charter
   release that carries the `pack-check` jar is `service-v0.1.0-rc.2` (ruling D4 of spec-kitty-charter#478). This
   issue then closes with #252.
2. **Teach the mirror the code:** `validate_pack_schemas.py` refuses a symbolic link below a pack root with
   `pack_entry_linked`, at the link's pack-relative path, and does not follow it. The pack root itself may be a link.

## No pack holds a link today

The D6 check, made on 2026-11-19 for ruling D6 of spec-kitty-charter#478, found no symbolic link inside any
open-packs pack. open-packs has 76 committed symbolic links, all outside its packs:

- 72 under `.agents/`;
- 2 under `.github/`;
- `CHANGELOG.md`;
- `CONTRIBUTING.md`.

No pack migrates. The work is the mirror only.
