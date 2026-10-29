---
title: "ADR-0043: Sanitized artifact shapes; procedures and toolguide guides in the pack store"
description: Sanitized-shape rules for artifact kinds; the procedure shape; the structured toolguide guide as a Markdown companion, sent over HTTP as content or a fetched URI.
doc_status: draft
updated: 2026-10-29T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0040-content-form-and-payload-kinds
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0038-one-configuration-root-a-directory-per-domain
  - adr/0032-steps-name-roles-and-profile-selection-order
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before designing or changing the stored shape of an artifact kind, or before touching how a toolguide's guide is read, written, fetched or answered.
  - profile: pack-author
    guidance_text: Read this for the procedure and guide shapes the service accepts, how a guide is laid out as Markdown, and why a pinned file in another shape is fixed in the pack.
  - profile: core-maintainer
    guidance_text: Read this for the sanitized procedure schema, the toolguide guide companion and the breaking change to toolguide registration over HTTP.
---

# ADR-0043: Sanitized artifact shapes; procedures and toolguide guides in the pack store

## Status

**Proposed** — 2026-10-29. Decider: Stijn Dejongh (operator). Records the operator rulings of
2026-10-28 and 2026-10-29 on [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194)
(part of epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)), held as decision
moments under `kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/decisions/` and
consolidated as D1 to D12 in that mission's `research.md`. The code of #194's second pull request
(PR-A2) implements it; the record stays Proposed pending the operator's acceptance, which that pull
request asks for.

The first pull request of #194 (PR-A1, [#263](https://github.com/spec-kitty/spec-kitty-charter/pull/263),
merged on 2026-10-29) built the `markdown` content form and amended
[ADR-0040](/adr/0040-content-form-and-payload-kinds/) for it. This record does not repeat that
amendment; it uses the form.

It **extends**
[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) (Accepted): the
sanitized-shape rules S1 to S8, stated there for pack-level objects, apply to the artifact kinds
this record shapes, and rules S9 to S16 below are added for them. ADR-0042 is not amended: its
rules stand as written.

It **amends** [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) (Accepted):
decisions 1 to 4 and a consequence. A companion declares its content form and structure schema
(decision 1), its content may be Markdown (decision 2), it owns no path any file-kind rule
classifies except its own rule (decision 3), and it has new refusal reasons (decision 4). ADR-0039
carries a dated amendment under its Status, with each point annotated where it stands; the amendment
takes effect when this record is accepted.

## Context

Issue #194 asks the whole-pack round trip to cover procedures, the inline anti-patterns and the
toolguides' guides. At `openPacksRef` `01edb4e` the open packs hold 41 procedures (built-in 23,
internal 13, qa 5) and 24 toolguide guides (18, 3, 3) across the built-in, internal and qa packs,
each guide a Markdown file a toolguide names with `guide_path`.

Five facts force a decision:

- **The issue asked for a round trip as-is.** On 2026-10-28 the operator ruled instead that every
  storage object has a sanitized, deliberately designed shape (the same ruling ADR-0042 records for
  pack-level objects). The pinned procedures name themselves with `name`, summarize with `purpose`
  and say who acts with `actor: human|agent`, which the conceptual model calls a role
  ([ADR-0032](/adr/0032-steps-name-roles-and-profile-selection-order/)).
- **Procedure references would mint conflicting edges.** `ArtifactEdges` mints a typed reference
  as `suggests`, while `procedure.graph.yaml` holds the same pair as `requires`. Which source is
  authoritative for an edge is [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189).
- **The 24 guides share no skeleton.** No H2 heading occurs in more than three of them, but the
  content squad found recurring meanings: an overview (24 of 24), cautions (14), usage (13) and
  setup (11). The operator asked for a structured guide: fixed blocks plus freeform sections.
- **The HTTP registration took a bare `guidePath`.** A path assumes a filesystem the client and the
  service share. The operator ruled that a registration carries the guide's content or a URI the
  service fetches, never a path.
