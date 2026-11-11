---
title: Charter example packs replace the open-packs corpus
description: "The plan for ADR-0070: the example packs charter tests own, which test reads what, the CI changes and the open-packs validation job."
doc_status: draft
updated: 2026-11-11T00:00:00Z
type: Plan
kind: Planning
related:
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before moving a corpus test onto the example packs, or adding an example.
  - profile: pack-author
    guidance_text: Read the open-packs section for the validation job that replaces charter's corpus tests.
---

# Charter example packs replace the open-packs corpus

The plan for [ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/) (Proposed). Nothing here
starts before the ADR is accepted.

## Today

Two Gradle tasks and one workflow read the pinned spec-kitty-open-packs checkout (`openPacksRef`):

- `:packs:openPacksRoundTrip` and `:deployable:openPacksProfiles`, the tests tagged `open-packs`, run in `verifyAll`
  (`Service / verify`, required);
- `:packs:wholePackRoundTrip`, tagged `whole-pack`, run by `whole-pack.yml` (required).

The tests that read the checkout, grouped by what they prove:

| Group | Tests | What the example packs must give them |
| --- | --- | --- |
| Whole-pack round trip | `WholePackRoundTripTest`, `WholePackOpenPacksRoundTripTest`, `WholePackExpectation(Test)`, `WholePackReport` | every modelled file kind, so the expected-red lists can be empty |
| Artifact round trip | `PackDoctrineCorpusRoundTripTest`, `MarkdownCorpusTest`, `ProcedureAndGuideCorpusTest`, `GlossaryPackAndSkillCorpusTest`, `GlossaryPackAndSkillSchemaTest` | each artifact kind at full shape, every optional field set once and left out once |
| Mission types and steps | `MissionTypeCorpusTest`, `MissionStepContractCorpusTest`, `PromptSlotLoadChecksTest`, `PromptSlotsCanonicalCorpusTest` | a mission type with lifecycle, guards, steps with prompts holding empty slots, a step enhancement with fragments |
| Composition | `OpenPacksComposeTest`, `PackGraphEdges` | a built-in, an org and a project pack with `depends_on`, qualified references, shards and an org fragment |
| Files and digests | `CompanionFilesTest`, `CompanionDigestTest`, `BinaryPayloadRevisionTest`, `PackDigestBaselineTest`, `PackManifestParityTest`, `PackFileKindTest`, `FileSystemPackInspectorTest`, `PackLevelSchemasTest` | companions, binary assets, a manifest with hashes, every pack file kind |
| Profiles | `ProfileCorpusTest`, `ProfileGovernanceCorpusTest`, `ProfileExamplesEdgeTest` | profiles with `role`/`alt-roles`, lineage, governance edges with `when` and `reason`, a fallback profile |
| Plumbing | `OpenPacksCorpus` | replaced by an `ExamplePacks` locator |

## The example packs

Under `service/packs/src/testFixtures/resources/example-packs/` (a test-fixtures source set, so `packs` and
`deployable` read the same files):

- `built-in/`: every artifact kind at full shape, a `policy.yaml` that locks a `generic` fallback profile declared in
  `pack.yaml`, a mission type with lifecycle and guards, step prompts with empty slots, presets, shards, a manifest.
- `org-base/`: an org pack with `depends_on` on built-in, an org fragment, qualified references into built-in, a step
  enhancement with prompt fragments, a specialist profile with `specializes_from`.
- `project/`: a project pack depending on `org-base`, overriding an open artifact and naming a parent profile as its
  fallback.

Rules for the examples:

- Canonical shapes only. Invalid shapes live with the error-handling tests that use them (the `planted/` packs).
- Small but complete: one example per kind and per rule, not a copy of real doctrine.
- Each example says in a comment which rule or field it exercises.

## Phases

1. **Example packs and locator.** Add the test-fixtures source set, the three packs and `ExamplePacks`. Move the
   whole-pack round trip onto them; empty the expected-red lists.
2. **Coverage check.** A test that fails when a pack schema property, an artifact kind or a raised refusal code has
   no example (the coverage of the keyword and refusal-code tests, applied to the examples).
3. **Move the groups.** One pull request per group of the table, each test reading `ExamplePacks` and asserting the
   same outcome; remove the `open-packs` tag as each group moves.
4. **Retire the corpus plumbing.** Remove `openPacksRoundTrip`, `openPacksProfiles`, the `whole-pack.yml` checkout of
   open-packs and `openPacksDir` from the test tasks. `openPacksRef` stays for `syncPacks` (ADR-0070 decision 5).
5. **Drift watch.** A nightly workflow runs the round trip over open-packs `main` and opens an issue on red; never
   required.
6. **Governance.** Amend the charter companion ("Gates before merge") and PROJECT_002 as ADR-0070 says; regenerate
   `charter.yaml`.

## The open-packs side

A job in spec-kitty-open-packs CI:

- pins a charter release (`charter_ref` in its repository config): the contract bundle and pack schemas, or the
  `service-v*` jar;
- loads every pack through the pinned service (or validates every file against the pinned schemas) and fails on any
  refusal;
- is the required check there; a contract change reaches open-packs as a pin bump plus the migration.

## Order with PR #474

PR #474 lands first, red on the corpus checks as ruled. This plan then runs as its own missions; the open-packs
migration (#66, #69, #70) proceeds in parallel and no longer blocks charter.
