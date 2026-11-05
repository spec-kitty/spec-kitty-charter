# Quickstart: verifying this mission

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew syncPacks                                   # once per checkout

# IC-01: one Mockito version, at Spring's
./gradlew -q :deployable:dependencies --configuration testRuntimeClasspath | grep mockito-core
./gradlew -q :kernel:dependencies --configuration testRuntimeClasspath | grep mockito-core

# IC-02: the 15 strengthened checks
./gradlew :packs:test -PrequireSymlinks=true

# Gate
./gradlew verifyAll -PrequireSymlinks=true
./gradlew pitest --continue
```

FR-003 planted break: change the `RefusalCode` raised on one sampled production path, run the
test class, record the red output, revert.
