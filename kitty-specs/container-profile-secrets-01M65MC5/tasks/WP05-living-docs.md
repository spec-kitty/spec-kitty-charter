---
work_package_id: WP05
title: Living docs
dependencies:
- WP01
- WP02
- WP03
- WP04
requirement_refs:
- SC-005
- C-002
planning_base_branch: issue-229-hosted-config-secrets
merge_target_branch: issue-229-hosted-config-secrets
branch_strategy: Planning artifacts for this mission were generated on issue-229-hosted-config-secrets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-229-hosted-config-secrets unless the human explicitly redirects the landing branch.
subtasks:
- T016
- T017
- T018
- T019
- T020
phase: Phase 2 - Polish
history:
- at: '2026-10-30T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: scribe-sally
authoritative_surface: docs/
create_intent:
- docs/guides/run-in-a-container.md
- docs/changelog/2026-10-30-container-profile-secrets.md
execution_mode: planning_artifact
model: ''
owned_files:
- docs/adr/0041-containerized-configuration-model.md
- docs/architecture/service-lifecycle.md
- docs/guides/run-in-a-container.md
- docs/guides/README.md
- docs/changelog/2026-10-30-container-profile-secrets.md
- docs/changelog/README.md
- docs/_meta/spelling-words.txt
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 – Living docs

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter (or any user-defined
profile), and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `scribe-sally`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Review Feedback

*[If this WP was returned from review, the reviewer feedback reference appears in the status event log.]*

---

## Objectives & Success Criteria

Describe what WP01 to WP04 landed, and correct the stale pages:

- ADR-0041 decision 3 gains a dated clarification note. Do not rewrite the decision.
- `docs/architecture/service-lifecycle.md` stops calling ADR-0041 "proposed" and describes the container profile's
  secrets.
- A how-to explains running under compose.
- A changelog entry names what changed and the follow-ups.

Every docs check passes.

## Context & Constraints

The rules of `scripts/docs/check_docs.py` (research R7):

- **Frontmatter:** `title`; `description` of 50 to 180 characters; `doc_status`; `updated` as an ISO date, not in the
  future (today is 2026-10-30); `type`; `kind`; `audience` entries whose profiles exist under
  `docs/context/audience/`.
- **related:** slugs must resolve to pages.
- **Terms:** "feature" and "features" are forbidden (`docs/_meta/terminology.yaml`).
- **Links:** use root routes such as `/adr/0041-containerized-configuration-model/`.
- **Changelog:** named `YYYY-MM-DD-slug.md`, under 3000 characters, listed newest first in `docs/changelog/README.md`.
- **Orphans:** a new guide is linked from `docs/guides/README.md`.

Use `docs/changelog/2026-10-29-container-properties-record.md` as the model for the entry. Put `configtree` and other
code words in code spans, or add them to `docs/_meta/spelling-words.txt`.

## Subtasks & Detailed Guidance

### Subtask T016 – ADR-0041 decision 3 note

Under decision 3's bullet list, add a dated note. Keep the ADR's `updated` current.

> **Clarified 2026-10-30 (#229, operator decision).** The import reads
> `configtree:${charter.container.secrets-dir:/run/secrets}/`. It is still required, and its default is the path
> above. Tests point it at a temporary directory, and an operator may name another absolute directory. The env form is
> `CHARTER_CONTAINER_SECRETS_DIR`, and a value takes no trailing slash. Under the container profile, a file in the
> directory may set only `charter.worktree-token` or a `charter.container.*` key, and the directory must not be blank,
> relative, the root or writable by others; a breach fails the start.

Do not change the ADR's status or other decisions. Run the `adr` check.

### Subtask T017 – Service lifecycle page

`docs/architecture/service-lifecycle.md`:

- Around lines 27 to 31, ADR-0041 is "proposed". Make it accepted.
- Around lines 62 to 64, the token source names only `CHARTER_WORKTREE_TOKEN`. Add the container form.
- Add a short paragraph on the container profile:
  - its file and defaults (0.0.0.0, 8080);
  - the required secrets directory, and env vars outranking files;
  - the allowlist;
  - a link to the how-to.

Update `updated`.

### Subtask T018 – How-to

Create `docs/guides/run-in-a-container.md` (`type: Guide`, `kind: How-To`, `doc_status: draft`), with audiences
`governance-lead` and `charter-service-contributor` (check they exist). Cover:

- the prerequisites (an image, which #234 will publish);
- `cp .env.example .env`;
- creating `./secrets` under `umask 077` with `charter.worktree-token`;
- allowed hosts as a file or an env var;
- `docker compose up`;
- what fails and why: a missing `./secrets`, a missing token, a foreign file, port 0, a missing configuration root;
- precedence: an env var outranks a file, and an empty one hides it;
- whitespace in a token file: one trailing newline is dropped, nothing else;
- the guard still requires a loopback `Host` until it reads `allowed-hosts`, so use `localhost`;
- never run `docker compose config` with a real `.env`, because it prints the values;
- the configuration root is local to the example until #234.

Link the guide from `docs/guides/README.md`.

### Subtask T019 – Changelog

Write `docs/changelog/2026-10-30-container-profile-secrets.md` and add its line at the top of
`docs/changelog/README.md`. Cover:

- what changed for an operator;
- what is unchanged for the local daemon;
- the token message;
- the allowlist, flagged as an open review item;
- follow-ups:
  - #230 (property metadata, including `charter.container.secrets-dir`);
  - #231;
  - #232 (compose health check);
  - #234 (image, config-root default as `${configRootLocation:<mount>}` and not an image `ENV`, and a `.dockerignore`
    excluding `.env*` and `secrets/`);
  - the guard reading `allowed-hosts`.

Stay under 3000 characters.

### Subtask T020 – Docs checks

```bash
python3 scripts/docs/check_docs.py all
python -m pytest -p no:cacheprovider scripts/docs/tests -q
npx cspell@9.2.0 --no-progress "docs/**/*.md"
npx markdownlint-cli2@0.18.1 "docs/**/*.md"   # advisory, but fix what this WP introduces
```

## Definition of Done

- [ ] Every check above is green. Record the commands and their results.
- [ ] Each page states only what landed: no claim of an image, a health check or a guard change.

## Risks & Reviewer Guidance

- `docs/changelog/README.md` is the hottest file. Expect a conflict when rebasing.
- Reviewer: check every claim against the code and tests of WP01 to WP04.

## Implementation command

```bash
spec-kitty agent action implement WP05 --agent claude --mission container-profile-secrets-01M65MC5
```
