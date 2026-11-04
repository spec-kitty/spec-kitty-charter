---
affected_files: []
cycle_number: 1
mission_slug: ci-sonar-nightly-checkstyle-complexity-01M6HQ46
reproduction_command:
reviewed_at: '2026-11-04T08:23:19Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP01
---

# WP01 review (reviewer-renata) - changes requested

Everything else checks out (details at the end). One register entry breaks the register convention that WP02's checker enforces.

## Issue 1 - `file:` field and `files` regex of the parity-test entry are not the file (FR-002, data-model "file", WP01 T003)

**Where:** `config/checkstyle/suppressions.xml:86-94` (the `rulesetCases` entry).

**Problem:**
- The comment reads `file:   service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/*ValidationParityTest.java (one file matches)`. That is a glob plus a remark, not a path. The real file is `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineValidationParityTest.java`.
- `files="[A-Z][a-z]+ValidationParityTest\.java"` is a pattern, not the escaped file name. T003 says "`files` is the escaped file name; widen it to a path fragment only if the name is ambiguous". It matches one file today, but it will also match any future `XxxValidationParityTest.java` and silently exempt that file's `rulesetCases` method from MethodLength. A shrink-only register must not do that.
- The header defines `file:` as "the file it applies to (also the `files` attribute)". `data-model.md` says the file "matches exactly one source file". WP02 T006 raises `MISSING_FILE` when "the comment's `file:` does not exist under the root", and it scopes liveness per module by the `file:` path. So this entry will fail WP02's check as soon as WP02 lands. The WP prompt says "WP02 builds the check that enforces this, so follow it exactly".

**Required fix:**
- Set `file:` to the literal path `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineValidationParityTest.java`.
- Set `files="DoctrineValidationParityTest\.java"`.
- The ban on that word applies to the prose `reason:`, which is already clean. A file name is an identifier and has to be quoted exactly. If the operator's terminology rule really does cover file paths, escalate to the operator; do not encode the path as a glob.
- Keep the entry order by file path. `DoctrineValidationParityTest` stays where it is, before `DomainRepositoryServiceTest`.

## Verified (no action needed)

- **checkstyle.xml:**
  - CyclomaticComplexity 15 with switchBlockAsSingleDecisionPoint=true.
  - NPathComplexity 1000.
  - MethodLength 60 with countEmpty=false.
  - NestedIfDepth 1.
  - SuppressionXpathFilter on `${config_loc}/suppressions.xml`, optional=false.
  - Top-level SuppressionFilter kept.
  - ADR-0056 comment present.
- **Register basics:** XPath experimental 1.2 DTD; 15 entries; `Size: 15 entries.`; #367 cited; every entry has a method-scoped `query`; no overloaded method names; `[/\\]ReleaseGate\.java` correctly disambiguates from `CheckReleaseGate.java`.
- **Empty-register run** (`checkstyleMain checkstyleTest --continue`, register restored afterwards): it reports exactly 15 methods, and each entry's `checks` equals the set of rules that method breaks. No ComposedBundle or ActivationConfigSchemaParityTest violation remains.
- **T017 refactor (039814ee):** behavior-preserving. List is checked first and non-map returns early; the map branch keeps the same check order, messages and recursion. In `reaches`, the ref-equals-self check, the `followed.add` check and the target lookup are unchanged. The only reorder (List checked before Map) differs only for an object that implements both, which does not occur. No test edited. `:contracts:test` 251/251 and the parity test 29/29 pass with `--rerun-tasks`.
- **`verifyAll`:** exits 0.
- **Non-vacuity:** planted methods in a scratch file (deleted afterwards) fail CyclomaticComplexity (17), NPathComplexity (65,536 and 2,048), MethodLength (66 lines) and NestedIfDepth (depth 2). Note: with max=1, a single if-in-if is allowed; the T004 wording "a nested if inside an if" needs three levels to fire. That matches the ruled limit, so it is not a defect.
- **Scope:** only the four owned files changed; both commits carry the Stijn Co-Authored-By; no model identifiers; no new plugin or library.
