# Quickstart: verifying this mission

```bash
. ~/.sk-env   # JAVA_HOME=/opt/jdk-25
./gradlew verifyAll -PopenPacksDir=../spec-kitty-open-packs   # every gate, scenarios included
./gradlew :charter-service:test --tests '*CharterContractSuite*'   # the contract scenarios only
python3 scripts/docs/check_docs.py all
npx --yes markdownlint-cli2@0.18.1
npx --yes cspell@10.4.0 --no-progress "docs/**/*.md"
```

Read the answer by hand. The service is started with a token and a project configuration
directory:

```bash
CHARTER_WORKTREE_TOKEN=secret ./gradlew :charter-service:bootRun \
  --args='--charter.project-config-dir=.kittify --charter.packs[0].name=built-in --charter.packs[0].level=built-in --charter.packs[0].path=../spec-kitty-open-packs/built-in'
curl -s -H 'Authorization: Bearer secret' 'localhost:8080/api/v1/mission-types/software-dev/actions/plan/context?depth=1' | jq '.guidance | group_by(.relation) | map({(.[0].relation): length}) | add'
curl -s -X POST -H 'Authorization: Bearer secret' localhost:8080/api/v1/refresh | jq
```
