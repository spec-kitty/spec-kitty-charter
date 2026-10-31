# Charter contract lint rules

The charter contract's own conventions, written as a Spectral-format ruleset (`ruleset.yaml`) and run
by [vacuum](https://quobix.com/vacuum/). The charter owns its contract and these checks (operator
decision, 2026-10-18, DM 01M57WH7P0QZWHKYGBA2R0MA3K). Some conventions are ones core's contracts also
follow (problem responses, closed responses, cited properties); each is expressed here in the
charter's own terms and from the charter's own contract shape, nothing is copied from core, and core
adapts to the charter through its integration seam.

## Running the checks

```bash
./gradlew :contracts:check                 # all of them; verifyAll runs them too
./gradlew :contracts:lintContract          # the contract against the rules
./gradlew :contracts:lintFixtures          # each rule against its planted fixtures
./gradlew :contracts:compatibilityFixtures # the breaking-change check against its planted pairs
./gradlew :contracts:checkClosedResponseObjects # the closure checker against the bundle
./gradlew :contracts:checkRefusalExamples      # the raised-code check of the refusals against the bundle
```

`contracts/build.gradle.kts` downloads vacuum and oasdiff at pinned versions, checks each archive's
SHA-256 before extracting it (downloads time out after 30 s without a connection or 60 s without
data), re-verifies the installed archive and executable against the pin on every build, also after
a Gradle build cache restore (`verifyVacuum`, `verifyOasdiff`), and runs seven tasks:

