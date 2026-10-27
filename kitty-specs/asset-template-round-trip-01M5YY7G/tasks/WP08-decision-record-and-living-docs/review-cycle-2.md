---
affected_files: []
cycle_number: 2
mission_slug: asset-template-round-trip-01M5YY7G
reproduction_command:
reviewed_at: '2026-10-27T19:28:23Z'
reviewer_agent: claude
wp_id: WP08
---

# WP08 review feedback (reviewer: claude, reviewer-renata lens with doctrine-daphne on authority and duplication)

Verdict: **changes requested**. ADR-0040's content matches the code. The edits to the Accepted
ADR-0036 break the immutability rule in `docs/adr/README.md`, and the lane is stale against
`origin/main` (b0bf89b accepted ADR-0039 and changed the index).

## Blocking

### B1. ADR-0036's decision text was rewritten and rows were removed. It is Accepted.

`docs/adr/README.md` (intro): "The *decision* in an accepted record is immutable ... the original
decision text stays, and each point it changes is annotated where it stands." ADR-0036 is
**Accepted** on main. Ruling R11 (DM-01M5YYAKM5S4TD42P0V115YFW0, "Edit ADR-0036 in place
(Proposed)") assumed the record was still Proposed, and it is not. The WP prompt's "still Proposed;
R11" repeats that wrong premise. An edit in place of an Accepted record has to take the amendment
form.

What the diff does:

- `docs/adr/0036-...md:139-145`: the original "**Other files.** *Reading:* a Markdown file compares
  its YAML frontmatter ... compares by bytes, because no other meaning is defined for it." is
  **deleted** and replaced with new text.
- `docs/adr/0036-...md` per-kind table: the `**/*.config.yaml`, `*.md (...)` and "Any other file
  (...)" rows are **deleted**, and the `*.asset.yaml` row and the table intro are reworded.
- `docs/adr/0036-...md:47-62`: the "Revision 2026-10-27" note says the change is "**Proposed** with
  ADR-0040 ... the rest of this record stays Accepted". This puts a Proposed decision inside an
  Accepted record, which no house form allows.

Required fix:

1. Restore ADR-0036's original Decision 2 "Other files" paragraph, the table intro, the
   `*.asset.yaml` row, and the `**/*.config.yaml`, `*.md` and "Any other file" rows **verbatim**
   (take them from `origin/main`).
2. Replace the "Revision 2026-10-27" block with a dated **amendment** under Status, in the shape of
   the "Extended in part by ADR-0039" note that main already has there, for example "### Amendment
   2026-10-27 (#193)". It cites the operator rulings R8 (DM-01M5YY9WFDRVZT6GZS8X59E2AZ, content
   form declared by the rule and never by extension or mime), R9 (DM-01M5YYA2CT1YSH7N6KVEPQ3XQC,
   templates are bytes), R1 (DM-01M5YY8HQKCGHH77B7VY0GHYC4, the config file is an asset payload),
   R4 (DM-01M5YY93YD7JZJZSV4W9H5EBCQ, the README exclusion) and R11 (DM-01M5YYAKM5S4TD42P0V115YFW0),
   plus ADR-0040, and says the decision is superseded in part by ADR-0040.
3. Annotate each affected point **where it stands**, with a short italic "*(Superseded in part by
   ADR-0040, 2026-10-27: ...)*" after the original text. Do this for the Other-files paragraph,
   each affected table row, and Decision 6. The new rows (`*/assets/**/*`, `*/templates/**/*`,
   `*/assets/**/README.md`, the declared-form row) belong in ADR-0040. They may also be appended
   to ADR-0036's table, but only clearly marked as added by the amendment, and they must not
   replace the original rows.
4. ADR-0040 Status (`docs/adr/0040-...md:31-35`): replace "It revises ADR-0036's ... in place
   (ruling R11)" with "**supersedes in part** ADR-0036" and name the exact points (Decision 2
   "Other files"; the per-kind rows `**/*.asset.yaml`, `**/*.config.yaml`, `*.md`, "any other
   file"). Then ADR-0039's precedent and ADR-0040's own text agree.
5. Index row for 0036 (`docs/adr/README.md`): use the house wording, for example "Accepted; extended
   in part by ADR-0039 (2026-10-27); superseded in part by ADR-0040 (2026-10-27)". Keep main's
   existing "extended in part by ADR-0039" (see B2). Drop "revised in part ... proposed". If ADR-0040
   is still Proposed when this lands, say so as the house does for a Proposed superseder, or have the
   operator accept ADR-0040 first.
6. Changelog `docs/changelog/2026-10-27-asset-and-template-round-trip.md`: "ADR-0036's
   extension-based rows are revised to match" becomes "are superseded in part by ADR-0040".

