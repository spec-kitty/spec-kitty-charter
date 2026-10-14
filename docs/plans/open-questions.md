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
| 1 | **Where is the charter contract authored**: here or in core's `contracts/`? | Here ([ADR-0002](/adr/0002-contract-source-of-record/), Proposed) | Operator |
| 2 | **How does the Python charter reach this repository?** Move it here first as a Python package (core ADR `2026-10-12-2` D-3), then strangle it with Java here; or rewrite in Java and replace it in core, so the Python code never moves. | Likely: **move the Python charter here first**, unless rewrite-and-replace proves safer or more economical. Not yet decided (operator, 2026-10-13). The comparison should weigh the cost of packaging Python that will later retire against running the strangler across two repositories. The extraction boundary is part of it: the service's resolution model needs `charter.activation`, so an offering-only extraction (`src/charter/offering/`, the scope epic #6099 names, pending spike #3101) leaves the conformance oracle split across two repositories | Operator |
| 3 | **Acceptance of core ADR `2026-10-03-1`.** It is still Proposed. | Treated as the plan ([ADR-0001](/adr/0001-java-service-with-openapi-contract/)) | Operator, in core |
| 4 | **Does `kernel` ship first?** The charter depends on `kernel`; core ADR `2026-10-12-2` leaves this to epic #6099. | Relevant if the Python charter moves first (question 2): the package needs `kernel` from somewhere. Irrelevant to the Java service, which depends on no Python | Epic #6099 |
| 5 | **Native binary or JVM artifact?** | Follow the Mission Status service (GraalVM native per OS) unless reflection-heavy YAML or MCP libraries make native impractical | Implementation ADR |
| 6 | **Which YAML codec** can pass codec identity (comments, anchors, scalar styles, null spellings)? | Undecided; a spike before any write migration | Implementation ADR |
| 7 | **MCP adapter authorization.** | Not shipped until decided; expected to reuse loopback + Host check + worktree token | Operator |
| 8 | **Launcher shape.** Daemon on first use, or one process per command? | Daemon on first use, stopped with the worktree | Core, `specify_cli` |
| 9 | **Do governance changes become ledger events?** An activation or an applied preset is not recorded on the ledger today. | No; if wanted, the runtime emits it through spec-kitty-events, never the charter service | Operator |
| 10 | **Does a runtime event cite which governance applied** (pack ids, versions, artifact URNs) for traceability? | Worth doing once the ledger-row schema exists; the identifiers come from this contract | Operator, with spec-kitty-events |
| 11 | **Performance claims.** Is a warm service actually faster end to end? | No claim until a cold-versus-warm end-to-end benchmark exists | Measurement |
| 12 | **Where does first-load context state live?** The context read records first-load state in `.kittify/charter/context-state.json` and answers differently on a first load, so it is not a pure read. | It stays in the Python seam; the service takes the load depth as a request parameter and stays a pure read model | Implementation ADR |
