---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T20:20:33Z'
reviewer_agent: claude
wp_id: WP07
---

# WP07 review, cycle 1: changes requested (reviewer-renata)

Most of the WP is in good shape. D1-D8 are correct and minimal. The CHANGELOG follows the house format. The red evidence is assertion-based. `LoadOutcomeTest` has an independent oracle (editing `shard-impacts-nan/expected.json` makes it fail). Vocabulary equality and keyword coverage are green. ArchUnit A-D, `WireTokenParityTest`, `verifyAll` and the gate are all green.

Three defects block approval. All three are in code this WP introduced, and each fix is small. I found them with a throwaway probe (now deleted) that loads packs through `FileSystemCharterPackRepository.load()` and calls `PackSchemas.validate` directly.

## Issue 1 (blocking): deep nesting throws `StackOverflowError` out of the adapter

T031 says the readers **never** throw on content, and `ParsedYaml`'s Javadoc says the same. In fact, a document nested deeper than the JVM stack escapes as `java.lang.StackOverflowError`, and `ParsedYaml.parse` only catches `RuntimeException`. Reproduced cases:

- shard `nodes: ` + `[`×10 000 + `]`×10 000;
- the same nesting with `{a: ` maps;
- block-sequence nesting 5 000 deep;
- the same nesting in `drg/fragment.yaml`;
- the same nesting under an extra key in `pack.yaml`.

The overflow happens inside snakeyaml-engine's recursive `Composer` (`composeSequenceNode` → `composeNode` …), before `problem()` and its `MAX_DEPTH = 200` ever run. So the 200-level guard only covers depths between 201 and roughly 1 000. snakeyaml-engine 3.2's `LoadSettingsBuilder` has no nesting limit.

**Fix:** bound the depth before composing.
- One way: iterate the events of `new Parse(Yaml.settings()).parseString(text)`. The parser is a state machine with an explicit stack, not recursive. Count `CollectionStartEvent`/`CollectionEndEvent`, and return the fixed failure `the document is nested too deeply` once the depth passes `MAX_DEPTH`. Then load as today. (Alternatively compose from those events.)
- Do **not** catch `StackOverflowError`.
- Keep `MAX_DEPTH = 200`; it is a sensible bound (real packs nest fewer than 10 levels, and networknt and Jackson recurse safely at 200).
- Add tests for 10 000-deep flow nesting in a shard, a fragment and `pack.yaml`. Each must give the refusal with the fixed first line: `drg_schema_error` for a shard or the fragment, `pack_unreadable` for `pack.yaml`. A `LoadOutcomeTest` fixture or a reader test both work.

## Issue 2 (blocking): `!!binary` escapes as `JsonNodeException`; a binary value is reported as `''`

- `drg/fragment.yaml` with `nodes: [{id: !!binary aGVsbG8=, kind: directives}]` throws `tools.jackson.databind.exc.JsonNodeException` ("'POJONode' method asString() cannot coerce value {POJO of type byte[]}"). It is raised from networknt's `MinLengthValidator` inside `PackSchemas.validate`, so it escapes the adapter. The same applies to any `minLength` or `pattern` property, in every schema.
- A shard edge with `relation: !!binary aGVsbG8=` is refused as `unknown relation ''`. That text is wrong: the value is not an empty string.

**Fix:** in `ParsedYaml.problem`, treat any loaded scalar that is not `String`, `Integer`, `Long`, `BigInteger`, `Double`, `Boolean` or null as a parse failure with a fixed first line, as you already do for non-string keys. Today the only such scalar is the `byte[]` from `!!binary`; a `!!set` already becomes a JSON array and is fine. Add a test for `!!binary` in a fragment id, in a shard relation and in `pack.yaml`.

## Issue 3 (blocking): the `.nan`/`.inf` → `0.0` substitution in `PackSchemas.toTree` makes schema rules pass wrongly outside `impacts`

On the load path the substitution is sound. `impacts` is the only numeric field in graph-shard, org-fragment and pack-descriptor, and it has no range. The domain catches every non-finite value with the pinned text: `.nan`, `.inf`, `-.inf`, `1e400` and a 39-digit integer all give `'impacts' … is outside [-1, 1]`.

But `toTree` is shared by all 14 schemas, and WP08's FULL scope inherits it. networknt counts `0.0` as an integer, and `0.0` satisfies most ranges. Measured with `PackSchemas.bundled().validate(...)`:

