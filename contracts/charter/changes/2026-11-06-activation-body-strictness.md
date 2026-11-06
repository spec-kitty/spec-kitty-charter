### Changed

- An activation body that states a member twice in one object is `422 activation_invalid` with the violation
  `duplicate_member` at the member's pointer; the last occurrence used to win without a word. A scalar of another type
  than the one declared (`"1"` or `1.0` for `schemaVersion`, a number or a boolean in an id list, a number for a pinned
  version) is `422 activation_invalid` with `unreadable_body` at its pointer; the service used to coerce it.
  `ActivationInvalid` says so, and `activation-register.feature` pins both, and now also the pointer and code of
  `unknown_member`. Core's seam: send each member once, and a JSON value of the declared type.
