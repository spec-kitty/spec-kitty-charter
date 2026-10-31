# Quickstart: verify the DRG tension and node-field round trip

```bash
# from the repository root, with ../spec-kitty-open-packs at openPacksRef and JAVA_HOME on Java 25
./gradlew :kernel:test --tests '*DoctrineGraphTest' --tests '*TensionTest'
./gradlew :packs:test --tests '*PackPolicyGraphTest' --tests '*GraphDocumentReaderTest' \
  --tests '*GraphShardWriterTest' --tests '*VocabularyEqualityTest'
./gradlew :packs:wholePackRoundTrip      # the gate: directive.graph.yaml and anti_pattern.graph.yaml now round-trip
./gradlew verifyAll                      # every check CI runs
```

The whole-pack report is written to `service/packs/build/reports/whole-pack/` (path named by
`charter.wholePackReport`). Neither file should appear in its red list.
