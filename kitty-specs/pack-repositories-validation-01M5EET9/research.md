# Research — Pack repositories and validation module

Phase 0 decisions. The design was settled through operator co-design (two Decision Moments) and corroborated by two reference-code reviews and two adversarial spec reviews.

## Decisions

- **D1 — Aggregate boundary.** The pack is the **aggregate root** (identity `PackIdentifier`); `Tactic` is a **child entity** (identity `TacticIdentifier`, identity-based equals). Rationale: a tactic is not independently addressable; its parts (steps/references) are value objects it owns. Alternatives: Tactic-as-aggregate-root (rejected — repository-per-root would make tactics top-level, contradicting pack containment); Tactic-as-value-object (rejected — it has identity and lifecycle via replace-by-id).
- **D2 — Identity vs persistence fidelity.** Domain entities use identity equals; the round-trip `fetch(write(fetch(x)))==fetch(x)` is a **persistence-fidelity** property asserted at the adapter on a **value-equal DSO**, never on the entity. Rationale: #138 abused the domain record's value-equals as the persistence oracle (a category error across layers).
- **D3 — Identifiers as value objects.** `PackIdentifier`/`TacticIdentifier` wrap the raw id (records). Rationale: future-proof representation (ULID/GUID/string/long); the domain port speaks domain identity, the adapter maps id→location (retires the infra-ish `PackLocator` from domain signatures).
- **D4 — DIP: ports in domain, impl at the edge.** Framework-free port interfaces in `packs.domain`; a stereotype-annotated (`@Service`, future `@Repository`) `DomainRepositoryService` in `packs.infra.storage` realizes them, ctor-injecting collaborators. Corroborated by TraMBU (`OccupationRepository`⇄`OccupationRepositoryImpl`).
- **D5 — Serialization Strategy + DSO.** A generic `SerializationStrategy` with ONE concrete `YamlSerializationStrategy` (entity↔DSO↔YAML); DSO records are the persistence shape; DSO↔entity mapping is **hand-written** (no MapStruct — avoids a new dependency + annotation processor, keeps the edge simple). Rationale: cross-format transparency via Strategy; YAGNI on extra formats.
- **D6 — Validation: vendor the design, not the artifact.** A new `validation` module reimplements a lean, jspecify-only subset of `sddevelopment-be/modular-validators` core (Constraint/Constraints/Checks/ModularRuleset `guard()`+`check()`/Evaluation/Reason/Rationale/Constrained). Rationale (verified): core's compile deps (Guava, commons-lang3, log4j, first-party commons) and `1.1.1-SNAPSHOT`/GitHub-Packages publishing make it unconsumable for domain purity. Drop commons-lang3 (`String.isBlank`, `Objects`); fix the description-only `Constraint.equals`; omit `Evidence`, dynamic descriptions, the `dsl` module. Apache-2.0 attribution + a took/changed ledger.
- **D7 — Screaming architecture = ADR-0006 vertical slices (operator DM 01M5F3GQ).** Migrate the whole service onto `kernel`/`packs`/`validation`/`bootstrap` with `ai.speckitty.charter.<concern>.<layer>` packages. `kernel` kept minimal (shared Pack/DRG vocab; YAGNI until a 2nd slice); `validation` a separate pure technical kernel-library. Landed as one PR with phased, behaviour-preserving commits.
- **D8 — Construction style (DM 01M5EEV6).** Records for value objects/DSOs/params (builder only where wide); final classes + public fluent builder + package-private all-args ctor for entities; guards in ctors via the validation vocabulary. Corroborated by modular-validators' telescoping-ctor+builder+arity-overload pattern.
- **D9 — Rename via occurrence map.** The migration is a DIRECTIVE_035 bulk edit driven by `occurrence_map.yaml` (8 categories); ArchUnit rewritten to ADR-0006's glob rules with known-bad controls; format-specific `Yaml…` class names kept.
- **D10 — ADR-0006 reconciliation.** ADR-0006 (Proposed) is realized by this migration; it is amended to Accepted/realized. The prior flat role-based modules are superseded.

## Findings disposition (adversarial + reference reviews)

| Source | Finding | Disposition |
|---|---|---|
| spec-review-quality M1 | SC-003 round-trip mislabelled no-op=no | Folded — relabelled yes + cited mutation control (now FR-012/SC-003 on the DSO). |
| spec-review-quality M2 | undeclared-key drop vs blanket equality | Folded — round-trip equality scoped to declared content; undeclared-key drop a pinned exception. |
| spec-review-quality M3/M4 | absence-assertions (no public ctor; zero-imports) lack positive controls | Folded — FR-008/FR-014 name paired positive/known-bad controls. |
| spec-review-architecture M1 | purity suite can't "stay green unchanged" with a validation dep | Folded — FR-014 amends the rule + adds a validation-purity rule + known-bad. |
| spec-review-architecture M2 | existing PackElement* tests must migrate | Folded — NFR-002 scoped; old port/tests replaced (IC-08). |
| spec-review-architecture M3 | Tactic value-equal vs entity classification | Resolved — D1/D2 (entity + DSO fidelity). |
| spec-review-architecture M4/M5 | multi-pack singleton; descriptor getById ill-defined | Resolved — explicit `PackIdentifier` param (D3); descriptor repo `get()/save()`. |
| spec-review-architecture M6 | PackDescription vs PackDescriptor naming | Folded — `PackDescriptorRepository`. |
| mv-architecture-review | core unconsumable (deps/SNAPSHOT) | Adopted → D6 (vendor the design). |
| trambu-ddd-review ADOPT | ports-in-domain/edge-@Service/ctor-injection/edge-mapper/VO-wrapping/builder | Corroborates D4/D5/D8. |
| trambu-ddd-review AVOID | aggregate self-persistence; anemic domain; raw-UUID id; @Data-on-JPA; config-in-controller; no app ring; kernel dep-accretion; decorative markers | Folded into conventions + How-to-DDD doc (IC-07); our Pack/Tactic hold no repo ref; typed id VOs; validation gated pure; if DDD markers used → RUNTIME + @Target(TYPE). |

No contested finding dropped.

## Supply-chain (DIRECTIVE_051)

**NIL.** No dependency is added, upgraded, or removed. `modular-validators` is **vendored as source** (a lean jspecify-only reimplementation), not a Gradle dependency; no MapStruct or other new library is introduced. Therefore no install-safety controls apply; recorded here per the plan step contract (silence is not compliance).
