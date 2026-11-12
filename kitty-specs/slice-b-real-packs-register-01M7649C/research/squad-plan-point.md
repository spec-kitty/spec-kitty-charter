# Plan-point adversarial squad, 2026-11-11

Three read-only lenses over spec.md, plan.md, research.md and data-model.md, each loading its profile YAML:
Architect Alphonso (model, contract, decision record), Reviewer Renata (brownfield integrity, test quality), Debugger
Debbie (concurrency, #490). **Verdict: PROCEED-WITH-FOLDS (3/3).** Every HIGH and MEDIUM finding is folded into the
plan; LOW findings are folded or dropped with a reason. Nothing is filed (issue-tracker Rule 1).

## Dispositions

| # | Lens | Sev | Finding | Disposition |
|---|---|---|---|---|
| A1 | model | HIGH | Dropping excluded files contradicts ADR-0065 D1 and the contract's "holds exactly the staged tree"; the reason never reaches the registrant. | **Folded.** Each file not held is reported in the registration's `diagnostics` with a new warning code and the rule's reason; ADR-0065 D1 and both contract texts amended; `### Changed` in the fragment. (FR-021) |
| R1 | brownfield | HIGH | Registration commits the staged tree as is (`PackRegistrationService:604-606`, `PackCommitStore.copyTree`), so excluded files would be held, undigested and stale. | **Folded.** Excluded files are removed from the staged tree before the revision and the commit; a store test pins that the live directory holds none. (FR-005) |
| A2/R2 | both | HIGH | The gate buckets an excluded or ignored file as red `other:<segment>`. | **Folded.** The census reads Excluded from `PackFileKind` and gives it a checked EXCLUDED status (absent from the written tree); a `.txt` control stays red; nothing added to `expected-red.yaml` or the gate's `exclusions.yaml`. |
| A3 | model | HIGH | Two 200 media types on one operation do not fit the generated `interfaceOnly` interface or ADR-0004. | **Folded.** A spike is the first work package; JSON form is a named component `PackDocumentRead`; escalate if it needs an unchecked cast. |
| A4 | model | HIGH | Strong ETag shared by two representations; Spring's implicit 304 undeclared; no `Vary`. | **Folded.** `Vary: Accept` and a declared `304` with `If-None-Match`; the shared strong ETag (the ruling's "pack revision as ETag") is recorded as a deliberate call in the new ADR. |
| D1 | concurrency | HIGH | Torn reads: a walk spanning both renames lists the old tree and reads the new one without an exception. | **Folded.** Re-read keyed on directory identity (`fileKey`) checked before and after the read, not on exceptions. |
| D2 | concurrency | HIGH | Caller list misses `DomainRepositoryService.over/resolve`, `FilesystemPackStore.existingDirectory`, `declaresId`, `StorageRootLocations.declaredLevel`, `CompositionFactsCache`. | **Folded.** The resolver sits at the location-to-directory step in all three `over()` copies, `declaredLevel` and `addPack`; whole loads run inside the identity-checked re-read. |
| D3 | concurrency | HIGH | `ConsistentRead` refuses `stale_input` (500) whenever the generation is odd, so verified reads still 500 in the window. | **Folded as a mission call for the operator:** a read that starts and ends in the same odd generation with equal digests is accepted (writers are serialized; one odd window moves old to new once). Its own commit, separable. |
| R3/A10 | both | MED | Case variants (`Readme.md`, `Pack.md`, `PROVENANCE.MD`) slip past exact rules into the fallback or `unmodelled`. | **Folded.** A file whose exact class is excluded-by-fallback or unclassified but whose case-folded class is a `files` or `retired` rule is refused by name; the fallback matches its extension case-insensitively. |
| R4 | brownfield | MED | Tests whose meaning flips are missing from research §A. | **Folded** into the WP lists (WholePackStoreTest:335-351, :543-551; ExamplePacksTest:80; WholePackExamplesRoundTripTest:176-179; CompanionFilesTest:170-185; CompanionCaseFoldTest:40,63; GeneratedPackTree verdicts; OpenPacksDriftTest:62). |
| R5/A11 | both | MED | FR-004/005 and SC-001/002 not proven on the HTTP path. | **Folded.** Scenarios in `whole-pack-registration.feature`: excluded files register with a diagnostic, retired files 422 `retired` (never `unmodelled`), `notes.txt` stays `unmodelled`, `active_charter` pinned, `doc` link present or absent; a `loadStaged` nest in RetiredFilesTest. |
| R6 | brownfield | MED | `.gitkeep` in real packs. | **No change needed:** on open-packs `main` the only hidden file is `foundation/missions/plan/templates/.gitkeep`, already excluded; D1 stops its refusal. A dry classification of open-packs `main` runs as research. |
| R7 | brownfield | MED | Fingerprint recapture must be proven. | **Folded.** Recapture with the trigger "pack document moved to README.md (#451)"; the fixture gains an ignored note, an excluded README and a skill body; DIGESTED changes only by the swap. |
| A5 | model | MED | ADR-0042 decision 8 alone is not enough. | **Folded.** New ADR (number chosen at write time) for the calls beyond the rulings; dated notes on ADR-0042 (D6, D8, D9, follow-ups), ADR-0036 (D6, table rows), ADR-0031 (#206), ADR-0065 (D1), ADR-0019 (packs map), ADR-0040 (file-kinds sections). |
| A6 | model | MED | `### Breaking` must name core's seam change and that stored packs with retired shapes become unreadable. | **Folded** into the fragment. |
| A7 | model | MED | Negotiation too coarse. | **Folded.** RFC 9110 §12.5.1: quality, then specificity; ties go to JSON; `text/*` answers Markdown; 406 carries the shared headers. |
| A8 | model | MED | No mapping for `org-charter.yaml`'s fields. | **Folded** into the how-to and the open-packs draft. |
| A9 | model | MED | Ignored vs companion claim can disagree across components. | **Folded.** One agreement test over load, validate mode, digest, census and registration for a skill body, a toolguide guide and an ignored note. |
| A11b | model | MED | Real READMEs never went through the strict codec (cave-kitty has `# ` lines). | **Folded.** Run the codec over open-packs `main` READMEs once as research; needed edits go into `open-packs#TBD-readme-doc`. |
| D4 | concurrency | MED | A bounded retry only makes the flake rarer. | **Folded.** Count only attempts where the identity changed (progress); cap 8; at the cap refuse `stale_input`, never `pack_unreadable`; an attempt with unchanged identity that fails refuses as today. |
| D5 | concurrency | MED | Listing change under-specified. | **Folded.** Half-swapped and stale entries use `tryLock`; a held half-swapped entry is listed through the resolver with no WARN; asides beside read-only linked targets are not served; `InterruptedCommitRepairTest:237-242` updated. |
| D6 | concurrency | MED | The test hook reimplements the swap. | **Folded.** A package-private phase hook inside the real `MOVE_ASIDE` (`afterAside`, `afterLive`, `afterHide`); `SlowSwapCommitStores` uses it. |
| D7 | concurrency | LOW | Create: an empty reserved directory reads as an empty pack. | **Folded.** An empty entry with no aside reads as absent (`pack_not_found`). |
| D8 | concurrency | LOW | Classification under the aside's name; links through the pack's own path. | **Folded** (pass the live name in); the link case is documented, not handled. |
| R8 | brownfield | LOW | `ignored` must stay out of `matchingRules` and the overlap check. | **Folded** as an invariant test. |
| R9 | brownfield | LOW | `missions/mission-steps/README.md` comment. | **Folded.** |
| R10 | brownfield | LOW | Slicing: split IC-01. | **Folded** (README document; exclusion semantics). |
| A12 | model | LOW | Naming: `PackDocumentRead`; one term for excluded. | **Folded.** The contract schema is `PackDocumentRead`; "excluded" is the one term (an `ignored` rule yields an excluded file). |
| A13 | model | LOW | `doc` vs IANA `describedby`. | **Folded:** keep `doc` (ruling); the table row records why. |
| A14 | model | LOW | Frontmatter only in the Markdown form. | **Folded** into the schema description. |
