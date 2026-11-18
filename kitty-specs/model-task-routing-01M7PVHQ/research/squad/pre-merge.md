# Pre-merge squad, model-task-routing-01M7PVHQ (#108)

Point-cut: pre-merge, over `origin/main...kitty/mission-model-task-routing-01M7PVHQ-lane-j` with all ten work packages
approved (procedure `adversarial-squad-deployment`). Question: is the aggregate change correct and canonical, and does
it hold its boundaries, before PR #600 goes ready?

Cast, each profile-loaded and read-only: `reviewer-renata` (correctness and fakeable tests), `architect-alphonso`
(boundaries, overlap, the canonical-model rule), `researcher-robbie` (parity with core against DIV-701 to DIV-707).

No CRITICAL or HIGH finding. The fixes landed on lane-j in `a3cde89c1` and `7bcf2d86c`;
`./gradlew verifyAll --continue` passed after them.

## Dispositions

| Lens | Severity | Finding | Disposition | Evidence |
| --- | --- | --- | --- | --- |
| reviewer | MEDIUM | A score with an adjusted exponent below -6 was written in scientific notation (`1E-7`), breaking rule 7 | accepted | `web-transport` `DecimalJson` (plain-notation serializer and the one wire helper), `DecimalJsonTest` |
| reviewer | MEDIUM | `top(inside)` with ties had no test that fails on a single pick | accepted | `RankerTest.tiesInsideTheTiersBelowAHigherModelOutside` |
| reviewer | MEDIUM | Only the tier was shown to move the recommendation's ETag | accepted | `EffectiveTagTest.eachInputOfTheRecommendationHasItsOwnTagAndAnAbsentPartIsStable`; the caching scenario of `model-recommendation.feature` (profile and task type) |
| reviewer | LOW | Ids tie-broke by UTF-16 unit, not code point | accepted | `Ranker.byCodePoint`, `RankerTest.supplementaryIdsOrderByCodePoint` |
| reviewer | LOW | Stale Javadoc said the YAML path goes through `double` | accepted | `RoutingCatalogBinding` Javadoc |
| reviewer | LOW | The before-the-304 scenario remembered an unused tag | accepted | `model-recommendation.feature`, "Every refusal comes before the 304" (three refusals under `*`) |
| architect | MEDIUM | `artifacts.infra.packs` names the routing domain, undeclared and unrecorded | accepted | `artifacts-edge/build.gradle.kts` (`:routing`); ADR-0037 amendment of 2026-11-18; `hexagonal-service.md` row; `ROUTING_DOMAIN_NAMED_ONLY_BY_STORAGE_AND_ADAPTER` with known-bad `ApplicationNamesRoutingDomain` |
| architect | MEDIUM | The catalog reads in `artifacts-edge` had no module tests and no view-to-domain parity | accepted | `PacksRoutingCatalogsTest`, `RoutingCatalogBodiesTest`, `EffectiveRoutingCatalogReadsTest` (22 tests); the placement recorded in the ADR-0037 amendment |
| architect | LOW | `WEB_NAMES_NO_SLICE` did not list `routing` | accepted | `HexagonalArchitectureTest` |
| architect | LOW | Two optional routing known-bad controls | deferred_with_rationale | The shared factories are controlled elsewhere and `SLICE_RINGS` runs over `routing`; no rule is unguarded |
| architect | LOW | Duplicated `number()` wire helper | changed | Folded into `DecimalJson.wire` |
| architect | LOW | The at-most-one-catalog rule and the query live in activations | deferred_with_rationale | The profile-selection precedent; it keeps ADR-0080 decision 9's order in one read |
| architect | LOW | A runtime cast of the catalog in the activations adapter | deferred_with_rationale | The binding is the only source of the kind |
| architect | note | The two catalog controllers have no module tests | deferred_with_rationale | Thin adapters covered by the contract scenarios; revisit if the nightly `artifacts-edge` floor (85) is missed |
| researcher | MEDIUM | The step field's rename and narrowing were unrecorded | accepted | DIV-704 (both forms); ADR-0080 "Divergences from core"; the changelog's core seam |
| researcher | MEDIUM | The changelog's core seam did not say what core must change | accepted | `contracts/charter/changes/2026-11-18-routing-catalog-and-model-recommendation.md` |
| researcher | MEDIUM | Exact decimals against core's floats were unrecorded | accepted | DIV-705 (both forms); ADR-0080 |
| researcher | LOW | ADR-0080 said the entries are `proposed` | accepted | ADR-0080 "Divergences from core" |
| researcher | LOW | DIV-707's core text was imprecise, and the stricter content rules unrecorded | accepted | DIV-707 (both forms) |
| researcher | LOW | Single-field strictness (`generated_at`, ids, text) unrecorded | accepted | DIV-707 (both forms) |
| researcher | LOW | The tier resolution's source vocabulary rename unrecorded | accepted | DIV-704 (both forms) |
| researcher | LOW | Code-point order versus UTF-16 | accepted | as the reviewer's LOW above |
| researcher | LOW | `override_mode` absent from DIV-702 | accepted | DIV-702 (both forms) |

No finding is filed as an issue: none is a correctness, data or security risk left open, and every one is folded or
deferred here with its rationale (docs/development/issue-tracker.md).
