# Mission Specification: R12: glossary contexts and terms

**Mission Branch**: `kitty/mission-glossary-contexts-and-terms-01M7B1PP` (lands on `feat/106-glossary-contexts`, then `main` by pull request)
**Created**: 2026-11-13
**Status**: Draft
**Input**: #106 (R12), the last box of slice A2 (#536). Kind `feature`, a model change, run as a full Mission (operator ruling at specify, 2026-11-12). Grounding: [glossary contexts grounding note](../../docs/development/notes/glossary-contexts-grounding.md).

## Intent Summary (confirmed by the operator, 2026-11-13)

A client of the charter contract reads vocabulary: the glossary contexts and terms a pack holds, and the vocabulary in force under an activation. Core's generated client after the cutover is one such client. A terminology gate or an editor plug-in that consumes Contextive is another.

Today the charter holds vocabulary only in core's `glossary_pack` shape:

- it serves no read for it;
- a term has no address.

This Mission replaces `glossary_pack` outright with [ADR-0049](../../docs/adr/0049-glossary-contexts-and-terms.md)'s glossary contexts and terms, with no reader of the old shape (charter, "the charter's model is canonical"). It serves the new kind by [ADR-0074](../../docs/adr/0074-the-remaining-cutover-reads-and-their-paths.md)'s paths (decisions 9 to 14), in a pack and under an activation. Under an activation it also serves:

- the terms in force across the activation's packs (ADR-0049 decision 10);
- two projections of them (ADR-0049 decision 15): the Contextive file and the avoid list.

A new ADR shapes those last three reads before they are built.

Decision Moments resolved at specify:

| Decision | Ruling |
| --- | --- |
| `DM-01M7B1QF8FQ38JNYVARN95AQ9F` | Context ids and term ids use the kernel's letter-first slug grammar. This Mission owns their identifier-bounds rows. |
| `DM-01M7B1Q1HW2KDEEW3MKCK2A2PT` | No upgrade of stored activations. A stored activation that holds `glossary_pack` is refused like any invalid stored document. |
| `DM-01M7B1Q4BPQB95EVQBRWN4Q4F8` | A context or term that is not held is `404 artifact_not_found`, and its detail says which segment missed. No new refusal code. |
| `DM-01M7B1Q77QRB2PNDG0CDBNVVH3` | Term ids for existing terms are minted from their surface form. The mapping is recorded for the open-packs migration. |
| `DM-01M7B7ZYK5H532Y4HR5FE1AGRX` | The projections are in scope: the Contextive file and the avoid list. |
| `DM-01M7B804ASKBSKS6TH817WYT7X` | A projection covers the contexts in force under an activation, and delivers the active-terms read. |
| `DM-01M7B809BWYR7HJE1HTRWM11BC` | Projections are contract reads. |
| `DM-01M7B80ENN0EZH860D9MFC4FYM` | This repository's own docs glossary (`site/.contextive/definitions.yaml`, ADR-0029) stays hand-authored. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Author vocabulary as glossary contexts (Priority: P1)

A pack author writes a glossary context: a named bounded context with a description, optional tags and `applies_to`, and its terms. Each term has an id, a name, a definition and a status, and optionally examples, aliases, words to avoid and links to other terms. The charter loads, validates and round-trips it. A pack still in the old `glossary_pack` shape is refused, and the refusal says why.

**Why this priority**: every read below depends on the model. The model change is the Mission's core, and it replaces the old shape outright.

**Independent Test**: register an example pack that holds a glossary context. Register a pack that holds the old shape. Round-trip the first.

**Acceptance Scenarios**:

1. **Given** a pack with `glossaries/spec-kitty-core.glossary.yaml` in the new shape, **When** it is registered, **Then** it is accepted, and its context and terms are held.
2. **Given** a pack with `glossary_packs/x.glossary-pack.yaml` in the old shape, **When** it is registered, **Then** it is refused as invalid content, and the refusal names the file.
3. **Given** a context whose id or a term id breaks the letter-first slug grammar (for example `Core`, `a--b` or `4-eyes`), **When** the pack is registered, **Then** it is refused as invalid content.
4. **Given** a context with two terms of the same id, **When** the pack is registered, **Then** it is refused.
5. **Given** any accepted glossary file, **When** it is written back, **Then** it has the same meaning (the whole-pack round trip, ADR-0036).

