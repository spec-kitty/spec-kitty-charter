---
affected_files: []
cycle_number: 1
mission_slug: pack-doctrine-artefacts-01M5JHD0
reproduction_command:
reviewed_at: '2026-10-22T22:38:06Z'
reviewer_agent: claude
wp_id: WP03
---

# WP03 review — one MAJOR fix (layering), otherwise approved

Verified green (6 StyleguideEntityTest + 2 ConfigValueTest) and the ConfigValue
fidelity/non-coercion proof is exactly right. One blocking fix before approval:

## MAJOR — ConfigValue must live in `packs.domain`, not `packs.infra.storage`
`Styleguide` (a `packs.domain` entity) holds `Optional<ConfigValue>`, so with
`ConfigValue` in `packs.infra.storage` the domain depends on infra. That fails the
hexagonal gate — both `ONION_DOMAIN_DEPENDS_ON_NOTHING_OUTWARD` (domain depends on
nothing outward, incl. `..infra..`) and `PACKS_DOMAIN_IS_PURE` (domain depends only on
java/JSpecify/kernel/its own slice's domain) — and would red verifyAll (NFR-003/SC-004).

Fix: move `ConfigValue` into `packs.domain`:
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/ConfigValue.java`
- move its test to `service/packs/src/test/java/ai/speckitty/charter/packs/domain/ConfigValueTest.java`
- update `Styleguide`'s import (now same package) and `StyleguideDso`'s import
  (infra→domain is the allowed direction).
Keep the implementation identical (raw-scalar typing, structural equality, of()/toParsed()).
Record the path change as an owned_files deviation with this rationale in the commit.

Everything else in WP03 is approved as-is — no other change requested. The internal
`Optional<List>` for patterns/anti_patterns is fine; the public `List` accessor stays.
