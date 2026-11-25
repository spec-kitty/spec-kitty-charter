### Changed

- A quoted number in a field the pack schema types `number` or `integer` (`"15"`, where the field takes `15`) keeps its
  code (`artifact_schema_error`, `drg_schema_error` or `pack_unreadable`, by file) and its pointer, and its violation's
  `detail` now says how to fix it: `<file>: '<field>': "15" must be a number: write 15 without quotes, or run
  pack-doctor remediate` (`must be an integer` in an integer field). Only a value that, written plain, is a finite
  number of the field's type gets the fix (an integer field takes integers only); any other value keeps
  `<file>: '<field>' must be a number`. The registration, every write and `pack-doctor validate` give the same detail
  (#651), and a composing read that refuses the pack as `pack_unreadable` carries it in its detail too: `the file
  <file> of <pack> cannot be read: '<field>': "15" must be a number: write 15 without quotes, or run pack-doctor
  remediate`. A file that is not `.yaml` (a Markdown file's frontmatter, say) keeps the plain text, and a quoted
  number in `policy.yaml`, whose faults join into one detail with no pointer, says `write 15 without quotes` without
  the remediate clause. Detail text only; no code, schema or accepted shape changes. `pack-doctor remediate` prints
  its repairs on its own outcome lines; `PackDoctorReport` is unchanged.
