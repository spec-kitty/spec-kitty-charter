---
affected_files: []
cycle_number: 1
mission_slug: slice-b-real-packs-register-01M7649C
reproduction_command:
reviewed_at: '2026-11-11T21:43:36Z'
reviewer_agent: claude-reviewer
wp_id: WP02
---

WP02 review 1 (reviewer-renata, opus): REJECT.
M1: commit 6c9edc5b is red on its own: it adds the retired rules to file-kinds.yaml but RetiredFileFixture.RULES gains them only in 28ffda77 (RetiredFilesTest.everyFamilyIsClassifiedRetired, :51-53). Move the RULES/rules() additions into 6c9edc5b.
M2: FR-016 half done: contracts/packs/README.md:41-42 still says `text` is "a form the service does not build yet". Reword to match file-kinds.yaml:33-37.
L3: RetiredFilesTest.java:242-243 comment claims case-insensitive-FS safety; plants colliding names. Plant variants in a second pack dir or fix the comment.
L4: the wider case-variant scope (pack.yaml, policy.yaml, pack-manifest.yaml) is untested: add Pack.yaml and Policy.yaml cases.
L5: PackFileKind.caseVariant relies on `compiled` holding no payloads (i >= files+exclusions); make the retired range explicit.
L7: add a PackFileSetExactnessTest mutant that drops RETIRED files from the digest; file-kinds.yaml:35-36 "no `files` rule names it".
Also: list the out-of-map edits in the commit message.
