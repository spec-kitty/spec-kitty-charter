# Spike: the doc read's contract shape (WP01)

Decides how `GET /api/v1/packs/{packId}/doc` is declared and served, before WP05 builds it. Run on branch
`feat/466-slice-b-real-packs-register` (2026-11-11). Every contract, source and test edit named here was temporary and is
reverted; this note is the only change.

## Decision

**Pattern (b): one operation in the contract with both 200 media types, served by two `@GetMapping` handlers on the same
path with disjoint `produces`.** The generated interface method answers JSON (`ResponseEntity<PackDocumentRead>`, ADR-0004
kept); a second, hand-written method in the same controller answers `text/markdown` as `ResponseEntity<String>`. Spring
picks by `Accept`. No unchecked cast, no `ResponseEntity<Object>`, no negotiation code of our own. Patterns (a) and (c)
were not needed: (a) is not usable (below), (c) would need an unchecked cast (below).

## What the generator does with two 200 media types (T001)

The Spring generator (`interfaceOnly`, `useTags`) types the method by the **first** 200 content entry and lists every
declared media type in `produces`, the declared error media type included:

```java
@RequestMapping(
    method = RequestMethod.GET,
    value = RegistrationApi.PATH_GET_PACK_DOCUMENT,          // "/packs/{packId}/doc"
    produces = { "application/json", "text/markdown", "application/problem+json" }
)
ResponseEntity<PackDocumentRead> getPackDocument(
    @Size(min = 1) @PathVariable("packId") String packId,
    @RequestHeader(value = "If-None-Match", required = false) @Nullable String ifNoneMatch
);
```

So (a) is moot: the interface type is `PackDocumentRead`, and the Markdown `string` content is invisible in the
signature; it cannot be `ResponseEntity<String>` as well. Order matters: JSON must be listed first in the contract, or the
generated return type becomes `String`.

The generated client (`client-java`, same contract) gives `PackDocumentRead getPackDocument(String packId, String
ifNoneMatch)` and `getPackDocumentWithHttpInfo`; its `selectHeaderAccept` sends `Accept: application/json, text/markdown`
(JSON wins), so the typed client reads the JSON form. The Markdown form is reachable only through the raw call. That is
the intended shape of the client.

The generator logs `Failed to get the schema name: null` (once, for the inline `type: string` Markdown schema); the build
succeeds and the line is harmless.

## The contract I would commit

`contracts/charter/openapi.yaml`, under `/packs/{packId}`:

```yaml
  /packs/{packId}/doc:
    $ref: paths/packs_packId_doc.yaml
```

`contracts/charter/paths/packs_packId_doc.yaml` (new):

```yaml
parameters:
- $ref: ../parameters/PackId.yaml
get:
  operationId: getPackDocument
  summary: Read a pack's document (its README.md).
  description: >-   # (WP05 writes the full text: the two forms, Accept negotiation, 304, 404 codes, 406)
  tags: [Registration]
  parameters:
  - $ref: ../parameters/IfNoneMatch.yaml
  responses:
    '200':
      description: The pack document.
      headers:
        Charter-Contract-Version: { $ref: ../headers/CharterContractVersion.yaml }
        Charter-Worktree:         { $ref: ../headers/CharterWorktree.yaml }
        Charter-Source-Fingerprint: { $ref: ../headers/CharterSourceFingerprint.yaml }
        ETag:                     # the pack revision, quoted, as getPack
          description: The pack revision, quoted.
          required: true
          schema: { type: string, pattern: '^"sha256:[0-9a-f]{64}"$' }
        Vary:
          description: Accept, on every 200 (two representations share one ETag).
          required: true
          schema: { type: string }
      content:
        application/json:            # FIRST: it types the generated method
          schema: { $ref: ../schemas/PackDocumentRead.yaml }
          examples:
            document: { $ref: ../examples/PackDocumentRead.yaml }
        text/markdown:
          schema: { type: string, description: The raw README.md, frontmatter included. }
    '304': { $ref: ../responses/NotModified.yaml }       # reused as is (ETag + the two Charter headers)
    '400': { $ref: ../../_shared/responses/Problem.yaml }
    '401': { $ref: ../responses/Unauthorized.yaml }
    '403': { $ref: ../responses/Forbidden.yaml }
    '404': { $ref: ../responses/PackNotFound.yaml }      # WP05: a doc-specific response for pack_document_not_found
    '406': { $ref: ../../_shared/responses/Problem.yaml } # code-less Problem with the shared headers
    '500': { $ref: ../responses/OfferingUnreadable.yaml }
    default: { $ref: ../../_shared/responses/Problem.yaml }
```

