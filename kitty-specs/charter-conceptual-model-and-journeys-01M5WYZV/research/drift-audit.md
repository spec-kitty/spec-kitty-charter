# Gap analysis: charter conceptual model, journeys and scenarios

Audit step of mission `charter-conceptual-model-and-journeys-01M5WYZV`, 2026-10-26. Read-only audit
of `docs/`, `contracts/charter/scenarios/`, `site/` glossary tooling and the Cucumber runner against
`docs/context/conceptual-model.md` (the source of truth, see `spec.md`).

Classification rule: Divio type from frontmatter `type`/`kind` (all pages carry it, so confidence is
high throughout). Priority: drift (a wrong statement) > missing concept > stale wording.

## Coverage matrix

| Area | Explanation | Reference | Scenarios |
|---|---|---|---|
| Conceptual model (meaning) | present-but-stale (working notes) | n/a | n/a |
| User journeys | missing | n/a | missing |
| Glossary | n/a | present-but-stale (imported from core) | n/a |
| Logical model (concept → representation) | present-but-stale (representation-heavy) | present-but-stale | n/a |
| ADRs on relations, integrity, activation, deployment | present-but-stale (drift, below) | n/a | n/a |
| Contract scenarios per journey | n/a | n/a | missing; no `@WIP` exclusion |

## Drift findings (high priority)

| Location | Says | Agreed |
|---|---|---|
| `docs/adr/0009*:47-70`, `0014*:37,70-71,91-93`, `docs/architecture/contract-and-dtos.md:134-145`, `logical-model.md:71,191-192,222-234`, `descriptors.md:110,122,156`, `0017*:62`, `0025*:40,92,119` | `impacts` is a signed edge between artifacts; tension = `impacts < 0` | `impacts` = per-value list; `tension` = relationship between two rules |
| `logical-model.md:223`, `0009*:68`, `0014*:92` | `reconciles_tension` targets `impacts < 0` pairs | it targets `tension` pairs |
| `0012*:74-81`, `logical-model.md:137` | `extendable` = enhance only, no deactivate; one "deactivate" column | removal (derived pack) allowed for `open`/`extendable`; only `locked` protected; removal ≠ deactivation |
| `0012*` D2 | policy file carries levels only | also role locks and role pins |
| `0026*:119` | "removal is deactivation" | removal is a pack-wide act distinct from deactivation |
| `0027*:45,68` | the symmetric relation is `impacts`; one relationship per pair | `tension`; may coexist with a structural relationship |
| `0020*:80-87,233-234`, `offering-and-activation.md:168-174`, `0013*:41` | empty activation = all active; service wins on divergence | each kind's default; local `activation.yaml` is the source of truth (write-through); local wins visibly; pack ids + versions pinned; opt-in upgrade |
| `0011*:73,158-167`, `service-lifecycle.md:3,22,51`, `system-context.md:54`, `overview.md:59`, `docs/architecture/README.md:4,66`, `plans/open-questions.md:53` | loopback per-worktree only; worktree token | local (spawned, offline) and hosted (endpoint + login token, fails closed) |
| `domain.md:106-109,120`, `resolution-model.md:~26,~130`, `descriptors.md:251,262-263` | profile resolved by `(profile_id, action)`; weighted score, one winner; steps name `agent_profile` | steps name roles; role fit → specialization (lineage + `routing-priority`) → fallback → not found; ties: open question (ADR-0032) |
| `logical-model.md:184-185`, `descriptors.md:299` | mission type is not an artifact kind | a mission type is activatable (current-state fact kept, labelled) |
| `plans/open-questions.md:54` | tension source framed as `in_tension_with` → `impacts` | authored `tension` relationship |

## Missing concepts (no page or section)

Role (extensible, primary/alt, `operator`), fallback profile and the not-found refusal, role pin,
ethos, project creed, value impact, `tension`, `supports`, removal by a derived pack, activation
context and resolution, preset as the source of requirements, paradigm (adopted, principles, test),
procedure vs tactic, rules vs resources, pack versions in the active charter, opt-in upgrade and the
outdated notice, local/hosted deployment, write-through activation with cold-start reconcile, the
open-core boundary, splitting the built-in pack.

## Glossary ownership

- Today `site/tools/import_core_glossary.py` writes `site/.contextive/definitions.yaml` from core; every
  docsite build (doc-kitty 0.4.0) regenerates `docs/glossary/{charter,governance,product-lines}/README.md`
  from it. Hand edits to those pages are lost.
- Gap: the source file says "IMPORTED, do not edit"; the guide, `doc-rules.yaml:102-103,114`,
  `terminology.yaml:2`, `check_docs.py:446,462,569` and ADR-0003:23 describe core as the owner.
- Terms to supersede: `in_tension_with`, `Mission-Type Profile`, `selected_<kind>` / `required_<kind>`;
  terms to reword: activation preset, active charter, activation context, charter offering, selection
  terms; governance terms needing cross-links: activated vs available profile, pack level.

## Scenarios

- `service/deployable/build.gradle.kts:29` copies `contracts/charter/scenarios/**` to the test
  classpath; `CharterContractSuite` runs `@IncludeTags("contract")`. No `@WIP` exclusion exists.
- Cucumber 8 is strict: an undefined step fails the scenario. So every scenario without existing
  steps must be `@WIP`.
- No other tool reads `.feature` files. New files need a line in `contracts/charter/CHANGELOG.md`.
- Runnable with existing steps: reads of a pack's artifacts, `/info`, fingerprints, registering a
  directive or relationship. Everything on activation, roles, profiles, pins, removal, presets,
  values, upgrades and hosting is `@WIP`.

## Prioritized gap list

| # | Gap | Impact | Audience |
|---|---|---|---|
| 1 | Drift in ADR-0009/0014/0012/0026/0027/0020/0011 and the architecture pages above | blocks-task (contributors would build the old model) | contributors, core maintainers |
| 2 | Formal conceptual model page | blocks-onboarding | governance leads, pack authors |
| 3 | Eleven journey pages with diagrams | blocks-task (API design) | all three |
| 4 | Glossary ownership and new terms | blocks-discoverability | all |
| 5 | Journey scenarios + `@WIP` exclusion | blocks-task (acceptance net) | contributors |
| 6 | Logical model as translation layer | blocks-task | contributors |
| 7 | Inbound links to changed pages (see audit list in the mission record) | nice-to-have, but checked by `links` | all |

Low-confidence classifications: none.
