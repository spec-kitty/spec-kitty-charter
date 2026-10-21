---
title: DDD patterns
description: The domain-driven design and architecture terms used in the charter service code, headlined by the difference between a domain entity and a persistence entity.
doc_status: active
updated: 2026-10-21T00:00:00Z
type: Glossary
kind: Glossary
glossary_context: "DDD patterns"
related:
  - development/how-to-ddd
  - development/code-style-and-conventions
  - architecture/architectural-patterns-applied
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the exact meaning of the DDD terms the service code and docs use.
---

# DDD patterns

The vocabulary of the charter service's code. Unlike the other glossary pages, this one is
authored in this repository, not imported from core. The reasoning behind it is in
[How to DDD here](/development/how-to-ddd/).

{#domain-entity-vs-persistence-entity}

## Domain Entity vs Persistence Entity

Two different things share the word "entity". A **domain entity** is a model object with an
identity that persists while its attributes change, and a lifecycle; two instances are equal when
their identifiers are equal (`Tactic`, identified by `TacticIdentifier`). A **persistence
entity** is a storage-mapping object, such as a database-row class; it exists to be saved and
loaded. Having a table, a file or a surrogate key does not make something a domain entity, and
being persisted does not stop something being a value object. In this codebase the persistence
shape is a DSO, never the domain entity. Domain identity and persistence fidelity are different
concerns: identity is `equals` on the entity; fidelity is value equality on the DSO.

{#value-object}

## Value Object

An immutable object defined wholly by its components and compared by value, with no identity
(`TacticStep`, `PackIdentifier`). Modelled as a Java `record`. A relationship defined entirely by
its two ends and its kind is a value object however it is stored.

{#aggregate-root}

## Aggregate Root

The one entity through which a cluster of related objects is reached and changed, so the cluster's
invariants hold together (`Pack`, over its `Tactic`s). Outside code holds a reference to the
root only; it never saves itself.

{#identifier-value-object}

## Identifier Value Object

A value object that wraps the raw identity of an entity so identities of different types cannot be
confused (`PackIdentifier`, `TacticIdentifier`). It replaces a bare `UUID` or `String`.

{#port}

## Port

A framework-free interface declared by the domain for something it needs from outside, such as
loading and saving (`TacticRepository`). Ports live in the domain and name only domain types.

{#adapter}

## Adapter

The code that connects a port to the outside world. A **driven** (outbound) adapter implements a
port, for example `DomainRepositoryService` over the filesystem. A **driving** (inbound) adapter
calls the application, for example a REST controller. Stereotypes such as `@Service` appear only
on adapters and on the application edge; the domain and kernel stay free of them.

{#projection}

## Projection

A read-oriented view derived from the model for a particular purpose, such as `PackSummary` or an
inspection result. It is derived, carries no identity of its own and is not a source of truth.

{#dto-vs-dso}

## DTO vs DSO

A **DTO** (data transfer object) is the shape that crosses the API: the generated contract types,
used only in the inbound adapter. A **DSO** (data storage object) is the shape that is written to
storage: `TacticDso`, a record that mirrors the stored document. Both are mapped to and from the
domain entity at the edge; the domain knows neither.
