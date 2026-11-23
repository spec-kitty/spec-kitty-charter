# Research: pack-check coverage cluster (#629, #630, #632, #614; #535, #52.3, #158)

Base: `main` at `a5d5f9a` (2026-11-21). Open-packs: `scripts/ci/validate_pack_schemas.py` (`cross_pack_violations`,
lines 1460-1518; `profile_reference_violations` 1301; `prompt_fragment_composition_violations` 1347;
`specialization_cycle_violations` 1418; `derived_manifest`/`derive_manifest` 466/533). Read only; no tests run.

## Surfaces

- **Registration**: one read (`StorageRulesets.verdict`: HOLDING + `PackRulesets.content()` + the manifest gate),
  then `RegistrationImpact.check` over every closure that holds the written pack: the offering's compose
  (`OfferingReadService.composedWithFacts`: delegations, references, enhancement targets, preset contests, case-only
  ids), the relation rules, `SpecializationCycleRule`, the fallback/pin/profile-enhancement rules, `requireComposable`.
- **pack-check**: the same per-pack verdict (PACK) and `PackRulesets.closureFaults` (CLOSURE: `PackCompositionResolver.faults`
  + `presetContests`). No closure walk, no reference check (ADR-0075 d6/d7).
- **Composing read**: the compose phases; refusals answer 500.

## Rule by rule

| # | Rule (Python) | Registration today | pack-check today | Deliberate? |
|---|---|---|---|---|
| 1 | `must_have` id not held in the closure (#629) | accepts; `RequiredFloor` drops it into `unheld` | no | yes: A10 / C31 (`DM-01M6RA8MG55V8C2V9PXWDTRNQS`), reads report `requiredNotHeld` |
| 2 | preset `activated_*` id no pack declares (#632.1) | accepts (`PCR:1064-1070`, operator 2026-11-06); only case-only match refused | no | yes; applying the preset answers `activation_invalid` |
| 3 | stale `content_hash` / `manifest_hash` (#630) | restamps (`ManifestMembership.stamp`), never verifies | only under `--round-trip`, as `round_trip_red` | yes: ADR-0042 amendment 2026-11-12, `whole-pack-registration.feature:560` |
| 4 | id re-declared across levels | accepts as a layer override | same-level `pack_sibling_conflict` only | yes: ADR-0053 d6, ADR-0059 d3 |
| 5 | bare edge endpoint held by no pack | accepts; held by another closure pack = `pack_reference_unqualified` | no | yes: DIV-007 (`unresolved_edge_endpoint` raised by no operation) |
| 6 | agent-profile references | refuses (`pack_reference_unqualified/_pack_missing/_artifact_missing`), except held-by-none (accepted) and self-qualified (allowed) | **no** | ADR-0053 d7 |
| 7a | enhancement target step missing | refuses (`EnhancementCompose.targetHeld`, C37) | **no** | ADR-0066 d7/d8 |
| 7b | fragment slot the prompt lacks | refuses only for a stored activation with it in force (`requireComposable`, C38) | no | ADR-0066 d8, OD-1(B) |
| 8 | `specializes_from` cycle | refuses `specialization_cycle`, per composed closure | **no** | ADR-0078 d2 |
| 9a | #535 file-name collision across layers | nothing; artifacts keyed by kind+id; stem rule only for kinds with `idIsFileStem` | nothing | — |
| 9b | #52.3 bare id under two kinds | impossible today: endpoints are `<kind>:<id>` URNs; `putIfAbsent` gone | — | DIV-007 |

## Findings

- **F1. Decision 9 is broken in both directions.** Rules 1-3 are accepted by registration by prior rulings, so
  making pack-check alone refuse them would add a second answer. Rules 6, 7a and 8 are refused by registration but
  not by pack-check, because the CLI composes no closure walk (ADR-0075 d6). The issues only name the first direction.
- **F2. #629 overturns A10/C31 if registration refuses.** A refusal needs a definition of "held" for a pack set:
  Python uses the declaring pack's `depends_on` closure; the service judges the closure being composed.
- **F3. #630 overturns the ADR-0042 2026-11-12 amendment if registration refuses.** One verification
  (`PackManifests.stale`) can live in the storage manifest gate beside `manifest_membership`
  (`ManifestRules`/`StorageRulesets.manifestMembership`), which validate mode already shares. The round trip then
  drops its own stale reason for the same file (`PackSetCheck.notReported` already dedupes by file).
- **F4. Manifest derivation exists only in `packs.infra.storage`** (`PackManifestDerivation`, `ManifestMembership.stamp`,
  package-private). A pack-check `--derive-manifest` reuses it through the composition root, no second classifier.
  #158's per-asset hashes would extend the same derivation.
- **F5. #52 item 3 is obsolete**: no bare-id map remains; close with evidence.
- **F6. #535 is a core activation-token concern** (tokens resolve by file name in core). The charter resolves by
  declared id; the collision is only possible for kinds without `idIsFileStem`.
- **F7. #614** is mechanical once #618 lands (`ClosureFault.of(CompositionFault)` is the bridge to delete).
