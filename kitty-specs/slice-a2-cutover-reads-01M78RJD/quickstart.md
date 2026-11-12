# Quickstart: Slice A2 reads

```bash
# Run against a service with a registered pack `built-in`, the token in $T.
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/packs/built-in/presets
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/packs/built-in/presets/team
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/packs/built-in/artifacts/asset
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/packs/built-in/artifacts/asset/carrier-lint
curl -H "Authorization: Bearer $T" -o lint.py localhost:8080/api/v1/packs/built-in/artifacts/asset/carrier-lint/payload
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/packs/built-in/artifacts/skill
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/activations/$A/artifacts/asset/carrier-lint/payload
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/activations/$A/artifacts/directive/D/references
curl -H "Authorization: Bearer $T" localhost:8080/api/v1/activations/$A   # _links: edit, delete, select, …
```

To verify:

```bash
./gradlew :packs:test :activations:test --max-workers=1
./gradlew :deployable:test --max-workers=1        # contract scenarios, switch gate, link checks
./gradlew verifyAll --max-workers=1               # the merge gate
```
