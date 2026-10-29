# Quickstart: Serve the contract document

```bash
./gradlew :deployable:test --tests '*OpenApiDocument*' --tests '*ContractDocument*'
./gradlew :packs:test --tests '*ContractDocument*'

# Run the service and fetch the document (token from the environment, 32+ characters)
CHARTER_WORKTREE_TOKEN=... ./gradlew :deployable:bootRun
curl -si -H "Authorization: Bearer $CHARTER_WORKTREE_TOKEN" http://127.0.0.1:<port>/api/v1/openapi.yaml
curl -si -H "Authorization: Bearer $CHARTER_WORKTREE_TOKEN" -H 'If-None-Match: "sha256:<hex>"' \
  http://127.0.0.1:<port>/api/v1/openapi.yaml      # 304, no body
sha256sum build/contracts/contract/charter-openapi.yaml   # equals the ETag's hex
```
