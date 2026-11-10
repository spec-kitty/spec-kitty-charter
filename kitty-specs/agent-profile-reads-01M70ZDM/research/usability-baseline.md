# Usability baseline (red): the walk before the profile reads

SC-002 of the mission `agent-profile-reads-01M70ZDM`: the same script, run on the service as it stood before this
mission, fails at the first profile hop.

- **Script:** `scripts/usability/service-usability-run.sh --open-packs /home/user/spec-kitty-open-packs` (WP09), from
  the lane workspace of WP09.
- **Service under test:** commit `25a59918bce882a574adb82c5686418d09d9f772`, the base of the mission's WP01 with the
  WP09 script on top and none of the profile reads.
- **Open-packs pin:** `a2a93794f2d8c9f3f549e94ad024ea0e386d5df4`.
- **Result:** `RESULT FAIL hops=3/5 failed=2 workarounds=3`, exit code non-zero.

## What failed

| Hop | Request | Answer | Why it is red |
|---|---|---|---|
| P2 | `GET /api/v1/packs/built-in/artifacts?kind=agent_profile` | `400 Bad Request` | The service serves five kinds, so `agent_profile` is an unknown `kind` filter |
| A1 | relation `activations` in `_links` of `GET /api/v1/` | absent (`artifacts`, `info`, `offering`, `self`, `service-desc` are present) | The root links no register of activations (the root half of #454) |

## Deviation from the plan: P2 answered 400, not 404

The plan expected the first profile hop to fail with `404`. It failed with `400`. That is the contracted answer, not a
defect of the run: an unknown `kind` filter on `listPackArtifacts` is a `400 Problem` without a code, and only an
unserved kind in the path of the item read is `404 kind_not_found` (`docs/api/service-contract.md`, "Built surface").
The red baseline therefore proves the same thing the plan wanted, that no profile can be listed, and it stays red at
the first profile hop. The script was not changed to make it answer 404.

## The output

```text
== open-packs checkout /home/user/spec-kitty-open-packs at a2a93794f2d8c9f3f549e94ad024ea0e386d5df4
   built-in copied into <config-root>/packs/, git tree c64382465ee07806d2137fa104591aa59fb8aeae = HEAD:packs/built-in
== build :deployable:bootJar
== start the service (/home/user/spec-kitty-charter/.worktrees/agent-profile-reads-01M70ZDM-lane-i/service/deployable/build/libs/deployable-0.1.0-SNAPSHOT.jar)
   listening on http://127.0.0.1:33185 (pid 26380)
== register packs
  pack packs/internal declares name 'spec-kitty-internal'; the registration's packId must equal it
    refused: 422 validation_failed The pack cannot be registered: 3 fault(s), the first at PROVENANCE.md.
      - PROVENANCE.md [unmodelled] PROVENANCE.md: no pack file kind models this file; a registration holds only modelled files
      - README.md [unmodelled] README.md: no pack file kind models this file; a registration holds only modelled files
      - org-charter.yaml [unmodelled] org-charter.yaml: no pack file kind models this file; a registration holds only modelled files
  [WORKAROUND] unmodelled-files (#451): 'spec-kitty-internal' was refused over PROVENANCE.md README.md org-charter.yaml; registered a staged copy of packs/internal without them. The checkout is untouched.
  [ ok ] R-internal registered 'spec-kitty-internal' from a staged copy of packs/internal
  pack packs/qa declares name 'quality-assurance'; the registration's packId must equal it
    refused: 422 validation_failed The pack cannot be registered: 5 fault(s), the first at PROVENANCE.md.
      - PROVENANCE.md [unmodelled] PROVENANCE.md: no pack file kind models this file; a registration holds only modelled files
      - README.md [unmodelled] README.md: no pack file kind models this file; a registration holds only modelled files
      - missions/qa/templates/README.md [unmodelled] missions/qa/templates/README.md: no pack file kind models this file; a registration holds only modelled files
      - missions/qa/templates/plan-field-declaration.yaml [unmodelled] missions/qa/templates/plan-field-declaration.yaml: no pack file kind models this file; a registration holds only modelled files
      - org-charter.yaml [unmodelled] org-charter.yaml: no pack file kind models this file; a registration holds only modelled files
  [WORKAROUND] unmodelled-files (#451): 'quality-assurance' was refused over PROVENANCE.md README.md missions/qa/templates/README.md missions/qa/templates/plan-field-declaration.yaml org-charter.yaml; registered a staged copy of packs/qa without them. The checkout is untouched.
  [ ok ] R-qa registered 'quality-assurance' from a staged copy of packs/qa
  project pack .kittify/charter-packs, as it is, with packId 'project'
    refused: 422 validation_failed The pack cannot be registered: pack pack has no pack.yaml
      - pack [pack_unreadable] pack pack has no pack.yaml
  [WORKAROUND] project-pack (#452): the project pack was refused (see above) and is not registered. No edit was tried: it needs a pack.yaml, plural folders, a different graph format and more. Selections only it holds are reported under 'unheld selections'.
== walk from GET /api/v1/
  [ ok ] P1   GET /api/v1/ -> 200 (the entry point)
== walk 1: pack-scoped (root -> artifacts -> java-jenny -> governance)
  [FAIL] P2   GET /api/v1/packs/built-in/artifacts?kind=agent_profile -> 400 400 | Bad Request (relation 'artifacts' of the API root)
== walk 2: under an activation (root -> activations -> activation -> artifacts -> java-jenny -> governance)
  [FAIL] A1   expected relation 'activations' (method POST) in _links of the API root; present: artifacts, info, offering, self, service-desc

== summary
  main commit        : 25a59918bce882a574adb82c5686418d09d9f772 (kitty/mission-agent-profile-reads-01M70ZDM-lane-i)
  open-packs pin     : a2a93794f2d8c9f3f549e94ad024ea0e386d5df4
  hops followed      : 3 ok, 2 failed
    failed: P2: GET /api/v1/packs/built-in/artifacts?kind=agent_profile -> 400 400 | Bad Request (relation 'artifacts' of the API root)
    failed: A1: expected relation 'activations' (method POST) in _links of the API root; present: artifacts, info, offering, self, service-desc
  workarounds        : 3
    - unmodelled-files (#451): 'spec-kitty-internal' was refused over PROVENANCE.md README.md org-charter.yaml; registered a staged copy of packs/internal without them. The checkout is untouched.
    - unmodelled-files (#451): 'quality-assurance' was refused over PROVENANCE.md README.md missions/qa/templates/README.md missions/qa/templates/plan-field-declaration.yaml org-charter.yaml; registered a staged copy of packs/qa without them. The checkout is untouched.
    - project-pack (#452): the project pack was refused (see above) and is not registered. No edit was tried: it needs a pack.yaml, plural folders, a different graph format and more. Selections only it holds are reported under 'unheld selections'.
  unheld selections  : 0
  p95 pack-scoped    : NA ms; under activation: NA ms (limit 500 ms, 20 reads each)
RESULT FAIL hops=3/5 failed=2 workarounds=3 p95=NA/NAms commit=25a59918 pin=a2a93794
```
