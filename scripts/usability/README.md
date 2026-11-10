# Service usability run

`service-usability-run.sh` plays a client's part against the charter service. It starts the service, loads the
spec-kitty-open-packs, activates this repository's charter and reads an agent profile (default `java-jenny`) with
its governance, both from the pack and under the activation. After `GET /api/v1/` it follows only `_links`: it never
builds a URL. A relation that a hop expects and the answer lacks fails that hop, and the line names the hop and the
relation. It proves FR-011, SC-001 to SC-003 and NFR-003 of the `agent-profile-reads` mission.

## Run it

```bash
JAVA_HOME=/usr/lib/jvm/temurin-25 scripts/usability/service-usability-run.sh \
  --open-packs ../spec-kitty-open-packs --profile java-jenny
```

| Option | Meaning |
|---|---|
| `--open-packs <dir>` | A checkout of spec-kitty-open-packs. Its `HEAD` must equal `openPacksRef` in `gradle.properties`. Required unless `--attach`. |
| `--profile <id>` | The agent profile to read. Default `java-jenny`. |
| `--jar <path>` | A built service jar. Default: build `:deployable:bootJar` (`./gradlew --max-workers=1`, in the foreground). |
| `--keep` | Leave the service running and the temporary directory in place. The output says how to stop it. |
| `--packs <a,b>` | The open-packs directories to register through `POST /packs`. Default `internal,qa`. |
| `--strict` | Treat selections no registered pack holds as a failure instead of a named workaround. |
| `--attach <base-url>` | Walk a service that is already running and provisioned. The token comes from `CHARTER_WORKTREE_TOKEN`. |

## What it does

1. **Packs, as pinned.** `packs/built-in` is extracted with `git archive` at the pin into `<config-root>/packs/`. The
   git tree of the copy is computed in a scratch repository and must equal `git rev-parse HEAD:packs/built-in`.
   `built-in` is never edited. The service starts with registration and the filesystem pack source on, a random
   token (passed in the environment, and to curl through an options file) and port 0.
2. **Registration.** Each pack of `--packs` goes through `POST /packs` with a filesystem source, under the name its
   `pack.yaml` declares (the registration requires `packId` to equal it: `internal` is `spec-kitty-internal`). The
   project pack (`.kittify/charter-packs`) is tried as it is.
3. **The activation document.** `charter-to-activation.py` turns `.kittify/charter/charter.yaml` into an activation
   document and adds the profile. It maps the directive slugs to the ids the service holds
   (`001-project-intent-directive` is `PROJECT_001`).
4. **The walk** (hop ids in the output):
   - pack-scoped: root `artifacts` (`packId=built-in`, `kind=agent_profile`) → the profile's list item `self` → body and
     `governance` → each entry's `artifact`;
   - under the activation: root `activations` (method POST, the document) → the answer's `self` → `artifacts`
     (`kind=agent_profile`) → the profile's item `effective-artifact` → body and `governance` → each entry's
     `effective-artifact` and `artifact`.
5. **Assertions.** `java-conventions` is a `requires` entry (SC-003); every governance entry carries `edge`; 20 reads
   on each path have a p95 under 500 ms (NFR-003).

## Workarounds

Nothing is silent. Every workaround prints as `[WORKAROUND]` when it is applied and again in the summary.

| Name | When | What it does |
|---|---|---|
| `unmodelled-files (#451)` | A registration is refused only over `unmodelled` files (README, PROVENANCE, `org-charter.yaml`, ...). | Registers a staged copy without the files the refusal named. The checkout is untouched. |
| `project-pack (#452)` | The project pack is refused. | Skips it. No edit is tried. |
| `unheld-selections` | The activation is refused `activation_invalid` only with `unknown_artifact` violations. | Leaves out exactly the selections the service names, and lists them. `--strict` fails instead. |

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Every hop answered 2xx, the assertions held and both p95 values are under 500 ms. |
| `1` | A hop failed, an assertion failed or a p95 was too high. The summary lists them. This is the code of the red baseline. |
| `2` | Usage or prerequisite error: a missing tool, no checkout, or a checkout that is not at the pin. |
| `3` | The service could not be built or did not start. |
| `4` | The packs could not be provisioned: the copy of `built-in` differs from the pin, or a pack is missing. |
| `130`, `143` | Interrupted. The service is stopped either way. |

The last line is `RESULT PASS|FAIL hops=<ok>/<all> failed=<n> workarounds=<n> p95=<pack>/<activation>ms commit=<8> pin=<8>`.
The service is stopped when the script ends, unless `--keep` is given.

## Prerequisites

`bash`, `curl`, `jq`, `git`, `tar`, `awk`, `python3` (run with `-I`, standard library only) and a JDK 25. Gradle runs
only when `--jar` is not given.

## Self-test

`python3 -I scripts/usability/charter-to-activation.py --self-test` checks the slug table and the translation.