`schemas/PackDocumentRead.yaml` (new): `title: PackDocumentRead`, closed object, `required: [body, _links]`, properties
`title` (optional string), `body` (string), `_links` (`Links.yaml`); every property carries a `description` and an
`x-derived` (the `resource-property-cited` rule), and the schema an `x-derived`. `examples/PackDocumentRead.yaml`: a
`value` with `title`, `body`, `_links.self` and `_links.pack` (both known relations). The `IfNoneMatch.yaml` parameter and
`NotModified.yaml` response are reused unchanged (the parameter's text mentions "the activation"; WP05 may generalise the
wording).

The sketch in `contracts/pack-doc.openapi.yaml` is otherwise right. Corrections: JSON listed first; `Vary` declared;
`If-None-Match` and `304` declared; `406` references the shared Problem (so it carries the shared headers, as the lint
requires).

## Serving pattern (T002)

In `PackRegistrationController` (or a delegate class it calls, as it does for `PackRemovalController`; the generated
method must be implemented on the class that `implements RegistrationApi`):

```java
// JSON: overrides the generated method, narrowing the interface's produces to JSON so the two mappings are disjoint.
// Parameter annotations (@PathVariable, @RequestHeader) are inherited from the interface.
@Override
@GetMapping(value = RegistrationApi.PATH_GET_PACK_DOCUMENT, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<PackDocumentRead> getPackDocument(String packId, @Nullable String ifNoneMatch) { ... }

// Markdown: hand-written, outside the generated interface.
@GetMapping(value = RegistrationApi.PATH_GET_PACK_DOCUMENT, produces = "text/markdown")
public ResponseEntity<String> getPackDocumentMarkdown(
        @PathVariable("packId") String packId,
        @RequestHeader(value = "If-None-Match", required = false) @Nullable String ifNoneMatch) { ... }
```

Both read the document once (one `PackDocumentQuery` call returning revision, fingerprint, title, body and raw text) and
build the answer with a shared helper:

- 200: `ResponseEntity.ok().headers(CharterHeaders.fingerprint(..)).eTag(revision).header("Vary", "Accept")`; the Markdown
  form sets `contentType(new MediaType("text", "markdown", UTF_8))`, which writes `text/markdown;charset=UTF-8`.
- 304: the handlers test `If-None-Match` themselves (the contract says `*` matches, a non-list value does not, and Spring's
  implicit check does neither; see `ContractDocumentETag`) and answer `ResponseEntity.status(NOT_MODIFIED).eTag(..)
  .header("Vary", "Accept").build()`. WP05 should lift `ContractDocument.EntityTagList`/`matchedBy` into a shared
  `EntityTags` matcher rather than copy it; my spike used a crude `contains` and is not a model for that.
- 406: produced by declared means, nothing of ours. An `Accept` that matches neither `produces` raises
  `HttpMediaTypeNotAcceptableException`, which `ProblemHandler` (a `ResponseEntityExceptionHandler`) renders as the
  code-less `application/problem+json` 406. `CharterHeaders` (a filter) adds the two Charter headers; no fingerprint.
- 404: `CharterRefusal(PACK_NOT_FOUND / PACK_DOCUMENT_NOT_FOUND)` as every read; it answers `application/problem+json`
  whatever `Accept` the request had (checked for `text/markdown`, `application/json` and `*/*`).

## Throwaway test (not committed): 14 methods, all asserted through standalone MockMvc

Setup: `PackRegistrationController` (mocked collaborators) + `RefusalHandler` + `ProblemHandler` + `CharterHeaders` +
the service's server converters (`HttpConverters.serverConverters()`). Results on the spike controller:

