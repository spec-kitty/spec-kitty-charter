# Quickstart — adding and round-tripping a doctrine artefact

How a pack author or a service consumer works with the three new kinds once this
mission lands. Mirrors the existing tactic flow.

## Author a directive in a pack

Add `my-rule.directive.yaml` to a pack:

```yaml
id: DIRECTIVE_200
schema_version: "1.0"
title: Keep adapters thin
intent: Business rules never live in an adapter.
enforcement: required          # required | lenient-adherence | advisory
scope: service layer           # optional, free text
procedures: [review-adapter-boundary]
integrity_rules: ["No domain type imports an infra package."]
validation_criteria: ["ArchUnit adapter-boundary rule is green."]
references:
  - { type: tactic, id: hexagonal-ports, when: on-review, reason: enforces the boundary }
```

`enforcement: lenient-adherence` requires a non-empty `explicit_allowances`.

## Load, validate, round-trip (service)

```java
// Load through the per-type repository port (implemented by the storage adapter)
Optional<Directive> d = directiveRepository.load(packId, new DirectiveIdentifier("DIRECTIVE_200"));

// Build in the domain with the fluent builder; build() validates the ModularRuleset
Directive built = Directive.builder()
        .id(new DirectiveIdentifier("DIRECTIVE_200"))
        .packId(packId)
        .schemaVersion("1.0")
        .title("Keep adapters thin")
        .intent("Business rules never live in an adapter.")
        .enforcement(Enforcement.REQUIRED)
        .procedure("review-adapter-boundary")
        .build();

directiveRepository.save(built);   // serializes back field-for-field
```

Round-trip fidelity is asserted at the DSO-record level: `readDirective(writeDirective(dso)).equals(dso)`.

## Use the family uniformly

```java
CharterArtifact a = built;        // Directive, Styleguide, Paradigm, Tactic all fit
a.kind();            // ArtifactKind.DIRECTIVE
a.label();           // "Keep adapters thin"  (title | name)
a.description();     // Optional["Business rules never live in an adapter."]  (empty for styleguide)
a.urn();             // directive:DIRECTIVE_200
a.edges();           // DrgEdges to the referenced tactic + the `procedures` (empty for styleguide)
```

## Styleguide / paradigm notes

- **Styleguide** `scope` is a required enum (`code|docs|architecture|testing|operations|glossary`);
  `principles` is non-empty; `patterns`/`anti_patterns` are structured objects;
  `tooling` and `structural_lint_config` round-trip with full type/nesting fidelity;
  a styleguide has no `description()` and no `edges()`.
- **Paradigm** is lean: `name` + `summary` + `directive_refs` (each becomes an edge
  to a `directive`) + typed `references`.

## Verify

```bash
# Targeted module checks (CI runs the full verifyAll):
./gradlew :packs:test --tests '*DirectiveEntityTest' --tests '*StyleguideEntityTest' --tests '*ParadigmEntityTest'
./gradlew :packs:test --tests '*RoundTrip*'
# Architecture gate (kernel purity, slice coverage):
./gradlew :deployable:test --tests '*HexagonalArchitectureTest'
```
