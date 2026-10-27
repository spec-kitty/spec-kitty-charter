---
title: Building the service
description: How to build and test the charter service locally, what each verification task checks, and how to change the contract or a dependency.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Guide
kind: How-To
related:
  - architecture/hexagonal-service
  - architecture/contract-and-dtos
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you build, test or change the contract.
---

# Building the service

How to build the charter service and run every check CI runs.

## Prerequisites

- **Java 25.** Gradle never downloads a toolchain (`org.gradle.java.installations.auto-download=false`).
  A machine without Java 25 can pass `-Pcharter.javaVersion=21` for a local build; CI never
  does.
- **A `spec-kitty-open-packs` checkout**, by default beside this repository
  (`../spec-kitty-open-packs`), or anywhere with `-PopenPacksDir=<path>`. The checkout must be
  clean and at `openPacksRef` in `gradle.properties` (currently
  `01edb4ebab308249a10ed4a4a01d77741ac1d14b`); the build refuses otherwise, and prints the
  command that fixes it (a `git fetch` and `git checkout` of the pinned commit, after you
  stash or clean the checkout).
- **The full Git history and tags.** The breaking-change check compares the contract with its
  last released version (see [the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline)) and refuses a shallow clone. In a clone
  you cannot deepen, pass `-PcontractBaseline=none` to skip that one check on purpose; CI
  never does. `verifyAll` in any shallow clone needs `-PcontractBaseline=none`, because the build
  refuses a shallow clone before it looks at tags. In a full clone, the check needs the
  `contract-charter-v*` tag of its baseline, merged into HEAD: the newest release the CHANGELOG
  records with the contract's major version. A release whose tag is not yet merged (as on a
  release PR) is skipped, and a new major version has no baseline, so it needs no tag. Fetch the
  tags if one is missing.
- **Network access on the first run.** The contract checks download vacuum and oasdiff at
  pinned versions and verify each archive's SHA-256 before using it (Linux and macOS, x86-64
  and arm64).

## Run everything

```bash
./gradlew verifyAll
```

`verifyAll` runs the `check` task of every project:

| Task | Checks |
|---|---|
| `:contracts:check` | The charter's own contract checks: `bundleContract`, `lintContract`, `lintFixtures`, `checkClosedResponseObjects`, `compatibilityFixtures` and `checkContractCompatibility` ([`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md) says what each one does) |
| `:charter-api:validateContract` | The split OpenAPI document parses and validates |
| `:charter-api:generateContract` | Spring interfaces and DTOs are generated; controllers that drift from the contract fail to compile |
| `:<module>:spotlessCheck` | Hand-written main and test sources are formatted by palantir-java-format |
| `:<module>:checkstyleMain`, `checkstyleTest` | The Checkstyle rules in `config/checkstyle/checkstyle.xml`; any violation fails |
| `:<module>:compileJava` | `-Xlint:all -Werror`, Error Prone, and NullAway on `@NullMarked` code (test sources get Error Prone without NullAway) |
| `:<module>:javadoc` | Javadoc of main sources with doclint on and warnings as errors |
| `:<module>:test`, `jacocoTestReport` | Unit tests of each module, and its coverage report |
| `:bootstrap:test` | The ArchUnit rules (dependency direction, a plain-Java core, `@NullMarked` packages) and every `@contract` scenario in `contracts/charter/scenarios/` |
| `:packs:openPacksRoundTrip` | The corpus checks over the pinned open-packs checkout: every directive, styleguide and paradigm file reads back equal after a write, and the vendored packs in `packs/` equal the pin. It fails, never skips, without a checkout |
| `:charter-reports:aggregateJavadoc` | One Javadoc site over the three hand-written service modules, doclint as errors ([Java API reference](/api/javadoc/)) |
| `:charter-reports:testCodeCoverageReport` | One JaCoCo report over the same modules |

The quality gates come from the `charter.hand-written` convention plugin, applied by every
hand-written module. The generated `charter-api` does not apply it: it applies
`charter.contract-api`, which reaches `charter.java-base` through `charter.spring-conventions`,
so it is exempt by construction and still compiles. A new hand-written module under `service/` must
also be listed in `service/charter-reports/build.gradle.kts`; the build fails, naming it,
until it is.

Not in `verifyAll`: mutation testing. `./gradlew pitest` runs PIT on the hand-written
service modules; `.github/workflows/mutation-nightly.yml` runs it nightly, and no pull
request runs it.

Not in `verifyAll` either, yet: the whole-pack round-trip gate (below).

## The whole-pack round-trip gate

