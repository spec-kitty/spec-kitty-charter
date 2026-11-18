# #554 PR body sections (WP10 drafts)

> Drafts for the orchestrator's pull request body, written by WP10 of mission `pack-closure-rulesets-01M7FA90`.

## Test collapse

Each test class of `research/squad-similar-surfaces.md` §5, and where its behaviours are tested now. The tests live in
their subject's module (ADR-0062): the core and storage rules in `:packs`, the validation edge in `:pack-validation`.
`PackCodesCoverageTest` and `PlacementCodesCoverageTest` pin that every code keeps a failing and a passing fixture
after the collapse.

| Test class (before) | Fate | Where its behaviours are tested now |
| --- | --- | --- |
| `PackPolicyRulesTest` (43) | deleted, WP13 | per rule: `ArtifactRulesTest`, `AssetRulesTest`, `StepRulesTest`, `MissionTypeRulesTest`, `GraphRulesTest`, `FragmentRulesTest`, `PromptSlotRulesTest`; read faults in `FileSystemPackInspectorTest`; level and org fragment in `LevelRulesTest`, `OrgFragmentRulesTest` (`:pack-validation`) |
| `PackPolicyVocabularyTest` (49) | moved whole, WP03 | `WordingTest` (`packs.infra.storage`, WP13) |
| `PackPolicyGraphTest` (29) | deleted, WP13 | `GraphBindingTest`, `GraphRulesTest`, `RelationshipEndpointRuleTest` |
| `PackPolicyOrderTest` (22) | moved whole, WP03 | `PrecedenceTest` (path-order corpus with `ArtifactRulesTest`) |
| `PackPolicyTest` (18) | deleted, WP13 | `PackCheckTest` (`:pack-validation`), `StorageRulesetsTest` (the compositions), `ValidateGoldenTest` |
| `PackPolicyStepEnhancementTest` (5) | deleted, WP13 | `FragmentRulesTest`, `StepEnhancementSurfacesTest` |
| `PackFindingTest` (8) | deleted with the converters, WP13 | the mappers: `PackRefusalsTest` (`packs.application`), `PackVerdictsTest` (`:pack-validation`) |
| `PackValidationTest` (5) | moved, WP09 | `PackCheckTest` (`:pack-validation`) |
| `StepEnhancementRulesTest` (34) | moved, WP07 | `FragmentRulesTest` (`packs.domain.rules`) |
| `OverrideSourceRuleTest` (12) | deleted by slice F (#537) | `RelationshipEndpointRuleTest`, `GraphRulesTest` |
| `PackCompositionResolverTest` (56) | kept, `CLOSURE` part moved, WP08 | `CompositionRulesTest` (one failing and one passing set per code, collect-all), `PackCompositionTest`; the reference part stays (#556) |
| `FileSystemPackInspectorTest` (58) | kept as the one reader's test | its rule assertions moved to the rule tests; it holds the read faults, the census and the S2 prechecks; WP10 adds the companion refusal and not-UTF-8 cases and the unparseable root shard |
| `WholePackStoreTest` (42) | kept, trimmed, WP12 | the load-only checks are storage rules: `StorageRulesetsTest`, `ManifestMembershipTest`, `HeldSetAgreementTest`, `OneReadRegistrationTest`; the store's own assembly in `StoreHalfFaultsTest` |
| `DomainRepositoryServiceTest` (37) | kept, WP12 | assembly no longer judges; the compose read's answers in `StoreHalfFaultsTest` and `SurfaceGoldenTest` |
| `StepEnhancementLoadChecksTest` (25) | renamed, WP13 | `StepEnhancementSurfacesTest` |
| `PromptSlotLoadChecksTest` (18) | renamed, WP13 | `PromptSlotSurfacesTest` |
| `ManifestMembershipTest` (13) | kept (plain function) | `ManifestMembershipTest`, `StorageRulesetsTest` (the gate) |
| `Judged` (helper) | deleted, WP13 | none needed: the mappers' tests read `Reason`s |

Behaviours with no test after the collapse: none found. WP10 restored the one assertion WP09's deletion had lost (an
unparseable root shard is one read fault) and added the WP04 companion cases.

## The golden snapshot (T058, NFR-002)

The snapshot WP01 took before any change (`66d2b4d48`, 108 subjects) against this head: `composition.json` is
byte-equal; 83 registration rows, 24 compose-read rows and 103 validate-mode rows changed. Every change came in one of
the commits below, each under one entry of NFR-002's diff list; a row two commits changed is counted under each. WP10
moves no row: `surfaces.json`, `validate.json` and `composition.json` are byte-equal to WP13's.

| Commit (work package) | NFR-002 entry | Registration | Compose read | Validate mode |
| --- | --- | --: | --: | --: |
| `18a99e4f1` (WP02) | Q1, open case 6: the twelve nested-shard and `charter.yaml` plants are accepted with one `file_not_held` warning | 12 | 0 | 0 |
| `399b8c9ba` (WP11) | open case 6: validate mode judges nothing in a file not held; WP02 review L2 (a retired file is no longer also listed as not held) | 0 | 0 | 24 |
| `7bc837c90` (WP11 review cycle 1) | open case 6's excluded files (the two example packs gain one warning) and open case 6's fixture moves (the C4 and C16 plants, 10 rows) | 2 | 4 | 6 |
| `f95875f19` (WP04) | the R2 table rows (codes and wording), FR-009's severity spelling (`violation`, not `blocking`), S5 (the compose read's repeated artifact id: a 500 becomes `pack_unreadable`) and WP02 review L2 | 67 | 19 | 90 |
| `682a6f860` (WP05) | open case 8: the clean control's missing payload, a fixed fixture | 1 | 1 | 1 |
| `7a00b83de` (WP09) | R2 row 24: a plant with no `pack.yaml` answers `pack_unreadable` at `pack.yaml` in validate mode (DIV-028) | 0 | 0 | 34 |
| `d9cdaf77b` (WP12) | S8 (38 rows: every fault of a pack whose `pack.yaml` is missing or unreadable) and S2 (9 rows: rows 14, 15 and 38; four rows are both) | 43 | 0 | 0 |

The rows of each commit are listed in the R2 note's outcomes of that work package
(`docs/development/notes/pack-code-unification.md`), and `SurfaceSubsetTest` and `PackRefusalsTest` name the S2 and
S8 rows. There are no unexplained rows.

## Mutation scores (T056, NFR-003, R3-b (i))

| Run | Before | After | Floor | Margin |
| --- | --- | --- | --: | --: |
| `:pack-validation:pitest` (whole module, a floor measurement) | ADR-0062: 1 mutant, margin 0 (#553) | 330 / 372, 88.7 % | 85 | 13 |
| `:packs` `domain+application` shard, now with `packs.infra.storage.rules` | ADR-0062: 1519 / 1632, margin 126 | 3846 / 4240, 90.7 % | 85 | 242 |
| `:packs` `infra.storage` shard, now without the storage rules | ADR-0062: 1900 / 2092, margin 120 | 3134 / 3439, 91.1 % | 85 | 210 |
| Narrowed: this mission's `:packs` classes (not a floor measurement) | 698 / 733, 95.2 % | 717 / 732, 98.0 % | — | — |

The 15 mutants left in the narrowed run are named in the R2 note, "Outcomes of WP10". No floor is lowered; ADR-0062 has
the amendment of 2026-11-17.
