# Quickstart: verifying this mission

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew syncPacks
./gradlew :packs:test -PrequireSymlinks=true \
  --tests '*ParsedYamlTest' --tests '*EmbeddedSchemasTest' --tests '*InterruptedCommitRepairTest' \
  --tests '*HttpsGuideFetcherTest' --tests '*HttpsGuideFetcherLoopbackTest'
./gradlew verifyAll -PrequireSymlinks=true
```

Planted breaks (one per guard, reverted after): raise the YAML alias budget past the bomb; force the
backtracking pattern engine; make the listing wait on a held lock; drop the fetcher's deadline on the lookup;
drop it on the body. Each test must go red.
