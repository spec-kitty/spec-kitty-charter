# Quickstart: see the asset and template round trip work

```bash
# Java 25 (local fallback: add -Pcharter.javaVersion=21)
./gradlew :packs:test --tests '*Asset*' --tests '*Template*' --tests '*PackFileKind*' --tests '*PackFileSet*'

# The whole-pack gate over the pinned built-in pack: asset, asset-payload and template green, six pinned reds
./gradlew :packs:wholePackRoundTrip
sed -n '1,60p' service/packs/build/reports/whole-pack/report.md

# Every open pack loads, writes and reloads; built-in reports exactly the six pinned violations
./gradlew :packs:openPacksRoundTrip

# Full gate before merge
./gradlew verifyAll
```

Expected: the gate's kind table (built-in only) shows `asset` 6 expected red (the pinned inconsistency, so the gate holds
no green sidecar; the 14 consistent sidecars of the other packs are proven by the open-packs round trip and unit tests),
`asset-payload` 7 green with 2 READMEs excluded, and `template` 2 green; a one-byte lossy writer turns a payload red.
