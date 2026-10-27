# Mission Specification: Agent profiles round-trip through the domain

**Mission Branch**: `feat/190-agent-profile-round-trip`
**Created**: 2026-10-27
**Status**: Draft
**Input**: Issue [#190](https://github.com/spec-kitty/spec-kitty-charter/issues/190), part of epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186) (Wave 1, alongside #191, #192
and #193).

> These mission documents were written by hand. The session could not install the Spec Kitty CLI
> (permission denied), so there is no CLI-generated `meta.json`, status log or lane file.

## Intent Summary

The **primary actor** is the whole-pack round trip: the gate (#187) and the whole-pack seam
(ADR-0037). The **trigger** is a pack that holds `agent_profiles/*.agent.yaml` files. The
**outcome**: each agent profile is read into a domain entity, written back from it, and the written
file means the same as its source (ADR-0036); the gate reports `agent-profile` green. The
**invariants**: lineage stays a DRG `specializes_from` edge in the graph shard, never a profile
field; nothing resolves `enhances` or `overrides` or selects a profile (ADR-0032); the HTTP contract
does not change.

**Confirmation basis.** The issue text and the operator's brief for Wave 1. The grounding is in
[research/grounding-squad.md](research/grounding-squad.md); every finding there is dispositioned.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pack's agent profiles load into the domain (Priority: P1)

A pack is loaded and its agent profiles are in the aggregate as `AgentProfile` entities, every
declared field kept, beside the five kinds already bound.

**Independent Test**: load the pinned `built-in` pack; the aggregate holds its 26 agent profiles with
no read violations. The `qa` packs have no `pack.yaml`; each of their 5 profile files reads and
writes back by meaning.

**Acceptance Scenarios**:

1. **Given** `built-in/agent_profiles/architect-alphonso.agent.yaml`, **When** the pack is loaded, **Then** the aggregate holds an `AgentProfile` whose id is `architect-alphonso`, URN `agent_profile:architect-alphonso`, with its roles, specialization, collaboration, mode defaults and references as authored.
2. **Given** a profile with an unknown field (top level or nested), **When** the pack is loaded, **Then** the file is a read violation (`ARTIFACT_SCHEMA_ERROR`) and is not in the aggregate.
3. **Given** a profile that declares `specializes_from`, `enhances` or `overrides`, **When** the pack is loaded, **Then** it is refused as an unknown field.

### User Story 2 - A written profile means what its source meant (Priority: P1)

The aggregate's agent profiles are written back and each written file compares equal by meaning to
its source.

**Independent Test**: the round-trip gate (`:packs:wholePackRoundTrip`) reports every `agent-profile`
file green, with `agent-profile` no longer in the expected-red baseline and no other kind's verdict
changed.

**Acceptance Scenarios**:

1. **Given** a profile that omits `max-concurrent-tasks`, **When** it is written, **Then** the written file omits it too (no default is invented).
2. **Given** `code: "003"` and `schema-version: "1.0"`, **When** written, **Then** both stay strings.
3. **Given** `routing-priority: 50` and `sentinel: true`, **When** written, **Then** they stay an integer and a boolean.
4. **Given** `excluding` in its list form, its map form, or a map entry with an empty list, **When** written, **Then** it is written as read.
5. **Given** an existing profile file whose name is not `<id>.agent.yaml`, **When** the profile is saved, **Then** that file is rewritten in place, found by `profile-id`.

### Edge Cases

- A file with an explicit `null` for a nullable field (`model: null`) equals one without it.
- A folded block scalar that ends in a newline keeps it.
- Two profile files with the same `profile-id` in one pack break the aggregate's local-id invariant.

## Requirements *(mandatory)*

- **FR-001**: An `AgentProfile` domain entity implements `CharterArtifact` from its own slice
  (`ai.speckitty.charter.profiles.domain`, module `:profiles`), per ADR-0035 decision 4. It holds
  every field the schema declares and keeps whether each defaulted field was declared.
- **FR-002**: Its invariants mirror the schema's constraints (roles non-empty and well-formed,
  routing priority 0 to 100, concurrency above 0, schema version `1.0` or `2`, a non-blank id).
- **FR-003**: `relationships()` mints no edge: profile edges, `specializes_from` included, are
  authored in the graph shard, which the whole-pack seam keeps as authored.
- **FR-004**: The kind is bound in `packs.infra.storage` (DSO, YAML format, binding), appended to
  `KindBindings` and `YamlFormats`. Its file token is `agent`, its schema `agent-profile`, its id key
  `profile-id`.
- **FR-005**: `KindBinding` names a kind's schema and id key (defaults: the token and `id`); the file
  walk, schema validation and file location resolve through them.
- **FR-006**: The gate models `agent-profile`, its baseline entry is removed, and `excluding` is one of
  its open maps.
- **FR-007**: The architecture test registers the `profiles` slice and gates its domain's purity.

## Constraints

- **C-001**: No change to `contracts/charter` (the HTTP contract) or to any pack schema.
- **C-002**: Shared files take append-only blocks for this kind, except the two seam defaults of
  FR-005 and their consumers (file walk, schema validation, file location, the gate adapter), which
  #191 needs too, and the comparator fix found on the way.
- **C-003**: No profile selection, lineage resolution or `enhances`/`overrides` semantics.
- **C-004**: Loading profiles changes two answers without a contract change: a relationship from a
  profile of the pack now resolves (it was `registration_conflict`), and a pack with two files of one
  `profile-id` no longer loads. Both are pinned by tests and stated in the changelog.

## Success Criteria *(mandatory)*

- **SC-001**: `:packs:wholePackRoundTrip` passes with all 26 built-in agent profiles green.
- **SC-002**: `./gradlew verifyAll` passes (CI on Java 25), including `WholePackOpenPacksRoundTripTest`
  over every pinned and vendored pack.
- **SC-003**: The ArchUnit gate passes with `profiles` registered and its known-bad controls biting.

## Out of scope

Other kinds (#191 to #195); HTTP read or registration of profiles; a profile repository port;
validate-mode duplicate detection by `profile-id`; schema hardening of `profile-id`.
