---
affected_files: []
cycle_number: 1
mission_slug: mission-step-contract-round-trip-01M5YHYD
reproduction_command:
reviewed_at: '2026-10-27T12:13:36Z'
reviewer_agent: claude
wp_id: WP05
---

# WP05 review feedback (cycle 1) - reviewer-renata

Verdict: changes requested. The changelog is accurate and in house style; the ADR amendment is
append-only and well placed, but two sentences do not match the code. Small wording fixes only.

Verified true: 21/21 built-in step contracts green (expected-red entry removed, 9e5a70d); 28/28
corpus (MissionStepContractCorpusTest asserts 21 built-in + 7 qa); new binding appended last in
KindBindings, first five order pinned by KindBindingsTest, so only packs with step contracts change
fingerprint; RelationshipRegistrationService checks `from` against `pack.artifacts()` URNs, which
now include mission_step_contract artifacts; KindBindingsTest ties fileName and schema to
file-kinds.yaml; FileSystemPackInspector still does `kind.replace('-', '_')`; README line in order.
check_docs all: ok. cspell: 0 issues. markdownlint: no findings in the 3 files.

## Must fix

1. [MEDIUM] docs/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam.md:134 - "The store and the
   strategy resolve a schema to its kind through the registry (`KindBindings.bySchema`)" is true of
   the store only (PackFileSet.loaded uses bySchema). YamlSerializationStrategy.read goes the other
   way: it asks the binding for its schema (`kind.schema()`) and validates against it; it never calls
   bySchema. Recommendation: e.g. "The store's file walk finds a schema's kind through the registry
   (`KindBindings.bySchema`), and the strategy validates against the schema the binding names,
   never by reading a schema name as a token."

2. [MEDIUM] docs/adr/0037-...md:140 - "That one change to shared test code is the exception to
   Consequences/Positive" understates it: this mission also edited the store (PackFileSet),
   the strategy (YamlSerializationStrategy) and DomainRepositoryService (saveArtifact widened to
   package-private for the gate adapter) to add the hook. Recommendation: say the hook itself is a
   one-time change to the store and the strategy, and that after it a kind joins by binding itself;
   mention saveArtifact's visibility if you keep the "exception" sentence.

## Should fix

3. [LOW] docs/adr/0037-...md:132 and docs/changelog/2026-10-27-mission-step-contract-kind.md:41 -
   "`agent_profile` (#190) uses the same hook" / "Agent profiles (#190) use the same hook": on this
   branch no agent_profile binding exists, and on origin/feat/190-agent-profile-round-trip the hook
   is named `KindBinding.schemaName()`, not `schema()`. Recommendation: name the concept rather than
   the method for #190 ("#190 binds `agent-profile` and `agent` the same way"), or reconcile the
   method name with #190 before merge; flag the naming collision to the orchestrator.

## Notes (no action)

- Placement after decision 7 instead of "right after decision 4" is right: inserting inside the
  numbered list would break it. Heading form matches ADR-0024/0006/0002.
- `updated:` was already 2026-10-27, so no frontmatter change was needed.
