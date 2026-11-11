# Quickstart

```bash
./gradlew :packs:test :deployable:test          # every pack-reading test, over the example packs
./gradlew :packs:wholePackRoundTrip             # the round-trip gate, over the example packs
./gradlew verifyAll                             # no -PopenPacksDir needed
```

To add a shape: change the schema, update the example packs in the same commit, and the coverage check tells you
what is still missing.
