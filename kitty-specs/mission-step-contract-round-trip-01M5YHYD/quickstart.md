# Quickstart: check the mission step contract round trip

From the repository root, with `spec-kitty-open-packs` checked out at `openPacksRef` beside it:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :packs:test --tests '*MissionStepContract*' --tests '*KindBindings*'   # the kind and the registry
./gradlew :packs:test --tests '*WholePack*' --tests '*DomainRoundTrip*'          # the #187 gate
./gradlew verifyAll                                                              # everything CI runs
python3 scripts/docs/check_docs.py all
```

The gate report shows `mission-step-contract` with 21 green files and no expected-red ones.
