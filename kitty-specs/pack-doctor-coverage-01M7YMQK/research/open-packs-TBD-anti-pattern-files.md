# Draft: `open-packs#TBD-anti-pattern-files` (not filed)

Drafted for plan D12-D14 (WP10, WP11, WP12; rulings R-p, R-q and R-r) under C-006. The orchestrator files it in
`spec-kitty/spec-kitty-open-packs` at landing, with WP09's other drafts, and links it from spec-kitty-charter#154,
which this PR closes. Evidence is from a read-only look at `/home/user/spec-kitty/spec-kitty-open-packs` at
`fb71367`. Counts are of `- name:`/`- title:` items under `anti_patterns:`.

> **Adopt the `anti_pattern` kind of the packs contract: files, `rejects` edges, no inline entries (ADR-0052)**
>
> **Why.** The charter release that closes spec-kitty-charter#154 completes the `anti_pattern` kind (ruling R-r):
>
> - an anti-pattern is its own file, `anti_patterns/<id>.anti-pattern.yaml`, closed, with `schema_version`, `id`
>   (the stem), `title`, `description`, `severity` (`smell`, `severe` or `breaking`; no default) and `bad_example`
>   required, `good_example` optional (ADR-0052 d8 and d10, ADR-0053 d30 and d32);
> - guidance rejects it with a `rejects` edge in its pack's graph documents; across packs the target is
>   pack-qualified and its pack is in `depends_on` (ADR-0064 d12, ADR-0052 d11);
> - **an `anti_pattern` node with no file in the same pack is refused** `drg_schema_error` (ADR-0052 d14, ADR-0053
>   d31);
> - **`procedure.schema.json` and `styleguide.schema.json` no longer carry `anti_patterns`**: a file that still has
>   the key is refused `artifact_schema_error` ("unknown key 'anti_patterns'") (ADR-0052 d16);
> - a preset `must_have` anti-pattern counts only when a pack of the own closure holds its file (ruling R-l).
>
> **So these files and packs will be refused by registration and `pack-doctor validate` from the repin until they
> are curated.** No deprecation window is offered (the charter's canonical model).
>
> **Inline blocks: 60 files, about 267 entries** (26 procedures, 34 styleguides), refused as an unknown key:
>
> | Pack | Files (entries) |
> |---|---|
> | `architecture` | `procedures/event-storming-discovery.procedure.yaml` (4)<br>`styleguides/aggregate-design-rules.styleguide.yaml` (2) |
> | `drupal` | `styleguides/drupal-conventions.styleguide.yaml` (9)<br>`styleguides/drupal-security-performance.styleguide.yaml` (5) |
> | `event-driven` | `styleguides/evd-event-naming-and-payload.styleguide.yaml` (3) |
> | `foundation` | `procedures/adversarial-squad-deployment.procedure.yaml` (9)<br>`procedures/example-mapping-workshop.procedure.yaml` (4)<br>`procedures/post-merge-arch-gate-adjudication.procedure.yaml` (5)<br>`styleguides/boring-code-review.styleguide.yaml` (3) |
> | `go` | `styleguides/go-conventions.styleguide.yaml` (10) |
> | `internal` | `procedures/charter-offering-scenario-extraction.procedure.yaml` (6)<br>`procedures/cloud-session-dispatch.procedure.yaml` (9)<br>`procedures/issue-to-mission-delivery.procedure.yaml` (2)<br>`procedures/issue-triage-pass.procedure.yaml` (5)<br>`procedures/landing-contributor-prs.procedure.yaml` (5)<br>`procedures/mission-tracer-files.procedure.yaml` (3)<br>`procedures/mission-wrap-up-sequence.procedure.yaml` (7)<br>`procedures/semi-auto-delivery.procedure.yaml` (6)<br>`procedures/test-suite-quality-assessment.procedure.yaml` (4)<br>`procedures/tracker-organisation-workflow.procedure.yaml` (7)<br>`styleguides/executive-debrief.styleguide.yaml` (6)<br>`styleguides/feature-and-scenario-authoring.styleguide.yaml` (5)<br>`styleguides/report-writing.styleguide.yaml` (3)<br>`styleguides/tiered-standards.styleguide.yaml` (3) |
> | `java` | `styleguides/java-conventions.styleguide.yaml` (6) |
> | `javascript` | `styleguides/css-conventions.styleguide.yaml` (5)<br>`styleguides/html-conventions.styleguide.yaml` (5)<br>`styleguides/typescript-conventions.styleguide.yaml` (5) |
> | `pack-authoring` | `procedures/onboard-external-agent-to-pack.procedure.yaml` (9)<br>`styleguides/deployable-skill-authoring.styleguide.yaml` (4)<br>`styleguides/doctrine-instruction-voice.styleguide.yaml` (1) |
> | `planning-analysis` | `procedures/domain-aware-decision-interview.procedure.yaml` (4)<br>`procedures/issue-triage-state-machine.procedure.yaml` (4)<br>`procedures/legacy-codebase-triage.procedure.yaml` (5)<br>`procedures/spike-timebox-policy.procedure.yaml` (3)<br>`styleguides/planning-and-tracking.styleguide.yaml` (3) |
> | `python` | `styleguides/python-conventions.styleguide.yaml` (5) |
> | `qa` | `procedures/qa-go-no-go-readiness-review.procedure.yaml` (1)<br>`procedures/qa-test-plan-review-workshop.procedure.yaml` (1) |
> | `rust` | `styleguides/rust-conventions.styleguide.yaml` (9) |
> | `testing` | `procedures/bdd-scenario-lifecycle.procedure.yaml` (4)<br>`procedures/development-assist-test-cleanup.procedure.yaml` (5)<br>`procedures/disciplined-defect-diagnosis.procedure.yaml` (5)<br>`procedures/test-first-bug-fixing.procedure.yaml` (5)<br>`styleguides/given-when-then-authoring.styleguide.yaml` (2)<br>`styleguides/mutation-aware-test-design.styleguide.yaml` (4)<br>`styleguides/quadruple-a-test-format.styleguide.yaml` (2)<br>`styleguides/test-desiderata-and-boundaries.styleguide.yaml` (5)<br>`styleguides/testing-principles.styleguide.yaml` (4) |
> | `writing` | `procedures/meeting-minutes-pipeline.procedure.yaml` (7)<br>`styleguides/common-docs.styleguide.yaml` (2)<br>`styleguides/divio-type-discipline.styleguide.yaml` (2)<br>`styleguides/docs-accessibility.styleguide.yaml` (2)<br>`styleguides/docs-freshness-sla.styleguide.yaml` (2)<br>`styleguides/meeting-minutes-format.styleguide.yaml` (6)<br>`styleguides/plain-language.styleguide.yaml` (2)<br>`styleguides/professional-communications.styleguide.yaml` (3)<br>`styleguides/publication-authority.styleguide.yaml` (2)<br>`styleguides/research-citation-discipline.styleguide.yaml` (4)<br>`styleguides/writing/kitty-glossary-writing.styleguide.yaml` (4) |
>
> **File-less nodes: 13**, refused `drg_schema_error`: `packs/architecture/drg/fragment.yaml` (6) and
> `packs/refactoring/drg/fragment.yaml` (7), the `kind: anti_patterns` nodes. They seed the general set in the
> foundation pack (ADR-0052 d15).
>
> **Curation (ADR-0052 d15, the open-packs curation issue `open-packs#TBD-anti-pattern-curation`, drafted in
> `docs/plans/triage-2026-11-12-rescopes-applied.md`, "Left to file").** Move each entry to a file in the pack whose
> guidance rejects it (ADR-0052 d11), dedupe by meaning (about 150 distinct built-in anti-patterns), assign each a
> severity and a `bad_example` (none is defaulted), give each file-less node its file, add a `rejects` edge from each
> former owner, delete the inline blocks, and restamp the manifests with `pack-doctor remediate`. The service's own
> writer (`remediate`) emits the canonical form.
>
> **Also changes in open-packs.**
>
> - `scripts/ci/validate_pack_schemas.py` treats `anti_pattern` as graph-only: `MANIFEST_EXEMPT_NODE_KINDS`
>   (lines 84-88) and the derived-manifest note (line 447) leave it out of the manifest, and the held set (lines
>   1181-1183) takes it from graph nodes. An anti-pattern **file** is now a manifest constituent and is held; a node
>   alone is refused. Retire the Python paths with the other rules (`TBD-retire-python-rules`) or align them at the
>   repin.
> - `docs/artefacts/index.md:36` ("Anti-pattern | inside other artefacts / the DRG | not a standalone file"), `:68`
>   and `:87` (`anti_patterns` among the styleguide and procedure fields) and `docs/guides/validate-a-pack.md:117`
>   ("Actions and anti-patterns have no file") describe the old model.
> - No open-packs preset activates an anti-pattern today (no `activated_anti_patterns` in `packs/`), so the preset rule
>   refuses nothing here.
>
> **Order.** Curate first, on a branch pinned to the new release, so the repin and the curation land together; or
> hold the repin until the curation is done. Either way the release that closes #154 refuses the current tree.
>
> **Done when.** Open-packs pins the release; no styleguide or procedure carries `anti_patterns`; every
> `anti_pattern` node has its file; every anti-pattern has a severity and a bad example; its checker and docs match.
