### Changed

- A write may spell an artifact id as the declared id, as its full URN of the list's kind (`directive:DIRECTIVE_001`)
  or in another case, and the service stores the declared id (ADR-0020 D4, operator ruling of 2026-11-06). `selected`
  and `deactivated` both normalize, so every spelling gives one document and one revision. A spelling that matches two
  declared ids differing only by case is `422 activation_invalid` with the new violation code `ambiguous_artifact`; an
  unknown id, or a URN of another kind than the list's, stays `unknown_artifact`. `ArtifactId` widens its `pattern` to
  admit the optional `kind:` prefix (in `ActivationConfig.schema.json` too), and its text, `ArtifactIdPath` and
  `ActivationConfig` describe the rule. The stored and answered document is unchanged: it holds declared ids.
