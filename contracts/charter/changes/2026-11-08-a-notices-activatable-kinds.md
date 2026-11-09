### Changed

- `PackRegistrationNotices` says in its description that notices name activatable kinds only: an entry of another kind
  (a template, an asset, a guard, a lifecycle) is left out of the lists, as it already was, and the service now logs it
  at WARN with its URN. No schema change. Widening the kinds is tracked in issue #417.
