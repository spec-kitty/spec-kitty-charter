# Quickstart: reproducing the main flows locally

For a reviewer of this mission's work packages. This mission is phase 1 of 2 (operator ruling, 2026-11-09): Flows 1
and 2 are its flows. Flow 3 moved to the phase-2 mission and is kept here as the record.

All commands run from the repository root on **Java 25** (the build's toolchain is 25 unless `-Pcharter.javaVersion` overrides it, which CI never does), with the sibling open-packs checkout
at the pin.

## Setup

```bash
java -version                                   # 25.x (Temurin)
git -C ../spec-kitty-open-packs fetch --quiet   # the pin 22c14b2d must be reachable there
./gradlew syncPacks -PopenPacksDir=../spec-kitty-open-packs
```

Module tasks only, as the work packages run them (`NO_FULL_HEAVY_SUITES_IN_MISSION`); `-PcontractBaseline=none`
because there is no released contract baseline:

```bash
G="./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=../spec-kitty-open-packs"
```

Run **all** of `:packs:test` per concern (plain JUnit, cheap), not a filtered list: the tests a concern breaks are
not only the ones named after it, for example `FileSystemPackInspectorTest`, `NoArtifactFieldMintsAnEdgeTest`,
`CompanionFilesTest`, `DomainRoundTripTest` and `PackFileSetExactnessTest`'s generator. (Running all of
`:activations:test` at IC-07, where `ActivationControllerTest` changes, is the phase-2 mission's, T-20.)

## Flow 1: a pack with a step enhancement registers, round-trips and is selectable (IC-01, US1, US2)

```bash
$G :kernel:test :packs:test
$G :deployable:test --tests '*CharterContractSuite'   # the suite runs every @contract feature; read step-enhancements.feature
```

Look for:

- The enhancement and its fragments are written back at the same paths with equal meaning, and the mutant test fails
  when a written fragment is altered or dropped.
- Packs without enhancements keep the digests captured on WP01's base commit before the binding (T001), and a fixture
  with one enhancement changes its digest (NFR-001).
- `PUT …/selections/step_enhancement/spdd-plan` answers `200`, beside a tactic selection in the same fixture that
  answers `200` too.
- A registration that `main` refuses as "unmodelled" is accepted.
- A preset listing the enhancement keeps its id when the activation is written again (SC-006). Being in force is not
  checked: under E6 as built, a selected step enhancement is reported not in force until PR C's E6 amendment (OD-6).

## Flow 2: malformed enhancements, fragments and prompt slots are refused (IC-02, IC-03, US3)

```bash
$G :kernel:test                   # PromptSlots edge-case table (kernel.markdown, A1)
$G :packs:test                    # rules, precedence, companion twins, the corpus property test
$G :deployable:test --tests '*CharterContractSuite'   # the suite runs every @contract feature; read prompt-slots.feature
$G :deployable:test --tests '*ReservedRefusalCodesTest' --tests '*HexagonalArchitectureTest'
```

Each rule has a failing fixture and a passing twin that differ only in the fault (SC-002). A multi-fault registration
lists every fault, each at its file (`/violations/<n>/pointer`); by OD-3 (a) the contract calls these codes raised. The built-in pack at the pin must still load and compose without `pack_unreadable`. The corpus test asserts
**exactly five** `prompt_slot_not_empty` warnings, one per software-dev prompt, each naming its file. On the wire, a
registration of an unversioned pack whose slot holds text lists the warning in the registration result's
`diagnostics` (OD-7; `prompt-slots.feature`).

```bash
$G :packs:openPacksRoundTrip
$G :packs:wholePackRoundTrip      # expects the empty_kinds entry for step-enhancement (#428)
```

## Flow 3: the effective prompt and the compose refusal (moved to the phase-2 mission)

> **Record only — phase 2 (#438) owns it.** No work package of this mission runs or checks this flow.

Kept as the record; IC-05 to IC-07, former US4 and US5. The phase-2 mission re-validates it after PR C merges. Since
it was written, OD-5 ruled `409 mission_type_not_in_force` for a mission type held but not in force.

```bash
$G :packs:test
$G :activations:test                  # adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-001 determinism (body and ETag), adr-0066-phase-2-compose-checks-and-effective-prompt-01M70271#NFR-002 counts at 1,000 and 2,000
$G :deployable:test --tests '*CharterContractSuite'   # the suite runs every @contract feature; read effective-step-prompt.feature
$G :deployable:test --tests '*OperationCoverageTest' --tests '*ReservedOperationCoverageTest' --tests '*RelationTableParityTest'
```

By hand, against a running service, after activating a fixture closure. See `docs/guides/service-build.md` for
starting it. `$CHARTER_URL` is the daemon's base URL and `$CHARTER_TOKEN` its bearer token, as in
`docs/guides/generate-a-client.md`; without the token, `security.feature`'s `401` answers. Add the worktree header if
your daemon requires it.

```bash
curl -s -H "Authorization: Bearer $CHARTER_TOKEN" \
  "$CHARTER_URL/api/v1/activations/project/mission-types/software-dev/actions/plan/prompt" | jq '.slots'
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $CHARTER_TOKEN" \
  -H "If-None-Match: <the ETag of the previous answer>" \
  "$CHARTER_URL/api/v1/activations/project/mission-types/software-dev/actions/plan/prompt"   # 304
```

Look for:

- Every declared slot is listed.
- Fragments are ordered built-in, org, project, then by `packName` (the descriptor name, not `packId`), then by
  enhancement id.
- An unselected enhancement is absent.
- `404` for an unknown activation, mission type or action; `400` for a malformed one.
- `304` on a matching `If-None-Match`, and a new ETag after editing one fragment's bytes.
- For a mission type held but not in force, the answer plan OD-5 rules.

Fragments appear only once the step-enhancer binding of plan OD-6 has landed: under E6 as built, no step enhancement
is in force.

## Contract and docs gates

```bash
$G :contracts:check               # bundle, lint, closed responses, compatibility, changelog fragments, refusal examples
python3 scripts/docs/check_docs.py all
npx markdownlint-cli2@0.18.1 "docs/**/*.md" "kitty-specs/adr-0066-step-enhancement-service-alignment-01M6ZRYK/**/*.md"
npx cspell@10.4.0 --no-progress "docs/**/*.md"
```

## Closeout (once, before the PR)

```bash
JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon -PopenPacksDir=../spec-kitty-open-packs \
  verifyAll :packs:wholePackRoundTrip --continue
```

The full closeout list (adversarial squad, docs checks, Spotless and Checkstyle, the conformance issue and the issue
verdicts) is in [tasks.md](tasks.md), "Closeout".
