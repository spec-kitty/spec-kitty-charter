# Quickstart: pack-level files round trip

```bash
# Prerequisites: Java 25 (or -Pcharter.javaVersion=21 locally), ../spec-kitty-open-packs at openPacksRef
export JAVA_HOME=/usr/lib/jvm/temurin-25

# Unit and storage tests of the pack-level kinds
./gradlew :packs:test --tests '*Preset*' --tests '*PackManifest*' --tests '*PackDescriptor*'

# The whole-pack gate (pack.yaml and pack.md green; presets and manifest on the known-failure list)
./gradlew :packs:wholePackRoundTrip

# Everything CI runs
./gradlew verifyAll
python3 scripts/docs/check_docs.py all
```

A sanitized preset:

```yaml
schema_version: "1.0"
id: house-rules
description: The organisation's house rules.
activated_kinds: [directives]
activated_directives:
  - id: GUARD
    must_have: true
  - id: STYLE
```

Loading a pack with this preset gives `pack.presets()` = `[house-rules]`, with `required()` = `{directive: [GUARD]}`
and `governing()` = true.
