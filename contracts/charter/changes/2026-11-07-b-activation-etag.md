### Breaking

- The `ETag` of `getActivation` is no longer the bare quoted revision (decision `01M6Y11E55YP5TG1EJ1MDKCFRP`, B4): it covers the activation's revision and the `governanceFingerprint` of the packs of its closure, because `Activation.denyNotApplied` is judged against those packs. It changes when a pack of the closure changes, and not when a pack outside it does. A client that compared the `ETag` to the `revision` must stop doing so; `getActivation` can now also answer `500` with a composition code or `pack_unreadable`. ADR-0059 D5 is amended by the docs work package.
