# Data Model: Nightly SonarCloud and per-PR complexity gate

## Register entry (`config/checkstyle/suppressions.xml`)

| Field | Source | Rule |
|---|---|---|
| rules | `checks` attribute (an alternation such as `CyclomaticComplexity\|NPathComplexity`) and comment `rule:` | Each is one of CyclomaticComplexity, NPathComplexity, MethodLength, NestedIfDepth |
| file | `files` attribute (regex) and comment `file:` | Matches exactly one source file |
| method | `query` attribute, `@text='<name>'` | Names one method declared in that file |
| reason | comment `reason:` | Non-empty |
| sunset | comment `sunset:` | ISO date |

**Invariants:**
- Each (file, method) has at most one entry; the entry lists every rule the method breaks.
- An entry is *live* only while the probe run reports a violation of **each** rule it lists on its
  method. An entry with a rule that no longer fires is stale (it must narrow or go), and a stale entry
  fails the build.
- The header's `Size: N entries` equals the number of entries.

**Lifecycle:** an entry is added (only for methods present when the mission lands), then shrinks
(removed when the method is simplified, which the stale check forces), or is re-justified at its sunset.

## Probe report

The probe is the Checkstyle XML report produced with an empty register, restricted to the register's
files. Each error carries a source (check), a file, a line and a message; for the four complexity checks
the line is the method's declaration.

## Nightly analysis run

| Field | Value |
|---|---|
| trigger | schedule, workflow_dispatch |
| token state | present → analyze; absent → skip green with a notice |
| inputs | service modules' `build/classes/java/main`, aggregated `testCodeCoverageReport.xml` |
| output | SonarCloud analysis; quality-gate verdict in the step summary |
