# Quickstart: try the activation register

```bash
./gradlew :deployable:bootRun        # serves on loopback; CHARTER_WORKTREE_TOKEN must be set (ADR-0018 D9)
T="Authorization: Bearer $CHARTER_WORKTREE_TOKEN"

# create
curl -sS -H "$T" -H 'Content-Type: application/json' -X POST localhost:8080/activations \
  -d '{"schemaVersion":1,"packs":[{"packId":"internal"}],"selected":{"directive":["DIRECTIVE_001"]}}' -i
# read, then the closure it composes
curl -sS -H "$T" localhost:8080/activations/<id> -i
curl -sS -H "$T" localhost:8080/activations/<id>/packs
# select with cascade (PR B)
curl -sS -H "$T" -H 'If-Match: "<revision>"' -H 'Content-Type: application/json' \
  -X PUT localhost:8080/activations/<id>/selections/directive/DIRECTIVE_002 -d '{"cascade":["all"]}'
```

Tests:

```bash
./gradlew :activations:check :packs:check        # module scope while a WP is in flight
./gradlew :deployable:test --tests '*CharterContractSuite*'   # the @contract scenarios
./gradlew verifyAll                               # once per PR, at closeout
```
