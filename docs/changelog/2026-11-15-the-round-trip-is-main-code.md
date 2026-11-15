---
okf_version: "0.2"
title: The whole-pack round trip is main code, its verdict a pack-validation rule
description: The round trip flow moves into packs main code behind PackRoundTrip, its verdict into pack-validation as WholePackRoundTripRule, and the gate and drift test follow.
doc_status: active
updated: 2026-11-15T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you run or change the whole-pack gate, the drift test or their expectation lists.
---

# 2026-11-15 — The round trip is main code

[#555](https://github.com/spec-kitty/spec-kitty-charter/issues/555), ADR-0075 decision 8 as the operator amended it on
2026-11-15. The whole-pack round trip lived in `:packs`' test source set. It is now production code in two parts.

- **The flow.** `PackRoundTrip.run(source, target)` is in `packs.infra.storage`. It reads a pack into the domain, writes
  it through the storage service and reads it back. It returns a `PackRoundTripResult` holding the census, one outcome
  per modelled file with both sides read in their content form, and every written file.
- **The verdict.** `WholePackRoundTripRule` is in `pack-validation`, a kernel `Rule` over that result. It compares by
  meaning (ADR-0036) against an expectation and reports `round_trip_red` or `round_trip_written_unpaired`.
- **The tasks moved.**
  - The gate is now `./gradlew :pack-validation:wholePackRoundTrip`, with its reports under
    `service/pack-validation/build/reports/whole-pack/`.
  - The drift test is now `./gradlew :pack-validation:driftTest -PopenPacksDir=<path>`.
  - The CI workflows follow. spec-kitty-open-packs changes its command when it moves its charter pin past this change.
- **The expectation lists moved** to `service/packs/src/testFixtures/resources/whole-pack/`, beside the example packs
  they name. Their content is unchanged.
- **The verdict is unchanged.** The same three example packs are green against the same lists. A file of a kind the
  round trip does not model still fails both the gate and the drift test, naming its kind and path (#547).
