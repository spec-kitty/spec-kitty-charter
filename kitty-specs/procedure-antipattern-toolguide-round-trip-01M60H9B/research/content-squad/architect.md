# Squad 2 — Architect lens: storage design for the structured toolguide guide and the `markdown` content form

Delegate: architect-alphonso (profile loaded via `spec-kitty agent profile show architect-alphonso`; charter context
`--action plan` loaded). Read-only.

Directives applied: **001** (Architectural Integrity — one codec, clean seams between the form, the companion rule and
the kind binding), **003** (Decision Documentation — every choice below is framed for ADR-0042 / ADR-0039/0040
amendments), **010** (Specification Fidelity — checked against spec FR-004/FR-005, SC-001/SC-003, C-001, C-005),
**032** (Conceptual Alignment — "block", "section", "equal in meaning" defined explicitly in section 2), **041/051**
(design for review/handoff to planner/implementer), plus charter **RECONCILE_CHANGE_SCOPE_TENSIONS** (collision with
#195) and **USE_C4_MODEL_TECHNIQUES** (component view in section 3). Tactics dependency-hygiene/supply-chain do not
apply (no new dependency is proposed: the Markdown parser is a ~150-line line scanner, not a CommonMark library).

Evidence base: ADR-0036/0037/0039/0040; storage code at `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`;
gate code under `service/packs/src/test/java/.../infra/storage/`; mission #194 spec/decisions
(`kitty-specs/procedure-antipattern-toolguide-round-trip-01M60H9B/`); open-packs at `01edb4e`; a SnakeYAML dump probe
and a corpus scan of 72 Markdown files (24 guides, `pack.md`, 47 mission `prompt.md`/`guidelines.md`), both run in the
scratchpad.

---

## 0. Corpus facts that drive the design (measured, not assumed)

| Fact | Evidence |
| --- | --- |
| All 72 `.md` files (24 guides, pack.md, 47 prompts/guidelines) already have canonical separators: one blank line before and after every H1/H2, one final newline, no trailing whitespace, no tabs, no CR, every fence closed | scratchpad `squad2/mdcheck.py` over the corpus: "clean: 71" + 1 file whose only deviation is H4s (which stay inside a body) |
| 76 heading-looking lines sit inside fenced code in 15 of 24 guides (`# comment` in bash blocks); prompts additionally hold indented fences (`specify/prompt.md:539,712,773`) containing `## Content Quality` etc. | corpus scan; `plan/prompt.md:301-304` |
| No setext headings, no `~~~` fences, no H4+ in guides; one prompt has H4 (`analyze/prompt.md`) | corpus scan |
| 8 of 24 guides' H1 differs from the toolguide `title` (e.g. `Contextive` vs `Contextive Toolguide`; `PowerShell Syntax Guide` vs `... for AI Agents`; 4 QA/internal "— Invocation Contract" variants) | title/H1 comparison loop |
| No duplicate H2 inside any file | `uniq -d` scan |
| 47 prompts start with YAML frontmatter and have **no H1**; `pack.md` has an H1 and **no H2** | `head` + frontmatter scan |
| Core never reads a guide body (`artifact_bodies.py:253` renders title/tool/summary only); its model is `extra="forbid"` | `/home/user/spec-kitty/src/charter/...` — but see concession C1 |
| `YamlDumpEmitter` emits a multi-line string as a `\|` literal block only when SnakeYAML can; one trailing space or a leading tab on any line flips the **whole** value to a single-line double-quoted string with `\n` escapes | probe: `body: "## Usage\n\nRun:\n\n```bash\n# comment\nruff check .  \n```..."` vs a clean `body: \|` block; `YamlDumpEmitter.java:93-99` (no scalar-style control; `ScalarWriter` only serves the edit path, `YamlDocument.java:185`) |

---

## 1. Where the structured guide lives — recommendation: **(ii) a Markdown companion, structure parsed by the `markdown` form**

| Criterion | (i) inline in toolguide YAML | (ii) Markdown companion, parsed | (iii) structured YAML companion |
| --- | --- | --- | --- |
| Fidelity | Meaning is preserved (ADR-0036 compares resolved strings), but presentation degrades silently: one trailing space anywhere turns a 300-line section into one quoted line on the next write (probe above). No style control exists in the dump path. | Bodies kept byte-exact; structure is a lossless partition of the file; canonical writer equals the source bytes for 100% of the pinned corpus. | Same YAML block-scalar cliff as (i). |
| Authoring (human/agent) | 100-300-line Markdown nested under `sections: - heading: body: \|` at 6-space indent; no Markdown preview/lint; agents must keep YAML indentation inside code fences. | Plain Markdown, previewable, lintable; structure = H2 headings authors already write. | Worst of both. |
| Consistency | Diverges from the skill body precedent (ADR-0039: body is a companion). | Same seam as the skill body (`SkillBinding.companions`), same path rule and one-owner rule (`Companions.resolve`). | **Forbidden**: ADR-0039 decision 3 refuses `.yaml` companions (`Companions.java` "is a YAML file"). |
| Reuse for #195 `pack.md` and step `prompt.md`/`guidelines.md` | None: those files are Markdown by format (frontmatter + headings), so #195 would still need the markdown form — two mechanisms. | One codec serves all three: guide (companion), pack.md (pack-level rule), prompts (companion or rule, #194 PR-B). | None. |
| Operator decision (a) "structured shape: fixed blocks + freeform sections" | Direct. | Satisfied in the **domain** (`Toolguide.guide()` is a `Guide` value with fixed blocks + sections); storage is Markdown whose H2 tree maps onto it. "Designed, not mirrored" (c) holds because the reserved headings, order and refusals are designed. | Direct but forbidden. |

**Verdict on 1:** (ii). (i) is viable on fidelity grounds only because the pinned corpus happens to have zero trailing
whitespace; its failure mode is ergonomic and silent, and it forces #195 to build a second mechanism anyway. (iii) is
ruled out by ADR-0039.

---

## 2. What the `markdown` content form MEANS (shared by guide, pack.md, prompts)

### 2.1 Parsed model (`MarkdownDocument`, infra-level, generic)

```
MarkdownDocument(
  Optional<Map<String,Object>> frontmatter,   // YAML mapping between a leading "---\n" and the next "---\n" line
  Optional<String> title,                     // text of an H1 that is the first non-blank line after frontmatter
  String preamble,                            // text between title/frontmatter and the first H2 ("" when none)
  List<Section> sections)                     // every H2, in order
Section(String heading, String body)          // H3..H6, tables, fences, lists all stay INSIDE body
```

Only H1 and H2 are structure. H3 nesting stays in the body (minimal; no consumer needs H3 structure; 17 H3 in
PLANTUML alone would otherwise explode the model).

### 2.2 Line scanner (fence protection)

1. A **fence** opens on any line whose left-stripped text starts with ```` ``` ```` or `~~~` (3+), at **any** indentation
   (covers `specify/prompt.md:712` indented fences); it closes on a line whose stripped text is the same char repeated
   at least as many times. Inside a fence nothing is a heading.
2. A **heading** is `^# ` or `^## ` at column 0, outside a fence. Heading text = rest of the line, kept verbatim.
3. **Refused as ambiguous** (CommonMark would see a heading or structure the scanner does not, so meaning could
   diverge between renderers and the model): an unclosed fence at EOF; an H1 anywhere but the first content line; an
   ATX H1/H2 indented 1-3 spaces outside a fence; a setext underline (`===`/`---` directly under a paragraph line) —
   corpus count for all four: 0.
4. **Refused as not text of this form**: a `\r` anywhere, a BOM (no normalization, C-005).

### 2.3 Canonical write and "equal in meaning"

Writer: `---\n<yaml>---\n` (if frontmatter) · `# <title>\n\n` (if title) · `<preamble>\n\n` (if non-empty) ·
for each section `## <heading>\n\n<body>\n\n` (body omitted when empty) · exactly one final `\n`.

Equality of meaning = **model equality**: frontmatter by ADR-0036 YAML meaning; `title`, `preamble`, `heading`, `body`
compared as exact strings. The only presentation that carries no meaning is the blank-line run between blocks and the
final newline (bodies/preamble hold no leading or trailing blank lines). Internal bytes — trailing spaces, tabs,
blank-line runs inside a body — are meaning. Because every pinned file is already canonical, the write is also
byte-identical for the corpus; a corpus test should assert that as a regression guard.

This **supersedes** ADR-0036's old `*.md` reading ("body as text, LF, one final newline") for files of this form, and
is stricter than ADR-0039's byte-for-byte text companion only in the separator runs. Record it as an ADR-0040
amendment (decision 3: "`markdown` is built").

### 2.4 Lossless invariant (must be enforced on WRITE, not only read)

`parse(write(doc)) == doc` holds only if no `body`/`preamble` contains, outside a fence, a column-0 H1/H2 line or an
unclosed fence, and no heading contains `\n`. The codec must refuse such a document on write (HTTP and whole-pack
writes), otherwise a write can split a section on the next read.

### 2.5 Structure schema

The form yields a generic JSON tree `{frontmatter?, title?, preamble?, sections:[{heading, body}]}` validated by a
**standard Draft 2020-12 schema** through the existing `PackSchemas`. Do **not** invent `x-` keywords:
`PackSchemas` loads with `DisallowUnknownKeywordFactory` (`PackSchemas.java:39-40`) and would refuse them.
Standard keywords suffice for the generic layer: `pack.md` → `title` required, `sections` `maxItems: 0` if #195
wants that; prompts → `frontmatter` sub-schema; guide → `frontmatter` absent, `title` required. Kind-specific
fixed-block semantics stay in the binding (section 3), the way a `YamlFormat` owns key names while the schema owns types.

For pack-level files this means a `files` rule may declare `content_form: markdown` with a schema that validates the
parsed tree — an amendment to ADR-0040 decision 1 (today "a `files` rule declaring any form but `yaml` fails",
`PackFileKind.java:332`).

---

## 3. Binding and seam changes

Component view (C4 level 3, packs adapter):

```
FilesystemPackStore ── readBytes ─► Companions.resolve (path rule, one owner; unchanged)
        │                                   │
        │                          Companion.form = TEXT | MARKDOWN(schema)
        ▼                                   ▼
  KindBinding.companions(dso)        MarkdownCodec.parse ─► MarkdownDocument ─► PackSchemas.validate(tree)
        │                                                        │
  ToolguideBinding.withCompanions ◄── CompanionContent.Markdown(doc)
        │
  GuideMapping (heading table, order, refusals)  ─►  domain Toolguide.guide(): Guide
```

1. **`KindBinding.Companion` gains a form**: `Companion(String path, CompanionForm form, Optional<CompanionContent> content)`
   with `CompanionForm = Text | Markdown(String schema)`; `CompanionContent` gains `Markdown(MarkdownDocument)`.
   Parse and schema refusals become ordinary `companion` violations `<path>: <reason>` (ADR-0039 decision 4 list
   extended: `is not well-formed markdown: <reason>`, `does not match schema toolguide-guide: <detail>`). Skill stays
   `Text` (no behavior change).
2. **The guide is a companion, not a pack-level rule**: it has exactly one owner (the toolguide), so ADR-0039 applies
   verbatim. No `file-kinds.yaml` rule for `toolguides/*.md`.
3. **`guide_path` resolves relative to the toolguide's file** (ADR-0039 decision 1). Sanitized built-in value:
   `CONTEXTIVE.md` (sibling). Default name for a new toolguide: `<id>.guide.md`.
4. **`GuideMapping` (in `ToolguideBinding` or a `ToolguideGuideFormat`)** maps the generic tree to `Guide`:
   reserved heading text ⇄ fixed block field, in a declared slot order (e.g. `[<lead blocks>, *freeform*, <tail blocks>]`,
   blocks chosen by the content delegates). Refusals, each a violation of the toolguide naming the guide:
   - missing required block — `guide <path>: block '<Heading>' is missing`;
   - duplicate block — `guide <path>: block '<Heading>' appears twice`;
   - near-miss — a freeform heading equal to a reserved one ignoring case/space — `heading 'usage' is not block 'Usage'`;
   - out of order — `block '<Heading>' is out of order` (no lossy reordering);
   - duplicate freeform heading (anchor collision; corpus has none);
   - **H1 ≠ toolguide `title`** — `guide title '<h1>' differs from title '<title>'`. Recommendation: the `Guide`
     value holds **no** title; the binding requires H1 == `title` on read and writes `# <title>`. One source of truth;
     8 pinned guides diverge → open-packs issue, no tolerance.
5. **Read refusals already covered by the companion rule**: missing guide, leaves the pack, is a YAML file, case-folded
   collision, two toolguides naming one guide (`named by two artifacts`) — matches spec edge cases.

---

## 4. Sketches

### 4.1 `contracts/packs/schemas/toolguide.schema.json`

- `guide_path`: keep the name (the companion convention is `<thing>_path`, cf. skill `body_path`); keep pattern
  `^[^/].*\.md$` (also forbid `\\`: `^[^/\\][^\\]*\.md$`); description: "relative to this toolguide's file
  (ADR-0039)". Required.
- Other field renames/reference shape: defer to the cross-kind delegates; nothing in this lens requires one.

### 4.2 `contracts/packs/schemas/toolguide-guide.schema.json` (over the generic tree)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://schemas.spec-kitty.ai/charter/packs/toolguide-guide.schema.json",
  "type": "object", "additionalProperties": false,
  "required": ["title", "sections"],
  "properties": {
    "title":    { "type": "string", "minLength": 1 },
    "preamble": { "type": "string" },
    "sections": {
      "type": "array",
      "items": { "type": "object", "additionalProperties": false, "required": ["heading", "body"],
                 "properties": { "heading": { "type": "string", "minLength": 1, "pattern": "^[^\\n]+$" },
                                 "body": { "type": "string" } } },
      "allOf": [
        { "contains": { "properties": { "heading": { "const": "<Required Block>" } } }, "minContains": 1, "maxContains": 1 },
        { "contains": { "properties": { "heading": { "const": "<Optional Block>" } } }, "minContains": 0, "maxContains": 1 }
      ]
    }
  }
}
```

(`frontmatter` absent from `properties` + `additionalProperties:false` ⇒ frontmatter refused.) Order, near-miss and
H1==title live in `GuideMapping`; a unit test asserts the mapping's heading table equals the schema's `const`s.

### 4.3 Domain

```
Toolguide { ...existing..., String guidePath, Guide guide }
Guide(String preamble, <FixedBlock fields: String or Optional<String> per block>, List<GuideSection> sections)
GuideSection(String heading, String body)
Invariants: heading single-line, non-blank, not a reserved heading (case-insensitive), unique;
            bodies/preamble: no leading/trailing blank line, no column-0 H1/H2 outside a fence, fences closed.
