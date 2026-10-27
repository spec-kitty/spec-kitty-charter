---
title: How to DDD here
description: How to tell an entity, value object and aggregate root apart in the charter service, why identity and persistence fidelity differ, and what not to copy.
doc_status: active
updated: 2026-10-21T00:00:00Z
type: Guide
kind: Explanation
related:
  - development/code-style-and-conventions
  - architecture/architectural-patterns-applied
  - development/ddd-patterns
  - adr/0006-onion-vertical-slices-archunit
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add an aggregate, entity, value object, port or adapter, to classify it correctly.
  - profile: core-maintainer
    guidance_text: Read this to see how the service separates domain identity from the shape it is stored in.
---

# How to DDD here

A worked guide to the reasoning, not a textbook. The vocabulary is in the
[DDD patterns reference](/development/ddd-patterns/); the construction rules are in
[Code style and conventions](/development/code-style-and-conventions/); the layers and their
diagrams are in [Architectural patterns applied](/architecture/architectural-patterns-applied/).
The running example is the `packs` slice: `Pack` holds `Tactic`s, and a tactic holds steps and
references.

## Classify a thing in three questions

1. **Does it have an identity that outlives its attributes?** If two instances with every
   field different are still "the same tactic" because they share an id, it is an **entity**.
   If two instances with the same fields are interchangeable, it is a **value object**.
2. **Is it the entry point to a cluster that must change together?** The one object the outside
   world talks to, which guards the cluster's invariants, is the **aggregate root**. Children
   are reached only through it.
3. **Does it do something that belongs to no one object?** Then it is a **domain service**, a
   stateless operation over domain types.

| Thing | Answer | Why |
|---|---|---|
| `Pack` | Aggregate root | Identified by `PackIdentifier`; owns its descriptor and tactics |
| `Tactic` | Child entity | Identified by `TacticIdentifier` within its pack; equal when the ids match |
| `TacticStep`, `TacticReference`, `PackPaths` | Value objects | Defined wholly by their parts; order and duplicates in a list are significant |
| `PackIdentifier`, `TacticIdentifier` | Identifier value objects | Typed wrappers so identities cannot be confused |
| `TacticDso`, `PackDescriptorDso` | Not domain concepts | Storage shapes; see below |

## Identity and fidelity are different concerns

