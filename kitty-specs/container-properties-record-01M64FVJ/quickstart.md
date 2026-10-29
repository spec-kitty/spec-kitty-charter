# Quickstart: Container profile properties record

```bash
# The new tests and the pinned side effect
./gradlew :deployable:test --tests '*ContainerPropertiesTest*' --tests '*ProblemResponsesTest*'
# Everything CI runs
./gradlew verifyAll
```

Under the profile, a start without `CHARTER_CONTAINER_ALLOWED_HOSTS` (or `charter.container.allowed-hosts`) fails with
`charter.container.allowed-hosts is required under the container profile: name at least one host`.