- **The sanitized shape refuses every pinned procedure and toolguide.** A refused toolguide leaves
  its guide unowned, and the gate's known-failure list can only pin files of a modelled kind, so
  listing the refusals would leave the guides red.

Ground truth and the three squads' findings, each with its disposition, are kept under the
mission's `research/` directory.

## Decision

### 1. The sanitized-shape rules for artifact kinds

S1 to S8 of [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)
apply, as written there, to every artifact kind this record shapes. For a procedure, S1's
"`id` is the file stem" is enforced: the store's load and validate mode both refuse a
`<stem>.procedure.yaml` whose `id` is not `<stem>`, the rule ADR-0042 applies to presets. For a
toolguide it is not enforced yet, because two pinned qa toolguides break it (see the follow-up list).
For artifact kinds the rules are extended by:

| Rule | What it says |
|---|---|
| S9. snake_case keys | Every key is snake_case. |
| S10. `title` is the label | The display label of an artifact or of an entry is `title`. |
| S11. `description` is the summary | The one "what is this" field is `description`. A field with a distinct meaning keeps its own name (`entry_condition`, `on_failure`). |
| S12. `content` is a body | A field that carries a body of text is `content`. Freeform sections are `{title, content}`. |
| S13. `<role>_path` names a file | A field that names another pack file is `<role>_path`, relative to the artifact's file. Every companion is named by a field (ADR-0039 decision 1). |
| S14. One reference shape | Where references are kept they are `{type, id, when?, reason?}`, `type` an artifact-kind token. Keeping a reference never mints a relationship until #189 rules on edge authority. |
| S15. `role` names who acts | Who performs a step is `role`, an open role token such as `operator` (ADR-0032); absent means any role may act. |
| S16. An empty optional list is refused | An optional list holds at least one entry (`minItems: 1`); an empty one is left out, never written `[]`. This is ADR-0042 decision 3's rule, so S6 (one spelling per meaning) holds with no comparator exception. |

A pinned file that breaks a rule is a pack inconsistency, fixed in `spec-kitty-open-packs`, never
tolerated by the service ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
decision 5). Already-merged kinds that break a rule are not changed here; they are listed in the
[epic #186 follow-up list](/development/epic-186-sanitized-shape-follow-ups/).

### 2. The procedure shape

`contracts/packs/schemas/procedure.schema.json` is rewritten in place (it is canonical here,
ADR-0015) and closed at every level:

- `schema_version` (`"1.0"`), `id` (`^[a-z0-9]+(-[a-z0-9]+)*$`, the file stem), `title` (was
  `name`), `description` (was `purpose`), `entry_condition`, `exit_condition`: required;
- `steps`: required, at least one, each `{title, description?, role?, on_success?, on_failure?}`.
  `role` (`^[a-z][a-z0-9-]*$`) replaces `actor`: a pinned `actor: human` becomes `role: operator`,
  and `agent` and `system` steps carry no role (operator ruling 2026-10-29);
- `anti_patterns`: optional, entries of decision 3;
- `references`: optional, `{type, id, when?, reason?}` (S14). They are kept on the procedure,
  verbatim and in authored order, and `Procedure.relationships()` is always empty: a procedure mints
  no relationship, pending #189;
- `notes` (not empty) and `applies_to_languages`: optional;
- each optional list (`anti_patterns`, `references`, `applies_to_languages`) holds at least one
  entry when present (S16).

`ProcedureBinding` maps the shape to the `Procedure` entity; procedures are a bound kind of the
registry ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/)).

```yaml
schema_version: "1.0"
id: land-a-change
title: Land a change
description: Take a reviewed change from branch to main.
entry_condition: The change is approved.
exit_condition: The change is on main and its branch is gone.
steps:
  - title: Ask for the merge
    role: operator
  - title: Merge and delete the branch
anti_patterns:
  - title: Merging your own unreviewed change
```

### 3. Anti-patterns: entries, not a kind

