# Squad 2: doctrine lens on the toolguide guide shape (doctrine-daphne)

Point-cut: plan, decision DM-01M60MF8K36ZCRV04HPV3Z5TSR ("Structured: fixed sections/blocks plus a freeform sections list").
Mode: read-only. Sources: open-packs @01edb4e, core `/home/user/spec-kitty` (working tree), charter HEAD ce67392.

## Profile applied

`doctrine-daphne` (curator). I used these profile rules:
- **Kind classification**: "tool or platform knowledge [is] a toolguide". A rule goes in a directive, a technique in a tactic, a gated workflow in a procedure, a convention in a styleguide. So the guide should not hold content that belongs to another kind.
- **One authority per relationship**: a cross-artifact reference must be backed by a DRG edge. Inline `references:` is frozen legacy and closed to growth. A plain path or URL to non-artifact material is still allowed.
- **External references need a toolguide**: `tool` is the identity a toolguide offers for that audit.
- **Doctrine offers, the charter activates, the runtime consumes**: what the runtime actually renders decides what is metadata and what is depth.
- **Augment, don't duplicate**: reuse existing `$def` shapes such as the AntiPattern entry rather than adding parallel ones.

Charter context (`--action plan`) directives applied: DIRECTIVE_001 (separation of concerns, so metadata and guide have distinct jobs), DIRECTIVE_032 (state term meanings, see the "Vocabulary" section), DIRECTIVE_037 (one canonical home per fact, no stale duplicates), DIRECTIVE_003 (decisions recorded with rationale).

## Vocabulary (DIRECTIVE_032)

- **Toolguide**: the YAML artifact. Glossary entry: "tool-specific operational guidance, syntax, and constraints … used by agents and contributors during execution" (site/.contextive/definitions.yaml:202-203). Open-packs: "How to use one tool, with commands" (open-packs docs/artefacts/index.md:25).
- **Guide**: the Markdown body that `guide_path` names. In this design it becomes a structured value: fixed blocks plus `sections`.
- **Block**: a fixed, named slot with a defined meaning.
- **Section**: a freeform item with a title and a Markdown body.

## 1. Doctrinal role: what goes in the metadata and what goes in the guide

**The evidence that settles it.** Core never renders the guide to an agent.
- `_format_inline_toolguide_body` emits only Title, Tool and Summary (core src/charter/activation/context_renderers/artifact_bodies.py:253-265).
- The selection block says to use toolguides when you "are about to invoke a project tool" (token_budget.py:495).
- `guide_path` is read only by the model (core src/charter/offering/toolguides/models.py:15) and by the scaffolder (authoring.py:151).

So the YAML is the **push** surface (it is rendered into context). The guide is the **pull** surface (the agent opens it on demand).

That gives the division of labour:

| Concern | Home | Why |
|---|---|---|
| Identity and label (`id`, `title`) | YAML | Push surface, CRG node label |
| Which external tool (`tool`) | YAML | Join key for the "external reference lacks a toolguide" audit; distinct from `title` |
| What it is and when to open it (`summary`, renamed `description` per R5) | YAML | It is the only prose an agent sees before deciding to pull the guide. It must carry the trigger, not the manual |
| Command index (`commands`) | YAML | A lookup/trigger index of command forms. It is **not** usage documentation |
| Scoping (`applies_to_languages`) | YAML | Language-scoped slot filter (action_governance_bundle.py:207) |
| Provenance (`last_updated`) | YAML | Metadata |
| Relationships to other artifacts | **DRG edges** | R11, profile rule. Neither the YAML `references` nor prose links |
| How to set up, invoke, verify; traps; tool-specific depth | Guide | Pull-on-demand depth |
| External sources (vendor docs URLs) | Guide `references` block | Raw reference material, one home |

**Duplication rules**, so each fact has one home (DIRECTIVE_037):
- **Title vs H1.** The guide has no title of its own. Its H1 is rendered from `title`. Today they diverge, for example CONTEXTIVE.md:1 "Contextive Toolguide" vs title "Contextive", and POWERSHELL_SYNTAX.md:1 "...for AI Agents".
- **Summary vs the guide's lead paragraph.** These overlap heavily today. SONAR.md:3-8 restates sonar.toolguide.yaml `summary`. GHERKIN.md:3-9 restates the gherkin summary's scope sentence. Rule: `summary` says what the guide is and when to pull it. The guide's `overview` may orient the reader but must not restate the summary. This is an authoring rule and cannot be checked mechanically, so it is a lint/review concern, not a schema one.
- **Commands vs a commands section.** No fixed "Commands" block. The YAML list is the index; the guide shows commands in context (setup, usage sections). The QA summaries break this the other way: qa-carrier-lint.toolguide.yaml:5-28 packs the flags, exit codes and an upstream-issue status essay into `summary`, duplicating QA-CARRIER-LINT.md "Flag surface" and "Exit codes" (:38, :55).
- **References.** Artifact relationships are edges. External URLs live in the guide `references` block. The YAML `references` list stays frozen legacy, read but not extended (see F4).

