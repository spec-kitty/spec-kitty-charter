# Quickstart: verify the closeout locally

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :kernel:test :missions:test :packs:test --tests '*Markdown*' --tests '*MissionValues*' --tests '*MissionTypeBinding*'
./gradlew :deployable:test --tests '*HexagonalArchitectureTest*'
./gradlew :packs:wholePackRoundTrip -PopenPacksDir=../spec-kitty-open-packs
# #319 against the real pack, spelled with '..'
./gradlew :packs:openPacksRoundTrip --tests '*FileSystemPackInspectorTest*' -PopenPacksDir=$PWD/../spec-kitty-open-packs
./gradlew verifyAll
python3 scripts/docs/check_docs.py all
```
