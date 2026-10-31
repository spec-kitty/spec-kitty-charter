# Draft issue (for the orchestrator to file): restore a runner for the load fixtures

**Repository**: spec-kitty/spec-kitty-charter · **Parent**: epic #186 · **Kind**: test debt

## What happens

`service/packs/src/test/resources/load-fixtures/` holds 63 cases, each with:

- `files/`: a pack to load;
- `expected.json`: the expected load outcome, diagnostics and graph counts.

Their runner, `LoadOutcomeTest`, was deleted in `2bb3f762`. The only remaining reader of the directory
is `FileSystemPackSourcesTest:240`, and it reads one directory. So the 63 cases are never checked, and
an `expected.json` can drift from the behaviour without any test failing.

Mission `drg-tension-and-node-fields-01M68A0T` (#235, #236) found two of them still recording the
retired `impacts` −1 reading of `in_tension_with`. It renamed and corrected them:

- `shard-tension-binds-as-tension/expected.json`, formerly `shard-legacy-tension-is-impacts-minus-one`.
  It now records `"edgeCount": 0`, `"edges": []` and a new `"tensions"` list,
  `"directive:a in_tension_with directive:b"`.
- `shard-impacts-on-tension-edge`, formerly `shard-impacts-on-legacy-tension-edge`. Its finding text
  is unchanged.

The `"tensions"` key is new, and no runner reads it yet.

## Done when

- A test runs every load fixture through the production load path and compares the outcome with
  `expected.json`. A fixture that drifts fails.
- The runner reads the `"tensions"` key, or the fixtures are migrated to the format it settles on.
- Each fixture that turns out stale is fixed in the same change, or removed with a reason.
