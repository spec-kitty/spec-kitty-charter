# Tracer: tooling-friction

One entry per finding: `YYYY-MM-DD · actor · <text>`.

Tooling this mission touches: Gradle (java-test-fixtures, Spotless, Checkstyle, NullAway), the whole-pack round-trip gate, GitHub Actions routing, the spec-kitty mission CLI (single_branch topology), and the charter generator.

---

2026-11-11 · claude · The tracer files were not seeded at planning; they are seeded during implementation (after WP01 and WP08), and the earlier entries below are reconstructed from the session.

2026-11-11 · claude · java-test-fixtures: the testFixtures configuration does not inherit the Spring Boot BOM, and NullAway failed to initialise on compileTestFixturesJava. Fixed in build-logic by adding the BOM to testFixturesImplementation, jspecify to testFixturesCompileOnly, and disabling NullAway for compileTestFixturesJava. Suggested fix: a convention plugin that configures testFixtures like test.

2026-11-11 · claude · Review recording on WP01: an earlier orchestrator `move-task --to in_review` recorded the claim as 'user', so the reviewer's approval was refused. Worked around by moving back to for_review and claiming with `agent action review --agent reviewer-renata`. Suggested fix: move-task to in_review should refuse without --agent, or inherit the reviewer identity (same family as the #368 friction).

2026-11-11 · claude · `spec-kitty charter generate` rewrote charter.yaml and synthesis-manifest.yaml and dropped unrelated procedures and styleguides, so its output was reverted and the generated copies were hand-edited. The hand edit then got the clause order wrong; the WP08 review caught it (fold 2026-11-11). Suggested fix: generate should be a no-op for unchanged sources, or show a diff before writing.

2026-11-11 · claude · `move-task --to for_review` after a rejection refused until every subtask was re-marked done: the move back to planned reset the subtask state. Same as the 2026-11-04 entry in test-tidy-mockito-refusal-codes.

2026-11-11 · claude · Every status move auto-commits on the mission branch; the stop hook then flags each unpushed status commit as Unverified until it is pushed. Many tiny commits; history cleanup before the PR squashes them.

2026-11-11 · claude · Parallel WPs on a single_branch mission: work packages are implemented in separate git worktrees (wip/WPnn) and cherry-picked, because a single_branch mission has one write checkout and the 4-core host takes two Gradle builds at once. WPs that touch the shared example packs (WP02, WP03, WP04) need serial integration; pack-manifest.yaml hashes must be regenerated after merging example-pack edits.

2026-11-11 · claude · The whole-pack round trip writes through DomainRepositoryService, which never writes policy.yaml, so the gate keeps `policy` in expected-red `kinds` (filed as #476).

2026-11-11 · claude · WP03: PackSchemas and PackFileKind are package-private, so the coverage check re-reads the bundled schema resources and approximates the Markdown parsing. Suggested fix: a test-visible schema/file-kind catalogue.

2026-11-11 · claude · WP03: Error Prone (StringSplitter, EscapedEntity) and Checkstyle (ModifiedControlVariable) forced rewrites; the graph rule that `impacts` is allowed only on `relation: impacts` surfaced only through load findings. Gradle runs take minutes, so they were run in the background.

2026-11-11 · claude · WP01 slipped past review with 3 HexagonalArchitectureTest violations in the testFixtures ExamplePacks (missing @NullMarked, dependency on infra.storage.PackLocation): the WP01 verification ran :packs and the deployable classpath test, not the whole :deployable:test. Fixed in WP03. Lesson: review runs the owning modules' full suites.

2026-11-11 · claude · WP03: raised refusal codes `registration_timed_out` and `pack_storage_full` had no scenario and only synthetic unit tests. No configuration property sets the storage limit or the registration timeout, so ExampleCoverageTest reaches them by replacing beans (a FreeSpaceCheck volume reporting 1 MiB free; a job held at the gate past the 90 s deadline). Suggested fix: configurable limits.

2026-11-11 · claude · WP03: charter.yaml and org-charter.yaml cannot sit in an example pack: the whole-pack load rejects files it does not model, so the coverage check skips those two schemas (derived from the loader, not a list).

2026-11-11 · claude · WP04: the `open-packs` tag also hid the moved tests from `:packs:test`, so the tag had to go with the move; Error Prone fails on an unused helper; targeted Gradle runs take ~2 min, and counts were read from test-results XML.

2026-11-11 · claude · The session clone was shallow: the contract rehearsal's compatibility check refuses a shallow clone, so reproducing CI's release-rehearsal red needed `git fetch --unshallow --tags`. The release-rehearsal red on PR #474 (lintContract, ApiRoot `_links` without x-source, only in the synthetic release) passes on main. Root cause: vacuum's 500 ms JSONPath lookup timeout silently reads a timed-out lookup as not found on a loaded runner; main already raised it to 10 s (174353610). Fixed by the planned rebase on main.

2026-11-11 · claude · WP03: `:packs` test sources compile only under `:packs:test`, so a `:deployable`-only run missed a broken caller of the changed ExamplePacks API.

2026-11-11 · claude · Integration of WP02–WP04: no textual conflicts, but the round trip refused WP03's `impacts` graph edge (RelationshipMerge; filed #477), and Spotless stopped the first run before the tests ran, leaving stale results on disk. Pack-manifest hashes were regenerated through a throwaway test calling PackManifests.recompute: there is no task for it. Suggested fix: a Gradle task that recomputes example-pack manifests.

2026-11-11 · claude · `move-task --to for_review` refuses a WP implemented in a side worktree and cherry-picked: no claim base is recorded when the claim is a plain `move-task --to in_progress`, so the for_review gate sees "no implementation commit since claim". Forced with a note. Suggested fix: let move-task record the claim base, or accept a commit range.

2026-11-11 · claude · Review of WP02–WP04: `build/test-results` keeps XML from earlier `openPacks*` task runs, some failing, which inflates naive counts; count per task. The `agent action review` output prints the reject command before the approve one, which misleads.
