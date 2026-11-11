<!-- spec-kitty:orientation -->
**Spec Kitty v4.0.0rc8** — project: spec-kitty-charter (healthy)

Two usage patterns:
- **Full mission** (spec → plan → tasks → implement → review → merge):
  trigger: "spec out", "create a mission", "write a spec", "plan this"
  → run `/spec-kitty.specify`
- **Lightweight dispatch** (ad-hoc fix, question, or advice — no mission created):
  trigger: "hey spec kitty", "use spec kitty to", "spec kitty <anything>"
  → **ALWAYS run `spec-kitty dispatch "<request verbatim>"` — do NOT answer directly.**
  If you know the right profile, pass it to skip routing:
  `spec-kitty dispatch "<request verbatim>" --profile <profile-id>`
  Reason: `spec-kitty dispatch` loads governance context, routes the request,
  and opens the Op. Skipping it produces ungoverned, untracked responses.
  After finishing the work, close the Op with the command printed in the capsule
  (`spec-kitty profile-invocation complete --invocation-id <id> --outcome <done|failed|abandoned>`).
<!-- /spec-kitty:orientation -->

## Model rules for agents (operator, 2026-11-10)

Read `.kittify/charter/charter.md` before planning. Two rules from it that sessions have missed:

- **The charter's model is canonical.** When a shape changes, define the rationalized shape and
  replace the old one outright: no deprecation window, no legacy reader beside the new one, no
  "core first" ordering. `spec-kitty-open-packs` follows the model; core aligns later through the
  generated client.
- **Tests and examples use only the canonical shape**, except tests of error handling (an invalid
  pack or invalid content). Update fixtures, example packs and contract examples with the model;
  never exclude a field from a parity or round-trip check to make a test pass.
