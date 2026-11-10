# Usability run (green): the walk on the integrated branch

SC-001 and NFR-003 of the mission `agent-profile-reads-01M70ZDM`: the script reaches `java-jenny`'s body and every one
of her governance entries, pack-scoped and under an activation, by following links from `/api/v1/`.

- **Script:** `scripts/usability/service-usability-run.sh --open-packs /home/user/spec-kitty-open-packs`, run from the
  repository root without `--jar` (it builds `:deployable:bootJar`).
- **Service under test:** commit `f0f60ae3` of `issue-407-agent-profile-reads`, with WP01 to WP09 integrated.
- **Open-packs pin:** `a2a93794f2d8c9f3f549e94ad024ea0e386d5df4`.
- **Result:** `RESULT PASS hops=52/52 failed=0 workarounds=4 unheld=7 unlinked=15 p95=323/341ms`, exit code 0.

The pass is qualified: it passes, with a count of what it could not reach, and the script prints that count rather than
hiding it. The baseline it replaces is in `usability-baseline.md`.

## Hops

- **Pack-scoped (P1 to P7):** `GET /api/v1/` to `artifacts?kind=agent_profile` (P2, a 400 at the baseline) to the list
  item `java-jenny` to her read (35 governance entries at the default `depth=0`, each with `edge`), then every held
  entry's links. `styleguide:java-conventions` is a `requires` entry (SC-003).
- **Under an activation (A1 to A9):** the root links `activations` (`POST`, A1, absent at the baseline), the created
  activation links `artifacts`, the effective item `java-jenny` links `effective-artifact`, and her read has the body
  and 7 governance entries, each with `edge`. `java-conventions` is a `requires` entry there too (SC-003).
- **Total:** 52 hops followed, 0 failed.

## Workarounds, named by the run

| Workaround | Issue | What it means |
|---|---|---|
| `unmodelled-files` for `spec-kitty-internal` and `quality-assurance` | #451 | A pack registration holds only modelled files. `PROVENANCE.md`, `README.md`, `org-charter.yaml` and the two `missions/qa/templates` files are refused, so the script registers a staged copy without them. The checkout is untouched |
| `project-pack` | #452 | This repository's own `.kittify/charter-packs` is refused (`pack_unreadable`: no `pack.yaml`). It is not registered and no edit was tried |
| `unheld-selections` | #452 | The 7 selections below are held only by that project pack |

## Unheld selections (7)

The activation document built from `.kittify/charter/charter.yaml` names seven artifacts that no registered pack holds,
and the service says so itself (`422 activation_invalid`, `unknown_artifact`). All seven live in the project pack, so
they need #452 (the project pack registered as a pack) before an activation can select them:

- `directive:PROJECT_001`
- `directive:PROJECT_002`
- `styleguide:documentation-policy-style-guide`
- `styleguide:java-style-guide`
- `styleguide:python-style-guide`
- `styleguide:quality-gates-style-guide`
- `styleguide:testing-style-guide`

## Unlinked entries (15)

Fifteen governance entries of the pack-scoped read (hop P7, `artifact procedure x15`) carry no `artifact` link. Their
kind is `procedure`, which the pack-scoped artifact routes do not serve (`GET /packs/built-in/artifacts?kind=procedure`
answers 400), so a procedure has no read to link to. This is not a missing link: the contract links only a kind the
service reads. Under the activation, 0 relations are unlinked.

## Timing (NFR-003)

p95 of 20 reads, limit 500 ms: pack-scoped 323 ms, under an activation 341 ms. An earlier run on `c160c960` gave
338 and 362 ms; the figures move by tens of milliseconds between runs.

## The output

