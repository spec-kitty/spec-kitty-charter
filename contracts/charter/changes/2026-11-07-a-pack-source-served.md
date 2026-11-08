### Changed

- `registerPackFromSource` (`POST /packs`) is built for the `https_archive` and `filesystem` source kinds (ADR-0065 D10,
  FR-004, NFR-004, NFR-005, NFR-012; PR A of the layer merge mission): it loses `x-reserved` and its `501`, and the `501`
  pin of `scenarios/registration/reserved-registration.feature` goes with them (the `DELETE`s are built by
  `2026-11-07-a-removal-served.md`). It
  answers `202` with a pending `PackRegistrationStatus` and a `Location` after the synchronous checks (registration and
  the kind's own flag switched on, the `PackSource` schema, both validators, a stale `If-Match`, a full queue); the fetch
  runs in the job, outside every lock, and a successful result carries `source` (`kind` and `url` or `path`, as sent).
  A source the service refuses ends `refused` with `422 pack_source_refused`, the detail of whose problem starts with one
  reason of a fixed set (`host_not_allowed`, `address_not_global`, `url_refused`, `redirect_refused`, `wrong_media_type`,
  `too_large`, `digest_mismatch`, `name_mismatch`, `source_malformed`, `path_refused`), never raw upstream text; an
  upstream failure ends `refused` with a plain `502` and an upstream timeout with a plain `504`. A `filesystem` source
  refuses every path it will not read, a missing one, a link or a file with another hard link, with the same
  `path_refused`. A `git` source is served by the git source change of WP18 (`2026-11-08-a-git-source-served.md`).
- `CharterRefusalCode`: `pack_source_refused` is `raised: true`. `errors.feature` lists it among the codes an operation
  raises.
- `PackSourceRecord.url` is the URL without its query, its fragment and its user information (WP18, WP17 hand-off): the
  status holds a registration for an hour, and a presigned URL's signature was readable by every holder of it. The audit
  locator already cut them. `source.path`, `source.ref` and `source.kind` are unchanged.
