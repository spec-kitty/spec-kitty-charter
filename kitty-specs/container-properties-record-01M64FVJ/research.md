# Research: Container profile properties record

A grounding squad ran before the spec: one lens on ADR constraints (ADRs 0011, 0034, 0038, 0041 and the service
lifecycle page), one on build and test patterns. Their findings are folded in below.

## R1 — What the record holds, and the start-up validator (resolved on #228)

- **Decision**: the record holds `allowed-hosts` only, the one key ADR-0041 places in `charter.container.*`
  (decision 6). Keys outside the namespace are not record components (decision 2). Whether #228 also builds the
  start-up validator for decisions 4 to 6 is the operator's call.
- **Rationale**: issue #228 predates ADR-0041 and lists "bind address, port, token/secret references" for the record.
  The ADR moves those out of the namespace. The brief says to follow the ADR and to raise an ambiguity on #228
  rather than decide it.
- **Alternatives considered**:
  - Building the validator here: deferred to the operator (#228, issuecomment-6505452290).
  - Putting `server.*` and the token in the record: refused by ADR-0041 decisions 2 and 8.
- **Resolution (operator, #228 issuecomment-6506747587)**: the start-up check is in scope. It is built as
  `ContainerStartupCheck` (WP03). D4's local loopback rule goes to #231, D3's token message to #229, and the guard's use
  of `allowed-hosts` becomes a follow-up issue. Decision `01M64FW0PR3KV90HJ3S8K4201P` is resolved.

## R2 — The validation starter switches on MVC request validation (accepted on #228)

- **Finding**: the generated API interfaces carry `@Valid` bodies and `@NotNull` DTO fields
  (`useBeanValidation=true`). With Hibernate Validator on the classpath, Spring MVC validates them before the
  controller runs. Measured against the real server, with and without the starter:
  - a body missing `title` changes from 500 `internal_error` (an NPE) to 400 `Bad Request`;
  - a relationship without `from` changes the same way, from 500 to 400;
  - a blank title stays 422 `validation_failed`;
  - a valid body stays 201.
- **Second effect (squad, measured):** the generated interfaces are `@Validated`, so Boot's
  `MethodValidationPostProcessor` now wraps every controller in a CGLIB proxy (confirmed:
  `DirectiveController$$SpringCGLIB$$1`). A `@Size(min = 1)` path-variable breach would surface as a
  `ConstraintViolationException`. No handler maps that exception, so it would be answered 500 `internal_error`.
  Today it cannot happen: an empty path segment does not match the route and answers 404. This is a latent trap,
  left for a follow-up that maps the exception to a 400 or pins the 404.
- **Decision (default, pending the operator)**: keep the starter, as ADR-0041 decision 2 says. Pin the 400 in
  `ProblemResponsesTest`, record it in the changelog, and leave the contract as it is: each operation's `default`
  Problem response admits it.
- **Alternatives considered**: a no-op MVC validator, so that only configuration-properties validation runs. Not
  chosen: it is more code, and it keeps a client error answered as a server error.
- **Resolution (operator, #228 issuecomment-6506747587)**: keep the starter and the 500-to-400 change, pin it in one
  test, record it in the changelog, and leave the contract unchanged.

## R3 — Binding only under the profile

- **Decision**: `@Profile("container")` goes on `ContainerWiring`, which carries
  `@EnableConfigurationProperties(ContainerProperties.class)`. The application's component scan registers it, as it
  does the other deployable wiring classes.
- **Rationale**: the namespace stays unbound without the profile, so an invalid or absent value cannot affect the
  local start.
- **Alternatives considered**:
  - `@ConditionalOnProperty`: it does not express a profile.
  - Adding the record to `CharterServiceApplication`'s `@EnableConfigurationProperties`: that binds it always, and
    an absent required list would then fail the local start.
- **Deviation from the issue body**: it says "register it alongside the existing enablement in
  `CharterServiceApplication`". Here the scan registers it, and a test pins that the shipped application binds the
  record under the profile.

## R4 — The failure names the canonical key

- **Finding**: Spring's `BindValidationException` names the field by its Java name, `allowedHosts`, along with its
  error codes. It never uses the canonical `charter.container.allowed-hosts`.
- **Decision**: each constraint's `message` names `charter.container.allowed-hosts`. The index of a blank entry
  comes from Spring's field path (`allowedHosts[1]`).

## R5 — Supply chain (DIRECTIVE_051)

- **Added**:
  - `org.springframework.boot:spring-boot-starter-validation` and `spring-boot-validation` 4.1.1 (BOM-managed);
  - `org.hibernate.validator:hibernate-validator` 9.1.3.Final;
  - `org.jboss.logging:jboss-logging` 3.6.3.Final;
  - `com.fasterxml:classmate` 1.7.3.

  `jakarta.validation-api` 3.1.1 and `tomcat-embed-el` 11.0.24 were already verified.
- **Controls**:
  - Every version comes from the Spring Boot BOM; none is pinned by hand.
  - The SHA-256 entries are regenerated by Gradle, never hand-edited, and the diff is reviewed: only these
    components are added.
  - These are the reference Jakarta Validation implementation and its own dependencies.
  - ADR-0041 decision 2 names this dependency.
- **Authenticity**: the SHA-1 of each new jar matches the published `.sha1` on Maven Central (fetched through
  Google's mirror): hibernate-validator, jboss-logging, classmate, spring-boot-validation and the starter.
- **Adversarial pass**: the pre-PR squad reviewed the dependency together with R2 (see R6).

## R6 — Pre-PR squad: findings and dispositions

The cast was architect-alphonso (ADR fidelity), reviewer-renata (tests) and qa-strategist-quinn (supply chain and
secrets). Their verdicts: approve-with-changes, approve-with-changes and approve.

| Finding | Severity | Disposition | Evidence |
|---------|----------|-------------|----------|
| Whitespace-only entry never tested: `withPropertyValues` trims values | MEDIUM | accepted | `ContainerPropertiesTest`: the whitespace entry goes through its own `MapPropertySource`; swapping `@NotBlank` for `@NotEmpty` turns it red |
| Controllers become method-validation proxies; a `@Size` breach on a path variable would be a 500 | MEDIUM | accepted (documented); the fix is deferred with rationale | R2 and the changelog. The case is unreachable today (404); the follow-up is noted in the PR hand-off |
| An empty value could bind as `null` rather than `[]` | LOW | accepted | `anEmptyValueBindsAsAnEmptyListSoTheRequiredRuleIsWhatRefusesIt` |
| The 400 pin does not prove its cause | LOW | accepted | `ProblemResponsesTest` asserts `MethodArgumentNotValidException`; the class Javadoc lists the case |
| Test names do not all follow one pattern | LOW | deferred with rationale | cosmetic; the names state behaviour in the style of `CharterWiringTest` |
| `PROFILE` constant lives on the properties record | LOW | deferred with rationale | revisit when #229 or #231 needs the profile name outside the record |
| Spring's start-up report prints rejected values | LOW | accepted | `ContainerProperties` Javadoc: never a secret component |
| Registry authenticity not recorded | LOW | accepted | R5, Maven Central SHA-1 cross-check |

## R7 — WP03 delta squad: findings and dispositions

The cast was architect-alphonso and reviewer-renata. Both returned approve-with-changes.

| Finding | Severity | Disposition | Evidence |
|---------|----------|-------------|----------|
| The value-leak assertion checks the wrapper message, not the root cause | MEDIUM | accepted | `ContainerStartupCheckTest` asserts on the whole stack trace (`Throwables.getStackTrace`) for the missing-root and file cases |
| In a web start the check runs after the filter beans built in `onRefresh`, so `StorageConfiguration` or `CharterWiring` may refuse first, with their own messages | MEDIUM | deferred with rationale | Fail-closed holds: connectors bind only at `finishRefresh`, after every singleton, so nothing is served. The earlier refusals also name their property. Moving the check into an environment listener buys only message framing, at the cost of more code and a web-start test that needs a fixed port in CI |
| The development-flag loop does not say which value failed | LOW | accepted | `@ParameterizedTest @ValueSource({"true", "false"})` |
| A whitespace-only root and an `InvalidPathException` root are untested | LOW | deferred with rationale | Both are defensive branches; every realistic value is covered |
| A non-integer `server.port` would be quoted by the conversion error | LOW | deferred with rationale | In a web start, Boot's own `ServerProperties` binding reports it first, with the value; a port is not a secret |
| Negative ports are not refused | INFO | deferred with rationale | Outside the operator's ruling (port 0 only) |
