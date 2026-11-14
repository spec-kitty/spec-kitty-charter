# Pre-merge squad: R12 glossary contexts and terms

**Point-cut**: pre-merge, 2026-11-14, against `origin/main...feat/106-glossary-contexts` at `ba676f82`.
**Question**: is the landed model canonical and coherent end to end, with one name per concept on the wire, and do the
tests pin what the spec says?

**Cast**: three read-only, profile-loaded delegates, dispatched in parallel.

| Lens | Profile | Verdict |
| --- | --- | --- |
| Correctness and tests | `reviewer-renata` | ship with fixes: 1 MEDIUM, 5 LOW |
| Architecture and contract consistency | `architect-alphonso` | ship with fixes: 3 MEDIUM, 6 LOW |
| Vocabulary and traceability | `lexical-larry` | ship with fixes: 7 MEDIUM, 13 LOW |

Two findings converged: a term may take part in any edge (renata M1, alphonso M1), and the term's id is `termId` in
one schema and `id` in the others (alphonso M2, larry M3).

The operator ruled the MEDIUM findings on 2026-11-14 (rulings A to D below) and asked for the LOW findings to be
folded in. The fixes landed in ten signed commits on `feat/106-glossary-contexts`, `62e58ae6` to `8b3b5782`.

## Operator rulings (2026-11-14)

| Ruling | Decision | Commit |
| --- | --- | --- |
| A | Rename, outright on the unreleased contract: `listActivationVocabularyTerms` and `listActivationVocabularyTaboos` (the Contextive read stays `get`: it answers one document); `ActiveTerm` and `ActiveTermList` become `VocabularyTerm` and `VocabularyTermList`, and the term's id is `id`; `TabooEntry` is `{word, urn, termName}`. A contract test pins the term members of `GlossaryTerm`, `GlossaryContextTerm` and `VocabularyTerm` | `a57f9aa7` |
| B | A `term` endpoint is valid only as the target of a `vocabulary` edge. Every other use is refused in `PackPolicy`, the registration wire and the graph-shard and org-fragment schemas | `62e58ae6` |
| C | A profile's `vocabulary` edge onto `term:<context-id>/<term-id>` lists the term under an activation while its context's winning copy is in force and holds it, and drops it otherwise | `68739262` |
| D | The change fragment drops `open-packs#TBD-glossary-contexts`; the plans and notes point to the Mission's open-packs draft. The issue is filed at closeout | `56c0be5c` |

## Findings and dispositions

**Fixed**: folded into the branch, with its commit. **Ruled**: an operator ruling above. **Known gap**: recorded in
the closeout's "Not done", below MEDIUM, not filed (issue-tracker Rule 1). **Orchestrator**: left for landing.

### reviewer-renata

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| R-M1 | MEDIUM | `NodeKind.TERM` is an edge target of any relation and a source too, so `term:g/t requires …`, `… overrides term:g/t` and `term:g/t overrides …` load; no test covers them | Ruled B, fixed in `62e58ae6`: `NodeKind.edgeSource()` and `edgeTargetOf(relation)`; `PackPolicy.endpoints` and `RelationshipWire` refuse; the edge schemas bind a term target to `vocabulary`; planted `glossary-term-as-an-edge-source` and `glossary-term-as-a-non-vocabulary-target` beside the control, which now loads a `vocabulary` edge onto a term |
| R-L1 | LOW | The `see_also` pattern does not bound its segments to 64 characters or guard a trailing newline, and the domain checks only for a slash | Fixed in `7fc1b2ae`: `glossary.schema.json` bounds both segments to the Slug grammar with the `(?!.*\n)` guard, and `GlossaryTerm` checks each segment with `IdentifierGrammar.isLetterFirstSlug`. The contract's `seeAlso` patterns keep no lookahead, because the contract's OpenAPI validator (RE2) refuses `(?!` and `(?=`; their item descriptions name the 64-character bound (`a57f9aa7`) |
| R-L2 | LOW | `InboundReferences.removedTarget` judges a term removed with its context by the context, so a same-id context elsewhere hides the dangling reference | Fixed in `5c4d2c74`: the term URN is judged when the owner came from `GlossaryTerm::contextOf`. No dedicated test: the case needs a bare reference that resolves in two packs, which the reference rule refuses at upload; it fails closed at the next compose either way |
| R-L3 | LOW | A salvaged stored pack (`PackRegistrationService`, `Before.of(Remains)`) has no terms in its diff, so dropping a term skips the inbound scan | Known gap (closeout) |
| R-L4 | LOW | No scenario edits a glossary file and shows the old ETag then answers 200 | Fixed in `864d845c`: a `vocabulary.feature` outline over the terms, Contextive and term reads re-registers the pack with the context edited |
| R-L5 | LOW | A `see_also` term target in the references read carries no `artifact` link | Known gap (closeout): a usability point, not a correctness one |

