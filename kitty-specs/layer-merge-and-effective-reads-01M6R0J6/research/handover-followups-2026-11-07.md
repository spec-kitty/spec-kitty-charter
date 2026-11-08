# Mission follow-ups / review items (activation-register)
- WP02 minor: IllegalArgumentException message "no configured pack is configured as" reads awkwardly.
- WP03: ADR numbered 0062 (0060 main, 0061 held by #392/#393) — re-check at closeout; check_docs gap.
- WP03: WholePackStore.write 71 lines (>60; was 75 before).
- WP04: same-level duplicate preset id → last-in-merge-order wins silently (presets aren't artifacts, so pack_sibling_conflict doesn't catch it). Operator ruling / follow-up.
- WP04: floor counts must-have items of a preset replaced by a higher-level preset with the same id, while presets map reports only the replacement (ADR-0059 D3 grain). Reconcile.
- WP06: RequiringPack.via always [] (no extends chain in snapshot).
- WP05: deployable may name generated types (bounded by bad fixture); reserved-method rule skips lambdas.
- WP13: per-artifact write violations should use empty pointer / path-param context, not sorted-list pointers.
- WP09: add HTTP test that ActivationStorageException → 500 internal_error problem body, no stack leak.
- DECISION (orchestrator, WP09): getActivation and listActivationPacks 200s carry no Charter-Source-Fingerprint (whole-source digest); getActivation depends only on the stored doc (ADR-0059 D5); closure read signals change via its ETag (revision + closure digest). Record in research open review items + contract fragment.
- WP09 suggested extra scenarios: null list item 422, version pin ≠ held 422, stored-deny-now-required variant.
- WP09: PacksAvailability catches any IllegalArgumentException → may mask a bug as stale_input when every root is configured; narrow the catch.
- WP09: put answers 422/409 before checking If-Match 412 (ordering of preconditions vs validation).
- Stray test docs at ~/.spec-kitty/charter/activations (a-1, project, 01M6PTDY…) from an early WP09 run; harmless, ephemeral container.
- WP16: four activation refusals (unreadable body, invalid value, 415, bad id) only proven via standalone MockMvc; test Javadoc overclaims full coverage. Refusal rules could drop the packs adapter now.
- WP13: exclusions with kind=mission_type must be refused (404 kind_not_found or 422) before the domain throws; never 500.
- Register DELETE answers 412 for If-Match on a missing activation while sub-resource writes answer 404 (RFC 9110 §13.2.1 favours 404 first): follow-up to align.

## WP14 review (non-blocking)
- Stale preset member 422 is not described in the presets path description or the fragment; the violation detail names the artifact, not the preset. -> WP15 docs
- No BDD for the stale-member 422 or a template/asset-only gate (unit tests only)
- WP14 owned-files list names the fragment 2026-11-05-...; the file is 2026-11-06-...
- The ActivationBodyAdvice body-shape 422 is answered before 428 on every write (shared-flow behaviour)

## WP15 review (fold at squad-fix time)
- offering-and-activation.md:286,308 mission-type parentheticals unreachable (CascadeScope excludes mission_type); CascadeRules javadoc carries the same dead branch -> reword docs + javadoc
- offering-and-activation.md:299 "pulled artifact in cleared and applied" -> only a denied one
- resolution-model.md:137 prefix core's --force refusal with "In core"
- stale-preset-member 422 missing from the applyActivationPreset description and activation-presets.feature -> contract fold
- PR A behaviour tightened in lane-m f2a5e08: create/replace now 422 wrong-type scalars and duplicate members (were coerced / last-wins). Note in PR B description.
## A0 handoffs (from WP03 review)
- WP08: ActivationConditionalRequestTest (deployable, unowned) has 14 failures from v1 bodies; T039 runs *Activation*, so WP08 takes it.
- WP04: mission-type id normalization in document bodies (case/URN, ambiguous) vs the exact per-artifact path; make the two agree.
- WP09: the ruleset page's W5 rule still lists unknown_mission_type; now unknown_artifact at /selected/mission_type/i.
## Sync with main (do at consolidation, before PR A0)
- #398 landed rebased (tip da1e462f); #399 landed squashed as 418fb689 with one operator edit in docs/architecture/resolution-model.md (line 95: "returning an active enhancement at read (ADR-0059 A4)").
- The mission branch has #399 only up to 1d37ccd9. First merge origin/issue-291-activation-writes (8188c996, a fast-forward ancestry), then merge-tree --merge-base=8188c996 into origin/main and commit with parents (HEAD, origin/main), signed.
- #398/#399 subscriptions ended (merged). The stale local branches issue-360 and issue-291 and the fold-a/fold-b worktrees are left for the operator; do not delete remote branches.
- WP08: map Written.dropped/droppedPins into the replace answer; map ActivationChange.droppedPins in ActivationChangeMapper; the replaceActivation contract text still says stale ids are 422 (A4 changes that).
- Docs WP (WP09): divergences: replace drops stale ids and unheld pins; mission-type path ids read in any spelling; presets refuse activated_kinds and may list skills and glossary packs; core's activated_kinds narrowing no longer honoured; v1 files upgraded on read.
- WP08 T038: changes fragment + divergences for A15 lock narrowing, B8 own-policy 409→success, A10 unheld floor items, A14 sibling enhancers compose; composition.feature changed (enhances→overrides in sibling contests).
- From WP05: WP08: activation-selections.feature "includeDeactivated clears a denied ancestor inside the scope" expects the old clearing; SelectionRequest.yaml includeDeactivated wording; a B5 changes fragment. WP04: ActivationWrites.deniable is unused (delete). WP24: ClosureViewSession drops ways from an action (requiredBy empty through steps). Divergences: B5, mission-type cascade and deny.
- OPEN: packs don't hold action:M/s, so the Q1 step cascade reaches nothing in production; decide where step holding belongs.
- From WP04 review, for WP08:
  - A4 drops must reach the client: add dropped/droppedPins to the replaceActivation response (contract addition, out-of-map from WP01's files), or answer an ActivationChange-like envelope; decide minimal.
  - Fix the replaceActivation description (stale ids no longer 422; "never drops an id" is now false).
  - Rewrite the activation-register.feature scenarios for refused-on-write, re-deny of now locked/required, and pin refused on PUT.
  - Document and test the 412 when all 8 no-If-Match attempts lose.
- Nit: ActivationChange.withDropped has only a test caller (WP04, or a clean-up later).
- From WP07 review: WP08 T038 needs a scenario showing sibling enhancers compose (A14) and the A15 lock narrowing; WP22 must surface floor.unheld(); the A10 DELETE half goes to WP14 T067 and WP19.
- WP05 ruling (orchestrator): holding steps stays in WP05 (action added to declaredWithoutFile); tell WP21 about EdgeIndex.stepsOf and the DomainRepositoryService edit.
- WP22 dispatch brief MUST include: bind the A10 report-half @WIP scenario in activation-ruling-changes.feature (split out by WP08 cycle 2) and surface floor.unheld().
