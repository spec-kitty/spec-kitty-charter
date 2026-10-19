---
affected_files: []
cycle_number: 3
mission_slug: charter-contract-and-build-retro-01M57QHJ
reproduction_command:
reviewed_at: '2026-10-18T17:16:08Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review 3 (reviewer-renata): changes requested

Reviewed 74ebbcc and b709c20. Review-2 finding 2 (unrecorded tags) is fixed. Review-2 finding 1
(anonymous objects) is fixed for the cases it named. But the new "allOf over a scalar" exemption is
not narrow: it lets an open object through when the allOf is nested.

## Findings

1. **[MEDIUM] contracts/lint/ruleset.yaml:210-228 (`closed-response-objects` at :183, third `anyOf` branch) -
   the scalar-allOf exemption hides an open object behind a nested allOf.**
   The exemption is negative: "not (has properties, or is type object, or has an allOf part that has
   properties or is type object)". The `contains` looks only one level down. The allOf parts
   themselves are not selected (`given` takes the response root, `..properties[*]` and `..items`).
   So a property whose only allOf part is itself an allOf over an open object counts as "not an
   object", and nothing judges the inner object.
   Probe: a property added to `PackSummary` in `fixtures/clean.yaml`, bundled, then linted with the
   ruleset at HEAD:
   ```yaml
   extra:
     description: Probe.
     x-derived: {rule: probe., inputs: [parameters/Action.yaml]}
     allOf:
       - allOf:
           - type: object
             properties:
               k: {type: string, description: K.}
   ```
   Result: no violation. (The earlier rule versions did not judge this position either, but this
   exemption is what now declares it "not an object".)
   This shape is realistic. It is exactly what the bundle produces when a property uses the `via`
   pattern (description + allOf[$ref] + x-derived) around a schema that is itself a composite, for
   example a schema extending `CharterRefusal`.
   **Recommendation:** make the exemption positive. Exempt a schema only when it has no `properties`,
   is not `type: object`, and every `allOf` part is a scalar, either `type` in
   {string, integer, number, boolean, null} or `const`/`enum`, applied recursively, or by forbidding a
   nested `allOf` in it. Add planted fixtures `closed-response-objects.nested-allof.yaml` (the probe
   above) and `closed-response-objects.scalar-first-allof.yaml`. The second already fails today; it
   pins that an object part after a scalar part is still judged.

2. **[LOW] contracts/lint/ruleset.yaml:183-190 and contracts/lint/README.md:75 - `anyOf`/`oneOf`
   members and untyped maps are not judged, though the README says "every object schema that stands
   on its own".**
   Probes:
   - `anyOf: [{type: object, properties: {k}}, {type: 'null'}]`, the usual 3.1 nullable object: no
     violation.
   - `{additionalProperties: {type: string}}` with no `type`: no violation.

   The contract uses neither shape today, and the earlier rules did not cover them either, so this is
   not a regression.
   **Recommendation:** either add `..anyOf[*]` and `..oneOf[*]` to `given`, and count
   `additionalProperties`/`patternProperties` as marking an object in the exemption, each with a
   fixture; or narrow the README to the positions the rule really judges.

## Verified fixed

- **Anonymous `meta` property:** fires `closed-response-objects` (my probe, plus the new
  `closed-response-objects.anonymous.yaml`).
- **Untitled open array item** (`type: array`, `items: {type: object, properties: {k}}`): fires.
- **allOf with a scalar part followed by an open object part:** fires.
- **Scalar `allOf` (the `via`/`action`/`missionType` shape):** passes. A closed anonymous object
  passes.
- **Unrecorded tags,** in a scratch full clone:

| Situation | Result |
|---|---|
| No release, no tag | "No released baseline", passes |
| Tag, no release | fails, lists the tag, "recorded: [none]" |
| Recorded 0.1.0, tagged | Compatible |
| Plus an unrecorded v9.9.9 | fails |
| Plus an unrecorded older v0.0.1 | fails |
| Recorded, no tag | fails |
| Tag on the pre-WP contract | breaking, 16 errors, fails |

## verifyAll

`verifyAll -PopenPacksDir=<open-packs@7f063ae> -PcontractBaseline=none --rerun-tasks --max-workers=2`
on JDK 25.0.4.1, in the shallow lane clone: BUILD SUCCESSFUL, 33 tasks executed. 564 tests, 0 skipped,
0 failures.
- `lintContract`: 12 rules pass.
- `lintFixtures`: the 15 planted fixtures each fail only their rule; clean passes.
- `compatibilityFixtures`: 2 breaking pairs, 1 compatible pair.
- The compatibility check was skipped on request.

Second run: configuration cache reused, 31 of 33 tasks up to date.
