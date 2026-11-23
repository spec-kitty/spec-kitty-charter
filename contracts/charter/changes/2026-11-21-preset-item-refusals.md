### Breaking

- Registration, every write judged over the closures its pack joins, and `pack-doctor validate` now refuse a preset
  item that the declaring pack's own closure (the pack, its `depends_on` and its `parent_pack`, never a dependent)
  lacks (issues #629 and #632 item 1; rulings R-a, R-k, R-l, R-m). Two new `CharterRefusalCode` values, both 422:
  `preset_item_not_held`, a `must_have` item that no pack of the own closure holds as an artifact file, and
  `preset_item_undeclared`, an item that no pack of the own closure declares as its kind, by a graph node or an
  artifact file. Each item gets one fault, with one violation at `presets/<id>.yaml#/activated_<kinds>/<index>`, and an
  item that names an artifact of the closure only by case keeps `pack_reference_artifact_missing`. A registration
  that was accepted before is refused now. A composing read is unchanged: it still reports such a `must_have` as
  `requiredNotHeld`. Core's seam: regenerate the refusal enum from `CharterRefusalCode.yaml`; a client that branches
  on registration codes adds the two.
