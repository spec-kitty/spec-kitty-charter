### Added

- Every answer that carries an `Activation` (`getActivation`, `createActivation`, `replaceActivation`) links what a
  client does next from it (#409): `offering`; `packs`, `graph` and `skipped`; `effective-context`, templated
  `…/mission-types/{missionType}/actions/{action}/context{?depth}`; `edit` (`PUT`) and `delete` (`DELETE`) on the
  activation; `select` and `exclude` (`PUT`), templated `…/selections/{kind}/{artifactId}` and
  `…/exclusions/{kind}/{artifactId}`; and `apply-preset` (`PUT`), templated `…/presets/{presetId}`, beside `self` and
  `artifacts`. `unselect` and `include` are not on the activation. An `ActivationChange` still links only `up`. The
  relation table moves these relations into the contract on an activation; no relation is new.