| Document | Result | Correct result |
|---|---|---|
| agent-profile `routing-priority: .inf` | no violation | refused (integer, 0..100) |
| agent-profile `routing-priority: .nan` | no violation | refused |
| org-charter `schema_version: .nan` or `.inf` | no violation | refused (`1.5` is correctly refused as `type`) |
| agent-profile `max-concurrent-tasks: .inf`, `-.inf`, `.nan` | `exclusiveMinimum` violation with `value = "0.0"` | a fact that names a value from the file, not `0.0` |

Core (pydantic `int`) refuses all of these, so this would also be an unrecorded divergence in WP08's core parity.

**Fix:**
- A non-finite number must never satisfy `type: integer`, `minimum`, `maximum` or `exclusiveMinimum`.
- A violation must never report a substituted value. Report the YAML's own value, for example `NaN` or `Infinity` as `Double.toString` prints it, the same way the domain text does.
- The approach is your choice. For example, substitute a non-integral stand-in only where a number is all the schema requires, and emit the type fact yourself elsewhere. Or validate a tree where a non-finite value stands in as a non-integral number, then rewrite `value` on any violation at that pointer.
- Correct the `toTree` Javadoc, which currently claims that a range "is not the schema's business" for every schema.
- Pin it with `PackSchemasTest` cases on agent-profile `routing-priority` and `max-concurrent-tasks`, and on org-charter `schema_version`.

## Not blocking (for the record and for WP08/WP10; no change required in this cycle)

- **N1. Unpinned behaviour changes.** These are consistent with the schema as the authority, with R4 and with core, but no CHANGELOG entry or commit body mentions them:
  - `pack_level: null` used to be the `pack_level_missing` diagnostic (200). It is now refused: `pack.yaml of <n>: pack_level must be a string` (500).
  - An explicit null `pack_id`, `pack_version` or `name` now says `must be a string` instead of `has no <key>`.

  No scenario pins either. The orchestrator should decide whether the `pack_level: null` 200→500 change deserves a CHANGELOG line, which would go beyond D1-D8, or a divergence-register or WP10 docs note.
- **N2. Parse failures with adapter-authored wording.** `ParsedYaml`'s fixed first lines ("a mapping key is not a string", "the document is nested too deeply", "the document contains itself", and the one you add for Issue 2) are adapter-authored English inside a finding text. They are acceptable as parse-failure reasons, but WP10 should list them next to the pinned texts.
- **N3. The keyword list is written three times.** `PackPolicy.VOCABULARY_KEYWORDS` restates the cases of the `detail` switch, and `SchemaKeywordCoverageTest` restates them a third time. A row added to one and not the others drifts silently. Consider a domain test that ties `handles()` to the switch.
- **N4. Unbounded detail length.** `requires_core_invalid` echoes the whole value: a 100 000-character `requires_core` yields a 100 000-character detail. `Pep440` itself is stack-safe; I probed 100k `(` and 50k clauses.
- **N5. Undecodable files are I/O refusals.** Non-UTF-8 or UTF-16 shard bytes are refused as `pack_unreadable … cannot be read`, from the I/O path. Core would report a parse error, which is `drg_schema_error` for a shard under R17. This is a parity note for WP08.
- **N6. `~` is not null here (pre-existing, `Yaml.settings()`).** snakeyaml-engine's default JSON schema resolves `~` as the string `"~"`, so `parent_pack: ~` reads as the parent `"~"`. Core's PyYAML (YAML 1.1) reads `None`. This is a parity note for WP08.
- **N7. A precedence edge case (WP06's).** `{id: '', kind: [x]}` gets the fixed fallback `'/nodes/0/id' breaks schema keyword 'minLength'`.
- **D8 judgement.** `apart from "detail"` is the right call for a language-neutral contract: core's parser words its errors differently. The scenario's description states the stable prefix.

Verified and fine:
- alias bomb (bounded by `maxAliasesForCollections = 50`), recursive anchors, recursive keys;
- multi-document streams, empty and comment-only files, a BOM;
- unknown, java and `omap` tags, bad `!!int`/`!!float`, `!!set`, merge keys, a null key, a boolean key;
- tabs, control characters, a NUL, a lone surrogate, a 39-digit integer.

None of these escapes, and each gets a sensible code.
