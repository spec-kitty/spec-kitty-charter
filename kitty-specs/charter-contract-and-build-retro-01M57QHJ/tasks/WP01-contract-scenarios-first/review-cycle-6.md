---
affected_files: []
cycle_number: 6
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T19:05:03Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 6 (reviewer-renata): changes requested

Reviewed 223f475 (T033, the closure checker), on top of 391c7ac. The design is right. The classification is
positive and recursive, combination is judged per facet, and the vocabulary fails closed on everything I
tried: `unevaluatedProperties`, `unevaluatedItems`, `propertyNames`, `contains`, `minContains`,
`contentSchema`, `$defs`, `$dynamicRef`, `nullable` and YAML merge keys. Duplicate keys are refused at load.
Both review-5 findings are fixed. Two holes remain: one keyword in the vocabulary is given the wrong
semantics, and one response shape is never judged.

I ran every probe below against the checker compiled from 223f475. Unless a probe says otherwise, it is a
property `probe` of a closed holder, as in `ClosureCheckerTest.probe`. Each probe also passes
`vacuum bundle` and then `oasdiff validate --fail-on WARN` with the pinned tools, so the real pipeline lets it
through. Scratch copies are in
`/tmp/claude-0/-home-user/fb45a2c5-8ebb-5df5-9291-5519fc2684d3/scratchpad/probes/`.

## Findings

1. **[MEDIUM] SchemaClassifier.java:93-100 (`conditional`): `then`/`else` close their holder even when there
   is no `if`, and JSON Schema 2020-12 ignores them in that case.**
   Core §10.2.2.2 and §10.2.2.3 say that `then` and `else` have no effect when `if` is absent. The checker
   treats them as alternatives whenever either is present. Probe:
   ```yaml
   type: object
   then: {additionalProperties: false}
   else: {additionalProperties: false}
   ```
   The checker prints "All 1 response schemas are closed". `jsonschema.Draft202012Validator` accepts
   `{"x": 1}` against it, so the schema admits any object. The untyped variant
   (`then`/`else: {type: object, additionalProperties: false}`) passes the checker too. This is the soundness
   failure T033 rule 8 exists to prevent: a keyword the checker accepts, read with the wrong semantics.
   **Recommendation:** in `conditional`, contribute a part only when the schema has `if`. Add a must-report
   fixture `then-else-without-if.yaml` with the probe above. Update Javadoc rule 7 to say that `then`/`else`
   are read only beside an `if`.

2. **[MEDIUM] ResponseSchemas.java:35: a media type with no `schema` is never a position, so a response
   body with no schema at all passes.**
   Probe (the whole document):
   ```yaml
   openapi: 3.1.0
   info: {title: p, version: 1.0.0}
   paths:
     /probe:
       get:
         responses:
           '200':
             description: d
             content:
               application/json: {}
   ```
   The checker prints "All 0 response schemas are closed" and exits 0. This JSON body is unconstrained, the
   most open response there is. The fail-closed principle of the Javadoc (rule 2: whatever the checker
   cannot prove is open) should cover it.
   **Recommendation:** report a media-type entry of `content` that has no `schema`, naming its pointer, and
   add a must-report fixture. If the decision is to allow schemaless bodies, the alternative is to say so in
   Javadoc rule 1, but no such body exists in the contract today.

3. **[LOW] Conjunction.java:22-31 and Javadoc rule 5: the "contradiction" refinement misstates when it fires,
   and its message names the wrong part.**
   The refinement (b) is sound, because it only adds reports. But the Javadoc says the parts contradict "(no
   value satisfies both)", and that is false when the declared type is a list or the open part is a union.
   Probes:
   - `type: [object, string]` with `allOf: [{type: string}]`. The value `"abc"` satisfies both parts.
   - `type: string` with `anyOf: [{type: object}, {minLength: 1}]`. This is a valid schema that admits no
     object. The message reads "the open object of its own keywords (...) sits beside of its own keywords
     (...)": the union part has the holder's pointer, so `name()` labels both parts as the holder's own
     keywords.

   **Recommendation:** either restrict `declared` to a part whose type admits only objects (and, for a
   union, only when its own members say so), or keep the strictness and reword rule 5 to state it as a
   convention: "a declared object type beside a non-object part is reported, even when the schema is
   satisfiable". Give the union and the conditional parts their own pointer token (for example `anyOf`,
   `oneOf`, `then|else`) so that the message names them.

4. **[LOW] Vocabulary.java:56-77 and OwnShape.java:30-42: malformed `enum` and YAML-null type names are
   accepted, contrary to the "fail closed on malformed" part of rule 8.**
   - `enum: {a: 1}` (a mapping, not an array) is classified as closed. `Draft202012Validator.check_schema`
     rejects the schema, and a lenient validator that drops the keyword is left with `{}`.
   - `type: [object, null]` with an unquoted YAML `null` is accepted as the type name `"null"`, through
     `String.valueOf(null)`. The schema is invalid.

   `oasdiff validate` lets both through.
   **Recommendation:** report an `enum` that is not a non-empty list, and a type name that is not a
   `String`, as malformed. Add one `MalformedInput` case for each.

