### Added

- `listPackSkills` (`GET /packs/{packId}/artifacts/skill`) and `getPackSkill` (`GET …/skill/{artifactId}`), tag
  `Skills` (#107; ADR-0074 decisions 4, 9 and 10). The list answers `SkillList {items: [SkillSummary {id, title,
  description, form, _links}], _links}` in id order, unpaged. The item answers the `Artifact` envelope with member
  `skill` and links `self` and `collection` (the skill list): the skill as its file declares it, in camel case, with a
  prompt skill's `body` verbatim, exactly as the pack holds it, and no `body` for a wrapper skill. `bodyPath`,
  `overrides` and `enhances` are not answered. Both answer the pack revision as `ETag` and the source fingerprint; an
  unknown pack is `404 pack_not_found`, a skill the pack does not hold `404 artifact_not_found`. Both operations are
  `ready` in the switch-gate register.
