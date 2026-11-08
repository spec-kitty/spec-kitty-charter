### Breaking

- The per-artifact writes run the registration impact check (IC-15; FR-025; operator ruling
  DM-01M6VG6GC40AKYYFRR35SV8HVW): `registerPackDirective`, `putPackDirective` and the same two of a paradigm, a
  styleguide and a toolguide, and `putPackRelationship`, judge every closure the pack joins as it will be after the
  write, under the registry lock, as a registration of the whole pack does. A write that would leave a closure unable to
  compose (two packs of one level defining one id is `pack_sibling_conflict`) or breaking a relation rule
  (`override_not_permitted`, `enhances_cycle`, `conflicting_relations`, `self_override`, `override_cycle`) is refused
  `422`, naming the closure's root pack and the packs involved, and writes nothing. Such a write answered `201` or `200`
  before, so a documented success becomes a refusal; core's seam must expect these `422`s from the per-artifact writes
  as from a registration. The `422` description of each of the nine operations says so, and the `operations` map of the
  eleven composition codes in `CharterRefusalCode` lists the nine operations at `422` (a composition code is `500` on a
  read). Nothing is edited in core by this change (C-003).
