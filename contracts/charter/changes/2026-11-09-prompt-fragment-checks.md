### Breaking

- A step enhancement and its prompt fragments are checked (#428; Refs #424), so content that validated or registered
  before is now refused with `prompt_fragment_invalid`, naming the file, at validate, at load, at registration and on
  a read:
  - a fragment that is blank, carries frontmatter, a carriage return or a byte order mark, or holds a slot marker of
    any id or leaves a comment open (the enhancement that names a blank, frontmatter-bearing, carriage-return or
    byte-order-mark fragment is the pointer; a fragment with a marker is its own pointer);
  - an enhancement that names one slot twice, a fragment that does not exist, or a fragment another entry names; a
    fragment under `missions/<type>/enhancements/` that no entry names (an orphan);
  - an enhancement without exactly one `enhances` edge, to an `action:<type>/<step>`, with a `<type>` directory that
    differs from that edge's type, or an id repeated across type directories (the second file in path order is
    refused and withheld from the pack), or no `prompt_fragments` key;
  - a step enhancement that is the source of any `overrides` edge is refused with `prompt_fragment_invalid`; an
    enhancement only adds.
- `PUT /packs/{packId}/relationships` refuses, with `422 prompt_fragment_invalid` and nothing written, an edge that
  would give a held step enhancement an `overrides` edge or a second `enhances` edge, or replace its one `enhances`
  edge. Before, a step enhancement was not held, and every edge from one answered `409 registration_conflict`.
  `prompt_fragment_invalid` declares `operations: {putPackRelationship: {status: 422}}` for it.
- A registration lists every such fault at once, each as a violation `{pointer: <pack-relative file>, code:
  prompt_fragment_invalid}` of `422 validation_failed`; an id that differs from the file stem, an entry whose `slot`
  breaks `<namespace>:<name>` and `prompt_fragments: []` stay schema violations. A file reported as
  `prompt_fragment_invalid` is not also listed as `unmodelled`; nor is a refused enhancement, or the fragments beside
  it, listed again beside its schema violation.
- Every verified read of a closure holding such a pack answers `500 pack_unreadable`, naming the first file.
- core: nothing while it is not a consumer (epic #186).
