---
affected_files: []
cycle_number: 1
mission_slug: test-mutation-floors-01M6NSDR
reproduction_command:
reviewed_at: '2026-11-05T16:58:11Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP04
---

# WP04 review cycle 1: REJECT (wording only; the build facts are all correct)

1. **should-fix:** ADR-0060 does not amend ADR-0058, which says mutation-nightly "gates nothing" (0058:67-68) and calls it "the local nightly, report-only shape" (0058:173). Add at the end of the Status section:
   > It also amends ADR-0058, whose Context and References describe `mutation-nightly.yml` as gating nothing and as the report-only shape the SonarCloud nightly follows. From this ADR on, the mutation nightly turns red when a floor is missed; it still gates no pull request and is no part of the required check. The SonarCloud nightly stays report-only, as ADR-0058 decides.
2. **minor:** FR-009 (charter-contract-and-build-retro spec.md:203) has no "experiment" wording; it is in the PIT-cadence decision (:62) and User Story 5 (:158). Reword the supersession accordingly.
3. **minor:** record Option D, PIT's own `mutationThreshold` or floors in `gradle.properties` (#239 design items 1/3, question 4), as not chosen, with its reasons:
   - it is one rounded integer per module, so it cannot express the packs groups;
   - it has no machine-readable summary;
   - floors belong in the module's own build file.
4. **nit:** #239 was adopted by the operator ruling of 2026-11-03. The status should credit 2026-11-03 for the adoption, and 2026-11-04/05 for the floors and scope.
5. **nit:** guide, first code block: comment that packs reads the synced open-packs checkout (`-PopenPacksDir`, see "Syncing the packs").
