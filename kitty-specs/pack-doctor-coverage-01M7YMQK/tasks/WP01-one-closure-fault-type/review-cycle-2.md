---
affected_files: []
cycle_number: 2
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-21T15:26:58Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP01
---

# WP01 review cycle 2: changes requested (reviewer-renata)

Reviewed 2debe6d..c4a452b and ca934845..c4a452b (lane head a1b5ff4; the merge only adds status files).

## What passed (keep it)
- All four cycle-1 findings are addressed. Origin is never a copy of the rule: `ClosureFault` takes the rule
  separately (`RULE`, or `closure.presets` through the long form). `Origin.NONE` is used only where no element is known.
  Producers fill a pack id, or a pack id plus a URN. The `ClosureVerdict` Javadoc is honest and names slice 7.
  `ResolverFaults.of` is the one Refusal-to-fault helper, and there is no `ClosureFault.of(Refusal)`.
- No behavior change: no expected code, status, detail or order changed. `PackRulesetsClosureTest` is untouched in
  cycle 2.
- The `FallbackProfileRule.check` and `RemovalDependents.fault` signatures (now `PackIdentifier`) are sound, and each
  has one caller. `profileOrigin` guards exactly Urn's preconditions, so it adds no new throw.
- Gates are green: compileJava and compileTestJava pass. Tests pass with --rerun-tasks: kernel 3118, packs 7167
  (6 skipped), pack-validation 465 (1 skipped), pack-check 251, with 0 failures. spotlessCheck passes on the 4 modules.

## Issue 1 [MEDIUM] PackOrigins re-implements the pack-qualified reference grammar
`service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackOrigins.java:19,42` defines a private
`QUALIFIER = "::"` and builds `of(pack).id() + QUALIFIER + artifact` by hand. The kernel already owns this written form:
`PackQualifiedReference` (`kernel/domain`). `Urn`'s Javadoc calls it "the one parser", and its `toString()` is the one
writer of `<pack-name>::<urn>`. Its constructor also checks the parts (pack name is a pack id, URN non-empty, no `::`).
The output matches today, but the claim "pack-qualified as a reference writes them" (Javadoc, and the WP's R-o note)
holds only by coincidence. A second spelling of a canonical grammar breaks the charter's one-model rule.

Fix: build the reference and use its text, for example
`new Origin(new PackQualifiedReference(Optional.of(pack.value()), artifact.toString()).toString())`.
Delete the private `QUALIFIER`. `PackIdentifier` already guarantees a pack id, so this adds no new throw.
Add one assertion to `PackOriginsTest`: `PackQualifiedReference.parse(PackOrigins.of(ACME, directive).id())` round-trips
to `acme` and the URN.

## Issue 2 [LOW] Duplicated empty heading in the WP's Origin note (commit ca0d0e3)
`kitty-specs/.../tasks/WP01-one-closure-fault-type.md:153` holds `## Origin (operator ruling R-o, 2026-11-21)` with no
body. The section itself follows at line 155. Drop one of the two headings.

## Note (no action required)
`FallbackProfileRule.profileOrigin` (`:92`) tests `profileId.contains("::")` to avoid Urn's throw. That is fine. A
qualified profile id falls back to the declaring pack alone, which matches the R-o "do not invent" rule.
