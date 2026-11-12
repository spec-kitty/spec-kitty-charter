### Added

- The pack document is read (#451). `GET /packs/{packId}/doc` (`getPackDocument`, tag `Registration`) answers the
  pack's `README.md` in the form `Accept` chooses (the service's producible-type matching ranks by quality and
  specificity, does not treat `q=0` as a refusal, and settles a tie by the first type listed in `Accept`):
  `text/markdown` (and `text/*`) answers the held file, byte for byte, frontmatter included, as
  `text/markdown;charset=UTF-8`; `application/json` (and `application/*`, `*/*`, and no `Accept`) answers the new
  closed schema `PackDocumentRead` (`title`, left out when the document has no H1; `body`, the Markdown below the H1 in
  canonical form, without the frontmatter; `_links` with `self` and `pack`). When both forms are named at equal quality
  the type the client lists first answers. The `ETag` is the pack revision, quoted, the same for both forms, as
  `getPack` answers it, and the 200 carries `Charter-Source-Fingerprint`. Every 200, 304 and 406 carries
  `Vary: Accept`; every operation's `406` now carries `Vary: Accept`. `If-None-Match` is honoured: a match, or `*`, is `304` with the `ETag`, `Vary` and no body. An
  `Accept` that names neither form is `406`, a `Problem` with no code. A pack with no `README.md` is
  `404 pack_document_not_found`, a new refusal code; an unknown or built-in pack is `404 pack_not_found`; a pack whose
  `pack.yaml` cannot be read is `500 pack_unreadable`, as `getPack` answers it; a `README.md` the store refuses is
  `500 pack_unreadable`.
  The `doc` relation joins the link table: the pack resource (`getPack`, and the result of a succeeded registration)
  carries `doc` exactly when the pack holds a `README.md`. `PackRegistration` is unchanged otherwise. The generated
  client types `getPackDocument` by its first response media type, `application/json`, so it reads the JSON form; the
  Markdown form is the raw call. Core's seam: nothing; additive. A client that matches on the closed set of refusal
  codes learns `pack_document_not_found`.
