---
work_package_id: WP01
title: The explicit NodeKind table (behaviour-preserving)
dependencies: []
requirement_refs:
- FR-006
planning_base_branch: feat/106-glossary-contexts
merge_target_branch: feat/106-glossary-contexts
branch_strategy: Planning artifacts for this mission were generated on feat/106-glossary-contexts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/106-glossary-contexts unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
history:
- at: '2026-11-14T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/NodeKindTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/NodeKindPluralTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/NodeRef.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipWire.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveProfileDtos.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileDtos.java
- service/profiles-edge/src/test/java/ai/speckitty/charter/profiles/infra/api/ProfileDtosTest.java
- service/artifacts-edge/src/main/java/ai/speckitty/charter/artifacts/infra/api/EffectiveBodies.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 - The explicit NodeKind table (behaviour-preserving)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under
`/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/`, and adopt it.

Then read, in `kitty-specs/glossary-contexts-and-terms-01M7B1PP/`:

- `spec.md`
- `plan.md`, IC-02, the kernel part
- `research.md`, R1
- `research/squad-post-plan.md`, findings J-H1, J-H2, J-H3 and A-M2

## Objective

Make `NodeKind` state its facts per constant instead of deriving them from the constant name. Make every place that
turns a URN's kind into an artifact kind go through that table. **Nothing observable changes in this WP.** The tokens,
plurals, flags, orders and answers stay byte-identical, and the build stays green.

This WP prepares WP02. There, `GLOSSARY_CONTEXT` (token `glossary_context`, artifact kind `glossary`) and `TERM`
(token `term`, no artifact kind, never declared, an edge target) cannot be expressed by name derivation.

## Context

`service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java` today:

- `token()` is `name().toLowerCase()`.
- `artifactKind()` is `ArtifactKind.fromToken(token())`.
- `of(ArtifactKind)` is `valueOf(kind.name())`.
- `plural()` is `artifactKind().map(ArtifactKind::plural).orElse("actions")`.
- `fromPlural` special-cases `actions`.
- `authoredInGraphs()` is `this != GUARD && this != LIFECYCLE`.

`authoredInGraphs()` is read with two different meanings:

| Call site | Meaning |
| --- | --- |
| `NodeRef.java:39,51` (activations) | may name a node |
| `PackCompositionResolver.java:507` | read the line and decide which meaning it has |
| `PackPolicy.java:746,760` | node declarable in a graph |
| `PackPolicy.java:855` | edge endpoint |
| `RelationshipWire.java:78` | edge endpoint |
| `DomainRepositoryService.java:547` | an artifact's node goes into the generated graph |
| `WireTokenParityTest.java:99-103` | the contract `NodeKind` enum equals the authored kinds |
| `VocabularyEqualityTest.java:66-71,129-143` | fragment plural and `DRGNode` branch per authored kind |
| `ProfileDtosTest.java:631` | read and classify |

Three places use a URN's kind *token* as an *artifact kind* token. That works today only because the two spellings are
equal for every kind:

| Place | How it is used |
| --- | --- |
| `EffectiveProfileDtos.java:106,114,118,148` | passed to `EffectiveGovernanceEntry.KindEnum.fromValue` / `ArtifactReference.KindEnum.fromValue`, typed by `ActivatableKind` |
| `ProfileDtos.java:115` | the same |
| `EffectiveBodies.java:259` (artifacts-edge) | `/packs/{p}/artifacts/{kind}/{id}` hrefs |

Also read the profile reads' `linksArtifact` (`ProfileRead`, `EffectiveProfileRead`). If they build an artifact path
from a URN, they belong here too. Record that as an out-of-map edit, with a one-line reason in the commit message.

## Subtasks

### T001: The explicit table

- Give each constant a constructor:
  `NodeKind(String token, @Nullable ArtifactKind artifactKind, @Nullable String plural, boolean declarable, boolean edgeTarget)`.
- Values today:
  - every artifact kind: its own token, its `ArtifactKind`, the `ArtifactKind.plural()`, and `declarable` = `edgeTarget`
    = today's `authoredInGraphs()`;
  - `ACTION`: token `action`, no artifact kind, plural `actions`, declarable and edge target as today;
  - `GUARD` and `LIFECYCLE`: false for both flags, as today.
