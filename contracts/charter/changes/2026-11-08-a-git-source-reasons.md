### Changed

- `pack_source_refused` has two more reasons in its fixed set, `bad_ref` (a git ref that is not a commit id or a
  `refs/heads/` or `refs/tags/` name of the grammar, or that names nothing in the repository) and `commit_mismatch` (the
  commit the ref resolved to is not the commit the request named), for the git source of `registerPackFromSource`
  (FR-004, NFR-012, WP18). The description of `CharterRefusalCode` and of the operation list them with the others.
