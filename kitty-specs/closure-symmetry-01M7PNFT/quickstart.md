# Quickstart: Closure symmetry

```bash
# The symmetry table and the closure's unit tests
./gradlew :pack-validation:test --tests '*ClosureSymmetryTest' :packs:test --tests '*PackClosure*'
# The load and round-trip guards that must stay green unchanged
./gradlew :packs:test --tests '*GuardAndLifecycle*' :pack-validation:wholePackRoundTrip
# #593, repeated
for i in $(seq 1 10); do ./gradlew :packs:test --tests '*MixedReadConcurrencyTest' --rerun -q || break; done
# The gate
./gradlew verifyAll
# The open-packs drift test
git clone --depth 1 https://github.com/spec-kitty/spec-kitty-open-packs /tmp/open-packs
./gradlew :pack-validation:driftTest -PopenPacksDir=/tmp/open-packs
```
