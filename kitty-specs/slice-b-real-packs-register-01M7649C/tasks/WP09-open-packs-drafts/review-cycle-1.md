---
affected_files: []
cycle_number: 1
mission_slug: slice-b-real-packs-register-01M7649C
reproduction_command:
reviewed_at: '2026-11-12T06:38:20Z'
reviewer_agent: claude-reviewer
wp_id: WP09
---

# WP09 review 1: changes requested (independent review of 14dba944)

The headline result reproduces exactly (0/19 as found, 19/19 with both drafts; internal base.yaml valid against
preset.schema.json). Fix these before the drafts are filed:

## MEDIUM (must fix)
- M1 org-charter-preset misses live mentions of the retired mechanism: repo-root README.md:102-104;
  docs/packs/index.md:51; docs/architecture/docsite-design.md:46,59; docs/packs/catalog-proposal.md:233,248;
  packs/internal/README.md:149 (`required_skills` puts all five in force); packs/rust/presets/rust.yaml:6 ("org charter
  activates nothing"); packs/foundation/glossary_packs/spec-kitty-core.glossary-pack.yaml:996 (as "check with core").
  Widen acceptance to "no file in the repo outside ADRs and the changelog".
- M2 readme-doc never says what to do with pack.md's second preamble paragraph (Level / Depends-on facts, e.g. java,
  writing); step 1 would import it, breaking the draft's own acceptance (line 172). Say per group: drop pack.md's
  preamble (para 1 repeats the README description, para 2 is descriptor facts), keeping only provenance facts such as
  "Carved out of `built-in` by #73" under `## Provenance`. Make apply_drafts.py do the same and re-run.
- M3 CI-checked generated docs: validate-packs.yml:75 and docs-site.yml:125 run `gen_pack_reference.py --check`. Add
  "run python3 scripts/docs/gen_pack_reference.py and commit docs/packs/*.md" to both drafts (org-charter changes
  docs/packs/go.md and rust.md too), and docs/packs/index.md:49,76 to readme-doc.
- M4 "all 19 packs register" overstates the test: the driver ran WholePackStore.loadStaged (IC-04 step 2) only. Reword
  dry-run.md:7-9, readme-doc:22-23, org-charter-preset:25 to "all 19 pass the registration load (IC-04 step 2) with no
  refusal". (A full POST run is for the mission's gates, not this WP.)

## LOW (fold in)
- readme-doc:74 BOM: "a byte order mark at the start".
- readme-doc:68-75: point at the full rule set in MarkdownScanner's class doc; acceptance "ATX headings only" also
  needs "no closing # sequence" (`## Provenance ##` is refused).
- readme-doc:81 "bullets 5 to 7" -> lines 5-7 (bullets 1-3). readme-doc:95 the bullet is facts only: "delete it".
- foundation/writing: pack.md's "This file" means the README after the merge; note it as for built-in.
- org-charter-preset:142-144: drop the "or apply this pack's event-driven preset" alternative or say it stays opt-in.
- org-charter-preset:151: the canonical layout shows pack.yaml, README.md and optionally policy.yaml.
- Both drafts cite "ADR-0042 as amended on 2026-11-11" and docs/guides/enforce-org-presets.md: correct names; they land
  with charter PR #501 (keep them; the orchestrator files after the PR exists).
- dry-run.md:50-56: describe everything apply_drafts.py does (event-driven/spdd provenance line, inline PROVENANCE
  links kept, all headings demoted, " (pack)" renames), or say "registration-relevant steps only"; paste the driver's
  core calls into the record.
