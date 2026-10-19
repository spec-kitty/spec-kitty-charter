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
| 13 | **Is a missing `pack_level` a refusal?** The service answered it with a non-blocking diagnostic, a call made without review. | **Decided 2026-10-18:** ratified as is. It stays a diagnostic, `pack_level_missing` ([`DiagnosticCode`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/charter/schemas/DiagnosticCode.yaml)). The service is deliberately stricter than core: the diagnostic applies at every level and to a pack with no `pack.yaml`, and `pack_version` is required at every level. [spec-kitty-open-packs#47](https://github.com/spec-kitty/spec-kitty-open-packs/issues/47) brings every pack into line; [spec-kitty#6403](https://github.com/spec-kitty/spec-kitty/issues/6403) asks core to correct its glossary, which calls it a refusal | Operator |
| 14 | **Which rules answer the action-scoped context?** The first slice was a call made without review. | **Decided 2026-10-18:** amended, and provisional until the conformance corpus ([#9](https://github.com/spec-kitty/spec-kitty-charter/issues/9)) decides the real rules. The operation is marked `x-provisional` and its scenarios `@provisional`; the answer is sorted by URN as core sorts it; an action that is not a node of the graph stays a `404`. The `requires`, `suggests` and `vocabulary` walks, activation filtering and core's degrade on a load error are recorded as open in the contract's `CHANGELOG.md` | Operator; then the corpus, #9 |
| 15 | **Where do the contract scenarios live?** The name `scenarios/` was a call made without review, forced by the terms guard. | **Decided 2026-10-18:** ratified and amended. `contracts/charter/scenarios/` keeps its name; only that subtree is on the suite's test classpath, and the suite runs the scenarios tagged `@contract`. Fixtures stay inline, and every scenario change gets a line in the contract's `CHANGELOG.md`. "Contract scenarios" is the canonical term | Operator |
| 16 | **How do the BDD suites fit together?** The repository has three homes for scenarios. | **Decided 2026-10-18:** each home stays, and moves or binds as follows ([Behavior and acceptance scenarios](/architecture/behavior-and-scenarios/#where-they-live)). Contract scenarios stay in `contracts/charter/scenarios/` and bind now. The pack registration suites of #43 (`src/test/resources/features/`) move to `contracts/charter/scenarios/<cluster>/` when their harness is bound, renamed for the terms guard, each cluster moved and bound in one change. The acceptance scenarios in `tests/scenarios/charter/` stay and bind with the harness of [#31](https://github.com/spec-kitty/spec-kitty-charter/issues/31) | Operator |
| 17 | **Who fixes the vendored-pack defects?** The `qa` pack has no `pack.yaml`, and the internal org pack requires glossary pack `spk-internal-glossary` while the vendored file stem is `spk-internal`. | **Owned upstream:** both are fixed in spec-kitty-open-packs ([#47](https://github.com/spec-kitty/spec-kitty-open-packs/issues/47) folds them in), then re-vendored here: the `qa` pack under [#36](https://github.com/spec-kitty/spec-kitty-charter/issues/36), the internal pack under [#37](https://github.com/spec-kitty/spec-kitty-charter/issues/37). No local fix | spec-kitty-open-packs, then this repository |
| 18 | **Do core's contract tools check this contract?** | **Decided 2026-10-18:** no. The charter owns its contract and its checks: its own lint ruleset ([`contracts/lint/`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md)), bundle and breaking-change check, and a Java checker for closed response objects. Core adapts through its integration seam, and may change later ([spec-kitty#6402](https://github.com/spec-kitty/spec-kitty/issues/6402) stays open as that record) | Operator |
| 19 | **Is OpenRewrite enabled?** | **Decided 2026-10-18:** no, neither in the build nor in CI: new releases ship only through an authenticated vendor channel, it does not support the configuration cache, and its changes did more harm than good here. Revisit if its distribution changes | Operator |
| 20 | **Is jqwik used for property-based tests?** | **Decided 2026-10-18:** no. Since 1.10.0 its test engine prints instructions to AI agents in the test output. Advisory: [spec-kitty-open-packs#46](https://github.com/spec-kitty/spec-kitty-open-packs/issues/46) | Operator |
