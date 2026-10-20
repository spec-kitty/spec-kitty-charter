# Implementation Plan: Pack parse/write-back round-trip

**Branch**: `feat/pack-roundtrip-io` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)
**Input**: Mission specification from `kitty-specs/pack-roundtrip-io-01M5DEH8/spec.md`

## Summary

Deliver a narrow, self-contained vertical in the charter offering service that reads a pack's **descriptor** and **tactic** artifacts from the local filesystem into plain domain models, and writes them back such that re-reading reconstructs an **equal** domain model (semantic round-trip). The domain reaches storage only through a **new, domain-owned repository port** (single-pack, partial-tolerant fetch + per-element write-by-id); a **new filesystem adapter** in `adapter-out-yaml` implements it, reusing the existing schema/validation and YAML plumbing. Graph shards, relationships and the domain graph shape are deferred; a fresh aggregate and port keep this vertical separable from `CharterPack`/`CharterPackRepository`.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain; CI runs JDK 25, `./gradlew verifyAll`)
**Primary Dependencies**: Existing only — networknt JSON-schema validator and SnakeYAML. Reuse is **read-side** (`PackSchemas`/`ParsedYaml`/`Yaml`/`YamlDocument`); emission uses a **new SnakeYAML `Dump`-based serializer** following the existing `GraphShardWriter` pattern, because `YamlDocument`/`ScalarWriter` can only edit an already-parsed document, not build one (see research D6). jspecify nullness annotations; (tests) JUnit 5, AssertJ, ArchUnit. **No new dependency is added, upgraded, or removed.**
**Storage**: Local-filesystem pack directory of YAML files (`pack.yaml` descriptor + `*.tactic.yaml` artifacts); no database.
**Testing**: JUnit 5 + AssertJ. Semantic round-trip tests with a same-fixture positive control (mutate→assert inequality); replace-existing proves the write path is load-bearing (mutate→write→refetch); a single shared fixture pins optional presence/absence + reference order/duplicates; ArchUnit layering with a known-bad control. Fixtures under `service/adapter-out-yaml/src/test/resources/`. Mirrors the existing `OpenPacksRoundTripTest` structure but at the domain-model (semantic) level, not the byte level.
**Target Platform**: JVM (Linux dev/CI).
**Project Type**: single (the existing multi-module Gradle service: `charter-domain`, `charter-read-app`, `adapter-out-yaml`, …).
**Performance Goals**: N/A — a correctness vertical over a small proving fixture; no throughput target.
**Constraints**: Semantic domain-model equality (not byte-identical); onion layering enforced by ArchUnit (no infra type/annotation in `charter-domain`; port signatures expose only domain types); atomic (write-temp-then-rename) and pack-root-contained writes; descriptor + tactic only (graph/relationships deferred); no REST/GraphQL surface change; references and steps carried as opaque verbatim structs.
**Scale/Scope**: One proving fixture pack (a descriptor + a handful of tactics); ~new: 4–6 domain types, 1 port, 1 adapter (reader + two writers), 1 test battery.

## Charter Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Charter present (`.kittify/charter/charter.md`). Relevant gates:

- **Hexagonal onion layering (ADR-0006)** — domain owns the port; infra behind it; dependencies point inward. PASS by design: new port lives in `charter-domain`, adapter in `adapter-out-yaml`; ArchUnit (incl. existing `HexagonalArchitectureTest.theCoreIsPlainJava`) + the new signature rule + a known-bad control enforce it (NFR-001).
- **Generated DTOs at the edge (ADR-0004)** — not touched; no `charter-api`/REST change (C-003). PASS.
- **Adversarial review squad at plan and pre-merge for contract/architecture/domain changes** — this mission introduces a new domain authority (aggregate + port), so it is an architectural change. SATISFIED by the operator run contract: a review squad runs after every stage (incl. this plan) and a pre-PR squad before hand-off.
- **BDD / scenarios-first** — acceptance scenarios authored in the spec; Given/When/Then round-trip tests drive implementation.
- **Test economy (Standing Order 4 / DIRECTIVE_041)** — every test must guard real behaviour through the production entry point (the port), with same-fixture positive controls; no shape-pins or inert duplicates. Carried into every implementer brief and vetted at closeout.
- **Build gates** — `./gradlew verifyAll` on Java 25 incl. palantir-java-format and pack-schema validation stay green (NFR-003).
- **Supply chain (DIRECTIVE_051)** — no dependency change; see `research.md` (nil result recorded, not assumed).

