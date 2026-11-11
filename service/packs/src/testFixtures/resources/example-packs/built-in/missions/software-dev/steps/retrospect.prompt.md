---
description: Facilitate a structured retrospective of a finished software-dev mission and record it as retrospective.yaml
---
# Mission Retrospective - Record What Helped and What Did Not

**Path reference rule:** When you mention directories or files, provide either the absolute path or a path relative to the project root (for example, `kitty-specs/<mission>/retrospective.yaml`). Never refer to a folder by name alone.

**In repos with multiple missions, always pass `--mission <handle>` to every spec-kitty command.** The `<handle>` can be the mission's `mission_id` (ULID), `mid8` (first 8 chars of the ULID), or `mission_slug`.

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## When This Step Runs

On demand, outside the step sequence: at mission terminus, or when the operator asks for a richer retrospective than the one the runtime generates on its own. Do not run it mid-mission.

## What This Step Produces

`kitty-specs/<mission>/retrospective.yaml`: a schema-valid record of what **helped**, what was **not helpful**, which governance and context **gaps** appeared, and which doctrine, DRG or glossary changes are **proposed**. Every finding and proposal carries provenance back to the mission's events or artifacts.

## What to Read

- The mission's own artifacts: `spec.md`, `plan.md`, `tasks.md`, the `tasks/WP*.md` files and their review cycles, and `analysis-report.md`.
- The mission's event log, `kitty-specs/<mission>/status.events.jsonl`: transitions, rejections, blocked steps and decisions.
- The governance context the mission actually loaded (`spec-kitty charter context --action retrospect --json`).

## What to Do

1. **Confirm the mission is finished.** A retrospective of a running mission is premature; stop and say so.
2. **Create or update the record.**

   ```bash
   spec-kitty retrospect create --mission <handle>          # first record
   spec-kitty retrospect create --mission <handle> --update # merge into an existing record
   ```

3. **Enrich the findings.** For each helped / not-helpful / gap entry, cite where it shows: an event, a review cycle, an artifact line. A finding with no provenance is opinion; drop it or find the evidence.
4. **Stage proposals, do not apply them.** Write each proposed doctrine, DRG or glossary change as a proposal in the record. Glossary proposals follow the `kitty-glossary-writing` styleguide.
5. **Keep product and process apart.** A defect in what the mission built belongs in the tracker, not in the retrospective; the retrospective is about how the mission ran.
6. **Commit** `retrospective.yaml` with co-author attribution.

## Success Criteria

- `retrospective.yaml` exists and validates.
- Every finding and proposal has provenance.
- Proposals are staged, none applied.

## Handoff

Applying a proposal is a separate, human-approved step: `spec-kitty agent retrospect synthesize --mission <handle>` (a dry run by default; `--apply` mutates project state). This step never edits doctrine, the DRG or the glossary itself.
