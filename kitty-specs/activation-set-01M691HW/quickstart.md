# Quickstart: check the activation set

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
OPEN_PACKS=-PopenPacksDir=/home/user/spec-kitty-open-packs

# The contract alone: bundle, lint, planted fixtures, closure, compatibility.
./gradlew :contracts:check

# The schema parity and content-alignment test, and the wire-token parity.
./gradlew :packs:test --tests '*ActivationConfigSchemaParityTest' --tests '*WireTokenParityTest' $OPEN_PACKS

# The contract scenarios (13 x 501 pins, 6 declaration pins) and the reserved-code ArchUnit pin.
./gradlew :deployable:test $OPEN_PACKS

# Everything CI runs.
./gradlew verifyAll $OPEN_PACKS
python3 scripts/docs/check_docs.py all
```

By hand, against a running service: `PUT /api/v1/activations/a-1` with
`{"schemaVersion": 1}` answers `501` with `application/problem+json` and no `code`.
