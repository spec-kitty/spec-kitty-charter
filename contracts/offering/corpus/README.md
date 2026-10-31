# The `ActivationConfig` corpus

Documents every validator of the activation document must agree on (#125, research D-13): the
canonical JSON Schema (`../schemas/ActivationConfig.schema.json`) and its OpenAPI mirror
(`contracts/charter/schemas/ActivationConfig.yaml`), with networknt (`ActivationConfigSchemaParityTest`
in `service/packs`) and with the scenario validator (`ActivationConfigConformanceTest` in
`service/deployable`).

- `valid/`: each document passes. They pin the three states (`missionTypes: []` and absent,
  `selected.skill: []`), packs with and without `version`, every activatable kind, and the
  languages pattern.
- `invalid/`: each document fails, for one reason its name gives: a missing or wrong
  `schemaVersion`; an unknown member at the top or in a pack; a kind that is not activatable
  (`template`, `mission_type`, `asset` as a deny kind); `null` in place of a member; an empty deny
  list or an empty `packs`; a malformed id; and the members left out by ruling
  (`appliedPresets`, `contextActivations`).

A rule the schema does not express is not here: `cascade: ["all", "tactic"]` is a request body
refused semantically (`422 activation_invalid`), and a duplicate `packId` too.
