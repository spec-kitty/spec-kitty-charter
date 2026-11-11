# REASONS Guidance

### REASONS Canvas Comparison (active for this project)

This project's active charter includes the SPDD/REASONS artifacts. Use the
mission's REASONS canvas as a comparison surface for this work package.

**1. Load the canvas.** Read `kitty-specs/<mission>/reasons-canvas.md`. If it
is missing, invoke the `spk-charter-spdd-reasons` skill to author it before
completing review. Do not auto-approve in the absence of a canvas.

**2. Trace the diff.**

- For each Requirement and Operation in the canvas, find concrete evidence in
  the diff or note its absence.
- Detect entities, files, or surfaces touched by the diff that do not appear
  in canvas Structure or Approach.
- Verify Norms (testing, observability, style) and Safeguards (hard
  constraints, security, performance limits, things not to break).

**3. Classify the divergence.** Choose ONE outcome:

| Outcome | When | Action |
|---|---|---|
| approved | No divergence OR all divergences match Deviations entries. | APPROVE |
| approved_with_deviation | Divergence is acceptable; reviewer adds a Deviations entry. | APPROVE + canvas update |
| canvas_update_needed | Code reality reveals the canvas was wrong. | APPROVE conditionally; open canvas update task |
| glossary_update_needed | Term drift surfaced. | APPROVE conditionally; open glossary update task |
| charter_follow_up | The active charter should change. | APPROVE conditionally; open charter follow-up |
| follow_up_mission | Out-of-scope work surfaced. | APPROVE current scope; open follow-up mission |
| scope_drift_block | Out-of-bounds undocumented work. | REJECT |
| safeguard_violation_block | Safeguard rule violated. | REJECT |

**4. Charter precedence.** If a charter directive conflicts with the canvas,
follow the directive and add a deviation note to the canvas.

**5. Record the outcome.** Reviewer should explicitly name the chosen outcome
in the review summary so downstream automation can route the WP correctly.
