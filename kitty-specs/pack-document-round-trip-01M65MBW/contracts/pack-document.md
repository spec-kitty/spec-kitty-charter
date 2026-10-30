# Contract change: the pack document

This mission changes the pack contract (`contracts/packs/`), not the charter OpenAPI contract
(`contracts/charter/**` is untouched, C-001). Both changes are additive.

- `contracts/packs/file-kinds.yaml`: a `files` rule `*/pack.md` → `schema: pack-document`,
  `content_form: markdown`, appended after the toolguide guide's rule.
- `contracts/packs/schemas/pack-document.schema.json`: the structure schema over the Markdown model
  `{frontmatter?, title?, preamble, sections: [{title, content}]}`: `frontmatter` an object, `title`
  and each section title a non-empty string, `preamble` and each content any string; closed
  (`additionalProperties: false`) at both levels; no `id` property.

A `pack.md` that was ignored before is now read: one the Markdown form refuses (a carriage return, a
byte order mark, an ambiguous heading) is a load violation and a validate-mode finding.