## 2. The fixed blocks

Each block is chosen because it answers a question **every** tool guide has, by meaning, not because a heading is frequent. Each maps to a shape another kind already uses. All blocks are optional except `sections`; the reason is under the table. The renderer emits blocks in the order shown.

| # | Block | One-line meaning | Content | Reuses |
|---|---|---|---|---|
| 1 | `overview` | What the tool is and the mental model needed before using it (reader-facing; must not restate `summary`) | Markdown string (prose, may hold a table) | Lead paragraph / "Overview" / "What is X" (CONTEXTIVE.md:3, TERMINOLOGY_GUARD.md:3, TEST_QUALITY_TRIAGE.md:3) |
| 2 | `when_to_use` | Conditions under which this tool, not an alternative, is the right choice | Markdown string (usually a bullet list) | Meaning of procedure `entry_condition`. Source: MERMAID/PLANTUML "Purpose and When to Use" (:5), TERMINOLOGY_GUARD "When to Run" (:10), BRANDED "Purpose and when to use" (:9) |
| 3 | `out_of_scope` | What this guide deliberately does not cover, or when **not** to use the tool, with the alternative | Markdown string | Styleguide `scope`'s boundary role. Source: GHERKIN.md:115, QA-RESULT-INGEST.md:11, MERMAID.md:17 "Avoid Mermaid when" |
| 4 | `setup` | Installing, configuring, or resolving the tool before first use | Markdown string (prose and code) | Source: "Installation" (CONTEXTIVE:21), "Getting Started" (MERMAID/PLANTUML), "Installation and Configuration" (mutation guides :7), "Dependencies" (BRANDED:49), "Resolving the script" (QA ×2) |
| 5 | `sections` | Tool-specific depth: the actual body | list of `Section` (below) | Skill `body`-style freeform |
| 6 | `verification` | How to confirm the tool did its job, or the order to run checks before handing off | Markdown string (list or code) | Meaning of procedure `exit_condition`. Source: BRANDED "Verifying the output" (:104), DRUPAL "Before Submitting" (:117), MAVEN/PYTHON "Review Checklist Order" (:101/:140) |
| 7 | `anti_patterns` | Ways agents misuse the tool: symptom and correct form | list of `AntiPatternEntry {name*, description*, bad_example?, good_example?}` | Styleguide `AntiPattern` / procedure `ProcedureAntiPattern`, unified as R8. Source: "Avoid" ×3, "Red Flags" ×3, "Common Pitfalls" ×2, "Common Mistakes to Avoid" (POWERSHELL:88 with WRONG/CORRECT pairs, which are a direct bad/good fit), "Gotchas" ×3, GIT_WORKTREE "# Avoid / # Prefer" code pairs |
| 8 | `known_limitations` | Defects or false positives **in the tool itself** that the agent must work around (it is the tool's fault, not the agent's) | list of strings (Markdown) | Analogue of tactic `failure_modes`, but `failure_modes` means a technique failing, so the name is not reused. Source: QA "Known limitation" ×2, TEST_QUALITY_TRIAGE "Known false positives" (:92) |
| 9 | `references` | External sources (vendor docs, upstream repos) | list of `{title*, url*}` | Source: CONTEXTIVE:205, MERMAID:297, PLANTUML:299; drupal YAML `references` URL entry |

**Why `sections` is the only required block.** GIT_AGENT_COMMIT_SIGNING.md (21 lines: "Required behavior", "Examples", "Avoid") and GHERKIN.md show that a valid guide may have no setup, no verification and no references. Requiring any of them would force placeholder content, which is an anti-pattern in itself. A guide with no `sections` but some fixed blocks is valid if at least one block is present. Express that in the schema as `minProperties: 1`, not as `sections` being required. Tightening the rule is the planner's call.

**`Section` item shape:** `{ title: string (required), body: string Markdown (required) }`, closed (`additionalProperties: false`).
- H2 becomes `title`. Everything up to the next H2 becomes `body`, kept verbatim, including H3+ subheadings, code fences, tables and blockquotes.
- No recursion. H3 stays inside `body`. Nesting would add depth without meaning, and the mine shows H3s are tool-specific (diagram types, mutation families).
- No `id`. Nothing refers to a section (R9: `id` only when something refers to the item).

