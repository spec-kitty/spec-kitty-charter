# Decision Moment `01M65MKX2FG5E5SAM9D57BX2AE`

- **Mission:** `pack-document-round-trip-01M65MBW`
- **Origin flow:** `specify`
- **Slot key:** `specify.pack_document_type`
- **Input key:** `pack_document_type`
- **Status:** `resolved`
- **Created:** `2026-10-30T05:30:42.127097+00:00`
- **Resolved:** `2026-10-30T05:30:45.323678+00:00`
- **Resolved by:** `claude`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

The domain cannot depend on infra (HexagonalArchitectureTest), and MarkdownDocument is infra. How does Pack hold the document while being typed as #194's markdown value?

## Options

- A domain PackDocument with the markdown model shape (frontmatter, title, preamble, sections) plus an infra mapper to and from MarkdownDocument, as #194 did for Guide/GuideMarkdown
- Move MarkdownDocument into the domain or kernel
- Other

## Final answer

A domain PackDocument with the markdown model shape (frontmatter, title, preamble, sections) plus an infra mapper to and from MarkdownDocument, as #194 did for Guide/GuideMarkdown

## Rationale

ADR-0006 and HexagonalArchitectureTest keep packs.domain free of infra; #194 set the precedent (Guide + GuideMarkdown). The value keeps the markdown model's shape, so nothing of the form is rebuilt: parsing, canonical writing and comparison stay MarkdownCodec's (ADR-0042 d8, DM-01M60HM8).

## Change log

- `2026-10-30T05:30:42.127097+00:00` — opened
- `2026-10-30T05:30:45.323678+00:00` — resolved (final_answer="A domain PackDocument with the markdown model shape (frontmatter, title, preamble, sections) plus an infra mapper to and from MarkdownDocument, as #194 did for Guide/GuideMarkdown")
