# qa: the mission type's profile list was dropped; add a preset to activate the profiles

Filed as spec-kitty/spec-kitty-open-packs#97. Refs spec-kitty/spec-kitty-charter#194, charter ADR-0032, ADR-0044.

A mission type names roles, not agent profiles (ADR-0032). qa's `governance-profile.yaml` listed
`selected_agent_profiles: [qa-strategist-quinn, qa-test-designer-tessa, qa-test-executor-elena, qa-reporter-rhea,
qa-defect-triager-dmitri]`; the migration drops it. Every qa step now names its role (from its profile's first role);
`qa-defect-triager-dmitri` (role `defect-triager`) is named by no step.

Ask: if qa wants these profiles activated, add `presets/qa.yaml` with `activated_agent_profiles` (the pack has no
preset today).