| Request | Result |
|---|---|
| `Accept: text/markdown` | 200, `Content-Type: text/markdown;charset=UTF-8`, `Vary: Accept`, `ETag`, fingerprint, raw body with frontmatter: pass |
| `Accept: application/json` | 200 JSON `{title, body, _links.self}`, `Vary: Accept`, `ETag`: pass |
| `Accept: */*` | JSON: pass |
| no `Accept` | JSON: pass |
| `Accept: application/*` | JSON: pass |
| `Accept: text/*` | Markdown: pass |
| `text/markdown;q=0.5, application/json` | JSON: pass |
| `text/markdown, application/json;q=0.5` | Markdown: pass |
| `application/json, text/markdown` (tie) | JSON: pass |
| `text/markdown, application/json` (tie) | **Markdown** (Accept order): see "needs the operator" |
| `text/*, application/*` (tie) | **Markdown**: same |
| `image/png` | 406, `application/problem+json`, `status: 406`, no `code`, both Charter headers present, no fingerprint: pass |
| `If-None-Match` = the ETag, with `application/json`, `text/markdown` and `*/*` | 304, empty body, `ETag`, `Vary: Accept`: pass |
| `If-None-Match: W/<etag>` (Markdown), `*` (JSON) | 304: pass; `garbage` -> 200: pass |
| missing pack, with each of the three `Accept` values | 404 `application/problem+json`, `pack_not_found`, Charter headers: pass |

12 of 14 passed; the two failures are the two tie rows, which are not among the WP's required assertions (they come from
the plan's A7 wording "ties go to JSON"). Every assertion the WP lists passed.

## Lint and checks (T001, step 4)

With the sketch above in place: `:charter-api:validateContract` valid; `:contracts:lintContract` "passes all 23 lint
rules"; `:contracts:check` green (1355 response schemas closed, refusal examples, changelog fragments well formed,
checkstyle, lint fixtures); `:client-java:generateClient` succeeds; `:packs:compileJava` (errorprone, `-Werror`) and
`:packs:test` for the spike class ran. The `headers on every response` rule is satisfied by the reused 304 response and by
the shared Problem for 406; `fingerprint-on-verified-reads` by the 200; `success-schemas-carry-links` and
`success-responses-have-link-examples` apply to the JSON content only, and the Markdown content is not judged. No rule
tripped, so no ruleset change is needed. A changelog fragment is not required by the checks for this addition (WP05 writes
one anyway).

## Needs the operator

1. **Ties.** With (b), when the client lists both forms at equal quality (`text/markdown, application/json`, or
   `text/*, application/*`), Spring answers the one listed first. RFC 9110 leaves a tie to the server. The plan-point
   squad's A7 says "ties go to JSON". Honouring that exactly needs pattern (c), one handler that negotiates itself, which
   cannot return a `String` from the generated `ResponseEntity<PackDocumentRead>` method without an unchecked cast, or a
   hand-written `produces`-free handler outside the interface. Recommendation: keep (b) and amend the plan and the
   contract text to "a tie goes to the form listed first in `Accept`; no `Accept`, `*/*` and `application/*` give JSON".
   This is a wording change, not a behaviour a real client relies on (the generated client sends JSON first).
2. **A handler outside the generated interface.** `getPackDocumentMarkdown` is hand-written and not on
   `RegistrationApi`, as `PackRemovalController`'s two DELETEs are not on it either. It returns a `String`, not a DTO, so
   ADR-0004 (generated DTOs) is kept for JSON only; the Markdown form has no DTO to generate. No unchecked cast is needed.
   The new ADR should say so.
3. **Operation coverage.** `OperationCoverage` and the contract conformance suite exercise every 2xx per operation; the
   Markdown 200 needs a scenario of its own, and `CharterSteps` has no raw-text body step yet (research §C already notes
   it). Not part of this WP.

## What WP05 must not do

- Do not list `text/markdown` before `application/json` in the contract: the generated method would return `String`.
- Do not let both handlers inherit the interface's mapping: the override must narrow `produces` to JSON, or the two
  mappings overlap and `*/*` is ambiguous.
- Do not rely on Spring's implicit conditional GET for the 304 (it ignores `*`, and would answer 304 to values the
  contract answers 200); answer it explicitly and declare it.
- Do not use `ResponseEntity<Object>`, a cast of `ResponseEntity<String>` to the generated type, or hand-built negotiation
  (`ContentNegotiationManager`, `sortBySpecificityAndQuality`) to get the Markdown form: the second handler is the
  mechanism.
- Do not forget `Vary: Accept`: it is on every 200 and the 304; the 406 needs nothing beyond the shared headers.
- Do not call `String.split(String)` in the handlers (errorprone `StringSplitter`, `-Werror`).