| Task | What it checks |
|---|---|
| `bundleContract` | Bundles `charter/openapi.yaml` into one file with every `$ref` inlined, `build/contracts/contract/charter-openapi.yaml` with a `.sha256` beside it, and checks the bundle is valid OpenAPI (`oasdiff validate`, warnings fail). This is the file core vendors. |
| `lintContract` | Lints the bundle with `ruleset.yaml`. Any violation fails, and so does a ruleset vacuum loads with a different number of rules than it declares. |
| `lintFixtures` | Every rule has at least one fixture, `fixtures/<rule id>.yaml` or `fixtures/<rule id>.<case>.yaml`, that fails that rule and no other; `fixtures/clean.yaml` fails none. Each fixture goes through the same bundle step as the contract first, so the rules are proven on the inlined shape they judge. |
| `checkClosedResponseObjects` | Runs the closure checker (see [Closed responses](#closed-responses)) over the bundle. Any violation fails, one line each with its JSON Pointer. |
| `checkRefusalExamples` | Runs the raised-code check (see [Refusal codes](#refusal-codes)) over the bundle. Any violation fails, one line each with its JSON Pointer. |
| `compatibilityFixtures` | Each `../compat/fixtures/<case>/` holds `base.yaml` and `revision.yaml`; a `breaking-*` case must fail the breaking-change comparison and a `compatible-*` case must pass it. |
| `checkContractCompatibility` | The release gate, then `oasdiff breaking` of the bundle against the bundle of the baseline release it names (see [The released baseline](#the-released-baseline)). |

The lint runs on the bundle, so a property that references a schema is judged with that schema's
content: a description or citation on a referenced enum or scalar file holds wherever it is used.

## The released baseline

The breaking-change comparison runs `oasdiff breaking --flatten-allof --fail-on ERR
--severity-levels ../compat/severity-levels.txt`. Without `--flatten-allof`, oasdiff judges each
`allOf` part alone and reports a refusal code added to the composed `CharterRefusal` as a warning
only; consumers branch on those codes, so it must fail.

**Adding a response property is breaking** (DM 01M5986E711JT296MA6NDN91MG). Every response schema
is closed (see [Closed responses](#closed-responses)), so a strict consumer, such as core's ACL
DTOs, may reject a member it does not know. oasdiff reports an added response property, optional
or required, as info; `../compat/severity-levels.txt` raises `response-optional-property-added`
and `response-required-property-added` to `ERR`. The planted pairs
`breaking-response-property-added` and `breaking-response-required-property-added` prove it. So
the check fails a new response field against a baseline of the same major version: it ships in a
new major version.

Which release is the baseline is decided by the release gate, `ReleaseGate`
([`ReleaseGate.java`](../src/main/java/ai/speckitty/charter/contracts/compat/ReleaseGate.java)), whose
class Javadoc is the authority on the rules and their messages (DM 01M5986KJ7B18K7GQ25E47J989). In
short: after checking the CHANGELOG against `info.version` and the git tags, it names the newest
release of the same line whose tag is merged into HEAD, and a release with no such tag yet is
*pending* and is compared against the previous one. A released contract is tagged
`contract-charter-v<version>` (`contract-charter-v0.1.0`).

The line is the major version from 1.0.0, and the major and minor while the major is 0
(ADR-0022): the contract is published as 0.x while it iterates with core, a 0.x minor may break
and a patch may not. So `0.2.1-SNAPSHOT` is compared against the newest 0.2 release, and
`0.2.0-SNAPSHOT` with only 0.1 releases has no baseline and passes. A bump of the line (a 0.x
minor bump, `0.3.0` to `1.0.0`, or a major bump after that) needs a `### Breaking` subsection in
its CHANGELOG section, and the check then shows the oasdiff report against the previous release
for information only; it does not fail on it.

A missing baseline is a stated fact, never an artifact of the checkout:

- In a shallow clone the check fails: a released contract's tag may simply not have been fetched.
  CI checks out the full history and tags (`fetch-depth: 0`).
- `-PcontractBaseline=none` skips the comparison on purpose (for a shallow clone you cannot deepen)
  and says so; the gates that read no git history still run. The build refuses it when the
  environment variable `CI` is `true`: CI must compare against the released baseline.

### Releasing a contract version

This is the one release procedure.

1. In a pull request, set `info.version` of `../charter/openapi.yaml` to the release (`1.1.0`) and
   turn the top CHANGELOG heading into `## 1.1.0` (adding `### Breaking` for a new major, or for
   a new minor while the major is 0). The
   check reports "pending release 1.1.0" while no tag for it is merged into HEAD, and compares
   against the previous release.
2. After the merge, tag `main` at the merge commit: `git tag contract-charter-v1.1.0 <merge commit>`
   and push the tag. From then on the check compares against that tag, and a commit after it under
   the `## 1.1.0` heading is refused until step 3.
3. Open the next version: a new top heading `## 1.2.0-SNAPSHOT` and `info.version: 1.2.0-SNAPSHOT`,
   together in one change.

Pushing the tag in step 2 publishes the release: `.github/workflows/contract-release.yml` rebuilds
the bundle at the tag and attaches `charter-openapi.yaml`, `charter-openapi.yaml.sha256` and
`charter-scenarios.tar.gz` (the contract scenarios) to a GitHub Release of the tag. It refuses,
and publishes nothing, unless the tag, `info.version` and the top heading name one released
version (`./gradlew :contracts:checkReleaseTag -PreleaseTag=<tag>`), the contract checks pass and
the tagged commit is on `main`; it also refuses a tag that moved while the assets were built.
The workflow runs from the tagged commit, so these checks catch mistakes, not someone who can push
tags: a tag ruleset on `contract-charter-v*` decides who can publish.

Nothing publishes without a tag. `service.yml` rehearses the whole path on every change
(`scripts/release/rehearse-contract-release.sh`): in a throwaway clone with no remote it expects
the unreleased commit to be refused and a synthetic release to yield the three assets. To
rehearse a real release, run `scripts/release/contract-release-assets.sh <tag> <out-dir>`, or the
workflow by hand (workflow_dispatch) with the tag, on the release pull request's branch after
step 1: both assemble the assets and create no Release. On `main` before step 1 they refuse,
because the contract there is a `-SNAPSHOT`.

## The conventions

vacuum's own OpenAPI rules are switched off; only the rules below run, so a vacuum upgrade cannot add
a rule silently.

| Rule | Convention | Why |
|---|---|---|
| `operation-id` | Every operation has an `operationId`. | Generated clients and servers name their methods after it. |
| `operation-operationId-unique` | No two operations share one. | vacuum's built-in check, switched on by name: uniqueness across operations cannot be written with the core functions. |
| `operation-tagged` | Every operation has a tag. | Generated interfaces are grouped by tag. |
| `error-responses-are-problems` | A read answers 200 and a registration 200 or 201 (ADR-0018 D10 as amended 2026-10-25): every other response, `default` included, is `application/problem+json` and nothing else, with the shared `Problem` or a schema whose first `allOf` part is the `Problem` (as `CharterRefusal` extends it). A `304` is the one exception: it has no body. | Consumers read one error shape and branch on its `code`. |
| `not-modified-has-no-body` | A `304 Not Modified` declares no `content`. | RFC 9110 (section 15.4.5) forbids a 304 a body; the answer to a conditional read (`getOpenApiDocument`'s `If-None-Match`) is its headers. |
| `charter-headers-on-every-response` | Every response of every operation, `default` included, declares the `Charter-Contract-Version` and `Charter-Worktree` headers. `Charter-Source-Fingerprint` is governed by the two rules below. | ADR-0018 D11: the always-on headers are declared on every response, so the contract says so on each one and the shared `Problem` response cannot lose them. A fixture gives every response the two headers, written once per fixture under the YAML anchor `&charter-headers`. |
| `fingerprint-on-verified-reads` | The `200` of every `GET` other than `/` and `/openapi.yaml` declares `Charter-Source-Fingerprint`, with `required: true`. The rule is written by path shape, not by `operationId`. | #175, ruling 4: every such `GET` is a verified read, and its 200 carries the digest that read verified. `/` and the contract document read no pack. A fixture's verified reads use the YAML anchor `&read-headers`: the two always-on headers plus the required fingerprint. |
| `fingerprint-only-on-verified-reads` | No other response declares `Charter-Source-Fingerprint`: not a write's (`PUT`, `POST`, `DELETE`, `PATCH`), not a `HEAD`, `OPTIONS` or `TRACE` answer, not a `GET`'s non-200 (refusals, `304`, `default`), not `/openapi.yaml`'s, and not a shared component response. | #175, ruling 4: the service sends the fingerprint on no other answer, so the contract declares it on no other answer. A `GET` 200 that declares no headers at all is not this rule's or `fingerprint-on-verified-reads`' to report: `charter-headers-on-every-response` fails it first, for the missing always-on headers. |
| `success-schemas-carry-links` | The schema of every 2xx `application/json` response requires `_links`. | ADR-0019 D3 and FR-008: every success answer can be navigated through the one `Links` component. The `application/yaml` answer of `/openapi.yaml` is not JSON and is not judged. |
| `success-responses-have-link-examples` | Every 2xx `application/json` response has an `example` or `examples`, and every example it has carries `_links` at its top level. | The relation keys are checked in examples (the next rule), so every success response must have one, or the key check would pass by having nothing to read. Written with the core `schema` function; no Java check is needed. |
| `link-relations-are-known` | Every key of a `_links` in an example (`examples.*.value` or `example`, at any depth) is a relation of the table in `docs/development/hypermedia-hateoas.md`. | FR-011: `Links` is an open map, so a relation key appears only in answers and examples, never in a schema. The rule's `enum` is that table's first column; `RelationTableParityTest` (`contracts` tests) fails when the two differ, so the guide stays the source. The scenario hook checks the keys of every answer against the same table. |
| `property-described` | Every schema property has a description. A bare `const` or `enum` refinement holding nothing else (an `if` condition, or an `allOf` part narrowing a property) is exempt: it narrows a property described where it is declared. | The contract is read by people as well as generators. |
| `enum-snake-case` | Enum values are snake_case. The pack level `built-in` is the one value with a hyphen. | One wire spelling for every code; `built-in` is the pack level as packs declare it. |
| `pack-level-token-spelling` | The pack level `built-in` is spelled `built-in`, never `built_in`, `builtin` or another case. | `built_in` would pass `enum-snake-case` and still be a second spelling of one pack level. |
| `resource-property-cited` | Every property of a titled object schema carries `x-source` or `x-derived`. The shared `Problem` is exempt. | Each field says where its value comes from, so a reviewer can check it against the producer. |
| `x-source-shape` | `x-source` is `{path, symbol}`: a path this repository tracks and the symbol that produces the value. | Citations stay checkable. |
| `x-derived-shape` | `x-derived` is `{rule, inputs}`: how the value is computed and the contract parts it is computed from (at least one). | A computed field names its rule and inputs. |
| `x-provisional-shape` | `x-provisional` is `{open_decision}`, naming what is not decided yet. | An unstable part says which decision will settle it. |

## Closed responses

Every response schema is closed (`additionalProperties: false`), so a new response field is a
deliberate contract change that the breaking-change check sees. A Java checker, `ClosureChecker`
(run by `checkClosedResponseObjects` over the bundle as part of `check`), enforces it, and its
[class Javadoc](../src/main/java/ai/speckitty/charter/contracts/closure/ClosureChecker.java) is the
authority on what closed means and what the checker refuses, with its cases as fixtures in
`contracts/src/test/resources/closure/`.

## Refusal codes

A client learns which refusal codes it may meet from a refusal's examples and its description, so neither
may name a code the service does not raise (#176). A Java check, `RefusalExamples` (run by
`checkRefusalExamples` over the bundle as part of `check`), reads the raised codes from the `x-refusal`
map of `CharterRefusalCode` (the codes whose `raised` is `true`) and fails a `CharterRefusal` example whose
`code` is not one of them, and a response description that names, as a whole word, a code of that map
that is not raised. Its [class Javadoc](../src/main/java/ai/speckitty/charter/contracts/refusal/RefusalExamples.java)
is the authority, with its cases as fixtures in `contracts/src/test/resources/refusal/`.

## Adding or changing a rule

1. Add the rule to `ruleset.yaml` with a one-line `description` of the convention.
2. Add `fixtures/<rule id>.yaml`: a copy of `fixtures/clean.yaml` with one planted violation of the new
   rule and nothing else, and a first-line comment saying what is planted. Fixtures are written inline
   (no `$ref`), the shape of the bundle. A second case of the same rule is `<rule id>.<case>.yaml`.
   The fixtures share their headers through two YAML anchors: `&read-headers` on a `GET` 200 (the
   always-on headers and the required fingerprint) and `&charter-headers` on every other response (the
   always-on headers only). Keep that split in a new fixture, or it fires a fingerprint rule as well.
   Each fixture's two `200` JSON schemas require `_links` (the `Links` map of a closed `Link`, written once under
   the anchor `&links`) and each `200` JSON response carries an example whose `_links` holds `self`. Keep both in a
   new fixture, or it fires a link rule as well.
3. Run `./gradlew :contracts:check`. `lintFixtures` fails until the rule and its fixture agree, and
   `lintContract` shows whether the contract already follows the rule.
4. Add the rule to the table above.

A rule never lands without at least one planted fixture per rule, and a fixture never names a rule
the ruleset lacks.
