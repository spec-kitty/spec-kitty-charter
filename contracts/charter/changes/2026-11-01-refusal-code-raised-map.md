### Changed

- `CharterRefusalCode`'s `x-refusal` entry for `governance_policy_violation` says `raised: true` (issue #293): the
  registration writes (the directive, paradigm, styleguide and toolguide writes and `registerRelationship`) refuse a
  write the mutability level of the pack that owns its target does not permit with it, 409 (ADR-0012). No policy file
  is read yet, so every target is open and no write is refused with it until one is; the schema description says so.
  The 409 descriptions of those writes name it beside `registration_conflict`, and the item writes, which raise no
  `registration_conflict` outside the toolguide's guide file, no longer promise a duplicate id. `drg_schema_error`
  stays `raised: false`: the whole-pack load raises it, and no operation reaches that load. The schema description
  now says what `raised` means: an operation of this version reaches the code that refuses with it. Scenarios: in
  `errors.feature`, the `@reserved` declaration pin "governance_policy_violation is reserved" becomes "is raised by
  the registration writes (#293)", and the raised-codes table gains `governance_policy_violation`. No enum value
  changes; core's seam changes nothing.
