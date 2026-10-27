# WP07 review 1 (reviewer-renata) - changes requested

Docs checks pass (check_docs all: 0 findings; markdownlint/cspell clean on the lines this commit adds; remaining findings are pre-existing MD060 separator rows and AFMA in untouched lines). DIV-010 line is identical in both register forms; index row 0039 is in order. Code claims about hooks, path rule, read/upsert order, orphan removal, whole-pack and digest order verified against lane-f. Three accuracy/consistency gaps block approval:

**Issue 1 (MEDIUM) - ADR-0039:98 vs ADR-0036 per-kind table.** The skill row says the `body_path` file "compares as text, byte for byte". Every pinned body is a `*.skill.md`, and ADR-0036's `*.md` row (which names `*.skill.md`) says "body as text (LF, one final newline)". ADR-0039 supersedes only the `*.skill.yaml`/`*.glossary-pack.yaml` row. Fix: state in Decision 8 that the `*.skill.md` entry of ADR-0036's `*.md` row is superseded for a file named by `body_path` (byte for byte, no LF/final-newline normalization), or align the claim.

**Issue 2 (MEDIUM) - ADR-0039:135 and changelog:43-44 omit the built-in pack.** The built-in pack holds `glossary_packs/spec-kitty-core.glossary-pack.yaml`; glossary_pack is newly bound, so `PackFileSet.digested` now lists it and the built-in pack's fingerprint/ETag also change once (application.yaml: every registered pack, built-in included, is served). Fix: name `built-in` alongside `qa`, `internal`, `event-driven` (or say "every pack holding a glossary pack or a skill").

**Issue 3 (MEDIUM) - ADR-0039:100-103 does not reconcile with ADR-0017.** ADR-0017 is the schema-import record and lists "Import core's hand-written YAML schemas ... they have drifted. Not chosen." ADR-0039 imports `skill.schema.json` from core's hand-written `skill.schema.yaml` and authors `glossary-pack` by hand, citing only ADR-0015. Fix: add ADR-0017 to `related` and one sentence on why this is consistent (amendment R20: schemas are edited here; core exports no model schema for these kinds; pinned at core 581b5c46da).

**Issue 4 (LOW) - ADR-0039:104-105.** "schema name and file token are `glossary-pack` ... the token stays `glossary_pack`" reads as a contradiction. Say "the kind token (`ArtifactKind.GLOSSARY_PACK`, URN) stays `glossary_pack`".

**Issue 5 (LOW) - ADR-0039:71-73.** The reason list omits `is empty` and `is not a valid path` (Companions.resolve). Either list them or say "for example".

**Issue 6 (LOW) - ADR-0039:92.** The ask to record rows is in ADR-0036's "Per-kind table" preamble, not Decision 6. Re-point.

**Issue 7 (LOW) - ADR-0039:69.** "The read, the upsert and the fingerprint use this one rule": the fingerprint applies the path rule but not the one-owner refusal (it de-duplicates, per Decision 7). Narrow the sentence to the path rule.
