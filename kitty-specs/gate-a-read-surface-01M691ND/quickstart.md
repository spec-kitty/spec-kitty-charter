# Quickstart: exercising the Gate-A read surface

Run everything with Java 25.

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :contracts:check                 # bundle, lint, lint fixtures, closure, release gate
./gradlew :contracts:test                  # closure checker and gate unit tests
./gradlew :deployable:test                 # contract scenarios (CharterContractSuite) and wiring tests
./gradlew verifyAll                        # the full gate CI runs
python3 scripts/docs/check_docs.py all     # docs checks
```

A walk through the surface, as the scenarios do. All paths are below `/api/v1`, and every request
carries `Authorization: Bearer <token>`.

1. `GET /` gives `_links.info`, `_links.offering`, `_links.artifacts` (a template) and
   `_links.service-desc`.
2. `GET /offering` gives `packs[]`: built-in, then org, then project, each with
   `_links.artifacts`.
3. `GET /packs/qa/artifacts?kind=tactic&pageSize=2` gives `items[]`, `page`, and `_links.next`
   while `page.hasNextPage` is true.
4. `GET /packs/qa/artifacts/tactic/<id>` gives `{kind, id, packId, tactic: {...}, _links}`, with
   the fingerprint header.
5. `POST /packs/qa/artifacts/directive` with a `DirectiveRegistration` gives a 201, a `Location`
   under `/artifacts/directive/`, and the envelope.
6. `GET /packs/qa/tactics` gives a 404 Problem without a `code`.
