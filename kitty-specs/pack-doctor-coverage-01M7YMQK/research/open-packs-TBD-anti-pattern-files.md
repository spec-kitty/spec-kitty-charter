# Draft: `open-packs#TBD-anti-pattern-files` (not filed)

Drafted for plan D12 (WP10; rulings R-p and R-q) under C-006. The orchestrator files it in
`spec-kitty/spec-kitty-open-packs` at landing, with WP09's other drafts, and links it from spec-kitty-charter#154.
Evidence is from a read-only look at `/home/user/spec-kitty/spec-kitty-open-packs` at `fb71367`.

> **Adopt the `anti_pattern` file kind of the packs contract (ADR-0052 d8)**
>
> **Why.** The charter now holds an anti-pattern as its own file, `anti_patterns/<id>.anti-pattern.yaml`
> (spec-kitty-charter#154, the file kind only). A preset `must_have` counts only when a pack in the declaring pack's
> own closure holds it as a file (ruling R-l), so an `anti_pattern` graph node alone no longer satisfies one.
>
> **What changes in open-packs.**
>
> - `scripts/ci/validate_pack_schemas.py` treats `anti_pattern` as a graph-only kind:
>   `MANIFEST_EXEMPT_NODE_KINDS` (lines 84-88) and the derived-manifest note (line 447) leave it out of the manifest,
>   and the held set (lines 1181-1183) takes it from graph nodes. Once the pinned contract and jar carry the kind, an
>   anti-pattern **file** is a manifest constituent and is held; a node alone is only declared. Retire the Python
>   paths with the other rules (`TBD-retire-python-rules`) or align them at the repin.
> - `docs/artefacts/index.md:36` ("Anti-pattern | inside other artefacts / the DRG | not a standalone file") and
>   `docs/guides/validate-a-pack.md:117` ("Actions and anti-patterns have no file") describe the old model.
> - No open-packs preset activates an anti-pattern today (no `activated_anti_patterns` in `packs/`), so nothing is
>   refused at the repin by this change alone.
> - The 13 file-less nodes (`packs/architecture/drg/fragment.yaml`, `packs/refactoring/drg/fragment.yaml`) stay
>   accepted for now. They are refused only after the migration (ADR-0053 d31), which is the curation issue
>   `open-packs#TBD-anti-pattern-curation` (drafted in `docs/plans/triage-2026-11-12-rescopes-applied.md`, "Left to
>   file"): it can start once this release is pinned.
>
> **Done when.** Open-packs pins the charter release that carries the kind; its checker and docs treat an
> anti-pattern file as held and listed in the manifest; the curation issue is unblocked.
