---
affected_files: []
cycle_number: 2
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T20:32:33Z'
reviewer_agent: claude
wp_id: WP07
---

# WP07 review, cycle 2: changes requested (reviewer-renata)

Issues 1 and 2 from cycle 1 are fixed and hold up under adversarial probing. The explicit-null CHANGELOG entry, the `packs.feature` scenario and the two `LoadOutcomeTest` fixtures are correct. Both gates exit 0.

Issue 3's fix, the non-finite post-pass in `PackSchemas.validate`, has two new defects. One lets an exception escape the readers. The other refuses documents the schema accepts. Both fixes are small and stay inside `PackSchemas.validate` and its helpers.

## Issue A (blocking): `NullPointerException` escapes the readers

`valueAt` walks a list with `List.copyOf(items)`, and `List.copyOf` rejects null elements. When a non-finite number sits beside a null element of an enclosing list, at a pointer the schema accepts as a string, the readers throw:

| Reader | Input |
|---|---|
| fragment (`GraphDocumentReader`) | `nodes: [null, {id: x, kind: directives, title: .nan}]` |
| shard (`GraphDocumentReader`) | `nodes: [null, {urn: 'tactic:x', kind: tactic, label: .inf}]` plus `edges: []` |
| `PackDescriptorReader` | `pack_id: 01ABC` / `pack_version: 1.0.0` / `name: qa` / `extra: [null, .nan]` |

Each one throws `java.lang.NullPointerException` out of the reader. That breaks the "never throws on content" contract, the same class of defect as cycle-1 Issue 1.

**Fix:** have `collectNonFinite` record each pointer together with its value or token. That removes `valueAt` altogether. If you keep `valueAt`, walk the collection by iteration, not `List.copyOf`. Add a test for each of the three inputs above.

## Issue B (blocking): the synthetic `type` violation refuses values the schema accepts

The post-pass adds a `type` violation at every non-finite pointer the validator did not flag. The code comment explains why: "the schema declares a string there". That holds only when the schema does declare a string. `pack-descriptor` has `additionalProperties: true`, so an undeclared key takes any value. Measured:

| `pack.yaml` addition | Result |
|---|---|
| `extra: 1.5` | loads, `undeclaredKeys=[extra]` (correct) |
| `extra: .nan` | refused, `pack_unreadable` (500): `pack.yaml of qa: '/extra' breaks schema keyword 'type'` |
| `extra: {a: .inf}` | refused, the same way at `/extra/a` |

Core reads the pack descriptor with extra keys allowed, so it accepts all three. The 500 is therefore a wrong refusal and a new parity divergence. Before cycle 2, `extra: .nan` loaded with the undeclared-key diagnostic, so this is also a regression within the WP.

The same defect sits in WP08's FULL scope: any free-form or untyped location in the 14 schemas would be wrongly refused.

A minor symptom of the same cause: a shard with `extra: .nan` at the root gets a second, spurious finding, `'/extra' breaks schema keyword 'type'`, next to the correct `unknown key 'extra'`.

**Fix:** emit the synthetic `type` violation only where the schema refuses a number. One sound way:

1. Validate a second tree in which each non-finite value stands in as a non-integral number, for example `DecimalNode 0.5`.
2. Add the `type` fact at a pointer only if that second run reports a violation at the same pointer.

With that rule:
- a free-form location accepts the value;
- a string field still refuses it;
- the `impacts` exception and everything else stays as it is now.

Any other approach that never adds a violation the schema would not raise for some number is fine. Update the `toTree` and `validate` comments to match. Pin the change with `PackSchemasTest` or `PackDescriptorReaderTest` cases: `extra: .nan` loads with `undeclaredKeys=[extra]`, and `name: .nan` stays refused.

## Verified in this cycle (no action)

**Depth.** The pre-scan of the event stream is stack-safe.
- All of these fail with `the document is nested too deeply` in well under 100 ms, with no `StackOverflowError`:
  - flow lists, flow maps and block sequences 1M deep;
  - block sequences mixed with flow (500k each);
  - flow nesting 500k deep inside a mapping value;
  - block mappings 2 500 deep;
  - an anchor on a collection 1M deep;
  - an unclosed `[` ×1M.