- Rewrite `token()`, `artifactKind()` (an `Optional` of the field), `of(ArtifactKind)`, `plural()` and
  `fromPlural(String)` to read the fields.
  - `of` must stay total over `ArtifactKind`: look it up through a static `EnumMap<ArtifactKind, NodeKind>` built
    once. A missing kind is an `IllegalStateException` at class init.
  - `plural()` returns `Optional<String>` only if no caller needs a plain `String`. Otherwise keep `String`, and add
    `hasPlural()` for WP02's `TERM`. Choose the smaller change and say why in the commit.
- Keep the constant order. `VocabularyEqualityTest` and `WireTokenParityTest` pin "the order of NodeKind".
- Keep `fromToken(String)` exact-match.

### T002: Two graph flags

- Replace `authoredInGraphs()` with two methods: `declarableInGraphs()`, for a node that a graph document or fragment
  may declare, and `edgeTarget()`, for a kind an edge may name as source or target.
- Classify every call site from the table above by what it checks.
  - Today both flags are equal for every constant, so the classification changes no behaviour. It matters from WP02,
    where `TERM` is an edge target but not declarable.
  - Write the reason for each call site's choice in the commit message.
- Update the tests:
  - `WireTokenParityTest`: the contract `NodeKind` enum is the kinds that are declarable *or* an edge target. It is
    the wire's set of URN kinds.
  - `VocabularyEqualityTest`: fragment plurals and `DRGNode` branches are checked for declarable kinds. Edge
    source/target alternations are checked for edge targets.

  This changes what each test checks, not how strictly; do not drop an assertion.
- Do not remove `authoredInGraphs()` behind a deprecation. Replace it outright (charter: no legacy).

### T003: URN kind → artifact kind

- In `EffectiveProfileDtos`, `ProfileDtos` and `EffectiveBodies`, replace each `urn.kind().token()` used as an
  artifact kind with `urn.kind().artifactKind().orElseThrow().token()`.
  - Where a URN can be of a kind with no artifact kind (`action`), first check how the code handles that today, and
    keep that behaviour: skip the URN, or answer it as today.
- Add one test per site that names the artifact kind through the mapping. Today the test cannot fail by token
  difference, so assert on the code path. A small fake `NodeKind` is not possible (it is an enum), so assert that the
  mapped token equals the `ArtifactKind` token for every artifact-backed `NodeKind`, through the production method.

### T004: Pin the table

- `NodeKindTest` gets a parameterized row per constant: token, artifact kind, plural, declarable, edge target. Its
  expected values are written out literally, not derived.
- `NodeKindPluralTest`: `fromPlural(plural())` round-trips for every constant that has a plural.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew --max-workers=1 :kernel:test :packs:test :activations:test :profiles-edge:test :artifacts-edge:test
./gradlew --max-workers=1 verifyAll   # once, before handing in for review
```

Run Gradle in the foreground, one run at a time; never `--stop`.

## Definition of Done

- `NodeKind` holds no name-derived logic. `grep -n 'name()' NodeKind.java` finds no use in `token()`, `of()` or
  `artifactKind()`.
- `authoredInGraphs()` is gone, and every caller uses the flag that matches its meaning.
- The three URN→artifact-kind sites map through `artifactKind()`.
- `verifyAll` is green, with no test removed or loosened.
- New code meets slice D's complexity gate (Checkstyle `CyclomaticComplexity` 15, `NPathComplexity` 1000,
  `MethodLength` 60, `NestedIfDepth` 1; research R15) and the kernel mutation floor (75).

## Risks and reviewer guidance

- **Behaviour-preserving**: the reviewer diffs the contract and any golden output; none may change.
- **Slice F (#537)** works on the kind taxonomy. Rebase onto `main` before starting, and tell the orchestrator if
  `NodeKind` changed there.

## Branch Strategy

- **Planning base and merge target**: `feat/106-glossary-contexts`.
- **Mission branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP`.
- **Worktrees**: allocated per computed lane (`lanes.json`).
- **Implement with**:
  `spec-kitty agent action implement WP01 --agent claude --mission glossary-contexts-and-terms-01M7B1PP`.
- **Commits**: signed (`git commit -S`), with no model name and no `Co-Authored-By` trailer. Reference `(#106)`.
- **Integrating `main`**: rebase, never merge. Force-push only a rebase of your own branch, with a lease.
