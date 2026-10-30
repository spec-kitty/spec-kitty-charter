---
title: Run the service in a container
description: Run the charter service under the container profile with docker compose, with its token as a mounted secret file and nothing secret committed.
doc_status: draft
updated: 2026-10-30T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0041-containerized-configuration-model
  - architecture/service-lifecycle
audience:
  - profile: governance-lead
    guidance_text: Follow this to run the charter service for a team in a container, and to see where its secrets go.
  - profile: charter-service-contributor
    guidance_text: Follow this to try the container profile locally, and for what a container start refuses.
---

# Run the service in a container

This page shows how to run the charter service in a container under the `container` profile, using the docker compose
example in `deploy/compose/`. The rules come from
[ADR-0041](/adr/0041-containerized-configuration-model/). Secrets come only from mounted files or env vars, and no
committed file holds one.

No container image is published yet ([#234](https://github.com/spec-kitty/spec-kitty-charter/issues/234)). You need one
you built yourself.

## Steps

1. Copy the env template and name your image:

   ```bash
   cd deploy/compose
   cp .env.example .env        # .env is ignored by git; never commit it
   ```

   In `.env`, set `CHARTER_IMAGE` to your image.
2. Put each secret in `./secrets`, in a file named after its property. Create the files so that only you can read them:

   ```bash
   (umask 077 && mkdir -p secrets && openssl rand -hex 32 > secrets/charter.worktree-token)
   printf 'localhost' > secrets/charter.container.allowed-hosts
   ```

   `./secrets` is ignored by git. The user the image runs as must be able to read the files.
3. Start the service:

   ```bash
   docker compose up
   ```

   The example publishes port 8080 on the host's loopback address only (see [Limits for now](#limits-for-now)).
   Send the token as `Authorization: Bearer <token>`.

## Where settings come from

| Setting | As a file in `./secrets` | As an env var |
| --- | --- | --- |
| The token | `charter.worktree-token` | `CHARTER_WORKTREE_TOKEN` |
| The allowed host names | `charter.container.allowed-hosts` | `CHARTER_CONTAINER_ALLOWEDHOSTS` |
| The configuration root | — | `CHARTER_CONFIGROOT` (set by the example) |

- **File names.** A file is named after its property, in dot form. A file named in env-var style
  (`CHARTER_WORKTREE_TOKEN`) is refused.
- **Precedence.** An env var outranks the file, and an empty env var hides the file. That is why `.env.example` lists
  the secrets only as comments.
- **Whitespace.** One trailing newline in a file is dropped. Any other whitespace stays part of the value.
- **Another directory.** The service reads the directory at start-up, `/run/secrets` unless
  `CHARTER_CONTAINER_SECRETS_DIR` names another absolute directory, given without a slash at the end.

## What stops the start

Among other checks, the service refuses to start, naming the property, file or directory, but never a value, when:

- the secrets directory is missing. The example's mount does not create `./secrets`.
- the secrets directory is relative, is the root, or is writable by others.
- a file in it sets anything other than the token or a `charter.container.*` key, such as `server.address`.
- there is no token, or it is blank or shorter than 32 characters.
- `charter.container.allowed-hosts` is missing or empty, or has a blank entry.
- the configuration root is unset or does not exist, or the port is 0.
- `charter.insecure-no-token` or `charter.project-config-dir` is set.

## Limits for now

- **Host names.** The request guard still accepts only loopback `Host` names (`localhost`, `127.0.0.1`, `[::1]`), so
  reach the service as `localhost:8080`. A request with a real host name gets a 403 until the guard reads
  `allowed-hosts`.
- **Health check.** There is no health endpoint, and so no compose health check
  ([#232](https://github.com/spec-kitty/spec-kitty-charter/issues/232)).
- **Configuration root.** The example's root, `/var/lib/spec-kitty/charter`, is local to the example until the image
  sets its own (#234).
- **Inspecting the file.** Never run `docker compose config` with a real `.env`: it prints the values.

## See also

- [ADR-0041](/adr/0041-containerized-configuration-model/)
- [Service lifecycle](/architecture/service-lifecycle/)
