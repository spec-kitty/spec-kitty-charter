# Review Action - Governance Guidelines

These guidelines govern the quality and correctness standards for work package review in the software-dev mission. They are injected at runtime via the charter context bootstrap.

---

## Dependency Verification

- Before reviewing a WP, confirm each WP listed in its `dependencies` frontmatter field is `approved` or `done`, and present in the review base.
- Read WP states with `spec-kitty agent tasks status --mission <handle>`, never from a checkout's status files.
- Identify any WPs that list the current WP as a dependency and note each dependent's current WP state.
- If you request changes AND dependents exist, tell those agents to re-run `spec-kitty agent action implement <WP> --mission <handle>` once this WP is approved again: re-entry refreshes their lane with approved dependency work only; give a manual command only if the CLI reports a conflict.
- Confirm that dependency declarations match actual code coupling (imports, shared modules, API contracts).

---

## Review Intent

- Assess intent and risk first before diving into line-level details.
- Verify that the implementation satisfies the acceptance criteria defined in the WP task file.
- Check that test coverage is adequate for the changes introduced.
- Verify that no deliverable files were written outside the WP's workspace (for lane topologies, the repository root checkout is outside it).
- Read each rejected requirement ref's reason: `malformed` and `unknown_spec_id` block approval; a `foreign_qualified` ref (`<mission-slug>#<ID>`) cites another mission and never blocks. Success criteria (`SC-###`) are tracked, not gating.

---

## Outcome Actions

- Approve: move WP to `approved` with a summary note. Consolidation later records `done`.
- Reject: write structured feedback to the temp file path shown in the prompt, then move WP back to `planned`.
- Use the exact temp file path provided by the prompt to avoid conflicts with other review agents.

---

## Supply-Chain Security Evidence

- For any dependency change (add/upgrade/removal), confirm the implementer's evidence covers every control `DIRECTIVE_051` names, per the `supply-chain-install-safety` tactic.
- Confirm every adversarial-squad or reviewer challenge to a supply-chain finding has an explicit, traceable disposition per the `adversarial-squad-deployment` procedure's findings-disposition contract (load it with `spec-kitty charter context --include procedure:adversarial-squad-deployment`) - never let a contested finding go unrecorded.
- This is advisory in v1: it does not add a new fail-closed transition gate, but missing evidence for a dependency change is a governance gap to flag in review feedback.