---

### User Story 2 - Read a pack's glossary contexts and terms (Priority: P1)

A client lists a pack's glossary contexts. It reads one context with its terms, then reads one term by the path that mirrors its address `term:<context>/<term>`.

**Why this priority**: this is #106's read, the cutover read R12, and the last read slice A2 owes.

**Independent Test**: register a pack with two contexts, list them, then read one context and one of its terms.

**Acceptance Scenarios**:

1. **Given** a pack that holds contexts `spec-kitty-core` (3 terms) and `qa` (2 terms), **When** the client lists the pack's glossary contexts, **Then** it gets both, each with its id, name, description, tags and term count.
2. **Given** the same pack, **When** the client lists the pack's artifacts filtered by kind `glossary`, **Then** it gets the same two contexts.
3. **Given** the same pack, **When** the client reads context `spec-kitty-core`, **Then** it gets the whole context, with every term.
4. **Given** the same pack, **When** the client reads term `mission` of context `spec-kitty-core`, **Then** it gets that term alone, with its id, name, definition, status and its optional fields when present.
5. **Given** a pack that holds no context `ghost`, **When** the client reads it or one of its terms, **Then** it is refused with `404 artifact_not_found`, and the detail names context `ghost`.
6. **Given** context `spec-kitty-core` without term `ghost`, **When** the client reads that term, **Then** it is refused with `404 artifact_not_found`, and the detail names term `ghost` in context `spec-kitty-core`.
7. **Given** no pack `nope`, **When** the client lists or reads any of these, **Then** it is refused with `404 pack_not_found`.

---

### User Story 3 - Read the same under an activation (Priority: P1)

A client reads the glossary contexts, a context and a term under an activation. It gets the version in force: the winning context across the activation's packs.

**Why this priority**: every artifact read has an activation twin (ADR-0074 decision 12). A client of a project reads through its activation, not a pack.

**Independent Test**: create an activation over a built-in pack and a project pack that both hold context `spec-kitty-core`, then read it under the activation.

**Acceptance Scenarios**:

1. **Given** an activation whose project pack overrides the built-in context `spec-kitty-core`, **When** the client reads that context under the activation, **Then** it gets the project pack's copy and the pack it came from.
2. **Given** the same activation, **When** the client lists glossary contexts under it, or filters its effective artifacts by kind `glossary`, **Then** each context appears once, as its winning copy.
3. **Given** a context the activation has deselected or denied, **When** the client reads it, **Then** the answer states that it is not in force, and why, as the other activatable kinds do.
4. **Given** a term or context no pack of the activation holds, **When** the client reads it, **Then** it is refused with `404 artifact_not_found`, and the detail names the missing segment.
5. **Given** an activation, **When** the client reads the activation, **Then** its links lead to the glossary reads.

---

### User Story 4 - Read the vocabulary in force and its projections (Priority: P2)

A terminology gate, an editor plug-in or a client reads the vocabulary in force under an activation. It reads every term in force, a Contextive definitions file of the contexts in force, and the avoid list: each word to avoid with the term to use instead.

**Why this priority**: the pack glossary is the one authored source of vocabulary (ADR-0049 decision 15). The projections are how gates and editors stop keeping their own lists.

**Independent Test**: create an activation over two packs whose contexts share a term name and one term URN, then read the active terms, the Contextive file and the avoid list.

**Acceptance Scenarios**:

1. **Given** an activation whose contexts in force hold 5 terms, **When** the client reads the active terms, **Then** it gets 5 terms, each with its `term:` address, its context and the pack it came from.
2. **Given** two contexts in force that both define a term named `Mission`, with different ids or contexts, **When** the client reads the active terms, **Then** both are returned, as two terms and not a conflict (ADR-0049 decision 12).
3. **Given** a project pack and the built-in pack that both define `term:spec-kitty-core/mission`, **When** the client reads the active terms, **Then** only the project's term is returned (ADR-0049 decision 13).
4. **Given** the same activation, **When** the client reads the Contextive projection, **Then** it gets a Contextive v1 definitions document. It has one context per context in force (name and domain vision statement) and the terms in force under each (name, definition, examples and aliases). A Contextive tool accepts it.
5. **Given** contexts in force whose terms carry `avoid` entries, **When** the client reads the avoid list, **Then** it gets each avoided word once per term that avoids it, with that term's address and name.
6. **Given** an activation with no glossary in force, **When** the client reads any of the three, **Then** it gets an empty result, not an error.

