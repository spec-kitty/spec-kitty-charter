### Changed

- An anti-pattern file that is there but refused for a fault of its own (an `id` that is not its stem, a schema
  breach) is now one fault, the file's, on registration and in `pack-doctor validate` alike (squad finding A9). Before,
  the `anti_pattern` node that declares it got a second violation, `drg_schema_error` `declared with no file`, though
  the file was there. A node with no file at all is refused as before. No code, schema or HTTP behavior changes beyond
  the dropped duplicate violation. Core's seam: none; core regenerates nothing.
