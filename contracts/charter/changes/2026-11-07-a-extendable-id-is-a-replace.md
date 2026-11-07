### Breaking

- The write gate treats an id that a pack below the written pack already holds as a replace of that pack's artifact, on
  both entry points: the whole-pack registration (`registerPackFromArchive`, `registerPackFromSource`, still reserved) and
  the per-artifact registrations (`registerPackDirective`, `putPackDirective` and the other kinds' `registerPack*` and
  `putPack*`, which are served). The level is that of the holder's policy, scoped to what the holder and the packs it builds
  on hold (A15), and the written pack's own policy never judges it (B8). `locked` and `extendable` refuse it with `409
  governance_policy_violation`; `open` allows it. Until now a pack built on another could register an id the other pack
  declared `extendable` (the gate saw a registration, which `extendable` permits), so a per-artifact `POST` of such an id
  that was accepted is now refused (decision 01M6VEDYX8TQ4AKCP7C19WAP75, amending 01M6VD94VEVPSC1JXQ1RA7YYHQ). No scenario
  relied on overriding an `extendable` ancestor id, so none changed its expectation. The code and its status are the
  existing ones; the refusal text names the replace.
