---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-14T16:24:54Z'
reviewer_agent: claude-reviewer
wp_id: WP09
---

# WP09 review, cycle 1 (claude-reviewer): changes requested

Reviewed commit e9c837e80 against its parent. Runs from lane-i, each with `--rerun --max-workers=1`, all green:
- `:kernel:test`: 2962 tests, 0 failed.
- `:packs:test`: 5156 tests, 0 failed. The 4 skips are environmental and existed before (FileSystemPackSourcesTest x3, SymbolicLinksTest).
- `:contracts:check`: passed.
- `:deployable:test`: 1991 tests, 0 failed, 0 skipped. This includes the offering, root and every-operation scenarios, HexagonalArchitectureTest and RetiredNamesScanTest.
- `spotlessCheck` and `checkstyle` on kernel, packs and deployable: passed.

## MEDIUM (blocks approval)

**M1: the FR-003 parity check leaves out the relation flags.**
- Where: `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java:353-395` (`assertTaxonomyIsTheModel`).
- What it checks today: each kind's token, order, plural, `artifact`, `activatable` and `authoredInGraphs`, and each relation's token, order and endpoints.
- What it never checks against the model:
  - `directed`;
  - `derived`;
  - `heldAs`;
  - each kind's `listed` fact (the `artifacts` link).
- Spec acceptance scenario 2 requires that "every kind, plural, flag and endpoint pair matches". The charter says a parity check never leaves out a field.
- Today a wrong `directed` value on any row would pass this test, and nothing else checks `directed` against the model:
  - the contract example is hand-written;
  - the scenarios check only `derived` on row 12 and `heldAs` on row 13.
- Fix: in `assertTaxonomyIsTheModel`, for each relation row, assert:
  - `directed` against `RelationType.directionality() == DIRECTED`, or `Tension.directionality()` for `in_tension_with`;
  - `derived` against `RelationType.derived()` (false for the tension);
  - `heldAs` is `TENSION` exactly for `in_tension_with`.
- Also assert each kind's `listed` against `ArtifactReadService.listed()`.
- Add a positive control, for example one that flips `directed` on one row and must fail with a message naming that row.

## LOW (fold in while you are there; these do not block)

- **L1.** `service/packs/src/main/java/ai/speckitty/charter/packs/application/KindTaxonomyReadService.java:133`: the tension row's `derived` is the literal `false`, the only fact not read from the kernel. Either expose it beside `Tension.directionality()`, or say in a comment why a tension is authored (ADR-0028 decision 2).
- **L2.** `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Tension.java:80`: the symmetry is cited to ADR-0028 decision 2. The explicit ruling is ADR-0036 decision 4 ("the one symmetric relation", with a canonical orientation and comparison as written). Cite that ruling for symmetry.
- **L3.** The class Javadoc of `OfferingController` and the root Javadoc of `InfoController` (around line 51) have lines over 120 characters. Checkstyle does not enforce this; rewrap if you touch the files.

## Judgment on the implementer's decisions (no change asked)

1. **Own enums `TaxonomyKind` (17) and `TaxonomyRelation` (14).** Accepted.
   - `NodeKind.yaml` documents that adding guard or lifecycle is a Breaking change for other carriers, and `Relation.yaml` excludes the tension by ADR-0045.
   - Both enums carry `x-source` and are paired in WireTokenParityTest.
   - The change departs from plan IC-04 ("references NodeKind.yaml / Relation.yaml"), so WP10 should record it in the ADR/docs pass as a reviewed call.
2. **`Tension.directionality() = BIDIRECTIONAL`, served as `directed: false`.** Consistent with the existing model; no operator decision is needed.
   - ADR-0036 decision 4 already calls the tension "the one symmetric relation", and data-model.md writes it `G <-> G`.
   - Comparing a tension as written is about identity and round trip, not about meaning, so the two do not conflict.
3. **The operation sits in `OfferingController`.** Accepted: the generator emits one `OfferingApi` per tag.
4. **The tension row comes last, and `artifacts` links appear only on the 10 listed kinds.** Accepted.
   - Both follow the spec and contract ("RelationType order, then in_tension_with").
   - A link to a kind the list refuses would not resolve.
   - The links come from `ArtifactReadService.listed()`, not from a hand-kept list. M1 asks for that fact to be in the parity test.
5. **Out-of-map edits.** Accepted. All are recorded in the commit message.
   - `EndpointTable.tension()` and `Tension.allowedEndpoints()` move no behavior: `tensionAllows` is the same G x G set.
   - `StrongETag` is a verbatim move of ContractDocument's parser and still covered by ContractDocumentTest.
   - `hypermedia-hateoas.md` is owned by WP10, which must rebase onto this change.
