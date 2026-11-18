### Breaking

- Only the distribution pack settles a sibling contest (#358, ADR-0048 decisions 8, 9 and 12, operator ruling (a) of
  2026-11-17). The distribution pack is derived, not declared: the root of the compose, the pack that no other pack of
  the compose depends on by `depends_on` or `parent_pack`, and that is not itself a contestant. Any pack of the compose
  used to settle a contest with an `overrides` entry, a contestant included. Now:
  - An `overrides` entry declared by a member of the compose (a pack another pack depends on) or by a contestant
    settles nothing. The compose is refused `pack_sibling_conflict`, and the detail and `packs` name the declaring
    pack after the two siblings.
  - Two roots that both settle one contested artifact are refused `pack_sibling_conflict`, naming both, even when they
    name the same winner (the question data-model.md left open, "two distribution packs"). The detail wording "overrides
    edges settling it name different winners" is gone: entries of two roots are this refusal.
  - The refusal is the same on every path that composes: `GET /offering`, the activation reads, and the closures a
    whole-pack registration (`PUT /packs`) or a per-file write judges. The layer merge reads only the distribution
    pack's entry, so a step contest settled only by a member or a contestant is refused at read too.
  - No refusal code, field or status is added.
- Core's seam: a set of packs that settled a contest from a member or a contestant, or from two unrelated packs, is now
  refused. Move the `overrides` entry to the pack that depends on both contestants and that nothing depends on.
