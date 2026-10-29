# Quickstart: exercising this mission

Prerequisites: Java 25 (`JAVA_HOME=/usr/lib/jvm/temurin-25`), `../spec-kitty-open-packs` clean at `openPacksRef`.

```bash
# Unit tests of the packs module (Markdown form, bindings, fetcher matrix): fast, no open-packs checkout needed
./gradlew :packs:test

# Corpus tests (tagged open-packs, part of check; run against ../spec-kitty-open-packs at openPacksRef)
./gradlew :packs:openPacksRoundTrip

# The whole-pack round-trip gate (#187; tagged whole-pack, NOT part of check — run it explicitly)
./gradlew :packs:wholePackRoundTrip

# Contract checks (lint, breaking-change report, generation)
./gradlew :contracts:check

# Contract scenarios (Cucumber, CharterContractSuite)
./gradlew :deployable:test

# Everything CI runs, once before review
./gradlew verifyAll
python3 scripts/docs/check_docs.py all
```

Expected after PR-A2 at the new pin: the gate passes with `procedure` and `other:toolguides` gone from
`service/packs/src/test/resources/whole-pack/expected-red.yaml`; `anti_pattern.graph.yaml` still listed under #236;
`VendoredPacksTest` green.
