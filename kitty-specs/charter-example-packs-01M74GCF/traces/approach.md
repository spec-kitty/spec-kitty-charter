# Tracer: approach

One entry per finding: `YYYY-MM-DD · actor · <text>`.

Approach (spec/plan): build charter-owned example packs (built-in, org-base, project) in a `java-test-fixtures` source set of `:packs`, served by `ExamplePacks`; move every open-packs-reading test onto them, guarded by a coverage check; remove the open-packs checkout from required CI and keep a nightly non-blocking drift watch.

---

2026-11-11 · claude · Example packs were built by pruning a copy of open-packs content with scratch scripts (build_examples.py, prune.py) and then fixing self-test failures. Faster than hand-authoring, but it imports open-packs quirks (README, org-charter.yaml) that had to be removed.

2026-11-11 · claude · WP02 found empty kinds in the built-in example and filled them (a skill, a step enhancement, graph shards, pack-manifest.yaml) rather than listing them: the expectation lists stayed the ratchet they are meant to be.
