# Research squad findings (2026-11-09)

Three lenses ran before specify: the domain modules and the gap to the REST adapter; the shapes core reads;
and the overlap with the ADR-0066 work and the open-packs pin bump. Only the findings for agent profiles are
kept here. The mission-type findings went to the parallel mission through the orchestrator.

## The gap between the domain and the REST adapter

- **Storage is done on `main`.**
  - `packs/infra/storage/KindBindings.java` binds `AgentProfileBinding`: `*.agent.yaml`, schema
    `agent-profile`, id key `profile-id`.
  - `contracts/packs/schemas/agent-profile.schema.json` validates every profile at load.
  - `Pack.artifacts(kind)` returns the parsed `AgentProfile`s.
- **The domain is done.**
  - `profiles.domain.AgentProfile` models the whole schema.
  - Lineage is not a field (Javadoc lines 21-24). `specializes_from`, `requires` and `suggests` are graph
    edges (`RelationType`).
  - The profiles slice has no application ring, no API ring and no adapter.
- **What blocks the read.**
  - In `ArtifactReadService`:
    - `SERVED` (:63-68) and `ABSENT` (:72-77);
    - the sealed `Item` (:146-225);
    - the `servedItem` switch (:439-485);
    - `kind_not_found` (:323, :374).
  - Removal shares `SERVED` (`ArtifactRemovalService.java:147`), and so does list order
    (`ArtifactPosition.java:22`).
- **ArchUnit** (`HexagonalArchitectureTest`):
  - `APPLICATION_IS_EDGE` (:258) bars `packs.application` from naming another slice's domain.
  - `ENTRYPOINT_STAYS_IN_ITS_SLICE` (:529) bars `packs.infra.api` from naming the profiles and missions
    domains.
  - Only `packs.infra.storage` may name them.
  - The activations slice reaches packs only through `activations.infra.packs`, which names
    `packs.application` and `packs.domain` (:990-1010). That is the pattern this mission follows.
- **Activation reads.**
  - `EffectiveReadService` covers the list, the graph and the action context.
  - None of their items links to a body.
  - No activation-scoped body read exists for any kind.
- **Contract.**
  - `Artifact.yaml` has a `oneOf` of five members, and `ArtifactKindToken` is an enum of five.
  - `ActivatableKind.yaml` already holds `agent_profile`.
  - The API root links `info`, `offering` and `artifacts`, but not the activations (#454).

## What core reads of a profile (oracle model only)

- **The model.**
  - `AgentProfile` in `src/charter/offering/agent_profiles/profile.py` uses `extra="forbid"`.
  - Lineage keys in a profile are an error (FR-028).
  - `AgentProfileRepository._lineage_parent` takes the first `specializes_from` target by sorted id.
  - `resolve_profile` merges from the root to the leaf.
- **Rendering.** `charter context --profile` renders, in order:
  1. the cited directives and tactics, with bodies;
  2. the cited styleguides and toolguides, as pointers;
  3. the procedures reached on the profile channel `{requires, specializes_from, suggests}`;
  4. the suggestions, labelled by `when`, leaving out anything in the `requires` closure.
- **A gap in core.** A requires edge that exists only in the graph, such as `java-jenny → java-conventions`,
  is in none of the sections that core renders. This service answers it as a required governance entry.
- **Edges in the built-in graph** (`packs/built-in/agent_profile.graph.yaml`): 169 `requires`, 35
  `suggests` and 5 `specializes_from`.

## The overlap with #437 and #438, and the pin bump

- **#437** (branch `issue-424-428-adr-0066-service-alignment`) has pushed planning only.
  - Its WP04 and WP05 own `CharterRefusalCode.yaml`, `ReservedRefusalCodesTest`, `ContractDescriptionsTest`
    and `errors.feature`.
  - Its WP01 owns `WholePackOpenPacksRoundTripTest` and `whole-pack/expected-red.yaml`, which the pin bump
    also changes.
- **#438** adds an effective step prompt read and a `prompt` relation. It shares nothing with profiles.
- **The pin bump** (`22c14b2d` → `a2a9379`, 50 commits) changes the following:
  - `openPacksRef` in `gradle.properties`;
  - `syncPacks -PupdatePacksLock=true`, which re-vendors `packs/internal` and `packs/qa` and rewrites
    `packs/open-packs.lock.yaml`;
  - the corpus counts: the manifest constituents drop from about 315 to 310, and `expected-red.yaml` may
    change.
- **What it brings.**
  - `java-jenny requires java-conventions` (#405).
  - The same kind of edge for drupal-dries, freddy and node-norris.
  - Duplicate `requires`/`suggests` pairs collapsed (#135).
  - The new `spdd` pack. It is outside the whole-pack gate, which reads only `built-in`.
- **No usability script was committed** with the engineering note (branch
  `docs/service-usability-run-2026-11-09`). This mission reconstructs one.
