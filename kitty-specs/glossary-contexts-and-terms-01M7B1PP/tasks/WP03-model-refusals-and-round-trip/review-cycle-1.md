---
affected_files: []
cycle_number: 1
mission_slug: glossary-contexts-and-terms-01M7B1PP
reproduction_command:
reviewed_at: '2026-11-14T12:24:56Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

# WP03 review, cycle 1: changes requested

Reviewer: claude::reviewer-renata. Range: `17c755472bf9..d387fadcd401`.

T015, T016, T017 and the round-trip half of T018 are good (see "What passes"). The broken-reference half of
T018 is not done. That half is an acceptance criterion of this WP. It is spec edge case `spec.md:131` and
`data-model.md:33` and `:56`: "a `see_also` URN, and a `vocabulary` edge target `term:<ctx>/<term>`, that no held
context resolves are each reported as a broken reference, as other unresolved references are … add one test per case,
with the resolving case as its positive control. If the existing check cannot see `term:` targets, extend it in the
class that owns reference resolution". No later WP (WP04 to WP07) picks this up.

## HIGH 1: every `term:` reference is judged unresolved, including the ones that resolve

The one answer to "does pack P hold URN U" is `PackUnderResolution.holds`
(`service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java:95-97`), which is
`definedArtifacts.contains(urn)`. `definedArtifacts` comes from `PackCompositionFacts.defined()`
(`OfferingReadService.java:463`). `DomainRepositoryService.compositionFacts` (`infra/storage/DomainRepositoryService.java:544-563`)
only puts `declarableInGraphs()` artifact URNs in it, so it never holds a `term:` URN. As a result:

- `EdgeIndex.holds` (`application/EdgeIndex.java:494-499`) is false for every term place. So `links()` drops every term
  edge, and `writtenLinks()` (`:454-468`) lists every `vocabulary → term:` edge with `held: false`. The pack-scoped
  governance read then reports a dangling parent for a term that is there.
- `PackCompositionResolver.holderOf` (`:390-410`) finds no holder. The references read
  (`ArtifactReadService.java:584-603`) and `ComposedRelationshipTargetCheck` therefore treat a resolving `term:` target
  exactly like a missing one.
- `checkQualifiedReference` (`PackCompositionResolver.java:506-515`) skips any URN whose kind has no artifact kind. So
  `base::term:g/missing` is not refused `REFERENCE_ARTIFACT_MISSING`, while `base::directive:MISSING` is.

So the reads cannot tell a broken term reference from a sound one. That is the opposite of "reported as other
unresolved references are".

**Do not fix this by putting term URNs into `defined()`.** You were right that it is too wide, and it is also wrong:
`LayerMerge.java:291`, `PackCompositionResolver.java:613/742`, `EdgeIndex.heldUrns` (`:384-391`) and
`ClosureQueries.java:280` would treat each term as an artifact. Terms would then merge one by one, which breaks
"the context overrides whole" (spec story 4, scenario 3), and they would be listed as artifacts.

**The fix: a term-aware step where holding is judged.**

1. **A fact.** `PackCompositionFacts` gains `terms`: the term URNs the pack's contexts hold, as bare URN text. It is a
   sibling of `languages` and `bindings`. The existing convenience constructors default it to empty, so the roughly 40
   test call sites do not change. Only the canonical-constructor callers change: `DomainRepositoryService` and the three
   in `WriteFacts`, which must carry it through. Fill it in `compositionFacts` in the same artifact loop
   (`DomainRepositoryService.java:545-560`): for each `GlossaryContext`, add `GlossaryTerm.termUrn(ctx, term.id())` for
   each of its terms.
2. **The one answer.** `PackUnderResolution` gains `heldTerms`, and the existing 3-arg and 4-arg constructors default
   it to empty. `OfferingReadService.java:463` passes `held.terms()`. `holds(urn)` becomes: for a `term:` URN,
   `definedArtifacts.contains(GlossaryTerm.contextOf(urn))` and `heldTerms.contains(urn)`; for any other URN,
   unchanged. `holderOf`, the references read, the relationship target check and `EdgeIndex` all go through this one
   method, so they all get the fix.