```
`ToolguideDso` gains `Optional<MarkdownDocument> guide` (empty until companions are read, as `SkillDso.body`).

---

## 5. Findings

[CRITICAL] `contracts/charter/schemas/ToolguideRegistration.yaml:12,39` + `ToolguideRegistrationService.java` —
HTTP POST/PUT of a toolguide carries `guidePath` but no guide content, and C-001 forbids a contract change; the store's
upsert requires every companion to have content (ADR-0039 decision 5) and a toolguide whose guide is missing is
refused on the next read (spec US1 scenario 2). A registration would either fail or commit an unreadable pack. —
Recommend: PUT with an unchanged `guidePath` carries the stored `Guide` over (application service); POST/PUT with no
stored guide at the resolved path is refused 422 `toolguide guide <path> has no content` — a served behavior change
with no shape change (precedent: ADR-0040 decision 8). Update the `guidePath` description text ("relative to the
toolguide's file"). Record in ADR-0042.

[HIGH] `service/packs/src/test/java/.../WholePackCensus.java:77-81` and `MeaningComparator.java:102-104` — the gate
picks the comparison by the `file-kinds.yaml` rule; a companion matches no rule, so it falls back to `ContentForm.YAML`
and is compared via `Yaml.load`. A guide parsed as YAML treats every `# ...` line (all headings and fence comments) as a
comment — two guides differing only in headings compare equal. Latent today only because the pinned built-in pack has
no skill (`expected-red.yaml:43-47`). — Recommend: the gate learns each companion's form from `WholePackStore`
(`companionFiles` already enumerates them, `WholePackStore.java:140-161`) and dispatches `MARKDOWN` to a markdown
meaning comparator (and `TEXT` to byte comparison for skill bodies). Must land in this PR, before `other:toolguides`
is removed.

