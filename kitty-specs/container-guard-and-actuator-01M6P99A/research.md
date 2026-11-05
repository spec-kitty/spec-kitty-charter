# Research: Container guard, actuator and single-replica ruling

Pre-spec squad, 2026-11-05, run as parallel profile-loaded delegates (adversarial-squad-deployment):

- architecture lens: `architect-alphonso`;
- security lens: `reviewer-renata`, security-audit mode;
- build and docs lens: `researcher-robbie`.

All three reported that the org-layer packs (`internal`, `qa`) are missing on disk. The charter accepts this
(charter, "Designing during the rewrite").

## Findings and dispositions

| # | Lens | Finding | Disposition |
|---|---|---|---|
| R1 | security, architecture | An entry in `allowed-hosts` is only checked for being non-blank, so `*`, `host:8080`, `0.0.0.0` and URLs bind. A wildcard defeats the DNS-rebinding protection; a port never matches. | accepted: FR-005 |
| R2 | security | An empty `Host` list passes `allMatch`. An HTTP/1.0 request without `Host` is then judged only on the server name, which Tomcat takes from the local address. | accepted: FR-002 (container form only; the local form is unchanged, FR-003) |
| R3 | security, architecture | Prefix or decoded matching of the health path would exempt `/actuator/health/../env`. | accepted: FR-009 exact raw `GET` match; the near misses are pinned by SC-002 |
| R4 | security, architecture | Env vars can widen the actuator configuration, and a separate management port runs outside the guard. | accepted: FR-011 |
| R5 | security | JMX should be pinned off. | accepted: FR-008 |
| R6 | security, architecture | On Kubernetes, Boot turns on forwarded-header handling, which changes the server name. | accepted: FR-016 (`server.forward-headers-strategy: none` under container) |
| R7 | security, architecture | A blank or unset `server.address` binds all interfaces. | accepted: FR-012 (judged as an `InetAddress`, loopback required) |
| R8 | security | Request-detail logging would log the `Authorization` header. | accepted: FR-016, refused under container |
| R9 | security | Extend the token-never-logged test to debug logging and to a token from a config tree. | accepted: FR-017 |
| R10 | architecture | `ContainerStartupCheck` returns early without the profile, so it cannot hold the local bind rule. | accepted: a separate always-on check (plan) |
| R11 | architecture | The `ConstraintViolationException` handler is local to `ArtifactController`. | accepted: FR-018, moved to the shared handler and limited to parameter violations |
| R12 | build | Actuator is not in the Gradle cache. The metadata write needs Maven Central (through the mirror), following `docs/guides/service-build.md` "Change a dependency". | accepted: NFR-001 |
| R13 | build | CI builds the native image on PRs that touch `service/`. | accepted: NFR-002 |
| R14 | build | `run-in-a-container.md` "Limits for now" lists the host and health limits this mission lifts. The compose example publishes on loopback only. | accepted: FR-020 |
| R15 | build | The contract 403 text lives in `responses/Forbidden.yaml` and the `securitySchemes` description. A change adds one fragment under `changes/`. | accepted: FR-007, C-003 |
| R16 | security | A Kubernetes `httpGet` probe sends the pod IP as `Host`. | accepted: guide note (FR-020), no wildcard |
| R17 | architecture | Open questions: HEAD exempt? Trailing dot? Specific interface IP under container? | changed: resolved from ADR-0041's text (HEAD not exempt; trailing dot refused; any address under container per D4). Recorded as decisions on this mission, not operator questions. |

## Operator rulings in force

- #226 (2026-10-27): a platform-neutral container, configured through env vars and mounted secrets.
- #233 (2026-11-05): one replica for now. The in-memory per-project state is the ADR-0033 write-through copy and
  the ADR-0018 ETags.
- ADR-0041 D4, D6 and D7. D4 wins over #231's "hosted ⇒ non-loopback" wording: loopback stays allowed in a
  container.

## Pre-PR squad (2026-11-05)

Three profile-loaded delegates reviewed the integrated lane:

- security and tests: `reviewer-renata`, verdict READY with one MEDIUM;
- architecture and contract: `architect-alphonso`, verdict NOT READY on one MEDIUM;
- docs accuracy: `curator-carla`, verdict READY.

| # | Lens | Finding | Disposition |
|---|---|---|---|
| P1 | architecture | ADR-0060's Context claimed the ETags and the fingerprint live in memory and differ per replica. They are digests of the committed files. The activation copy is not built (`501`). The real hazard is the in-process pack-write lock (ADR-0026, "one writer per pack"). | accepted: the ADR-0060 Context rewritten around that hazard; the decision is unchanged; the guide bullet is corrected. The correction is noted to the operator on #233. |
| P2 | security | Publishing beyond loopback exposes the token over plain HTTP. | accepted: a TLS note in the guide ("Plain HTTP") and in the compose comment |
| P3 | security | A configured health group adds `groups` to the unauthenticated body. | accepted: any `management.endpoint.health.group.*` fails the start |
| P4 | security | Re-enabling JMX was not refused. | accepted: `spring.jmx.enabled` on, or a JMX exposure, fails the start |
| P5 | security | The base path was compared without case. | accepted: compared exactly |
| P6 | security | `freePort` has a race in `ContainerHealthTest`. | accepted: a start that loses the port is retried, up to 3 attempts |
| P7 | security | The native smoke did not check that the exemption is narrow. | accepted: `/info` without the token is 401 and `/actuator/env` with it is 404 |
| P8 | architecture, docs | "Refuses a request without Host" ignores Tomcat's HTTP/1.1 400. | accepted: the contract and the fragment say HTTP/1.0; the guide drops the clause |
| P9 | architecture | The 403 description opened with "A browser made the request". | accepted: reworded and re-wrapped |
| P10 | architecture | `service-lifecycle.md` was stale (container summary, the replica bullet). ADR-0041 had no pointer to the answer. | accepted: both updated, linking ADR-0060 |
| P11 | docs | The guide's "see Limits" link was stale, and "exact request" was imprecise (the query is ignored). | accepted: the link is retargeted; the wording is now "exact path" |
| P12 | architecture | The docs branch and the code branch both edit the container guide. | changed: the docs PR (ADR-0060) is synced to the corrected text; the code PR rebases on main after it merges |
| P13 | architecture | Run `HexagonalArchitectureTest` in isolation (the delegates' Gradle runs collided). | accepted: run with `verifyAll` in lane-f, 45/45 green |
