### Breaking

- Only the distribution pack settles a sibling contest (#358, ADR-0048 decisions 8, 9 and 12, operator ruling (a) of
  2026-11-17). The distribution pack is derived, not declared: for a contest at a level, it is the pack of that level
  that depends on every contestant (over `depends_on` and `parent_pack`), that is not one of them, and that no other
  pack of that level depends on. A dependent at another level does not count. Before this change, any pack of the
  compose could settle a contest with an `overrides` entry, a contestant included. Now:
  - An `overrides` entry that names a contestant is refused `pack_sibling_conflict` when any other pack declares it:
    a member, a contestant, or a pack that does not depend on every contestant. This holds even beside the
    distribution pack's entry. The detail names the declaring pack, and `packs` lists it first.
  - Two distribution packs that both settle one contested artifact are refused `pack_sibling_conflict`, naming both
    first, even when they name the same winner. This answers the question data-model.md left open, "two distribution
    packs". The detail wording "overrides edges settling it name different winners" is gone: this refusal replaces
    it.
  - The refusal is the same on every path that composes: `GET /offering`, the activation reads, and the closures that a
    whole-pack registration (`PUT /packs`) or a per-file write judges. The layer merge settles through the same rule,
    so a step contest whose entry comes from a pack that is not the distribution pack is refused at read, naming that
    pack.
  - No refusal code, field or status is added.
- Core's seam: a set of packs that settled a contest from a member, a contestant or an unrelated pack is now refused.
  So is one where two packs settle the same contest. Move the `overrides` entry to the one pack that depends on both
  contestants and that no other pack of their level depends on.
