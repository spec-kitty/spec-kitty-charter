# Research: Nightly SonarCloud and per-PR complexity gate

## R1 — Complexity distribution and limits

- **Decision:** CyclomaticComplexity 15, NPathComplexity 1000, MethodLength 60, NestedIfDepth 1, on main and test
  (operator decision `01M6HQ9K8ZPB1W0PS5CNDEE9WR`).
- **Evidence (measured 2026-11-03 at `d2e1670`, every limit probed at its minimum):**

  | Measure | Main: p50 / p99 / max | Test: max | Methods over the chosen limit (main + test) |
  |---|---|---|---|
  | Cyclomatic | 1 / 11 / 24 | 37 | 2 + 3 |
  | NPath | 1 / 189 / 10,800 | 29,030,400 | 4 + 2 |
  | Length (non-empty lines) | 4 / 39 / 75 | 135 | 1 + 8 |
  | Nested-if depth | 1 / 2 / 2 | 2 | 3 + 1 |

- **Alternatives:** strict norms (about 80 entries) and a ceiling above today's maximum (about 1 entry,
  little pressure). Both were rejected by the operator.

## R2 — Method-scoped register in one file

- **Decision:** `suppress-xpath` entries naming the method. The register switches to
  `suppressions_1_2_xpath_experimental.dtd`, and both `SuppressionFilter` and `SuppressionXpathFilter`
  read the same file (decision `01M6JHQW6K8TH51XPHZK5VNJNB`).
- **Spike (2026-11-04, Checkstyle 14.3.0, reverted afterwards):** with `CyclomaticComplexity` at 15 and
  one entry `//CLASS_DEF//METHOD_DEF[./IDENT[@text='isGlobalV4']]` for `AddressPolicy\.java`,
  `:packs:checkstyleMain` passed with 0 errors. Retargeted to a method that does not exist, it failed
  with "Cyclomatic Complexity is 24 (max allowed is 15)" at line 42. The plain `SuppressionFilter`
  loaded the XPath-DTD file without error.
- **Alternatives:** per-file `suppress` entries, rejected because they exempt new methods in the same
  file; `lines` attributes, rejected because they drift.

## R3 — Stale-entry detection

- **Decision:** a probe Checkstyle task with an empty register, over only the register's files, then a
  pure Kotlin comparison. Checkstyle never reports unused suppressions, so detection needs a run without
  the register.
- **Mechanism:**
  - `checkstyle.xml` reads the register path from a property, `${charter.suppressions}`.
  - The normal tasks set it to the real register.
  - The probe sets it to an empty register in `build/`.
  - The verifier maps each probe violation of a complexity check to its method name, read from the
    source at the violation's line, and requires one violation per entry.
- **Alternatives:** a format-only guard and a CI-only script, both rejected by the operator.

## R4 — SonarCloud workflow shape and supply chain

- **Decision:** mirror core's `sonar.yml`: nightly and dispatch, a token probe that skips green, the
  generic scan action, and the quality-gate read written to the step summary.
- **Supply-chain disposition (DIRECTIVE_051):**
  - No Gradle or library dependency is added.
  - Two actions are new to this repository: `SonarSource/sonarqube-scan-action@22918119ff8e1ca75a623e15c8296b6ea4fbe28f`
    (v8.2.1) and `SonarSource/sonarqube-quality-gate-action@7a5fffe8e523c40e0c740b6bc2712ab503e52efa`
    (v1.2.1). Both are pinned to the SHAs that core's reviewed workflows run.
  - Checkout, setup-java and setup-gradle reuse this repository's existing pins.
  - The adversarial pass is the pre-PR CI-security lens, and the trust model is: no PR-authored code
    next to the token, and no `pull_request*` or `merge_group` triggers.
- **Alternatives:** the `org.sonarqube` Gradle plugin, rejected by C-001; a per-PR `workflow_run`
  consumer, out of scope (C-004).
