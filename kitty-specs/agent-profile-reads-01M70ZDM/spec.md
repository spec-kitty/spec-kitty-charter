# Mission Specification: Agent profile reads

**Mission Branch**: `issue-407-agent-profile-reads`
**Created**: 2026-11-09
**Status**: Draft
**Input**: Operator brief of 2026-11-09 (remediation for #407 and #96), narrowed by the operator to the agent-profile half. Mission types, their steps, step contracts and templates (#99, #148) run as a separate, parallel mission with its own pull request.

**Context**: `docs/plans/engineering-notes/service-usability-run-2026-11-09.md` (gaps 1 and 3) and `research/squad-findings.md`.

## Intent Summary

- **Primary actor:** a client of the charter service. This is core at the seam, or a person using the API.
- **Trigger:** the client starts at the API root and wants the guidance an agent profile carries.
- **Outcome:** by following links only, the client reaches the profile's stored body and its lineage-resolved governance. It does so for the pack that holds the profile, and under an activation built from this repository's charter.
- **Rule:**
  - The body answered is the body the pack holds, with no loss of meaning.
  - The governance is walked from the profile's own outgoing edges (`specializes_from`, `requires`, `suggests`) under the same walker rules the other kinds use.
  - Under an activation, the governance honours the activation's in-force states.
- **Boundary:**
  - Mission types, steps, step contracts and templates are out of scope.
  - The effective step prompt belongs to #438.

## Domain Language

| Term | Meaning | Avoid |
|------|---------|-------|
| Agent profile | An `agent_profile` artifact of a pack (`*.agent.yaml`), identified by its `profile-id`. | persona, role file |
| Lineage | The chain of `specializes_from` edges from a profile up to its root ancestor. Lineage is never a profile field: the pack graph declares it (FR-028 hard cutover). | parent field, inheritance |
| Governance (of a profile) | The artifacts a profile reaches by its lineage and by the outgoing `requires` and `suggests` edges of it and its ancestors. Each is listed with the walk step, relation and path that reached it. | profile context, doctrine bundle |
| Walker rules | The step order the action context uses: own edges, `requires` closure, `suggests` up to a depth, `requires` of what is suggested, then one hop of vocabulary. | cascade (that word names only the `requires` closure) |
| Pack-scoped read | A read under `/packs/{packId}/…` that answers what one pack holds, resolved against its composed `depends_on` closure. | offering read |
| Activation-scoped read | A read under `/activations/{id}/…` that answers the layer-merge winner, with its in-force state. | effective read (that term names the existing lists) |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read a profile from its pack (Priority: P1)

A client finds `java-jenny` in the `built-in` pack's artifact list and follows the item's link. It receives her stored profile in the artifact envelope. The envelope also carries her governance: her lineage, what she requires (including `java-conventions` at the bumped open-packs pin), and what she suggests, with each suggestion's `when` and `reason`. Every governance entry links to the read of its target.

**Why this priority**: It is the P0 gap of the usability run. Almost every read core makes of a profile depends on it.

**Independent Test**: Register the open-packs corpus at the bumped pin, then GET `/packs/built-in/artifacts/agent_profile/java-jenny`. The body must match the pack file. The governance must list `styleguide:java-conventions` as required, and every link must resolve.

**Acceptance Scenarios**:

1. **Given** the `built-in` pack holds `java-jenny`, **When** a client reads `/packs/built-in/artifacts/agent_profile/java-jenny`, **Then** it gets `200` with the profile body, its governance, a `Charter-Source-Fingerprint` and links to `self`, the collection and the references.
2. **Given** a profile that `specializes_from` a parent, **When** it is read, **Then** the governance lists the parent as lineage, and lists the parent's `requires` and `suggests` targets as reached through the lineage.
3. **Given** a profile whose parent is not held by the composed closure, **When** it is read, **Then** it answers `200`, and the unresolved parent is reported in the governance as not held. It does not fail the read.
4. **Given** no profile with that id, **When** it is read, **Then** it answers `404 artifact_not_found`.
5. **Given** the artifact list of a pack, **When** a client filters by `kind=agent_profile`, **Then** each profile is listed with a link to its read.

---

### User Story 2 - Read a profile under an activation (Priority: P1)

A client creates an activation from this repository's `charter.yaml` with `agent_profile: java-jenny`, and follows the activation's links to its effective artifacts. The `java-jenny` item links to `/activations/{id}/artifacts/agent_profile/java-jenny`. That read answers the layer-merge winner's body and the pack that holds it, plus her governance. In the governance, each entry carries its in-force state under the activation.

**Why this priority**: Core reads profiles under the project's activation, not per pack.

**Independent Test**: Create the activation and read the effective list. Follow the profile item's `artifact` link and check the winner's `packId`. The governance entries must carry states that agree with the effective list.

**Acceptance Scenarios**:

1. **Given** an activation that selects `java-jenny`, **When** a client reads `/activations/{id}/artifacts/agent_profile/java-jenny`, **Then** it gets `200` with the winner's body, its `packId` and `level`, its state, and a governance whose entries carry states.
2. **Given** an artifact of any served kind in an activation's effective list, graph or action context, **When** the client reads the item, **Then** the item carries an `artifact` link to its activation-scoped read.
3. **Given** a profile that no pack of the activation holds, **When** it is read under the activation, **Then** it answers `404 artifact_not_found`.
4. **Given** an unknown activation, **When** it is read, **Then** it answers `404 activation_not_found`.

---

### User Story 3 - Follow links from the API root (Priority: P1)

A client knows only `GET /api/v1/`. It POSTs the activation document to the root's `activations` link and follows the answer's `self`, then the activation's effective artifacts, the profile item, the profile, and each governance entry. It does the same pack-scoped, from the root's `artifacts` link.

**Why this priority**: The brief's acceptance is link-following from the root, proved by the usability script.

**Independent Test**: The usability script walks the links without building any URL by hand, except to fill in templated variables the link declares.

**Acceptance Scenarios**:

1. **Given** the API root, **When** it is read, **Then** it links `activations` (method `POST`) to create an activation.
2. **Given** the usability script, **When** it is run against the service and open-packs at the bumped pin, **Then** it reaches `java-jenny`'s body and governance on both the pack-scoped and the activation-scoped path, and exits `0`.

---

### User Story 4 - Remove a profile from a pack (Priority: P2)

A pack maintainer deletes an agent profile from a registered pack, under the same rules that apply to the five kinds already served.

**Independent Test**: DELETE `/packs/{packId}/artifacts/agent_profile/{id}` on a writable pack. It answers like the existing removal does, and a later read answers `404`.

**Acceptance Scenarios**:

1. **Given** a writable pack holding a profile, **When** it is deleted, **Then** the answer is the one the five served kinds give, and the profile is gone.
2. **Given** a locked or required profile, **When** it is deleted, **Then** it is refused exactly as it would be for a served kind.

### Edge Cases

- A profile `specializes_from` a profile held by another pack in the composed closure, written with that pack's name (`built-in::…`).
- A `specializes_from` cycle. The walk terminates, and every node is listed once.
- A target reached both by `requires` and by `suggests`. It is listed once, under the stronger relation (the action-context rule).
- A `suggests` edge that carries `when` and `reason`. Both appear on the entry.
- Under an activation, a profile overridden by a higher layer. The winner's body and `packId` are answered.
- Under an activation, a governance target that is deactivated or excluded. It is still listed, with its state, and the walk does not descend past it (the action-context rule).
- An artifact kind that is not served, read through the activation path. It answers `404 kind_not_found`, as the pack path does.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Bump the open-packs pin | As a maintainer, I want the corpus pinned to open-packs `main` (`a2a9379`) with `packs/internal`, `packs/qa` and the lock re-vendored, and the round-trip gates green at the new pin, so that the `java-jenny → java-conventions` edge (#405) and the curated edges are what the service reads (#435). | High | Open | [build] | no |
| FR-002 | Serve the profile body pack-scoped | As a client, I want `GET /packs/{packId}/artifacts/agent_profile/{id}` to answer the stored profile in the artifact envelope, with every schema field, so that I can act on a profile's guidance. | High | Open | [build] | no |
| FR-003 | Answer the profile's governance | As a client, I want the profile read to carry its governance, walked from its own outgoing `specializes_from`, `requires` and `suggests` edges under the walker rules of the other kinds, so that I get the lineage-resolved guidance of the profile. | High | Open | [build] | no |
| FR-004 | Link every governance entry | As a client, I want each governance entry to carry its relation, its walk step, the node it was reached through, `when` and `reason` where the edge has them, and a link to its target's read, so that I can follow the governance without building URLs. | High | Open | [build] | no |
| FR-005 | List profiles in the artifact list | As a client, I want `agent_profile` listed and filterable in a pack's artifact list, with each item linking to its read and the references read open to it, so that I can find profiles. | High | Open | [build] | no |
| FR-006 | Remove a profile | As a pack maintainer, I want DELETE of an `agent_profile` to behave as it does for the five served kinds, so that removal is uniform. | Medium | Open | [build] | no |
| FR-007 | Read any served artifact under an activation | As a client, I want `GET /activations/{id}/artifacts/{kind}/{artifactId}` to answer the layer-merge winner of a served kind: its body, `packId`, `level` and state. For `agent_profile`, it also answers the governance, each entry with its in-force state. This lets me read what is in force for my project. | High | Open | [build] | no |
| FR-008 | Link effective items to their reads | As a client, I want items of a served kind in the effective list, the effective graph and the action context to carry an `effective-artifact` link to their activation-scoped read, so that I can reach bodies from the activation (the item-link part of #450). | High | Open | [build] | no |
| FR-009 | Link the activations collection from the root | As a client, I want the API root to link to the activations collection, so that a client starting at `/api/v1/` can reach the activation reads (part of #454). | Medium | Open | [build] | no |
| FR-010 | Refuse missing profiles plainly | As a client, I want an unknown profile to answer `404 artifact_not_found`, and a kind that is not served to answer `404 kind_not_found`, on both paths, so that refusals stay uniform. | Medium | Open | [build] | no |
| FR-011 | Prove it with the usability script | As the operator, I want a committed, rerunnable usability script that registers the open-packs corpus at the pin and activates this repository's `charter.yaml` with `java-jenny`. It must follow links from `/api/v1/` to her body and governance, pack-scoped and under the activation, and exit `0` only if every hop resolves. This proves the acceptance. | High | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Round-trip fidelity | Every profile in the open-packs corpus at the pin is answered with its meaning intact (ADR-0036). This is checked for 100% of the corpus's profiles by an automated test. | Correctness | High | Open |
| NFR-002 | Contract discipline | Every contract change is recorded in change fragments, one per `###` block (ADR-0047), `@contract` scenarios and an example with `_links`. `./gradlew verifyAll` passes on Java 25 with no new suppression. | Governance | High | Open |
| NFR-003 | Read latency | A profile read, pack-scoped or under an activation, of the open-packs corpus answers in under 500 ms at p95 on a warm service on the CI runner, measured by the usability script over 20 reads. | Performance | Medium | Open |
| NFR-004 | Bounded walk | The governance walk visits each node at most once per step and terminates on a `specializes_from` cycle. A cycle fixture finishes in under 1 s. | Reliability | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Slice-owned rings | The profile read lives in the profiles slice (application, inbound API, adapter over the packs application ring), on the same pattern as the activations adapter. No ArchUnit rule is weakened. New rules gate the new rings, and each rule has a fixture that proves it bites. | Technical | High | Open |
| C-002 | Charter's own model | The shapes are judged against the charter's own model, contract and packs, not against core's current behaviour (charter, epic #186). | Governance | High | Open |
| C-003 | No mission-type surface | No read, schema or link for mission types, steps, step contracts or templates is added. That is the parallel mission's. | Scope | High | Open |
| C-004 | ADR-0022 versioning | The contract stays on the 0.x line. Any break is recorded under `### Breaking`, with what core's seam must change. | Governance | High | Open |
| C-005 | Hot files shared with #437 | Edits to `CharterRefusalCode.yaml`, `ReservedRefusalCodesTest`, `ContractDescriptionsTest` and `errors.feature` are kept to the minimum. No new refusal code is added unless the plan proves one is needed. | Coordination | Medium | Open |
| C-006 | Build discipline | Gradle runs in the foreground with `--max-workers=1`, one at a time. Commits are SSH-signed. | Process | Medium | Open |

### Key Entities

- **Agent profile (body)**: identity, roles, capabilities, purpose, specialization, collaboration, mode defaults, specialization context, directive and artifact references, self-review protocol, exclusions.
- **Governance entry**:
  - the target artifact (kind, id, holding pack);
  - the relation (`specializes_from`, `requires`, `suggests`, vocabulary);
  - the walk step;
  - the node it was reached through, and the hop count;
  - `when` and `reason` if the edge has them;
  - under an activation, its state;
  - a link to the target's read.
- **Activation-scoped artifact**: the layer-merge winner of one served kind and id: body, `packId`, `level`, state. For a profile, also the governance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Starting from the API root, the usability script reaches `java-jenny`'s body and every one of her governance entries on both paths, with 0 unresolved links. — [build] · no-op passable: no
- **SC-002**: The same script, run on `main` before this mission, fails at the first profile hop. This is recorded in the mission's research as the red baseline. — [ratchet] · no-op passable: no
- **SC-003**: `java-conventions` appears as a required entry of `java-jenny`'s governance at the bumped pin. — [build] · no-op passable: no
- **SC-004**: 100% of the corpus's agent profiles are readable pack-scoped, with a body that round-trips. — [build] · no-op passable: no
- **SC-005**: The pull request is green on `CI gate / gate`, with the whole-pack round trip included. — [build] · no-op passable: no

## Assumptions

- Lineage is a graph edge, never a profile field: core's FR-028 cutover, and the profiles domain Javadoc.
- **Walker rules:** "the same walker rules as the other kinds" means the action-context walk. The profile node's own outgoing edges stand in for the action's `scope` step. `specializes_from` is followed transitively first, and each ancestor's own edges join the first step.
- **Pack-scoped walk:** it is resolved over the pack's composed `depends_on` closure, as the references read is today.
- **Activation-scoped walk:** it is resolved over the activation's merged closure, with its in-force predicate.
- A profile's body references (`directive-references` and the others) are body fields. They are not walked; only graph edges are.
- Only kinds already served are linked from effective items. Mission-type kinds will be linked by the parallel mission.
