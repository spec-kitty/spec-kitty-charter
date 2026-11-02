---
title: "ADR-0051: The event registry: a thin, provider-neutral artifact for a consistency check"
description: "A pack may hold an event_registry (namespace, type, name per event) so an emit can be checked; the charter holds no schemas, envelopes, payloads or built-in events."
doc_status: active
updated: 2026-11-02T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-02'
type: ADR
kind: ADR
related:
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0026-write-surface-is-functional-registration
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - plans/pack-ecosystem-work-breakdown
  - architecture/events-and-ledger
  - plans/pack-ecosystem-design-notes
audience:
  - profile: pack-author
    guidance_text: Read this for how a pack declares the events its mission types and lifecycles emit, and what the load checks.
  - profile: charter-service-contributor
    guidance_text: Read this before binding the event_registry kind or writing the emit resolution check.
  - profile: core-maintainer
    guidance_text: Read this for what the charter declares about events and what stays the client's job (sending, shaping, payloads).
---

# ADR-0051: The event registry: a thin, provider-neutral artifact for a consistency check

## Status

**Accepted**, 2026-11-02. Decider: Stijn Dejongh (operator). Formalizes the operator rulings of 2026-11-02
recorded in the [pack ecosystem design notes](/plans/pack-ecosystem-design-notes/) (section 4, "Event
declarations": R3, R4, R5 and the second revision with the entry shape, option b), on epic
[#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142). It answers the notes' open question 9.
The rulings are not re-opened here. This record adds the shape details and the consequences.

## Context

- [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/) lets a mission type and a
  lifecycle declare actions: `on_enter`, `on_exit` and `on_transition`, each a list of `emit` entries naming an
  event type. Something must say which event types exist, or a typo in an `emit` passes silently.
- **The charter is the source of truth for declarations, and the client runs them** (the responsibility
  split, ruled 2026-11-02). The charter service sends no events
  ([Events and the ledger](/architecture/events-and-ledger/)).
- **spec-kitty-events** publishes a catalog (`producer_contract/registry.json`, `registry_version` 1.1.0,
  36 CloudEvents types under `ai.spec-kitty`, each with a closed data schema). Some of its types name
  software-dev's steps (`ai.spec-kitty.mission.{specify,plan,tasks}.{started,completed}.v1`), so a mission type
  with other steps has no event of its own.
- **Two richer designs were rejected by the operator** on 2026-11-02:
  - pinning the spec-kitty-events contract and validating every `emit` against its catalog (E1 to E3). That
    couples the charter service to one events provider;
  - an `event_contract` kind with data schemas and an envelope format (R1, R2). It still made the charter
    the keeper of payload rules that belong to whoever sends and consumes the events.

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### A thin `event_registry` kind

1. **A pack may contain `event_registry` artifacts.** An event registry lists event types. It holds nothing
   else: no data schema, no envelope format, no payload rules, no transport.
2. **The shape** (proposed; the schema goes into the canonical pack schemas, #114):

   | Field | Required | Shape |
   |---|---|---|
   | `schema_version` | yes | `"1.0"` |
   | `id` | yes | kebab-case, the file stem |
   | `title` | yes | string |
   | `description` | no | string |
   | `entries` | yes | at least one entry (below) |

   An **entry** (ruled, option b):

   | Field | Required | Shape |
   |---|---|---|
   | `namespace` | yes | a reverse-DNS prefix, for example `com.acme` |
   | `type` | yes | the event's local name with its version, for example `review.requested.v1` |
   | `name` | yes | the human label, for example "Review requested" |

3. **The full identifier is derived, never stored.** It is always `namespace + "." + type`
   (`com.acme.review.requested.v1`). Storing it would repeat two fields (sanitized-shape rule S5,
   [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)). Within one compose a full
   identifier is unique.
4. **Layout** (proposed): `event_registries/<id>.event-registry.yaml`, read at
   `/packs/{packId}/artifacts/{kind}/{id}` and registered through a per-kind registration route, as every
   registrable kind has today (`PUT /packs/{packId}/artifacts/event_registry/{id}`,
   [ADR-0026](/adr/0026-write-surface-is-functional-registration/)), with the kind token `event_registry` in the path
   (the spelling is ADR-0050 open question 9). The operator token is `event-registry`. A registry is not a CRG node in
   this cut: the load checks `emit` entries against it directly. The kernel gives every artifact kind a node kind, so
   either `event_registry` gets one that no edge targets (as `asset` has) or that rule gains a stated exception; the
   follow-ups list it.
5. **Not activatable.** A registry is reference data, like an asset or a template. It is in force when its pack
   is loaded, and no preset lists it.

### Ownership of a namespace (R3)

6. **A namespace is claimed by exactly one registry in a compose.** Two registries that list entries under the
   same namespace are refused at compose time with a structured error naming both. A registry may list
   entries under more than one namespace.
7. **Overlap means equal or nested on a dot boundary.** `com.acme` and `com.acme.review` overlap: claimed by two
   registries, `com.acme` + `review.requested.v1` and `com.acme.review` + `requested.v1` would join to the same
   identifier. Refusing nested claims makes every joined identifier unique by construction. `com.acme` and
   `com.acmecorp` do not overlap. One registry may list both a namespace and a namespace nested in it, and must then
   keep its own joined identifiers unique.
8. **Reserved namespaces are deployment configuration, not code.** A deployment may reserve a namespace (for
   example `ai.spec-kitty` for the registrant that publishes it). The service has no built-in list.

### Resolution (R4)

9. **An `emit` must resolve.** An `emit` in a mission type, a lifecycle or a step enhancement names its event
   type either as the joined string (`com.acme.review.requested.v1`) or as `{namespace, type}`. It must match an
   entry of a registry in the declaring pack or in that pack's `depends_on` closure
   ([ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/)). Otherwise the load is refused.
10. **There is no separate pin mechanism.** A pack that emits events lists the registry's pack in `depends_on`
    with a version range, like any other dependency. Which version of a provider's catalog a pack targets is
    the version range on that pack.

### What the charter does not hold (R5)

11. **No built-in standard events.** Which standard events a client always sends (step entered, step exited,
    transition taken) is the client's concern. A mission type or lifecycle may declare such events in
    `on_enter` and `on_exit` like any other event, but the service holds no list of them.
12. **No payloads.** Filling an event's data from the transition context (Mission id, step id, work-package id,
    actor) is the client's job (the earlier E5), and the charter checks no payload.
13. **No provider coupling.** The service knows the shape of a registry, never who publishes it.
    spec-kitty-events becomes one registrant among others: a pack (registered, or vendored into
    spec-kitty-open-packs) holds an `event_registry` with its `ai.spec-kitty` entries.

<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- A typo in an `emit` is refused at load, at the cost of one small file per provider.
- The charter service stays provider-neutral. Any events provider, or none, can be used by a client.
- The registry reuses the pack, registration and `depends_on` machinery; there is no new pin or fetch path.

### Negative

- The check is shallow: it proves that a type is declared, not that its payload fits or that anyone consumes it.
- A provider's catalog is duplicated as a registry (names only), and the two can drift until the provider
  publishes the registry itself.

### Risks

- If a client grows a dependency on events the charter does not declare, the registry stops being complete.
  The registry claims consistency of declarations only, never completeness of what a client sends.

### Follow-ups

Nothing below is applied in this record.

- **Pack schemas.** `event-registry.schema.json` and the kind binding (#114, ADR-0037); a `files` rule for
  `*.event-registry.yaml`; the `pack-manifest` constituent kind enum. The `emit` entry shape is defined once and
  used by the mission-type, lifecycle and step-enhancement schemas (ADR-0050).
- **Kernel facts** (`ArtifactKind`, `NodeKind`): the node kind for `event_registry` that no edge targets, or the
  stated exception to "every artifact kind has a node kind" (decision 4).
- **Contract, additive** ([ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)): the kind's
  read and registration paths.
- **Contract, breaking on a 0.x minor** (a `### Breaking` entry, ADR-0022): the new member of the closed `Artifact`
  `oneOf`, its not-found code, and the compose refusals for an overlapping namespace and an unresolved `emit`.
- **spec-kitty-events** (an issue to raise there, the earlier E4): step-specific core types such as
  `ai.spec-kitty.mission.specify.started.v1` do not fit declared mission types; generic step events, with the
  step and type in the data, would. The same repository could publish its catalog as an event registry.
- **spec-kitty-open-packs.** A pack with the `ai.spec-kitty` registry entries the shipped mission types emit,
  in the `depends_on` closure of the foundation pack.

## Open questions

This record is accepted; the questions below still await an operator ruling, each with a recommended default.

1. **Activation** (decision 5): not activatable; in force with its pack.
2. **Overlap** (decision 7): equal or nested on a dot boundary is an overlap. The stricter reading follows the ruling
   ("overlapping claims are refused") and keeps joined identifiers unique.
3. **Layout and node kind** (decision 4): `event_registries/<id>.event-registry.yaml`; not a CRG node.
4. **One `emit` spelling**: see ADR-0050 open question 11.

## Alternatives considered

### Validate against a pinned spec-kitty-events contract (E1 to E3)

Rejected by the operator. It couples the charter service to one events provider and its release line.

### An `event_contract` kind with data schemas and an envelope format (R1, R2)

Rejected by the operator in the second revision. Payload shape belongs to the sender and the consumers; the
charter would have held rules it never enforces. AsyncAPI import was noted as a later possibility under R2 and
falls with it.

### No registry: free-text event names

Rejected. Nothing would catch a misspelled `emit`, and nothing would stop two packs from using one name for two
events.

## References

- [Pack ecosystem design notes](/plans/pack-ecosystem-design-notes/), section 4, "Event declarations".
- Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142).
- [CloudEvents specification](https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md), the `type`
  attribute's reverse-DNS convention.
- spec-kitty-events `producer_contract/registry.json` (`registry_version` 1.1.0).
