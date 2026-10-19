---
affected_files: []
cycle_number: 1
mission_slug: action-context-reserved-surface-01M5AFEK
reproduction_command:
reviewed_at: '2026-10-19T19:00:24Z'
reviewer_agent: claude
wp_id: WP05
---

# WP05 review feedback, cycle 1 (reviewer-renata, architecture and documentation-fidelity lens)

Commit under review: 90fe905. Verdict: **changes requested**. The substance is strong. ADR-0018 D1 to D14 match research §3 and the shipped contract and code: the walk in `ActionContextWalk`, depth 0..2 with default 2 and no maximum on the response, hops/via, the fingerprint domain tag and presence byte in `ProjectConfigSources`, the token rules in `GuardWiring` (including `spring.application.json` on argv or `-D`), the 401/403/`WWW-Authenticate` responses, and `POST /packs/refresh` answering `PackListing`. The supersession list matches D14. The operator rulings are marked. The register matches §5, with DIV-104 withdrawn and DIV-110 narrowed. The service-contract page records the #95 decision and its rationale. Checks are green: `check_docs.py all`, markdownlint (0 errors), cspell (0 issues) and `verifyAll`.

What remains is fidelity drift in the CHANGELOG, follow-up issues that are filed but not cited, and a few amendment-convention and SC-003 leftovers.

## Issue 1: the CHANGELOG still has statements the new section contradicts (SC-003)

The new "Action context and the seam surface" section is accurate. Earlier bullets in the same `1.0.0-SNAPSHOT` entry now say the opposite:

- Line 77: "The depth of the walk is echoed" is a truncated title. The real title was "The depth of the walk is echoed, and this version answers depth 1 only". Use the real title and mark it replaced.
- Line 78: "A depth this version does not answer is a bad request, not a charter refusal" no longer exists. It is now "A depth outside 0 to 2 is a bad request, not a charter refusal". Record the rename.
- Line 83: `service.feature` "the three headers on an answer and on a refusal". A refusal no longer carries the fingerprint. Annotate this bullet, or reword it with a pointer to the new section.
- Lines 128-129: Scenario "A bad request carries the headers too" ... "carries the three headers". That scenario is now "A bad request carries the contract version and the worktree, not the fingerprint", and it asserts that there is no fingerprint. Record the rename and the change in behavior.
- Line 25 (the `DIRECTIVE_042` scenario, "A directive scoped by both an action and its mission type is answered once") and line 103 (the derivation rule "an artifact in both scopes keeps its action entry") describe things that are gone. Add "(replaced, see below)" to each, as line 77 does.
- `errors.feature` (3 to 9 scenarios, 13 with examples) and `service.feature` (6 to 10): give the counts, as you did for the other features.
- Reflow the overlong lines (77-78, 163, 215) to the file's wrap width of about 100 columns.

## Issue 2: cite the follow-up issues that are filed

The follow-ups exist: #119 (generator `Problem1`/`PackListing1`), #120 (pin that the fingerprint digests exactly the files the load reads, the S6 follow-up) and #121 (refuse a placeholder that resolves the token from the command line).

- CHANGELOG "Other": "a generator-side follow-up is planned" should say "#119".
- ADR-0018 Risks, "Contract tooling": cite #119.
- ADR-0018 Risks, "A placeholder split": cite #121, keeping #70 as the launcher review. Do the same in `service-lifecycle.md` (the residual-risks bullet) and `open-questions.md` Q26.
- ADR-0018 Risks: add the S6 gap (no test yet pins "loaded set = digested set", the fingerprint inputs of D8 and D11) with #120.

## Issue 3: an ADR-0011 inline annotation garbles the original sentence

In `docs/adr/0011-...md:90`, the annotation sits mid-sentence: "Every answer carries *(the fingerprint on 200 answers only, per Amendment 2026-10-19)* the `Charter-Contract-Version`, ...". That changes how the original decision reads. The amendment then says "The original text above is unchanged", which is not quite true.

Move each annotation to the end of its bullet or sentence, as a separate italic note, following ADR-0002's pattern (`*Narrowed by Amendment 2026-10-18: ...*`). Do the same for lines 75 and 84 of ADR-0011. ADR-0009:72 and ADR-0016:87 are already at the end and are fine.

## Issue 4: follow the repository's amendment convention in Status and in the index

`docs/adr/README.md` says a dated amendment sits "under the record's Status", and the index shows it in the Status column (ADR-0002: "Accepted, with Amendment 2026-10-18"; ADR-0005: "Proposed, with Amendment 2026-10-19"). Keep the `## Amendment (2026-10-19)` sections where the WP prompt put them, and also:

- add one line to the `## Status` of ADR-0009, ADR-0011 and ADR-0016: "Amended 2026-10-19 by [ADR-0018](...): see [Amendment](#amendment-2026-10-19)";
- change the index Status column for 0009, 0011 and 0016 to "Accepted (provisional), with Amendment 2026-10-19".

## Issue 5: SC-003 leftovers found by grep

- `docs/plans/roadmap.md:33` (step 3) still says "at both load depths". ADR-0016 is now amended to "at depths 0, 1 and 2". Align it.
- `contracts/lint/README.md:96` ("ADR-0011: every response carries them") and `contracts/_shared/VENDORED.md:16` ("ADR-0011: every response carries them") give a rationale ADR-0018 D11 superseded. The headers are still declared on every response, but the fingerprint is sent on 200 only. Reword both to cite ADR-0018 D11. Both files are outside `owned_files`, so record them as out-of-map edits with this rationale in the history note.

## Minor (fix while you are there)

- `docs/api/README.md:23-26` reads "pack listing (...), action-scoped context (...) and service info (...) and pack refresh (...)". Make it one list: "..., service info (...) and pack refresh (...)".
- Rewrap the long lines in `service-lifecycle.md` (the Host/Origin bullet and the version-handshake bullet) and `contract-and-dtos.md:105-106`.

## Anti-pattern checklist

1. Dead code: N/A (docs only).
2. Synthetic-fixture test: N/A.
3. Silent empty return: N/A.
4. FR coverage: N/A for a docs WP. The FRs are covered by WP01 to WP04's scenarios. SC-003: **FAIL** until issues 1 and 5 are fixed.
5. Frozen surface: PASS. No runner-B page or file is touched, and no `docs/changelog/` entry is added.
6. Locked decision: PASS.
7. Shared-file ownership: the CHANGELOG, `service-contract.md`, ADR-0009 and the ADR index are shared with runner B (research §6). Keep the coordination note in the move-task reason. ADR-0018's number may be renumbered at the landing rebase.
8. Production fragility: N/A.
