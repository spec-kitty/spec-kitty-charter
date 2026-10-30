---
work_package_id: WP02
title: Container-profile token messages
dependencies: []
requirement_refs:
- FR-005
- NFR-001
- NFR-002
- NFR-004
planning_base_branch: issue-229-hosted-config-secrets
merge_target_branch: issue-229-hosted-config-secrets
branch_strategy: Planning artifacts for this mission were generated on issue-229-hosted-config-secrets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-229-hosted-config-secrets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-container-profile-secrets-01M65MC5
base_commit: 3f9bed187bf7fa5bd02f6c4671cf099c50e727f9
created_at: '2026-10-30T06:09:32.239928+00:00'
subtasks:
- T005
- T006
- T007
phase: Phase 1 - Implementation
history:
- at: '2026-10-30T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/deployable/src/main/java/ai/speckitty/charter/deployable/GuardWiring.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/deployable/src/main/java/ai/speckitty/charter/deployable/GuardWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/GuardWiringTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – Container-profile token messages

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined
profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Objectives & Success Criteria

ADR-0041 decision 3: "The token refusal message, which names only the env var today, names both forms under the
container profile." The #228 mission handed this over (research R2), and the plan-point squad added two more items
(research R10, rows 1 and 7).

Under the `container` profile:

- **Missing token.** The refusal names `CHARTER_WORKTREE_TOKEN` and `<dir>/charter.worktree-token`, where `<dir>` is
  `charter.container.secrets-dir`, defaulting to `/run/secrets`. It does **not** offer `charter.insecure-no-token`,
  which the container profile refuses.
- **Token from the command line or a system property.** The refusal names both accepted forms.
- **Blank token.** The refusal adds that an empty `CHARTER_WORKTREE_TOKEN` hides a mounted token file.

Without the profile, all three messages are exactly as on `origin/main`, and the existing tests pin them. No message
holds the token.

## Context & Constraints

- `GuardWiring` (`service/deployable/.../GuardWiring.java`) reads the token from the `Environment`.
  - Missing-token message: `:93-96`.
  - Blank message: `:101-103`.
  - Unreadable-source message: `unreadableSourceRefusal`, `:135-138`.
- Read the profile with `environment.matchesProfiles(ContainerProperties.PROFILE)`. Both classes are in the deployable,
  so the packs module stays unaware of the profile (C-001).
- Keep cyclomatic complexity at 15 or below per method. Extracting message builders is fine.
- doclint: `-Xdoclint:all -Werror` on main code. New private methods need no javadoc, but update the class javadoc's
  first bullet to mention the container form.

## Subtasks & Detailed Guidance

### Subtask T005 – `GuardWiringTest` container cases (red first)

Add tests on the existing `ApplicationContextRunner` (`Surroundings` plus `GuardWiring`) with
`spring.profiles.active=container`:

1. No token: the failure contains `CHARTER_WORKTREE_TOKEN` and `/run/secrets/charter.worktree-token`, and does **not**
   contain `charter.insecure-no-token=true`.
2. No token, `charter.container.secrets-dir=/srv/secrets`: the failure contains `/srv/secrets/charter.worktree-token`.
3. Token from the command line (reuse the existing `SimpleCommandLinePropertySource` initializer pattern): the failure
   names both forms.
4. Blank token from the environment (reuse the `SystemEnvironmentPropertySource("test-environment", …)` pattern): the
   failure contains `is blank` and mentions an empty `CHARTER_WORKTREE_TOKEN` hiding the mounted file.
5. Positive control on the local path: the same "no token" case without the profile still contains
   `set CHARTER_WORKTREE_TOKEN in the environment` and offers `charter.insecure-no-token=true`. This may already be
   covered; if so, cite the existing test rather than duplicating it.
6. No message contains a token value. For the command-line case, assert that the failure text does not contain the
   planted token.

Run the class before T006. The new container tests fail. Record the red run.

### Subtask T006 – Profile-aware messages in `GuardWiring`

A suggested shape (adjust to taste, keeping it simple):

```java
static final String SECRETS_DIR_PROPERTY = "charter.container.secrets-dir";
private static final String DEFAULT_SECRETS_DIR = "/run/secrets";

/** Where a container takes the token from: the env var or the mounted file. */
private static String containerSources(Environment environment) {
    String dir = environment.getProperty(SECRETS_DIR_PROPERTY, DEFAULT_SECRETS_DIR);
    return "CHARTER_WORKTREE_TOKEN in the environment or the file " + dir + "/charter.worktree-token";
}
```

- Missing, container: `TOKEN_PROPERTY + " is required under the container profile: set " + containerSources(env)`.
- Unreadable source, container:
  `TOKEN_PROPERTY + " must come from " + containerSources(env) + ", not from the command line or a system property, where other processes can read it"`.
- Blank, container: append `"; an empty CHARTER_WORKTREE_TOKEN hides a mounted token file"`.
- `unreadableSourceRefusal()` needs the environment; thread it through as a parameter.
- The insecure flag's existing warning and behaviour stay as they are: `ContainerStartupCheck` refuses the flag under
  the profile.
- A trailing slash in `secrets-dir` would print `//`; strip one trailing `/` from the value.

### Subtask T007 – Non-vacuity and module check

- Revert T006 locally (`git stash`) and confirm that the new tests are red. Restore it.
- Run `./gradlew :deployable:spotlessApply :deployable:check`.

## Definition of Done

- [ ] The new container tests pass, and were red before T006.
- [ ] The existing `GuardWiringTest` assertions are unchanged and pass.
- [ ] `:deployable:check` is green, with no new suppressions.

## Risks & Reviewer Guidance

- Reviewer: diff the local messages and confirm they are byte-identical to `origin/main`.
- Reviewer: grep the new messages for any interpolated token value.

## Implementation command

```bash
spec-kitty agent action implement WP02 --agent claude --mission container-profile-secrets-01M65MC5
```
