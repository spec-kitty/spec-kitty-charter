# Squad: tracked issues

This read-only lens ran before the spec on 2026-11-13. It covers the 140 open issues, plus the recently closed ones that matter. Dispositions:

- **IN**: delivered and closed by slice F.
- **TOUCH**: referenced with `Refs`.
- **SEQUENCE**: an ordering constraint.
- **OBSOLETED**: closed with evidence.
- **OUT**: untouched.

| Issue | Disposition |
|---|---|
| #112 R18 | **IN** |
| #514 retire `mission_step_contract` | **IN**. Its "behind core #3420/#3419" conflicts with charter.md (no core-first ordering). The charter wins, and the text is amended at landing. |
| #520 mission-type schema | **IN**. Under the canonical-model rule, core #2751's org-pack exception for `action_sequence` has no place here. |
| #515 endpoint kinds | **IN** |
| #533 step-contract read under an activation | **OBSOLETED** by #514. Closed with evidence once the route is deleted. |
| #447 `overrides` from a non-activatable kind | **SEQUENCE**. Slice C delivers it (C's FR-014). F folds C's rule into the endpoint table (research R8, Q7 revised). |
| #350 refuse a procedure edge whose target is `action:` | **IN, proposed**. It is one row of the endpoint table, and its open question is the same ruling. Included only if the operator agrees (Q8). |
| #154 `anti_pattern` kind | **TOUCH**. The `rejects` row. Refs. |
| #49 derive hand-kept lists | **TOUCH**. The kind table is a source it could derive from. Refs. |
| #433 wire-token parity for the registration enums | **TOUCH**. The `NodeKind` pairs follow. Refs. |
| #417 registration notices for non-activatable kinds | **TOUCH**. It uses the `activatable` fact. Refs. |
| #355 validate mode skips the mission-template closure | **TOUCH**. Wording follows #520. Refs. |
| #559 packs contract release | **TOUCH**. The schemas and `file-kinds.yaml` break. Recorded in the changelog. |
| #337 core reads mission-type bundles | **TOUCH**. Its text lists `step_contracts/`. Refreshed at landing (core-tracked). |
| #339 core guard entries replace `GateBinding` | **SEQUENCE (content)**. The one gate moves to the lifecycle guard in open-packs when the step-contract files go. Otherwise OUT (core). |
| #547 drift test misses unmodelled kinds | **SEQUENCE**. Retiring a file kind while open-packs still ships it is what this test misses. At landing, check by hand that the open-packs draft removes every file. |
| #549, #554, #556 | **SEQUENCE**. The read-time refusal belongs in #554's graph ruleset; whoever lands second rehomes it. Order goes to the orchestrator. |
| #106 `glossary` replaces `glossary_pack` | **SEQUENCE**, not blocking. The kind table follows it, and the second to land rebases. |
| #434 step transitions; #164 mission-type requirement kinds | **OUT**. They edit the same schema file; coordinate at rebase. |
| #513 substeps | **OUT**. Becomes the future home of substep structure (R3, Q2). Its new relation needs an endpoint row. |
| #477 round trip of an `impacts` strength | **OUT**. If Q6 retires `impacts`, #477 is re-checked at landing. |
| #519 derive reference edges for project artifacts | **OUT**. It looks obsoleted by ADR-0064. Reported to the orchestrator for the operator, not acted on here. |
| #97, #452, #467 | **OUT** |
| #94, #142, #537 | Ticked at landing. |

Proposed closeout: `Closes #112, #514, #520, #515, #533` (plus #350 if Q8). Refs #49, #154, #337, #355, #417, #433, #559.

No finding is filed as a new issue. None is a correctness, data or security risk beyond what the slice already delivers (issue-tracker.md Rule 1).