[HIGH] `service/packs/src/test/resources/whole-pack/expected-red.yaml:23-27`, `WholePackStore.java:137-161` — SC-001
says `other:toolguides` leaves the baseline, but at `01edb4e` all 18 built-in toolguides use repo-rooted
`guide_path` and none has the designed fixed blocks, so all 18 are refused; a refused artifact's companions are not
counted as modelled, so the 18 real `.md` files reappear as unmodelled `other:toolguides`. `known-failures.yaml`
admits only "files of a modelled kind" (header lines 1-8), so those orphans cannot be pinned. — Recommend (in order):
(a) land the open-packs migration (relative `guide_path`, fixed-block headings, H1 = title, for all 24 guides) first and
move `openPacksRef` in this PR, so SC-001/SC-003 hold for real; else (b) 18 known-failure entries for the toolguide
files **and** keep `other:toolguides` re-pointed at the open-packs issue, amending SC-001/SC-003 explicitly. Either
way add a corrected-copy test (as ADR-0040 decision 6 did) proving the migrated built-in pack round-trips whole. Do not
extend known-failures to cover orphans (that is tolerance logic by another name).

[HIGH] `service/packs/src/main/java/.../storage/YamlDumpEmitter.java:93-99` — rules out option (i): the dump has no
scalar-style control, so a single trailing space or leading tab turns a whole section into a one-line double-quoted
string (probe). — Recommend option (ii); do not add per-field style hints to the emitter for this.

