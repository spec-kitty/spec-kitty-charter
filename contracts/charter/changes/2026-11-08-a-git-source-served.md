### Changed

- `registerPackFromSource` (`POST /packs`) is built for the `git` source kind (FR-004, NFR-005, NFR-011, NFR-012, C-008;
  PR A of the layer merge mission): it fetches one ref of a public repository over https with the host's `git` binary,
  never as a work tree, and registers the tree it holds as `PUT /packs/{packId}` does. A `git` source no longer answers
  `503` for being unbuilt; it answers `503 pack_source_unavailable` only when `charter.pack-sources.git.enabled` is off or
  the system `git` is missing or older than 2.50.1, which leaves the other kinds working. The URL is `https`, port 443 or
  none, on `charter.pack-sources.git.allowed-hosts`, with no user information, query or fragment; the ref is a commit id
  or a `refs/heads/` or `refs/tags/` name; the host must resolve to global addresses only, and git is pinned to the
  address the service checked. A redirect is `redirect_refused`; a ref of another form or that names nothing is `bad_ref`;
  a named `commit` that is not the one the ref resolved to is `commit_mismatch`; a link, a submodule or any entry mode but
  a file or a directory in the tree is a `422 validation_failed` naming the entry, as for an archive. A successful result
  carries `source` with `kind`, `url`, `ref` and `commit`, the commit the ref resolved to.
- `registerPackFromSource` answers `428` for a `git` source whose ref is a branch and that names no `commit`, when the
  request carries neither `If-Match` nor `If-None-Match: *`; a tag, a commit id and a branch with a `commit` need neither.
