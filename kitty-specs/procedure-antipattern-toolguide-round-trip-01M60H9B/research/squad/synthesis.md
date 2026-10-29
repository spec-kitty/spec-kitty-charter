# Pre-spec squad synthesis — charter #194

Point-cut: pre-spec. Question: what is the grounded scope of #194, and what sanitized DSO shape + divergence table does it need?
Cast: researcher-robbie (prior art), architect-alphonso (domain/shape), analyst-annie (content inventory), planner-priti (scope/slicing).
Operator ruling (2026-10-28): the sanitized-shape requirement wins over #194's "round-trip as-is".

## Convergent findings

| # | Finding | Lenses | Disposition |
|---|---------|--------|-------------|
| C1 | procedure: schema, ArtifactKind, NodeKind, file rule exist; no binding/DSO/entity; Tactic is the template; 41 files validate against the merged schema | R, A, An, P | accepted — PR-A scope |
| C2 | anti_pattern has no file/kind (ADR-0037 d4); inline blocks ride their owner; `anti_pattern.graph.yaml` red only because node `tags` dropped (#236) | R, A, An, P | pending operator Q2 |
| C3 | mission tier: ~150 built-in files in `other:missions`, 7 YAML shapes + ~80 non-YAML, no schemas; needs ADR before schemas | R, A, An, P | accepted — PR-B, ADR first |
| C4 | split into two PRs: PR-A procedure+anti_pattern, PR-B mission types | R, P (A implicit) | accepted |
| C5 | ADR-0032 leakage: profile ids where roles are expected (4 spellings), no role field anywhere | A, An | accepted — sanitized shape uses `role`; divergences → open-packs issues |
| C6 | markdown content form is unbuilt and both #194 (mission prompts) and #195 (pack.md) need it | R, P | pending coordination with #195 via parent session |

## Divergent / consequential points (adjudicated from source)

| # | Point | Adjudication | Disposition |
|---|-------|--------------|-------------|
| D1 | Analyst: procedures need almost nothing. Architect: procedure schema needs sanitizing (actor→role, unified anti-pattern entry, reference shape) | Both true: content validates against the *current* schema, but that schema itself violates the cross-kind rules. contracts/packs/README.md:7 — core consumes these schemas → change requires core lockstep | pending operator Q3 |
| D2 | Architect: retire mission-runtime.yaml, action index.yaml, governance-profile.yaml, expected-artifacts.yaml into mission_type + mission_step. Planner: one schema per existing file family | Core runtime reads mission-runtime and the refuse-extra-fields models (runtime/next/*, charter/offering/missions/*). Retiring files is a core-breaking cross-repo change, larger than "sanitize the DSO" | pending operator Q3 |
| D3 | Architect D-9: step files fit neither KindBinding (kind() returns ArtifactKind) nor companion seam (ADR-0039 D3 forbids .yaml companions) | Confirmed structural gap → ADR-0042 must decide | accepted — ADR WP in PR-B |
| D4 | Gate: who removes own exclusions in file-kinds.yaml (append-only rule) | Operator rule ambiguity | pending operator Q1 |

## Follow-ups (not changed here)
- 14 merged-schema sanitized-shape violations (architect §4) → list on epic #186.
- Procedure inline `references` vs CRG edges → wait for #189; do not mint relationships.
- Next free ADR: 0042 (0041 earmarked #227); reserve with #195.

## Operator decisions (2026-10-28, in-session)
- Q-mission (D2): CONSOLIDATE NOW — sanitized mission_type + mission_step model; mission-runtime.yaml, actions/*/index.yaml, governance-profile.yaml, expected-artifacts.yaml retired. open-packs and core follow afterwards, outside this mission; core follows on cut-over to service usage. → D2 changed (architect's design adopted).
- Q-core-schema (D1): sanitize procedure.schema.json in place + file a core issue for the Pydantic change; diverging pack files on the known-failure list. → D1 accepted.
- Q-anti_pattern (C2): leave graph-node tags to #236; prove inline anti-patterns round-trip; graph file stays expected-red under #236. → C2 deferred_with_rationale (#236).
- Q-shared (D4): #194 may remove its OWN exclusion entries in file-kinds.yaml; "absorb #168" INCLUDES the toolguide Markdown guides. → D4 accepted; scope widened.

## Operator ruling on epic #186 (2026-10-28)
Operate as if core does not exist: no core issue (C-004 rewritten), no core-compatibility shaping; retirements follow the charter's own model; pack divergences → open-packs issues only. Verified on #186 (comment 6462455631).
