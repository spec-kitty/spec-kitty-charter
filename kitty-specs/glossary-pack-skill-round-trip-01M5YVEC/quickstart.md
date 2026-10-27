# Quickstart: check the glossary pack and skill round trip

From the repository root, with `spec-kitty-open-packs` checked out at `openPacksRef` beside it:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :packs:test                 # unit tests, corpus test, registry tests
./gradlew :packs:wholePackRoundTrip   # the #187 gate: spec-kitty-core.glossary-pack.yaml green
./gradlew :packs:openPacksRoundTrip   # every pack with a pack.yaml, internal's skills included
./gradlew verifyAll                   # everything CI runs
```

The gate report is written to `build/reports/whole-pack/report.md`. Its `glossary-pack` row is
green, and it has no `other:glossary_packs` row.
