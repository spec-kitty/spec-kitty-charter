# Quickstart

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew verifyAll                                   # includes the gate's unit tests and the hints tests
./gradlew :deployable:bootJar
scripts/release/smoke-service.sh service/deployable/build/libs/deployable-*.jar \
  "$(grep '^version=' gradle.properties | cut -d= -f2)" "<contract info.version>" 60
./gradlew checkServiceReleaseTag -PreleaseTag=service-v0.1.0-rc.1   # refuses: the build version is 0.1.0-SNAPSHOT
scripts/release/rehearse-service-release.sh           # every refusal case, publishes nothing
# Native (needs GraalVM 25 with native-image on JAVA_HOME; CI runs it):
./gradlew :deployable:nativeCompile -Pcharter.native=true   # Spring AOT and native-image only with this flag
scripts/release/smoke-service.sh service/deployable/build/native/nativeCompile/spec-kitty-charter-service <ver> <contract> 5
```
