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
```

`contracts/build.gradle.kts` downloads vacuum and oasdiff at pinned versions, checks each archive's
SHA-256 before extracting it (downloads time out after 30 s without a connection or 60 s without
data), and runs six tasks:

| Task | What it checks |
|---|---|
| `bundleContract` | Bundles `charter/openapi.yaml` into one file with every `$ref` inlined, `build/contracts/contract/charter-openapi.yaml` with a `.sha256` beside it, and checks the bundle is valid OpenAPI (`oasdiff validate`, warnings fail). This is the file core vendors. |
| `lintContract` | Lints the bundle with `ruleset.yaml`. Any violation fails, and so does a ruleset vacuum loads with a different number of rules than it declares. |
| `lintFixtures` | Every rule has at least one fixture, `fixtures/<rule id>.yaml` or `fixtures/<rule id>.<case>.yaml`, that fails that rule and no other; `fixtures/clean.yaml` fails none. Each fixture goes through the same bundle step as the contract first, so the rules are proven on the inlined shape they judge. |
| `checkClosedResponseObjects` | Runs the closure checker (see [Closed responses](#closed-responses)) over the bundle. Any violation fails, one line each with its JSON Pointer. |
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

Which release is the baseline is decided by the release gate, the Java class
`contracts/src/main/java/ai/speckitty/charter/contracts/compat/ReleaseGate.java`, whose class
Javadoc is the authority (DM 01M5986KJ7B18K7GQ25E47J989). A released contract is tagged
`contract-charter-v<version>`, and its version is a `## <version>` heading of
`../charter/CHANGELOG.md` without `-SNAPSHOT` (`## 1.2.0`, `## v1.2.0` and `## [1.2.0] - <date>`
are all read). The gate:

- **Version gate.** `info.version` of `../charter/openapi.yaml` must equal the top CHANGELOG
  heading, `X.Y.Z-SNAPSHOT` or a release. A mismatch fails, naming both.
- **Only tags merged into HEAD count** (`git tag --merged HEAD`). An older commit or a stale branch
  does not see a later release and stays green. Every merged tag must be a release the CHANGELOG
  records; an unrecorded one fails, because comparing against an older baseline would hide it.
- **Pending release.** While the top heading is a release whose tag is not yet merged into HEAD
  (the release pull request), the check compares against the previous release, or reports no
  baseline when there is none, and says "pending release <version>". Once `main` is tagged, the
  release is compared against its own tag.
- **Same major only.** The baseline is the newest release with the major version of `info.version`;
  its tag must be merged into HEAD, or the check fails rather than fall back to an older one. With
  no such release there is no baseline and the check passes, saying so: `2.0.0-SNAPSHOT` with only
  1.x releases is not compared.
- **A major bump lists what breaks.** When the major version of `info.version` is above that of the
  previous release, the top CHANGELOG section must contain a `### Breaking` subsection.

A missing baseline is a stated fact, never an artifact of the checkout:

- In a shallow clone the check fails: a released contract's tag may simply not have been fetched.
  CI checks out the full history and tags (`fetch-depth: 0`).
- `-PcontractBaseline=none` skips the comparison on purpose (for a shallow clone you cannot deepen)
  and says so; the version gate and the Breaking subsection still run, since they read no git
  history. The build refuses it when the environment variable `CI` is `true`: CI must compare
  against the released baseline.

To release a contract version:

1. In a pull request, set `info.version` of `../charter/openapi.yaml` to the release (`1.1.0`) and
   turn the top CHANGELOG heading into `## 1.1.0` (adding `### Breaking` for a new major). The
   check reports "pending release 1.1.0" and compares against the previous release.
2. After the merge, tag `main` at the merge commit: `git tag contract-charter-v1.1.0 <merge commit>`
   and push the tag. From then on the check compares against that tag.
3. Open the next version: a new top heading `## 1.2.0-SNAPSHOT` and `info.version: 1.2.0-SNAPSHOT`,
   together in one change.

Publishing the released bundle from a tag-triggered workflow is a follow-up.

## The conventions

vacuum's own OpenAPI rules are switched off; only the rules below run, so a vacuum upgrade cannot add
a rule silently.

