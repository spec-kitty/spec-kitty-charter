### Changed

- A `drg/fragment.yaml` node whose `body_path` leaves the pack root is refused (#254). The path is judged as written:
  absolute in POSIX or Windows form (a drive or UNC root), or with a `..` part. It is a graph fault of the fragment: a
  registration answers `422 validation_failed` with a `drg_schema_error` violation at `drg/fragment.yaml`, detail
  `node <id> body_path '<path>' escapes the pack root` (example `PackRegistrationStatus.fragment-body-path-escape`),
  and a composing read of a closure that holds the pack answers `500 pack_unreadable`. The description of
  `drg_schema_error` names the rule. A pack that carried such a path was accepted before and is refused now; no
  example pack does. Core's seam: nothing; no code is added.