```text
== open-packs checkout /home/user/spec-kitty-open-packs at a2a93794f2d8c9f3f549e94ad024ea0e386d5df4
   built-in copied into <config-root>/packs/, git tree c64382465ee07806d2137fa104591aa59fb8aeae = HEAD:packs/built-in
== build :deployable:bootJar
== start the service (/home/user/spec-kitty-charter/service/deployable/build/libs/deployable-0.1.0-SNAPSHOT.jar)
   listening on http://127.0.0.1:35961 (pid 2499)
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
  [ ok ] P2   GET /api/v1/packs/built-in/artifacts?kind=agent_profile -> 200 (relation 'artifacts' of the API root)
  [ ok ] P3   found agent_profile 'java-jenny' in the agent_profile list of pack built-in (page 1)
  [ ok ] P4   GET /api/v1/packs/built-in/artifacts/agent_profile/java-jenny -> 200 (relation 'self' of the java-jenny list item)
  [ ok ] P5   the pack-scoped read has the body of 'java-jenny' and 35 governance entries, each with an edge
  [ ok ] P6   SC-003: styleguide java-conventions is a requires entry of java-jenny in the pack-scoped read
  kind 'procedure' is not served: GET /api/v1/packs/built-in/artifacts?kind=procedure -> 400
  (P7: 35 held entries of the pack-scoped read walked; 0 with held=false have no pack to link to; 15 relation(s) not linked because their kind is not served; not linked, kind not served: artifact procedure x15; 0 failed)
  timing 20 pack-scoped reads of /api/v1/packs/built-in/artifacts/agent_profile/java-jenny
  [ ok ] TP   NFR-003: p95 of 20 pack-scoped profile reads is 323 ms (limit 500 ms)
== walk 2: under an activation (root -> activations -> activation -> artifacts -> java-jenny -> governance)
  [ ok ] A1   relation 'activations' of the API root, method POST, /api/v1/activations
  the service refused the document: 422 | activation_invalid | Activation invalid | The activation document names 7 thing(s) its packs do not hold.
    - /selected/directive/11 [unknown_artifact] The activation's packs hold no directive 'PROJECT_001'.
    - /selected/directive/12 [unknown_artifact] The activation's packs hold no directive 'PROJECT_002'.
    - /selected/styleguide/0 [unknown_artifact] The activation's packs hold no styleguide 'documentation-policy-style-guide'.
    - /selected/styleguide/3 [unknown_artifact] The activation's packs hold no styleguide 'java-style-guide'.
    - /selected/styleguide/4 [unknown_artifact] The activation's packs hold no styleguide 'python-style-guide'.
    - /selected/styleguide/5 [unknown_artifact] The activation's packs hold no styleguide 'quality-gates-style-guide'.
    - /selected/styleguide/8 [unknown_artifact] The activation's packs hold no styleguide 'testing-style-guide'.
  [WORKAROUND] unheld-selections: 7 selection(s) no registered pack holds were left out of the activation document: directive:PROJECT_001 directive:PROJECT_002 styleguide:documentation-policy-style-guide styleguide:java-style-guide styleguide:python-style-guide styleguide:quality-gates-style-guide styleguide:testing-style-guide. Every one is named by the service's own 422.
  [ ok ] A2   POST /api/v1/activations -> 201 (relation 'activations' of the API root), activation 01M72HBK6WFRH6BYT2BS637D98
  [ ok ] A3   GET /api/v1/activations/01M72HBK6WFRH6BYT2BS637D98 -> 200 (relation 'self' of the created activation)
  [ ok ] A4   GET /api/v1/activations/01M72HBK6WFRH6BYT2BS637D98/artifacts?kind=agent_profile -> 200 (relation 'artifacts' of the activation)
  [ ok ] A5   found agent_profile 'java-jenny' in the effective artifacts of the activation (page 1)
  [ ok ] A6   GET /api/v1/activations/01M72HBK6WFRH6BYT2BS637D98/artifacts/agent_profile/java-jenny -> 200 (relation 'effective-artifact' of the java-jenny effective item)
  [ ok ] A7   the read under the activation has the body of 'java-jenny' and 7 governance entries, each with an edge
  [ ok ] A8   SC-003: styleguide java-conventions is a requires entry of java-jenny in the read under the activation
  (A9: 7 held entries of the read under the activation walked; 0 with held=false have no pack to link to; 0 relation(s) not linked because their kind is not served; 0 failed)
  timing 20 reads of /api/v1/activations/01M72HBK6WFRH6BYT2BS637D98/artifacts/agent_profile/java-jenny
  [ ok ] TA   NFR-003: p95 of 20 activation profile reads is 341 ms (limit 500 ms)

== summary
  main commit        : f0f60ae31e84bea58167f9beb8873e3c40bedefb (issue-407-agent-profile-reads)
  open-packs pin     : a2a93794f2d8c9f3f549e94ad024ea0e386d5df4
  hops followed      : 52 ok, 0 failed
  workarounds        : 4
    - unmodelled-files (#451): 'spec-kitty-internal' was refused over PROVENANCE.md README.md org-charter.yaml; registered a staged copy of packs/internal without them. The checkout is untouched.
    - unmodelled-files (#451): 'quality-assurance' was refused over PROVENANCE.md README.md missions/qa/templates/README.md missions/qa/templates/plan-field-declaration.yaml org-charter.yaml; registered a staged copy of packs/qa without them. The checkout is untouched.
    - project-pack (#452): the project pack was refused (see above) and is not registered. No edit was tried: it needs a pack.yaml, plural folders, a different graph format and more. Selections only it holds are reported under 'unheld selections'.
    - unheld-selections: 7 selection(s) no registered pack holds were left out of the activation document: directive:PROJECT_001 directive:PROJECT_002 styleguide:documentation-policy-style-guide styleguide:java-style-guide styleguide:python-style-guide styleguide:quality-gates-style-guide styleguide:testing-style-guide. Every one is named by the service's own 422.
  unheld selections  : 7 (directive:PROJECT_001 directive:PROJECT_002 styleguide:documentation-policy-style-guide styleguide:java-style-guide styleguide:python-style-guide styleguide:quality-gates-style-guide styleguide:testing-style-guide)
  not linked (kind not served): 15
    - P7 artifact procedure x15
  p95 pack-scoped    : 323 ms; under activation: 341 ms (limit 500 ms, 20 reads each)
RESULT PASS hops=52/52 failed=0 workarounds=4 unheld=7 unlinked=15 p95=323/341ms commit=f0f60ae3 pin=a2a93794
```
