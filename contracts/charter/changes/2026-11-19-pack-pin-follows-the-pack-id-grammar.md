### Breaking

- An activation pin's `packId` (`PackPin`, in `ActivationConfig.schema.json` and its mirror `PackPin.yaml`) follows
  the pack id grammar: a kebab slug of at most 63 characters, `^[a-z0-9]+(-[a-z0-9]+)*$` (ADR-0075 decision 2; #583,
  FR-011). It was the URN segment `Slug`, which also allows `_` and 64 characters. No registered pack carries such an
  id, so a pin naming one could never be configured: the write was refused `422 activation_invalid` when the pin was
  used. It is now refused by the schema, at validation (`422 activation_invalid` as before). The unused `Slug` entry
  of the offering schema's `$defs` is removed. Core's seam: a pin is written with the registered pack id, as the pack
  registry names it. Corpus documents added: `invalid/20-pack-id-underscore.json` and
  `invalid/21-pack-id-sixty-four-characters.json`.
