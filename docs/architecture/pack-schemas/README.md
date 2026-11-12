---
okf_version: "0.2"
title: Pack schemas
description: "One page per charter pack JSON Schema, each with a rendered diagram and property list generated from the schema file."
doc_status: draft
updated: 2026-11-12T00:00:00Z
type: Architecture
kind: Hub
audience:
  - profile: pack-author
    guidance_text: Use this list to find the schema of the pack file you are writing.
---

# Pack schemas

The structural authority for every pack file is a JSON Schema in
[`contracts/packs/schemas/`](https://github.com/spec-kitty/spec-kitty-charter/tree/main/contracts/packs/schemas).
Each schema has a page here. The build draws the diagram and the property list from the schema file, so the
pages cannot drift from it. For entity diagrams of the descriptor kinds and their edges, see
[Descriptor schemas](/architecture/descriptor-schemas/).

- [Agent profile](/architecture/pack-schemas/agent-profile/) — the identity, purpose and specialization of an agent role.
- [Asset manifest](/architecture/pack-schemas/asset/) — the sidecar manifest that describes one asset blob in a pack.
- [Charter](/architecture/pack-schemas/charter/) — the structured shape of the git-tracked charter.yaml.
- [Directive](/architecture/pack-schemas/directive/) — a constraint-oriented governance rule and its enforcement level.
- [Glossary pack](/architecture/pack-schemas/glossary-pack/) — a named collection of canonical terms with provenance.
- [Graph shard](/architecture/pack-schemas/graph-shard/) — a document of the Charter Reference Graph.
- [Guard](/architecture/pack-schemas/guard/) — an enforced check a transition names, implemented by an executable asset or a built-in.
- [Lifecycle](/architecture/pack-schemas/lifecycle/) — a reusable state machine of states and guarded transitions.
- [Mission step contract](/architecture/pack-schemas/mission-step-contract/) — the legacy structural contract of a mission action.
- [Mission step guidelines](/architecture/pack-schemas/mission-step-guidelines/) — the parsed Markdown structure of a step guidelines file.
- [Mission step prompt](/architecture/pack-schemas/mission-step-prompt/) — the parsed Markdown structure of a step prompt file.
- [Mission type](/architecture/pack-schemas/mission-type/) — the ordered steps, expected artifacts and guidance of a mission type.
- [Organization graph fragment](/architecture/pack-schemas/org-fragment/) — a loaded organization-tier graph fragment with its provenance.
- [Pack descriptor](/architecture/pack-schemas/pack-descriptor/) — the identity and lineage of a charter pack.
- [Pack document](/architecture/pack-schemas/pack-document/) — the parsed Markdown structure of the pack document, the pack's README.md.
- [Pack manifest](/architecture/pack-schemas/pack-manifest/) — the generated list of a pack's constituent artifacts.
- [Pack mutability policy](/architecture/pack-schemas/policy/) — the optional policy.yaml that sets a pack's mutability levels.
- [Paradigm](/architecture/pack-schemas/paradigm/) — a worldview-level framing that guides how the charter is read.
- [Preset](/architecture/pack-schemas/preset/) — an activation preset that selects artifacts and mission types.
- [Procedure](/architecture/pack-schemas/procedure/) — a reusable workflow with entry and exit conditions.
- [Prompt fragment](/architecture/pack-schemas/prompt-fragment/) — the parsed Markdown structure of a step enhancement's prompt fragment companion.
- [Pack skill](/architecture/pack-schemas/skill/) — a thin skill with parameters distributed in a pack.
- [Step enhancement](/architecture/pack-schemas/step-enhancement/) — the prompt fragments a pack contributes to a mission step.
- [Styleguide](/architecture/pack-schemas/styleguide/) — the conventions, anti-patterns and quality tests of one scope.
- [Tactic](/architecture/pack-schemas/tactic/) — a reusable behavior pattern made of ordered steps.
- [Toolguide](/architecture/pack-schemas/toolguide/) — a tool-specific governance guide.
- [Toolguide guide](/architecture/pack-schemas/toolguide-guide/) — the parsed Markdown structure of a toolguide guide file.
