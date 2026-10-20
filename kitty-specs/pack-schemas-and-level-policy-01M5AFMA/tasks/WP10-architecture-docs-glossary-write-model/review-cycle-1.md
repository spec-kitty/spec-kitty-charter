---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T20:52:02Z'
reviewer_agent: claude
wp_id: WP10
---

# WP10 review feedback (reviewer-renata), cycle 1

The gates are green, the SVG matches the pinned-jar render byte for byte, the scope is clean and the
glossary rows are good. The domain.md wording on the `mission_steps` reversal matches core
(`org_pack_loader.py` `_ORG_DRG_KIND_ALIASES`). The changes below are needed because some claims
do not match the code in this lane. Make all of them in one pass.

## Required changes

**1. `docs/architecture/descriptors.md`: the explicit-`null` claim is wrong for four fields.**
The paragraph reads: "A descriptor field that is present but `null` is "must be a string", like any other wrong type".
`pack-descriptor.schema.json` declares `parent_pack`, `requires_core`, `active_charter` and `pack_drg` as
`anyOf [string, null]`, so an explicit `null` there is accepted. `PackDescriptorReaderTest:76` reads
`parent_pack: null` and `requires_core: null` without error, and the same table says `parent_pack` is "or `null` for a root pack".
An explicit `null` is refused only for `pack_id`, `pack_version`, `name` and `pack_level`. Name those four.
(The contract CHANGELOG's "requires a string wherever the key is present" is also too broad, but it is not WP10's file. Do not copy it.)

**2. `descriptors.md`, "Parse failures and the first line of a finding": `pack.yaml` carries no first line.**
`PackPolicy.unreadable` (`charter-domain/.../PackPolicy.java:611-616`) writes `pack.yaml of <name> is not valid YAML`
with no parser line. Only shard and fragment findings append `: <first line>`. `ParsedYamlTest`'s
`aPackDescriptorNestedTooDeeplyIsPackUnreadableWithTheFixedFirstLine` and `aBinaryValueInPackYamlIsAParseFailure` pin this.
Say that the fixed first lines show in the detail of a graph shard or fragment finding, and that a `pack.yaml` finding says only that the file is not valid YAML.

**3. `descriptors.md`: non-finite numbers are not reported "as written in the file".**
`PackSchemas.toTree`/`validate` (`adapter-out-yaml/.../PackSchemas.java:181-195, 204-207`) report the value as
`Double.toString` writes it: `NaN`, `Infinity` or `-Infinity`. So `.inf` and `1e400` are both reported as `Infinity`
(`PackSchemasTest:268`, `GraphDocumentReaderTest:173`, where the domain text is `'impacts' Infinity is outside [-1, 1]`).
State that.

**4. `docs/guides/service-build.md`: the shallow-clone sentence has a qualifier the code does not apply.**
The new sentence reads: "This includes `verifyAll` in a shallow clone that has no `contract-charter-v*` tag".
`contracts/build.gradle.kts:570-574` fails any shallow clone before it looks at tags. Say that `verifyAll` in any
shallow clone needs `-PcontractBaseline=none`.

**5. `docs/architecture/diagrams/pack-write-model.puml` (then re-render the `.svg` with the pinned jar).**
- `ArtifactFile` shows `+ id : string` as common to all kinds. `agent-profile.schema.json` requires `profile-id`, not `id`.
  Show the identity as `id` (`profile-id` for `*.agent.yaml`), or drop the field and keep "one schema per kind".
- `ShardNode` leaves out the optional `provenance` that `graph-shard.schema.json` `DRGNode` declares. Add `- provenance : string`.
  If you omit it on purpose, as ADR-0017 does for edge provenance, say so in the source comment and in the caption.
- `descriptor-schemas.md` says "(14 schemas)" and that the diagram is "drawn from the schemas", but `charter.schema.json` is not drawn.
  Add one sentence to the caption: it validates the project's `charter.yaml`, which is not a pack file (`file-kinds.yaml`), so the diagram leaves it out.

**6. `docs/plans/open-questions.md`, Q28 and Q29: the citations do not match their sources.**
- Q28 cites "ADR-0017, Amendment 2026-10-19". The Amendment does not mention `requires_core`.
  Link ADR-0017's section "`requires_core` stays in `pack.yaml`" (optional; grammar checked in the domain).
  For the `pack_unreadable` answer, cite the contract CHANGELOG scenario or spec R3.
- Q29 attributes "a graph file that is not valid YAML is refused the same way" to DIV-005/6/7. That ruling is R17
  (`research.md` R11), and it aligns with core, so it has no divergence entry. Cite R17 for that clause.

**7. `docs/_meta/terminology.yaml`: three of the four new `allow` entries are dead.**
`check_terms` runs `prose()`, which removes inline code before matching (`scripts/docs/check_docs.py:211-215, 446`).
Every `mission_steps` mention in `descriptors.md`, `descriptor-schemas.md` and `domain.md` is in backticks, so none of
those pages can match. Their allows only hide a future bare mention. Remove those three entries.
Keep the `docs/conformance/divergence-register.md` entry, because that page names the alias in bare prose (DIV-011).

## Non-blocking observations (no change required in WP10)

- DIV-011 in the register (WP09) says "Core: Accepts the aliases mission_steps and mission_type". In core, `mission_steps`
  is the canonical name and `mission_step_contracts` is the back-compat alias. domain.md has this right. Spec R14 has the
  same imprecision. Consider a follow-up to align the register's wording and put its identifiers in code formatting.
- `descriptors.md` links the write model and then embeds the same SVG on the next line. One of the two is enough.

## Re-run after the fix

`python3 scripts/docs/check_docs.py all`, `PLANTUML_JAR=<pinned jar> python3 scripts/docs/check_docs.py plantuml`,
`npx --yes markdownlint-cli2@0.18.1`, `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`.