This is the crux, and the defect that motivated the rule (issue #138) was mixing them up.

- **Domain identity** answers "is this the same tactic?". `Tactic.equals` compares the
  `TacticIdentifier`. Changing a step does not make it a different tactic.
- **Persistence fidelity** answers "did the file survive a load and a save unchanged?". That is
  a question about *content*, so it needs *value* equality over every stored field.

If you assert fidelity with the entity's identity-equals, the test passes whenever the ids
match, even when a step was dropped. It cannot fail, so it proves nothing. If you instead make the
entity compare every field so the test has teeth, you have destroyed its identity semantics.

The resolution is to assert each at its own layer:

```text
load  ->  TacticDso (record)  ->  write  ->  reload  ->  TacticDso
assertThat(dsoAfter).isEqualTo(dsoBefore)      // value equality, at the storage adapter
```

The DSO is a record, so `equals` is field-wise by construction. The round-trip test lives in the
`infra.storage` tests and runs on DSOs. The entity keeps identity equality.

## A storage structure is not a domain concept

Model the meaning, derive the structure. The charter graph is how the packs are *stored and
resolved*; it is not what a pack *is*. A relationship between two charter artifacts is a domain
meaning. The edges list of a graph is how one storage format spells it. If the domain types are
shaped like the file (nodes, edges, shards), every change to the file format ripples into the
core, and the language of the domain gets replaced by the language of the storage.

So the aggregate `Pack` is lightweight and meaningful (descriptor, tactics, violations), and it is
now the single pack model: the earlier graph-coupled `CharterPack` transitional type has been
removed. The storage adapter derives whatever structure it needs from the meaning.

## A row is not an entity

"Entity" means two different things, and the DDD patterns reference headlines the distinction
([Domain Entity vs Persistence Entity](/development/ddd-patterns/#domain-entity-vs-persistence-entity)).

- A **domain entity** has identity and a lifecycle in the model.
- A **persistence entity** is a database-row mapper (a JPA `@Entity`); it is a storage shape.

Do not conclude that something is a domain entity because it has a table, an `id` column or a
file of its own. A surrogate key is a storage convenience, not domain identity. And do not
conclude it is not a value object because it is persisted. A relationship defined wholly by its
two ends and its kind is a **value object** however many rows or lines hold it; there is no
"relationship 7" in the domain, only "this depends on that".

## How to add each piece

| To add | Do this | The gate |
|---|---|---|
| A value object | A `record` in the slice's `domain` package, with guards in the compact constructor | `DOMAIN_IS_PURE` |
| An entity | A `final class`, package-private all-args constructor, public fluent builder, identity `equals` | Code review; `DOMAIN_IS_PURE` |
| An aggregate root | As an entity, plus the only public way to reach its children | Code review |
| A port | A framework-free interface in `domain`, naming only domain types | `PORTS_LIVE_IN_DOMAIN` |
| An adapter | A `@Service` in `infra.storage` implementing the port; map DSO to entity inside it | `PORTS_LIVE_IN_DOMAIN`, `STORAGE_RETURNS_DOMAIN` |
| A use case | A plain-Java class in `application`, contract-neutral | `ONION_DEPENDS_INWARD` |
| An endpoint | A controller in `infra.api` over the application ring | `ENTRYPOINT_OVER_APPLICATION`, `TRANSPORT_STAYS_IN_ADAPTER` |

The dependency rule behind the table is the dependency inversion principle: the domain declares
the port it needs, the edge implements it, and the framework stereotype (`@Service`) lives only
on the implementation. The domain never imports the edge.

## Cautionary examples

These are lessons from reading an older reference project (TraMBU, a 2019 time-tracking
service) and from the `modular-validators` library. The reference was useful because it got the
package geography right and the runtime dependencies wrong. Each item names what to avoid.

1. **The aggregate that saves itself.** In the reference, the aggregate held an injected
   repository and called `repository.save(this)` from its own methods, and its builder saved on
   `build()`. That is Active Record leaking through the port: the domain drives its own
   persistence and constructing an object hits storage. Here, `Pack` and `Tactic` hold no
   repository, and building one has no side effect. Saving is the storage ring's job.
2. **The anemic, mutable domain.** Public setters and a public no-argument constructor "for the
   mapper" turn entities into bags of fields. Use records for values and builders with guards
   for entities, so an invalid object cannot exist.
3. **Raw `UUID` identity.** An entity keyed by a bare `UUID` can be confused with any other. Use
   a typed identifier value object (`PackIdentifier`).
4. **Persistence annotations on the domain, or `@Data` on a persistence class.** Generated
   `equals` over all fields including a surrogate key is a known pitfall. DSOs are plain records.
5. **Configuration in a controller.** The reference declared its `ObjectMapper` bean inside a web
   controller. Wiring belongs in the composition root (`bootstrap`), not in an adapter.
6. **No application ring.** Controllers orchestrated the aggregate and repository directly.
   Keep the application ring as the contract-neutral use-case seam that every inbound adapter
   shares; do not collapse it into the web adapter.
7. **A kernel that accretes dependencies.** The reference's shared kernel quietly pulled in two
   third-party libraries. Keep `kernel.validation` to `java` and `jspecify` and gate it with
   `VALIDATION_LIBRARY_IS_PURE`.
8. **Decorative markers.** Its `@AggregateRoot` and `@ValueObject` annotations had default
   retention, so no test could read them. If DDD markers are used here they are
   `@Retention(RUNTIME)` and `@Target(TYPE)`, and a test enforces them.
9. **Honest names.** A package called `dto` that held persistence entities, and an aggregate whose
   Javadoc said "value object", show drift between stereotype and prose. Name things for what
   they are and keep the stereotype and the text aligned.

What to keep from the same sources: ports in the domain with implementations at the edge,
constructor injection, framework annotations only at the edge, layers as packages inside a slice,
a separate persistence shape bridged by a mapper, and builders for aggregates.
