---
work_package_id: WP08
title: Guarded guide fetcher
dependencies:
- WP02
requirement_refs:
- NFR-004
- FR-010
planning_base_branch: feat/194a-procedure-antipattern-toolguide
merge_target_branch: feat/194a-procedure-antipattern-toolguide
branch_strategy: Planning artifacts for this mission were generated on feat/194a-procedure-antipattern-toolguide. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/194a-procedure-antipattern-toolguide unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-procedure-antipattern-toolguide-round-trip-01M60H9B
base_commit: f18f79315853e1a97ad7ce57b536d75ab3955fb7
created_at: '2026-10-29T06:18:12.084816+00:00'
subtasks:
- T032
- T033
- T034
- T035
- T036
- T037
phase: PR-A2 - HTTP
agent: claude
history:
- at: '2026-10-29T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GuideFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GuideFetchRefusal.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/HttpsGuideFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/GlobalAddressResolverProvider.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/GuideFetchProperties.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/package-info.java
- service/packs/src/main/resources/META-INF/services/java.net.spi.InetAddressResolverProvider
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/HttpsGuideFetcherTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/GlobalAddressResolverProviderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/NetworkCodeBoundaryTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GuideFetcher.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GuideFetchRefusal.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/fetch/**
- service/packs/src/main/resources/META-INF/services/java.net.spi.InetAddressResolverProvider
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/fetch/**
- service/packs/src/test/java/ai/speckitty/charter/packs/NetworkCodeBoundaryTest.java
role: implementer
tags: []
task_type: implement
---

# Work Package Prompt: WP08 — Guarded guide fetcher

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

The service's first outbound HTTP call: fetch a toolguide guide from an HTTPS URI with SSRF protection, using only the
JDK. The fetcher is an application port with an infra adapter; nothing outside `infra` imports `java.net`.

## Branch Strategy

Planning branch and merge target `feat/194a-procedure-antipattern-toolguide`; lanes from `lanes.json`.
`spec-kitty agent action implement WP08 --agent <name> --mission procedure-antipattern-toolguide-round-trip-01M60H9B`.
**PR-A2.** Parallel with WP03–WP06.

## Context

- Spec NFR-004, US5-AS5; `research.md` D8 and D8a; adversary review `research/post-plan-squad/adversary.md` §1.
- Operator decisions: fetch-and-store; direct (never a proxy) with a JEP 418 resolver guard; proxy-only deployments
  cannot use `guideUri`.
- This container sets `https.proxyHost` through `JAVA_TOOL_OPTIONS`; the JDK client honours the system
  `ProxySelector` unless told `NO_PROXY`. Tests must never touch the network.
- Configuration: ADR-0038 one configuration root — Spring properties under `charter.` (see `StorageProperties`,
  `CharterProperties`).

## Subtasks

### T032 — Port and refusals

- `application/GuideFetcher`: `String fetch(URI uri) throws GuideFetchRefusal` (returns the UTF-8 text).
- `GuideFetchRefusal` with a `Reason` enum: `NOT_HTTPS`, `HOST_NOT_ALLOWED`, `USERINFO`, `IP_LITERAL`,
  `NON_GLOBAL_ADDRESS`, `TOO_MANY_REDIRECTS`, `REDIRECT_REFUSED`, `TIMEOUT`, `TOO_LARGE`, `ENCODED`, `NOT_UTF8`,
  `HTTP_STATUS` (non-2xx after redirects), `UNREACHABLE`. Messages name the URI host (never echo query strings).

### T033 — `GlobalAddressResolverProvider` (JEP 418)

- Implements `java.net.spi.InetAddressResolverProvider`; registered in
  `META-INF/services/java.net.spi.InetAddressResolverProvider`. It delegates to the built-in resolver and, **only for
  hosts on the guide allowlist**, refuses (throws `UnknownHostException` with a reason) when any resolved address is
  not global unicast. Other hosts pass through untouched (it is JVM-wide).
- Global unicast check: refuse loopback, any-local (0.0.0.0, ::), link-local (169.254/16, fe80::/10), site-local and
  private (10/8, 172.16/12, 192.168/16), unique-local fc00::/7, CGNAT 100.64/10, multicast, broadcast, documentation
  and benchmarking ranges, IPv4-mapped (`::ffff:a.b.c.d`) and IPv4-compatible (`::a.b.c.d`) forms of any refused IPv4,
  NAT64 `64:ff9b::/96` and 6to4 `2002::/16` embedding a refused IPv4. Implement as a pure function
  `AddressPolicy.isGlobalUnicast(InetAddress)` so it is unit-testable.
- The allowlist reaches the provider through a static holder set at startup by Spring (the SPI is instantiated by the
  JDK, not Spring); document this and make the holder empty by default (refuse nothing extra, allow nothing).

### T034 — `HttpsGuideFetcher`

- `HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).followRedirects(HttpClient.Redirect.NEVER)
  .connectTimeout(...)`; no cookie handler, no authenticator.
- Before each hop (initial and every redirect): scheme `https`; no userinfo; host non-null (IDN/underscore hosts give
  null → refuse); host not an IP literal; host lower-cased with a trailing dot stripped equals an allowlist entry
  exactly; resolve and check addresses with `AddressPolicy` (belt and braces with T033).
- Redirects: follow 301/302/303/307/308 manually, at most 3; `Location` resolved against the current URI and
  re-checked; any other 3xx → `REDIRECT_REFUSED`.
- One deadline for the whole exchange (default 10 s): compute `Instant deadline` once; each hop uses `sendAsync`
  with a `BodySubscriber` that counts bytes and fails at 1 MiB, and `orTimeout(remaining)`; cancel the future on
  timeout. DNS resolution counts against the deadline (run it under the same budget).
- Response: `Content-Encoding` absent or `identity` (else `ENCODED`); charset from `Content-Type` absent or UTF-8
  (else `NOT_UTF8`); decode with a strict UTF-8 decoder (malformed → `NOT_UTF8`).
- Seams for tests: package-visible constructor taking a `Resolver` (host → addresses) and a `Transport` (send one
  request to a given address, record the connect target) — the production `Transport` wraps `HttpClient`.

### T035 — `GuideFetchProperties` [P]

`@ConfigurationProperties("charter.guide-fetch")` record: `List<String> allowedHosts` (default empty → every URI
registration is refused with `HOST_NOT_ALLOWED`), `Duration timeout` (10 s), `DataSize maxSize` (1 MiB),
`int maxRedirects` (3). Wire the fetcher bean and the T033 holder in the packs module's configuration (follow how
`StorageProperties` is enabled).

### T036 — Offline matrix

`HttpsGuideFetcherTest` and `GlobalAddressResolverProviderTest` with the seams (optionally a JDK
`com.sun.net.httpserver.HttpsServer` on loopback with the resolver seam mapping an allowlisted name to it — only if
the loopback refusal is bypassed for that test through the seam, never in production):
- Address classes: every class in T033 refused, with a global address accepted next to it; mixed DNS answers (one
  global, one private) refused.
- Redirect to private address, to `http`, to a non-allowlisted host, 4th redirect → refused; 3 redirects accepted.
- DNS rebinding: resolver returns global on the check and private on the connect → the recorded connect target is the
  checked address or the request is refused (prove which with the transport seam).
- Size: exactly 1 MiB accepted, 1 MiB + 1 refused; timeout: a slow body over the deadline refused while headers
  arrive in time (shortened deadline in the test).
- Userinfo, IP literal, uppercase host and trailing dot normalisation, empty allowlist.
- Encoding and charset refusals; malformed UTF-8 refused.
- No `Authorization`/`Cookie` header ever sent (transport records headers).

### T037 — ArchUnit boundary [P]

`NetworkCodeBoundaryTest`: no class in `..packs.domain..` or `..packs.application..` depends on `java.net..` or
`java.net.http..`; include a known-bad fixture class (in a test package matched by the rule) that the rule must flag,
following the repo's bad-fixture pattern in `service/deployable/src/test/java/.../HexagonalArchitectureTest.java`.
(`java.net.URI` in the port signature: allow `java.net.URI` explicitly, or pass a `String` — choose one and document.)

## Definition of Done

- `./gradlew :packs:test` green offline; style clean; no new dependency.
- Mark T032–T037 done.

## Risks / reviewer guidance

- Reviewer: `NO_PROXY` set; redirects manual; deadline covers body and DNS; allowlist exact; address policy covers
  mapped/compatible IPv6; resolver provider only affects allowlisted hosts; tests never open external sockets.
