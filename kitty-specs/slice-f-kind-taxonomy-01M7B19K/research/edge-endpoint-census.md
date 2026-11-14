# Edge endpoint census

Every authored edge in the graph documents (`*.graph.yaml` shards and `drg/fragment.yaml`), split by relation,
source kind and target kind. Endpoints are parsed as `[<pack>::]<kind>:<id>`, which is how `PackPolicy.endpoints`
splits them. The full table is `edge-census.csv`, with the columns `corpus, relation, source_kind, target_kind, count`.

| Corpus | Files with edges | Edges |
|---|---|---|
| open-packs `packs/*` at `1692a3b` | 39 | 1,331 |
| charter example packs (`service/packs/src/testFixtures/resources/example-packs/`) | 6 | 48 |
| charter `packs/internal` and `packs/qa` (synced copies of open-packs) | 2 | 170 |
| charter load fixtures, planted fixtures and other test packs | about 117 | 31 |

No `registered.graph.yaml` exists in either corpus.

## Findings

- **Open-packs uses 12 relations.** It has no edges for `applies`, `delegates_to`, `overrides`, `replaces` or
  `impacts`, and no guard or lifecycle endpoints.
- **Charter's example packs** carry one `overrides` edge (`skill → skill`), two `impacts` edges and one
  `suggests styleguide → anti_pattern` edge.
- **Existing endpoint checks.**
  - `NodeKind.authoredInGraphs` refuses guard and lifecycle endpoints. It is checked at `PackPolicy.java:715, 729, 824`,
    at `RelationshipWire.java:78` and at `NodeRef.java:39, 51`.
  - A derived relation is refused when authored, at `PackPolicy.java:766` and `RelationshipWire.java:84`.
  - A self-loop is refused, at `Relationship.java:75` and `RelationshipRegistrationService.java:106`.
  - The step-enhancement rules are at `StepEnhancementRules.java:182-215`.
  - No check ensures that `specializes_from`, `overrides` or `enhances` join one kind.
- **#447:** there are 0 `overrides` edges whose source is a non-activatable kind in either corpus.

## What the draft table of research R6 would refuse

- In open-packs: 0 of the 1,331 edges.
- In charter's example packs: 3 of the 48 edges. These are the 2 `impacts` edges and the `suggests styleguide →
  anti_pattern` edge.
- In the error fixtures, some edges are already refused for another reason and would gain a second fault:
  - 6 `impacts` edges in the load fixtures;
  - `scope tactic → tactic` in `shard-impacts-on-scope-edge`;
  - 4 `impacts` edges in the planted fixtures.

  These fixtures are rewritten so that each one exercises a single fault.
