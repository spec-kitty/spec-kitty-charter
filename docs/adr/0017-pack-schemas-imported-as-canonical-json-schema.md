---
title: "ADR-0017: Charter Pack schemas are imported from core's pydantic models as canonical JSON Schema"
description: Core's pack and artifact models are exported to JSON Schema and owned here; the YAML adapter validates packs with networknt, and the domain keeps the rules a schema cannot express.
doc_status: active
updated: 2026-10-19
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0006-onion-vertical-slices-archunit
  - adr/0012-pack-mutability-policy
  - adr/0015-charter-repository-is-canonical-for-charter-structure
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

**Accepted (provisional)** — 2026-10-19. Decider: Stijn Dejongh (operator). Like every decision
taken on 2026-10-19, this one is provisional: it is revisited as the system evolves, on the
triggers under [Revisit when](#revisit-when).

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

## Decision

1. **Import the exported schemas as this repository's canonical pack schemas**, one file per pack
   file kind, under `contracts/packs/schemas/`. A `VENDORED.md` records their origin (core's
   models at a named commit, `model_json_schema(mode="validation")`) and every change made here.
2. **Change them on import:**
   - add `$schema` and `$id`;
   - close the nested sections that stay open;
   - encode the URN patterns, the fragment kind vocabulary and the rule that a built-in pack
     declares `pack_version`;
   - drop the two lenient conversions (`role` to `roles`, `schema_version: "1"`), which no
     open-packs file needs.
3. **The YAML adapter validates with networknt json-schema-validator.** It runs in
   `adapter-out-yaml` only, over the tree snakeyaml-engine already loads, through Jackson. The
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

- The imported schemas and core's models drift. Mitigation: `VENDORED.md` lists every change,
  and the doctor parity test (#115) compares verdicts with core's.

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
