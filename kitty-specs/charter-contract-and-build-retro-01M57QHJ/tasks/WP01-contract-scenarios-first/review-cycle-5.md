---
affected_files: []
cycle_number: 5
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T17:33:13Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 5 (reviewer-renata): changes requested

Reviewed 391c7ac. The `prefixItems`, boolean-schema and `property-described` fixes hold. The new
exemption for constraint-only `anyOf`/`oneOf` members is sound only when the object holding the
union is closed, and the rule never checks that it is.

Every probe below is a property added to `PackSummary` in `fixtures/clean.yaml`, bundled with the
pinned vacuum, then linted with the ruleset at HEAD. Each bundled probe passes
`oasdiff validate --fail-on WARN`.

## Findings

1. **[MEDIUM] contracts/lint/ruleset.yaml:217-218 together with :255 (union branch) - a union with no
   object keywords of its own, whose members are constraint-only, accepts any open object, and the
   rule reports nothing.**
   A constraint-only member (`{required: [k]}`, `{minProperties: 1}`, `{not: {required: [k]}}`)
   admits every object that meets the constraint. It is safe only where the holding object is closed,
   because the holder's `additionalProperties: false` then still applies. But the union branch exempts
   a holder that has no object keywords of its own, and the `given` filters drop the constraint-only
   members as positions. Nothing judges either side.
   Probes, no violation for any of them:
   - `anyOf: [{required: [k]}, {type: 'null'}]`
   - `oneOf: [{required: [k]}, {required: [j]}]`: the exactly-one-of idiom with no closed holder
   - `anyOf: [{not: {required: [k]}}, {type: 'null'}]`
   - `oneOf: [{minProperties: 1}, {type: string}]`
   - `anyOf: [{required: [k], not: {properties: {k: {const: a}}}}, {type: 'null'}]`

   The same idiom on a closed holder (`P-exactly-one-of`) correctly passes.
   **Recommendation:** in the union branch, exempt a union only when none of its members is
   constraint-only. For example, add to that branch a `not: {properties: {anyOf: {contains: <the
   constraint-only shape>}, oneOf: {contains: ...}}}` with the same keyword lists as the `given`
   filter. A constraint-only member is then accepted only beside a holder that the closed-object branch
   accepts. Add a fixture `closed-response-objects.constraint-only-union.yaml` with the first probe.

2. **[LOW] contracts/lint/ruleset.yaml:217-218 - `minProperties: 0` is not seen as a constraint.**
   The filter tests keyword truthiness, so `0` reads as absent. A member `{minProperties: 0}` on a
   closed holder is judged as a position and fails. This fails loudly, so it is harmless, but it is
   inconsistent with the stated rule.
   **Recommendation:** test for the key's presence, not its value, if vacuum's JSONPath allows it.
   Otherwise note the limitation in the ruleset comment.

## Verified

- **These fire `closed-response-objects`, and only it:**
  - `items: true`;
  - an open object in `prefixItems`;
  - `additionalProperties: true` on the holding object;
  - a member with `required` plus `additionalProperties: true`;
  - a member with `minProperties` plus `patternProperties`;
  - a constraint member that nests a `oneOf` over an open object;
  - a `dependentSchemas` that hides an open object.
- **These pass:**
  - the exactly-one-of idiom on a closed object;
  - a closed tuple (`items: false`, closed `prefixItems`);
  - an `if` condition holding a bare `const`, which no longer fires `property-described`.
- **The real contract** passes all 12 rules.
- **Fixtures:** each of the 24 planted fixtures fails only its own rule, and the clean fixture passes.

## verifyAll

`verifyAll -PopenPacksDir=<open-packs@7f063ae> -PcontractBaseline=none --rerun-tasks --max-workers=2`
on JDK 25.0.4.1: BUILD SUCCESSFUL, 33 tasks executed. 564 tests, 0 skipped, 0 failures.
- `compatibilityFixtures`: 2 breaking pairs, 1 compatible pair.
- The compatibility check was skipped on request.