### B2. The lane is stale against origin/main, and ADR-0039 is now Accepted

`origin/main` b0bf89b ("ratify the decision log") is not in the lane
(`git merge-base --is-ancestor origin/main HEAD` fails). On main:

- ADR-0039 is **Accepted** (`doc_status: active`). The lane still has Proposed, and its index row
  reads "Proposed; amended by ADR-0040", which conflicts with main's "Accepted".
- ADR-0036 gained an "Extended in part by ADR-0039 (2026-10-27)" note under Status, and its index
  row reads "Accepted; extended in part by ADR-0039 (2026-10-27)".
- `docs/adr/README.md` changed many rows ("firm the provisional band"), and
  `docs/changelog/README.md` gained a new entry.

Required fix: merge `origin/main` into the mission branch, or into this lane through the usual
reconcile path, before the docs fix. Then rebuild the 0036, 0039 and 0040 rows on main's index.

### B3. The ADR-0039 amendment must follow the amendment rule, now that 0039 is Accepted

`docs/adr/0039-...md:35-53` is append-only and dated, which is right. Two things are still missing.

- It cites "the operator's ruling of 2026-10-27" without the decision id. Required: cite
  DM-01M5ZAMP0R5V5YF5KM09YXY0NS (reconcile with ADR-0039) and ADR-0040.
- The points it changes are not annotated **where they stand**: Decision 2 at lines 83-84 ("#193
  adds a `Bytes` case", "move to bytes with it"), the Consequences line at 152 ("#193 ... can extend
  the same hooks") and line 162 ("a binary companion would fail them until #193 moves them to
  bytes"). Required: add a short italic "*(Amended 2026-10-27, see Status: ...)*" after each one and
  keep the original text.
- Index row: use main's "Accepted" plus the house wording, for example "Accepted, with Amendment
  2026-10-27".

## Non-blocking (fix in the same pass)

- N1. `docs/adr/0040-...md`, Decision 1: "`mime` stays metadata: ... validated by the asset schema
  (a string that is not empty)". `PackPolicy` (`domain/PackPolicy.java:370-381`, through
  `AssetMimeTable`) also checks `mime` for a well-formed `type/subtype` and reports a policy finding
  when it does not match the extension. It is still never a selector. State that as well, so the
  record does not under-describe the validation that exists.
- N2. `PackFileKind.java:295` and its test say "#192 builds markdown". #192 (ADR-0039) built
  companions, not the `markdown` content form, and ADR-0040 says "`markdown` is named and not built
  here". This is a code string, out of WP08's map, so only consider aligning ADR-0040 decision 3 /
  "markdown is #192's" wording with what #192 actually shipped.

## Verified (no action)

- ADR-0040 numbering: free on `origin/main` (0039 is the highest). Operator decision
  DM-01M5ZAMVQ19X9GT3RN2SNJ44QM.
- Claims checked against the code and tests:
  - content_form `markdown` and `text` refused at load: `PackFileKindTest:210-211`.
  - Missing-payload refusal reason: `FilesystemPackStore:315`, `DomainRoundTripTest:329`,
    `WholePackStoreTest:560`.
  - "already named by asset": `FilesystemPackStore:320`, `AssetStoreTest:235`.
  - Undeclared key named: `FilesystemPackStore:790`, `AssetStoreTest:184`.
  - `computeOverBytes` keeps text revisions: `PackRevisionTest:99-121`.
  - Fingerprint unchanged without assets or templates: `PackFileSetTest:97`.
  - Case-fold refusal for asset files and templates: `WholePackStoreTest:518,534`.
  - Six pinned refusals: `expected-red.yaml` (6).
  - Byte offset in the gate's red detail: `WholePackRoundTripTest:465-477`.
  - `Companions.inPack`: `Companions.java:129`.
- Logical model and glossary: accurate. There is no term collision: "Template (pack)" is
  disambiguated from "Template Set" and "Template Instantiation", "Content form" is distinguished
  from `YamlFormat`, `mime` and `SerializationStrategy`, and the contextive shape (name, definition,
  meta.status) matches its neighbours.
- Out-of-map edits: `docs/changelog/README.md` (the index entry the new changelog requires) and
  ADR-0039 (required by T032) are justified. ADR-0039 is listed in owned_files.
- `python3 scripts/docs/check_docs.py all`: all ok. markdownlint-cli2@0.18.1 on the changed docs:
  0 errors. cspell@9.2.0: 3 findings in `site/.contextive/definitions.yaml` lines 75, 159 and 205
  ("unioned", "frozenset"). They predate this WP; none are in WP08's lines.
