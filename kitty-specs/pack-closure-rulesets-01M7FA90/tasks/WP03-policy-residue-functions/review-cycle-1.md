---
affected_files: []
cycle_number: 1
mission_slug: pack-closure-rulesets-01M7FA90
reproduction_command:
reviewed_at: '2026-11-16T20:43:52Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

**Issue 1 (MEDIUM, dead code):** three public delegates in `PackPolicy` have no caller anywhere: `descriptorFindings` (:574), `assetPathEscape` (:701) and `joinsItself` (:713). Delete them. Inline the private `descriptorRules` (:563) into its only call site (:135) as `DescriptorChecks.findings(...)`. Repoint the javadoc `{@link PackPolicy#joinsItself}` at `DomainRepositoryService.java:768` to `GraphBinding#joinsItself`.

**Issue 2 (LOW, fold now):** 16 test files outside `PackPolicy*Test` still call `PackPolicy`'s delegates. Repoint them to `GraphBinding`, `Wording`, `Paths` and `Precedence`, so that the remaining delegates serve only `PackPolicy*Test`, which is the R4(b) staging WP13 deletes. The files:
- `infra/api`: WireTokenParityTest, RelationshipWireTest
- `infra/storage`: GraphShardWriterTest, PackRootSpellingTest, ParsedYamlTest, PackDescriptorReaderTest, OrgCharterRetirementTest, MissionTypeBindingTest, GraphDocumentReaderTest, VocabularyEqualityTest (:331), GlossaryAndSkillSchemaTest, RetiredFilesTest, SchemaKeywordCoverageTest, GlossaryOldShapeRefusalTest
- `domain`: StepEnhancementRulesTest (the comment at :459), RelationshipEndpointRuleTest (:152)

Then delete any delegate left with no caller.

**Issue 3 (LOW, fold):** some javadoc prose still says `PackPolicy` binds the graph or tension: `GraphDocumentReader.java:22-23, :126` and `RelationshipMapper.java:8`. Repoint it to `GraphBinding`.

Re-run `:packs:test` and the `HexagonalArchitectureTest` through the Gradle lock.
