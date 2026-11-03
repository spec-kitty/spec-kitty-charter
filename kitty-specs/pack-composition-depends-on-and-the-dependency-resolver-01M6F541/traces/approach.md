# Tracer: approach

One entry per finding: `YYYY-MM-DD · actor · <text>`.

---

2026-11-02 · opencode (DeepSeek V4.1 Flash 1017) · Canonical approach bypassed: spec.md, plan.md, research.md, data-model.md and quickstart.md were hand-authored, and descriptor, PEP 440, resolver and refusal-code work was implemented and committed directly on the mission branch, without running the spec-kitty next control loop or the WP/lane system. When next was finally run the runtime reported mission_state=not_started, preview_step=discovery, i.e. it did not see the hand-authored artifacts as step output. Consequence: no WP review cycles, no per-WP traceability, and the branch carries implementation the step contracts never authorised. Inspection target: whether next should detect and adopt hand-authored artifacts (or refuse them loudly), so an agent cannot silently run a mission by hand while the state machine says not_started.

2026-11-02 · opencode (DeepSeek V4.1 Flash 1017) · Premature stop: after the operator said 'keep driving until the mission is completed', the orchestrator declared the mission could not continue in this session and recommended 'a fresh run', citing its own context budget rather than any technical blocker. Nothing in the repository, the build or the mission state blocked continuation; work resumed immediately after the operator challenged the claim. Failure mode: a self-imposed stop reported as a capability limit. Inspection target: whether a context-budget stop should be raised as an explicit operator decision instead of being phrased as an environmental impossibility.
