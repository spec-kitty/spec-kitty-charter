# Mission Specification: Pack composition: depends_on and the dependency resolver

**Mission Branch**: `issue-145-pack-composition`
**Created**: 2026-11-02
**Status**: Draft
**Input**: User description: "pack composition — depends_on and the dependency resolver (epic #142, keystone #145, #146)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pack author declares what a pack depends on, and the offering resolves it (Priority: P1)

A pack author gives a pack descriptor a `depends_on` list: for each dependency, the pack id and a version range. When
a project configures that pack, the offering resolves the whole closure: every dependency is loaded exactly once,
dependencies come before the packs that need them, and a second pack that shares a dependency does not load it twice.

**Why this priority**: Nothing else in the named-packs split works without a descriptor that can express a dependency
and a resolver that can order one. The split (ADR-0048) and the 2026-11-02 rulings (ADR-0053 decisions 4 and 7) are
accepted, and the schema and resolver are the next two items in the work breakdown.

**Independent Test**: Configure a three-pack compose where B and C both depend on A. Reading the offering lists all
three, and the order puts A before B and C with no duplicate. A second fixture with an exact range on A resolves; a
range that excludes A's version is refused.

**Acceptance Scenarios**:

1. **Given** a pack whose descriptor declares `depends_on: [{pack_id: <A>, range: ">=1.0,<2"}]`, and A at 1.4.0 in the
   offering, **When** the project configures that pack, **Then** the resolved order contains A before it, A appears
   once, and the offering answers without a refusal.
2. **Given** two packs that both depend on A, **When** the compose resolves, **Then** A is loaded once, and the two
   ranges are reported as overlapping.
3. **Given** a pack whose descriptor declares no `depends_on`, **When** it is configured, **Then** it resolves exactly
   as it does today.

---

### User Story 2 - A broken compose is refused with a code that names the pack (Priority: P2)

A pack author or operator configures a compose that cannot work: a cycle, a dependency that is not in the offering, a
version outside the declared range, a parent that does not resolve, or a pack placed above the level it may depend on.
The refusal says which pack is wrong and why, and no partial compose is served.

**Why this priority**: A resolver that silently drops a dependency, or that serves half a compose, hides a broken pack
graph until an artifact read fails somewhere else. Refusals are how the charter stays honest.

**Independent Test**: One fixture per failure mode, each configured directly. Every one is refused, the refusal names
the offending pack id, and the offering is not served.

**Acceptance Scenarios**:

1. **Given** A depends on B and B depends on A, **When** the compose resolves, **Then** it is refused with the cycle
   code, naming both packs.
2. **Given** A depends on a pack id that no configured pack declares, **When** the compose resolves, **Then** it is
   refused, naming A and the missing id.
3. **Given** A declares `range: ">=2.0"` and the only B is 1.9.0, **When** the compose resolves, **Then** it is
   refused, naming A, B and the range.
4. **Given** a pack whose `parent_pack` names an id no pack declares, **When** it is configured, **Then** it is
   refused rather than degraded to a root pack.
5. **Given** a `project` pack that depends on another `project` pack, and an `org` pack that depends on a
   `project` pack, **When** each is configured, **Then** each is refused, because a pack may only depend on its own
   level or a lower one.

---

### User Story 3 - A cross-pack reference names the pack it relies on (Priority: P3)

An artifact in one pack points at an artifact in another by writing `<pack-name>::<urn>`. A reference inside the same
pack stays bare. A bare reference that would only resolve in another pack is refused, so a reader can see which pack
an artifact depends on and the closure check can name the pack that is missing.

**Why this priority**: Without qualification, a bare id that happens to exist in two packs resolves by load order.
With qualification, the dependency is visible and checkable, which is the point of the named-packs split.

**Independent Test**: A two-pack compose where a directive in A references `B::directive:DIRECTIVE_009`. The read
resolves it. The same reference written bare is refused and names B. A reference to `C::directive:...` with C outside
the closure is refused and names C.

**Acceptance Scenarios**:

1. **Given** a pack that references an artifact of another pack in its closure, **When** the reference is written
   `<pack-name>::<urn>`, **Then** it resolves and the artifact read answers.
2. **Given** the same reference written bare, and the id resolves only in the other pack, **When** the pack loads,
   **Then** it is refused, naming the pack that holds the id.
3. **Given** two packs in one compose that declare the same name, **When** the compose resolves, **Then** it is
   refused, because a qualified reference would be ambiguous.

---

### Edge Cases

- A dependency range that parses as PEP 440 but names an exact version that is not held: refused as out of range, not
  as unknown.
- Two ranges on the same pack that touch at one version only (`<2` and `>=2`): they do not overlap, so the compose is
  refused.
- A dependency on the pack itself: a cycle of length one, refused with the cycle code.
- A pack declared at no level (`pack_level` absent): today it reads at the configured level (a diagnostic, not a
  refusal); after this mission the descriptor version that adds `depends_on` makes `pack_level` required.
