### Changed

- `pack-doctor validate --full` runs `REFERENCES` whenever `CLOSURE` found no composition fault, a rooted closure that
  does not compose (ruling R-aa; ADR-0083 decision 8). A preset contest, a preset-item fault or a case-only answer no
  longer hides a reference fault: each is reported beside `REFERENCES`' own, and `checks` lists `REFERENCES`. Before,
  any `CLOSURE` fault left `REFERENCES` out. `REFERENCES` left out for a composition fault is still not listed in
  `skipped`. No code, schema or HTTP behavior changes. Core's seam: none; the CLI and its report are not part of the API
  core calls, and core regenerates nothing.
