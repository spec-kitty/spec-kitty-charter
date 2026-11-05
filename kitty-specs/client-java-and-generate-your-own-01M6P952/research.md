# Research: the generated Java client and the generate-your-own-client how-to

Grounding squad of 2026-11-05 (two read-only lenses: build and bundle wiring; docsite and publishing), then
spikes run in this session against `main`'s snapshot contract (`info.version` 0.2.0-SNAPSHOT) with openapi-generator
7.26.0, the version pinned in `gradle/libs.versions.toml`. #217 still names 7.25.0; the pin has moved since.

## R1. Where the bundle comes from

- `:contracts:bundleContract` writes `build/contracts/contract/charter-openapi.yaml` and its `.sha256`
  (`<hex>  charter-openapi.yaml`). Both are published as the consumable variant `contractBundle`
  (`Category = charter-contract-bundle`); `service/deployable` already consumes it that way.
- **Decision:** the client resolves the same variant and hands the generator the YAML file only. No task path, no
  reach into `contracts/charter/**` (FR-002); the configuration cache stays valid.
- The bundling tools run on Linux and macOS only. The client is not built on Windows (`service-native.yml` builds
  only the deployable there), so no `-PcontractBundle` override is needed now.

## R2. What the generator does with the contract's closed objects

- Every response object is `additionalProperties: false` (56 in the bundle). The okhttp-gson `pojo.mustache` emits,
  for every model that is not `isAdditionalPropertiesTrue`, a check that throws
  `IllegalArgumentException("The field ... is not defined ...")` on any undeclared member. Generated plainly from
  the bundle, 56 models carry that check. No generator option turns it off, and none of the built-in normalizer
  rules touches `additionalProperties`.
- **Decision (our build):** `TolerantClientNormalizer` (build-logic), loaded by name as `NORMALIZER_CLASS`, reads
  every closed object as open (`additionalProperties: true`) after applying #119's boolean-schema fix, and
  `disallowAdditionalPropertiesIfNotPresent=false` opens objects that declare nothing. The models then keep
  unknown members in their additional-properties map. Result: 0 models with the strict check, 0 duplicate models.
  The schema walk is shared with `SingleModelPerSchemaNormalizer` (extracted to `SchemaWalker`, unchanged
  behaviour for the server).
- **Decision (how-to):** a reader cannot load our normalizer, so the how-to opens the closed objects with a short
  Python script before generating Java. Verified: 0 strict checks after it; the result builds with `mvn package`
  and with `javac --release 21`.
- Python: the strict check sits inside `{{#disallowAdditionalPropertiesIfNotPresent}}`, so
  `disallowAdditionalPropertiesIfNotPresent=false` alone tolerates unknown members (they are dropped). Verified.
- Positive control: with the server's normalizer and the default options, the client's tolerance tests do not
  compile (the additional-properties API is absent), so they cannot pass vacuously.

## R3. Unknown enum values

- Java okhttp-gson with `enumUnknownDefaultCase=true`: each enum gains `UNKNOWN_DEFAULT_OPEN_API`, and
  `fromValue`, the type adapter and `validateJsonElement` all map an unknown value to it. Verified by test.
- Python with the same option: the member is added, but pydantic rejects an unknown value
  (`ValidationError ... Input should be ...`). Verified. A one-method template override (`_missing_` returning
  `UNKNOWN_DEFAULT_OPEN_API`) in `model_enum.mustache` fixes it; verified with pydantic 2.14.
- Refusal codes stay tolerant without this: `Problem.code` is an open string in the contract.

## R4. Header, determinism, release

- The okhttp-gson templates include `licenseInfo.mustache` at the top of every Java source. A one-file template
  directory overrides it with `SPDX-License-Identifier: MIT` and "Generated from the charter contract
  {{version}} ... Do not edit" (operator, #217). `{{version}}` is the contract's `info.version`.
- `hideGenerationTimestamp=true` leaves `@jakarta.annotation.Generated` without a date. Two generations into two
  directories compare byte-identical (`checkClientReproducible`).
- `options.release = 21` on the Java 25 toolchain; a test reads every class file's major version (65).

## R5. Gates and module shape

- `charter.hand-written` applies `-Xlint:all -Werror` and Error Prone to every `JavaCompile`. Generated code fails
  both. Precedent: `charter.native-image` exempts its AOT source sets by name. **Decision:** generated sources in
  their own `generated` source set, exempted the same way; the hand-written `ProblemDetails` stays in `main` with
  every gate (Checkstyle complexity limits of ADR-0058 included). One jar and one sources jar carry both.
- `ProblemDetails` is `@NullMarked` on the class, not the package, because the package is shared with generated
  classes.
- Runtime dependencies: okhttp, logging-interceptor, gson, gson-fire, jakarta.annotation-api (five). Two were new
  to dependency verification (logging-interceptor 4.12.0, gson-fire 1.9.0); the regenerated metadata adds only
  their entries. The logging-interceptor module's SHA-1 matched Maven Central's; Central rate-limited the other
  cross-checks (HTTP 429).

## R6. Publishing and docs facts for #224

- The docsite serves the bundle at `/reference/api/main/openapi.yaml` with `openapi.yaml.sha256` beside it
  (`docs-site.yml` checks it with `sha256sum -c`). The running service serves it at `GET /api/v1/openapi.yaml`
  with a strong ETag `"sha256:<hex>"` of the same digest; the call needs the worktree bearer token.
- No contract tag exists yet. From 0.1.0 the release assets are `charter-openapi.yaml` and
  `charter-openapi.yaml.sha256` on the `contract-charter-v<version>` release.
- Docs rules: `kind: How-To` under `docs/guides/`, linked from `docs/guides/README.md`; description 50–180
  characters; no "feature" in prose; root-absolute links; release download URLs as code, not links (they 404 until
  0.1.0).
- Maven Central publishes only a `.sha1` for `openapi-generator-cli-7.26.0.jar`; the how-to verifies the jar with it.
