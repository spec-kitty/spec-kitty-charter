### Changed

- The bundled contract core vendors (`charter-openapi.yaml`) keeps `components` instead of inlining every `$ref`
  (issue #294). It shrinks from 5,238,317 to 248,412 bytes, so a YAML reader with snakeyaml's default limit (3 MiB of
  code points) reads it again; the build's own readers drop the 64 MiB limit, and the lint's rule timeout falls from
  60 to 20 seconds. Each file the split contract references is one component named after the file (a
  schema's name is its `title`), in `schemas`, `responses`, `parameters`, `headers` or `examples`; every `$ref` in
  the bundle is `#/components/<section>/<name>`; `paths` holds the path items themselves. The API is unchanged:
  `oasdiff breaking` finds no change against the inlined bundle, and resolving every reference gives that bundle
  exactly; the `getOpenApiDocument` description says what the served document is. This replaces the 0.1.0 line
  above that the inlined bundle grows to about 5.2 MB and that core raises its YAML limit: no release carried that
  shape. Core's seam: a reader of the vendored file that assumed no `$ref` resolves the component references
  (an OpenAPI parser or generator does); a reader that raised the YAML limit for the inlined bundle may drop it.
