### Changed

- `registerPackFromArchive` (`PUT /packs/{packId}`), `getPack` (`GET /packs/{packId}`) and `getPackRegistration`
  (`GET /pack-registrations/{registrationId}`) are built (ADR-0065, FR-001, FR-024, FR-037, FR-039; PR A of the layer
  merge mission): they lose `x-reserved` and their `501`, and the three `501` pins of
  `scenarios/registration/reserved-registration.feature` go with them. `POST /packs` and the two `DELETE`s are built by
  their own changes (`2026-11-07-a-pack-source-served.md`, `2026-11-08-a-git-source-served.md`,
  `2026-11-07-a-removal-served.md`), so at the end of PR A no operation of the `pack-registration` set is reserved. The `PUT` answers `202` with a pending `PackRegistrationStatus` and `Location` after the synchronous
  checks (the `packId` grammar `400` before the body is read, both validators `400`, a declared length over 32 MiB
  `413` before a byte is read and a chunked body counted while it streams, a media type that does not match the leading
  bytes `415`, a stale `If-Match` or an `If-None-Match: *` over an existing pack `412`, registration switched off
  `503 pack_source_unavailable`, a full queue `503` with `Retry-After`); the job runs the flow behind it, and a registry
  lock that is not free in time ends the job `refused` with `503`.
- `CharterRefusalCode`: `registration_not_found`, `pack_source_unavailable`, `registration_timed_out` and
  `pack_storage_full` are `raised: true`. The `x-refusal` text no longer says that a reserved operation's codes stay
  `raised: false`: a code's flag follows what the service names (`pack_source_refused` and the two `DELETE` codes are
  raised by the changes that build their operations).
- A `Problem` with `packs` and `violations` together: `artifact_referenced` lists the referring packs (at most 100) as well
  as the referring artifacts.
- Where the status answers a pack that is built in: `getPack` answers `404 pack_not_found` for a built-in pack, which is
  read-only and has no registration summary (`PackRegistration.level` is `org` or `project`).
