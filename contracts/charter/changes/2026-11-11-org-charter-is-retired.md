### Breaking

- `org-charter.yaml` is retired, and `pack.yaml` loses `active_charter` (#431, which also settles #381). A pack that
  ships `org-charter.yaml` at its root is refused at registration with `422 validation_failed` and violation code
  `retired` at `org-charter.yaml`, naming the rule and its reason (never `unmodelled`); so is a root file that differs
  from it only in letter case (`Org-Charter.yaml`). A pack already stored before this change with one keeps it until
  it is registered again. Until then the reads of the pack's descriptor, revision, document and of one kind's files (`getPack`, `/doc`, the artifact
  listing, a directive's item) still answer; the reads that load its whole aggregate do not. The step contract item
  read answers `404 pack_not_found`, as for any pack whose aggregate cannot be read after its `pack.yaml` was read;
  a read that composes the pack with the others (`GET /offering`, the activation and effective reads) answers
  `500 pack_unreadable`, whichever pack it was asked about. The descriptor is closed, so a `pack.yaml` that declares `active_charter` is refused as an unknown
  key: every read that reads the descriptor (`getPack`, `/doc`, the artifact listing and item reads) answers that pack
  `500 pack_unreadable`, and so does every composed read, and a registration refuses it with `422 validation_failed` and one
  `pack_unreadable` violation at `pack`; both details name the undeclared key. The `org-charter` schema, with its reserved `skill_namespace`, is gone. Org
  enforcement is a governing activation preset whose items are must-have, plus the levels of `policy.yaml`. No alias,
  no fallback reader. Core's seam: stop reading `org-charter.yaml` and `active_charter`, and apply the governing
  preset's required floor, which the service already serves.
