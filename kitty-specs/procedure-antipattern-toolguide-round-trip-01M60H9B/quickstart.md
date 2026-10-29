# Quickstart: exercising this mission

Prerequisites: Java 25 (`JAVA_HOME=/usr/lib/jvm/temurin-25`), `../spec-kitty-open-packs` clean at `openPacksRef`.

```bash
# Markdown form (PR-A1): unit tests and the gate
./gradlew :packs:test --tests '*Markdown*'
./gradlew :packs:test --tests '*WholePack*'

# Procedures and toolguide guides (PR-A2): corpus round trips over built-in, internal and qa
./gradlew :packs:test --tests '*Procedure*' --tests '*Toolguide*'

# Registration over HTTP: contract scenarios
./gradlew :contracts:check

# Everything CI runs
./gradlew verifyAll
python3 scripts/docs/check_docs.py all
```

Expected: the gate passes with `procedure` and `other:toolguides` gone from
`service/packs/src/test/resources/whole-pack/expected-red.yaml`; `anti_pattern.graph.yaml` still listed under #236.
