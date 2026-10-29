# Analyst Annie - empirical mining of toolguide Markdown shapes

Scope: 24 guides in /home/user/spec-kitty-open-packs (HEAD 01edb4e, untouched, read-only). Lens: empirical heading/block mining. Scripts: /tmp/claude-0/-home-user-spec-kitty-charter/1b017596-2b1b-5bc7-9561-825d8823c58f/scratchpad/squad2/{mine.py,ov.py,tbl.py}; raw outputs mine.txt, tbl.txt, mine.json in the same dir.

Profile applied (analyst-annie, plan-action charter context): traceability (every claim cites file:line or script output), no implementation/architecture choices (I recommend a *content shape*, not a schema/storage design), unambiguous testable criteria (the fixed-block definitions below are phrased so a validator could check them), edge cases explicit. Directives from charter context in force: DIRECTIVE_001/003/010/025/030/032/034/037/041/051, QA_003, RECONCILE_CHANGE_SCOPE_TENSIONS; paradigms specification-by-example + BDD (hence the example-driven per-guide table). Read-only: nothing edited.

Method caveats (honest limits): heading-to-block assignment is my semantic judgement (assignments listed in section 5 so they can be challenged); counts are of H2s outside fences; "coverage" = guides (of 24) with at least one H2 (or preamble for overview) semantically in that block. N=24 is small and 3 packs are uneven (18/3/3), so percentages are descriptive, not statistical.

## 0. Headline numbers

- 24 guides, 171 H2, 83 H3 (H3 in only 10 guides). 3,549 lines total. All start with H1; none has frontmatter.
- 21/24 have a preamble (text between H1 and first H2); the 3 without (contextive, terminology-guard, test-quality-triage) open straight into an H2 whose content is the intro ("What is Contextive?", "Overview", "Overview").
- 7/24 H1s differ from YAML `title` (contextive, github-tracker, powershell-syntax, test-quality-triage, qa-carrier-lint, qa-promise-trace-lint, qa-result-ingest).
- Under the recommended fixed set (4 blocks), 121 of 171 H2 (71%) land in freeform. The fixed blocks are real but thin; the freeform list carries most of the content. This is the key empirical finding.
- Code fence languages (guides using each): bash 19, yaml 6, none(untagged) 5, powershell 2, markdown 2, toml 2, one each gherkin/mermaid/plantuml/makefile/python/properties/json/typescript. Pipe tables in 13 guides (8 each in the two mutation guides). Blockquotes in only 1 guide (GITHUB_TRACKER.md:68-69, a "Note:" admonition). Pseudo-admonitions as bold lines: GITHUB_TRACKER.md:3 and POWERSHELL_SYNTAX.md:3 ("**⚠️ READ THIS ...**") followed by `---`.

## 1. Per-guide outline, preamble, blocks, H1 mismatch

Full machine outline (H2/H3 with line numbers, block counts, YAML keys) is in section A (appendix) and in /tmp/claude-0/-home-user-spec-kitty-charter/1b017596-2b1b-5bc7-9561-825d8823c58f/scratchpad/squad2/mine.txt. Summary points:

H1 vs YAML `title` mismatches (type of drift):
- CONTEXTIVE.md:1 "Contextive Toolguide" vs "Contextive" - suffix.
- GITHUB_TRACKER.md:1 "... — `gh` CLI + GraphQL" vs "... (gh CLI + GraphQL)" - punctuation/markup only.
- POWERSHELL_SYNTAX.md:1 "... for AI Agents" vs "PowerShell Syntax Guide" - suffix.
- TEST_QUALITY_TRIAGE.md:1 "Test-Quality Triage" vs "... (static scan of the pytest corpus)" - YAML carries a descriptor the H1 lacks.
- QA-CARRIER-LINT.md:1 / QA-RESULT-INGEST.md:1 "... — Invocation Contract" vs YAML "(Coverage Validator)" / "(JUnit-XML Producer)" - different subtitles.
- QA-PROMISE-TRACE-LINT.md:1 "QA Promise Trace Lint" vs "(promise -> scenario chain)".
=> H1 is a second, drifting copy of `title`. 

Preamble content types: (a) scope/purpose paragraph (most); (b) cross-references to doctrine via relative links `../styleguides/...yaml`, `../tactics/...yaml` (GHERKIN, PYTHON_MUTATION_TOOLS, TYPESCRIPT_MUTATION_TOOLS, SONAR: 4 guides with live relative links; the 3 supply-chain guides reference tactics by name in backticks only); (c) agent-warning callout + `---` (GITHUB_TRACKER, POWERSHELL_SYNTAX); (d) a scope-exclusion statement ("documents invocation contract only; does not restate the carrier schema" - QA-CARRIER-LINT, QA-RESULT-INGEST; "reads two trees and runs nothing" - QA-PROMISE-TRACE-LINT).

## 2. Semantic clustering of H2/H3 headings into candidate forms

Coverage = guides of 24. "YAML overlap" = which YAML field carries the same information.

