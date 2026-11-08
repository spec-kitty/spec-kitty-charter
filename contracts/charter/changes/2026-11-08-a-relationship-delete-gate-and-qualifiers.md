### Changed

- `deletePackRelationship` gates the removed edge as a `RELATIONSHIP` write on its source, as a whole-pack replace
  that drops the edge is judged (DM-01M6TR1KDAJ64GMJD6AYNZKT59 item 4): a `409 governance_policy_violation` when the
  mutability level of the pack that owns the source does not permit it. The `409` was already listed for the
  operation (for `relationship_not_registered`); its description now names the second code, and the code needs no
  new status. The `from` and `to` descriptions say that a pack-qualified reference matches only an edge end that
  names the same pack (with the edges `A` to `base::X` and `A` to `other::X`, naming `base::X` removes one edge), and
  that a reference that names no pack matches by its URN, as before.
