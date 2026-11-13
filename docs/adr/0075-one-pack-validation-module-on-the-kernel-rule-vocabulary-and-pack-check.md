---
title: "ADR-0075: One pack-validation module on the kernel's rule vocabulary, packaged as pack-check"
description: Every pack check runs as a kernel ruleset in one Spring-free module that the service and a standalone pack-check CLI share; the expensive closure walks stay behind ports.
doc_status: active
updated: 2026-11-13T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-13'
type: ADR
kind: ADR
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0046-service-release-line-and-native-packaging
  - adr/0068-a-pack-declares-its-fallback-profile
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add or change a pack check, a refusal code, or anything in kernel.validation.
  - profile: pack-author
    guidance_text: Read this for what pack-check checks on your machine and in CI, and what only the service checks.
---

# ADR-0075: One pack-validation module on the kernel's rule vocabulary, packaged as pack-check

## Status

**Accepted**, 2026-11-13. Decider: Stijn Dejongh (operator).

- **The steers.** The operator gave them on 2026-11-13, after the open-packs drift watch research. The kernel's
  validator is used throughout pack validation. The pack validator is a module the service itself uses. It is then
  repackaged as a standalone CLI that runs the rule-based consistency check, without the full walk.
- **The squad.** A four-lens squad (architect, patterns, code shape, reducer) read the code and proposed the design.
- **The rulings.** The operator ruled its six open questions one at a time, in an interview on 2026-11-13. The rulings
  are marked *(operator)* below.
- **The identifier ruling.** When the landing review raised that the pack id, the roles and the slot ids were left out,
  the operator ruled on 2026-11-13: "everything strict", meaning every identifier, including the pack id, roles and
  slot ids.

The work is tracked in epic [#549](https://github.com/spec-kitty/spec-kitty-charter/issues/549), slices 1 to 7 (#550 to #556), slice 8 (#478, `pack-check`) and slice 9 (#557), with #547.

### Amendment 2026-11-13 (operator, #553): pack-validation reads the packs load; the packs domain stays

Slice #553 measured what decision 3's extraction would move. The load that builds `PackContents` needs
every kind's codec and the artifact domain: a closure of 168 classes (about 24,000 lines), 79 of the
113 `packs.domain` types and 89 `packs.infra.storage` codecs, 79 of them package-private. Moving them
would split the packs slice, which ruling 2 refused. The operator asked why `pack-validation` did not
import them instead, and then ruled for that: the packs domain model stays in `packs`.

- **The dependency direction is reversed.** `pack-validation` depends on `:packs`, not the other way.
  It names only `packs.domain`. It reads a pack through the slice's public `PackInspector` port
  (`PackLoad.contents`), and it never parses a file itself (no second derivation). A composition root
  plugs in the file-system adapter, `FileSystemPackInspector`: the service's `deployable`, and
  `pack-check`'s main (#478). #478 lets that main name the adapter in
  `PACK_VALIDATION_IS_FRAMEWORK_FREE` and `ONION_DEPENDS_INWARD`, with a known-good control.
- **Decision 3, "what moves in", is replaced.** Nothing moves. `PackSchemas`, `PackFileKind`,
  `ManifestMembership` and the readers stay in `packs.infra.storage`. `pack-validation` holds
  `PackLoad`, its read over the port, and the rulesets of later slices.
- **Framework-free, by reach.** `:packs` puts Spring on the classpath of both modules. So
  `PACK_VALIDATION_IS_FRAMEWORK_FREE` gains a transitive half: no charter class the modules reach, and
  none the file-system inspector reaches, uses Spring, HTTP, a commit store or a lock. The direct half
  allows `packs.domain` and no other ring of the slice. The walk follows the classes a class names, not
  a port's implementations, so the inspector is walked from its own root. Leaving Spring off
  `pack-check`'s runtime classpath, and proving it runs without it, is an obligation of #478. Until
  then, Spring is on that classpath, unused.
- **Rulings 1 and 2 hold.** `pack-check` stays its own module and jar, and `packs` is not split.

The other decisions follow the reversed direction. Each is confirmed, or changed, in its own slice:

- **Decision 4 (one pipeline).**
  - `FileSystemPackInspector` is not deleted: it is the read that `pack-validation`'s rulesets run
    over.
  - The converters between `PackFinding`, `PackElementViolation` and `RefusalViolation` still go.
  - The registration load (`WholePackStore`, `DomainRepositoryService`) keeps its own reader. It runs
    the same `PACK` ruleset through a port that `packs.domain` declares and `deployable` wires; #554
    decides which contents it judges.
  - Until then, validate mode's verdict stays `PackValidation`'s, in `packs.domain`.
- **Decision 6 (external checks behind ports).**
  - `packs.domain` declares `ClosureWalk`, `ImpactCheck` and `MutabilityCheck`, not `pack-validation`,
    which would be a cycle.
  - `packs.application` implements them over `GraphWalk`, `Reachability`, `RegistrationImpact`,
    `FallbackProfileRule` and `PackMutabilityPolicy`.
  - `pack-validation`'s rules call the ports, and `deployable` wires them. `pack-check` composes none
    of them.
- **Decision 8 (the round trip).** The round trip moves from the `packs` test source set into `packs`
  main code (`packs.infra.storage`, beside the codecs it needs). A composition root calls it through a
  public entry: the whole-pack gate and the drift test through the service's wiring, and
  `pack-check --round-trip` through its main. It does not move into `pack-validation`.

## Context

- **The kernel's validator is a port of `modular-validators`.** `ai.speckitty.charter.kernel.validation` is a lean
  port of the operator's library ([`sddevelopment-be/modular-validators`](https://github.com/sddevelopment-be/modular-validators),
  Apache-2.0). It is vendored, and its attribution is in `kernel-validation-ATTRIBUTION.md`.
  - 73 main files outside the package import it, but only as a check in constructors: 72 `guard()` calls, all fail-fast.
  - No main code uses `check()` or `Rationale`, so the full ruleset result goes unused.
  - Several adapters catch `ConstraintBreachException` to collect faults instead: `FilesystemPackStore`,
    `DomainRepositoryService`, `GuardClosure` and `GuideMarkdown`.
