### Breaking

- Paradigms and styleguides state named principles (ADR-0067, #456). One element, `Principle`
  (`{name, description}`, closed; name 1 to 64 characters, description 1 to 255, neither blank), is used by
  `Paradigm`, `ParadigmRegistration`, `Styleguide` and `StyleguideRegistration`, and by the `paradigm` and
  `styleguide` pack schemas:
  - a paradigm may state `principles` (optional; at least one item when sent; `[]` on a read when it states none);
  - a styleguide's `principles` changes from a list of strings to a list of `Principle` (still required, at least
    one). **Breaking**: a styleguide body or file with string principles is refused (`422 validation_failed` on a
    write, `500 pack_unreadable` on a read).
  - names are unique within their list, ignoring case and surrounding whitespace; a repeat is refused like a schema
    error, reported in a pack file as `uniqueItems` at `/principles/<n>/name`. The list has no count cap.
- Core's seam: nothing; core reads no styleguide or paradigm through this contract yet. spec-kitty-open-packs rewrites
  its styleguide principles before its pin moves (open-packs#66).
