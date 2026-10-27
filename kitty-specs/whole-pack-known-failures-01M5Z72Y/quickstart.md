# Quickstart: verify the known-failure list

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew :packs:test --tests '*WholePackExpectationTest*'   # judgement, loader, report, real-list load
./gradlew :packs:wholePackRoundTrip                           # the corpus gate at openPacksRef (needs ../spec-kitty-open-packs)
sed -n '1,/^## Files/p' service/packs/build/reports/whole-pack/report.md   # totals show "known failure 0"
./gradlew verifyAll                                           # the merge gate
```

Workflow for a contributor (documented in `docs/guides/service-build.md`): file the open-packs issue (ADR-0036
decision 5), add an entry to `service/packs/src/test/resources/whole-pack/known-failures.yaml` with the `at` the
report shows, and remove the entry in the change that moves `openPacksRef` to the fixed commit (the gate fails with
`UNEXPECTEDLY_GREEN` until you do).
