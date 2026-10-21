---
title: Code style and conventions
description: The construction and style rules of the charter service Java code - how each type is modelled, how invariants fail fast, how outcomes are values, and how tests are named.
doc_status: active
updated: 2026-10-21T00:00:00Z
type: Guide
kind: Reference
related:
  - development/how-to-ddd
  - architecture/architectural-patterns-applied
  - adr/0006-onion-vertical-slices-archunit
  - glossary/ddd-patterns
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a type to the service, so it is built the way the rest of the code is built.
  - profile: core-maintainer
    guidance_text: Read this to see the house style the service code follows and which rules a build gate enforces.
---

# Code style and conventions

The rules for writing Java in `service/`. They were set by the pack repositories and validation
mission ([ADR-0006](/adr/0006-onion-vertical-slices-archunit/) is the layering they sit in) and
the first slice, `packs`, is the worked example. Each rule says what to do, why, and what the
build does about it. For the reasoning behind the domain vocabulary, read
[How to DDD here](/development/how-to-ddd/).

## Model a type by its nature

Pick the construction form from what the type *is*, not from habit.

| Nature | Examples | Form |
|---|---|---|
| Value object | `PackIdentifier`, `TacticStep`, `TacticReference`, `PackPaths` | `record` |
| Data structure, parameter or result | `PackFinding`, `PackSummary`, query records | `record` |
| Storage shape (DSO) | `TacticDso`, `PackDescriptorDso` | `record` |
| Transport shape (DTO) | generated contract types | generated, never hand-edited |
| Entity or aggregate root | `Pack`, `Tactic` | `final class`, public fluent builder, package-private all-args constructor |

- **Records** carry value equality for free and are immutable. Use a record whenever a thing is
  defined entirely by its components.
- **Entities** are `final` classes. The all-args constructor is package-private, so no caller can
  build one from a wide positional list; the public way in is `Tactic.builder()...build()`. An
  entity has identity equality: `equals` and `hashCode` use the identifier only.
- **No public wide constructor.** If a record needs many components, keep the canonical
  constructor and add a builder or a narrow, intention-named factory (`Foo.of(...)`,
  `Foo.empty()`) rather than asking callers to fill every slot.
- **Prefer chainable builders** to setters. A builder method returns the builder; `build()` runs
  the guards and returns an immutable value. There are no setters on domain types, and no
  public no-argument constructor kept "for the framework".
- **Typed identifiers, never raw `UUID` or `String`.** Identity is a value object
  (`PackIdentifier`, `TacticIdentifier`) so a pack id cannot be passed where a tactic id is
  expected.

## Nullability and formatting

- Every package is `@NullMarked` (jspecify) through `package-info.java`; non-null is the default.
  Mark a genuinely absent return with `@Nullable` at the return type, or return `Optional`.
  `Optional` is for return values, not fields or parameters.
- Format with palantir-java-format; `spotlessCheck` is part of `verifyAll`, so unformatted code
  fails the build. Run `./gradlew spotlessApply` to fix it.
- Every package has a `package-info.java` that names its role and its notable types.
- Javadoc is dense on the public API: state intent and the invariant, not the obvious.

## Fail fast with `kernel.validation`

Invariants are enforced where a value is created, through the pure `kernel.validation` library
(`java` and `jspecify` only; an ArchUnit rule keeps it that way). A type declares its rules once,
as a `ModularRuleset`, and uses one of two strategies.

| Strategy | Behavior | Use it for |
|---|---|---|
| `guard(value)` | Stops at the first breached constraint and throws `ConstraintBreachException` | Constructors and builders: an invalid entity must never exist |
| `check(value)` | Evaluates every constraint and returns an `Evaluation` with each `Reason` | Reporting: show a pack author everything that is wrong at once |

```java
private static final ModularRuleset<Tactic> RULES = ModularRuleset.<Tactic>emptyRules()
    .must(Constraints.of("A tactic needs at least one step.", t -> !t.steps.isEmpty()))
    .must(Constraints.of("A tactic needs a name that is not blank.", t -> !t.name.isBlank()));
```

- Messages are full declarative sentences that state the invariant.
- Never throw `RuntimeException`, `Exception` or `Throwable` directly. Throw a named exception
  (`ConstraintBreachException`, `CharterRefusal`).
- `Objects.requireNonNull(x, "x")` covers plain null checks; use constraints for meaning.
- The kernel stays small and dependency-free. A third-party library in `kernel.validation`
  fails the build.

## Outcomes are values; exceptions are for the edge

Model an outcome that a caller is expected to handle as a value: `Evaluation` (`PASS` or `FAIL`),
`Reason`, `Rationale`, `PackVerdict`, `PackFinding`. A broken file in a pack is a finding the
inspection query reports, not an exception that aborts the load. Convert a value to an exception
or a Problem response only at the edge (`infra.api`), where the contract needs it.

## Layering and annotations

- Domain and kernel types carry no Spring, Jakarta, Jackson or YAML annotation.
- Ports are framework-free interfaces in `packs.domain`; the adapter that implements one lives in
  `packs.infra.storage` and is the only place a stereotype (`@Service`) appears.
- Inject collaborators by constructor into `final` fields. No field injection.
- A domain object never holds a repository and never saves itself. Saving belongs to the
  application and storage rings.
- Marker annotations, if used, are `@Retention(RUNTIME)` and `@Target(TYPE)` so a test can
  enforce them. A marker that cannot be tested is decoration.

## Test conventions

- **Names are behavior sentences.** Use `subject_doesThing_givenCondition` and group related
  cases in `@Nested` classes with a `@DisplayName`. Follow the repository's BDD wording
  (`given`/`when`/`then`) for scenario tests.
- **AssertJ** for assertions; factor repeated assertions into a named `Condition`.
- **One non-vacuous test per concern** (DIRECTIVE_041). A test must be able to fail: it
  asserts an observable result, not that a call did not throw.
- **Every ArchUnit rule has a known-bad control.** Next to a rule, a test builds a small
  violating fixture and asserts the rule *fails* on it (see `HexagonalArchitectureTest`,
  `theOnionRulesFailOnAClassThatReachesOutward`). A rule that has never been seen to fail is not
  proven to guard anything.
- **Round-trip fidelity is asserted on the DSO**, with value equality, in the storage adapter's
  tests, never on an entity's identity-equals. See
  [How to DDD here](/development/how-to-ddd/#identity-and-fidelity-are-different-concerns).

## Where these rules came from

The construction and result style follows the authors' `modular-validators` library: named
static factories over bare constructors, a builder for wide records, outcomes as values with
exceptions only at the boundary, and an importable ArchUnit conventions contract. The
anti-patterns this page forbids (self-persisting aggregates, public setters, raw-UUID identity,
decorative markers) are lessons taken from reading the older TraMBU project; they are listed with
examples in [How to DDD here](/development/how-to-ddd/#cautionary-examples).
