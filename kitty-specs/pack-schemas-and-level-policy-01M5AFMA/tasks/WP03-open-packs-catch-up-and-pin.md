---
work_package_id: WP03
title: Open-packs layout catch-up, pin and re-vendor
dependencies: []
requirement_refs:
- C-004
- FR-011
- FR-017
- NFR-001
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
subtasks:
- T012
- T013
- T014
- T015
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/adapter-out-yaml/
create_intent:
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksCorpus.java
execution_mode: code_change
model: sonnet
owned_files:
- gradle.properties
- service/adapter-out-yaml/build.gradle.kts
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksRoundTripTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/OpenPacksCorpus.java
- .github/workflows/service.yml
- packs/internal/**
- packs/qa/**
role: implementer
tags: []
tracker_refs: []
---

# WP03 — Open-packs layout catch-up, pin and re-vendor

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context: `spec-kitty charter context --action implement`.
The charter wins over this prompt.

## Objective

Operator ruling R11 has three parts:

- **Catch up to open-packs `main`'s layout.** `built-in/` moved under `packs/built-in/` in
  `cb00e82`.
- **Pin the corpus to the URN-only migration,** PR spec-kitty/spec-kitty-open-packs#52, head
  `0b19a8347c4e97593fa49abbf62a440b39c38f04` ("0b19a83"). The pin stays on that head until the
  operator merges #52, then moves to the merge commit. That later move is not part of this WP.
- **Refresh the packs this repository vendors** (`packs/internal`, `packs/qa`).

There must be one source of truth for the pin (plan P6, "Open-packs pin").

Read first:
- [plan.md](../plan.md): "Open-packs pin" and IC-02;
- [research.md](../research.md) R7 (the round-trip overlay experiment: only the layout check, the
  walk roots and the `internal` diagnostics row fail on the new layout);
- spec FR-011 and FR-017.

## Environment

- The default open-packs checkout is the sibling `../spec-kitty-open-packs`. In this environment
  that is `/home/user/spec-kitty-open-packs`, the clone on branch `feat/urn-only-fragment-endpoints`
  at the pinned head.
- JDK 25: `export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH`.
- Maven Central sometimes answers HTTP 429. Retry with `--max-workers=2`, and never touch the
  verification metadata.

## Branch Strategy

- Planning base and merge target: `feat/pack-schemas-and-level-policy`.
- Lane worktree from `lanes.json`.
- Implement with:
  `spec-kitty agent action implement WP03 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.

## Subtasks

### T012 — Pin plumbing and the `OpenPacksCorpus` helper

**Steps**:

1. In `gradle.properties`, add `openPacksRef=0b19a8347c4e97593fa49abbf62a440b39c38f04`, with a
   comment that it is the single source of truth for the open-packs pin, read by Gradle and by CI.
2. In `service/adapter-out-yaml/build.gradle.kts`:
   - read `providers.gradleProperty("openPacksRef")`;
   - validate it **lazily**, in the task's `doFirst`, never at configuration time, which would
     break every Gradle invocation and the configuration cache. Fail the task if the value is
     missing, blank or not 40 lowercase hex characters;
   - pass it as `systemProperty("charter.openPacksRef", …)` **and** declare it a task input with
     `inputs.property("openPacksRef", …)`, so a pin change is never answered from the cache.
3. Create the package-private helper `OpenPacksCorpus` in the test sources. It holds the shared
   logic for WP03 and WP08:
   - `Path root()`: from `charter.openPacksDir`. It fails with the existing "never skips" message
     when the directory is missing, and requires `<root>/packs/built-in` (the new layout).
   - `void assertPinned()`:
     - the `charter.openPacksRef` system property is 40-hex;
     - `git -C <root> rev-parse HEAD` equals it;
     - `git -C <root> status --porcelain --untracked-files=all` is empty.

     The failure message prints the exact fix, for example
     `git -C <root> fetch origin <pin> && git -C <root> checkout <pin>`. Use `ProcessBuilder`,
     check exit codes, and time out after 30 seconds.
   - `List<Path> yamlFiles()`: every `*.yaml` under `packs/`, sorted.
   - `static final Map<String, List<String>> DIAGNOSTICS`: the expected-diagnostics table, moved
     here from `OpenPacksRoundTripTest:45-58`, so WP08 reuses it without editing a WP03 test.
   - `List<PackLocation> packs()`:
     - `built-in` (`BUILT_IN`) at `packs/built-in`;
     - every other directory under `packs/` as `ORG`, sorted by name;
     - files directly in `packs/` (`README.md`, `built-in.source.yaml`) are not packs.

### T013 — `OpenPacksRoundTripTest` on the new layout (red-first)

**Steps**:

1. **Red.** Run the current test against the pinned checkout:
   `./gradlew :adapter-out-yaml:openPacksRoundTrip -PopenPacksDir=/home/user/spec-kitty-open-packs`.
   It fails with "No spec-kitty-open-packs checkout" because `built-in/` is gone. Record that
   output.
2. Change the test to use `OpenPacksCorpus`.
   - Call `assertPinned()` first, in every test method or in a static initializer. It never skips:
     no `assumeTrue`.
   - Walk `yamlFiles()`, replacing the old `["built-in","packs"]` roots.
   - Build the packs from `packs()`.
   - Replace the weak `hasSizeGreaterThan(100)` with an exact count. All 516 YAML files under
     `packs/` at the pinned head were counted by the research probe; re-count them, and keep the
     number in one constant shared with WP08 through `OpenPacksCorpus`.
3. **`DIAGNOSTICS` table** (`:45-58`):
   - `internal` now declares `pack_level: org`, so its expected list becomes empty.
   - `qa`, `cave-kitty` and `event-driven` still have no `pack.yaml`, so they keep the "has no
     pack.yaml" text.
   - Verify each row against the actual output; do not guess.
4. **Green.** The round-trip passes: bytes, data, identity and domain, for every file and every
   pack.

### T014 — CI reads the pin from `gradle.properties`

**Steps**:

1. In `.github/workflows/service.yml`, remove the hard-coded `OPEN_PACKS_REF` env value (`:47-48`).
2. Add a step before the open-packs checkout (`:62-67`). It reads `openPacksRef` from
   `gradle.properties` with `grep` and `cut`, then:
   - fails with `::error::` if the value is empty or does not match `^[0-9a-f]{40}$`;
   - writes `ref=<value>` to `$GITHUB_OUTPUT`.
3. Use that output as the checkout `ref`, and keep `path: .open-packs`.
4. Keep the existing `-PopenPacksDir` argument. Gradle reads `openPacksRef` itself.
5. Validate the YAML syntax locally with
   `~/.venv-sk/bin/python -I -c 'import sys; from ruamel.yaml import YAML; YAML(typ="safe").load(open(sys.argv[1]))' .github/workflows/service.yml`.
   `actionlint` is not installed. Note that in the evidence; do not install tools.

### T015 — Re-vendor `packs/internal` and `packs/qa`, and the local governance check

**Steps**:

1. For each pack, replace the directory with the pinned copy. `rsync` is not installed, so use
   `rm -rf packs/<pack> && cp -a <pinned checkout>/packs/<pack> packs/<pack>`, **but**
   first read the local `PROVENANCE.md` and `README.md`. These files differ from upstream (research
   R7), so keep any local section the upstream copy lacks, merged by hand.
2. Update each `PROVENANCE.md` with the upstream commit (`0b19a83…`) and the date.
3. **Local governance check.** These packs are this repository's own governance inputs
   (`.kittify/config.yaml:16-25`). With the installed CLI
   (`export PATH=~/.venv-sk/bin:$PATH`, spec-kitty 4.0.0rc8), run:
   - `spec-kitty charter context --action plan --json`: exit 0, and no error mentioning
     `internal` or `qa`;
   - `spec-kitty doctor charter-packs --json`: no new errors or dangling endpoints compared with
     the same command on `main` before the re-vendor. Capture both outputs.

   Record the outputs in the evidence. If either regresses, stop and report to the orchestrator.
4. Review `git diff --stat packs/`. The expected change is the fragments (URN endpoints, no header
   fields), `internal/pack.yaml` and the provenance files. Anything else needs a one-line note.

## Definition of Done

- [ ] The pin lives only in `gradle.properties`. Gradle validates it as a task input, and CI
      reads it and fails on a blank or malformed value.
- [ ] The round-trip passes on the new layout at the pinned clean HEAD. It was shown red before
      the change.
- [ ] An exact YAML-file count replaces `hasSizeGreaterThan(100)`.
- [ ] `packs/internal` and `packs/qa` are re-vendored with their provenance, and the
      local-governance check is recorded and clean.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Risks

- **The pin points at an unmerged PR (K1).** The charter PR stays blocked until #52 merges, and
  then the pin moves to the merge commit.
- **A dirty sibling checkout now fails the build locally.** That is intended; the failure message
  gives the exact fix.

## Reviewer guidance (opus, reviewer-renata)

- Try to make the pin check pass silently: a blank pin, a dirty tree, a different commit. Each
  must fail.
- Check that the `DIAGNOSTICS` rows match the real output, and that `assertPinned` runs before
  anything else.
