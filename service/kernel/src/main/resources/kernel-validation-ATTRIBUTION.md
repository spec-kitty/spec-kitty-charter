# Attribution: `ai.speckitty.charter.kernel.validation`

This package is a lean reimplementation of the design of **modular-validators**
(<https://github.com/sddevelopment-be/modular-validators>, `core` module), licensed under the
**Apache License, Version 2.0** (<https://www.apache.org/licenses/LICENSE-2.0>).
The design is vendored, not the artifact: there is no runtime dependency on the upstream library.

## Took / changed / omitted

| Upstream | Disposition | Here |
|---|---|---|
| (none) | added | `Rule<T>`: returns a list of `Reason`s, composes with `on`, `forEach` and `when` (ADR-0075 decision 1) |
| `Constraint` | took, changed | a `Rule` of one predicate, a record with a severity, a code, and a location and detail that are functions of the subject; `equals`/`hashCode` are value-correct (see below) |
| `Constraints`, `Checks` | took, trimmed | `of` and `notBlank`; `StringUtils.isNotBlank` became `s != null && !s.isBlank()`. The slug rules `slug(max)` and `letterFirstSlug(max)` live beside their grammar, in `kernel.domain.IdentifierGrammar` |
| `ModularRuleset` | took, changed | immutable, and itself a `Rule`, so rulesets nest; `guard(T)` is fail-fast and throws `ConstraintBreachException`; `check(T)` collects every reason |
| `Evaluation`, `Reason`, `Rationale` | took, changed | `Reason` carries the rule, the result, a `Severity`, a `ReasonCode`, a `Location` and a detail; `Rationale` is an immutable record of `Reason`s (upstream: mutable list) with `violations()`, `warnings()`, `notices()` and `and()` |
| `Evidence` | replaced | `Location`: a file and a JSON pointer, typed and immutable |
| (none) | added | `Severity` (`VIOLATION`, `WARNING`, `NOTICE`); only a violation fails a check or a guard |
| (none) | added | `ReasonCode`: the typed code of a reason; a slice's enum implements it |
| `Constrained` | took, trimmed | value + ruleset pairing; only the combinators in use |
| `InvalidObjectException` (checked) | replaced | `ConstraintBreachException` (unchecked, carries the breached `Reason`) |

### Changed

- **Dropped `commons-lang3`**: `StringUtils`, `EqualsBuilder` and `HashCodeBuilder` are gone; only `java.*` and
  `org.jspecify` remain (gated by the validation-purity rule in `HexagonalArchitectureTest`).
- **Fixed `Constraint.equals`**: upstream compared on the description only, so two different rules with the same wording
  were equal. A `Constraint` is now a record: equal only when description and predicate (by reference) are equal.

- **Wired the dynamic descriptions**: upstream declared object-dependent descriptions but never used them. A
  `Constraint`'s location and detail are functions of the subject, applied only to a breach.
- **Replaced `Evidence`**: upstream's mutable string map, whose `merge` dropped the other side's keys, became the typed,
  immutable `Location`.

### Omitted

- The `dsl` module.

The changes were offered upstream as
[`sddevelopment-be/modular-validators#13`](https://github.com/sddevelopment-be/modular-validators/issues/13) (ADR-0075
decision 10).