3. **Whole-context override.** In `EdgeIndex.holds` and `wins` (`:228-230`, `:494-499`), judge a `term:` place's `wins`
   on its context's place, `new Place(place.pack(), contextOf(urn))`. The term URN is never a key of `winners`, so
   without this a losing copy of a context would still hold its terms in a merged index.
4. **Parity with qualified references.** In `checkQualifiedReference`, also refuse a qualified `term:` URN that the
   named pack does not hold (term-aware `holds`), with `REFERENCE_ARTIFACT_MISSING`. This is the code a qualified
   reference to a missing directive already gets, so it is no new code.

**Tests (one per case, with its control).** Use the same fixture as `InboundReferencesTest`, which already has
`base::term:g/lane`:

- a vocabulary edge to `base::term:g/lane` is `held: true` on the pack-scoped walk and resolves on the references read;
- `base::term:g/nope` (context held, term not) is `held: false` and unresolved;
- `base::term:zz/lane` (context not held) is `held: false` and unresolved;
- the qualified missing term is refused `reference_artifact_missing`;
- a merged closure whose winning copy of `g` lacks `lane` does not hold `term:g/lane`, and the closure without the
  override pack holds it.

## HIGH 2: a dangling `see_also` is reported nowhere

`GlossaryContext` overrides neither `derivedRelationships()` nor `writtenReferences()`. So a term's `see_also` never
reaches `typedEdges` (`DomainRepositoryService.java:628-636`), `facts.references()`, the references read
(`ArtifactReadService.java:584-586`) or the governance walk. A `see_also` naming a term no pack holds is accepted and
never mentioned.

**The fix: follow the `Guard` precedent** (`domain/Guard.java:130-147`, ADR-0064). A field-authored reference is a
derived relationship: runtime data, never authored, and never compared by the round trip. Have
`GlossaryContext.derivedRelationships()` return one `vocabulary` relationship from `glossary_context:<id>` to each
distinct `see_also` URN of its terms, in term order. Check that the relation table allows a `glossary_context` source
for `vocabulary`. If it does not, say so in the WP notes and use the relation the table allows. With HIGH 1 fixed, a
`see_also` then:

- resolves on the references read and the pack-scoped walk when the term is held, and is `held: false` or unresolved
  when it is not;
- counts as a referrer in `InboundReferences` when a replace removes its context.

**A consequence to state, not hide.** The default `writtenReferences()` derives from these relationships, so they also
enter the compose reference check. A bare `term:` URN held only by another pack of the closure is then
`REFERENCE_UNQUALIFIED` (FR-014 and FR-015), which is the rule for every bare reference. The `see_also` grammar
(`GlossaryBinding.java:86`, `List<Urn>`) cannot qualify a reference, so a cross-pack `see_also` would become
impossible. Check the built-in `spec-kitty-core` glossary (all its `see_also` entries should be in-context). Then
either:

- record in `data-model.md` that a `see_also` resolves as a bare reference, in its own pack, under the canonical
  reference rule; or
- if you think a cross-pack `see_also` must be allowed, stop and raise it as a decision for the operator. Do not add
  an exemption from the reference rule.

**Tests:** a context whose term has `see_also: [term:core/mission]`, which is held (the control: resolves, `held:
true`), and the same context with `see_also: [term:core/nope]`, which is unresolved or `held: false` on the same
reads. Add a `GlossaryContextTest` row asserting the derived relationships. The round trip must stay green: derived
relationships are not compared (ADR-0064), and `WholePackStore.java:636` already collects them.

## MEDIUM (recommended fold-in, cheap once HIGH 1 lands): dropping one term while keeping its context

`PackRegistrationService.requireNoInboundReference` (`application/PackRegistrationService.java:568-571`) builds the
removed set from `defined()` only. Your `contextOf` extension (`InboundReferences.java:265-266`) covers removing the
whole context. A replace that drops only `lane` from `g` leaves `derived`'s edge to `base::term:g/lane` dangling, with
no refusal. Removing the context, or any artifact, is refused `artifact_referenced`, so the rule is half applied.

