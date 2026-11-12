# Draft issue for spec-kitty-open-packs: `open-packs#TBD-readme-doc`

**Title:** Each pack's documentation is its `README.md`: merge `pack.md`, fold `PROVENANCE.md` in, delete both

**Type:** Task · **Filed for:** charter slice B (spec-kitty/spec-kitty-charter#466, ruling on #451)

## Why

The charter service now holds one documentation file per pack, `README.md` at the pack root. This follows the operator
ruling of 2026-11-11 on spec-kitty/spec-kitty-charter#451 and ADR-0042 as amended on 2026-11-11, which lands with the
charter's slice B PR (#501). The README is held in the store, round-tripped, and served at
`GET /api/v1/packs/{packId}/doc`.

- `pack.md` is **retired**. A pack that ships it is refused at registration by name (`retired`) and is not loaded.
- `PROVENANCE.md` is **retired** in the same way. Its content belongs in the README as a section.
- A file that differs only in letter case from `README.md`, `pack.md` or `PROVENANCE.md` (for example `Readme.md`) is
  refused as `retired` too.
- Other Markdown that no rule models is author aid. It is left out of the pack with a stated reason and is not refused.
  This covers the mission and asset `README.md` notes, `agent_profiles/README.md` and `styleguides/writing/README.md`.

A dry run of the slice B service against `main` `a6cfeab` ran the registration load (`WholePackStore.loadStaged`,
IC-04 step 2) on every pack. Today 35 of these files are refused there as `retired`: 19 `pack.md` and 16
`PROVENANCE.md`. Seven packs are refused for these files alone: architecture, built-in, foundation, planning-analysis,
refactoring, testing and writing. With this change and `open-packs#TBD-org-charter-preset` applied, all 19 packs pass
the registration load with no refusal. With this change alone, the 12 packs that ship `org-charter.yaml` still fail it.

## What

### The 19 packs

The table shows the state at `main` `a6cfeab`, in line counts. The same files were present at `faa4770`.

| Pack | `pack.md` | `README.md` | `PROVENANCE.md` | Work |
| --- | --- | --- | --- | --- |
| architecture | 13 | 19 | 10 | merge, fold |
| built-in | 20 | — | — | rename `pack.md` to `README.md` |
| cave-kitty | 10 | 84 | 50 | merge, fold; keep usage text |
| drupal | 13 | 19 | 10 | merge, fold |
| event-driven | 10 | 77 | — | merge; keep usage text |
| foundation | 16 | 16 | 10 | merge, fold |
| go | 9 | 51 | 42 | merge, fold |
| internal | 10 | 185 | 31 | merge, fold; keep usage text |
| java | 13 | 19 | 10 | merge, fold |
| javascript | 13 | 19 | 10 | merge, fold |
| pack-authoring | 13 | 19 | 10 | merge, fold |
| planning-analysis | 13 | 19 | 10 | merge, fold |
| python | 13 | 19 | 10 | merge, fold |
| qa | 11 | 120 | 65 | merge, fold; keep usage text |
| refactoring | 13 | 19 | 10 | merge, fold |
| rust | 8 | 53 | 41 | merge, fold |
| spdd | 9 | 27 | — | merge |
| testing | 13 | 19 | 10 | merge, fold |
| writing | 14 | 13 | 10 | merge, fold |

"Keep usage text" marks the long READMEs of internal, qa, cave-kitty and event-driven. Their usage sections stay.

### Steps for every pack

1. **Merge `pack.md` into `README.md`.** Keep the README's H1 and its description. Take from `pack.md` only what the
   README lacks:
   - **The `pack.md` preamble is dropped**, except where "Per-pack fixes" below says otherwise (go, rust). In every pack
     its first paragraph repeats the README's description. In most packs its second paragraph is descriptor
     facts: the level, the ADR decision that places the pack, and what it builds on. A provenance fact in it, such as
     "Carved out of `built-in` by #73", belongs under `## Provenance`. Every such fact at `a6cfeab` is already in the
     `PROVENANCE.md` text that step 3 folds in.
   - **`pack.md`'s H2 sections** are added as H2 sections of the README: "Who opts in" or "Who uses it", and "Authored
     and generated files". The exception is "Contents", which repeats the README's contents bullet and is dropped.
2. **Drop facts that duplicate `pack.yaml`.** These are the **Level**, **pack_id** and **Depends on** facts. The
   service serves them from the descriptor. Keep the contents, agent-profile and mission-type bullets: they describe
   the pack and are not descriptor fields.
3. **Fold `PROVENANCE.md` in as a `## Provenance` section** (H2). Drop its H1 and demote every heading in it one level,
   so its H2s become H3s. The section replaces the README's pointer to the file: a `## Provenance` section whose only
   line links `PROVENANCE.md`, or an inline "see [PROVENANCE.md](PROVENANCE.md)". A pack without `PROVENANCE.md`
   (built-in, event-driven, spdd) gets no new section.
4. **Delete `pack.md` and `PROVENANCE.md`.**
5. **Keep the README readable by the charter's Markdown model.** The model is the `pack-document` structure schema.
   It allows one optional YAML frontmatter block, at most one H1, a preamble, and H2 sections. The codec's full line
   rules are in the class comment of `MarkdownScanner` (spec-kitty/spec-kitty-charter,
   `service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/MarkdownScanner.java`). These are refused:
   - a second H1, such as a copied `# Provenance`: "a second H1 (the first is at line 1)";
   - a heading underlined with `---` or `===` (a `setext` heading);
   - an H1 or H2 with 1 to 3 leading spaces, an empty one, one separated from its text by a tab, or one with a closing
     `#` sequence (`## Provenance ##`);
   - a code fence still open at the end of the file;
   - a carriage return anywhere in the file, or a byte order mark at the start.

   H3 and deeper headings, and `#` lines inside a fence, are section text. All 18 READMEs at `a6cfeab` already pass.
   Only the merge can break them.

### Per-pack fixes

- **architecture, drupal, java, javascript, pack-authoring, planning-analysis, python, refactoring, testing.**
  - Delete README lines 5 to 7, which are bullets 1 to 3 (Level, pack_id, Depends on).
  - Drop `pack.md`'s preamble and its "Contents" section. Add its "Who opts in" or "Who uses it" section.
  - Replace the README's `## Provenance` section, "Carved from `packs/built-in/` by #73; see
    [PROVENANCE.md](PROVENANCE.md).", with the `PROVENANCE.md` body. That body is two paragraphs under its H1 and has
    no H2s.
- **built-in.**
  - `git mv pack.md README.md`. Its H1 "Built-in Charter Pack" and its H2 "Authored and generated files" are already
    readable.
  - In "Authored and generated files", "This file" now means the README.
  - It has no `PROVENANCE.md`. Provenance is `packs/built-in.source.yaml` and the README's existing text.
  - Update the comment in `packs/built-in.source.yaml` (line 16) that lists `pack.md`.
- **foundation, writing.**
  - The README has no H2 yet.
  - The bullet "**Level:** built-in. **pack_id:** `…`. **Depends on:** …" holds only descriptor facts: delete it.
  - Drop `pack.md`'s preamble. Add its "Authored and generated files" as an H2. There, "This file" now means the README,
    as for built-in.
  - Replace the closing line "Carved from `packs/built-in/` by #73; see [PROVENANCE.md](PROVENANCE.md)." with a
    `## Provenance` section that holds the `PROVENANCE.md` body.
- **cave-kitty.**
  - Drop `pack.md`'s preamble. Its first paragraph repeats the README's opening, and its second paragraph, which ends
    "See the pack's `README.md` for the scope notes and runtime caveats", is covered by the README's scope note.
  - `PROVENANCE.md` has three H2s ("What was taken, and what was not", `Licence`, "Status"). Under `## Provenance` they
    become H3s.
  - The `# Voice only, …` lines in the "Adopt" section sit inside a fenced `bash` block. They are content and stay as
    they are.
- **event-driven.**
  - Drop `pack.md`'s preamble. Its two paragraphs repeat the README's preamble almost word for word.
  - There is no `PROVENANCE.md`. The README's `## Status` section already says the content is net-new.
  - The fenced block at lines 10 to 13 is fine.
- **go, rust.**
  - Keep the first paragraph of `pack.md`'s preamble ("Idiomatic Go for any Go codebase: …", "Idiomatic Rust for any
    Rust codebase: …") as the README's opening paragraph, before "An opt-in org stack pack …". It is the summary the
    generated docs page shows as the pack's Purpose. Drop the second paragraph: it repeats the README's artifact table
    and states the level.
  - `PROVENANCE.md` has four H2s ("Seed", "Seed contradictions and how they were resolved", "Other sources consulted",
    "Verification at authoring time"). They become H3s under `## Provenance`.
  - Replace the closing line "Where the content came from: [PROVENANCE.md](PROVENANCE.md)." with that section.
  - The README sentence about `org-charter.yaml` is `open-packs#TBD-org-charter-preset`'s.
- **internal.**
  - Drop `pack.md`'s preamble. The README's opening and its "Why this is a separate pack" section say the same.
  - Fold `PROVENANCE.md`, whose H1 is "Provenance: spec-kitty-internal Charter Pack (canonical home)". Its three H2s
    ("Identity is minted and owned here", "How downstream repositories take the pack", "Received from built-in (#73)")
    become H3s.
  - Update the comment in `pack.yaml` (line 3), "see PROVENANCE.md", to point at the README's Provenance section.
  - The layout tree's `org-charter.yaml` line is `open-packs#TBD-org-charter-preset`'s.
- **qa.**
  - Drop `pack.md`'s preamble. It repeats the README's opening.
  - Fold `PROVENANCE.md`. Its three H2s ("What changed in the port", "Verified at port time", "Received from built-in
    (#73)") become H3s.
  - Replace the pointer in item 1 of the preamble list, "see [PROVENANCE.md](PROVENANCE.md)", with "see Provenance
    below".
  - The existing `### Known limitations` under `## Status` stays.
- **spdd.** Drop `pack.md`'s preamble. Its two paragraphs repeat the README's preamble. There is no `PROVENANCE.md`.

### Repository changes that move with the files

1. **`scripts/ci/charter_service_check.py`.** Remove `README.md` and `PROVENANCE.md` from `NOT_REGISTERED`. The
   README is the pack document now: left out of the uploaded archive, it never reaches the service, and the doc read
   answers that the pack has none. The other entries are now excluded by the charter itself
   (`missions/*/templates/README.md`, `missions/*/templates/plan-field-declaration.yaml`). Removing them is optional.
   Update `tests/ci/test_charter_service_check.py` to match.
2. **`scripts/ci/validate_pack_schemas.py`.**
   - Stop treating `pack.md` as the pack's Markdown companion. The pre-check at lines 832 to 836 asks for `pack.yaml`
     when `pack.md` is present: change it to `README.md`.
   - Drop `*/PROVENANCE.md` from `UNCLASSIFIED_ALLOWANCES`.
   - Once the pin moves, the root `README.md` is a `files` rule (`pack-document`, `markdown`) of the pinned charter's
     `file-kinds.yaml`.
   - Update `tests/ci/test_validate_pack_schemas.py`.
3. **`scripts/ci/check_vocabulary.py`.** The `PROVENANCE_PHRASES` row that reads `packs/qa/PROVENANCE.md` reads
   `packs/qa/README.md`. Update `tests/vocabulary/test_check_vocabulary.py`.
4. **`scripts/docs/gen_pack_reference.py` and the generated pages.** The generator renders the `pack.md` preamble as
   each page's Purpose. Make it read the README preamble. Then run `python3 scripts/docs/gen_pack_reference.py` and
   commit `docs/packs/*.md`. Both `validate-packs.yml` (line 75) and `docs-site.yml` (line 125) run
   `gen_pack_reference.py --check`, which fails on any drift. Update `tests/docs/test_gen_pack_reference.py`.
5. **Tree digests.** Run `python3 scripts/ci/pack_digest.py --update` so that every `packs/<pack>.source.yaml` records
   the new tree. `scripts/ci/check-pack-digests.sh` fails otherwise.
6. **Every other file outside ADRs and the changelog that names `pack.md` or `PROVENANCE.md` as a live file.** At
   `a6cfeab` these are:
   - `docs/packs/index.md`, lines 49 to 50 ("a `pack.md` — the pack's description, whose preamble the generated
     reference page renders under its Purpose heading") and lines 75 to 76 ("the preamble of the pack's authored
     `pack.md`");
   - `docs/guides/author-a-charter-pack.md` (the layout tree at line 29 and the file table at line 58) and
     `docs/guides/validate-a-pack.md` (lines 40 and 124);
   - the comments in `.kittify/config.yaml` (line 25), `packs/built-in.source.yaml` (line 16) and
     `packs/internal/pack.yaml` (line 3);
   - the scripts and tests in items 1 to 4.

   ADRs and the changelog keep their history.

### Order

Steps 1 to 6 can land before or after `open-packs#TBD-org-charter-preset`. Take the `NOT_REGISTERED` change and the
`charter.lock.yaml` bump to the charter release that carries slice B together. A charter older than slice B reads
`pack.md` and does not model the root `README.md`, so uploading the README to it is refused as `unmodelled`.

Out of scope: other Markdown inside packs, such as mission templates and their notes, `agent_profiles/README.md` and
guides. Templates and assets keep their rules. Other unmodelled Markdown is ignored by the charter, not refused.

## Acceptance

- [ ] No pack under `packs/` holds `pack.md` or `PROVENANCE.md`, in any letter case.
- [ ] Every pack has a root `README.md` with exactly one H1, ATX headings only and no closing `#` sequence on any H1 or
      H2. `built-in` has one too.
- [ ] Every pack that had a `PROVENANCE.md` has a `## Provenance` section with its content and no link to the deleted
      file. No README restates a Level, `pack_id` or Depends-on fact
      as a descriptor bullet or sentence; adoption guidance that names the packs to install alongside it
      stays.
- [ ] No file in the repository outside ADRs and the changelog names `pack.md` or `PROVENANCE.md` as a live file.
- [ ] `NOT_REGISTERED` no longer lists `README.md` or `PROVENANCE.md`, so the README is uploaded and
      `GET /api/v1/packs/{packId}/doc` serves it.
- [ ] `check-pack-digests.sh`, `validate_pack_schemas.py`, `check_vocabulary.py` and `gen_pack_reference.py --check`
      pass.
- [ ] Open-packs CI, run against the charter release that carries slice B, registers every pack with no `retired`
      violation for these files and no `unreadable` violation for any `README.md`.

## Related

- spec-kitty/spec-kitty-charter#451 (ruling), #466 (slice B), and the charter's slice B PR #501, which carries the
  amended ADR-0042.
- `open-packs#TBD-org-charter-preset` is the other half: `org-charter.yaml` and `active_charter`. Both are needed for
  every pack to register.
- Spec Kitty core reads neither `pack.md` nor `README.md`, but its fresh project seed (`charter synthesize`) writes
  and reads a root `PROVENANCE.md`, which the charter now refuses as `retired`. That divergence is recorded on the
  charter side (DIV-302), and core aligns later through the generated client.
- open-packs #201 (built-in `policy.yaml`) and #202 (the `jvm` language tag, possibly settled by open-packs PR #198)
  are not part of this change.
