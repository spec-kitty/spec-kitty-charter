# Charter-offering acceptance scenarios

Promise-first BDD scenarios that define **what the charter offering does for a consumer** — its
role and its boundary — independent of the command or implementation that delivers it. They are
the behavioural contract this repository specifies before choosing an implementation (see
`docs/plans/overhaul-approach.md`), and the conformance net both the current Python charter and
any future implementation must pass.

## Status

- **Layer A — the contract (here now):** the `.feature` files and `PROMISES.md`. Implementation-free
  Gherkin; they import no code and are valid as they stand.
- **Layer B — the harness (not yet wired):** the Cucumber-JVM runner and step definitions that
  execute these scenarios (as in core's `acceptance-jvm/`) are not yet set up in this repository.
  Standing it up — and pointing it at whatever answers the charter commands — is tracked in the
  issue tracker.

These three outcome features were copied from the charter-offering suite in
`spec-kitty/spec-kitty` (`tests/scenarios/charter/`) as the starting contract. The
CLI-affordance feature (`charter.feature`) was left behind on purpose: it asserts the Python
CLI's output surface, which is mechanism, not an offering promise.

## Layout

One folder per consumer outcome:

| Folder | Outcome |
|--------|---------|
| `governance-in-force/` | The governance in force is what I chose. |
| `refuses-unsound-governance/` | The system refuses unsound governance with a clear reason. |
| `share-governance-safely/` | I can share governance across a team or project safely. |
| `PROMISES.md` | Each `@promise:` id mapped to the offering authority (FR/ADR) it asserts. |

## Authoring

New scenarios follow the activated org doctrine: the `feature-and-scenario-authoring` styleguide
and the `charter-offering-scenario-extraction` procedure (consumer-first outcomes, one promise
per scenario, the consumer's language only), and the `qa-description-to-acceptance-ci` procedure
for the description → acceptance → CI loop.

> Note on `PROMISES.md`: its "offering authority" column still cites paths in core's
> `src/charter/offering/` (for example `artifact_kinds.py`). Those are the authorities the
> promises trace to **today**, while the charter is Python in core. They are re-homed as the
> offering moves into this repository.