5. **[NOTE] Out of the checker's stated scope, so no change is required.** The checker does not assert
   `openapi: 3.1.*`, or that `jsonSchemaDialect` is absent or the OAS default, and its semantics depend on
   both. Today `oasdiff validate --fail-on WARN` catches a 3.0 document that uses `if` (`if-field-for-3-1-plus`),
   but it does not catch a foreign dialect. One line in Javadoc rule 1 saying that the checker assumes the
   OAS 3.1 default dialect would make this explicit. The `webhooks` responses and non-lowercase method keys
   (which are not operations under OAS) are not positions, which is consistent with rule 1.

6. **[NOTE, for WP02]** `:charter-api`'s `generateContract` declares all of `contracts/` as an input, so an
   edit to the checker or its fixtures re-runs code generation. WP02 owns build-logic and can narrow that
   input to `contracts/charter/**` and `contracts/_shared/**`.

## Rulings on the four refinements beyond T033's literal text

- **(a) `then`/`else` close their holder only when both do, and `dependentSchemas` never closes it:** sound
  and acceptable when `if` is present. A missing branch admits whatever the holder admits, and a dependent
  schema applies only when its member is present. The implementation has to gate on `if`, though: see
  finding 1.
- **(b) a non-object part never excuses an open part typed `object`:** sound, because it only adds reports,
  and acceptable as a convention against author mistakes such as `type: object` beside `allOf: [{type: string}]`.
  Its stated rationale is inaccurate: see finding 3.
- **(c) objects and arrays are separate facets:** sound. A conjunction takes the narrowest part per facet
  (the intersection) and a disjunction the widest member per facet (the union). The example
  `type: object` beside an untyped closing part is classified correctly. Acceptable.
- **(d) a union member can never reopen a closed holder:** sound. Under 2020-12, the holder's
  `additionalProperties: false` considers only its own `properties`/`patternProperties` and applies
  conjunctively. The members' nested positions are still judged (verified). Acceptable.

## Verified

- **Review 5.** Finding 1: all five constraint-only union probes are reported (`RoundFiveProbes`). Finding 2:
  `minProperties: 0` is judged by presence (`min-properties-zero-on-closed-holder.yaml` passes).
- **Reported correctly:**
  - `{type: [object, string]}`;
  - `{anyOf: [{type: object}, {type: 'null'}]}`;
  - empty `allOf`;
  - `not` that never closes;
  - `if` alone;
  - `additionalProperties: 'false'` (a string);
  - `items: [ ... ]` (draft-04 tuple);
  - `discriminator` on its own;
  - `dependentSchemas` on its own;
  - an alias cycle.
- **Passed correctly:**
  - a closed `oneOf` beside a scalar;
  - `allOf: [{additionalProperties: false}, {type: object}]`;
  - `if`/`then`/`else` all present and both branches closed;
  - `not` holding an open object;
  - an object `const`/`enum`.
- **Planted proof.** With `additionalProperties: true` in `contracts/charter/schemas/PackSummary.yaml`,
  `checkClosedResponseObjects` fails with exactly one violation:
  `/paths/~1packs/get/responses/200/content/application~1json/schema/properties/packs/items: additionalProperties: true admits any member`.
  After the revert the task passes and the working tree is clean.
- **Code and tests.**
  - The classes are small and cohesive, and the names say what each one does.
  - The Javadoc carries the semantics. Rules 5 and 6 have two over-long lines.
  - The tests assert the pointer and the reason. The positive controls are the must-pass set and the lint
    clean fixture. Nothing is tautological.
  - `checkClosedResponseObjects` is cacheable and configuration-cache safe (the cache entry was reused), and
    `check` depends on it.
  - The README's closure section points to the Javadoc.
- **The rest of WP01.**
  - The ruleset has 11 rules and no `closed-response-objects` fixtures are left. `lintFixtures` passes: each
    rule fails its planted fixtures and only them, and the clean fixture passes.
  - `compatibilityFixtures`: 2 breaking pairs fail and 1 compatible pair passes.

## verifyAll

`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew verifyAll -PopenPacksDir=/home/user/spec-kitty-open-packs -PcontractBaseline=none --max-workers=2 --rerun-tasks`
with open-packs at 7f063ae: BUILD SUCCESSFUL, 38 tasks executed.
- 620 tests, 0 failures, 0 errors, 0 skipped.
- The closure checker printed "All 7 response schemas are closed".
- The compatibility check was skipped on request.
