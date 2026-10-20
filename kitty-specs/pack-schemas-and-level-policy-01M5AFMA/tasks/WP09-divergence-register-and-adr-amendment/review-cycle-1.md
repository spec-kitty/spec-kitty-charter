---
affected_files: []
cycle_number: 1
mission_slug: pack-schemas-and-level-policy-01M5AFMA
reproduction_command:
reviewed_at: '2026-10-19T18:43:52Z'
reviewer_agent: claude
wp_id: WP09
---

# WP09 review feedback (cycle 1) — reviewer-renata

Gates are green (check_docs all, markdownlint-cli2 0.18.1, cspell 10.4.0: all exit 0). The YAML
holds DIV-001..019 exactly, with the documented schema, status `open` and since 2026-10-19. The
register intro, Consumers note, plan anchor and VENDORED cross-link are all correct. The changes
below are about factual accuracy against core at `9554e7b2`
(`/home/user/spec-kitty/spec-kitty/src/charter/`). #72 and #115 read this register, so its
core claims and evidence have to be right.

## Blocking

**1. DIV-018 miscounts core's Relation enum.** It says "The schema's Relation enum is core's 16
values plus impacts". Core's `Relation` has **15** members (`offering/drg/models.py:83-148`, and its
own registry comment says "all 15 Relation members"). The schema has 16 values: core's 15 plus
`impacts`. VENDORED C7 already says this correctly. Fix both the YAML and the page.

**2. Evidence paths do not resolve under the rule the YAML header states.** The header says core
paths are "relative to core's src/charter/". Several are not:
- `drg/merge.py` and `drg/models.py` (DIV-007, 009, 012, 015, 018, 019) are really
  `offering/drg/merge.py` and `offering/drg/models.py`.
- `pack_validator.py:727-767` (DIV-003) is really `offering/packs/pack_validator.py`.
- `org_pack_loader.py:519-566` (DIV-003) and `org_pack_loader.py:648-655` (DIV-008) are really
  `offering/drg/org_pack_loader.py`.
- `offering/packs/org_pack_loader.py:118-139` (DIV-011) does not exist. It is
  `offering/drg/org_pack_loader.py`.

Make every core path resolve from `src/charter/`. The alternative is to change the header rule, but
it must hold for every entry.

**3. DIV-012 cites the wrong line.** `drg/models.py:19` is `_URN_RE`, which accepts any
`[a-z_]+` kind. The `glossary` and `glossary_scope` kinds are `NodeKind` members at
`offering/drg/models.py:61-66` (`class NodeKind` at :43). Cite those lines.

**4. Evidence must be file:line, as the WP asks.** Three entries lack lines:
- DIV-010 cites "the agent profile and org charter models, before validation". Cite:
  - `offering/agent_profiles/profile.py:294-327`, for scalar `role` to `roles`;
  - `offering/agent_profiles/profile.py:54-55` and `:93`, for role lowercasing in `Role`;
  - `activation/org_charter.py:217-230`, for the `schema_version` "1" coercion;
  - pydantic lax mode (no strict config), for string-to-integer.

  The behaviour claims themselves are correct.
- DIV-005: cite `offering/packs/pack_descriptor.py` `extra="forbid"` (model_config, ~:90). For the
  second report, cite `offering/drg/org_pack_loader.py:547-560`.
- DIV-014: cite `offering/packs/pack_validator.py:291-295` (`_scan_files`: rglob only for
  styleguides and assets).

**5. DIV-001 and DIV-002 must link the CHANGELOG lines.** The WP table says "link the line". Today
they are code spans (`contracts/charter/CHANGELOG.md:146-150` and `:151-154`), not links. On the
page, make them links in the house absolute `main` style
(`https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/CHANGELOG.md#L146-L150`),
or link the `### Provisional` heading. Note that WP07 may add lines above `### Provisional`, which
would shift the line numbers. Prefer the heading anchor plus "the second item", or re-check the
numbers at merge.

**6. ADR-0017 decision 2 needs an inline annotation, per the ADR-0005 pattern.** ADR-0005 notes the
amendment in Status and also annotates the amended decision where it stands
(`*(Amended 2026-10-19: ...)*`). ADR-0017 has the Status pointer and the Amendment section. Decision
2's bullets still read "a built-in pack declares `pack_version`" and "drop the two lenient
conversions", with no marker, so a reader of decision 2 is misled. Add a short italic
`*(Amended 2026-10-19: see [Amendment](#amendment-2026-10-19-operator-rulings); R4/R9.)*` to
those bullets. Also reword "each point it changes is annotated here" so it matches.

## Non-blocking

- **DIV-005 wording.** "Reported twice" holds only when the pack has `drg/fragment.yaml`. I checked
  this with `spec-kitty charter pack validate --json` on a probe pack. With a fragment, the result is
  two `schema_invalid` errors, `artifact_type` `drg` and `pack`. Without a fragment, it is one
  `pack` error. Qualify the sentence: "twice when the pack has an org fragment".
- **DIV-014 count.** "87 nested files" comes from research.md. At core `9554e7b2` (and open-packs
  `7f063ae`) I count 89 `*.tactic.yaml` files at depth ≥ 2 under `packs/built-in/tactics/`.
  Re-check the number or drop it.
- **`docs/_meta/sections.yaml`.** The `conformance` entry (order 35) sits after `plans` (order 40).
  Every other entry is in ascending order in the file, so move it between `adr` and `plans`.
- **`docs/README.md`.** The Conformance bullet is outside `owned_files`, but `check_docs orphans`
  requires it. It is a justified minimal edit; mention it in the move-task note for WP10's owner.
- **ADR-0017 `updated:`.** It already read 2026-10-19, so there was nothing to bump. Fine.
- **Link style.** Absolute GitHub `main` URLs for `contracts/` match the house style
  (docs/api/service-contract.md, docs/plans/open-questions.md). Fine.
