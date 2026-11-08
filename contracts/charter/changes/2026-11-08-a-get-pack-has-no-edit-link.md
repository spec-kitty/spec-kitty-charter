### Changed

- `getPack` no longer describes an `edit` link: its `_links` hold `self` and `artifacts`. The description and the
  examples `PackRegistration.stored` and `PackRegistrationStatus.succeeded` drop `edit`. The service has never sent
  it, and the contract names no `edit` relation for a pack (WP16 decision), so the whole-pack `PUT` is not advertised
  by a link. A description and example change only.
