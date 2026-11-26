### Changed

- A pack with many violations no longer costs time quadratic in their number (issue #648). Four steps passed over
  every violation once per violation: dropping the `null` branch of each nullable place, judging each non-finite
  number (`.nan`, `.inf`), matching each relationship that joins an artifact to itself against the violations already
  stated, and placing each such fault in the order the load records it. A tactic of 20 000 steps that each break a
  nullable string took 44 s under `pack-doctor validate`, and a whole-pack registration held its request thread past
  10 s before refusing it; a graph document of 14 000 self-joins beside four such tactics took about 50 s (46–53 s).
  Each of these packs now takes about as long as reading its files. The violations, their order and their wording are
  unchanged, so the first 100 that a registration or `pack-doctor validate` lists are the same. Core needs no change:
  the contract and schemas are unchanged.
