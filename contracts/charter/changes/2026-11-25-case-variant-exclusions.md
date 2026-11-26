### Breaking

- Registration and validate mode judge a letter-case variant of a pack file in lower case (#661, operator rulings
  2026-11-25 and 2026-11-26; packs contract `0.1.0-SNAPSHOT`). A file an exclusion takes in lower case is not held and
  is reported as a `file_not_held` diagnostic under that exclusion's reason, where a payload rule held it as bytes
  before (`missions/x/templates/ReadMe.md`), or a retired rule refused it (`missions/mission-steps/ReadMe.md`). A file
  a payload rule takes as written and a retired or `files` rule takes in lower case is refused as retired, or judged
  as that kind. A retired family in another letter case is refused wherever it stands
  (`Mission_Step_Contract.graph.yaml` is no longer read as a graph shard, and `a.Step-Contract.yaml` is no longer only
  warned), a hidden file of a kind (`assets/.X.Asset.yaml`) is judged as that kind, and every spelling of a file has
  the kind of its lower-case spelling (`presets/a.Directive.yaml` is a directive). Two `file_not_held` details change:
  `Assets/logos/README.md` and its like carry the exclusion's reason, no longer the Markdown fallback's ("Author
  aid"), and `Assets/.DS_Store` carries the hidden-file exclusion's reason, no longer the unmodelled one. Core's seam:
  none; a pack that carried such a file is stored without it, refused, or validated as its kind. Scenario:
  `registration/whole-pack-registration.feature`, "A letter-case variant of an excluded file is not held as a
  payload".
