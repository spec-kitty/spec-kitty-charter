---
work_package_id: WP03
title: 'How-to: generate your own client from the published contract'
dependencies:
- WP01
requirement_refs:
- FR-014
- FR-015
planning_base_branch: claude/client-java-218-224
merge_target_branch: claude/client-java-218-224
branch_strategy: Planning artifacts for this mission were generated on claude/client-java-218-224. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into claude/client-java-218-224 unless the human explicitly redirects the landing branch.
subtasks:
- T010
- T011
- T012
history: []
agent_profile: java-jenny
authoritative_surface: docs/guides/
create_intent:
- docs/guides/generate-a-client.md
- client-java/src/test/java/ai/speckitty/charter/client/HowToTest.java
execution_mode: code_change
model: ''
owned_files:
- docs/guides/generate-a-client.md
- docs/guides/README.md
- docs/_meta/spelling-words.txt
- client-java/src/test/java/ai/speckitty/charter/client/HowToTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP03: How-to: generate your own client from the published contract

Design: plan.md D-5; research R2, R3, R6.

## Subtasks

- **T010** `docs/guides/generate-a-client.md`: get, verify, generator, Java, Python, caveats, release status.
- **T011** Link it from `docs/guides/README.md`; spelling words if needed.
- **T012** `HowToTest`: the page names the catalog's openapi-generator version.

## Done when

Every command on the page was run in this session against the snapshot bundle; the docs checks pass; the version guard fails on a stale version.
