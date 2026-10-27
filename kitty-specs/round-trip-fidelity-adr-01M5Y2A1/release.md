# Release handoff: round-trip fidelity decision (ADR-0036)

Publish step, 2026-10-27. Gate: `audit-report.md`, verdict **ready-to-publish** as a Proposed record.

## What ships

- `docs/adr/0036-round-trip-fidelity-is-equality-of-meaning.md`: the decision, with the per-kind table
  and sixteen worked examples. Status **Proposed**.
- Dated "on acceptance" pointers on ADR-0017 (supersede in part, R5/R13), ADR-0025 (supersede in part,
  D5) and ADR-0027 (clarification, D2); open question #29; a **Planned** note in
  `docs/architecture/descriptors.md`.
- The ADR index row 0036, a changelog entry and its index line.

The full file list is `docs/README.md` in this mission directory.

## Where it ships

The docsite (doc-kitty, GitHub Pages), built by `.github/workflows/docs-site.yml` when the pull request
merges to `main`. The operator merges, and publication follows the merge. Source revision: the PR's head
at merge. No generator is involved (hand-authored Markdown, ADR-0003).

## Owner

The operator (Stijn Dejongh) owns publication, acceptance of ADR-0036 and its open questions.

## Caveats accepted in validate

- **Proposed, not Accepted.** The three questions on #188 are still open: expected failures while an
  upstream fix is pending, the charter wording, and kinds without a schema plus `pack-manifest.yaml`.
  On acceptance:
  - the Status changes to Accepted;
  - the "on acceptance" pointers on ADR-0017/0025/0027 and open question #29 become dated
    supersessions;
  - the index status of those rows is updated.
- The agent's readings are marked *Reading:* in the ADR (18 markers) and listed on #188 for the
  operator to overrule.
- FR-007 is met only up to open question 1 (audit-report S23).
- No code, contract or schema changes. The ADR's follow-up list names that work for #187, #189 or an
  issue the operator places.

## Living documentation

ADR-0036 is an explanation tied to the pack schemas and `file-kinds.yaml`. DIRECTIVE_037 applies.
- When a kind mission (#190 to #195) adds a schema, it records the kind's sets and open maps against
  Decision 2.
- When the unknown-field follow-up lands, DIV-005/006 close and the Planned note in `descriptors.md`
  becomes current behavior.

## Drift handed to the next iteration

- The charter text ("round-trips exactly", PROJECT_002) waits for open question 2.
- In code (#187): the stale build comment in `service/packs/build.gradle.kts` and the reference to a
  byte-level test that no longer exists in `PackRoundTripTest`.
- The stale branch `feat/pack-roundtrip-io` pins the old drop of undeclared keys. Branch housekeeping
  is the operator's.
