---
affected_files: []
cycle_number: 1
mission_slug: gate-a-read-surface-01M691ND
reproduction_command:
reviewed_at: '2026-10-31T14:23:07Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review feedback (cycle 1)

**Verdict: changes requested.** There is one blocking item, a Definition of Done miss. Everything else
passes. The fix is small and changes no behaviour.

## Blocking

1. **[MAJOR] service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ContractConformance.java:420.**
   The new method `bound(...)` has cyclomatic complexity **16**. NFR-003 and this WP's DoD require at most 15
   in every method added or changed, and commit `cfd7570f` says each method "stays within complexity 15".

   I measured with the project's own Checkstyle 14.3.0 (`CyclomaticComplexity`, `max=15`, default settings),
   run on this file only:

   `ContractConformance.java:420:5: Cyclomatic Complexity is 16 (max allowed is 15).`

   The count is 5 switch cases, 5 `if`s, 4 `&&` and the ternary `length` computation. The other methods are
   within the limit: `vetNode` 14, `validate` 11, `constrain` 14.

   **Required change:** bring `bound` to 15 or less without changing behaviour. For example, split the string
   length bounds (`minLength`, `maxLength`) from the size and range bounds (`maxItems`, `minimum`, `maximum`),
   or dispatch from `constrain` directly. Keep `vetNode` at 14 or less. Re-measure and state the figure in the
   commit or the activity log.

## Checked and passing (no change needed)

- **Shape agreed with #125.** `Link.yaml` and `Links.yaml` match it exactly:
  - `Link`: closed, `required [href]`, the `href` pattern `^/api/v1(/|$|\{)`, `templated` a boolean,
    `method` matching the pattern `^(GET|POST|PUT)$` with no enum, `title` a string;
  - `Links`: `type: object` and `additionalProperties {$ref: Link.yaml}`, with no required keys.
- **Closure exemption.** It is structural: the schema is reached as `properties/_links`, its type is
  `object` alone, it has no `patternProperties`, and its value is titled `Link` and classified CLOSED by the
  walk. Each of the three lookalike fixtures differs from the exempt one only in its planted change, and
  each reports.
- **ContractConformance.**
  - Map values are validated: a link without `href`, and one with an extra `rel`, both fail.
  - `true`, `{}` and `null` are refused.
  - An absent `additionalProperties` was already accepted before this WP: the base `vetNode` and
    `validate` acted only when the key was present. So this is not a new hole, and nothing that was
    closed got looser. Closure stays enforced at the contract level by the closure checker and lint.
  - Matching on path and method works, and does not depend on the order of the paths.
- **Hypermedia and Relation.** Hrefs are correct, each segment is encoded with
  `UriUtils.encodePathSegment` (`/` becomes `%2F`, a space `%20`), and GET is omitted. They have no
  production caller yet, by design: WP03 is the first consumer.
- **Process.**
  - Red first: each test commit precedes its feat commit.
  - No new suppressions.
  - Every commit carries the Stijn trailer and names no model.
  - All files are within owned_files.
  - `:contracts:test :contracts:check :deployable:test :packs:test` are green (rerun with `--rerun`).

## NITs (non-blocking)

- `isSchema` accepts any non-empty mapping, for example `additionalProperties: {description: x}`, which
  constrains nothing. The closure checker still catches such a map in the real contract, so the harness
  could tighten this later.
- `Hypermedia.LinkValue` has no `title`. That is fine for now; add it when WP03 maps the record to the
  generated DTO, if a link needs one.
