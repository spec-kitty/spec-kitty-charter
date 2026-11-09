### Breaking

- A step prompt's slots are checked (#424; Refs #428), so content that validated or registered before is now refused,
  naming the prompt file, at validate, at load, at registration and on a read: a slot marker that is malformed, repeated,
  nested or unpaired is refused with `prompt_slot_malformed` whatever the pack descriptor declares, and text between
  paired markers is refused with `prompt_slot_not_empty` in a pack whose descriptor declares `schema_version: "1.0"`.
  The pinned packs are unaffected: an unversioned descriptor admits the text with a warning.
- `PackRegistration` gains an optional `diagnostics` member (closed object), present in the result of a succeeded
  registration and listing load warnings, today only `prompt_slot_not_empty` for an unversioned descriptor; `getPack`
  carries none.
- A registration lists every such fault at once, each as a violation `{pointer: <pack-relative file>, code:
  prompt_slot_malformed | prompt_slot_not_empty}` of `422 validation_failed`. Every verified read of a closure holding
  such a pack answers `500 pack_unreadable`, naming the first file.
- core: nothing while it is not a consumer (epic #186).
