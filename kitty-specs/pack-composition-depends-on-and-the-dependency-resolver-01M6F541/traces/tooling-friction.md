# Tracer: tooling-friction

One entry per finding: `YYYY-MM-DD · actor · <text>`.

---

2026-11-03 · opencode (GLM 5.3, java-jenny) · WP02 prompt cites Gradle module paths as `:service:packs:*`, but the build exposes `:packs`, `:deployable`, `:kernel`. Gate commands had to be reconstructed from the actual project layout. Disposition: workaround; worth normalising module paths in generated WP prompts.

2026-11-03 · opencode (GLM 5.3, java-jenny) · spotlessCheck rejected the first draft of the new test files on import ordering; `spotlessApply` fixed it. Routine, no action needed.

2026-11-03 · opencode (GLM 5.3, java-jenny) · `spec-kitty agent tasks add-history` lacks `--owned-checkout`: it resolves the mission from the main repository root, where this mission's branch is not checked out, so every invocation from the owned worktree fails with MISSION_NOT_FOUND. mark-status and move-task accept `--owned-checkout`; add-history does not. Disposition: workaround — notes written through the same emit_inner_state_changed seam the CLI uses; candidate gap for the tooling backlog.

2026-11-03 · opencode (DeepSeek V4.1 Flash 1017) · Accept gate vs implementation-ahead-of-the-loop: `spec-kitty accept` refused with IMPLEMENTATION_MISSING for all five WPs, because their evidence window opens at the finalize base (be7e32ba) while the implementation had been committed before the WPs were claimed. The gate is correct; the failure is the earlier bypass surfacing late. Recovery required either replaying each WP's owned files into its window (chosen) or an operator-documented exception, and `--attest-no-implementation` would have been a false attestation. Inspection target: the loop cannot distinguish "work done ahead of the state machine" from "no work done"; a diagnostic that says which commits touch the WP's owned files outside the window would turn a late hard stop into an early, actionable warning.
