### Changed

- The `offering.feature` scenario "The packs are ordered by configured level, then by pack id in code-point order"
  configures its packs under pack ids (#583, FR-010). It configured `Zeta`, `Omega` and `Kilo`, which are no pack ids:
  the service now refuses such a directory on read (`pack_id_invalid`), so it would never be offered. They are
  `a-zeta`, `b-zulu` and `c-kilo`, and the scenario shows that a hyphen sorts before a letter by code point. The
  `Offering` schema and `getOffering` descriptions say "a-zeta comes before alpha". The order rule is unchanged.