**Why the set stops at nine.** Content such as "Exit codes", "Flag surface", "Options" or "Outputs" (QA ×3, TEST_QUALITY_TRIAGE) is the invocation contract of a *script* toolguide, a subfamily of maybe 4 of 24. Making it fixed would make generic guides carry empty slots. It stays in `sections`. Revisit only if a "script toolguide" subkind is ratified.

**Concession.** Mapping varied source headings onto blocks, for example "Red Flags" to `anti_patterns`, is the empirical delegate's call. A gotcha that reads as tool behaviour rather than agent misuse may belong in `known_limitations`. I give the meaning, not the per-guide classification.

## 3. Does the pattern generalize to other Markdown bodies? (brief, no design)

The **mechanism** (fixed blocks plus `sections: [Section]`, with shared `$def Section` and `$def AntiPatternEntry`) generalizes. The **fixed set** does not; each body kind has its own.
- **Skill bodies** (`*.skill.md`): issue-triage.skill.md has "Steps" (:5), "Stop and ask the operator when" (:20) and "Hand-off" (:25). These are procedure-shaped (steps / escalation / exit). A skill body is an executable prompt, so restructuring it risks changing prompt semantics. Recommend freeform-only `sections` at first.
- **Mission-step `prompt.md`**: templated, imperative, emoji headings (tasks/prompt.md:20, :38). It carries "Common Mistakes to Avoid" (:415, `❌ MISTAKE N`), which is the only clear fixed-block candidate (`anti_patterns`). Prompt text is runtime input, so prefer freeform plus at most `anti_patterns`.
- **Mission-step `guidelines.md`**: rule lists (specify/guidelines.md:7-61). Close to styleguide `principles`, but leave it freeform. Note that no field names it today (R6, architect §1).
- **`pack.md`**: catalog prose, one H1 (built-in/pack.md:1). Freeform `sections` only. This is #195's markdown content form; share the `Section` `$def` with it.

## 4. Findings