### architect-alphonso

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| A-M1 | MEDIUM | `PackPolicy.endpoints` accepts any `edgeTarget` kind at either end; the schemas admit `term` as source and target | Ruled B, fixed in `62e58ae6` (as R-M1) |
| A-M2 | MEDIUM | `GlossaryTerm` uses `id`, `ActiveTerm` uses `termId`, and no parity test pins the three term schemas | Ruled A, fixed in `a57f9aa7`: `VocabularyTerm.id`; `TermCopiesParityTest` asserts the term members (`id`, `name`, `definition`, `examples`, `aliases`, `avoid`, `status`, `seeAlso`) are identical sub-schemas and required alike, modelled on `EffectiveArtifactCopiesParityTest` |
| A-M3 | MEDIUM | A `vocabulary` hop to a `term:` target is listed by the pack read and dropped by the activation read; no test covers it | Ruled C, fixed in `68739262`: the governance walk judges a term by its context's state, and the merged index links it only where the winning copy holds it; `EffectiveGovernanceEntry` gains `termId` (kind `glossary`, the context as `artifactId`, link `effective-term`). Tests in both trees, each with a term only a replaced copy holds: `GovernanceWalkTest`, `ClosureViewGovernanceTest`, `ClosureViewSessionArtifactTest`, `ActivationsEffectiveProfilesTest`, `EffectiveProfileDtosTest` |
| A-L1 | LOW | The two refusal detail texts are written in three places | Fixed in `d005279d`: `TermHolder.contextNotHeld` and `TermHolder.termNotHeld`, used by `ArtifactReadService`, the `GlossaryReads` adapter and `EffectiveReadService` |
| A-L2 | LOW | `EdgeIndexTest`'s helper is still `glossaryPack(…)` | Fixed in `5c4d2c74`: `glossaryHolder` |
| A-L3 | LOW | `PackCompositionResolver.holds` and `InboundReferences.defines` are two predicates for one question | Fixed in `5c4d2c74`: `PackCompositionFacts.holds(Urn)`, used by both |
| A-L4 | LOW | ADR-0064's `decision_status` omits the 2026-11-14 note | Fixed in `166761ee` |
| A-L5 | LOW | `descriptor-doctrine-kinds.puml` still lists `glossary_pack` | Fixed in `166761ee`: `glossary`; the SVG re-rendered with the pinned PlantUML 1.2026.6 (sha256 checked), and `check_docs.py plantuml` passes |
| A-L6 | LOW | `EffectiveGlossaryTermRead.mission.yaml` points `seeAlso` at a copy the project's context replaces | Fixed in `8b3b5782`: `built-in::term:qa/flaky-test`, another context's term, in that example and the context example |
| A-L7 | LOW | The branch is behind `origin/main` and needs a rebase | Orchestrator: this session was not to rebase; the orchestrator rebases at landing |