- A compose whose only pack is a single tree (today's `built-in`): it resolves with no edges and behaves exactly as
  before.
- A qualified reference whose pack is in the descriptor's `depends_on` but not in the project's closure: refused, and
  the message names the pack that is missing from the closure.
- A URN whose kind token is multi-word: the kind token spelling is not decided yet (ADR-0053 open question 3), so the
  mission must not invent a second spelling while resolving one.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Descriptor declares dependencies | As a pack author, I want to declare each dependency's pack id and version range so that a compose can be resolved without out-of-band knowledge. | High | Open | [build] | no |
| FR-002 | Range grammar is PEP 440 | As a pack author, I want one range grammar across the descriptor, so that `depends_on` and `requires_core` read the same way. | High | Open | [build] | no |
| FR-003 | Identity fields are ULIDs | As an operator, I want `pack_id` and `parent_pack` to hold ULIDs so that a reference cannot point at a display name that later changes. | High | Open | [build] | no |
| FR-004 | Dependencies resolve before dependents | As a consumer, I want the resolved order to place every dependency before the packs that need it, so that a later pack can override an earlier one deliberately. | High | Open | [build] | no |
| FR-005 | A shared dependency loads once | As an operator, I want a dependency shared by several packs loaded exactly once, so that one compose has one copy of each pack. | High | Open | [build] | no |
| FR-006 | Cycles are refused | As an operator, I want a cycle refused with the packs named, so that a broken graph never reaches a read. | High | Open | [build] | no |
| FR-007 | Unknown dependencies are refused | As a pack author, I want an unknown dependency id refused with the referring pack named, so that a typo is caught at configuration time. | High | Open | [build] | no |
| FR-008 | Out-of-range versions are refused | As a pack author, I want a held version outside every declared range refused with the range named, so that a compose never runs against a version it did not ask for. | High | Open | [build] | no |
| FR-009 | Parent packs must resolve | As an operator, I want an unresolvable `parent_pack` refused rather than degraded, so that lineage is never silently invented. | High | Open | [build] | no |
| FR-010 | Level rule is enforced | As an operator, I want a pack to depend only on its own level or a lower one (project, then org, then built-in), so that a lower tier cannot lean on a higher one. | High | Open | [build] | no |
| FR-011 | One compose is one closure at one level | As an operator, I want a compose to be one `depends_on` closure at one level with ids unique inside it, so that composition and layer merge stay separate ideas. | High | Open | [build] | no |
| FR-012 | Sibling refinement is refused unless declared | As an operator, I want two sibling packs that refine the same artifact refused unless the distribution pack declares its own `overrides` edge, so that a conflict is a decision, not load order. | Medium | Open | [build] | no |
| FR-013 | Cross-pack references are pack-qualified | As a pack author, I want to write `<pack-name>::<urn>` to reference another pack, so that the dependency is visible in the artifact itself. | High | Open | [build] | no |
| FR-014 | In-pack references stay bare | As a pack author, I want a reference inside my own pack to stay bare, so that a pack's internal references do not churn when it is composed. | High | Open | [build] | no |
| FR-015 | A bare reference that only resolves elsewhere is refused | As a consumer, I want such a reference refused and the holding pack named, so that an accidental cross-pack coupling cannot resolve by accident. | High | Open | [build] | no |
| FR-016 | Pack names are unique in a compose | As an operator, I want duplicate pack names refused, so that a qualified reference is never ambiguous. | High | Open | [build] | no |
| FR-017 | The closure check names the missing pack | As a pack author, I want a qualified reference outside the closure refused with the missing pack named, so that I know which pack to add. | Medium | Open | [build] | no |
| FR-018 | Today's single tree still loads | As an operator, I want the current single `built-in` tree to load unchanged until the open-packs split lands, so that this mission does not force the split. | High | Open | [ratchet] | no |
| FR-019 | The contract carries the new refusals | As a contract consumer, I want the resolver's refusals as named codes, so that I can branch on them instead of on a message. | High | Open | [build] | no |
| FR-020 | The pack reads carry the dependency facts | As a contract consumer, I want the pack pin and offering reads to expose what a compose resolved, so that I can see the dependency a pack declared and the version that satisfied it. | Medium | Open | [build] | no |
| FR-021 | The logical model and architecture describe composition | As a maintainer, I want the logical model and the architecture pages to describe `depends_on`, the compose and the resolver's refusals, so that the docs do not describe a pack model the code no longer has. | High | Open | [build] | no |
| FR-022 | The glossary carries the new terms | As a pack author, I want Compose, Closure, Dependency edge and Qualified reference defined in the glossary and the contextive definitions, so that the specification and the code use one vocabulary. | Medium | Open | [build] | no |
| FR-023 | The change is recorded where readers look | As an operator, I want a changelog entry and the work-breakdown items 2 and 3 marked done, so that the split's plan reflects what landed. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Resolution is deterministic | The same configured packs and descriptor contents produce the same resolved order across 100 repeated runs, independent of directory listing order. | Reliability | High | Open |
| NFR-002 | Refusals are complete | 100% of resolver refusals carry a contract refusal code and name the offending pack id; none answers with a bare 500. | Reliability | High | Open |
| NFR-003 | Resolution cost is bounded | Resolving a compose of 50 packs with 200 dependency edges completes within 250 ms at p95 on the reference build. | Performance | Medium | Open |
| NFR-004 | Single-pack answers are unchanged | The offering and pack reads for a fixture of today's packs are byte-identical before and after this mission, except for additive fields. | Compatibility | High | Open |
| NFR-005 | Every refusal is testable | Each refusal code has at least one BDD scenario and one negative control on the same fixture, so a no-op implementation cannot pass. | Quality | High | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | The governing records | ADR-0048 as amended 2026-11-02 and ADR-0053 decisions 4, 6, 7, 8 and 9 decide this mission; the work breakdown items 2 and 3 are its scope. | Technical | High | Open |
| C-002 | 0.x contract rules | Every contract change follows ADR-0022: a breaking change on the 0.x line takes a minor bump and a `### Breaking` entry, and no released contract is broken incompatibly. | Technical | High | Open |
| C-003 | Pack names are stable identifiers | A pack name becomes a reference prefix, so renaming a pack is a breaking change for its dependents and is refused by the resolver's name rules rather than tolerated. | Technical | High | Open |
| C-004 | The split is not in scope | Renaming `built-in` into `foundation` and `writing`, and re-homing artifacts, are the open-packs split's work, not this mission's; this mission keeps the single tree loadable. | Business | High | Open |
| C-005 | Do not decide URN kind tokens here | ADR-0053 open question 3 (whether multi-word URN kind tokens move to kebab case) is still open; no URN-parsing code is written until the operator rules, and no second spelling is introduced. | Technical | High | Open |
| C-006 | One contract family at a time | The contract changes land as one set; if another contract PR lands first, this branch rebases rather than merging two contract change sets. | Business | High | Open |

### Key Entities *(include if the mission involves data)*

- **Pack descriptor**: a pack's authored identity and lineage; now also the list of packs it depends on, each with a
  version range.
- **Pack dependency**: one edge from a pack to another pack id, with a range that must admit the held version.
- **Compose**: one `depends_on` closure at one level; the unit the resolver orders and the unit a pack name is unique
  inside.
- **Level**: the pack tier a pack sits at (`project`, `org`, `built-in`), which bounds the direction a dependency may
  point.
- **Pack-qualified reference**: a reference from one pack to an artifact of another, written with the pack name before
  the URN.
- **Pack pin**: the contract's existing statement of which pack version an activation builds on; it gains what the
  resolved dependency contributed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A five-pack compose with a diamond dependency resolves with every dependency before its dependents and no duplicate entry, in 100% of the scenario fixtures. — [build] · no-op passable: no
- **SC-002**: Each of the six refusal classes (cycle, unknown dependency, out-of-range version, unresolvable parent, level violation, duplicate pack name) is refused with its own code and names the offending pack, in 100% of fixtures. — [build] · no-op passable: no
- **SC-003**: A compose built only from today's packs produces offering and pack responses with zero differences other than additive fields, across the existing fixture set. — [ratchet] · no-op passable: no
- **SC-004**: A cross-pack reference written pack-qualified resolves, and the same reference written bare is refused and names the holding pack, in 100% of fixtures, with both assertions on one shared fixture. — [build] · no-op passable: no
- **SC-005**: The contract change set is recorded with a `### Breaking` entry where it breaks the 0.x line, and no code outside the recorded set changes. — [build] · no-op passable: no
- **SC-006**: Every page that describes the pack model either describes `depends_on` and the compose or links to the page that does, and the docs checks (`check_docs.py all`, markdownlint, cspell) pass with zero findings. — [build] · no-op passable: no

## Assumptions

- The operator confirms that the next mission is the descriptor schema and resolver (work breakdown items 2 and 3),
  before the artifact kinds of items 4 to 13.
- ADR-0053 open question 3 (URN kind tokens) is still open at mission start. The mission can build the resolver and
  the `<pack-name>::` qualifier without it, but the first URN-parsing change waits for the ruling. Recorded as C-005.
- ADR-0053 open question 1 (partly empty charter) and ADR-0054 open question 3 (interim trust mark) do not block this
  mission; they are reported to the operator in one batch with recommendations.
- The compatibility shim is temporary and is removed by the open-packs split; this mission does not schedule that
  removal.
- Pack names are unique in practice today because `built-in` is one pack; the uniqueness rule is enforced from the
  first multi-pack compose onward.

## Open decisions for the operator

1. **URN kind tokens (ADR-0053 open question 3).** Recommended: move multi-word URN kind tokens to kebab case inside
   the named-packs split migration, with a one-release reader that still accepts snake case. This mission writes no
   URN-parsing code until the ruling.
2. **Partly empty charter (ADR-0053 open question 1).** Recommended: keep the per-kind default, so only a wholly empty
   charter means nothing, and add an explicit "none" spelling rather than reading omission as deactivation.
3. **Interim trust mark (ADR-0054 open question 3).** Recommended: the project's activation file, where pack ids and
   versions are already named.
