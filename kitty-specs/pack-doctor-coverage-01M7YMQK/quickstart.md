# Quickstart: pack-doctor coverage

```bash
# build the jar
./gradlew :pack-doctor:packDoctorJar
JAR=service/pack-doctor/build/libs/pack-doctor-*.jar
EX=service/packs/src/testFixtures/resources/example-packs

# the default validation: PACK + CLOSURE (with the preset-item rule)
java -jar $JAR validate --config-root <root-with-example-packs>

# everything registration judges, plus the round trip, with a YAML report
java -jar $JAR validate --config-root <root> --full --round-trip --report out.yaml

# the same report as XML
java -jar $JAR validate --config-root <root> --report out.xml --format xml

# repair a pack's manifest and canonical form, then validate again
java -jar $JAR remediate $EX/org-base
```

The exit codes are unchanged. `0` means valid (warnings are allowed), `1` means a violation (or `remediate` refused),
and `2` means a usage or I/O failure.

Gates before the PR: `./gradlew verifyAll`, `:pack-validation:wholePackRoundTrip`, `check_docs.py`,
markdownlint-cli2 and cspell.