[HIGH] `service/packs/src/main/java/.../storage/PackSchemas.java:39-40` — the strict loader refuses unknown keywords,
so a heading table encoded as `x-markdown-heading` in the guide schema would fail to load. — Recommend: standard
keywords only (`contains`/`minContains`/`maxContains`/`const`); heading⇄field table and order in `GuideMapping`, held
equal to the schema by a test. Also: `PackSchemas.bundled()` loads only `files` rules' schemas (`:111-121`); a
companion schema (`toolguide-guide`) needs an explicit registration path (e.g. bindings expose companion schema names
and `bundled()` unions them).

[MEDIUM] `service/packs/src/main/java/.../storage/PackFileKind.java:332` / ADR-0040 decision 1 — admitting `markdown`
for a `files` rule (needed by #195 `pack.md`) contradicts "a `files` rule declaring any form but `yaml` fails". —
Recommend: amend decision 1 to "a `files` rule's schema validates the parsed tree of its form (YAML document or
Markdown tree)"; build the admission + `PackSchemas` + gate dispatch here with a test-fixture table, and leave the
actual `*/pack.md` rule and schema to #195 (C-006). Coordinate: both PRs touch `ContentForm`, `PackFileKind`,
`file-kinds.yaml`, ADR-0040 and `expected-red.yaml` — this PR owns the form, #195 only appends its rule.

