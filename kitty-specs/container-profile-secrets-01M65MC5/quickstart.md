# Quickstart: Container profile file and secret import

## Verify locally

```bash
./gradlew :deployable:test --tests '*ContainerSecretsTest' --tests '*ShippedConfigurationTest' \
  --tests '*ContainerPropertiesTest' --tests '*GuardWiringTest'
./gradlew verifyAll
python3 scripts/docs/check_docs.py all
CHARTER_IMAGE=example.invalid/charter:dev docker compose -f deploy/compose/compose.yaml --env-file deploy/compose/.env.example config
```

## Run under compose (once an image exists, #234)

```bash
cd deploy/compose
cp .env.example .env            # fill CHARTER_IMAGE and CHARTER_CONTAINER_ALLOWEDHOSTS
mkdir -p secrets && openssl rand -hex 32 > secrets/charter.worktree-token
docker compose up
```

A start without `./secrets` fails, naming the missing directory.