### lexical-larry

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| L-M1 | MEDIUM | The vocabulary collection reads are `get*` where every other collection read is `list*` | Ruled A, fixed in `a57f9aa7`. `getActivationVocabularyContextive` stays a `get`: it answers one document, as `getOpenApiDocument` does |
| L-M2 | MEDIUM | `ActiveTerm` reads as a status (`active`) and a state | Ruled A, fixed in `a57f9aa7`: `VocabularyTerm`, `VocabularyTermList`, and the application record `VocabularyTerm` |
| L-M3 | MEDIUM | One identity, `termId` in one schema and `id` in the others | Ruled A, fixed in `a57f9aa7` (as A-M2) |
| L-M4 | MEDIUM | `TabooEntry.term` is a URN while `term` is an object elsewhere, and `name` is the replacement's name | Ruled A, fixed in `a57f9aa7`: `{word, urn, termName}` |
| L-M5 | MEDIUM | `open-packs#TBD-glossary-contexts` sits in a Breaking fragment and three docs | Ruled D, fixed in `56c0be5c` (the roadmap's A2 row too) |
| L-M6 | MEDIUM | ADR-0049's ruling names `REFERENCE_UNQUALIFIED` and cites this Mission's FR-014 and FR-015 | Fixed in `166761ee`: `pack_reference_unqualified`, and ADR-0053 decision 7 (FR-014 and FR-015 of Mission `pack-composition-depends-on-and-the-dependency-resolver-01M6F541`) |
| L-M7 | MEDIUM | "deselects, denies or does not select" and "selected, deselected, denied or not selected" are not contract words | Fixed: the terms path in `8b3b5782`, ADR-0076 line 56 in `166761ee` (states `active`, `inactive`, `excluded`; reasons `deactivated`, `not_selected`) |
| L-L1 | LOW | Three relation names reach one target | Fixed in `a57f9aa7`: the `effective-glossary` row says it targets what `effective-artifact` targets for kind `glossary` |
| L-L2 | LOW | Three term shapes are easy to confuse, and `EffectiveGlossaryTermRead`'s term carries no `urn` | Folded: `TermCopiesParityTest` and the `VocabularyTerm` description name the three as one term (`a57f9aa7`). No `urn` was added to the effective term read: a client has `artifactId` and `term.id`, and the shape is the one ADR-0074 decision 11 ruled |
| L-L3 | LOW | `description` and `appliesTo` are both described as "where it applies" | Fixed in `8b3b5782`: a context's `description` is what it covers |
| L-L4 | LOW | ADR-0076 frontmatter dates, line 18 "avoid projections", line 118 "no glossary in force", line 123 "links `terms` as a templated relation `term`" | Fixed in `166761ee` |
| L-L5 | LOW | `descriptor-schemas.md` says the glossary is built and that today's shape is old | Fixed in `8b3b5782`: the old-shape sentence covers the kinds not built |
| L-L6 | LOW | `term:<context>/<term>` in `logical-model.md` and the pack schema index | Fixed in `8b3b5782`: `term:<context-id>/<term-id>` |
| L-L7 | LOW | `ArtifactKind.yaml` says "the glossary"; `EffectiveETag.yaml`'s "each vocabulary read has a tag of its own" sits inside the list | Fixed: `ArtifactKind.yaml` in `8b3b5782`, `EffectiveETag.yaml` in `a57f9aa7` |
| L-L8 | LOW | The glossary-replaces fragment says `surface` is gone and cites C-001; the twins fragment says each winner names its pack | Fixed in `8b3b5782`: `surface` is renamed `name`, no C-001, and an item's `artifact` link leads to its pack's copy |
| L-L9 | LOW | The packs CHANGELOG entry is `### Changed` and cites ADR-0076 | Fixed in `8b3b5782`: it cites ADR-0049 and ADR-0053. It stays under `### Changed`: packs contract `0.1.0-SNAPSHOT` is unreleased (closeout) |
| L-L10 | LOW | "banned vocabulary" beside "taboos" | Fixed: the contract schemas in `a57f9aa7`, `glossary.schema.json` and `GlossaryTerm` in `7fc1b2ae`, the taboos path in `a57f9aa7`. ADR-0049 decision 5's history keeps its words |
| L-L11 | LOW | `glossary.feature`'s title "no configured pack answers to" | Fixed in `8b3b5782`: "matches" |
| L-L12 | LOW | Spec FR-002 says `see_also` holds `term:` URNs | Fixed in `8b3b5782`: term references, bare or pack-qualified |
| L-L13 | LOW | `site/.contextive/definitions.yaml` has no "term in force", "vocabulary" or "taboo list", and calls a term an "entry" | Known gap (closeout): a proposal for curator-carla |

### Raised at the squad's close

| # | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| O-1 | LOW | The generated pack README's `content_hash` stamp (orchestrator) | Known gap (closeout) |