```bash
./gradlew :packs:wholePackRoundTrip          # needs the pinned spec-kitty-open-packs checkout
```

The gate ([#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187), epic
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)) reads every file of the pinned built-in pack
into the domain, writes it to a fresh directory through the storage service, reads it back, and compares each
written file with its source **by meaning**, as [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
defines it: quoting, comments, layout and key order do not count, and for a declared field an absent value, `null`
and an empty list or map are the same (inside an open map such as a styleguide's `tooling` they are data); a missing,
extra or changed key or value counts. Numbers compare by value. Graph-shard edges compare as a set (order and
identical repeats do not count) and nodes by URN.

It lands red first. `service/packs/src/test/resources/whole-pack/expected-red.yaml` lists what does not round-trip
yet: a whole kind that has no reader and writer (`kinds:`), or a single file of a modelled kind with every place it
differs (`files:`, `at:` lists each difference path, joined by ` ; `), each with a reason and the issue that owns it.
Because `at:` holds every difference, a new loss in a file that is already expected red still fails the gate. `exclusions.yaml` next to it is the gate's
own append-only list of files it does not compare. The gate passes only when the result matches both lists exactly:

- an unlisted red file fails it (`UNEXPECTED_RED`), and so does a listed file whose differences changed
  (`CHANGED_RED`);
- a listed file or kind that now round-trips fails it too (`UNEXPECTEDLY_GREEN`): remove the entry in the same
  change that turns it green;
- stale entries, an exclusion over a modelled file, a `kinds:` entry for a modelled kind and a modelled kind with no
  files fail it, so it cannot pass vacuously.

Where the gate still differs from ADR-0036, on purpose and only towards false reds, never false greens:

- written artifacts are paired with their source by kind and id (the binding's id key: `profile-id` for an agent
  profile), not path for path, because the store writes a new
  artefact at the pack root; any written file nothing pairs with fails the gate;
- an absent field is not yet treated as its schema default;
- the open maps are listed per kind in `MeaningComparator` (styleguide `tooling` and `structural_lint_config`, agent-profile
  `excluding`), not derived from the schemas; a kind that brings an open map adds it there.

The report, per kind and per file, is written to `service/packs/build/reports/whole-pack/report.md`. CI runs the gate
in its own workflow, `.github/workflows/whole-pack.yml`, which is not part of `CI gate / gate` and not a required
check until the gate is green.

To fix formatting rather than report it, run `./gradlew spotlessApply`.

## Exceptions to a gate

`config/checkstyle/suppressions.xml` is the one register of exceptions to the hand-written
gates. It holds no entry today. An entry names the rule, the file, the reason and a sunset
date; no gate is relaxed anywhere else, and blanket suppressions are not allowed.

## Change the contract

1. Edit the split files under `contracts/charter/` and list new files in the directory's
   `_index.yaml`.
2. Write or change the scenario in `contracts/charter/scenarios/` that pins the behavior.
3. Add an entry to `contracts/charter/CHANGELOG.md`; a scenario change gets one too.
4. Run `./gradlew :contracts:check`, then `./gradlew verifyAll`.

The contract checks follow the conventions in
[`contracts/lint/README.md`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md).
A new lint rule lands with at least one planted fixture that fails it, and only it; that page
describes the steps.

Adding a property to a response schema is a breaking change, because response schemas are
closed ([Contract and DTOs](/architecture/contract-and-dtos/#versioning)).

## Release a contract version

Releasing a contract version is a short procedure with its own rules, kept in one place:
[the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline). Follow it from a topic branch, and run
`./gradlew :contracts:check` before you open the pull request.

`contracts/_shared/` started as a copy of core's shared vocabulary. This repository's copy is
canonical ([ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/)): change
it here when there is reason to, and list the change in `contracts/_shared/VENDORED.md`.

## Change a dependency

Versions live in `gradle/libs.versions.toml`, tool plugins (Spotless, Error Prone, PIT)
included; convention plugins read them from the catalog. Dependency verification is strict,
so a new or changed artifact needs its checksum recorded:

```bash
./gradlew --write-verification-metadata sha256 verifyAll
```

Review the diff of `gradle/verification-metadata.xml`; never edit it by hand. The vacuum and
oasdiff binaries are not Gradle dependencies: their versions and SHA-256 checksums are pinned
in `contracts/build.gradle.kts`.

A module that is not a Spring module may still import the Spring Boot BOM as a platform, to
pin a transitive dependency to the version the service uses. `packs` does this for
the Jackson and slf4j versions that networknt's JSON Schema validator brings in.