No charter conflict. Re-checked after Phase 1 design: the data model and port keep all infra behind the boundary — still PASS.

## Project Structure

### Documentation (this mission)

```
kitty-specs/pack-roundtrip-io-01M5DEH8/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── spec.md              # From /spec-kitty.specify
# contracts/ : none — see meta.json "contracts": "none" (no external API surface; the port is an internal domain interface)
```

### Source Code (repository root)

```
service/
├── charter-domain/src/main/java/ai/speckitty/charter/domain/
│   ├── Tactic.java                    # NEW: id, schema_version, name, List<TacticStep> steps (non-empty),
│   │                                  #      optional purpose/notes, List<String> failureModes,
│   │                                  #      List<String> appliesToLanguages, List<TacticReference> references
│   ├── TacticStep.java                # NEW: title (req), optional description, List<String> examples,
│   │                                  #      List<TacticReference> references
│   ├── TacticReference.java           # NEW: name, type, id, when — carried verbatim (opaque struct)
│   ├── PackSnapshot.java              # NEW aggregate (name TBD): descriptor + ordered List<Tactic>
│   │                                  #      + reported violations; DISTINCT from CharterPack (no graph)
│   ├── PackLocator.java               # NEW domain value (or reuse domain PackRoot) — NOT the infra PackLocation
│   ├── PackDescriptor.java            # EXTEND: add Optional<String> activeCharter + packDrg (schema-declared, dropped today)
│   └── PackElementRepository.java     # NEW port: fetch(PackRoot) -> PackSnapshot (partial-tolerant);
│                                      #      write(element) by id (replace-existing); domain-type signatures only
└── adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/
    ├── FilesystemPackElementRepository.java  # NEW: implements the port; reuses PackSchemas/ParsedYaml (read);
    │                                  #      resolves id->path by locate-by-id via file-kinds classification
    ├── TacticReader.java              # NEW: parse a *.tactic.yaml into Tactic (steps + references structured)
    ├── TacticWriter.java              # NEW: serialize Tactic -> YAML via a NEW SnakeYAML Dump emitter (GraphShardWriter pattern)
    └── PackDescriptorWriter.java      # NEW: serialize PackDescriptor (incl. active_charter/pack_drg) -> pack.yaml, same Dump emitter

service/adapter-out-yaml/src/test/
├── java/.../PackElementRoundTripTest.java    # NEW: semantic round-trip + positive controls + partial-tolerant
├── resources/roundtrip-fixtures/<pack>/      # NEW: proving fixture pack (descriptor + tactics)
service/charter-service/src/test/java/.../HexagonalArchitectureTest.java  # EXTEND: port signature rule + known-bad control
```

Final source-code module placement (domain port in `charter-domain`, adapter in `adapter-out-yaml`) is fixed; concrete type names above are indicative and may be refined in tasks.

## Implementation Concern Map

Decomposes the architectural intent into IC-## concerns for `/spec-kitty.tasks` to turn into work packages. Dependencies flow IC-01 → IC-02 → IC-03; IC-04 depends on all three.

Each IC carries its own unit tests; IC-04 is the integration/acceptance battery. Per the charter's ATDD posture, the port-level acceptance test (IC-04) is authored against IC-02's port **before** IC-03's adapter fills it.

