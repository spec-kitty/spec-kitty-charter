# Docs and guidance still describe the retired mission layout

Filed as spec-kitty/spec-kitty-open-packs#100. Refs spec-kitty/spec-kitty-charter#194, charter ADR-0044.

After the mission-type consolidation (`spec-kitty/spec-kitty-open-packs#95`), these files still name
`mission.yaml`, `mission-runtime.yaml`, action indexes, `mission-steps/`, `command-templates/`,
`governance-profile.yaml` or `expected-artifacts.yaml`. Update each to the mission-type file and its `steps/`
companions:

- `packs/built-in/missions/README.md` and the per-type READMEs (software-dev, plan, …).
- `docs/guides/author-a-charter-pack.md` (the `mission_types/` tree) and `docs/artefacts/index.md` (line 36).
- `.agents/skills/spec-kitty.implement/SKILL.md` (lines 335–338: step prompts under `mission-steps/`).
- `packs/qa/PROVENANCE.md` (lines 24–47: `mission-steps/`, `command-templates/`, the governance profile) and
  `packs/cave-kitty/PROVENANCE.md` (line 40: `mission-runtime.yaml`).
- Tactic `reviewer-implementer-role-separation`, which names `mission-runtime.yaml` as the home of implement/review
  profile routing (lines 18–79). **That is now wrong:** a step names a role, and a preset activates profiles
  (charter ADR-0032). The tactic's guidance needs rewording, not just a path change.
- Tactic `ownership-map-leeway` (lines 54–55: prompt paths under `mission-steps/`).
- The qa toolguides `qa-carrier-lint` and `qa-result-ingest` (guide and toolguide texts), and the docstrings of the
  `qa_carrier_lint.py` and `qa_result_ingest.py` assets. They name `expected-artifacts.yaml`'s `blocking:` flag, which
  is now a step's expected output's `blocking` in the mission-type file.