| Candidate form | Observed synonyms (guide) | Coverage | YAML overlap |
|---|---|---|---|
| Overview / Purpose / When to use | preamble (21 guides); "What is Contextive?", "Overview" (terminology-guard, test-quality), "Purpose and When to Use" (mermaid, plantuml), "Purpose and when to use" (branded), "What Sonar Measures", "When to Run" (terminology-guard), "When to Use What" (powershell) | 24/24 as content; an explicitly-headed H2 in only 8 | `summary` (see F-2: paraphrase, not verbatim; 5-gram overlap with preamble is 0-11% in all 24 cases, and for the QA trio the YAML summary is 561-1726 chars vs ~100-word preamble) |
| Setup / Prerequisites / Install / Config | "Installation" (contextive), "Installation and Configuration" (py/ts-mutation), "Getting Started" (mermaid, plantuml), "Dependencies" (branded), "Environment" (drupal), "Resolving the script" (qa-carrier, qa-ingest), "Inputs" (qa-promise), "Auth" (github-tracker), "Glossary File Setup" (contextive) | 11/24 (46%) | none directly; `applies_to_languages` is adjacent but is metadata, not setup |
| Usage / Run / Commands / Workflow | "Running" (py/ts-mutation), "Command" (terminology-guard), "Kick off a run" (test-quality), "Generating the PDF" (branded), "Flag surface" (qa-carrier, qa-ingest), "Rendering" (mermaid, plantuml), "Running Spec-Kitty Commands (PowerShell)", "Required behavior" (commit-signing), "Handy one-call queries" (github-tracker), "Integration with Spec Kitty Workflows" (contextive) | 13/24 (54%); zero shared heading tokens across synonyms | `commands` (see F-1) |
| Output / Results / Exit codes / Findings interpretation | "Exit codes" (qa-carrier, qa-ingest, qa-promise), "Findings" (qa-promise), "Outputs", "Flag codes" (test-quality), "Understanding Failure Output" (terminology-guard), "Reviewing Results" (py-mut), "Reading the HTML Report" (ts-mut), "Verifying the output" (branded), "`qa-coverage.json` - the computed-output record", "`qa-traceability.yaml` - the produced carrier" | 10/24 (42%) but body semantics diverge (exit-code tables vs report-reading advice) | none |
| Pitfalls / Avoid / Red flags / Known limitations | "Avoid" (efficient, commit-signing, worktree), "Common Pitfalls" (mermaid, plantuml), "Common Mistakes to Avoid" (powershell), "Red Flags" (py-mut, ts-mut, sonar), "mutmut 3.x-Specific Gotchas", "GraphQL query gotchas", "Pitfall quick-table" (github-tracker), "Known gotchas" (branded), "Known false positives" (test-quality), "Known limitation" (qa-carrier, qa-ingest) | 14/24 (58%); 12/24 if qa "Known limitation" (tool limits, not agent mistakes) is excluded | `summary` of qa-carrier/qa-ingest duplicates the Known-limitation text (F-3) |
| Examples | "Examples" H2 (commit-signing); "Examples" H3 (powershell); "A Complete Feature" (gherkin); "Template Library" (mermaid, plantuml) | 5/24 as headed section (21%); fenced code present in 24/24 guides as inline examples | none |
| References | "References" H2 (contextive, mermaid, plantuml); unheaded trailing attribution after `---` (drupal, DRUPAL_REVIEW_CHECKS.md:130-135) | 4/24 (17%) | `references` (present in YAML for drupal + worktree only; worktree's two tactic paths appear nowhere in its md; drupal's amazee.io source is in both) |
| Related doctrine links | relative links to styleguide/tactic YAML in preamble | 4/24 live links (gherkin, py-mut, ts-mut, sonar) + 3 supply-chain by backticked name | `references` (same purpose, different place) |
| CI integration | "In CI" (qa-promise), "CI Integration (opt-in)" (ts-mut), "CI Integration (GitHub Actions)" H3 (mermaid, plantuml) | 4/24 | none |
| Review checklist order | "Review Checklist Order" (python, maven), "Before Submitting" (drupal) | 3/24 (review-checks family only) | none |
| Troubleshooting | "Debugging PowerShell Issues" (powershell), "How to Fix" (terminology-guard) | 2/24 | none |
| Project conventions / Accessibility / Theming | mermaid + plantuml only | 2/24 | none |
| Reference data / syntax tables | "Term Definition Syntax", "Core Diagram Types", "Python-Specific Mutation Families", "Flag codes", "Quick Reference: Bash vs PowerShell" | ~6/24, heterogeneous | none |

Notable H3 patterns: per-tool H3 under category H2 (maven 5, python-review 11, drupal 3); per-diagram-type H3 (mermaid 5, plantuml 5); per-mutation-family H3 (py 7, ts 7); alt-environment H3 (VS Code / CLI). These are repeated-item lists inside a freeform section, not new top-level forms.

## 3. Families (shared skeletons)

- Supply-chain (JAVA, JAVASCRIPT, PYTHON): H2 skeleton Registry authenticity -> Freshness -> Install-time hook scripts (JS: "Deny-by-default lifecycle scripts") -> Lockfile-driven reproducible installs -> [one ecosystem extra: "Checksum verification summary" (java), "Node Active LTS runtime baseline" (js), "Hash pinning" (python)] -> Incident/IoC posture. Identical 5-item backbone, all three preambles open with the same sentence ("Ecosystem-specific mechanics for the `supply-chain-install-safety` tactic's checklist..."). No cautions/setup/usage H2s. Pure freeform in a generic shape, but strongly family-structured.
- Review-checks (PYTHON, MAVEN, DRUPAL): category-per-H2 (Formatting/Style, Linting, Type safety, Static analysis, Architecture, Security (and Supply Chain), Test Quality/Tests, Coverage) with tool-per-H3 and a bash block each; python+maven end with an ordered "Review Checklist Order"; drupal ends with "Before Submitting" and an unheaded attribution. Python/Maven preambles are near-identical sentences (PYTHON_REVIEW_CHECKS.md:3, MAVEN_REVIEW_CHECKS.md:3).
- Mutation-tools (PYTHON, TYPESCRIPT): Installation and Configuration -> Running -> Reviewing Results / Reading the HTML Report -> Annotating Equivalent Mutants -> [Applying a Fix | Incremental Mode] -> language-specific mutation families (7 H3s each, 8 pipe tables each) -> [CI Integration] -> Red Flags (+ mutmut gotchas). Only family with all four recommended blocks.
- Diagramming (MERMAID, PLANTUML): 10-11 H2 identical skeleton: Purpose and When to Use / Getting Started / Core Diagram Types / Theming / [Styling Patterns] / Rendering / Project Conventions / Accessibility / Common Pitfalls / Template Library / References. Highest overlap in the corpus; the only guides with an explicit References H2 besides contextive.
- QA tool guides (QA-CARRIER-LINT, QA-RESULT-INGEST, QA-PROMISE-TRACE-LINT): "invocation contract" shape - Resolving the script / Flag surface / Exit codes / output-record schema / Known limitation. Promise-trace variant: Inputs / Promise page frontmatter / Scenario tags / Findings / Exit codes / In CI (no flag surface, no limitation). Two of three also carry meta text ("Note on this guide's own provenance", QA-RESULT-INGEST.md:103-110).
- Internal run-a-tool (TERMINOLOGY_GUARD, TEST_QUALITY_TRIAGE, BRANDED_DOCUMENT_GENERATION): Overview -> run/command -> options/outputs -> failure/gotchas. Loose family; resembles the QA shape.
- Gotcha collections (EFFICIENT_LOCAL_TOOLING, GIT_WORKTREE_PR_WORKFLOW, GIT_AGENT_COMMIT_SIGNING, GITHUB_TRACKER, POWERSHELL_SYNTAX): topic-per-H2 plus a closing "Avoid"/pitfall section (4 of 5).
- Notation/reference singletons: GHERKIN, CONTEXTIVE, SONAR (no cautions/usage in most) - no family.

