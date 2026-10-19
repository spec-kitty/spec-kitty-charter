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
| `checkContractCompatibility` | `oasdiff breaking` of the bundle against the bundle of the last released contract (see below). |

The lint runs on the bundle, so a property that references a schema is judged with that schema's
content: a description or citation on a referenced enum or scalar file holds wherever it is used.

## The released baseline

The breaking-change comparison runs `oasdiff breaking --flatten-allof --fail-on ERR`. Without
`--flatten-allof`, oasdiff judges each `allOf` part alone and reports a refusal code added to the
composed `CharterRefusal` as a warning only; consumers branch on those codes, so it must fail.

The baseline is the tag `contract-charter-v<version>` of the newest released heading (`## <version>`,
without `-SNAPSHOT`) of `../charter/CHANGELOG.md`. A missing baseline is a stated fact, never an
artifact of the checkout:

- In a shallow clone the check fails: a released contract's tag may simply not have been fetched.
  CI checks out the full history and tags (`fetch-depth: 0`).
- When the CHANGELOG records a released version whose tag is not in the clone, the check fails.
- When a `contract-charter-v*` tag exists that the CHANGELOG does not record as released, the check
  fails.
- When the CHANGELOG records no released version and the full clone has no such tag, there is no
  baseline: the check prints "No released baseline" with that reason and passes.
- `-PcontractBaseline=none` skips the check on purpose (for a shallow clone you cannot deepen) and
  says so. Never set it in CI.

To release a contract version: turn its `-SNAPSHOT` heading in the CHANGELOG into `## <version>` and tag
that commit `contract-charter-v<version>`.

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
| `enum-snake-case` | Enum values are snake_case. The pack tier `built-in` is the one value with a hyphen. | One wire spelling for every code; `built-in` is the tier as packs declare it. |
| `tier-token-spelling` | The built-in tier is spelled `built-in`, never `built_in`, `builtin` or another case. | `built_in` would pass `enum-snake-case` and still be a second spelling of one tier. |
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
positively and recursively, and fails closed on any keyword it does not understand, on malformed
input, on a response media type with no schema, and on any document that is not OpenAPI 3.1 in the
OAS default JSON Schema dialect. Its cases, the
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

A rule never lands without its fixture, and a fixture never names a rule the ruleset lacks.
