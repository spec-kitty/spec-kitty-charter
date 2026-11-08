# Quickstart: verify the mission by hand

```bash
# Gate (Java 25)
JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew -PopenPacksDir=../spec-kitty-open-packs verifyAll

# Focused tests
./gradlew :packs:test --tests '*WorktreeGuardTest' --tests '*GuardPolicyTest' --tests '*ProblemHandler*'
./gradlew :deployable:test --tests '*ExposureStartupCheckTest' --tests '*ContainerHealthTest' --tests '*GuardWiringTest'
```

Container smoke test (with an image you built):

1. Set `CHARTER_CONTAINER_ALLOWEDHOSTS=localhost,charter.internal` and run `docker compose up`.
2. `curl -s localhost:8080/actuator/health` answers `{"status":"UP"}` without a token.
3. `curl -s -H 'Host: attacker.example' localhost:8080/actuator/health` answers 403.
4. `curl -s localhost:8080/actuator/env` answers 401. With the token it answers 404.
