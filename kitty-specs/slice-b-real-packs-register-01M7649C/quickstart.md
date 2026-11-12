# Quickstart: verify slice B

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
# Unit and store tests of the pack model
./gradlew --max-workers=1 :packs:test
# The doc read and the retired files in the contract scenarios
./gradlew --max-workers=1 :deployable:test --tests '*CharterContractSuite*'
# The whole-pack round trip over charter's example packs
./gradlew --max-workers=1 :packs:wholePackRoundTrip
# The swap window, twenty times
for i in $(seq 20); do ./gradlew --max-workers=1 :packs:test --tests '*RegistrationConcurrencyTest*' --rerun -q || break; done
# All gates
./gradlew --max-workers=1 verifyAll
python3 scripts/docs/check_docs.py all
```

By hand, against a running service:

```bash
curl -s -H 'Accept: text/markdown' localhost:8080/api/v1/packs/org-base/doc
curl -s -H 'Accept: application/json' localhost:8080/api/v1/packs/org-base/doc
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: image/png' localhost:8080/api/v1/packs/org-base/doc   # 406
```
