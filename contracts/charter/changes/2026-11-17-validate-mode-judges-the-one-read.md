### Breaking

- Validate mode (`pack-validation`'s `PackCheck`, the pack doctor's check) judges the one read of the pack that the
  registration judges, so it answers the faults only the registration answered before (#554, operator rulings R2 and
  S6; R2 rows 24, 29 and 30 in `docs/development/notes/pack-code-unification.md`):
  - A pack with no `pack.yaml` is refused: one `pack_unreadable` finding at `pack.yaml`, `pack <name> has no
    pack.yaml`, first among the pack's findings (row 24). Core reports nothing for a missing descriptor: DIV-028.
  - A manifest that lists a file the pack does not hold, or leaves out one it holds, is `manifest_membership` at the
    manifest, in the registration's words, when nothing else of the pack is a violation (row 29).
  - A manifest that is not a regular file is `unreadable`, `<manifest>: the manifest is not a regular file` (row 30).
- Core's seam: a client of the pack doctor that treated a pack without `pack.yaml` as valid reads the refusal above.
