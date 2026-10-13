---
title: Open questions
description: Decisions the charter design depends on that are not made yet, each with the default this repository assumes until someone rules.
doc_status: draft
updated: 2026-10-13
type: Plan
kind: Planning
related:
  - plans/roadmap
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
---

# Open questions

Each question names the default this repository works from until someone with the authority
decides. A ruling becomes an ADR here or in core.

| # | Question | Default assumed here | Who decides |
|---|---|---|---|
| 1 | **Where is the charter contract authored**: here or in core's `contracts/`? | Here ([ADR-0002](../adr/0002-contract-source-of-record.md), Proposed) | Operator |
| 2 | **Does the Python charter move to this repository** before it retires, as core ADR `2026-10-12-2` D-3 suggests, or stay in core until the strangler retires it? | It stays in core behind the seam; this repository holds Java, the contract and the corpus. Core ADR `2026-10-12-2` would need a sentence saying so | Operator, in core |
| 3 | **Acceptance of core ADR `2026-10-03-1`.** It is still Proposed. | Treated as the plan ([ADR-0001](../adr/0001-java-service-with-openapi-contract.md)) | Operator, in core |
| 4 | **Does `kernel` ship first?** The charter depends on `kernel`; core ADR `2026-10-12-2` leaves this to epic #6099. | Irrelevant to the Java service, which depends on no Python; relevant only if question 2 goes the other way | Epic #6099 |
| 5 | **Native binary or JVM artifact?** | Follow the Mission Status service (GraalVM native per OS) unless reflection-heavy YAML or MCP libraries make native impractical | Implementation ADR |
| 6 | **Which YAML codec** can pass codec identity (comments, anchors, scalar styles, null spellings)? | Undecided; a spike before any write migration | Implementation ADR |
| 7 | **MCP adapter authorisation.** | Not shipped until decided; expected to reuse loopback + Host check + worktree token | Operator |
| 8 | **Launcher shape.** Daemon on first use, or one process per command? | Daemon on first use, stopped with the worktree | Core, `specify_cli` |
| 9 | **Do governance changes become ledger events?** An activation or an applied preset is not recorded on the ledger today. | No; if wanted, the runtime emits it through spec-kitty-events, never the charter service | Operator |
| 10 | **Does a runtime event cite which governance applied** (pack ids, versions, artifact URNs) for traceability? | Worth doing once the ledger-row schema exists; the identifiers come from this contract | Operator, with spec-kitty-events |
| 11 | **Performance claims.** Is a warm service actually faster end to end? | No claim until a cold-versus-warm end-to-end benchmark exists | Measurement |
