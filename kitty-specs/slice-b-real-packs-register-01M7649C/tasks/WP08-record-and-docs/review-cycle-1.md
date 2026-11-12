---
affected_files: []
cycle_number: 1
mission_slug: slice-b-real-packs-register-01M7649C
reproduction_command:
reviewed_at: '2026-11-12T07:11:12Z'
reviewer_agent: claude-reviewer
wp_id: WP08
---

# WP08 review 1: changes requested

Independent review of f44b195b..e6b7068a. Everything else was verified against the code (the /doc read, excluded files,
the swap window, ConsistentRead, ADR-0073, the amendments, the how-to mapping, the usability script). Docs gates pass.

## MEDIUM (must fix)
- M1 DIV-302 is wrong about core and PROVENANCE.md (divergences.yaml:1694-1696, :1710; divergence-register.md:620,
  :626). Core `charter synthesize` writes `.kittify/charter-packs/PROVENANCE.md` at the project pack root as a required
  artifact (/opt/spec-kitty src/specify_cli/cli/commands/charter/_fresh_project_layer.py:22,34; synthesize.py:157) and
  core's freshness check reads it (src/specify_cli/charter_runtime/freshness/computer.py:739). Correct the Core line,
  cite these, and state that core's fresh project seed writes and reads a root PROVENANCE.md the service refuses as
  `retired` (optionally link #452). The earlier scan only covered charter/offering/packs.
- M2 scripts/usability/README.md:43-46 says active_charter is refused "by name". HOLD this one and L1: a parallel code
  fix is changing how a stale active_charter key is refused (FR-010: refused naming the key). The orchestrator will
  send you the final wire shape; word M2 and L1 to match it then.

## LOW (fold in)
- L1 (hold, see M2) enforce-org-presets.md:28-30 and compose-packs.md:241 "refused as an unknown key".
- L2 resolution-model.md:108-110 org `required_<kind>` union claim: prefix "In core," and note the service retired the
  file (#431).
- L3 enforce-org-presets.md:102-105 PUT example: add `Content-Type: application/json` and a `{}` body
  (requestBody required; PresetApplication "Send {} for the default").
- L4 enforce-org-presets.md:37 locked: "any write to it" (no replace, extend or relate), per policy.schema.json.
- L5 DIV-303 Core text (divergences.yaml:1717, divergence-register.md:632): OrgCharterPolicy is at org_charter.py:136;
  :487 is load_org_charter_policy.
- L6 DIV-021 evidence pack_validator.py:595-604 no longer points at an org-charter leg at 78c48ce9: re-cite.
- L7 ADR-0036 note says "The last row no longer refers to it" but decision 3's last row (:244) still reads "As for
  `org-charter.yaml`: skipped and listed": annotate that row in place or reword the note.
- L8 descriptor-schemas.md:180 still lists src/charter/activation/org_charter.py as a diagram source: drop it.