---

### Edge Cases

- A context whose `terms` list is empty is refused as invalid content: a context holds at least one term.
- `tags`, when present, holds at least one entry.
- A term `see_also` entry that is not a `term:` URN is refused.
- A `see_also` entry that names a term no pack holds is a broken reference, reported as other unresolved references are.
- A term id that is valid alone but repeated across two contexts is two terms (`term:a/x`, `term:b/x`).
- The same context id at the same pack level in two packs is a sibling conflict, refused at compose unless it is settled explicitly (ADR-0049 decision 13; ADR-0048 decision 9).
- A preset that names `activated_glossary_packs` is refused when the pack is loaded. A preset names `activated_glossaries`.
- A stored activation that holds `selected.glossary_pack` or `deactivated.glossary_pack` is refused, with no upgrade.
- A term id that would make the term path ambiguous (a `/` or a segment such as `terms`) cannot occur: the slug grammar refuses it.
- A deprecated term is still served, with its status. Filtering by status is left to the client.

## Domain Language

| Canonical term | Meaning | Avoid |
| --- | --- | --- |
| glossary context | One artifact of kind `glossary`: a ubiquitous-language bounded context with its terms | glossary pack (the old kind), glossary scope (core's retired scopes) |
| term | An item inside a glossary context, addressed `term:<context-id>/<term-id>` | entry, glossary term artifact (a term is not an artifact) |
| `glossary` | The kind token, path token and operator token | `glossary_pack`, `glossary-pack` |
| `glossary_context:<id>` | A context's URN (ADR-0053 decision 13). The URN token differs from the kind token on purpose. | `glossary:<id>` (core's retiring node kind) |
| avoid | The words a term says not to use; the one source of banned vocabulary | banned synonyms, synonyms to avoid |
| projection | A generated view of the vocabulary in force, never hand-edited | export, sync |

## Requirements *(mandatory)*

### Functional Requirements