Once `facts.terms()` exists, add `before.terms − now.terms` to `removed`. `Before` needs the terms too (`:472`).
Then add a third row to `aVocabularyEdgeOntoATermOfARemovedContextRefusesTheReplace`: drop `lane` from `g`, which is
refused, and drop another term of `g`, which is the control. If this turns out not to be small, say so in the WP
notes instead of leaving it silent.

## LOW (fold in)

- **Validate mode accepts repeated term ids** (`planted/glossary-term-id-duplicate/expected.json`: `findings: []`). This
  is the only planted case where validate finds nothing and load refuses. The class comment
  (`GlossaryPlantedRefusalsTest.java:39-42`) calls this the pattern for domain rules, but the near precedent,
  `duplicate-step-id`, is a validate finding (`PackPolicy.java:512-529`). This mission also already reports a non-schema
  rule, the file stem, as `artifact_schema_error` at `id`. Either report the repeat as `artifact_schema_error` at
  `terms[<second>].id` in validate mode (an existing code, so nothing is invented), or correct the comment so it does
  not claim a precedent that is not there. The first is preferred: a pack that validates clean and is then refused at
  registration is a validator false negative.
- **`glossary-context-id-not-kebab` has two faults.** `id: Core` in `core.glossary.yaml` breaks both the pattern and
  the stem rule. Only one finding is expected, so this is harmless, but the "one fault each" claim does not hold for
  it. Either note it in `expected.json` covers or the test comment, or move the case to a file named `Core.glossary.yaml`
  if the store allows that (it probably does not, so a note is enough).

## Accepted deviation

The write refusal answers `422 activation_invalid` with `unknown_member` at `/selected/glossary_pack`, not
`validation_failed`. That matches `activation-register.feature:463-474` for any undeclared member, so R2's wording
was the error. Please add one line recording this in `research.md` R2, or in the WP notes for WP08's records, so the
docs do not keep the stale code.

## What passes

- **T015.** Eight planted cases. Each refusal differs from `glossary-context-valid` by one edit (checked by diff), with
  real codes: `artifact_schema_error` at the exact places, and the old shape refused as `retired`, naming the file.
  `GlossaryPlantedRefusalsTest` runs validate, load and registration, and the control loads. No core-verdicts row, as
  for `asset-id-not-kebab`.
- **T016.** `IdentifierBoundsTest` has rows for both identifiers in the bounds list (64/65) and the verdict rows: `a--b`,
  digit-first, uppercase, trailing hyphen, empty, blank.
- **T017.**
  - `ActivationGlossaryPackRefusalTest` refuses `selected` and `deactivated`, naming the id and the cause.
  - Its control is byte-identical canonical JSON with only the member changed, which is a strong control.
  - Corpus document `invalid/19` and its row in `invalid-reasons.json` are in place.
  - The feature file has a positive control in each scenario.
- **T018 round trip.** `theBuiltInGlossaryIsModelledAndRoundTripsGreen` proves #547's failure mode with a planted
  round trip that does not model the kind. That round trip fails both drift (`UNEXPECTED_RED … no adapter models kind
  glossary`) and `UNMODELLED_SCHEMA`.
- **WP02 fold-ins.**
  - `PolicyYaml`: the `glossary` key maps to `GLOSSARY_CONTEXT`, is written back under `glossary`, and the URN token is
    refused as a key.
  - `GlossaryContextTest` has a named positive control.
  - The schema rows refuse a term with a required member missing or null.
- **Anti-pattern checklist.**
  1. Dead code: PASS. `GlossaryTerm.contextOf` has a live caller in `InboundReferences`.
  2. Synthetic fixture: PASS. The tests drive the loader, the store and the registration.
  3. Silent empty return: PASS. `contextOf` returning empty is documented.
  4. FR coverage: PASS for FR-003 and FR-005. T018's broken-reference criterion is FAIL, as above.
  5. Frozen surface: PASS.
  6. Locked decision: PASS. The old shape appears only in refusal tests.
  7. Shared files: N/A. The out-of-map edits are recorded in the commit.
  8. Production fragility: PASS.
