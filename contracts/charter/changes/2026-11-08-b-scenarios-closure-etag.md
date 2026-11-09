### Changed

- Scenarios of the activation set that compared getActivation's `ETag` with the revision now state the declared
  contract (decisions `01M6Y11E55YP5TG1EJ1MDKCFRP` and `01M6Y3GZFRC5WEQQ7G6GT0YMW3`): the `ETag` of `getActivation` is
  the closure tag, so a read that stored nothing is checked on `/revision` and on the `ETag` differing from the
  revision, a conditional read sends the tag it was answered, the revision as `If-None-Match` answers `200`, and a
  write that carries the tag of a closure a pack change has since altered is `412`. Twenty-eight scenarios of
  `activation-cascade-depth`, `activation-kind-model`, `activation-presets`, `activation-register`,
  `activation-register-guards`, `activation-ruling-changes` and `activation-selections` changed their expectation, and
  `layer-merge` and `effective-reads` lost their `@WIP` tags (the glue is bound). Six steps were added to the
  vocabulary (`scenarios/README.md`), and `I remember the field` takes a JSON pointer. No operation, schema or response changes.
