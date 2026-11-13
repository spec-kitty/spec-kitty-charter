# Pre-merge squad: slice A2

Run on 2026-11-13 against `origin/main...slice-a2-536-cutover-reads`: 10 commits, rebased on `f7a4f9d`. The squad had three read-only, profile-loaded delegates:

- **reviewer-renata** (correctness and tests): approve with changes, 0 BLOCKER, 0 MAJOR.
- **architect-alphonso** (boundaries, overlap, slice C): mergeable after two fixes, 2 MAJOR.
- **security lens** (no security profile exists, so architect-alphonso was loaded): OK to merge, 1 MEDIUM.

## Findings and dispositions

| # | Sev | Finding (lens) | Disposition |
| --- | --- | --- | --- |
| P1 | MAJOR | `EffectiveTag` DOMAIN keeps v2 although existing effective bodies change: activation links, and skill items now link (architect) | **accepted**: bumped to v3, the same version slice C takes, and a fragment line names the reads whose bodies change. Whichever of A2 and C lands second keeps one v3 if no release lands between them. |
| P2 | MAJOR | The asset id pattern (refuses packs that registered before) and the new `ArtifactKindToken` and `Artifact` one-of values are filed as `### Changed`, not `### Breaking` (architect) | **accepted**: both moved to `### Breaking` fragments that say what core regenerates or refuses |
| P3 | MEDIUM | Preset reads set `.eTag()` directly, so Spring can answer a 304 the contract rules out (reviewer) | **accepted**: they use `DeferredETag.offer`, with a test that a matching `If-None-Match` still gets 200 |
| P4 | MEDIUM | The activation does not link the held-asset list or a templated asset item, so a guard runner builds that URL by hand (reviewer) | **accepted**: the activation links `assets`, a templated `asset` (`…/artifacts/asset/{artifactId}`) and `skills`, with relation-table rows |
| P5 | MEDIUM | `DeferredETag` is global and offered before the body is built, so an error answer can carry a 200's tag (security, reviewer, architect) | **accepted**: the advice writes the tag only on a 2xx and clears the attribute, with a test where the mapper throws after `offer` |
| P6 | MEDIUM | The `AssetList`, `SkillList`, `AssetSummary` and `SkillSummary` text and `x-derived` describe only the pack read (architect, reviewer) | **accepted**: they describe both trees |
| P7 | MEDIUM | ADR-0037 has no amendment for a domain-less read-composition edge (architect) | **accepted**: a dated ADR-0037 amendment |
| P8 | MEDIUM | ETag transport is split three ways: `DeferredETag`, direct `.eTag()`, `ContractDocumentETag` (architect) | **deferred_with_rationale**: moving the existing reads onto `DeferredETag` changes reads outside this mission. Recorded under the PR's "Not done". Not filed: the issue rules file only correctness, data or security risks, and the existing reads behave as their contract says. |
| P9 | MEDIUM | Overlap with slice C (`EffectiveTag`, `EffectiveETag.yaml`, relation rows, `PacksSession`, `ActivationMapper`) (architect) | **accepted** as coordination: whichever lands second reconciles them. The orchestrator is told. |
| P10 | MEDIUM | `ActivationMapper` concatenates strings where C and main use the generated path constants (architect) | **accepted**: the links are built from the `ActivationApi` path constants |
| P11 | MINOR | Relation table "On" columns miss `payload` on the effective asset, `collection` and `effective-artifact` on the activation lists, and `up` on the new reads (architect) | **accepted** |
| P12 | MINOR | `DELETE …/asset/run` says "serves no artifact kind asset" (reviewer) | **accepted**: "The service removes no artifact of kind asset." |
| P13 | MINOR | The spec says every twin answers 304, but the references twin takes no ETag by design (reviewer) | **accepted**: the spec edge case is narrowed |
| P14 | MINOR | One row of the 304 outline cannot fail (reviewer) | **accepted**: that row is retitled, and an `EffectiveTag` unit test pins the separate tag namespaces |
| P15 | MINOR | `heldWinner` filters `heldWinners` instead of `winnerOf`; the stale mapping is written twice (architect) | **accepted**: it routes through `winnerOf`. Both stale mappings stay, documented: the packs-side `WinnerReferences` maps the kind's own absent code, which `WinnerReads` cannot see. |
| P16 | MINOR | `portImplementationsResideInStorage("artifacts")` has no biting fixture (architect) | **accepted** |
| P17 | MINOR | The `EffectiveSkills` tag text should point to the stateful list (architect) | **accepted** |
| P18 | MINOR | Payload has no `Content-Disposition`; the CSP lacks `default-src 'none'` (security) | **accepted**: `Content-Security-Policy: sandbox; default-src 'none'`. `Content-Disposition` is left out on purpose and recorded in the path file: the payload is read by programs, and `nosniff` plus the sandbox already contain it. |
| P19 | MINOR | A payload read loads the whole pack, twice under an activation (security) | **deferred_with_rationale**: bounded for archives by the 413 limit, and token-guarded. Recorded under "Not done". Not filed. |
| P20 | MINOR | Refusal details echo the client's id (security) | **dropped**: JSON, token-guarded, no header or HTML context |
