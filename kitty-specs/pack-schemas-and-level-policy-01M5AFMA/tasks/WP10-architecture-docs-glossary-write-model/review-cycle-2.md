---
affected_files: []
cycle_number: 2
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T20:58:33Z'
reviewer_agent: claude
wp_id: WP10
---

# WP10 review feedback (reviewer-renata), cycle 2

Cycle-1 items 1, 2, 3, 5 and 7 and the embed nit are fixed and match the code. I checked
`PackPolicy.unreadable` (pack.yaml gets no first line), `PackSchemas.toTree`/`validate` and the
domain `impacts` path (`GraphDocumentReader` reads `Number.doubleValue()`, and
`"'impacts' " + strength` prints `Infinity` for `.inf` and for `1e400`). The diagram shows
`provenance` on shard nodes only and `profile-id` for agent profiles, and the caption covers
`charter.schema.json`. The SVG is byte-identical to a pinned-jar re-render, and all four gates exit 0.

The fix commit adds two new claims that do not match their targets. Both are small.

## Required changes

**1. `docs/plans/open-questions.md`, Q28: the ADR-0017 anchor does not exist on the built site.**
The link is `/adr/0017-pack-schemas-imported-as-canonical-json-schema/#requirescore-stays-in-packyaml`.
Starlight/Astro make heading ids with github-slugger from the heading's text, and that keeps
underscores. The heading `` ## `requires_core` stays in `pack.yaml` `` gets the id
`requires_core-stays-in-packyaml` (checked with github-slugger 2.0.0). Use
`#requires_core-stays-in-packyaml`.
`check_docs.py links` passes only because its `slugify` strips every `_` as if it were emphasis
(`scripts/docs/check_docs.py:218-222`). That bug is outside WP10's files. Do not fix it here. Note
it as a follow-up if you like.

**2. `docs/guides/service-build.md`: the full-clone sentence names the wrong tag in two cases.**
New text: "In a full clone, the check needs the `contract-charter-v*` tag of the newest released
contract version that the CHANGELOG records, merged into HEAD".
`ReleaseGate.decide` (`contracts/src/main/java/.../compat/ReleaseGate.java:206-224`) picks a different baseline:
- It uses the newest recorded release **with the same major version as `info.version`**. With no such
  release (for example `2.0.0-SNAPSHOT` with only 1.x releases), there is no baseline, and the check
  passes without any tag.
- A **pending** release (the top heading released, its tag not yet merged, as on the release PR) is
  skipped. The check then needs the tag of the release before it, not the newest recorded one.
Reword it to match. For example: "In a full clone, the check needs the tag of its baseline, the newest
release the CHANGELOG records with the contract's major version (a release whose tag is not yet
merged is skipped), merged into HEAD; fetch the tags if it is missing." You can also just link the
`ReleaseGate` Javadoc or the lint README section you already link, and drop the paraphrase.

## Nits (fix them in the same pass)

- Q29: link `research.md#r11-implementation-time-operator-rulings`, which is GitHub's slug for
  "## R11. Implementation-time operator rulings" on `feat/pack-schemas-and-level-policy` (c95ab3c). The
  page is long, and the anchor resolves once the mission merges to main. The bare file link resolves too,
  but it is less precise.
- Q29: "the unknown-key and URN rulings are ([DIV-005](...), ...)" is not a sentence. Write
  "the unknown-key and URN rulings are DIV-005, DIV-006 and DIV-007 ([Divergence register](...))", or
  similar.

## Non-blocking

- `descriptors.md` gives `pack_level: null` as "must be a string". `pack_level` also has an `enum`, so
  networknt may report an `enum` violation too, which gives a second finding. I did not run this.
  If it is true, "refused (for example 'must be a string')" would be safer.

## Re-run after the fix

`python3 scripts/docs/check_docs.py all`, `PLANTUML_JAR=<pinned jar> python3 scripts/docs/check_docs.py plantuml`,
`npx --yes markdownlint-cli2@0.18.1`, `npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"`.
