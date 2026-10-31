---
work_package_id: WP01
title: 'ADR-0046: the service release line, and the plan updates (PR 1)'
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-004
- FR-005
- C-002
- C-006
- C-007
- SC-001
planning_base_branch: issue-241-71-service-release-native
merge_target_branch: issue-241-71-service-release-native
branch_strategy: Planning artifacts for this mission were generated on issue-241-71-service-release-native. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-241-71-service-release-native unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
history: []
agent_profile: architect-alphonso
authoritative_surface: docs/
create_intent:
- docs/adr/0046-service-release-line-and-native-packaging.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/adr/0046-service-release-line-and-native-packaging.md
- docs/plans/open-questions.md
- docs/plans/milestones.md
- docs/plans/roadmap.md
role: architect
tags: []
tracker_refs: []
---

# WP01: ADR-0046, the service release line, and the plan updates

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `architect-alphonso`
- **Role**: `architect`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Record the operator's rulings D1 to D4 as an accepted ADR, the durable home of the service release line and
its asset contract. Then mark open question 5 decided and update the plan documents. This is PR 1 of two
stacked pull requests (C-006). It changes documentation only.

Implementation command:
`spec-kitty agent action implement WP01 --agent claude --mission service-release-native-01M693YF`

## Context

- **Inputs:**
  - Rulings: `decisions/` D1 to D7, 2026-10-31; spec-kitty/spec-kitty#6117, comment 6544221340.
  - `research.md` R1 and R2.
  - `contracts/service-release-assets.md`, the asset table, copied verbatim into the ADR.
- **ADR style:** follow the house ADR form; copy the frontmatter and sections of ADR-0041 or ADR-0022:
  - frontmatter with `related:` slugs that exist as pages under `docs/`, and `audience`;
  - the sections Status, Context, Decision (numbered), Consequences (Positive, Negative, Risks),
    Alternatives considered, Revisit when, References.
- **Numbering:** ADR-0046 was free on `main` and in every open PR on 2026-10-31. Recheck at writing time
  (cloud-session-setup.md, "ADR numbers collide").
- **ADR-0011:** do not edit it. Its line 131 risk ("A native image is not ready in time") is answered by
  this ADR; cite it under References.

### Subtask T001: Write ADR-0046

**Purpose:** the one record of how the service is versioned and released (FR-001 to FR-004, SC-001).

**Steps:**

1. Create `docs/adr/0046-service-release-line-and-native-packaging.md`. Status: **Accepted 2026-10-31**,
   decider Stijn Dejongh (operator), with the rulings cited.
2. Context:
   - only contract tags exist;
   - #234 needs a release trigger;
   - core pins services by tag and asset (core ADR 2026-10-29-1, `kernel/service_pin.py`);
   - open question 5;
   - ADR-0011's native risk.
3. Decisions:
   1. **The tag line** is `service-vX.Y.Z[-pre]`.
      - The regex is `^service-v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$`, which is core's `_TAG_RE`.
        `service-v0.0.0` is refused.
      - The line is separate from `contract-charter-v*` (ADR-0022): neither moves the other.
   2. **The version**:
      - The service's own SemVer is set by `version` in `gradle.properties`; the tag must equal it.
      - A major release is a launcher- or operator-visible break: a CLI flag, configuration key, environment
        variable or token rule is removed or changed, or a contract major is dropped.
      - A minor release implements a new contract minor or adds a feature.
      - A patch release carries fixes only.
      - The service stays at 0.x until contract 1.0.0 (#128) and the first switched route.
   3. **The contract relation:**
      - Each release implements exactly one bundled contract version. It is reported as `contractVersion`
        by `GET /info` and named in the release notes.
      - A pre-release may bundle a `-SNAPSHOT` contract, and its notes say so.
      - A final release requires `contract-charter-v<info.version>` merged into the tagged commit.
   4. **The assets:** the table, the `.sha256` files and `SHA256SUMS` (format, bare names, sorted). The
      prefix `spec-kitty-charter-service` stops being provisional. Native assets are raw executables. Core's
      requests are spec-kitty/spec-kitty#6909.
   5. **Native plus jar:**
      - Native for the four targets; Java 25 is needed only on other hosts; open question 5 is closed.
      - `macos-x64` has no target, as in core, so it takes the jar.
      - The binaries are not code-signed (a risk).
   6. **Provenance:** a build-provenance attestation over `SHA256SUMS`, with the verify command and its
      `--signer-workflow` and `--source-ref` flags. Core does not check it yet (#6909).
   7. **The gate and publishing:**
      - Refusal cases: the tag format, main ancestry, the version match, the final-release contract tag, every
        target built, and the tag-moved recheck.
      - Only the publish job writes.
      - `--latest=false`, and a `-` suffix makes a pre-release.
      - An existing release is never replaced.
      - A manual run is a rehearsal.
   8. **Not shipped:** contract archives (core would vendor them as pin extras), a companion wheel (core's),
      and the container image (#234 builds on this line).
4. Alternatives:
   - one tag line for the contract and the service (events' choice; rejected because ADR-0022 keeps the
     contract line);
   - jar only (rejected by the operator);
   - archives per target (core expects raw executables);
   - a different prefix (it would cost a core change).
5. Revisit when: core verifies attestations; a target is added; code signing is wanted; contract 1.0.0 is
   reached.

**Validation:** every question in SC-001 can be answered from the ADR text alone.

### Subtask T002: Mark open question 5 decided

In `docs/plans/open-questions.md`, change row 5's default to "**Decided 2026-10-31:**". The text: native per
OS for linux-x64, linux-arm64, macos-arm64 and windows-x64, with the jar as fallback
([ADR-0046](/adr/0046-service-release-line-and-native-packaging/)). Bump `updated:` and add the ADR to
`related:`.

### Subtask T003: Update milestones and roadmap

- `docs/plans/milestones.md`:
  - the #241 row names ADR-0046;
  - the #71 row says the artifacts are built by the `service-v*` line (ADR-0046), and the launcher and
    core-verify criteria moved to #70 and spec-kitty#6909 (D7).
- `docs/plans/roadmap.md`: where step 7 or a running service is mentioned, add one sentence on the release
  line, only where it fits.
- Bump `updated:` on both.

### Subtask T004: Docs checks

Run:

```bash
python3 scripts/docs/check_docs.py all
npx --yes markdownlint-cli2 <changed files>
npx --yes cspell --no-progress <changed files>
```

Fix every finding. Each `related:` slug must map to a file under `docs/`.

## Definition of Done

- ADR-0046 exists, Accepted, and answers SC-001.
- Open question 5 is decided. The milestone and roadmap entries are updated.
- The docs checks are green. Each subtask is marked done with
  `spec-kitty agent tasks mark-status <Txxx> --status done`.

## Risks

- **An ADR number collision with a sibling PR.** Mitigation: recheck at writing time and before the PR.
- **A non-existent `related:` slug fails the site build.** Mitigation: only cite real pages.

## Reviewer Guidance

- Check that the asset names and the tag regex match core exactly (`research.md` R1).
- Check that the contract relation matches ruling D1, including the pre-release exception.
- Check that nothing in the ADR implies core work this repository does.