- Alias chains are caught by the `problem()` backstop at 200 and give the fixed line through both readers: 50 anchors of 150 levels each, with each anchor's innermost item an alias of the one before (list and map variants), and a 198-deep list holding an alias.
- Recursive anchors give "contains itself".
- 200 nested collections are read and 201 are refused, the same at the pre-scan and at `problem()`.
- A parse error below the depth bound keeps the parser's own first line ("while parsing a flow sequence") and the normal `drg_schema_error` code.
- An unclosed document deeper than 200 reports "nested too deeply", which is acceptable.

**Pre-scan cost.** Over the 531 corpus YAML files, after warm-up, `Yaml.load` alone takes about 105 ms and `ParsedYaml.parse` about 160 ms, roughly 1.5x. Acceptable.

**Scalars.** With `Yaml.settings()` (snakeyaml-engine 3.2, JSON schema), no constructed value other than `byte[]` falls outside the allowed set:
- these tags are parse failures with "could not determine a constructor": `!!timestamp`, `!!omap`, `!!pairs`, `!!merge`, `!!value`, `!!yaml`, `!foo` on a scalar, map or sequence, and the `!!python/...` and `!!java...` tags;
- `!!set` gives a `LinkedHashSet` and is fine;
- `!!float 1e400` and `1e309` give `Infinity`, handled as non-finite;
- `!!int` with 23 digits gives a `BigInteger`;
- `2001-12-14`, `0x1F`, `1_000` and `.5` are strings;
- `!!binary`, including the verbatim `!<tag:yaml.org,2002:binary>` form and an anchored one, gives the NOT_PLAIN line.

**Non-finite values (3a, 3c, 3d).**
- A quoted `'NaN'` or `"Infinity"` in a string field is read as a string and not flagged. The post-pass keys on real `Double` values in the YAML tree.
- A quoted `impacts: 'NaN'` gives `'impacts' must be a number`.
- `impacts` of `.nan`, `1e400` and `-1e400` in a shard or a fragment reach the domain unflagged, as intended.
- `1e400` and `-1e400` in string fields are refused as `type`, and the value is reported as `Infinity` or `-Infinity`, which matches the domain's wording.

**3b.** `impacts` appears only in `graph-shard` and `org-fragment`.

**Explicit null (item 4).** The CHANGELOG entry follows the house format (issue, rule, "behavior change, no schema change", codes, scenario). `pack_unreadable` → 500 comes from the unchanged `RefusalHandler` (`notFound() ? 404 : 500`), so no new status is introduced.

**Red evidence.** `wp07-c2-red.txt` consists of assertion failures.

## Not blocking (notes for WP08 and WP10)

- **C2-N1 (WP08).** `PackSchemas.toTree` and `validate` recurse. On a 100k-deep Java tree they throw `StackOverflowError`. Every WP07 content path reaches them through `ParsedYaml`, which bounds depth at 200, so this is safe today. Every new WP08 caller must also go through `ParsedYaml.parse`, never `Yaml.load`.
- **C2-N2 (WP08, parity).** `charter.schema.json` declares `cli_timeout_seconds` as `type: number` (line 445). It now refuses `.nan` and `.inf`, which core's pydantic `float` accepts. This is the right call for a JSON-Schema contract, since JSON has no non-finite numbers, but it belongs in the divergence register.
- **C2-N3 (WP10, wording).** Some parse failures carry a raw Java exception message as their first line. For example, `k: !!seq` and `!!set [a]` give `java.lang.ClassCastException: class org.snakeyaml.engine.v2.nodes.ScalarNode cannot be cast to ... (in unnamed module of loader 'app')`, and `!!int abc` gives `java.lang.NumberFormatException: ...`. These lines contain no host information, but they are implementation detail. List them with cycle-1 N2.
- Cycle-1 notes N3 to N7 still stand.
