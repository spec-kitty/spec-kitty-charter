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
