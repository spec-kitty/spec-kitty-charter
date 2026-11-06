### Changed

- `PackPin.version` (and `ActivationConfig.schema.json`) declare `pattern: ^\s*\S.*$`: a pinned version that is only
  blanks is invalid. The service answered it `500`; it is now `422 activation_invalid` with the violation
  `invalid_value` at `/packs/{i}/version`. The corpus case `17-pack-version-blank` pins it. Core's seam: a blank
  `version` was never a version; send none to take the held one.
