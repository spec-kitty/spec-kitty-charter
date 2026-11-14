# Squad: similar surfaces

This squad ran before the spec, on 2026-11-13, as a read-only lens. It looked for existing surfaces that slice F
should reuse instead of building parallel ones. Each finding has a disposition.

| Finding | Disposition |
|---|---|
| The facts of the kind table already sit in `contracts/packs/file-kinds.yaml`: node kinds, plurals, artifact kinds and relations. `VocabularyEqualityTest` checks that file against the domain. | **Reuse.** The `/offering/kinds` body is built from the same enums, and its parity case joins `WireTokenParityTest` or `VocabularyEqualityTest`. Do not add a new mechanism. |
| `WireTokenParityTest` already pairs each contract enum with its domain tokens. It has a `retiredContractOnly` exemption and a positive control. `RelationSetParityTest` pins `Relation`. | **Reuse.** The kind retirement and the relation rows go through these tests. Retiring `mission_step_contract` takes it out of both sides, so it needs no exemption. |
| `x-source` metadata is checked only for whether its path exists (`XSourceCitationsTest`). | **Fold.** The new schemas carry `x-source` pointing at `NodeKind` or `RelationType`. Checking the `symbol` is not this slice's work (#49). |
| `getOffering` is a verified read that carries the fingerprint headers. | **Plan.** The kind table does not depend on the packs. The plan decides whether it carries the fingerprint, or only the contract version as its `ETag`. |
| `RelationType` already takes per-relation flags in its constructor (`Directionality`, `derived`). `Relationship` validates in its constructor. No code types anything by kind. | **Reuse.** The endpoint rule is a constructor field on `RelationType`, and `Relationship` gets typed factories. The kernel stays pure (`DOMAIN_IS_PURE`). |
| The edge rules are enforced at three chokepoints: `PackPolicy.edge`, `RelationshipWire` and `refuseStepEnhancementEdge`. | **Reuse.** The rule is enforced at the same three places, plus the load path (`DomainRepositoryService.typedEdge`). |
| Retirements have precedents: the `retired:` section of `file-kinds.yaml`, `RetiredFilesTest`, `RetiredMissionFieldNamesTest`, and the changelog fragments `2026-11-11-org-charter-is-retired.md`, `2026-11-12-asset-and-skill-kinds-breaking.md` and `2026-11-05-edges-only-in-graph-documents.md`. | **Reuse.** `*.step-contract.yaml` becomes a retired file kind. A scan of the main sources keeps `mission_step_contract` out. The changelog gets one dated `### Breaking` fragment, enforced by `ChangelogFragmentsTest`. |
| Invalid packs follow two patterns: `planted/<name>/{files, expected.json}` and `load-fixtures/<name>/{files, expected.json}`. The nearest existing ones are `r15-glossary-urn-as-an-edge-endpoint` and `c7-shard-relation-extends`. | **Reuse.** Each endpoint refusal gets one invalid pack, and so does each authored `action_sequence` or `template_set`. |
| Gates that apply: `HexagonalArchitectureTest`, the lint ruleset, `ExampleCoverageTest`, `ReachableContractFilesTest`, `KindBindingsTest` and `PackFileKindTest`. | **Plan.** These are the checks each work package must keep green. |
