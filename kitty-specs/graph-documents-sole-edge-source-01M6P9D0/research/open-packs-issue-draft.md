# Issue draft for spec-kitty/spec-kitty-open-packs

**Title:** Move artifact reference fields into the graph documents (charter #240, ADR-0063)

---

The charter now reads relationships only from a pack's graph documents (`*.graph.yaml`,
`drg/fragment.yaml`) and writes them only through `PUT /packs/{packId}/relationships`
(spec-kitty/spec-kitty-charter#240, ADR-0063). The artifact fields that used to carry references
(`references` on directives, paradigms, tactics and tactic steps, procedures, styleguides, toolguides,
mission types and mission steps; `directive_refs` on paradigms; a directive's `procedures`) are
dropped from the charter's pack schemas, which refuse unknown fields.

It lands in three parts so neither repository is ever red. Ready patches, their generators and the
evidence are attached to the charter PR's hand-off comment.

### Part A: reference fields into edges (generated)

- Removes the fields from 206 artifact files, keeping comments and layout.
- Adds 29 graph edges where no graph document held the pair, and merges 31 `reason`
  into existing edges that had none. Adds nothing for 9 references that a reverse edge already
  covers (ADR-0027: one structural edge per pair).
- Moves 20 citations that name no artifact into the styleguide text, as an inline `(source: …)` on
  the principle they back or a closing `Sources: …` sentence.
- Restamps `packs/built-in/pack-manifest.yaml`, records the new `packs/built-in` tree digest and
  regenerates `docs/packs/*.md`.

### Part A2: directive procedures into procedure artifacts (authored)

- Turns 41 directives' 192 prose steps into 47 procedure artifacts. 6 of them are shared between
  directives.
- Each directive `requires` its procedures through a graph edge (50 edges). The `procedures` field is
  removed.
- Review the slicing and wording; step text is verbatim apart from four noted fixes.

### Behaviour

Every dropped reference maps to a live graph edge (743 already authored, 222 added by A and A2) or,
for a citation, to text (20). None is left unresolved; the per-reference table is
`dropped-reference-map.md`. Where a field and an existing edge disagreed on the relation (142 pairs)
or on the `when`/`reason` text (49), the graph's value is kept, and each case is listed for review.

### Checked

The checks run on A, and on A + A2:
- `validate_pack_schemas.py` passes under the pinned charter contract (`85c20fa`). A + A2 also pass
  under the new one.
- `check-built-in.sh`, `gen_pack_reference.py --check` and `pytest tests/ci` pass.
- The charter's `verifyAll` passes against A + A2.

The docsite build is still to run on the PR.

### Part B (after the charter PR merges)

- Add `when` to the 27 fragment edges listed in the migration notes. The org-fragment edge schema
  gains `when` with the charter change.
- Move `CHARTER_COMMIT` (`scripts/ci/validate_pack_schemas.py`) to the merged charter commit.
- Remove `TYPED_REFERENCE_SCHEMAS`, `BARE_ID_LISTS` and `written_references` from
  `validate_pack_schemas.py`, along with their test table in `tests/ci/test_validate_pack_schemas.py`
  and the `directive_refs` mention in `docs/artefacts/index.md`.
- Regenerate `docs/packs/*.md`.

### Merge order

1. open-packs A
2. open-packs A2
3. charter #240 PR (moves `openPacksRef` to the A2 commit)
4. open-packs B
