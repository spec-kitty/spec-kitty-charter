# Quickstart: Nightly SonarCloud and per-PR complexity gate

**Historical; superseded in part.** See the note at the top of [spec.md](spec.md): the complexity limits, register and stale check did not land.

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-25
./gradlew syncPacks && ./gradlew verifyAll        # complexity limits + register check run in check
./gradlew :packs:checkstyleMain                   # one module's limits
```

- **A method fails a limit.** Simplify it. Adding a register entry for a new method is not accepted.
- **A register entry is reported stale.** The method no longer breaks the rule: delete the entry and
  lower the Size count.
- **Nightly report.** Actions → "Sonar (nightly, reported)" → the run summary. It skips green until the
  operator adds `SONAR_TOKEN`; run it by hand with "Run workflow".
