# Data model: example packs

- **built-in** (`pack_level: built-in`): every artifact kind at full shape; `policy.yaml` locking `generic-agent`;
  `fallback_profile: generic-agent`; a mission type with lifecycle, guards and step prompts holding empty slots;
  presets; graph shards; a manifest; companion files; a binary asset.
- **org-base** (`pack_level: org`, `depends_on` built-in): an org fragment; qualified references into built-in; a step
  enhancement with prompt fragments; a specialist profile `specializes_from` a built-in profile; a policy with
  `extendable` and `open` levels.
- **project** (`pack_level: project`, `depends_on` org-base): an `overrides` of an open artifact; `fallback_profile`
  naming a parent's locked profile.

Invariants: canonical shapes only; every pack schema property set at least once across the three packs; each pack
loads clean and round-trips with its meaning.
