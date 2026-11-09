# Research: Bounded identifier patterns and Sonar cleanup

Research squad on main `5458e1fa`, 2026-11-09: an alignment and scope lens and a technical-remedy lens.

## Findings

- **R1: still real.** All three issues hold on main. No commit, ADR, divergence or changelog decides identifier
  length or the YAML resolver. The only length precedent is ADR-0044 (step description 160).
- **R2: site inventory.** Nine unbounded identifier sites plus Yaml.java INT and FLOAT. Preset and SkillIdentifier
  already check a 64 cap before matching. GitSourceRules HOST and GuideAllowlist are bounded by a `(?=.{1,253}$)`
  lookahead. GitSourceRules PATH is bounded by the 2048 URL cap (at most about 1 000 repetitions).
- **R3: the remedy.** Possessive quantifiers remove the recursion with the same language. A non-capturing group
  alone does not: it still overflows at 10 000 segments. Possessive timings at 1 000 000 segments are 10-27 ms. A
  hand-written scanner is faster but is new logic; it is rejected.
- **R4: overflow surface.** Nothing in main code catches `Error`. Over HTTP, registration answers a generic 500 with
  no refusal code; a background fetch or a startup load dies.
- **R5: the schema validator.** networknt with `Re2jRegularExpressionFactory`; RE2/J is linear and cannot overflow.
  Schema patterns must stay ECMA-compatible, so no possessive quantifiers in schemas.
- **R6: conventions.** 64 is the house id cap (5 pack schema uses, 13 charter schema uses, SkillIdentifier, Preset).
  The longest real pack id is 55 characters (open-packs `22c14b2`).
- **R7: tests.** No `*IdentifierTest` exists; identifiers are tested through their entity tests. Mutation testing does
  not mutate regex literals, and a cap adds one boundary mutant.
- **R8: capture groups.** GuardReference reads groups 1 and 2, and GuardImplementation reads groups 1-3; every other
  site calls only `matches()`.

## Decisions

The four operator decisions are recorded in `decisions/` and quoted in the spec's Intent Summary.
