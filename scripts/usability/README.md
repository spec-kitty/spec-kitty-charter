# Service usability run

`service-usability-run.sh` plays a client's part against the charter service. It starts the service, loads the
spec-kitty-open-packs, activates this repository's charter and reads an agent profile (default `java-jenny`) with
its governance, both from the pack and under the activation. After `GET /api/v1/` it follows only `_links`: it never
builds a URL (only the bootstrap calls, `GET /api/v1/` and `POST /packs`, are written out). A relation that a hop
expects and the answer lacks fails that hop, and the line names the hop and the
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
| `--self-check` | Run the pure parts of the client without a service (`expand`, href resolution, the removal guard, option parsing, and `charter-to-activation.py --self-test`). Exit 0 or 1. |

An option that takes a value (`--open-packs`, `--profile`, `--jar`, `--packs`, `--attach`) and gets none, or gets
another option in its place, exits 2 with the usage text.

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
     `governance` → each held entry's `artifact`;
   - under the activation: root `activations` (method POST, the document) → the answer's `self` (A3) →
     that activation's `artifacts` (A4, `kind=agent_profile`, `{?kind,state}`) → the profile's item `effective-artifact` → body and `governance` → each
     held entry's `effective-artifact` and `artifact`.

   **Which entries must carry a link.** The service links `artifact` and `effective-artifact` only on a held entry
   whose kind has a pack-scoped read; `procedure` has none, so there is nothing to point to. The client does not keep
   a table of kinds. For an entry that lacks the link it asks the service: it follows the root `artifacts` link with
   that entry's `packId` and `kind`. `200` means the kind is served, so the missing link fails the hop. `400` means the
   kind is not served (a 400 Problem without a code, per `contracts/charter/paths/packs_packId_artifacts.yaml`), so the
   absence is expected. The answer is cached per kind. Any other answer fails the hop, because the client cannot
   classify the entry. Nothing is skipped silently: each walk prints `not linked, kind not served: <relation> <kind> x<n>`,
   the summary lists the counts by kind, and the `RESULT` line carries their total as `unlinked=<n>`. Entries with
   `held=false` have no pack and are counted in the walk line.

   **Hrefs.** An href is followed when it is a path starting with `/` (relative to the service's base) or an absolute
   URL on the base the script started or was attached to. An absolute URL on any other origin, a `//` reference, or a
   path without a leading `/` is not followed and fails the hop with a message, so the bearer token never leaves the
   service. URI-template variables are percent-encoded when filled (RFC 6570).
5. **Assertions.** `java-conventions` is a `requires` entry (SC-003); every governance entry carries `edge`; 20 reads
   on each path have a p95 under 500 ms (NFR-003).

## Workarounds

Nothing is silent. Every workaround prints as `[WORKAROUND]` when it is applied and again in the summary.

| Name | When | What it does |
|---|---|---|
| `unmodelled-files (#451)` | A registration is refused only over `unmodelled` files (README, PROVENANCE, `org-charter.yaml`, ...). | Registers a staged copy without the files the refusal named. The checkout is untouched. |
| `project-pack (#452)` | The project pack is refused. | Skips it. No edit is tried. |
| `unheld-selections` | The activation is refused `activation_invalid` only with `unknown_artifact` violations. | Leaves out exactly the selections the service names, and lists them. `--strict` fails instead. |

The script only removes what it made: the temporary directory is removed only if it is the directory `mktemp` returned
for this run (it carries a marker file), and a staged-copy file is removed only if its path resolves strictly inside
that directory. A path the service names that is absolute or contains `..` is refused and fails the registration hop.

`charter-to-activation.py` fails (it exits 3 itself) on a top-level `activated_*` key it does not map, and on a mapped
key whose list is inline or indented, because either would drop selections without a word. The run then fails hop A2
and exits 1.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Every hop answered 2xx, the assertions held and both p95 values are under 500 ms. If the `RESULT` line says `unheld=<n>` with n above 0, it is a pass with named gaps: report those selections to the operator, they are not covered by the proof. |
| `1` | A hop failed, an assertion failed or a p95 was too high. The summary lists them. This is the code of the red baseline. |
| `2` | Usage or prerequisite error: an option without its value, an unknown option, a missing tool, no checkout, or a checkout that is not at the pin. |
| `3` | The service could not be built or did not start. |
| `4` | The packs could not be provisioned: the copy of `built-in` differs from the pin, or a pack is missing. |
| `130`, `143` | Interrupted. The service is stopped unless `--keep` is given. |

With `--keep`, the stop hint is printed before the last line. The last line is `RESULT PASS|FAIL hops=<ok>/<all> failed=<n> workarounds=<n> unheld=<n> unlinked=<n> p95=<pack>/<activation>ms client=<8>[-dirty] [jar=<12>|service=unknown] pin=<8>`.
`client` is `git describe --always --dirty` of this repository, the tree Gradle builds without `--jar`; with `--jar`, `jar` is the
start of the jar's sha256, and with `--attach` the service under test is `unknown`. `--attach` creates one activation on that
service and does not delete it.
The service is stopped when the script ends, unless `--keep` is given.

When `--jar` is not given the script builds with `./gradlew --max-workers=1 :deployable:bootJar`. That leaves the Gradle
daemon running after the build, which the repository's rules allow (never `--stop` it). The script stops only the
service it started.

## Prerequisites

`bash` 4.2 or later (the script uses associative arrays; the macOS `/bin/bash` 3.2 is too old, the script refuses it),
GNU coreutils (`realpath -e`, `sha256sum`; BSD `realpath` lacks `-e`), `curl`, `jq`, `git`, `tar`, `awk`, `python3` (run with `-I`, standard library only) and a JDK 25. Gradle runs
only when `--jar` is not given.

## Self-test

`python3 -I scripts/usability/charter-to-activation.py --self-test` checks all 16 entries of the slug table: a slug
`NNN-...` must map to `DIRECTIVE_NNN`, and the six others are listed in the test. It also reads
`governance.charter.selected_directives` of `.kittify/charter/charter.yaml` and requires each id to be a value of the
table and to appear in the translated document. A wrong id in the table fails it; it would otherwise reach the service
as a 422 `unknown_artifact` and look like a pack gap. `service-usability-run.sh --self-check` runs it among its own.
