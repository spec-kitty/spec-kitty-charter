---
affected_files: []
cycle_number: 4
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T17:24:58Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 4 (reviewer-renata): changes requested

Reviewed 344a42b. The positive classification holds against every shape asked about. I found two
silent holes in array handling and one false positive on a legitimate closed shape. All three are
small edits to the same rule.

Every probe below is a property added to `PackSummary` in `fixtures/clean.yaml`, bundled with the
pinned vacuum (`vacuum bundle`), then linted with the ruleset at HEAD. Each bundled probe passes
`oasdiff validate --fail-on WARN`, so each is a document the build would accept.

## Findings

1. **[MEDIUM] contracts/lint/ruleset.yaml:190-198 (`given`) - array items listed in `prefixItems` are
   never judged.**
   The array branch exempts `type: array` because "its items are a position of their own". But only
   `..items` is a position. OpenAPI 3.1 tuples put their item schemas in `prefixItems`, so an open
   object there is never judged.
   Probes, no violation for either:
   - `{type: array, prefixItems: [{type: object, properties: {k}}]}`
   - `{type: array, items: false, prefixItems: [<open object>]}`

   This contradicts README line 75 ("every array item").
   **Recommendation:** add `"$.paths[*][*].responses[*].content[*].schema..prefixItems[*]"` to
   `given`, and add a fixture `closed-response-objects.open-tuple-item.yaml`. `contains` needs no
   position: it only constrains items that `items`/`prefixItems` already describe.

2. **[MEDIUM] contracts/lint/ruleset.yaml:205-206 (boolean branch) - `items: true` passes.**
   The boolean branch is meant for an `additionalProperties` value. It exempts `true` at every
   position. `{type: array, items: true}` reports no violation, yet it accepts any item, open objects
   included. `true` is not a "provable non-object", which is what the rule's description promises.
   At `properties[*]` and at `anyOf` members, `true` happens to fire already, because other branches
   catch it there.
   **Recommendation:** exempt `false` everywhere, and `true` only as an `additionalProperties` value.
   For example, keep the boolean branch at `const: false` and let the closed-object branch judge
   `additionalProperties: true` through its owner, as it does today. Add a fixture
   `closed-response-objects.items-true.yaml`.

3. **[LOW] contracts/lint/ruleset.yaml:194-195 (`..anyOf[*]`, `..oneOf[*]`) - the rule rejects a
   legitimate closed shape.**
   `{type: object, additionalProperties: false, properties: {k, j}, oneOf: [{required: [k]},
   {required: [j]}]}` is the standard "exactly one of k or j" idiom on a closed object. It fails
   `closed-response-objects`, because each `{required: [...]}` member is judged as a schema that could
   be an object. This fails loudly, not silently, so it is the safe direction, but it would block a
   reasonable contract change.
   **Recommendation:** exempt a member that only constrains an object it is part of: no `type`,
   `properties`, `additionalProperties`, `patternProperties` or composition keywords of its own, only
   `required`/`minProperties`/`maxProperties`/`not` and similar. Exempt it only when its owner has
   `additionalProperties: false`, or simply when the member has none of those object-building
   keywords. Add a passing case to `clean.yaml`.

Note, not blocking: `property-described` fires on a property inside `if:` (`if: {properties: {k:
{const: a}}}`). A refinement there narrows a property already described. Consider exempting a bare
`const`/`enum` refinement in that rule. The contract does not use `if` today.

## The new rule holds against

All asked-for probes fire `closed-response-objects`, and only it:
- nested allOf over an open object;
- nullable `anyOf: [open object, null]`;
- untyped `additionalProperties` map;
- an open object nested in the second allOf part when the first part is closed;
- `oneOf` with an open object inside `items`;
- `type: [object, null]` open;
- an object with no `type` but with `properties`;
- a `not: {type: string}` wrapper;
- `if`/`then`/`else` with open objects;
- `allOf` whose first part is scalar-only and a later part has properties;
- `true` as a property or as an `anyOf` member.

A `$ref`-only position cannot reach the linter, because the bundle step inlines everything and fails
on any `$ref` left over.

The answer to "a union with no object keywords of its own is exempt because its members are judged"
is yes, at every depth, for anyOf/oneOf: `..anyOf[*]` and `..oneOf[*]` match at any depth, and a
deep `oneOf` inside `items` fired. It holds only as far as every member is a position, which
findings 1 and 2 break for arrays.

These legitimate shapes pass:
- `type: [object, null]` closed;
- an array of a closed object;
- `allOf: [{enum: [...]}]`;
- a closed object with `dependentRequired`;
- `oneOf: [string, null]`;
- the clean cases `origin`, `owner`, `action` and `missionType`;
- the real contract (all 12 rules pass).

## verifyAll

`verifyAll -PopenPacksDir=<open-packs@7f063ae> -PcontractBaseline=none --rerun-tasks --max-workers=2`
on JDK 25.0.4.1: BUILD SUCCESSFUL, 33 tasks executed. 564 tests, 0 skipped, 0 failures.
- `lintFixtures`: each of the 21 planted fixtures fails only its rule (9 for
  `closed-response-objects`); clean passes.
- `compatibilityFixtures`: 2 breaking pairs, 1 compatible pair.
- The compatibility check was skipped on request.
