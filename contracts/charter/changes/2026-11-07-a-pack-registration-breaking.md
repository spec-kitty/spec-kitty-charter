### Breaking

- Registering a pack replaces the whole pack (ADR-0065 D1, D2; PR-PACK-REGISTER-003): two sources that claim one pack id
  no longer refuse, the later registration replaces the stored pack under the identity checks (a replace keeps the
  `pack_id` and the level; a new pack may not take the `pack_id` or the name of another configured pack). The old
  `@WIP` promise `registration/register-pack.feature` that said the second claim is refused with `SOURCE_CONFLICT` is
  retired, and `whole-pack-registration.feature` states the replace. Registration is asynchronous: `PUT /packs/{packId}`
  and `POST /packs` answer `202` and a status resource, not `201 Created` (ADR-0019 decision 5 is amended), and the `409`
  and `422` answers of the flow are the `problem` of a `refused` status. ADR-0026 decision 2 ("no `DELETE`") is
  superseded: the contract gains two `DELETE` operations, which `Link`'s description (a pack write is never `DELETE`)
  is to follow. Core's seam: core must not wait for `SOURCE_CONFLICT`, must poll `GET /pack-registrations/{id}` instead
  of reading a `201`, and must send `If-Match` on a `DELETE`. Nothing is edited in core by this change (C-003).
