---
affected_files: []
cycle_number: 1
mission_slug: slice-b-real-packs-register-01M7649C
reproduction_command:
reviewed_at: '2026-11-12T02:36:00Z'
reviewer_agent: claude-reviewer
wp_id: WP05
---

# WP05 review, cycle 1: GET /packs/{packId}/doc and the doc link (#451)

Reviewer: Reviewer Renata (code review), with the Architect Alphonso lens on the contract.
Worktree: `.worktrees/slice-b-real-packs-register-01M7649C-lane-d`. Commits reviewed: `0e0d8e56` and `8f4ca676`
(diff `00938272..8f4ca676`, kitty-specs excluded).

## Verdict: REJECT

There is one MEDIUM finding (M1). A README.md that the store and registration accept answers 500 on both forms of the
document read, while the pack still advertises `doc`. The fix is small and local. Apart from M1, the WP is in good
shape:
- it follows the spike's pattern (b);
- it keeps every item on the "must not do" list;
- the build is green at both commits.

## Gradle evidence

| Run | Result |
|---|---|
| Tip `8f4ca676`: `./gradlew --max-workers=1 :packs:check :contracts:check :deployable:check` | BUILD SUCCESSFUL. Test tasks were up to date against an earlier green run with the same inputs. `pack-document.feature` results: 13 scenarios plus the outline examples, 0 failures, 0 skipped. |
| `0e0d8e56` alone, detached scratch worktree, `:packs:check` | BUILD SUCCESSFUL in 3m57s; `:packs:test` executed. Worktree removed and pruned afterwards. |

## Findings

### M1 (MEDIUM): a valid README whose body opens with a `---` line answers 500 on both forms, and `doc` is still linked

**Evidence.**
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDocumentFiles.java:55,72-83`: `read()`
  eagerly builds the JSON `body` by writing `new MarkdownDocument(empty, empty, preamble, sections)` through
  `MarkdownCodec.write`.
- `MarkdownDocument` forbids a preamble that starts with a `---` line when the document has neither frontmatter nor a
  title, so the constructor throws `IllegalArgumentException`. `body()` turns that into `PACK_UNREADABLE`.
- The store reads such a file without complaint. Reproduced against the built classes with the README
  `"# T\n\n---\n\nx\n"` (an H1 followed by a thematic break, a common style):
  - `PackDocumentMarkdown.read` gives `Read[... title=T, preamble="---\n\nx"]`: accepted, schema-valid.
  - `PackDocumentFiles.read` gives `CharterRefusal: acme/README.md has a body that cannot stand without its title:
    preamble must not start with a --- line ...`
- The same happens for a README with frontmatter, no H1, and a preamble that opens with `---`.

**Consequences.**
- `GET /packs/{id}/doc` answers 500 `pack_unreadable` for `Accept: text/markdown` too. The Markdown form is meant to be
  the held file byte for byte, and it does not need the body at all.
- `PackSummaryQuery` uses `holdsDocument` (a regular-file check), so the pack resource still carries `doc`. That
  contradicts `contracts/charter/paths/packs_packId_doc.yaml:21-22`: "The pack carries a doc link exactly when this read
  would answer 200".
- Registration accepts the pack, so the registration result also carries `doc`.
- The 500 description at `packs_packId_doc.yaml:18-20` lists the reasons the store refuses a README. This case is not
  one of them, because the store does not refuse it.

**Fix.**
1. Derive `body` without requiring it to stand alone as a document. For example, write the canonical document without
   frontmatter but keep the title, then drop the leading `# title` line and the blank line after it. When there is no
   title, write it with the frontmatter and drop the frontmatter block. Any form that cannot fail for a model the codec
   has just parsed will do.
2. Alternatively, compute the body lazily, only for the JSON form, so the Markdown form never depends on it. Item 1 is
   still needed for JSON either way.
3. Add a `PackDocumentFilesTest` case for `"# T\n\n---\n\nx\n"` and for frontmatter followed by a `---` preamble. Add a
   scenario that reads such a pack in both forms.
4. Either way, change the contract sentence at `packs_packId_doc.yaml:21-22` to what is true: "exactly when the pack
   holds a README.md", which is what the changes fragment says. A README the store refuses still answers 500 behind a
   `doc` link.

### L1 (LOW): the contract claims RFC 9110 quality semantics that Spring does not give for q=0

**Evidence.** `packs_packId_doc.yaml:10` and fragment line 4 say "chosen by quality first, then by specificity (RFC 9110,
section 12.5.1)". I probed `ProducesRequestCondition` from Spring 7.0.9, as the two mappings are declared:

| Accept | Spring answers | RFC 9110 |
|---|---|---|
| `application/json;q=0, text/markdown;q=0` | JSON 200 | 406 |
| `application/json;q=0, */*` | **JSON 200** | Markdown (JSON is explicitly not acceptable) |
| `text/markdown;q=0, */*` | JSON 200 | JSON (correct) |
| `application/json;q=0, text/markdown` | Markdown (correct) | Markdown |

The second row serves the form the client refused, which is the opposite of what the text promises.

**Fix.** Add one sentence to the operation description and the fragment, for example: "a q=0 weight is read as the
lowest weight, not as 'not acceptable'". Do not hand-roll negotiation (see point 1).

### L2 (LOW): the `doc` link on a registration's stored answer is never exercised with `hasDocument = true`

**Evidence.**
- `PackRegistrationService.java:633` derives it from the staged pack.
- The only `RegistrationOutcome` test sites (`PackRegistrationMapperNoticesTest`, `PackRegistrationMapperSourceTest`)
  pass `false`.
- No registration scenario asserts `_links.doc` on a result.

The fragment and `data-model.md` both promise this link, and the change is untested.

**Fix.** Add a mapper test with `hasDocument = true` that asserts `doc`. Add a registration-result scenario, or a step in
an existing one, for a pack that has a README, and one for a replace (PUT) that removes the README, which asserts the
link is gone.

### L3 (LOW): the generic `Vary: Accept` on every 406 is not mentioned in the fragment

**Evidence.** `service/web-transport/src/main/java/ai/speckitty/charter/web/ProblemHandler.java:99-104` adds `Vary`
to every operation's 406. No other operation declares a 406, so those answers fall to `default` (the shared Problem),
which does not forbid an extra header. The behaviour is correct (RFC 9110 §12.5.5).

**Fix.** Add one sentence to the fragment, for example: "every 406 now carries Vary: Accept". No per-operation
declaration is needed.

### L4 (LOW, inherited, not WP05's to fix): the If-None-Match text is stricter than `EntityTags`

**Evidence.** `IfNoneMatch.yaml` and the operation text say "a value that is not an entity-tag list matches nothing".
`EntityTags` accepts some values that are not such a list:
- It deliberately matches an unquoted bare token (`If-None-Match: sha256:...` gives 304).
- It matches a list with a malformed member before a valid one (`garbage, "sha256:…"` gives 304).

This came before WP05; the activation read has the same behaviour. Note it for WP08's ADR or a later wording pass.

## Rulings on the implementer's 10 deviations

1. **q=0 is not honoured.**
   - Behaviour: accepted. Spring's `produces` matching ignores q=0, and the spike forbids hand-built negotiation.
   - Cheap fix: none. There is no non-hand-rolled fix within Spring MVC's mapping. A `ContentNegotiationStrategy` that
     drops q=0 entries would be service-wide negotiation code, and handling "all refused" would mean returning a
     sentinel list. That is outside this WP.
   - Contract: the text does over-claim. It says "by quality first" per §12.5.1, and `application/json;q=0, */*`
     answers JSON. Fix the wording (L1).
2. **`OperationCoverage` records only 2xx.** Accepted. Coverage is keyed by (method, template, status), not by media type.
   Scenarios cover the Markdown 200 (outline rows plus the byte-for-byte scenario) and the 304 (outline for both forms,
   and `*`). `ContractConformance` accepts a text body for a non-JSON declared type and checks that the content type is
   declared.
3. **No `operations:` map for `pack_document_not_found`.** Accepted, and it is the correct reading. In `x-refusal`,
   `operations:` is a per-operation *status override* (see `pack_dependency_cycle` and `RefusalExamples.java:192`), not a
   list of operations that raise the code. The code is 404 everywhere, so the map would be redundant. The description
   names `getPackDocument`.
4. **PACK_UNREADABLE becomes PACK_NOT_FOUND only for pack and revision resolution.** Accepted.
   - `PackDocumentQuery.java:104-113` matches `ProcedureReadService`.
   - A refused README stays 500 `pack_unreadable`. This is declared in the operation description
     (`packs_packId_doc.yaml:18-20`), and the 500 response `OfferingUnreadable.yaml` lists `pack_unreadable`.
   - M1 adds one case where a README the store does not refuse still answers 500.
5. **The mappings sit on `PackRegistrationController`, with a new `PackDocumentController` component.** Accepted. This
   is the spike's serving pattern, mirroring `PackRemovalController`. The JSON override narrows `produces` to JSON, and
   the Markdown handler sits outside the generated interface.
