# Draft issue for spec-kitty-open-packs, filed as spec-kitty-open-packs#316

**Title**: Migrate tension edges to charter's `tension` spelling, canonical orientation and one per pair

**Filed by**: the orchestrator, for charter #602 (ADR-0045, accepted 2026-11-18). Not filed by this session (C-004).

## Why

Charter's model now holds a tension as the relation `tension` (ADR-0045 decisions 1 and 2). The next charter release:

- no longer reads `in_tension_with`: a graph document that writes it names an unknown relation and the pack is refused;
- refuses a pack that holds two tensions on one unordered pair (`tension_duplicated`);
- refuses a pack whose tension has the side it owns as the target, or, when the pack owns both sides or neither, a
  `source` that is not the lexicographically smaller bare URN (`tension_orientation`);
- refuses a tension whose two sides have the same bare URN (`tension_self`).

## What

1. In every `*.graph.yaml` shard and `drg/fragment.yaml`, rename `relation: in_tension_with` to `relation: tension`.
2. Write each tension with the side the pack owns as `source`; when the pack owns both sides, or neither, the smaller
   bare URN (pack qualifier ignored) is the `source`.
3. Keep at most one tension per unordered pair in a pack; a `requires` (or any one structural relationship) beside it
   stays.
4. Bump the charter pin to the release that carries #602, in the same pull request, so open-packs CI validates
   against it (ADR-0070 decision 3).

## Acceptance

- [ ] No `in_tension_with` remains in the pack content.
- [ ] The open-packs round-trip gate is green at the new pin.
