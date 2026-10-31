# Research: pack-model cleanup (#271, #272, #273, #277)

Follow-ups of #260 (PR #270) and #194 PR-B (#275, ADR-0044). Read-only research, 2026-10-31, on
`issue-271-273-277-pack-model-cleanup` at `5fb3d46e` (origin/main), open-packs at the pin `86d9284`.
Op `01M69B3SGYMKG9Q659RQANHB9B`.

## Run shape

A `spec-kitty dispatch` Op, not a mission: one tidy-first move (#271), three small behaviour changes
(#271's build-time check, #272, #273) and one domain check with an ADR-0044 amendment (#277). They all
sit in `service/packs` and `service/missions`. There is no contract or API shape change. The two open
choices (#277's representation, and #273's wording) go to the operator before anything is built.

Size: one reviewable PR. The #271 move touches about 30 files, but only in their imports. It lands as
its own structure-first commit, so the review can skip it. The behaviour commits are small.

## #271: one home for the Markdown model's content rules

- `MarkdownDocument` and `MarkdownScanner` in `packs.infra.storage.markdown` import only `java.*`
  and JSpecify. `MarkdownRefusal` implements the codec's sealed `MarkdownCodec.Outcome`. The codec
  needs `FrontmatterYaml`, which is infra.
- **The move.** `MarkdownDocument` and `MarkdownScanner` move to `packs.domain.markdown`, which
  `PACKS_DOMAIN_IS_PURE` covers (`..packs.domain..`). `MarkdownRefusal` stays in infra: it is the
  codec's reading outcome, and the sealed `Outcome` must keep its permitted types in its package.
  The scanner's own stop (`Refused`) carries the line and the reason, and the codec turns it into a
  `MarkdownRefusal`. The members of the scanner that the codec uses (`scan`, `Line`, `Kind`,
  `Refused`, `refused`) become public.
- **The drift.** `Guide.standsAsSectionContent` is a hand copy of the block rule. It does not refuse
  an H1 or H2 indented by 1 to 3 spaces, an empty heading, a tab after `#`, a closing `#` sequence or
  a setext underline. The scanner refuses all of these.
- **Today's PackDocument.** It checks its titles only. The block rules (and the rule that a title
  must read back as itself) are applied at the write, by `PackDocumentMarkdown.toWritableText`, as
  `VALIDATION_FAILED` naming `pack.md`.
- **Reach of a stricter build-time check.** No content that loads today is refused:
  - Every `Guide` is built from a codec-parsed document. This holds for the store read
    (`GuideMarkdown.fromStored`) and for the API (`GuideFormat` and `MarkdownGuideFormat` parse
    first).
  - Every loaded `PackDocument` is built from a codec-parsed document (`PackDocumentMarkdown.toDomain`).
  - A section's content scanned on its own is in the same scanner state as inside the document
    (after an H2 and a blank line, with no fence open and no paragraph above). So the block rule
    judges it the same way.
  - The only callers that see a change are those that build the domain values in memory: tests, and
    the whole-pack gate's `writePackDocument` seam, which no production path calls. Their refusal
    moves from the write to the build.
- **Plan.**
  - `MarkdownDocument` exposes its rules as problems: `blockProblem`, `headingProblem` and
    `openingProblem`. Its constructor uses them.
  - `headingProblem` adds the read-back rule (a heading that ends in a closing `#` sequence) from the
    scanner. Today only the codec's write refuses that, through `IllegalStateException`.
  - `Guide` and `GuideSection` guard their content with `blockProblem`, raising
    `ConstraintBreachException`.
  - `PackDocument` and `PackDocument.Section` guard the title, the preamble, the content and the
    opening the same way.
  - `PackDocumentMarkdown.toWritableText` no longer refuses, because a `PackDocument` is always
    writable.
  - `DomainRepositoryService.assembly` also catches `ConstraintBreachException` for the document.

## #272: companion paths classified case-folded

- `Companions.claimedByRule` (`Companions.java:173-195`) classifies through `PackFileKind.classify`,
  whose globs compile with no flags, so case matters. The write's claim folds case:
  `Inside.claim()` is `toLowerCase(Locale.ROOT)`, and `WholePackStore.put` at :507.
- **Plan.** Classify the companion path case-folded, by folding both sides, because rules such as
  `README.md` are upper case. Use a folded classification that only `claimedByRule` calls, so load and
  validate classify real files exactly as before.
- **Red-first test.** It goes next to `WholePackStoreTest.aCompanionThatIsThePackDocumentIsRefused`,
  with `body_path: ../PACK.md` on any file system. The test asserts the same violation and that the
  skill is dropped.
- **Reach.** No `body_path`, `guide_path`, `prompt_path` or `guidelines_path` at the open-packs pin
  holds an upper-case letter. Nothing that loads today is refused.

## #273: one wording for "not UTF-8"

- **Store.** `FilesystemPackStore.java:1012` and `WholePackStore.java:226` say "the file cannot be
  read as UTF-8 text". The companion reason at `Companions.java:264` says "cannot be read as UTF-8
  text".
- **Validate mode.** `FileSystemPackInspector.NOT_UTF8` says "the file is not valid UTF-8". This
  wording is pinned in a load fixture (`shard-not-utf8/expected.json`) and quoted in
  `contracts/charter/CHANGELOG.md` (448-450).
- **Plan.**
  - One package-private constant next to the shared strict decoder in `infra.storage`, used by all
    three readers. The companion reason reads `<path>: is not valid UTF-8`.
  - The parity test (`PackDocumentRoundTripTest.validateModeAgreesWithTheLoad`) then pins one reason
    for both readers.
  - Fix the quote in ADR-0039 (line 152). Add a contracts CHANGELOG line, because the load's reason
    reaches the API's findings.

## #277: a step output's `template_path`

- ADR-0044 decision 7 keeps `template_path` verbatim and checks that it names a template file on read,
  on write and in validate mode. That check is `FilesystemPackStore.missingNamedFile`, recorded as a
  `PackElementViolation(file, "reference", "<path> (named by step '<id>'): …")`. It does not resolve the
  path to the pack's `Template` entity, and it leaves resolving to #277.
- **Dependency direction.**
  - `ExpectedArtifact` lives in `service/missions`, which depends on the kernel only.
  - `TemplateIdentifier` lives in `packs.domain`, and `PACKS_DOMAIN_IS_PURE` keeps `packs.domain`
    off `missions.domain`.
  - A typed `TemplateIdentifier` on `ExpectedArtifact` (option A) would reverse ADR-0044's direction.
    Holding a template URN as a kernel type would need a new kernel type.
- **Byte fidelity.**
  - All 13 `template_path` values at the pin (software-dev 2, research 2, plan 2, documentation 2,
    qa 5) and the vendored qa type are spelled `templates/<file>.md`, and all of them resolve.
  - The current rules admit `templates/./x` and `templates//x`, which a typed reference would
    normalise away. Option A must therefore refuse them as well, or the write is not byte-faithful.
- **Recommendation: B.**
  - Keep the path. Resolve it in the missions domain against the pack's template ids
    (`<type>/<path after templates/>`, the template URN).
  - Refuse an unresolved path with a typed violation that names the type, the step (or "the type")
    and the path.
  - Wire the check where the aggregate is assembled (load) and before the whole-pack write. An
    unresolved type is dropped with the violation, as the read's file check does.
  - Record the choice as an ADR-0044 amendment (decision 7).

## Open-packs

None of the four changes refuses content at the pin, so no open-packs issue is drafted.

## Follow-ups (from the pre-PR squad, not in this PR; filed under #186)

- **`StepDocument` keeps no Markdown rule** ([#299](https://github.com/spec-kitty/spec-kitty-charter/issues/299)). The missions domain's step prompt and guidelines
  (`StepDocument`) have the shape of a Markdown document. They check only blank texts, so a
  prompt whose preamble holds a column-0 H1 is refused only at the write
  (`MissionTypeBinding.markdownOf`). `MISSIONS_DOMAIN_IS_PURE` keeps `:missions` off
  `packs.domain.markdown`, so giving it the one rule needs one of two changes:
  - move the line rules to the kernel; or
  - accept the write-time refusal for step documents and record it in ADR-0044.
- **A template path in another case on a case-insensitive file system**
  ([#300](https://github.com/spec-kitty/spec-kitty-charter/issues/300)). The read's, the
  per-file write's and validate mode's named-file check (`missingNamedFile`) follows the file
  system. So on macOS, `templates/Spec.md` passes the check when the file is `spec.md`, and the
  load then refuses the type as unresolved. Two possible fixes:
  - compare the real file name in `namedFileProblem`; or
  - resolve against the template ids in the per-file write too.

  Validate mode builds no domain, so it does not resolve at all.
- **The `cannot be read` branch has no test** ([#301](https://github.com/spec-kitty/spec-kitty-charter/issues/301)). An I/O failure between listing and reading is
  `the file cannot be read`, or `<path>: cannot be read` for a companion. A test needs a
  read-failure seam: `chmod 000` does not stop root, which is how the CI and cloud runners run.