- [HIGH] open-packs packs/built-in/toolguides/SONAR.md:5-6, GHERKIN.md:8, PYTHON_MUTATION_TOOLS.md:4-5,168, TYPESCRIPT_MUTATION_TOOLS.md:4-5,199, CONTEXTIVE.md:162 - The guide prose links to styleguides and tactics (`../styleguides/...yaml`, `../tactics/...yaml`), but toolguide.graph.yaml has outbound edges only from `contextive` (:60-72, three tactics, not kitty-glossary-writing) and `git-worktree-pr-workflow` (:75-78). These relationships are invisible to context resolution. - The structured shape must **not** parse Markdown links into relationships (R11). Keep them as body text, and file an open-packs issue to add `suggests` edges, or to remove the links. Put it on the divergence register; it does not block this mission.
- [HIGH] core src/charter/activation/context_renderers/artifact_bodies.py:253-265 - Only title, tool and summary reach an agent, so any fixed block in the guide (including `when_to_use`) is pull-only. - Keep `when_to_use` in the guide for this mission, since moving it is a content migration. Record that if core ever renders the trigger, `when_to_use` should be promoted to YAML metadata and removed from the guide. It must never live in both (DIRECTIVE_037).
- [MEDIUM] open-packs packs/qa/toolguides/qa-carrier-lint.toolguide.yaml:5-28 (also qa-result-ingest) - The `summary` carries invocation flags, exit codes and upstream issue status. That duplicates the guide's "Flag surface" and "Exit codes" sections and inflates the push surface (token budget). - Recommend an authoring/lint rule: the summary is at most about two sentences of what and when. File as an open-packs issue; the converter must not rewrite it.
- [MEDIUM] charter contracts/packs/schemas/toolguide.schema.json:60 (`references: string[]`) - This list mixes artifact paths (git-worktree-pr-workflow.toolguide.yaml references two tactic yamls) with external URLs (drupal-review-checks.toolguide.yaml `references`). The guide also has its own "References" sections. That gives external sources two homes and relationships two authorities. - In the sanitized shape, external sources go in guide `references: [{title,url}]`. YAML `references` stays frozen legacy pending #189: read, not extended, with artifact entries moving to edges. Do not widen it to the R7 `Reference` `$def` for toolguides now (profile: the fix is an edge, not a wider enum).
- [MEDIUM] charter toolguide.schema.json:30 `summary` - This conflicts with R5 (`description`). - Rename in the sanitized DSO only together with core (models.py:18 `summary: str`, extra=forbid) and the renderer (artifact_bodies.py:262). Otherwise it goes on the divergence register (architect §4 already lists it).
- [MEDIUM] Naming conflict: the operator's phrase "heading + body" vs R4/R9. R4 makes `title` the display label, and R9 step items use `title*`. - Use `Section {title, body}`. `body` does not clash with R5's `description`: `description` is summary prose and `body` is Markdown content (skill `body_path` precedent). Flag it to the operator, because "heading" as a key would be a new label word.
- [MEDIUM] R8 vs bare-bullet sources: GIT_AGENT_COMMIT_SIGNING.md:18-21 and EFFICIENT_LOCAL_TOOLING.md:165 "Avoid" are one-line bullets with no separate description. R8 makes `description` required. - Either map each bullet to `description`, with `name` derived (that is fabrication, which the converter must not do), or make `name` the only required field. My recommendation: `AntiPatternEntry {name*, description?, bad_example?, good_example?}`, which deviates from R8. Alternatively keep bare-bullet "Avoid" lists in `sections` and record a divergence. This is the operator's choice; I flag the conflict.
- [MEDIUM] H1/title divergence (CONTEXTIVE.md:1, POWERSHELL_SYNTAX.md:1) and canonical headings - Rendering H1 from `title` and fixed blocks with canonical headings ("Anti-patterns" instead of "Red Flags" or "Avoid") changes bytes for most guides. The operator ruling "sanitized wins over round-trip as-is" (squad/synthesis.md:5) covers this. - Do not add a per-block `heading` override, because that would reintroduce the free shape. List the diverging guides as known failures with an open-packs normalization issue.
- [LOW] open-packs packs/qa/toolguides/*.toolguide.yaml:5 `guide_path: toolguides/...` vs built-in `packs/built-in/toolguides/...` - There are two base conventions. R6 says the path is relative to the artifact file, and neither matches it. - Add to the divergence register. If the guide is now stored structured inside the DSO, clarify whether `guide_path` names the content companion (ADR-0039 text companion) or just the render target. It must not be both a content source and a rendered output.
- [LOW] core src/specify_cli/cli/commands/charter/authoring.py:145 - The comment says `guide_path must match ^src/charter/offering/.+\.md$`, but models.py:15 enforces `^[^/].*\.md$` and the scaffold writes a core-tree path for pack content. - Stale comment: file a core issue. No toolguide guide template exists anywhere (scaffold writes only YAML), so the fixed-block set has no core precedent to conflict with.
- [LOW] core src/charter/offering/toolguides/README.md - Calls POWERSHELL_SYNTAX.md "a canonical example". That guide has an emoji banner, an `---` rule and ❌ headings (:3-7, :90), none of which fit a sanitized shape. - Docs drift: a core issue once the shape lands.
- [INFO] `tool` field: no other kind has an equivalent. Keep it. It is the join key for the profile's "external reference lacks a toolguide" audit, and it is not redundant with `title`. Several tools are stacks (`python-quality-stack`, `java-supply-chain-stack`), which is acceptable.

## 5. Naming check against cross-kind conventions (R1-R13)

| Name | Verdict |
|---|---|
| `overview`, `when_to_use`, `out_of_scope`, `setup`, `verification`, `known_limitations`, `references`, `sections` | snake_case (R1). New words, none clashing with an existing field of different meaning. `when_to_use` deliberately does **not** reuse `entry_condition`: a toolguide is not gated, and using the procedure term would imply a gate (profile: the tactic/procedure entry/exit-gate test) |
| `anti_patterns` / `AntiPatternEntry` | Same name and meaning as styleguide/procedure, so it reuses R8. Conflict on `description` required: see F7 |
| `Section.title` | R4/R9-consistent. Conflicts with the operator's "heading" wording (F6) |
| `Section.body` | Consistent with skill `body_path`. Distinct from R5 `description` |
| `references[].{title,url}` | `title` per R4. `url` is new. Do **not** call this `Reference` (R7 reserves it for artifact refs `{type,id,...}`); name the `$def` `ExternalReference` |
| `known_limitations` vs tactic `failure_modes` | Kept distinct on purpose: different meaning |
| Closed objects (R12), lists default `[]` (R13), `schema_version` const "1.0" (R2) | Apply to the guide object and its items. No open maps needed |

## Verdict

**Go, with two operator choices.** A small, meaning-based fixed set holds up: `overview`, `when_to_use`, `out_of_scope`, `setup`, `verification`, `anti_patterns`, `known_limitations` and `references`, plus a required-in-spirit `sections: [{title, body}]`. Each block answers a question every tool guide has, and each reuses an existing cross-kind shape or meaning where one exists. The YAML stays the push surface (identity, trigger summary, command index, scope); the guide is pull-only depth. Relationships stay in DRG edges, never in guide prose or a grown YAML `references`.

Operator choices:
1. `Section.title` vs "heading". I recommend `title`.
2. `AntiPatternEntry.description` required (R8) vs optional, to absorb bare "Avoid" bullets without fabricating text. I recommend optional.

Out-of-lens concession: which source heading maps to which block, and the byte-level round-trip consequences, belong to the empirical miner and the gate design.