- **The pack checks do not use the kernel at all.**
  - They report through six types: `PackFinding`, `PackElementViolation`, `RefusalViolation`, `CharterRefusal`,
    `PackCompositionResolver.Refusal` and `FallbackProfileRule.Breach`. Several sub-codes are untyped strings.
  - One pack is judged by two pipelines kept equal by hand:
    - validate mode: `FileSystemPackInspector`, then `PackPolicy.judge`;
    - load and registration: `FilesystemPackStore`, then `DomainRepositoryService.assembly`, then
      `WholePackStore.loadStaged`.
  - Converters glue the two pipelines together.
  - `PackValidation`, the validate-mode entry point, is wired as a bean in `CharterWiring` that no main code injects;
    only tests use it. It misses the checks only the
    load runs: unmodelled files, manifest membership, the reserved shard and orphan fragments.
- **Rules are duplicated.**
  - The pack-id grammar appears in both `PackRegistrationService` and `RegistrationAdmission`, while `PackIdentifier`
    checks only that the id is not blank.
  - Four identifier types write their own `^[a-z][a-z0-9-]*$`, which accepts `a--b`, beside the shared
    `IdentifierGrammar`. The agent and mission roles and the slot ids do the same.
  - The kernel's `Constraints.isNotBlank()` has no callers; five sites write the same
    `text != null && !text.isBlank()` check inline.
  - spec-kitty-open-packs re-implements the classification, the schema checks and the manifest rules in Python
    (`validate_pack_schemas.py`, 1,398 lines).
- **The expensive checks compose a closure.** Registration impact (before and after each write), the mutability
  policy, the ADR-0068 fallback profile, qualified cross-pack references and the required floor each read every pack
  of the closure. The walker (`kernel.domain.GraphWalker`, driven by `GraphWalk`, `Reachability`, `GovernanceWalk`
  and `ActionContextWalk`) answers reads and validates nothing.
