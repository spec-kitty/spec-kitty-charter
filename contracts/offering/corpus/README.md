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
- `invalid-reasons.json`: the reasons each invalid document fails for, so a test pins why it fails, not only
  that it does (#295). A reason is the JSON Schema `keyword` that refuses it, the instance location `at` as a
  JSON Pointer and, for `required` and `additionalProperties`, the `member` it is about; most documents have
  one, and `15-artifact-id-empty.json` two (an empty id is both too short and off the pattern). Both tests
  check that it names exactly the files of `invalid/`, and that a document fails for exactly its reasons:
  networknt's errors are those reasons, and each of the scenario validator's violations says one of them at
  its location, one violation per reason.

A rule the schema does not express is not here: `cascade: ["all", "tactic"]` is a request body
refused semantically (`422 activation_invalid`), and a duplicate `packId` too.
