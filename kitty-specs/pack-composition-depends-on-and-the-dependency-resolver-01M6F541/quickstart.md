# Quickstart: Pack composition

**Mission**: pack-composition-depends-on-and-the-dependency-resolver-01M6F541

## Prerequisites

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25        # Java 25; `java` is not on PATH in this environment
cd ../spec-kitty-open-packs && git checkout 86d9284695a18a71f9f7cc934f0305b1b03de19e   # the pinned openPacksRef
```

The build refuses a sibling checkout that is not at `openPacksRef` in `gradle.properties`.

## Run the module you change

```bash
./gradlew --no-daemon :service:packs:test
./gradlew --no-daemon :service:kernel:test
```

## Full gate before a PR

```bash
./gradlew --no-daemon verifyAll
python3 scripts/docs/check_docs.py all
```

## Try a compose by hand

1. In a pack's `pack.yaml`, declare:

   ```yaml
   pack_level: org
   depends_on:
     - pack_id: 01J6XW9KQT7M0YB3N4R5CQZ2EX
       range: ">=1.0,<2"
   ```

2. Configure that pack and a pack with that id in the offering.
3. Read the offering. The response lists the dependency before the pack that declares it, once.
4. Break it deliberately: point `pack_id` at an unconfigured id, then at a cycle. Each is refused with its code, and
   the message names the pack that declared the bad edge.

## What to read first

- `contracts/packs/schemas/pack-descriptor.schema.json` for the authored shape.
- `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/PackDescriptor.java` for what the service reads.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java` for the read the
  resolver feeds.