- **IC-01 — Domain models & equality** (`charter-domain`): `Tactic`, `TacticStep`, `TacticReference` records; the new descriptor+tactics aggregate (distinct from `CharterPack`, no graph); a domain locator value (`PackLocator`, or reuse `PackRoot`). **Extend `PackDescriptor`** with `Optional<String> activeCharter` + `packDrg` (schema-declared path strings dropped today — ripples to `PackDescriptorReader` and existing callers; D8). Pins equality semantics: structural record equality; all lists order- and duplicate-sensitive; null==absent and absent-list==empty-list (single-sourced, D4); scalar-typing stringification pinned; `undeclaredKeys` excluded. No storage coordinate on any model. *Refs: FR-002, FR-006, FR-009, NFR-002, C-002, C-004, C-006.*
- **IC-02 — Domain repository port** (`charter-domain`): the new `PackElementRepository` port — a single-pack, partial-tolerant `fetch(PackRoot)` returning the aggregate plus reported violations, and a per-element `write`-by-id (replace-existing). Signatures expose only domain types (the location is domain `PackRoot`/`PackLocator`, never the infra `PackLocation`). Distinct from `CharterPackRepository` (`load()→CharterOffering`, all-or-nothing). *Refs: FR-001, FR-003, FR-007, FR-008, C-006.*
- **IC-03 — Filesystem adapter** (`adapter-out-yaml`): the port implementation — a tactic-content reader (today tactics are only `ArtifactFacts(file,kind,id)`; it must still LOCATE tactic files via the existing `file-kinds.yaml` classification), a descriptor writer and a tactic writer built on a **new SnakeYAML `Dump` emitter** (GraphShardWriter pattern — not `YamlDocument`/`ScalarWriter`, which cannot construct a document; D6/MAJOR-2). Reuses `PackSchemas`/`ParsedYaml` on the read side. Atomic write (temp-then-rename) with a **failure-injection seam**, pack-root containment, id→path resolved **in the adapter** (locate-by-id). Does not use `GraphShardWriter` itself or any graph representation. *Refs: FR-001–004, FR-007, NFR-004, C-003, C-004.*
- **IC-04 — Round-trip & layering verification** (tests + fixtures): (a) a **direct fetch-fidelity oracle** asserting fetched step/reference fields (incl. `when`, nested step refs, duplicate order) equal the fixture **literals** — guards reader-drop blindness (M1); (b) the semantic round-trip with a same-fixture control that mutates→**writes**→refetches (not in-memory `equals()`); (c) replace-existing (write-is-load-bearing); (d) the single shared fixture for presence/absence + reference order/duplicates; (e) descriptor-only/empty-collection and the `active_charter`/`pack_drg` control descriptor; (f) partial-tolerant invalid-file with a same-fixture valid-file control, the round-trip assertion set **filtered to valid elements**; (g) ArchUnit port-signature rule with a **committed known-bad sample** that asserts a violation fires; (h) write-safety: failure-injection atomicity + in-root positive control alongside the path-escape refusal. Fixtures under `adapter-out-yaml/src/test/resources`. Delineated from the existing byte-level `OpenPacksRoundTripTest`. *Refs: FR-002, FR-005, FR-006, FR-008, FR-009, NFR-001, NFR-002, NFR-004, SC-001, SC-002, SC-003.*

## Phase 0: Outline & Research

See [research.md](./research.md). All spec decisions are resolved (no `[NEEDS CLARIFICATION]`); research records the design rationale (new aggregate/port vs. reuse, id→path mapping, equality semantics, atomic write, references/steps opaque-struct treatment) and the supply-chain nil result.

## Phase 1: Design & Contracts

See [data-model.md](./data-model.md) and [quickstart.md](./quickstart.md). `contracts/` is intentionally absent (`meta.json` `"contracts": "none"`): the mission adds no external API surface; the repository port is an internal domain interface captured in the data model.
