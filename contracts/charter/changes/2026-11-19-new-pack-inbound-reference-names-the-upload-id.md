### Changed

- `artifact_referenced` names every referring pack by the id it is registered under, in each violation's `pointer`
  (`<packId>/<referrer>`) and in `packs[].packId`, a pack the registration stages included (#583, FR-008). The inbound
  scan named a staged pack the registry does not hold yet by its descriptor `name`, which may hold `_` and so is no
  pack id a client can address; it now names it by the id the upload targets. No answer the service gives changes
  today: a registration runs the scan only when it replaces a stored pack, whose id it already named.