There is no standalone anti-pattern kind or file (ADR-0037 decision 4). A procedure's anti-pattern
is an `AntiPatternEntry`, a value of its procedure with no identity: `title` (required), `content`
(its body, S12), `bad_example`, `good_example`. Only the title is required, so a bare "avoid this"
entry needs no invented text. A styleguide's anti-patterns keep their merged shape
(`StyleguideAntiPattern`, `{name, description, bad_example, good_example}`, all required); renaming
them is an epic #186 follow-up. The anti-pattern graph nodes in `anti_pattern.graph.yaml` stay on
the gate's expected-red list for their node tags under
[#236](https://github.com/spec-kitty/spec-kitty-charter/issues/236).

### 4. The toolguide guide: structured, stored as a Markdown companion

A toolguide's guide is a `Guide` value: fixed blocks and freeform sections.

- **Fixed blocks** (`GuideBlock`), each an H2 with a canonical heading: `Overview` (required, not
  blank), `When to use`, `Setup`, `Usage`, `Cautions`, `Known limitations` and `References`
  (external links).
- **Freeform sections**, `{title, content}`, in authored order, between `Usage` and `Cautions`. A
  freeform title may not be a fixed heading in another letter case.
- **Content rules**, the Markdown model's own: no carriage return, no leading or trailing blank
  line, no H1 or H2 at column 0 outside a fence, no fence left open. H3 and deeper stay in content.

The guide is stored as an ADR-0039 companion of the `markdown` form, `<id>.guide.md` beside its
toolguide. `guide_path` names it relative to the toolguide's file and must equal `<id>.guide.md`
(the schema's pattern and the `Toolguide` entity's invariant). The toolguide `id` grammar is
`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, the grammar of the guide's file name, so every id the service
accepts names a guide file the schema accepts (an id such as `a-` or `a--b` is refused). The `files` rule
`**/toolguides/*.guide.md` (`content_form: markdown`, structure schema `toolguide-guide`) classifies
it, and ADR-0039's ownership rule, as amended, admits a companion only at that rule of its own.
`GuideMarkdown` maps the guide to the document: no frontmatter, the H1 written from the toolguide's
`title`, an empty preamble, then the leading blocks, the freeform sections and the trailing blocks.
On read it refuses frontmatter, a preamble, an H1 that differs from the title, a missing overview, a
fixed block out of order or twice, and a fixed heading in another letter case.

**The H1 is derived data (S8).** It is computed from the toolguide's `title` and written on every
write; `title` is its one home. A stored guide must carry it, so a stored guide has one spelling:
the store's load refuses a guide file without its H1. Incoming content over HTTP may leave the H1
out (FR-011): the service writes it. A title that cannot be written as an H1 (blank, more than one
line, or one the Markdown reader would not read back as itself, such as `Search for #`) is refused
422 on registration and is a read violation of a stored toolguide, never a failure when it is
answered.

**`guide_path` is determined by the id (S5).** It always equals `<id>.guide.md`, so it repeats a
fact the `id` already states. It is kept because it is the file reference S13 asks for: every
companion is named by a field (ADR-0039 decision 1), and a reader finds the guide from the field,
not from a naming convention. The domain invariant `guide_path == id + ".guide.md"` keeps the two
from diverging.

Validate mode reads each guide file too: a `*.guide.md` file the `files` rule maps is parsed as
Markdown, validated against the `toolguide-guide` structure schema and held to the rules above, so
validate mode and the load refuse the same guide.

The toolguide YAML stays the one home of `title`, `summary`, `commands` and the artifact
`references`. The guide holds none of them, so none is written twice.

### 5. The guide over HTTP

The contract changes on the 0.x line (breaking; `contracts/charter/CHANGELOG.md`):

- `ToolguideRegistration` loses `guidePath` and carries exactly one of `guide` (the Markdown) or
  `guideUri` (a URI; the service fetches `https` only and enforces it, the schema carries no
  pattern for it). The schema says so with a `oneOf`, which the generator this repository builds
  with ignores, so the service enforces it: both, neither, an empty value or an explicit `null`
  (also beside a valid other field) is 422 `validation_failed`.
- An inline guide is capped at the guide size, 1 MiB of UTF-8 by default (configurable via
  `charter.guide-fetch.max-size`, the same cap a fetched guide is held to), refused 422 before it is
  parsed; so is one holding an unpaired surrogate, which has no UTF-8 form. A registration body
  larger than six times that cap plus 1 MiB (7 MiB by default) is refused 422 before it is read, by
  its `Content-Length` or while it is read. `commands`, `appliesToLanguages` and `references` hold
  at most 256 items each.
- The response, `Toolguide`, loses `guidePath` and carries `guide`: the stored Markdown, its H1
  written from `title`.
- A refused fetch or an invalid guide is 422 `validation_failed`. The detail names the typed reason
  and the host, never the URI's path or query, which may carry secrets. A guide file already
  claimed by another artifact is 409 `registration_conflict`.
- Inline and fetched text go through one port, `application/GuideFormat` (adapter
  `MarkdownGuideFormat`), so both obey the same rules. A URI is fetched through the port
  `application/GuideFetcher` before the pack's write lock is taken, never under it, and on every
  registration that names it: a PUT with the same URI fetches again. The refusals that need no guide
  come first, so they never cost a fetch: an unknown pack, a stale `If-Match`, a POST that names an
  existing id and a write the policy refuses. They run again under the lock of the commit, where
  they are authoritative.

**The fetch posture** (`HttpsGuideFetcher`, configured under `charter.guide-fetch.*`,
[ADR-0038](/adr/0038-one-configuration-root-a-directory-per-domain/)):

- **Direct.** The client is built with no proxy: a JVM proxy would resolve and connect for the
  service and void every address check.
- **Allowlisted HTTPS on port 443.** The host must be exactly on `allowed-hosts`, after
  lower-casing and stripping a trailing dot; the list is empty by default, so every URI is refused
  until an operator allowlists a host. A port other than 443, user information and IP literals are
  refused, on the first URI and on every redirect. Each allowlist entry is checked when it is bound:
  a URI, a `host:port`, a wildcard or an IP literal fails the start, and a Unicode host name is
  listed in its ASCII (`xn--`) form.
- **Global unicast only.** Every resolved address is checked before the first request and after
  every redirect (at most 3, followed by hand). A JEP 418 resolver provider
  (`GlobalAddressResolverProvider`, installed through `META-INF/services`) refuses a non-global
  answer for every allowlisted host when the client resolves again to connect, which closes DNS
  rebinding. The service refuses to start with a non-empty allowlist if the JVM did not install
  that provider. Each exchange runs on a client of its own, whose every task works for the fetch, so
  a guard refusal is attributed to the fetch that caused it and never relabels another fetch's
  failure.
- **Bounded.** By default (configurable via `charter.guide-fetch.*`): one 10-second deadline over
  resolution, every hop and the body, at most 3 redirects, and a 1 MiB cap. The cap is streamed, and
  a success whose `Content-Length` already passes it is refused unread. The pre-check's lookup is
  interrupted at the deadline; the JDK's resolver blocks in the operating system's `getaddrinfo`,
  which an interrupt cannot stop, so at most 8 lookups run at once and a fetch that finds none free
  is refused.
- **Markdown text only.** The response must declare `text/markdown`, `text/x-markdown` or
  `text/plain` (parameters such as `charset` aside; an absent `Content-Type` is refused), identity
  encoding and UTF-8; no credentials or cookies are forwarded. A refusal typed `UNREACHABLE` logs
  its cause at DEBUG with the host only, the URI's path and query cut from every message.

### 6. Migration by the service's own writer, then a re-pin

The pinned procedures, toolguides and guides were converted with this service's own writer, not by
hand and not by listing known failures. This decision is historical: the harness no longer exists
on this branch or on `main`. It was a throwaway test-scope harness, run as
`./gradlew :packs:packMigration` while it existed; its code was not kept. Its output, the verifier
report and the manual decisions are in
[open-packs#91](https://github.com/spec-kitty/spec-kitty-open-packs/pull/91). It read the old shapes, wrote the sanitized ones through the
production writer into a copy, and checked the result field by field with a verifier independent of
the conversion. Every source key path maps to one target path or to a justified drop: an
`actor: agent` or `actor: system` step that carries no role (decision 2), and an empty list the
writer leaves out. The verifier also checks the order within each section. An authored YAML
comment, which the writer cannot keep, is reported as a `yaml-comment` manual decision and folded
into the procedure's top-level `notes`, once, and only when the procedure had no `notes`; it is
never folded into a reference `reason`. The output is landed in `spec-kitty-open-packs`, generated
on its `main` at `72cb7dbc` (operator ruling), and `openPacksRef` moves to it. On that `main` 2
procedures had become directives, so the migration covers 39 procedures (built-in 22, internal 12,
qa 5) and 24 toolguides with 24 guides. The known-failure list keeps only residual divergences. The
harness was then deleted, before this branch was opened for review.

The output landed as
[spec-kitty/spec-kitty-open-packs#91](https://github.com/spec-kitty/spec-kitty-open-packs/pull/91),
and `openPacksRef` moves to `5df92a5d02e47e8877364de5e2deb06c016d30a9`, which holds 39 procedures,
24 toolguides and 24 guides.

### 7. Operating as if core does not exist

This record follows the ruling on #186 that the rewrite operates as if core does not exist: the
shapes stand on their merits, and a pinned file in another shape is an open-packs issue only. Core
(spec-kitty) still requires the old procedure fields, so the open-packs check that validates with
core is red on the migrated procedures: the maintainer merged
[open-packs#91](https://github.com/spec-kitty/spec-kitty-open-packs/pull/91) with that check red,
by waiver. Core follows when it cuts over to using the service, per the #186 ruling. No core issue
is filed. The shapes the service now accepts and core does not are recorded in the
[divergence register](/conformance/divergence-register/) as DIV-401 to DIV-403, as #195 recorded
DIV-301, so the conformance gate (#72) sees them.

### Per-kind rows

Rows for [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)'s per-kind table, as
ADR-0039 decision 8 recorded its own:

| Files (`match`) | Schema | Compared as | Sets (member identity) | Unknown fields refused |
| --- | --- | --- | --- | --- |
| `**/*.procedure.yaml` | procedure | YAML; `steps`, `anti_patterns` and `references` are sequences | none | yes |
| `**/toolguides/*.guide.md` | toolguide-guide (structure) | the Markdown model (ADR-0040, as amended); the H1 is derived from the toolguide's `title` (S8), required in a stored guide and recomputed on write | none | yes (frontmatter and a preamble are refused) |

## Consequences

### Positive

- Procedures and toolguide guides round-trip through the domain, with one designed shape each.
- The guide has one structure across all toolguides, and the toolguide's fields have one home.
- A client registers a guide without sharing a filesystem with the service.
- The conversion is the service's own writer, checked field by field, so the migrated packs are what
  the service writes.
- It closes [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/)'s
  "until #194" condition on the derived manifest: procedures are now bound, so the corrected
  built-in manifest lists all 272 constituents, and ADR-0042's interim exclusion of the
  unmodelled ones ends.

### Negative

- The HTTP change breaks every client that sent or read `guidePath`. No release carried the old
  shape.
- A deployment whose only egress is a proxy cannot use `guideUri`; it sends the content.
- The resolver guard is JVM-wide: it is installed on the JVM's first name lookup and acts on every
  lookup of an allowlisted host, not only the fetcher's.
- The scanner diverges from CommonMark on purpose: an HTML block or comment is text, so a column-0
  `##` inside one is a heading; a fence may open at any indentation; a tab is never indentation. No
  pinned guide is affected.
- The whole-pack gate was red for procedures and toolguides until `openPacksRef` moved to the
  migrated packs (`5df92a5d`).
- The toolguide YAML keeps its merged shape apart from `guide_path` (`summary`, string
  `references`, a nullable `last_updated`); its renames are epic #186 follow-ups.
- Packs that hold procedures or guides get a new fingerprint and ETag once, as binding any kind
  does (ADR-0037).
- A guide is a companion, not a manifest constituent, so no `content_hash` of the derived manifest
  covers a guide edit: a changed guide leaves the manifest unchanged. Whether companions join the
  constituents is a follow-up for [#110](https://github.com/spec-kitty/spec-kitty-charter/issues/110).
- The migration drops meaning on purpose: an `actor: agent` or `actor: system` step carries no role,
  which reads as "any role may act", so the fact that a system or an agent performs the step is
  lost. ADR-0036 would flag such a loss; the operator ruled it (2026-10-29).
- The toolguide id grammar is narrower than before (`a-` and `a--b` are refused). Every pinned
  toolguide id fits it.

### Risks

- Procedure references are stored and never minted. Until #189 rules, a reader that wants them as
  edges reads the graph shards.
- The structured guide is fixed by seven headings. A block the packs come to need is a schema and
  enum change.

## Alternatives considered

- **Round-trip as-is.** The issue's wording. Overruled by the operator (sanitized shapes).
- **Mint procedure references.** Conflicting `suggests` and `requires` edges for one pair, and it
  decides #189 ahead of time. **Drop them** decides #189 ahead of time too.
- **A standalone anti-pattern kind.** ADR-0037 decision 4 keeps anti-patterns out of the registry.
- **The guide as a plain text companion, an inline YAML block, or sections only.** The operator
  wanted structure. Inline YAML was also refused because the service's emitter has no scalar-style
  control: a trailing space turns a section into a quoted one-line string. A YAML companion is
  forbidden by ADR-0039.
- **A CommonMark library.** A new dependency with its own normalization; one purpose-built scanner
  serves the guides and `pack.md`.
- **Keep `guidePath`, or store the URI only.** Both assume shared files or leave nothing to store.
- **Fetch through a configured proxy, or accept residual rebinding risk.** Declined by the operator
  in favour of a direct fetch and the resolver guard.
- **List the refused files as known failures.** A refused toolguide leaves its guide unowned, and an
  unowned guide cannot be listed, so the gate would stay red; migration and a re-pin keep the list for residuals only.

## References

- `contracts/packs/schemas/procedure.schema.json`, `toolguide.schema.json`,
  `toolguide-guide.schema.json`; `contracts/packs/file-kinds.yaml` (the guide's `files` rule).
- `contracts/charter/schemas/ToolguideRegistration.yaml`, `Toolguide.yaml`;
  `contracts/charter/CHANGELOG.md`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/`: `Procedure.java`,
  `ProcedureStep.java`, `ProcedureReference.java`, `AntiPatternEntry.java`, `Guide.java`,
  `GuideBlock.java`, `GuideSection.java`, `Toolguide.java`.
- `.../packs/application/`: `GuideFormat.java`, `GuideFetcher.java`, `GuideFetchRefusal.java`,
  `ToolguideRegistrationService.java`.
- `.../packs/infra/storage/`: `ProcedureBinding.java`, `GuideMarkdown.java`,
  `MarkdownGuideFormat.java`, `Companions.java`, `CompanionContent.java`, `KindBinding.java`,
  `FileStems.java`, `FileSystemPackInspector.java` (validate mode reads the guides).
- `.../packs/infra/fetch/`: `HttpsGuideFetcher.java`, `GlobalAddressResolverProvider.java`,
  `GuardRefusals.java`, `AddressPolicy.java`, `GuideAllowlist.java`, `GuideFetchProperties.java`;
  `.../packs/infra/api/ToolguideController.java`, `ToolguideRegistrationBodyAdvice.java`.
- `service/deployable/src/test/java/.../HexagonalArchitectureTest.java` (`FETCH_ADAPTER_STANDS_ALONE`),
  `GeneratedConstraintTypesTest.java`.
- [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/),
  [ADR-0040](/adr/0040-content-form-and-payload-kinds/),
  [ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/); the
  [service build guide](/guides/service-build/) for the fetch configuration.
