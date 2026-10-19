---
affected_files: []
cycle_number: 1
mission_slug: action-context-reserved-surface-01M5AFEK
reproduction_command:
reviewed_at: '2026-10-19T17:53:23Z'
reviewer_agent: claude
wp_id: WP03
---

# WP03 review, cycle 1: changes requested

Verified independently:
- `verifyAll --rerun-tasks` is green: 117 charter-service tests, 0 failures.
- The red run at 4d7f7b5 fails on assertions: 23 of 113 scenarios/tests in charter-service and 4 of 34 in ConsistentLoadTest.
- The seam is honest: it uses the real adapters and rewrites a real temp file, and ConsistentLoad computes both digests. The negative control is present.
- Each 200 carries exactly one header, and no non-200 carries one. CharterHeaders no longer digests.
- The x-refusal map is correct for all nine codes.
- ReservedRefusalCodesTest excludes classes by name pattern, and the planted-throw evidence is recorded.
- The ArchUnit seam rule has a plant fixture that fails when planted.
- Scope is clean: no runner-B file, GraphDocumentReader or CHANGELOG changed.

Only one item blocks approval.

**Issue 1 (blocking): the pack_unreadable scenario pins snakeyaml's own parser message in a language-neutral contract scenario.**

`contracts/charter/scenarios/errors.feature:68` asserts the exact detail
`"built-in/broken.graph.yaml is not valid YAML: while parsing a flow node"`.

- The part after the colon is `firstLine(e)` of a snakeyaml-engine exception. snakeyaml wrote that text, not the charter.
- `scenarios/README.md` says the scenarios are language-neutral and that any other implementation may bind the same steps. Another implementation would use a different YAML parser, so it could never pass this scenario.
- A snakeyaml-engine upgrade could also break the contract suite without any change to the charter's behavior.
- This is the only scenario that pins third-party text. Every other exact detail in the suite is a sentence the charter wrote.
- The existing unit test already avoids this: `FileSystemCharterPackRepositoryTest:70` uses `startsWith("qa/tactic.graph.yaml is not valid YAML: ")`.

Fix (least risky; do NOT change GraphDocumentReader):
- Its wording is existing behavior, pinned since cae6c85 and covered by adapter tests. Rewording it would widen this WP for no gain.
- Add one step, `Then the field "<field>" of the response body starts with "<text>"`. Put it in CharterSteps, or in ReadConsistencySteps if you prefer, and list it under "Step vocabulary" in `scenarios/README.md`.
- In the pack_unreadable outline, replace the exact-detail line with
  `And the field "detail" of the response body starts with "built-in/broken.graph.yaml is not valid YAML"`.
- Keep the `apart from "detail"` exact-body check and the no-fingerprint check.
- The step pins the part the charter wrote: it names the shard relative to its pack and no host path. It leaves the parser's text free.
- Record the change in history, together with the scenario-text change in the contract change list for WP05.

**Non-blocking (optional, no action required for approval):**

a. After a successful load, `ConsistentLoad.load()` takes the second digest with a plain `sources.fingerprint()`. If an input became unreadable during the load, the read answers that refusal (`pack_unreadable`), not `stale_input`.
- This follows the algorithm the WP prompt prescribes for step 3, so it is not a defect.
- By D11's reasoning, a file that the first digest could read and the second could not has changed while the read was answered. Calling `digestOrNull()` there as well would make the two paths symmetric.
- If you change it, add one ConsistentLoadTest case for it.

b. The `x-refusal` map has no lint shape rule. The steps and the `@After` cross-check read it, so it is enforced in practice. A spectral shape rule with a planted fixture could follow later.
