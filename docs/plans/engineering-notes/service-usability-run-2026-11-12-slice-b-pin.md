---
okf_version: "0.2"
title: "Service usability run, 2026-11-12: the slice B pin"
description: "At the open-packs commit that carries open-packs #248, internal and qa register as they are, and a link-only client passes all 146 hops."
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/service-usability-run-2026-11-10-mission-types
  - plans/next-slices-2026-11-12
  - adr/0073-real-packs-register-as-they-are
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the evidence behind slice B's registration proof, and what the run changed to follow the split built-in.
  - profile: governance-lead
    guidance_text: Read this for the evidence that closes slice B (#466).
---

# Service usability run, 2026-11-12: the slice B pin

**Status: assessment, not a decision.** It answers the last question of slice B (#466): with `openPacksRef` raised to
the open-packs commit that carries open-packs #248, do the open-packs register as they are? It follows the
[mission type run of 2026-11-10](/plans/engineering-notes/service-usability-run-2026-11-10-mission-types/) and uses
the same script.

## What was run

- **Script:** `scripts/usability/service-usability-run.sh` at `7f7a756`.
- **Service:** `:deployable:bootJar` built from `15615e5`, the commit that models the org fragment in the round trip.
  The commits after it change only the script and the documentation.
- **Packs:** spec-kitty-open-packs at the pin `1692a3b`. The run configured `architecture`, `built-in`, `foundation`,
  `java`, `planning-analysis`, `refactoring`, `testing` and `writing` into `<config-root>/packs/`, each copy checked
  against its pinned git tree. It registered `internal` and `qa` through `POST /packs`.
- **Activation:** this repository's `.kittify/charter/charter.yaml` translated by `charter-to-activation.py`, plus
  `agent_profile: java-jenny`.

```bash
scripts/usability/service-usability-run.sh --open-packs ../spec-kitty-open-packs --profile java-jenny \
  --jar service/deployable/build/libs/deployable-0.1.0-SNAPSHOT.jar
```

## Result

```text
[ ok ] R-internal registered 'spec-kitty-internal' unmodified from packs/internal
[ ok ] R-qa registered 'quality-assurance' unmodified from packs/qa
RESULT PASS hops=146/146 failed=0 workarounds=3 unheld=7 unlinked=0 p95=102/157ms client=7f7a7568 jar=eda6f68503f1 pin=1692a3b1
```

- **Registration.** Both packs register unmodified. Since open-packs #241 and #242, neither carries `pack.md`,
  `PROVENANCE.md` or `org-charter.yaml`, so ADR-0073 refuses nothing in them.
- **Three workarounds, none new to slice B.**
  - The project pack is refused for want of a `pack.yaml` (#452).
  - Seven charter selections no registered pack holds are left out of the activation, as before.
  - The activation still links neither `graph` nor an action context (#469).

## What the run changed

At the old pin `a2a9379`, `built-in` held every artifact. Since open-packs split it (open-packs #73), it aggregates
bundles through `depends_on` and holds none. `java-jenny` is in `java`, and `software-dev` is in `foundation`. Run
unchanged at the new pin, the script copied `built-in` alone. `internal` and `qa` were refused with
`pack_dependency_unknown` (they depend on `foundation`), and 6 of 10 hops failed. The script now configures the
`depends_on` closure from the pin's `pack.yaml` files. It reads the profile and the mission type from the packs that
hold them.