## 4. Findings

[HIGH] packs/*/toolguides/*.toolguide.yaml `summary` vs guide preamble - Two parallel intro texts per toolguide. In all 24 the YAML `summary` and the Markdown preamble/overview paraphrase the same scope (verbatim 5-gram overlap only 0-11%, so a dedupe script cannot detect it; they drift independently). For 3 packs the YAML summary is long (qa-result-ingest 1726, qa-carrier 1448, branded 808 chars) and acts as a second guide body. - Recommend: keep a short `summary` in YAML as the index blurb; make the guide's `overview` block the long-form "scope/when to use" and forbid restating the summary; cap summary length (observed median ~250 chars; QA trio are outliers).

[HIGH] qa-carrier-lint.toolguide.yaml (summary, ~lines 5-28) vs QA-CARRIER-LINT.md:110-120 - The "Known limitation" paragraph (GateBinding.handler_kind "asset" inert, #2599, #3597, 'agent-invoked, never machine-enforced') appears near-verbatim in both YAML summary and guide; QA-RESULT-INGEST has the same limitation text (md:93-101) and its YAML summary repeats it. Time-sensitive claim stored twice -> staleness risk. - Recommend: single home in the guide `cautions` block; summary links/omits.

[HIGH] `commands` YAML vs in-guide invocations - 21 of 23 guides with non-empty `commands` contain every (or all but one) YAML command string verbatim in the Markdown (ov.py: e.g. python-review-checks 11/11, drupal 9/9, efficient-local-tooling 19/20, maven 8/8). So YAML `commands` is a derived index of what the guide's code fences already say. Exceptions show the field's meaning is inconsistent: qa trio list `python qa_carrier_lint.py --carrier PATH ...` but md contains it only 1/4 (md uses different fence form); gherkin has 0 commands (legitimately N/A); review-checks list bare tool names (ruff, mypy) rather than invocations. - Recommend: do NOT add a "Commands" fixed block to the guide (it would triple the duplication); keep `commands` in YAML as the machine index and let guide `usage` hold prose + fenced examples. Consider normalising `commands` semantics (tool names vs invocations) separately.

[MEDIUM] Heading-text matching is not viable for fixed blocks - The 13 "usage" headings share no token ("Running", "Command", "Kick off a run", "Generating the PDF", "Flag surface", "Rendering", ...); setup and cautions have 9 and 10 distinct spellings. A validator or divergence check that keys on heading text would report false divergence. - Recommend: fixed blocks carry an explicit kind key (overview|setup|usage|cautions); the heading is a free display title.

[MEDIUM] H1 duplicates YAML `title` and drifts in 7/24 (see section 1) - Recommend: strip H1 from the stored body (title is canonical in YAML) and render H1 from `title`; record the 7 divergent pairs in the divergence table. Moving the subtitle decorations ("- Invocation Contract") into `summary` or dropping them is a content decision for the operator.

[MEDIUM] DRUPAL_REVIEW_CHECKS.md:130-135 - Trailing unheaded attribution paragraph after a `---` rule ("Adapted from amazee.io's drupal-agents-md ..."). Fits no section; the same source is already in YAML `references` (drupal-review-checks.toolguide.yaml:23-24). Only guide with a trailing unheaded note. - Recommend: drop from body (YAML references is the single home), or map to a `provenance`/freeform "Attribution" section if the operator wants it visible.

[MEDIUM] QA-RESULT-INGEST.md:103-110 "Note on this guide's own provenance" - Process note about a work-package review ("must be reconciled ... before that review approves"): authoring-time scaffolding, not toolguide content, and stale once the WP closes. - Recommend: flag to operator for removal, not a fixed block; otherwise freeform.

[MEDIUM] Preamble boilerplate that is presentation, not content - GITHUB_TRACKER.md (lines 3-11) and POWERSHELL_SYNTAX.md (lines 3-7): "**⚠️ READ THIS ...**" bold pseudo-admonition + `---`; GITHUB_TRACKER has 9 `---` rules in total (lines 11,22,49,64,98,118,160,179,196); POWERSHELL_SYNTAX has 10. Also emoji in headings (GITHUB_TRACKER.md:87 "⚠️ Two different `.id`s", POWERSHELL_SYNTAX.md:90,100 "❌"). - Recommend: sanitiser strips horizontal rules between sections (structure makes them redundant) and maps ⚠️ READ THIS to an optional `callout`-free overview sentence or a freeform "Read first" section; decide emoji-in-heading policy.

[MEDIUM] References are split across three places - H2 "References" in 3 md (CONTEXTIVE.md:205, MERMAID_DIAGRAMMING.md:297, PLANTUML_DIAGRAMMING.md:299), YAML `references` in 2 yaml (drupal, worktree), live relative doctrine links in 4 preambles (gherkin, py-mut, ts-mut, sonar), backticked tactic names in 3. Worktree's two YAML refs (tactic paths) are absent from its md; contextive/mermaid/plantuml URL lists are absent from their YAML. Relative links like `../styleguides/given-when-then-authoring.styleguide.yaml` (GHERKIN.md:8) break if the guide is stored/rendered elsewhere. - Recommend: References coverage (3-4/24, 17%) misses the 40% bar; resolve to YAML `references` as single home, migrate the 3 H2 lists (17 URLs total) into it, and sanitise relative doctrine links into references entries.

[LOW] Coverage cliff for "Examples" (5/24 headed) and "Troubleshooting" (2/24) - Below any sensible threshold; tempting generic names with little data behind them. - Recommend: not fixed blocks. (Examples exist as fenced code inside other sections in 24/24 guides - a block type, not a section.)

[LOW] Output/exit-code block sits at 10/24 (42%, just above a 40% bar) but its bodies diverge (3 qa exit-code tables vs report-reading prose) - Recommend: keep out of the fixed set for v1 (rule of thumb: weak meaning-consistency); revisit as an optional `results` block if the QA family grows.

[LOW] Lens limitation - Empirical mining shows what *exists*, not what *should*; it under-represents forms that no current author wrote (e.g. troubleshooting, prerequisites for the 13 guides with no setup). Also 6 of the 24 guides sit in two families (supply-chain, review-checks) whose structure is *domain-defined by tactics*, not by toolguide form - I concede that for them a family-specific subtype may beat the generic shape (see verdict). Block-boundary assignments are my judgement; a second reviewer should challenge section 5 mapping.

## 5. Recommended fixed set

Threshold used: a block is fixed iff (a) at least 10/24 guides (>= ~40%) contain semantically matching content, AND (b) its meaning is one sentence and does not duplicate a YAML field in a way that can be removed. Passing blocks, in order:

1. overview (required, 24/24). Definition: what the tool/practice is, scope, and when to apply it (and what the guide does NOT cover). Maps: preamble (21 guides); H2 "What is Contextive?", "Overview" x2, "Purpose and When to Use" (mermaid/plantuml), "Purpose and when to use" (branded, which has BOTH a preamble and this H2: merge). Content to move/rename: contextive, terminology-guard, test-quality-triage (no preamble: promote their intro H2 to overview); mermaid, plantuml, branded (merge preamble + Purpose H2); strip `**⚠️ READ THIS**`/`---` in github-tracker, powershell. Must not repeat YAML `summary`.
2. setup (optional, 11/24 = 46%). Definition: what must be installed, resolved, authenticated or configured before first use. Maps: Installation, Installation and Configuration, Getting Started, Dependencies, Environment, Resolving the script, Inputs, Auth, Glossary File Setup. Move/rename: contextive (2 H2 -> setup), github-tracker "Auth", py/ts-mutation, mermaid/plantuml "Getting Started" (note it holds three H3 install variants), branded "Dependencies", drupal "Environment", qa-carrier/qa-ingest "Resolving the script" (borderline: it is script-asset resolution), qa-promise "Inputs" (borderline: it describes inputs, not setup; could stay freeform).
3. usage (optional, 13/24 = 54%). Definition: how to invoke or operate the tool or practice (commands, flags, workflow steps). Maps: Running, Command, Kick off a run, Generating the PDF, Flag surface, Rendering, Running Spec-Kitty Commands, Required behavior, Handy one-call queries, Integration with Spec Kitty Workflows. Largest ambiguity: the review-checks and supply-chain families are *entirely* usage-like (every H2 is "how to run check X") yet I leave them freeform because mapping all 25+ H2s into one block would erase their structure; the operator must decide (see verdict). Commands themselves stay in fenced code; YAML `commands` remains the index.
4. cautions (optional, 14/24 = 58%). Definition: things to avoid, failure modes, false positives or known limitations. Maps: Avoid, Common Pitfalls, Common Mistakes to Avoid, Red Flags, Gotchas / Known gotchas, Pitfall quick-table, Known false positives, Known limitation. Move/rename: 14 guides (list in table); qa "Known limitation" (decide whether tool limitations belong here or in freeform - affects 2 guides); mutmut gotchas (py-mut) merge into Red Flags or keep as a second item; github-tracker has two (gotchas + quick-table) - allow a body with several items.

Rejected for fixed status (and why): references (4/24, 17%; YAML field exists -> single home there), examples (5/24 headed), results/exit-codes (10/24 but inconsistent meaning), CI (4/24), checklist order (3/24, family-only), troubleshooting (2/24), related-doctrine (4/24, belongs in references).

Freeform: everything else = 121 of 171 H2 (71%) plus all 83 H3 nested under their parent (H3 stays inside the parent body; only two guides, mermaid/plantuml, use H3-heavy structure - 14 and 17). Freeform entry = {heading, body}; body keeps fenced code, tables, nested H3.

Content that fits no section (flag): trailing attribution (DRUPAL_REVIEW_CHECKS.md:130-135); authoring provenance note (QA-RESULT-INGEST.md:103-110); `---` rules; `⚠️ READ THIS` banners (GITHUB_TRACKER, POWERSHELL_SYNTAX); POWERSHELL_SYNTAX.md:234 closing line "For full spec-kitty documentation, see the main templates and README."; GITHUB_TRACKER.md:68-69 blockquote "Note:" scoped mid-section (should stay in its freeform body, only blockquote in corpus).

## 6. Per-guide mapping table (divergence-table input)

Y = block present (by my section-2 assignment), - = missing. "freeform H2" = H2s not mapped to the four fixed blocks or to YAML-owned refs. The overview column is Y for all 24, so "missing" divergence is zero there; the real divergence is the number of guides needing the preamble merged/promoted.

| guide | H2 | H3 | preamble | overview | setup | usage | cautions | refs-H2 | freeform H2 | code blocks | tables | bq | H1==title |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| contextive | 7 | 11 | N | Y | Y | Y | - | Y | 2 | 9 | 2 | - | N |
| drupal-review-checks | 7 | 3 | Y | Y | Y | - | - | - | 6 | 7 | 1 | - | Y |
| efficient-local-tooling | 13 | 0 | Y | Y | - | - | Y | - | 12 | 12 | 0 | - | Y |
| gherkin | 6 | 0 | Y | Y | - | - | - | - | 6 | 4 | 1 | - | Y |
| git-agent-commit-signing | 3 | 0 | Y | Y | - | Y | Y | - | 1 | 1 | 0 | - | Y |
| git-worktree-pr-workflow | 5 | 0 | Y | Y | - | - | Y | - | 4 | 4 | 0 | - | Y |
| github-tracker | 9 | 2 | Y | Y | Y | Y | Y | - | 5 | 1 | 1 | Y | N |
| java-supply-chain | 6 | 0 | Y | Y | - | - | - | - | 6 | 3 | 0 | - | Y |
| javascript-supply-chain | 6 | 0 | Y | Y | - | - | - | - | 6 | 7 | 0 | - | Y |
| maven-review-checks | 9 | 5 | Y | Y | - | - | - | - | 9 | 8 | 0 | - | Y |
| mermaid-diagramming | 10 | 14 | Y | Y | Y | Y | Y | Y | 5 | 13 | 0 | - | Y |
| plantuml-diagramming | 11 | 17 | Y | Y | Y | Y | Y | Y | 6 | 14 | 1 | - | Y |
| powershell-syntax | 9 | 6 | Y | Y | - | Y | Y | - | 7 | 14 | 1 | - | N |
| python-mutation-tools | 8 | 7 | Y | Y | Y | Y | Y | - | 4 | 7 | 8 | - | Y |
| python-review-checks | 7 | 11 | Y | Y | - | - | - | - | 7 | 11 | 0 | - | Y |
| python-supply-chain | 6 | 0 | Y | Y | - | - | - | - | 6 | 6 | 0 | - | Y |
| sonar | 5 | 0 | Y | Y | - | - | Y | - | 4 | 2 | 1 | - | Y |
| typescript-mutation-tools | 8 | 7 | Y | Y | Y | Y | Y | - | 5 | 6 | 8 | - | Y |
| branded-document-generation | 6 | 0 | Y | Y | Y | Y | Y | - | 2 | 3 | 0 | - | Y |
| terminology-guard | 5 | 0 | N | Y | - | Y | - | - | 3 | 3 | 0 | - | Y |
| test-quality-triage | 7 | 0 | N | Y | - | Y | Y | - | 4 | 4 | 3 | - | N |
| qa-carrier-lint | 5 | 0 | Y | Y | Y | Y | Y | - | 2 | 1 | 2 | - | N |
| qa-promise-trace-lint-guide | 6 | 0 | Y | Y | Y | - | - | - | 5 | 2 | 2 | - | N |
| qa-result-ingest-guide | 7 | 0 | Y | Y | Y | Y | Y | - | 4 | 1 | 2 | - | N |

Totals: overview 24, setup 11, usage 13, cautions 14, refs-H2 3. Freeform H2 total 121/171.
Guides needing most restructuring (divergence ranking): contextive (promote H2 to overview, 2->setup, 1->usage, References->YAML, H1 differs), mermaid/plantuml (merge purpose, 4 fixed mappings, References->YAML), github-tracker (H1 differs, banners, 4 mappings), powershell-syntax (H1 differs, banners/rules, 3 mappings), branded-document-generation (preamble + Purpose H2 to merge), qa-* (H1 differs, summary-duplicated limitation). Near-zero restructuring: gherkin, java/javascript/python-supply-chain, maven/python-review-checks (overview only; rest freeform), commit-signing (3 mappings, clean).

## Verdict

Mine-able fixed forms are few: overview is universal; cautions (58%), usage (54%) and setup (46%) pass a 40% bar; everything else fails it. The honest reading of the data is that a "few fixed blocks + freeform list" shape works for the ~12 run-a-tool/gotcha guides but leaves ~71% of H2 content freeform, and the two largest families (supply-chain, review-checks: 6 guides, 35 H2) are structured by an external tactic/checklist, not by these generic forms. Recommend: ship {overview required; setup, usage, cautions optional; freeform[]} keyed by explicit `kind` (not heading text), take references/summary/commands/title from YAML as the single home (dedupe the 4 duplications above), strip H1/rules/banners at sanitise time. Open operator decisions: (1) treat review-checks and supply-chain families as freeform or give them a repeating `checks[]` subtype; (2) where `Known limitation` of QA guides lives; (3) fate of DRUPAL trailing attribution and QA-RESULT-INGEST provenance note. Confidence: medium-high on counts, medium on block assignment (judgement), low-medium on generalisation beyond these 24.

## Appendix A - machine outline (mine.txt)

```
== contextive built-in/toolguides/CONTEXTIVE.md lines 212 pre 0 H1==title False
   H1: Contextive Toolguide | YAML: Contextive
  H2 What is Contextive? (L3)
  H2 Installation (L21)
  H2 Glossary File Setup (L35)
    H3 Single-context repository (L37)
    H3 Multi-context repository (monorepo) (L59)
  H2 Term Definition Syntax (L76)
    H3 Required fields (L78)
    H3 Recommended fields (L88)
    H3 Multi-word terms (L104)
    H3 Aliases (L119)
    H3 Metadata (L131)
  H2 Integration with Spec Kitty Workflows (L144)
    H3 Glossary curation (L146)
    H3 Bounded Context Canvas (L153)
    H3 Glossary writing styleguide (L160)
    H3 Term lifecycle (L169)
  H2 Multiline YAML Tips (L184)
  H2 References (L205)
  blocks: code {'yaml': 8, 'none': 1} tbl 2 bq 0 ul 13 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== drupal-review-checks built-in/toolguides/DRUPAL_REVIEW_CHECKS.md lines 135 pre 3 H1==title True
  H2 Coding Standards (L7)
    H3 phpcs (`Drupal` and `DrupalPractice`) (L9)
  H2 Static Analysis (L25)
    H3 PHPStan (L27)
    H3 drupal-check (deprecated API usage) (L38)
  H2 Tests (L49)
  H2 JavaScript (L84)
  H2 Environment (L100)
  H2 Supply Chain (L106)
  H2 Before Submitting (L117)
  blocks: code {'bash': 7} tbl 1 bq 0 ul 5 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'references']
== efficient-local-tooling built-in/toolguides/EFFICIENT_LOCAL_TOOLING.md lines 176 pre 1 H1==title True
  H2 Session record (L5)
  H2 Repository search (L18)
  H2 Large-file inspection (L35)
  H2 Compressed content (L49)
  H2 Structured data inspection (L61)
  H2 Logs and disk usage (L73)
  H2 Git ergonomics (L85)
  H2 Navigation (L97)
  H2 Python environment isolation (uv) (L108)
  H2 Gate execution mode (L122)
  H2 Shell quoting (L134)
  H2 Windows and WSL (L151)
  H2 Avoid (L165)
  blocks: code {'bash': 11, 'powershell': 1} tbl 0 bq 0 ul 38 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands', 'last_updated']
== gherkin built-in/toolguides/GHERKIN.md lines 128 pre 7 H1==title True
  H2 The Building Blocks (L11)
  H2 A Complete Feature (L32)
  H2 Scenario Outline and Examples (L57)
  H2 Step Arguments: Doc Strings and Data Tables (L76)
  H2 And / But (L100)
  H2 Scope Boundary — Language, Not Runner (L115)
  blocks: code {'gherkin': 4} tbl 1 bq 0 ul 3 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== git-agent-commit-signing built-in/toolguides/GIT_AGENT_COMMIT_SIGNING.md lines 22 pre 1 H1==title True
  H2 Required behavior (L5)
  H2 Examples (L11)
  H2 Avoid (L18)
  blocks: code {'bash': 1} tbl 0 bq 0 ul 5 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== git-worktree-pr-workflow built-in/toolguides/GIT_WORKTREE_PR_WORKFLOW.md lines 109 pre 4 H1==title True
  H2 Shared stash stack across worktrees (L8)
  H2 Don't move a worktree's HEAD while a background job reads it (L38)
  H2 `--force-with-lease` rejection means origin moved, not "force harder" (L61)
  H2 Fork pull-request pushes go over SSH, not HTTPS (L84)
  H2 Avoid (L99)
  blocks: code {'bash': 4} tbl 0 bq 0 ul 13 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands', 'last_updated', 'references']
== github-tracker built-in/toolguides/GITHUB_TRACKER.md lines 211 pre 7 H1==title False
   H1: GitHub Issue-Tracker Operations — `gh` CLI + GraphQL | YAML: GitHub Issue-Tracker Operations (gh CLI + GraphQL)
  H2 Auth (L13)
  H2 Sub-issues — the native parent/child source of truth (L24)
  H2 Reading the parent / walking the chain (L51)
  H2 Issue types (Task / Bug / Feature) (L66)
    H3 ⚠️ Two different `.id`s — the trap that bites repeatedly (L87)
  H2 GraphQL query gotchas (the big time-sinks) (L100)
  H2 Rate-limiting and loops (critical for bulk work) (L120)
    H3 --paginate applies to all gh list surfaces (L136)
  H2 Handy one-call queries (L162)
  H2 Closing keywords — one issue per keyword (L181)
  H2 Pitfall quick-table (L198)
  blocks: code {'bash': 1} tbl 1 bq 2 ul 35 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands', 'applies_to_languages', 'last_updated']
== java-supply-chain built-in/toolguides/JAVA_SUPPLY_CHAIN.md lines 111 pre 7 H1==title True
  H2 Registry authenticity (L11)
  H2 Freshness (L26)
  H2 Install-time hook scripts (L36)
  H2 Lockfile-driven reproducible installs (L45)
  H2 Checksum verification summary (L91)
  H2 Incident/IoC posture (L104)
  blocks: code {'bash': 3} tbl 0 bq 0 ul 0 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== javascript-supply-chain built-in/toolguides/JAVASCRIPT_SUPPLY_CHAIN.md lines 105 pre 7 H1==title True
  H2 Registry authenticity (L11)
  H2 Freshness (L22)
  H2 Deny-by-default lifecycle scripts (L32)
  H2 Lockfile-driven reproducible installs (L68)
  H2 Node Active LTS runtime baseline (L86)
  H2 Incident/IoC posture (L98)
  blocks: code {'bash': 5, 'yaml': 2} tbl 0 bq 0 ul 0 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== maven-review-checks built-in/toolguides/MAVEN_REVIEW_CHECKS.md lines 115 pre 1 H1==title True
  H2 Compilation (L5)
  H2 Tests (L14)
  H2 Coverage (L23)
  H2 Style (L34)
    H3 Checkstyle (L36)
  H2 Static Analysis (L47)
    H3 SpotBugs (L49)
  H2 Architecture (L60)
    H3 ArchUnit (via tests) (L62)
  H2 Security (L75)
    H3 OWASP Dependency-Check (L77)
  H2 Test Quality (L88)
    H3 PIT Mutation Testing (optional, high-cost) (L90)
  H2 Review Checklist Order (L101)
  blocks: code {'bash': 8} tbl 0 bq 0 ul 0 ol 8
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands']
== mermaid-diagramming built-in/toolguides/MERMAID_DIAGRAMMING.md lines 303 pre 1 H1==title True
  H2 Purpose and When to Use (L5)
  H2 Getting Started (L26)
    H3 Quickest (no install) (L28)
    H3 VS Code (L32)
    H3 Command Line (Mermaid CLI) (L37)
  H2 Core Diagram Types (L53)
    H3 Flowchart (L55)
    H3 Sequence Diagram (L69)
    H3 Class Diagram (L86)
    H3 State Diagram (L110)
    H3 Entity-Relationship Diagram (L124)
  H2 Theming (L144)
    H3 Common (Neutral) Theme (L149)
    H3 Bluegray Conversation Theme (L166)
    H3 Custom Node Classes (L187)
  H2 Rendering (L206)
    H3 Inline in Markdown (L208)
    H3 Command Line (L222)
    H3 CI Integration (GitHub Actions) (L235)
  H2 Project Conventions (L245)
  H2 Accessibility (L259)
  H2 Common Pitfalls (L266)
  H2 Template Library (L279)
  H2 References (L297)
  blocks: code {'bash': 2, 'mermaid': 8, 'markdown': 1, 'none': 1, 'yaml': 1} tbl 0 bq 0 ul 29 ol 7
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== plantuml-diagramming built-in/toolguides/PLANTUML_DIAGRAMMING.md lines 306 pre 1 H1==title True
  H2 Purpose and When to Use (L5)
  H2 Getting Started (L21)
    H3 Quickest (no install) (L23)
    H3 VS Code (L27)
    H3 Command Line (L31)
  H2 Core Diagram Types (L46)
    H3 Sequence Diagram (L48)
    H3 Component Diagram (L66)
    H3 Class Diagram (L89)
    H3 Deployment Diagram (L113)
    H3 C4 Context Diagram (L134)
  H2 Theming (L152)
    H3 Including a Theme (L163)
    H3 When to Skip Theming (L173)
  H2 Styling Patterns (L179)
    H3 Basic Skinparam (L181)
    H3 Shared Definitions (L192)
    H3 Notes (L199)
  H2 Rendering (L209)
    H3 Command Line (L211)
    H3 GitHub/GitLab Embedding (L227)
    H3 CI Integration (GitHub Actions) (L235)
    H3 Makefile (L245)
  H2 Project Conventions (L257)
  H2 Accessibility (L267)
  H2 Common Pitfalls (L274)
  H2 Template Library (L285)
  H2 References (L299)
  blocks: code {'bash': 2, 'plantuml': 9, 'markdown': 1, 'yaml': 1, 'makefile': 1} tbl 1 bq 0 ul 31 ol 7
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== powershell-syntax built-in/toolguides/POWERSHELL_SYNTAX.md lines 235 pre 3 H1==title False
   H1: PowerShell Syntax Guide for AI Agents | YAML: PowerShell Syntax Guide
  H2 Quick Reference: Bash vs PowerShell (L9)
  H2 Location Verification (PowerShell) (L24)
  H2 Running Spec-Kitty Commands (PowerShell) (L40)
    H3 Using the spec-kitty CLI (L42)
    H3 Parameter Naming Convention (L57)
    H3 Examples (L64)
  H2 Common Mistakes to Avoid (L88)
    H3 ❌ Don't Use Bash Operators (L90)
    H3 ❌ Don't Use Bash-Style Parameters on Cmdlets (L100)
    H3 Path Separators in PowerShell (L113)
  H2 Environment Variables (L129)
  H2 File Operations (L153)
  H2 Workflow Tips (L177)
  H2 When to Use What (L187)
  H2 Debugging PowerShell Issues (L211)
  blocks: code {'powershell': 14} tbl 1 bq 0 ul 19 ol 9
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== python-mutation-tools built-in/toolguides/PYTHON_MUTATION_TOOLS.md lines 202 pre 3 H1==title True
  H2 Installation and Configuration (L7)
  H2 Running (L31)
  H2 Reviewing Results (L44)
  H2 Annotating Equivalent Mutants (L58)
  H2 Applying a Fix (L73)
  H2 Python-Specific Mutation Families (L85)
    H3 Comparison Operators (L87)
    H3 Arithmetic Operators (L98)
    H3 Logical Operators (L109)
    H3 Membership and Identity (L119)
    H3 Collection Aggregates (L130)
    H3 Loop Control (L139)
    H3 String/Sequence Methods (L148)
  H2 Red Flags (L161)
  H2 mutmut 3.x-Specific Gotchas (L173)
  blocks: code {'bash': 4, 'toml': 1, 'python': 2} tbl 8 bq 0 ul 6 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== python-review-checks built-in/toolguides/PYTHON_REVIEW_CHECKS.md lines 156 pre 1 H1==title True
  H2 Formatting (L5)
    H3 ruff format (or black) (L7)
  H2 Linting (L19)
    H3 ruff check (L21)
  H2 Type Safety (L32)
    H3 mypy (L34)
  H2 Architecture (L47)
    H3 import-linter (L49)
    H3 pytestarch / pyarchtest (L60)
  H2 Security and Supply Chain (L71)
    H3 bandit (L73)
    H3 pip-audit (L83)
    H3 cyclonedx-bom (L93)
  H2 Test Quality (L104)
    H3 pytest + pytest-cov (L106)
    H3 vulture (L117)
    H3 mutmut (optional, high-cost) (L128)
  H2 Review Checklist Order (L140)
  blocks: code {'bash': 11} tbl 0 bq 0 ul 0 ol 10
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands']
== python-supply-chain built-in/toolguides/PYTHON_SUPPLY_CHAIN.md lines 125 pre 6 H1==title True
  H2 Registry authenticity and index pinning (L10)
  H2 Freshness (L41)
  H2 Install-time hook scripts (L50)
  H2 Lockfile-driven reproducible installs (L62)
  H2 Hash pinning (L98)
  H2 Incident/IoC posture (L118)
  blocks: code {'bash': 5, 'toml': 1} tbl 0 bq 0 ul 0 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== sonar built-in/toolguides/SONAR.md lines 93 pre 6 H1==title True
  H2 What Sonar Measures (L10)
  H2 Gate on NEW Code, Not the Whole Project (L22)
  H2 Loopback-Safe URLs — Do NOT Force HTTPS on 127.0.0.1 (L52)
  H2 Working the Findings (L68)
  H2 Red Flags (L85)
  blocks: code {'properties': 1, 'bash': 1} tbl 1 bq 0 ul 12 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== typescript-mutation-tools built-in/toolguides/TYPESCRIPT_MUTATION_TOOLS.md lines 201 pre 3 H1==title True
  H2 Installation and Configuration (L7)
  H2 Running (L40)
  H2 Reading the HTML Report (L53)
  H2 Incremental Mode (L65)
  H2 Annotating Equivalent Mutants (L74)
  H2 TypeScript/JavaScript-Specific Mutation Families (L87)
    H3 Optional Chaining (L89)
    H3 Nullish Coalescing (L98)
    H3 Logical Operators (L107)
    H3 Array Methods (L117)
    H3 Arithmetic and Assignment (L128)
    H3 Comparison Operators (L139)
    H3 Unary Operators (L149)
  H2 CI Integration (opt-in) (L160)
  H2 Red Flags (L191)
  blocks: code {'bash': 2, 'json': 2, 'typescript': 1, 'yaml': 1} tbl 8 bq 0 ul 14 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands', 'last_updated']
== branded-document-generation internal/toolguides/BRANDED_DOCUMENT_GENERATION.md lines 139 pre 5 H1==title True
  H2 Purpose and when to use (L9)
  H2 What the pipeline enforces (L20)
  H2 Dependencies (L49)
  H2 Generating the PDF (L73)
  H2 Verifying the output (L104)
  H2 Known gotchas (L123)
  blocks: code {'bash': 3} tbl 0 bq 0 ul 18 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands']
== terminology-guard internal/toolguides/TERMINOLOGY_GUARD.md lines 61 pre 0 H1==title True
  H2 Overview (L3)
  H2 When to Run (L10)
  H2 Command (L20)
  H2 Understanding Failure Output (L26)
  H2 How to Fix (L42)
  blocks: code {'bash': 2, 'none': 1} tbl 0 bq 0 ul 0 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands']
== test-quality-triage internal/toolguides/TEST_QUALITY_TRIAGE.md lines 114 pre 0 H1==title False
   H1: Test-Quality Triage | YAML: Test-Quality Triage (static scan of the pytest corpus)
  H2 Overview (L3)
  H2 Kick off a run (L16)
  H2 Options (L46)
  H2 Outputs (L56)
  H2 Flag codes (L70)
  H2 Known false positives (L92)
  H2 Shallow clones (L104)
  blocks: code {'bash': 4} tbl 3 bq 0 ul 3 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'applies_to_languages', 'summary', 'commands']
== qa-carrier-lint qa/toolguides/QA-CARRIER-LINT.md lines 121 pre 6 H1==title False
   H1: QA Traceability Carrier Lint — Invocation Contract | YAML: QA Traceability Carrier Lint (Coverage Validator)
  H2 Resolving the script (L10)
  H2 Flag surface (L38)
  H2 Exit codes (L55)
  H2 `qa-coverage.json` — the computed-output record (L65)
  H2 Known limitation (FR-016) (L110)
  blocks: code {'none': 1} tbl 2 bq 0 ul 9 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== qa-promise-trace-lint-guide qa/toolguides/QA-PROMISE-TRACE-LINT.md lines 83 pre 2 H1==title False
   H1: QA Promise Trace Lint | YAML: QA Promise Trace Lint (promise -> scenario chain)
  H2 Inputs (L6)
  H2 Promise page frontmatter (L17)
  H2 Scenario tags (L39)
  H2 Findings (L53)
  H2 Exit codes (L69)
  H2 In CI (L74)
  blocks: code {'yaml': 1, 'bash': 1} tbl 2 bq 0 ul 4 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
== qa-result-ingest-guide qa/toolguides/QA-RESULT-INGEST.md lines 110 pre 7 H1==title False
   H1: QA Result Ingestion — Invocation Contract | YAML: QA Result Ingestion (JUnit-XML Producer)
  H2 Scope: what this ingester does not read (L11)
  H2 Resolving the script (L21)
  H2 Flag surface (L51)
  H2 Exit codes (L69)
  H2 `qa-traceability.yaml` — the produced/refreshed carrier (L81)
  H2 Known limitation (L93)
  H2 Note on this guide's own provenance (L103)
  blocks: code {'none': 1} tbl 2 bq 0 ul 2 ol 0
  yamlkeys ['schema_version', 'id', 'tool', 'title', 'guide_path', 'summary', 'commands']
```
