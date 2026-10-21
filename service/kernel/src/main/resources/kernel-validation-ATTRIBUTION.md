# Attribution: `ai.speckitty.charter.kernel.validation`

This package is a lean reimplementation of the design of **modular-validators**
(<https://github.com/sddevelopment-be/modular-validators>, `core` module), licensed under the
**Apache License, Version 2.0** (<https://www.apache.org/licenses/LICENSE-2.0>).
The design is vendored, not the artifact: there is no runtime dependency on the upstream library.

## Took / changed / omitted

| Upstream | Disposition | Here |
|---|---|---|
| `Constraint` | took, changed | `Constraint` record (description + predicate); `equals`/`hashCode` are value-correct (see below) |
| `Constraints`, `Checks` | took, trimmed | static factories and field predicates; `StringUtils.isNotBlank` became `s != null && !s.isBlank()` |
| `ModularRuleset` | took, changed | immutable; `guard(T)` is fail-fast and throws `ConstraintBreachException`; `check(T)` collects every breach |
| `Evaluation`, `Reason`, `Rationale` | took, changed | `Rationale` is an immutable record of `Reason`s (upstream: mutable list) |
| `Constrained` | took, trimmed | value + ruleset pairing; only the combinators in use |
| `InvalidObjectException` (checked) | replaced | `ConstraintBreachException` (unchecked, names the breached constraint) |

### Changed

- **Dropped `commons-lang3`**: `StringUtils`, `EqualsBuilder` and `HashCodeBuilder` are gone; only `java.*` and
  `org.jspecify` remain (gated by the validation-purity rule in `HexagonalArchitectureTest`).
- **Fixed `Constraint.equals`**: upstream compared on the description only, so two different rules with the same wording
  were equal. A `Constraint` is now a record: equal only when description and predicate (by reference) are equal.

### Omitted

- `Evidence` (mutable, buggy upstream) and everything that carried it.
- The dynamic, object-dependent descriptions (never wired through upstream).
- The `dsl` module.
