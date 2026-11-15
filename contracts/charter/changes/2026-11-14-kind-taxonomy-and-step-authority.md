### Added

- `getKindTaxonomy`, `GET /offering/kinds` (R18, #112; ADR-0077 decision 1, operator ruling
  DM-01M7B1YE4J84K0T4ZA27M48QKG): the kind taxonomy of the charter's model, built from the kernel's enums and the
  same whatever packs are configured. `kinds` lists every node kind in the model's order (`KindRow`: `kind`, `plural`,
  `artifact`, `activatable`, `authoredInGraphs`, and a templated `artifacts` link for a kind the pack list serves);
  `relations` lists every relation, then `in_tension_with` (`RelationRow`: `relation`, `heldAs`, `directed`, `derived`
  and its allowed `{source, target}` pairs, `EndpointPair`). New schemas `KindTaxonomy`, `KindRow`, `RelationRow`,
  `EndpointPair`, `TaxonomyKind` and `TaxonomyRelation`; the header `KindTaxonomyETag`, a strong `ETag` that is the
  sha256 of the served body, with `304` on a matching `If-None-Match`; the example `KindTaxonomy.model`. It carries the
  `Charter-*` headers and no source fingerprint, and none of core's org facts. The taxonomy carries its own kind and
  relation enums, because `NodeKind` and `Relation` leave out the kinds and relations no graph read or relationship
  write names (guard, lifecycle, `implemented_by`, `in_tension_with`); a parity test keeps both equal to the domain.
  `ApiRoot` and `Offering` link it as `kinds`. The switch-gate register lists `getKindTaxonomy` as `ready`. Core's
  seam: read the kinds, plurals, flags and endpoint pairs from it instead of hand-kept lists.