6. **The `doc` link in a registration's stored answer comes from the staged pack.**
   - Accepted in design. README.md is never an excluded file, so the staged and held documents agree.
   - A whole-pack replace (PUT) writes the staged tree. A replace without a README therefore stages
     `document() = empty`, gives `hasDocument = false`, and the link is correctly absent.
   - The stored answer is a snapshot of that registration, so a later change to the pack does not falsify it.
   - Untested with `true` (L2).
7. **T022 and T024–T027 are one commit.** Accepted. Each commit is green on its own (`0e0d8e56` passes `:packs:check`;
   the tip is green), and the message lists every edit outside the WP's owned files.
8. **`Vary: Accept` is added to every 406.** Accepted, and correct. Other operations need no contract declaration,
   because they declare no 406 and `default` allows it. Add a line to the fragment (L3).
9. **The 304 bypasses Spring's implicit check.**
   - Accepted. `PackDocumentETag` sets the ETag in `beforeBodyWrite`, after `HttpEntityMethodProcessor`'s
     not-modified check; this is the `ContractDocumentETag` technique. The advice is scoped to
     `PackRegistrationController`, and its method-name filter is narrow.
   - `garbage "sha256:…"` answers 200: it is one bare member that does not equal the token.
   - `EntityTags.noneMatch` is RFC-correct on what matters: weak comparison (`W/` matches), `*` matches, and the
     comparison runs only after the pack resolved. The extra leniency it has is inherited (L4).
   - The 304 carries the ETag, `Vary` and the Charter headers, and no fingerprint or body, as declared.
10. **`Vary` is declared with the pattern `^Accept$`.** Accepted. It is equivalent to a one-value enum, and the service
    has no CORS configuration that would add other `Vary` values.

## Other judgements

**JSON body.**
- The Markdown below the H1, without frontmatter, in canonical form.
- `title` is optional and omitted when absent (mix-in on `PackRegistrationJson`, covered by `NullOmissionTest`).
- The Markdown form is byte for byte. The scenario compares the full text, frontmatter included.
- Correct apart from M1.

**Representation headers.**
- The ETag equals the quoted pack revision and is identical for both forms (scenario "The ETag is the pack revision, and
  the same for both forms").
- `Vary: Accept` is on the 200, 304 and 406.
- `Charter-Source-Fingerprint` is on both 200 forms, and `CharterSteps.isVerifiedRead` now covers `text/markdown`, which
  strengthens the check.
- The shared headers are on every answer.

**Contract hygiene.**
- JSON is listed first, so the generated method is typed `PackDocumentRead`, as ADR-0004 requires.
- `PackDocumentRead` is closed and every property has `x-derived`.
- There is a refusal example, plus both examples inside `PackDocumentNotFound.yaml`.
- All three `_index.yaml` files are updated.
- `doc` is in the lint relation list.
- The fragment is a single `### Added`, additive.
- The scenarios are not `@WIP`.
- The refusal-code edit is one enum entry plus one `x-refusal` entry, in order, as the #496 coordination note requires.

**Code quality.**
- No `ResponseEntity<Object>` in WP code. The one in `ProblemHandler` is Spring's override signature.
- No casts (`instanceof` patterns only) and no `String.split`.
- Onion layering holds: the port is in `domain`, the implementation is in `infra.storage` via `DomainRepositoryService`
  (no fourth `resolve()`), and the query is in `application`.

**Brownfield.**
- The ten `PackRegistrationController` constructor sites only gained `mock(PackDocumentController.class)`.
- `PackSummaryQueryTest` and the two mapper tests only gained the new argument.
- `HypermediaTest` and `ReservedRefusalCodesTest` gained the new relation and code.
- No test was weakened and nothing was excluded from a parity check.

**Scenario coverage.** Every response row has a scenario:
- 200 JSON;
- 200 Markdown;
- 304, in both forms and with `*`;
- 404 `pack_document_not_found`, under both Accept values;
- 404 `pack_not_found`, for an unknown pack and a built-in one;
- 406, three types.

The negative `doc`-link control is present ("The pack resource has no document link when the pack has no document").
The scenarios define their own packs inline rather than reusing charter's example packs, as the file's header comment
explains. That is the suite's convention and acceptable.

**Commits.**
- Both are signed (`gpgsig` present).
- Neither has a trailer.
- No model identifiers appear. The author is `Claude <noreply@anthropic.com>`, consistent with every commit on the lane.
- Subjects reference #451.

## Required for approval

- **M1:** fix the body derivation so a README the store accepts never answers 500. Add the tests named in M1, and
  correct the "exactly when this read would answer 200" sentence.

Recommended in the same cycle: L1 (q=0 wording), L2 (a test of the registration-result `doc` link), L3 (a fragment line).
