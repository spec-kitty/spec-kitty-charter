# Mission Specification: Slice B: real packs register as they are

**Mission Branch**: `kitty/mission-slice-b-real-packs-register-01M7649C` (lands on `feat/466-slice-b-real-packs-register`, then by pull request on `main`)
**Created**: 2026-11-11
**Status**: Draft
**Input**: The operator's mission prompt for slice B (#466) and the operator rulings of 2026-11-11, recorded on
[#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466#issuecomment-6805477785), #451, #431, #490 and #432
and in `docs/plans/next-slices-2026-11-11.md` (PR #498).

## Intent Summary

Brief-intake mode. The brief is the operator's mission prompt, which carries binding rulings with objective,
constraints, approach and acceptance criteria, so no discovery questions were asked: the rulings are the confirmed
intent. Calls this mission makes beyond the rulings are listed under "Calls made by the mission" and go to the
plan-point review squad and the operator.

- **Primary actor:** a pack author or an organization that publishes packs in spec-kitty-open-packs, and core (the
  consumer) that registers those packs through the service.
- **Trigger:** `POST /packs` or `PUT /packs/{packId}` with a real open-packs pack, after open-packs has applied the
  two content changes this mission drafts.
- **Outcome:** the pack registers without hand edits. Its `README.md` is held as the pack's document, round-trips,
  and is served at `GET /packs/{packId}/doc`.
- **Rule that always holds:** the charter's model is canonical. A retired shape (`pack.md`, `PROVENANCE.md`,
  `org-charter.yaml`, `active_charter`) is refused by name, is never read beside the new shape, and appears in tests
  only to exercise that refusal.
- **Boundary:** pack content changes land in spec-kitty-open-packs, drafted here as `open-packs#TBD-readme-doc` and
  `open-packs#TBD-org-charter-preset`. Slice C (#438, #450) and the quick-win slices (#492 to #497) are out of scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A real pack registers with its README as its document (Priority: P1)

A pack author keeps one `README.md` at the pack root, with a `## Provenance` section where `PROVENANCE.md` used to be.
They may keep other Markdown notes in the pack. They register the pack and it is accepted; the README is held and
round-trips, the other notes are left out with a stated reason, and nothing is refused as unmodelled.

**Why this priority**: no real pack registers today, and core can use nothing else the service serves until one does.

**Independent Test**: register charter's own example packs, which carry a `README.md` with a provenance section and an
author note elsewhere, and read them back.

**Acceptance Scenarios**:

1. **Given** a pack whose root holds `README.md`, **When** it is registered, **Then** the registration succeeds and
   the README is held as the pack's document.
2. **Given** a pack that holds a Markdown file no rule models (for example `docs/notes.md`), **When** it is registered,
   **Then** the registration succeeds, and the file is neither held nor refused.
3. **Given** a pack that holds a file an exclusion rule names (for example a mission template folder's `README.md`
   or `plan-field-declaration.yaml`), **When** it is registered, **Then** that file is not a refusal.
4. **Given** a pack that still ships `pack.md` or `PROVENANCE.md` at its root, **When** it is registered, **Then** it
   is refused with a violation that names the file as retired and says where its content moved; never `unmodelled`.
5. **Given** a registered pack, **When** the whole-pack round trip runs, **Then** the README compares equal in meaning.

---

### User Story 2 - A client reads the pack's documentation (Priority: P1)

A client (core, a catalogue page, a person with curl) reads a pack's documentation, as raw Markdown or as a JSON
envelope with the title and body, and can cache it by the pack revision.

**Why this priority**: the README is now the pack's one document; holding it without serving it leaves clients
reading the file system.

**Independent Test**: contract scenarios against charter's example packs.

**Acceptance Scenarios**:

1. **Given** a pack with a document, **When** a client sends `GET /packs/{packId}/doc` with `Accept: text/markdown`,
   **Then** it gets `200` with the README's Markdown and the pack revision as `ETag`.
2. **Given** the same pack, **When** the client accepts `application/json`, **Then** it gets `200` with `title` (the
   H1), `body` (the Markdown below it) and `_links` with `self` and `pack`, and the same `ETag`.
3. **Given** a pack with no document, **When** the client reads `/doc`, **Then** it gets `404` with a dedicated code.
4. **Given** an unknown pack, **When** the client reads `/doc`, **Then** it gets `404 pack_not_found`.
5. **Given** a pack, **When** the client accepts only a media type the read cannot produce (for example `image/png`),
   **Then** it gets `406`.
6. **Given** a pack with a document, **When** a client reads the pack resource, **Then** its `_links` carry `doc`;
   a pack without a document carries no `doc` link.

---

### User Story 3 - An organization requires presets without org-charter.yaml (Priority: P1)

An organization that used `org-charter.yaml` to impose activations on its projects expresses the same intent with an
activation preset whose items carry the must-have mark, plus `policy.yaml` levels, which the service already enforces.
A pack that still ships `org-charter.yaml`, or a descriptor that still names `active_charter`, is refused by name.

**Why this priority**: twelve open-packs packs ship `org-charter.yaml`; without this none of them registers.

**Independent Test**: a test that classification, validate mode, load and registration agree on a retired
`org-charter.yaml`, plus the how-to's recipe exercised on an example pack's preset.

**Acceptance Scenarios**:

1. **Given** a pack that ships `org-charter.yaml`, **When** it is classified, validated, loaded or registered, **Then**
   all four name it as retired with the same reason.
2. **Given** a `pack.yaml` that declares `active_charter`, **When** it is read, **Then** the key is refused as an
   unknown key of the closed descriptor.
3. **Given** the documentation, **When** an organization looks for how to enforce presets on its projects, **Then** a
   how-to describes the preset plus policy recipe.

---

### User Story 4 - Reads stay correct while a pack is replaced (Priority: P2)

A client reads a pack (its revision, an item, a listing) while another client replaces it. The read answers the last
committed state or the new one, never `500 pack_unreadable`.

**Why this priority**: a concurrent replace can answer a 500 to a valid request (#490), and the concurrency test
flakes on it.

**Independent Test**: a deterministic test that pauses a replace between its two renames and reads the pack.

**Acceptance Scenarios**:

1. **Given** a replace paused after the live directory moved aside and before the new one moved in, **When** a reader
   reads the pack's revision or content, **Then** it gets the old revision, without waiting for the writer.
2. **Given** a pack whose live directory is missing and no aside exists, **When** it is read, **Then** the read refuses
   as it does today.
3. **Given** a replace racing puts with `If-Match`, **When** the concurrency test runs, **Then** it passes without any
   test-only retry.

---

### User Story 5 - The pack vocabulary says what is built (Priority: P3)

A contributor reading `file-kinds.yaml` learns that text companions exist (#192) and that there is no standalone
`text` kind.

**Independent Test**: reading the comment; the table loads as before.

### Edge Cases

- A `pack.md` below the pack root (`docs/pack.md`) is not retired: it is unmodelled Markdown, excluded.
- A `README.md` below the pack root (for example under `missions/` or `assets/`) keeps its existing exclusion and is not
  the pack document.
- Markdown that a payload rule claims (an asset, a template, a mission template) stays a payload; Markdown under a
  retired family stays retired; a companion that a kind's descriptor names (a skill body) is still claimed.
- A README with a carriage return, a byte order mark or an ambiguous heading is refused as `pack.md` was, never
  normalized.
- A README with no H1: the JSON read omits `title`.
- A request with no `Accept` header, or `*/*`, gets the JSON form.
- A pack replaced twice in quick succession: a reader that meets a vanished aside re-resolves and reads the live
  directory.
- A linked (symlinked) pack: the aside sits beside the link's real target and the fallback follows it.

## Requirements *(mandatory)*

### Functional Requirements

| ID | Requirement | Status | Delivery | No-op passable? |
|---|---|---|---|---|
| FR-001 | `README.md` at the pack root is the pack's document: classified as `pack-document`, held in the pack, written canonically, digested in the pack-level tail where `pack.md` was, and read the same way by the load and by validate mode. (#451) | Proposed | [build] | no |
| FR-002 | `pack.md` at the pack root is retired: refused by name with a reason that names `README.md`, in classification, load, validate mode and registration. (#451) | Proposed | [build] | no |
| FR-003 | `PROVENANCE.md` at the pack root is retired the same way, with a reason that names the README's provenance section. (#451) | Proposed | [build] | no |
| FR-004 | A Markdown file that no `files`, `exclusions`, `retired` or `payloads` rule claims, and that no kind claims as a companion, is excluded with a stated reason: not held, not served, not refused, not digested. (#451) | Proposed | [build] | no |
| FR-005 | A registration does not refuse a file that an exclusion rule names; the file is not held. (#451) | Proposed | [build] | no |
| FR-006 | `GET /api/v1/packs/{packId}/doc` answers the document by content negotiation: `text/markdown` gives the document's Markdown; `application/json` (also an absent `Accept` or `*/*`) gives `{title, body, _links: {self, pack}}`; both carry the pack revision as `ETag`. (#451) | Proposed | [build] | no |
| FR-007 | The doc read answers `404` with a dedicated refusal code for a pack with no document, `404 pack_not_found` for an unknown pack, and `406` for an `Accept` it cannot produce. (#451) | Proposed | [build] | no |
| FR-008 | The pack resource carries a `doc` link when, and only when, the pack has a document; `doc` is a registered link relation. (#451) | Proposed | [build] | no |
| FR-009 | `org-charter.yaml` is retired: its `files` rule and schema are removed, a `retired` rule refuses it by name, and the validate-mode `org_charter_schema_error` finding is removed. (#431) | Proposed | [build] | no |
| FR-010 | `active_charter` is removed from the pack descriptor: a `pack.yaml` that declares it is refused as an unknown key. (#431) | Proposed | [build] | no |
| FR-011 | One test shows that classification, validate mode, the load and registration agree on a retired `org-charter.yaml`. (#431) | Proposed | [build] | no |
| FR-012 | A how-to explains how an organization enforces presets on its projects with a governing preset plus `policy.yaml`. (#431) | Proposed | [build] | no |
| FR-013 | A reader that finds a pack's live directory missing while exactly one `.pack-old-*` aside exists reads the aside; a reader that finds neither refuses as today; hot reads take no read lock. (#490) | Proposed | [build] | no |
| FR-014 | A deterministic test pauses a replace between its two renames and shows a reader gets the old revision. (#490) | Proposed | [build] | no |
| FR-015 | `RegistrationConcurrencyTest.aReplaceRacingPutsWithIfMatchLeavesOneLinearETagChain` passes through the code fix, with no test-only retry. (#490) | Proposed | [build] | no |
| FR-016 | The `text` content-form comment in `file-kinds.yaml` and the schema docs says text companions exist (#192) and no standalone `text` kind does. (#432) | Proposed | [build] | no |
| FR-017 | ADR-0042 decision 8 is amended to name `README.md`, with the retirements of `pack.md`, `PROVENANCE.md` and `org-charter.yaml` recorded. | Proposed | [build] | no |
| FR-018 | The contract change carries scenarios, examples and a `contracts/charter/changes/` fragment: additive, with the retired files and key under `### Breaking` (ADR-0022). | Proposed | [build] | no |
| FR-019 | Charter's example packs carry the canonical shape: a `README.md` with a provenance section, governing presets in place of any org charter, no `active_charter`. | Proposed | [build] | no |
| FR-020 | Two open-packs drafts (`open-packs#TBD-readme-doc`, `open-packs#TBD-org-charter-preset`) are written into the mission research directory. | Proposed | [build] | no |
| FR-021 | A registration reports each file it does not hold (an excluded file) as a warning diagnostic naming the file and the rule's reason; the service holds exactly the staged pack less those files. (squad A1, R1) | Proposed | [build] | no |
| FR-022 | A root file that differs only in letter case from the pack document or a retired file (`Readme.md`, `Pack.md`, `PROVENANCE.MD`) is refused by name, never excluded or `unmodelled`. (squad R3) | Proposed | [build] | no |
| FR-023 | A verified read that meets a replace in progress answers the last committed state or the new one, not `500 stale_input`, when its input did not change while it read. (squad D3; mission call for the operator) | Proposed | [build] | no |
| FR-024 | The doc read declares `Vary: Accept` and answers `304` to a matching `If-None-Match`; negotiation follows quality, then specificity; a tie goes to the form the client lists first in `Accept` (doc-read spike, accepted 2026-11-11); no `Accept`, `*/*` and `application/*` answer JSON; `text/*` answers Markdown. (squad A4, A7) | Proposed | [build] | no |

### Non-Functional Requirements

| ID | Requirement | Threshold | Status |
|---|---|---|---|
| NFR-001 | The swap-window fix adds no lock to hot reads. | 0 lock acquisitions on the revision and item read paths; a reader during a paused swap returns within 1 s | Proposed |
| NFR-002 | The concurrency test is stable. | 20 consecutive local runs of `RegistrationConcurrencyTest` pass | Proposed |
| NFR-003 | The gates pass. | `./gradlew verifyAll` on Java 25, `:packs:wholePackRoundTrip`, `check_docs.py all`, markdownlint-cli2 and cspell all green | Proposed |
| NFR-004 | No parity or round-trip check loses a field. | 0 fields excluded from a parity or round-trip check; `expected-red.yaml` gains no entry | Proposed |

### Constraints

| ID | Constraint | Status |
|---|---|---|
| C-001 | Canonical model only: no deprecation window, no reader of `pack.md`, `PROVENANCE.md`, `org-charter.yaml` or `active_charter` beside the new shape. | Fixed |
| C-002 | Old shapes appear in tests only in error-handling tests of their named refusals. | Fixed |
| C-003 | Do not touch slice C (#438, #450) or the quick-win slices (#492 to #497); coordinate shared files (refusal codes, `file-kinds.yaml`, `changes/`) through the orchestrator. #381 is taken over from #496 by retiring the schema. | Fixed |
| C-004 | No write to spec-kitty-open-packs; its content changes are drafts with `open-packs#TBD-<slug>` placeholders. | Fixed |
| C-005 | Squad findings below MEDIUM are folded or dropped, never filed (`docs/development/issue-tracker.md`). | Fixed |
| C-006 | Gradle runs in the foreground, one at a time, with `--max-workers=1`. | Fixed |

## Calls made by the mission

These go beyond the letter of the rulings and are put to the plan-point squad and the operator:

1. **Exclusions are not refused (FR-005).** The ruling names unmodelled Markdown; #451's acceptance also needs the
   already-excluded `missions/*/templates/README.md` and `plan-field-declaration.yaml` to register. So no exclusion
   is refused, not only Markdown.
2. **Unmodelled Markdown is a fallback, not an exclusion glob (FR-004).** A `**/*.md` exclusion would come before the
   retired rules and the payloads, and would capture skill bodies. The fallback is consulted last, and a companion
   claim wins over it.
3. **JSON `body` is the Markdown below the H1 (FR-006):** the preamble and sections in canonical form, without the
   frontmatter and the title. The raw form keeps everything.
4. **No `Accept`, or `*/*`, answers JSON (FR-006).**
5. **The dedicated code is `pack_document_not_found` (FR-007).**
6. **`active_charter` gets the closed descriptor's unknown-key refusal (FR-010),** not a retired rule of its own: it
   is a key, not a file.
7. **Excluded files are reported, not silently dropped (FR-021),** and removed from the staged tree before the commit,
   so ADR-0065's "holds exactly the pack that was sent" becomes "less the files it reports as not held".
8. **The doc read's two representations share the pack revision as a strong `ETag`,** as the ruling says, with
   `Vary: Accept`; RFC 9110 prefers distinct strong validators per representation, so this is recorded as a deliberate
   call in the new ADR.
9. **`ConsistentRead` accepts a read during a swap when its input did not change (FR-023).** Without it, verified reads
   still answer `500 stale_input` in the window the ruling closes. It is a separate, revertible change.

The plan-point squad's dispositions are in `research/squad-plan-point.md`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Charter's example packs, in the canonical shape, register through `POST /packs` and `PUT /packs/{packId}`
  with 0 refusals. — [build] · no-op passable: no
- **SC-002**: A pack carrying each retired file is refused with 1 named violation per file and 0 `unmodelled`
  violations for it. — [build] · no-op passable: no
- **SC-003**: The pack document read answers all 6 acceptance scenarios of User Story 2 in the contract scenarios. —
  [build] · no-op passable: no
- **SC-004**: A reader during a paused replace gets the old revision in 100% of runs. — [build] · no-op passable: no
- **SC-005**: Once open-packs applies the two drafts, the usability run's registration checks pass with no hand edits;
  under ADR-0070 that proof runs in open-packs CI against a pinned charter release. — [build] · no-op passable: no

## Key Entities

- **Pack document**: the pack's one documentation file, `README.md` at the pack root; optional frontmatter, an optional
  H1 title, a preamble and H2 sections.
- **Retired file**: a file of a layout the model no longer has; refused by name with a reason.
- **Excluded file**: a file that is not pack content; neither held nor refused, with a stated reason.
- **Governing preset**: an activation preset with at least one must-have item; with `policy.yaml` it replaces the org
  charter.
- **Aside**: `<name>.pack-old-*`, the previous live directory of a pack during a replace.

## Assumptions

- open-packs applies both drafts before the usability run is repeated; until then real packs are still refused, now by
  name.
- The usability script's `unmodelled` workaround is retired with this change; the run is green only after open-packs
  applies the drafts.
- Core reads `pack.md` and `org-charter.yaml` today; that divergence is recorded, not reconciled (core aligns later).

## Dependencies

- Slice A (`main` as of 8c42f52) for the read checks; ADR-0071 for item ETags.
- #496 holds #381 for this mission.
