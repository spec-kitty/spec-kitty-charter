# #260 ADR research (architect-alphonso, read-only)

## 0. Numbering trap
The issue's "ADR-0043 decisions 8, 9, 10" are **ADR-0042's** (docs/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest.md). ADR-0043 (#194) has decisions 1-7 only. Source of the mix-up: DM-01M60HM8 says "ADR number for this mission is 0043 (0041 #227, 0042 #194)"; the numbers landed swapped. WP08 (kitty-specs/pack-level-files-round-trip-01M60H1A/tasks/WP08-pack-document-markdown.md) repeats "ADR-0043 decision 8".

## 1. ADR-0042 decisions 8-10 (verbatim) and lines to change
- d8, 0042:293-301: "### 8. `pack.md` uses #194's `markdown` content form (split off to #260)" ... "`pack.md` is held through the `markdown` content form that sibling #194 builds in its first pull request (`DM-01M60HM8BV7CE17HYE1QS829K4`, which supersedes this mission's earlier `bytes` choice). This mission builds no form of its own. The `pack.md` work is not built here: #194's pull request had not opened, so the operator split it off to #260 on 2026-10-28, and #195 closes on the scope it delivered. Until #260 lands, `other:pack.md` stays on the gate's expected-red list."
- d9, 0042:303-318: digested order 1 pack.yaml; 2 bound kinds + companions; 3 root shards + drg/fragment.yaml; 4 assets then templates; "5. the pack-level tail, in one ordered step: the presets in path order, then `pack-manifest.yaml`." 0042:313 "When `pack.md` lands (decision 8), it joins the tail between the presets and the manifest. A pack with no presets and no manifest keeps its exact digest list and fingerprint. A pack that holds them gets a new `Charter-Source-Fingerprint` and ETag once". Constants: `PresetFiles.PRESETS`, `PackFileSet.MANIFEST` (0042:317).
- d10, 0042:320-332: presets slot sorted by id; manifest on `LoadedPack`, not in `Pack`; 0042:330-332 "Asset files (ADR-0040), presets, and the pack document to come are three pack-level slots beside the kind map. The rule of three has fired. Extracting them into one `PackLevelContent` value is a follow-up on epic #186. It is not done here, because #194 edits `Pack` at the same time."

Stale/pending in ADR-0042: 47-48 (Status: "`pack.md` is split off to #260, blocked on #194"); 301; 313; 330 ("to come"); per-kind table 338-342 lacks a `*/pack.md` row; 425 ("a third is coming"); 443-444 follow-up #260. 397 ("pack.md diverge from nothing") and 512-515 (bytes alternative) stay. Note 466-468 open question 2 says "Proposed until accepted" though Status is Accepted (pre-existing inconsistency).

ADR-0037: 91-92 "**Decision 4 ... built for presets and the manifest; `pack.md` is split off to #260 (blocked on #194).**"; 95-96 "the pack document is the third to come"; 162-164 "*(Built by #195 ... `pack.md` is split off to #260 (blocked on #194). See the amendment under Status.)*".
ADR-0043: no pending qualifier on pack.md; only 369-370 ("one purpose-built scanner serves the guides and `pack.md`") - still true. Line 34 "pending" = acceptance, unrelated.

## 2. Invariants
- ADR-0036 d2 "Other files" 0036:178-179 (original): Markdown = frontmatter as YAML, "body as text, with line endings normalized to LF and one final newline"; per-kind row 0036:352 `*.md (pack.md, ...)` same. Superseded in part by ADR-0040 (0036:182-185, 353): form is the rule's content_form. The LF-normalising wording is now contradicted by #194's refusal and still sits un-annotated for pack.md.
- ADR-0036 d5 (0036:253-281): pack inconsistency filed in open-packs; "No reader rule, normalization or exemption keeps the inconsistent shape"; known-failure list until re-pin.
- ADR-0040 amendment 2026-10-29 (#194), 0040:84-129: a markdown `files` rule's schema is a **structure schema** over `{frontmatter?, title?, preamble, sections:[{title,content}]}` (so pack.md needs one, e.g. a new pack-document schema). Equality = model equality; frontmatter by `Map.equals` of loaded YAML; 0040:98-99 "full ADR-0036 numeric and empty-versus-absent equality of the frontmatter is left to #260, where a non-canonical reader first compares two separately-authored documents" (an open decision #260 inherits). "the blank lines between blocks and the final newline are not part of the model." Refused: unclosed fence, carriage return, BOM, misplaced H1, indented H1/H2, empty heading, tab after #, closing #, setext. Writer is canonical and self-verifying. 0040:122-129 "Admitted, not yet held ... The first consumer (#260 for `pack.md`) wires the store read and write, the digest and validate mode"; markdown rule must stay disjoint from exclusions/payloads (`PackFileKindTest`).
- ADR-0040 d9 annotation 0040:296-298 and amendment 0040:62-70: pack.md joins tail between presets and manifest.
- ADR-0039 (as amended by #194, 0039:65-97): companion owns no classified path except its own rule; 0039:91 cites pack.md as the guarded case (stays true once `*/pack.md` is a files rule - strengthens it).

## 3. DM-01M60HM8BV7CE17HYE1QS829K4
kitty-specs/pack-level-files-round-trip-01M60H1A/decisions/DM-01M60HM8BV7CE17HYE1QS829K4.md:16,24,28. Q: "Does pack.md stay a bytes payload?" Final answer: "No: pack.md consumes #194's markdown content form (supersedes the specify decision pack_md_form). The pack.md work package is sequenced last, depends on #194 PR-A merging, and rebases onto it; until then other:pack.md stays expected-red and only tests may stub the form. ADR number for this mission is 0043 (0041 #227, 0042 #194)." Rationale: "one owner per content form (#194 builds markdown); avoids a second pack-level bytes kind."

## 4. New ADR or amendments?
Amendments suffice: d8 is already decided in ADR-0042 (Accepted -> dated amendment under Status + annotations, house style). Amend ADR-0042 (d8 done, d9 tail, d10 slot, per-kind row), ADR-0037 (d4), ADR-0040 (#260 is the first held files-rule markdown; frontmatter equality decision), ADR-0036 (pack.md row / LF wording). A new ADR only if #260 makes a substantive new decision (pack.md structure schema shape, frontmatter numeric/empty equality).
Stale outside ADRs:
- docs/architecture/logical-model.md:174 "| `pack.md` | none | Planned: held through the `markdown` content form that #194 builds. Not modelled yet. |" (also 167 calls ADR-0042 "proposed").
- service/packs/src/test/resources/whole-pack/expected-red.yaml:11-13 `other:pack.md` entry.
- contracts/packs/file-kinds.yaml: no `*/pack.md` rule (pattern at 140-145 for the guide).
- docs/changelog/2026-10-29-markdown-content-form.md:42 and 2026-10-28-pack-level-files-round-trip.md:37: dated history, leave; add a new changelog entry.
- No root CHANGELOG*; contracts/ only journey-file names; docs/guides/service-build.md has no pack.md mention.

## 5. CRLF / final newline - #194's rule
- Store does not normalise: Companions.read (Companions.java:276-297) passes raw text from FilesystemPackStore.readText (879-892: strict UTF-8 decode, no newline handling) straight to MarkdownCodec.parse.
- MarkdownCodec.read (MarkdownCodec.java:136-143): BOM -> refusal line 1 "a byte order mark is not admitted"; any '\r' -> refusal "a carriage return is not admitted" at its line. Javadoc line 19. Tests: MarkdownCodecTest:47 (`"# T\n\ntext\r\n"` refused at line 3, neighbour LF accepted); MarkdownCompanionTest:136-151 ("land.md: line 4: a carriage return is not admitted").
- Missing final newline: accepted; split keeps last line, model ignores final newline (ADR-0040:100); writer emits exactly one final newline (MarkdownCodec.java:128, javadoc 33-34). MarkdownCodecTest:117 "tight" text without final newline equals canonical.
So: CRLF = refusal (pack inconsistency, ADR-0036 d5), NOT normalise (this overrides ADR-0036's original "normalized to LF"); no final newline = same meaning, rewritten canonical. Empty file = valid empty document.