| ID | Title | User Story | Priority | Status | Delivery | No-op passable? |
|----|-------|------------|----------|--------|----------|-----------------|
| FR-001 | Glossary context shape | As a pack author, I want a glossary context to be `{schema_version, id, name, description, tags?, applies_to?, terms}`, closed, with at least one term, so that vocabulary has ADR-0049's shape. | High | Open | [build] | no |
| FR-002 | Term shape | As a pack author, I want a term to be `{id, name, definition, examples?, aliases?, avoid?, status, see_also?}`, closed, with `status` one of `draft`, `active` or `deprecated` and `see_also` holding `term:` URNs, so that terms have an address and one avoid list. | High | Open | [build] | no |
| FR-003 | Ids on the kernel grammar | As a pack author, I want context and term ids to be letter-first kebab slugs of at most 64 characters, so that every id is one path segment. A context id must equal its file stem. A term id is unique within its context. A pack that breaks either rule is refused as invalid content. | High | Open | [build] | no: positive control is a valid id on the same fixture |
| FR-004 | Kind and layout | As a pack author, I want the kind token `glossary`, the layout `glossaries/<context-id>.glossary.yaml` and the fragment plural `glossaries`, so that the file family matches the kind. | High | Open | [build] | no |
| FR-005 | Old shape replaced outright | As a maintainer, I want every `glossary_pack` spelling to be replaced, with no reader, alias or upgrade beside the new one. That covers the kind enums, the selection, deselection, cascade and node kinds, presets (`activated_glossaries`), the charter and org-charter schemas, the manifest, the policy, the graph shard, the file-kind rules and the stored activation document. A pack, preset or stored activation in the old shape is refused, so that the model has one shape. | High | Open | [build] | no: the refusal of an old-shape fixture is the check, beside an accepted new-shape fixture |
| FR-006 | URNs | As a client, I want a context addressed as `glossary_context:<id>` and a term as `term:<context-id>/<term-id>`, both in the graph's URN kinds, and the `vocabulary` relation to target them, so that steps and mission types can link to a term. | High | Open | [build] | no |
| FR-007 | List a pack's contexts | As a client, I want `GET /packs/{packId}/artifacts/glossary` to list the pack's contexts with id, name, description, tags and term count, so that I can browse vocabulary. | High | Open | [build] | no |
| FR-008 | Kind filter | As a client, I want the pack's artifact list and the activation's effective list to accept kind `glossary` and list the contexts, so that a generic client finds them. | High | Open | [build] | no |
| FR-009 | Read a context | As a client, I want `GET /packs/{packId}/artifacts/glossary/{contextId}` to answer the whole context, so that I read a bounded context with its terms. | High | Open | [build] | no |
| FR-010 | Read a term | As a client, I want `GET /packs/{packId}/artifacts/glossary/{contextId}/terms/{termId}` to answer one term, so that I can resolve a `term:` address. | High | Open | [build] | no |
| FR-011 | Activation twins | As a client, I want each of FR-007, FR-009 and FR-010 to have its twin under `/activations/{activationId}` at the same suffix, answering the version in force with its pack and state, as the other activatable kinds do, so that a project reads through its activation. | High | Open | [build] | no |
| FR-012 | Refusals | As a client, I want an unknown pack to be `404 pack_not_found`, and a context or term that is not held to be `404 artifact_not_found` whose detail names the missing context or term, checked pack, then context, then term, so that I know which segment missed. | High | Open | [build] | no: positive control is the held term on the same fixture |
| FR-013 | Activation links | As a client, I want an activation's links to lead to its glossary reads, so that I discover them without building URLs. | Medium | Open | [build] | no |
| FR-014 | Active terms | As a client, I want to read every term in force under an activation, with its address, context and pack, so that I read the whole vocabulary in force (ADR-0049 decision 10). | Medium | Open | [build] | no |
| FR-015 | Term precedence | As a client, I want the active terms to follow ADR-0049 decisions 12 and 13, so that a clash is resolved by one rule. A shared name is two terms. The same `term:` URN across levels keeps the project, then org, then built-in copy, and the load warns. At the same level it is refused unless settled. | Medium | Open | [build] | no: the two-names-two-terms row is the positive control |
| FR-016 | Contextive projection | As an editor or docs tool, I want a Contextive v1 definitions document of the contexts and terms in force under an activation, so that I read vocabulary from the one authored source. | Medium | Open | [build] | no |
| FR-017 | Avoid projection | As a terminology gate, I want the avoid list of the terms in force under an activation, so that I refuse avoided words without a list of my own. Each entry is an avoided word, the address of the term that avoids it and that term's name. | Medium | Open | [build] | no |
| FR-018 | Decision record first | As a maintainer, I want a new ADR to shape the paths, bodies and media types of FR-014, FR-016 and FR-017 before they are built, so that the contract set that adds them is decided (ADR-0049 decision 10). The ADR goes through the adversarial review squad and operator approval. | High | Open | [build] | no |
| FR-019 | Contract evidence | As a maintainer, I want every new operation to have `@contract` scenarios, a contract example and its switch-gate row passing the ADR-0059 D1 gate, so that the read is proven, not only declared. | High | Open | [build] | no |
| FR-020 | Example packs, fixtures and examples follow | As a maintainer, I want charter's example packs, test fixtures and contract examples to carry only the new shape, so that the parity and round-trip checks run on the canonical model with nothing excluded. Term ids are minted from surface forms, and the surface-to-id map is recorded. | High | Open | [build] | no |
| FR-021 | Records and docs follow | As a maintainer, I want these to say what the service does: the decision in `docs/api/service-contract.md` (#106's criterion), the `### Breaking` CHANGELOG entry, the divergence register (DIV-012, DIV-106, DIV-211 and the lenient-conversions entry), the pack-ecosystem work breakdown item 4, the next-slices plan and roadmap rows, and the ADR-0049 and ADR-0074 built annotations. | Medium | Open | [build] | no |
| FR-022 | Open-packs draft | As the orchestrator, I want a draft issue for `spec-kitty-open-packs` in the Mission research (`open-packs#TBD-glossary-contexts`), so that open-packs migrates its four glossaries with the minted ids. | Medium | Open | [build] | no |

### Non-Functional Requirements

| ID | Title | Requirement | Category | Priority | Status |
|----|-------|-------------|----------|----------|--------|
| NFR-001 | Gates green | `./gradlew verifyAll` on Java 25 passes, with strict dependency verification and no new dependency. That includes the whole-pack round trip over charter's example packs, the contract checks, the style checks and the architecture gate. The docs checks pass too: `check_docs.py all`, markdownlint-cli2 and cspell. | Quality | High | Open |
| NFR-002 | No old token left | Zero occurrences of `glossary_pack`, `glossary-pack` or `GlossaryPack` remain in `contracts/`, `service/` and `packs/`, except where a test pins the refusal of the old shape, a CHANGELOG or change entry records the rename, or a planted corpus case refuses core's `glossary:` URN. Each remaining occurrence is listed in the occurrence map. | Maintainability | High | Open |
| NFR-003 | Every glossary read covered | 100% of the new operations (6 pack and twin reads plus the 3 activation reads) have at least one passing `@contract` scenario for success and one for each 404 path they raise. | Quality | High | Open |
| NFR-004 | Mutation floor kept | The packs and activations modules keep their configured mutation-testing floors. No floor is lowered. | Quality | Medium | Open |
| NFR-005 | Bounded projection | The three activation reads answer the 172-term example vocabulary within the service's existing read budget. No read walks a pack more than once per request. | Performance | Medium | Open |

### Constraints

| ID | Title | Constraint | Category | Priority | Status |
|----|-------|------------|----------|----------|--------|
| C-001 | Canonical model, no legacy | No deprecation window, no reader of the old shape, no stored-document upgrade (charter, 2026-11-10). | Technical | High | Open |
| C-002 | Implement after slice C | Implementation starts only after slice C (#467) merges into `main`, or rebases onto it (operator, 2026-11-13). Specify and plan run now. | Process | High | Open |
| C-003 | Contract versioning | The 0.x line may break on a minor, with a `### Breaking` CHANGELOG entry (ADR-0022). The contract's own baseline rules hold. | Technical | High | Open |
| C-004 | Out of scope | The following are out: core's glossary consumers (#332 to #335, #518); the open-packs content itself (a draft issue only); this repository's docs glossary (ADR-0029); filtering by `applies_to`, which stays advisory (ADR-0049 decision 14). | Business | High | Open |
| C-005 | Shared identifier grammar | Ids use the kernel `IdentifierGrammar` constants already on `main`. Whichever of this Mission and epic #549's #551/#552 lands second rebases onto the other. | Technical | Medium | Open |
| C-006 | Process | Signed commits. No merge, no push to `main`, no force-push of a pushed branch. Gradle runs in the foreground with `--max-workers=1`, one run at a time. | Process | High | Open |

### Key Entities

- **Glossary context**: an artifact of kind `glossary` in a pack, at a pack level. It holds its terms, and its URN is `glossary_context:<id>`.
- **Term**: an item of one context, unique by id within it. Its URN is `term:<context-id>/<term-id>`, and it links to other terms by `see_also`.
- **Activation**: selects packs and artifacts. Under it, one context copy wins per id, and one term per URN.
- **Contextive projection**: a generated document of the contexts and terms in force.
- **Avoid list**: a generated list of avoided words and the terms that avoid them.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A client reads any term of the example vocabulary by its address in one request, in a pack and under an activation. — [build] · no-op passable: no
- **SC-002**: #536's glossary criterion holds: glossary contexts are served in a pack and under an activation by ADR-0074's paths, and every new operation passes the switch gate. — [build] · no-op passable: no
- **SC-003**: The old shape is gone from the contract, the example packs and the fixtures. A pack in the old shape is refused in 100% of registrations. — [build] · no-op passable: no
- **SC-004**: A terminology gate or editor reads the vocabulary in force without a hand-kept list: the avoid list and the Contextive file match the active terms one to one. — [build] · no-op passable: no
- **SC-005**: #106 and #536 are closed with evidence, #94 is ticked, and an open-packs draft issue for the content migration exists in Mission research. — [build] · no-op passable: no

## Assumptions

- No deployment holds stored activations today, so refusing an old-shape stored document loses no data (Q1 ruling).
- The surface forms of the example-pack terms all yield distinct letter-first slugs within their context. Any that do not get a hand-picked id, recorded in the surface-to-id map.
- This repository's synced governance packs (`packs/internal`, `packs/qa`) carry the old shape until open-packs migrates. The charter CLI's own use of them may warn until then. Charter's gates read only charter's example packs (ADR-0070), so they are not affected.
