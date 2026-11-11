---
title: Run the service in a container
description: Run the charter service under the container profile with docker compose, with its token as a mounted secret file and nothing secret committed.
doc_status: draft
updated: 2026-11-11T00:00:00Z
type: Guide
kind: How-To
related:
  - adr/0041-containerized-configuration-model
  - architecture/service-lifecycle
  - adr/0061-one-replica-per-deployment
  - guides/service-build
  - guides/compose-packs
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

If your image will register packs from a git repository, it must carry git 2.50.1 or later (see
[Registering packs](#registering-packs)). No published image carries it yet: adding git to a published image is the scope
of #234.

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
   printf 'localhost,charter.example.org' > secrets/charter.container.allowed-hosts   # every name clients use
   ```

   `./secrets` is ignored by git. The user the image runs as must be able to read the files: give them to that user
   (`chown <uid>`) or to a group it is in (`chmod 0440`), and do not widen the directory's permissions.
3. Start the service:

   ```bash
   docker compose up
   ```

   The example publishes port 8080 on the host's loopback address only (see "Plain HTTP" under
   [Limits for now](#limits-for-now)).
   Send the token as `Authorization: Bearer <token>`.

## Where settings come from

| Setting | As a file in `./secrets` | As an env var |
| --- | --- | --- |
| The token | `charter.worktree-token` | `CHARTER_WORKTREE_TOKEN` |
| The allowed host names | `charter.container.allowed-hosts` | `CHARTER_CONTAINER_ALLOWEDHOSTS` |
| The configuration root | — | `CHARTER_CONFIGROOT` (set by the example) |

- **File names.** A file is named after its property, in dot form. A file named in env-var style
  (`CHARTER_WORKTREE_TOKEN`) is refused.
- **The token itself.** The file or env var holds the token, never a placeholder such as `${x}`: the service refuses
  to start on one, since it could resolve the token from the command line.
- **Precedence.** An env var outranks the file, and an empty env var hides the file. That is why `.env.example` lists
  the secrets only as comments.
- **Why files.** An env var's value shows in `docker inspect` and in the process environment; a mounted file does
  not.
- **Whitespace.** One trailing newline in a file is dropped. Any other whitespace stays part of the value.
- **Allowed hosts.** List every name clients use to reach the service, comma-separated, without a port. Each entry is
  one of:
  - a host name, such as `charter.example.org` or `localhost`;
  - a dotted IPv4 address;
  - a bracketed IPv6 address, such as `[::1]`.

  Matching ignores case and the port. A wildcard, a port, a scheme, a trailing dot, non-ASCII text and the
  any-address (`0.0.0.0`, `[::]`) are refused. A request whose `Host` is not on the list gets a 403.
- **Another directory.** The service reads the directory at start-up, `/run/secrets` unless
  `CHARTER_CONTAINER_SECRETS_DIR` names another absolute directory; a trailing slash is not needed.
- **Kubernetes.** The service-account files Kubernetes mounts under `/var/run/secrets/kubernetes.io` are left alone.

## What stops the start

Among other checks, the service refuses to start, naming the property, file or directory, but never a value, when:

- the secrets directory is missing. The example's mount does not create `./secrets`.
- the secrets directory is relative or is the root.
- a file in it sets anything other than the token or a `charter.container.*` key, such as `server.address`, or names
  the secrets directory itself.
- there is no token, or it is blank or shorter than 32 characters.
- `charter.container.allowed-hosts` is missing or empty, or has a malformed entry.
- the environment widens the management endpoints:
  - another exposed endpoint;
  - a management port of its own;
  - health details, components or probe groups;
  - a moved `/actuator` path, an endpoint mapped to another path, or the discovery page switched on.
- `server.forward-headers-strategy` is anything but `none`: forwarded headers are ignored.
- `spring.mvc.log-request-details` is on: it would log the token.
- `logging.level` puts `org.apache.coyote` or the request logger beneath it at `TRACE`, directly or through `root`,
  `org` or `org.apache`, or `server.tomcat.accesslog.pattern` holds `%{Authorization}i`: either would log the token.
  `DEBUG` stays allowed.
- the configuration root is unset or does not exist, or the port is 0.
- `charter.insecure-no-token` or `charter.project-config-dir` is set.

## Registering packs

Registration is off by default. These settings switch it on and name where packs may come from. Set them as env vars or
properties like any other setting (`CHARTER_REGISTRATION_ENABLED`, and so on). The full list, with defaults, is in
[Building the service](/guides/service-build/#registering-packs).

- `charter.registration.enabled=true` allows `PUT /packs/{packId}`, `POST /packs` and the two removals of a pack
  (`DELETE` of an artifact and of a relationship). While it is off, each answers `503 pack_source_unavailable`.
- Each source kind is off until its own flag is on: `charter.pack-sources.https.enabled`,
  `charter.pack-sources.git.enabled` and `charter.pack-sources.filesystem.enabled`. A flag is read at every request.
- Allowlists and the root are empty by default, so a source nobody configured is refused: set
  `charter.pack-sources.https.allowed-hosts`, `charter.pack-sources.git.allowed-hosts` and
  `charter.pack-sources.filesystem.root`. A path source switched on with no root stops the start.
- The image must carry **git 2.50.1 or later** for the git source, on the `PATH` or at
  `charter.pack-sources.git.executable`. The service checks the version at every start. With an old or missing git, the
  git source answers `503 pack_source_unavailable` and the other kinds work. The service never uses JGit and never
  checks out a work tree.
- Registration spools its upload under `<config-root>/staging`, on the volume of the packs, unless
  `charter.registration.staging-dir` names another directory. Keep it on the same volume as the packs.
- A registration needs about three times its archive in free space, at most 96 MiB: the archive, the tree it expands to
  and the copy made beside the pack. On one volume the whole of it must be free there; on two, 64 MiB on the staging
  volume and 32 MiB on the volume of the packs. Less is `503 pack_storage_full`, answered before the body is read.
- Two registrations run and eight wait. A registration takes its slot after the service has read the body, so a slow
  upload holds no slot, but it does hold a connection, up to 32 MiB of staging and one of ten places while it arrives:
  at most ten uploads are open at once, and the eleventh is `503` with `Retry-After`. The free-space check counts the
  declared bytes of the open uploads, so ten full-size uploads need about 320 MiB of staging free on top of a
  registration's own room. Put a proxy with an upload timeout in front if clients are slow.

## Health check

`GET /actuator/health` answers `{"status":"UP"}` without the token, so a probe needs no secret. Only that exact
path is exempt:

- every other method and path needs the token, `/actuator/health/` and `HEAD` included; a query string is not part
  of the path, so `/actuator/health?x=1` answers like `/actuator/health`;
- no other management endpoint answers, with or without the token;
- the answer never carries details, components or probe groups.

The request still needs an allowed `Host` and no browser header, so point the probe at a listed name:

```bash
curl -fsS -H 'Host: localhost' http://127.0.0.1:8080/actuator/health
```

A Kubernetes `httpGet` probe sends the pod's IP as `Host`. Set `httpHeaders` to a listed name instead of listing the
IP or a wildcard. The answer says only whether the process is up: one replica has nothing to drain
([ADR-0061](/adr/0061-one-replica-per-deployment/)).

## Limits for now

- **Plain HTTP.** The service speaks HTTP, and every request carries the token. Publish it beyond loopback only
  behind a proxy that terminates TLS.
- **Behind a proxy.** Forwarded headers (`X-Forwarded-Host`, `Forwarded`) are ignored. List the names the proxy sends
  in `Host`.
- **Configuration root.** The example's root, `/var/lib/spec-kitty/charter`, is local to the example until the image
  sets its own (#234).
- **Single replica.** A deployment runs exactly one replica. Pack writes are serialized inside one process only, so
  a second replica could lose a write that `If-Match` should have refused
  ([ADR-0061](/adr/0061-one-replica-per-deployment/)). Stop the old container before starting a new one; do not use a
  rolling update that overlaps them.
- **One container per configuration root.** Never start two containers, or a container and a local daemon, over
  the same configuration root or volume. Only one process may write a pack, and nothing refuses a second one yet
  ([one writer per pack](/architecture/service-lifecycle/#one-writer-per-pack); #349).
- **Inspecting the file.** Never run `docker compose config` with a real `.env`: it prints the values.

## See also

- [ADR-0041](/adr/0041-containerized-configuration-model/)
- [ADR-0061](/adr/0061-one-replica-per-deployment/)
- [Service lifecycle](/architecture/service-lifecycle/)