[MEDIUM] Domain/codec write path — without the lossless invariant (2.4) a guide body containing a column-0 `## ` line
or an unclosed fence is written fine and re-read as a different structure. — Recommend: `Guide`/`GuideSection`
invariants plus a codec-level write refusal; property-style test `parse(write(d)) == d` over the corpus and fuzzed bodies.

[MEDIUM] 8 of 24 guides: H1 ≠ toolguide `title` — Recommend: H1 == `title` enforced, `Guide` holds no title; include in
the single open-packs migration issue; no tolerance.

[LOW] `ContentForm.java:15-19` javadoc and ADR-0039 decision 2 ("Content is text today") — update both in the same
change (DIRECTIVE_037); `CompanionContent` javadoc too.

[LOW] Mission prompts (#194 PR-B) — the form already handles frontmatter + no-H1 + indented fences (measured), so the
step `prompt.md`/`guidelines.md` reuse needs no model change. Whether they become companions of `step.yaml` (allowed: ADR-0039 forbids a YAML
*companion*, not a YAML owner) or pack-level rules is PR-B's call.

---

## 6. Concessions (where this lens does not apply or yields)

- C1. Core coupling is **moot** by the operator ruling on epic #186 (2026-10-28, `spec.md` C-004 "operate as if core
  does not exist"). Core's `extra="forbid"` model would have penalized option (i); it is not a factor.
- C2. Which fixed blocks exist, their headings and whether preamble is a block are the content delegates' call; the
  design above is parametric in them (slot order list + heading table).
- C3. If the operator prefers option (i) for single-file locality, it is *correct* on ADR-0036 meaning; my objection is
  ergonomic and reuse, not fidelity.
- C4. The scanner is deliberately not CommonMark-complete; it refuses what it cannot classify unambiguously. If a
  future pack needs setext or indented ATX headings, that is a form amendment, not a reader tolerance.

## Verdict

**Option (ii)**: the guide stays a Markdown file, owned by its toolguide as an ADR-0039 companion whose
`guide_path` resolves relative to the toolguide's file; the companion gains a `Markdown` content form backed by one
generic `MarkdownCodec` (frontmatter? · H1 title? · preamble · H2 sections with exact bodies; H3+ in bodies; fences
protect headings; ambiguous constructs refused; canonical separators are the only non-meaning presentation). The
structured shape (fixed blocks + freeform sections) lives in the domain `Guide`, mapped by the toolguide binding and
checked by a standard-keyword schema over the generic tree. The same codec serves #195's `pack.md` through a `files`
rule once ADR-0040 decision 1 is amended. **Blocking before build:** resolve the HTTP registration gap (CRITICAL),
teach the gate the companion form (HIGH), and decide the open-packs-first sequencing vs. amended SC-001 (HIGH).
