### Changed

- `getActivation`, `replaceActivation`, `deleteActivation` and `listActivationPacks` declare `400` as the shared
  `Problem` response, as `listPackArtifacts` does. The service already answered it: an `activationId` outside its
  pattern, an `If-Match` that is neither a quoted revision nor `*`, and (on `replaceActivation`) both validators on one
  request or an `If-None-Match` other than `*`. Two scenarios in `activation-register-guards.feature` pin the first two.
  The behavior is unchanged.