| Rule | Convention | Why |
|---|---|---|
| `operation-id` | Every operation has an `operationId`. | Generated clients and servers name their methods after it. |
| `operation-operationId-unique` | No two operations share one. | vacuum's built-in check, switched on by name: uniqueness across operations cannot be written with the core functions. |
| `operation-tagged` | Every operation has a tag. | Generated interfaces are grouped by tag. |
| `error-responses-are-problems` | The charter's operations are reads, so 200 is their only success: every other response, `default` included, is `application/problem+json` and nothing else, with the shared `Problem` or a schema whose first `allOf` part is the `Problem` (as `CharterRefusal` extends it). | Consumers read one error shape and branch on its `code`. |
| `property-described` | Every schema property has a description. A bare `const` or `enum` refinement holding nothing else (an `if` condition, or an `allOf` part narrowing a property) is exempt: it narrows a property described where it is declared. | The contract is read by people as well as generators. |
| `enum-snake-case` | Enum values are snake_case. The pack level `built-in` is the one value with a hyphen. | One wire spelling for every code; `built-in` is the pack level as packs declare it. |
| `pack-level-token-spelling` | The pack level `built-in` is spelled `built-in`, never `built_in`, `builtin` or another case. | `built_in` would pass `enum-snake-case` and still be a second spelling of one pack level. |
| `resource-property-cited` | Every property of a titled object schema carries `x-source` or `x-derived`. The shared `Problem` is exempt. | Each field says where its value comes from, so a reviewer can check it against the producer. |
| `x-source-shape` | `x-source` is `{path, symbol}`: a path this repository tracks and the symbol that produces the value. | Citations stay checkable. |
| `x-derived-shape` | `x-derived` is `{rule, inputs}`: how the value is computed and the contract parts it is computed from (at least one). | A computed field names its rule and inputs. |
| `x-provisional-shape` | `x-provisional` is `{open_decision}`, naming what is not decided yet. | An unstable part says which decision will settle it. |

## Closed responses

Every response schema is closed (`additionalProperties: false`), so a new response field is a
deliberate contract change that the breaking-change check sees, never something a server can add
unannounced. This convention is not a rule of `ruleset.yaml`: JSONPath cannot classify composed
schemas (`allOf`/`anyOf`/`oneOf`, tuples, boolean schemas, constraint-only members) recursively, and
five review rounds each found a new hole in the Spectral version (DM 01M584Q3NNK0B78H9NKHV7VJDK).

It is a small Java checker in this project, `contracts/src/main/java/ai/speckitty/charter/contracts/closure/`,
run by `checkClosedResponseObjects` over the bundle as part of `check`. The class Javadoc of
`ClosureChecker` is the authority on what closed means; it classifies every response schema
positively and recursively, response headers included, and fails closed on any keyword it does not
understand, on any key of a path item, operation, response, header or media type it does not know
(`GET:`, `responsez:`), on `webhooks` and `callbacks`, which the contract does not use, on malformed
input, on a response media type with no schema, on a run that judges no response schema at all, and
on any document that is not OpenAPI 3.1 in the OAS default JSON Schema dialect. Its cases, the
shapes it must report and the shapes it must pass, are fixtures in
`contracts/src/test/resources/closure/` with one test each (`ClosureCheckerTest`), and it also
passes `fixtures/clean.yaml` of this ruleset.

## Adding or changing a rule

1. Add the rule to `ruleset.yaml` with a one-line `description` of the convention.
2. Add `fixtures/<rule id>.yaml`: a copy of `fixtures/clean.yaml` with one planted violation of the new
   rule and nothing else, and a first-line comment saying what is planted. Fixtures are written inline
   (no `$ref`), the shape of the bundle. A second case of the same rule is `<rule id>.<case>.yaml`.
3. Run `./gradlew :contracts:check`. `lintFixtures` fails until the rule and its fixture agree, and
   `lintContract` shows whether the contract already follows the rule.
4. Add the rule to the table above.

A rule never lands without at least one planted fixture per rule, and a fixture never names a rule
the ruleset lacks.
