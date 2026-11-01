---
title: "ADR-0017: Charter Pack schemas are imported from core's pydantic models as canonical JSON Schema"
description: Core's pack and artifact models are exported to JSON Schema and owned here; the YAML adapter validates packs with networknt, and the domain keeps the rules a schema cannot express.
doc_status: active
updated: 2026-10-27T00:00:00Z
decision_status: Accepted, with Amendments 2026-10-19 and 2026-10-20; superseded in part by ADR-0028 (2026-10-26) and ADR-0036 (2026-10-27)
decision_date: '2026-10-20'
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0006-onion-vertical-slices-archunit
  - adr/0012-pack-mutability-policy
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - conformance/divergence-register
audience:
  - profile: pack-author
    guidance_text: Read this for the schema your pack files are validated against, and what changed from core's models.
  - profile: charter-service-contributor
    guidance_text: Read this for where pack validation lives in the service and which rules belong in the domain.
  - profile: core-maintainer
    guidance_text: Read this for how the pack schemas now owned here relate to core's pydantic models.
---

# ADR-0017: Charter Pack schemas are imported from core's pydantic models as canonical JSON Schema

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-19). Decider: Stijn Dejongh (operator). Taken on
2026-10-19 and ratified on 2026-10-27, this decision is still revisited as the system evolves, on the
triggers under [Revisit when](#revisit-when).

See [Amendment 2026-10-19](#amendment-2026-10-19-operator-rulings): the operator's rulings of that
day refine decision 2 and the points it touches. See [Amendment 2026-10-20](#amendment-2026-10-20-r20-the-dependency-is-flipped):
the schemas are edited here and core pulls them; the import tooling is retired.

Superseded in part by [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) (2026-10-26): the relation between two rules is `tension`; `impacts` is a per-value list on one artifact.

Superseded in part by [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) (accepted 2026-10-27): unknown and retired `pack.yaml` keys are refused, not warned (rulings R5 and R13).

## Amendment 2026-10-19 (operator rulings)

The operator ruled on 2026-10-19. The rulings refine or override
the decisions below. The original text stays. Each point it changes is annotated here and, where the amended text
stands in decision 2, marked inline. Every
difference from core that follows is in the [divergence register](/conformance/divergence-register/),
and every change the import made to the schemas (C1-C16) is tabled in
[the mission research](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16).

### Decision changes

- **`pack_version` is required at every level** (R4). Open-questions Q13 holds over decision 2's
  "a built-in pack declares `pack_version`". The value is a non-empty string. Register entry DIV-004.
- **All lenient conversions are dropped** (R9). These are decision 2's two (a scalar `role` read as
  `roles`, and `schema_version: "1"` read as an integer), plus role lowercasing and the
  string-to-integer conversion. A file is judged as written. Register entry DIV-010.
- **The fragment header fields are not authored** (R7). `pack_name`, `source_kind`, `source_ref`,
  `layer_index` and `provenance_marker` are refused as unknown keys. DIV-008.
- **Edge endpoints are URNs only** (R6), in shards and in fragments. A bare local id is refused. DIV-007.
- **Unknown and retired `pack.yaml` keys are warned, not refused** (R5, R13), with the diagnostic
  `pack_unknown_key`. DIV-005 and DIV-006.
  *(Superseded in part by ADR-0036, accepted 2026-10-27: an unknown or retired key is a
  schema violation and is refused; `pack_unknown_key` is retired.)*
- **Fragment node kinds have one name per concept** (R14), with no aliases. `actions` and
  `anti_patterns` are accepted; `mission_steps` and `mission_type` are refused. DIV-011.
- **URNs take the 15 kinds the service models** (R15). Core's `glossary` and `glossary_scope` are
  refused. DIV-012.
- **Shard `generated_by` is optional** (R8). The service is laxer than core here. DIV-009.
- **The `impacts` relation and edge strength are modeled** (ADR-0009 D1, import changes C7 and C12). Core
  has neither yet; the contract leads and core aligns at the cutover. DIV-018.
  *(Superseded in part by ADR-0028, 2026-10-26: the relation between two rules is `tension`;
  `impacts` is a per-value list on one artifact.)*
- **Edge `provenance` is not authored** (plan call P10). It is a merge-time marker that core
  stamps. DIV-019.

### Corrections to Context

- **The corpus count.** The investigation counted 299 artifact files. That is 275 artifact files
  under core's scanner, plus 14 shards, 4 fragments, 2 `pack.yaml` files and 4 `org-charter.yaml`
  files. At the pinned open-packs commit the service classifies all 516 YAML files: 409 are mapped
  to a schema and 107 are excluded, each class with a reason.
- **The lost validators.** The list of custom validators that do not survive the export gains
  `routing_priority`, `max_concurrent_tasks`, lenient adherence and the retired-field rejections.
  The schemas now encode the first three where a schema can express them (import change C11). The
  retired-field rejections become warnings (R13).

## Context

[ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/) makes this repository
the owner of the Charter Pack schemas. The first pack goal is to reproduce the behavior of core's
pack validator and doctor on the real packs in spec-kitty-open-packs (#115). The operator asked
whether core's existing pydantic models could be carried over and checked against this design.

An investigation on 2026-10-19 (core `9554e7b2`, open-packs `7f063ae`) found:

- **Core has two sources.** The pydantic models are what `validate_pack` uses
  (`pack_validator.py:243-284`). There is also a set of hand-written YAML schemas
  (`src/charter/offering/schemas/*.schema.yaml`). These cover 7 of 14 kinds, are not used by the
  validator, and have already drifted from the models (agent profile).
- **The models export cleanly.** `model_json_schema()` succeeds for all 14 models: the pack
  descriptor, graph shard, org fragment, nine artifact kinds, org charter and `charter.yaml`. The
  output uses Draft 2020-12 vocabulary, with no discriminated unions. `extra="forbid"` becomes
  `additionalProperties: false` on top-level models. There is no `$schema` or `$id`, and some
  nested sections stay open.
- **The exported schemas agree with pydantic on real packs.** On all 299 artifact files of
  open-packs there are 0 disagreements.
- **Custom validators do not survive the export.** Mutations show the gaps: the built-in pack's
  `pack_version` rule, PEP 440 parsing of `requires_core`, the URN grammar and prefix-equals-kind
  check, the fragment kind vocabulary and unique step ids. Two lenient conversions also do not
  survive: a scalar `role` coerced to `roles`, and `schema_version: "1"` coerced to an integer.

## Amendment 2026-10-20 (R20: the dependency is flipped)

The operator ruled on 2026-10-20 (R20, at the pre-PR review). The dependency is flipped: the
schemas in `contracts/packs/schemas/` are canonical here and are edited in place, and core pulls
them. `contracts/packs/VENDORED.md` and `scripts/packs/sync_schemas.py` are retired, so decision 1's
`VENDORED.md` and the "imported from core" wording below describe the one-time import only. The
import's changes C1-C16 are recorded in
[the mission research](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/pack-schemas-and-level-policy-01M5AFMA/research.md#import-changes-c1-c16)
and in git history. The risk "the imported schemas and core's models drift" now reads: core adapts
to the schemas, and the doctor parity test (#115) compares verdicts with core's.

## Decision

1. **Import the exported schemas as this repository's canonical pack schemas**, one file per pack
   file kind, under `contracts/packs/schemas/`. *(Amended 2026-10-20: the import was one-time. The
   origin, core's models at commit `9554e7b2` exported with `model_json_schema(mode="validation")`,
   and every change made on import are recorded in the mission research; the schemas are now
   edited here and core pulls them. See [Amendment](#amendment-2026-10-20-r20-the-dependency-is-flipped), R20.)*
2. **Change them on import:**
   - add `$schema` and `$id`;
   - close the nested sections that stay open;
   - encode the URN patterns, the fragment kind vocabulary and the rule that a built-in pack
     declares `pack_version`; *(Amended 2026-10-19: every pack declares it, as a non-empty string;
     see [Amendment](#amendment-2026-10-19-operator-rulings), R4.)*
   - drop the two lenient conversions (`role` to `roles`, `schema_version: "1"`), which no
     open-packs file needs. *(Amended 2026-10-19: all lenient conversions are dropped, also role
     lowercasing and string-to-integer; see [Amendment](#amendment-2026-10-19-operator-rulings), R9.)*
3. **The YAML adapter validates with networknt json-schema-validator.** It runs in
   `packs.infra.storage` only (formerly `adapter-out-yaml`), over the tree snakeyaml-engine already loads, through Jackson. The
   domain records stay hand-written, as [ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)
   requires. The dependency gets a catalog entry, an exact pin and verification metadata.
4. **Rules a schema cannot express live in the domain**, with the pack-level policy move (#48):
   PEP 440 parsing, URN prefix equals kind, unique step ids, endpoint binding and the cross-file
   rules.
5. **Generated records are not used.** jsonschema2pojo and openapi-generator would create
   annotated, mutable types that conflict with the onion rules of
   [ADR-0006](/adr/0006-onion-vertical-slices-archunit/).

## Consequences

### Positive

- The schema structure comes from what core validates today, so pack verdicts start in parity.
- Pack authors get one published schema per file kind, owned here.
- The adapter stops hand-coding structural checks that duplicate a schema.

### Negative

- A new runtime dependency in the adapter, with its supply-chain upkeep.
- Packs that relied on the lenient conversions are refused here. None in open-packs does.

### Risks

- The schemas and core's models drift. Mitigation: the divergence register lists every difference,
  and the doctor parity test (#115) compares verdicts with core's. *(Amended 2026-10-20, R20: the
  schemas are canonical here; the import's change list is in the mission research.)*

## Alternatives considered

### Keep the schemas as documentation and validate by hand

No new dependency, but the code and the schemas can disagree. Not chosen.

### Write new schemas from the domain model

The cleanest result, but the most work, and parity with open-packs would have to be proven again.
Not chosen.

### Import core's hand-written YAML schemas

They cover half the kinds, the validator does not use them and they have drifted. Not chosen.

## Revisit when

- networknt cannot validate a schema construct the packs need, or its upkeep becomes a burden.
- Core changes its models in a way this repository does not want to follow.

## `requires_core` stays in `pack.yaml`

The operator ruled on 2026-10-19 to keep it. `requires_core` is an optional PEP 440 version
range of the Spec Kitty core versions a pack supports. Core validates it at descriptor load
(`src/charter/offering/packs/pack_descriptor.py:118-140`). `charter pack vendor` refuses a pack
the running core does not satisfy (`pack_vendor.py:114-121`, `core_compat.py`). Open-packs
already declares it (`built-in.source.yaml:45`), and [ADR-0012](/adr/0012-pack-mutability-policy/)
relies on it to make an older CLI refuse a pack with a policy file. The imported descriptor schema
keeps the field as optional. Its PEP 440 grammar is checked in the domain, because a JSON Schema
pattern cannot express it fully.
[ADR-0005](/adr/0005-service-registers-and-distributes-packs/) decision 9 is amended to match.

## References

- Issues #48 (pack-level policy in the domain), #114 (the schema import), #115 (pack doctor parity).
- [ADR-0005](/adr/0005-service-registers-and-distributes-packs/), [ADR-0006](/adr/0006-onion-vertical-slices-archunit/),
  [ADR-0012](/adr/0012-pack-mutability-policy/), [ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/).
