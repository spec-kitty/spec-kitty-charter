# Decision Moment `01M7G73XXVE0W5AGMZTWDNPTEJ`

- **Mission:** `pack-closure-rulesets-01M7FA90`
- **Origin flow:** `plan`
- **Slot key:** `plan.squad.packs-test-rules`
- **Input key:** `packs_tests_rules`
- **Status:** `resolved`
- **Created:** `2026-11-15T18:22:07.803261+00:00`
- **Resolved:** `2026-11-16T05:26:32.098858+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How do :packs tests and the whole-pack gate get real rules? (a) a test-only testImplementation(project(':pack-validation')) in :packs. (b) move the judging tests, the gate and planted/ to pack-validation or deployable tests.

## Options

- (a)
- (b)
- Other

## Final answer

Design per research/design-r3.md with its challenge folds (operator, 2026-11-16): rules, PACK with surface profiles, CLOSURE, PackCodes and the mappers live in packs.domain; no judging port; registration judged in packs.application on the inspector's contents; pack-validation is the Spring-free check library pack-check runs (PackLoad, round-trip verdict, from #478 the set check); ADR-0075 amended. R3-b: (i) the margin criterion is met by the nightly measurement of #578's code, re-setting ADR-0062's documented margin.

**Superseded in part by S1–S3 (2026-11-16), see research/squad-shaping.md.** The surface profiles go (named
compositions); the mappers live at their edges (`PackRefusals` in `packs.application`, `PackVerdicts` in
`pack-validation`); the storage rules and `StorageCodes` live in `packs.infra.storage.rules`; registration judges the
one read, not the inspector's contents beside a second read; `pack-validation` is the validation edge (`PackCheck`,
placement, `PlacementCodes`). No judging port, the ADR-0075 amendment and R3-b (i) stand.

**S6 note (2026-11-16), see research/squad-shaping.md ("Port, no rule change").** The core's rules are plain
rulesets with no judging port; S6 adds one reading port (`PackReading`) whose readout carries the storage edge's
verdict. `PackReading` replaces the `PackInspector` port; `pack-validation` names only `packs.domain` and the kernel,
and S3's "`pack-validation` may name `packs.infra.storage.rules`" is withdrawn. No ArchUnit rule is widened. The
ADR-0075 amendment records R3 as superseded by S1 to S3 and S6; R3-b (i) stands.

## Rationale

_(none)_

## Change log

- `2026-11-15T18:22:07.803261+00:00` - opened
- `2026-11-16T05:26:32.098858+00:00` - resolved (final_answer="Design per research/design-r3.md with its challenge folds (operator, 2026-11-16): rules, PACK with surface profiles, CLOSURE, PackCodes and the mappers live in packs.domain; no judging port; registration judged in packs.application on the inspector's contents; pack-validation is the Spring-free check library pack-check runs (PackLoad, round-trip verdict, from #478 the set check); ADR-0075 amended. R3-b: (i) the margin criterion is met by the nightly measurement of #578's code, re-setting ADR-0062's documented margin.")
