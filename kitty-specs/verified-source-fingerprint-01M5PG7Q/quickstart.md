# Quickstart: verifying this mission

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25 PATH=$JAVA_HOME/bin:$PATH
./gradlew verifyAll -PopenPacksDir=<open-packs checkout at openPacksRef>
```

Focused runs:

```bash
./gradlew :packs:test --tests '*PackFileSet*' --tests '*FileSystemPackSources*' --tests '*ConsistentRead*'
./gradlew :deployable:test --tests '*CharterContractSuite*'
```

What to look for:

- `service.feature`: the tactic list and single-tactic 200s carry `Charter-Source-Fingerprint`
  once, equal to `/info`'s `sourceFingerprint`.
- `errors.feature`: a tactic file changed mid-read refuses both tactic reads with `stale_input`
  and no fingerprint; the retry answers; a changed pack during a read of an absent tactic is
  `stale_input`, not 404.
- The generated-tree test reports its seed on failure.
