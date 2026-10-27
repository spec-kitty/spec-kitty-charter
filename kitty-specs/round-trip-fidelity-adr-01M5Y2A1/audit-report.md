# Validation report: ADR-0036 adversarial review squad (2026-10-27)

Procedure `adversarial-squad-deployment`, point-cut **ad-hoc decision / pre-PR**, on branch
`feat/round-trip-fidelity-adr` at `8d6fe4c`. Question: *can #187 and #189 be built from ADR-0036
alone without re-deciding, and is the decision sound and consistent with the record?* Four
read-only delegates ran in parallel:

- `reviewer-renata`: buildability, worked examples, facts against code and schemas.
- `architect-alphonso`: consistency with the ADRs, onion, contract and charter.
- `doctrine-daphne`: duplication, terminology, separating rulings from readings.
- A fresh-context applier (lighter model tier, ADR text only): applied the ADR to 14 new cases whose
  expected verdicts the author held back.

**Fresh-context result (SC-001):** 12 of 14 cases matched the held-back verdicts. Two came back
ambiguous: N4 (whether `null` is valid for `requires_core`) and N13 (`title: 1.0` against a string
schema). Both pointed at the same gap: the ADR never said that validation runs before comparison
and that values are never converted. The revision adds that rule ("Validation comes first"), and
both cases now resolve.

**Docs checks after remediation:** `check_docs.py all` ok (8/8); cspell 0 issues; markdownlint-cli2
0.18.1 0 findings on the changed files.

**Verdict: ready-to-publish** as a **Proposed** record. Every finding is dispositioned below.
Acceptance waits for the operator's answers to the three open questions on #188.

## Findings and dispositions

Each finding is listed once. Where lenses converged, it is listed under all of them.

| # | Lens | Sev | Finding | Disposition | Evidence |
|---|---|---|---|---|---|
| S1 | architect | HIGH | Decision 3 claimed an unknown field "fails the load" through the existing path for artifacts and org-charter, but `ARTIFACT_SCHEMA_ERROR` and `ORG_CHARTER_SCHEMA_ERROR` are validate-mode only (`PackFindingCode.refusal()` throws). | accepted. Table split into "on load" and "in validate mode"; *Reading:* the file fails to load and the round trip fails; whole-pack refusal is for the operator to rule. | ADR-0036 Decision 3 |
| S2 | architect | HIGH | `drg_schema_error` is `raised: false`, so "refused as `drg_schema_error`" names an unanswered code. | accepted. The text now says it is raised once #189 loads graphs, and the contract change is added to the follow-ups. | Decision 3; Follow-up work |
| S3 | reviewer | HIGH | The text said "YAML 1.2 core schema", but the reader uses the JSON schema plus null forms (R18). The two resolve `True`, `0x10`, `.inf` and others differently. | accepted. The service reader's resolver is named canonical; merge keys, timestamps and `1e3` are stated; *Reading:* a core/service resolver mismatch is a pack inconsistency. | Decision 2 "Canonical form" |
| S4 | reviewer | HIGH | E12 nested a map in `tooling`, which the schema types as string values. | accepted. E12 rewritten with string values. | Worked examples |
| S5 | reviewer, fresh | MED | Open maps were listed as if complete; other schema maps have free keys. | accepted. Open maps are defined by rule (`additionalProperties` not `false`), with examples. | Decision 2; per-kind table |
| S6 | reviewer | MED | The absent/null/empty equivalence was applied "deeply" in open maps, against the writer's literal `{}` and nested nulls. | accepted. Equivalence applies to declared fields only; data and sequence items compare literally (E14). | Decision 2 |
| S7 | reviewer | MED | "`1.0` and `1` differ" clashes with JSON Schema and with `impacts` held as a double. | accepted. *Reading:* numbers compare by value (E13). | Decision 2; E13 |
| S8 | reviewer | MED | Empty files, null documents, top-level scalars, multi-document streams and the BOM were not covered. | accepted. "Documents" row added; the BOM is listed under presentation. | Decision 2 |
| S9 | reviewer, architect | MED | Ring and paths were wrong: `PackPolicy` and `PackContents` are domain, and "the domain never sees an unknown key" is inaccurate. | accepted. Now reads: detected by `PackSchemas` (adapter), judged by `PackPolicy` (domain); the key never reaches a record. References fixed. | Decision 3; References |
| S10 | doctrine, architect | HIGH/MED | The ADR-0025 pointer was labelled a clarification but changes D5. | accepted. Relabelled "to be superseded in part … on acceptance", also in ADR-0036 Status. | ADR-0025 Status; ADR-0036 Status |
| S11 | doctrine, architect | MED | Supersession tense was inconsistent across ADR-0017/0025/0027, open question #29 and ADR-0036. | accepted. "On acceptance" is used everywhere. | Those four records |
| S12 | doctrine, fresh | MED | Filing steps contradicted each other: step 2 always filed in open-packs, and "a file mapping to no rule" was called a pack inconsistency. | accepted. Step 2 routes by step 1; a classification or schema defect is filed here (ADR-0015). | Decision 5 |
| S13 | doctrine | MED | The per-kind table duplicates `file-kinds.yaml` and will drift; "every new set is an amendment" overloads the README's amendment rule. | changed. `file-kinds.yaml` is stated as the authority; the table only attaches comparison, sets and open maps; a kind mission records its sets in its own record or an amendment. | Per-kind table preamble; Risks |
| S14 | doctrine, architect | MED/LOW | Unmarked readings: nodes as a set, schema defaults, scalar typing, Markdown normalization, bytes, the ring and the code, `pack_unknown_key` retirement, comments not kept. | accepted. Each is marked *Reading:* (18 markers). | ADR-0036 Decisions 2–4 |
| S15 | doctrine, architect | MED | An ADR should not redefine the charter, and dropping comments strains PROJECT_002 "or rewrite". | accepted. Acceptance waits for open question 2; "rewrite" and comments are added to that question. | Status; Open question 2 |
| S16 | architect | LOW | The forward-compatibility argument missed that refusal converges with core (DIV-005/006). | accepted. | Decision 3 |
| S17 | architect | LOW | "Merge becomes set-based" reached into #189. | accepted. It is now a consequence for #189 to decide. | Follow-up work |
| S18 | reviewer, fresh, doctrine | LOW | The 55 vs 54 count needed explaining. | accepted. | Context; Consequences |
| S19 | reviewer, fresh | LOW | "Structural" relations were never listed. | accepted. Every relation except tension is structural. | Decision 4 |
| S20 | reviewer | LOW | Message template and code form were inconsistent between E8 and E9. | accepted. Both show the finding code; E9 adds the wire code. | Decision 3; E8, E9 |
| S21 | reviewer | LOW | Context omitted dropped `provenance` and the hard-coded `generated_at`. | accepted. | Context |
| S22 | reviewer | LOW | A `.md` with frontmatter was normalized, one without was compared by bytes. | accepted. All `.md` files get the same rule. | Decision 2; per-kind table |
| S23 | reviewer | LOW | FR-007 ("what this repository does until the pin moves") is not met while open question 1 is open. | deferred_with_rationale. It is the operator's call; it is open question 1, and the ADR stays Proposed. | Open question 1 |
| S24 | reviewer | LOW | FR-009 asked for DIV-005/006 pointers, which are absent. | changed. DIV entries describe current code and close when it changes; FR-009 is reworded; `descriptors.md` carries the Planned note. | spec.md FR-009 |
| S25 | doctrine | LOW | The changelog said "settling #188" while questions are open. | accepted. | Changelog entry |
| S26 | doctrine | LOW | The ADR is long (vs 89–119 lines); the table and examples could move to an architecture page. | deferred_with_rationale. The table and examples are the decision's substance for #187; once #189 has a registry, the sets can move into its data (Per-kind table preamble). | Per-kind table preamble |
| S27 | fresh | LOW | "Artifact file" was not defined. | accepted. Decision 3's table lists the kinds. | Decision 3 |

