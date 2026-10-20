# Quickstart: pack schemas and pack validation

## Build and verify

```bash
export JAVA_HOME=/opt/jdk-25 PATH=/opt/jdk-25/bin:$PATH
# open-packs checked out at the pinned commit (gradle.properties: openPacksRef)
git -C ../spec-kitty-open-packs checkout "$(grep '^openPacksRef=' gradle.properties | cut -d= -f2)"
./gradlew verifyAll -PopenPacksDir=../spec-kitty-open-packs
```

These tasks matter here:

| Task | What it checks |
|---|---|
| `:adapter-out-yaml:test` | `PackSchemasTest` (metaschema, unique `$id`, local `.json` refs, strict keywords, `VENDORED.md` lists every file), the vocabulary and mapper tests, the planted files and the I2 parity test |
| `:adapter-out-yaml:openPacksRoundTrip` | The byte and data round-trip, plus the acceptance checks in the same class: pinned clean HEAD, 409 mapped and 107 excluded, all valid, timing |
| `:charter-service:test` | The contract scenarios (`packs.feature`) and the ArchUnit gate |

## Regenerate the schemas from core

Run this only when core's models change, and only by an operator decision (ADR-0015 D4: a
refresh never overwrites a listed change).

```bash
python3 -m venv ~/.venv-sk && ~/.venv-sk/bin/pip install <core checkout at the commit in VENDORED.md>
~/.venv-sk/bin/python -I scripts/packs/sync_schemas.py   # export from core's models + apply the VENDORED.md changes
git diff --exit-code contracts/packs/schemas   # clean = the committed schemas are reproducible
```

## Mutation testing (NFR-003)

```bash
./gradlew :charter-domain:pitest --no-configuration-cache --max-workers=2 \
  -I service/charter-domain/pit-targets.init.gradle -Dpit.classes=ai.speckitty.charter.domain.PackPolicy,ai.speckitty.charter.domain.Pep440 \
  -Dpit.tests='ai.speckitty.charter.domain.*'
```

The mission records the score (80% or more, DEFAULTS mutators) and the rationale for every surviving mutant in its evidence.
