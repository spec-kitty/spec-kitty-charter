---
title: Open questions
description: Decisions the charter design depends on that are not made yet, each with the default this repository assumes until someone rules.
doc_status: draft
updated: 2026-10-18
type: Plan
kind: Planning
related:
  - plans/roadmap
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
audience:
  - profile: core-maintainer
    guidance_text: Read this to rule on the questions that are core's to decide.
  - profile: charter-service-contributor
    guidance_text: Read this for the default to work from while a question is open.
---

# Open questions

Each question names the default this repository works from until someone with the authority
decides. A ruling becomes an ADR here or in core; decided rows stay, marked with the date.

| # | Question | Default assumed here | Who decides |
|---|---|---|---|
| 1 | **Where is the charter contract authored**: here or in core's `contracts/`? | **Decided 2026-10-18:** here ([ADR-0002](/adr/0002-contract-source-of-record/), Accepted) | Operator |
| 2 | **How does the Python charter reach this repository?** Move it here first and strangle it here, or rewrite in Java and replace it in core. | **Decided 2026-10-18:** rewrite and replace; it does not move. The service is built against BDD contracts and the OpenAPI document, and the open-packs content must round-trip through the file-based infrastructure layer ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/)) | Operator |
| 3 | **Acceptance of core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler).** | **Decided 2026-10-18:** accepted, with Amendment 2026-10-18 (contract here, rewrite and replace) | Operator, in core |
| 4 | **Does `kernel` ship first?** The charter depends on `kernel`; core ADR `2026-10-12-2` leaves this to epic #6099. | No longer relevant here: with question 2 decided, no Python package ships from this repository, and the Java service depends on no Python | Epic #6099 |
| 5 | **Native binary or JVM artifact?** | Follow the Mission Status service (GraalVM native per OS) unless reflection-heavy YAML or MCP libraries make native impractical | Implementation ADR |
| 6 | **Which YAML codec** can pass codec identity (comments, anchors, scalar styles, null spellings)? | Undecided; a spike before any write migration | Implementation ADR |
| 7 | **MCP adapter authorization.** | Not shipped until decided; expected to reuse loopback + Host check + worktree token | Operator |
| 8 | **Launcher shape.** Daemon on first use, or one process per command? | Daemon on first use, stopped with the worktree | Core, `specify_cli` |
| 9 | **Do governance changes become ledger events?** An activation or an applied preset is not recorded on the ledger today. | No; if wanted, the core emits it through its one canonical event writer, as a spec-kitty-events event, never the charter service | Operator |
| 10 | **Does a runtime event cite which governance applied** (pack ids, versions, artifact URNs) for traceability? | Worth doing once the core's canonical writer is in place; the identifiers come from this contract, the payload from spec-kitty-events | Operator, with spec-kitty-events |
| 11 | **Performance claims.** Is a warm service actually faster end to end? | No claim until a cold-versus-warm end-to-end benchmark exists | Measurement |
| 12 | **Where does first-load context state live?** The context read records first-load state in `.kittify/charter/context-state.json` and answers differently on a first load, so it is not a pure read. | It stays in the Python seam; the service takes the load depth as a request parameter and stays a pure read model | Implementation ADR |
