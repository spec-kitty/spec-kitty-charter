# Op 01M7B88SWG4XRDADEN97ZRFEEA: publish the packs contract as a versioned release (#559)

Profile: java-jenny. Kind: feature (release machinery). Size: M. Suggested triage for #559, not applied: milestone
Infra & Enablers, `priority:P2`, type Feature (issue-tracker Rule 7; #559 has none of the three).

## Why an Op, not a Mission

Operator ruling: #559 is its own small slice. The change adds a release line beside the charter contract's
(`contract-release.yml`, #45) and mirrors it: a version carrier, a tag gate, an assets script, a rehearsal and a
workflow. It also adds data (the conformance cases) and the test that holds the classifier to them. It changes no wire
code, schema, contract operation or production behavior.

## Operator question (step 1)

Asked before any workflow was built, through the orchestrator: the tag name (`contract-packs-v*` beside
`contract-charter-v*`, recommended), the version start (0.1.0 under ADR-0022 by analogy, recommended), the tag
protection (#196's ruleset widened to `contract-packs-v*`, recommended), and a minimal gate (the tag and the CHANGELOG
top heading agree; no schema-diff gate).

**Ruling (operator, 2026-11-14):** A1 `contract-packs-v*`; B1 start at 0.1.0 under ADR-0022, recorded as a note in the
ADR; C1 #196's tag ruleset extended to `contract-packs-v*` (the operator applies the setting); D minimal gate, no
schema-diff gate now. The build matched the recommendation, so no code changed.

## Grounding

- **#559.** Publish `file-kinds.yaml`, the pack and artifact schemas (the pack-document structure schema included)
  and classifier conformance cases (input path → expected kind or refusal) as a versioned release, versioned on the
  contract's own changes, with a documented pin and check. Done when doc-kitty's `pnpm packs:sync-contract` can
  fetch a tagged release instead of a commit, and the cases pass against charter's own classifier in CI.
- **#45 / `contract-release.yml`.** The template. A tag push runs `scripts/release/contract-release-assets.sh`, which
  first runs the publishing gate (`:contracts:checkReleaseTag` → `ReleaseGate.releaseRefusal`), then the contract
  checks, then stages assets and moves them out only when all exist. A read-only `assemble` job and a write-only
  `publish` job; the publish job checks the tag still names the built commit and never replaces a Release.
  `service.yml`'s `release-rehearsal` job runs every `scripts/release/rehearse-*.sh`, so a new rehearsal is picked up
  without a workflow change.
- **#196.** The tag ruleset for `contract-charter-v*` is a repository setting, still open. The same reasoning holds
  for `contract-packs-v*`: the workflow runs from the tagged commit, so its checks catch mistakes, not a tag pusher.
- **#478.** pack-check CLI and `service-v*` releases. Separate; open-packs' Python port of `PackFileKind.classify`
  (open-packs #249, #251) is the other consumer the cases serve until pack-check replaces it.
- **open-packs#252.** Gates open-packs on charter's `:packs:driftTest` at the pin in `charter.lock.yaml`. It pins a
  charter commit for the service, not the packs contract; unaffected.
- **doc-kitty#328, #330, #337 (ADR-0079).** doc-kitty vendors `file-kinds.yaml`, the schemas the reader uses and the
  conformance cases into `src/lib/packs/contract/`, with `contract.lock.json` (the charter commit and a sha256 per
  file). Until #559 lands it transcribes the cases from `PackFileKindTest` and `RetiredFilesTest`. ADR-0079 D1: "A
  pinned charter release replaces the commit once the charter publishes contract releases." So the release must give
  a sha256 per file, which `packs-contract.sha256` does, and the cases must be data, not Java.
- **Classifier.** `PackFileKind.classify(String)` returns `Mapped(kind, schema, contentForm)`, `Excluded(match,
  reason)`, `Retired(match, reason)` or `Unclassified`. The order is `files` → `exclusions` → `retired`, then the
  root case-variant refusal (a `Retired` under the root rule's glob), then `payloads`, then `ignored` (never for a path
  a `files`, `retired` or `payloads` rule takes case-folded). `Unclassified` is refused as `unmodelled` by the store.
  Companion claims happen after classification and are outside the cases.
- **Version carrier.** The charter contract has two (`info.version`, the CHANGELOG heading) and checks they agree. The
  packs contract has no OpenAPI document, and `file-kinds.yaml`'s top-level keys are closed (#194), so a version key
  there would change the table's schema for no reader's benefit. The top heading of `contracts/packs/CHANGELOG.md` is
  the only carrier (one place for each fact).

## Sweeps

**Similar surfaces.**

| Surface | Disposition |
| --- | --- |
| `contract-release.yml`, `contract-release-assets.sh`, `rehearse-contract-release.sh` | Mirrored for the packs line; unchanged |
| `ReleaseGate` CHANGELOG rules | Reused, not copied: `changelogRefusal` and `orderRefusal` take the CHANGELOG's name; charter messages are byte-identical |
| `service-release.yml` (`service-v*`) | Different artifact (the jar); unaffected |
| `service/packs` `contract-version.properties` | The charter contract's version served by the API; the packs contract's version is not served. Not needed by #559 |
| `ci.yml` service path regex, `service.yml` push paths | The new workflow file added to both, as `contract-release.yml` is |
| `PackFileKindTest`, `RetiredFilesTest` sample paths | Remain the unit tests of the table's behavior; the cases are the published, cross-implementation subset. Not deduplicated: the Java tests assert more (reasons, overlaps, load-time refusals) |

**Tracked issues.**

| Issue | Relation | Disposition |
| --- | --- | --- |
| #559 | This Op | `Closes #559` |
| #45 | Extended by #559 (charter contract release) | Untouched; still open for its own first tag |
| #196 | Tag protection | `Refs #196`: the ruleset should cover `contract-packs-v*` too; a repository setting for the operator |
| #478 | pack-check | Unaffected; the cases serve its consumers meanwhile |
| #194 | Closed top-level keys of `file-kinds.yaml` | Respected: no version key added to the table |
| open-packs#252 | Commit pin for the service | Unaffected |
| doc-kitty#330 | Consumer of the release | Can switch `packs:sync-contract` to a tag once `contract-packs-v0.1.0` is pushed |

## Not done

- No tag is pushed; tagging is the operator's.
- No schema-diff (breaking-change) gate for the packs line; the CHANGELOG's `### Breaking` rule is the only check.
- No check that a pull request touching `contracts/packs/` adds a CHANGELOG line; the README states the rule.