## Concessions recorded by the squad

All 14 `files` rules and all 14 `exclusions` are covered (NFR-003). Source-against-written exposes
reader loss (E10, true against `GraphShardWriter`). Edge and node identities match the schemas. The
tension orientation matches the schema's `Relation` documentation. The one-per-pair rule stays a
domain invariant, so nothing collapses silently. The rulings are quoted faithfully. Terminology is
canonical. There is no collision with #187 or #189.

## Pre-PR verification pass

One `reviewer-renata` delegate checked that every disposition above had landed. 23 of 27 had landed
in full. S1, S6, S9 and S14 had landed only partly. Its findings:

| # | Sev | Finding | Disposition | Evidence |
|---|---|---|---|---|
| V1 | HIGH | "Not loaded, and reported" for artifact and org-charter files has no wire channel: both `refusal()` and `diagnostic()` throw on load. (S1 partial.) | accepted. The text now says the store skips the file and lists it in `PackRead.violations()`, and that nothing reaches the wire today. Whether it should is for #189 to propose and the operator to rule; it would be a contract change. | Decision 3 |
| V2 | HIGH | The literal rule "inside every sequence" contradicted the declared-field rule for object items such as edges and steps. (S6 partial.) | accepted. The declared-field rule now applies at any depth; literal comparison is limited to open-map contents and `null` list items (E16 added). | Decision 2; E16 |
| V3 | MED | "Decisions 1 to 4 are rulings" did not fit the overrulable readings. | accepted. | Status |
| V4 | MED | The References path for `PackPolicy` was still wrong. (S9 partial.) | accepted. | References |
| V5 | MED | The structural classification was not marked as a reading and was silent on `rejects` and `impacts`. (S14 partial.) | accepted. | Decision 4 |
| V6 | LOW | `.inf` as a string could not be verified against snakeyaml-engine. | accepted. Dropped from the list. | Decision 2 |
| V7 | LOW | E13: an `impacts` value sits only on an `impacts` edge. | accepted. | E13 |
| V8 | LOW | "Numbers by value" depends on the validator accepting `2.0` as an integer. | accepted. Cited as JSON Schema 2020-12; a test in #187 confirms it. | Decision 2 |
| V9 | — | Still open for #187: the gate's output format for an inconsistency, and detecting resolver drift against core. | deferred_with_rationale. Gate mechanics are #187's (spec C-002); named in Follow-up work. | Follow-up work |

Docs checks after this pass: `check_docs.py all` ok; cspell 0 issues; markdownlint 0 findings on the
changed files.
