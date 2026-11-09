
## Fix cycle 1

Branch `wp29-fix-c1` in `/home/user/wp29-review`, from `888412b4`.

| Finding | What changed |
|---|---|
| MAJOR-1 | `ActivationExclusionService.refuseFloor` now calls `checkWrite(before.withoutDeny(k,id), before.withDeny(k,id), snapshot, session::inForce)`, so the named target is judged whether or not its deny entry is stored (lock first, then the requirement through the facts). Javadoc updated. |
| MINOR-1 | Change fragment (still one `### Changed` block) says a documented `409 artifact_required` now succeeds (200/201) when an active override satisfies it, and that core's in-process backend still refuses it (DIV-204). |
| MINOR-2 | The `out-of-map:` rationales for `InForceFacts.java` and `docs/api/service-contract.md` are in the last commit message (below). |
| MINOR-3 | Tests that called the no-facts overloads now pass `InForceFacts.NONE` (DocumentRulesTest 27 calls, PacksAvailabilityTest 3 calls, no assertion changed). The overloads `check(document, snapshot)`, `check(built, snapshot)`, `checkWrite(before, after, snapshot)` and `checkReplace(built, stored, snapshot)` are deleted; the Javadoc merged into the facts overloads. No production caller existed (`grep service/*/src/main`). |
| MINOR-4 | `ActivationPresetServiceTest.forceKeepsTheDenyEntry...`: required `T`, preset `keep-f` (member `F`), stored deny of `T` and selection `[A]`; force keeps the entry on `T`, selects `F`, no refusal. Note: a preset never adds a deny entry (`PresetRules`), so "the preset's deny of T" is read as the stored entry that force keeps; the added-deny path of a force preset is not reachable. |
| NIT | The three YAML descriptions are rewrapped (same words, 118 columns). Changelog file name and "behaviour": recorded only. |

Also added at the service level: `ActivationExclusionServiceTest.aTargetOnTheFloorWhoseDenyEntryIsAlreadyStoredIsRefusedAgainAndNothingIsStored` (LK locked, RQ required, both stored).
No cascade variant (the early refusal precedes the cascade; the unit case shows no walk and nothing stored).

### Red evidence (at `888412b4`, feature filtered)

- `IC-13 - deactivating a locked LK whose deny entry is already stored is 409 artifact_locked`: `the response status is 409` fails, `expected: 409 but was: 200`.
- `IC-13 - deactivating T again after the override was unselected is 409 artifact_required`: same assertion, `expected: 409 but was: 200`.
- Service unit `aTargetOnTheFloor...AlreadyStored...`: `Expecting code to raise a throwable` with the fix stashed.
- Green after the fix: both scenarios and the whole feature (10 scenarios), full suite below.

### Commands (JAVA_HOME=temurin-25, `--max-workers=1`, `-PcontractBaseline=none`, foreground)

| Command | Result |
|---|---|
| `:charter-api:validateContract :contracts:check :activations:check` | BUILD SUCCESSFUL; activations 806 tests, 0 failed |
| `:deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' :packs:test --tests '*DivergenceRegisterTest*'` | BUILD SUCCESSFUL; deployable 635 (was 633, plus 2 scenarios), 0 failed; packs 3, 0 failed |
| `python3 scripts/docs/check_docs.py all` | ok |
| markdownlint-cli2@0.18.1 (changed md) | 0 errors |
| cspell@10.4.0 `docs/**/*.md` | 301 files, 0 issues |

### Out-of-map corrections

- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/InForceFacts.java`: new type the IC-13 seam needs (the domain rule asks the in-force resolution through it, with no copy of the resolution).
- `docs/api/service-contract.md`: the reserved 501 rows that T165 removes.