- **open-packs has to prove its packs against a pinned charter.** ADR-0070 decision 4, as PR [#548](https://github.com/spec-kitty/spec-kitty-charter/pull/548) amends it, gates them in
  open-packs at a pin ([spec-kitty-open-packs#252](https://github.com/spec-kitty/spec-kitty-open-packs/issues/252)).
  It needs a released artifact that runs charter's own checks
  ([#478](https://github.com/spec-kitty/spec-kitty-charter/issues/478)).

## Decision

1. **The kernel gains a rule vocabulary that collects every finding.** It stays `java.*` plus JSpecify only
   (`VALIDATION_LIBRARY_IS_PURE`), and it replaces the current shapes outright.
   - A `Rule<T>` returns a list of `Reason`s, because one rule can find many faults. It composes:
     - `on(focus)` narrows the rule to a part of the subject;
     - `forEach(parts)` applies it to each part;
     - `when(applicable)` applies it only when a condition holds.
   - A `Reason` carries the rule, the result, a `Severity` (`VIOLATION`, `WARNING`, `NOTICE`), a code, a `Location`
     (file, plus a pointer into it) and a detail.
   - A `Constraint<T>` is a `Rule` with a predicate. Its location and detail are functions of the subject: these are
     the dynamic descriptions upstream declared but never wired. Equality is that of a record.
   - A `ModularRuleset<T>` is a `Rule` too, so a ruleset can hold rulesets. `check` returns a `Rationale` with every
     finding. `guard` stays for value objects, and its `ConstraintBreachException` carries the `Reason`.
   - `Location` is upstream's `Evidence` done right: typed and immutable, not a mutable string map.
   - The kernel turns the existing `IdentifierGrammar` predicates into the rules `notBlank`, `slug(max)` and
     `letterFirstSlug(max)`. `isSlug` and `isLetterFirstSlug` are what the asset, skill, toolguide, preset, mission
     type and mission step ids already use since #546.
2. **Every identifier uses the shared grammar rules** *(operator, "everything strict")*. No identifier keeps a
   grammar of its own, so `a--b` and a trailing hyphen are refused everywhere.
   - **The pack id** is `slug(63)`: lowercase alphanumerics in hyphen-separated runs, with no leading, trailing or
     doubled hyphen, at most 63 characters. A digit first stays allowed, because `isSlug` (the non-letter-first
     slug) permits it, as the pack id does today. `PackIdentifier` holds the rule, and the copies go:
     the regex `^[a-z0-9][a-z0-9-]{0,62}$` in `PackRegistrationService` and `RegistrationAdmission`, and the contract's
     `PackId.yaml`, `PackSource.yaml` and the two `packs_packId` path parameters, which take the same pattern as the
     rule (`^[a-z0-9]+(-[a-z0-9]+)*$`, `maxLength: 63`).
   - **The letter-first types** move to `letterFirstSlug`, because each is letter-first today:
     - `ParadigmIdentifier`, `StyleguideIdentifier`, `MissionStepContractIdentifier` and `ProcedureStep`, which write
       `^[a-z][a-z0-9-]*$` and accept `a--b`;
     - the agent role (`AgentProfile`) and the mission step role (`MissionStep`), which write `[a-z][a-z0-9-]*`;
     - the slot id (`SlotId`, and the slot of `StepEnhancement`): each of its two parts is a `letterFirstSlug`.
   - The charter's model is canonical: fixtures and example packs follow, and an open-packs id that breaks is fixed
     in open-packs.
3. **A new `pack-validation` module holds every pack check as a kernel ruleset** *(operator: extract only what is
   needed, no `packs`/`packs-edge` split)*.
   - **What moves in, without Spring:**
     - the rules (`PackPolicy`'s, and the checks only the load runs today);
     - the codecs and the classification they need (`PackSchemas`, `PackFileKind`, `ManifestMembership`);
     - a load that builds `PackContents` and the census.
   - **What stays in `packs`.** `packs` depends on `pack-validation`. `WholePackStore` and the other Spring beans stay
     there, as thin adapters over the module's load.
   - **What it may not use.** It depends on `kernel`, `profiles`, `missions` and the parsing libraries only, never on
     Spring, HTTP, the commit stores or locks. An ArchUnit rule, `PACK_VALIDATION_IS_FRAMEWORK_FREE`, pins that.
   - **Rulesets per file kind.** Descriptor, graph, step, artifact, asset, fragment and prompt slot each get one.
     `forEach` composes them into one `PACK` ruleset.
   - **Plain functions where a rule would add code.** Classification, ordering and precedence, and the manifest set
     difference stay plain functions that rules call.
   - **No second derivation.** Rules read the `PackContents` and census the load produced. They never parse a file
     again or derive the census a second time.
4. **One pipeline per pack.**
   - Validate mode and the load run the same `PACK` ruleset.
   - `FileSystemPackInspector`, and the converters between `PackFinding`, `PackElementViolation` and
     `RefusalViolation`, are deleted. No legacy reader is kept beside the new one.
   - One mapping turns a `Rationale` into the API problem (`RefusalViolation` and the refusal code), and one turns it
     into a `PackVerdict`.
   - The wire codes (`PackFindingCode`, `RefusalCode`) do not change.
5. **An unmodelled file is a code, not a severity** *(operator)*: `file_not_held` at `WARNING`. The kernel keeps three
   generic severities.
6. **The closure checks and the walk sit behind ports, as external checkers.**
   - An external check is a `Rule` whose work is done elsewhere. The module declares its ports:
     - `ClosureWalk`, over the composed facts: qualified references, the fallback profile, the required floor;
     - `ImpactCheck`, the closure difference of a write: overrides, conflicting relations, cycles;
     - `MutabilityCheck`.
   - `packs` implements them over `GraphWalk`, `Reachability`, `RegistrationImpact`, `FallbackProfileRule` and
     `PackMutabilityPolicy`.
   - The service composes `PACK`, `CLOSURE` and these checks. The CLI composes `PACK` and `CLOSURE` only. Leaving the
     walk out means not composing it; no rule carries a flag for it.
   - The impact and fallback checks collect every fault instead of the first.
7. **The descriptor-level closure checks run without the walk** *(operator)*. `CLOSURE` covers:
   - the dependency cycle;
   - the unknown and the out-of-range dependency;
   - the parent;
   - the level;
   - name, sibling and preset conflicts.

   It runs over the descriptors of a set of packs with their levels. It needs every `pack.yaml`, never the graph, and
   it is part of the CLI.
8. **The round trip moves into the module.**
   - The whole-pack round trip (ADR-0036) moves from the test source set into `pack-validation`.
   - The drift test and the whole-pack gate call it.
   - It fails on a file kind it does not model ([#547](https://github.com/spec-kitty/spec-kitty-charter/issues/547)).
9. **`pack-check` is its own module and jar** *(operator)*.
   - **What it is.** `service/pack-check` is a thin `main` over `pack-validation`, with no Spring and no CLI framework.
     It ships as `pack-check-<version>.jar`, with its checksum, on the same `service-v*` release as the service
     (ADR-0046).
   - **Inputs.** `--config-root <dir>`, or a repeated `--pack <dir>[:<level>]`, plus `--round-trip` to add the round
     trip.
   - **Output.** The contract's problem JSON, with the same codes the API answers. A contract test checks it against
     `contracts/charter/schemas`.
   - **Exit codes.** `0` when valid (warnings allowed), `1` on a violation, `2` on a usage or I/O failure.
10. **The vendored port stays; the design is offered upstream** *(operator)*.
    - Charter cannot depend on the library today. It is an unreleased Java 21 `1.1.1-SNAPSHOT`, and it brings in
      commons-lang3, slf4j and first-party commons, which the kernel's purity rule refuses.
    - The design of decision 1 is offered to `modular-validators` as an issue:
      [`sddevelopment-be/modular-validators#13`](https://github.com/sddevelopment-be/modular-validators/issues/13).
    - Once upstream releases a core with no dependencies and this vocabulary, charter swaps the port for it in one
      change and deletes the port. The two never sit side by side.

### Amends, as each slice lands

- **ADR-0006 and ADR-0037:** the module map gains `pack-validation` and `pack-check`, and the new ArchUnit rule; as
  amended on 2026-11-13 (#553), `pack-validation` depends on `packs`.
- **ADR-0017:** schema validation is a rule of `pack-validation`; as amended on 2026-11-13 (#553), it stays a check
  of the codecs in `packs.infra.storage`, whose findings reach the `PACK` ruleset through `PackContents`.
- **ADR-0036:** the round trip is main code, run by the gate, the drift test and `pack-check`; as amended on
  2026-11-13 (#553), main code of `packs`.
- **ADR-0046:** the release carries `pack-check`.
- **ADR-0068:** the fallback-profile rule runs behind `ClosureWalk`.
- **ADR-0070:** builds on the amendment of decision 4 in PR #548. open-packs gates on `pack-check` at its pin. Charter's `open-packs-drift.yml` is removed.

## Consequences

### Positive

- One rule exists once and runs in validate mode, at registration and in `pack-check`. Parity by hand, and the
  converters that kept it, go.
- Every check returns every finding, with a code and a location, which is the full-result mode the kernel was built
  for.
- open-packs runs charter's own checks from a released jar. The Python port of the classification and of the schema
  and manifest checks retires (about 950 to 1,050 of 2,594 lines).
- The cheap checks and the expensive ones are separated by composition, so a pack author gets a fast check on their
  own machine.

### Negative

- **Large, refactor-heavy migration.** It touches `PackPolicy` (1,269 lines), `FilesystemPackStore`, `WholePackStore`
  and `DomainRepositoryService`.
- **Breaking: stricter identifiers.** Ids that are valid today (`a--b`, or a trailing hyphen) are refused, for the
  pack id, the roles and the slot ids as much as for the other types. A registered pack whose id has a doubled or
  trailing hyphen is refused.
  - A contract change fragment and a `### Breaking` CHANGELOG entry land with slice #552 (ADR-0022).
  - Fixtures and example packs change with the model.
  - open-packs must check its pack ids, roles and slot ids, and fix any that break.
- **Mixed `packs` module.** Without the split, `packs` keeps its Spring beans and pure code side by side. Only the
  extracted module is framework-free.

### Risks

- **Changed codes or order.** A refusal code or the order of findings can change and break open-packs' expected-code
  fixtures. Mitigation: a parity test of codes against the open-packs expectations before slice 7 (#556) ships.
- **A second derivation creeping back.** A rule could re-parse a file. Mitigation: rules take `PackContents` and the
  census only, and review checks it.
- **Version skew between the CLI and the service.** Mitigation: one version and one release.

## Alternatives considered

### A `--check` flag on the service's `main`

About 40 to 60 lines, with no new module. Not chosen *(operator)*: the jar keeps Spring and the HTTP layers, and it is
not a standalone library.

### Split `packs` into `packs` and `packs-edge` first

The squad's architect proposed it, as `profiles-edge` and `missions-edge` were split. Not chosen *(operator)*: extract
only what `pack-validation` needs, a smaller cut.

### Each pack on its own in the CLI

Not chosen *(operator)*: the descriptor-level closure checks are cheap, and leaving them out keeps open-packs' Python
cross-pack checks (about 420 lines).

### Depend on `modular-validators` now

Not chosen *(operator)*: see decision 10.

## References

- [#478](https://github.com/spec-kitty/spec-kitty-charter/issues/478) (`pack-check` and service releases)
- [#547](https://github.com/spec-kitty/spec-kitty-charter/issues/547) (the round trip fails on an unmodelled kind)
- [spec-kitty-open-packs#252](https://github.com/spec-kitty/spec-kitty-open-packs/issues/252) and
  [#253](https://github.com/spec-kitty/spec-kitty-open-packs/issues/253)
- The open-packs drift watch research note and the ADR-0070 decision 4 amendment (PR
  [#548](https://github.com/spec-kitty/spec-kitty-charter/pull/548))
- [`modular-validators`](https://github.com/sddevelopment-be/modular-validators) and `kernel-validation-ATTRIBUTION.md`

## Appendix: the upstream issue

Filed as [`sddevelopment-be/modular-validators#13`](https://github.com/sddevelopment-be/modular-validators/issues/13)
on 2026-11-13. Its text, as proposed:

> **Title:** A dependency-free core with rules that return many reasons, each with a code, location and severity
>
> spec-kitty-charter vendors a lean port of `core` (its `kernel.validation`). It wants to depend on the library
> instead, and to use it for every pack check (charter ADR-0075). It needs:
>
> - a `Rule<T>` that returns a list of `Reason`s, composed with `on`, `forEach` and `when`, and a `ModularRuleset`
>   that is itself a `Rule`;
> - on `Reason`: a code, a `Severity` (`VIOLATION`, `WARNING`, `NOTICE`) and a typed, immutable `Location` (file and
>   pointer), in place of the mutable `Evidence` (whose `merge` drops the other side's keys);
> - the dynamic description wired: `Constraint`'s location and detail are functions of the subject, and are used
>   by `applyTo`;
> - an immutable `Rationale`, `Constraint` equality as a record (today the description alone decides it), and an
>   unchecked breach exception that carries the `Reason`;
> - a released `core` with no dependencies beyond the JDK (no commons-lang3, slf4j or first-party commons), so a
>   framework-free domain can depend on it.
