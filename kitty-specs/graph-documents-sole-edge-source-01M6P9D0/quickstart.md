# Quickstart: verify the mission

```bash
# from the repository root, Java 25
JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon -PopenPacksDir=../spec-kitty-open-packs verifyAll
# the corpus gates at the pinned openPacksRef (the migrated part-A commit; every gate green)
JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon -PopenPacksDir=../spec-kitty-open-packs :packs:wholePackRoundTrip
# docs
python3 scripts/docs/check_docs.py
```

Check by hand:

1. `grep -rn "directive_refs\|directiveRefs\|ArtifactEdges" service contracts` returns nothing.
2. Registering a directive with a `relationships` member answers 400 `validation_failed`.
3. `GET /packs/{pack}/artifacts/directive/{id}/references` lists the directive's outgoing graph
   edges, each with its relation.
