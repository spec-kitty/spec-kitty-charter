# Representation notes (moved from the conceptual model working notes)

Copied verbatim from `docs/context/conceptual-model.md` (working notes, 2026-10-26) when slice G1
formalized that page. Slice G8 moves these into `docs/architecture/logical-model.md`.

### Representation notes (for later, not conceptual)
- Core ADR `2026-10-03-1` (loopback, per-worktree daemon) is amended so loopback is the local
  option and a configured remote endpoint is allowed. ADR-0020 D12 already shapes the hosted one.
- `activation/register`: `POST` registers a project's active charter, `PUT` updates it, including
  the offering pack ids and versions. Exact path to follow ADR-0020 / ADR-0026 naming.
- An `/activation` resolution API takes the client's activation context and returns the resolved
  artifacts for that step. Open: one large payload, or references plus hypermedia links to the
  contents. Relates to ADR-0018 (action context) and ADR-0020 / #103 (R9, effective activation).
- Domain: a role is an `ExecutorRole` value object (wrapper).
- API: the profiles resource answers "best profile for role R in context C"
  (`findByRoleAndContext(Role, Context)`). Exact HTTP shape to follow the repo's resource naming
  and ADR-0019/0020 conventions (e.g. a query on the profiles collection, under `/offering`?).
- Separate domain models fit ADR-0006's planned slices for mission types and profiles. It decides
  #171: a shared artifact interface can only span those slices from `kernel.domain`; otherwise
  mission types and profiles do not implement it.
- Mission-type layout is scattered (`missions/`, `mission-steps/`, `mission_types/`). Target:
  `missions/<type>/mission.yaml`, `missions/<type>/steps/<step>/`,
  `missions/<type>/steps/<step>/template.md`.
- Today `mission-type` is not an `ArtifactKind` (`MissionTypeNotAnArtifactKind`); conceptually it
  is activatable like the others.

